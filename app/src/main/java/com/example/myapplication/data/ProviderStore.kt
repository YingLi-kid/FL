package com.example.myapplication.data

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.booleanPreferencesKey
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

class ProviderRepository(private val context: Context) {

    // 所有服务商的运行配置（启用状态 + 密钥字段），供 UI 实时订阅
    val configs: Flow<Map<String, ProviderRuntimeConfig>> =
        context.providerDataStore.data.map { prefs ->
            serviceProviders.associate { provider ->
                val enabled = prefs[booleanPreferencesKey("enabled_${provider.id}")] ?: false
                val credentials = provider.credentialFields.mapNotNull { field ->
                    prefs[stringPreferencesKey("cred_${provider.id}_${field.key}")]
                        ?.let { field.key to it }
                }.toMap()
                provider.id to ProviderRuntimeConfig(enabled, credentials)
            }
        }

    suspend fun setEnabled(providerId: String, enabled: Boolean) {
        context.providerDataStore.edit { prefs ->
            prefs[booleanPreferencesKey("enabled_$providerId")] = enabled
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
