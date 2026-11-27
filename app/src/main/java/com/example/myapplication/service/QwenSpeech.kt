package com.example.myapplication.service

import com.example.myapplication.R
import com.example.myapplication.appText

import android.content.Context
import android.os.Build
import com.qwen.tts.studio.engine.QwenEngine
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.RandomAccessFile
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal fun supportsQwenTrial(): Boolean =
    Build.VERSION.SDK_INT >= 31 && Build.SUPPORTED_ABIS.firstOrNull() == "arm64-v8a"

internal suspend fun generateQwenSpeech(
    context: Context,
    model: QwenModel,
    reference: File,
    text: String,
    language: String,
    volume: Float = 1f,
    onProgress: suspend (segment: Int, total: Int) -> Unit = { _, _ -> }
): File {
    require(supportsQwenTrial()) { appText(R.string.qwen_android_requirement) }
    require(reference.isFile) { appText(R.string.qwen_reference_missing) }
    require(speechCharacterCount(text) <= SPEECH_TEXT_LIMIT) { appText(R.string.synthesis_text_limit, SPEECH_TEXT_LIMIT) }
    val parts = splitQwenText(text)
    require(parts.isNotEmpty()) { appText(R.string.synthesis_text_required) }
    val languageId = requireNotNull(qwenLanguageIds[language]) { appText(R.string.qwen_language_unsupported) }
    val gain = volume.coerceIn(0f, 1f)
    return VerifiedModelStore(context, model.files).withInstalledModel { modelDir ->
        val engine = try {
            QwenEngine()
        } catch (e: LinkageError) {
            throw IOException(appText(R.string.qwen_runtime_missing), e)
        }
        engine.use {
            it.setBackendPreference(QwenEngine.BACKEND_CPU)
            it.setCpuThreads(Runtime.getRuntime().availableProcessors().coerceIn(1, 4))
            check(it.loadModels(modelDir.absolutePath, model.talkerName)) {
                it.getLastError() ?: appText(R.string.qwen_load_error)
            }
            check(it.getModelCapabilities()?.supportsCloning == true) { appText(R.string.qwen_cloning_unsupported) }
            val file = File.createTempFile("qwen-result-", ".wav", context.cacheDir)
            var sampleRate = 0
            var bytes = 0
            var finished = false
            try {
                file.outputStream().buffered().use { output ->
                    output.write(ByteArray(44)) // The final RIFF size is written after all parts succeed.
                    val maxTokens = if (parts.size == 1 && speechCharacterCount(parts.single()) <= 24) 256 else 512
                    var frames = 0
                    it.setProgressCallback { generated, _ -> frames = generated }
                    for ((index, part) in parts.withIndex()) {
                        currentCoroutineContext().ensureActive()
                        onProgress(index + 1, parts.size)
                        frames = 0
                        val result = it.synthesize(
                            text = part,
                            referenceWav = reference.absolutePath,
                            params = QwenEngine.NativeParams(languageId = languageId, maxAudioTokens = maxTokens)
                        )
                        currentCoroutineContext().ensureActive()
                        val audio = result.audio
                        check(result.success && audio != null && audio.isNotEmpty() && result.sampleRate > 0) {
                            appText(R.string.qwen_segment_error, index + 1, result.errorMsg ?: appText(R.string.qwen_no_audio))
                        }
                        check(frames < maxTokens) { appText(R.string.qwen_segment_limit, index + 1) }
                        if (index == 0) {
                            sampleRate = result.sampleRate
                        } else {
                            check(result.sampleRate == sampleRate) { appText(R.string.qwen_sample_rate_mismatch) }
                            val pause = ByteArray((sampleRate * 12 / 100) * 2)
                            output.write(pause)
                            bytes += pause.size
                        }
                        for (sample in audio) {
                            val pcm = ((sample * gain).coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt()
                            output.write(pcm and 0xff)
                            output.write(pcm shr 8 and 0xff)
                        }
                        bytes += audio.size * 2
                    }
                }
                RandomAccessFile(file, "rw").use { it.write(pcmWavHeader(bytes, sampleRate)) }
                currentCoroutineContext().ensureActive()
                finished = true
                file
            } finally {
                if (!finished) file.delete()
            }
        }
    }
}

internal fun pcmWavHeader(size: Int, sampleRate: Int): ByteArray {
    val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
    header.put("RIFF".toByteArray()).putInt(36 + size).put("WAVEfmt ".toByteArray())
        .putInt(16).putShort(1).putShort(1).putInt(sampleRate).putInt(sampleRate * 2)
        .putShort(2).putShort(16).put("data".toByteArray()).putInt(size)
    return header.array()
}
