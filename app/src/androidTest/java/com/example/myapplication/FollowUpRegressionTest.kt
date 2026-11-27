package com.example.myapplication

import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.data.SpeechHistory
import com.example.myapplication.data.VoiceOption
import com.example.myapplication.service.AppUpdate
import com.example.myapplication.service.AppUpdates
import com.example.myapplication.service.LocalSpeechBackend
import com.example.myapplication.service.SpeechParameters
import com.example.myapplication.service.cosyVoice3Local
import com.example.myapplication.service.pcmWavHeader
import com.example.myapplication.ui.AppUpdatePrompt
import com.example.myapplication.ui.AppUpdateViewModel
import com.example.myapplication.ui.SettingsScreen
import com.example.myapplication.ui.SpeechGeneration
import com.example.myapplication.ui.TtsScreen
import com.example.myapplication.ui.rememberSpeechGeneration
import com.example.myapplication.ui.theme.MyApplicationTheme
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FollowUpRegressionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun offlineGenerationKeepsItsProgressAndSavesAfterLeavingThePage() {
        val finish = CompletableDeferred<Unit>()
        val backend = object : LocalSpeechBackend by cosyVoice3Local {
            override val id = "navigation-regression"
            override val presets = listOf(VoiceOption("test", "测试音色", "", false))

            override suspend fun synthesize(
                context: Context, voice: VoiceOption, text: String, language: String,
                parameters: SpeechParameters, credentials: Map<String, String>, progress: suspend (Int, Int) -> Unit
            ): File {
                progress(2, 3)
                finish.await()
                val pcm = ByteArray(48_000)
                return File.createTempFile("navigation-test-", ".wav", context.cacheDir).apply {
                    outputStream().use {
                        it.write(pcmWavHeader(pcm.size, 24000))
                        it.write(pcm)
                    }
                }
            }
        }
        val showSpeech = mutableStateOf(true)
        lateinit var generation: SpeechGeneration
        compose.setContent {
            generation = rememberSpeechGeneration()
            MyApplicationTheme {
                if (showSpeech.value) {
                    TtsScreen(emptyList(), null, {}, {}, null, backend = backend, generation = generation)
                } else {
                    SettingsScreen(remember { SnackbarHostState() }, {}, {}, generation = generation)
                }
            }
        }
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val history = SpeechHistory(app)
        try {
            compose.onNode(hasSetTextAction()).performTextInput("Offline navigation regression")
            compose.onNodeWithText("离线生成").performScrollTo().performClick()
            compose.waitUntil(5000) { generation.state.value.progress?.segment == 2 }
            compose.runOnIdle { showSpeech.value = false }
            compose.onNodeWithText("清除缓存").performScrollTo().assertIsNotEnabled()
            compose.runOnIdle { showSpeech.value = true }
            compose.onNodeWithText("正在离线生成第 2 / 3 段").assertExists()
            compose.runOnIdle { showSpeech.value = false }
            compose.onNodeWithText("检查更新").assertExists()
            finish.complete(Unit)
            compose.waitUntil(5000) { generation.state.value.results.containsKey(backend.id) }
            val result = checkNotNull(generation.state.value.results[backend.id])
            assertEquals(result, runBlocking { history.find(result.id) })
            assertTrue(result.audio.isFile)
            compose.runOnIdle { showSpeech.value = true }
            compose.onNodeWithText("已生成 · 测试音色", substring = true).assertExists()
        } finally {
            generation.state.value.results[backend.id]?.let { runBlocking { history.delete(it.id) } }
        }
    }

    @Test fun updateCheckSelectsTheNewestPublishedApkAndOpensItsOfficialLink() {
        val releases = """
            [
              {"tag_name":"v1.9.0","assets":[{"name":"FLYinFang.apk","state":"uploaded"}]},
              {"tag_name":"v2.0.0","draft":true,"assets":[{"name":"FLYinFang.apk","state":"uploaded"}]},
              {"tag_name":"v1.11.0","assets":[{"name":"model.zip","state":"uploaded"}]},
              {"tag_name":"v1.10.0","prerelease":true,"assets":[{"name":"FLYinFang.apk","state":"uploaded"}]}
            ]
        """.trimIndent()
        assertNull(AppUpdates.selectUpdate("1.10.0", releases))
        var openedUrl: String? = null
        var calls = 0
        val updates = AppUpdateViewModel {
            calls++
            AppUpdates.selectUpdate(if (calls == 1) "1.10.0" else "1.9.0", releases)
        }
        compose.setContent {
            CompositionLocalProvider(LocalUriHandler provides object : UriHandler {
                override fun openUri(uri: String) { openedUrl = uri }
            }) {
                MyApplicationTheme {
                    SettingsScreen(remember { SnackbarHostState() }, {}, {}, checkingUpdate = updates.checkingManually,
                        onCheckForUpdate = updates::checkManually)
                    AppUpdatePrompt(updates)
                }
            }
        }
        compose.runOnIdle { assertEquals(1, calls) }
        compose.onNodeWithText("下载 APK").assertDoesNotExist()
        compose.onNodeWithText("检查更新").performScrollTo().performClick()
        compose.onNodeWithText("发现新版 1.10.0").assertExists()
        compose.onNodeWithText("下载 APK").performClick()
        compose.runOnIdle {
            assertEquals("https://github.com/YingLi-kid/FL/releases/download/v1.10.0/FLYinFang.apk", openedUrl)
        }
    }

    @Test fun manualCheckJoinsStartupAndDismissalSurvivesRemount() {
        val release = CompletableDeferred<AppUpdate?>()
        var calls = 0
        val updates = AppUpdateViewModel {
            calls++
            release.await()
        }
        val mounted = mutableStateOf(true)
        compose.setContent { if (mounted.value) AppUpdatePrompt(updates) }
        compose.runOnIdle {
            updates.checkManually()
            assertTrue(updates.checkingManually)
            assertEquals(1, calls)
            release.complete(AppUpdate("2.0.0", "https://github.com/YingLi-kid/FL"))
        }
        compose.onNodeWithText("发现新版 2.0.0").assertExists()
        compose.onNodeWithText("稍后").performClick()
        compose.runOnIdle { mounted.value = false }
        compose.runOnIdle { mounted.value = true }
        compose.onNodeWithText("下载 APK").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, calls) }
    }

    @Test fun newerReleasePromptsOnLaunchWithoutInteraction() {
        val releases = """[{"tag_name":"v2.0.0","assets":[{"name":"FLYinFang.apk","state":"uploaded"}]}]"""
        val updates = AppUpdateViewModel { AppUpdates.selectUpdate("1.9.0", releases) }
        compose.setContent { AppUpdatePrompt(updates) }
        compose.onNodeWithText("发现新版 2.0.0").assertExists()
        compose.onNodeWithText("稍后").performClick()
        compose.onNodeWithText("下载 APK").assertDoesNotExist()
    }

    @Test fun offlineStartupIsSilentAndManualRetryCanFindAnUpdate() {
        var calls = 0
        val updates = AppUpdateViewModel {
            calls++
            if (calls == 1) throw java.net.UnknownHostException()
            AppUpdate("2.0.0", "https://github.com/YingLi-kid/FL")
        }
        compose.setContent { AppUpdatePrompt(updates) }
        compose.runOnIdle {
            assertEquals(1, calls)
            assertNull(updates.message)
            assertNull(updates.available)
        }
        compose.onNodeWithText("下载 APK").assertDoesNotExist()
        compose.runOnIdle { updates.checkManually() }
        compose.onNodeWithText("发现新版 2.0.0").assertExists()
    }
}
