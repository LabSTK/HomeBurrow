package dev.labstk.homeburrow.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.labstk.homeburrow.auth.ApiException
import dev.labstk.homeburrow.auth.MustChangePasswordException
import dev.labstk.homeburrow.network.models.GroupDetailResponse
import dev.labstk.homeburrow.network.models.GroupMemberResponse
import dev.labstk.homeburrow.network.models.GroupSummaryResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class GroupsUiState(
    val isLoading: Boolean = false,
    val groups: List<GroupSummaryResponse> = emptyList(),
    val selectedGroup: GroupDetailResponse? = null,
    val members: List<GroupMemberResponse> = emptyList(),
    val error: String? = null,
    val info: String? = null,
)

class GroupsViewModel(
    private val groupsRepository: GroupsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(GroupsUiState())
    val state: StateFlow<GroupsUiState> = _state.asStateFlow()

    fun loadGroups() {
        _state.value = _state.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            groupsRepository.listGroups().fold(
                onSuccess = { groups ->
                    val selected = _state.value.selectedGroup
                    val selectedStillVisible = selected != null && groups.any { it.id == selected.id }
                    _state.value = _state.value.copy(
                        isLoading = false,
                        groups = groups,
                        selectedGroup = if (selectedStillVisible) selected else null,
                        members = if (selectedStillVisible) _state.value.members else emptyList(),
                        error = null,
                    )
                },
                onFailure = { error ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = toMessage(error),
                    )
                },
            )
        }
    }

    fun createGroup(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            _state.value = _state.value.copy(error = "Group name is required.", info = null)
            return
        }

        _state.value = _state.value.copy(isLoading = true, error = null, info = null)
        viewModelScope.launch {
            groupsRepository.createGroup(trimmed).fold(
                onSuccess = { created ->
                    _state.value = _state.value.copy(info = "Group created.")
                    loadGroups()
                    openGroup(created.id)
                },
                onFailure = { error ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = toMessage(error),
                    )
                },
            )
        }
    }

    fun openGroup(groupId: String) {
        _state.value = _state.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            fetchGroupAndMembers(groupId).fold(
                onSuccess = { (group, members) ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        selectedGroup = group,
                        members = members,
                        error = null,
                    )
                },
                onFailure = { error ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = toMessage(error),
                    )
                },
            )
        }
    }

    fun closeGroup() {
        _state.value = _state.value.copy(selectedGroup = null, members = emptyList(), error = null, info = null)
    }

    fun addMember(userId: String, role: String) {
        val group = _state.value.selectedGroup ?: return
        val trimmedUserId = userId.trim()
        val normalizedRole = role.lowercase()

        if (trimmedUserId.isEmpty()) {
            _state.value = _state.value.copy(error = "User ID is required.", info = null)
            return
        }
        if (normalizedRole != "owner" && normalizedRole != "member") {
            _state.value = _state.value.copy(error = "Role must be owner or member.", info = null)
            return
        }

        _state.value = _state.value.copy(isLoading = true, error = null, info = null)
        viewModelScope.launch {
            groupsRepository.addMember(group.id, trimmedUserId, normalizedRole).fold(
                onSuccess = {
                    _state.value = _state.value.copy(info = "Member added.")
                    refreshSelectedGroup()
                },
                onFailure = { error ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = toMessage(error),
                    )
                },
            )
        }
    }

    fun removeMember(userId: String) {
        val group = _state.value.selectedGroup ?: return
        _state.value = _state.value.copy(isLoading = true, error = null, info = null)
        viewModelScope.launch {
            groupsRepository.removeMember(group.id, userId).fold(
                onSuccess = {
                    _state.value = _state.value.copy(info = "Member removed.")
                    refreshSelectedGroup()
                },
                onFailure = { error ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = toMessage(error),
                    )
                },
            )
        }
    }

    fun clearMessages() {
        _state.value = _state.value.copy(error = null, info = null)
    }

    fun resetState() {
        _state.value = GroupsUiState()
    }

    private suspend fun refreshSelectedGroup() {
        val groupId = _state.value.selectedGroup?.id ?: run {
            _state.value = _state.value.copy(isLoading = false)
            return
        }
        fetchGroupAndMembers(groupId).fold(
            onSuccess = { (group, members) ->
                _state.value = _state.value.copy(
                    isLoading = false,
                    selectedGroup = group,
                    members = members,
                    error = null,
                )
            },
            onFailure = { error ->
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = toMessage(error),
                )
            },
        )
    }

    private suspend fun fetchGroupAndMembers(groupId: String): Result<Pair<GroupDetailResponse, List<GroupMemberResponse>>> {
        return groupsRepository.getGroup(groupId).fold(
            onSuccess = { group ->
                groupsRepository.listMembers(groupId).map { members ->
                    group to members
                }
            },
            onFailure = { error ->
                Result.failure(error)
            },
        )
    }

    private fun toMessage(error: Throwable): String =
        when (error) {
            is MustChangePasswordException -> "You must change your password before continuing."
            is ApiException -> "${error.code}: ${error.message}"
            else -> error.message ?: "Request failed."
        }
}
