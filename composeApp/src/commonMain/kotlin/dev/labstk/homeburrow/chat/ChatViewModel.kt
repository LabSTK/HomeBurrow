package dev.labstk.homeburrow.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.labstk.homeburrow.auth.ApiException
import dev.labstk.homeburrow.auth.MustChangePasswordException
import dev.labstk.homeburrow.network.models.GroupMessageResponse
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ChatUiState(
    val groupId: String? = null,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isSending: Boolean = false,
    val messages: List<GroupMessageResponse> = emptyList(),
    val hasMore: Boolean = false,
    val nextBefore: String? = null,
    val error: String? = null,
    val info: String? = null,
)

class ChatViewModel(
    private val repository: ChatRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var pollJob: Job? = null

    fun open(groupId: String) {
        if (_state.value.groupId != groupId) {
            _state.value = ChatUiState(groupId = groupId)
        }
        refresh()
    }

    fun refresh(showLoading: Boolean = true) {
        val groupId = _state.value.groupId ?: return
        if (_state.value.isLoading || _state.value.isLoadingMore) {
            return
        }
        if (showLoading) {
            _state.value = _state.value.copy(isLoading = true, error = null)
        }

        viewModelScope.launch {
            repository.listMessages(groupId = groupId, before = null, limit = PAGE_SIZE).fold(
                onSuccess = { page ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        messages = page.items,
                        hasMore = page.hasMore,
                        nextBefore = page.nextBefore,
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

    fun loadOlderMessages() {
        val current = _state.value
        val groupId = current.groupId ?: return
        val before = current.nextBefore ?: return
        if (current.isLoading || current.isLoadingMore) {
            return
        }

        _state.value = current.copy(isLoadingMore = true, error = null)
        viewModelScope.launch {
            repository.listMessages(groupId = groupId, before = before, limit = PAGE_SIZE).fold(
                onSuccess = { page ->
                    _state.value = _state.value.copy(
                        isLoadingMore = false,
                        messages = (_state.value.messages + page.items).distinctBy { it.id },
                        hasMore = page.hasMore,
                        nextBefore = page.nextBefore,
                        error = null,
                    )
                },
                onFailure = { error ->
                    _state.value = _state.value.copy(
                        isLoadingMore = false,
                        error = toMessage(error),
                    )
                },
            )
        }
    }

    fun sendMessage(body: String) {
        val groupId = _state.value.groupId ?: return
        val trimmedBody = body.trim()
        if (trimmedBody.isEmpty()) {
            _state.value = _state.value.copy(error = "Message body is required.", info = null)
            return
        }

        _state.value = _state.value.copy(isSending = true, error = null, info = null)
        viewModelScope.launch {
            repository.sendMessage(groupId = groupId, body = trimmedBody).fold(
                onSuccess = { message ->
                    _state.value = _state.value.copy(
                        isSending = false,
                        messages = listOf(message) + _state.value.messages.filterNot { it.id == message.id },
                        info = "Message sent.",
                    )
                    refresh(showLoading = false)
                },
                onFailure = { error ->
                    _state.value = _state.value.copy(
                        isSending = false,
                        error = toMessage(error),
                    )
                },
            )
        }
    }

    fun startPolling(intervalMs: Long = POLL_INTERVAL_MS) {
        if (pollJob?.isActive == true) {
            return
        }

        pollJob = viewModelScope.launch {
            while (isActive) {
                delay(intervalMs)
                val current = _state.value
                if (
                    current.groupId != null &&
                    !current.isLoading &&
                    !current.isLoadingMore &&
                    !current.isSending
                ) {
                    refresh(showLoading = false)
                }
            }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    fun clearMessages() {
        _state.value = _state.value.copy(error = null, info = null)
    }

    fun resetState() {
        stopPolling()
        _state.value = ChatUiState()
    }

    override fun onCleared() {
        stopPolling()
        super.onCleared()
    }

    private fun toMessage(error: Throwable): String =
        when (error) {
            is MustChangePasswordException -> "You must change your password before continuing."
            is ApiException -> "${error.code}: ${error.message}"
            else -> error.message ?: "Request failed."
        }

    companion object {
        private const val POLL_INTERVAL_MS = 5_000L
        private const val PAGE_SIZE = 50
    }
}
