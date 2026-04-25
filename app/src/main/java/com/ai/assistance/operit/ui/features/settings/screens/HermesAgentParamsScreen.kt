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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.hermes.gateway.HermesGatewayPreferences
import com.ai.assistance.operit.ui.components.CustomScaffold
import kotlinx.coroutines.launch

@Composable
fun HermesAgentParamsScreen() {
    val context = LocalContext.current
    val prefs = remember { HermesGatewayPreferences.getInstance(context) }
    val scope = rememberCoroutineScope()

    val currentMaxTurns by prefs.agentMaxTurnsFlow
        .collectAsState(initial = HermesGatewayPreferences.DEFAULT_AGENT_MAX_TURNS)

    var input by remember { mutableStateOf(currentMaxTurns.toString()) }
    LaunchedEffect(currentMaxTurns) {
        input = currentMaxTurns.toString()
    }

    var savedFlash by remember { mutableStateOf(false) }
    LaunchedEffect(savedFlash) {
        if (savedFlash) {
            kotlinx.coroutines.delay(1500)
            savedFlash = false
        }
    }

    CustomScaffold { padding: PaddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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
                        text = stringResource(R.string.hermes_agent_max_turns),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    OutlinedTextField(
                        value = input,
                        onValueChange = { raw ->
                            input = raw.filter { it.isDigit() }.take(3)
                        },
                        label = { Text(stringResource(R.string.hermes_agent_max_turns)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = stringResource(R.string.hermes_agent_max_turns_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            val parsed = input.toIntOrNull() ?: return@Button
                            scope.launch {
                                prefs.saveAgentMaxTurns(parsed)
                                savedFlash = true
                            }
                        }) {
                            Text(stringResource(R.string.hermes_common_save))
                        }
                    }
                }
            }
        }
    }
}
