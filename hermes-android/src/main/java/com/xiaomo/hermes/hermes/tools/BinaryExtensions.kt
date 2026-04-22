package com.xiaomo.hermes.hermes.tools

/**
 * Binary file extensions that should not be read as text.
 * Ported from binary_extensions.py
 */
object BinaryExtensions {

    val BINARY_EXTENSIONS: Set<String> = setOf(
        // Images
        ".png", ".jpg", ".jpeg", ".gif", ".bmp", ".tiff", ".tif", ".webp",
        ".ico", ".svg", ".eps", ".psd", ".ai", ".raw", ".cr2", ".nef",
        // Audio
        ".mp3", ".wav", ".ogg", ".flac", ".aac", ".wma", ".m4a", ".opus",
        // Video
        ".mp4", ".avi", ".mkv", ".mov", ".wmv", ".flv", ".webm", ".m4v",
        ".mpg", ".mpeg", ".3gp",
        // Archives
        ".zip", ".tar", ".gz", ".bz2", ".xz", ".7z", ".rar", ".jar",
        ".war", ".ear", ".deb", ".rpm", ".dmg", ".iso", ".img",
        // Executables / compiled
        ".exe", ".dll", ".so", ".dylib", ".o", ".a", ".lib", ".pyc",
        ".pyo", ".class", ".wasm",
        // Fonts
        ".ttf", ".otf", ".woff", ".woff2", ".eot",
        // Documents (binary formats)
        ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx",
        ".odt", ".ods", ".odp",
        // Databases
        ".db", ".sqlite", ".sqlite3", ".mdb", ".accdb",
        // Other binary
        ".bin", ".dat", ".data", ".sav", ".plist", ".pb", ".onnx",
        ".ttf", ".cert", ".cer", ".der", ".p12", ".pfx", ".jks",
        ".keystore")

    fun isBinaryExtension(filename: String): Boolean {
        val lower = filename.lowercase()
        return BINARY_EXTENSIONS.any { lower.endsWith(it) }
    }

    fun isBinaryContent(bytes: ByteArray): Boolean {
        // Heuristic: check for null bytes in the first 8KB
        val sampleSize = minOf(bytes.size, 8192)
        for (i in 0 until sampleSize) {
            if (bytes[i] == 0.toByte()) return true
        }
        return false
    }


}
