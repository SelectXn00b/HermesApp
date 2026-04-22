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

private fun _filterAndSummarize(entities: List<Any?>, vararg args: Any?): List<Any?> = emptyList()

private fun _buildServicePayload(vararg args: Any?): Map<String, Any?> = emptyMap()

private fun _parseServiceResponse(vararg args: Any?): Map<String, Any?> = emptyMap()

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
private suspend fun _asyncListEntities(args: Map<String, Any?>): String =
    toolError("Home Assistant tool is not available on Android")

/** Async get-state helper (Python `_async_get_state`). Android stub. */
@Suppress("UNUSED_PARAMETER")
private suspend fun _asyncGetState(args: Map<String, Any?>): String =
    toolError("Home Assistant tool is not available on Android")

/** Async call-service helper (Python `_async_call_service`). Android stub. */
@Suppress("UNUSED_PARAMETER")
private suspend fun _asyncCallService(args: Map<String, Any?>): String =
    toolError("Home Assistant tool is not available on Android")

/** Async list-services helper (Python `_async_list_services`). Android stub. */
@Suppress("UNUSED_PARAMETER")
private suspend fun _asyncListServices(args: Map<String, Any?>): String =
    toolError("Home Assistant tool is not available on Android")
