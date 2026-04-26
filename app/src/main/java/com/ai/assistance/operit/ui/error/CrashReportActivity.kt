package com.ai.assistance.operit.ui.error

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import com.ai.assistance.operit.util.AppLogger
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ai.assistance.operit.ui.components.CustomScaffold
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.toolbox.screens.logcat.LogcatExportHelper
import com.ai.assistance.operit.ui.main.MainActivity
import com.ai.assistance.operit.ui.theme.OperitTheme
import com.ai.assistance.operit.util.CrashFeedbackBridge
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CrashReportActivity : ComponentActivity() {

    companion object {
        const val EXTRA_STACK_TRACE = "extra_stack_trace"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val stackTrace = intent.getStringExtra(EXTRA_STACK_TRACE) ?: "No stack trace available."

        AppLogger.e("CrashReportActivity", "stackTrace: $stackTrace")
        setContent { OperitTheme { CrashReportScreen(stackTrace = stackTrace) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrashReportScreen(stackTrace: String) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isExportingLogs by remember { mutableStateOf(false) }
    var logExportMessage by remember { mutableStateOf<String?>(null) }
    var logExportSuccess by remember { mutableStateOf<Boolean?>(null) }

    CustomScaffold(
            topBar = {
                TopAppBar(
                        title = { Text(stringResource(id = R.string.title_activity_crash_report)) },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            titleContentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                )
            },
            content = { paddingValues ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        stringResource(id = R.string.crash_report_header),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    // 主要操作按钮
                    Button(
                        onClick = { restartApp(context) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text(stringResource(id = R.string.crash_report_restart_button))
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 次要操作按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                copyToClipboard(context, stackTrace)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text(stringResource(id = R.string.crash_report_copy_button))
                        }

                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    exportToFile(context, stackTrace)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text(stringResource(id = R.string.crash_report_export_button))
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                isExportingLogs = true
                                logExportMessage = null
                                logExportSuccess = null
                                try {
                                    val result = LogcatExportHelper.exportLogs(context)
                                    logExportMessage = result.message
                                    logExportSuccess = result.success
                                } finally {
                                    isExportingLogs = false
                                }
                            }
                        },
                        enabled = !isExportingLogs,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isExportingLogs) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(ButtonDefaults.IconSize),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.Description,
                                contentDescription = null,
                                modifier = Modifier.size(ButtonDefaults.IconSize)
                            )
                        }
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text(stringResource(id = R.string.crash_report_export_logs_button))
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Send Feedback button
                    OutlinedButton(
                        onClick = {
                            CrashFeedbackBridge.savePendingCrashFeedback(context, stackTrace)
                            restartApp(context)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            Icons.Default.RateReview,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text(stringResource(id = R.string.crash_report_send_feedback))
                    }

                    if (logExportMessage != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (logExportSuccess == true) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.errorContainer
                                }
                            )
                        ) {
                            SelectionContainer {
                                Text(
                                    text = logExportMessage.orEmpty(),
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    color = if (logExportSuccess == true) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onErrorContainer
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        stringResource(id = R.string.crash_report_details_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            text = stackTrace,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.2
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
    )
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("error_stack_trace", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, R.string.crash_report_copy_success, Toast.LENGTH_SHORT).show()
}

private fun exportToFile(context: Context, text: String) {
    try {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val errorDir = File(downloadsDir, "Operit/error")
        if (!errorDir.exists()) {
            errorDir.mkdirs()
        }

        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val file = File(errorDir, "error-report-$timestamp.log")

        FileOutputStream(file).use {
            it.write(text.toByteArray())
        }
        val successMsg = context.getString(R.string.crash_report_export_success, file.absolutePath)
        Toast.makeText(context, successMsg, Toast.LENGTH_LONG).show()

    } catch (e: Exception) {
        val errorMsg = context.getString(R.string.crash_report_export_failed, e.localizedMessage)
        Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
        e.printStackTrace()
    }
}

private fun restartApp(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        ?: Intent(context, MainActivity::class.java)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

    val flags = PendingIntent.FLAG_CANCEL_CURRENT or
        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
    val pendingIntent = PendingIntent.getActivity(context, 0, intent, flags)

    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    alarmManager.setExact(
        AlarmManager.RTC,
        System.currentTimeMillis() + 200,
        pendingIntent
    )

    (context as? Activity)?.finishAffinity()
    android.os.Process.killProcess(android.os.Process.myPid())
    kotlin.system.exitProcess(0)
}
