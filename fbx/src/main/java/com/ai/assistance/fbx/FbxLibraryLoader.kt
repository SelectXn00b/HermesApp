package com.ai.assistance.fbx

internal object FbxLibraryLoader {
    @Volatile
    private var loaded = false

    private val lock = Any()

    fun loadLibraries() {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            System.loadLibrary("FbxWrapper")
            loaded = true
        }
    }
}
