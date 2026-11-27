package com.example.myapplication.service

import androidx.annotation.StringRes
import com.example.myapplication.R
import com.example.myapplication.appText

private const val source = "https://huggingface.co/Serveurperso/Qwen3-TTS-GGUF/resolve/9684422"

private val tokenizer = ModelAsset(
    "qwen-tokenizer-12hz-Q4_K_M.gguf", 254_974_752,
    "cf3788b4d50aaa665fb6e57c170396aae03a3555fea52d2b5d0cda902d658039"
)

internal class QwenModel(id: String, @StringRes private val nameRes: Int, talker: ModelAsset) {
    val name: String get() = appText(nameRes)
    val files = ModelPackageSpec(id, source, listOf(talker, tokenizer))
    val talkerName = talker.name
}

internal val qwen06b = QwenModel(
    "qwen3-tts-0.6b-q4km", R.string.model_qwen_small,
    ModelAsset(
        "qwen-talker-0.6b-base-Q4_K_M.gguf", 628_905_056,
        "4b468ec7b1f62b90ef4ca316c0aa57deadfd54b2cf9651703ea753cedaf04226"
    )
)

internal val qwen17b = QwenModel(
    "qwen3-tts-1.7b-q4km", R.string.model_qwen_large,
    ModelAsset(
        "qwen-talker-1.7b-base-Q4_K_M.gguf", 1_219_245_248,
        "ea393ebaf2167ea23ce9fc18b093822851358a950d7075cd47ab4f6ce23e887d"
    )
)

internal val qwenModels = listOf(qwen06b, qwen17b)
