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
