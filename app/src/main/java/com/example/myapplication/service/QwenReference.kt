package com.example.myapplication.service

import com.example.myapplication.R
import com.example.myapplication.appText

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import com.example.myapplication.data.VoiceEngine
import com.example.myapplication.data.VoiceProfile
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteOrder
import java.util.UUID

/** Keep reference recordings in app-private storage, outside backups and cache clearing. */
internal fun qwenReferenceFile(context: Context, voiceId: String): File =
    File(context.noBackupFilesDir, "qwen-voices/${UUID.fromString(voiceId)}.wav")

internal suspend fun saveQwenReference(context: Context, profile: VoiceProfile, sample: File): File {
    require(profile.engine == VoiceEngine.QWEN)
    return saveLocalReference(qwenReferenceFile(context, profile.id), sample)
}

internal suspend fun saveLocalReference(destination: File, sample: File): File {
    check(destination.parentFile?.let { it.isDirectory || it.mkdirs() } == true) {
        appText(R.string.reference_directory_error)
    }
    val temporary = File.createTempFile("reference-", ".tmp", destination.parentFile)
    try {
        decodeReference(sample, temporary)
        check(temporary.renameTo(destination)) { appText(R.string.reference_save_error) }
        return destination
    } finally {
        temporary.delete()
    }
}

/** Use Android's audio decoder for imported MP3/M4A/WAV and for microphone recordings. */
private suspend fun decodeReference(source: File, destination: File) {
    val extractor = MediaExtractor()
    try {
        extractor.setDataSource(source.absolutePath)
        val track = (0 until extractor.trackCount).firstOrNull {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: error(appText(R.string.reference_missing_track))
        val format = extractor.getTrackFormat(track)
        val mime = requireNotNull(format.getString(MediaFormat.KEY_MIME))
        extractor.selectTrack(track)

        val decoder = MediaCodec.createDecoderByType(mime)
        try {
            decoder.configure(format, null, null, 0)
            decoder.start()
            writeDecodedAudio(decoder, extractor, destination)
        } finally {
            decoder.release()
        }
    } finally {
        extractor.release()
    }
}

private suspend fun writeDecodedAudio(codec: MediaCodec, extractor: MediaExtractor, destination: File) {
    val info = MediaCodec.BufferInfo()
    val deadline = SystemClock.elapsedRealtime() + 30_000
    var inputEnded = false
    var outputEnded = false
    var sampleRate = 0
    var channels = 0
    var bytes = 0
    RandomAccessFile(destination, "rw").use { output ->
        output.seek(44)
        while (!outputEnded) {
            currentCoroutineContext().ensureActive()
            check(SystemClock.elapsedRealtime() < deadline) { appText(R.string.reference_decode_timeout) }
            if (!inputEnded) {
                val index = codec.dequeueInputBuffer(5_000)
                if (index >= 0) {
                    val input = requireNotNull(codec.getInputBuffer(index))
                    val size = extractor.readSampleData(input, 0)
                    inputEnded = size < 0
                    codec.queueInputBuffer(
                        index, 0, size.coerceAtLeast(0), extractor.sampleTime.coerceAtLeast(0L),
                        if (inputEnded) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
                    )
                    if (!inputEnded) extractor.advance()
                }
            }
            val index = codec.dequeueOutputBuffer(info, 5_000)
            when {
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val format = codec.outputFormat
                    sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    check(sampleRate in 8_000..192_000 && channels in 1..8) { appText(R.string.reference_format_unsupported) }
                    if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                        check(format.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_16BIT) {
                            appText(R.string.reference_format_help)
                        }
                    }
                }
                index >= 0 -> {
                    try {
                        if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                            check(channels > 0 && info.size % (channels * 2) == 0) { appText(R.string.reference_decode_incomplete) }
                            val buffer = requireNotNull(codec.getOutputBuffer(index)).duplicate().order(ByteOrder.LITTLE_ENDIAN)
                            buffer.position(info.offset)
                            val mono = ByteArray(info.size / channels)
                            for (i in mono.indices step 2) {
                                var sum = 0
                                repeat(channels) { sum += buffer.short.toInt() }
                                val value = sum / channels
                                mono[i] = value.toByte()
                                mono[i + 1] = (value shr 8).toByte()
                            }
                            bytes += mono.size
                            check(bytes <= sampleRate * 2 * 31) { appText(R.string.reference_too_long) }
                            output.write(mono)
                        }
                        outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    } finally {
                        codec.releaseOutputBuffer(index, false)
                    }
                }
            }
        }
        check(bytes > 0 && sampleRate > 0) { appText(R.string.reference_empty) }
        output.seek(0)
        output.write(pcmWavHeader(bytes, sampleRate))
    }
}
