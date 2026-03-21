package com.xiaomo.androidforclaw.workspace

import android.os.Environment
import java.io.File

/**
 * Unified storage path constants for /sdcard/.androidforclaw.
 * Requires MANAGE_EXTERNAL_STORAGE permission — prompt the user if not granted.
 */
object StoragePaths {

    val root: File = File(Environment.getExternalStorageDirectory(), ".androidforclaw")

    val config: File get() = File(root, "config")
    val workspace: File get() = File(root, "workspace")
    val logs: File get() = File(root, "logs")
    val skills: File get() = File(root, "skills")
    val openclawConfig: File get() = File(root, "openclaw.json")
}
