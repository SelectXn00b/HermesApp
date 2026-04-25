package com.ai.assistance.operit.ui.features.settings.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.hermes.gateway.HermesGatewayPreferences
import com.ai.assistance.operit.ui.components.CustomScaffold
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun HermesGatewayCredentialsScreen() {
    val context = LocalContext.current
    val prefs = remember { HermesGatewayPreferences.getInstance(context) }
    val scope = rememberCoroutineScope()

    val feishuEnabled by prefs.platformEnabledFlow(HermesGatewayPreferences.PLATFORM_FEISHU)
        .collectAsState(initial = false)
    val weixinEnabled by prefs.platformEnabledFlow(HermesGatewayPreferences.PLATFORM_WEIXIN)
        .collectAsState(initial = false)

    CustomScaffold { padding: PaddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PlatformCredentialsCard(
                title = stringResource(R.string.hermes_platform_feishu),
                enabled = feishuEnabled,
                onEnabledChange = { enabled ->
                    scope.launch {
                        prefs.savePlatformEnabled(HermesGatewayPreferences.PLATFORM_FEISHU, enabled)
                    }
                },
                platform = HermesGatewayPreferences.PLATFORM_FEISHU,
                fields = FEISHU_FIELDS,
                prefs = prefs,
            )
            PlatformCredentialsCard(
                title = stringResource(R.string.hermes_platform_weixin),
                enabled = weixinEnabled,
                onEnabledChange = { enabled ->
                    scope.launch {
                        prefs.savePlatformEnabled(HermesGatewayPreferences.PLATFORM_WEIXIN, enabled)
                    }
                },
                platform = HermesGatewayPreferences.PLATFORM_WEIXIN,
                fields = WEIXIN_FIELDS,
                prefs = prefs,
            )
        }
    }
}

private data class CredentialField(
    val key: String,
    val labelResId: Int,
    val isSecret: Boolean,
)

private val FEISHU_FIELDS = listOf(
    CredentialField(HermesGatewayPreferences.SECRET_FEISHU_APP_ID, R.string.hermes_credentials_feishu_app_id, false),
    CredentialField(HermesGatewayPreferences.SECRET_FEISHU_APP_SECRET, R.string.hermes_credentials_feishu_app_secret, true),
    CredentialField(HermesGatewayPreferences.SECRET_FEISHU_VERIFICATION_TOKEN, R.string.hermes_credentials_feishu_verification_token, true),
    CredentialField(HermesGatewayPreferences.SECRET_FEISHU_ENCRYPT_KEY, R.string.hermes_credentials_feishu_encrypt_key, true),
)

private val WEIXIN_FIELDS = listOf(
    CredentialField(HermesGatewayPreferences.SECRET_WEIXIN_ACCOUNT_ID, R.string.hermes_credentials_weixin_account_id, false),
    CredentialField(HermesGatewayPreferences.SECRET_WEIXIN_LOGIN_TOKEN, R.string.hermes_credentials_weixin_login_token, true),
)

@Composable
private fun PlatformCredentialsCard(
    title: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    platform: String,
    fields: List<CredentialField>,
    prefs: HermesGatewayPreferences,
) {
    val state = remember(platform) {
        mutableStateMapOf<String, String>().apply {
            fields.forEach { put(it.key, prefs.readSecret(platform, it.key)) }
        }
    }

    var savedFlash by remember(platform) { mutableStateOf(false) }
    LaunchedEffect(savedFlash) {
        if (savedFlash) {
            kotlinx.coroutines.delay(1500)
            savedFlash = false
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.hermes_common_enabled),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
            }

            fields.forEach { field ->
                OutlinedTextField(
                    value = state[field.key].orEmpty(),
                    onValueChange = { state[field.key] = it },
                    label = { Text(stringResource(field.labelResId)) },
                    visualTransformation = if (field.isSecret) PasswordVisualTransformation()
                    else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (field.isSecret) KeyboardType.Password else KeyboardType.Text,
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.weight(1f))
                if (savedFlash) {
                    Text(
                        text = stringResource(R.string.hermes_common_saved),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Button(onClick = {
                    fields.forEach { field ->
                        prefs.writeSecret(platform, field.key, state[field.key].orEmpty())
                    }
                    savedFlash = true
                }) {
                    Text(stringResource(R.string.hermes_common_save))
                }
            }
        }
    }
}
