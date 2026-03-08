package com.xiaomo.androidforclaw.gateway.protocol

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser

/**
 * Frame serializer/deserializer
 */
class FrameSerializer {
    internal val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .create()

    /**
     * Serialize Frame to JSON string
     */
    fun serialize(frame: Frame): String {
        return gson.toJson(frame)
    }

    /**
     * Deserialize JSON string to Frame
     */
    fun deserialize(json: String): Frame {
        val jsonObject = JsonParser.parseString(json).asJsonObject
        val type = jsonObject.get("type")?.asString
            ?: throw IllegalArgumentException("Missing 'type' field")

        return when (type) {
            "request" -> gson.fromJson(json, RequestFrame::class.java)
            "response" -> gson.fromJson(json, ResponseFrame::class.java)
            "event" -> gson.fromJson(json, EventFrame::class.java)
            else -> throw IllegalArgumentException("Unknown frame type: $type")
        }
    }
}
