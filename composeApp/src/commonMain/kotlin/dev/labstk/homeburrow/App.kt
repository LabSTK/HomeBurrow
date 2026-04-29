package dev.labstk.homeburrow

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.labstk.homeburrow.auth.AuthState
import dev.labstk.homeburrow.auth.ChangePasswordScreen
import dev.labstk.homeburrow.auth.LoginScreen
import dev.labstk.homeburrow.di.AppModule
import dev.labstk.homeburrow.groups.GroupsScreen

@Composable
fun App(appModule: AppModule) {
    val viewModel = appModule.authViewModel
    val state by viewModel.state.collectAsStateWithLifecycle()

    MaterialTheme {
        when (state) {
            is AuthState.Loading -> { /* splash / spinner — nothing for now */ }
            is AuthState.LoggedOut -> LoginScreen(viewModel)
            is AuthState.MustChangePassword -> ChangePasswordScreen(viewModel)
            is AuthState.LoggedIn -> {
                val user = (state as AuthState.LoggedIn).user
                GroupsScreen(
                    viewModel = appModule.groupsViewModel,
                    currentUserIsAdmin = user.isAdmin,
                    onLogout = {
                        appModule.groupsViewModel.resetState()
                        viewModel.logout()
                    },
                )
            }
        }
    }
}
