package com.ai.assistance.llama

internal object LlamaLibraryLoader {
    @Volatile
    private var loaded = false

    private val lock = Any()

    fun loadLibraries() {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            System.loadLibrary("LlamaWrapper")
            loaded = true
        }
    }
}
