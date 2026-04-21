package com.ai.assistance.operit.ui.features.packages.screens

import com.ai.assistance.operit.util.AppLogger
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ai.assistance.operit.ui.components.CustomScaffold
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.PackageTool
import com.ai.assistance.operit.core.tools.ToolPackage
import com.ai.assistance.operit.core.tools.EnvVar
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.data.mcp.MCPRepository
import com.ai.assistance.operit.data.preferences.EnvPreferences
import com.ai.assistance.operit.data.skill.SkillRepository
import com.ai.assistance.operit.ui.features.packages.screens.mcp.components.MCPEnvironmentVariablesDialog
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.ui.components.ErrorDialog
import com.ai.assistance.operit.ui.features.packages.components.EmptyState
import com.ai.assistance.operit.ui.features.packages.components.PackageTab
import com.ai.assistance.operit.ui.features.packages.dialogs.PackageDetailsDialog
import com.ai.assistance.operit.ui.features.packages.dialogs.ScriptExecutionDialog
import com.ai.assistance.operit.ui.features.packages.lists.PackagesList
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.ai.assistance.operit.R

private data class ExternalPackageImportResult(
    val message: String,
    val availablePackages: Map<String, ToolPackage>,
    val allAvailablePackages: Map<String, ToolPackage>,
    val importedPackages: List<String>,
    val packageLoadErrors: Map<String, String>,
    val packageLoadErrorInfos: List<PackageManager.PackageLoadErrorInfo>,
    val newPackageLoadErrors: Map<String, String>
)

private data class PackageManagerSnapshot(
    val availablePackages: Map<String, ToolPackage>,
    val allAvailablePackages: Map<String, ToolPackage>,
    val importedPackages: List<String>,
    val packageLoadErrors: Map<String, String>,
    val packageLoadErrorInfos: List<PackageManager.PackageLoadErrorInfo>
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PackageManagerScreen(
    onNavigateToMCPMarket: () -> Unit = {},
    onNavigateToSkillMarket: () -> Unit = {},
    onNavigateToArtifactMarket: () -> Unit = {},
    onOpenToolPkgPluginConfig: (String, String, String) -> Unit = { _, _, _ -> },
    onNavigateToMCPDetail: ((com.ai.assistance.operit.data.api.GitHubIssue) -> Unit)? = null
) {
    val context = LocalContext.current
    val packageManager = remember {
        PackageManager.getInstance(context, AIToolHandler.getInstance(context))
    }
    val scope = rememberCoroutineScope()
    val mcpRepository = remember { MCPRepository(context) }
    val skillRepository = remember { SkillRepository.getInstance(context.applicationContext) }

    val envPreferences = remember { EnvPreferences.getInstance(context) }

    // State for available and imported packages
    val availablePackages = remember { mutableStateOf<Map<String, ToolPackage>>(emptyMap()) }
    val allAvailablePackages = remember { mutableStateOf<Map<String, ToolPackage>>(emptyMap()) }
    val importedPackages = remember { mutableStateOf<List<String>>(emptyList()) }
    // UI展示用的导入状态列表，与后端状态分离
    val visibleImportedPackages = remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // State for selected package and showing details
    var selectedPackage by remember { mutableStateOf<String?>(null) }
    var showDetails by remember { mutableStateOf(false) }

    // State for script execution
    var showScriptExecution by remember { mutableStateOf(false) }
    var selectedTool by remember { mutableStateOf<PackageTool?>(null) }
    var selectedToolPackageName by remember { mutableStateOf<String?>(null) }
    var scriptExecutionResult by remember { mutableStateOf<ToolResult?>(null) }

    // State for snackbar
    val snackbarHostState = remember { SnackbarHostState() }

    // Tab selection state
    var selectedTab by rememberSaveable { mutableStateOf(PackageTab.PACKAGES) }

    // Environment variables dialog state
    var showEnvDialog by remember { mutableStateOf(false) }
    var envVariables by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    val packageLoadErrors = remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val packageLoadErrorInfos =
        remember { mutableStateOf<List<PackageManager.PackageLoadErrorInfo>>(emptyList()) }
    var showPackageLoadErrorsDialog by remember { mutableStateOf(false) }
    var importErrorMessage by remember { mutableStateOf<String?>(null) }

    val requiredEnvByPackage by remember {
        derivedStateOf {
            val packagesMap = allAvailablePackages.value
            val imported = importedPackages.value.toSet()

            imported
                .mapNotNull { packageName ->
                    packagesMap[packageName]
                }
                .sortedBy { it.name }
                .associate { toolPackage ->
                    toolPackage.name to toolPackage.env
                }
                .filterValues { envVars -> envVars.isNotEmpty() }
        }
    }

    val requiredEnvKeys by remember {
        derivedStateOf {
            requiredEnvByPackage.values
                .flatten()
                .map { it.name }
                .toSet()
                .toList()
                .sorted()
        }
    }

    // File picker launcher for importing external packages
    val packageFilePicker =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                scope.launch {
                    try {
                        val fileName: String? =
                            withContext(Dispatchers.IO) {
                                var name: String? = null
                                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                                    val nameIndex = cursor.getColumnIndex("_display_name")
                                    if (cursor.moveToFirst() && nameIndex >= 0) {
                                        name = cursor.getString(nameIndex)
                                    }
                                }
                                name
                            }

                        if (fileName == null) {
                            snackbarHostState.showSnackbar(context.getString(R.string.no_filename))
                            return@launch
                        }

                        // 根据当前选中的标签页处理不同类型的文件
                        when (selectedTab) {
                            PackageTab.PACKAGES -> {
                                val fileNameNonNull = fileName ?: return@launch
                                val lowerFileName = fileNameNonNull.lowercase()
                                val supported =
                                    lowerFileName.endsWith(".js") ||
                                        lowerFileName.endsWith(".toolpkg")
                                if (!supported) {
                                    snackbarHostState.showSnackbar(message = context.getString(R.string.package_js_only))
                                    return@launch
                                }

                                isLoading = true
                                val loadResult =
                                    withContext(Dispatchers.IO) {
                                        val inputStream = context.contentResolver.openInputStream(uri)
                                        val tempFile = File(context.cacheDir, fileNameNonNull)
                                        try {
                                            inputStream?.use { input ->
                                                tempFile.outputStream().use { output -> input.copyTo(output) }
                                            }

                                            val errorsBeforeImport = packageManager.getPackageLoadErrors()
                                            val importMessage = packageManager.addPackageFileFromExternalStorage(tempFile.absolutePath)

                                            val available = packageManager.getTopLevelAvailablePackages(forceRefresh = true)
                                            val allAvailable = packageManager.getAvailablePackages()
                                            val imported = packageManager.getEnabledPackageNames()
                                            val errors = packageManager.getPackageLoadErrors()
                                            val errorInfos = packageManager.getPackageLoadErrorInfos()
                                            val newErrors =
                                                errors.filter { (key, value) -> errorsBeforeImport[key] != value }

                                            ExternalPackageImportResult(
                                                message = importMessage,
                                                availablePackages = available,
                                                allAvailablePackages = allAvailable,
                                                importedPackages = imported,
                                                packageLoadErrors = errors,
                                                packageLoadErrorInfos = errorInfos,
                                                newPackageLoadErrors = newErrors
                                            )
                                        } finally {
                                            if (tempFile.exists()) {
                                                tempFile.delete()
                                            }
                                        }
                                    }

                                availablePackages.value = loadResult.availablePackages
                                allAvailablePackages.value = loadResult.allAvailablePackages
                                importedPackages.value = loadResult.importedPackages
                                packageLoadErrors.value = loadResult.packageLoadErrors
                                packageLoadErrorInfos.value = loadResult.packageLoadErrorInfos
                                visibleImportedPackages.value = importedPackages.value.toList()
                                isLoading = false

                                val importSucceeded =
                                    loadResult.message.startsWith(
                                        prefix = "Successfully imported",
                                        ignoreCase = true
                                    )

                                if (importSucceeded) {
                                    snackbarHostState.showSnackbar(message = context.getString(R.string.external_package_imported))
                                } else {
                                    importErrorMessage =
                                        buildString {
                                            append(loadResult.message)
                                            if (loadResult.newPackageLoadErrors.isNotEmpty()) {
                                                append("\n\n")
                                                append(
                                                    loadResult.newPackageLoadErrors
                                                        .toSortedMap()
                                                        .entries
                                                        .joinToString(separator = "\n\n") { (packageName, errorText) ->
                                                            "$packageName:\n$errorText"
                                                        }
                                                )
                                            }
                                        }
                                }
                            }
                            else -> {
                                snackbarHostState.showSnackbar(context.getString(R.string.current_tab_not_support_import))
                            }
                        }
                    } catch (e: Exception) {
                        isLoading = false
                        AppLogger.e("PackageManagerScreen", "Failed to import file", e)
                        importErrorMessage =
                            context.getString(
                                R.string.import_failed,
                                e.message ?: context.getString(R.string.unknown_error)
                            ) + "\n\n" + e.stackTraceToString()
                    }
                }
            }

        }

    // Load packages
    LaunchedEffect(Unit) {
        isLoading = true
        try {
            val loadResult =
                withContext(Dispatchers.IO) {
                    val available = packageManager.getTopLevelAvailablePackages(forceRefresh = true)
                    val allAvailable = packageManager.getAvailablePackages()
                    val imported = packageManager.getEnabledPackageNames()
                    val errors = packageManager.getPackageLoadErrors()
                    val errorInfos = packageManager.getPackageLoadErrorInfos()
                    PackageManagerSnapshot(
                        availablePackages = available,
                        allAvailablePackages = allAvailable,
                        importedPackages = imported,
                        packageLoadErrors = errors,
                        packageLoadErrorInfos = errorInfos
                    )
                }

            availablePackages.value = loadResult.availablePackages
            allAvailablePackages.value = loadResult.allAvailablePackages
            importedPackages.value = loadResult.importedPackages
            packageLoadErrors.value = loadResult.packageLoadErrors
            packageLoadErrorInfos.value = loadResult.packageLoadErrorInfos
            // 初始化UI显示状态
            visibleImportedPackages.value = importedPackages.value.toList()
        } catch (e: Exception) {
            AppLogger.e("PackageManagerScreen", "Failed to load packages", e)
        } finally {
            isLoading = false
        }
    }

    CustomScaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    snackbarData = data
                )
            }
        },
        floatingActionButton = {
            if (selectedTab == PackageTab.PACKAGES) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    if (packageLoadErrors.value.isNotEmpty()) {
                        SmallFloatingActionButton(
                            onClick = { showPackageLoadErrorsDialog = true },
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = context.getString(R.string.error_occurred_simple)
                            )
                        }
                    }

                    // Environment variables management button
                    SmallFloatingActionButton(
                        onClick = {
                            envVariables =
                                requiredEnvKeys.associateWith { key ->
                                    envPreferences.getEnv(key) ?: ""
                                }
                            showEnvDialog = true
                        },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.pkg_manage_env_vars)
                        )
                    }

                    FloatingActionButton(
                        onClick = onNavigateToArtifactMarket,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier =
                            Modifier.shadow(
                                elevation = 6.dp,
                                shape = FloatingActionButtonDefaults.shape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Store,
                            contentDescription = "Artifact Market"
                        )
                    }

                    // Existing import package button
                    FloatingActionButton(
                        onClick = { packageFilePicker.launch("*/*") },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier =
                            Modifier.shadow(
                                elevation = 6.dp,
                                shape = FloatingActionButtonDefaults.shape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = when (selectedTab) {
                                PackageTab.PACKAGES -> context.getString(R.string.import_external_package)
                                else -> context.getString(R.string.import_action)
                            }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
        ) {
            // 优化标签栏布局 - 直接使用TabRow，不再使用Card包裹，移除边距完全贴满
            TabRow(
                selectedTabIndex = selectedTab.ordinal,
                modifier = Modifier.fillMaxWidth(),
                divider = {
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                },
                indicator = { tabPositions ->
                    if (selectedTab.ordinal < tabPositions.size) {
                        TabRowDefaults.PrimaryIndicator(
                            modifier =
                                Modifier.tabIndicatorOffset(
                                    tabPositions[selectedTab.ordinal]
                                ),
                            height = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            ) {
                // 包管理标签
                Tab(
                    selected = selectedTab == PackageTab.PACKAGES,
                    onClick = { selectedTab = PackageTab.PACKAGES },
                    modifier = Modifier.height(48.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Extension,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (selectedTab == PackageTab.PACKAGES)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            context.getString(R.string.packages),
                            style = MaterialTheme.typography.bodySmall,
                            softWrap = false,
                            color = if (selectedTab == PackageTab.PACKAGES)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Skills标签
                Tab(
                    selected = selectedTab == PackageTab.SKILLS,
                    onClick = { selectedTab = PackageTab.SKILLS },
                    modifier = Modifier.height(48.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (selectedTab == PackageTab.SKILLS)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            context.getString(R.string.skills),
                            style = MaterialTheme.typography.bodySmall,
                            softWrap = false,
                            color = if (selectedTab == PackageTab.SKILLS)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // MCP标签
                Tab(
                    selected = selectedTab == PackageTab.MCP,
                    onClick = { selectedTab = PackageTab.MCP },
                    modifier = Modifier.height(48.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cloud,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (selectedTab == PackageTab.MCP)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            context.getString(R.string.mcp),
                            style = MaterialTheme.typography.bodySmall,
                            softWrap = false,
                            color = if (selectedTab == PackageTab.MCP)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 内容区域添加水平padding
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                when (selectedTab) {
                    PackageTab.PACKAGES -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp)
                        ) {
                            if (availablePackages.value.isEmpty() && isLoading) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            } else if (availablePackages.value.isEmpty()) {
                                EmptyState(message = context.getString(R.string.no_packages_available))
                            } else {
                                Surface(
                                    modifier = Modifier.fillMaxSize(),
                                    color = MaterialTheme.colorScheme.background,
                                    shape = MaterialTheme.shapes.medium
                                ) {
                                    val packages = availablePackages.value
                                    val groupedPackagesRaw = packages.entries.groupBy { it.value.category }
                                    val categoryOrder = listOf("ToolPkg", "Automatic", "Experimental", "Draw", "Other")
                                    val sortedCategories =
                                        groupedPackagesRaw.keys.sortedWith { a, b ->
                                            val indexA = categoryOrder.indexOf(a)
                                            val indexB = categoryOrder.indexOf(b)
                                            when {
                                                indexA == -1 && indexB == -1 -> a.compareTo(b)
                                                indexA == -1 -> 1
                                                indexB == -1 -> -1
                                                else -> indexA - indexB
                                            }
                                        }

                                    val groupedPackages = linkedMapOf<String, Map<String, ToolPackage>>()
                                    sortedCategories.forEach { category ->
                                        val entries = groupedPackagesRaw[category].orEmpty()
                                        val sortedEntries = entries.sortedBy { it.key }
                                        groupedPackages[category] =
                                            sortedEntries.associate { entry -> entry.key to entry.value }
                                    }

                                    val automaticColor = MaterialTheme.colorScheme.primary
                                    val experimentalColor = MaterialTheme.colorScheme.tertiary
                                    val drawColor = MaterialTheme.colorScheme.secondary
                                    val toolPkgColor = MaterialTheme.colorScheme.primary
                                    val otherColor = MaterialTheme.colorScheme.onSurfaceVariant

                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(1.dp),
                                        contentPadding = PaddingValues(bottom = 120.dp)
                                    ) {
                                        groupedPackages.forEach { (category, packagesInCategory) ->
                                            val categoryColor = when (category) {
                                                "Automatic" -> automaticColor
                                                "Experimental" -> experimentalColor
                                                "Draw" -> drawColor
                                                "ToolPkg" -> toolPkgColor
                                                else -> otherColor
                                            }

                                            items(
                                                packagesInCategory.keys.toList(),
                                                key = { it }) { packageName ->
                                                val isFirstInCategory =
                                                    packageName == packagesInCategory.keys.first()
                                                val categoryTagText =
                                                    if (category == "ToolPkg") {
                                                        context.getString(R.string.package_category_plugin)
                                                    } else {
                                                        category
                                                    }

                                                PackageListItemWithTag(
                                                    packageName = packageName,
                                                    toolPackage = packagesInCategory[packageName],
                                                    isImported = visibleImportedPackages.value.contains(
                                                        packageName
                                                    ),
                                                    categoryTag = if (isFirstInCategory) categoryTagText else null,
                                                    category = category,
                                                    categoryColor = categoryColor,
                                                    isProminent = category == "ToolPkg",
                                                    onPackageClick = {
                                                        selectedPackage = packageName
                                                        showDetails = true
                                                    },
                                                    onToggleImport = { isChecked ->
                                                        val currentImported =
                                                            visibleImportedPackages.value.toMutableList()
                                                        if (isChecked) {
                                                            if (!currentImported.contains(
                                                                    packageName
                                                                )
                                                            ) {
                                                                currentImported.add(packageName)
                                                            }
                                                        } else {
                                                            currentImported.remove(packageName)
                                                        }
                                                        visibleImportedPackages.value =
                                                            currentImported

                                                        scope.launch {
                                                            try {
                                                                val updatedImported =
                                                                    withContext(Dispatchers.IO) {
                                                                        if (isChecked) {
                                                                            packageManager.enablePackage(packageName)
                                                                        } else {
                                                                            packageManager.disablePackage(packageName)
                                                                        }
                                                                        packageManager.getEnabledPackageNames()
                                                                    }

                                                                importedPackages.value = updatedImported
                                                            } catch (e: Exception) {
                                                                AppLogger.e(
                                                                    "PackageManagerScreen",
                                                                    if (isChecked) "Failed to import package" else "Failed to remove package",
                                                                    e
                                                                )
                                                                visibleImportedPackages.value =
                                                                    importedPackages.value
                                                                snackbarHostState.showSnackbar(
                                                                    message = if (isChecked) context.getString(
                                                                        R.string.package_import_failed
                                                                    ) else context.getString(R.string.package_remove_failed)
                                                                )
                                                            }
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (isLoading && availablePackages.value.isNotEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                        }

                    }

                    PackageTab.SKILLS -> {
                        SkillConfigScreen(
                            skillRepository = skillRepository,
                            snackbarHostState = snackbarHostState,
                            onNavigateToSkillMarket = onNavigateToSkillMarket
                        )
                    }

                    PackageTab.MCP -> {
                        MCPConfigScreen(
                            onNavigateToMCPMarket = onNavigateToMCPMarket
                        )
                    }
                }
            }

            // Package Details Dialog
            if (showDetails && selectedPackage != null) {
                PackageDetailsDialog(
                    packageName = selectedPackage!!,
                    packageDescription = availablePackages.value[selectedPackage]?.description?.resolve(context)
                        ?: "",
                    toolPackage = availablePackages.value[selectedPackage],
                    packageManager = packageManager,
                    onRunScript = { toolPackageName, tool ->
                        selectedToolPackageName = toolPackageName
                        selectedTool = tool
                        showScriptExecution = true
                    },
                    onOpenToolPkgPluginConfig = { containerPackageName, uiModuleId, title ->
                        showDetails = false
                        onOpenToolPkgPluginConfig(containerPackageName, uiModuleId, title)
                    },
                    onDismiss = {
                        showDetails = false
                        scope.launch {
                            val imported = withContext(Dispatchers.IO) { packageManager.getEnabledPackageNames() }
                            importedPackages.value = imported
                            visibleImportedPackages.value = imported.toList()
                        }
                    },
                    onPackageDeleted = {
                        showDetails = false
                        scope.launch {
                            AppLogger.d(
                                "PackageManagerScreen",
                                "onPackageDeleted callback triggered. Refreshing package lists."
                            )
                            // Refresh the package lists after deletion
                            isLoading = true
                            val loadResult =
                                withContext(Dispatchers.IO) {
                                    val available = packageManager.getTopLevelAvailablePackages(forceRefresh = true)
                                    val allAvailable = packageManager.getAvailablePackages()
                                    val imported = packageManager.getEnabledPackageNames()
                                    Triple(available, allAvailable, imported)
                                }

                            availablePackages.value = loadResult.first
                            allAvailablePackages.value = loadResult.second
                            importedPackages.value = loadResult.third
                            visibleImportedPackages.value = importedPackages.value.toList()
                            AppLogger.d(
                                "PackageManagerScreen",
                                "Lists refreshed. Available: ${availablePackages.value.keys}, Imported: ${importedPackages.value}"
                            )
                            isLoading = false
                            snackbarHostState.showSnackbar("Package deleted successfully.")
                        }
                    }
                )
            }

            // Script Execution Dialog
            if (showScriptExecution && selectedTool != null && selectedPackage != null) {
                ScriptExecutionDialog(
                    packageName = selectedToolPackageName ?: selectedPackage!!,
                    tool = selectedTool!!,
                    packageManager = packageManager,
                    initialResult = scriptExecutionResult,
                    onExecuted = { result -> scriptExecutionResult = result },
                    onDismiss = {
                        showScriptExecution = false
                        scriptExecutionResult = null
                        selectedToolPackageName = null
                    }
                )
            }

            // Environment Variables Dialog for packages
            if (showEnvDialog) {
                PackageEnvironmentVariablesDialog(
                    requiredEnvByPackage = requiredEnvByPackage,
                    currentValues = envVariables,
                    onDismiss = { showEnvDialog = false },
                    onConfirm = { updated ->
                        val merged = envPreferences.getAllEnv().toMutableMap().apply {
                            updated.forEach { (key, value) ->
                                if (value.isBlank()) {
                                    remove(key)
                                } else {
                                    this[key] = value
                                }
                            }
                        }
                        envPreferences.setAllEnv(merged)
                        envVariables = updated
                        showEnvDialog = false
                    }
                )
            }

            if (showPackageLoadErrorsDialog) {
                PackageLoadErrorsDialog(
                    errorInfos = packageLoadErrorInfos.value,
                    onDeleteSource = { sourcePath ->
                        scope.launch {
                            val deleted =
                                withContext(Dispatchers.IO) {
                                    packageManager.deleteExternalPackageSource(sourcePath)
                                }
                            if (!deleted) {
                                snackbarHostState.showSnackbar(
                                    message = context.getString(R.string.package_conflict_delete_failed)
                                )
                                return@launch
                            }

                            val refreshed =
                                withContext(Dispatchers.IO) {
                                    PackageManagerSnapshot(
                                        availablePackages = packageManager.getTopLevelAvailablePackages(forceRefresh = true),
                                        allAvailablePackages = packageManager.getAvailablePackages(),
                                        importedPackages = packageManager.getEnabledPackageNames(),
                                        packageLoadErrors = packageManager.getPackageLoadErrors(),
                                        packageLoadErrorInfos = packageManager.getPackageLoadErrorInfos()
                                    )
                                }
                            availablePackages.value = refreshed.availablePackages
                            allAvailablePackages.value = refreshed.allAvailablePackages
                            importedPackages.value = refreshed.importedPackages
                            packageLoadErrors.value = refreshed.packageLoadErrors
                            packageLoadErrorInfos.value = refreshed.packageLoadErrorInfos
                            visibleImportedPackages.value = refreshed.importedPackages.toList()

                            if (packageLoadErrorInfos.value.isEmpty()) {
                                showPackageLoadErrorsDialog = false
                            }
                            snackbarHostState.showSnackbar(
                                message = context.getString(R.string.package_conflict_delete_success)
                            )
                        }
                    },
                    onDismiss = { showPackageLoadErrorsDialog = false }
                )
            }

            importErrorMessage?.let { errorMessage ->
                ErrorDialog(
                    errorMessage = errorMessage,
                    onDismiss = { importErrorMessage = null }
                )
            }
        }
    }
}

@Composable
private fun PackageLoadErrorsDialog(
    errorInfos: List<PackageManager.PackageLoadErrorInfo>,
    onDeleteSource: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.error_occurred_simple)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(scrollState)
            ) {
                errorInfos.forEach { errorInfo ->
                    Text(
                        text = errorInfo.packageName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    errorInfo.sourcePath?.let { sourcePath ->
                        Text(
                            text = sourcePath,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    Text(
                        text = errorInfo.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (errorInfo.isExternalSource && errorInfo.sourcePath != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { onDeleteSource(errorInfo.sourcePath) }
                        ) {
                            Text(text = stringResource(R.string.package_conflict_delete_source))
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.ok))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun PackageEnvironmentVariablesDialog(
    requiredEnvByPackage: Map<String, List<EnvVar>>,
    currentValues: Map<String, String>,
    onDismiss: () -> Unit,
    onConfirm: (Map<String, String>) -> Unit
) {
    val context = LocalContext.current
    
    val requiredEnvKeys = remember(requiredEnvByPackage) {
        requiredEnvByPackage.values
            .flatten()
            .map { it.name }
            .toSet()
            .toList()
            .sorted()
    }

    val editableValuesState =
        remember(requiredEnvKeys, currentValues) {
            mutableStateOf(
                requiredEnvKeys.associateWith { key: String -> currentValues[key] ?: "" }
            )
        }
    val editableValues by editableValuesState

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.pkg_config_env_vars)) },
        text = {
            if (requiredEnvKeys.isEmpty()) {
                Text(
                    text = stringResource(R.string.pkg_no_env_vars),
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    requiredEnvByPackage.forEach { (packageName, envVars) ->
                        stickyHeader(key = "header:$packageName") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    modifier = Modifier.size(24.dp),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = packageName.first().uppercaseChar().toString(),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                                Text(
                                    text = packageName,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
 
                        items(
                            items = envVars,
                            key = { envVar -> "${packageName}:${envVar.name}" }
                        ) { envVar ->
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = envVar.name,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            // 显示是否必需的标记
                                            if (envVar.required) {
                                                Surface(
                                                    modifier = Modifier.size(16.dp),
                                                    shape = CircleShape,
                                                    color = MaterialTheme.colorScheme.error
                                                ) {
                                                    Box(
                                                        contentAlignment = Alignment.Center,
                                                        modifier = Modifier.size(16.dp)
                                                    ) {
                                                        Text(
                                                            text = "!",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onError
                                                        )
                                                    }
                                                }
                                            } else {
                                                Surface(
                                                    modifier = Modifier.size(16.dp),
                                                    shape = CircleShape,
                                                    color = MaterialTheme.colorScheme.secondaryContainer
                                                ) {
                                                    Box(
                                                        contentAlignment = Alignment.Center,
                                                        modifier = Modifier.size(16.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Check,
                                                            contentDescription = "Optional",
                                                            modifier = Modifier.size(10.dp),
                                                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        // 显示描述
                                        val description = envVar.description.resolve(context)
                                        if (description.isNotBlank()) {
                                            Text(
                                                text = description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                                // 显示默认值（如果有）
                                if (envVar.defaultValue != null) {
                                    Text(
                                        text = stringResource(R.string.pkg_default, envVar.defaultValue),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = editableValues[envVar.name] ?: "",
                                onValueChange = { newValue ->
                                    val currentMap = editableValuesState.value
                                    val newMap = currentMap.toMutableMap()
                                    newMap[envVar.name] = newValue
                                    editableValuesState.value = newMap
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = {
                                    Text(
                                        text = if (envVar.required) stringResource(R.string.pkg_input_required) else stringResource(R.string.pkg_input_optional),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                },
                                shape = RoundedCornerShape(6.dp),
                                textStyle = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(editableValues) }) {
                Text(text = stringResource(R.string.pkg_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.pkg_cancel))
            }
        }
    )
}

@Composable
private fun PackageListItemWithTag(
    packageName: String,
    toolPackage: ToolPackage?,
    isImported: Boolean,
    categoryTag: String?,
    category: String, // 新增分类参数
    categoryColor: Color,
    isProminent: Boolean = false,
    onPackageClick: () -> Unit,
    onToggleImport: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val packageDisplayName =
        toolPackage
            ?.displayName
            ?.resolve(context)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    val displayName = packageDisplayName ?: toolPackage?.name ?: packageName

    Column(modifier = Modifier.fillMaxWidth()) {
        // 分类标签（仅在有标签时显示）
        if (categoryTag != null) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = if (isProminent) 4.dp else 16.dp,
                            vertical = if (isProminent) 8.dp else 6.dp
                        ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isProminent) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.36f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Apps,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = categoryColor
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = categoryTag,
                                style = MaterialTheme.typography.labelSmall,
                                color = categoryColor,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                } else {
                    Surface(
                        modifier =
                            Modifier
                                .width(3.dp)
                                .height(12.dp),
                        color = categoryColor,
                        shape = RoundedCornerShape(1.5.dp)
                    ) {}
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = categoryTag,
                        style = MaterialTheme.typography.labelSmall,
                        color = categoryColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Surface(
            onClick = onPackageClick,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(if (isProminent) Modifier.padding(horizontal = 4.dp) else Modifier),
            color =
                if (isProminent) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.24f)
                } else {
                    MaterialTheme.colorScheme.surface
                },
            tonalElevation = if (isProminent) 2.dp else 0.dp,
            shadowElevation = 0.dp,
            shape = if (isProminent) RoundedCornerShape(14.dp) else RoundedCornerShape(0.dp)
        ) {
            // 主要内容行
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 16.dp,
                            vertical =
                                if (isProminent) {
                                    if (categoryTag != null) 10.dp else 12.dp
                                } else {
                                    if (categoryTag != null) 4.dp else 8.dp
                                }
                        ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when (category) {
                        "Automatic" -> Icons.Default.AutoMode
                        "Experimental" -> Icons.Default.Science
                        "Draw" -> Icons.Default.Palette
                        "ToolPkg" -> Icons.Default.Apps
                        "Other" -> Icons.Default.Widgets
                        else -> Icons.Default.Extension
                    },
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = categoryColor
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isProminent) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val description = toolPackage?.description?.resolve(context).orEmpty()
                    if (description.isNotBlank()) {
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = isImported,
                    onCheckedChange = onToggleImport,
                    modifier = Modifier.scale(0.8f)
                )
            }
        }
    }
}
