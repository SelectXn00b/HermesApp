package com.xiaomo.androidforclaw.ui.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class LegalActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_TYPE = "legal_type"
        const val TYPE_PRIVACY = "privacy"
        const val TYPE_TERMS = "terms"

        fun start(context: Context, type: String) {
            context.startActivity(Intent(context, LegalActivity::class.java).apply {
                putExtra(EXTRA_TYPE, type)
            })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val type = intent.getStringExtra(EXTRA_TYPE) ?: TYPE_PRIVACY

        setContent {
            MaterialTheme(colorScheme = dynamicDarkColorScheme()) {
                LegalScreen(
                    type = type,
                    onBack = { finish() }
                )
            }
        }
    }

    @Composable
    private fun dynamicDarkColorScheme(): ColorScheme {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            dynamicDarkColorScheme(this)
        } else {
            darkColorScheme()
        }
    }

    @Composable
    private fun dynamicDarkColorScheme(context: Context): ColorScheme {
        return androidx.compose.material3.dynamicDarkColorScheme(context)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LegalScreen(type: String, onBack: () -> Unit) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val title = if (type == LegalActivity.TYPE_PRIVACY) "隐私政策" else "用户协议"
    val content = if (type == LegalActivity.TYPE_PRIVACY) privacyPolicyText() else termsOfServiceText()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            content.forEach { section ->
                if (section.isHeading) {
                    Text(
                        text = section.text,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                } else {
                    Text(
                        text = section.text,
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

private data class TextSection(val text: String, val isHeading: Boolean = false)

private fun privacyPolicyText(): List<TextSection> = listOf(
    TextSection("ForClaw 隐私政策", isHeading = true),
    TextSection("最后更新日期：2025年3月22日"),

    TextSection("一、概述", isHeading = true),
    TextSection("ForClaw（以下简称\u201C本应用\u201D）是一款 AI Agent 运行时工具，运行在 Android 设备上。我们非常重视您的隐私保护。本隐私政策旨在帮助您了解我们如何收集、使用和保护您的信息。"),

    TextSection("二、信息收集与使用", isHeading = true),
    TextSection("""本应用在运行过程中可能涉及以下数据：

1. AI 对话数据：您与 AI 助手的对话内容仅存储在您的设备本地，不会上传至我们的服务器。对话内容会发送至您配置的第三方 AI 服务提供商（如 OpenAI、Anthropic 等）以获取 AI 回复。

2. 设备信息：为提供无障碍辅助功能，本应用可能读取屏幕内容和应用信息。这些数据仅在设备本地处理，不会上传。

3. 网络通信：本应用需要网络连接以调用 AI API 服务。网络请求仅包含您主动发送的对话内容和必要的 API 认证信息。

4. 文件访问：本应用可能读写设备存储中的配置文件和会话记录，所有文件均存储在应用专属目录或您授权的目录中。"""),

    TextSection("三、权限说明", isHeading = true),
    TextSection("""本应用申请以下权限及其用途：

• 无障碍服务：用于辅助操作手机界面，执行 AI Agent 的自动化任务。
• 悬浮窗：用于显示 AI 会话悬浮窗口，方便您在使用其他应用时与 AI 交互。
• 录屏/截图：用于获取屏幕内容，帮助 AI 理解当前界面状态。
• 网络访问：用于调用 AI API 服务和消息渠道（飞书、Discord 等）。
• 存储访问：用于读写配置文件、会话记录和工作空间文件。
• 通知监听：用于读取和管理设备通知，支持通知相关的自动化任务。
• 安装应用：用于应用内自动更新功能。"""),

    TextSection("四、第三方服务", isHeading = true),
    TextSection("""本应用可能会将数据发送至以下第三方服务：

1. AI 服务提供商：包括但不限于 OpenAI、Anthropic、Google 等，用于处理您的 AI 对话请求。具体使用哪个服务取决于您的配置。

2. 消息渠道：包括飞书、Discord、Telegram、Slack 等，仅在您主动启用并配置相关渠道后才会使用。

请注意，第三方服务的数据处理受其各自的隐私政策约束。"""),

    TextSection("五、数据存储与安全", isHeading = true),
    TextSection("""• 所有用户数据均存储在您的设备本地。
• API 密钥等敏感信息使用 Android EncryptedSharedPreferences 加密存储。
• 本应用不设立独立服务器，不收集、不存储任何用户数据到云端。
• 网络传输使用 HTTPS 加密（连接 AI API 时）。"""),

    TextSection("六、用户权利", isHeading = true),
    TextSection("""您可以随时：
• 在应用设置中查看和修改您的配置信息。
• 清除应用数据以删除所有本地存储的对话记录和配置。
• 卸载应用以完全删除所有相关数据。
• 在系统设置中撤销本应用的任何权限。"""),

    TextSection("七、儿童隐私", isHeading = true),
    TextSection("本应用不面向 13 岁以下的儿童。我们不会有意收集 13 岁以下儿童的个人信息。"),

    TextSection("八、隐私政策更新", isHeading = true),
    TextSection("我们可能会不时更新本隐私政策。更新后的隐私政策将在应用内公布。继续使用本应用即表示您同意更新后的隐私政策。"),

    TextSection("九、联系我们", isHeading = true),
    TextSection("如果您对本隐私政策有任何疑问，请通过以下方式联系我们：\n\n邮箱：xiaomochn@gmail.com\nGitHub：https://github.com/SelectXn00b/AndroidForClaw"),
)

private fun termsOfServiceText(): List<TextSection> = listOf(
    TextSection("ForClaw 用户协议", isHeading = true),
    TextSection("最后更新日期：2025年3月22日"),

    TextSection("一、服务说明", isHeading = true),
    TextSection("ForClaw 是一款运行在 Android 设备上的 AI Agent 运行时工具。本应用为您提供 AI 对话、自动化操作和多渠道消息接入等功能。使用本应用即表示您同意遵守本协议。"),

    TextSection("二、使用条件", isHeading = true),
    TextSection("""使用本应用，您需要：

1. 拥有合法的 AI 服务 API 密钥（如 OpenAI、Anthropic 等）。本应用本身不提供 AI 服务，仅作为客户端工具。

2. 确保您使用 AI 服务的方式符合相应服务提供商的使用条款。

3. 对使用本应用产生的所有 AI 交互结果自行承担责任。"""),

    TextSection("三、用户责任", isHeading = true),
    TextSection("""您同意：

• 不使用本应用进行任何违法违规活动。
• 不利用本应用的自动化功能骚扰他人或破坏其他应用/服务。
• 妥善保管您的 API 密钥和相关配置信息。
• 对通过本应用执行的所有操作承担责任。"""),

    TextSection("四、免责声明", isHeading = true),
    TextSection("""• 本应用按\u201C现状\u201D提供，不作任何明示或暗示的保证。
• AI 生成的内容可能不准确或包含错误，请您自行甄别。
• 本应用的自动化操作可能产生非预期结果，请在使用前充分了解相关功能。
• 因第三方 AI 服务变更、中断或终止导致的功能不可用，我们不承担责任。
• 因用户配置不当导致的数据丢失或安全问题，我们不承担责任。"""),

    TextSection("五、知识产权", isHeading = true),
    TextSection("本应用的代码基于开源项目开发，遵循相应的开源协议。应用内的 UI 设计、图标和其他原创内容的知识产权归开发者所有。"),

    TextSection("六、协议变更", isHeading = true),
    TextSection("我们保留随时修改本协议的权利。修改后的协议将在应用内公布。继续使用本应用即表示您同意修改后的协议。"),

    TextSection("七、联系方式", isHeading = true),
    TextSection("如果您对本协议有任何疑问，请通过以下方式联系我们：\n\n邮箱：xiaomochn@gmail.com\nGitHub：https://github.com/SelectXn00b/AndroidForClaw"),
)
