package com.ai.assistance.mmd

internal object MmdLibraryLoader {
    @Volatile
    private var loaded = false

    private val lock = Any()

    fun loadLibraries() {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            System.loadLibrary("MmdWrapper")
            loaded = true
        }
    }
}
