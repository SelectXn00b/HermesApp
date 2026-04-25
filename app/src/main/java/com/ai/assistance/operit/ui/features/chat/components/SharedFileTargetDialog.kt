package com.ai.assistance.operit.ui.features.chat.components

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.ChatHistory
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun SharedFileTargetDialog(
    fileCount: Int,
    recentChats: List<ChatHistory>,
    currentChatId: String?,
    onCreateNewChat: () -> Unit,
    onSelectChat: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val dateTimeFormatter = remember {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(Locale.getDefault())
    }
    val newConversationText = stringResource(R.string.new_conversation)
    val currentConversationBadge = stringResource(R.string.chat_shared_file_target_current_badge)
    val configuration = LocalConfiguration.current
    val maxDialogHeight = configuration.screenHeightDp.dp * 0.8f
    val scrollState = rememberScrollState()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 24.dp)
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .heightIn(max = maxDialogHeight),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = stringResource(R.string.chat_shared_file_target_dialog_title),
                    style = MaterialTheme.typography.headlineSmall
                )

                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(
                            R.string.chat_shared_file_target_dialog_message,
                            fileCount
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )

                    OutlinedCard(
                        onClick = onCreateNewChat,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.new_conversation),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = stringResource(R.string.chat_shared_file_target_new_chat_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (recentChats.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.chat_shared_file_target_recent_section),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        recentChats.forEach { chat ->
                            val title = if (chat.title.isBlank()) newConversationText else chat.title
                            val subtitle = if (chat.id == currentChatId) {
                                "${chat.updatedAt.format(dateTimeFormatter)}  $currentConversationBadge"
                            } else {
                                chat.updatedAt.format(dateTimeFormatter)
                            }

                            OutlinedCard(
                                onClick = { onSelectChat(chat.id) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.chat_shared_file_target_no_recent),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(text = stringResource(R.string.cancel))
                    }
                }
            }
        }
    }
}

@Composable
fun SharedIncomingContentHandler(
    sharedFiles: List<Uri>?,
    sharedLinks: List<String>?,
    chatHistories: List<ChatHistory>,
    currentChatId: String?,
    onHandleSharedFiles: (List<Uri>, String?) -> Unit,
    onHandleSharedLinks: (List<String>) -> Unit,
    onClearSharedFiles: () -> Unit,
    onClearSharedLinks: () -> Unit,
) {
    var pendingSharedFilesForSelection by remember { mutableStateOf<List<Uri>?>(null) }

    LaunchedEffect(sharedFiles) {
        if (!sharedFiles.isNullOrEmpty()) {
            pendingSharedFilesForSelection = sharedFiles
        }
    }

    LaunchedEffect(sharedLinks, pendingSharedFilesForSelection) {
        if (pendingSharedFilesForSelection != null) {
            return@LaunchedEffect
        }
        if (!sharedLinks.isNullOrEmpty()) {
            onHandleSharedLinks(sharedLinks)
            onClearSharedLinks()
        }
    }

    val recentChatTargets = remember(chatHistories) {
        chatHistories
            .sortedWith(
                compareByDescending<ChatHistory> { chat -> chat.updatedAt }
                    .thenByDescending { chat -> chat.createdAt }
            )
            .take(5)
    }

    fun consumePendingSharedFiles(targetChatId: String?) {
        val uris = pendingSharedFilesForSelection ?: return
        pendingSharedFilesForSelection = null
        onClearSharedFiles()
        onHandleSharedFiles(uris, targetChatId)
    }

    pendingSharedFilesForSelection?.let { sharedUris ->
        SharedFileTargetDialog(
            fileCount = sharedUris.size,
            recentChats = recentChatTargets,
            currentChatId = currentChatId,
            onCreateNewChat = { consumePendingSharedFiles(targetChatId = null) },
            onSelectChat = { chatId -> consumePendingSharedFiles(targetChatId = chatId) },
            onDismiss = {
                pendingSharedFilesForSelection = null
                onClearSharedFiles()
            }
        )
    }
}
