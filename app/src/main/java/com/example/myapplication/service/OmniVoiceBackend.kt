package com.example.myapplication.service

import com.example.myapplication.R
import com.example.myapplication.appText

import android.content.Context
import android.os.Build
import androidx.annotation.Keep
import com.example.myapplication.data.VoiceEngine
import com.example.myapplication.data.VoiceOption
import com.example.myapplication.data.VoiceProfile
import com.example.myapplication.data.supportedLanguages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.function.BooleanSupplier

internal object OmniVoiceBackend : LocalSpeechBackend {
    private val weights = ModelAsset(
        "omnivoice-base-Q8_0.gguf", 656_395_008,
        "2882d887921798aea13d45236556bdf8012842ab6f8cd2690943eead6289f298"
    )
    private val codec = ModelAsset(
        "omnivoice-tokenizer-F32.gguf", 734_300_704,
        "83820c6316da023076af7c1d06de5e38dcd09ae9f42203675bf8b3bd9a58e330"
    )
    override val id = "omnivoice-q8"
    override val name get() = appText(R.string.model_omnivoice)
    private val spec = ModelPackageSpec(
        id, "https://huggingface.co/Serveurperso/OmniVoice-GGUF/resolve/361609388ae572a820d085185bbbe2a2aac4b30e",
        listOf(weights, codec)
    )
    override val engine = VoiceEngine.OMNIVOICE
    override val outputLanguages = supportedLanguages.filter { it.code in setOf("zh", "ja", "en") }
    override val sampleLanguages = outputLanguages
    override val requiresReferenceText = true
    override val parameters = setOf(SpeechParameter.VOLUME)
    override val format = SpeechFormat.WAV
    override val modelNotice get() = appText(R.string.model_omnivoice_notice)

    override fun isSupported() = Build.VERSION.SDK_INT >= 31 && Build.SUPPORTED_ABIS.firstOrNull() == "arm64-v8a"
    override fun credentialsReady(credentials: Map<String, String>) = true
    override fun modelPackage(context: Context): LocalModelPackage = VerifiedModelStore(context, spec)

    private fun voiceFile(context: Context, id: String, extension: String) =
        File(context.noBackupFilesDir, "omnivoice-voices/${UUID.fromString(id)}.$extension")

    override fun isVoiceUsable(context: Context, profile: VoiceProfile): Boolean = acceptsVoice(profile) &&
        runCatching { voiceFile(context, profile.id, "wav").isFile && voiceFile(context, profile.id, "txt").length() > 0 }
            .getOrDefault(false)

    override suspend fun createVoice(
        context: Context, profile: VoiceProfile, sample: File, mimeType: String,
        credentials: Map<String, String>, referenceText: String?, persist: suspend (VoiceProfile) -> Unit
    ) = withContext(Dispatchers.IO) {
        val transcript = referenceText.orEmpty().trim()
        require(transcript.isNotEmpty() && transcript.length <= 1000 && '\u0000' !in transcript) { appText(R.string.reference_transcript_required) }
        saveLocalReference(voiceFile(context, profile.id, "wav"), sample)
        try {
            voiceFile(context, profile.id, "txt").writeText(transcript)
            persist(profile)
        } catch (e: Exception) {
            deleteReference(context, profile.id)
            throw e
        }
    }

    override suspend fun synthesize(
        context: Context, voice: VoiceOption, text: String, language: String,
        parameters: SpeechParameters, credentials: Map<String, String>, progress: suspend (Int, Int) -> Unit
    ): File = VerifiedModelStore(context, spec).withInstalledModel { dir ->
        require(isSupported()) { appText(R.string.omnivoice_android_requirement) }
        require(text.isNotBlank() && speechCharacterCount(text) <= SPEECH_TEXT_LIMIT && '\u0000' !in text) {
            appText(R.string.synthesis_text_invalid, SPEECH_TEXT_LIMIT)
        }
        require(outputLanguages.any { it.code == language }) { appText(R.string.omnivoice_language_unsupported) }
        val reference = voiceFile(context, voice.id, "wav")
        val transcript = voiceFile(context, voice.id, "txt")
        require(reference.isFile && transcript.length() > 0) { appText(R.string.omnivoice_reference_missing) }
        val audio = File.createTempFile("omnivoice-", ".wav", context.cacheDir)
        val coroutine = currentCoroutineContext()
        var complete = false
        try {
            progress(1, 1)
            val error = OmniVoiceNative.synthesize(
                dir.resolve(weights.name).absolutePath.encodeToByteArray(),
                dir.resolve(codec.name).absolutePath.encodeToByteArray(),
                reference.absolutePath.encodeToByteArray(), transcript.readBytes(),
                text.trim().encodeToByteArray(), language.encodeToByteArray(), audio.absolutePath.encodeToByteArray(),
                parameters.volume.coerceIn(0f, 1f), BooleanSupplier { !coroutine.isActive }
            )
            coroutine.ensureActive()
            if (error != null || audio.length() <= 44) throw IOException(error ?: appText(R.string.omnivoice_no_audio))
            complete = true
            audio
        } catch (e: LinkageError) {
            throw IOException(appText(R.string.omnivoice_runtime_missing), e)
        } finally {
            if (!complete) audio.delete()
        }
    }

    override fun deleteReference(context: Context, voiceId: String) {
        voiceFile(context, voiceId, "wav").delete()
        voiceFile(context, voiceId, "txt").delete()
    }
}

@Keep
internal object OmniVoiceNative {
    init { System.loadLibrary("fl_omnivoice_jni") }

    external fun synthesize(
        model: ByteArray, codec: ByteArray, reference: ByteArray, transcript: ByteArray,
        text: ByteArray, language: ByteArray, destination: ByteArray, volume: Float, cancel: BooleanSupplier
    ): String?
}
