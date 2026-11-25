package com.example.myapplication

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.service.VerifiedModelStore
import com.example.myapplication.service.qwen06b
import com.example.myapplication.service.qwenModels
import com.example.myapplication.service.generateQwenSpeech
import com.example.myapplication.service.saveQwenReference
import com.example.myapplication.data.VoiceEngine
import com.example.myapplication.data.VoiceProfile
import com.example.myapplication.data.VoiceSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class QwenOfflineSmokeTest {
    @Test
    fun savedReferenceVoiceGeneratesJapaneseWav() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("runQwenOfflineSmoke") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val reference = File(context.cacheDir, "qwen-smoke-reference.wav")
        val id = InstrumentationRegistry.getArguments().getString("qwenModel")
        val model = if (id == null) qwen06b else qwenModels.single { it.files.id == id }
        assertTrue("Import the verified Qwen model first.", VerifiedModelStore(context, model.files).isInstalled())
        assertTrue("Provide an authorized WAV at ${reference.absolutePath}.", reference.isFile)
        val profile = VoiceProfile(
            name = "Offline smoke reference", language = "zh", source = VoiceSource.UPLOADED,
            sampleSeconds = 16, engine = VoiceEngine.QWEN
        )
        val saved = withContext(Dispatchers.IO) { saveQwenReference(context, profile, reference) }
        try {
            val audio = generateQwenSpeech(context, model, saved, "こんにちは。今日はいい天気ですね。", "ja")
            try {
                assertTrue("Expected a WAV with audio samples.", audio.length() > 44)
                val bytes = ByteArray(4)
                audio.inputStream().use { check(it.read(bytes) == bytes.size) }
                assertTrue(bytes.contentEquals("RIFF".toByteArray()))
            } finally {
                audio.delete()
            }
        } finally {
            saved.delete()
        }
    }
}
