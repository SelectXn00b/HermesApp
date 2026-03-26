package com.xiaomo.androidforclaw.core

import android.content.Context
import android.content.Intent
import com.xiaomo.androidforclaw.logging.Log

/**
 * 通过 Termux RUN_COMMAND intent 自动启动 sshd。
 *
 * 需要在 AndroidManifest.xml 中声明:
 *   <uses-permission android:name="com.termux.permission.RUN_COMMAND" />
 *
 * 需要 Termux v0.119.0+ 且用户在 Termux 设置中启用
 * "Allow External Apps" (~/.termux/termux.properties → allow-external-apps = true)。
 *
 * Reference: https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent
 */
object TermuxSshdLauncher {

    private const val TAG = "TermuxSshdLauncher"

    private const val TERMUX_PACKAGE = "com.termux"
    private const val RUN_COMMAND_SERVICE = "$TERMUX_PACKAGE.app.RunCommandService"
    private const val ACTION_RUN_COMMAND = "$TERMUX_PACKAGE.RUN_COMMAND"

    // Termux RUN_COMMAND extras
    private const val EXTRA_COMMAND = "$TERMUX_PACKAGE.RUN_COMMAND_PATH"
    private const val EXTRA_ARGUMENTS = "$TERMUX_PACKAGE.RUN_COMMAND_ARGUMENTS"
    private const val EXTRA_BACKGROUND = "$TERMUX_PACKAGE.RUN_COMMAND_BACKGROUND"

    /** sshd 的完整路径 */
    const val SSHD_PATH = "/data/data/$TERMUX_PACKAGE/files/usr/bin/sshd"

    /**
     * 构建 RUN_COMMAND intent（可用于测试）。
     */
    fun buildIntent(): Intent = Intent(ACTION_RUN_COMMAND).apply {
        setClassName(TERMUX_PACKAGE, RUN_COMMAND_SERVICE)
        putExtra(EXTRA_COMMAND, SSHD_PATH)
        putExtra(EXTRA_BACKGROUND, true)
    }

    /**
     * 发送 RUN_COMMAND intent 让 Termux 执行 sshd。
     * 非阻塞，调用后 sshd 可能需要 1-2 秒才能监听端口。
     */
    fun launch(context: Context) {
        val intent = buildIntent()
        try {
            context.startService(intent)
            Log.i(TAG, "✅ 已发送 RUN_COMMAND 启动 sshd")
        } catch (e: SecurityException) {
            Log.w(TAG, "RUN_COMMAND 被拒绝（需要在 Termux 中启用 allow-external-apps）: ${e.message}")
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "RUN_COMMAND 发送失败: ${e.message}")
            throw e
        }
    }
}
