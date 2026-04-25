package com.ai.assistance.operit.ui.features.toolbox.screens.logcat

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.ai.assistance.operit.R
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LogcatExportResult(
    val message: String,
    val success: Boolean
)

object LogcatExportHelper {

    suspend fun exportLogs(context: Context): LogcatExportResult = withContext(Dispatchers.IO) {
        try {
            val logsToSave = LogcatManager(context).loadInitialLogs()
            if (logsToSave.isEmpty()) {
                return@withContext LogcatExportResult(
                    message = context.getString(R.string.logcat_no_logs_to_save),
                    success = false
                )
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "operit_log_$timestamp.txt"
            val content = buildLogContent(context, logsToSave)
            val filePath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveUsingMediaStore(context, fileName, content)
            } else {
                saveUsingFileSystem(context, fileName, content)
            }

            LogcatExportResult(
                message = context.getString(R.string.logcat_saved_to, filePath),
                success = true
            )
        } catch (e: Exception) {
            LogcatExportResult(
                message = context.getString(
                    R.string.logcat_save_failed,
                    e.message ?: context.getString(R.string.logcat_unknown_error)
                ),
                success = false
            )
        }
    }

    private fun buildLogContent(context: Context, logsToSave: List<LogRecord>): String {
        val exportTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        return buildString {
            appendLine(context.getString(R.string.logcat_header))
            appendLine(context.getString(R.string.logcat_date, exportTime))
            appendLine(context.getString(R.string.logcat_total_count, logsToSave.size))
            appendLine("===================================")
            appendLine()

            logsToSave.forEach { record ->
                val recordTimestamp =
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
                        .format(Date(record.timestamp))
                val tag = record.tag ?: ""
                appendLine("$recordTimestamp ${record.level.symbol}/$tag: ${record.message}")
            }
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun saveUsingMediaStore(context: Context, fileName: String, content: String): String {
        try {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/operit")
            }
            val uri = context.contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                contentValues
            ) ?: throw Exception(context.getString(R.string.logcat_cannot_create_file))

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(content.toByteArray())
            } ?: throw Exception(context.getString(R.string.logcat_cannot_open_output_stream))

            val downloadsDir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            return "${downloadsDir.absolutePath}/operit/$fileName"
        } catch (e: Exception) {
            throw Exception(context.getString(R.string.logcat_mediestore_save_failed, e.message ?: ""))
        }
    }

    private fun saveUsingFileSystem(context: Context, fileName: String, content: String): String {
        try {
            val downloadsDir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloadsDir == null || !downloadsDir.exists() && !downloadsDir.mkdirs()) {
                throw Exception(context.getString(R.string.logcat_cannot_create_download_dir))
            }
            val operitDir = File(downloadsDir, "operit")
            if (!operitDir.exists() && !operitDir.mkdirs()) {
                throw Exception(context.getString(R.string.logcat_cannot_create_operit_dir))
            }
            val file = File(operitDir, fileName)
            FileWriter(file).use { it.write(content) }
            if (!file.exists() || file.length() == 0L) {
                throw Exception(context.getString(R.string.logcat_file_create_failed))
            }
            return file.absolutePath
        } catch (e: Exception) {
            throw Exception(context.getString(R.string.logcat_filesystem_save_failed, e.message ?: ""))
        }
    }
}
