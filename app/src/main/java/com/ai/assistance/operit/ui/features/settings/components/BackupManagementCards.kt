package com.ai.assistance.operit.ui.features.settings.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CharacterCardManagementCard(
    totalCharacterCardCount: Int,
    operationState: CharacterCardOperation,
    operationMessage: String,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    val characterCardsTitle = stringResource(R.string.backup_character_cards_title)
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionHeader(
                title = characterCardsTitle,
                subtitle = stringResource(R.string.backup_character_cards_subtitle),
                icon = Icons.Default.Person
            )

            Text(
                text = stringResource(
                    R.string.backup_character_cards_current_count,
                    totalCharacterCardCount
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ManagementButton(
                    text = stringResource(R.string.backup_export),
                    icon = Icons.Default.CloudDownload,
                    onClick = onExport,
                    modifier = Modifier.weight(1f, fill = false)
                )
                ManagementButton(
                    text = stringResource(R.string.backup_import),
                    icon = Icons.Default.CloudUpload,
                    onClick = onImport,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }

            AnimatedVisibility(visible = operationState != CharacterCardOperation.IDLE) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (operationState) {
                        CharacterCardOperation.EXPORTING ->
                            OperationProgressView(
                                message = stringResource(R.string.backup_exporting, characterCardsTitle)
                            )

                        CharacterCardOperation.IMPORTING ->
                            OperationProgressView(
                                message = stringResource(R.string.backup_importing, characterCardsTitle)
                            )

                        CharacterCardOperation.EXPORTED -> OperationResultCard(
                            title = stringResource(R.string.backup_export_success),
                            message = operationMessage,
                            icon = Icons.Default.CloudDownload
                        )

                        CharacterCardOperation.IMPORTED -> OperationResultCard(
                            title = stringResource(R.string.backup_import_success),
                            message = operationMessage,
                            icon = Icons.Default.CloudUpload
                        )

                        CharacterCardOperation.FAILED -> OperationResultCard(
                            title = stringResource(R.string.backup_operation_failed),
                            message = operationMessage,
                            icon = Icons.Default.Info,
                            isError = true
                        )

                        else -> {}
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DataManagementCard(
    totalChatCount: Int,
    operationState: ChatHistoryOperation,
    operationMessage: String,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onDelete: () -> Unit
) {
    val chatTitle = stringResource(R.string.backup_chat_history)

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionHeader(
                title = chatTitle,
                subtitle = stringResource(R.string.backup_chat_history_subtitle),
                icon = Icons.Default.History
            )

            Text(
                text = stringResource(R.string.backup_chat_current_count, totalChatCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ManagementButton(
                    text = stringResource(R.string.backup_export),
                    icon = Icons.Default.CloudDownload,
                    onClick = onExport,
                    modifier = Modifier.weight(1f, fill = false)
                )
                ManagementButton(
                    text = stringResource(R.string.backup_import),
                    icon = Icons.Default.CloudUpload,
                    onClick = onImport,
                    modifier = Modifier.weight(1f, fill = false)
                )
                ManagementButton(
                    text = stringResource(R.string.backup_delete_all),
                    icon = Icons.Default.Delete,
                    onClick = onDelete,
                    isDestructive = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            AnimatedVisibility(visible = operationState != ChatHistoryOperation.IDLE) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (operationState) {
                        ChatHistoryOperation.EXPORTING ->
                            OperationProgressView(message = stringResource(R.string.backup_exporting, chatTitle))

                        ChatHistoryOperation.IMPORTING ->
                            OperationProgressView(message = stringResource(R.string.backup_importing, chatTitle))

                        ChatHistoryOperation.DELETING ->
                            OperationProgressView(message = stringResource(R.string.backup_deleting))

                        ChatHistoryOperation.EXPORTED -> OperationResultCard(
                            title = stringResource(R.string.backup_export_success),
                            message = operationMessage,
                            icon = Icons.Default.CloudDownload
                        )

                        ChatHistoryOperation.IMPORTED -> OperationResultCard(
                            title = stringResource(R.string.backup_import_success),
                            message = operationMessage,
                            icon = Icons.Default.CloudUpload
                        )

                        ChatHistoryOperation.DELETED -> OperationResultCard(
                            title = stringResource(R.string.backup_delete_success),
                            message = operationMessage,
                            icon = Icons.Default.Delete
                        )

                        ChatHistoryOperation.FAILED -> OperationResultCard(
                            title = stringResource(R.string.backup_operation_failed),
                            message = operationMessage,
                            icon = Icons.Default.Info,
                            isError = true
                        )

                        else -> {}
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MemoryManagementCard(
    totalMemoryCount: Int,
    totalLinkCount: Int,
    operationState: MemoryOperation,
    operationMessage: String,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    val memoryTitle = stringResource(R.string.backup_memory_library)

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionHeader(
                title = memoryTitle,
                subtitle = stringResource(R.string.backup_memory_library_subtitle),
                icon = Icons.Default.Psychology
            )

            Text(
                text = stringResource(R.string.backup_memory_current_count, totalMemoryCount, totalLinkCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ManagementButton(
                    text = stringResource(R.string.backup_export),
                    icon = Icons.Default.CloudDownload,
                    onClick = onExport,
                    modifier = Modifier.weight(1f, fill = false)
                )
                ManagementButton(
                    text = stringResource(R.string.backup_import),
                    icon = Icons.Default.CloudUpload,
                    onClick = onImport,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }

            AnimatedVisibility(visible = operationState != MemoryOperation.IDLE) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (operationState) {
                        MemoryOperation.EXPORTING ->
                            OperationProgressView(message = stringResource(R.string.backup_exporting, memoryTitle))

                        MemoryOperation.IMPORTING ->
                            OperationProgressView(message = stringResource(R.string.backup_importing, memoryTitle))

                        MemoryOperation.EXPORTED -> OperationResultCard(
                            title = stringResource(R.string.backup_export_success),
                            message = operationMessage,
                            icon = Icons.Default.CloudDownload
                        )

                        MemoryOperation.IMPORTED -> OperationResultCard(
                            title = stringResource(R.string.backup_import_success),
                            message = operationMessage,
                            icon = Icons.Default.CloudUpload
                        )

                        MemoryOperation.FAILED -> OperationResultCard(
                            title = stringResource(R.string.backup_operation_failed),
                            message = operationMessage,
                            icon = Icons.Default.Info,
                            isError = true
                        )

                        else -> {}
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModelConfigManagementCard(
    totalConfigCount: Int,
    operationState: ModelConfigOperation,
    operationMessage: String,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    val modelConfigTitle = stringResource(R.string.backup_model_config)

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionHeader(
                title = modelConfigTitle,
                subtitle = stringResource(R.string.backup_model_config_subtitle),
                icon = Icons.Default.Settings
            )

            Text(
                text = stringResource(R.string.backup_model_config_current_count, totalConfigCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ManagementButton(
                    text = stringResource(R.string.backup_export),
                    icon = Icons.Default.CloudDownload,
                    onClick = onExport,
                    modifier = Modifier.weight(1f, fill = false)
                )
                ManagementButton(
                    text = stringResource(R.string.backup_import),
                    icon = Icons.Default.CloudUpload,
                    onClick = onImport,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }

            AnimatedVisibility(visible = operationState != ModelConfigOperation.IDLE) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (operationState) {
                        ModelConfigOperation.EXPORTING ->
                            OperationProgressView(message = stringResource(R.string.backup_exporting, modelConfigTitle))

                        ModelConfigOperation.IMPORTING ->
                            OperationProgressView(message = stringResource(R.string.backup_importing, modelConfigTitle))

                        ModelConfigOperation.EXPORTED -> OperationResultCard(
                            title = stringResource(R.string.backup_export_success),
                            message = operationMessage,
                            icon = Icons.Default.CloudDownload
                        )

                        ModelConfigOperation.IMPORTED -> OperationResultCard(
                            title = stringResource(R.string.backup_import_success),
                            message = operationMessage,
                            icon = Icons.Default.CloudUpload
                        )

                        ModelConfigOperation.FAILED -> OperationResultCard(
                            title = stringResource(R.string.backup_operation_failed),
                            message = operationMessage,
                            icon = Icons.Default.Info,
                            isError = true
                        )

                        else -> {}
                    }
                }
            }
        }
    }
}

@Composable
fun ManagementButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDestructive: Boolean = false,
    isWarning: Boolean = false
) {
    val colors = if (isDestructive) {
        ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.error
        )
    } else if (isWarning) {
        ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.tertiary
        )
    } else {
        ButtonDefaults.filledTonalButtonColors()
    }

    FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
        colors = colors,
        shape = RoundedCornerShape(14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            modifier = Modifier.size(ButtonDefaults.IconSize)
        )
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text(text)
    }
}

@Composable
fun OperationResultCard(
    title: String,
    message: String,
    icon: ImageVector,
    isError: Boolean = false
) {
    val containerColor = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
    val contentColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.padding(end = 16.dp)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
fun OperationProgressView(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
fun FaqCard() {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.backup_faq_title),
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = stringResource(R.string.backup_faq_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            androidx.compose.material3.HorizontalDivider()
            FaqItem(
                question = stringResource(R.string.backup_faq_why),
                answer = stringResource(R.string.backup_faq_why_answer)
            )
            FaqItem(
                question = stringResource(R.string.backup_faq_where),
                answer = stringResource(R.string.backup_faq_where_answer)
            )
            FaqItem(
                question = stringResource(R.string.backup_faq_duplicate),
                answer = stringResource(R.string.backup_faq_duplicate_answer)
            )
        }
    }
}

@Composable
fun FaqItem(question: String, answer: String) {
    Column(modifier = Modifier.padding(top = 16.dp)) {
        Text(
            text = question,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = answer,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
