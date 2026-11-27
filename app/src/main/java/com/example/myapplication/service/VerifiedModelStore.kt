package com.example.myapplication.service

import com.example.myapplication.R
import com.example.myapplication.appText

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

enum class ModelImportStage { OPENING, READING, INSTALLING }

data class ModelDownloadFile(val name: String, val url: String)

interface LocalModelPackage {
    val downloadBytes: Long
    val defaultSource: String
    val downloadFiles: List<ModelDownloadFile>
    fun isInstalled(): Boolean
    fun downloadedBytes(): Long
    suspend fun download(baseUrl: String, progress: (Long) -> Unit)
    suspend fun importZip(open: () -> InputStream, progress: (Long, ModelImportStage) -> Unit)
    suspend fun remove()
}

internal data class ModelAsset(val name: String, val bytes: Long, val sha256: String)

internal data class ModelPackageSpec(val id: String, val source: String, val assets: List<ModelAsset>)

/** Installs pinned model assets separately from the APK and Android backup. */
internal class VerifiedModelStore(context: Context, spec: ModelPackageSpec) : LocalModelPackage {
    private val files = spec.assets
    override val downloadBytes = files.sumOf { it.bytes }
    override val defaultSource = spec.source
    override val downloadFiles: List<ModelDownloadFile> get() {
        val base = defaultSource.toHttpUrl()
        return files.map { ModelDownloadFile(it.name, base.newBuilder().addPathSegment(it.name).build().toString()) }
    }
    private val root = File(context.noBackupFilesDir, spec.id)
    private val modelDir = File(root, "installed")
    private val previousModelDir = root.resolve("previous")

    override fun isInstalled(): Boolean =
        isComplete(modelDir) || (!modelDir.exists() && isComplete(previousModelDir))

    private fun isComplete(dir: File): Boolean = files.all { asset ->
        val file = dir.resolve(asset.name)
        file.isFile && file.length() == asset.bytes
    }

    override fun downloadedBytes(): Long = files.sumOf { part(it).length().coerceAtMost(it.bytes) }

    override suspend fun download(baseUrl: String, progress: (Long) -> Unit) = transfer {
        val base = baseUrl.toHttpUrl()
        require(base.isHttps && base.username.isEmpty() && base.password.isEmpty()) {
            appText(R.string.model_source_https_required)
        }
        val stage = root.resolve("download")
        stage.mkdirs()
        for (asset in files) {
            val target = part(asset)
            if (target.length() > asset.bytes) target.delete()
            val otherBytes = files.filterNot { it == asset }.sumOf { part(it).length() }
            var offset = target.length()
            if (offset < asset.bytes) checkSpace(asset.bytes - offset)
            while (offset < asset.bytes) {
                currentCoroutineContext().ensureActive()
                val lastByte = minOf(asset.bytes - 1, offset + chunkBytes - 1)
                val url = base.newBuilder().addPathSegment(asset.name).build()
                val request = Request.Builder().url(url)
                    .header("Accept-Encoding", "identity")
                    .header("Range", "bytes=$offset-$lastByte")
                    .build()
                http.newCall(request).execute().use { response ->
                    val body = response.body ?: throw IOException(appText(R.string.model_file_missing_response))
                    val limit = when (response.code) {
                        200 -> {
                            if (body.contentLength() >= 0 && body.contentLength() != asset.bytes) {
                                throw IOException(appText(R.string.model_source_size_mismatch))
                            }
                            offset = 0L
                            asset.bytes
                        }
                        206 -> {
                            if (response.header("Content-Range") != "bytes $offset-$lastByte/${asset.bytes}") {
                                throw IOException(appText(R.string.model_resume_range_mismatch))
                            }
                            lastByte - offset + 1
                        }
                        else -> throw IOException(appText(R.string.model_download_http_error, response.code))
                    }
                    RandomAccessFile(target, "rw").use { out ->
                        out.setLength(offset)
                        out.seek(offset)
                        val total = copy(body.byteStream(), limit, out::write) {
                            progress(otherBytes + offset + it)
                        }
                        if (total != limit) throw IOException(appText(R.string.model_download_interrupted))
                        offset += total
                    }
                }
            }
            verify(target, asset)
        }
        promote(stage)
    }

    override suspend fun importZip(
        open: () -> InputStream,
        progress: (Long, ModelImportStage) -> Unit
    ) = transfer(skipInstalled = false) {
        restoreInstallation()
        val stage = root.resolve("import")
        discardStage(stage)
        checkSpace(downloadBytes)
        stage.mkdirs()
        val found = mutableSetOf<ModelAsset>()
        var completed = 0L
        try {
            progress(0, ModelImportStage.OPENING)
            val input = runInterruptible { open() }
            ZipInputStream(input.buffered()).use { zip ->
                progress(0, ModelImportStage.READING)
                var entryCount = 0
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val entry = runInterruptible { zip.nextEntry } ?: break
                    if (++entryCount > 128) throw IOException(appText(R.string.model_zip_too_many_entries))
                    val asset = files.firstOrNull { it.name == entry.name.substringAfterLast('/') }
                    if (entry.isDirectory || asset == null) {
                        // Bound extra entries. Never extract a path supplied by the archive.
                        copy(
                            zip, 2L * 1024 * 1024, { _, _, _ -> }, interruptible = true,
                            overflowMessage = appText(R.string.model_zip_unexpected_file)
                        )
                        continue
                    }
                    if (!found.add(asset)) throw IOException(appText(R.string.model_zip_duplicate_file))
                    val target = stage.resolve(asset.name)
                    val hash = MessageDigest.getInstance("SHA-256")
                    val bytes = target.outputStream().buffered().use { out ->
                        copy(zip, asset.bytes, { buffer, start, count ->
                            out.write(buffer, start, count)
                            hash.update(buffer, start, count)
                        }, interruptible = true) {
                            progress(completed + it, ModelImportStage.READING)
                        }
                    }
                    if (bytes != asset.bytes) throw IOException(appText(R.string.model_zip_size_mismatch, asset.name))
                    verifyHash(target, asset, hash)
                    completed += bytes
                }
            }
            if (found.size != files.size) {
                val missing = files.filterNot { it in found }.joinToString("、") { it.name }
                throw IOException(appText(R.string.model_zip_missing_files, missing))
            }
            currentCoroutineContext().ensureActive()
            progress(completed, ModelImportStage.INSTALLING)
            promote(stage)
            discardStage(root.resolve("download"))
        } finally {
            discardStage(stage)
        }
    }

    suspend fun <T> withInstalledModel(action: suspend (File) -> T): T = withContext(Dispatchers.IO) {
        operation.withLock {
            restoreInstallation()
            check(isInstalled()) { appText(R.string.model_install_required) }
            action(modelDir)
        }
    }

    override suspend fun remove() = transfer(skipInstalled = false) {
        discardStage(modelDir)
        discardStage(root.resolve("download"))
        discardStage(root.resolve("import"))
        discardStage(previousModelDir)
        if (isInstalled()) throw IOException(appText(R.string.model_remove_error))
    }

    private suspend fun transfer(skipInstalled: Boolean = true, block: suspend () -> Unit) =
        withContext(Dispatchers.IO) {
            operation.withLock {
                if (!skipInstalled || !isInstalled()) block()
            }
        }

    private fun part(asset: ModelAsset) = root.resolve("download").resolve(asset.name)

    private fun checkSpace(bytes: Long) {
        root.mkdirs()
        if (root.usableSpace < bytes) throw IOException(appText(R.string.model_storage_insufficient))
    }

    private fun restoreInstallation() {
        if (!modelDir.exists() && previousModelDir.exists() && !previousModelDir.renameTo(modelDir)) {
            throw IOException(appText(R.string.model_restore_error))
        }
    }

    private fun promote(stage: File) {
        restoreInstallation()
        discardStage(previousModelDir)
        if (modelDir.exists() && !modelDir.renameTo(previousModelDir)) {
            throw IOException(appText(R.string.model_replace_error))
        }
        if (!stage.renameTo(modelDir)) {
            restoreInstallation()
            throw IOException(appText(R.string.model_commit_error))
        }
        discardStage(previousModelDir)
    }

    private fun discardStage(dir: File) {
        files.forEach { dir.resolve(it.name).delete() }
        dir.delete()
    }

    private suspend fun verify(file: File, asset: ModelAsset) {
        if (file.length() != asset.bytes) throw IOException(appText(R.string.model_file_size_mismatch, asset.name))
        val hash = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { copy(it, asset.bytes, hash::update) }
        verifyHash(file, asset, hash)
    }

    private fun verifyHash(file: File, asset: ModelAsset, hash: MessageDigest) {
        val actual = hash.digest().joinToString("") { "%02x".format(it) }
        if (actual != asset.sha256) {
            file.delete()
            throw IOException(appText(R.string.model_verification_error))
        }
    }

    private suspend fun copy(
        input: InputStream,
        limit: Long,
        write: (ByteArray, Int, Int) -> Unit,
        interruptible: Boolean = false,
        overflowMessage: String = appText(R.string.file_exceeds_expected_size),
        progress: (Long) -> Unit = {}
    ): Long {
        var total = 0L
        val buffer = ByteArray(128 * 1024)
        val coroutine = currentCoroutineContext()
        while (true) {
            coroutine.ensureActive()
            val count = if (interruptible) runInterruptible { input.read(buffer) } else input.read(buffer)
            if (count < 0) return total
            total += count
            if (total > limit) throw IOException(overflowMessage)
            write(buffer, 0, count)
            progress(total)
        }
    }

    companion object {
        private const val chunkBytes = 8L * 1024 * 1024
        private val operation = Mutex()
        private val http = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .followSslRedirects(false)
            .build()
    }
}
