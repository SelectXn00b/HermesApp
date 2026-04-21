package com.ai.assistance.operit.ui.main.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.core.tools.system.ShizukuAuthorizer
import com.ai.assistance.operit.data.preferences.DisplayPreferencesManager
import com.ai.assistance.operit.data.repository.WorkflowRepository
import com.ai.assistance.operit.ui.common.NavItem
import com.ai.assistance.operit.ui.main.NavGroup
import com.ai.assistance.operit.ui.main.screens.OperitRouter
import com.ai.assistance.operit.ui.main.screens.Screen
import com.ai.assistance.operit.ui.theme.liquidGlass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class SidebarPermissionStatus(
        val badgeTextResId: Int
)

/** Content for the expanded navigation drawer */
@Composable
fun DrawerContent(
        navGroups: List<NavGroup>,
        currentScreen: Screen,
        selectedItem: NavItem,
        isNetworkAvailable: Boolean,
        networkType: String,
        appearance: NavigationDrawerAppearance,
        topContentPadding: Dp? = null,
        scope: CoroutineScope,
        drawerState: androidx.compose.material3.DrawerState,
        onScreenSelected: (Screen, NavItem) -> Unit
) {
        val context = LocalContext.current
        val displayPreferencesManager = remember(context) { DisplayPreferencesManager.getInstance(context) }
        val enableNewSidebar by displayPreferencesManager.enableNewSidebar.collectAsState(initial = true)
        val bottomInset =
                WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val resolvedTopContentPadding =
                topContentPadding ?:
                WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val fixedBottomItems = remember {
                setOf(NavItem.Settings, NavItem.Help, NavItem.About)
        }
        val quickActionItems = remember {
                setOf(NavItem.Packages, NavItem.Workflow)
        }
        val packageManager = remember(context) {
                PackageManager.getInstance(context, AIToolHandler.getInstance(context))
        }
        val workflowRepository = remember(context) { WorkflowRepository(context) }
        val activePackageCount by
                produceState(initialValue = 0, currentScreen, enableNewSidebar) {
                        value =
                                withContext(Dispatchers.IO) {
                                        packageManager.getEnabledPackageNames().size
                                }
                }
        val workflowCount by
                produceState(initialValue = 0, currentScreen, enableNewSidebar) {
                        value =
                                withContext(Dispatchers.IO) {
                                        workflowRepository.getAllWorkflows().getOrDefault(emptyList()).size
                                }
                }
        val permissionStatus by
                produceState(
                        initialValue =
                                SidebarPermissionStatus(
                                        badgeTextResId = R.string.status_not_installed
                                ),
                        currentScreen,
                        enableNewSidebar
                ) {
                        value =
                                withContext(Dispatchers.IO) {
                                        when {
                                                !ShizukuAuthorizer.isShizukuInstalled(context) ->
                                                        SidebarPermissionStatus(
                                                                badgeTextResId =
                                                                        R.string.status_not_installed
                                                        )
                                                !ShizukuAuthorizer.isShizukuServiceRunning() ->
                                                        SidebarPermissionStatus(
                                                                badgeTextResId =
                                                                        R.string.status_not_running
                                                        )
                                                ShizukuAuthorizer.hasShizukuPermission() ->
                                                        SidebarPermissionStatus(
                                                                badgeTextResId =
                                                                        R.string.sidebar_status_normal
                                                        )
                                                else ->
                                                        SidebarPermissionStatus(
                                                                badgeTextResId =
                                                                        R.string.unauthorized
                                                        )
                                        }
                                }
                }
        val oldModeNavGroups = navGroups
        val newModeFlatItems =
                remember(navGroups) {
                        navGroups.flatMap { it.items }
                                .filterNot {
                                        it in fixedBottomItems ||
                                                it in quickActionItems ||
                                                it == NavItem.ShizukuCommands
                                }
                }
        val handleScreenSelection: (Screen, NavItem) -> Unit = { screen, item ->
                val shouldCloseBeforeNavigate =
                        drawerState.currentValue == DrawerValue.Open ||
                                drawerState.targetValue == DrawerValue.Open

                scope.launch {
                        if (shouldCloseBeforeNavigate) {
                                drawerState.close()
                        }
                        onScreenSelected(screen, item)
                }
        }
        val handleNavItemClick: (NavItem) -> Unit = { item ->
                handleScreenSelection(OperitRouter.getScreenForNavItem(item), item)
        }

        if (!enableNewSidebar) {
                Column(
                        modifier =
                                Modifier.fillMaxHeight()
                                        .verticalScroll(rememberScrollState())
                                        .padding(end = 8.dp, bottom = bottomInset)
                ) {
                        DrawerTopContent(
                                navGroups = oldModeNavGroups,
                                selectedItem = selectedItem,
                                isNetworkAvailable = isNetworkAvailable,
                                networkType = networkType,
                                appearance = appearance,
                                onNavItemClick = handleNavItemClick
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                }
                return
        }

        Column(
                modifier =
                        Modifier.fillMaxHeight()
                                .padding(
                                        top = resolvedTopContentPadding,
                                        end = 8.dp,
                                        bottom = bottomInset
                                )
        ) {
                Column(
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                ) {
                        NewSidebarTopContent(
                                selectedItem = selectedItem,
                                isNetworkAvailable = isNetworkAvailable,
                                networkType = networkType,
                                appearance = appearance,
                                navItems = newModeFlatItems,
                                activePackageCount = activePackageCount,
                                workflowCount = workflowCount,
                                permissionStatus = permissionStatus,
                                onNavItemClick = handleNavItemClick
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                }

                HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        thickness = 0.5.dp,
                        color = appearance.dividerColor.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(8.dp))

                DrawerBottomShortcutRow(
                        selectedItem = selectedItem,
                        appearance = appearance,
                        onNavItemClick = handleNavItemClick
                )
        }
}

/** Content for the collapsed navigation drawer (for tablet mode) */
@Composable
fun CollapsedDrawerContent(
        navItems: List<NavItem>,
        selectedItem: NavItem,
        isNetworkAvailable: Boolean,
        appearance: NavigationDrawerAppearance,
        onScreenSelected: (Screen, NavItem) -> Unit
) {
        Column(
                modifier =
                        Modifier.fillMaxHeight()
                                .verticalScroll(rememberScrollState())
                                .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
        ) {
                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                        modifier =
                                Modifier.size(44.dp)
                                        .liquidGlass(
                                                enabled = appearance.buttonLiquidGlassEnabled,
                                                shape = CircleShape,
                                                containerColor = appearance.buttonContainerColor,
                                                shadowElevation = 5.dp,
                                                borderWidth = 0.5.dp,
                                                blurRadius = 14.dp,
                                                overlayAlphaBoost = 0.05f,
                                                enableLens = false
                                        )
                                        .clip(CircleShape),
                        color = Color.Transparent,
                        shape = CircleShape
                ) {
                        IconButton(onClick = { }) {
                                Icon(
                                        imageVector =
                                                if (isNetworkAvailable) Icons.Default.Wifi
                                                else Icons.Default.WifiOff,
                                        contentDescription = stringResource(id = R.string.network_status_label),
                                        tint = appearance.statusAvailableColor,
                                        modifier = Modifier.size(24.dp)
                                )
                        }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(
                        modifier = Modifier.fillMaxWidth(0.6f),
                        color = appearance.dividerColor
                )
                Spacer(modifier = Modifier.height(16.dp))

                for (item in navItems) {
                        val selectedGlassOverlayColor =
                                if (selectedItem == item) {
                                        appearance.selectedContainerColor.copy(alpha = 0.18f)
                                } else {
                                        Color.Transparent
                                }
                        Surface(
                                modifier =
                                        Modifier.padding(vertical = 8.dp)
                                                .size(44.dp)
                                                .liquidGlass(
                                                        enabled = appearance.buttonLiquidGlassEnabled,
                                                        shape = CircleShape,
                                                        containerColor =
                                                                appearance.buttonContainerColor,
                                                        shadowElevation =
                                                                if (selectedItem == item) 6.dp else 5.dp,
                                                        borderWidth = 0.5.dp,
                                                        blurRadius = 14.dp,
                                                        overlayAlphaBoost = 0.05f,
                                                        enableLens = false
                                                )
                                                .clip(CircleShape)
                                                .background(selectedGlassOverlayColor),
                                color = Color.Transparent,
                                shape = CircleShape
                        ) {
                                IconButton(
                                        onClick = {
                                                onScreenSelected(
                                                        OperitRouter.getScreenForNavItem(item),
                                                        item
                                                )
                                        }
                                ) {
                                        Icon(
                                                imageVector = item.icon,
                                                contentDescription = stringResource(id = item.titleResId),
                                                tint =
                                                        if (selectedItem == item) {
                                                                if (appearance.buttonLiquidGlassEnabled) {
                                                                        appearance.selectedContentColor
                                                                } else {
                                                                        appearance.titleColor
                                                                }
                                                        } else {
                                                                appearance.itemColor
                                                        },
                                                modifier = Modifier.size(24.dp)
                                        )
                                }
                        }
                }

                Spacer(modifier = Modifier.height(16.dp))
        }
}

@Composable
private fun DrawerTopContent(
        navGroups: List<NavGroup>,
        selectedItem: NavItem,
        isNetworkAvailable: Boolean,
        networkType: String,
        appearance: NavigationDrawerAppearance,
        onNavItemClick: (NavItem) -> Unit
) {
        Spacer(modifier = Modifier.height(54.dp))
        Text(
                text = stringResource(id = R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = appearance.titleColor,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )

        Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
                Icon(
                        imageVector =
                                if (isNetworkAvailable) Icons.Default.Wifi else Icons.Default.WifiOff,
                        contentDescription = stringResource(id = R.string.network_status_label),
                        tint = appearance.statusAvailableColor,
                        modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                        text = networkType,
                        style = MaterialTheme.typography.bodyMedium,
                        color = appearance.statusAvailableColor
                )
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = appearance.dividerColor
        )
        Spacer(modifier = Modifier.height(8.dp))

        navGroups.forEach { group ->
                NavigationDrawerItemHeader(
                        title = stringResource(id = group.titleResId),
                        appearance = appearance
                )
                group.items.forEach { item ->
                        CompactNavigationDrawerItem(
                                icon = item.icon,
                                label = stringResource(id = item.titleResId),
                                selected = selectedItem == item,
                                appearance = appearance,
                                onClick = { onNavItemClick(item) }
                        )
                }
        }
}

@Composable
private fun NewSidebarTopContent(
        selectedItem: NavItem,
        isNetworkAvailable: Boolean,
        networkType: String,
        appearance: NavigationDrawerAppearance,
        navItems: List<NavItem>,
        activePackageCount: Int,
        workflowCount: Int,
        permissionStatus: SidebarPermissionStatus,
        onNavItemClick: (NavItem) -> Unit
) {
        Spacer(modifier = Modifier.height(24.dp))

        SidebarInfoCard(
                isNetworkAvailable = isNetworkAvailable,
                networkType = networkType,
                appearance = appearance
        )

        Spacer(modifier = Modifier.height(14.dp))

        Row(
                modifier =
                        Modifier.fillMaxWidth()
                                .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
                SidebarQuickActionCard(
                        modifier = Modifier.weight(1f),
                        icon = NavItem.Packages.icon,
                        label = stringResource(id = NavItem.Packages.titleResId),
                        badgeText = activePackageCount.toString(),
                        selected = selectedItem == NavItem.Packages,
                        appearance = appearance,
                        onClick = { onNavItemClick(NavItem.Packages) }
                )
                SidebarQuickActionCard(
                        modifier = Modifier.weight(1f),
                        icon = NavItem.ShizukuCommands.icon,
                        label = stringResource(id = R.string.sidebar_permission_short),
                        badgeText = stringResource(id = permissionStatus.badgeTextResId),
                        selected = selectedItem == NavItem.ShizukuCommands,
                        appearance = appearance,
                        onClick = { onNavItemClick(NavItem.ShizukuCommands) }
                )
                SidebarQuickActionCard(
                        modifier = Modifier.weight(1f),
                        icon = NavItem.Workflow.icon,
                        label = stringResource(id = NavItem.Workflow.titleResId),
                        badgeText = workflowCount.toString(),
                        selected = selectedItem == NavItem.Workflow,
                        appearance = appearance,
                        onClick = { onNavItemClick(NavItem.Workflow) }
                )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
                text = stringResource(id = R.string.nav_group_ai_features),
                style = MaterialTheme.typography.titleSmall,
                color = appearance.titleColor.copy(alpha = 0.82f),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 28.dp, end = 20.dp, bottom = 2.dp)
        )

        Spacer(modifier = Modifier.height(6.dp))

        navItems.forEach { item ->
                CompactNavigationDrawerItem(
                        icon = item.icon,
                        label = stringResource(id = item.titleResId),
                        selected = selectedItem == item,
                        appearance = appearance,
                        onClick = { onNavItemClick(item) }
                )
        }
}

@Composable
private fun SidebarInfoCard(
        isNetworkAvailable: Boolean,
        networkType: String,
        appearance: NavigationDrawerAppearance
) {
        Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)
        ) {
                Text(
                        text = stringResource(id = R.string.app_name),
                        style = MaterialTheme.typography.titleLarge.copy(
                                letterSpacing = 0.5.sp
                        ),
                        color = appearance.titleColor,
                        fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                        shape = RoundedCornerShape(50),
                        color = appearance.statusAvailableColor.copy(alpha = 0.12f)
                ) {
                        Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                                Box(
                                        modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(
                                                        if (isNetworkAvailable) Color(0xFF4CAF50)
                                                        else Color(0xFFEF5350)
                                                )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                        imageVector =
                                                if (isNetworkAvailable) Icons.Default.Wifi else Icons.Default.WifiOff,
                                        contentDescription = stringResource(id = R.string.network_status_label),
                                        tint = appearance.statusAvailableColor,
                                        modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                        text = networkType,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = appearance.statusAvailableColor,
                                        fontWeight = FontWeight.Medium
                                )
                        }
                }
        }
}

@Composable
private fun SidebarQuickActionCard(
        modifier: Modifier = Modifier,
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        label: String,
        badgeText: String,
        selected: Boolean,
        appearance: NavigationDrawerAppearance,
        onClick: () -> Unit
) {
        val itemShape = RoundedCornerShape(12.dp)
        val selectedGlassOverlayColor =
                if (selected) {
                        appearance.selectedContainerColor.copy(alpha = 0.18f)
                } else {
                        Color.Transparent
                }
        val accentColor = appearance.selectedContentColor
        Surface(
                modifier =
                        modifier.height(76.dp)
                                .liquidGlass(
                                        enabled = appearance.buttonLiquidGlassEnabled,
                                        shape = itemShape,
                                        containerColor = appearance.buttonContainerColor,
                                        shadowElevation = if (selected) 4.dp else 2.dp,
                                        borderWidth = 0.5.dp,
                                        blurRadius = 16.dp,
                                        overlayAlphaBoost = 0.04f,
                                        enableLens = false
                                )
                                .clip(itemShape)
                                .background(selectedGlassOverlayColor),
                onClick = onClick,
                color =
                        if (appearance.buttonLiquidGlassEnabled) {
                                Color.Transparent
                        } else if (selected) {
                                appearance.selectedContainerColor
                        } else {
                                Color.Transparent
                        },
                shape = itemShape
        ) {
                Box(modifier = Modifier.fillMaxWidth().height(76.dp)) {
                        if (selected) {
                                Box(
                                        modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .fillMaxWidth(0.5f)
                                                .height(2.dp)
                                                .padding(bottom = 4.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(accentColor.copy(alpha = 0.7f))
                                )
                        }
                        SidebarQuickActionBadge(
                                text = badgeText,
                                appearance = appearance,
                                selected = selected,
                                modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(top = 6.dp, end = 6.dp)
                        )
                        Column(
                                modifier = Modifier
                                        .align(Alignment.Center)
                                        .padding(horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                        ) {
                                Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint =
                                                if (selected) appearance.selectedContentColor
                                                else appearance.itemColor,
                                        modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                                letterSpacing = 0.2.sp
                                        ),
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                        color =
                                                if (selected) appearance.selectedContentColor
                                                else appearance.itemColor,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                )
                        }
                }
        }
}

@Composable
private fun SidebarQuickActionBadge(
        text: String,
        appearance: NavigationDrawerAppearance,
        selected: Boolean,
        modifier: Modifier = Modifier
) {
        Surface(
                modifier = modifier,
                shape = RoundedCornerShape(50),
                color =
                        if (selected) {
                                appearance.selectedContentColor.copy(alpha = 0.16f)
                        } else {
                                appearance.selectedContainerColor.copy(alpha = 0.18f)
                        }
        ) {
                Text(
                        text = text,
                        style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 0.3.sp
                        ),
                        color =
                                if (selected) appearance.selectedContentColor
                                else appearance.titleColor.copy(alpha = 0.8f),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                )
        }
}

@Composable
private fun DrawerBottomShortcutRow(
        selectedItem: NavItem,
        appearance: NavigationDrawerAppearance,
        onNavItemClick: (NavItem) -> Unit
) {
        Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
                BottomShortcutDrawerItem(
                        modifier = Modifier.weight(1f),
                        item = NavItem.About,
                        selected = selectedItem == NavItem.About,
                        appearance = appearance,
                        onClick = { onNavItemClick(NavItem.About) }
                )
                BottomShortcutDrawerItem(
                        modifier = Modifier.weight(1f),
                        item = NavItem.Help,
                        selected = selectedItem == NavItem.Help,
                        appearance = appearance,
                        onClick = { onNavItemClick(NavItem.Help) }
                )
                BottomShortcutDrawerItem(
                        modifier = Modifier.weight(1f),
                        item = NavItem.Settings,
                        selected = selectedItem == NavItem.Settings,
                        appearance = appearance,
                        onClick = { onNavItemClick(NavItem.Settings) }
                )
        }
}

@Composable
private fun BottomShortcutDrawerItem(
        modifier: Modifier = Modifier,
        item: NavItem,
        selected: Boolean,
        appearance: NavigationDrawerAppearance,
        onClick: () -> Unit
) {
        val itemShape = RoundedCornerShape(14.dp)
        val selectedGlassOverlayColor =
                if (selected) {
                        appearance.selectedContainerColor.copy(alpha = 0.18f)
                } else {
                        Color.Transparent
                }
        val accentColor = appearance.selectedContentColor

        Surface(
                modifier =
                        modifier
                                .height(68.dp)
                                .liquidGlass(
                                        enabled = appearance.buttonLiquidGlassEnabled,
                                        shape = itemShape,
                                        containerColor = appearance.buttonContainerColor,
                                        shadowElevation = if (selected) 6.dp else 4.dp,
                                        borderWidth = 0.5.dp,
                                        blurRadius = 12.dp,
                                        overlayAlphaBoost = 0.04f,
                                        enableLens = false
                                )
                                .clip(itemShape)
                                .background(selectedGlassOverlayColor),
                onClick = onClick,
                color =
                        if (appearance.buttonLiquidGlassEnabled) {
                                Color.Transparent
                        } else if (selected) {
                                appearance.selectedContainerColor
                        } else {
                                Color.Transparent
                        },
                shape = itemShape
        ) {
                Box(modifier = Modifier.fillMaxSize()) {
                        if (selected) {
                                Box(
                                        modifier = Modifier
                                                .align(Alignment.TopCenter)
                                                .fillMaxWidth(0.4f)
                                                .height(2.5.dp)
                                                .padding(top = 4.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(accentColor.copy(alpha = 0.7f))
                                )
                        }
                        Column(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                        ) {
                                Icon(
                                        imageVector = item.icon,
                                        contentDescription = null,
                                        tint =
                                                if (selected) appearance.selectedContentColor
                                                else appearance.itemColor,
                                        modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.height(5.dp))
                                Text(
                                        text = stringResource(id = item.titleResId),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                                letterSpacing = 0.1.sp
                                        ),
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                        textAlign = TextAlign.Center,
                                        color =
                                                if (selected) appearance.selectedContentColor
                                                else appearance.itemColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.fillMaxWidth()
                                )
                        }
                }
        }
}
