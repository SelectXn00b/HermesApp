package com.ai.assistance.operit.util

import com.ai.assistance.operit.util.AppLogger

object TtsCleaner {
    private const val TAG = "TtsCleaner"

    /**
     * Cleans a given text by removing all parts that match the provided regex pattern.
     *
     * @param text The input text to clean.
     * @param regexPattern The regex pattern to use for cleaning. If the pattern is blank,
     *                     the original text will be returned.
     * @return The cleaned text.
     */
    fun clean(text: String, regexPattern: String): String {
        if (regexPattern.isBlank()) {
            AppLogger.d(TAG, "clean(single): Empty regex pattern, returning original text")
            return text
        }

        return try {
            val regex = Regex(regexPattern)
            val cleanedText = text.replace(regex, "")
            AppLogger.d(TAG, "clean(single): Original='$text' | Pattern='$regexPattern' | Cleaned='$cleanedText'")
            cleanedText
        } catch (e: Exception) {
            AppLogger.e(TAG, "clean(single): Invalid regex pattern '$regexPattern', returning original text", e)
            text
        }
    }

    /**
     * Cleans a given text by removing all parts that match any of the provided regex patterns.
     *
     * @param text The input text to clean.
     * @param regexPatterns The list of regex patterns to use for cleaning. Empty or blank patterns are skipped.
     * @return The cleaned text.
     */
    fun clean(text: String, regexPatterns: List<String>): String {
        if (regexPatterns.isEmpty()) {
            AppLogger.d(TAG, "clean(list): Empty pattern list, returning original text")
            return text
        }

        AppLogger.d(TAG, "clean(list): Starting with text='$text' | Patterns count=${regexPatterns.size}")
        
        var cleanedText = text
        regexPatterns.forEachIndexed { index, pattern ->
            if (pattern.isNotBlank()) {
                try {
                    val regex = Regex(pattern)
                    val beforeClean = cleanedText
                    cleanedText = cleanedText.replace(regex, "")
                    if (beforeClean != cleanedText) {
                        AppLogger.d(TAG, "clean(list): Pattern[$index]='$pattern' matched | Before='$beforeClean' | After='$cleanedText'")
                    } else {
                        AppLogger.d(TAG, "clean(list): Pattern[$index]='$pattern' no match")
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "clean(list): Invalid regex pattern[$index]='$pattern', skipping", e)
                }
            } else {
                AppLogger.d(TAG, "clean(list): Pattern[$index] is blank, skipping")
            }
        }
        
        AppLogger.d(TAG, "clean(list): Final result='$cleanedText'")
        return cleanedText
    }
}
