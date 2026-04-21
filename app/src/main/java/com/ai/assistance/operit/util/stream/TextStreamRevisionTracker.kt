package com.ai.assistance.operit.util.stream

class TextStreamRevisionTracker(initialContent: String = "") {
    private val contentBuffer = StringBuilder(initialContent)
    private val savepoints = linkedMapOf<String, String>()

    fun currentContent(): String = contentBuffer.toString()

    fun append(chunk: String): String {
        contentBuffer.append(chunk)
        return contentBuffer.toString()
    }

    fun savepoint(id: String) {
        savepoints[id] = contentBuffer.toString()
    }

    fun rollback(id: String): String? {
        val snapshot = savepoints[id] ?: return null
        contentBuffer.setLength(0)
        contentBuffer.append(snapshot)
        return snapshot
    }

    fun replace(content: String) {
        contentBuffer.setLength(0)
        contentBuffer.append(content)
    }
}
