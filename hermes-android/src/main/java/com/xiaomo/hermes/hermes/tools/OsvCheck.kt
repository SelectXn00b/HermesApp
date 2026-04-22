package com.xiaomo.hermes.hermes.tools

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * OSV malware check for MCP extension packages.
 * Ported from osv_check.py
 *
 * Before launching an MCP server via npx/uvx, queries the OSV API to check
 * if the package has any known malware advisories (MAL-* IDs).  Regular
 * CVEs are ignored — only confirmed malware is blocked.
 *
 * Fail-open: network errors allow the package to proceed.
 */

private const val _TAG_OSV = "OsvCheck"
private val _OSV_ENDPOINT: String = System.getenv("OSV_ENDPOINT") ?: "https://api.osv.dev/v1/query"
private const val _TIMEOUT = 10L  // seconds

private val _osvGson = Gson()
private val _osvJsonMedia = "application/json".toMediaType()
private val _osvClient = OkHttpClient.Builder()
    .connectTimeout(_TIMEOUT, TimeUnit.SECONDS)
    .readTimeout(_TIMEOUT, TimeUnit.SECONDS)
    .build()

/**
 * Check if an MCP server package has known malware advisories.
 *
 * Returns an error message string if malware is found, or null if clean/unknown.
 * Returns null (allow) on network errors or unrecognized commands.
 */
fun checkPackageForMalware(command: String, args: List<String>): String? {
    val ecosystem = _inferEcosystem(command) ?: return null
    val (packageName, version) = _parsePackageFromArgs(args, ecosystem)
    if (packageName == null) return null

    val malware = try {
        _queryOsv(packageName, ecosystem, version)
    } catch (e: Exception) {
        Log.d(_TAG_OSV, "OSV check failed for $ecosystem/$packageName (allowing): ${e.message}")
        return null
    }

    if (malware.isNotEmpty()) {
        val ids = malware.take(3).joinToString(", ") { it["id"] as String }
        val summaries = malware.take(3).joinToString("; ") {
            ((it["summary"] as? String) ?: (it["id"] as String)).take(100)
        }
        return "BLOCKED: Package '$packageName' ($ecosystem) has known malware advisories: $ids. Details: $summaries"
    }
    return null
}

private fun _inferEcosystem(command: String): String? {
    val base = command.substringAfterLast("/").lowercase()
    return when (base) {
        "npx", "npx.cmd" -> "npm"
        "uvx", "uvx.cmd", "pipx" -> "PyPI"
        else -> null
    }
}

private fun _parsePackageFromArgs(args: List<String>, ecosystem: String): Pair<String?, String?> {
    if (args.isEmpty()) return null to null
    val packageToken = args.firstOrNull { it.isNotEmpty() && !it.startsWith("-") } ?: return null to null
    return when (ecosystem) {
        "npm" -> _parseNpmPackage(packageToken)
        "PyPI" -> _parsePypiPackage(packageToken)
        else -> packageToken to null
    }
}

private fun _parseNpmPackage(token: String): Pair<String?, String?> {
    if (token.startsWith("@")) {
        val match = Regex("^(@[^/]+/[^@]+)(?:@(.+))?$").find(token)
        return if (match != null) match.groupValues[1] to match.groupValues[2].ifEmpty { null }
        else token to null
    }
    val atIndex = token.lastIndexOf('@')
    if (atIndex > 0) {
        val name = token.substring(0, atIndex)
        val ver = token.substring(atIndex + 1)
        return name to if (ver == "latest") null else ver
    }
    return token to null
}

private fun _parsePypiPackage(token: String): Pair<String?, String?> {
    val match = Regex("^([a-zA-Z0-9._-]+)(?:\\[[^\\]]*\\])?(?:==(.+))?$").find(token)
    return if (match != null) match.groupValues[1] to match.groupValues[2].ifEmpty { null }
    else token to null
}

private fun _queryOsv(packageName: String, ecosystem: String, version: String?): List<Map<String, Any>> {
    val payload = JsonObject().apply {
        add("package", JsonObject().apply {
            addProperty("name", packageName)
            addProperty("ecosystem", ecosystem)
        })
        version?.let { addProperty("version", it) }
    }

    val request = Request.Builder()
        .url(_OSV_ENDPOINT)
        .post(payload.toString().toRequestBody(_osvJsonMedia))
        .header("Content-Type", "application/json")
        .header("User-Agent", "hermes-agent-osv-check/1.0")
        .build()

    _osvClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) return emptyList()
        val body = response.body?.string() ?: return emptyList()
        val json = _osvGson.fromJson(body, JsonObject::class.java)
        val vulns = json.getAsJsonArray("vulns") ?: return emptyList()
        return vulns.mapNotNull { el ->
            val obj = el.asJsonObject
            val id = obj.get("id")?.asString ?: return@mapNotNull null
            if (id.startsWith("MAL-")) {
                val map = mutableMapOf<String, Any>("id" to id)
                obj.get("summary")?.asString?.let { map["summary"] = it }
                map
            } else null
        }
    }
}
