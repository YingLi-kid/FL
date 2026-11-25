package com.example.myapplication.ui

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
import com.example.myapplication.data.TtsResult
import com.example.myapplication.data.VoiceOption
import com.example.myapplication.data.VoiceProfile
import com.example.myapplication.data.languageLabel
import com.example.myapplication.service.SPEECH_TEXT_LIMIT
import com.example.myapplication.service.speechCharacterCount
import com.example.myapplication.service.limitSpeechText
import com.example.myapplication.data.SpeechHistory
import com.example.myapplication.service.SpeechBackend
import com.example.myapplication.service.LocalSpeechBackend
import com.example.myapplication.service.CosyVoiceBackend
import com.example.myapplication.service.SpeechParameter
import com.example.myapplication.service.SpeechParameters
import kotlinx.coroutines.NonCancellable
import com.example.myapplication.service.AudioPlayer
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.example.myapplication.ui.theme.TechCyan
import kotlinx.coroutines.delay

/**
 * 文字转语音页：选音色 → 语言 → 文本 → 参数 → 生成 → 播放。
 * 已配置阿里云服务商时走真实 TTS（CosyVoice 合成），未配置服务商时提示完成配置。
 */
@Composable
fun TtsScreen(
    voices: List<VoiceProfile>,
    preselectedVoiceId: String?,
    onPreselectedConsumed: () -> Unit,
    onShowMessage: (String) -> Unit,
    providerApiKey: String?,
    backend: SpeechBackend = CosyVoiceBackend,
    credentials: Map<String, String> = providerApiKey?.let { mapOf("apiKey" to it) }.orEmpty(),
    onConfigureCloud: (() -> Unit)? = null,
    onCreateVoice: () -> Unit = {}
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

    var isGenerating by remember { mutableStateOf(false) }
    var generateProgress by remember { mutableFloatStateOf(0f) }
    var currentSegment by remember { mutableIntStateOf(0) }
    var totalSegments by remember { mutableIntStateOf(0) }
    var audioPath by rememberSaveable { mutableStateOf<String?>(null) }
    var resultVoiceName by rememberSaveable { mutableStateOf<String?>(null) }
    var resultLanguage by rememberSaveable { mutableStateOf("zh") }
    var resultText by rememberSaveable { mutableStateOf("") }
    var resultDuration by rememberSaveable { mutableIntStateOf(0) }
    val lastAudio = audioPath?.let(::File)?.takeIf { it.isFile }
    val result = if (resultVoiceName != null && audioPath?.let { File(it).isFile } == true)
        TtsResult(resultText, resultVoiceName!!, resultLanguage, resultDuration) else null

    var isPlaying by remember { mutableStateOf(false) }
    var playMs by remember { mutableIntStateOf(0) }
    var audioDurationMs by rememberSaveable { mutableIntStateOf(0) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val audioPlayer = remember { AudioPlayer(context.applicationContext) }
    val history = remember(context) { SpeechHistory(context.applicationContext) }

    // 下载：弹出系统保存框，让用户选择保存位置
    val downloadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(backend.format.mime)
    ) { uri ->
        val audio = lastAudio
        if (uri != null && audio != null) {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        requireNotNull(context.contentResolver.openOutputStream(uri)) { "无法打开保存位置" }.use { sink ->
                            audio.inputStream().use { it.copyTo(sink) }
                        }
                    }
                    onShowMessage("已保存到所选位置")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    onShowMessage("保存失败：${e.message ?: "未知错误"}")
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
                description = "${languageLabel(v.language)} · ${if (local) "本地音色" else "克隆音色"}",
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

    LaunchedEffect(isGenerating) {
        if (!isGenerating) return@LaunchedEffect
        try {
            generateProgress = 0.25f
            currentSegment = 0
            totalSegments = 0
            val voice = requireNotNull(currentVoice) { "请选择可用音色。" }
            val requestText = text.trim()
            val requestLanguage = selectedLanguage
            val temporary = backend.synthesize(
                context, voice, requestText, requestLanguage,
                SpeechParameters(speed, volume, pitch), credentials
            ) { segment, total ->
                withContext(Dispatchers.Main.immediate) {
                    currentSegment = segment
                    totalSegments = total
                }
            }
            val record = withContext(NonCancellable) {
                try {
                    history.save(temporary, requestText, voice.name, requestLanguage, backend.name, backend.format)
                } finally {
                    withContext(Dispatchers.IO) { temporary.delete() }
                }
            }
            audioPath = record.audio.absolutePath
            val playedDuration = audioPlayer.playFile(
                file = record.audio,
                onCompletion = { playMs = audioPlayer.duration(); isPlaying = false },
                onError = onShowMessage
            )
            audioDurationMs = if (playedDuration > 0) playedDuration else record.durationMs
            resultText = requestText
            resultVoiceName = voice.name
            resultLanguage = requestLanguage
            resultDuration = (audioDurationMs / 1000).coerceAtLeast(1)
            isPlaying = audioPlayer.isPlaying()
            playMs = 0
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onShowMessage("合成失败：${e.message ?: "未知错误"}")
        } finally {
            isGenerating = false
        }
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
                Text("还没有可用的本地音色。")
                androidx.compose.material3.OutlinedButton(onClick = onCreateVoice) { Text("创建本地音色") }
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
                        Text("选择音色", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(2.dp))
                        Text(currentVoice?.name ?: "", style = MaterialTheme.typography.titleMedium)
                        if (currentVoice?.isCloned == true) {
                            Text("已克隆音色", style = MaterialTheme.typography.bodySmall, color = TechCyan)
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
            SectionTitle("目标语言", icon = Icons.Rounded.Translate)
            Spacer(Modifier.height(12.dp))
            LanguageSelector(
                selected = selectedLanguage,
                onSelect = { if (!isGenerating) selectedLanguage = it },
                languages = backend.outputLanguages
            )
        }

        // 文本输入
        GlassCard {
            SectionTitle("合成文本", icon = Icons.Rounded.RecordVoiceOver)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = limitSpeechText(it) },
                enabled = !isGenerating,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                placeholder = { Text("输入要合成语音的文字…") },
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
            SectionTitle("发音参数", icon = Icons.Rounded.Tune)
            Spacer(Modifier.height(12.dp))
            if (SpeechParameter.RATE in backend.parameters) ParamSlider("语速", speed, 0.5f, 2.0f, "x") { speed = it }
            if (SpeechParameter.VOLUME in backend.parameters) ParamSlider("音量", volume, 0f, 1f, "%") { volume = it }
            if (SpeechParameter.PITCH in backend.parameters) ParamSlider("音调", pitch, -12f, 12f, "") { pitch = it }
        }

        NeonButton(
            text = when {
                isGenerating -> "生成中…"
                local -> "离线生成"
                needsKey && onConfigureCloud != null -> "配置 API Key"
                else -> "云端生成"
            },
            onClick = {
                when {
                    needsKey && onConfigureCloud != null -> onConfigureCloud()
                    needsKey -> onShowMessage("请先配置所选服务商的 API Key")
                    else -> isGenerating = true
                }
            },
            enabled = !isGenerating && (needsKey || (currentVoice != null && text.isNotBlank())),
            modifier = Modifier.fillMaxWidth()
        )

        // 生成进度
        if (isGenerating) {
            GlassCard {
                Text(
                    when {
                        !local -> "正在连接阿里云合成…"
                        currentSegment == 0 -> "正在加载千问模型…"
                        totalSegments > 1 -> "正在离线生成第 $currentSegment / $totalSegments 段"
                        else -> "正在手机上生成…"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(10.dp))
                if (local && totalSegments <= 1) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(
                    progress = { if (local) (currentSegment - 1).toFloat() / totalSegments else generateProgress },
                    modifier = Modifier.fillMaxWidth(),
                    color = TechCyan,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            }
        }

        // 生成结果播放条
        if (result != null) {
            val r = result!!
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            if (audioPlayer.duration() > 0) {
                                isPlaying = audioPlayer.togglePauseResume()
                                playMs = audioPlayer.currentPosition()
                            } else if (lastAudio != null) {
                                audioDurationMs = audioPlayer.playFile(
                                    lastAudio,
                                    onCompletion = { playMs = audioPlayer.duration(); isPlaying = false },
                                    onError = { onShowMessage(it) }
                                )
                                playMs = 0
                                isPlaying = audioPlayer.isPlaying()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "暂停" else "播放",
                            tint = TechCyan
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "已生成 · ${r.voiceName} · ${languageLabel(r.language)}",
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
                            if (lastAudio != null) {
                                downloadLauncher.launch("FL音坊_${System.currentTimeMillis()}.$extension")
                            }
                        },
                        enabled = lastAudio != null
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = if (local) "导出 WAV" else "下载", tint = TechCyan)
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
                    VoiceProfile(name = "我的声音", language = "zh", source = com.example.myapplication.data.VoiceSource.RECORDED, sampleSeconds = 12)
                ),
                preselectedVoiceId = null,
                onPreselectedConsumed = {},
                onShowMessage = {},
                providerApiKey = null
            )
        }
    }
}
