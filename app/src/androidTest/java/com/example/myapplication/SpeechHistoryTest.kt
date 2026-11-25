package com.example.myapplication

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.data.SpeechHistory
import com.example.myapplication.service.SpeechFormat
import com.example.myapplication.service.pcmWavHeader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SpeechHistoryTest {
    @Test fun generatedAudioSurvivesCacheRemovalAndCanBeDeleted() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val temporary = File.createTempFile("history-test-", ".wav", app.cacheDir)
        val pcm = ByteArray(48_000)
        temporary.outputStream().use {
            it.write(pcmWavHeader(pcm.size, 24000))
            it.write(pcm)
        }
        val history = SpeechHistory(app)
        val record = history.save(temporary, "test fixture", "test voice", "ja", "test engine", SpeechFormat.WAV)
        try {
            assertTrue(temporary.delete())
            val reopened = SpeechHistory(app).list().single { it.id == record.id }
            assertEquals(record, reopened)
            assertEquals(1000, reopened.durationMs)
            assertTrue(reopened.audio.isFile)
            assertTrue(history.delete(record.id))
            assertFalse(reopened.audio.exists())
            assertFalse(history.list().any { it.id == record.id })
        } finally {
            temporary.delete()
            history.delete(record.id)
        }
    }
}
