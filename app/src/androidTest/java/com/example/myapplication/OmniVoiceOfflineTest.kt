package com.example.myapplication

import android.media.MediaMetadataRetriever
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.data.VoiceOption
import com.example.myapplication.data.VoiceProfile
import com.example.myapplication.data.VoiceSource
import com.example.myapplication.service.AudioSamples
import com.example.myapplication.service.OmniVoiceBackend
import com.example.myapplication.service.SpeechParameters
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class OmniVoiceOfflineTest {
    @Test fun clonesSavedReferenceInChineseJapaneseAndEnglish() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("runOmniVoiceOffline") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val backend = OmniVoiceBackend
        assertTrue("Install the verified OmniVoice model package first.", backend.isInstalled(context))
        val reference = File(context.cacheDir, "omnivoice-smoke-reference.wav")
        assertTrue(reference.isFile)
        val transcript = File(context.cacheDir, "omnivoice-smoke-reference.txt").readText().trim()
        val profile = VoiceProfile(
            name = "OmniVoice offline check", language = "en", source = VoiceSource.UPLOADED,
            sampleSeconds = (AudioSamples.durationMs(reference) / 1000).toInt(), engine = backend.engine,
            backendId = backend.id
        )
        try {
            backend.createVoice(context, profile, reference, "audio/wav", emptyMap(), transcript) {
                assertEquals(profile, it)
            }
            assertTrue(backend.isVoiceUsable(context, profile))
            val voice = VoiceOption(profile.id, profile.name, "", true)
            val sentences = mapOf("zh" to "今天的天气很好。", "ja" to "今日はいい天気ですね。", "en" to "What a beautiful day.")
            for ((language, sentence) in sentences) {
                val output = backend.synthesize(context, voice, sentence, language, SpeechParameters(), emptyMap()) { _, _ -> }
                try {
                    val metadata = MediaMetadataRetriever()
                    val duration = try {
                        metadata.setDataSource(output.absolutePath)
                        metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
                    } finally {
                        metadata.release()
                    }
                    assertTrue("Expected playable $language output", duration > 400)
                    val copy = context.cacheDir.resolve("omnivoice-smoke-${language}.wav")
                    output.copyTo(copy, overwrite = true)
                } finally {
                    output.delete()
                }
            }
        } finally {
            backend.deleteReference(context, profile.id)
        }
    }
}
