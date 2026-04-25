package com.ai.assistance.operit.ui.features.settings.screens

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.hermes.gateway.HermesGatewayPreferences
import com.ai.assistance.operit.services.gateway.GatewayForegroundService
import com.ai.assistance.operit.ui.components.CustomScaffold
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.xiaomo.hermes.hermes.getHermesHome
import com.xiaomo.hermes.hermes.gateway.platforms.qrLogin
import com.xiaomo.hermes.hermes.gateway.platforms.qrRegister
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HermesGatewayQrBindScreen() {
    CustomScaffold { padding: PaddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.hermes_qr_placeholder),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FeishuQrBindCard()
            WeixinQrBindCard()
        }
    }
}

@Composable
private fun FeishuQrBindCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { HermesGatewayPreferences.getInstance(context) }

    var statusText by remember { mutableStateOf("") }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var userCode by remember { mutableStateOf("") }
    var isRunning by remember { mutableStateOf(false) }
    var appId by remember { mutableStateOf(prefs.readSecret(HermesGatewayPreferences.PLATFORM_FEISHU, HermesGatewayPreferences.SECRET_FEISHU_APP_ID)) }
    var domain by remember { mutableStateOf(prefs.readSecret(HermesGatewayPreferences.PLATFORM_FEISHU, HermesGatewayPreferences.SECRET_FEISHU_DOMAIN).ifEmpty { "feishu" }) }
    var botName by remember { mutableStateOf(prefs.readSecret(HermesGatewayPreferences.PLATFORM_FEISHU, HermesGatewayPreferences.SECRET_FEISHU_BOT_NAME)) }
    var loginJob by remember { mutableStateOf<Job?>(null) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "飞书 (Feishu) / Lark 扫码创建应用",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "使用 Feishu Open Platform 的 accounts device-code 流程。扫码 → 飞书确认 → 落 app_id / app_secret。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )

            if (appId.isNotEmpty() && qrBitmap == null && !isRunning) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "已绑定: $appId",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (botName.isNotEmpty()) {
                            Text(
                                text = "Bot: $botName",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Text(
                            text = "Domain: $domain",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            qrBitmap?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "飞书注册二维码",
                    modifier = Modifier.size(260.dp),
                )
                if (userCode.isNotEmpty()) {
                    Text(
                        text = "User code: $userCode",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            if (statusText.isNotBlank()) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (statusText.startsWith("✅")) {
                        MaterialTheme.colorScheme.primary
                    } else if (statusText.startsWith("❌")) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            if (isRunning) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }

            if (isRunning) {
                OutlinedButton(
                    onClick = {
                        loginJob?.cancel()
                        loginJob = null
                        isRunning = false
                        statusText = "已取消"
                        qrBitmap = null
                        userCode = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("取消") }
            } else {
                Button(
                    onClick = {
                        statusText = "正在初始化注册流程..."
                        qrBitmap = null
                        userCode = ""
                        isRunning = true
                        loginJob = scope.launch {
                            val result = try {
                                withContext(Dispatchers.IO) {
                                    qrRegister(
                                        initialDomain = if (domain.isNotEmpty()) domain else "feishu",
                                        timeoutSeconds = 600,
                                        onQrCodeReady = { qrUrl, code ->
                                            val bmp = generateQrBitmap(qrUrl, 512)
                                            if (bmp != null) {
                                                qrBitmap = bmp
                                                userCode = code
                                                statusText = "请使用飞书 App 扫码"
                                            } else {
                                                statusText = "⚠️ 二维码生成失败"
                                            }
                                        },
                                        onStatusUpdate = { status ->
                                            statusText = when (status) {
                                                "init" -> "检查环境支持..."
                                                "begin" -> "请求 device code..."
                                                "waiting" -> "等待扫码..."
                                                "confirmed" -> "✅ 已确认，正在拉取 Bot 信息"
                                                "denied_or_timeout" -> "❌ 用户拒绝或超时"
                                                "error" -> "❌ 注册失败"
                                                else -> status
                                            }
                                        },
                                    )
                                }
                            } catch (e: Exception) {
                                statusText = "❌ 注册失败: ${e.message}"
                                null
                            }
                            isRunning = false
                            if (result != null) {
                                val gotAppId = (result["app_id"] as? String).orEmpty()
                                val gotAppSecret = (result["app_secret"] as? String).orEmpty()
                                val gotDomain = (result["domain"] as? String).orEmpty()
                                val gotBotName = (result["bot_name"] as? String).orEmpty()
                                val gotBotOpenId = (result["bot_open_id"] as? String).orEmpty()
                                if (gotAppId.isNotEmpty()) {
                                    prefs.writeSecret(
                                        HermesGatewayPreferences.PLATFORM_FEISHU,
                                        HermesGatewayPreferences.SECRET_FEISHU_APP_ID,
                                        gotAppId,
                                    )
                                    appId = gotAppId
                                }
                                if (gotAppSecret.isNotEmpty()) {
                                    prefs.writeSecret(
                                        HermesGatewayPreferences.PLATFORM_FEISHU,
                                        HermesGatewayPreferences.SECRET_FEISHU_APP_SECRET,
                                        gotAppSecret,
                                    )
                                }
                                if (gotDomain.isNotEmpty()) {
                                    prefs.writeSecret(
                                        HermesGatewayPreferences.PLATFORM_FEISHU,
                                        HermesGatewayPreferences.SECRET_FEISHU_DOMAIN,
                                        gotDomain,
                                    )
                                    domain = gotDomain
                                }
                                if (gotBotName.isNotEmpty()) {
                                    prefs.writeSecret(
                                        HermesGatewayPreferences.PLATFORM_FEISHU,
                                        HermesGatewayPreferences.SECRET_FEISHU_BOT_NAME,
                                        gotBotName,
                                    )
                                    botName = gotBotName
                                }
                                if (gotBotOpenId.isNotEmpty()) {
                                    prefs.writeSecret(
                                        HermesGatewayPreferences.PLATFORM_FEISHU,
                                        HermesGatewayPreferences.SECRET_FEISHU_BOT_OPEN_ID,
                                        gotBotOpenId,
                                    )
                                }
                                // 自动打开平台开关 + 拉起网关前台服务
                                prefs.savePlatformEnabled(HermesGatewayPreferences.PLATFORM_FEISHU, true)
                                GatewayForegroundService.start(context)
                                qrBitmap = null
                                userCode = ""
                                statusText = "✅ 凭证已保存,已自动启动网关"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (appId.isNotEmpty()) "重新扫码注册" else "开始扫码注册") }
            }

            if (appId.isNotEmpty() && !isRunning) {
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = {
                        prefs.clearSecrets(HermesGatewayPreferences.PLATFORM_FEISHU)
                        appId = ""
                        domain = "feishu"
                        botName = ""
                        statusText = "已清除凭证"
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("清除凭证") }
            }
        }
    }
}

@Composable
private fun WeixinQrBindCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { HermesGatewayPreferences.getInstance(context) }

    var statusText by remember { mutableStateOf("") }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isRunning by remember { mutableStateOf(false) }
    var accountId by remember { mutableStateOf(prefs.readSecret(HermesGatewayPreferences.PLATFORM_WEIXIN, HermesGatewayPreferences.SECRET_WEIXIN_ACCOUNT_ID)) }
    var loginJob by remember { mutableStateOf<Job?>(null) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "微信 (Weixin) iLink 扫码登录",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "使用 Hermes Python gateway 的 qr_login 协议。扫码 → 手机确认 → 凭证落盘。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )

            if (accountId.isNotEmpty() && qrBitmap == null && !isRunning) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Text(
                        text = "已绑定账号: $accountId",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            qrBitmap?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "微信登录二维码",
                    modifier = Modifier.size(260.dp),
                )
            }

            if (statusText.isNotBlank()) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (statusText.startsWith("✅")) {
                        MaterialTheme.colorScheme.primary
                    } else if (statusText.startsWith("❌")) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            if (isRunning) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }

            if (isRunning) {
                OutlinedButton(
                    onClick = {
                        loginJob?.cancel()
                        loginJob = null
                        isRunning = false
                        statusText = "已取消"
                        qrBitmap = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("取消") }
            } else {
                Button(
                    onClick = {
                        statusText = "正在获取二维码..."
                        qrBitmap = null
                        isRunning = true
                        loginJob = scope.launch {
                            val hermesHome = withContext(Dispatchers.IO) { getHermesHome().absolutePath }
                            val result = try {
                                qrLogin(
                                    hermesHome = hermesHome,
                                    onQrCodeReady = { qrcodeImgContent, qrcode ->
                                        val content = qrcodeImgContent.ifEmpty { qrcode }
                                        val bmp = generateQrBitmap(content, 512)
                                        if (bmp != null) {
                                            qrBitmap = bmp
                                            statusText = "请使用微信扫描二维码"
                                        } else {
                                            statusText = "⚠️ 二维码生成失败"
                                        }
                                    },
                                    onStatusUpdate = { status ->
                                        statusText = when (status) {
                                            "wait" -> "等待扫码..."
                                            "scaned" -> "👀 已扫码，请在微信上确认"
                                            "scaned_but_redirect" -> "服务器重定向中..."
                                            "expired" -> "二维码已过期，正在刷新..."
                                            "expired_final" -> "❌ 多次过期，已停止"
                                            "confirmed" -> "✅ 登录成功"
                                            "timeout" -> "❌ 登录超时"
                                            else -> status
                                        }
                                    },
                                )
                            } catch (e: Exception) {
                                statusText = "❌ 登录失败: ${e.message}"
                                null
                            }
                            isRunning = false
                            if (result != null) {
                                val gotAccountId = result["account_id"].orEmpty()
                                val gotToken = result["token"].orEmpty()
                                if (gotAccountId.isNotEmpty()) {
                                    prefs.writeSecret(
                                        HermesGatewayPreferences.PLATFORM_WEIXIN,
                                        HermesGatewayPreferences.SECRET_WEIXIN_ACCOUNT_ID,
                                        gotAccountId,
                                    )
                                    accountId = gotAccountId
                                }
                                if (gotToken.isNotEmpty()) {
                                    prefs.writeSecret(
                                        HermesGatewayPreferences.PLATFORM_WEIXIN,
                                        HermesGatewayPreferences.SECRET_WEIXIN_LOGIN_TOKEN,
                                        gotToken,
                                    )
                                }
                                qrBitmap = null
                                prefs.savePlatformEnabled(HermesGatewayPreferences.PLATFORM_WEIXIN, true)
                                GatewayForegroundService.start(context)
                                statusText = "✅ 凭证已保存,已自动启动网关"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (accountId.isNotEmpty()) "重新扫码" else "开始扫码登录") }
            }

            if (accountId.isNotEmpty() && !isRunning) {
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = {
                        prefs.clearSecrets(HermesGatewayPreferences.PLATFORM_WEIXIN)
                        accountId = ""
                        statusText = "已清除凭证"
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("清除凭证") }
            }
        }
    }
}

private fun generateQrBitmap(content: String, size: Int): Bitmap? {
    return try {
        val hints = mapOf(
            EncodeHintType.MARGIN to 2,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        )
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bmp.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        bmp
    } catch (e: Exception) {
        null
    }
}
