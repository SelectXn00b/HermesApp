package com.xiaomo.hermes.core

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.xiaomo.hermes.logging.Log

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

    /** 确保 Termux 拉起后再发 RUN_COMMAND 的初始等待时间 */
    private const val TERMUX_LAUNCH_WAIT_MS = 3000L

    /** RUN_COMMAND 最大重试次数 */
    private const val MAX_LAUNCH_RETRIES = 3

    /** 重试间隔 */
    private const val RETRY_INTERVAL_MS = 2000L

    /** 用于构建通用 RUN_COMMAND intent 的 bash 路径 */
    private const val BASH_PATH = "/data/data/$TERMUX_PACKAGE/files/usr/bin/bash"

    /**
     * 构建 RUN_COMMAND intent（可用于测试）。
     */
    fun buildIntent(): Intent = Intent(ACTION_RUN_COMMAND).apply {
        setClassName(TERMUX_PACKAGE, RUN_COMMAND_SERVICE)
        putExtra(EXTRA_COMMAND, SSHD_PATH)
        putExtra(EXTRA_BACKGROUND, true)
    }

    /**
     * 构建一个通过 bash -c 执行任意命令的 RUN_COMMAND intent。
     */
    private fun buildBashIntent(command: String): Intent = Intent(ACTION_RUN_COMMAND).apply {
        setClassName(TERMUX_PACKAGE, RUN_COMMAND_SERVICE)
        putExtra(EXTRA_COMMAND, BASH_PATH)
        putExtra(EXTRA_ARGUMENTS, arrayOf("-c", command))
        putExtra(EXTRA_BACKGROUND, true)
    }

    /**
     * 检测是否为 MIUI/HyperOS 等小米系统（会拦截跨应用 startService）。
     */
    fun isMiui(): Boolean {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val get = clazz.getMethod("get", String::class.java, String::class.java)
            val miuiVersion = get.invoke(null, "ro.miui.ui.version.name", "") as String
            miuiVersion.isNotEmpty()
        } catch (_: Exception) {
            // fallback: check manufacturer
            Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true) ||
                Build.MANUFACTURER.equals("Redmi", ignoreCase = true)
        }
    }

    /**
     * 确保 Termux 进程已启动。
     * RUN_COMMAND 需要 Termux 的 RunCommandService 在运行才能响应，
     * 所以先用 launch intent 把 Termux 拉起来。
     *
     * @return true 如果成功发送了启动 intent
     */
    fun ensureTermuxRunning(context: Context): Boolean {
        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(TERMUX_PACKAGE)
        if (launchIntent == null) {
            Log.w(TAG, "Termux 未安装，无法拉起")
            return false
        }
        // FLAG_ACTIVITY_NO_HISTORY: Termux 不留在返回栈，用户按返回直接回 Hermes
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY)
        try {
            context.startActivity(launchIntent)
            Log.i(TAG, "✅ 已发送 Termux 启动 intent")
            return true
        } catch (e: Exception) {
            Log.w(TAG, "启动 Termux 失败: ${e.message}")
            return false
        }
    }

    /**
     * 发送 RUN_COMMAND intent 让 Termux 执行 sshd。
     * 先尝试 startService，失败则尝试 PendingIntent 绕过 OEM 限制。
     */
    fun launch(context: Context) {
        val intent = buildIntent()
        try {
            context.startService(intent)
            Log.i(TAG, "✅ 已发送 RUN_COMMAND 启动 sshd (startService)")
        } catch (e: SecurityException) {
            Log.w(TAG, "startService 被拒绝，尝试 PendingIntent 方式: ${e.message}")
            launchViaPendingIntent(context)
        } catch (e: Exception) {
            Log.w(TAG, "startService 失败，尝试 PendingIntent 方式: ${e.message}")
            launchViaPendingIntent(context)
        }
    }

    /**
     * 通过 PendingIntent 发送 RUN_COMMAND。
     * PendingIntent 走不同的 framework 路径，部分 OEM 限制不会拦截。
     */
    fun launchViaPendingIntent(context: Context) {
        try {
            val intent = buildIntent()
            val flags = PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            val pi = PendingIntent.getService(context, 0, intent, flags)
            pi.send()
            Log.i(TAG, "✅ 已发送 RUN_COMMAND 启动 sshd (PendingIntent)")
        } catch (e: Exception) {
            Log.w(TAG, "PendingIntent 方式也失败: ${e.message}")
            throw e
        }
    }

    /**
     * 通过 RUN_COMMAND 将公钥注入到 Termux 的 ~/.ssh/authorized_keys。
     * 用于 sshd 可达但认证失败（authorized_keys 丢失）的场景。
     */
    fun injectPublicKey(context: Context, publicKey: String) {
        // 转义公钥中的单引号（虽然 SSH 公钥通常不含单引号）
        val escaped = publicKey.replace("'", "'\\''")
        val cmd = "mkdir -p ~/.ssh && echo '$escaped' >> ~/.ssh/authorized_keys && chmod 700 ~/.ssh && chmod 600 ~/.ssh/authorized_keys"
        val intent = buildBashIntent(cmd)
        try {
            context.startService(intent)
            Log.i(TAG, "✅ 已通过 RUN_COMMAND 注入公钥到 authorized_keys")
        } catch (e: Exception) {
            Log.w(TAG, "startService 注入公钥失败，尝试 PendingIntent: ${e.message}")
            try {
                val flags = PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
                val pi = PendingIntent.getService(context, 1, intent, flags)
                pi.send()
                Log.i(TAG, "✅ 已通过 PendingIntent 注入公钥到 authorized_keys")
            } catch (e2: Exception) {
                Log.w(TAG, "PendingIntent 注入公钥也失败: ${e2.message}")
            }
        }
    }

    /**
     * sshd 就绪后把前台切回 Hermes。
     */
    fun bringBackHermes(context: Context) {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                context.startActivity(intent)
                Log.i(TAG, "✅ 已切回 Hermes")
            }
        } catch (e: Exception) {
            Log.w(TAG, "切回 Hermes 失败: ${e.message}")
        }
    }

    /**
     * 打开 MIUI 自启动管理页面，引导用户给 Termux 开启自启动权限。
     *
     * @return true 如果成功打开了设置页面
     */
    fun openAutoStartSettings(context: Context): Boolean {
        // MIUI / HyperOS 自启动管理页面的已知 ComponentName 列表
        val autoStartActivities = listOf(
            // MIUI 经典路径
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            // HyperOS / 新版 MIUI
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity"),
        )

        for (component in autoStartActivities) {
            try {
                val intent = Intent().apply {
                    this.component = component
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Log.i(TAG, "✅ 已打开自启动设置: ${component.className}")
                return true
            } catch (_: Exception) {
                // 该路径不存在，尝试下一个
            }
        }
        Log.w(TAG, "未找到 MIUI 自启动设置页面")
        return false
    }

    /**
     * 在主线程显示 Toast 提示用户。
     */
    fun showAutoStartGuide(context: Context) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(
                context,
                "⚠️ 小米系统拦截了 Termux 自启动，请在弹出的设置页中找到 Termux 并开启自启动权限",
                Toast.LENGTH_LONG
            ).show()
        }
        // 尝试打开设置页
        openAutoStartSettings(context)
    }

    /**
     * 先确保 Termux 已启动，等待其初始化完成，再发送 RUN_COMMAND 执行 sshd。
     * 如果 RUN_COMMAND 失败（Termux 尚未就绪），会重试最多 [MAX_LAUNCH_RETRIES] 次。
     * 适合在 IO 协程中调用（包含 delay）。
     */
    suspend fun ensureAndLaunch(context: Context) {
        // 先拉起 Termux
        ensureTermuxRunning(context)
        // 等 Termux 初始化（RunCommandService 注册）
        kotlinx.coroutines.delay(TERMUX_LAUNCH_WAIT_MS)

        // 发送 RUN_COMMAND，失败则重试
        for (attempt in 1..MAX_LAUNCH_RETRIES) {
            try {
                launch(context)
                Log.i(TAG, "RUN_COMMAND 发送成功（第 ${attempt} 次）")
                return
            } catch (e: Exception) {
                Log.w(TAG, "RUN_COMMAND 第 ${attempt} 次失败: ${e.message}")
                if (attempt < MAX_LAUNCH_RETRIES) {
                    kotlinx.coroutines.delay(RETRY_INTERVAL_MS)
                } else {
                    throw e
                }
            }
        }
    }
}
