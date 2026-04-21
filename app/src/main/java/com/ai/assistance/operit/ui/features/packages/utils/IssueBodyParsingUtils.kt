package com.ai.assistance.operit.ui.features.packages.utils

import com.ai.assistance.operit.util.AppLogger
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

object IssueBodyDescriptionExtractor {
    private val DESCRIPTION_LABEL_WORDS = setOf(
        "description",
        "desc",
        "summary",
        "introduction",
        "简介",
        "描述",
        "介绍",
        "说明"
    )

    private fun isLabelOnlyLine(raw: String): Boolean {
        val normalized = raw
            .replace("*", "")
            .replace("_", "")
            .trim()
            .trimEnd(':', '：')
        if (normalized.isBlank()) return false

        val parts = normalized
            .split('/', '|')
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (parts.isEmpty()) return false

        return parts.all { part ->
            DESCRIPTION_LABEL_WORDS.contains(part.lowercase())
        }
    }

    fun extractHumanDescriptionFromBody(body: String): String {
        if (body.isBlank()) return ""

        val withoutComments = body.replace(Regex("<!--[\\s\\S]*?-->"), "\n")
        val withoutCodeBlocks = withoutComments.replace(Regex("```[\\s\\S]*?```"), "\n")

        val sb = StringBuilder()
        val paragraphs = mutableListOf<String>()

        fun flush() {
            val paragraph = sb.toString().trim()
            if (paragraph.isNotBlank()) paragraphs.add(paragraph)
            sb.clear()
        }

        for (rawLine in withoutCodeBlocks.lines()) {
            val trimmedRaw = rawLine.trim()
            if (trimmedRaw.isBlank()) {
                flush()
                continue
            }

            if (isLabelOnlyLine(trimmedRaw)) continue
            if (trimmedRaw.startsWith("#")) continue
            if (trimmedRaw.startsWith("|")) continue
            if (trimmedRaw == "---") continue

            val trimmed = trimmedRaw
                .replace(Regex("^\\*\\*[^*]+\\*\\*\\s*[:：]\\s*"), "")
                .replace(
                    Regex(
                        "^(描述|简介|介绍|说明|description|desc|summary|introduction)\\s*[:：]\\s*",
                        RegexOption.IGNORE_CASE
                    ),
                    ""
                )
                .trim()
            if (trimmed.isBlank()) continue

            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(trimmed)

            if (sb.length >= 400) {
                flush()
                break
            }
        }
        flush()

        val candidate = paragraphs.firstOrNull { paragraph ->
            paragraph.length >= 6 &&
                !paragraph.startsWith("{") &&
                !paragraph.contains("operit-", ignoreCase = true)
        }

        return candidate?.take(300)?.trim().orEmpty()
    }
}

object IssueBodyMetadataParser {
    @PublishedApi
    internal val json = Json { ignoreUnknownKeys = true }

    inline fun <reified T> parseCommentJson(
        body: String,
        prefix: String,
        tag: String,
        metadataName: String
    ): T? {
        val start = body.indexOf(prefix)
        if (start < 0) return null

        val jsonStart = start + prefix.length
        val end = body.indexOf(" -->", startIndex = jsonStart)
        if (end <= jsonStart) return null

        val jsonString = body.substring(jsonStart, end)
        return try {
            json.decodeFromString<T>(jsonString)
        } catch (e: Exception) {
            AppLogger.e(tag, "Failed to parse $metadataName JSON from issue body.", e)
            null
        }
    }
}
