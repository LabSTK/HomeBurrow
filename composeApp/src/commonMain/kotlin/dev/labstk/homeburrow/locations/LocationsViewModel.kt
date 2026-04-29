package dev.labstk.homeburrow.locations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.labstk.homeburrow.auth.ApiException
import dev.labstk.homeburrow.auth.MustChangePasswordException
import dev.labstk.homeburrow.network.models.CurrentLocationResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LocationsUiState(
    val groupId: String? = null,
    val isLoading: Boolean = false,
    val locationsByUserId: Map<String, CurrentLocationResponse> = emptyMap(),
    val error: String? = null,
    val info: String? = null,
)

class LocationsViewModel(
    private val repository: LocationsRepository,
    private val deviceLocationProvider: DeviceLocationProvider,
    private val mapLauncher: MapLauncher,
) : ViewModel() {

    private val _state = MutableStateFlow(LocationsUiState())
    val state: StateFlow<LocationsUiState> = _state.asStateFlow()

    fun open(groupId: String) {
        if (_state.value.groupId != groupId) {
            _state.value = LocationsUiState(groupId = groupId)
        }
        refresh()
    }

    fun refresh() {
        val groupId = _state.value.groupId ?: return
        _state.value = _state.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            repository.listCurrentLocations(groupId).fold(
                onSuccess = { rows ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        locationsByUserId = rows.associateBy { it.userId },
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

    fun setSharingEnabled(enabled: Boolean) {
        val groupId = _state.value.groupId ?: return
        _state.value = _state.value.copy(isLoading = true, error = null, info = null)
        viewModelScope.launch {
            repository.setLocationSharing(groupId, enabled).fold(
                onSuccess = {
                    _state.value = _state.value.copy(info = if (enabled) "Location sharing enabled." else "Location sharing disabled.")
                    refresh()
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

    fun updateMyLocation() {
        val groupId = _state.value.groupId ?: return
        _state.value = _state.value.copy(isLoading = true, error = null, info = null)
        viewModelScope.launch {
            deviceLocationProvider.getCurrentLocation().fold(
                onSuccess = { location ->
                    repository.postCurrentLocation(
                        groupId = groupId,
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracy = location.accuracyMeters,
                    ).fold(
                        onSuccess = {
                            _state.value = _state.value.copy(info = "Location updated.")
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

    fun openInMaps(latitude: Double, longitude: Double, label: String?) {
        mapLauncher.openLocation(latitude, longitude, label).fold(
            onSuccess = {
                _state.value = _state.value.copy(info = "Opened map app.")
            },
            onFailure = { error ->
                _state.value = _state.value.copy(error = toMessage(error))
            },
        )
    }

    fun clearMessages() {
        _state.value = _state.value.copy(error = null, info = null)
    }

    fun resetState() {
        _state.value = LocationsUiState()
    }

    private fun toMessage(error: Throwable): String =
        when (error) {
            is MustChangePasswordException -> "You must change your password before continuing."
            is ApiException -> "${error.code}: ${error.message}"
            else -> error.message ?: "Request failed."
        }
}
