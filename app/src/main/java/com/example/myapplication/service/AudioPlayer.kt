package com.example.myapplication.service

import android.content.Context
import android.media.MediaPlayer
import java.io.File

/*
 * 简单的音频播放器：把合成得到的 mp3 字节写入缓存临时文件后用 MediaPlayer 播放。
 */
class AudioPlayer(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private var tempFile: File? = null
    private var completed = false

    /**
     * 播放音频字节，返回总时长（毫秒）。
     */
    fun play(
        audio: ByteArray,
        onCompletion: () -> Unit = {},
        onError: (String) -> Unit = {}
    ): Int {
        stop()
        return try {
            val file = File.createTempFile("tts_", ".mp3", context.cacheDir)
            file.writeBytes(audio)
            tempFile = file

            val player = MediaPlayer()
            mediaPlayer = player
            player.apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener { completed = true; onCompletion() }
                setOnErrorListener { _, _, _ ->
                    onError("音频播放失败")
                    true
                }
                prepare()
                start()
            }
            mediaPlayer?.duration ?: 0
        } catch (e: Exception) {
            stop()
            onError(e.message ?: "音频播放失败")
            0
        }
    }

    /** Returns the actual playing state after pausing, resuming or replaying. */
    fun togglePauseResume(): Boolean {
        val mp = mediaPlayer ?: return false
        return try {
            if (mp.isPlaying) {
                mp.pause()
            } else {
                if (completed) {
                    mp.seekTo(0)
                    completed = false
                }
                mp.start()
            }
            mp.isPlaying
        } catch (_: Exception) {
            false
        }
    }

    fun isPlaying(): Boolean = try {
        mediaPlayer?.isPlaying == true
    } catch (_: Exception) {
        false
    }

    fun currentPosition(): Int = try {
        mediaPlayer?.currentPosition ?: 0
    } catch (_: Exception) {
        0
    }

    fun duration(): Int = try {
        mediaPlayer?.duration ?: 0
    } catch (_: Exception) {
        0
    }

    fun stop() {
        mediaPlayer?.let { player ->
            runCatching { player.stop() }
            runCatching { player.release() }
        }
        mediaPlayer = null
        completed = false
        try {
            tempFile?.delete()
        } catch (_: Exception) {
        }
        tempFile = null
    }
}
