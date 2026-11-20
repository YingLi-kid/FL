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
import android.util.Base64
import android.util.Log
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
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Icon
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
import com.example.myapplication.data.cosyVoiceLanguageHint
import com.example.myapplication.data.languageLabel
import com.example.myapplication.service.AliyunVoice
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
    providerApiKey: String?
) {
    var agreed by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var selectedLanguage by rememberSaveable { mutableStateOf("zh") }

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

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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
                    samplePath?.let { File(it).delete() }
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
    val canClone = agreed && name.isNotBlank() && sampleExists && !isCloning && !isLoadingSample

    LaunchedEffect(isCloning) {
        if (!isCloning) return@LaunchedEffect
        val apiKey = providerApiKey
        if (apiKey.isNullOrBlank()) {
            onShowMessage("请先在设置 → 服务商管理中配置 API Key 并启用阿里云")
            isCloning = false
            return@LaunchedEffect
        }
        cloneError = null
        val submittedName = name.trim()
        val submittedLanguage = selectedLanguage
        val submittedSource = sampleSource ?: VoiceSource.RECORDED
        try {
            val file = requireNotNull(samplePath?.let { File(it) }) { "请重新选择声音样本" }
            cloneStatusText = "正在上传音频并创建复刻音色…"
            cloneProgress = 0.2f
            Log.d("CloneScreen", "Validating uploaded sample: ${file.length()} bytes")
            val (seconds, b64) = withContext(Dispatchers.IO) {
                (AudioSamples.durationMs(file) / 1000).toInt() to Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
            }
            Log.d("CloneScreen", "Sample validated; submitting clone")
            // Once submitted, retain the returned ID even if the user navigates away.
            withContext(NonCancellable) {
                when (val response = AliyunVoice().cloneVoice(
                    apiKey = apiKey,
                    audioBase64 = b64,
                    mimeType = mimeTypeForFormat(sampleFormat),
                    prefix = "vc" + (System.currentTimeMillis() % 100000000L).toString().padStart(8, '0'),
                    languageHint = cosyVoiceLanguageHint(submittedLanguage)
                )) {
                    is AliyunVoice.Result.Success -> {
                        onAddVoice(VoiceProfile(
                            name = submittedName,
                            language = submittedLanguage,
                            source = submittedSource,
                            sampleSeconds = seconds,
                            speakerId = response.voiceId,
                            status = AliyunVoice.STATUS_DEPLOYING
                        ))
                        onShowMessage("音色已提交审核并保存，可在我的音色中查看状态")
                        name = ""
                        sampleSource = null
                        sampleSeconds = 0
                        sampleFileName = null
                        samplePath = null
                        file.delete()
                        agreed = false
                    }
                    is AliyunVoice.Result.Failure -> {
                        cloneError = "克隆失败：${response.message}"
                        onShowMessage(cloneError!!)
                    }
                }
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
                    Text("克隆专属音色", style = gradientTitleStyle())
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "用一段本人语音样本，复刻出可复用的音色。",
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
                onSelect = { selectedLanguage = it }
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
                    text = "上传音频",
                    onClick = { filePicker.launch("audio/*") },
                    enabled = !isCloning && !isLoadingSample,
                    leadingIcon = Icons.Rounded.UploadFile
                )
            }
            Spacer(Modifier.height(12.dp))
            if (samplePath != null && !sampleExists) {
                Text("样本缓存已清理，请重新录音或上传", color = TechPink)
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
                        label = "已上传",
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
        }

        // 克隆按钮
        NeonButton(
            text = if (isCloning) "克隆中…" else "开始克隆音色",
            onClick = {
                if (providerApiKey.isNullOrBlank()) onShowMessage("请先在设置 → 服务商管理中配置 API Key 并启用阿里云")
                else isCloning = true
            },
            enabled = canClone,
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
                LinearProgressIndicator(
                    progress = { cloneProgress },
                    modifier = Modifier.fillMaxWidth(),
                    color = TechCyan,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
                Spacer(Modifier.height(6.dp))
                Text(
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
                        onClick = { onUseVoice(voice.id) },
                        onDelete = { onRemoveVoice(voice.id) }
                    )
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }

    // 现场录音对话框（真实 MediaRecorder）
    if (showRecorder) {
        RecorderDialog(
            onDismiss = { showRecorder = false },
            onConfirm = { file, seconds ->
                sampleSource = VoiceSource.RECORDED
                sampleSeconds = seconds
                samplePath?.let { File(it).delete() }
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
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = voice.status == "OK" && !voice.speakerId.isNullOrBlank(), onClick = onClick)
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
                    if (voice.source == VoiceSource.RECORDED) "录音" else "上传",
                    "${voice.sampleSeconds}s",
                    when {
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

    fun begin() {
        recordFile?.delete()
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
            accepted = true
            onConfirm(file, (duration / 1000).toInt())
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
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassOutlineButton(text = if (isRecording) "停止" else "开始录音", onClick = {
                    if (isRecording) finish()
                    else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) begin()
                    else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                })
                NeonButton(text = "取消", onClick = onDismiss)
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
