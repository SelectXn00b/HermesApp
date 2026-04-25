package com.ai.assistance.mnn

import android.util.Log

/**
 * MNN 库加载器
 * 确保 MNN 和 MNNWrapper 库只被加载一次
 */
internal object MNNLibraryLoader {
    private const val TAG = "MNNLibraryLoader"
    
    @Volatile
    private var loaded = false
    
    private val lock = Any()
    
    /**
     * 加载 MNN 库
     * 如果库已经加载，则不会重复加载
     */
    fun loadLibraries() {
        if (loaded) {
            return
        }
        
        synchronized(lock) {
            if (loaded) {
                return
            }
            
            try {
                // 首先加载 MNN 核心库
                System.loadLibrary("MNN")
                Log.d(TAG, "MNN library loaded successfully")
                
                // 然后加载我们的 JNI 包装库
                System.loadLibrary("MNNWrapper")
                Log.d(TAG, "MNNWrapper library loaded successfully")
                
                loaded = true
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load MNN libraries", e)
                throw e
            }
        }
    }
    
    /**
     * 检查库是否已加载
     */
    fun isLoaded(): Boolean = loaded
}

