package com.ai.assistance.operit.util

import android.content.Context
import android.content.Intent
import com.ai.assistance.operit.ui.error.CrashReportActivity
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

class GlobalExceptionHandler(private val context: Context) : Thread.UncaughtExceptionHandler {

    private val defaultUEH: Thread.UncaughtExceptionHandler? =
            Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, ex: Throwable) {
        val stackTrace = StringWriter()
        ex.printStackTrace(PrintWriter(stackTrace))
        CrashRecoveryState.markPendingCrashReportLaunch(context)

        val intent =
                Intent(context, CrashReportActivity::class.java).apply {
                    putExtra(CrashReportActivity.EXTRA_STACK_TRACE, stackTrace.toString())
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
        context.startActivity(intent)

        // 终止当前进程
        exitProcess(1)
    }
}
