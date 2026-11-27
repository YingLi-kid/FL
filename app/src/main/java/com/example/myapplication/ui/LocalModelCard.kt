package com.example.myapplication.ui

import com.example.myapplication.R
import com.example.myapplication.appText

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
                if (isImport) status = appText(R.string.model_import_success)
            } catch (e: CancellationException) {
                status = if (isImport) appText(R.string.model_import_cancelled) else appText(R.string.model_download_paused)
                throw e
            } catch (e: Exception) {
                status = when (e) {
                    is java.util.zip.ZipException -> appText(R.string.model_zip_invalid)
                    is java.net.SocketTimeoutException -> appText(R.string.model_download_timeout)
                    is java.net.UnknownHostException -> appText(R.string.model_source_unreachable)
                    is IOException -> e.localizedMessage ?: appText(R.string.file_operation_error)
                    else -> e.localizedMessage ?: appText(R.string.operation_error)
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
                open = { context.contentResolver.openInputStream(uri) ?: throw IOException(appText(R.string.model_zip_open_error)) },
                progress = { bytes, stage ->
                    importStage = stage
                    progress = bytes
                }
            )
        }
    }

    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(appText(R.string.model_selected, backend.name), style = MaterialTheme.typography.titleMedium)
            Button(
                enabled = !busy,
                onClick = { importZip.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) }
            ) { Text(appText(R.string.import_zip)) }
            Text(appText(R.string.model_list), style = MaterialTheme.typography.titleMedium)
            models.forEach { model ->
                FilterChip(
                    selected = model.id == backend.id,
                    enabled = !busy,
                    onClick = { onSelectModel(model.id) },
                    label = { Text(model.name) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Text(appText(R.string.model_summary, (store.downloadBytes + 500_000) / 1_000_000, backend.outputLanguages.size))
            backend.modelNotice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            when {
                busy -> {
                    LinearProgressIndicator(
                        progress = { (progress.toDouble() / store.downloadBytes).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (importing) {
                        Text(when (importStage) {
                            ModelImportStage.OPENING -> appText(R.string.model_zip_opening)
                            ModelImportStage.READING -> appText(R.string.model_zip_reading, progress / 1_000_000, (store.downloadBytes + 500_000) / 1_000_000)
                            ModelImportStage.INSTALLING -> appText(R.string.model_installing)
                        })
                        if (waitingForFile && !cancelling) {
                            Text(appText(R.string.model_zip_slow))
                        }
                    } else {
                        Text(if (progress >= store.downloadBytes) appText(R.string.model_verifying) else appText(R.string.model_downloaded, progress / 1_000_000))
                    }
                    OutlinedButton(enabled = !cancelling, onClick = {
                        cancelling = true
                        job?.cancel()
                    }) { Text(if (cancelling) appText(R.string.stopping) else if (importing) appText(R.string.cancel_import) else appText(R.string.pause)) }
                }
                installed -> {
                    Text(appText(R.string.model_verified))
                    OutlinedButton(onClick = { start(0) { store.remove() } }) { Text(appText(R.string.delete_model)) }
                }
                else -> {
                    if (downloaded > 0) Text(appText(R.string.model_resume_size, downloaded / 1_000_000))
                    OutlinedButton(onClick = {
                        start(downloaded) { store.download(store.defaultSource) { progress = it } }
                    }) { Text(if (downloaded > 0) appText(R.string.model_resume_international) else appText(R.string.model_download_international)) }
                    OutlinedButton(onClick = { customSource = !customSource }) {
                        Text(if (customSource) appText(R.string.hide_mirror) else appText(R.string.use_mirror))
                    }
                    if (customSource) {
                        OutlinedTextField(
                            value = source,
                            onValueChange = { source = it },
                            label = { Text(appText(R.string.mirror_directory)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedButton(enabled = source.isNotBlank(), onClick = {
                            start(downloaded) { store.download(source.trim()) { progress = it } }
                        }) { Text(appText(R.string.download_from_mirror)) }
                    }
                }
            }
            if (!busy) {
                OutlinedButton(onClick = { showDownloadFiles = !showDownloadFiles }) {
                    Text(if (showDownloadFiles) appText(R.string.hide_file_links) else appText(R.string.show_file_links))
                }
                if (showDownloadFiles) {
                    Text(
                        appText(R.string.model_package_help),
                        style = MaterialTheme.typography.bodySmall
                    )
                    store.downloadFiles.forEach { file ->
                        OutlinedButton(
                            onClick = {
                                runCatching { uriHandler.openUri(file.url) }.onFailure {
                                    status = appText(R.string.browser_open_app_download)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(file.name) }
                    }
                }
            }
            if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall)
            Text(
                if (backend.isSupported()) appText(R.string.model_ready_help) else appText(R.string.model_import_only),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
