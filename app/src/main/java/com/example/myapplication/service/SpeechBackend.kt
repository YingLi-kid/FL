package com.example.myapplication.service

import com.example.myapplication.R
import com.example.myapplication.appText
import com.example.myapplication.originalChineseText

import android.content.Context
import android.util.Base64
import com.example.myapplication.BuildConfig
import com.example.myapplication.data.LanguageOption
import com.example.myapplication.data.ProviderCapability
import com.example.myapplication.data.ServiceProvider
import com.example.myapplication.data.VoiceEngine
import com.example.myapplication.data.VoiceOption
import com.example.myapplication.data.VoiceProfile
import com.example.myapplication.data.cosyVoiceLanguageHint
import com.example.myapplication.data.presetVoices
import com.example.myapplication.data.serviceProviders
import com.example.myapplication.data.supportedLanguages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.pow

enum class SpeechParameter { RATE, VOLUME, PITCH }

data class SpeechParameters(val rate: Float = 1f, val volume: Float = 1f, val pitch: Float = 0f)

enum class SpeechFormat(val extension: String, val mime: String) {
    WAV("wav", "audio/wav"),
    MP3("mp3", "audio/mpeg")
}

/** The UI asks the selected backend about capabilities and passes only that backend's credentials. */
interface SpeechBackend {
    val id: String
    val name: String
    val engine: VoiceEngine
    val sampleLanguages: List<LanguageOption>
    val outputLanguages: List<LanguageOption>
    val parameters: Set<SpeechParameter>
    val format: SpeechFormat
    val presets: List<VoiceOption> get() = emptyList()
    val supportsClone: Boolean get() = true
    val requiresReferenceText: Boolean get() = false
    fun credentialsReady(credentials: Map<String, String>): Boolean
    fun acceptsVoice(profile: VoiceProfile): Boolean = profile.backendId == id
    fun isVoiceUsable(context: Context, profile: VoiceProfile): Boolean

    suspend fun createVoice(
        context: Context,
        profile: VoiceProfile,
        sample: File,
        mimeType: String,
        credentials: Map<String, String>,
        referenceText: String?,
        persist: suspend (VoiceProfile) -> Unit
    )

    suspend fun synthesize(
        context: Context,
        voice: VoiceOption,
        text: String,
        language: String,
        parameters: SpeechParameters,
        credentials: Map<String, String>,
        progress: suspend (Int, Int) -> Unit
    ): File
}

interface LocalSpeechBackend : SpeechBackend {
    val modelNotice: String? get() = null
    fun modelPackage(context: Context): LocalModelPackage
    fun isInstalled(context: Context): Boolean = modelPackage(context).isInstalled()
    fun isSupported(): Boolean
    fun deleteReference(context: Context, voiceId: String)
}

interface CloudSpeechBackend : SpeechBackend {
    val provider: ServiceProvider
    suspend fun refreshStatus(credentials: Map<String, String>, speakerId: String): String?
}

internal val qwenLanguageIds = linkedMapOf(
    "zh" to 2055, "en" to 2050, "ja" to 2058, "ko" to 2064, "fr" to 2061,
    "de" to 2053, "es" to 2054, "ru" to 2069, "it" to 2070, "pt" to 2071
)

internal class QwenBackend(private val model: QwenModel) : LocalSpeechBackend {
    override val id = model.files.id
    override val name get() = model.name
    override val engine = VoiceEngine.QWEN
    override val outputLanguages = supportedLanguages.filter { it.code in qwenLanguageIds }
    override val sampleLanguages = outputLanguages
    override val parameters = setOf(SpeechParameter.VOLUME)
    override val format = SpeechFormat.WAV
    override fun credentialsReady(credentials: Map<String, String>) = true
    override fun modelPackage(context: Context): LocalModelPackage = VerifiedModelStore(context, model.files)
    override fun isSupported() = supportsQwenTrial()
    override fun acceptsVoice(profile: VoiceProfile) = qwenModels.any { it.files.id == profile.backendId }
    override fun isVoiceUsable(context: Context, profile: VoiceProfile) =
        acceptsVoice(profile) && runCatching { qwenReferenceFile(context, profile.id).isFile }.getOrDefault(false)

    override suspend fun createVoice(
        context: Context, profile: VoiceProfile, sample: File, mimeType: String,
        credentials: Map<String, String>, referenceText: String?, persist: suspend (VoiceProfile) -> Unit
    ) = withContext(Dispatchers.IO) {
        val saved = saveQwenReference(context, profile, sample)
        try {
            persist(profile)
        } catch (e: Exception) {
            saved.delete()
            throw e
        }
    }

    override suspend fun synthesize(
        context: Context, voice: VoiceOption, text: String, language: String,
        parameters: SpeechParameters, credentials: Map<String, String>, progress: suspend (Int, Int) -> Unit
    ) = generateQwenSpeech(context, model, qwenReferenceFile(context, voice.id), text, language, parameters.volume, progress)

    override fun deleteReference(context: Context, voiceId: String) {
        qwenReferenceFile(context, voiceId).delete()
    }
}

object CosyVoiceBackend : CloudSpeechBackend {
    override val provider = serviceProviders.first { it.id == "aliyun" }
    override val id = provider.id
    override val name get() = appText(R.string.provider_aliyun_backend)
    override val engine = VoiceEngine.ALIYUN
    override val sampleLanguages = supportedLanguages.filter { it.code !in setOf("it", "pt") }
    override val outputLanguages = sampleLanguages
    override val parameters = SpeechParameter.entries.toSet()
    override val format = SpeechFormat.MP3
    override val presets get() = presetVoices
    override val supportsClone = ProviderCapability.VOICE_CLONE in provider.capabilities

    override fun credentialsReady(credentials: Map<String, String>) =
        provider.credentialFields.all { !credentials[it.key].isNullOrBlank() }

    override fun isVoiceUsable(context: Context, profile: VoiceProfile) =
        profile.backendId == id && !profile.speakerId.isNullOrBlank()

    override suspend fun createVoice(
        context: Context, profile: VoiceProfile, sample: File, mimeType: String,
        credentials: Map<String, String>, referenceText: String?, persist: suspend (VoiceProfile) -> Unit
    ) {
        val (duration, encoded) = withContext(Dispatchers.IO) {
            val duration = (AudioSamples.durationMs(sample) / 1000).toInt()
            duration to Base64.encodeToString(sample.readBytes(), Base64.NO_WRAP)
        }
        val result = AliyunVoice().cloneVoice(
            apiKey = apiKey(credentials),
            audioBase64 = encoded,
            mimeType = mimeType,
            prefix = "vc" + profile.id.filter { it.isLetterOrDigit() }.take(8),
            languageHint = cosyVoiceLanguageHint(profile.language)
        )
        when (result) {
            is AliyunVoice.Result.Success -> persist(profile.copy(
                sampleSeconds = duration,
                speakerId = result.voiceId,
                status = AliyunVoice.STATUS_DEPLOYING
            ))
            is AliyunVoice.Result.Failure -> error(result.message)
        }
    }

    override suspend fun synthesize(
        context: Context, voice: VoiceOption, text: String, language: String,
        parameters: SpeechParameters, credentials: Map<String, String>, progress: suspend (Int, Int) -> Unit
    ): File = when (val result = AliyunVoice().synthesize(
        apiKey = apiKey(credentials), voice = voice.speaker, text = text, format = format.extension,
        sampleRate = 24000, volume = (parameters.volume * 100).toInt().coerceIn(0, 100),
        rate = parameters.rate.coerceIn(0.5f, 2f),
        pitch = 2.0.pow(parameters.pitch.toDouble() / 12).toFloat().coerceIn(0.5f, 2f)
    )) {
        is AliyunVoice.Result.Success -> withContext(Dispatchers.IO) {
            check(result.audio.isNotEmpty()) { appText(R.string.provider_empty_audio) }
            val file = File.createTempFile("result_", ".mp3", context.cacheDir)
            try {
                file.writeBytes(result.audio)
                file
            } catch (e: Exception) {
                file.delete()
                throw e
            }
        }
        is AliyunVoice.Result.Failure -> error(result.message)
    }

    override suspend fun refreshStatus(credentials: Map<String, String>, speakerId: String): String? =
        when (val result = AliyunVoice().getVoiceStatus(apiKey(credentials), speakerId)) {
            is AliyunVoice.Result.Success -> result.status
            is AliyunVoice.Result.Failure -> throw java.io.IOException(result.message)
        }

    private fun apiKey(credentials: Map<String, String>): String =
        requireNotNull(credentials["apiKey"]?.trim()?.takeIf { it.isNotEmpty() }) { appText(R.string.api_key_required) }
}

val localSpeechBackends: List<LocalSpeechBackend> = buildList {
    if (BuildConfig.QWEN_PROTOTYPE) addAll(qwenModels.map(::QwenBackend))
    if (BuildConfig.OMNIVOICE_ENABLED) add(OmniVoiceBackend)
    if (BuildConfig.AUDIOCPP_ENABLED) {
        add(indexTts25)
        add(cosyVoice3Local)
    }
}
val cloudSpeechBackends: List<CloudSpeechBackend> = listOf(CosyVoiceBackend)

private val backendNameResources = mapOf(
    "aliyun" to R.string.provider_aliyun_backend,
    qwen06b.files.id to R.string.model_qwen_small,
    qwen17b.files.id to R.string.model_qwen_large,
    OmniVoiceBackend.id to R.string.model_omnivoice,
    indexTts25.id to R.string.model_index_tts,
    cosyVoice3Local.id to R.string.model_cosyvoice_local
)

internal fun speechBackendLabel(idOrLegacyName: String): String {
    val resource = backendNameResources[idOrLegacyName]
        ?: backendNameResources.values.firstOrNull { originalChineseText(it) == idOrLegacyName }
    return resource?.let { appText(it) } ?: idOrLegacyName
}
