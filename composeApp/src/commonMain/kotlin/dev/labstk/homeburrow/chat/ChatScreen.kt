package dev.labstk.homeburrow.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.labstk.homeburrow.network.models.GroupMessageResponse

@Composable
fun GroupChatRoute(
    viewModel: ChatViewModel,
    groupId: String,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(groupId) {
        viewModel.open(groupId)
        viewModel.startPolling()
    }

    DisposableEffect(groupId) {
        onDispose {
            viewModel.stopPolling()
        }
    }

    ChatScreen(
        state = state,
        onBack = onBack,
        onRefresh = { viewModel.refresh() },
        onLoadOlderMessages = { viewModel.loadOlderMessages() },
        onSendMessage = { body -> viewModel.sendMessage(body) },
        onDismissMessage = { viewModel.clearMessages() },
    )
}

@Composable
private fun ChatScreen(
    state: ChatUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onLoadOlderMessages: () -> Unit,
    onSendMessage: (String) -> Unit,
    onDismissMessage: () -> Unit,
) {
    var draftMessage by remember(state.groupId) { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("Back") }
            TextButton(onClick = onRefresh, enabled = !state.isLoading && !state.isLoadingMore) {
                Text("Refresh")
            }
        }

        Text("Chat", style = MaterialTheme.typography.headlineSmall)
        Text("Group ID: ${state.groupId.orEmpty()}", style = MaterialTheme.typography.bodySmall)

        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = draftMessage,
            onValueChange = { draftMessage = it },
            label = { Text("Message") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 4,
        )

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    onSendMessage(draftMessage)
                    draftMessage = ""
                },
                enabled = !state.isSending && draftMessage.isNotBlank(),
            ) {
                Text("Send")
            }

            if (state.isSending) {
                Spacer(Modifier.width(8.dp))
                CircularProgressIndicator()
            }
        }

        if (state.isLoading && state.messages.isEmpty()) {
            Spacer(Modifier.height(12.dp))
            CircularProgressIndicator()
        }

        state.error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onDismissMessage) { Text("Dismiss") }
        }

        state.info?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.primary)
            TextButton(onClick = onDismissMessage) { Text("Dismiss") }
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(Modifier.height(10.dp))
        Text("Messages", style = MaterialTheme.typography.titleMedium)

        if (state.messages.isEmpty() && !state.isLoading) {
            Spacer(Modifier.height(8.dp))
            Text("No messages yet.")
        } else {
            state.messages.forEach { message ->
                MessageRow(message = message)
                HorizontalDivider()
            }
        }

        if (state.hasMore) {
            Spacer(Modifier.height(10.dp))
            TextButton(
                onClick = onLoadOlderMessages,
                enabled = !state.isLoadingMore && !state.isLoading,
            ) {
                Text(if (state.isLoadingMore) "Loading…" else "Load older messages")
            }
        }
    }
}

@Composable
private fun MessageRow(message: GroupMessageResponse) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Text(message.senderDisplayName, style = MaterialTheme.typography.titleSmall)
        Text(message.body, style = MaterialTheme.typography.bodyMedium)
        Text("Sent: ${message.createdAt}", style = MaterialTheme.typography.bodySmall)
        message.editedAt?.let {
            Text("Edited: $it", style = MaterialTheme.typography.bodySmall)
        }
    }
}
