package dev.labstk.homeburrow.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.labstk.homeburrow.network.models.UserResponse
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AuthState {
    data object Loading : AuthState()
    data object LoggedOut : AuthState()
    data class LoggedIn(val user: UserResponse) : AuthState()
    data object MustChangePassword : AuthState()
}

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val tokenStorage: TokenStorage,
) : ViewModel() {

    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    /** Email kept in memory so we can auto-login after a password change. */
    private var pendingEmail: String? = null

    init {
        checkExistingSession()
    }

    /** Called on app start — verify any saved access token is still valid. */
    private fun checkExistingSession() {
        if (tokenStorage.getAccessToken() == null) {
            _state.value = AuthState.LoggedOut
            return
        }
        viewModelScope.launch {
            authRepository.me().fold(
                onSuccess = { user -> applyUserState(user) },
                onFailure = {
                    tokenStorage.clear()
                    _state.value = AuthState.LoggedOut
                },
            )
        }
    }

    fun login(email: String, password: String) {
        _state.value = AuthState.Loading
        viewModelScope.launch {
            authRepository.login(email, password).fold(
                onSuccess = { tokens ->
                    tokenStorage.saveTokens(tokens.accessToken, tokens.refreshToken)
                    pendingEmail = email
                    authRepository.me().fold(
                        onSuccess = { user -> applyUserState(user) },
                        onFailure = { err ->
                            tokenStorage.clear()
                            _state.value = AuthState.LoggedOut
                            _errors.tryEmit(err.message ?: "Login failed")
                        },
                    )
                },
                onFailure = { err ->
                    _state.value = AuthState.LoggedOut
                    _errors.tryEmit(err.message ?: "Invalid email or password")
                },
            )
        }
    }

    fun changePassword(oldPassword: String, newPassword: String) {
        viewModelScope.launch {
            authRepository.changePassword(oldPassword, newPassword).fold(
                onSuccess = {
                    // Tokens are now invalid (token_version incremented on server).
                    // Re-login automatically with the new password if we have the email.
                    val email = pendingEmail
                    tokenStorage.clear()
                    if (email != null) {
                        login(email, newPassword)
                    } else {
                        _state.value = AuthState.LoggedOut
                    }
                },
                onFailure = { err ->
                    _errors.tryEmit(err.message ?: "Password change failed")
                },
            )
        }
    }

    fun logout() {
        viewModelScope.launch {
            val refreshToken = tokenStorage.getRefreshToken()
            tokenStorage.clear()
            _state.value = AuthState.LoggedOut
            pendingEmail = null
            if (refreshToken != null) {
                authRepository.logout(refreshToken) // best-effort; ignore result
            }
        }
    }

    private fun applyUserState(user: UserResponse) {
        _state.value = if (user.mustChangePassword) {
            AuthState.MustChangePassword
        } else {
            AuthState.LoggedIn(user)
        }
    }
}
