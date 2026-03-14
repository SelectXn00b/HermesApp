package com.xiaomo.androidforclaw.agent.skills

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/skills/(all)
 *
 * AndroidForClaw adaptation: parse SKILL.md metadata and requirements.
 */


import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject

/**
 * Skill Document Parser — the single unified parser for SKILL.md files.
 * Supports AgentSkills.io format with full metadata.openclaw field extraction.
 *
 * Format specification:
 * ---
 * name: skill-name
 * description: Skill description
 * metadata: { "openclaw": { ... } }
 * ---
 * # Markdown Content
 */
object SkillParser {
    private const val TAG = "SkillParser"
    private val gson = Gson()

    /**
     * Parse Skill document
     *
     * @param content Full content of SKILL.md file
     * @param filePath Optional file path for diagnostics
     * @return SkillDocument
     * @throws IllegalArgumentException If format is incorrect
     */
    fun parse(content: String, filePath: String = ""): SkillDocument {
        try {
            // 1. Split frontmatter and body
            val (frontmatter, body) = splitFrontmatter(content)

            // 2. Parse frontmatter fields
            val name = extractYamlField(frontmatter, "name")
            val description = extractYamlField(frontmatter, "description")
            val metadataJson = extractYamlField(frontmatter, "metadata")

            // 3. Validate required fields
            if (name.isEmpty()) {
                throw IllegalArgumentException("Missing required field: name")
            }
            if (description.isEmpty()) {
                throw IllegalArgumentException("Missing required field: description")
            }

            // 4. Parse metadata
            val metadata = parseMetadata(metadataJson)

            return SkillDocument(
                name = name,
                description = description,
                metadata = metadata,
                content = body,
                filePath = filePath
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse skill document: $filePath", e)
            throw IllegalArgumentException("Invalid skill format: ${e.message}", e)
        }
    }

    /**
     * Validate Skill document format
     *
     * @return null on success, error message on failure
     */
    fun validate(content: String): String? {
        return try {
            parse(content)
            null
        } catch (e: Exception) {
            e.message
        }
    }

    // ==================== Frontmatter Splitting ====================

    /**
     * Split YAML frontmatter and Markdown body
     */
    private fun splitFrontmatter(content: String): Pair<String, String> {
        val parts = content.split(Regex("^---\\s*$", RegexOption.MULTILINE))

        if (parts.size < 3) {
            throw IllegalArgumentException(
                "Invalid format: missing frontmatter delimiters (---)"
            )
        }

        val frontmatter = parts[1].trim()
        val body = parts.drop(2).joinToString("---").trim()

        return Pair(frontmatter, body)
    }

    // ==================== YAML Field Extraction ====================

    /**
     * Extract YAML field value
     *
     * Supported formats:
     * 1. Single line: name: value
     * 2. Single-line JSON: metadata: { "openclaw": { "always": true } }
     * 3. Multi-line JSON (brace counting):
     *    metadata:
     *      {
     *        "openclaw": { ... }
     *      }
     */
    private fun extractYamlField(yaml: String, field: String): String {
        // Try to match single-line format: field: value
        val singleLineRegex = Regex("$field:\\s*([^\\n{]+)")
        val singleLineMatch = singleLineRegex.find(yaml)
        if (singleLineMatch != null) {
            val value = singleLineMatch.groupValues[1].trim()
            // If not empty and remaining text doesn't start with {, it's a simple value
            if (value.isNotEmpty() && !yaml.substring(singleLineMatch.range.last).trimStart().startsWith("{")) {
                return value
            }
        }

        // Try to match JSON value: field: { ... } or field:\n  { ... }
        val fieldRegex = Regex("$field:\\s*")
        val fieldMatch = fieldRegex.find(yaml) ?: return ""

        val jsonStart = yaml.indexOf('{', fieldMatch.range.last)
        if (jsonStart == -1) return ""

        // Brace counting to correctly extract nested JSON
        var braceCount = 0
        var jsonEnd = jsonStart
        while (jsonEnd < yaml.length) {
            when (yaml[jsonEnd]) {
                '{' -> braceCount++
                '}' -> {
                    braceCount--
                    if (braceCount == 0) {
                        val jsonStr = yaml.substring(jsonStart, jsonEnd + 1)
                        return jsonStr.replace(Regex("\\s+"), " ").trim()
                    }
                }
            }
            jsonEnd++
        }

        return ""
    }

    // ==================== Metadata Parsing ====================

    /**
     * Parse metadata JSON into SkillMetadata
     * Extracts all metadata.openclaw fields aligned with OpenClaw.
     */
    private fun parseMetadata(json: String): SkillMetadata {
        if (json.isEmpty()) {
            return SkillMetadata()
        }

        return try {
            val jsonObj = gson.fromJson(json, JsonObject::class.java)
            val openclaw = jsonObj.getAsJsonObject("openclaw")
                ?: return SkillMetadata()

            SkillMetadata(
                always = openclaw.get("always")?.asBoolean ?: false,
                skillKey = openclaw.get("skillKey")?.asString,
                primaryEnv = openclaw.get("primaryEnv")?.asString,
                emoji = openclaw.get("emoji")?.asString,
                homepage = openclaw.get("homepage")?.asString,
                os = jsonArrayToStringList(openclaw.getAsJsonArray("os")),
                requires = parseRequires(openclaw),
                install = parseInstallSpecs(openclaw.getAsJsonArray("install"))
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse metadata JSON: $json", e)
            SkillMetadata()
        }
    }

    /**
     * Parse requires field
     */
    private fun parseRequires(openclaw: JsonObject): SkillRequires? {
        val requiresObj = openclaw.getAsJsonObject("requires") ?: return null

        return try {
            SkillRequires(
                bins = jsonArrayToStringList(requiresObj.getAsJsonArray("bins")),
                anyBins = jsonArrayToStringList(requiresObj.getAsJsonArray("anyBins")),
                env = jsonArrayToStringList(requiresObj.getAsJsonArray("env")),
                config = jsonArrayToStringList(requiresObj.getAsJsonArray("config"))
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse requires", e)
            null
        }
    }

    /**
     * Parse install specifications array
     */
    private fun parseInstallSpecs(array: JsonArray?): List<SkillInstallSpec>? {
        if (array == null || array.size() == 0) return null

        return array.mapNotNull { element ->
            try {
                if (!element.isJsonObject) return@mapNotNull null
                val obj = element.asJsonObject

                val kindStr = obj.get("kind")?.asString ?: return@mapNotNull null
                val kind = when (kindStr.lowercase()) {
                    "brew" -> InstallKind.BREW
                    "node" -> InstallKind.NODE
                    "go" -> InstallKind.GO
                    "uv" -> InstallKind.UV
                    "download" -> InstallKind.DOWNLOAD
                    "apk" -> InstallKind.APK
                    else -> return@mapNotNull null
                }

                SkillInstallSpec(
                    id = obj.get("id")?.asString,
                    kind = kind,
                    label = obj.get("label")?.asString,
                    bins = jsonArrayToStringList(obj.getAsJsonArray("bins")),
                    os = jsonArrayToStringList(obj.getAsJsonArray("os")),
                    formula = obj.get("formula")?.asString,
                    `package` = obj.get("package")?.asString,
                    module = obj.get("module")?.asString,
                    url = obj.get("url")?.asString,
                    archive = obj.get("archive")?.asString,
                    extract = obj.get("extract")?.asBoolean,
                    stripComponents = obj.get("stripComponents")?.asInt,
                    targetDir = obj.get("targetDir")?.asString
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse install spec", e)
                null
            }
        }.takeIf { it.isNotEmpty() }
    }

    // ==================== Utility ====================

    /**
     * Convert JsonArray to List<String>, returns empty list for null
     */
    private fun jsonArrayToStringList(array: JsonArray?): List<String> {
        if (array == null) return emptyList()
        return array.mapNotNull { it.asString }
    }
}
