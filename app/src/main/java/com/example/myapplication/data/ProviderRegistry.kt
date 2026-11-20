package com.example.myapplication.data

/*
 * 内置语音服务商清单。
 * 当前仅保留已接入真实 API 的服务商（阿里云百炼 CosyVoice）。
 * 后续新增服务商时，在此追加一条 ServiceProvider 元数据，并在接入层实现对应调用，
 * UI 会自动渲染，无需改动页面代码。
 */
val serviceProviders: List<ServiceProvider> = listOf(
    ServiceProvider(
        id = "aliyun",
        name = "阿里云 · 百炼 CosyVoice",
        region = "国内",
        capabilities = setOf(ProviderCapability.VOICE_CLONE, ProviderCapability.TTS),
        credentialFields = listOf(
            CredentialField("apiKey", "API Key", "百炼控制台 API-KEY 获取（sk- 开头）")
        ),
        description = "声音复刻免费 + 语音合成（约 2 元/万字符）",
        docsUrl = "https://bailian.console.aliyun.com"
    )
)
