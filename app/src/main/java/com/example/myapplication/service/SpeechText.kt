package com.example.myapplication.service

internal const val SPEECH_TEXT_LIMIT = 2000

internal fun speechCharacterCount(text: String): Int = text.codePointCount(0, text.length)

internal fun limitSpeechText(text: String): String =
    if (speechCharacterCount(text) <= SPEECH_TEXT_LIMIT) text
    else text.substring(0, text.offsetByCodePoints(0, SPEECH_TEXT_LIMIT))

/** Prefer sentence or phrase boundaries, with a hard cap for unpunctuated input. */
internal fun splitQwenText(text: String, maxChars: Int = 48): List<String> {
    require(maxChars >= 2)
    val parts = mutableListOf<String>()
    var start = 0
    while (start < text.length) {
        val remaining = text.codePointCount(start, text.length)
        val hardEnd = text.offsetByCodePoints(start, minOf(remaining, maxChars))
        var end = hardEnd
        if (hardEnd < text.length) {
            val earliest = text.offsetByCodePoints(start, maxChars / 3)
            val sentenceEnd = lastBoundary(text, earliest, hardEnd) { it in "。！？!?；;\n" }
            val phraseEnd = lastBoundary(text, earliest, hardEnd) { it in "、，,：:" || it.isWhitespace() }
            end = sentenceEnd ?: phraseEnd ?: hardEnd
        }
        val part = text.substring(start, end).trim()
        if (part.isNotEmpty()) parts.add(part)
        start = end
    }
    return parts
}

private inline fun lastBoundary(text: String, start: Int, end: Int, match: (Char) -> Boolean): Int? {
    for (i in end - 1 downTo start) {
        if (match(text[i])) return i + 1
    }
    return null
}
