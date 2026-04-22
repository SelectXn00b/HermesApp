package com.xiaomo.hermes.hermes.agent

/**
 * Helpers for translating OpenAI-style tool schemas to Gemini's schema subset.
 *
 * Ported from agent/gemini_schema.py
 */

// Gemini's `FunctionDeclaration.parameters` field accepts the `Schema`
// object, which is only a subset of OpenAPI 3.0 / JSON Schema.  Strip fields
// outside that subset before sending Hermes tool schemas to Google.
private val _GEMINI_SCHEMA_ALLOWED_KEYS = setOf(
    "type",
    "format",
    "title",
    "description",
    "nullable",
    "enum",
    "maxItems",
    "minItems",
    "properties",
    "required",
    "minProperties",
    "maxProperties",
    "minLength",
    "maxLength",
    "pattern",
    "example",
    "anyOf",
    "propertyOrdering",
    "default",
    "items",
    "minimum",
    "maximum")

/**
 * Return a Gemini-compatible copy of a tool parameter schema.
 *
 * Hermes tool schemas are OpenAI-flavored JSON Schema and may contain keys
 * such as `$schema` or `additionalProperties` that Google's Gemini
 * `Schema` object rejects.  This helper preserves the documented Gemini
 * subset and recursively sanitizes nested `properties` / `items` /
 * `anyOf` definitions.
 */
fun sanitizeGeminiSchema(schema: Any?): Map<String, Any?> {
    if (schema !is Map<*, *>) return emptyMap()

    val cleaned = LinkedHashMap<String, Any?>()
    for ((rawKey, value) in schema) {
        val key = rawKey as? String ?: continue
        if (key !in _GEMINI_SCHEMA_ALLOWED_KEYS) continue
        when (key) {
            "properties" -> {
                if (value !is Map<*, *>) continue
                val props = LinkedHashMap<String, Any?>()
                for ((propName, propSchema) in value) {
                    val name = propName as? String ?: continue
                    props[name] = sanitizeGeminiSchema(propSchema)
                }
                cleaned[key] = props
            }
            "items" -> cleaned[key] = sanitizeGeminiSchema(value)
            "anyOf" -> {
                if (value !is List<*>) continue
                cleaned[key] = value.filterIsInstance<Map<*, *>>().map { sanitizeGeminiSchema(it) }
            }
            else -> cleaned[key] = value
        }
    }
    return cleaned
}

/** Normalize tool parameters to a valid Gemini object schema. */
fun sanitizeGeminiToolParameters(parameters: Any?): Map<String, Any?> {
    val cleaned = sanitizeGeminiSchema(parameters)
    if (cleaned.isEmpty()) {
        return mapOf("type" to "object", "properties" to emptyMap<String, Any?>())
    }
    return cleaned
}
