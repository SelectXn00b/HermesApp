package com.ai.assistance.operit.ui.features.settings.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MiscellaneousServices
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.components.CustomScaffold

@Composable
fun HermesSettingsScreen(
    navigateToCredentials: () -> Unit,
    navigateToPolicies: () -> Unit,
    navigateToAgentParams: () -> Unit,
    navigateToGatewayService: () -> Unit,
    navigateToQrBind: () -> Unit,
) {
    CustomScaffold { padding: PaddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HermesSubScreenCard(
                title = stringResource(R.string.screen_title_hermes_gateway_credentials),
                subtitle = stringResource(R.string.hermes_settings_gateway_credentials_subtitle),
                icon = Icons.Default.VpnKey,
                onClick = navigateToCredentials,
            )
            HermesSubScreenCard(
                title = stringResource(R.string.screen_title_hermes_gateway_policies),
                subtitle = stringResource(R.string.hermes_settings_gateway_policies_subtitle),
                icon = Icons.Default.Rule,
                onClick = navigateToPolicies,
            )
            HermesSubScreenCard(
                title = stringResource(R.string.screen_title_hermes_agent_params),
                subtitle = stringResource(R.string.hermes_settings_agent_params_subtitle),
                icon = Icons.Default.Tune,
                onClick = navigateToAgentParams,
            )
            HermesSubScreenCard(
                title = stringResource(R.string.screen_title_hermes_gateway_service),
                subtitle = stringResource(R.string.hermes_settings_gateway_service_subtitle),
                icon = Icons.Default.PowerSettingsNew,
                onClick = navigateToGatewayService,
            )
            HermesSubScreenCard(
                title = stringResource(R.string.screen_title_hermes_gateway_qr_bind),
                subtitle = stringResource(R.string.hermes_settings_gateway_qr_bind_subtitle),
                icon = Icons.Default.QrCode,
                onClick = navigateToQrBind,
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = stringResource(R.string.settings_hermes_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HermesSubScreenCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
