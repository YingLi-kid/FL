package com.example.myapplication.data

import com.example.myapplication.R
import com.example.myapplication.appText

import android.content.Context
import android.media.MediaMetadataRetriever
import com.example.myapplication.service.SpeechFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.UUID

internal data class SpeechRecord(
    val id: String,
    val text: String,
    val voice: String,
    val language: String,
    val backend: String,
    val format: SpeechFormat,
    val createdAt: Long,
    val durationMs: Int,
    val audio: File
)

/** Audio and metadata become visible together after the staging folder has been renamed. */
internal class SpeechHistory(context: Context) {
    private val root = File(context.noBackupFilesDir, "speech-history")

    suspend fun save(
        temporaryAudio: File,
        text: String,
        voice: String,
        language: String,
        backend: String,
        format: SpeechFormat
    ): SpeechRecord = withContext(Dispatchers.IO) {
        lock.withLock {
            val id = UUID.randomUUID().toString()
            check(root.isDirectory || root.mkdirs()) { appText(R.string.history_directory_error) }
            check(temporaryAudio.isFile && temporaryAudio.length() > 0) { appText(R.string.history_audio_invalid) }
            val durationMs = audioDuration(temporaryAudio)
            val stage = root.resolve("pending-$id")
            val destination = root.resolve(id)
            check(stage.mkdir()) { appText(R.string.history_temp_directory_error) }
            try {
                val audioName = "speech.${format.extension}"
                val createdAt = System.currentTimeMillis()
                temporaryAudio.copyTo(stage.resolve(audioName))
                val metadata = JSONObject()
                    .put("text", text).put("voice", voice).put("language", language)
                    .put("backend", backend).put("format", format.name)
                    .put("createdAt", createdAt).put("durationMs", durationMs)
                stage.resolve("record.json").writeText(metadata.toString())
                check(stage.renameTo(destination)) { appText(R.string.history_save_error) }
                SpeechRecord(id, text, voice, language, backend, format, createdAt, durationMs, destination.resolve(audioName))
            } finally {
                if (stage.exists()) stage.deleteRecursively()
            }
        }
    }

    suspend fun list(): List<SpeechRecord> = withContext(Dispatchers.IO) {
        lock.withLock {
            root.listFiles().orEmpty().mapNotNull { dir ->
                if (dir.name.startsWith("pending-")) {
                    dir.deleteRecursively()
                    return@mapNotNull null
                }
                runCatching { read(dir) }.getOrNull()
            }.sortedByDescending { it.createdAt }
        }
    }

    suspend fun find(id: String): SpeechRecord? = withContext(Dispatchers.IO) {
        lock.withLock {
            val dir = root.resolve(UUID.fromString(id).toString())
            if (dir.isDirectory) read(dir) else null
        }
    }

    suspend fun delete(id: String): Boolean = withContext(Dispatchers.IO) {
        lock.withLock {
            val dir = root.resolve(UUID.fromString(id).toString())
            if (!dir.exists()) return@withLock true
            val record = read(dir)
            if (record != null && !record.audio.delete()) return@withLock false
            dir.resolve("record.json").delete()
            dir.delete() || !dir.exists()
        }
    }

    private fun read(dir: File): SpeechRecord? {
        UUID.fromString(dir.name)
        val json = JSONObject(dir.resolve("record.json").readText())
        val format = SpeechFormat.valueOf(json.getString("format"))
        val audio = dir.resolve("speech.${format.extension}")
        if (!audio.isFile || audio.length() == 0L) return null
        return SpeechRecord(
            dir.name, json.getString("text"), json.getString("voice"), json.getString("language"),
            json.getString("backend"), format, json.getLong("createdAt"), json.getInt("durationMs"), audio
        )
    }

    private fun audioDuration(file: File): Int {
        val reader = MediaMetadataRetriever()
        return try {
            reader.setDataSource(file.absolutePath)
            val duration = reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toIntOrNull()
            require(duration != null && duration > 0) { appText(R.string.history_unplayable) }
            duration
        } finally {
            reader.release()
        }
    }

    companion object {
        private val lock = Mutex()
    }
}
