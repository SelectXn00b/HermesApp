/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/gateway/(all)
 *
 * AndroidForClaw adaptation: Android service layer.
 */
package com.xiaomo.androidforclaw.service

import android.content.Context
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest

/**
 * AdbIME 管理器
 * 提供对 AdbIME 的直接调用接口,避免使用广播
 *
 * 工作原理:
 * - AdbIME 是同进程的 InputMethodService
 * - 通过单例模式让 AdbIME 注册自己的实例
 * - 其他组件通过此 Manager 直接调用 AdbIME 的方法
 */
object AdbIMEManager {
    private const val TAG = "AdbIMEManager"

    // AdbIME 实例引用
    private var adbImeInstance: AdbIME? = null

    /**
     * 注册 AdbIME 实例 (由 AdbIME.onCreateInputView 调用)
     */
    fun registerInstance(instance: AdbIME) {
        adbImeInstance = instance
        Log.d(TAG, "✓ AdbIME instance registered")
    }

    /**
     * 注销 AdbIME 实例 (由 AdbIME.onDestroy 调用)
     */
    fun unregisterInstance() {
        adbImeInstance = null
        Log.d(TAG, "✓ AdbIME instance unregistered")
    }

    /**
     * 检查 AdbIME 是否为当前启用的输入法
     */
    fun isAdbImeEnabled(context: Context): Boolean {
        return try {
            val currentIme = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.DEFAULT_INPUT_METHOD
            )
            val adbImeName = "${context.packageName}/com.xiaomo.androidforclaw.service.AdbIME"
            val isEnabled = currentIme == adbImeName
            Log.d(TAG, "Current IME: $currentIme, AdbIME enabled: $isEnabled")
            isEnabled
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check IME status", e)
            false
        }
    }

    /**
     * 检查 AdbIME 是否已连接 (实例存在且有输入连接)
     */
    fun isConnected(): Boolean {
        val connected = adbImeInstance?.currentInputConnection != null
        Log.d(TAG, "isConnected: $connected")
        return connected
    }

    /**
     * 输入文本
     */
    fun inputText(text: String): Boolean {
        val ime = adbImeInstance
        if (ime == null) {
            Log.e(TAG, "AdbIME instance not available")
            return false
        }

        val ic = ime.currentInputConnection
        if (ic == null) {
            Log.e(TAG, "No input connection available")
            return false
        }

        return try {
            ic.commitText(text, 1)
            Log.d(TAG, "✓ Input text: ${text.take(50)}${if (text.length > 50) "..." else ""}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to input text", e)
            false
        }
    }

    /**
     * 清空输入框
     */
    fun clearText(): Boolean {
        val ime = adbImeInstance
        if (ime == null) {
            Log.e(TAG, "AdbIME instance not available")
            return false
        }

        val ic = ime.currentInputConnection
        if (ic == null) {
            Log.e(TAG, "No input connection available")
            return false
        }

        return try {
            // REF: stackoverflow/33082004 author: Maxime Epain
            val curPos = ic.getExtractedText(ExtractedTextRequest(), 0)?.text
            if (curPos != null) {
                val beforePos = ic.getTextBeforeCursor(curPos.length, 0)
                val afterPos = ic.getTextAfterCursor(curPos.length, 0)
                ic.deleteSurroundingText(beforePos?.length ?: 0, afterPos?.length ?: 0)
            }
            Log.d(TAG, "✓ Cleared text")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear text", e)
            false
        }
    }

    /**
     * 发送消息 (执行编辑器动作或回车)
     */
    fun sendMessage(): Boolean {
        val ime = adbImeInstance
        if (ime == null) {
            Log.e(TAG, "AdbIME instance not available")
            return false
        }

        val ic = ime.currentInputConnection
        if (ic == null) {
            Log.e(TAG, "No input connection available")
            return false
        }

        return try {
            // 先尝试 IME_ACTION_SEND
            var sent = ic.performEditorAction(EditorInfo.IME_ACTION_SEND)
            Log.d(TAG, "performEditorAction IME_ACTION_SEND: $sent")

            // 如果失败,再尝试 IME_ACTION_GO
            if (!sent) {
                sent = ic.performEditorAction(EditorInfo.IME_ACTION_GO)
                Log.d(TAG, "performEditorAction IME_ACTION_GO: $sent")
            }

            // 如果还是失败,尝试发送回车键
            if (!sent) {
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
                Log.d(TAG, "sendKeyEvent KEYCODE_ENTER as fallback")
                sent = true
            }

            sent
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send message", e)
            false
        }
    }

    /**
     * 发送按键事件
     */
    fun sendKey(keyCode: Int): Boolean {
        val ime = adbImeInstance
        if (ime == null) {
            Log.e(TAG, "AdbIME instance not available")
            return false
        }

        val ic = ime.currentInputConnection
        if (ic == null) {
            Log.e(TAG, "No input connection available")
            return false
        }

        return try {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
            Log.d(TAG, "✓ Sent key code: $keyCode")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send key", e)
            false
        }
    }
}
