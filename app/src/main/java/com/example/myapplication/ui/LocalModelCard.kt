package com.example.myapplication.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.example.myapplication.service.LocalSpeechBackend
import com.example.myapplication.service.ModelImportStage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException

@Composable
internal fun LocalModelCard(
    backend: LocalSpeechBackend,
    models: List<LocalSpeechBackend>,
    onSelectModel: (String) -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val store = remember(context, backend) { backend.modelPackage(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var installed by remember(store) { mutableStateOf(store.isInstalled()) }
    var downloaded by remember(store) { mutableStateOf(store.downloadedBytes()) }
    var progress by remember { mutableStateOf(0L) }
    var job by remember { mutableStateOf<Job?>(null) }
    var status by remember { mutableStateOf("") }
    var source by rememberSaveable { mutableStateOf("") }
    var customSource by rememberSaveable { mutableStateOf(false) }
    var showDownloadFiles by rememberSaveable { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var importStage by remember { mutableStateOf(ModelImportStage.OPENING) }
    var cancelling by remember { mutableStateOf(false) }
    var waitingForFile by remember { mutableStateOf(false) }
    val busy = job != null

    LaunchedEffect(importing, importStage, progress / 1_000_000) {
        waitingForFile = false
        if (importing && importStage != ModelImportStage.INSTALLING) {
            delay(10_000)
            waitingForFile = true
        }
    }

    fun start(initialProgress: Long, isImport: Boolean = false, operation: suspend () -> Unit) {
        if (job != null) return
        progress = initialProgress
        status = ""
        importing = isImport
        importStage = ModelImportStage.OPENING
        cancelling = false
        job = scope.launch {
            try {
                operation()
                if (isImport) status = "ZIP 导入完成，模型已校验。"
            } catch (e: CancellationException) {
                status = if (isImport) "已取消导入，可重新选择 ZIP。" else "下载已暂停；已下载的部分可以续传。"
                throw e
            } catch (e: Exception) {
                status = when (e) {
                    is java.util.zip.ZipException -> "ZIP 文件无法解压，请换用完整的模型包。"
                    is java.net.SocketTimeoutException -> "连接超时，已保留下载进度。可以重试、换来源或导入 ZIP。"
                    is java.net.UnknownHostException -> "无法连接下载来源。可以换来源或导入 ZIP。"
                    is IOException -> e.localizedMessage ?: "文件操作失败，请重试。"
                    else -> e.localizedMessage ?: "操作失败，请重试。"
                }
            } finally {
                downloaded = store.downloadedBytes()
                installed = store.isInstalled()
                importing = false
                cancelling = false
                job = null
            }
        }
    }

    val importZip = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) start(0, isImport = true) {
            store.importZip(
                open = { context.contentResolver.openInputStream(uri) ?: throw IOException("无法读取 ZIP 文件。") },
                progress = { bytes, stage ->
                    importStage = stage
                    progress = bytes
                }
            )
        }
    }

    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("所选型号 · ${backend.name}", style = MaterialTheme.typography.titleMedium)
            Button(
                enabled = !busy,
                onClick = { importZip.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) }
            ) { Text("导入 ZIP") }
            Text("模型列表", style = MaterialTheme.typography.titleMedium)
            models.forEach { model ->
                FilterChip(
                    selected = model.id == backend.id,
                    enabled = !busy,
                    onClick = { onSelectModel(model.id) },
                    label = { Text(model.name) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Text("约 ${(store.downloadBytes + 500_000) / 1_000_000} MB · 支持 ${backend.outputLanguages.size} 种合成语言")
            backend.modelNotice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            when {
                busy -> {
                    LinearProgressIndicator(
                        progress = { (progress.toDouble() / store.downloadBytes).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (importing) {
                        Text(when (importStage) {
                            ModelImportStage.OPENING -> "正在打开 ZIP…"
                            ModelImportStage.READING -> "正在导入并校验 ${progress / 1_000_000} / ${(store.downloadBytes + 500_000) / 1_000_000} MB"
                            ModelImportStage.INSTALLING -> "校验通过，正在安装模型…"
                        })
                        if (waitingForFile && !cancelling) {
                            Text("文件读取较慢。若 ZIP 在网盘，请先保存到手机的「下载」目录。")
                        }
                    } else {
                        Text(if (progress >= store.downloadBytes) "正在校验模型…" else "已下载 ${progress / 1_000_000} MB")
                    }
                    OutlinedButton(enabled = !cancelling, onClick = {
                        cancelling = true
                        job?.cancel()
                    }) { Text(if (cancelling) "正在停止…" else if (importing) "取消导入" else "暂停") }
                }
                installed -> {
                    Text("模型文件已校验")
                    OutlinedButton(onClick = { start(0) { store.remove() } }) { Text("删除模型") }
                }
                else -> {
                    if (downloaded > 0) Text("可续传 ${downloaded / 1_000_000} MB")
                    OutlinedButton(onClick = {
                        start(downloaded) { store.download(store.defaultSource) { progress = it } }
                    }) { Text(if (downloaded > 0) "继续下载 · 国际源" else "下载模型 · 国际源") }
                    OutlinedButton(onClick = { customSource = !customSource }) {
                        Text(if (customSource) "收起备用来源" else "使用备用来源")
                    }
                    if (customSource) {
                        OutlinedTextField(
                            value = source,
                            onValueChange = { source = it },
                            label = { Text("HTTPS 镜像目录") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedButton(enabled = source.isNotBlank(), onClick = {
                            start(downloaded) { store.download(source.trim()) { progress = it } }
                        }) { Text("从备用来源下载") }
                    }
                }
            }
            if (!busy) {
                OutlinedButton(onClick = { showDownloadFiles = !showDownloadFiles }) {
                    Text(if (showDownloadFiles) "收起文件下载链接" else "查看文件下载链接")
                }
                if (showDownloadFiles) {
                    Text(
                        "在浏览器下载下列全部文件，保留原文件名，压缩成一个 ZIP 后再导入。",
                        style = MaterialTheme.typography.bodySmall
                    )
                    store.downloadFiles.forEach { file ->
                        OutlinedButton(
                            onClick = {
                                runCatching { uriHandler.openUri(file.url) }.onFailure {
                                    status = "无法打开浏览器，请使用应用内下载。"
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(file.name) }
                    }
                }
            }
            if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall)
            Text(
                if (backend.isSupported()) "安装后，可在主页切换到「本地离线」。" else "此设备可验证模型导入，离线克隆需要 Android 12 及以上的 ARM64 真机。",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
