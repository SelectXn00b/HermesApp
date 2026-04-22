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
 */
object OsvCheck {

    private const val _TAG = "OsvCheck"
    private const val DEFAULT_ENDPOINT = "https://api.osv.dev/v1/query"
    private const val TIMEOUT_SECONDS = 10L

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val JSON = "application/json".toMediaType()

    /**
     * Check if an MCP server package has known malware advisories.
     * Returns an error message string if malware is found, or null if clean/unknown.
     */
    fun checkPackageForMalware(command: String, args: List<String>): String? {
        val ecosystem = inferEcosystem(command) ?: return null
        val (packageName, version) = parsePackageFromArgs(args, ecosystem) ?: return null
        if (packageName == null) return null

        return try {
            val malware = queryOsv(packageName, ecosystem, version)
            if (malware.isNotEmpty()) {
                val ids = malware.take(3).joinToString(", ") { it.id }
                val summaries = malware.take(3).joinToString("; ") {
                    (it.summary ?: it.id).take(100)
                }
                "BLOCKED: Package '$packageName' ($ecosystem) has known malware advisories: $ids. Details: $summaries"
            } else null
        } catch (e: Exception) {
            Log.d(_TAG, "OSV check failed for $ecosystem/$packageName (allowing): ${e.message}")
            null // Fail-open
        }
    }

    data class OsvVuln(val id: String, val summary: String?)

    private fun queryOsv(packageName: String, ecosystem: String, version: String?): List<OsvVuln> {
        val payload = JsonObject().apply {
            add("package", JsonObject().apply {
                addProperty("name", packageName)
                addProperty("ecosystem", ecosystem)
            })
            version?.let { addProperty("version", it) }
        }

        val endpoint = System.getenv("OSV_ENDPOINT") ?: DEFAULT_ENDPOINT
        val request = Request.Builder()
            .url(endpoint)
            .post(payload.toString().toRequestBody(JSON))
            .header("Content-Type", "application/json")
            .header("User-Agent", "hermes-agent-osv-check/1.0")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string() ?: return emptyList()
            val json = gson.fromJson(body, JsonObject::class.java)
            val vulns = json.getAsJsonArray("vulns") ?: return emptyList()
            return vulns.mapNotNull { el ->
                val obj = el.asJsonObject
                val id = obj.get("id")?.asString ?: return@mapNotNull null
                if (id.startsWith("MAL-")) {
                    OsvVuln(id, obj.get("summary")?.asString)
                } else null
            }
        }
    }

    private fun inferEcosystem(command: String): String? {
        val base = command.substringAfterLast("/").lowercase()
        return when (base) {
            "npx", "npx.cmd" -> "npm"
            "uvx", "uvx.cmd", "pipx" -> "PyPI"
            else -> null
        }
    }

    private fun parsePackageFromArgs(args: List<String>, ecosystem: String): Pair<String?, String?> {
        val packageToken = args.firstOrNull { it.isNotEmpty() && !it.startsWith("-") } ?: return null to null
        return when (ecosystem) {
            "npm" -> parseNpmPackage(packageToken)
            "PyPI" -> parsePypiPackage(packageToken)
            else -> packageToken to null
        }
    }

    private fun parseNpmPackage(token: String): Pair<String?, String?> {
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

    private fun parsePypiPackage(token: String): Pair<String?, String?> {
        val match = Regex("^([a-zA-Z0-9._-]+)(?:\\[[^\\]]*\\])?(?:==(.+))?$").find(token)
        return if (match != null) match.groupValues[1] to match.groupValues[2].ifEmpty { null }
        else token to null
    }


    // === Missing constants (auto-generated stubs) ===
    val _OSV_ENDPOINT = ""
    val _TIMEOUT = 0

}
