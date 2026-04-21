package com.ai.assistance.operit.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatMarkupRegexMetaTest {

    @Test fun extractSignature_ignoresEmptyBody() {
        assertNull(ChatMarkupRegex.extractGeminiThoughtSignature("<meta provider=\"gemini:thought_signature\">   </meta>"))
    }

    @Test fun removeSignature_preservesTrailingText() {
        assertEquals("prefixsuffix", ChatMarkupRegex.removeGeminiThoughtSignatureMeta("prefix<meta provider=\"gemini:thought_signature\">a</meta>suffix"))
    }

    @Test fun removeSignature_removesMultipleMatchingTags() {
        assertEquals("body", ChatMarkupRegex.removeGeminiThoughtSignatureMeta("<meta provider=\"gemini:thought_signature\">a</meta>body<meta provider=\"gemini:thought_signature\">b</meta>"))
    }

    @Test fun extractSignature_returnsLastAmongMixedMetaTags() {
        assertEquals(
            "target",
            ChatMarkupRegex.extractGeminiThoughtSignature(
                "<meta provider=\"other\">x</meta><meta provider=\"gemini:thought_signature\">target</meta>"
            )
        )
    }
}
