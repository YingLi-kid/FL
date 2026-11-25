package com.example.myapplication

import android.media.MediaMetadataRetriever
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.data.VoiceOption
import com.example.myapplication.data.VoiceProfile
import com.example.myapplication.data.VoiceSource
import com.example.myapplication.service.AudioSamples
import com.example.myapplication.service.SpeechParameters
import com.example.myapplication.service.cosyVoice3Local
import com.example.myapplication.service.indexTts25
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AudioCppOfflineSmokeTest {
    @Test fun installedModelClonesJapaneseFromAnEnglishReference() = runBlocking {
        val model = InstrumentationRegistry.getArguments().getString("audioCppModel")
        assumeTrue(model != null)
        val backend = listOf(indexTts25, cosyVoice3Local).single { it.id == model }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue("Import the ${backend.name} ZIP before running this test.", backend.isInstalled(context))
        val sample = File(context.cacheDir, "audiocpp-smoke-reference.wav")
        assertTrue("Provide an authorized reference recording at ${sample.absolutePath}.", sample.isFile)
        val profile = VoiceProfile(
            name = "Android voice clone check", language = "en", source = VoiceSource.UPLOADED,
            sampleSeconds = (AudioSamples.durationMs(sample) / 1000).toInt(), engine = backend.engine,
            backendId = backend.id
        )
        backend.createVoice(context, profile, sample, "audio/wav", emptyMap(), null) { }
        try {
            assertTrue(backend.isVoiceUsable(context, profile))
            val voice = VoiceOption(profile.id, profile.name, "", true)
            val start = SystemClock.elapsedRealtime()
            val output = backend.synthesize(context, voice, "こんにちは。", "ja", SpeechParameters(), emptyMap()) { _, _ -> }
            val metadata = MediaMetadataRetriever()
            try {
                metadata.setDataSource(output.absolutePath)
                val duration = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
                assertTrue("Expected a playable WAV, got $duration ms.", duration > 300)
                output.copyTo(context.cacheDir.resolve("${backend.id}-smoke-ja.wav"), overwrite = true)
                println("${backend.id}: ${SystemClock.elapsedRealtime() - start} ms generated ${duration} ms of WAV")
            } finally {
                metadata.release()
                output.delete()
            }
        } finally {
            backend.deleteReference(context, profile.id)
        }
    }
}
