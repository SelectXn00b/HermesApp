package com.ai.assistance.operit.ui.main.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.common.NavItem
import com.ai.assistance.operit.ui.features.about.screens.AboutScreen
import com.ai.assistance.operit.ui.features.assistant.screens.AssistantConfigScreen
import com.ai.assistance.operit.ui.features.chat.screens.AIChatScreen
import com.ai.assistance.operit.ui.features.demo.screens.ShizukuDemoScreen
import com.ai.assistance.operit.ui.features.help.screens.HelpScreen
import com.ai.assistance.operit.ui.features.memory.screens.MemoryScreen
import com.ai.assistance.operit.ui.features.packages.screens.MarketHomeTab
import com.ai.assistance.operit.ui.features.packages.screens.PackageManagerScreen
import com.ai.assistance.operit.ui.features.packages.screens.MCPManageScreen
import com.ai.assistance.operit.ui.features.packages.screens.MCPPublishScreen
import com.ai.assistance.operit.ui.features.packages.screens.MCPPluginDetailScreen
import com.ai.assistance.operit.ui.features.packages.screens.ArtifactDetailScreen
import com.ai.assistance.operit.ui.features.packages.screens.ArtifactManageScreen
import com.ai.assistance.operit.ui.features.packages.screens.ArtifactPublishScreen
import com.ai.assistance.operit.ui.features.packages.screens.SkillDetailScreen
import com.ai.assistance.operit.ui.features.packages.screens.SkillManageScreen
import com.ai.assistance.operit.ui.features.packages.screens.SkillPublishScreen
import com.ai.assistance.operit.ui.features.packages.screens.UnifiedMarketScreen
import com.ai.assistance.operit.ui.features.settings.screens.ChatBackupSettingsScreen
import com.ai.assistance.operit.ui.features.settings.screens.ChatHistorySettingsScreen
import com.ai.assistance.operit.ui.features.settings.screens.ContextSummarySettingsScreen
import com.ai.assistance.operit.ui.features.settings.screens.ExternalHttpChatSettingsScreen
import com.ai.assistance.operit.ui.features.settings.screens.FunctionalConfigScreen
import com.ai.assistance.operit.ui.features.settings.screens.GlobalDisplaySettingsScreen
import com.ai.assistance.operit.ui.features.settings.screens.GitHubAccountScreen
import com.ai.assistance.operit.ui.features.settings.screens.HermesAgentParamsScreen
import com.ai.assistance.operit.ui.features.settings.screens.HermesGatewayCredentialsScreen
import com.ai.assistance.operit.ui.features.settings.screens.HermesGatewayPoliciesScreen
import com.ai.assistance.operit.ui.features.settings.screens.HermesGatewayQrBindScreen
import com.ai.assistance.operit.ui.features.settings.screens.HermesGatewayServiceScreen
import com.ai.assistance.operit.ui.features.settings.screens.HermesSettingsScreen
import com.ai.assistance.operit.ui.features.settings.screens.LanguageSettingsScreen
import com.ai.assistance.operit.ui.features.settings.screens.LayoutAdjustmentSettingsScreen
import com.ai.assistance.operit.ui.features.settings.screens.ModelConfigScreen
import com.ai.assistance.operit.ui.features.settings.screens.ModelPromptsSettingsScreen
import com.ai.assistance.operit.ui.features.settings.screens.TagMarketScreen
import com.ai.assistance.operit.ui.features.settings.screens.SettingsScreen
import com.ai.assistance.operit.ui.features.settings.screens.SpeechServicesSettingsScreen
import com.ai.assistance.operit.ui.features.settings.screens.ThemeSettingsScreen
import com.ai.assistance.operit.ui.features.settings.screens.ToolPermissionSettingsScreen
import com.ai.assistance.operit.ui.features.settings.screens.UserPreferencesGuideScreen
import com.ai.assistance.operit.ui.features.settings.screens.UserPreferencesSettingsScreen
import com.ai.assistance.operit.ui.features.settings.screens.MnnModelDownloadScreen
import com.ai.assistance.operit.ui.features.settings.screens.TokenUsageStatisticsScreen
import com.ai.assistance.operit.ui.features.token.TokenConfigWebViewScreen
import com.ai.assistance.operit.ui.features.toolbox.screens.AppPermissionsToolScreen
import com.ai.assistance.operit.ui.features.toolbox.screens.FileManagerToolScreen
import com.ai.assistance.operit.ui.features.toolbox.screens.LogcatToolScreen
import com.ai.assistance.operit.ui.features.toolbox.screens.ShellExecutorToolScreen
import com.ai.assistance.operit.ui.features.toolbox.screens.StreamMarkdownDemoScreen
import com.ai.assistance.operit.ui.features.toolbox.screens.TerminalAutoConfigToolScreen
import com.ai.assistance.operit.ui.features.toolbox.screens.TerminalToolScreen
import com.ai.assistance.operit.ui.features.toolbox.screens.ToolboxScreen
import com.ai.assistance.operit.ui.common.composedsl.ToolPkgComposeDslToolScreen
import com.ai.assistance.operit.ui.features.toolbox.screens.UIDebuggerToolScreen
import com.ai.assistance.operit.ui.features.toolbox.screens.DefaultAssistantGuideToolScreen
import com.ai.assistance.operit.ui.features.toolbox.screens.ProcessLimitRemoverToolScreen
import com.ai.assistance.operit.ui.features.toolbox.screens.sqlviewer.SqlViewerToolScreen
import com.ai.assistance.operit.ui.features.toolbox.screens.htmlpackager.HtmlPackagerScreen
import com.ai.assistance.operit.ui.features.toolbox.screens.speechtotext.SpeechToTextToolScreen
import com.ai.assistance.operit.ui.features.toolbox.screens.texttospeech.TextToSpeechToolScreen
import com.ai.assistance.operit.ui.features.toolbox.screens.tooltester.ToolTesterScreen
import com.ai.assistance.operit.ui.features.toolbox.screens.autoglm.AutoGlmOneClickToolScreen
import com.ai.assistance.operit.ui.features.toolbox.screens.autoglm.AutoGlmToolScreen
import com.ai.assistance.operit.ui.features.toolbox.screens.skillrecorder.SkillRecorderScreen
import com.ai.assistance.operit.ui.features.toolbox.screens.skillrecorder.SkillRecorderReviewScreen
import com.ai.assistance.operit.ui.features.feedback.FeedbackScreen
import com.ai.assistance.operit.ui.features.update.screens.UpdateScreen
import com.ai.assistance.operit.ui.features.workflow.screens.WorkflowListScreen
import com.ai.assistance.operit.ui.features.workflow.screens.WorkflowDetailScreen

// 路由配置类
typealias ScreenNavigationHandler = (Screen) -> Unit

typealias NavItemChangeHandler = (NavItem) -> Unit

// 重构的Screen类，添加了路由相关属性和内容渲染函数
sealed class Screen(
        // 指定父屏幕，用于返回导航
        open val parentScreen: Screen? = null,
        // 对应的导航项，用于侧边栏高亮显示
        open val navItem: NavItem? = null,
        // 屏幕标题资源ID
        open val titleRes: Int? = null,
        // 是否参与 AppContent 的跨页淡入淡出。
        // 某些包含实时渲染视图的页面在转场中保留上一页会产生明显残影。
        open val participatesInCrossfadeTransition: Boolean = true
) {
    // 屏幕内容渲染函数
    @Composable
    open fun Content(
            navController: NavController,
            navigateTo: ScreenNavigationHandler,
            updateNavItem: NavItemChangeHandler,
            onGoBack: () -> Unit,
            hasBackgroundImage: Boolean,
            onLoading: (Boolean) -> Unit,
            onError: (String) -> Unit,
            onGestureConsumed: (Boolean) -> Unit
    ) {
        // 子类实现具体内容
    }

    // Main screens (primary)
    data object AiChat : Screen(navItem = NavItem.AiChat) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                updateNavItem: NavItemChangeHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            AIChatScreen(
                    padding = PaddingValues(0.dp),
                    viewModel = null,
                    isFloatingMode = false,
                    hasBackgroundImage = hasBackgroundImage,
                    onNavigateToTokenConfig = { navigateTo(TokenConfig) },
                    onNavigateToSettings = {
                        navigateTo(Settings)
                        updateNavItem(NavItem.Settings)
                    },
                    onNavigateToUserPreferences = { navigateTo(UserPreferencesSettings) },
                    onNavigateToModelConfig = { navigateTo(ModelConfig) },
                    onNavigateToModelPrompts = { navigateTo(ModelPromptsSettings) },
                    onNavigateToPackageManager = { navigateTo(Packages) },
                    onNavigateToFeedback = { navigateTo(Feedback) },
                    onLoading = onLoading,
                    onError = onError,
                    onGestureConsumed = onGestureConsumed
            )
        }
    }

    data object MemoryBase : Screen(navItem = NavItem.MemoryBase, titleRes = R.string.screen_title_memory_base) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                updateNavItem: NavItemChangeHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            MemoryScreen()
        }
    }

    data object Packages : Screen(navItem = NavItem.Packages) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                updateNavItem: NavItemChangeHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            PackageManagerScreen(
                onNavigateToMCPMarket = { navigateTo(Market(MarketHomeTab.MCP)) },
                onNavigateToSkillMarket = { navigateTo(Market(MarketHomeTab.SKILL)) },
                onNavigateToArtifactMarket = { navigateTo(Market(MarketHomeTab.ARTIFACT)) },
                onOpenToolPkgPluginConfig = { containerPackageName, uiModuleId, title ->
                    navigateTo(
                        ToolPkgPluginConfig(
                            containerPackageName = containerPackageName,
                            uiModuleId = uiModuleId,
                            title = title
                        )
                    )
                }
            )
        }
    }

    data class Market(val initialTab: MarketHomeTab = MarketHomeTab.ARTIFACT) :
            Screen(parentScreen = Packages, navItem = NavItem.Packages, titleRes = R.string.screen_title_market) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                updateNavItem: NavItemChangeHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            UnifiedMarketScreen(
                initialTab = initialTab,
                onNavigateToArtifactPublish = { navigateTo(ArtifactPublish) },
                onNavigateToArtifactManage = { navigateTo(ArtifactManage) },
                onNavigateToArtifactDetail = { issue ->
                    navigateTo(ArtifactDetail(issue))
                },
                onNavigateToSkillPublish = { navigateTo(SkillPublish) },
                onNavigateToSkillManage = { navigateTo(SkillManage) },
                onNavigateToSkillDetail = { issue ->
                    navigateTo(SkillDetail(issue))
                },
                onNavigateToMcpPublish = { navigateTo(MCPPublish) },
                onNavigateToMcpManage = { navigateTo(MCPManage) },
                onNavigateToMcpDetail = { issue ->
                    navigateTo(MCPPluginDetail(issue))
                }
            )
        }
    }

    data object ArtifactManage : Screen(parentScreen = Market(MarketHomeTab.ARTIFACT), navItem = NavItem.Packages, titleRes = R.string.screen_title_artifact_manage) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                updateNavItem: NavItemChangeHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            ArtifactManageScreen(
                onNavigateBack = onGoBack,
                onNavigateToEdit = { issue ->
                    navigateTo(ArtifactEdit(issue))
                },
                onNavigateToPublish = { navigateTo(ArtifactPublish) },
                onNavigateToDetail = { issue ->
                    navigateTo(ArtifactDetail(issue))
                }
            )
        }
    }

    data object ArtifactPublish : Screen(parentScreen = Market(MarketHomeTab.ARTIFACT), navItem = NavItem.Packages, titleRes = R.string.screen_title_artifact_publish) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                updateNavItem: NavItemChangeHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            ArtifactPublishScreen(onNavigateBack = onGoBack)
        }
    }

    data class ArtifactEdit(val editingIssue: com.ai.assistance.operit.data.api.GitHubIssue) :
            Screen(parentScreen = ArtifactManage, navItem = NavItem.Packages, titleRes = R.string.screen_title_artifact_publish) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                updateNavItem: NavItemChangeHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            ArtifactPublishScreen(
                onNavigateBack = onGoBack,
                editingIssue = editingIssue
            )
        }
    }

    data class ArtifactDetail(val issue: com.ai.assistance.operit.data.api.GitHubIssue) :
            Screen(parentScreen = Market(MarketHomeTab.ARTIFACT), navItem = NavItem.Packages) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                updateNavItem: NavItemChangeHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            ArtifactDetailScreen(
                issue = issue,
                onNavigateBack = onGoBack
            )
        }
    }

    data object SkillManage : Screen(parentScreen = Market(MarketHomeTab.SKILL), navItem = NavItem.Packages, titleRes = R.string.screen_title_skill_manage) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                updateNavItem: NavItemChangeHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            SkillManageScreen(
                onNavigateBack = onGoBack,
                onNavigateToEdit = { issue ->
                    navigateTo(SkillEdit(issue))
                },
                onNavigateToPublish = { navigateTo(SkillPublish) },
                onNavigateToDetail = { issue ->
                    navigateTo(SkillDetail(issue))
                }
            )
        }
    }

    data object SkillPublish : Screen(parentScreen = Market(MarketHomeTab.SKILL), navItem = NavItem.Packages, titleRes = R.string.screen_title_skill_publish) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                updateNavItem: NavItemChangeHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            SkillPublishScreen(onNavigateBack = onGoBack)
        }
    }

    data class SkillEdit(val editingIssue: com.ai.assistance.operit.data.api.GitHubIssue) :
            Screen(parentScreen = SkillManage, navItem = NavItem.Packages, titleRes = R.string.screen_title_skill_publish) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                updateNavItem: NavItemChangeHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            SkillPublishScreen(
                onNavigateBack = onGoBack,
                editingIssue = editingIssue
            )
        }
    }

    data class SkillDetail(val issue: com.ai.assistance.operit.data.api.GitHubIssue) :
            Screen(parentScreen = Market(MarketHomeTab.SKILL), navItem = NavItem.Packages) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                updateNavItem: NavItemChangeHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            SkillDetailScreen(
                issue = issue,
                onNavigateBack = onGoBack
            )
        }
    }

    data object MCPPublish : Screen(parentScreen = Market(MarketHomeTab.MCP), navItem = NavItem.Packages, titleRes = R.string.screen_title_mcp_publish) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                updateNavItem: NavItemChangeHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            MCPPublishScreen(onNavigateBack = onGoBack)
        }
    }

    data object MCPManage : Screen(parentScreen = Market(MarketHomeTab.MCP), navItem = NavItem.Packages, titleRes = R.string.screen_title_mcp_manage) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                updateNavItem: NavItemChangeHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            MCPManageScreen(
                onNavigateBack = onGoBack,
                onNavigateToEdit = { issue ->
                    navigateTo(MCPEditPlugin(issue))
                },
                onNavigateToPublish = { navigateTo(MCPPublish) }
            )
        }
    }

    data class MCPEditPlugin(val editingIssue: com.ai.assistance.operit.data.api.GitHubIssue) : Screen(parentScreen = MCPManage, navItem = NavItem.Packages, titleRes = R.string.screen_title_mcp_publish) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                updateNavItem: NavItemChangeHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            MCPPublishScreen(
                onNavigateBack = onGoBack,
                editingIssue = editingIssue
            )
        }
    }

    data object Toolbox : Screen(navItem = NavItem.Toolbox) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                updateNavItem: NavItemChangeHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            ToolboxScreen(
                    navController = navController,
                    onFileManagerSelected = { navigateTo(FileManager) },
                    onTerminalSelected = { navigateTo(Terminal) },
                    onAppPermissionsSelected = { navigateTo(AppPermissions) },
                    onUIDebuggerSelected = { navigateTo(UIDebugger) },
                    onShellExecutorSelected = { navigateTo(ShellExecutor) },
                    onLogcatSelected = { navigateTo(Logcat) },
                    onTextToSpeechSelected = { navigateTo(TextToSpeech) },
                    onSpeechToTextSelected = { navigateTo(SpeechToText) },
                    onToolTesterSelected = { navigateTo(ToolTester) },
                    onAgreementSelected = { navigateTo(Agreement) },
                    onDefaultAssistantGuideSelected = { navigateTo(DefaultAssistantGuide) },
                    onProcessLimitRemoverSelected = { navigateTo(ProcessLimitRemover) },
                    onHtmlPackagerSelected = { navigateTo(HtmlPackager) },
                    onAutoGlmOneClickSelected = { navigateTo(AutoGlmOneClick) },
                    onAutoGlmToolSelected = { navigateTo(AutoGlmTool) },
                    onSkillRecorderSelected = { navigateTo(SkillRecorder) },
                    onSqlViewerSelected = { navigateTo(SqlViewer) },
                    onTokenConfigSelected = { navigateTo(TokenConfig) },
                    onToolPkgComposeDslSelected = { containerPackageName, uiModuleId, title ->
                        navigateTo(
                            ToolPkgComposeDsl(
                                containerPackageName = containerPackageName,
                                uiModuleId = uiModuleId,
                                title = title
                            )
                        )
                    }
            )
        }
    }


    data object ShizukuCommands : Screen(navItem = NavItem.ShizukuCommands) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                updateNavItem: NavItemChangeHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            ShizukuDemoScreen(navigateTo = navigateTo)
        }
    }

    data object Settings : Screen(navItem = NavItem.Settings) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                updateNavItem: NavItemChangeHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            SettingsScreen(
                    navigateToToolPermissions = { navigateTo(ToolPermission) },
                    onNavigateToUserPreferences = { navigateTo(UserPreferencesSettings) },
                    navigateToGitHubAccount = { navigateTo(GitHubAccount) },
                    navigateToHermesSettings = { navigateTo(HermesSettings) },
                    navigateToModelConfig = { navigateTo(ModelConfig) },
                    navigateToThemeSettings = { navigateTo(ThemeSettings) },
                    navigateToGlobalDisplaySettings = { navigateTo(GlobalDisplaySettings) },
                    navigateToModelPrompts = { navigateTo(ModelPromptsSettings) },
                    navigateToFunctionalConfig = { navigateTo(FunctionalConfig) },
                    navigateToChatHistorySettings = { navigateTo(ChatHistorySettings) },
                    navigateToChatBackupSettings = { navigateTo(ChatBackupSettings) },
                    navigateToLanguageSettings = { navigateTo(LanguageSettings) },
                    navigateToSpeechServicesSettings = { navigateTo(SpeechServicesSettings) },
                    navigateToExternalHttpChatSettings = { navigateTo(ExternalHttpChatSettings) },
                    navigateToPersonaCardGeneration = { navigateTo(PersonaCardGeneration) },
                    navigateToWaifuModeSettings = { navigateTo(WaifuModeSettings) },
                    navigateToTokenUsageStatistics = { navigateTo(TokenUsageStatistics) },
                    navigateToContextSummarySettings = { navigateTo(ContextSummarySettings) },
                    navigateToLayoutAdjustmentSettings = { navigateTo(LayoutAdjustmentSettings) }
            )
        }
    }

    data object GitHubAccount : Screen(parentScreen = Settings, navItem = NavItem.Settings, titleRes = R.string.github_account) {
        @Composable
        override fun Content(
            navController: NavController,
            navigateTo: ScreenNavigationHandler,
            updateNavItem: NavItemChangeHandler,
            onGoBack: () -> Unit,
            hasBackgroundImage: Boolean,
            onLoading: (Boolean) -> Unit,
            onError: (String) -> Unit,
            onGestureConsumed: (Boolean) -> Unit
        ) {
            GitHubAccountScreen()
        }
    }

    data object HermesSettings : Screen(parentScreen = Settings, navItem = NavItem.Settings, titleRes = R.string.screen_title_hermes_settings) {
        @Composable
        override fun Content(
            navController: NavController,
            navigateTo: ScreenNavigationHandler,
            updateNavItem: NavItemChangeHandler,
            onGoBack: () -> Unit,
            hasBackgroundImage: Boolean,
            onLoading: (Boolean) -> Unit,
            onError: (String) -> Unit,
            onGestureConsumed: (Boolean) -> Unit
        ) {
            HermesSettingsScreen(
                navigateToCredentials = { navigateTo(HermesGatewayCredentials) },
                navigateToPolicies = { navigateTo(HermesGatewayPolicies) },
                navigateToAgentParams = { navigateTo(HermesAgentParams) },
                navigateToGatewayService = { navigateTo(HermesGatewayService) },
                navigateToQrBind = { navigateTo(HermesGatewayQrBind) },
            )
        }
    }

    data object HermesGatewayCredentials : Screen(parentScreen = HermesSettings, navItem = NavItem.Settings, titleRes = R.string.screen_title_hermes_gateway_credentials) {
        @Composable
        override fun Content(
            navController: NavController,
            navigateTo: ScreenNavigationHandler,
            updateNavItem: NavItemChangeHandler,
            onGoBack: () -> Unit,
            hasBackgroundImage: Boolean,
            onLoading: (Boolean) -> Unit,
            onError: (String) -> Unit,
            onGestureConsumed: (Boolean) -> Unit
        ) {
            HermesGatewayCredentialsScreen()
        }
    }

    data object HermesGatewayPolicies : Screen(parentScreen = HermesSettings, navItem = NavItem.Settings, titleRes = R.string.screen_title_hermes_gateway_policies) {
        @Composable
        override fun Content(
            navController: NavController,
            navigateTo: ScreenNavigationHandler,
            updateNavItem: NavItemChangeHandler,
            onGoBack: () -> Unit,
            hasBackgroundImage: Boolean,
            onLoading: (Boolean) -> Unit,
            onError: (String) -> Unit,
            onGestureConsumed: (Boolean) -> Unit
        ) {
            HermesGatewayPoliciesScreen()
        }
    }

    data object HermesAgentParams : Screen(parentScreen = HermesSettings, navItem = NavItem.Settings, titleRes = R.string.screen_title_hermes_agent_params) {
        @Composable
        override fun Content(
            navController: NavController,
            navigateTo: ScreenNavigationHandler,
            updateNavItem: NavItemChangeHandler,
            onGoBack: () -> Unit,
            hasBackgroundImage: Boolean,
            onLoading: (Boolean) -> Unit,
            onError: (String) -> Unit,
            onGestureConsumed: (Boolean) -> Unit
        ) {
            HermesAgentParamsScreen()
        }
    }

    data object HermesGatewayService : Screen(parentScreen = HermesSettings, navItem = NavItem.Settings, titleRes = R.string.screen_title_hermes_gateway_service) {
        @Composable
        override fun Content(
            navController: NavController,
            navigateTo: ScreenNavigationHandler,
            updateNavItem: NavItemChangeHandler,
            onGoBack: () -> Unit,
            hasBackgroundImage: Boolean,
            onLoading: (Boolean) -> Unit,
            onError: (String) -> Unit,
            onGestureConsumed: (Boolean) -> Unit
        ) {
            HermesGatewayServiceScreen()
        }
    }

    data object HermesGatewayQrBind : Screen(parentScreen = HermesSettings, navItem = NavItem.Settings, titleRes = R.string.screen_title_hermes_gateway_qr_bind) {
        @Composable
        override fun Content(
            navController: NavController,
            navigateTo: ScreenNavigationHandler,
            updateNavItem: NavItemChangeHandler,
            onGoBack: () -> Unit,
            hasBackgroundImage: Boolean,
            onLoading: (Boolean) -> Unit,
            onError: (String) -> Unit,
            onGestureConsumed: (Boolean) -> Unit
        ) {
            HermesGatewayQrBindScreen()
        }
    }

    data object Help : Screen(navItem = NavItem.Help) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                updateNavItem: NavItemChangeHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            HelpScreen(onBackPressed = onGoBack)
        }
    }

    data object About : Screen(navItem = NavItem.About) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                updateNavItem: NavItemChangeHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            AboutScreen(
                navigateToUpdateHistory = {
                    navigateTo(UpdateHistory)
                }
            )
        }
    }

    data object Agreement : Screen(navItem = NavItem.Agreement) {
        @Composable
        override fun Content(
            navController: NavController,
            navigateTo: ScreenNavigationHandler,
            updateNavItem: NavItemChangeHandler,
            onGoBack: () -> Unit,
            hasBackgroundImage: Boolean,
            onLoading: (Boolean) -> Unit,
            onError: (String) -> Unit,
            onGestureConsumed: (Boolean) -> Unit
        ) {
            com.ai.assistance.operit.ui.features.agreement.screens.AgreementScreen(
                    onAgreementAccepted = onGoBack
            )
        }
    }

    data object UpdateHistory :
            Screen(
                    parentScreen = About,
                    navItem = NavItem.About,
                    titleRes = R.string.update_history
            ) {
        @Composable
        override fun Content(
            navController: NavController,
            navigateTo: ScreenNavigationHandler,
            updateNavItem: NavItemChangeHandler,
            onGoBack: () -> Unit,
            hasBackgroundImage: Boolean,
            onLoading: (Boolean) -> Unit,
            onError: (String) -> Unit,
            onGestureConsumed: (Boolean) -> Unit
        ) {
            UpdateScreen(onNavigateToThemeSettings = { navigateTo(ThemeSettings) })
        }
    }

    data object AssistantConfig :
            Screen(
                    navItem = NavItem.AssistantConfig,
                    participatesInCrossfadeTransition = false
            ) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                updateNavItem: NavItemChangeHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            AssistantConfigScreen()
        }
    }

    data object Workflow : Screen(navItem = NavItem.Workflow) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                updateNavItem: NavItemChangeHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            WorkflowListScreen(
                onNavigateToDetail = { workflowId ->
                    navigateTo(WorkflowDetail(workflowId))
                }
            )
        }
    }

    data class WorkflowDetail(val workflowId: String) : Screen(parentScreen = Workflow, navItem = NavItem.Workflow, titleRes = R.string.nav_workflow) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                updateNavItem: NavItemChangeHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            WorkflowDetailScreen(
                workflowId = workflowId,
                onNavigateBack = onGoBack
            )
        }
    }

    data object TokenConfig : Screen(parentScreen = AiChat, navItem = NavItem.TokenConfig) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                updateNavItem: NavItemChangeHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            TokenConfigWebViewScreen(onNavigateBack = onGoBack)
        }
    }

    // Secondary screens - Settings
    data object ToolPermission :
            Screen(parentScreen = Settings, navItem = NavItem.Settings, titleRes = R.string.screen_title_tool_permissions) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                updateNavItem: NavItemChangeHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            ToolPermissionSettingsScreen(navigateBack = onGoBack)
        }
    }

    data class UserPreferencesGuide(var profileName: String = "", var profileId: String = "") :
            Screen(parentScreen = Settings, navItem = NavItem.Settings, titleRes = R.string.screen_title_user_preferences_guide) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                updateNavItem: NavItemChangeHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            UserPreferencesGuideScreen(
                    profileName = profileName,
                    profileId = profileId,
                    onComplete = onGoBack,
                    navigateToPermissions = {
                        navigateTo(ShizukuCommands)
                        updateNavItem(NavItem.ShizukuCommands)
                    }
            )
        }
    }

    data object UserPreferencesSettings :
            Screen(parentScreen = Settings, navItem = NavItem.Settings, titleRes = R.string.screen_title_user_preferences_settings) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                updateNavItem: NavItemChangeHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            UserPreferencesSettingsScreen(
                    onNavigateBack = onGoBack,
                    onNavigateToGuide = { profileName, profileId ->
                        navigateTo(UserPreferencesGuide(profileName, profileId))
                    }
            )
        }
    }

    data object ModelConfig :
            Screen(parentScreen = Settings, navItem = NavItem.Settings, titleRes = R.string.screen_title_model_config) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                updateNavItem: NavItemChangeHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            ModelConfigScreen(
                onBackPressed = onGoBack,
                navigateToMnnModelDownload = { navigateTo(MnnModelDownload) }
            )
        }
    }
    // 添加SpeechServicesSettings屏幕定义
    data object SpeechServicesSettings :
            Screen(parentScreen = Settings, navItem = NavItem.Settings, titleRes = R.string.screen_title_speech_services_settings) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                updateNavItem: NavItemChangeHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            SpeechServicesSettingsScreen(
                onBackPressed = onGoBack,
                onNavigateToTextToSpeech = { navigateTo(TextToSpeech) }
            )
        }
    }
    
    data object ExternalHttpChatSettings :
        Screen(parentScreen = Settings, navItem = NavItem.Settings, titleRes = R.string.screen_title_external_http_chat_settings) {
        @Composable
        override fun Content(
            navController: NavController,
            navigateTo: ScreenNavigationHandler,
            updateNavItem: NavItemChangeHandler,
            onGoBack: () -> Unit,
            hasBackgroundImage: Boolean,
            onLoading: (Boolean) -> Unit,
            onError: (String) -> Unit,
            onGestureConsumed: (Boolean) -> Unit
        ) {
            ExternalHttpChatSettingsScreen(onBackPressed = onGoBack)
        }
    }
    
    // MNN模型下载屏幕
    data object MnnModelDownload :
        Screen(parentScreen = Settings, navItem = NavItem.Settings, titleRes = R.string.screen_title_mnn_model_download) {
        @Composable
        override fun Content(
            navController: NavController,
            navigateTo: ScreenNavigationHandler,
            updateNavItem: NavItemChangeHandler,
            onGoBack: () -> Unit,
            hasBackgroundImage: Boolean,
            onLoading: (Boolean) -> Unit,
            onError: (String) -> Unit,
            onGestureConsumed: (Boolean) -> Unit
        ) {
            MnnModelDownloadScreen(onBackPressed = onGoBack)
        }
    }
    
    // 新增：人设卡生成页面
    data object PersonaCardGeneration :
        Screen(parentScreen = Settings, navItem = NavItem.Settings, titleRes = R.string.screen_title_persona_card_generation) {
        @Composable
        override fun Content(
            navController: NavController,
            navigateTo: ScreenNavigationHandler,
            updateNavItem: NavItemChangeHandler,
            onGoBack: () -> Unit,
            hasBackgroundImage: Boolean,
            onLoading: (Boolean) -> Unit,
            onError: (String) -> Unit,
            onGestureConsumed: (Boolean) -> Unit
        ) {
            com.ai.assistance.operit.ui.features.settings.screens.PersonaCardGenerationScreen(
                onNavigateToSettings = { navigateTo(Settings) },
                onNavigateToUserPreferences = { navigateTo(UserPreferencesSettings) },
                onNavigateToModelConfig = { navigateTo(ModelConfig) },
                onNavigateToModelPrompts = { navigateTo(ModelPromptsSettings) }
            )
        }
    }

    // 新增：Waifu模式设置页面
    data object WaifuModeSettings :
        Screen(parentScreen = Settings, navItem = NavItem.Settings, titleRes = R.string.screen_title_waifu_mode_settings) {
        @Composable
        override fun Content(
            navController: NavController,
            navigateTo: ScreenNavigationHandler,
            updateNavItem: NavItemChangeHandler,
            onGoBack: () -> Unit,
            hasBackgroundImage: Boolean,
            onLoading: (Boolean) -> Unit,
            onError: (String) -> Unit,
            onGestureConsumed: (Boolean) -> Unit
        ) {
            com.ai.assistance.operit.ui.features.settings.screens.WaifuModeSettingsScreen(
                onNavigateBack = onGoBack,
                onNavigateToCustomEmoji = { navigateTo(CustomEmojiManagement) }
            )
        }
    }
    
    // 自定义表情管理页面
    data object CustomEmojiManagement :
        Screen(parentScreen = WaifuModeSettings, navItem = NavItem.Settings, titleRes = R.string.manage_custom_emoji) {
        @Composable
        override fun Content(
            navController: NavController,
            navigateTo: ScreenNavigationHandler,
            updateNavItem: NavItemChangeHandler,
            onGoBack: () -> Unit,
            hasBackgroundImage: Boolean,
            onLoading: (Boolean) -> Unit,
            onError: (String) -> Unit,
            onGestureConsumed: (Boolean) -> Unit
        ) {
            com.ai.assistance.operit.ui.features.settings.screens.CustomEmojiManagementScreen(
                onNavigateBack = onGoBack
            )
        }
    }
    
    data object TagMarket :
        Screen(parentScreen = ModelPromptsSettings, navItem = NavItem.Settings, titleRes = R.string.screen_title_tag_market) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                updateNavItem: NavItemChangeHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            TagMarketScreen(onBackPressed = onGoBack)
        }
    }

    data object ModelPromptsSettings :
            Screen(parentScreen = Settings, navItem = NavItem.Settings, titleRes = R.string.screen_title_model_prompts_settings) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                updateNavItem: NavItemChangeHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            ModelPromptsSettingsScreen(
                onBackPressed = onGoBack,
                onNavigateToMarket = { navigateTo(TagMarket) },
                onNavigateToPersonaGeneration = { navigateTo(PersonaCardGeneration) },
                onNavigateToChatManagement = { navigateTo(ChatHistorySettings) }
            )
        }
    }

    data object FunctionalConfig :
            Screen(parentScreen = Settings, navItem = NavItem.Settings, titleRes = R.string.screen_title_functional_config) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                updateNavItem: NavItemChangeHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            FunctionalConfigScreen(
                    onBackPressed = onGoBack,
                    onNavigateToModelConfig = { navigateTo(ModelConfig) }
            )
        }
    }

    data object ThemeSettings :
            Screen(parentScreen = Settings, navItem = NavItem.Settings, titleRes = R.string.screen_title_theme_settings) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                updateNavItem: NavItemChangeHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            ThemeSettingsScreen()
        }
    }

    data object GlobalDisplaySettings :
            Screen(parentScreen = Settings, navItem = NavItem.Settings, titleRes = R.string.screen_title_global_display_settings) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                updateNavItem: NavItemChangeHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            GlobalDisplaySettingsScreen(onBackPressed = onGoBack)
        }
    }

    data object LayoutAdjustmentSettings :
            Screen(parentScreen = Settings, navItem = NavItem.Settings, titleRes = R.string.screen_title_layout_adjustment) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                updateNavItem: NavItemChangeHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            LayoutAdjustmentSettingsScreen(onNavigateBack = onGoBack)
        }
    }

    data object ChatHistorySettings :
            Screen(parentScreen = Settings, navItem = NavItem.Settings, titleRes = R.string.screen_title_chat_history_settings) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                updateNavItem: NavItemChangeHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            ChatHistorySettingsScreen()
        }
    }

    data object ChatBackupSettings :
            Screen(parentScreen = Settings, navItem = NavItem.Settings, titleRes = R.string.screen_title_chat_backup_settings) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                updateNavItem: NavItemChangeHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            ChatBackupSettingsScreen()
        }
    }

    data object LanguageSettings :
            Screen(parentScreen = Settings, navItem = NavItem.Settings, titleRes = R.string.screen_title_language_settings) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                updateNavItem: NavItemChangeHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            LanguageSettingsScreen(onBackPressed = onGoBack)
        }
    }

    data object TokenUsageStatistics :
            Screen(parentScreen = Settings, navItem = NavItem.Settings, titleRes = R.string.settings_token_usage_stats) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                updateNavItem: NavItemChangeHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            TokenUsageStatisticsScreen(onBackPressed = onGoBack)
        }
    }

    data object ContextSummarySettings :
            Screen(parentScreen = Settings, navItem = NavItem.Settings, titleRes = R.string.screen_title_context_summary_settings) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                updateNavItem: NavItemChangeHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            ContextSummarySettingsScreen(onBackPressed = onGoBack)
        }
    }

    data class ToolPkgComposeDsl(
        val containerPackageName: String,
        val uiModuleId: String,
        val title: String
    ) : Screen(parentScreen = Toolbox, navItem = NavItem.Toolbox) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                updateNavItem: NavItemChangeHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            ToolPkgComposeDslToolScreen(
                navController = navController,
                containerPackageName = containerPackageName,
                uiModuleId = uiModuleId,
                fallbackTitle = title
            )
        }

        @Composable
        override fun getTitle(): String = title
    }

    data class ToolPkgPluginConfig(
        val containerPackageName: String,
        val uiModuleId: String,
        val title: String
    ) : Screen(parentScreen = Packages, navItem = NavItem.Packages) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                updateNavItem: NavItemChangeHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            ToolPkgComposeDslToolScreen(
                navController = navController,
                containerPackageName = containerPackageName,
                uiModuleId = uiModuleId,
                fallbackTitle = title
            )
        }

        @Composable
        override fun getTitle(): String = title
    }

    // Toolbox secondary screens

    data object FileManager :
            Screen(parentScreen = Toolbox, navItem = NavItem.Toolbox, titleRes = R.string.screen_title_file_manager) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                updateNavItem: NavItemChangeHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            FileManagerToolScreen(navController = navController)
        }
    }

    data object Terminal :
            Screen(parentScreen = Toolbox, navItem = NavItem.Toolbox, titleRes = R.string.screen_title_terminal) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                updateNavItem: NavItemChangeHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            TerminalToolScreen(navController = navController)
        }
    }

    data object TerminalSetup :
            Screen(parentScreen = Toolbox, navItem = NavItem.Toolbox, titleRes = R.string.screen_title_terminal) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                updateNavItem: NavItemChangeHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            TerminalToolScreen(navController = navController, forceShowSetup = true)
        }
    }

    data object TerminalAutoConfig :
            Screen(parentScreen = Toolbox, navItem = NavItem.Toolbox, titleRes = R.string.screen_title_terminal_auto_config) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                updateNavItem: NavItemChangeHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            TerminalAutoConfigToolScreen(navController = navController)
        }
    }

    data object AppPermissions :
            Screen(parentScreen = Toolbox, navItem = NavItem.Toolbox, titleRes = R.string.screen_title_app_permissions) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                updateNavItem: NavItemChangeHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            AppPermissionsToolScreen(navController = navController)
        }
    }

    data object UIDebugger :
            Screen(parentScreen = Toolbox, navItem = NavItem.Toolbox, titleRes = R.string.screen_title_ui_debugger) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                updateNavItem: NavItemChangeHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            UIDebuggerToolScreen(navController = navController)
        }
    }

    data object ShellExecutor :
            Screen(parentScreen = Toolbox, navItem = NavItem.Toolbox, titleRes = R.string.screen_title_shell_executor) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                updateNavItem: NavItemChangeHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            ShellExecutorToolScreen(navController = navController)
        }
    }

    data object Logcat :
            Screen(parentScreen = Toolbox, navItem = NavItem.Toolbox, titleRes = R.string.screen_title_logcat) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                updateNavItem: NavItemChangeHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            LogcatToolScreen(navController = navController)
        }
    }

    data object SqlViewer :
            Screen(parentScreen = Toolbox, navItem = NavItem.Toolbox, titleRes = R.string.screen_title_sql_viewer) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                updateNavItem: NavItemChangeHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            SqlViewerToolScreen(navController = navController)
        }
    }

    // 流式Markdown演示屏幕
    data object MarkdownDemo :
            Screen(parentScreen = Toolbox, navItem = NavItem.Toolbox, titleRes = R.string.screen_title_markdown_demo) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                updateNavItem: NavItemChangeHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            StreamMarkdownDemoScreen(onBackClick = onGoBack)
        }
    }

    // 工具测试屏幕
    data object ToolTester :
            Screen(parentScreen = Toolbox, navItem = NavItem.Toolbox, titleRes = R.string.screen_title_tool_tester) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                updateNavItem: NavItemChangeHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            ToolTesterScreen(navController = navController)
        }
    }

    // 在MarkdownDemo对象后添加TextToSpeech对象
    data object TextToSpeech :
            Screen(parentScreen = Toolbox, navItem = NavItem.Toolbox, titleRes = R.string.screen_title_text_to_speech) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                updateNavItem: NavItemChangeHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            TextToSpeechToolScreen(navController = navController)
        }
    }

    // Tools screens
    data object SpeechToText :
            Screen(parentScreen = Toolbox, navItem = NavItem.Toolbox, titleRes = R.string.screen_title_speech_to_text) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                updateNavItem: NavItemChangeHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            SpeechToTextToolScreen(navController = navController)
        }
    }

    data object DefaultAssistantGuide :
            Screen(parentScreen = Toolbox, navItem = NavItem.Toolbox, titleRes = R.string.screen_title_default_assistant_guide) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                updateNavItem: NavItemChangeHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            DefaultAssistantGuideToolScreen(navController = navController)
        }
    }


    data object ProcessLimitRemover : Screen(parentScreen = Toolbox, navItem = NavItem.Toolbox, titleRes = R.string.tool_process_limit_remover) {
        @Composable
        override fun Content(
            navController: NavController,
            navigateTo: ScreenNavigationHandler,
            updateNavItem: NavItemChangeHandler,
            onGoBack: () -> Unit,
            hasBackgroundImage: Boolean,
            onLoading: (Boolean) -> Unit,
            onError: (String) -> Unit,
            onGestureConsumed: (Boolean) -> Unit
        ) {
            ProcessLimitRemoverToolScreen(navController = navController)
        }
    }

    data object HtmlPackager : Screen(parentScreen = Toolbox, navItem = NavItem.Toolbox, titleRes = R.string.screen_title_html_packager) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                updateNavItem: NavItemChangeHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            HtmlPackagerScreen(onGoBack = onGoBack)
        }
    }

    data object AutoGlmOneClick : Screen(parentScreen = Toolbox, navItem = NavItem.Toolbox, titleRes = R.string.screen_title_autoglm_one_click) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                updateNavItem: NavItemChangeHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            AutoGlmOneClickToolScreen(
                navController = navController,
                onNavigateToModelConfig = {
                    navigateTo(ModelConfig)
                    updateNavItem(NavItem.Settings)
                }
            )
        }
    }
    
    data object AutoGlmTool : Screen(parentScreen = Toolbox, navItem = NavItem.Toolbox, titleRes = R.string.screen_title_autoglm_tool) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                updateNavItem: NavItemChangeHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            AutoGlmToolScreen()
        }
    }

    data object SkillRecorder : Screen(parentScreen = Toolbox, navItem = NavItem.Toolbox, titleRes = R.string.screen_title_skill_recorder) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                updateNavItem: NavItemChangeHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            SkillRecorderScreen(
                onNavigateToReview = { navigateTo(SkillRecorderReview) }
            )
        }
    }

    data object SkillRecorderReview : Screen(parentScreen = SkillRecorder, navItem = NavItem.Toolbox, titleRes = R.string.screen_title_skill_recorder_review) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                updateNavItem: NavItemChangeHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            SkillRecorderReviewScreen(onGoBack = onGoBack)
        }
    }

    // MCP 插件详情页面
    data class MCPPluginDetail(val issue: com.ai.assistance.operit.data.api.GitHubIssue) :
            Screen(parentScreen = Market(MarketHomeTab.MCP), navItem = NavItem.Packages) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                updateNavItem: NavItemChangeHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            MCPPluginDetailScreen(
                issue = issue,
                onNavigateBack = onGoBack
            )
        }
    }

    data object Feedback : Screen(navItem = NavItem.Feedback, titleRes = R.string.screen_title_feedback) {
        @Composable
        override fun Content(
                navController: NavController,
                navigateTo: ScreenNavigationHandler,
                updateNavItem: NavItemChangeHandler,
                onGoBack: () -> Unit,
                hasBackgroundImage: Boolean,
                onLoading: (Boolean) -> Unit,
                onError: (String) -> Unit,
                onGestureConsumed: (Boolean) -> Unit
        ) {
            FeedbackScreen(onNavigateBack = onGoBack)
        }
    }

    // 获取屏幕标题
    @Composable
    open fun getTitle(): String = titleRes?.let { stringResource(it) } ?: ""

    // 判断是否为二级屏幕
    val isSecondaryScreen: Boolean
        get() = parentScreen != null
}

// 路由管理器
object OperitRouter {
    // 处理返回导航
    fun handleBackNavigation(currentScreen: Screen): Screen? {
        return currentScreen.parentScreen
    }

    // 根据NavItem获取对应的Screen
    fun getScreenForNavItem(navItem: NavItem): Screen {
        return when (navItem) {
            NavItem.AiChat -> Screen.AiChat
            NavItem.MemoryBase -> Screen.MemoryBase
            NavItem.Packages -> Screen.Packages
            NavItem.Toolbox -> Screen.Toolbox
            NavItem.ShizukuCommands -> Screen.ShizukuCommands
            NavItem.Settings -> Screen.Settings
            NavItem.Help -> Screen.Help
            NavItem.About -> Screen.About
            NavItem.TokenConfig -> Screen.TokenConfig
            NavItem.UserPreferencesGuide -> Screen.UserPreferencesGuide()
            NavItem.AssistantConfig -> Screen.AssistantConfig
            NavItem.Agreement -> Screen.Agreement
            NavItem.Workflow -> Screen.Workflow
            NavItem.ModelConfig -> Screen.ModelConfig
            NavItem.Feedback -> Screen.Feedback
            else -> Screen.AiChat
        }
    }
}

// 全局的手势状态持有者，用于在不同组件间共享手势状态
object GestureStateHolder {
    // 聊天界面手势是否被消费的状态
    var isChatScreenGestureConsumed: Boolean = false
}
