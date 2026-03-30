package com.xiaomo.androidforclaw.agent.context

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextBuilderModelAliasesTest {
    @Test
    fun contextBuilderSource_containsOpenClawModelAliases() {
        val source = File("src/main/java/com/xiaomo/androidforclaw/agent/context/ContextBuilder.kt").readText()
        // Model aliases section header is still present
        assertTrue(source.contains("## Model Aliases"))
        // Dynamic alias generation delegates to ModelSelection.buildModelAliasLines()
        assertTrue(source.contains("ModelSelection.buildModelAliasLines"))
    }
}
