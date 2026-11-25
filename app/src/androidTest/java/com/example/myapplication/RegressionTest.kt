package com.example.myapplication

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.data.*
import com.example.myapplication.service.AudioPlayer
import com.example.myapplication.service.AudioSamples
import com.example.myapplication.ui.*
import com.example.myapplication.ui.theme.MyApplicationTheme
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.sin

class RegressionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun draftsSurviveTabsSettingsAndStateRestoration() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { MyApplicationTheme { HomeScreen() } }
        compose.onNodeWithText("文字转语音").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Retained draft")
        compose.onNodeWithText("克隆音色").performClick()
        compose.waitForIdle()
        compose.onNode(hasSetTextAction()).performTextInput("Voice draft")
        compose.onNodeWithText("文字转语音").performClick()
        compose.onNode(hasSetTextAction()).assertTextEquals("Retained draft")
        compose.onNodeWithContentDescription("设置").performClick()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNode(hasSetTextAction()).assertTextEquals("Retained draft")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNode(hasSetTextAction()).assertTextEquals("Retained draft")
        compose.onNodeWithText("克隆音色").performClick()
        compose.onNode(hasSetTextAction()).assertTextContains("Voice draft")
    }

    @Test fun missingKeyCannotGenerateFakeAudio() {
        var message = ""
        compose.setContent { MyApplicationTheme {
            TtsScreen(emptyList(), null, {}, { message = it }, null)
        } }
        compose.onNode(hasSetTextAction()).performTextInput("Test")
        compose.onNodeWithText("云端生成").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(message.contains("API Key")) }
        compose.onNodeWithText("已生成", substring = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("播放").assertDoesNotExist()
    }

    @Test fun credentialEditorRetainsRapidInputBeforeStorageEcho() {
        var saved = ""
        compose.setContent { MyApplicationTheme {
            ProviderManagerScreen(emptyMap(), { _, _, value -> saved = value }, {})
        } }
        compose.onNodeWithText("阿里云 · 百炼 CosyVoice").performClick()
        val fakeKey = "sk-test-not-a-real-key-0123456789-abcdefghijklmnopqrstuvwxyz"
        for (character in fakeKey) compose.onNode(hasSetTextAction()).performTextInput(character.toString())
        compose.runOnIdle { assertEquals(fakeKey, saved) }
    }

    @Test fun providerSystemBackReturnsToList() {
        var leftManager = false
        compose.setContent { MyApplicationTheme {
            ProviderManagerScreen(emptyMap(), { _, _, _ -> }, { leftManager = true })
        } }
        compose.onNodeWithText("阿里云 · 百炼 CosyVoice").performClick()
        compose.onNodeWithText("密钥配置").assertExists()
        Espresso.pressBack()
        compose.onNodeWithText("服务商管理").assertExists()
        compose.runOnIdle { assertFalse(leftManager) }
    }

    @Test fun clearCacheActuallyRemovesFiles() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sample = File(context.cacheDir, "cache_regression.m4a").apply { writeText("fixture") }
        compose.setContent { MyApplicationTheme {
            SettingsScreen(remember { SnackbarHostState() }, {}, {})
        } }
        compose.onNodeWithText("清除缓存").performScrollTo().performClick()
        compose.waitUntil(5000) { !sample.exists() }
        compose.onNodeWithText("缓存已清理").assertExists()
    }

    @Test fun sampleValidationUsesMediaDuration() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "duration_regression.wav")
        try {
            file.writeBytes(wave(6))
            assertTrue(AudioSamples.durationMs(file) in 5900..6100)
            for (seconds in listOf(1, 31)) {
                file.writeBytes(wave(seconds))
                try {
                    AudioSamples.durationMs(file)
                    fail("Invalid duration was accepted: $seconds")
                } catch (_: IllegalArgumentException) { }
            }
        } finally { file.delete() }
    }

    @Test fun pendingVoiceIdAndStatusSurviveReload() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fixtureFile = File(context.cacheDir, "voice-regression-${System.nanoTime()}.preferences_pb")
        val fixtureScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = PreferenceDataStoreFactory.create(scope = fixtureScope, produceFile = { fixtureFile })
        val repository = VoiceRepository(context, store)
        try {
        val profile = VoiceProfile(name = "pending", language = "zh", source = VoiceSource.UPLOADED,
            sampleSeconds = 6, speakerId = "test-remote-id", status = "DEPLOYING")
        repository.save(listOf(profile))
        assertEquals(listOf(profile), repository.load())
        repository.updateStatus(profile.id, "OK")
        assertEquals("OK", repository.load().single().status)
        val second = profile.copy(id = "second-id", name = "second")
        repository.upsert(second)
        repository.remove(profile.id)
        repository.updateStatus(profile.id, "DEPLOYING")
        assertEquals(listOf(second), repository.load())
        } finally {
            fixtureScope.cancel()
            fixtureFile.delete()
        }
    }

    @Test fun voiceMenuTracksLibraryChanges() {
        val profiles = androidx.compose.runtime.mutableStateListOf<VoiceProfile>()
        compose.setContent { MyApplicationTheme {
            TtsScreen(profiles, null, {}, {}, null)
        } }
        compose.onNodeWithText("选择音色").performClick()
        val voice = VoiceProfile(name = "New voice", language = "zh", source = VoiceSource.UPLOADED,
            sampleSeconds = 6, speakerId = "test-ready-id")
        compose.runOnIdle { profiles.add(voice) }
        compose.onNodeWithText("New voice").assertExists()
        compose.runOnIdle { profiles.clear() }
        compose.onNodeWithText("New voice").assertDoesNotExist()
    }

    @Test fun audioCanPauseResumeAndReplayAfterCompletion() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val player = AudioPlayer(context)
        val finished = CountDownLatch(2)
        try {
            compose.runOnIdle {
                assertTrue(player.play(wave(2), onCompletion = { finished.countDown() }) > 0)
                assertTrue(player.isPlaying())
                assertFalse(player.togglePauseResume())
                assertTrue(player.togglePauseResume())
            }
            val firstDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(6)
            while (finished.count == 2L && System.nanoTime() < firstDeadline) Thread.sleep(20)
            assertEquals(1L, finished.count)
            compose.runOnIdle {
                assertTrue(player.togglePauseResume())
                assertTrue(player.isPlaying())
            }
            assertTrue("Replay never completed", finished.await(6, TimeUnit.SECONDS))
        } finally { compose.runOnIdle { player.stop() } }
    }

    private fun wave(seconds: Int): ByteArray {
        val samples = 16000 * seconds
        return ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + samples * 2); put("WAVEfmt ".toByteArray())
            putInt(16); putShort(1); putShort(1); putInt(16000); putInt(32000)
            putShort(2); putShort(16); put("data".toByteArray()); putInt(samples * 2)
            repeat(samples) { putShort((sin(2 * Math.PI * 440 * it / 16000) * 2000).toInt().toShort()) }
        }.array()
    }
}
