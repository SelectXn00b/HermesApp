package com.xiaomo.hermes.hermes.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeassistantToolTest {

    @Test
    fun `_ENTITY_ID_RE matches valid entity ids`() {
        assertTrue(_ENTITY_ID_RE.matches("light.living_room"))
        assertTrue(_ENTITY_ID_RE.matches("sensor.temp_outdoor"))
        assertTrue(_ENTITY_ID_RE.matches("a.b"))
    }

    @Test
    fun `_ENTITY_ID_RE rejects invalid entity ids`() {
        assertFalse(_ENTITY_ID_RE.matches("Light.kitchen"))  // uppercase domain
        assertFalse(_ENTITY_ID_RE.matches("1light.kitchen"))  // starts with digit
        assertFalse(_ENTITY_ID_RE.matches("light"))  // no dot
        assertFalse(_ENTITY_ID_RE.matches("light."))  // empty suffix
        assertFalse(_ENTITY_ID_RE.matches(".kitchen"))  // empty domain
        assertFalse(_ENTITY_ID_RE.matches("light.Kitchen"))  // uppercase name
    }

    @Test
    fun `_SERVICE_NAME_RE matches valid service names`() {
        assertTrue(_SERVICE_NAME_RE.matches("turn_on"))
        assertTrue(_SERVICE_NAME_RE.matches("toggle"))
        assertTrue(_SERVICE_NAME_RE.matches("a_b_c123"))
    }

    @Test
    fun `_SERVICE_NAME_RE rejects invalid service names`() {
        assertFalse(_SERVICE_NAME_RE.matches("Turn_on"))  // uppercase
        assertFalse(_SERVICE_NAME_RE.matches("1turn_on"))  // starts with digit
        assertFalse(_SERVICE_NAME_RE.matches(""))
        assertFalse(_SERVICE_NAME_RE.matches("turn-on"))  // dash
    }

    @Test
    fun `_BLOCKED_DOMAINS covers arbitrary-code-exec vectors`() {
        assertTrue("shell_command" in _BLOCKED_DOMAINS)
        assertTrue("command_line" in _BLOCKED_DOMAINS)
        assertTrue("python_script" in _BLOCKED_DOMAINS)
        assertTrue("pyscript" in _BLOCKED_DOMAINS)
        assertTrue("hassio" in _BLOCKED_DOMAINS)
        assertTrue("rest_command" in _BLOCKED_DOMAINS)
    }

    @Test
    fun `_BLOCKED_DOMAINS has six entries`() {
        assertEquals(6, _BLOCKED_DOMAINS.size)
    }

    @Test
    fun `_checkHaAvailable returns false`() {
        assertFalse(_checkHaAvailable())
    }

    @Test
    fun `_handleListEntities returns error`() {
        val result = _handleListEntities(emptyMap())
        assertTrue("not available" in result.lowercase() || "error" in result.lowercase())
    }

    @Test
    fun `_handleGetState returns error`() {
        val result = _handleGetState(emptyMap())
        assertTrue("not available" in result.lowercase() || "error" in result.lowercase())
    }

    @Test
    fun `_handleCallService returns error`() {
        val result = _handleCallService(emptyMap())
        assertTrue("not available" in result.lowercase() || "error" in result.lowercase())
    }

    @Test
    fun `_handleListServices returns error`() {
        val result = _handleListServices(emptyMap())
        assertTrue("not available" in result.lowercase() || "error" in result.lowercase())
    }

    @Test
    fun `schemas are declared as empty maps`() {
        assertNotNull(HA_LIST_ENTITIES_SCHEMA)
        assertNotNull(HA_GET_STATE_SCHEMA)
        assertNotNull(HA_LIST_SERVICES_SCHEMA)
        assertNotNull(HA_CALL_SERVICE_SCHEMA)
        assertTrue(HA_LIST_ENTITIES_SCHEMA.isEmpty())
        assertTrue(HA_GET_STATE_SCHEMA.isEmpty())
        assertTrue(HA_LIST_SERVICES_SCHEMA.isEmpty())
        assertTrue(HA_CALL_SERVICE_SCHEMA.isEmpty())
    }
}
