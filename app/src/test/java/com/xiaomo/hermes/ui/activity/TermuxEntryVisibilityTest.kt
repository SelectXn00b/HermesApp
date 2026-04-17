package com.xiaomo.hermes.ui.activity

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class TermuxEntryVisibilityTest {
    @Test
    fun settingsTab_containsTermuxEntry() {
        val candidates = listOf(
            "src/main/java/com/xiaomo/hermes/ui/compose/ForClawSettingsTab.kt",
            "app/src/main/java/com/xiaomo/hermes/ui/compose/ForClawSettingsTab.kt"
        )
        val file = candidates.map { File(it) }.firstOrNull { it.exists() }
            ?: throw AssertionError("ForClawSettingsTab.kt (HermesSettingsTab) not found, cwd=${System.getProperty("user.dir")}")
        val source = file.readText()
        assertTrue("TermuxSetupActivity reference missing", source.contains("TermuxSetupActivity"))
    }
}
