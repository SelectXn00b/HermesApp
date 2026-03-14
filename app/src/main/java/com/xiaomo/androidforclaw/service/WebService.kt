/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/gateway/(all)
 *
 * AndroidForClaw adaptation: Android service layer.
 */
package com.xiaomo.androidforclaw.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

// TODO: MyWebServer 已删除，Web服务功能将通过新架构实现
class WebService : Service() {
    // private var server: MyWebServer? = null

    override fun onCreate() {
        super.onCreate()
        Log.d("WebService", "onCreate: Web服务暂时禁用")
        // server = MyWebServer(this)
        // server?.startServer()
    }

    override fun onDestroy() {
        // server?.stopServer()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}