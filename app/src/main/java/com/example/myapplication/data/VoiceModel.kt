package com.example.myapplication.data

import androidx.annotation.StringRes
import com.example.myapplication.R
import com.example.myapplication.appText

import java.util.UUID

/*
 * FL音坊 · 数据模型（纯 Kotlin，无 Compose 依赖）。
 *
 * 阿里云百炼 CosyVoice 接入后：
 *   - VoiceOption.speaker 为 CosyVoice 音色 ID（预设或复刻 voice_id）；
 *   - VoiceProfile.speakerId 为声音复刻返回的 voice_id。
 */

// 支持的语言（克隆样本语言 + TTS 目标语言共用）
data class LanguageOption(val code: String, @StringRes private val labelRes: Int) {
    val label: String get() = appText(labelRes)
}

val supportedLanguages = listOf(
    LanguageOption("zh", R.string.language_chinese),
    LanguageOption("en", R.string.language_english),
    LanguageOption("ja", R.string.language_japanese),
    LanguageOption("ko", R.string.language_korean),
    LanguageOption("fr", R.string.language_french),
    LanguageOption("de", R.string.language_german),
    LanguageOption("es", R.string.language_spanish),
    LanguageOption("ru", R.string.language_russian),
    LanguageOption("yue", R.string.language_cantonese),
    LanguageOption("it", R.string.language_italian),
    LanguageOption("pt", R.string.language_portuguese)
)

fun languageLabel(code: String): String =
    supportedLanguages.firstOrNull { it.code == code }?.label ?: code

// 语言编码 → 阿里云 CosyVoice language_hints（不支持的返回 null，由系统自动检测语种）
fun cosyVoiceLanguageHint(code: String): String? = when (code) {
    "zh", "en", "ja", "ko", "fr", "de", "ru" -> code
    else -> null
}

// 克隆音色的样本来源
enum class VoiceSource { RECORDED, UPLOADED }

enum class VoiceEngine { ALIYUN, QWEN, OMNIVOICE, INDEX_TTS, COSYVOICE_LOCAL }

fun defaultBackendId(engine: VoiceEngine): String = when (engine) {
    VoiceEngine.ALIYUN -> "aliyun"
    VoiceEngine.QWEN -> "qwen3-tts-0.6b-q4km"
    VoiceEngine.OMNIVOICE -> "omnivoice-q8"
    VoiceEngine.INDEX_TTS -> "index-tts-2.5-q8"
    VoiceEngine.COSYVOICE_LOCAL -> "cosyvoice-3-q8"
}

// 一个已克隆的音色档案
data class VoiceProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val language: String,
    val source: VoiceSource,
    val sampleSeconds: Int,
    val speakerId: String? = null,          // 阿里云声音复刻返回的 voice_id
    val createdAt: Long = System.currentTimeMillis(),
    val status: String = "OK",
    val engine: VoiceEngine = VoiceEngine.ALIYUN,
    val backendId: String = defaultBackendId(engine)
)

// 文字转语音可选的音色：预设系统音色 + 用户克隆音色
data class VoiceOption(
    val id: String,
    val name: String,
    val description: String,
    val isCloned: Boolean,
    val speaker: String = ""                // CosyVoice 音色 ID（预设）或复刻 voice_id（克隆）
)

// 预设系统音色（阿里云 CosyVoice 音色）
val presetVoices get() = listOf(
    VoiceOption("preset_zh_female", appText(R.string.preset_xiaochun), appText(R.string.preset_xiaochun_description), false, "longxiaochun_v3"),
    VoiceOption("preset_zh_male", appText(R.string.preset_anyang), appText(R.string.preset_anyang_description), false, "longanyang")
)

// 一次文字转语音的生成结果
data class TtsResult(
    val text: String,
    val voiceName: String,
    val language: String,
    val durationSec: Int,
    val createdAt: Long = System.currentTimeMillis()
)
