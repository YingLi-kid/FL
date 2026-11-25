package com.example.myapplication

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.service.ModelAsset
import com.example.myapplication.service.ModelPackageSpec
import com.example.myapplication.service.VerifiedModelStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class VerifiedModelStoreTest {
    @Test
    fun installsByModelDefinitionAndPreservesExistingInstallOnBadZip() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val first = VerifiedModelStore(context, spec("first.gguf", "aaa"))
        val second = VerifiedModelStore(context, spec("second.gguf", "bbb"))
        try {
            first.importZip({ zip("first.gguf", "aaa").inputStream() }) { _, _ -> }
            assertTrue(first.isInstalled())
            assertFalse(second.isInstalled())

            try {
                first.importZip({ zip("first.gguf", "xxx").inputStream() }) { _, _ -> }
                fail("Expected a ZIP with the wrong SHA-256 to be rejected")
            } catch (_: IOException) {
                assertEquals("aaa", first.withInstalledModel { it.resolve("first.gguf").readText() })
            }

            second.importZip({ zip("second.gguf", "bbb").inputStream() }) { _, _ -> }
            first.remove()
            assertFalse(first.isInstalled())
            assertEquals("bbb", second.withInstalledModel { it.resolve("second.gguf").readText() })
        } finally {
            first.remove()
            second.remove()
        }
    }

    private fun spec(name: String, content: String): ModelPackageSpec {
        val bytes = content.toByteArray()
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        return ModelPackageSpec("model-test-${UUID.randomUUID()}", "https://example.invalid/", listOf(
            ModelAsset(name, bytes.size.toLong(), hash)
        ))
    }

    private fun zip(name: String, content: String): ByteArray {
        val buffer = ByteArrayOutputStream()
        ZipOutputStream(buffer).use { output ->
            output.putNextEntry(ZipEntry("model/$name"))
            output.write(content.toByteArray())
            output.closeEntry()
        }
        return buffer.toByteArray()
    }
}
