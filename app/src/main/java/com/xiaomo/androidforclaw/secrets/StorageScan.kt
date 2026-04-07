package com.xiaomo.androidforclaw.secrets

/**
 * OpenClaw Source Reference:
 * - src/secrets/storage-scan.ts
 * - src/secrets/auth-store-paths.ts
 *
 * Android adaptation: file scanning utilities for secret stores.
 */

import org.json.JSONObject
import java.io.File

/**
 * Parse an env assignment value (strips quotes).
 * Aligned with TS parseEnvAssignmentValue.
 */
fun parseEnvAssignmentValue(raw: String): String =
    parseEnvValue(raw)

/**
 * Read a JSON object from a file if it exists and is valid.
 * Aligned with TS readJsonObjectIfExists.
 */
fun readJsonObjectIfExists(
    filePath: String,
    maxBytes: Long? = null,
    requireRegularFile: Boolean = false
): ReadJsonObjectResult {
    val file = File(filePath)
    if (!file.exists()) return ReadJsonObjectResult(value = null)
    try {
        if (requireRegularFile && !file.isFile) {
            return ReadJsonObjectResult(
                value = null,
                error = "Refusing to read non-regular file: $filePath"
            )
        }
        if (maxBytes != null && file.length() > maxBytes) {
            return ReadJsonObjectResult(
                value = null,
                error = "Refusing to read oversized JSON (${file.length()} bytes): $filePath"
            )
        }
        val raw = file.readText()
        val parsed = JSONObject(raw)
        val map = mutableMapOf<String, Any?>()
        for (key in parsed.keys()) {
            map[key] = parsed.opt(key)
        }
        return ReadJsonObjectResult(value = map)
    } catch (err: Exception) {
        return ReadJsonObjectResult(
            value = null,
            error = err.message ?: err.toString()
        )
    }
}

data class ReadJsonObjectResult(
    val value: Map<String, Any?>?,
    val error: String? = null
)

/**
 * List auth profile store paths for a given state directory.
 * Android adaptation of TS listAuthProfileStorePaths.
 */
fun listAuthProfileStorePaths(stateDir: String): List<String> {
    val paths = mutableSetOf<String>()
    val agentsRoot = File(stateDir, "agents")

    // Default main agent store
    paths.add(File(agentsRoot, "main/agent/auth-profiles.json").absolutePath)

    // Scan for additional agent directories
    if (agentsRoot.exists() && agentsRoot.isDirectory) {
        agentsRoot.listFiles()?.filter { it.isDirectory }?.forEach { entry ->
            paths.add(File(entry, "agent/auth-profiles.json").absolutePath)
        }
    }

    return paths.toList()
}

/**
 * List legacy auth.json paths.
 * Aligned with TS listLegacyAuthJsonPaths.
 */
fun listLegacyAuthJsonPaths(stateDir: String): List<String> {
    val out = mutableListOf<String>()
    val agentsRoot = File(stateDir, "agents")
    if (!agentsRoot.exists()) return out

    agentsRoot.listFiles()?.filter { it.isDirectory }?.forEach { entry ->
        val candidate = File(entry, "agent/auth.json")
        if (candidate.exists()) {
            out.add(candidate.absolutePath)
        }
    }
    return out
}

/**
 * List agent models.json paths.
 * Aligned with TS listAgentModelsJsonPaths.
 */
fun listAgentModelsJsonPaths(stateDir: String): List<String> {
    val paths = mutableSetOf<String>()
    val resolvedStateDir = File(stateDir)

    // Default main agent
    paths.add(File(resolvedStateDir, "agents/main/agent/models.json").absolutePath)

    // Scan agent directories
    val agentsRoot = File(resolvedStateDir, "agents")
    if (agentsRoot.exists() && agentsRoot.isDirectory) {
        agentsRoot.listFiles()?.filter { it.isDirectory }?.forEach { entry ->
            paths.add(File(entry, "agent/models.json").absolutePath)
        }
    }

    return paths.toList()
}
