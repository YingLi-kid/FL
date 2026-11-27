package com.example.myapplication.service

import com.example.myapplication.R
import com.example.myapplication.appText

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray

internal data class AppUpdate(val version: String, val downloadUrl: String)

internal object AppUpdates {
    private const val repository = "YingLi-kid/FL"
    private const val apkName = "FLYinFang.apk"
    private const val responseLimit = 512L * 1024
    private val client = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()
    private val request = Request.Builder()
        .url("https://api.github.com/repos/$repository/releases?per_page=20")
        .header("Accept", "application/vnd.github+json")
        .header("X-GitHub-Api-Version", "2022-11-28")
        .build()

    suspend fun check(currentVersion: String): AppUpdate? {
        val releases = JSONArray()
        var page = 1
        do {
            val url = request.url.newBuilder().addQueryParameter("page", page.toString()).build()
            val batch = JSONArray(readPage(request.newBuilder().url(url).build()))
            for (i in 0 until batch.length()) releases.put(batch.get(i))
            page++
        } while (batch.length() == 20)
        return selectUpdate(currentVersion, releases.toString())
    }

    private suspend fun readPage(request: Request): String = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                val result = runCatching {
                    response.use {
                        if (!it.isSuccessful) throw IOException(appText(R.string.update_http_error, it.code))
                        val source = it.body?.source() ?: throw IOException(appText(R.string.update_empty_response))
                        if (source.request(responseLimit + 1)) throw IOException(appText(R.string.update_response_too_large))
                        source.readUtf8()
                    }
                }
                continuation.resumeWith(result)
            }
        })
    }

    internal fun selectUpdate(currentVersion: String, response: String): AppUpdate? {
        val current = requireNotNull(Version.parse(currentVersion)) { appText(R.string.update_invalid_current_version) }
        val releases = JSONArray(response)
        var latestVersion: Version? = null
        var latestTag: String? = null
        for (index in 0 until releases.length()) {
            val release = releases.getJSONObject(index)
            val tag = release.optString("tag_name")
            val version = Version.parse(tag) ?: continue
            if (release.optBoolean("draft") || latestVersion?.let { version <= it } == true) continue
            // Public previews use numeric tags too. Require the official APK, not a model ZIP.
            val assets = release.optJSONArray("assets") ?: continue
            val hasApk = (0 until assets.length()).any { assetIndex ->
                val asset = assets.optJSONObject(assetIndex)
                asset?.optString("name") == apkName && asset.optString("state") == "uploaded"
            }
            if (hasApk) {
                latestVersion = version
                latestTag = tag
            }
        }
        val version = latestVersion ?: throw IOException(appText(R.string.update_no_apk))
        if (version <= current) return null
        return AppUpdate(version.toString(), "https://github.com/$repository/releases/download/$latestTag/$apkName")
    }

    private data class Version(val major: Int, val minor: Int, val patch: Int) : Comparable<Version> {
        override fun compareTo(other: Version) = compareValuesBy(this, other, Version::major, Version::minor, Version::patch)
        override fun toString() = "$major.$minor.$patch"

        companion object {
            private val pattern = Regex("""v?(\d+)\.(\d+)(?:\.(\d+))?""")

            fun parse(text: String): Version? {
                val match = pattern.matchEntire(text) ?: return null
                val major = match.groupValues[1].toIntOrNull() ?: return null
                val minor = match.groupValues[2].toIntOrNull() ?: return null
                val patch = match.groups[3]?.let { it.value.toIntOrNull() ?: return null } ?: 0
                return Version(major, minor, patch)
            }
        }
    }
}
