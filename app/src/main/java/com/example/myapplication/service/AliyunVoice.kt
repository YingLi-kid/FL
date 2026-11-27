package com.example.myapplication.service

import com.example.myapplication.R
import com.example.myapplication.appText

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/*
 * 阿里云百炼 · CosyVoice 语音服务（声音复刻 + 非实时语音合成）。
 *
 * 1) cloneVoice：上传 base64 音频创建音色（免费）。
 * 2) getVoiceStatus：查询音色状态（DEPLOYING=处理中 / OK=可用 / UNDEPLOYED=未通过）。
 * 3) synthesize：用系统音色或复刻音色合成语音（按目标模型计费，约 2 元/万字符）。
 *
 * 鉴权：Authorization: Bearer <DASHSCOPE_API_KEY>（百炼控制台 API-KEY，sk- 开头）。
 * 复刻音色绑定 target_model，合成时必须使用同一模型。
 */
class AliyunVoice {

    companion object {
        const val TAG = "AliyunVoice"

        // 统一使用的模型（复刻与合成必须一致）
        const val DEFAULT_MODEL = "cosyvoice-v3-flash"

        const val CLONE_URL =
            "https://dashscope.aliyuncs.com/api/v1/services/audio/tts/customization"
        const val SYNTH_URL =
            "https://dashscope.aliyuncs.com/api/v1/services/audio/tts/SpeechSynthesizer"

        // 音色状态
        const val STATUS_DEPLOYING = "DEPLOYING"
        const val STATUS_OK = "OK"
        const val STATUS_UNDEPLOYED = "UNDEPLOYED"

        // 语言 hints（与 App 语言代码一致）
        fun languageHint(langCode: String): String = langCode
    }

    sealed class Result {
        data class Success(
            val voiceId: String = "",
            val status: String = "",
            val audio: ByteArray = byteArrayOf()
        ) : Result()

        data class Failure(val message: String) : Result()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .callTimeout(180, TimeUnit.SECONDS)
        .build()

    /**
     * 创建复刻音色。
     *
     * @param audioBase64 音频字节的 base64（不含 data uri 前缀）。
     * @param mimeType 音频 MIME 类型，如 audio/wav、audio/mpeg、audio/mp4。
     * @param prefix 音色名前缀（仅数字和英文字母，≤10 字符）。
     * @param languageHint 样本语言（zh/en/ja/ko/fr/de/ru/pt/th/id/vi 等）。
     */
    suspend fun cloneVoice(
        apiKey: String,
        audioBase64: String,
        mimeType: String = "audio/wav",
        prefix: String = "vc",
        languageHint: String? = "zh"
    ): Result = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("model", "voice-enrollment")
                put("input", JSONObject().apply {
                    put("action", "create_voice")
                    put("target_model", DEFAULT_MODEL)
                    put("prefix", prefix)
                    put("audio", "data:$mimeType;base64,$audioBase64")
                    if (languageHint != null) put("language_hints", JSONArray().put(languageHint))
                })
            }
            Log.d(TAG, "Sending clone creation request")
            val resp = post(CLONE_URL, apiKey, body)
            Log.d(TAG, "Clone creation HTTP ${resp.code}")
            val json = safeJson(resp.body?.string() ?: "")
            if (resp.code in 200..299) {
                val voiceId = json.optJSONObject("output")?.optString("voice_id") ?: ""
                if (voiceId.isNotEmpty()) {
                    Result.Success(voiceId = voiceId)
                } else {
                    Result.Failure(appText(R.string.provider_clone_error, json.optString("message", appText(R.string.provider_missing_voice_id))))
                }
            } else {
                Result.Failure(parseError(json, resp.code))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "clone upload failed", e)
            Result.Failure(e.message ?: appText(R.string.network_error))
        }
    }

    /**
     * 查询复刻音色状态。status 为 OK 时可合成。
     */
    suspend fun getVoiceStatus(apiKey: String, voiceId: String): Result =
        withContext(Dispatchers.IO) {
            try {
                val body = JSONObject().apply {
                    put("model", "voice-enrollment")
                    put("input", JSONObject().apply {
                        put("action", "query_voice")
                        put("voice_id", voiceId)
                    })
                }
                val resp = post(CLONE_URL, apiKey, body)
                val json = safeJson(resp.body?.string() ?: "")
                if (resp.code in 200..299) {
                    val status = json.optJSONObject("output")?.optString("status")
                        ?: STATUS_DEPLOYING
                    Result.Success(voiceId = voiceId, status = status)
                } else {
                    Result.Failure(parseError(json, resp.code))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.Failure(e.message ?: appText(R.string.network_error))
            }
        }

    /**
     * 非实时语音合成。返回音频字节。
     *
     * @param voice 系统音色 ID 或复刻音色 ID。
     * @param format mp3/wav/pcm/opus。
     * @param volume 0-100。
     * @param rate 语速 0.5-2.0。
     * @param pitch 音调 0.5-2.0。
     */
    suspend fun synthesize(
        apiKey: String,
        voice: String,
        text: String,
        format: String = "mp3",
        sampleRate: Int = 24000,
        volume: Int = 50,
        rate: Float = 1.0f,
        pitch: Float = 1.0f
    ): Result = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("model", DEFAULT_MODEL)
                put("input", JSONObject().apply {
                    put("text", text)
                    put("voice", voice)
                    put("format", format)
                    put("sample_rate", sampleRate)
                    put("volume", volume)
                    put("rate", rate.toDouble())
                    put("pitch", pitch.toDouble())
                })
            }
            val resp = post(SYNTH_URL, apiKey, body)
            val json = safeJson(resp.body?.string() ?: "")
            if (resp.code in 200..299) {
                val url = json.optJSONObject("output")?.optJSONObject("audio")?.optString("url")
                    ?: ""
                if (url.isNotEmpty()) {
                    val audio = download(url)
                    if (audio.isNotEmpty()) {
                        Result.Success(audio = audio)
                    } else {
                        Result.Failure(appText(R.string.audio_download_error))
                    }
                } else {
                    Result.Failure(appText(R.string.provider_synthesis_error, json.optString("message", appText(R.string.provider_missing_audio))))
                }
            } else {
                Result.Failure(parseError(json, resp.code))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Failure(e.message ?: appText(R.string.network_error))
        }
    }

    private fun post(url: String, apiKey: String, body: JSONObject): okhttp3.Response {
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        return client.newCall(request).execute()
    }

    private fun download(url: String): ByteArray {
        return try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { resp ->
                if (resp.code in 200..299) resp.body?.bytes() ?: byteArrayOf() else byteArrayOf()
            }
        } catch (e: Exception) {
            Log.e(TAG, "download failed", e)
            byteArrayOf()
        }
    }

    private fun safeJson(s: String): JSONObject = try {
        JSONObject(s)
    } catch (e: Exception) {
        JSONObject()
    }

    private fun parseError(json: JSONObject, code: Int): String {
        val msg = json.optString("message", "")
        val codeStr = json.optString("code", "")
        return if (msg.isNotEmpty()) "$msg ($codeStr)" else "HTTP $code"
    }
}
