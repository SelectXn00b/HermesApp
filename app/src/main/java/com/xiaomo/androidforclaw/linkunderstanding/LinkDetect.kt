package com.xiaomo.androidforclaw.linkunderstanding

/**
 * OpenClaw module: link-understanding
 * Source: OpenClaw/src/link-understanding/detect.ts
 *
 * URL detection in free-form text: extract all links, check presence,
 * and grab the first link.
 */

private val URL_REGEX = Regex(
    "https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+",
    RegexOption.IGNORE_CASE
)

data class DetectedLink(
    val url: String,
    val startIndex: Int,
    val endIndex: Int
)

/** Extract all URLs from the given text, preserving order of appearance. */
fun extractLinksFromMessage(text: String): List<DetectedLink> {
    return URL_REGEX.findAll(text).map { match ->
        DetectedLink(
            url = match.value,
            startIndex = match.range.first,
            endIndex = match.range.last + 1
        )
    }.toList()
}

/** Check whether the text contains at least one URL. */
fun hasLinks(text: String): Boolean = URL_REGEX.containsMatchIn(text)

/** Extract the first URL found in the text, or null. */
fun extractFirstLink(text: String): String? = URL_REGEX.find(text)?.value

/** Count the number of distinct URLs in the text. */
fun countLinks(text: String): Int = URL_REGEX.findAll(text).count()
