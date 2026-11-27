package com.example.myapplication.service

import com.example.myapplication.R
import com.example.myapplication.appText

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.File

/** Read at most 10 MB and validate the actual decoded media duration. */
object AudioSamples {
    const val MAX_BYTES = 10 * 1024 * 1024
    const val MIN_DURATION_MS = 5000L
    const val MAX_DURATION_MS = 30000L

    fun durationMs(file: File): Long {
        require(file.length() in 1..MAX_BYTES.toLong()) { appText(R.string.sample_size_invalid) }
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            require(duration != null && duration in MIN_DURATION_MS..MAX_DURATION_MS) {
                appText(R.string.sample_duration_invalid)
            }
            duration
        } finally {
            retriever.release()
        }
    }

    fun importFile(context: Context, uri: Uri, extension: String): Pair<File, Int> {
        val file = File.createTempFile("sample_", ".$extension", context.cacheDir)
        try {
            requireNotNull(context.contentResolver.openInputStream(uri)) { appText(R.string.sample_open_error) }.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= MAX_BYTES) { appText(R.string.sample_too_large) }
                        output.write(buffer, 0, count)
                    }
                }
            }
            return file to (durationMs(file) / 1000).toInt()
        } catch (e: Exception) {
            file.delete()
            throw e
        }
    }
}
