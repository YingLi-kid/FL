package com.example.myapplication.data

import java.util.UUID

/*
 * FL音坊 · 数据模型（纯 Kotlin，无 Compose 依赖）。
 *
 * 阿里云百炼 CosyVoice 接入后：
 *   - VoiceOption.speaker 为 CosyVoice 音色 ID（预设或复刻 voice_id）；
 *   - VoiceProfile.speakerId 为声音复刻返回的 voice_id。
 */

// 支持的语言（克隆样本语言 + TTS 目标语言共用）
data class LanguageOption(val code: String, val label: String)

val supportedLanguages = listOf(
    LanguageOption("zh", "中文"),
    LanguageOption("en", "英语"),
    LanguageOption("ja", "日语"),
    LanguageOption("ko", "韩语"),
    LanguageOption("fr", "法语"),
    LanguageOption("de", "德语"),
    LanguageOption("es", "西班牙语"),
    LanguageOption("ru", "俄语"),
    LanguageOption("yue", "粤语")
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

// 一个已克隆的音色档案
data class VoiceProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val language: String,
    val source: VoiceSource,
    val sampleSeconds: Int,
    val speakerId: String? = null,          // 阿里云声音复刻返回的 voice_id
    val createdAt: Long = System.currentTimeMillis(),
    val status: String = "OK"
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
val presetVoices = listOf(
    VoiceOption("preset_zh_female", "龙小淳", "中文女声 · 知性自然", false, "longxiaochun_v3"),
    VoiceOption("preset_zh_male", "龙安洋", "中文男声 · 阳光清爽", false, "longanyang")
)

// 一次文字转语音的生成结果
data class TtsResult(
    val text: String,
    val voiceName: String,
    val language: String,
    val durationSec: Int,
    val createdAt: Long = System.currentTimeMillis()
)
