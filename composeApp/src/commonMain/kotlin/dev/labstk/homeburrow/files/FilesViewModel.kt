package dev.labstk.homeburrow.files

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.labstk.homeburrow.auth.ApiException
import dev.labstk.homeburrow.auth.MustChangePasswordException
import dev.labstk.homeburrow.network.models.GroupFileResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FilesUiState(
    val groupId: String? = null,
    val isLoading: Boolean = false,
    val files: List<GroupFileResponse> = emptyList(),
    val error: String? = null,
    val info: String? = null,
)

class FilesViewModel(
    private val repository: FilesRepository,
    private val filePicker: LocalFilePicker,
    private val downloadedFileOpener: DownloadedFileOpener,
) : ViewModel() {

    private val _state = MutableStateFlow(FilesUiState())
    val state: StateFlow<FilesUiState> = _state.asStateFlow()

    fun open(groupId: String) {
        if (_state.value.groupId != groupId) {
            _state.value = FilesUiState(groupId = groupId)
        }
        refresh()
    }

    fun refresh() {
        val groupId = _state.value.groupId ?: return
        _state.value = _state.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            repository.listFiles(groupId).fold(
                onSuccess = { files ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        files = files,
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

    fun pickAndUploadFile() {
        val groupId = _state.value.groupId ?: return
        _state.value = _state.value.copy(isLoading = true, error = null, info = null)
        viewModelScope.launch {
            filePicker.pickFile().fold(
                onSuccess = {
                    if (it.bytes.isEmpty()) {
                        _state.value = _state.value.copy(
                            isLoading = false,
                            error = "Selected file is empty.",
                        )
                        return@fold
                    }
                    repository.uploadFile(
                        groupId = groupId,
                        fileName = it.fileName.trim().ifEmpty { "upload.bin" },
                        mimeType = it.mimeType.ifBlank { "application/octet-stream" },
                        bytes = it.bytes,
                    ).fold(
                        onSuccess = {
                            _state.value = _state.value.copy(info = "File uploaded.")
                            refresh()
                        },
                        onFailure = { error ->
                            _state.value = _state.value.copy(
                                isLoading = false,
                                error = toMessage(error),
                            )
                        },
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

    fun downloadFile(file: GroupFileResponse) {
        val groupId = _state.value.groupId ?: return
        _state.value = _state.value.copy(isLoading = true, error = null, info = null)
        viewModelScope.launch {
            repository.downloadFile(
                groupId = groupId,
                fileId = file.id,
                fallbackFileName = file.originalFilename,
            ).fold(
                onSuccess = { downloaded ->
                    downloadedFileOpener.openFile(
                        LocalFileData(
                            fileName = downloaded.fileName,
                            mimeType = downloaded.mimeType,
                            bytes = downloaded.bytes,
                        ),
                    ).fold(
                        onSuccess = {
                            _state.value = _state.value.copy(
                                isLoading = false,
                                info = "Downloaded and opened ${downloaded.fileName}.",
                            )
                        },
                        onFailure = { error ->
                            _state.value = _state.value.copy(
                                isLoading = false,
                                error = toMessage(error),
                            )
                        },
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

    fun clearMessages() {
        _state.value = _state.value.copy(error = null, info = null)
    }

    fun resetState() {
        _state.value = FilesUiState()
    }

    private fun toMessage(error: Throwable): String =
        when (error) {
            is MustChangePasswordException -> "You must change your password before continuing."
            is ApiException -> "${error.code}: ${error.message}"
            else -> error.message ?: "Request failed."
        }
}
