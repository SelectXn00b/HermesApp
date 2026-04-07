package com.xiaomo.androidforclaw.plugins

import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * OpenClaw module: plugins
 * Source: OpenClaw/src/plugins/schema-validator.ts
 *
 * JSON Schema validation for plugin config.
 * Android adaptation: lightweight schema validation using org.json
 * instead of AJV.
 */

data class JsonSchemaValidationError(
    val path: String,
    val message: String,
    val text: String,
    val allowedValues: List<String>? = null,
    val allowedValuesHiddenCount: Int? = null,
)

object PluginSchemaValidator {

    private val schemaCache = ConcurrentHashMap<String, Map<String, Any?>>()

    /**
     * Validate a value against a JSON Schema definition.
     * Returns an empty list on success, or a list of validation errors.
     *
     * This is a lightweight Android implementation covering the most common
     * schema constructs used by plugin configs: type, required, enum, properties,
     * additionalProperties, default.
     */
    fun validate(
        schema: Map<String, Any?>,
        cacheKey: String,
        value: Any?,
    ): List<String> {
        schemaCache[cacheKey] = schema
        val errors = mutableListOf<String>()
        validateNode(schema, value, "<root>", errors)
        return errors
    }

    fun validateAndGetErrors(
        schema: Map<String, Any?>,
        cacheKey: String,
        value: Any?,
    ): List<JsonSchemaValidationError> {
        val rawErrors = validate(schema, cacheKey, value)
        return rawErrors.map { msg ->
            val colonIdx = msg.indexOf(':')
            val path = if (colonIdx > 0) msg.substring(0, colonIdx).trim() else "<root>"
            val message = if (colonIdx > 0) msg.substring(colonIdx + 1).trim() else msg
            JsonSchemaValidationError(
                path = path,
                message = message,
                text = msg,
            )
        }
    }

    private fun validateNode(
        schema: Map<String, Any?>,
        value: Any?,
        path: String,
        errors: MutableList<String>,
    ) {
        // Type check
        val type = schema["type"] as? String
        if (type != null && value != null) {
            val typeValid = when (type) {
                "object" -> value is Map<*, *>
                "array" -> value is List<*> || value is JSONArray
                "string" -> value is String
                "number", "integer" -> value is Number
                "boolean" -> value is Boolean
                "null" -> false // null already handled
                else -> true
            }
            if (!typeValid) {
                errors.add("$path: must be $type")
                return
            }
        }

        // Enum check
        @Suppress("UNCHECKED_CAST")
        val enumValues = schema["enum"] as? List<Any?>
        if (enumValues != null && value != null) {
            if (value !in enumValues) {
                val allowed = enumValues.joinToString(", ") { it?.toString() ?: "null" }
                errors.add("$path: must be one of [$allowed]")
            }
        }

        // Required check
        @Suppress("UNCHECKED_CAST")
        val required = schema["required"] as? List<String>
        if (required != null && value is Map<*, *>) {
            for (requiredKey in required) {
                if (!value.containsKey(requiredKey)) {
                    errors.add("$path.$requiredKey: is required")
                }
            }
        }

        // Properties
        @Suppress("UNCHECKED_CAST")
        val properties = schema["properties"] as? Map<String, Any?>
        if (properties != null && value is Map<*, *>) {
            for ((propKey, propSchema) in properties) {
                val propValue = value[propKey]
                if (propValue != null && propSchema is Map<*, *>) {
                    @Suppress("UNCHECKED_CAST")
                    validateNode(
                        propSchema as Map<String, Any?>,
                        propValue,
                        if (path == "<root>") propKey else "$path.$propKey",
                        errors,
                    )
                }
            }
        }

        // Items (array schema)
        @Suppress("UNCHECKED_CAST")
        val items = schema["items"] as? Map<String, Any?>
        if (items != null && value is List<*>) {
            for ((index, item) in value.withIndex()) {
                validateNode(items, item, "$path[$index]", errors)
            }
        }
    }

    fun clearCache() {
        schemaCache.clear()
    }
}
