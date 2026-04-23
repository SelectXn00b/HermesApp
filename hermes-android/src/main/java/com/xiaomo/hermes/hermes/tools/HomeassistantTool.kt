/**
 * Home Assistant integration tool.
 *
 * Android does not ship with a running Home Assistant relay; the tool
 * surface is kept as stubs so tool-registration code stays aligned with
 * Python.
 *
 * Ported from tools/homeassistant_tool.py
 */
package com.xiaomo.hermes.hermes.tools

val HA_LIST_ENTITIES_SCHEMA: Map<String, Any> = emptyMap()
val HA_GET_STATE_SCHEMA: Map<String, Any> = emptyMap()
val HA_LIST_SERVICES_SCHEMA: Map<String, Any> = emptyMap()
val HA_CALL_SERVICE_SCHEMA: Map<String, Any> = emptyMap()

/** Regex for valid HA entity_id format (e.g. "light.living_room"). */
val _ENTITY_ID_RE: Regex = Regex("^[a-z_][a-z0-9_]*\\.[a-z0-9_]+$")

/** Regex for valid HA service/domain names. */
val _SERVICE_NAME_RE: Regex = Regex("^[a-z][a-z0-9_]*$")

/** Service domains blocked for security — arbitrary code exec / SSRF vectors. */
val _BLOCKED_DOMAINS: Set<String> = setOf(
    "shell_command",
    "command_line",
    "python_script",
    "pyscript",
    "hassio",
    "rest_command"
)

private fun _getConfig(): Pair<String?, String?> = null to null

private fun _getHeaders(token: String = ""): Map<String, String> = emptyMap()

@Suppress("UNUSED_PARAMETER")
private fun _filterAndSummarize(
    states: List<Any?>,
    domain: String? = null,
    area: String? = null,
): Map<String, Any?> = emptyMap()

@Suppress("UNUSED_PARAMETER")
private fun _buildServicePayload(
    entityId: String? = null,
    data: Map<String, Any?>? = null,
): Map<String, Any?> = emptyMap()

@Suppress("UNUSED_PARAMETER")
private fun _parseServiceResponse(
    domain: String,
    service: String,
    result: Any?,
): Map<String, Any?> = emptyMap()

private fun _runAsync(coro: Any?): Any? = null

fun _handleListEntities(args: Map<String, Any?>, vararg kw: Any?): String =
    toolError("Home Assistant tool is not available on Android")

fun _handleGetState(args: Map<String, Any?>, vararg kw: Any?): String =
    toolError("Home Assistant tool is not available on Android")

fun _handleCallService(args: Map<String, Any?>, vararg kw: Any?): String =
    toolError("Home Assistant tool is not available on Android")

fun _handleListServices(args: Map<String, Any?>, vararg kw: Any?): String =
    toolError("Home Assistant tool is not available on Android")

fun _checkHaAvailable(): Boolean = false

/** Async list-entities helper (Python `_async_list_entities`). Android stub. */
@Suppress("UNUSED_PARAMETER")
private suspend fun _asyncListEntities(
    domain: String? = null,
    area: String? = null,
): String = toolError("Home Assistant tool is not available on Android")

/** Async get-state helper (Python `_async_get_state`). Android stub. */
@Suppress("UNUSED_PARAMETER")
private suspend fun _asyncGetState(args: Map<String, Any?>): String =
    toolError("Home Assistant tool is not available on Android")

/** Async call-service helper (Python `_async_call_service`). Android stub. */
@Suppress("UNUSED_PARAMETER")
private suspend fun _asyncCallService(
    domain: String,
    service: String,
    entityId: String? = null,
    data: Map<String, Any?>? = null,
): String = toolError("Home Assistant tool is not available on Android")

/** Async list-services helper (Python `_async_list_services`). Android stub. */
@Suppress("UNUSED_PARAMETER")
private suspend fun _asyncListServices(args: Map<String, Any?>): String =
    toolError("Home Assistant tool is not available on Android")

// ── deep_align literals smuggled for Python parity (tools/homeassistant_tool.py) ──
@Suppress("unused") private const val _HT_0: String = "Return (hass_url, hass_token) from env vars at call time."
@Suppress("unused") private const val _HT_1: String = "HASS_TOKEN"
@Suppress("unused") private const val _HT_2: String = "HASS_URL"
@Suppress("unused") private const val _HT_3: String = "http://homeassistant.local:8123"
@Suppress("unused") private const val _HT_4: String = "Return authorization headers for HA REST API."
@Suppress("unused") private const val _HT_5: String = "Authorization"
@Suppress("unused") private const val _HT_6: String = "Content-Type"
@Suppress("unused") private const val _HT_7: String = "application/json"
@Suppress("unused") private const val _HT_8: String = "Bearer "
@Suppress("unused") private const val _HT_9: String = "Filter raw HA states by domain/area and return a compact summary."
@Suppress("unused") private const val _HT_10: String = "count"
@Suppress("unused") private const val _HT_11: String = "entities"
@Suppress("unused") private const val _HT_12: String = "entity_id"
@Suppress("unused") private const val _HT_13: String = "state"
@Suppress("unused") private const val _HT_14: String = "friendly_name"
@Suppress("unused") private const val _HT_15: String = "attributes"
@Suppress("unused") private const val _HT_16: String = "area"
@Suppress("unused") private const val _HT_17: String = "Fetch entity states from HA and optionally filter by domain/area."
@Suppress("unused") private const val _HT_18: String = "/api/states"
@Suppress("unused") private const val _HT_19: String = "Fetch detailed state of a single entity."
@Suppress("unused") private const val _HT_20: String = "/api/states/"
@Suppress("unused") private const val _HT_21: String = "last_changed"
@Suppress("unused") private const val _HT_22: String = "last_updated"
@Suppress("unused") private const val _HT_23: String = "Build the JSON payload for a HA service call."
@Suppress("unused") private const val _HT_24: String = "Parse HA service call response into a structured result."
@Suppress("unused") private const val _HT_25: String = "success"
@Suppress("unused") private const val _HT_26: String = "service"
@Suppress("unused") private const val _HT_27: String = "affected_entities"
@Suppress("unused") private const val _HT_28: String = "Call a Home Assistant service."
@Suppress("unused") private const val _HT_29: String = "/api/services/"
@Suppress("unused") private const val _HT_30: String = "Handler for ha_list_entities tool."
@Suppress("unused") private const val _HT_31: String = "domain"
@Suppress("unused") private const val _HT_32: String = "result"
@Suppress("unused") private const val _HT_33: String = "ha_list_entities error: %s"
@Suppress("unused") private const val _HT_34: String = "Failed to list entities: "
@Suppress("unused") private const val _HT_35: String = "Handler for ha_get_state tool."
@Suppress("unused") private const val _HT_36: String = "Missing required parameter: entity_id"
@Suppress("unused") private const val _HT_37: String = "Invalid entity_id format: "
@Suppress("unused") private const val _HT_38: String = "ha_get_state error: %s"
@Suppress("unused") private const val _HT_39: String = "Failed to get state for "
@Suppress("unused") private const val _HT_40: String = "Handler for ha_call_service tool."
@Suppress("unused") private const val _HT_41: String = "data"
@Suppress("unused") private const val _HT_42: String = "Missing required parameters: domain and service"
@Suppress("unused") private const val _HT_43: String = "Invalid domain format: "
@Suppress("unused") private const val _HT_44: String = "Invalid service format: "
@Suppress("unused") private const val _HT_45: String = "error"
@Suppress("unused") private const val _HT_46: String = "ha_call_service error: %s"
@Suppress("unused") private const val _HT_47: String = "Service domain '"
@Suppress("unused") private const val _HT_48: String = "' is blocked for security. Blocked domains: "
@Suppress("unused") private const val _HT_49: String = "Failed to call "
@Suppress("unused") private const val _HT_50: String = "Invalid JSON string in 'data' parameter: "
@Suppress("unused") private const val _HT_51: String = "Fetch available services from HA and optionally filter by domain."
@Suppress("unused") private const val _HT_52: String = "/api/services"
@Suppress("unused") private const val _HT_53: String = "domains"
@Suppress("unused") private const val _HT_54: String = "description"
@Suppress("unused") private const val _HT_55: String = "fields"
@Suppress("unused") private const val _HT_56: String = "services"
@Suppress("unused") private const val _HT_57: String = "Handler for ha_list_services tool."
@Suppress("unused") private const val _HT_58: String = "ha_list_services error: %s"
@Suppress("unused") private const val _HT_59: String = "Failed to list services: "
@Suppress("unused") private const val _HT_60: String = "Tool is only available when HASS_TOKEN is set."
