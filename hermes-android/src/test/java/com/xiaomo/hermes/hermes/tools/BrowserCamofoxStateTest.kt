package com.xiaomo.hermes.hermes.tools

import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserCamofoxStateTest {

    @Test
    fun `CAMOFOX_STATE_DIR_NAME is browser_auth`() {
        assertEquals("browser_auth", CAMOFOX_STATE_DIR_NAME)
    }

    @Test
    fun `CAMOFOX_STATE_SUBDIR is camofox`() {
        assertEquals("camofox", CAMOFOX_STATE_SUBDIR)
    }

    // Tests for getCamofoxStateDir / getCamofoxIdentity require an initialized
    // Android Context (via initHermesConstants) — not feasible in pure JVM tests.
    // See BrowserCamofoxStateKt.getCamofoxStateDir → getHermesHome → getAppContext().
}
