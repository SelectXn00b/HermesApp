package com.ai.assistance.operit.ui.features.packages.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.api.GitHubIssue
import com.ai.assistance.operit.ui.features.packages.market.ArtifactMarketScope
import com.ai.assistance.operit.ui.features.packages.market.PublishArtifactType
import com.ai.assistance.operit.ui.features.packages.market.PublishProgressStage
import com.ai.assistance.operit.ui.features.packages.screens.artifact.viewmodel.ArtifactMarketViewModel
import com.ai.assistance.operit.ui.features.packages.utils.ArtifactIssueParser

@Composable
fun ArtifactPublishScreen(
    onNavigateBack: () -> Unit,
    editingIssue: GitHubIssue? = null
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val viewModel: ArtifactMarketViewModel =
        viewModel(
            key = "artifact-publish-all",
            factory = ArtifactMarketViewModel.Factory(context.applicationContext, ArtifactMarketScope.ALL)
        )

    val artifacts by viewModel.publishableArtifacts.collectAsState()
    val publishStage by viewModel.publishProgressStage.collectAsState()
    val publishMessage by viewModel.publishMessage.collectAsState()
    val publishError by viewModel.publishErrorMessage.collectAsState()
    val publishSuccess by viewModel.publishSuccessMessage.collectAsState()
    val requiresForgeInitialization by viewModel.requiresForgeInitialization.collectAsState()
    val registrationRetryAvailable by viewModel.registrationRetryAvailable.collectAsState()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()

    val isEditMode = editingIssue != null
    val initialInfo = remember(editingIssue) { editingIssue?.let { ArtifactIssueParser.parseArtifactInfo(it) } }

    var selectedPackageName by rememberSaveable { mutableStateOf("") }
    var displayName by rememberSaveable { mutableStateOf(initialInfo?.title.orEmpty()) }
    var description by rememberSaveable { mutableStateOf(initialInfo?.description.orEmpty()) }
    var version by rememberSaveable { mutableStateOf(initialInfo?.version.orEmpty().ifBlank { "1.0.0" }) }
    var minSupportedAppVersion by rememberSaveable { mutableStateOf(initialInfo?.minSupportedAppVersion.orEmpty()) }
    var maxSupportedAppVersion by rememberSaveable { mutableStateOf(initialInfo?.maxSupportedAppVersion.orEmpty()) }

    var selectorExpanded by remember { mutableStateOf(false) }
    var showConfirmationDialog by remember { mutableStateOf(false) }
    var showSecondForgeConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshPublishableArtifacts()
    }

    LaunchedEffect(artifacts, initialInfo?.normalizedId) {
        if (selectedPackageName.isBlank()) {
            val matched =
                artifacts.firstOrNull {
                    normalizePackageMatch(it.packageName) == initialInfo?.normalizedId
                } ?: artifacts.firstOrNull()
            if (matched != null) {
                selectedPackageName = matched.packageName
                if (!isEditMode) {
                    displayName = matched.displayName
                    description = matched.description
                    version = matched.inferredVersion ?: "1.0.0"
                }
            }
        }
    }

    val selectedArtifact = artifacts.firstOrNull { it.packageName == selectedPackageName }
    val selectedType = selectedArtifact?.type
    val isPublishing = publishStage !in listOf(PublishProgressStage.IDLE, PublishProgressStage.COMPLETED)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(if (isEditMode) R.string.edit_description else R.string.publish_description),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(if (isEditMode) R.string.artifact_edit_info_description else R.string.artifact_publish_info_description),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        if (!isLoggedIn) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(stringResource(R.string.need_login_before_publish_artifact), modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }

        Box {
            OutlinedTextField(
                value = selectedArtifact?.displayName.orEmpty(),
                onValueChange = {},
                label = { Text(stringResource(R.string.local_artifact_entry)) },
                modifier = Modifier.fillMaxWidth().clickable(enabled = artifacts.isNotEmpty()) { selectorExpanded = true },
                readOnly = true,
                enabled = artifacts.isNotEmpty(),
                supportingText = {
                    if (selectedType != null) {
                        Text(
                            text = if (selectedType == PublishArtifactType.PACKAGE) stringResource(R.string.publish_target_package_market) else stringResource(R.string.publish_target_script_market)
                        )
                    }
                }
            )
            DropdownMenu(expanded = selectorExpanded, onDismissRequest = { selectorExpanded = false }) {
                artifacts.forEach { artifact ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(artifact.displayName)
                                Text(
                                    text = if (artifact.type == PublishArtifactType.PACKAGE) stringResource(R.string.artifact_type_package) else stringResource(R.string.artifact_type_script),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        onClick = {
                            selectedPackageName = artifact.packageName
                            selectorExpanded = false
                            if (!isEditMode) {
                                displayName = artifact.displayName
                                description = artifact.description
                                version = artifact.inferredVersion ?: "1.0.0"
                            }
                        }
                    )
                }
            }
        }

        OutlinedTextField(value = displayName, onValueChange = { displayName = it }, label = { Text(stringResource(R.string.display_name_label)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text(stringResource(R.string.description_label)) }, modifier = Modifier.fillMaxWidth(), minLines = 3)
        OutlinedTextField(value = version, onValueChange = { version = it }, label = { Text(stringResource(R.string.version_label)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(
            value = minSupportedAppVersion,
            onValueChange = { minSupportedAppVersion = it },
            label = { Text(stringResource(R.string.min_supported_app_version)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            supportingText = { Text(stringResource(R.string.supported_version_input_hint)) }
        )
        OutlinedTextField(
            value = maxSupportedAppVersion,
            onValueChange = { maxSupportedAppVersion = it },
            label = { Text(stringResource(R.string.max_supported_app_version)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            supportingText = { Text(stringResource(R.string.supported_version_input_hint)) }
        )

        publishError?.let { error ->
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.92f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.publish_failed_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (registrationRetryAvailable) {
                        OutlinedButton(onClick = viewModel::retryPendingMarketRegistration) {
                            Text(stringResource(R.string.retry_market_registration))
                        }
                    }
                }
            }
        }

        Button(
            onClick = { showConfirmationDialog = true },
            modifier = Modifier.fillMaxWidth(),
            enabled = isLoggedIn && selectedPackageName.isNotBlank() && displayName.isNotBlank() && description.isNotBlank() && !isPublishing
        ) {
            if (isPublishing) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(if (isEditMode) stringResource(R.string.update_to_market) else stringResource(R.string.publish_to_market))
        }

        OutlinedButton(onClick = onNavigateBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.cancel))
        }
    }

    if (publishMessage != null && isPublishing) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.publishing_progress)) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = publishMessage.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                }
            },
            confirmButton = {}
        )
    }

    if (showConfirmationDialog && selectedArtifact != null) {
        AlertDialog(
            onDismissRequest = { showConfirmationDialog = false },
            title = { Text(stringResource(if (isEditMode) R.string.confirm_update else R.string.confirm_publish)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.please_check_submitted_info))
                    Text(stringResource(R.string.name_colon, displayName))
                    Text(stringResource(R.string.description_colon, description))
                    Text(stringResource(R.string.version_colon, version))
                    Text(
                        stringResource(
                            R.string.artifact_type_colon,
                            if (selectedArtifact.type == PublishArtifactType.PACKAGE) stringResource(R.string.artifact_type_package) else stringResource(R.string.artifact_type_script)
                        )
                    )
                    Text(
                        stringResource(
                            R.string.supported_app_versions_colon,
                            minSupportedAppVersion.ifBlank { "-" },
                            maxSupportedAppVersion.ifBlank { "-" }
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmationDialog = false
                        viewModel.requestPublish(
                            packageName = selectedPackageName,
                            displayName = displayName,
                            description = description,
                            version = version,
                            minSupportedAppVersion = minSupportedAppVersion.ifBlank { null },
                            maxSupportedAppVersion = maxSupportedAppVersion.ifBlank { null }
                        )
                    }
                ) { Text(stringResource(if (isEditMode) R.string.confirm_update else R.string.confirm_publish)) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmationDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (requiresForgeInitialization && !showSecondForgeConfirm) {
        AlertDialog(
            onDismissRequest = {
                showSecondForgeConfirm = false
                viewModel.dismissForgeInitializationPrompt()
            },
            title = { Text(stringResource(R.string.create_operit_forge_title)) },
            text = { Text(stringResource(R.string.create_operit_forge_message)) },
            confirmButton = {
                TextButton(onClick = { showSecondForgeConfirm = true }) {
                    Text(stringResource(R.string.continue_action))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSecondForgeConfirm = false
                    viewModel.dismissForgeInitializationPrompt()
                }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (requiresForgeInitialization && showSecondForgeConfirm) {
        AlertDialog(
            onDismissRequest = {
                showSecondForgeConfirm = false
                viewModel.dismissForgeInitializationPrompt()
            },
            title = { Text(stringResource(R.string.confirm_create_public_forge_title)) },
            text = { Text(stringResource(R.string.confirm_create_public_forge_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showSecondForgeConfirm = false
                    viewModel.confirmForgeInitializationAndPublish()
                }) { Text(stringResource(R.string.create_and_publish)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSecondForgeConfirm = false
                    viewModel.dismissForgeInitializationPrompt()
                }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    publishSuccess?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.clearPublishMessages() },
            title = { Text(stringResource(if (isEditMode) R.string.update_success else R.string.publish_success)) },
            text = { Text(text = message) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearPublishMessages()
                    onNavigateBack()
                }) { Text(stringResource(R.string.confirm)) }
            }
        )
    }
}

private fun normalizePackageMatch(value: String): String {
    return value.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
}
