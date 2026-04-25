package com.ai.assistance.operit.ui.features.packages.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.components.CustomScaffold

@Composable
fun MarketManageScaffold(
    isLoggedIn: Boolean,
    isLoading: Boolean,
    errorMessage: String?,
    isEmpty: Boolean,
    onLogin: () -> Unit,
    onPublish: () -> Unit,
    publishContentDescription: String,
    loginDescription: String,
    loadingMessage: String,
    emptyIcon: ImageVector,
    emptyTitle: String,
    emptyDescription: String,
    emptyActionLabel: String? = null,
    content: @Composable BoxScope.() -> Unit
) {
    CustomScaffold(
        floatingActionButton = {
            if (isLoggedIn) {
                FloatingActionButton(onClick = onPublish) {
                    Icon(Icons.Default.Add, contentDescription = publishContentDescription)
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            if (!isLoggedIn) {
                MarketManageLoginRequiredCard(
                    description = loginDescription,
                    onLogin = onLogin
                )
            }

            errorMessage?.let { message ->
                MarketManageErrorCard(message = message)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isLoading -> MarketManageLoadingState(message = loadingMessage)
                    isLoggedIn && isEmpty -> {
                        MarketManageEmptyState(
                            icon = emptyIcon,
                            title = emptyTitle,
                            description = emptyDescription,
                            actionLabel = emptyActionLabel,
                            onAction = onPublish
                        )
                    }

                    isLoggedIn -> content()
                }
            }
        }
    }
}

@Composable
fun MarketManageItemCard(
    title: String,
    description: String,
    issueNumber: Int,
    isOpen: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    supportingContent: @Composable ColumnScope.() -> Unit = {},
    actions: @Composable RowScope.() -> Unit
) {
    val clickableModifier =
        if (onClick != null) {
            modifier.clickable(onClick = onClick)
        } else {
            modifier
        }

    Card(
        modifier = clickableModifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor =
                if (isOpen) {
                    MaterialTheme.colorScheme.surface
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                }
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    MarketManagePublicationStatus(isOpen = isOpen)
                }

                Spacer(modifier = Modifier.width(12.dp))
                MarketManageIssueNumberChip(issueNumber = issueNumber)
            }

            if (description.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            supportingContent()

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                content = actions
            )
        }
    }
}

@Composable
fun MarketManageLabelChip(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = containerColor
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun MarketManageGitHubLabelChip(
    text: String,
    colorHex: String,
    modifier: Modifier = Modifier
) {
    val parsedColor =
        runCatching {
            Color(android.graphics.Color.parseColor("#${colorHex.removePrefix("#")}"))
        }.getOrDefault(MaterialTheme.colorScheme.surfaceVariant)

    MarketManageLabelChip(
        text = text,
        containerColor = parsedColor.copy(alpha = 0.18f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
    )
}

@Composable
fun MarketManageReviewStatusChip(
    isApproved: Boolean,
    modifier: Modifier = Modifier
) {
    val label =
        if (isApproved) {
            stringResource(R.string.market_review_approved)
        } else {
            stringResource(R.string.market_review_pending)
        }
    val containerColor =
        if (isApproved) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.tertiaryContainer
        }
    val contentColor =
        if (isApproved) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onTertiaryContainer
        }

    MarketManageLabelChip(
        text = label,
        containerColor = containerColor,
        contentColor = contentColor,
        modifier = modifier
    )
}

@Composable
fun RowScope.MarketManageSecondaryActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.weight(1f)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(label)
    }
}

@Composable
fun RowScope.MarketManageDangerActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.error
        ),
        modifier = Modifier.weight(1f)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(label)
    }
}

@Composable
fun RowScope.MarketManagePrimaryActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.weight(1f)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(label)
    }
}

@Composable
fun MarketManageDeleteDialog(
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    titleText: String = stringResource(R.string.confirm_delete),
    confirmText: String = stringResource(R.string.confirm_delete_action),
    dismissText: String = stringResource(R.string.cancel)
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titleText) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissText)
            }
        }
    )
}

@Composable
private fun MarketManageLoginRequiredCard(
    description: String,
    onLogin: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.please_login_github_first),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onLogin) {
                Text(stringResource(R.string.login_github))
            }
        }
    }
}

@Composable
private fun MarketManageErrorCard(message: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun MarketManageLoadingState(message: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(8.dp))
        Text(message)
    }
}

@Composable
private fun MarketManageEmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    actionLabel: String?,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            actionLabel?.let { label ->
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(onClick = onAction) {
                    Text(label)
                }
            }
        }
    }
}

@Composable
private fun MarketManagePublicationStatus(isOpen: Boolean) {
    val statusColor =
        if (isOpen) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outline
        }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (isOpen) Icons.Default.CheckCircle else Icons.Default.Cancel,
            contentDescription = null,
            tint = statusColor,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = if (isOpen) stringResource(R.string.published) else stringResource(R.string.removed),
            color = statusColor,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun MarketManageIssueNumberChip(issueNumber: Int) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = "#$issueNumber",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}
