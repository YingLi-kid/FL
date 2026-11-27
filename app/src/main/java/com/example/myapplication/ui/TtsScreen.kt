package com.example.myapplication.ui

import com.example.myapplication.R
import com.example.myapplication.appText

import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.example.myapplication.data.VoiceOption
import com.example.myapplication.data.VoiceProfile
import com.example.myapplication.data.languageLabel
import com.example.myapplication.service.SPEECH_TEXT_LIMIT
import com.example.myapplication.service.speechCharacterCount
import com.example.myapplication.service.limitSpeechText
import com.example.myapplication.service.SpeechBackend
import com.example.myapplication.service.LocalSpeechBackend
import com.example.myapplication.service.CosyVoiceBackend
import com.example.myapplication.service.SpeechParameter
import com.example.myapplication.service.SpeechParameters
import com.example.myapplication.service.AudioPlayer
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.example.myapplication.ui.theme.TechCyan
import kotlinx.coroutines.delay

/**
 * 文字转语音页：选音色 → 语言 → 文本 → 参数 → 生成 → 播放。
 * 已配置阿里云服务商时走真实 TTS（CosyVoice 合成），未配置服务商时提示完成配置。
 */
@Composable
internal fun TtsScreen(
    voices: List<VoiceProfile>,
    preselectedVoiceId: String?,
    onPreselectedConsumed: () -> Unit,
    onShowMessage: (String) -> Unit,
    providerApiKey: String?,
    backend: SpeechBackend = CosyVoiceBackend,
    credentials: Map<String, String> = providerApiKey?.let { mapOf("apiKey" to it) }.orEmpty(),
    onConfigureCloud: (() -> Unit)? = null,
    onCreateVoice: () -> Unit = {},
    generation: SpeechGeneration = rememberSpeechGeneration(),
    isVisible: Boolean = true
) {
    val local = backend is LocalSpeechBackend
    val extension = backend.format.extension
    val needsKey = !backend.credentialsReady(credentials)
    var selectedVoiceId by rememberSaveable { mutableStateOf(backend.presets.firstOrNull()?.id.orEmpty()) }
    var voiceMenuExpanded by remember { mutableStateOf(false) }
    var selectedLanguage by rememberSaveable { mutableStateOf(backend.outputLanguages.first().code) }
    var text by rememberSaveable { mutableStateOf("") }
    var speed by rememberSaveable { mutableFloatStateOf(1.0f) }
    var volume by rememberSaveable { mutableFloatStateOf(1.0f) }
    var pitch by rememberSaveable { mutableFloatStateOf(0f) }

    val generationState by generation.state.collectAsState()
    val progress = generationState.progress
    val isGenerating = progress != null
    val result = generationState.results[backend.id]?.takeIf { it.audio.isFile }
    var observedRecordId by remember { mutableStateOf(result?.id) }

    var isPlaying by remember { mutableStateOf(false) }
    var playMs by remember { mutableIntStateOf(0) }
    var audioDurationMs by remember { mutableIntStateOf(result?.durationMs ?: 0) }
    var exportPath by rememberSaveable { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val audioPlayer = remember { AudioPlayer(context.applicationContext) }

    // 下载：弹出系统保存框，让用户选择保存位置
    val downloadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(backend.format.mime)
    ) { uri ->
        val audio = exportPath?.let(::File)
        exportPath = null
        if (uri != null && audio != null) {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        requireNotNull(context.contentResolver.openOutputStream(uri)) { appText(R.string.save_location_error) }.use { sink ->
                            audio.inputStream().use { it.copyTo(sink) }
                        }
                    }
                    onShowMessage(appText(R.string.save_location_success))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    onShowMessage(appText(R.string.save_audio_error, e.message ?: appText(R.string.unknown_error)))
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { audioPlayer.stop() }
    }

    // 可选音色 = 预设 + 已克隆
    val voiceOptions = remember(voices.toList(), backend, context) {
        val profiles = voices.filter { it.status == "OK" && backend.isVoiceUsable(context, it) }
        backend.presets + profiles.map { v ->
            VoiceOption(
                id = v.id,
                name = v.name,
                description = "${languageLabel(v.language)} · ${if (local) appText(R.string.local_voice) else appText(R.string.clone_tab)}",
                isCloned = true,
                speaker = v.speakerId ?: ""
            )
        }
    }
    val currentVoice = voiceOptions.firstOrNull { it.id == selectedVoiceId } ?: voiceOptions.firstOrNull()
    LaunchedEffect(voiceOptions, selectedVoiceId) {
        if (voiceOptions.none { it.id == selectedVoiceId }) selectedVoiceId = voiceOptions.firstOrNull()?.id.orEmpty()
    }

    // 从克隆页跳转过来的预选音色
    LaunchedEffect(preselectedVoiceId) {
        if (preselectedVoiceId != null) {
            selectedVoiceId = preselectedVoiceId
            if (local) voices.firstOrNull { it.id == preselectedVoiceId }?.let { profile ->
                if (backend.outputLanguages.any { it.code == profile.language }) selectedLanguage = profile.language
            }
            onPreselectedConsumed()
        }
    }

    fun playResult(file: File, durationMs: Int) {
        val playedDuration = audioPlayer.playFile(
            file,
            onCompletion = { playMs = audioPlayer.duration(); isPlaying = false },
            onError = onShowMessage
        )
        audioDurationMs = if (playedDuration > 0) playedDuration else durationMs
        isPlaying = audioPlayer.isPlaying()
        playMs = 0
    }

    LaunchedEffect(result?.id) {
        if (observedRecordId == result?.id) return@LaunchedEffect
        observedRecordId = result?.id
        audioPlayer.stop()
        isPlaying = false
        playMs = 0
        audioDurationMs = result?.durationMs ?: 0
        if (isVisible && result != null) playResult(result.audio, result.durationMs)
    }

    // 播放进度轮询
    LaunchedEffect(isPlaying, audioDurationMs) {
        if (isPlaying && audioDurationMs > 0) {
            while (audioPlayer.isPlaying()) {
                playMs = audioPlayer.currentPosition()
                delay(200)
            }
            playMs = audioPlayer.currentPosition()
            isPlaying = audioPlayer.isPlaying()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (local && voiceOptions.isEmpty()) {
            GlassCard {
                Text(appText(R.string.no_local_voices))
                androidx.compose.material3.OutlinedButton(onClick = onCreateVoice) { Text(appText(R.string.clone_local_title)) }
            }
        }

        // 音色选择
        if (voiceOptions.isNotEmpty()) Box {
            GlassCard(onClick = { if (!isGenerating) voiceMenuExpanded = true }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(TechCyan.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.RecordVoiceOver, contentDescription = null, tint = TechCyan)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(appText(R.string.choose_voice), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(2.dp))
                        Text(currentVoice?.name ?: "", style = MaterialTheme.typography.titleMedium)
                        if (currentVoice?.isCloned == true) {
                            Text(appText(R.string.cloned_voices), style = MaterialTheme.typography.bodySmall, color = TechCyan)
                        }
                    }
                    Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            DropdownMenu(
                expanded = voiceMenuExpanded,
                onDismissRequest = { voiceMenuExpanded = false }
            ) {
                voiceOptions.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(option.name)
                                Text(
                                    option.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        onClick = {
                            selectedVoiceId = option.id
                            if (local) voices.firstOrNull { it.id == option.id }?.let { profile ->
                                if (backend.outputLanguages.any { it.code == profile.language }) selectedLanguage = profile.language
                            }
                            voiceMenuExpanded = false
                        }
                    )
                }
            }
        }

        // 目标语言
        GlassCard {
            SectionTitle(appText(R.string.output_language), icon = Icons.Rounded.Translate)
            Spacer(Modifier.height(12.dp))
            LanguageSelector(
                selected = selectedLanguage,
                onSelect = { if (!isGenerating) selectedLanguage = it },
                languages = backend.outputLanguages
            )
        }

        // 文本输入
        GlassCard {
            SectionTitle(appText(R.string.synthesis_text), icon = Icons.Rounded.RecordVoiceOver)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = limitSpeechText(it) },
                enabled = !isGenerating,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                placeholder = { Text(appText(R.string.synthesis_text_placeholder)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TechCyan,
                    focusedLabelColor = TechCyan,
                    cursorColor = TechCyan,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                )
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "${speechCharacterCount(text)} / $SPEECH_TEXT_LIMIT",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.End)
            )
        }

        // 参数调节
        if (backend.parameters.isNotEmpty()) GlassCard {
            SectionTitle(appText(R.string.speech_controls), icon = Icons.Rounded.Tune)
            Spacer(Modifier.height(12.dp))
            if (SpeechParameter.RATE in backend.parameters) ParamSlider(appText(R.string.speech_rate), speed, 0.5f, 2.0f, "x") { speed = it }
            if (SpeechParameter.VOLUME in backend.parameters) ParamSlider(appText(R.string.speech_volume), volume, 0f, 1f, "%") { volume = it }
            if (SpeechParameter.PITCH in backend.parameters) ParamSlider(appText(R.string.speech_pitch), pitch, -12f, 12f, "") { pitch = it }
        }

        NeonButton(
            text = when {
                progress != null -> appText(R.string.generating)
                generationState.clearingCache -> appText(R.string.clearing_cache)
                local -> appText(R.string.generate_offline)
                needsKey && onConfigureCloud != null -> appText(R.string.configure_api_key)
                else -> appText(R.string.generate_cloud)
            },
            onClick = {
                when {
                    needsKey && onConfigureCloud != null -> onConfigureCloud()
                    needsKey -> onShowMessage(appText(R.string.provider_key_prompt))
                    currentVoice != null -> generation.start(
                        context, backend, currentVoice, text.trim(), selectedLanguage,
                        SpeechParameters(speed, volume, pitch), credentials
                    )
                }
            },
            enabled = progress == null && !generationState.clearingCache &&
                (needsKey || (currentVoice != null && text.isNotBlank())),
            modifier = Modifier.fillMaxWidth()
        )

        // 生成进度
        if (progress != null) {
            GlassCard {
                Text(
                    when {
                        !progress.offline -> appText(R.string.cloud_synthesis_progress, progress.backendName)
                        progress.segment == 0 -> appText(R.string.local_synthesis_preparing, progress.backendName)
                        progress.total > 1 -> appText(R.string.local_synthesis_segment, progress.segment, progress.total)
                        else -> appText(R.string.local_synthesis_progress, progress.backendName)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(10.dp))
                if (progress.total <= 1) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(
                    progress = { ((progress.segment - 1).toFloat() / progress.total).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = TechCyan,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            }
        }

        generationState.error?.takeIf { it.first == backend.id }?.let { (_, message) ->
            Text(appText(R.string.synthesis_error, message), color = MaterialTheme.colorScheme.error)
        }

        // 生成结果播放条
        if (result != null) {
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            if (audioPlayer.duration() > 0) {
                                isPlaying = audioPlayer.togglePauseResume()
                                playMs = audioPlayer.currentPosition()
                            } else {
                                playResult(result.audio, result.durationMs)
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) appText(R.string.pause) else appText(R.string.play),
                            tint = TechCyan
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            appText(R.string.synthesis_complete, result.voice, languageLabel(result.language)),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = {
                                if (audioDurationMs > 0) (playMs.toFloat() / audioDurationMs).coerceIn(0f, 1f) else 0f
                            },
                            modifier = Modifier.fillMaxWidth(),
                            color = TechCyan,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${formatSeconds(playMs / 1000)} / ${formatSeconds(audioDurationMs / 1000)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            exportPath = result.audio.absolutePath
                            downloadLauncher.launch(appText(R.string.tts_export_filename, System.currentTimeMillis(), extension))
                        }
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = if (local) appText(R.string.export_wav) else appText(R.string.download), tint = TechCyan)
                    }
                }
            }
        }
    }
}

@Composable
private fun ParamSlider(
    label: String,
    value: Float,
    rangeStart: Float,
    rangeEnd: Float,
    unit: String,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.weight(1f))
            Text(
                when (unit) {
                    "x" -> "${"%.1f".format(value)}x"
                    "%" -> "${(value * 100).toInt()}%"
                    else -> value.toInt().toString()
                },
                style = MaterialTheme.typography.labelMedium,
                color = TechCyan
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = rangeStart..rangeEnd,
            colors = SliderDefaults.colors(
                thumbColor = TechCyan,
                activeTrackColor = TechCyan,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
        )
    }
}

private fun formatSeconds(total: Int): String {
    val m = total / 60
    val s = total % 60
    return "%02d:%02d".format(m, s)
}

@Preview(showBackground = true)
@Composable
private fun TtsScreenPreview() {
    MyApplicationTheme {
        Box(modifier = Modifier.background(Color(0xFF080D1C))) {
            TtsScreen(
                voices = listOf(
                    VoiceProfile(name = appText(R.string.sample_voice_name), language = "zh", source = com.example.myapplication.data.VoiceSource.RECORDED, sampleSeconds = 12)
                ),
                preselectedVoiceId = null,
                onPreselectedConsumed = {},
                onShowMessage = {},
                providerApiKey = null
            )
        }
    }
}
