/**
 * OpenClaw Source Reference:
 * - 无 OpenClaw 对应 (Android 平台独有)
 */
package com.xiaomo.androidforclaw.accessibility

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.xiaomo.androidforclaw.accessibility.service.AccessibilityBinderService
import com.xiaomo.androidforclaw.accessibility.service.ViewNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

object AccessibilityProxy {
    private const val TAG = "AccessibilityProxy"

    private val service get() = AccessibilityBinderService.serviceInstance

    private val _isConnected = MutableLiveData(false)
    val isConnected: LiveData<Boolean> = _isConnected

    private val _overlayGranted = MutableLiveData(false)
    val overlayGranted: LiveData<Boolean> = _overlayGranted

    private val _screenCaptureGranted = MutableLiveData(false)
    val screenCaptureGranted: LiveData<Boolean> = _screenCaptureGranted

    /** 检查 Shizuku 是否可用且已授权（可选能力，不影响无障碍操作） */
    fun isShizukuReady(): Boolean = ShizukuManager.isReady

    /** 从任意地方调用来刷新权限状态（PermissionActivity 授权后调用） */
    fun refreshPermissions(context: android.content.Context) {
        _overlayGranted.postValue(android.provider.Settings.canDrawOverlays(context))
        _screenCaptureGranted.postValue(isMediaProjectionGranted())
        val svcConnected = service != null && isServiceReady()
        _isConnected.postValue(svcConnected)
    }

    // Cached UI tree
    private data class CachedUITree(
        val nodes: List<ViewNode>,
        val timestamp: Long,
        val packageName: String
    )

    private var cachedTree: CachedUITree? = null
    private val cacheValidityMs = 500L

    suspend fun dumpViewTree(useCache: Boolean = true): List<ViewNode> = withContext(Dispatchers.IO) {
        ensureConnectedWithRetry()
        val svc = service
        if (svc == null) {
            Log.w(TAG, "Service not available for dumpViewTree after retry")
            _isConnected.postValue(false)
            return@withContext emptyList()
        }
        _isConnected.postValue(true)

        val currentPkg = svc.currentPackageName
        val now = System.currentTimeMillis()

        // Return cache if valid
        if (useCache && cachedTree != null) {
            val cache = cachedTree!!
            if (cache.packageName == currentPkg &&
                (now - cache.timestamp) < cacheValidityMs) {
                return@withContext cache.nodes
            }
        }

        val nodes = svc.dumpView()
        cachedTree = CachedUITree(nodes, now, currentPkg)
        nodes
    }

    suspend fun tap(x: Int, y: Int): Boolean = withContext(Dispatchers.IO) {
        ensureConnectedWithRetry(requireReady = true)
        service?.performClickAt(x.toFloat(), y.toFloat(), isLongClick = false) ?: false
    }

    suspend fun longPress(x: Int, y: Int): Boolean = withContext(Dispatchers.IO) {
        ensureConnectedWithRetry(requireReady = true)
        service?.performClickAt(x.toFloat(), y.toFloat(), isLongClick = true) ?: false
    }

    suspend fun swipe(
        startX: Int, startY: Int,
        endX: Int, endY: Int,
        durationMs: Long = 300
    ): Boolean = withContext(Dispatchers.IO) {
        val svc = service
        if (svc == null) {
            Log.w(TAG, "Service not available for swipe")
            return@withContext false
        }
        svc.performSwipe(startX.toFloat(), startY.toFloat(), endX.toFloat(), endY.toFloat())
        true
    }

    fun pressHome(): Boolean {
        val svc = service ?: return false
        svc.pressHomeButton()
        return true
    }

    fun pressBack(): Boolean {
        val svc = service ?: return false
        svc.pressBackButton()
        return true
    }

    fun inputText(text: String): Boolean {
        val svc = service ?: return false
        return svc.inputText(text)
    }

    suspend fun getCurrentPackageName(): String = withContext(Dispatchers.IO) {
        service?.currentPackageName ?: ""
    }

    /**
     * 异步检查服务是否就绪
     */
    suspend fun isServiceReadyAsync(): Boolean {
        return service?.rootInActiveWindow != null
    }

    /**
     * 同步检查服务是否就绪
     */
    fun isServiceReady(): Boolean {
        return service?.rootInActiveWindow != null
    }

    // ===== MediaProjection (Screenshot) Methods =====

    fun isMediaProjectionGranted(): Boolean {
        return MediaProjectionHelper.isMediaProjectionGranted()
    }

    suspend fun captureScreen(): String = withContext(Dispatchers.IO) {
        ensureConnectedWithRetry()
        MediaProjectionHelper.captureScreen()?.second ?: ""
    }

    fun getMediaProjectionStatus(): String {
        return MediaProjectionHelper.getDetailedStatus()
    }

    /**
     * 确保服务已连接；可选地等待 AccessibilityService 真正 ready。
     */
    private suspend fun ensureConnectedWithRetry(requireReady: Boolean = false) {
        if (service != null && (!requireReady || checkServiceReadyOnce())) {
            _isConnected.postValue(true)
            return
        }

        Log.w(TAG, "Service not connected/ready, waiting... requireReady=$requireReady")

        repeat(3) { attempt ->
            val connected = kotlinx.coroutines.withTimeoutOrNull(2000L) {
                while (service == null) {
                    delay(100)
                }
                true
            } == true

            if (!connected) {
                Log.w(TAG, "Attempt ${attempt + 1} failed: serviceInstance still null")
                delay(500)
                return@repeat
            }

            if (!requireReady) {
                Log.d(TAG, "Service connected on attempt ${attempt + 1}")
                _isConnected.postValue(true)
                return
            }

            val ready = kotlinx.coroutines.withTimeoutOrNull(2500L) {
                while (!checkServiceReadyOnce()) {
                    delay(100)
                }
                true
            } == true

            if (ready) {
                Log.d(TAG, "Service ready on attempt ${attempt + 1}")
                _isConnected.postValue(true)
                return
            }

            Log.w(TAG, "Attempt ${attempt + 1} failed: service not ready")
            delay(500)
        }

        _isConnected.postValue(false)
        if (requireReady) {
            throw IllegalStateException("Accessibility service not ready after 3 retry attempts")
        } else {
            throw IllegalStateException("Accessibility service not connected after 3 retry attempts")
        }
    }

    private fun checkServiceReadyOnce(): Boolean {
        return try {
            service?.rootInActiveWindow != null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check service ready state", e)
            false
        }
    }
}
