package dev.labstk.homeburrow.files

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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.labstk.homeburrow.network.models.GroupFileResponse

@Composable
fun GroupFilesRoute(
    viewModel: FilesViewModel,
    groupId: String,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(groupId) {
        viewModel.open(groupId)
    }

    FilesScreen(
        state = state,
        onBack = onBack,
        onRefresh = { viewModel.refresh() },
        onPickAndUploadFile = { viewModel.pickAndUploadFile() },
        onDownloadFile = { file -> viewModel.downloadFile(file) },
        onDismissMessage = { viewModel.clearMessages() },
    )
}

@Composable
private fun FilesScreen(
    state: FilesUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onPickAndUploadFile: () -> Unit,
    onDownloadFile: (GroupFileResponse) -> Unit,
    onDismissMessage: () -> Unit,
) {
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
            TextButton(onClick = onRefresh, enabled = !state.isLoading) { Text("Refresh") }
        }

        Text("Files", style = MaterialTheme.typography.headlineSmall)
        Text("Group ID: ${state.groupId.orEmpty()}", style = MaterialTheme.typography.bodySmall)

        Spacer(Modifier.height(10.dp))
        Text("Upload a local file", style = MaterialTheme.typography.titleMedium)
        Text("Select a file from your device and upload it to this group.")
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onPickAndUploadFile,
            enabled = !state.isLoading,
        ) {
            Text("Pick file and upload")
        }

        if (state.isLoading) {
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
        Text("Group files", style = MaterialTheme.typography.titleMedium)

        if (state.files.isEmpty() && !state.isLoading) {
            Spacer(Modifier.height(8.dp))
            Text("No files uploaded yet.")
        } else {
            state.files.forEach { file ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                ) {
                    Text(file.originalFilename, style = MaterialTheme.typography.titleSmall)
                    Text("Type: ${file.mimeType}", style = MaterialTheme.typography.bodySmall)
                    Text("Size: ${file.sizeBytes} bytes", style = MaterialTheme.typography.bodySmall)
                    Text("Uploaded: ${file.createdAt}", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(6.dp))
                    Row {
                        Button(
                            onClick = { onDownloadFile(file) },
                            enabled = !state.isLoading,
                        ) {
                            Text("Download & open")
                        }
                    }
                }
                HorizontalDivider()
            }
        }
    }
}
