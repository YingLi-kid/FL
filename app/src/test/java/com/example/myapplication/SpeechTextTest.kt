package com.example.myapplication

import com.example.myapplication.service.SPEECH_TEXT_LIMIT
import com.example.myapplication.service.limitSpeechText
import com.example.myapplication.service.speechCharacterCount
import com.example.myapplication.service.splitQwenText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechTextTest {
    @Test fun limitDoesNotSplitSupplementaryCharacters() {
        val full = "あ".repeat(SPEECH_TEXT_LIMIT - 1) + "🎤"
        assertEquals(SPEECH_TEXT_LIMIT, speechCharacterCount(full))
        assertEquals(full, limitSpeechText(full + "次"))
    }

    @Test fun splitPreservesFullLengthJapaneseSpeech() {
        val text = "小さな挑戦を一つずつ続けると、昨日できなかったこともできるようになります。".repeat(100)
        val limited = limitSpeechText(text)
        val parts = splitQwenText(limited)
        assertEquals(SPEECH_TEXT_LIMIT, speechCharacterCount(limited))
        assertEquals(limited, parts.joinToString(""))
        assertTrue(parts.size > 1 && parts.all { speechCharacterCount(it) <= 48 })
    }

    @Test fun prefersSentenceBoundaryAndHandlesUnpunctuatedText() {
        assertEquals(listOf("こんにちは。", "日本語です。"), splitQwenText("こんにちは。日本語です。", 9))
        val text = "漢🎤".repeat(52)
        val parts = splitQwenText(text)
        assertEquals(text, parts.joinToString(""))
        assertEquals(listOf(48, 48, 8), parts.map(::speechCharacterCount))
    }
}
