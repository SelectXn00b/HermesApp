package com.xiaomo.androidforclaw.agent.tools

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class TermuxAutoSetupApiTest {
    @Test
    fun source_exposesTriggerAutoSetupEntryPoint() {
        val source = File("src/main/java/com/xiaomo/androidforclaw/agent/tools/TermuxBridgeTool.kt").readText()
        assertTrue(source.contains("suspend fun triggerAutoSetup(): TermuxStatus"))
    }
}
