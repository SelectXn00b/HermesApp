/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/gateway/(all)
 *
 * AndroidForClaw adaptation: accessibility integration.
 */
package com.xiaomo.androidforclaw.accessibility

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Looper
import android.os.Handler
import android.os.RemoteException
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.xiaomo.androidforclaw.Point
import com.xiaomo.androidforclaw.ViewNode
import com.xiaomo.androidforclaw.aidl.IAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay

object AccessibilityProxy {
    private const val TAG = "AccessibilityProxy"

    private var service: IAccessibilityService? = null
    private var connectionRetryCount = 0
    private val maxRetries = 3

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = IAccessibilityService.Stub.asInterface(binder)
            Log.d(TAG, "✅ Service connected")
            connectionRetryCount = 0
            _isConnected.postValue(true)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.w(TAG, "⚠️ Service disconnected")
            service = null
            _isConnected.postValue(false)

            // Auto reconnect
            if (connectionRetryCount < maxRetries) {
                connectionRetryCount++
                Handler(Looper.getMainLooper()).postDelayed({
                    bindService(context)
                }, 1000L * connectionRetryCount)
            }
        }
    }

    private val _isConnected = MutableLiveData(false)
    val isConnected: LiveData<Boolean> = _isConnected

    private lateinit var context: Context

    fun init(applicationContext: Context) {
        this.context = applicationContext
    }

    fun bindService(context: Context): Boolean {
        val intent = Intent().apply {
            component = ComponentName(
                "com.xiaomo.androidforclaw",
                "com.xiaomo.androidforclaw.accessibility.service.AccessibilityBinderService"
            )
            action = "com.xiaomo.androidforclaw.ACCESSIBILITY_BIND"
        }

        return try {
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind service", e)
            false
        }
    }

    fun unbindService(context: Context) {
        try {
            context.unbindService(serviceConnection)
            service = null
            _isConnected.postValue(false)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unbind service", e)
        }
    }

    // Cached UI tree
    private data class CachedUITree(
        val nodes: List<ViewNode>,
        val timestamp: Long,
        val packageName: String
    )

    private var cachedTree: CachedUITree? = null
    private val cacheValidityMs = 500L

    // Service ready status cache
    private var cachedServiceReady: Boolean = false
    private var serviceReadyTimestamp: Long = 0
    private val serviceReadyCacheMs = 1000L  // 1 秒缓存

    // MediaProjection status cache
    private var cachedMediaProjectionGranted: Boolean = false
    private var mediaProjectionTimestamp: Long = 0
    private val mediaProjectionCacheMs = 2000L  // 2 秒缓存

    suspend fun dumpViewTree(useCache: Boolean = true): List<ViewNode> = withContext(Dispatchers.IO) {
        val currentPkg = try {
            service?.currentPackageName ?: ""
        } catch (e: Exception) {
            ""
        }
        val now = System.currentTimeMillis()

        // Return cache if valid
        if (useCache && cachedTree != null) {
            val cache = cachedTree!!
            if (cache.packageName == currentPkg &&
                (now - cache.timestamp) < cacheValidityMs) {
                return@withContext cache.nodes
            }
        }

        // Fetch new data
        ensureConnectedWithRetry()
        val parcelables = try {
            service?.dumpViewTree() ?: emptyList()
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to dump view tree (RemoteException)", e)
            _isConnected.postValue(false)
            emptyList()
        }

        val nodes = parcelables.map { p ->
            ViewNode(
                index = p.index,
                text = p.text,
                resourceId = p.resourceId,
                className = p.className,
                packageName = p.packageName,
                contentDesc = p.contentDesc,
                clickable = p.clickable,
                enabled = p.enabled,
                focusable = p.focusable,
                focused = p.focused,
                scrollable = p.scrollable,
                point = Point(p.centerX, p.centerY),
                left = p.left,
                right = p.right,
                top = p.top,
                bottom = p.bottom,
                node = null
            )
        }

        cachedTree = CachedUITree(nodes, now, currentPkg)
        nodes
    }

    suspend fun tap(x: Int, y: Int): Boolean = withContext(Dispatchers.IO) {
        ensureConnectedWithRetry()
        try {
            service?.performTap(x, y) ?: false
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to tap", e)
            _isConnected.postValue(false)
            false
        }
    }

    suspend fun longPress(x: Int, y: Int): Boolean = withContext(Dispatchers.IO) {
        ensureConnectedWithRetry()
        try {
            service?.performLongPress(x, y) ?: false
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to long press", e)
            _isConnected.postValue(false)
            false
        }
    }

    suspend fun swipe(
        startX: Int, startY: Int,
        endX: Int, endY: Int,
        durationMs: Long = 300
    ): Boolean = withContext(Dispatchers.IO) {
        ensureConnectedWithRetry()
        try {
            service?.performSwipe(startX, startY, endX, endY, durationMs) ?: false
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to swipe", e)
            _isConnected.postValue(false)
            false
        }
    }

    fun pressHome(): Boolean {
        return try {
            service?.pressHome() ?: false
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to press home", e)
            _isConnected.postValue(false)
            false
        }
    }

    fun pressBack(): Boolean {
        return try {
            service?.pressBack() ?: false
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to press back", e)
            _isConnected.postValue(false)
            false
        }
    }

    fun inputText(text: String): Boolean {
        return try {
            service?.inputText(text) ?: false
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to input text", e)
            _isConnected.postValue(false)
            false
        }
    }

    suspend fun getCurrentPackageName(): String = withContext(Dispatchers.IO) {
        try {
            service?.currentPackageName ?: ""
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to get current package", e)
            _isConnected.postValue(false)
            ""
        }
    }

    private fun ensureConnected() {
        if (service == null) {
            throw IllegalStateException("Accessibility service not connected")
        }
    }

    /**
     * 异步检查服务是否就绪（推荐使用）
     * 使用协程，不会阻塞主线程
     */
    suspend fun isServiceReadyAsync(): Boolean {
        val now = System.currentTimeMillis()

        // 使用缓存避免频繁的 Binder 调用
        if (now - serviceReadyTimestamp < serviceReadyCacheMs) {
            return cachedServiceReady
        }

        return try {
            val result = withTimeoutOrNull(500L) {  // 500ms 超时
                withContext(Dispatchers.IO) {
                    service?.isServiceReady() ?: false
                }
            } ?: false

            cachedServiceReady = result
            serviceReadyTimestamp = now
            result
        } catch (e: RemoteException) {
            Log.e(TAG, "Service died, attempting reconnect", e)
            _isConnected.postValue(false)
            cachedServiceReady = false
            serviceReadyTimestamp = now
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check service status", e)
            cachedServiceReady = false
            serviceReadyTimestamp = now
            false
        }
    }

    /**
     * 同步检查服务是否就绪（遗留方法，不推荐使用）
     * ⚠️ 会阻塞主线程，可能导致 ANR
     */
    @Deprecated("使用 isServiceReadyAsync() 替代，避免阻塞主线程")
    fun isServiceReady(): Boolean {
        val now = System.currentTimeMillis()

        // 使用缓存避免频繁的 Binder 调用
        if (now - serviceReadyTimestamp < serviceReadyCacheMs) {
            return cachedServiceReady
        }

        return try {
            // 使用 runBlocking 但设置超时
            val result = runBlocking {
                withTimeoutOrNull(500L) {  // 500ms 超时
                    withContext(Dispatchers.IO) {
                        service?.isServiceReady() ?: false
                    }
                }
            } ?: false

            cachedServiceReady = result
            serviceReadyTimestamp = now
            result
        } catch (e: RemoteException) {
            Log.e(TAG, "Service died, attempting reconnect", e)
            _isConnected.postValue(false)
            cachedServiceReady = false
            serviceReadyTimestamp = now
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check service status", e)
            cachedServiceReady = false
            serviceReadyTimestamp = now
            false
        }
    }

    // ===== MediaProjection (Screenshot) Methods =====

    fun isMediaProjectionGranted(): Boolean {
        val now = System.currentTimeMillis()

        // 使用缓存避免频繁的 Binder 调用
        if (now - mediaProjectionTimestamp < mediaProjectionCacheMs) {
            return cachedMediaProjectionGranted
        }

        return try {
            // 使用 runBlocking 但设置超时
            val result = runBlocking {
                withTimeoutOrNull(500L) {  // 500ms 超时
                    withContext(Dispatchers.IO) {
                        service?.isMediaProjectionGranted() ?: false
                    }
                }
            } ?: false

            cachedMediaProjectionGranted = result
            mediaProjectionTimestamp = now
            result
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to check MediaProjection status", e)
            _isConnected.postValue(false)
            cachedMediaProjectionGranted = false
            mediaProjectionTimestamp = now
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check MediaProjection status", e)
            cachedMediaProjectionGranted = false
            mediaProjectionTimestamp = now
            false
        }
    }

    suspend fun captureScreen(): String = withContext(Dispatchers.IO) {
        ensureConnectedWithRetry()
        try {
            service?.captureScreen() ?: ""
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to capture screen", e)
            _isConnected.postValue(false)
            ""
        }
    }

    /**
     * 确保服务已连接，如果未连接则尝试重连（带重试）
     */
    private suspend fun ensureConnectedWithRetry() {
        if (service != null) return

        Log.w(TAG, "Service not connected, attempting to bind...")

        // 尝试重连3次
        repeat(3) { attempt ->
            bindService(context)

            // 等待连接建立
            withTimeoutOrNull(1000L) {
                while (service == null) {
                    delay(100)
                }
            }

            if (service != null) {
                Log.d(TAG, "✅ Service reconnected on attempt ${attempt + 1}")
                return
            }

            Log.w(TAG, "❌ Reconnect attempt ${attempt + 1} failed")
            delay(500)
        }

        throw IllegalStateException("Accessibility service not connected after 3 retry attempts")
    }

    fun getMediaProjectionStatus(): String {
        return try {
            // 先确保服务已连接
            if (service == null) {
                bindService(context)
                Thread.sleep(300)  // 等待连接建立
            }

            // 使用 runBlocking 但设置超时
            runBlocking {
                withTimeoutOrNull(500L) {  // 500ms 超时
                    withContext(Dispatchers.IO) {
                        service?.mediaProjectionStatus ?: "未连接"
                    }
                }
            } ?: "超时"
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to get MediaProjection status", e)
            _isConnected.postValue(false)
            "未连接"
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get MediaProjection status", e)
            "错误"
        }
    }
}
