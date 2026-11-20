package com.example.myapplication.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/*
 * 已克隆音色的本地持久化存储。
 *
 * 用 DataStore 存一份 JSON 字符串，App 重启后已克隆音色不会丢失，
 * 支持多次克隆累积保存。
 */
private val Context.voiceDataStore by preferencesDataStore(name = "voice_profiles")

class VoiceRepository(context: Context, private val store: DataStore<Preferences> = context.voiceDataStore) {

    private val profilesKey = stringPreferencesKey("profiles_json")

    val profiles = store.data.map { prefs ->
        prefs[profilesKey]?.let { decode(it) } ?: emptyList()
    }

    suspend fun load(): List<VoiceProfile> = profiles.first()

    suspend fun upsert(profile: VoiceProfile) {
        store.edit { prefs ->
            val current = prefs[profilesKey]?.let { decode(it) } ?: emptyList()
            prefs[profilesKey] = encode(current.filterNot { it.id == profile.id } + profile)
        }
    }

    suspend fun remove(id: String) {
        store.edit { prefs ->
            val current = prefs[profilesKey]?.let { decode(it) } ?: emptyList()
            prefs[profilesKey] = encode(current.filterNot { it.id == id })
        }
    }

    suspend fun updateStatus(id: String, status: String) {
        store.edit { prefs ->
            val current = prefs[profilesKey]?.let { decode(it) } ?: emptyList()
            prefs[profilesKey] = encode(current.map { if (it.id == id) it.copy(status = status) else it })
        }
    }

    suspend fun save(profiles: List<VoiceProfile>) {
        store.edit { prefs ->
            prefs[profilesKey] = encode(profiles)
        }
    }

    private fun encode(list: List<VoiceProfile>): String {
        val arr = JSONArray()
        list.forEach { v ->
            arr.put(
                JSONObject().apply {
                    put("id", v.id)
                    put("name", v.name)
                    put("language", v.language)
                    put("source", v.source.name)
                    put("sampleSeconds", v.sampleSeconds)
                    if (v.speakerId == null) put("speakerId", JSONObject.NULL)
                    else put("speakerId", v.speakerId)
                    put("createdAt", v.createdAt)
                    put("status", v.status)
                }
            )
        }
        return arr.toString()
    }

    private fun decode(s: String): List<VoiceProfile> {
        val arr = try {
            JSONArray(s)
        } catch (e: Exception) {
            return emptyList()
        }
        val out = mutableListOf<VoiceProfile>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(
                VoiceProfile(
                    id = o.optString("id").ifEmpty { UUID.randomUUID().toString() },
                    name = o.optString("name"),
                    language = o.optString("language"),
                    source = runCatching { VoiceSource.valueOf(o.optString("source")) }
                        .getOrDefault(VoiceSource.RECORDED),
                    sampleSeconds = o.optInt("sampleSeconds"),
                    speakerId = if (o.isNull("speakerId")) null else o.optString("speakerId"),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                    status = o.optString("status", "OK")
                )
            )
        }
        return out
    }
}
