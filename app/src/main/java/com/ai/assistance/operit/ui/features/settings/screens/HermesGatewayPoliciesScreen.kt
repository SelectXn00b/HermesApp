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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.hermes.gateway.HermesGatewayPreferences
import com.ai.assistance.operit.ui.components.CustomScaffold
import kotlinx.coroutines.launch

@Composable
fun HermesGatewayPoliciesScreen() {
    val context = LocalContext.current
    val prefs = remember { HermesGatewayPreferences.getInstance(context) }
    val scope = rememberCoroutineScope()

    CustomScaffold { padding: PaddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PlatformPoliciesCard(
                title = stringResource(R.string.hermes_platform_feishu),
                platform = HermesGatewayPreferences.PLATFORM_FEISHU,
                dmPolicyOptions = listOf("open", "pairing", "allowlist"),
                groupPolicyOptions = listOf("open", "allowlist", "disabled"),
                defaultDmPolicy = "pairing",
                defaultGroupPolicy = "allowlist",
                defaultRequireMention = true,
                prefs = prefs,
                saveScope = scope,
            )
            PlatformPoliciesCard(
                title = stringResource(R.string.hermes_platform_weixin),
                platform = HermesGatewayPreferences.PLATFORM_WEIXIN,
                dmPolicyOptions = listOf("open", "allowlist", "disabled"),
                groupPolicyOptions = listOf("open", "allowlist", "disabled"),
                defaultDmPolicy = "open",
                defaultGroupPolicy = "disabled",
                defaultRequireMention = false,
                prefs = prefs,
                saveScope = scope,
            )
        }
    }
}

@Composable
private fun PlatformPoliciesCard(
    title: String,
    platform: String,
    dmPolicyOptions: List<String>,
    groupPolicyOptions: List<String>,
    defaultDmPolicy: String,
    defaultGroupPolicy: String,
    defaultRequireMention: Boolean,
    prefs: HermesGatewayPreferences,
    saveScope: kotlinx.coroutines.CoroutineScope,
) {
    val dmPolicy by prefs.platformPolicyFieldFlow(platform, HermesGatewayPreferences.FIELD_DM_POLICY, defaultDmPolicy)
        .collectAsState(initial = defaultDmPolicy)
    val dmAllow by prefs.platformPolicyFieldFlow(platform, HermesGatewayPreferences.FIELD_DM_ALLOW_FROM)
        .collectAsState(initial = "")
    val groupPolicy by prefs.platformPolicyFieldFlow(platform, HermesGatewayPreferences.FIELD_GROUP_POLICY, defaultGroupPolicy)
        .collectAsState(initial = defaultGroupPolicy)
    val groupAllow by prefs.platformPolicyFieldFlow(platform, HermesGatewayPreferences.FIELD_GROUP_ALLOW_FROM)
        .collectAsState(initial = "")
    val replyMode by prefs.platformPolicyFieldFlow(platform, HermesGatewayPreferences.FIELD_REPLY_TO_MODE, "reply")
        .collectAsState(initial = "reply")
    val requireMentionStr by prefs.platformPolicyFieldFlow(
        platform,
        HermesGatewayPreferences.FIELD_REQUIRE_MENTION,
        if (defaultRequireMention) "true" else "false",
    ).collectAsState(initial = if (defaultRequireMention) "true" else "false")

    val state = remember(platform) { mutableStateMapOf<String, String>() }
    LaunchedEffect(platform, dmPolicy, dmAllow, groupPolicy, groupAllow, replyMode, requireMentionStr) {
        if (state.isEmpty()) {
            state[HermesGatewayPreferences.FIELD_DM_POLICY] = dmPolicy
            state[HermesGatewayPreferences.FIELD_DM_ALLOW_FROM] = dmAllow
            state[HermesGatewayPreferences.FIELD_GROUP_POLICY] = groupPolicy
            state[HermesGatewayPreferences.FIELD_GROUP_ALLOW_FROM] = groupAllow
            state[HermesGatewayPreferences.FIELD_REPLY_TO_MODE] = replyMode
            state[HermesGatewayPreferences.FIELD_REQUIRE_MENTION] = requireMentionStr
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
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )

            Text(
                text = stringResource(R.string.hermes_policy_dm_policy),
                style = MaterialTheme.typography.labelMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                dmPolicyOptions.forEach { option ->
                    FilterChip(
                        selected = state[HermesGatewayPreferences.FIELD_DM_POLICY] == option,
                        onClick = { state[HermesGatewayPreferences.FIELD_DM_POLICY] = option },
                        label = { Text(option) },
                    )
                }
            }

            PolicyField(
                label = stringResource(R.string.hermes_policy_dm_allow_from),
                value = state[HermesGatewayPreferences.FIELD_DM_ALLOW_FROM].orEmpty(),
                onValueChange = { state[HermesGatewayPreferences.FIELD_DM_ALLOW_FROM] = it },
                singleLine = false,
            )

            Text(
                text = stringResource(R.string.hermes_policy_group_policy),
                style = MaterialTheme.typography.labelMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                groupPolicyOptions.forEach { option ->
                    FilterChip(
                        selected = state[HermesGatewayPreferences.FIELD_GROUP_POLICY] == option,
                        onClick = { state[HermesGatewayPreferences.FIELD_GROUP_POLICY] = option },
                        label = { Text(option) },
                    )
                }
            }

            PolicyField(
                label = stringResource(R.string.hermes_policy_group_allow_from),
                value = state[HermesGatewayPreferences.FIELD_GROUP_ALLOW_FROM].orEmpty(),
                onValueChange = { state[HermesGatewayPreferences.FIELD_GROUP_ALLOW_FROM] = it },
                singleLine = false,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "群聊需 @ 机器人",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "开启后只有被 @ 才会触发回复",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state[HermesGatewayPreferences.FIELD_REQUIRE_MENTION] == "true",
                    onCheckedChange = {
                        state[HermesGatewayPreferences.FIELD_REQUIRE_MENTION] = if (it) "true" else "false"
                    },
                )
            }

            PolicyField(
                label = stringResource(R.string.hermes_policy_reply_to_mode),
                value = state[HermesGatewayPreferences.FIELD_REPLY_TO_MODE].orEmpty(),
                onValueChange = { state[HermesGatewayPreferences.FIELD_REPLY_TO_MODE] = it },
                singleLine = true,
            )

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
                    saveScope.launch {
                        state.forEach { (field, value) ->
                            prefs.savePlatformPolicyField(platform, field, value)
                        }
                        savedFlash = true
                    }
                }) {
                    Text(stringResource(R.string.hermes_common_save))
                }
            }
        }
    }
}

@Composable
private fun PolicyField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    singleLine: Boolean,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        modifier = Modifier.fillMaxWidth(),
    )
}
