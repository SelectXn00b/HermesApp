package com.ai.assistance.operit.hermes

import com.ai.assistance.operit.data.model.ToolParameterSchema
import com.ai.assistance.operit.data.model.ToolPrompt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for the bridge that exports Operit ToolPrompts as OpenAI
 * tool schemas consumed by HermesAgentLoop.
 */
class OpenAiToolSchemaTest {

    private fun prompt(
        name: String,
        description: String = "d",
        details: String = "",
        notes: String = "",
        structured: List<ToolParameterSchema>? = null
    ): ToolPrompt = ToolPrompt(
        name = name,
        description = description,
        details = details,
        notes = notes,
        parametersStructured = structured
    )

    @Test fun toolPromptsToOpenAiSchemas_emptyList_returnsEmpty() {
        val out = toolPromptsToOpenAiSchemas(emptyList())
        assertTrue(out.isEmpty())
    }

    @Test fun toolPromptsToOpenAiSchemas_singleTool_producesFunctionTypeSchema() {
        val schemas = toolPromptsToOpenAiSchemas(listOf(prompt("echo")))
        assertEquals(1, schemas.size)
        val schema = schemas[0]
        assertEquals("function", schema["type"])
        val fn = schema["function"] as Map<*, *>
        assertEquals("echo", fn["name"])
        assertEquals("d", fn["description"])
    }

    @Test fun toolPromptsToOpenAiSchemas_concatenatesDescriptionDetailsNotes() {
        val schemas = toolPromptsToOpenAiSchemas(
            listOf(prompt(name = "t", description = "A", details = "B", notes = "C"))
        )
        val fn = schemas[0]["function"] as Map<*, *>
        assertEquals("A\nB\nC", fn["description"])
    }

    @Test fun toolPromptsToOpenAiSchemas_skipsBlankDetailsAndNotes() {
        val schemas = toolPromptsToOpenAiSchemas(
            listOf(prompt(name = "t", description = "A", details = "", notes = ""))
        )
        val fn = schemas[0]["function"] as Map<*, *>
        assertEquals("A", fn["description"])
    }

    @Test fun toolPromptsToOpenAiSchemas_structuredParams_emitsObjectSchema() {
        val schemas = toolPromptsToOpenAiSchemas(
            listOf(
                prompt(
                    name = "x",
                    structured = listOf(
                        ToolParameterSchema(name = "path", type = "string", description = "p", required = true),
                        ToolParameterSchema(name = "n", type = "integer", description = "count", required = false, default = "1")
                    )
                )
            )
        )
        val fn = schemas[0]["function"] as Map<*, *>
        val params = fn["parameters"] as Map<*, *>
        assertEquals("object", params["type"])

        @Suppress("UNCHECKED_CAST")
        val properties = params["properties"] as Map<String, Map<String, Any?>>
        assertEquals(setOf("path", "n"), properties.keys)
        assertEquals("string", properties["path"]!!["type"])
        assertEquals("p", properties["path"]!!["description"])
        assertNull(properties["path"]!!["default"])
        assertEquals("integer", properties["n"]!!["type"])
        assertEquals("1", properties["n"]!!["default"])

        @Suppress("UNCHECKED_CAST")
        val required = params["required"] as List<String>
        assertEquals(listOf("path"), required)
    }

    @Test fun toolPromptsToOpenAiSchemas_noRequired_omitsRequiredKey() {
        val schemas = toolPromptsToOpenAiSchemas(
            listOf(
                prompt(
                    name = "x",
                    structured = listOf(
                        ToolParameterSchema(name = "q", type = "string", description = "d", required = false)
                    )
                )
            )
        )
        val fn = schemas[0]["function"] as Map<*, *>
        val params = fn["parameters"] as Map<*, *>
        assertFalse(params.containsKey("required"))
    }

    @Test fun toolPromptsToOpenAiSchemas_nullStructured_emitsEmptyProperties() {
        val schemas = toolPromptsToOpenAiSchemas(listOf(prompt("x", structured = null)))
        val fn = schemas[0]["function"] as Map<*, *>
        val params = fn["parameters"] as Map<*, *>
        val properties = params["properties"] as Map<*, *>
        assertTrue(properties.isEmpty())
    }

    @Test fun extractToolNames_returnsNamesInInsertionOrder() {
        val schemas = toolPromptsToOpenAiSchemas(
            listOf(prompt("alpha"), prompt("beta"), prompt("gamma"))
        )
        val names = extractToolNames(schemas)
        assertEquals(listOf("alpha", "beta", "gamma"), names.toList())
    }

    @Test fun extractToolNames_ignoresMalformedSchemas() {
        val malformed: List<Map<String, Any?>> = listOf(
            mapOf("type" to "function"),
            mapOf("type" to "function", "function" to "notAMap"),
            mapOf("type" to "function", "function" to mapOf("name" to 42)),
            mapOf("type" to "function", "function" to mapOf("name" to "valid"))
        )
        val names = extractToolNames(malformed)
        assertEquals(setOf("valid"), names)
    }

    @Test fun extractToolNames_dedupesDuplicates() {
        val dup = listOf(
            mapOf("type" to "function", "function" to mapOf("name" to "a")),
            mapOf("type" to "function", "function" to mapOf("name" to "a")),
            mapOf("type" to "function", "function" to mapOf("name" to "b"))
        )
        assertEquals(setOf("a", "b"), extractToolNames(dup))
    }

    @Test fun endToEnd_schemaRoundtripsThroughExtractToolNames() {
        val prompts = listOf(prompt("first"), prompt("second"))
        val schemas = toolPromptsToOpenAiSchemas(prompts)
        val names = extractToolNames(schemas)
        assertNotNull(names)
        assertEquals(prompts.map { it.name }.toSet(), names)
    }
}
