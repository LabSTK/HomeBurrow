package dev.labstk.homeburrow.locations

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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.labstk.homeburrow.network.models.GroupDetailResponse
import dev.labstk.homeburrow.network.models.GroupMemberResponse

@Composable
fun LocationsScreen(
    group: GroupDetailResponse,
    members: List<GroupMemberResponse>,
    viewModel: LocationsViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(group.id) {
        viewModel.open(group.id)
    }

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
            TextButton(onClick = { viewModel.refresh() }, enabled = !state.isLoading) { Text("Refresh") }
        }

        Text("${group.name} — Locations", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(10.dp))

        Row {
            Button(
                onClick = { viewModel.updateMyLocation() },
                enabled = !state.isLoading,
            ) { Text("Update my location now") }
        }

        Spacer(Modifier.height(8.dp))
        Row {
            Button(
                onClick = { viewModel.setSharingEnabled(true) },
                enabled = !state.isLoading,
            ) { Text("Enable sharing") }

            Spacer(Modifier.width(8.dp))

            Button(
                onClick = { viewModel.setSharingEnabled(false) },
                enabled = !state.isLoading,
            ) { Text("Disable sharing") }
        }

        if (state.isLoading) {
            Spacer(Modifier.height(12.dp))
            CircularProgressIndicator()
        }

        state.error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { viewModel.clearMessages() }) { Text("Dismiss") }
        }

        state.info?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.primary)
            TextButton(onClick = { viewModel.clearMessages() }) { Text("Dismiss") }
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(Modifier.height(10.dp))
        Text("Members", style = MaterialTheme.typography.titleMedium)

        if (members.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("No members in this group.")
            return@Column
        }

        members.forEach { member ->
            val location = state.locationsByUserId[member.userId]
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            ) {
                Text("${member.displayName} (${member.email})")
                Text(
                    text = if (location == null) {
                        "Not sharing or no location yet."
                    } else {
                        "Last known: ${location.recordedAt}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                )

                if (location != null) {
                    Spacer(Modifier.height(6.dp))
                    Button(
                        onClick = {
                            viewModel.openInMaps(
                                latitude = location.latitude,
                                longitude = location.longitude,
                                label = member.displayName,
                            )
                        },
                        enabled = !state.isLoading,
                    ) { Text("Open in maps") }
                }
            }
            HorizontalDivider()
        }
    }
}
