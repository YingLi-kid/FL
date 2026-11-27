package com.example.myapplication.ui

import com.example.myapplication.R
import com.example.myapplication.appText

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
private val submissionLock = kotlinx.coroutines.sync.Mutex()
private var submissionActive by mutableStateOf(false)

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
                    onShowMessage(appText(R.string.sample_selected, fileName, seconds))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    onShowMessage(appText(R.string.sample_read_error, e.message ?: appText(R.string.unknown_error)))
                } finally {
                    isLoadingSample = false
                }
            }
        }
    }

    val sampleExists = samplePath?.let { File(it).isFile } == true
    val transcriptReady = !backend.requiresReferenceText || referenceText.isNotBlank()
    val canClone = !submissionActive && agreed && name.isNotBlank() && sampleExists && transcriptReady && !isCloning && !isLoadingSample

    LaunchedEffect(isCloning) {
        if (!isCloning) return@LaunchedEffect
        if (!submissionLock.tryLock()) { isCloning = false; return@LaunchedEffect }
        submissionActive = true
        cloneError = null
        try {
            check(backend.credentialsReady(credentials)) { appText(R.string.provider_key_required) }
            val file = requireNotNull(samplePath?.let(::File)) { appText(R.string.sample_reselect) }
            val profile = VoiceProfile(
                name = name.trim(), language = selectedLanguage,
                source = sampleSource ?: VoiceSource.RECORDED, sampleSeconds = sampleSeconds,
                engine = backend.engine, backendId = backend.id
            )
            cloneStatusText = if (local) appText(R.string.clone_local_saving) else appText(R.string.clone_uploading)
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
                onShowMessage(if (local) appText(R.string.clone_local_saved) else appText(R.string.clone_submitted))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            cloneError = appText(R.string.clone_error, e.message ?: appText(R.string.unknown_error))
            onShowMessage(cloneError!!)
        } finally {
            submissionActive = false
            submissionLock.unlock()
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
                    Text(if (local) appText(R.string.clone_local_title) else appText(R.string.clone_cloud_title), style = gradientTitleStyle())
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (local) appText(R.string.clone_local_intro) else appText(R.string.clone_cloud_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 授权声明
        GlassCard {
            SectionTitle(appText(R.string.clone_authorization), icon = Icons.Rounded.VerifiedUser)
            Spacer(Modifier.height(10.dp))
            CheckRow(
                checked = agreed,
                onToggle = { agreed = !agreed },
                title = appText(R.string.clone_authorization_confirm)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                appText(R.string.clone_authorization_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 音色信息
        GlassCard {
            SectionTitle(appText(R.string.voice_details), icon = Icons.Rounded.GraphicEq)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(appText(R.string.voice_name)) },
                placeholder = { Text(appText(R.string.voice_name_example)) },
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
                appText(R.string.sample_language),
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
            SectionTitle(appText(R.string.voice_sample), icon = Icons.Rounded.Mic)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassOutlineButton(
                    text = appText(R.string.record_sample),
                    onClick = { showRecorder = true },
                    enabled = !isCloning && !isLoadingSample,
                    leadingIcon = Icons.Rounded.Mic
                )
                GlassOutlineButton(
                    text = if (local) appText(R.string.import_audio) else appText(R.string.upload_audio),
                    onClick = { filePicker.launch("audio/*") },
                    enabled = !isCloning && !isLoadingSample,
                    leadingIcon = Icons.Rounded.UploadFile
                )
            }
            Spacer(Modifier.height(12.dp))
            if (samplePath != null && !sampleExists) {
                Text(appText(R.string.sample_cache_missing), color = TechPink)
            }
            if (sampleExists && !isCloning && !isLoadingSample) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    androidx.compose.material3.TextButton(onClick = {
                        samplePath?.let { samplePlayer.playFile(File(it), onError = onShowMessage) }
                    }) { Text(appText(R.string.preview_sample)) }
                    androidx.compose.material3.TextButton(onClick = { clearSample() }) { Text(appText(R.string.clear_current_recording)) }
                    androidx.compose.material3.TextButton(onClick = {
                        clearSample()
                        showRecorder = true
                    }) { Text(appText(R.string.record_again)) }
                }
            }
            when (sampleSource) {
                VoiceSource.RECORDED -> {
                    SampleStatusRow(
                        label = appText(R.string.sample_recorded),
                        detail = appText(R.string.sample_duration, formatSeconds(sampleSeconds)),
                        accent = TechCyan
                    )
                }
                VoiceSource.UPLOADED -> {
                    SampleStatusRow(
                        label = if (local) appText(R.string.sample_imported) else appText(R.string.sample_uploaded),
                        detail = sampleFileName ?: "",
                        accent = TechCyan
                    )
                }
                null -> {
                    Text(
                        appText(R.string.sample_required),
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
                    label = { Text(appText(R.string.reference_transcript)) },
                    supportingText = { Text(appText(R.string.reference_transcript_help)) },
                    enabled = !isCloning,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // 克隆按钮
        NeonButton(
            text = when {
                isCloning -> if (local) appText(R.string.saving) else appText(R.string.cloning)
                local -> appText(R.string.clone_save_local)
                needsKey && onConfigureCloud != null -> appText(R.string.configure_api_key)
                else -> appText(R.string.clone_start)
            },
            onClick = {
                when {
                    needsKey && onConfigureCloud != null -> onConfigureCloud()
                    needsKey -> onShowMessage(appText(R.string.provider_key_prompt))
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
                    cloneStatusText.ifBlank { appText(R.string.clone_working) },
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
                    SectionTitle(appText(R.string.my_voices, voices.size), icon = Icons.Rounded.GraphicEq)
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
            title = { Text(appText(R.string.rename_voice)) },
            text = {
                OutlinedTextField(value = renameText, onValueChange = { renameText = it }, singleLine = true,
                    label = { Text(appText(R.string.voice_name)) })
            },
            confirmButton = {
                TextButton(enabled = renameText.isNotBlank(), onClick = {
                    onRenameVoice?.invoke(id, renameText.trim())
                    renameId = null
                }) { Text(appText(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { renameId = null }) { Text(appText(R.string.cancel)) } }
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
                    if (voice.source == VoiceSource.RECORDED) appText(R.string.voice_source_recording) else if (voice.engine != VoiceEngine.ALIYUN) appText(R.string.voice_source_import) else appText(R.string.voice_source_upload),
                    "${voice.sampleSeconds}s",
                    when {
                        voice.engine != VoiceEngine.ALIYUN -> if (hasLocalSample) appText(R.string.local_voice) else appText(R.string.local_voice_missing)
                        voice.speakerId.isNullOrBlank() -> appText(R.string.cloud_voice_id_invalid)
                        voice.status == "DEPLOYING" -> appText(R.string.cloud_voice_pending)
                        voice.status == "UNDEPLOYED" -> appText(R.string.cloud_voice_rejected)
                        else -> appText(R.string.voice_ready)
                    }
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        onRename?.let {
            IconButton(onClick = it) { Icon(Icons.Rounded.Edit, contentDescription = appText(R.string.rename_voice)) }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Rounded.Delete, contentDescription = appText(R.string.delete), tint = TechPink)
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
            error = appText(R.string.recording_start_error, e.message ?: appText(R.string.microphone_check))
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
            error = appText(R.string.recording_invalid, e.message ?: appText(R.string.recording_min_duration))
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) begin() else error = appText(R.string.recording_permission_denied)
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
            Text(appText(R.string.recording_title), style = gradientTitleStyle())
            Spacer(Modifier.height(6.dp))
            Text(appText(R.string.recording_instructions), style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it, color = TechPink, style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(18.dp))
            VoiceWave(active = isRecording, modifier = Modifier.height(48.dp))
            Text(formatSeconds(seconds), style = MaterialTheme.typography.titleLarge, color = TechCyan)
            Spacer(Modifier.height(20.dp))
            val ready = validatedSeconds
            if (ready != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { recordFile?.let { preview.playFile(it, onError = { error = it }) } }) { Text(appText(R.string.preview_audio)) }
                    TextButton(onClick = { discard() }) { Text(appText(R.string.clear_recording)) }
                    TextButton(onClick = { begin() }) { Text(appText(R.string.record_again)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = onDismiss) { Text(appText(R.string.cancel)) }
                    NeonButton(text = appText(R.string.use_recording), onClick = {
                        val file = recordFile ?: return@NeonButton
                        preview.stop()
                        accepted = true
                        onConfirm(file, ready)
                    })
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GlassOutlineButton(text = if (isRecording) appText(R.string.stop) else appText(R.string.start_recording), onClick = {
                        if (isRecording) finish()
                        else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) begin()
                        else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    })
                    if (isRecording) TextButton(onClick = { begin() }) { Text(appText(R.string.record_again)) }
                    TextButton(onClick = onDismiss) { Text(appText(R.string.cancel)) }
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
                    VoiceProfile(name = appText(R.string.sample_voice_name), language = "zh", source = VoiceSource.RECORDED, sampleSeconds = 12)
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
