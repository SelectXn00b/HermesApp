/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/gateway/(all)
 *
 * AndroidForClaw adaptation: Android service layer.
 */
package com.xiaomo.androidforclaw.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import androidx.lifecycle.MutableLiveData
import com.xiaomo.androidforclaw.Point
import com.xiaomo.androidforclaw.ViewNode
import com.xiaomo.androidforclaw.core.MyApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.xiaomo.androidforclaw.util.LayoutExceptionLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

// adb view tree structure capture encountered bug
class PhoneAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "PhoneAccessibilityService"
        @JvmField
        var Accessibility: PhoneAccessibilityService? = null

        // Accessibility permission status constants
        const val STATUS_SYSTEM_DISABLED = "系统无障碍未开启"
        const val STATUS_SERVICE_NOT_ENABLED = "服务未在系统设置中启用"
        const val STATUS_SERVICE_NOT_CONNECTED = "服务未连接"
        const val STATUS_AUTHORIZED = "已授权"
        const val STATUS_CHECK_FAILED = "检查失败"

        // Use LiveData to store accessibility service status
        val accessibilityEnabled = MutableLiveData<Boolean>().apply {
            postValue(false) // Initial state is false
        }

        // Periodic monitoring and throttling
        private val monitorScope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

        /**
         * Check if accessibility service is enabled
         */
        fun isAccessibilityServiceEnabled(): Boolean {
//            val isEnabled = Accessibility != null
            val isEnabled = isSystemAccessibilityEnabled(MyApplication.application.applicationContext)
            accessibilityEnabled.postValue(isEnabled) // 同步更新 LiveData 状态
            return isEnabled
        }

        fun requestAccessibilityPermission(context: Context) {
            try {
                Log.d(TAG, "开始申请无障碍权限")
                try {
                    val appPkg = context.applicationContext.packageName
                    val serviceClass = PhoneAccessibilityService::class.java.name
                    val serviceName = "$appPkg/$serviceClass"
                    Settings.Secure.putString(
                        context.contentResolver,
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                        serviceName
                    )
                    Settings.Secure.putInt(
                        context.contentResolver,
                        Settings.Secure.ACCESSIBILITY_ENABLED,
                        1
                    )
                    Log.d(TAG, "无障碍权限申请命令已发送: $serviceName")
                } catch (e: Exception) {
                    LayoutExceptionLogger.log("PhoneAccessibilityService#requestAccessibilityPermission#sendCommand", e)
                    Log.w(TAG, "代码申请无障碍权限失败: ${'$'}{e.message}")
                }

                monitorScope.launch {
                    try {
                        delay(1000)
                        val isEnabled = isSystemAccessibilityEnabled(context)
                        if (isEnabled) {
                            Log.d(TAG, "无障碍权限申请成功")
                        } else {
                            Log.d(TAG, "代码申请失败，跳转到系统设置页面")
                            Toast.makeText(context, "代码申请失败，请手动开启无障碍权限", Toast.LENGTH_LONG).show()
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            context.startActivity(intent)
                        }
                    } catch (e: Exception) {
                        LayoutExceptionLogger.log("PhoneAccessibilityService#requestAccessibilityPermission#checkResult", e)
                        Log.e(TAG, "异步检查权限申请结果异常", e)
                    }
                }
            } catch (e: Exception) {
                LayoutExceptionLogger.log("PhoneAccessibilityService#requestAccessibilityPermission", e)
                Log.e(TAG, "申请无障碍权限失败", e)
            }
        }
        
        /**
         * Check if system accessibility permission is enabled
         */
        fun isSystemAccessibilityEnabled(context: Context): Boolean {
            if (accessibilityEnabled.value == true) return true
            return try {
                val accessibilityEnabled = Settings.Secure.getInt(
                    context.contentResolver,
                    Settings.Secure.ACCESSIBILITY_ENABLED,
                    0
                ) == 1
                
                val enabledServices = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                )
                
                val serviceName = "${context.packageName}/${PhoneAccessibilityService::class.java.name}"
                val isServiceEnabled = enabledServices?.contains(serviceName) == true
                
                Log.d(TAG, "系统无障碍权限: $accessibilityEnabled")
                Log.d(TAG, "服务已启用: $isServiceEnabled")
                Log.d(TAG, "服务实例存在: ${Accessibility != null}")
                
                accessibilityEnabled && isServiceEnabled && Accessibility != null
            } catch (e: Exception) {
                LayoutExceptionLogger.log("PhoneAccessibilityService#isSystemAccessibilityEnabled", e)
                Log.e(TAG, "检查无障碍权限失败", e)
                false
            }
        }
        
        /**
         * Get detailed accessibility permission status
         */
        fun getAccessibilityStatus(context: Context): String {
            return try {
                val accessibilityEnabled = Settings.Secure.getInt(
                    context.contentResolver,
                    Settings.Secure.ACCESSIBILITY_ENABLED,
                    0
                ) == 1
                
                val enabledServices = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                )
                
                val serviceName = "${context.packageName}/${PhoneAccessibilityService::class.java.name}"
                val isServiceEnabled = enabledServices?.contains(serviceName) == true
                val isServiceConnected = Accessibility != null
                
                when {
                    !accessibilityEnabled -> STATUS_SYSTEM_DISABLED
                    !isServiceEnabled -> STATUS_SERVICE_NOT_ENABLED
                    !isServiceConnected -> STATUS_SERVICE_NOT_CONNECTED
                    else -> STATUS_AUTHORIZED
                }
            } catch (e: Exception) {
                LayoutExceptionLogger.log("PhoneAccessibilityService#checkAccessibilityStatus", e)
                "$STATUS_CHECK_FAILED: ${e.message}"
            }
        }
    }

    var currentPackageName = ""
    var activityClassName = ""
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Accessibility = this
        accessibilityEnabled.postValue(true) // Directly update LiveData
        Log.d(TAG, "onServiceConnected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
//        Log.d(TAG, "onAccessibilityEvent")
        Accessibility = this
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (event.packageName != packageName) {
                currentPackageName = event.packageName?.toString() ?: ""
                activityClassName = event.className?.toString() ?: ""

                Log.d(TAG, "当前前台App: $currentPackageName, 当前Activity: $activityClassName")
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "onInterrupt")
        Accessibility = null
        accessibilityEnabled.postValue(false) // Update LiveData
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "onUnbind - 无障碍服务断开")
        Accessibility = null
        accessibilityEnabled.postValue(false) // Update LiveData
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy - 无障碍服务销毁")
        Accessibility = null
        accessibilityEnabled.postValue(false) // Update LiveData
    }

    // Save global Index for traversal
    private var globalIndex = 0

    fun dumpView(): List<ViewNode> {
        // Use getWindows() to get all windows, not just the current active window
        val windows = this.windows
        if (windows.isEmpty()) {
            Log.w(TAG, "No windows available, trying rootInActiveWindow as fallback")
            // Try traditional rootInActiveWindow as fallback
            val rootNode = rootInActiveWindow
            if (rootNode != null) {
                globalIndex = 0
                val nodesList = mutableListOf<ViewNode>()
                traverseNode(rootNode, nodesList)
                return nodesList
            }
            return emptyList()
        }

        globalIndex = 0  // Reset count on each dump
        val nodesList = mutableListOf<ViewNode>()

        // Traverse all windows, sorted by Z-order, top windows first
        val sortedWindows = windows.sortedByDescending { it.layer }
        Log.d(TAG, "Found ${sortedWindows.size} windows")

        // Traverse all windows
        for ((index, window) in sortedWindows.withIndex()) {
            val rootNode = window.root
            if (rootNode == null) {
                Log.w(TAG, "Window $index has no root node")
                continue
            }

            // ⚡ Filter system windows
            val windowTitle = window.title?.toString() ?: ""
            if (windowTitle.contains("FloatingRootContainer") ||
                windowTitle.contains("layout_floating")) {
                Log.d(TAG, "跳过系统窗口: $windowTitle")
                continue
            }

            Log.d(
                TAG,
                "Processing window $index: ${window.title}, type: ${window.type}, layer: ${window.layer}"
            )
            try {
                traverseNode(rootNode, nodesList)
            } catch (e: Exception) {
                LayoutExceptionLogger.log("PhoneAccessibilityService#getWindowNodes#traverse", e)
                Log.e(TAG, "Error traversing window $index", e)
            }
        }

        Log.d(TAG, "Total nodes collected: ${nodesList.size}")
        return nodesList
    }

    private fun traverseNode(node: AccessibilityNodeInfo, nodesList: MutableList<ViewNode>) {
        val rect = Rect()
        node.getBoundsInScreen(rect)

        // Validate if boundary rectangle is valid
        val isValidRect = rect.left >= 0 && rect.top >= 0 && rect.right > rect.left && rect.bottom > rect.top

        if (!isValidRect) {
            // Node with invalid bounds is not saved, but continue traversing child nodes (child nodes might be valid)
            val nodeText = node.text?.toString() ?: ""
            val nodeContentDesc = node.contentDescription?.toString() ?: ""
            Log.w(TAG, "traverseNode跳过边界无效的节点: text='$nodeText', contentDesc='$nodeContentDesc', " +
                    "边界=[left=${rect.left}, top=${rect.top}, right=${rect.right}, bottom=${rect.bottom}], " +
                    "可能是ViewPager中未显示的Tab页面节点")
            // Continue traversing child nodes
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { childNode ->
                    traverseNode(childNode, nodesList)
                }
            }
            return
        }

        val centerX = rect.centerX()
        val centerY = rect.centerY()

        // Validate if center coordinates are valid (non-negative)
        if (centerX < 0 || centerY < 0) {
            val nodeText = node.text?.toString() ?: ""
            val nodeContentDesc = node.contentDescription?.toString() ?: ""
            Log.w(TAG, "traverseNode跳过坐标无效的节点: text='$nodeText', contentDesc='$nodeContentDesc', " +
                    "centerX=$centerX, centerY=$centerY, 边界=[left=${rect.left}, top=${rect.top}, right=${rect.right}, bottom=${rect.bottom}]")
            // Continue traversing child nodes
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { childNode ->
                    traverseNode(childNode, nodesList)
                }
            }
            return
        }

        val nodeInfo = ViewNode(
            index = globalIndex++,
            text = node.text?.toString(),
            resourceId = node.viewIdResourceName,
            className = node.className?.toString(),
            packageName = node.packageName?.toString(),
            contentDesc = node.contentDescription?.toString(),
            clickable = node.isClickable,
            enabled = node.isEnabled,
            focusable = node.isFocusable,
            focused = node.isFocused,
            scrollable = node.isScrollable,
            point = Point(centerX, centerY),
            left = rect.left,
            right = rect.right,
            top = rect.top,
            bottom = rect.bottom,
            node = node
        )
        nodesList.add(nodeInfo)

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { childNode ->
                traverseNode(childNode, nodesList)
            }
        }
    }

    /**
     * Find and click node by text
     */
    suspend fun clickViewByText(text: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val nodeList = rootNode.findAccessibilityNodeInfosByText(text)
        nodeList.firstOrNull()?.let { node ->
            return performClick(node)
        }
        Log.w(TAG, "No node found with text: $text")
        return false
    }

    public suspend fun performClick(
        node: AccessibilityNodeInfo,
        isLongClick: Boolean = false
    ): Boolean {
        if (isLongClick) {
            return performLongClick(node)
        }
        Log.d(TAG, "performClick: ${node}")
        // If node is clickable and click succeeds, return directly
        if ( node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true
        }

        // Search upward for parent node and try to click
        var parent = node.parent
        while (parent != null) {
            if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
            parent = parent.parent
        }

        // Set accessibility focus and selection state (especially important for controls in popups)
        node.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
        node.performAction(AccessibilityNodeInfo.ACTION_SELECT)

        // Get coordinates and click
        val rect = Rect()
        node.getBoundsInScreen(rect)
        val centerX = (rect.left + rect.right) / 2
        val centerY = (rect.top + rect.bottom) / 2

        val path = Path().apply { moveTo(centerX.toFloat(), centerY.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 200))
            .build()
        val result = CompletableDeferred<Boolean>()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                Log.d(TAG, "点击完成")
                result.complete(true)  // 手势成功完成
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                Log.d(TAG, "点击被取消")
                result.complete(false)  // 手势成功完成
            }
        }, null)

        // Wait for result (with timeout)
        return withTimeoutOrNull(500) {
            result.await()
        } ?: false  // Timeout returns false
    }

    /**
     * Perform click operation by coordinates
     * @param x X coordinate for click
     * @param y Y coordinate for click
     * @param isLongClick Whether long press, default is false
     * @return Whether click succeeded
     */
    public suspend fun performClickAt(
        x: Float,
        y: Float,
        isLongClick: Boolean = false
    ): Boolean {
        Log.d(TAG, "performClickAt: x=$x, y=$y, isLongClick=$isLongClick")
        
        val duration = if (isLongClick) 600L else 200L
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        
        val result = CompletableDeferred<Boolean>()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                Log.d(TAG, "坐标点击完成: ($x, $y)")
                result.complete(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                Log.d(TAG, "坐标点击被取消: ($x, $y)")
                result.complete(false)
            }
        }, null)

        // 等待结果（带超时）
        return withTimeoutOrNull(500) {
            result.await()
        } ?: false
    }

    public suspend fun performLongClick(node: AccessibilityNodeInfo): Boolean {
        Log.d(TAG, "performLongClick: ${node}")

        // 如果节点可点击并成功点击，直接返回
        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)) {
            return true
        }

        // 向上查找父节点并尝试点击
        var parent = node.parent
        while (parent != null) {
            if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)) {
                return true
            }
            parent = parent.parent
        }
        return false
    }

    fun pressHomeButton() {
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    fun pressBackButton() {
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    fun performSwipe(startX: Float, startY: Float, endX: Float, endY: Float) {
        rootInActiveWindow?.let {
            val swipe = GestureDescription.Builder()
                .addStroke(
                    GestureDescription.StrokeDescription(
                        Path().apply {
                            moveTo(startX, startY)
                            lineTo(endX, endY)
                        }, 0L, 200L
                    )
                ).build()

            dispatchGesture(swipe, null, null)
        }
    }


}