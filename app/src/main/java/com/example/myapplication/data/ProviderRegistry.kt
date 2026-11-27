package com.example.myapplication.data

import com.example.myapplication.R

/*
 * 内置语音服务商清单。
 * 当前仅保留已接入真实 API 的服务商（阿里云百炼 CosyVoice）。
 * 后续新增服务商时，在此追加一条 ServiceProvider 元数据，并在接入层实现对应调用，
 * UI 会自动渲染，无需改动页面代码。
 */
val serviceProviders: List<ServiceProvider> = listOf(
    ServiceProvider(
        id = "aliyun",
        nameRes = R.string.provider_aliyun,
        regionRes = R.string.provider_region_china,
        capabilities = setOf(ProviderCapability.VOICE_CLONE, ProviderCapability.TTS),
        credentialFields = listOf(
            CredentialField("apiKey", "API Key", R.string.provider_key_hint)
        ),
        descriptionRes = R.string.provider_description,
        docsUrl = "https://bailian.console.aliyun.com"
    )
)
