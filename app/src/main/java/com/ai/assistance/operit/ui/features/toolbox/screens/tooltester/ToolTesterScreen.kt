package com.ai.assistance.operit.ui.features.toolbox.screens.tooltester

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ai.assistance.operit.ui.components.CustomScaffold
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.util.OperitPaths
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

/** 工具测试屏幕 - 最终版网格布局 + 中间弹窗详情 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolTesterScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val aiToolHandler = remember { AIToolHandler.getInstance(context) }
    val focusRequester = remember { FocusRequester() }

    var testResults by remember { mutableStateOf<Map<String, ToolTestResult>>(emptyMap()) }
    var isTestingAll by remember { mutableStateOf(false) }
    var selectedTestForDetails by remember { mutableStateOf<ToolTest?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    var testInputText by remember { mutableStateOf("") }

    val toolGroups = remember { getFinalToolTestGroups(context) }

    suspend fun runTest(toolTest: ToolTest) {
        // UI preparation phase on Main thread
        if (toolTest.id == "set_input_text") {
            // If the dialog is showing for a single test run, dismiss it first.
            if (showDialog) {
                showDialog = false
                delay(300) // Wait for dialog animation to finish.
            }
            focusRequester.requestFocus()
            delay(100) // Wait for focus to be processed by the system.
        }

        // Mark test as running on Main thread
        testResults = testResults.toMutableMap().apply {
            put(toolTest.id, ToolTestResult(TestStatus.RUNNING, null))
        }

        // Execution phase on IO thread
        val result = withContext(Dispatchers.IO) {
            try {
                aiToolHandler.executeTool(AITool(toolTest.id, toolTest.parameters))
            } catch (e: Exception) {
                ToolResult(toolName = toolTest.id, success = false, result = StringResultData(""), error = e.message ?: "Unknown error")
            }
        }

        // Update UI with result on Main thread
        testResults = testResults.toMutableMap().apply {
            put(toolTest.id, ToolTestResult(if (result.success) TestStatus.SUCCESS else TestStatus.FAILED, result))
        }
    }

    CustomScaffold() { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues).fillMaxSize().verticalScroll(rememberScrollState())) {
            // Header Section
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(context.getString(R.string.ai_tools_availability_test), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(context.getString(R.string.ai_tools_grouped_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                
                OutlinedTextField(
                    value = testInputText,
                    onValueChange = { testInputText = it },
                    label = { Text(context.getString(R.string.test_input_field)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .testTag("tool_tester_input")
                )

                Button(
                    onClick = {
                        scope.launch {
                            isTestingAll = true
                            testResults = emptyMap()
                            for (group in toolGroups.filter { !it.isManual }) {
                                if (group.sequential) {
                                    for (test in group.tests) { runTest(test) }
                                } else {
                                    val jobs = group.tests.map { test -> launch { runTest(test) } }
                                    jobs.forEach { it.join() }
                                }
                            }
                            isTestingAll = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isTestingAll
                ) {
                    if (isTestingAll) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text(context.getString(R.string.batch_testing_in_progress))
                        }
                    } else {
                        Text(context.getString(R.string.start_comprehensive_test))
                    }
                }
            }
            HorizontalDivider()
            // Grid Body
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 75.dp),
                modifier = Modifier.fillMaxWidth().height(800.dp), // 设定一个足够的高度以避免嵌套滚动问题
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                toolGroups.forEach { group ->
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = group.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }
                    items(group.tests) { toolTest ->
                        ToolTestGridItem(
                            toolTest = toolTest,
                            testResult = testResults[toolTest.id]
                        ) {
                            selectedTestForDetails = it
                            showDialog = true
                        }
                    }
                }
            }
        }
    }

    if (showDialog && selectedTestForDetails != null) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(),
                shape = MaterialTheme.shapes.large
            ) {
                ToolDetailsSheet(
                    toolTest = selectedTestForDetails!!,
                    testResult = testResults[selectedTestForDetails!!.id],
                    scope = scope,
                    context = context,
                    onTest = { test -> scope.launch { runTest(test); } },
                    onDismiss = { showDialog = false }
                )
            }
        }
    }
}

@Composable
fun ToolTestGridItem(toolTest: ToolTest, testResult: ToolTestResult?, onClick: (ToolTest) -> Unit) {
    val status = testResult?.status
    val color = when (status) {
        TestStatus.SUCCESS -> MaterialTheme.colorScheme.primaryContainer
        TestStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
        TestStatus.RUNNING -> MaterialTheme.colorScheme.tertiaryContainer
        null -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when (status) {
        TestStatus.SUCCESS -> MaterialTheme.colorScheme.onPrimaryContainer
        TestStatus.FAILED -> MaterialTheme.colorScheme.onErrorContainer
        TestStatus.RUNNING -> MaterialTheme.colorScheme.onTertiaryContainer
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.size(65.dp),
        colors = CardDefaults.cardColors(containerColor = color),
        onClick = { onClick(toolTest) }
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(toolTest.name, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, color = contentColor, lineHeight = MaterialTheme.typography.labelSmall.fontSize * 1.1)
        }
    }
}

@Composable
fun ToolDetailsSheet(
    toolTest: ToolTest,
    testResult: ToolTestResult?,
    scope: CoroutineScope,
    context: android.content.Context,
    onTest: (ToolTest) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val (icon, color) = when(testResult?.status) {
                TestStatus.SUCCESS -> Icons.Default.CheckCircle to MaterialTheme.colorScheme.primary
                TestStatus.FAILED -> Icons.Default.Error to MaterialTheme.colorScheme.error
                TestStatus.RUNNING -> Icons.Default.HourglassTop to MaterialTheme.colorScheme.tertiary
                null -> Icons.Outlined.HelpOutline to MaterialTheme.colorScheme.onSurfaceVariant
            }
            Icon(icon, contentDescription = "Status", tint = color, modifier = Modifier.size(32.dp))
            Column {
                Text(toolTest.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(toolTest.id, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalDivider()
        Text(toolTest.description, style = MaterialTheme.typography.bodyMedium)
        
        if (toolTest.parameters.isNotEmpty()) {
            Text(context.getString(R.string.parameters_label), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)).padding(8.dp)) {
                toolTest.parameters.forEach { param ->
                    val rawValue = param.value
                    val preview = if (rawValue.length > 200) {
                        "${rawValue.take(200)}... (len=${rawValue.length})"
                    } else {
                        rawValue
                    }
                    Text(" • ${param.name}: $preview", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        testResult?.result?.let { result ->
            Text(context.getString(R.string.detailed_result), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            val resultText = (if (result.success) result.result.toString() else result.error ?: context.getString(R.string.unknown_error)).take(1000)
            Text(
                text = resultText,
                style = MaterialTheme.typography.bodyMedium,
                color = if (result.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)).padding(8.dp)
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            TextButton(onClick = onDismiss) { Text(context.getString(R.string.close)) }
            Button(onClick = { onTest(toolTest) }, enabled = testResult?.status != TestStatus.RUNNING) {
                Text(context.getString(R.string.retest))
            }
        }
    }
}


private fun getFinalToolTestGroups(context: android.content.Context): List<ToolGroup> {
    val testBaseDir = OperitPaths.testPathSdcard()
    val testFile = "$testBaseDir/test_file.txt"
    val testLargeWriteFile = "$testBaseDir/write_large.txt"
    val testFileCopy = "$testBaseDir/test_file_copy.txt"
    val testZip = "$testBaseDir/test.zip"
    val testUnzipDir = "$testBaseDir/unzipped"
    val testImage = "$testBaseDir/test_image.png"
    val largeWriteContent = "Large write test content.".repeat(12000)

    return listOf(
        ToolGroup(context.getString(R.string.env_setup_group), true, false, listOf(
            ToolTest("make_directory", context.getString(R.string.create_test_dir), context.getString(R.string.create_test_dir_desc), listOf(ToolParameter("path", testBaseDir), ToolParameter("create_parents", "true"))),
            ToolTest("download_file", context.getString(R.string.download_test_image), context.getString(R.string.download_test_image_desc), listOf(ToolParameter("url", "https://picsum.photos/100"), ToolParameter("destination", testImage))),
            ToolTest("write_file", context.getString(R.string.create_text_file), context.getString(R.string.create_text_file_desc), listOf(ToolParameter("path", testFile), ToolParameter("content", "This is a test file for Operit tool testing.")))
        )),
        ToolGroup(context.getString(R.string.basic_http_group), false, false, listOf(
            ToolTest("sleep", context.getString(R.string.delay_test), context.getString(R.string.delay_test_desc), listOf(ToolParameter("duration_ms", "1000"))),
            ToolTest("device_info", context.getString(R.string.device_info_test), context.getString(R.string.device_info_test_desc), emptyList()),
            ToolTest("http_request", context.getString(R.string.http_get_test), context.getString(R.string.http_get_test_desc), listOf(ToolParameter("url", "https://httpbin.org/get"), ToolParameter("method", "GET"))),
            ToolTest("multipart_request", context.getString(R.string.file_upload_test), context.getString(R.string.file_upload_test_desc), listOf(ToolParameter("url", "https://httpbin.org/post"), ToolParameter("method", "POST"), ToolParameter("files", testFile))),
            ToolTest("manage_cookies", context.getString(R.string.manage_cookies_test), context.getString(R.string.manage_cookies_test_desc), listOf(ToolParameter("action", "get"), ToolParameter("domain", "google.com"))),
            ToolTest("visit_web", context.getString(R.string.visit_web_test), context.getString(R.string.visit_web_test_desc), listOf(ToolParameter("url", "https://www.baidu.com"))),
            ToolTest("use_package", context.getString(R.string.use_package_test), context.getString(R.string.use_package_test_desc), listOf(ToolParameter("package_name", "non_existent_package"))),
            ToolTest("query_memory", context.getString(R.string.query_memory_test), context.getString(R.string.query_memory_test_desc), listOf(ToolParameter("query", "test")))
        )),
        ToolGroup(context.getString(R.string.file_readonly_group), false, false, listOf(
            ToolTest("list_files", context.getString(R.string.list_files_test), context.getString(R.string.list_files_test_desc), listOf(ToolParameter("path", testBaseDir))),
            ToolTest("file_exists", context.getString(R.string.file_exists_test), context.getString(R.string.file_exists_test_desc), listOf(ToolParameter("path", testFile))),
            ToolTest("read_file", context.getString(R.string.ocr_read_test), context.getString(R.string.ocr_read_test_desc), listOf(ToolParameter("path", testImage))),
            ToolTest("read_file_part", context.getString(R.string.chunk_read_test), context.getString(R.string.chunk_read_test_desc), listOf(ToolParameter("path", testFile), ToolParameter("partIndex", "0"))),
            ToolTest("file_info", context.getString(R.string.file_info_test), context.getString(R.string.file_info_test_desc), listOf(ToolParameter("path", testFile))),
            ToolTest("find_files", context.getString(R.string.find_files_test), context.getString(R.string.find_files_test_desc), listOf(ToolParameter("path", testBaseDir), ToolParameter("pattern", "*.txt")))
        )),
        ToolGroup(context.getString(R.string.file_write_group), true, false, listOf(
            ToolTest(
                "write_file",
                context.getString(R.string.write_large_file_test),
                context.getString(R.string.write_large_file_test_desc),
                listOf(
                    ToolParameter("path", testLargeWriteFile),
                    ToolParameter("content", largeWriteContent)
                )
            ),
            ToolTest("copy_file", context.getString(R.string.copy_file_test), context.getString(R.string.copy_file_test_desc), listOf(ToolParameter("source", testFile), ToolParameter("destination", testFileCopy))),
            ToolTest("move_file", context.getString(R.string.move_file_test), context.getString(R.string.move_file_test_desc), listOf(ToolParameter("source", testFileCopy), ToolParameter("destination", "$testBaseDir/moved_file.txt"))),
            ToolTest("zip_files", context.getString(R.string.zip_files_test), context.getString(R.string.zip_files_test_desc), listOf(ToolParameter("source", testBaseDir), ToolParameter("destination", testZip))),
            ToolTest("unzip_files", context.getString(R.string.unzip_files_test), context.getString(R.string.unzip_files_test_desc), listOf(ToolParameter("source", testZip), ToolParameter("destination", testUnzipDir))),
        )),
        ToolGroup(context.getString(R.string.system_group), false, false, listOf(
            ToolTest("list_installed_apps", context.getString(R.string.list_apps_test), context.getString(R.string.list_apps_test_desc), listOf(ToolParameter("include_system_apps", "false"))),
            ToolTest("get_notifications", context.getString(R.string.get_notifications_test), context.getString(R.string.get_notifications_test_desc), listOf(ToolParameter("limit", "5"))),
            ToolTest("get_device_location", context.getString(R.string.device_location_test), context.getString(R.string.device_location_test_desc), listOf(ToolParameter("high_accuracy", "false"))),
            ToolTest("get_system_setting", context.getString(R.string.read_system_setting_test), context.getString(R.string.read_system_setting_test_desc), listOf(ToolParameter("setting", "screen_off_timeout"))),
            ToolTest("modify_system_setting", context.getString(R.string.write_system_setting_test), context.getString(R.string.write_system_setting_test_desc), listOf(ToolParameter("setting", "test_setting"), ToolParameter("value", "1"), ToolParameter("namespace", "system")))
        )),
        ToolGroup(context.getString(R.string.ui_automation_group), false, false, listOf(
            ToolTest("get_page_info", context.getString(R.string.page_info_test), context.getString(R.string.page_info_test_desc), emptyList()),
            ToolTest("press_key", context.getString(R.string.simulate_key_test), context.getString(R.string.simulate_key_test_desc), listOf(ToolParameter("key_code", "KEYCODE_VOLUME_UP"))),
            ToolTest("set_input_text", context.getString(R.string.text_input_test), context.getString(R.string.text_input_test_desc), listOf(ToolParameter("text", "Hello from Operit!"))),
            ToolTest("tap", context.getString(R.string.simulate_tap_test), context.getString(R.string.simulate_tap_test_desc), listOf(ToolParameter("x", "1"), ToolParameter("y", "1"))),
            ToolTest("swipe", context.getString(R.string.simulate_swipe_test), context.getString(R.string.simulate_swipe_test_desc), listOf(ToolParameter("start_x", "500"), ToolParameter("start_y", "1000"), ToolParameter("end_x", "500"), ToolParameter("end_y", "1200")))
        )),
        ToolGroup(context.getString(R.string.cleanup_group), true, false, listOf(
            ToolTest("delete_file", context.getString(R.string.cleanup_test_dir), context.getString(R.string.cleanup_test_dir_desc), listOf(ToolParameter("path", testBaseDir), ToolParameter("recursive", "true")))
        ))
    )
}


data class ToolTest(val id: String, val name: String, val description: String, val parameters: List<ToolParameter>)
data class ToolTestResult(val status: TestStatus, val result: ToolResult?)
enum class TestStatus { SUCCESS, FAILED, RUNNING }
data class ToolGroup(val name: String, val sequential: Boolean, val isManual: Boolean, val tests: List<ToolTest>)
