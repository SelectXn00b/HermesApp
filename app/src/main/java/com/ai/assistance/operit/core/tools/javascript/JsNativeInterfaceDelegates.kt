package com.ai.assistance.operit.core.tools.javascript

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.Base64
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.BinaryResultData
import com.ai.assistance.operit.core.tools.BooleanResultData
import com.ai.assistance.operit.core.tools.IntResultData
import com.ai.assistance.operit.core.tools.SandboxScriptExecutionResultData
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.ToolResultData
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.preferences.EnvPreferences
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.OperitPaths
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.json.JSONObject

internal object JsNativeInterfaceDelegates {
    private const val TAG = "JsNativeInterface"
    private data class ParsedToolCall(
        val params: Map<String, String>,
        val fullToolName: String,
        val aiTool: AITool
    )

    private data class SerializedToolResultData(
        val data: JsonElement,
        val dataType: String? = null
    )

    private fun buildToolErrorJson(message: String): String {
        return Json.encodeToString(
            JsonElement.serializer(),
            buildJsonObject {
                put("success", JsonPrimitive(false))
                put("error", JsonPrimitive(message))
            }
        )
    }

    private fun parseToolCall(
        toolType: String,
        toolName: String,
        paramsJson: String
    ): ParsedToolCall {
        val normalizedToolName = toolName.trim()
        if (normalizedToolName.isEmpty()) {
            throw IllegalArgumentException("Tool name cannot be empty")
        }

        val params = mutableMapOf<String, String>()
        val jsonObject = JSONObject(paramsJson)
        jsonObject.keys().forEach { key ->
            params[key] = jsonObject.opt(key)?.toString() ?: ""
        }

        val fullToolName =
            if (toolType.isNotEmpty() && toolType != "default") {
                "$toolType:$normalizedToolName"
            } else {
                normalizedToolName
            }

        val toolParameters = params.map { (name, value) -> ToolParameter(name = name, value = value) }
        val aiTool = AITool(name = fullToolName, parameters = toolParameters)
        return ParsedToolCall(
            params = params,
            fullToolName = fullToolName,
            aiTool = aiTool
        )
    }

    private fun serializeToolExecutionResult(
        result: ToolResult,
        binaryDataRegistry: ConcurrentHashMap<String, ByteArray>,
        binaryHandlePrefix: String,
        binaryDataThreshold: Int
    ): String {
        val serializedData =
            serializeToolResultData(
                resultData = result.result,
                binaryDataRegistry = binaryDataRegistry,
                binaryHandlePrefix = binaryHandlePrefix,
                binaryDataThreshold = binaryDataThreshold
            )

        return Json.encodeToString(
            JsonElement.serializer(),
            buildJsonObject {
                put("success", JsonPrimitive(result.success))
                if (!result.success) {
                    put("error", JsonPrimitive(result.error ?: "Unknown error"))
                }
                put("data", serializedData.data)
                serializedData.dataType?.let { put("dataType", JsonPrimitive(it)) }
            }
        )
    }

    private fun serializeToolResultData(
        resultData: ToolResultData,
        binaryDataRegistry: ConcurrentHashMap<String, ByteArray>,
        binaryHandlePrefix: String,
        binaryDataThreshold: Int
    ): SerializedToolResultData {
        return when (resultData) {
            is BinaryResultData -> {
                if (resultData.value.size > binaryDataThreshold) {
                    val handle = UUID.randomUUID().toString()
                    binaryDataRegistry[handle] = resultData.value
                    AppLogger.d(TAG, "Stored large binary data with handle: $handle")
                    SerializedToolResultData(
                        data = JsonPrimitive("$binaryHandlePrefix$handle"),
                        dataType = "base64"
                    )
                } else {
                    SerializedToolResultData(
                        data = JsonPrimitive(Base64.encodeToString(resultData.value, Base64.NO_WRAP)),
                        dataType = "base64"
                    )
                }
            }
            is StringResultData -> SerializedToolResultData(data = JsonPrimitive(resultData.value))
            is BooleanResultData -> SerializedToolResultData(data = JsonPrimitive(resultData.value))
            is IntResultData -> SerializedToolResultData(data = JsonPrimitive(resultData.value))
            is SandboxScriptExecutionResultData -> {
                val jsonData =
                    Json.parseToJsonElement(
                        Json.encodeToString(SandboxScriptExecutionResultData.serializer(), resultData)
                    )
                SerializedToolResultData(data = jsonData)
            }
            else -> {
                val jsonString = resultData.toJson()
                val jsonData =
                    try {
                        Json.parseToJsonElement(jsonString)
                    } catch (_e: Exception) {
                        JsonPrimitive(jsonString)
                    }
                SerializedToolResultData(data = jsonData)
            }
        }
    }

    private inline fun <T> guard(
        fallback: T,
        failureMessage: String,
        block: () -> T
    ): T {
        return try {
            block()
        } catch (e: Exception) {
            AppLogger.e(TAG, failureMessage, e)
            fallback
        }
    }

    private fun normalizeNonBlank(value: String): String? {
        return value.trim().takeIf { it.isNotBlank() }
    }

    private fun parseBooleanFlag(value: String): Boolean {
        return when (value.trim().lowercase()) {
            "1", "true", "yes", "y", "on" -> true
            else -> false
        }
    }

    private fun applyEnvValue(
        preferences: EnvPreferences,
        key: String,
        value: String?
    ) {
        val normalizedKey = normalizeNonBlank(key) ?: return
        val normalizedValue = value?.trim().orEmpty()
        if (normalizedValue.isBlank()) {
            preferences.removeEnv(normalizedKey)
        } else {
            preferences.setEnv(normalizedKey, normalizedValue)
        }
    }

    fun setEnv(context: Context, key: String, value: String?) {
        guard(Unit, "Error writing environment variable from JS: $key") {
            applyEnvValue(EnvPreferences.getInstance(context), key, value)
        }
    }

    fun getEnv(
        context: Context,
        key: String,
        envOverrides: Map<String, String>
    ): String {
        return guard("", "Error reading environment variable from JS: $key") {
            val normalizedKey = normalizeNonBlank(key) ?: return@guard ""
            envOverrides[normalizedKey]?.takeIf { it.isNotEmpty() }
                ?: EnvPreferences.getInstance(context).getEnv(normalizedKey)
                ?: ""
        }
    }

    fun setEnvs(context: Context, valuesJson: String) {
        guard(Unit, "Error batch-writing environment variables from JS") {
            if (valuesJson.isBlank()) {
                return@guard
            }
            val payload = JSONObject(valuesJson)
            val preferences = EnvPreferences.getInstance(context)
            payload.keys().forEach { rawKey ->
                applyEnvValue(preferences, rawKey, payload.opt(rawKey)?.toString())
            }
        }
    }

    fun isPackageImported(packageManager: PackageManager, packageName: String): Boolean {
        return guard(false, "Error checking package imported from JS: $packageName") {
            normalizeNonBlank(packageName)?.let(packageManager::isPackageEnabled) ?: false
        }
    }

    fun importPackage(packageManager: PackageManager, packageName: String): String {
        return guard("Error: package import failed", "Error importing package from JS: $packageName") {
            val normalized = normalizeNonBlank(packageName) ?: return@guard "Package name is required"
            packageManager.enablePackage(normalized)
        }
    }

    fun removePackage(packageManager: PackageManager, packageName: String): String {
        return guard("Error: package removal failed", "Error removing package from JS: $packageName") {
            val normalized = normalizeNonBlank(packageName) ?: return@guard "Package name is required"
            packageManager.disablePackage(normalized)
        }
    }

    fun usePackage(packageManager: PackageManager, packageName: String): String {
        return guard("Error: package activation failed", "Error using package from JS: $packageName") {
            val normalized = normalizeNonBlank(packageName) ?: return@guard "Package name is required"
            packageManager.usePackage(normalized)
        }
    }

    fun listImportedPackagesJson(packageManager: PackageManager): String {
        return guard("[]", "Error listing imported packages from JS") {
            Json.encodeToString(
                ListSerializer(String.serializer()),
                packageManager.getEnabledPackageNames()
            )
        }
    }

    fun resolveToolName(
        packageManager: PackageManager,
        packageName: String,
        subpackageId: String,
        toolName: String,
        preferImported: String
    ): String {
        return guard(
            fallback = toolName.trim(),
            failureMessage = "Error resolving tool name from JS: package=$packageName, subpackage=$subpackageId, tool=$toolName"
        ) {
            val normalizedTool = normalizeNonBlank(toolName) ?: return@guard ""
            if (normalizedTool.contains(":")) {
                return@guard normalizedTool
            }

            val preferEnabledBool = !preferImported.equals("false", ignoreCase = true)
            val resolvedPackageName =
                normalizeNonBlank(packageName)?.let { candidate ->
                    packageManager.findPreferredPackageNameForSubpackageId(
                        candidate,
                        preferEnabled = preferEnabledBool
                    ) ?: candidate
                } ?: normalizeNonBlank(subpackageId)?.let { candidate ->
                    packageManager.findPreferredPackageNameForSubpackageId(
                        candidate,
                        preferEnabled = preferEnabledBool
                    ) ?: candidate
                }.orEmpty()

            if (resolvedPackageName.isBlank()) {
                normalizedTool
            } else {
                "$resolvedPackageName:$normalizedTool"
            }
        }
    }

    fun readToolPkgResource(
        context: Context,
        packageManager: PackageManager,
        packageNameOrSubpackageId: String,
        resourceKey: String,
        outputFileName: String,
        internal: String
    ): String {
        return guard(
            fallback = "",
            failureMessage = "Error reading toolpkg resource from JS: package/subpackage=$packageNameOrSubpackageId, resource=$resourceKey"
        ) {
            val target = normalizeNonBlank(packageNameOrSubpackageId) ?: return@guard ""
            val key = normalizeNonBlank(resourceKey) ?: return@guard ""
            val fileName = normalizeNonBlank(outputFileName)
                ?: packageManager.getToolPkgResourceOutputFileName(
                    packageNameOrSubpackageId = target,
                    resourceKey = key,
                    preferEnabledContainer = true
                )
                ?: "$key.bin"
            val safeName = fileName.substringAfterLast('/').substringAfterLast('\\').ifBlank { "$key.bin" }
            val outputDir =
                if (parseBooleanFlag(internal)) {
                    OperitPaths.cleanOnExitInternalDir(context)
                } else {
                    OperitPaths.cleanOnExitDir()
                }

            val outputFile = File(outputDir, safeName)
            val copied =
                packageManager.copyToolPkgResourceToFile(target, key, outputFile) ||
                    packageManager.copyToolPkgResourceToFileBySubpackageId(
                        subpackageId = target,
                        resourceKey = key,
                        destinationFile = outputFile,
                        preferEnabledContainer = true
                    )
            if (copied) outputFile.absolutePath else ""
        }
    }

    fun readToolPkgTextResource(
        packageManager: PackageManager,
        packageNameOrSubpackageId: String,
        resourcePath: String
    ): String {
        return guard(
            fallback = "",
            failureMessage = "Error reading toolpkg text resource from JS: package/subpackage=$packageNameOrSubpackageId, path=$resourcePath"
        ) {
            val target = normalizeNonBlank(packageNameOrSubpackageId) ?: return@guard ""
            val path = normalizeNonBlank(resourcePath) ?: return@guard ""
            packageManager.readToolPkgTextResource(
                packageNameOrSubpackageId = target,
                resourcePath = path
            ) ?: ""
        }
    }

    fun measureComposeText(context: Context, payloadJson: String): String {
        val payload = JSONObject(payloadJson)
        val text = payload.optString("text")
        if (text.isEmpty()) {
            return JSONObject()
                .put("width", 0)
                .put("height", 0)
                .toString()
        }

        val fontSize = payload.optDouble("fontSize", 10.0).toFloat()
        val maxWidth = payload.optInt("maxWidth", -1)
        require(maxWidth > 0) { "measureText requires maxWidth" }

        val maxHeight =
            if (payload.has("maxHeight")) payload.optInt("maxHeight", -1).takeIf { it > 0 } else null
        val minWidth =
            if (payload.has("minWidth")) payload.optInt("minWidth", 0).takeIf { it >= 0 } else null
        val minHeight =
            if (payload.has("minHeight")) payload.optInt("minHeight", 0).takeIf { it >= 0 } else null
        val maxLines = payload.optInt("maxLines", Int.MAX_VALUE).takeIf { it > 0 } ?: Int.MAX_VALUE
        val overflow = payload.optString("overflow", "clip").trim().lowercase()

        val scaledDensity = context.resources.displayMetrics.scaledDensity
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        paint.textSize = fontSize * scaledDensity

        val builder =
            StaticLayout.Builder.obtain(text, 0, text.length, paint, maxWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false)
                .setMaxLines(maxLines)

        if (overflow == "ellipsis") {
            builder.setEllipsize(TextUtils.TruncateAt.END)
        }

        val layout = builder.build()
        var width = 0f
        for (i in 0 until layout.lineCount) {
            width = maxOf(width, layout.getLineWidth(i))
        }
        var height = layout.height.toFloat()

        if (minWidth != null) {
            width = maxOf(width, minWidth.toFloat())
        }
        if (minHeight != null) {
            height = maxOf(height, minHeight.toFloat())
        }
        if (maxHeight != null) {
            height = minOf(height, maxHeight.toFloat())
        }
        if (width > maxWidth) {
            width = maxWidth.toFloat()
        }

        return JSONObject()
            .put("width", width)
            .put("height", height)
            .toString()
    }

    fun decompress(
        data: String,
        algorithm: String,
        binaryDataRegistry: ConcurrentHashMap<String, ByteArray>,
        binaryHandlePrefix: String
    ): String {
        return try {
            if (algorithm.lowercase() != "deflate") {
                throw IllegalArgumentException("Unsupported algorithm: $algorithm. Only 'deflate' is supported.")
            }

            val compressedData: ByteArray =
                if (data.startsWith(binaryHandlePrefix)) {
                    val handle = data.substring(binaryHandlePrefix.length)
                    binaryDataRegistry.remove(handle)
                        ?: throw Exception("Invalid or expired binary handle: $handle")
                } else {
                    Base64.decode(data, Base64.NO_WRAP)
                }

            if (compressedData.isEmpty()) {
                return ""
            }

            val inflater = java.util.zip.Inflater(true)
            inflater.setInput(compressedData)
            val outputStream = ByteArrayOutputStream()
            val buffer = ByteArray(1024)

            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count == 0 && inflater.needsInput()) {
                    throw java.util.zip.DataFormatException("Input is incomplete or corrupt")
                }
                outputStream.write(buffer, 0, count)
            }

            outputStream.close()
            inflater.end()

            outputStream.toByteArray().toString(Charsets.UTF_8)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Native decompress operation failed: ${e.message}", e)
            "{\"nativeError\":\"${e.message?.replace("\"", "'")}\"}"
        }
    }

    fun callToolSync(
        toolHandler: AIToolHandler,
        toolType: String,
        toolName: String,
        paramsJson: String,
        binaryDataRegistry: ConcurrentHashMap<String, ByteArray>,
        binaryHandlePrefix: String,
        binaryDataThreshold: Int
    ): String {
        if (toolName.trim().isEmpty()) {
            AppLogger.e(TAG, "Tool name cannot be empty")
            return "Error: Tool name cannot be empty"
        }

        return try {
            val parsed = parseToolCall(toolType, toolName, paramsJson)
            AppLogger.d(TAG, "[Sync] JavaScript tool call: ${parsed.fullToolName} with params: ${parsed.params}")
            val result = toolHandler.executeTool(parsed.aiTool)
            if (result.success) {
                val resultString = result.result.toString()
                AppLogger.d(
                    TAG,
                    "[Sync] Tool execution succeeded: ${resultString.take(1000)}${if (resultString.length > 1000) "..." else ""}"
                )
            } else {
                AppLogger.e(TAG, "[Sync] Tool execution failed: ${result.error}")
            }

            serializeToolExecutionResult(
                result = result,
                binaryDataRegistry = binaryDataRegistry,
                binaryHandlePrefix = binaryHandlePrefix,
                binaryDataThreshold = binaryDataThreshold
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "[Sync] Error in tool call: ${e.message}", e)
            buildToolErrorJson("Error: ${e.message}")
        }
    }

    fun callToolAsync(
        toolHandler: AIToolHandler,
        callbackId: String,
        toolType: String,
        toolName: String,
        paramsJson: String,
        binaryDataRegistry: ConcurrentHashMap<String, ByteArray>,
        binaryHandlePrefix: String,
        binaryDataThreshold: Int,
        sendToolResult: (callbackId: String, result: String, isError: Boolean) -> Unit
    ) {
        val parsed =
            try {
                parseToolCall(toolType, toolName, paramsJson)
            } catch (e: Exception) {
                AppLogger.e(TAG, "[Async] Error preparing tool call: ${e.message}", e)
                val rawMessage = e.message?.trim().orEmpty()
                val finalMessage =
                    if (rawMessage.equals("Tool name cannot be empty", ignoreCase = true)) {
                        "Tool name cannot be empty"
                    } else {
                        "Error: ${if (rawMessage.isBlank()) "Unknown error" else rawMessage}"
                    }
                sendToolResult(
                    callbackId,
                    buildToolErrorJson(finalMessage),
                    true
                )
                return
            }

        AppLogger.d(
            TAG,
            "[Async] JavaScript tool call: ${parsed.fullToolName} with params: ${parsed.params}, callbackId: $callbackId"
        )

        Thread {
            try {
                val result = toolHandler.executeTool(parsed.aiTool)

                if (result.success) {
                    val resultString = result.result.toString()
                    AppLogger.d(
                        TAG,
                        "[Async] Tool execution succeeded: ${resultString.take(1000)}${if (resultString.length > 1000) "..." else ""}"
                    )
                } else {
                    AppLogger.e(TAG, "[Async] Tool execution failed: ${result.error}")
                }

                val resultJson =
                    serializeToolExecutionResult(
                        result = result,
                        binaryDataRegistry = binaryDataRegistry,
                        binaryHandlePrefix = binaryHandlePrefix,
                        binaryDataThreshold = binaryDataThreshold
                    )
                sendToolResult(callbackId, resultJson, !result.success)
            } catch (e: Exception) {
                AppLogger.e(TAG, "[Async] Error in async tool execution: ${e.message}", e)
                sendToolResult(
                    callbackId,
                    buildToolErrorJson("Error: ${e.message}"),
                    true
                )
            }
        }.start()
    }

    fun buildToolResultCallbackScript(callbackId: String, result: String, isError: Boolean): String {
        val trimmedResult = result.trim()
        val isJsonLiteral =
            (trimmedResult.startsWith("{") && trimmedResult.endsWith("}")) ||
                (trimmedResult.startsWith("[") && trimmedResult.endsWith("]")) ||
                (trimmedResult.startsWith("\"") && trimmedResult.endsWith("\""))

        return if (isJsonLiteral) {
            """
                if (typeof window['$callbackId'] === 'function') {
                    window['$callbackId']($result, $isError);
                } else {
                    console.error("Callback not found: $callbackId");
                }
            """.trimIndent()
        } else {
            val escapedResult =
                result.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
            """
                if (typeof window['$callbackId'] === 'function') {
                    window['$callbackId']("$escapedResult", $isError);
                } else {
                    console.error("Callback not found: $callbackId");
                }
            """.trimIndent()
        }
    }

    fun imageProcessing(
        callbackId: String,
        operation: String,
        argsJson: String,
        binaryDataRegistry: ConcurrentHashMap<String, ByteArray>,
        bitmapRegistry: ConcurrentHashMap<String, Bitmap>,
        binaryHandlePrefix: String,
        sendToolResult: (callbackId: String, result: String, isError: Boolean) -> Unit
    ) {
        Thread {
            try {
                val args = Json.decodeFromString(ListSerializer(JsonElement.serializer()), argsJson)
                val result: Any? =
                    when (operation.lowercase()) {
                        "read" -> {
                            AppLogger.d(TAG, "Entering 'read' operation in image_processing.")
                            val data = args[0].jsonPrimitive.content
                            val decodedBytes: ByteArray
                            if (data.startsWith(binaryHandlePrefix)) {
                                val handle = data.substring(binaryHandlePrefix.length)
                                AppLogger.d(TAG, "Reading image from binary handle: $handle")
                                decodedBytes =
                                    binaryDataRegistry.remove(handle)
                                        ?: throw Exception("Invalid or expired binary handle: $handle")
                            } else {
                                AppLogger.d(TAG, "Reading image from Base64 string.")
                                decodedBytes = Base64.decode(data, Base64.DEFAULT)
                            }
                            AppLogger.d(TAG, "Decoded data to ${decodedBytes.size} bytes.")

                            val bitmap =
                                BitmapFactory.decodeByteArray(
                                    decodedBytes,
                                    0,
                                    decodedBytes.size
                                )

                            if (bitmap == null) {
                                AppLogger.e(
                                    TAG,
                                    "BitmapFactory.decodeByteArray returned null. Throwing exception."
                                )
                                throw Exception(
                                    "Failed to decode image. The format may be unsupported or data is corrupt."
                                )
                            } else {
                                AppLogger.d(
                                    TAG,
                                    "BitmapFactory.decodeByteArray returned a non-null Bitmap."
                                )
                                AppLogger.d(TAG, "Bitmap dimensions: ${bitmap.width}x${bitmap.height}")
                                AppLogger.d(TAG, "Bitmap config: ${bitmap.config}")
                                val id = UUID.randomUUID().toString()
                                AppLogger.d(TAG, "Storing bitmap with ID: $id")
                                bitmapRegistry[id] = bitmap
                                id
                            }
                        }
                        "create" -> {
                            val width = args[0].jsonPrimitive.int
                            val height = args[1].jsonPrimitive.int
                            val bitmap =
                                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            val id = UUID.randomUUID().toString()
                            bitmapRegistry[id] = bitmap
                            id
                        }
                        "crop" -> {
                            val id = args[0].jsonPrimitive.content
                            AppLogger.d(TAG, "Attempting to crop bitmap with ID: $id")
                            val x = args[1].jsonPrimitive.int
                            val y = args[2].jsonPrimitive.int
                            val w = args[3].jsonPrimitive.int
                            val h = args[4].jsonPrimitive.int
                            val originalBitmap =
                                bitmapRegistry[id]
                                    ?: throw Exception("Source bitmap not found for crop (ID: $id)")
                            val croppedBitmap = Bitmap.createBitmap(originalBitmap, x, y, w, h)
                            val newId = UUID.randomUUID().toString()
                            bitmapRegistry[newId] = croppedBitmap
                            newId
                        }
                        "composite" -> {
                            val baseId = args[0].jsonPrimitive.content
                            val srcId = args[1].jsonPrimitive.content
                            AppLogger.d(
                                TAG,
                                "Attempting to composite with base ID: $baseId and src ID: $srcId"
                            )
                            val x = args[2].jsonPrimitive.int
                            val y = args[3].jsonPrimitive.int
                            val baseBitmap =
                                bitmapRegistry[baseId]
                                    ?: throw Exception(
                                        "Base bitmap not found for composite (ID: $baseId)"
                                    )
                            val srcBitmap =
                                bitmapRegistry[srcId]
                                    ?: throw Exception(
                                        "Source bitmap not found for composite (ID: $srcId)"
                                    )
                            val canvas = Canvas(baseBitmap)
                            canvas.drawBitmap(srcBitmap, x.toFloat(), y.toFloat(), null)
                            null
                        }
                        "getwidth" -> {
                            val id = args[0].jsonPrimitive.content
                            AppLogger.d(TAG, "Attempting to getWidth for bitmap with ID: $id")
                            bitmapRegistry[id]?.width
                                ?: throw Exception("Bitmap not found for getWidth (ID: $id)")
                        }
                        "getheight" -> {
                            val id = args[0].jsonPrimitive.content
                            AppLogger.d(TAG, "Attempting to getHeight for bitmap with ID: $id")
                            bitmapRegistry[id]?.height
                                ?: throw Exception("Bitmap not found for getHeight (ID: $id)")
                        }
                        "getbase64" -> {
                            val id = args[0].jsonPrimitive.content
                            AppLogger.d(TAG, "Attempting to getBase64 for bitmap with ID: $id")
                            val mime = args.getOrNull(1)?.jsonPrimitive?.content ?: "image/jpeg"
                            val bitmap =
                                bitmapRegistry[id]
                                    ?: throw Exception("Bitmap not found for getBase64 (ID: $id)")
                            val outputStream = ByteArrayOutputStream()
                            val format =
                                if (mime == "image/png") {
                                    Bitmap.CompressFormat.PNG
                                } else {
                                    Bitmap.CompressFormat.JPEG
                                }
                            bitmap.compress(format, 90, outputStream)
                            Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
                        }
                        "release" -> {
                            val id = args[0].jsonPrimitive.content
                            AppLogger.d(TAG, "Attempting to release bitmap with ID: $id")
                            bitmapRegistry.remove(id)?.recycle()
                            null
                        }
                        else -> throw IllegalArgumentException("Unknown image operation: $operation")
                    }
                val jsonResultElement =
                    when (result) {
                        is String -> JsonPrimitive(result)
                        is Number -> JsonPrimitive(result)
                        is Boolean -> JsonPrimitive(result)
                        null -> JsonNull
                        else -> JsonPrimitive(result.toString())
                    }
                sendToolResult(
                    callbackId,
                    Json.encodeToString(JsonElement.serializer(), jsonResultElement),
                    false
                )
            } catch (e: Exception) {
                AppLogger.e(TAG, "Native image processing failed: ${e.message}", e)
                sendToolResult(callbackId, e.message ?: "Unknown image processing error", true)
            }
        }.start()
    }

    fun crypto(algorithm: String, operation: String, argsJson: String): String {
        return try {
            val args = Json.decodeFromString(ListSerializer(String.serializer()), argsJson)

            when (algorithm.lowercase()) {
                "md5" -> {
                    val input = args.getOrNull(0) ?: ""
                    val md = MessageDigest.getInstance("MD5")
                    val digest = md.digest(input.toByteArray(Charsets.UTF_8))
                    digest.joinToString("") { "%02x".format(it) }
                }
                "aes" -> {
                    when (operation.lowercase()) {
                        "decrypt" -> {
                            val data = args.getOrNull(0) ?: ""
                            val keyHex =
                                args.getOrNull(1)
                                    ?: throw IllegalArgumentException(
                                        "Missing key for AES decryption"
                                    )

                            val keyBytes = keyHex.toByteArray(Charsets.UTF_8)
                            val secretKey = SecretKeySpec(keyBytes, "AES")
                            val cipher = Cipher.getInstance("AES/ECB/NoPadding")
                            cipher.init(Cipher.DECRYPT_MODE, secretKey)
                            val decodedData = Base64.decode(data, Base64.DEFAULT)
                            val decryptedWithPadding = cipher.doFinal(decodedData)

                            if (decryptedWithPadding.isEmpty()) {
                                return ""
                            }

                            val paddingLength = decryptedWithPadding.last().toInt()

                            if (paddingLength < 1 || paddingLength > decryptedWithPadding.size) {
                                throw Exception("Invalid PKCS7 padding length: $paddingLength")
                            }

                            val decryptedBytes =
                                decryptedWithPadding.copyOfRange(
                                    0,
                                    decryptedWithPadding.size - paddingLength
                                )

                            String(decryptedBytes, Charsets.UTF_8)
                        }
                        else -> throw IllegalArgumentException("Unknown AES operation: $operation")
                    }
                }
                else -> throw IllegalArgumentException("Unknown algorithm: $algorithm")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Native crypto operation failed: ${e.message}", e)
            "{\"nativeError\":\"${e.message?.replace("\"", "'")}\"}"
        }
    }
}
