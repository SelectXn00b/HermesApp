package com.ai.assistance.operit.ui.features.packages.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.api.GitHubIssue
import com.ai.assistance.operit.data.skill.SkillRepository
import com.ai.assistance.operit.ui.features.packages.screens.skill.viewmodel.SkillMarketViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillPublishScreen(
    onNavigateBack: () -> Unit,
    editingIssue: GitHubIssue? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val skillRepository = remember { SkillRepository.getInstance(context.applicationContext) }
    val viewModel: SkillMarketViewModel = viewModel(
        factory = SkillMarketViewModel.Factory(context.applicationContext, skillRepository)
    )

    val isEditMode = editingIssue != null

    val initialDraft = if (isEditMode && editingIssue != null) {
        viewModel.parseSkillInfoFromIssue(editingIssue)
    } else {
        viewModel.publishDraft
    }

    var title by remember { mutableStateOf(initialDraft.title) }
    var description by remember { mutableStateOf(initialDraft.description) }
    var repositoryUrl by remember { mutableStateOf(initialDraft.repositoryUrl) }

    var isPublishing by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var showConfirmationDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    if (!isEditMode) {
        LaunchedEffect(title, description, repositoryUrl) {
            viewModel.saveDraft(
                title = title,
                description = description,
                repositoryUrl = repositoryUrl
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    androidx.compose.material3.Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 12.dp, top = 2.dp)
                    )
                    Text(
                        text = if (isEditMode) stringResource(R.string.skill_edit_info_description) else stringResource(R.string.skill_publish_info_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text(stringResource(R.string.skill_name_required)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            singleLine = true,
            isError = title.isBlank()
        )

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text(stringResource(R.string.skill_description_required)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            minLines = 3,
            maxLines = 6,
            isError = description.isBlank()
        )

        OutlinedTextField(
            value = repositoryUrl,
            onValueChange = { repositoryUrl = it },
            label = { Text(stringResource(R.string.skill_repo_address_required)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            singleLine = true,
            placeholder = { Text("https://github.com/username/repo") },
            isError = repositoryUrl.isBlank()
        )

        errorMessage?.let { error ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Text(
                    text = error,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Button(
            onClick = {
                if (title.isBlank() || description.isBlank() || repositoryUrl.isBlank()) {
                    errorMessage = context.getString(R.string.please_fill_all_required_fields)
                    return@Button
                }
                showConfirmationDialog = true
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            enabled = !isPublishing && title.isNotBlank() && description.isNotBlank() && repositoryUrl.isNotBlank()
        ) {
            if (isPublishing) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(end = 8.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Text(stringResource(if (isEditMode) R.string.updating_progress else R.string.publishing_progress))
            } else {
                Text(stringResource(if (isEditMode) R.string.update_plugin else R.string.publish_to_market))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onNavigateBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.cancel))
        }
    }

    if (showConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmationDialog = false },
            title = { Text(stringResource(if (isEditMode) R.string.confirm_update else R.string.confirm_publish)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.please_check_submitted_info))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.name_colon, title), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.description_colon, description), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.repository_colon, repositoryUrl), style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.confirm_skill_git_import_deployment),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmationDialog = false
                        scope.launch {
                            isPublishing = true
                            errorMessage = null

                            try {
                                val success = if (isEditMode && editingIssue != null) {
                                    viewModel.updatePublishedSkill(
                                        issueNumber = editingIssue.number,
                                        title = title,
                                        description = description,
                                        repositoryUrl = repositoryUrl
                                    )
                                } else {
                                    viewModel.publishSkill(
                                        title = title,
                                        description = description,
                                        repositoryUrl = repositoryUrl
                                    )
                                }

                                if (success) {
                                    if (!isEditMode) {
                                        viewModel.clearDraft()
                                    }
                                    showSuccessDialog = true
                                } else {
                                    errorMessage = context.getString(R.string.publish_failed_check_network_repo)
                                }
                            } catch (e: Exception) {
                                errorMessage = context.getString(R.string.publish_failed_with_error, e.message ?: "")
                            } finally {
                                isPublishing = false
                            }
                        }
                    }
                ) {
                    Text(stringResource(if (isEditMode) R.string.confirm_update else R.string.confirm_publish))
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmationDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(if (isEditMode) R.string.update_success else R.string.publish_success)) },
            text = {
                Text(
                    stringResource(if (isEditMode) R.string.skill_update_success_message else R.string.skill_publish_success_message)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSuccessDialog = false
                        onNavigateBack()
                    }
                ) {
                    Text(stringResource(R.string.confirm))
                }
            }
        )
    }
}
