/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/gateway/(all)
 *
 * AndroidForClaw adaptation: Android UI layer.
 */
package com.xiaomo.androidforclaw.ui.float

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.TextView
import com.lzf.easyfloat.EasyFloat
import com.lzf.easyfloat.enums.ShowPattern
import com.lzf.easyfloat.enums.SidePattern
import com.draco.ladb.R
import com.xiaomo.androidforclaw.util.MMKVKeys
import com.tencent.mmkv.MMKV

/**
 * Session info floating window manager
 *
 * Features:
 * - Only shown when main page is not visible
 * - Display current session status and latest message
 * - No scrolling, only fixed content
 * - Disabled by default, controlled by in-app switch
 */
object SessionFloatWindow {
    private const val TAG = "SessionFloatWindow"
    private const val FLOAT_TAG = "session_float"

    private var isEnabled = false
    private var isMainActivityVisible = true
    private var sessionInfoTextView: TextView? = null

    /**
     * Initialize floating window configuration
     */
    fun init(context: Context) {
        // Read switch status from MMKV
        val mmkv = MMKV.defaultMMKV()
        isEnabled = mmkv.decodeBool(MMKVKeys.FLOAT_WINDOW_ENABLED.key, false)

        Log.d(TAG, "SessionFloatWindow initialized, enabled=$isEnabled")
    }

    /**
     * Set floating window switch status
     */
    fun setEnabled(context: Context, enabled: Boolean) {
        isEnabled = enabled

        // Save to MMKV
        val mmkv = MMKV.defaultMMKV()
        mmkv.encode(MMKVKeys.FLOAT_WINDOW_ENABLED.key, enabled)

        Log.d(TAG, "Float window enabled=$enabled")

        if (enabled) {
            // If main page not visible, create and show floating window
            if (!isMainActivityVisible) {
                createFloatWindow(context)
            }
        } else {
            // Destroy floating window when disabled
            dismissFloatWindow()
        }
    }

    /**
     * Get floating window switch status
     */
    fun isEnabled(): Boolean {
        return isEnabled
    }

    /**
     * Set main activity visibility
     */
    fun setMainActivityVisible(visible: Boolean, context: Context) {
        isMainActivityVisible = visible

        Log.d(TAG, "Main activity visible=$visible, enabled=$isEnabled")

        if (!isEnabled) {
            return
        }

        if (visible) {
            // Main page visible, hide floating window
            dismissFloatWindow()
        } else {
            // Main page not visible, show floating window
            createFloatWindow(context)
        }
    }

    /**
     * Update session info
     */
    @SuppressLint("SetTextI18n")
    fun updateSessionInfo(title: String, content: String) {
        sessionInfoTextView?.text = "$title\n$content"
        Log.d(TAG, "Updated session info: $title")
    }

    /**
     * Create floating window
     */
    @SuppressLint("InflateParams")
    private fun createFloatWindow(context: Context) {
        // Check if already exists
        if (EasyFloat.isShow(FLOAT_TAG)) {
            Log.d(TAG, "Float window already exists")
            return
        }

        try {
            EasyFloat.with(context)
                .setTag(FLOAT_TAG)
                .setLayout(R.layout.layout_session_float) { view ->
                    // Initialize view
                    sessionInfoTextView = view.findViewById(R.id.tv_session_info)
                    sessionInfoTextView?.text = "等待会话信息..."
                }
                .setGravity(Gravity.END or Gravity.CENTER_VERTICAL, 0, 0)
                .setShowPattern(ShowPattern.CURRENT_ACTIVITY)
                .setSidePattern(SidePattern.RESULT_SIDE)
                .setDragEnable(true)
                .show()

            Log.d(TAG, "Float window created")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create float window", e)
        }
    }

    /**
     * Destroy floating window
     */
    private fun dismissFloatWindow() {
        try {
            if (EasyFloat.isShow(FLOAT_TAG)) {
                EasyFloat.dismiss(FLOAT_TAG)
                sessionInfoTextView = null
                Log.d(TAG, "Float window dismissed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dismiss float window", e)
        }
    }
}
