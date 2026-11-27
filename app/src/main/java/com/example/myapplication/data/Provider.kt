package com.example.myapplication.data

import androidx.annotation.StringRes
import com.example.myapplication.appText

/*
 * 服务商抽象层 · 数据模型。
 *
 * 本应用是「多服务商语音 API 客户端壳」：
 *   - ServiceProvider 是服务商的静态元数据（内置，不可改）；
 *   - ProviderRuntimeConfig 是用户填写的运行配置（密钥，本地持久化）；
 *   - 密钥由使用者自己提供，仅保存在本机，请求时直接发给对应服务商官方 API。
 */

// 服务商能力：音色克隆 / 文字转语音
enum class ProviderCapability { VOICE_CLONE, TTS }

// 一个密钥字段的定义（例如 App ID、Access Token、API Key）
data class CredentialField(
    val key: String,
    val label: String,
    @StringRes private val placeholderRes: Int
) {
    val placeholder: String get() = appText(placeholderRes)
}

// 服务商静态元数据
data class ServiceProvider(
    val id: String,
    @StringRes private val nameRes: Int,
    @StringRes private val regionRes: Int,
    val capabilities: Set<ProviderCapability>,
    val credentialFields: List<CredentialField>,
    @StringRes private val descriptionRes: Int,
    val docsUrl: String
) {
    val name: String get() = appText(nameRes)
    val region: String get() = appText(regionRes)
    val description: String get() = appText(descriptionRes)
    val supportsClone: Boolean get() = ProviderCapability.VOICE_CLONE in capabilities
    val supportsTts: Boolean get() = ProviderCapability.TTS in capabilities
}

// 服务商运行配置（用户填写，持久化）
data class ProviderRuntimeConfig(
    val credentials: Map<String, String> = emptyMap()
) {
    // 是否已填齐全部密钥字段
    fun isCredentialComplete(provider: ServiceProvider): Boolean =
        provider.credentialFields.all { !credentials[it.key].isNullOrBlank() }
}
