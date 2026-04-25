package com.ai.assistance.operit.ui.features.chat.components.style.input.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.config.SystemToolPrompts
import com.ai.assistance.operit.util.LocaleUtils

@Composable
fun ToolPromptManagerDialog(
    visible: Boolean,
    toolPromptVisibility: Map<String, Boolean>,
    onSaveToolPromptVisibilityMap: (Map<String, Boolean>) -> Unit,
    onDismissRequest: () -> Unit,
    onManagePackagesClick: () -> Unit,
) {
    if (!visible) return

    val context = LocalContext.current
    val useEnglish = remember(context) {
        LocaleUtils.getCurrentLanguage(context).lowercase().startsWith("en")
    }
    val manageableTools = remember(useEnglish) {
        SystemToolPrompts.getManageableToolPrompts(useEnglish)
    }
    var localToolPromptVisibility by remember(toolPromptVisibility) {
        mutableStateOf(toolPromptVisibility)
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.common_close))
            }
        },
        title = {
            Text(
                text = stringResource(R.string.tool_prompt_manager_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
            ) {
                manageableTools.forEachIndexed { index, tool ->
                    val isVisible = localToolPromptVisibility[tool.name] ?: true
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .toggleable(
                                    value = isVisible,
                                    onValueChange = { checked ->
                                        val updated = localToolPromptVisibility + (tool.name to checked)
                                        localToolPromptVisibility = updated
                                        onSaveToolPromptVisibilityMap(updated)
                                    },
                                    role = Role.Switch,
                                )
                                .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tool.name,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = tool.categoryName,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = isVisible,
                            onCheckedChange = null,
                            modifier = Modifier.scale(0.8f),
                        )
                    }
                    if (index < manageableTools.lastIndex) {
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                        )
                    }
                }

                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                            .clickable(onClick = onManagePackagesClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.manage_packages),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp,
                    )
                }
            }
        },
    )
}
