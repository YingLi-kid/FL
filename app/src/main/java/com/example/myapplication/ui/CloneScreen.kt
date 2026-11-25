package com.example.myapplication.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.SystemClock
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import com.example.myapplication.service.AudioSamples
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.example.myapplication.data.VoiceProfile
import com.example.myapplication.data.VoiceSource
import com.example.myapplication.data.languageLabel
import com.example.myapplication.data.VoiceEngine
import com.example.myapplication.service.SpeechBackend
import com.example.myapplication.service.LocalSpeechBackend
import com.example.myapplication.service.CosyVoiceBackend
import com.example.myapplication.service.AudioPlayer
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.example.myapplication.ui.theme.TechCyan
import com.example.myapplication.ui.theme.TechPink
import kotlinx.coroutines.delay
import java.io.File

/**
 * 克隆音色页：授权声明 → 命名/语言 → 录音或上传样本 → 真实复刻训练 → 已克隆音色列表。
 * 已配置阿里云服务商时走真实声音复刻（base64 上传 + 审核轮询），未配置服务商时提示完成配置。
 */
@Composable
fun CloneScreen(
    voices: List<VoiceProfile>,
    onAddVoice: suspend (VoiceProfile) -> Unit,
    onRemoveVoice: (String) -> Unit,
    onUseVoice: (String) -> Unit,
    onShowMessage: (String) -> Unit,
    providerApiKey: String?,
    backend: SpeechBackend = CosyVoiceBackend,
    credentials: Map<String, String> = providerApiKey?.let { mapOf("apiKey" to it) }.orEmpty(),
    onConfigureCloud: (() -> Unit)? = null,
    onRenameVoice: ((String, String) -> Unit)? = null
) {
    val local = backend is LocalSpeechBackend
    val needsKey = !backend.credentialsReady(credentials)
    var agreed by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var referenceText by rememberSaveable { mutableStateOf("") }
    var selectedLanguage by rememberSaveable { mutableStateOf(backend.sampleLanguages.first().code) }

    var sampleSource by rememberSaveable { mutableStateOf<VoiceSource?>(null) }
    var sampleSeconds by rememberSaveable { mutableIntStateOf(0) }
    var sampleFileName by rememberSaveable { mutableStateOf<String?>(null) }
    var samplePath by rememberSaveable { mutableStateOf<String?>(null) }
    var isLoadingSample by remember { mutableStateOf(false) }
    var sampleFormat by rememberSaveable { mutableStateOf("wav") }

    var showRecorder by remember { mutableStateOf(false) }
    var isCloning by remember { mutableStateOf(false) }
    var cloneProgress by remember { mutableFloatStateOf(0f) }
    var cloneStatusText by remember { mutableStateOf("") }
    var cloneError by rememberSaveable { mutableStateOf<String?>(null) }
    var renameId by rememberSaveable { mutableStateOf<String?>(null) }
    var renameText by rememberSaveable { mutableStateOf("") }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val samplePlayer = remember(context) { AudioPlayer(context.applicationContext) }
    DisposableEffect(samplePlayer) { onDispose { samplePlayer.stop() } }

    fun clearSample() {
        samplePlayer.stop()
        samplePath?.let { File(it).delete() }
        samplePath = null
        sampleSource = null
        sampleSeconds = 0
        sampleFileName = null
        sampleFormat = "wav"
        referenceText = ""
        cloneError = null
    }

    // 上传音频文件选择器
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isLoadingSample = true
                try {
                    val fileName = queryDisplayName(context, uri) ?: "voice_sample"
                    val format = formatFromFileName(fileName)
                    val (file, seconds) = withContext(Dispatchers.IO) { AudioSamples.importFile(context, uri, format) }
                    clearSample()
                    samplePath = file.absolutePath
                    sampleSource = VoiceSource.UPLOADED
                    sampleSeconds = seconds
                    sampleFileName = fileName
                    sampleFormat = format
                    onShowMessage("已选择音频：$fileName（${seconds} 秒）")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    onShowMessage("读取音频失败：${e.message}")
                } finally {
                    isLoadingSample = false
                }
            }
        }
    }

    val sampleExists = samplePath?.let { File(it).isFile } == true
    val transcriptReady = !backend.requiresReferenceText || referenceText.isNotBlank()
    val canClone = agreed && name.isNotBlank() && sampleExists && transcriptReady && !isCloning && !isLoadingSample

    LaunchedEffect(isCloning) {
        if (!isCloning) return@LaunchedEffect
        cloneError = null
        try {
            check(backend.credentialsReady(credentials)) { "请先配置所选服务商的 API Key。" }
            val file = requireNotNull(samplePath?.let(::File)) { "请重新选择声音样本。" }
            val profile = VoiceProfile(
                name = name.trim(), language = selectedLanguage,
                source = sampleSource ?: VoiceSource.RECORDED, sampleSeconds = sampleSeconds,
                engine = backend.engine, backendId = backend.id
            )
            cloneStatusText = if (local) "正在保存本地音色…" else "正在上传音频并创建复刻音色…"
            cloneProgress = 0.2f
            // Persist a submitted voice even if navigation removes the page during the operation.
            withContext(NonCancellable) {
                backend.createVoice(
                    context, profile, file, mimeTypeForFormat(sampleFormat), credentials,
                    referenceText.takeIf { backend.requiresReferenceText }?.trim(), onAddVoice
                )
                clearSample()
                name = ""
                agreed = false
                onShowMessage(if (local) "已保存本地音色，可在文字转语音中使用。" else "音色已提交审核并保存。")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            cloneError = "克隆失败：${e.message ?: "未知错误"}"
            onShowMessage(cloneError!!)
        } finally {
            isCloning = false
            cloneProgress = 0f
            cloneStatusText = ""
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 引导说明
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(TechCyan.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.PersonAdd, contentDescription = null, tint = TechCyan)
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(if (local) "创建本地音色" else "克隆专属音色", style = gradientTitleStyle())
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (local) "保存参考录音，之后可在手机上离线合成。" else "用一段本人语音样本，复刻出可复用的音色。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 授权声明
        GlassCard {
            SectionTitle("本人授权", icon = Icons.Rounded.VerifiedUser)
            Spacer(Modifier.height(10.dp))
            CheckRow(
                checked = agreed,
                onToggle = { agreed = !agreed },
                title = "我确认这是本人声音，或已获得声音本人的明确授权"
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "未获授权的音色克隆可能侵犯他人声音权益，请务必遵守。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 音色信息
        GlassCard {
            SectionTitle("音色信息", icon = Icons.Rounded.GraphicEq)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("音色名称") },
                placeholder = { Text("例如：我的声音 / 妈妈的声音") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TechCyan,
                    focusedLabelColor = TechCyan,
                    cursorColor = TechCyan,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                )
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "克隆样本语言",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            LanguageSelector(
                selected = selectedLanguage,
                onSelect = { selectedLanguage = it },
                languages = backend.sampleLanguages
            )
        }

        // 样本采集
        GlassCard {
            SectionTitle("声音样本", icon = Icons.Rounded.Mic)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassOutlineButton(
                    text = "现场录音",
                    onClick = { showRecorder = true },
                    enabled = !isCloning && !isLoadingSample,
                    leadingIcon = Icons.Rounded.Mic
                )
                GlassOutlineButton(
                    text = if (local) "导入音频" else "上传音频",
                    onClick = { filePicker.launch("audio/*") },
                    enabled = !isCloning && !isLoadingSample,
                    leadingIcon = Icons.Rounded.UploadFile
                )
            }
            Spacer(Modifier.height(12.dp))
            if (samplePath != null && !sampleExists) {
                Text("样本缓存已清理，请重新录音或上传", color = TechPink)
            }
            if (sampleExists && !isCloning && !isLoadingSample) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    androidx.compose.material3.TextButton(onClick = {
                        samplePath?.let { samplePlayer.playFile(File(it), onError = onShowMessage) }
                    }) { Text("试听样本") }
                    androidx.compose.material3.TextButton(onClick = { clearSample() }) { Text("清除本次录音") }
                    androidx.compose.material3.TextButton(onClick = {
                        clearSample()
                        showRecorder = true
                    }) { Text("重新录音") }
                }
            }
            when (sampleSource) {
                VoiceSource.RECORDED -> {
                    SampleStatusRow(
                        label = "已录音",
                        detail = "时长 ${formatSeconds(sampleSeconds)}",
                        accent = TechCyan
                    )
                }
                VoiceSource.UPLOADED -> {
                    SampleStatusRow(
                        label = if (local) "已导入" else "已上传",
                        detail = sampleFileName ?: "",
                        accent = TechCyan
                    )
                }
                null -> {
                    Text(
                        "尚未采集样本，请录音或上传一段 5～30 秒的清晰语音。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (backend.requiresReferenceText && sampleExists) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = referenceText,
                    onValueChange = { referenceText = it.take(1000) },
                    label = { Text("参考录音原文") },
                    supportingText = { Text("填写这段录音里实际说的话，稍后可以用另一种语言生成。") },
                    enabled = !isCloning,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // 克隆按钮
        NeonButton(
            text = when {
                isCloning -> if (local) "正在保存…" else "克隆中…"
                local -> "保存本地音色"
                needsKey && onConfigureCloud != null -> "配置 API Key"
                else -> "开始克隆音色"
            },
            onClick = {
                when {
                    needsKey && onConfigureCloud != null -> onConfigureCloud()
                    needsKey -> onShowMessage("请先配置所选服务商的 API Key")
                    else -> isCloning = true
                }
            },
            enabled = canClone || (needsKey && onConfigureCloud != null && !isCloning),
            modifier = Modifier.fillMaxWidth()
        )

        cloneError?.let { message ->
            GlassCard { Text(message, color = TechPink) }
        }

        // 克隆进度
        if (isCloning) {
            GlassCard {
                Text(
                    cloneStatusText.ifBlank { "正在克隆…" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(10.dp))
                if (local) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(
                    progress = { cloneProgress },
                    modifier = Modifier.fillMaxWidth(),
                    color = TechCyan,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
                Spacer(Modifier.height(6.dp))
                if (!local) Text(
                    "${(cloneProgress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = TechCyan
                )
            }
        }

        // 已克隆音色列表
        if (voices.isNotEmpty()) {
            GlassCard(contentPadding = PaddingValues(0.dp)) {
                Column(modifier = Modifier.padding(20.dp)) {
                    SectionTitle("我的音色（${voices.size}）", icon = Icons.Rounded.GraphicEq)
                }
                voices.forEach { voice ->
                    VoiceRow(
                        voice = voice,
                        hasLocalSample = local && backend.isVoiceUsable(context, voice),
                        onClick = { onUseVoice(voice.id) },
                        onRename = if (onRenameVoice != null) ({
                            renameText = voice.name
                            renameId = voice.id
                        }) else null,
                        onDelete = { onRemoveVoice(voice.id) }
                    )
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }

    renameId?.let { id ->
        AlertDialog(
            onDismissRequest = { renameId = null },
            title = { Text("重命名音色") },
            text = {
                OutlinedTextField(value = renameText, onValueChange = { renameText = it }, singleLine = true,
                    label = { Text("音色名称") })
            },
            confirmButton = {
                TextButton(enabled = renameText.isNotBlank(), onClick = {
                    onRenameVoice?.invoke(id, renameText.trim())
                    renameId = null
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { renameId = null }) { Text("取消") } }
        )
    }

    // 现场录音对话框（真实 MediaRecorder）
    if (showRecorder) {
        RecorderDialog(
            onDismiss = { showRecorder = false },
            onConfirm = { file, seconds ->
                clearSample()
                sampleSource = VoiceSource.RECORDED
                sampleSeconds = seconds
                sampleFileName = "recording.m4a"
                samplePath = file.absolutePath
                sampleFormat = "m4a"
                showRecorder = false
            }
        )
    }
}

@Composable
private fun SampleStatusRow(label: String, detail: String, accent: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(accent.copy(alpha = 0.10f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            "$label · $detail",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun VoiceRow(
    voice: VoiceProfile,
    hasLocalSample: Boolean,
    onClick: () -> Unit,
    onRename: (() -> Unit)?,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = voice.status == "OK" && (hasLocalSample || !voice.speakerId.isNullOrBlank()), onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(TechCyan.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = TechCyan, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(voice.name, style = MaterialTheme.typography.titleMedium)
            Text(
                listOf(
                    languageLabel(voice.language),
                    if (voice.source == VoiceSource.RECORDED) "录音" else if (voice.engine != VoiceEngine.ALIYUN) "导入" else "上传",
                    "${voice.sampleSeconds}s",
                    when {
                        voice.engine != VoiceEngine.ALIYUN -> if (hasLocalSample) "本地音色" else "样本缺失，请重新创建"
                        voice.speakerId.isNullOrBlank() -> "无有效音色 ID，请重新克隆"
                        voice.status == "DEPLOYING" -> "审核中（联网后自动查询）"
                        voice.status == "UNDEPLOYED" -> "审核未通过"
                        else -> "可使用"
                    }
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        onRename?.let {
            IconButton(onClick = it) { Icon(Icons.Rounded.Edit, contentDescription = "重命名音色") }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Rounded.Delete, contentDescription = "删除", tint = TechPink)
        }
    }
}

@Composable
private fun RecorderDialog(onDismiss: () -> Unit, onConfirm: (File, Int) -> Unit) {
    val context = LocalContext.current
    var isRecording by remember { mutableStateOf(false) }
    var seconds by remember { mutableIntStateOf(0) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordFile by remember { mutableStateOf<File?>(null) }
    var accepted by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var startedAt by remember { mutableStateOf(0L) }
    var validatedSeconds by remember { mutableStateOf<Int?>(null) }
    val preview = remember(context) { AudioPlayer(context.applicationContext) }

    fun discard() {
        preview.stop()
        isRecording = false
        runCatching { recorder?.release() }
        recorder = null
        recordFile?.delete()
        recordFile = null
        validatedSeconds = null
        seconds = 0
        error = null
    }

    fun begin() {
        discard()
        val file = File.createTempFile("rec_", ".m4a", context.cacheDir)
        recordFile = file
        val rec = MediaRecorder()
        recorder = rec
        try {
            rec.setAudioSource(MediaRecorder.AudioSource.MIC)
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            rec.setAudioSamplingRate(24000)
            rec.setAudioEncodingBitRate(96000)
            rec.setOutputFile(file.absolutePath)
            rec.prepare()
            rec.start()
            seconds = 0
            startedAt = SystemClock.elapsedRealtime()
            error = null
            isRecording = true
        } catch (e: Exception) {
            rec.release()
            recorder = null
            file.delete()
            error = "无法开始录音：${e.message ?: "请检查麦克风"}"
        }
    }

    fun finish() {
        isRecording = false
        try {
            recorder?.stop()
            recorder?.release()
            recorder = null
            val file = requireNotNull(recordFile)
            val duration = AudioSamples.durationMs(file)
            validatedSeconds = (duration / 1000).toInt()
            seconds = requireNotNull(validatedSeconds)
            error = null
        } catch (e: Exception) {
            recorder?.release()
            recorder = null
            recordFile?.delete()
            error = "录音不可用：${e.message ?: "请录制至少 5 秒后重试"}"
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) begin() else error = "录音权限被拒绝，请在系统设置中开启麦克风权限"
    }
    LaunchedEffect(isRecording) {
        while (isRecording) {
            delay(200)
            seconds = ((SystemClock.elapsedRealtime() - startedAt) / 1000).toInt()
            // Leave room for encoder padding so the actual file remains below 30 seconds.
            if (SystemClock.elapsedRealtime() - startedAt >= 29000) finish()
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            preview.stop()
            runCatching { recorder?.release() }
            if (!accepted) recordFile?.delete()
        }
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("录制声音样本", style = gradientTitleStyle())
            Spacer(Modifier.height(6.dp))
            Text("请用正常语速清晰朗读 5～30 秒", style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it, color = TechPink, style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(18.dp))
            VoiceWave(active = isRecording, modifier = Modifier.height(48.dp))
            Text(formatSeconds(seconds), style = MaterialTheme.typography.titleLarge, color = TechCyan)
            Spacer(Modifier.height(20.dp))
            val ready = validatedSeconds
            if (ready != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { recordFile?.let { preview.playFile(it, onError = { error = it }) } }) { Text("试听") }
                    TextButton(onClick = { discard() }) { Text("清除录音") }
                    TextButton(onClick = { begin() }) { Text("重新录音") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    NeonButton(text = "使用录音", onClick = {
                        val file = recordFile ?: return@NeonButton
                        preview.stop()
                        accepted = true
                        onConfirm(file, ready)
                    })
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GlassOutlineButton(text = if (isRecording) "停止" else "开始录音", onClick = {
                        if (isRecording) finish()
                        else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) begin()
                        else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    })
                    if (isRecording) TextButton(onClick = { begin() }) { Text("重新录音") }
                    TextButton(onClick = onDismiss) { Text("取消") }
                }
            }
        }
    }
}

private fun queryDisplayName(context: Context, uri: Uri): String? {
    return try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    } catch (e: Exception) {
        null
    }
}

private fun formatFromFileName(name: String): String {
    val ext = name.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "wav", "mp3", "ogg", "m4a", "aac", "pcm" -> ext
        "mp4" -> "m4a"
        else -> "wav"
    }
}

private fun mimeTypeForFormat(format: String): String = when (format) {
    "mp3" -> "audio/mpeg"
    "m4a" -> "audio/mp4"
    "aac" -> "audio/aac"
    "ogg" -> "audio/ogg"
    else -> "audio/wav"
}

private fun formatSeconds(total: Int): String {
    val m = total / 60
    val s = total % 60
    return "%02d:%02d".format(m, s)
}

@Preview(showBackground = true)
@Composable
private fun CloneScreenPreview() {
    MyApplicationTheme {
        Box(modifier = Modifier.background(Color(0xFF080D1C))) {
            CloneScreen(
                voices = listOf(
                    VoiceProfile(name = "我的声音", language = "zh", source = VoiceSource.RECORDED, sampleSeconds = 12)
                ),
                onAddVoice = {},
                onRemoveVoice = {},
                onUseVoice = {},
                onShowMessage = {},
                providerApiKey = null
            )
        }
    }
}
