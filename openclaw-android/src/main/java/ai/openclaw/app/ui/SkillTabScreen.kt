package ai.openclaw.app.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ai.openclaw.app.skill.AgencyAgentsFetcher
import ai.openclaw.app.skill.NoOpSkillActions
import ai.openclaw.app.skill.OnlineSkill
import ai.openclaw.app.skill.SkillActions
import ai.openclaw.app.ui.chat.ChatMarkdown

/** Derive a slug from the OnlineSkill filename. */
private fun OnlineSkill.toSlug(): String =
    filename.removePrefix("__bundled__").removeSuffix(".md")

private const val FILTER_ALL = "all"
private const val FILTER_INSTALLED = "installed"

private val categoryLabels = linkedMapOf(
    FILTER_ALL to "全部",
    FILTER_INSTALLED to "已安装",
    "bundled" to "预置",
    "engineering" to "Engineering",
    "design" to "Design",
    "marketing" to "Marketing",
    "sales" to "Sales",
    "social" to "Social",
)

@Composable
fun SkillTabScreen(
    modifier: Modifier = Modifier,
    skillActions: SkillActions = NoOpSkillActions,
    onDetailPageChanged: (Boolean) -> Unit = {},
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var skills by remember { mutableStateOf(emptyList<OnlineSkill>()) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var installedSet by remember { mutableStateOf(emptySet<String>()) }

    // Track detail page state for parent
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val isOnDetailPage = navBackStackEntry?.destination?.route?.startsWith("detail/") == true
    LaunchedEffect(isOnDetailPage) {
        onDetailPageChanged(isOnDetailPage)
    }

    /** Refresh installed set from filesystem via SkillActions. */
    fun refreshInstalled() {
        val bundled = AgencyAgentsFetcher.getBundledFilenames(context)
        val managedSlugs = skillActions.getInstalledSlugs()
        val managed = skills
            .filter { !it.filename.startsWith("__bundled__") && it.toSlug() in managedSlugs }
            .map { it.filename }
            .toSet()
        installedSet = bundled + managed
    }

    LaunchedEffect(Unit) {
        isLoading = true
        loadError = null
        try {
            skills = withContext(Dispatchers.IO) { AgencyAgentsFetcher.loadAllSkills(context) }
        } catch (e: Exception) {
            loadError = e.message ?: "加载失败"
        }
        withContext(Dispatchers.IO) { refreshInstalled() }
        isLoading = false
    }

    NavHost(
        navController = navController,
        startDestination = "list",
        modifier = modifier,
    ) {
        composable("list") {
            SkillListPage(
                skills = skills,
                isLoading = isLoading,
                loadError = loadError,
                installedSet = installedSet,
                onRefresh = {
                    scope.launch {
                        isLoading = true
                        loadError = null
                        try {
                            skills = withContext(Dispatchers.IO) { AgencyAgentsFetcher.forceRefresh(context) }
                        } catch (e: Exception) {
                            loadError = e.message ?: "刷新失败"
                        }
                        withContext(Dispatchers.IO) { refreshInstalled() }
                        isLoading = false
                    }
                },
                onSkillClick = { skill ->
                    navController.navigate("detail/${skill.filename}")
                },
            )
        }
        composable(
            route = "detail/{filename}",
            arguments = listOf(navArgument("filename") { type = NavType.StringType }),
        ) { backStackEntry ->
            val filename = backStackEntry.arguments?.getString("filename") ?: return@composable
            val skill = skills.find { it.filename == filename } ?: return@composable
            val isInstalled by remember(installedSet, filename) {
                derivedStateOf { filename in installedSet }
            }
            val isBundled = filename.startsWith("__bundled__")
            SkillDetailPage(
                skill = skill,
                isInstalled = isInstalled,
                isBundled = isBundled,
                skillActions = skillActions,
                onBack = { navController.popBackStack() },
                onToggleInstall = { content ->
                    if (!isBundled) {
                        scope.launch(Dispatchers.IO) {
                            if (filename in installedSet) {
                                skillActions.uninstall(skill.toSlug())
                            } else {
                                skillActions.install(skill.name, skill.toSlug(), content)
                            }
                            refreshInstalled()
                        }
                    }
                },
            )
        }
    }
}

// ────────────────────────────────────────────────────────
// LIST PAGE
// ────────────────────────────────────────────────────────

@Composable
private fun SkillListPage(
    skills: List<OnlineSkill>,
    isLoading: Boolean,
    loadError: String?,
    installedSet: Set<String>,
    onRefresh: () -> Unit,
    onSkillClick: (OnlineSkill) -> Unit,
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var activeFilter by rememberSaveable { mutableStateOf(FILTER_ALL) }

    val filteredSkills = remember(skills, searchQuery, activeFilter, installedSet) {
        var result = skills
        if (activeFilter == FILTER_INSTALLED) {
            result = result.filter { it.filename in installedSet }
        } else if (activeFilter == FILTER_ALL) {
            result = result.filter { it.category != "bundled" }
        } else {
            result = result.filter { it.category == activeFilter }
        }
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.lowercase()
            result = result.filter {
                it.name.lowercase().contains(q) || it.specialty.lowercase().contains(q)
            }
        }
        result
    }

    val showFeatured = activeFilter != FILTER_INSTALLED && searchQuery.isBlank()
    val grouped = remember(filteredSkills) { filteredSkills.groupBy { it.category } }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("搜索 Skills...", color = mobileTextTertiary) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = mobileTextTertiary) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "清除", tint = mobileTextTertiary)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = mobileCardSurface,
                unfocusedContainerColor = mobileCardSurface,
                focusedBorderColor = mobileAccent,
                unfocusedBorderColor = mobileBorder,
                focusedTextColor = mobileText,
                unfocusedTextColor = mobileText,
                cursorColor = mobileAccent,
            ),
        )

        // Filter chips
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(categoryLabels.entries.toList(), key = { it.key }) { (key, label) ->
                val active = key == activeFilter
                val displayLabel = if (key == FILTER_INSTALLED) {
                    val count = skills.count { it.filename in installedSet }
                    "$label ($count)"
                } else {
                    label
                }
                Surface(
                    onClick = { activeFilter = key },
                    shape = RoundedCornerShape(20.dp),
                    color = if (active) mobileAccent else mobileCardSurface,
                    border = if (!active) CardDefaults.outlinedCardBorder() else null,
                ) {
                    Text(
                        text = displayLabel,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = if (active) mobileText else mobileTextSecondary,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 13.sp,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        when {
            isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = mobileAccent)
                }
            }
            loadError != null && skills.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("加载失败", style = mobileTitle2, color = mobileText)
                        Text(loadError, style = mobileBody, color = mobileTextSecondary)
                        Button(
                            onClick = onRefresh,
                            colors = ButtonDefaults.buttonColors(containerColor = mobileAccent),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("重试", color = mobileText)
                        }
                    }
                }
            }
            filteredSkills.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("🔍", fontSize = 48.sp)
                        if (activeFilter == FILTER_INSTALLED) {
                            Text("还没有已安装的 Skills", style = mobileHeadline, color = mobileText)
                            Text("浏览全部 Skills 并安装你感兴趣的", style = mobileBody, color = mobileTextSecondary)
                            TextButton(onClick = { activeFilter = FILTER_ALL }) {
                                Text("浏览全部", color = mobileAccent)
                            }
                        } else {
                            Text("没有找到匹配的 Skill", style = mobileHeadline, color = mobileText)
                        }
                    }
                }
            }
            else -> {
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    // Featured section
                    if (showFeatured && activeFilter != "bundled" && AgencyAgentsFetcher.featuredSkills.isNotEmpty()) {
                        item(key = "featured_header") {
                            Text(
                                "🔥 精选",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                style = mobileHeadline,
                                color = mobileText,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        item(key = "featured_row") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                items(AgencyAgentsFetcher.featuredSkills, key = { it.filename }) { skill ->
                                    FeaturedSkillCard(
                                        skill = skill,
                                        installed = skill.filename in installedSet,
                                        onClick = { onSkillClick(skill) },
                                    )
                                }
                            }
                        }
                        item(key = "featured_spacer") {
                            Spacer(Modifier.height(8.dp))
                        }
                    }

                    // Grouped by category
                    for ((cat, catSkills) in grouped) {
                        val label = categoryLabels[cat] ?: cat
                        item(key = "header_$cat") {
                            Text(
                                "${categoryEmoji(cat)} $label (${catSkills.size})",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                style = mobileHeadline,
                                color = mobileText,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        items(catSkills, key = { it.filename }) { skill ->
                            SkillListItem(
                                skill = skill,
                                installed = skill.filename in installedSet,
                                onClick = { onSkillClick(skill) },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun categoryEmoji(cat: String): String = when (cat) {
    "bundled" -> "📦"
    "engineering" -> "🏗️"
    "design" -> "🎨"
    "marketing" -> "📢"
    "sales" -> "💼"
    "social" -> "📣"
    else -> "📂"
}

@Composable
private fun FeaturedSkillCard(skill: OnlineSkill, installed: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.width(160.dp),
        shape = RoundedCornerShape(16.dp),
        color = mobileCardSurface,
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(skill.emoji, fontSize = 28.sp)
                if (installed) {
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Default.Check, contentDescription = "已安装", modifier = Modifier.size(16.dp), tint = mobileAccent)
                }
            }
            Text(
                skill.name,
                style = mobileCallout,
                fontWeight = FontWeight.SemiBold,
                color = mobileText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (skill.specialty.isNotEmpty()) {
                Text(
                    skill.specialty,
                    style = mobileCaption1,
                    color = mobileTextTertiary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SkillListItem(skill: OnlineSkill, installed: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = mobileCardSurface,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(skill.emoji, fontSize = 24.sp)
            Column(modifier = Modifier.weight(1f)) {
                Text(skill.name, style = mobileCallout, fontWeight = FontWeight.SemiBold, color = mobileText)
                if (skill.specialty.isNotEmpty()) {
                    Text(skill.specialty, style = mobileCaption1, color = mobileTextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (installed) {
                Icon(Icons.Default.Check, contentDescription = "已安装", modifier = Modifier.size(18.dp), tint = mobileAccent)
            }
            Text("›", fontSize = 20.sp, color = mobileTextTertiary)
        }
    }
}

// ────────────────────────────────────────────────────────
// FILE VIEWER HELPER
// ────────────────────────────────────────────────────────

private fun openSkillFolder(context: Context, skill: OnlineSkill, skillActions: SkillActions) {
    try {
        val dir = skillActions.getSkillDir(skill.toSlug())

        // Open the skill folder in system file manager via DocumentsUI
        val encodedPath = ".androidforclaw%2Fskills%2F${dir.name}"
        val uri = android.net.Uri.parse(
            "content://com.android.externalstorage.documents/document/primary:$encodedPath",
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "vnd.android.document/directory")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            // Fallback: open parent .androidforclaw/skills/
            val parentPath = ".androidforclaw%2Fskills"
            val parentUri = android.net.Uri.parse(
                "content://com.android.externalstorage.documents/document/primary:$parentPath",
            )
            val fallback = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(parentUri, "vnd.android.document/directory")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        }
    } catch (_: Exception) {
        // Silently fail if no file manager available
    }
}

// ────────────────────────────────────────────────────────
// DETAIL PAGE
// ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SkillDetailPage(
    skill: OnlineSkill,
    isInstalled: Boolean,
    isBundled: Boolean,
    skillActions: SkillActions,
    onBack: () -> Unit,
    onToggleInstall: (content: String) -> Unit,
) {
    val context = LocalContext.current
    var content by remember(skill.filename) { mutableStateOf("") }
    var isLoading by remember(skill.filename) { mutableStateOf(true) }
    var loadFailed by remember(skill.filename) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(skill.filename) {
        isLoading = true
        loadFailed = false
        val result = withContext(Dispatchers.IO) { AgencyAgentsFetcher.fetchContent(context, skill) }
        content = result
        loadFailed = result.isEmpty()
        isLoading = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(skill.name, color = mobileText, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = mobileText)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = mobileCardSurface),
        )

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = mobileAccent)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
            ) {
                item(key = "hero") {
                    // Hero
                    Text(skill.emoji, fontSize = 48.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        skill.name,
                        style = mobileTitle1,
                        fontWeight = FontWeight.Bold,
                        color = mobileText,
                    )
                    Spacer(Modifier.height(4.dp))
                    if (skill.specialty.isNotEmpty()) {
                        Text(skill.specialty, style = mobileBody, color = mobileTextSecondary)
                    }
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = mobileAccentSoft,
                    ) {
                        Text(
                            "${categoryEmoji(skill.category)} ${skill.category}",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = mobileCaption1,
                            color = mobileAccent,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Spacer(Modifier.height(20.dp))

                    // Install/Uninstall button
                    if (isBundled) {
                        OutlinedButton(
                            onClick = {},
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            enabled = false,
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp), tint = mobileAccent)
                            Spacer(Modifier.width(6.dp))
                            Text("预置 Skill", color = mobileAccent)
                        }
                    } else if (isInstalled) {
                        OutlinedButton(
                            onClick = { onToggleInstall(content) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            border = ButtonDefaults.outlinedButtonBorder(true),
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp), tint = mobileAccent)
                            Spacer(Modifier.width(6.dp))
                            Text("已安装 — 点击卸载", color = mobileAccent)
                        }
                    } else {
                        Button(
                            onClick = { onToggleInstall(content) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = mobileAccent),
                            enabled = content.isNotEmpty(),
                        ) {
                            Text("安装此 Skill", color = mobileText)
                        }
                    }

                    // View file button (installed skills only)
                    if (isInstalled && content.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { openSkillFolder(context, skill, skillActions) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp), tint = mobileTextSecondary)
                            Spacer(Modifier.width(6.dp))
                            Text("查看文件", color = mobileTextSecondary)
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                    HorizontalDivider(color = mobileBorder)
                    Spacer(Modifier.height(20.dp))
                }
                item(key = "content") {
                    if (content.isNotEmpty()) {
                        ChatMarkdown(text = content, textColor = mobileText)
                    } else if (loadFailed) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text("无法加载内容", style = mobileBody, color = mobileTextSecondary)
                            Button(
                                onClick = {
                                    scope.launch {
                                        isLoading = true
                                        loadFailed = false
                                        val result = withContext(Dispatchers.IO) {
                                            AgencyAgentsFetcher.fetchContent(context, skill)
                                        }
                                        content = result
                                        loadFailed = result.isEmpty()
                                        isLoading = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = mobileAccent),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text("重试", color = mobileText)
                            }
                        }
                    }
                }
            }
        }
    }
}
