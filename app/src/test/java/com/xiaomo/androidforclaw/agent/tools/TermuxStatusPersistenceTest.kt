package com.xiaomo.androidforclaw.agent.tools

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class TermuxStatusPersistenceTest {
    @Test
    fun source_persistsStatusToJsonFile() {
        val source = File("src/main/java/com/xiaomo/androidforclaw/agent/tools/TermuxBridgeTool.kt").readText()
        assertTrue(source.contains("termux_setup_status.json"))
        assertTrue(source.contains("put(\"lastStep\", status.lastStep.name)"))
        assertTrue(source.contains("put(\"updatedAt\", System.currentTimeMillis())"))
    }
}
