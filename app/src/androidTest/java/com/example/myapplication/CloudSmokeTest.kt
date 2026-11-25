package com.example.myapplication

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.data.VoiceRepository
import com.example.myapplication.ui.HomeScreen
import com.example.myapplication.ui.theme.MyApplicationTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.json.JSONObject
import java.io.File

/** Opt-in only: uses the configured provider and may incur a small synthesis charge. */
class CloudSmokeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun uploadedCloneSynthesizesChineseAndPlays() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("runCloudSmoke") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val voice = runBlocking { VoiceRepository(context).load() }
            .last { it.name == "UploadTest1120" && it.status == "OK" && it.speakerId != null }
        val startedAt = System.currentTimeMillis()
        compose.setContent { MyApplicationTheme { HomeScreen() } }
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("UploadTest1120").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithText("UploadTest1120").onLast().performScrollTo().performClick()
        val text = "这是上传音频克隆后的语音测试。现在确认文字转语音能够正常生成和播放。"
        compose.onNode(hasSetTextAction()).performTextInput(text)
        compose.onNodeWithText("云端生成").performScrollTo().performClick()
        compose.waitUntil(180000) {
            context.cacheDir.listFiles()?.any { it.name.startsWith("result_") && it.lastModified() >= startedAt } == true
        }
        compose.onNodeWithText("已生成 · UploadTest1120 · 中文").performScrollTo().assertExists()
        compose.waitUntil(60000) {
            compose.onAllNodesWithContentDescription("播放").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("播放").performClick()
        compose.onNodeWithContentDescription("暂停").assertExists().performClick()
        compose.onNodeWithContentDescription("播放").assertExists().performClick()
        compose.onNodeWithContentDescription("暂停").assertExists().performClick()
        val audio = context.cacheDir.listFiles()!!.filter { it.name.startsWith("result_") && it.lastModified() >= startedAt }
            .maxBy { it.lastModified() }
        val saved = File(context.filesDir, "cloud-smoke-result.mp3")
        audio.copyTo(saved, overwrite = true)
        val retriever = MediaMetadataRetriever()
        val duration = try {
            retriever.setDataSource(saved.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
        } finally { retriever.release() }
        assertTrue(saved.length() > 1000)
        assertTrue(duration > 0)
        File(context.filesDir, "cloud-smoke-evidence.json").writeText(JSONObject().apply {
            put("voiceName", voice.name); put("voiceId", voice.speakerId); put("status", voice.status)
            put("text", text); put("audioBytes", saved.length()); put("durationMs", duration)
            put("replayPauseResumeVerified", true)
        }.toString(2))
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(context.filesDir, "cloud-smoke-screen.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
        println("CLOUD_SMOKE_SUCCESS bytes=${saved.length()} durationMs=$duration")
    }
}
