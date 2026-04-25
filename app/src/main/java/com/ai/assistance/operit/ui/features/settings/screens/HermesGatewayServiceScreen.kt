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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.hermes.gateway.HermesGatewayController
import com.ai.assistance.operit.hermes.gateway.HermesGatewayPreferences
import com.ai.assistance.operit.services.gateway.GatewayForegroundService
import com.ai.assistance.operit.ui.components.CustomScaffold
import kotlinx.coroutines.launch

@Composable
fun HermesGatewayServiceScreen() {
    val context = LocalContext.current
    val prefs = remember { HermesGatewayPreferences.getInstance(context) }
    val controller = remember { HermesGatewayController.getInstance(context) }
    val scope = rememberCoroutineScope()

    val serviceEnabled by prefs.serviceEnabledFlow.collectAsState(initial = false)
    val autoStartOnBoot by prefs.autoStartOnBootFlow.collectAsState(initial = false)
    val status by controller.status.collectAsState()
    val error by controller.error.collectAsState()

    CustomScaffold { padding: PaddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Run / autostart toggles
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ToggleRow(
                        label = stringResource(R.string.hermes_service_run_switch),
                        checked = serviceEnabled,
                        onCheckedChange = { checked ->
                            scope.launch {
                                prefs.saveServiceEnabled(checked)
                                if (checked) {
                                    GatewayForegroundService.start(context)
                                } else {
                                    GatewayForegroundService.stop(context)
                                }
                            }
                        },
                    )
                    ToggleRow(
                        label = stringResource(R.string.hermes_service_autostart_switch),
                        checked = autoStartOnBoot,
                        onCheckedChange = { checked ->
                            scope.launch { prefs.saveAutoStartOnBoot(checked) }
                        },
                    )
                }
            }

            // Status card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.hermes_common_status),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = statusLabel(status),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    val errMsg = error
                    if (!errMsg.isNullOrBlank()) {
                        Text(
                            text = errMsg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun statusLabel(status: HermesGatewayController.Status): String =
    when (status) {
        HermesGatewayController.Status.STOPPED -> stringResource(R.string.hermes_service_status_stopped)
        HermesGatewayController.Status.STARTING -> stringResource(R.string.hermes_service_status_starting)
        HermesGatewayController.Status.RUNNING -> stringResource(R.string.hermes_service_status_running)
        HermesGatewayController.Status.STOPPING -> stringResource(R.string.hermes_service_status_stopping)
        HermesGatewayController.Status.FAILED -> stringResource(R.string.hermes_service_status_failed)
    }
