package com.ai.assistance.operit.ui.features.toolbox.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.BuildCircle
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.ai.assistance.operit.ui.components.CustomScaffold
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ai.assistance.operit.plugins.toolbox.ToolboxScriptDefinition
import com.ai.assistance.operit.plugins.toolbox.ToolboxScriptHookParams
import com.ai.assistance.operit.plugins.toolbox.ToolboxScriptPluginRegistry
import com.ai.assistance.operit.ui.features.toolbox.screens.apppermissions.AppPermissionsScreen
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.FileManagerScreen
import com.ai.assistance.operit.ui.features.toolbox.screens.logcat.LogcatScreen
import com.ai.assistance.operit.ui.features.toolbox.screens.shellexecutor.ShellExecutorScreen
import com.ai.assistance.operit.terminal.main.TerminalScreen as TerminalViewScreen
// import com.ai.assistance.operit.ui.features.toolbox.screens.terminalconfig.TerminalAutoConfigScreen
import com.ai.assistance.operit.ui.features.toolbox.screens.uidebugger.UIDebuggerScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.content.Context
import com.ai.assistance.operit.R
import androidx.compose.ui.res.stringResource
import com.ai.assistance.operit.terminal.TerminalManager
import com.ai.assistance.operit.terminal.rememberTerminalEnv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// 工具类别
enum class ToolCategory {
    ALL,
    FILE_MANAGEMENT,
    DEVELOPMENT,
    SYSTEM;

    fun getDisplayName(context: Context): String {
        return when (this) {
            ALL -> context.getString(R.string.tool_category_all)
            FILE_MANAGEMENT -> context.getString(R.string.tool_category_file_management)
            DEVELOPMENT -> context.getString(R.string.tool_category_development)
            SYSTEM -> context.getString(R.string.tool_category_system)
        }
    }
}

data class Tool(
        val name: String,
        val icon: ImageVector,
        val description: String,
        val category: ToolCategory,
        val onClick: () -> Unit
)

/** 工具箱屏幕，展示可用的各种工具 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolboxScreen(
        navController: NavController,
        onFileManagerSelected: () -> Unit,
        onTerminalSelected: () -> Unit,
        onAppPermissionsSelected: () -> Unit,
        onUIDebuggerSelected: () -> Unit,
        onShellExecutorSelected: () -> Unit,
        onLogcatSelected: () -> Unit,
        onTextToSpeechSelected: () -> Unit,
        onSpeechToTextSelected: () -> Unit,
        onToolTesterSelected: () -> Unit,
        onAgreementSelected: () -> Unit,
        onDefaultAssistantGuideSelected: () -> Unit,
        onProcessLimitRemoverSelected: () -> Unit,
        onHtmlPackagerSelected: () -> Unit,
        onAutoGlmOneClickSelected: () -> Unit,
        onAutoGlmToolSelected: () -> Unit,
        onSkillRecorderSelected: () -> Unit,
        onSqlViewerSelected: () -> Unit,
        onTokenConfigSelected: () -> Unit,
        onToolPkgComposeDslSelected: (containerPackageName: String, uiModuleId: String, title: String) -> Unit
) {
        // 屏幕配置信息，用于响应式布局
        val configuration = LocalConfiguration.current
        val context = LocalContext.current
        var dynamicScriptDefinitions by
                remember {
                        mutableStateOf(emptyList<ToolboxScriptDefinition>())
                }

        LaunchedEffect(configuration) {
                dynamicScriptDefinitions =
                        withContext(Dispatchers.IO) {
                                ToolboxScriptPluginRegistry.createDefinitions(
                                        ToolboxScriptHookParams(
                                                context = context
                                        )
                                )
                        }
        }

        // 当前选中的分类过滤器
        var selectedCategory by remember { mutableStateOf(ToolCategory.ALL) }

        val tools =
                listOf(
                        Tool(
                                name = stringResource(R.string.tool_test_center),
                                icon = Icons.Default.BuildCircle,
                                description = stringResource(R.string.tool_test_center_desc),
                                category = ToolCategory.DEVELOPMENT,
                                onClick = onToolTesterSelected
                        ),
                        Tool(
                                name = stringResource(R.string.tool_file_manager),
                                icon = Icons.Rounded.Folder,
                                description = stringResource(R.string.tool_file_manager_desc),
                                category = ToolCategory.FILE_MANAGEMENT,
                                onClick = onFileManagerSelected
                        ),
                        Tool(
                                name = stringResource(R.string.tool_tts),
                                icon = Icons.Default.RecordVoiceOver,
                                description = stringResource(R.string.tool_tts_desc),
                                category = ToolCategory.SYSTEM,
                                onClick = onTextToSpeechSelected
                        ),
                        Tool(
                                name = stringResource(R.string.tool_speech_recognition),
                                icon = Icons.Default.Mic,
                                description = stringResource(R.string.tool_speech_recognition_desc),
                                category = ToolCategory.SYSTEM,
                                onClick = onSpeechToTextSelected
                        ),
                        Tool(
                                name = stringResource(R.string.tool_permission_manager),
                                icon = Icons.Rounded.Security,
                                description = stringResource(R.string.tool_permission_manager_desc),
                                category = ToolCategory.SYSTEM,
                                onClick = onAppPermissionsSelected
                        ),
                        Tool(
                                name = stringResource(R.string.tool_user_agreement),
                                icon = Icons.Default.Policy,
                                description = stringResource(R.string.tool_user_agreement_desc),
                                category = ToolCategory.SYSTEM,
                                onClick = onAgreementSelected
                        ),
                        Tool(
                                name = stringResource(R.string.tool_default_assistant_guide),
                                icon = Icons.Default.Assistant,
                                description = stringResource(R.string.tool_default_assistant_guide_desc),
                                category = ToolCategory.SYSTEM,
                                onClick = onDefaultAssistantGuideSelected
                        ),
                        Tool(
                                name = stringResource(R.string.tool_terminal),
                                icon = Icons.Rounded.Terminal,
                                description = stringResource(R.string.tool_terminal_desc),
                                category = ToolCategory.DEVELOPMENT,
                                onClick = onTerminalSelected
                        ),
                        Tool(
                                name = stringResource(R.string.tool_ui_debugger),
                                icon = Icons.Default.DeviceHub,
                                description = stringResource(R.string.tool_ui_debugger_desc),
                                category = ToolCategory.DEVELOPMENT,
                                onClick = onUIDebuggerSelected
                        ),
                        Tool(
                                name = stringResource(R.string.tool_shell_executor),
                                icon = Icons.Default.Code,
                                description = stringResource(R.string.tool_shell_executor_desc),
                                category = ToolCategory.DEVELOPMENT,
                                onClick = onShellExecutorSelected
                        ),
                        Tool(
                                name = stringResource(R.string.tool_log_viewer),
                                icon = Icons.Default.DataObject,
                                description = stringResource(R.string.tool_log_viewer_desc),
                                category = ToolCategory.DEVELOPMENT,
                                onClick = onLogcatSelected
                        ),
                        Tool(
                                name = stringResource(R.string.tool_sql_viewer),
                                icon = Icons.Default.TableView,
                                description = stringResource(R.string.tool_sql_viewer_desc),
                                category = ToolCategory.DEVELOPMENT,
                                onClick = onSqlViewerSelected
                        ),
                        Tool(
                                name = stringResource(R.string.token_config),
                                icon = Icons.Default.Token,
                                description = stringResource(R.string.token_config_title),
                                category = ToolCategory.SYSTEM,
                                onClick = onTokenConfigSelected
                        ),
                        Tool(
                                name = stringResource(R.string.tool_process_limit_remover),
                                icon = Icons.Default.LockOpen,
                                description = stringResource(R.string.tool_process_limit_remover_desc),
                                category = ToolCategory.SYSTEM,
                                onClick = onProcessLimitRemoverSelected
                        ),
                        Tool(
                                name = stringResource(R.string.tool_html_packager),
                                icon = Icons.Default.Html,
                                description = stringResource(R.string.tool_html_packager_desc),
                                category = ToolCategory.DEVELOPMENT,
                                onClick = onHtmlPackagerSelected
                        ),
                        Tool(
                                name = stringResource(R.string.tool_autoglm_one_click),
                                icon = Icons.Default.AutoMode,
                                description = stringResource(R.string.tool_autoglm_one_click_desc),
                                category = ToolCategory.DEVELOPMENT,
                                onClick = onAutoGlmOneClickSelected
                        ),
                        Tool(
                                name = stringResource(R.string.tool_autoglm_tool),
                                icon = Icons.Default.AutoMode,
                                description = stringResource(R.string.tool_autoglm_tool_desc),
                                category = ToolCategory.DEVELOPMENT,
                                onClick = onAutoGlmToolSelected
                        ),
                        Tool(
                                name = stringResource(R.string.tool_skill_recorder),
                                icon = Icons.Default.FiberManualRecord,
                                description = stringResource(R.string.tool_skill_recorder_desc),
                                category = ToolCategory.DEVELOPMENT,
                                onClick = onSkillRecorderSelected
                        )
                )
        val dynamicTools =
                dynamicScriptDefinitions.map { module ->
                        Tool(
                                name = module.title,
                                icon = Icons.Default.Extension,
                                description =
                                        module.description.ifBlank {
                                                module.containerPackageName
                                        },
                                category = ToolCategory.DEVELOPMENT,
                                onClick = {
                                        onToolPkgComposeDslSelected(
                                                module.containerPackageName,
                                                module.uiModuleId,
                                                module.title
                                        )
                                }
                        )
                }
        val allTools = tools + dynamicTools

        // 根据选中的分类过滤工具
        val filteredTools =
                if (selectedCategory == ToolCategory.ALL) {
                        allTools
                } else {
                        allTools.filter { it.category == selectedCategory }
                }

        Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                        // 顶部标题区域
                        TopAppSection()

                        // 分类选择器
                        CategorySelector(
                                selectedCategory = selectedCategory,
                                onCategorySelected = { selectedCategory = it }
                        )

                        // 工具网格
                        LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 156.dp),
                                contentPadding = PaddingValues(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize()
                        ) { items(filteredTools) { tool -> ToolCard(tool = tool) } }
                }
        }
}

@Composable
private fun TopAppSection() {
        Column(
                modifier =
                        Modifier.fillMaxWidth()
                                .background(
                                        brush =
                                                Brush.verticalGradient(
                                                        colors =
                                                                listOf(
                                                                        MaterialTheme.colorScheme
                                                                                .primary.copy(
                                                                                alpha = 0.05f
                                                                        ),
                                                                        MaterialTheme.colorScheme
                                                                                .surface
                                                                )
                                                )
                                )
                                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp)
        ) {
                Text(
                        text = stringResource(R.string.toolbox),
                        style =
                                MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.Bold
                                )
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                        text = stringResource(R.string.toolbox_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
        }
}

@Composable
private fun CategorySelector(
        selectedCategory: ToolCategory,
        onCategorySelected: (ToolCategory) -> Unit
) {
        val categories = ToolCategory.values()

        // 水平滚动的分类选择器
        Row(
                modifier =
                        Modifier.fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
                categories.forEach { category ->
                        val isSelected = selectedCategory == category
                        val backgroundColor =
                                if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                } else {
                                        MaterialTheme.colorScheme.surface
                                }

                        val textColor =
                                if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                }

                        Surface(
                                onClick = { onCategorySelected(category) },
                                shape = RoundedCornerShape(20.dp),
                                color = backgroundColor,
                                tonalElevation = if (isSelected) 0.dp else 1.dp,
                                shadowElevation = if (isSelected) 2.dp else 0.dp,
                                modifier = Modifier.height(36.dp)
                        ) {
                                Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                ) {
                                        Text(
                                                text = category.getDisplayName(LocalContext.current),
                                                style =
                                                        MaterialTheme.typography.bodyMedium.copy(
                                                                fontWeight =
                                                                        if (isSelected)
                                                                                FontWeight.Bold
                                                                        else FontWeight.Normal
                                                        ),
                                                color = textColor
                                        )
                                }
                        }
                }
        }
}

/** 工具项卡片 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolCard(tool: Tool) {
        var isPressed by remember { mutableStateOf(false) }

        // 创建协程作用域
        val scope = rememberCoroutineScope()

        // 缩放动画
        val scale by
                animateFloatAsState(
                        targetValue = if (isPressed) 0.95f else 1f,
                        animationSpec = tween(durationMillis = if (isPressed) 100 else 200),
                        label = "scale"
                )

        Card(
                onClick = {
                        isPressed = true
                        // 使用rememberCoroutineScope来启动协程
                        scope.launch {
                                delay(100)
                                tool.onClick()
                                isPressed = false
                        }
                },
                modifier = Modifier.fillMaxWidth().height(156.dp).scale(scale),
                colors =
                        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation =
                        CardDefaults.cardElevation(
                                defaultElevation = 2.dp,
                                pressedElevation = 8.dp
                        ),
                shape = RoundedCornerShape(12.dp)
        ) {
                // 卡片内容
                Column(
                        modifier = Modifier.fillMaxSize().padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                        // 工具图标带有背景圆圈
                        Box(
                                contentAlignment = Alignment.Center,
                                modifier =
                                        Modifier.size(48.dp)
                                                .clip(CircleShape)
                                                .background(
                                                        color =
                                                                when (tool.category) {
                                                                        ToolCategory
                                                                                .FILE_MANAGEMENT ->
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .primaryContainer
                                                                        ToolCategory.DEVELOPMENT ->
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .tertiaryContainer
                                                                        ToolCategory.SYSTEM ->
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .secondaryContainer
                                                                        else ->
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .primaryContainer
                                                                }
                                                )
                                                .padding(8.dp)
                        ) {
                                Icon(
                                        imageVector = tool.icon,
                                        contentDescription = tool.name,
                                        modifier = Modifier.size(24.dp),
                                        tint =
                                                when (tool.category) {
                                                        ToolCategory.FILE_MANAGEMENT ->
                                                                MaterialTheme.colorScheme.primary
                                                        ToolCategory.DEVELOPMENT ->
                                                                MaterialTheme.colorScheme.tertiary
                                                        ToolCategory.SYSTEM ->
                                                                MaterialTheme.colorScheme.secondary
                                                        else -> MaterialTheme.colorScheme.primary
                                                }
                                )
                        }

                        Text(
                                text = tool.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                minLines = 1,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                        )

                        Text(
                                text = tool.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 2.dp),
                                minLines = 1,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                        )
                }
        }
}


/** 显示文件管理器工具屏幕 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerToolScreen(navController: NavController) {
        CustomScaffold() { paddingValues ->
                Box(modifier = Modifier.padding(paddingValues)) {
                        FileManagerScreen(navController = navController)
                }
        }
}

/** 显示终端工具屏幕 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalToolScreen(navController: NavController, forceShowSetup: Boolean = false) {
        val context = LocalContext.current
        val terminalManager = remember { TerminalManager.getInstance(context) }
        val terminalEnv = rememberTerminalEnv(terminalManager = terminalManager, forceShowSetup = forceShowSetup)
        CustomScaffold() { paddingValues ->
                Box(modifier = Modifier.padding(paddingValues)) { TerminalViewScreen(env = terminalEnv) }
        }
}

/** 显示终端自动配置工具屏幕 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalAutoConfigToolScreen(navController: NavController) {
        CustomScaffold() { paddingValues ->
                Box(modifier = Modifier.padding(paddingValues)) {
                        // TODO: 需要重构以适配新的终端架构
                        // TerminalAutoConfigScreen(navController = navController)
                        Text(
                            text = stringResource(R.string.tool_terminal_auto_config_under_construction),
                            modifier = Modifier.padding(16.dp)
                        )
                }
        }
}

/** 显示应用权限管理工具屏幕 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPermissionsToolScreen(navController: NavController) {
        CustomScaffold() { paddingValues ->
                Box(modifier = Modifier.padding(paddingValues)) {
                        AppPermissionsScreen(navController = navController)
                }
        }
}

/** 显示UI调试工具屏幕 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UIDebuggerToolScreen(navController: NavController) {
        CustomScaffold() { paddingValues ->
                Box(modifier = Modifier.padding(paddingValues)) {
                        UIDebuggerScreen(navController = navController)
                }
        }
}


/** 显示Shell命令执行器屏幕 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShellExecutorToolScreen(navController: NavController) {
        CustomScaffold() { paddingValues ->
                Box(modifier = Modifier.padding(paddingValues)) {
                        ShellExecutorScreen(navController = navController)
                }
        }
}

/** 显示日志查看器屏幕 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogcatToolScreen(navController: NavController) {
        CustomScaffold() { paddingValues ->
                Box(modifier = Modifier.padding(paddingValues)) {
                        LogcatScreen(navController = navController)
                }
        }
}

/** 显示工具测试屏幕 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolTesterToolScreen(navController: NavController) {
        CustomScaffold() { paddingValues ->
                Box(modifier = Modifier.padding(paddingValues)) {
                        com.ai.assistance.operit.ui.features.toolbox.screens.tooltester
                                .ToolTesterScreen(navController = navController)
                }
        }
}

/** 显示默认助手设置引导屏幕 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DefaultAssistantGuideToolScreen(navController: NavController) {
        com.ai.assistance.operit.ui.features.toolbox.screens.defaultassistant
                .DefaultAssistantGuideScreen(navController = navController)
}
