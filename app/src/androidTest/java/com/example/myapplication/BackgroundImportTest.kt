package com.example.myapplication

import android.content.ContentValues
import android.content.Intent
import android.provider.MediaStore
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.myapplication.data.CredentialCipher
import com.example.myapplication.data.CredentialMigration
import com.example.myapplication.service.ModelImportService
import com.example.myapplication.service.localSpeechBackends
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class BackgroundImportTest {
    @Test fun migratesExistingKeyWithoutKeepingPlaintext() = runBlocking {
        val old = stringPreferencesKey("cred_aliyun_apiKey")
        val sealed = stringPreferencesKey("sealed_aliyun_apiKey")
        val prefs = CredentialMigration.migrate(mutablePreferencesOf(old to "synthetic-example"))
        assertNull(prefs[old])
        assertNotEquals("synthetic-example", prefs[sealed])
        assertEquals("synthetic-example", CredentialCipher.read(requireNotNull(prefs[sealed])))
        assertFalse(CredentialMigration.shouldMigrate(prefs))
    }

    @Test fun importsQwenWhileActivityIsInBackground() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val fixture = File(app.getExternalFilesDir(null), "import-smoke.zip")
        assumeTrue(android.os.Build.VERSION.SDK_INT >= 29 && fixture.isFile)
        val backend = localSpeechBackends.first { it.id == "qwen3-tts-0.6b-q4km" }
        val resolver = app.contentResolver
        val uri = requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "FL-import-test.zip")
            put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
        }))
        try {
            requireNotNull(resolver.openOutputStream(uri)).use { out -> fixture.inputStream().use { it.copyTo(out) } }
            ActivityScenario.launch<MainActivity>(Intent(app, MainActivity::class.java)).use { activity ->
                activity.onActivity { ModelImportService.start(it, backend.id, uri) }
                activity.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
                val result = runBlocking { withTimeout(180000) {
                    ModelImportService.status.first { it.model == backend.id && !it.active }
                } }
                assertEquals(appText(R.string.model_import_success), result.message)
                assertTrue(backend.modelPackage(app).isInstalled())
            }
        } finally { resolver.delete(uri, null, null) }
    }
}
