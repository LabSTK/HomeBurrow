package dev.labstk.homeburrow.groups

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.labstk.homeburrow.locations.LocationsScreen
import dev.labstk.homeburrow.locations.LocationsViewModel
import dev.labstk.homeburrow.network.models.GroupDetailResponse
import dev.labstk.homeburrow.network.models.GroupMemberResponse
import dev.labstk.homeburrow.network.models.GroupSummaryResponse

@Composable
fun GroupsScreen(
    viewModel: GroupsViewModel,
    locationsViewModel: LocationsViewModel,
    currentUserIsAdmin: Boolean,
    onLogout: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.loadGroups()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        when (val selected = state.selectedGroup) {
            null -> GroupListContent(
                groups = state.groups,
                isLoading = state.isLoading,
                error = state.error,
                info = state.info,
                canCreateGroups = currentUserIsAdmin,
                onRefresh = { viewModel.loadGroups() },
                onCreateGroup = { viewModel.createGroup(it) },
                onOpenGroup = { viewModel.openGroup(it) },
                onDismissMessage = { viewModel.clearMessages() },
                onLogout = {
                    viewModel.resetState()
                    onLogout()
                },
            )

            else -> if (state.isLocationsOpen) {
                LocationsScreen(
                    group = selected,
                    members = state.members,
                    viewModel = locationsViewModel,
                    onBack = { viewModel.closeLocations() },
                )
            } else {
                GroupDetailContent(
                    group = selected,
                    members = state.members,
                    isLoading = state.isLoading,
                    error = state.error,
                    info = state.info,
                    canManageMembers = currentUserIsAdmin || selected.myRole == "owner",
                    onBack = { viewModel.closeGroup() },
                    onRefresh = { viewModel.openGroup(selected.id) },
                    onOpenLocations = { viewModel.openLocations() },
                    onAddMember = { userId, role -> viewModel.addMember(userId, role) },
                    onRemoveMember = { userId -> viewModel.removeMember(userId) },
                    onDismissMessage = { viewModel.clearMessages() },
                )
            }
        }
    }
}

@Composable
private fun GroupListContent(
    groups: List<GroupSummaryResponse>,
    isLoading: Boolean,
    error: String?,
    info: String?,
    canCreateGroups: Boolean,
    onRefresh: () -> Unit,
    onCreateGroup: (String) -> Unit,
    onOpenGroup: (String) -> Unit,
    onDismissMessage: () -> Unit,
    onLogout: () -> Unit,
) {
    var newGroupName by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("My Groups", style = MaterialTheme.typography.headlineSmall)
            Row {
                TextButton(onClick = onRefresh, enabled = !isLoading) {
                    Text("Refresh")
                }
                TextButton(onClick = onLogout) {
                    Text("Logout")
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (isLoading) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
        }

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = onDismissMessage) { Text("Dismiss") }
        }

        info?.let {
            Text(it, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = onDismissMessage) { Text("Dismiss") }
        }

        if (canCreateGroups) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = newGroupName,
                onValueChange = { newGroupName = it },
                label = { Text("New group name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    onCreateGroup(newGroupName)
                    newGroupName = ""
                },
                enabled = !isLoading && newGroupName.isNotBlank(),
            ) {
                Text("Create group")
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
        }

        Spacer(Modifier.height(12.dp))
        if (groups.isEmpty() && !isLoading) {
            Text("No groups yet.")
        } else {
            groups.forEach { group ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                ) {
                    Text(group.name, style = MaterialTheme.typography.titleMedium)
                    Text("My role: ${group.myRole}", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                    Button(onClick = { onOpenGroup(group.id) }, enabled = !isLoading) {
                        Text("Open")
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun GroupDetailContent(
    group: GroupDetailResponse,
    members: List<GroupMemberResponse>,
    isLoading: Boolean,
    error: String?,
    info: String?,
    canManageMembers: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenLocations: () -> Unit,
    onAddMember: (String, String) -> Unit,
    onRemoveMember: (String) -> Unit,
    onDismissMessage: () -> Unit,
) {
    var memberUserId by remember(group.id) { mutableStateOf("") }
    var addAsOwner by remember(group.id) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text("Back")
            }
            TextButton(onClick = onRefresh, enabled = !isLoading) {
                Text("Refresh")
            }
        }

        Text(group.name, style = MaterialTheme.typography.headlineSmall)
        Text("My role: ${group.myRole}", style = MaterialTheme.typography.bodyMedium)

        Spacer(Modifier.height(8.dp))
        Button(onClick = onOpenLocations, enabled = !isLoading) {
            Text("Locations")
        }

        Spacer(Modifier.height(10.dp))
        if (isLoading) {
            CircularProgressIndicator()
            Spacer(Modifier.height(10.dp))
        }

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = onDismissMessage) { Text("Dismiss") }
        }

        info?.let {
            Text(it, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = onDismissMessage) { Text("Dismiss") }
        }

        if (canManageMembers) {
            Spacer(Modifier.height(8.dp))
            Text("Add member", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = memberUserId,
                onValueChange = { memberUserId = it },
                label = { Text("User ID (UUID)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { addAsOwner = false },
                    enabled = !isLoading && addAsOwner,
                ) {
                    Text("Member")
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { addAsOwner = true },
                    enabled = !isLoading && !addAsOwner,
                ) {
                    Text("Owner")
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    onAddMember(memberUserId, if (addAsOwner) "owner" else "member")
                    memberUserId = ""
                },
                enabled = !isLoading && memberUserId.isNotBlank(),
            ) {
                Text("Add to group")
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
        }

        Spacer(Modifier.height(10.dp))
        Text("Members", style = MaterialTheme.typography.titleMedium)

        if (members.isEmpty() && !isLoading) {
            Spacer(Modifier.height(8.dp))
            Text("No members in this group.")
        } else {
            members.forEach { member ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                ) {
                    Text("${member.displayName} (${member.email})")
                    Text("Role: ${member.role}", style = MaterialTheme.typography.bodySmall)
                    Text(member.userId, style = MaterialTheme.typography.bodySmall)
                    if (canManageMembers) {
                        Spacer(Modifier.height(6.dp))
                        Button(
                            onClick = { onRemoveMember(member.userId) },
                            enabled = !isLoading,
                        ) {
                            Text("Remove")
                        }
                    }
                }
                HorizontalDivider()
            }
        }
    }
}
