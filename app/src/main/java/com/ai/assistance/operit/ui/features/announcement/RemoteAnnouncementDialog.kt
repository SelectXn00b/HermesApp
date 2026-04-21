package com.ai.assistance.operit.ui.features.announcement

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun RemoteAnnouncementDialog(
    title: String,
    body: String,
    acknowledgeText: String,
    onAcknowledge: () -> Unit,
    countdownSeconds: Int = 5
) {
    var remainingSeconds by remember(countdownSeconds) { mutableStateOf(countdownSeconds.coerceAtLeast(0)) }

    LaunchedEffect(countdownSeconds) {
        while (remainingSeconds > 0) {
            delay(1000)
            remainingSeconds--
        }
    }

    val acknowledgeEnabled = remainingSeconds == 0
    val label = if (acknowledgeEnabled) {
        acknowledgeText
    } else {
        "$acknowledgeText (${remainingSeconds}s)"
    }

    val bodyScrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = {},
        title = { Text(text = title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(bodyScrollState)
            ) {
                Text(text = body)
            }
        },
        confirmButton = {
            TextButton(onClick = onAcknowledge, enabled = acknowledgeEnabled) {
                Text(text = label)
            }
        }
    )
}
