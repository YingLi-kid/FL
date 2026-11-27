package com.example.myapplication.service

import androidx.annotation.StringRes
import com.example.myapplication.R
import com.example.myapplication.appText

import android.content.Context
import android.os.Build
import androidx.annotation.Keep
import com.example.myapplication.data.VoiceEngine
import com.example.myapplication.data.VoiceOption
import com.example.myapplication.data.VoiceProfile
import com.example.myapplication.data.defaultBackendId
import com.example.myapplication.data.supportedLanguages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

private const val weightsBase = "https://huggingface.co/audio-cpp/audio.cpp-gguf/resolve/02f5de92a82339ebb722ea6fdadc14b7bec43a7f"

internal val indexTts25 = AudioCppVoiceBackend(
    VoiceEngine.INDEX_TTS, R.string.model_index_tts, "index_tts2", "IndexTTS2.5-GGUF",
    ModelAsset("index-tts2_5-q8_0.gguf", 3_502_960_096, "d1ab7b5c3f5ecf2bcd285753be76c7290b0a54939a76b7c39e8f886f80cf6b5c"),
    R.string.model_index_notice
)

internal val cosyVoice3Local = AudioCppVoiceBackend(
    VoiceEngine.COSYVOICE_LOCAL, R.string.model_cosyvoice_local, "cosyvoice3", "CosyVoice3-GGUF",
    ModelAsset("cosyvoice3-q8_0.gguf", 2_257_658_336, "d13bd731ead92d6d514d5749d9b59ea1b3ae735569fa46978250caaf5c983133"),
    R.string.model_cosyvoice_notice
)

internal class AudioCppVoiceBackend(
    override val engine: VoiceEngine,
    @StringRes private val nameRes: Int,
    private val family: String,
    sourceFolder: String,
    private val weights: ModelAsset,
    @StringRes private val modelNoticeRes: Int
) : LocalSpeechBackend {
    override val name: String get() = appText(nameRes)
    override val modelNotice: String get() = appText(modelNoticeRes)
    override val id = defaultBackendId(engine)
    private val spec = ModelPackageSpec(id, "$weightsBase/$sourceFolder", listOf(weights))
    override val sampleLanguages = supportedLanguages.filter { it.code in setOf("zh", "ja", "en") }
    override val outputLanguages = sampleLanguages
    override val parameters = setOf(SpeechParameter.RATE, SpeechParameter.VOLUME)
    override val format = SpeechFormat.WAV

    override fun isSupported() = Build.VERSION.SDK_INT >= 31 && Build.SUPPORTED_ABIS.firstOrNull() == "arm64-v8a"
    override fun credentialsReady(credentials: Map<String, String>) = true
    override fun modelPackage(context: Context): LocalModelPackage = VerifiedModelStore(context, spec)

    private fun reference(context: Context, voiceId: String): File =
        File(context.noBackupFilesDir, "$id-references/${UUID.fromString(voiceId)}.wav")

    override fun isVoiceUsable(context: Context, profile: VoiceProfile): Boolean =
        acceptsVoice(profile) && runCatching { reference(context, profile.id).isFile }.getOrDefault(false)

    override suspend fun createVoice(
        context: Context, profile: VoiceProfile, sample: File, mimeType: String,
        credentials: Map<String, String>, referenceText: String?, persist: suspend (VoiceProfile) -> Unit
    ) = withContext(Dispatchers.IO) {
        val saved = saveLocalReference(reference(context, profile.id), sample)
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
    ): File = VerifiedModelStore(context, spec).withInstalledModel { modelDir ->
        require(isSupported()) { appText(R.string.model_android_requirement) }
        require(text.isNotBlank() && speechCharacterCount(text) <= SPEECH_TEXT_LIMIT && '\u0000' !in text) {
            appText(R.string.synthesis_text_invalid, SPEECH_TEXT_LIMIT)
        }
        require(outputLanguages.any { it.code == language }) { appText(R.string.model_language_unsupported) }
        val sample = reference(context, voice.id)
        require(sample.isFile) { appText(R.string.reference_missing) }
        val audio = File.createTempFile("$id-", ".wav", context.cacheDir)
        var runtimeSpec: File? = null
        var complete = false
        try {
            val specFile = File.createTempFile("$id-", ".json", context.cacheDir)
            runtimeSpec = specFile
            context.assets.open("audiocpp-specs/$family.json").use { input ->
                specFile.outputStream().use { input.copyTo(it) }
            }
            progress(1, 1)
            val error = AudioCppNative.synthesize(
                family, specFile.absolutePath, modelDir.resolve(weights.name).absolutePath, sample.absolutePath, text.trim().encodeToByteArray(),
                language, audio.absolutePath, parameters.rate.coerceIn(0.5f, 2f), parameters.volume.coerceIn(0f, 1f),
                Runtime.getRuntime().availableProcessors().coerceIn(1, 6)
            )
            currentCoroutineContext().ensureActive()
            if (error != null || audio.length() <= 44) throw IOException(error ?: appText(R.string.model_no_audio))
            complete = true
            audio
        } catch (e: LinkageError) {
            throw IOException(appText(R.string.model_runtime_missing), e)
        } finally {
            runtimeSpec?.delete()
            if (!complete) audio.delete()
        }
    }

    override fun deleteReference(context: Context, voiceId: String) {
        reference(context, voiceId).delete()
    }
}

@Keep
internal object AudioCppNative {
    init { System.loadLibrary("fl_audiocpp_jni") }

    external fun synthesize(
        family: String, runtimeSpec: String, model: String, reference: String, text: ByteArray,
        language: String, destination: String, rate: Float, volume: Float, threads: Int
    ): String?
}
