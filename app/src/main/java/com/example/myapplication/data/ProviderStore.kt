package com.example.myapplication.data

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/*
 * 服务商配置存储层。
 *
 * 当前实现：DataStore 明文存储（实现简单）。
 * 密钥属于敏感信息，正式发布前建议替换为 Android Keystore 加密实现——
 * 只需在 UI 层替换 ProviderRepository 的数据源，接口不变，页面无需改动。
 */

// 应用级单例 DataStore
private val Context.providerDataStore by preferencesDataStore(name = "provider_config")

// Keep the existing preference values so upgrading preserves both selections.
internal enum class SpeechMode(val preferenceValue: String) {
    LOCAL("QWEN"), CLOUD("ALIYUN")
}

class ProviderRepository(private val context: Context) {

    private val speechModeKey = stringPreferencesKey("voice_engine")
    internal val speechMode: Flow<SpeechMode> = context.providerDataStore.data.map { prefs ->
        SpeechMode.entries.firstOrNull { it.preferenceValue == prefs[speechModeKey] } ?: SpeechMode.CLOUD
    }

    internal suspend fun setSpeechMode(mode: SpeechMode) {
        context.providerDataStore.edit { it[speechModeKey] = mode.preferenceValue }
    }

    internal val backendSelections: Flow<Map<SpeechMode, String>> = context.providerDataStore.data.map { prefs ->
        SpeechMode.entries.mapNotNull { mode ->
            prefs[backendKey(mode)]?.let { mode to it }
        }.toMap()
    }

    internal suspend fun setSelectedBackend(mode: SpeechMode, id: String) {
        context.providerDataStore.edit { prefs ->
            prefs[backendKey(mode)] = id
        }
    }

    private fun backendKey(mode: SpeechMode) = stringPreferencesKey("backend_${mode.preferenceValue}")

    // 所有服务商的密钥字段，供 UI 实时订阅
    val configs: Flow<Map<String, ProviderRuntimeConfig>> =
        context.providerDataStore.data.map { prefs ->
            serviceProviders.associate { provider ->
                val credentials = provider.credentialFields.mapNotNull { field ->
                    prefs[stringPreferencesKey("cred_${provider.id}_${field.key}")]
                        ?.let { field.key to it }
                }.toMap()
                provider.id to ProviderRuntimeConfig(credentials)
            }
        }

    suspend fun setCredential(providerId: String, fieldKey: String, value: String) {
        context.providerDataStore.edit { prefs ->
            prefs[stringPreferencesKey("cred_${providerId}_$fieldKey")] = value
        }
    }
}

@Composable
fun rememberProviderRepository(): ProviderRepository {
    val context = LocalContext.current
    return remember(context) { ProviderRepository(context.applicationContext) }
}
