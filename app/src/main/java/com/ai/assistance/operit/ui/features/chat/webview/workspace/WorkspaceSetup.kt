package com.ai.assistance.operit.ui.features.chat.webview.workspace

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.chat.webview.createAndGetDefaultWorkspace
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.ai.assistance.operit.data.preferences.ApiPreferences
import kotlinx.coroutines.*

/**
 * VSCode风格的工作区设置组件
 * 用于初始绑定工作区
 */
@Composable
fun WorkspaceSetup(chatId: String, onBindWorkspace: (String, String?) -> Unit) {
    val context = LocalContext.current
    var showFileBrowser by remember { mutableStateOf(false) }
    var showProjectTypeDialog by remember { mutableStateOf(false) }

    var pendingRepoBookmarkUri by remember { mutableStateOf<Uri?>(null) }
    var repoBookmarkNameInput by remember { mutableStateOf("") }
    var showRepoBookmarkNameDialog by remember { mutableStateOf(false) }
    var repoBookmarkNameError by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    val apiPreferences = remember { ApiPreferences.getInstance(context) }
    val safBookmarks by apiPreferences.safBookmarksFlow.collectAsState(initial = emptyList())

    fun querySafBookmarkDisplayName(uri: Uri): String {
        return try {
            val treeDocId = DocumentsContract.getTreeDocumentId(uri)
            val docUri = DocumentsContract.buildDocumentUriUsingTree(uri, treeDocId)

            context.contentResolver.query(
                docUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                val idx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                if (cursor.moveToFirst() && idx >= 0 && !cursor.isNull(idx)) {
                    cursor.getString(idx)
                } else {
                    null
                }
            } ?: uri.toString()
        } catch (_: Exception) {
            uri.toString()
        }
    }

    fun queryRepoBookmarkName(uri: Uri): String {
        fun normalizeName(raw: String): String {
            return raw.trim()
                .lowercase(java.util.Locale.ROOT)
                .replace(Regex("\\s+"), "_")
                .ifBlank { "repo" }
        }

        val providerLabel =
            runCatching {
                val authority = uri.authority ?: return@runCatching null
                val provider = context.packageManager.resolveContentProvider(authority, 0)
                provider?.applicationInfo?.loadLabel(context.packageManager)?.toString()?.trim()
            }.getOrNull()

        val raw = providerLabel?.takeIf { it.isNotBlank() } ?: uri.authority ?: "repo"
        return normalizeName(raw)
    }

    val bindSafLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
            pendingRepoBookmarkUri = uri
            repoBookmarkNameInput = queryRepoBookmarkName(uri)
            showRepoBookmarkNameDialog = true
        }
    }

    if (showRepoBookmarkNameDialog) {
        AlertDialog(
            onDismissRequest = {
                showRepoBookmarkNameDialog = false
                pendingRepoBookmarkUri = null
                repoBookmarkNameError = null
            },
            title = { Text(context.getString(R.string.repo_bookmark_name)) },
            text = {
                TextField(
                    value = repoBookmarkNameInput,
                    onValueChange = {
                        repoBookmarkNameInput = it
                        repoBookmarkNameError = null
                    },
                    label = { Text(context.getString(R.string.repo_bookmark_name_label)) },
                    singleLine = true,
                    isError = repoBookmarkNameError != null,
                    supportingText = {
                        repoBookmarkNameError?.let { Text(it) }
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val uri = pendingRepoBookmarkUri
                        val name = repoBookmarkNameInput.trim()
                        if (uri == null) {
                            showRepoBookmarkNameDialog = false
                            pendingRepoBookmarkUri = null
                            repoBookmarkNameError = null
                            return@TextButton
                        }

                        if (name.isEmpty()) {
                            repoBookmarkNameError = context.getString(R.string.repo_bookmark_name_empty)
                            return@TextButton
                        }

                        val nameExists = safBookmarks.any {
                            it.uri != uri.toString() && it.name.equals(name, ignoreCase = true)
                        }
                        if (nameExists) {
                            repoBookmarkNameError = context.getString(R.string.repo_bookmark_name_exists)
                            return@TextButton
                        }

                        scope.launch {
                            apiPreferences.addSafBookmark(uri.toString(), name)
                            onBindWorkspace("/", "repo:$name")
                        }

                        showRepoBookmarkNameDialog = false
                        pendingRepoBookmarkUri = null
                        repoBookmarkNameError = null
                    }
                ) { Text(context.getString(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showRepoBookmarkNameDialog = false
                        pendingRepoBookmarkUri = null
                        repoBookmarkNameError = null
                    }
                ) { Text(context.getString(android.R.string.cancel)) }
            }
        )
    }

    if (showFileBrowser) {
        FileBrowser(
            initialPath = context.filesDir.absolutePath, // 默认应用内部目录
            onBindWorkspace = { path, env -> onBindWorkspace(path, env) },
            onCancel = { showFileBrowser = false }
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null, // 移除点击时的涟漪效果
                    enabled = true,
                    onClick = {}
                ) // 添加点击拦截
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (showProjectTypeDialog) {
                AlertDialog(
                    onDismissRequest = { showProjectTypeDialog = false },
                    title = {
                        Text(
                            text = context.getString(R.string.workspace_select_language_type_title),
                            style = MaterialTheme.typography.headlineSmall
                        )
                    },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = context.getString(R.string.workspace_select_language_type_prompt),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            ProjectTypeCard(
                                icon = Icons.Default.CreateNewFolder,
                                title = context.getString(R.string.workspace_project_type_blank_title),
                                description = context.getString(R.string.workspace_project_type_blank_description),
                                onClick = {
                                    val workspaceDir = createAndGetDefaultWorkspace(context, chatId, "blank")
                                    onBindWorkspace(workspaceDir.absolutePath, null)
                                    showProjectTypeDialog = false
                                }
                            )
                            
                            // Office 项目卡片
                            ProjectTypeCard(
                                icon = Icons.Default.Description,
                                title = context.getString(R.string.workspace_project_type_office_title),
                                description = context.getString(R.string.workspace_project_type_office_description),
                                onClick = {
                                    val workspaceDir = createAndGetDefaultWorkspace(context, chatId, "office")
                                    onBindWorkspace(workspaceDir.absolutePath, null)
                                    showProjectTypeDialog = false
                                }
                            )
                            
                            // Web 项目卡片
                            ProjectTypeCard(
                                icon = Icons.Default.Language,
                                title = context.getString(R.string.workspace_project_type_web_title),
                                description = context.getString(R.string.workspace_project_type_web_description),
                                onClick = {
                                    val workspaceDir = createAndGetDefaultWorkspace(context, chatId)
                                    onBindWorkspace(workspaceDir.absolutePath, null)
                                    showProjectTypeDialog = false
                                }
                            )

                            // Android 项目卡片
                            ProjectTypeCard(
                                icon = Icons.Default.PhoneAndroid,
                                title = context.getString(R.string.workspace_project_type_android_title),
                                description = context.getString(R.string.workspace_project_type_android_description),
                                onClick = {
                                    val workspaceDir = createAndGetDefaultWorkspace(context, chatId, "android")
                                    onBindWorkspace(workspaceDir.absolutePath, null)
                                    showProjectTypeDialog = false
                                }
                            )

                            // Flutter 项目卡片
                            ProjectTypeCard(
                                icon = Icons.Default.Widgets,
                                title = context.getString(R.string.workspace_project_type_flutter_title),
                                description = context.getString(R.string.workspace_project_type_flutter_description),
                                onClick = {
                                    val workspaceDir = createAndGetDefaultWorkspace(context, chatId, "flutter")
                                    onBindWorkspace(workspaceDir.absolutePath, null)
                                    showProjectTypeDialog = false
                                }
                            )
                             
                            // Node.js 项目卡片
                            ProjectTypeCard(
                                icon = Icons.Default.Terminal,
                                title = context.getString(R.string.workspace_project_type_node_title),
                                description = context.getString(R.string.workspace_project_type_node_description),
                                onClick = {
                                    val workspaceDir = createAndGetDefaultWorkspace(context, chatId, "node")
                                    onBindWorkspace(workspaceDir.absolutePath, null)
                                    showProjectTypeDialog = false
                                }
                            )
                            
                            // TypeScript 项目卡片
                            ProjectTypeCard(
                                icon = Icons.Default.Code,
                                title = context.getString(R.string.workspace_project_type_typescript_title),
                                description = context.getString(R.string.workspace_project_type_typescript_description),
                                onClick = {
                                    val workspaceDir = createAndGetDefaultWorkspace(context, chatId, "typescript")
                                    onBindWorkspace(workspaceDir.absolutePath, null)
                                    showProjectTypeDialog = false
                                }
                            )
                            
                            // Python 项目卡片
                            ProjectTypeCard(
                                icon = Icons.Default.Code,
                                title = context.getString(R.string.workspace_project_type_python_title),
                                description = context.getString(R.string.workspace_project_type_python_description),
                                onClick = {
                                    val workspaceDir = createAndGetDefaultWorkspace(context, chatId, "python")
                                    onBindWorkspace(workspaceDir.absolutePath, null)
                                    showProjectTypeDialog = false
                                }
                            )
                            
                            // Java 项目卡片
                            ProjectTypeCard(
                                icon = Icons.Default.Settings,
                                title = context.getString(R.string.workspace_project_type_java_title),
                                description = context.getString(R.string.workspace_project_type_java_description),
                                onClick = {
                                    val workspaceDir = createAndGetDefaultWorkspace(context, chatId, "java")
                                    onBindWorkspace(workspaceDir.absolutePath, null)
                                    showProjectTypeDialog = false
                                }
                            )
                            
                            // Go 项目卡片
                            ProjectTypeCard(
                                icon = Icons.Default.Build,
                                title = context.getString(R.string.workspace_project_type_go_title),
                                description = context.getString(R.string.workspace_project_type_go_description),
                                onClick = {
                                    val workspaceDir = createAndGetDefaultWorkspace(context, chatId, "go")
                                    onBindWorkspace(workspaceDir.absolutePath, null)
                                    showProjectTypeDialog = false
                                }
                            )
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { showProjectTypeDialog = false }) {
                            Text(context.getString(R.string.cancel))
                        }
                    }
                )
            }

            // VSCode风格的图标
            Icon(
                imageVector = Icons.Default.Widgets, // 使用更通用的图标
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = context.getString(R.string.setup_workspace),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = context.getString(R.string.workspace_description),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            
            Spacer(modifier = Modifier.height(40.dp))
            
            // VSCode风格的选项卡
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                WorkspaceOption(
                    icon = Icons.Default.CreateNewFolder,
                    title = context.getString(R.string.create_default_workspace),
                    description = context.getString(R.string.create_new_workspace_in_app),
                    onClick = {
                        showProjectTypeDialog = true
                    }
                )
                
                WorkspaceOption(
                    icon = Icons.Default.FolderOpen,
                    title = context.getString(R.string.select_existing_workspace),
                    description = context.getString(R.string.select_folder_from_device),
                    onClick = { showFileBrowser = true }
                )
            }
        }
    }
}

/**
 * 项目类型卡片组件（IDE风格）
 */
@Composable
fun ProjectTypeCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 图标
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            
            // 文字内容
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // 箭头指示
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 工作区选项卡组件
 */
@Composable
fun WorkspaceOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(160.dp) // 调整大小
            .height(160.dp)
            .clip(RoundedCornerShape(12.dp)) // 更圆的角
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp, // 移除阴影
            pressedElevation = 0.dp
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
} 
