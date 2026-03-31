package ai.openclaw.app.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ai.openclaw.app.skill.AgencyAgentsFetcher
import ai.openclaw.app.skill.OnlineSkill

private enum class SkillTab { INSTALLED, ONLINE }

/** Simple installed-skill manager backed by SharedPreferences. */
private object InstalledSkillsManager {
    private const val PREFS = "installed_skills_prefs"
    private const val KEY_INSTALLED = "installed_filenames"

    fun getInstalled(context: Context): Set<String> {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_INSTALLED, emptySet())?.toSet() ?: emptySet()
    }

    fun install(context: Context, filename: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = getInstalled(context).toMutableSet()
        current.add(filename)
        prefs.edit().putStringSet(KEY_INSTALLED, current).apply()
    }

    fun uninstall(context: Context, filename: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = getInstalled(context).toMutableSet()
        current.remove(filename)
        prefs.edit().putStringSet(KEY_INSTALLED, current).apply()
    }

    fun isInstalled(context: Context, filename: String): Boolean {
        return filename in getInstalled(context)
    }
}

@Composable
fun SkillTabScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var activeTab by remember { mutableStateOf(SkillTab.INSTALLED) }
    var skills by remember { mutableStateOf(emptyList<OnlineSkill>()) }
    var isLoading by remember { mutableStateOf(true) }
    var expandedSkill by remember { mutableStateOf<OnlineSkill?>(null) }
    var skillContent by remember { mutableStateOf("") }
    var isLoadingContent by remember { mutableStateOf(false) }
    // track installed set to trigger recomposition on install/uninstall
    var installedSet by remember { mutableStateOf(emptySet<String>()) }

    LaunchedEffect(Unit) {
        isLoading = true
        skills = withContext(Dispatchers.IO) { AgencyAgentsFetcher.loadSkills(context) }
        installedSet = InstalledSkillsManager.getInstalled(context)
        isLoading = false
    }

    Column(modifier = modifier.fillMaxSize()) {
        // ── Tab bar: 已安装 / 在线 ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SkillTab.entries.forEach { tab ->
                val active = tab == activeTab
                Surface(
                    onClick = { activeTab = tab },
                    shape = RoundedCornerShape(20.dp),
                    color = if (active) mobileAccent else mobileCardSurface,
                    border = if (!active) CardDefaults.outlinedCardBorder() else null,
                ) {
                    Text(
                        text = if (tab == SkillTab.INSTALLED) "已安装" else "在线",
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        color = if (active) mobileText else mobileTextSecondary,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 14.sp,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            if (activeTab == SkillTab.ONLINE) {
                IconButton(onClick = {
                    scope.launch {
                        isLoading = true
                        skills = withContext(Dispatchers.IO) { AgencyAgentsFetcher.forceRefresh(context) }
                        isLoading = false
                    }
                }) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新", tint = mobileTextSecondary)
                }
            }
        }

        when {
            isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = mobileAccent)
                }
            }
            expandedSkill != null -> {
                val isInst = InstalledSkillsManager.isInstalled(context, expandedSkill!!.filename)
                SkillDetailContent(
                    skill = expandedSkill!!,
                    content = skillContent,
                    isLoading = isLoadingContent,
                    isInstalled = isInst,
                    onBack = { expandedSkill = null },
                    onToggleInstall = {
                        if (isInst) {
                            InstalledSkillsManager.uninstall(context, expandedSkill!!.filename)
                        } else {
                            InstalledSkillsManager.install(context, expandedSkill!!.filename)
                        }
                        installedSet = InstalledSkillsManager.getInstalled(context)
                    },
                )
            }
            activeTab == SkillTab.INSTALLED -> {
                val installedSkills = skills.filter { it.filename in installedSet }
                InstalledSkillList(
                    skills = installedSkills,
                    allSkills = skills,
                    onSkillClick = { skill ->
                        expandedSkill = skill
                        isLoadingContent = true
                        scope.launch {
                            skillContent = withContext(Dispatchers.IO) {
                                AgencyAgentsFetcher.fetchContent(context, skill)
                            }
                            isLoadingContent = false
                        }
                    },
                    onBrowseOnline = { activeTab = SkillTab.ONLINE },
                )
            }
            else -> {
                OnlineSkillList(
                    skills = skills,
                    installedSet = installedSet,
                    onSkillClick = { skill ->
                        expandedSkill = skill
                        isLoadingContent = true
                        scope.launch {
                            skillContent = withContext(Dispatchers.IO) {
                                AgencyAgentsFetcher.fetchContent(context, skill)
                            }
                            isLoadingContent = false
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun InstalledSkillList(
    skills: List<OnlineSkill>,
    allSkills: List<OnlineSkill>,
    onSkillClick: (OnlineSkill) -> Unit,
    onBrowseOnline: () -> Unit,
) {
    if (skills.isEmpty()) {
        // Empty state — guide user to online tab
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("🔧", fontSize = 48.sp)
                Text(
                    "还没有已安装的 Skills",
                    style = MaterialTheme.typography.titleMedium,
                    color = mobileText,
                )
                Text(
                    "去「在线」浏览并安装你感兴趣的 Skill 吧！",
                    style = MaterialTheme.typography.bodyMedium,
                    color = mobileTextSecondary,
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onBrowseOnline,
                    colors = ButtonDefaults.buttonColors(containerColor = mobileAccent),
                ) {
                    Text("浏览在线 Skills", color = mobileText)
                }
            }
        }
    } else {
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Text(
                    "已安装 (${skills.size})",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = mobileText,
                    fontWeight = FontWeight.Bold,
                )
            }
            items(skills) { skill ->
                SkillListItem(
                    skill = skill,
                    installed = true,
                    onClick = { onSkillClick(skill) },
                )
            }
        }
    }
}

@Composable
private fun OnlineSkillList(
    skills: List<OnlineSkill>,
    installedSet: Set<String>,
    onSkillClick: (OnlineSkill) -> Unit,
) {
    val featured = AgencyAgentsFetcher.featuredSkills
    val categories = skills.groupBy { it.category }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        // ── Featured section ──
        item {
            Text(
                "🔥 热门",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleSmall,
                color = mobileText,
                fontWeight = FontWeight.Bold,
            )
        }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(featured) { skill ->
                    FeaturedSkillCard(skill = skill, onClick = { onSkillClick(skill) })
                }
            }
        }

        // ── Category sections ──
        val categoryLabels = mapOf(
            "engineering" to "🏗️ Engineering",
            "design" to "🎨 Design",
            "marketing" to "📢 Marketing",
            "sales" to "💼 Sales",
            "social" to "📣 Social & Growth",
        )

        for ((cat, catSkills) in categories) {
            item {
                Text(
                    categoryLabels[cat] ?: cat,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = mobileText,
                    fontWeight = FontWeight.Bold,
                )
            }
            items(catSkills) { skill ->
                SkillListItem(
                    skill = skill,
                    installed = skill.filename in installedSet,
                    onClick = { onSkillClick(skill) },
                )
            }
        }
    }
}

@Composable
private fun FeaturedSkillCard(skill: OnlineSkill, onClick: () -> Unit) {
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
            Text(skill.emoji, fontSize = 28.sp)
            Text(
                skill.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = mobileText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (skill.specialty.isNotEmpty()) {
                Text(
                    skill.specialty,
                    style = MaterialTheme.typography.labelSmall,
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
                Text(
                    skill.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = mobileText,
                )
                if (skill.specialty.isNotEmpty()) {
                    Text(
                        skill.specialty,
                        style = MaterialTheme.typography.labelSmall,
                        color = mobileTextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (installed) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "已安装",
                    modifier = Modifier.size(18.dp),
                    tint = mobileAccent,
                )
            }
            Text("›", fontSize = 20.sp, color = mobileTextTertiary)
        }
    }
}

@Composable
private fun SkillDetailContent(
    skill: OnlineSkill,
    content: String,
    isLoading: Boolean,
    isInstalled: Boolean,
    onBack: () -> Unit,
    onToggleInstall: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = onBack) {
                Text("← 返回", color = mobileAccent)
            }
            Spacer(Modifier.weight(1f))
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = mobileAccent)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
            ) {
                item {
                    Text(skill.emoji, fontSize = 40.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        skill.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = mobileText,
                    )
                    Spacer(Modifier.height(4.dp))
                    if (skill.specialty.isNotEmpty()) {
                        Text(
                            skill.specialty,
                            style = MaterialTheme.typography.bodyMedium,
                            color = mobileTextSecondary,
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    Text(
                        "📂 ${skill.category}",
                        style = MaterialTheme.typography.labelSmall,
                        color = mobileTextTertiary,
                    )
                    Spacer(Modifier.height(16.dp))

                    // Install/Uninstall button
                    val buttonColors = if (isInstalled) {
                        ButtonDefaults.outlinedButtonColors()
                    } else {
                        ButtonDefaults.buttonColors(containerColor = mobileAccent)
                    }
                    Button(
                        onClick = onToggleInstall,
                        colors = buttonColors,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        if (isInstalled) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp), tint = mobileAccent)
                            Spacer(Modifier.width(6.dp))
                            Text("已安装 — 点击卸载", color = mobileAccent)
                        } else {
                            Text("安装此 Skill", color = mobileText)
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = mobileBorder)
                    Spacer(Modifier.height(16.dp))
                }
                item {
                    if (content.isNotEmpty()) {
                        Text(
                            content,
                            style = MaterialTheme.typography.bodySmall,
                            color = mobileText,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                        )
                    } else {
                        Text(
                            "无法加载内容，请检查网络连接。",
                            color = mobileTextSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}
