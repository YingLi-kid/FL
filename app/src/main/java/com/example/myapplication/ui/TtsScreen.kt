package com.example.myapplication.ui

import android.util.Log
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
import androidx.compose.foundation.clickable
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
import com.example.myapplication.data.presetVoices
import com.example.myapplication.service.AliyunVoice
import com.example.myapplication.service.AudioPlayer
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.example.myapplication.ui.theme.TechCyan
import kotlin.math.pow
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
    providerApiKey: String?
) {
    var selectedVoiceId by rememberSaveable { mutableStateOf(presetVoices.first().id) }
    var voiceMenuExpanded by remember { mutableStateOf(false) }
    var selectedLanguage by rememberSaveable { mutableStateOf("zh") }
    var text by rememberSaveable { mutableStateOf("") }
    var speed by rememberSaveable { mutableFloatStateOf(1.0f) }
    var volume by rememberSaveable { mutableFloatStateOf(1.0f) }
    var pitch by rememberSaveable { mutableFloatStateOf(0f) }

    var isGenerating by remember { mutableStateOf(false) }
    var generateProgress by remember { mutableFloatStateOf(0f) }
    var audioPath by rememberSaveable { mutableStateOf<String?>(null) }
    var resultVoiceName by rememberSaveable { mutableStateOf<String?>(null) }
    var resultLanguage by rememberSaveable { mutableStateOf("zh") }
    var resultText by rememberSaveable { mutableStateOf("") }
    var resultDuration by rememberSaveable { mutableIntStateOf(0) }
    val lastAudio = remember(audioPath) {
        audioPath?.let { path -> runCatching { File(path).readBytes() }.getOrNull() }
    }
    val result = if (resultVoiceName != null && audioPath?.let { File(it).isFile } == true)
        TtsResult(resultText, resultVoiceName!!, resultLanguage, resultDuration) else null

    var isPlaying by remember { mutableStateOf(false) }
    var playMs by remember { mutableIntStateOf(0) }
    var audioDurationMs by rememberSaveable { mutableIntStateOf(0) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val audioPlayer = remember { AudioPlayer(context.applicationContext) }

    // 下载：弹出系统保存框，让用户选择保存位置
    val downloadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("audio/mpeg")
    ) { uri ->
        val audio = lastAudio
        if (uri != null && audio != null) {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        requireNotNull(context.contentResolver.openOutputStream(uri)) { "无法打开保存位置" }.use { it.write(audio) }
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
    val voiceOptions = remember(voices.toList()) {
        presetVoices + voices.filter { it.status == "OK" && !it.speakerId.isNullOrBlank() }.map { v ->
            VoiceOption(
                id = v.id,
                name = v.name,
                description = "${languageLabel(v.language)} · 克隆音色",
                isCloned = true,
                speaker = v.speakerId ?: ""
            )
        }
    }
    val currentVoice = voiceOptions.firstOrNull { it.id == selectedVoiceId } ?: voiceOptions.first()
    LaunchedEffect(voiceOptions, selectedVoiceId) {
        if (voiceOptions.none { it.id == selectedVoiceId }) selectedVoiceId = voiceOptions.first().id
    }

    // 从克隆页跳转过来的预选音色
    LaunchedEffect(preselectedVoiceId) {
        if (preselectedVoiceId != null) {
            selectedVoiceId = preselectedVoiceId
            onPreselectedConsumed()
        }
    }

    // Only report success when the provider has returned playable audio.
    LaunchedEffect(isGenerating) {
        if (isGenerating) {
            try {
                generateProgress = 0f
                val voice = currentVoice
                val apiKey = providerApiKey
                val requestText = text
                val requestLanguage = selectedLanguage
                Log.d("TtsScreen", "generate start, apiKey=${if (apiKey.isNullOrBlank()) "NULL" else "SET"}, voice=${voice.name}, isCloned=${voice.isCloned}")

                if (!apiKey.isNullOrBlank()) {
                    val speaker = voice.speaker

                    if (speaker.isBlank()) {
                        onShowMessage("该音色缺少服务商音色 ID，请重新克隆")
                        isGenerating = false
                        return@LaunchedEffect
                    }

                    generateProgress = 0.25f
                    val r = AliyunVoice().synthesize(
                        apiKey = apiKey,
                        voice = speaker,
                        text = requestText,
                        format = "mp3",
                        sampleRate = 24000,
                        volume = (volume * 100).toInt().coerceIn(0, 100),
                        rate = speed.coerceIn(0.5f, 2.0f),
                        pitch = 2.0.pow(pitch.toDouble() / 12.0).toFloat().coerceIn(0.5f, 2.0f)
                    )
                    Log.d("TtsScreen", "synthesize result=${r::class.simpleName} ${if (r is AliyunVoice.Result.Failure) r.message else ""}")
                    when (r) {
                        is AliyunVoice.Result.Success -> {
                            generateProgress = 1f
                            require(r.audio.isNotEmpty()) { "服务商返回了空音频" }
                            val saved = withContext(Dispatchers.IO) {
                                File.createTempFile("result_", ".mp3", context.cacheDir).apply { writeBytes(r.audio) }
                            }
                            audioPath?.let { File(it).delete() }
                            audioPath = saved.absolutePath
                            audioDurationMs = audioPlayer.play(
                                audio = r.audio,
                                onCompletion = { playMs = audioPlayer.duration(); isPlaying = false },
                                onError = { onShowMessage(it) }
                            )
                            resultText = requestText
                            resultVoiceName = voice.name
                            resultLanguage = requestLanguage
                            resultDuration = (audioDurationMs / 1000).coerceAtLeast(1)
                            isPlaying = audioPlayer.isPlaying()
                            playMs = 0
                        }
                        is AliyunVoice.Result.Failure -> {
                            onShowMessage("合成失败：${r.message}")
                        }
                    }
                } else {
                    onShowMessage(if (apiKey.isNullOrBlank()) "请先在设置 → 服务商管理中配置 API Key 并启用阿里云" else "请选择可用音色")
                }
                isGenerating = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onShowMessage("合成失败：${e.message ?: "未知错误"}")
                isGenerating = false
            }
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
        // 音色选择
        Box {
            GlassCard(onClick = { voiceMenuExpanded = true }) {
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
                onSelect = { selectedLanguage = it }
            )
        }

        // 文本输入
        GlassCard {
            SectionTitle("合成文本", icon = Icons.Rounded.RecordVoiceOver)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= 500) text = it },
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
                "${text.length} / 500",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.End)
            )
        }

        // 参数调节
        GlassCard {
            SectionTitle("发音参数", icon = Icons.Rounded.Tune)
            Spacer(Modifier.height(12.dp))
            ParamSlider("语速", speed, 0.5f, 2.0f, "x") { speed = it }
            ParamSlider("音量", volume, 0f, 1f, "%") { volume = it }
            ParamSlider("音调", pitch, -12f, 12f, "") { pitch = it }
        }

        // 生成按钮
        NeonButton(
            text = if (isGenerating) "生成中…" else "生成语音",
            onClick = {
                if (text.isBlank()) {
                    onShowMessage("请先输入要合成的文字")
                } else if (providerApiKey.isNullOrBlank()) {
                    onShowMessage("请先在设置 → 服务商管理中配置 API Key 并启用阿里云")
                } else {
                    isGenerating = true
                }
            },
            enabled = !isGenerating,
            modifier = Modifier.fillMaxWidth()
        )

        // 生成进度
        if (isGenerating) {
            GlassCard {
                Text(
                    if (providerApiKey.isNullOrBlank()) "正在合成语音…" else "正在连接语音服务合成…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { generateProgress },
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
                                audioDurationMs = audioPlayer.play(
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
                                downloadLauncher.launch("音色工坊_${System.currentTimeMillis()}.mp3")
                            }
                        },
                        enabled = lastAudio != null
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = "下载", tint = TechCyan)
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
