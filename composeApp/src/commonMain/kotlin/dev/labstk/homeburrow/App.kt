package dev.labstk.homeburrow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.labstk.homeburrow.auth.AuthState
import dev.labstk.homeburrow.auth.ChangePasswordScreen
import dev.labstk.homeburrow.auth.LoginScreen
import dev.labstk.homeburrow.chat.GroupChatRoute
import dev.labstk.homeburrow.di.AppModule
import dev.labstk.homeburrow.files.GroupFilesRoute
import dev.labstk.homeburrow.groups.GroupDetailRoute
import dev.labstk.homeburrow.groups.GroupLocationsRoute
import dev.labstk.homeburrow.groups.GroupsListRoute
import dev.labstk.homeburrow.navigation.AppRoutes
import dev.labstk.homeburrow.navigation.GroupRoutes

@Composable
fun App(appModule: AppModule) {
    val authViewModel = appModule.authViewModel
    val authState by authViewModel.state.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    MaterialTheme {
        NavHost(
            navController = navController,
            startDestination = AppRoutes.splash,
        ) {
            composable(AppRoutes.splash) { }

            composable(AppRoutes.login) {
                LoginScreen(authViewModel)
            }

            composable(AppRoutes.changePassword) {
                ChangePasswordScreen(authViewModel)
            }

            composable(AppRoutes.groups) {
                val user = (authState as? AuthState.LoggedIn)?.user ?: return@composable
                GroupsListRoute(
                    viewModel = appModule.groupsViewModel,
                    currentUserIsAdmin = user.isAdmin,
                    onOpenGroup = { groupId ->
                        navController.navigate(GroupRoutes.detail(groupId))
                    },
                    onOpenSettings = {
                        navController.navigate(AppRoutes.settings)
                    },
                    onLogout = {
                        appModule.groupsViewModel.resetState()
                        appModule.locationsViewModel.resetState()
                        appModule.chatViewModel.resetState()
                        appModule.filesViewModel.resetState()
                        authViewModel.logout()
                    },
                )
            }

            composable(
                route = GroupRoutes.detail,
                arguments = listOf(navArgument(GroupRoutes.groupIdArg) { type = NavType.StringType }),
            ) { entry ->
                val user = (authState as? AuthState.LoggedIn)?.user ?: return@composable
                val groupId = entry.stringArg(GroupRoutes.groupIdArg) ?: return@composable
                GroupDetailRoute(
                    viewModel = appModule.groupsViewModel,
                    groupId = groupId,
                    currentUserIsAdmin = user.isAdmin,
                    onBack = { navController.popBackStack() },
                    onOpenLocations = { targetGroupId ->
                        navController.navigate(GroupRoutes.locations(targetGroupId))
                    },
                    onOpenChat = { targetGroupId ->
                        navController.navigate(GroupRoutes.chat(targetGroupId))
                    },
                    onOpenFiles = { targetGroupId ->
                        navController.navigate(GroupRoutes.files(targetGroupId))
                    },
                )
            }

            composable(
                route = GroupRoutes.locations,
                arguments = listOf(navArgument(GroupRoutes.groupIdArg) { type = NavType.StringType }),
            ) { entry ->
                val groupId = entry.stringArg(GroupRoutes.groupIdArg) ?: return@composable
                GroupLocationsRoute(
                    viewModel = appModule.groupsViewModel,
                    locationsViewModel = appModule.locationsViewModel,
                    groupId = groupId,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(
                route = GroupRoutes.chat,
                arguments = listOf(navArgument(GroupRoutes.groupIdArg) { type = NavType.StringType }),
            ) { entry ->
                val groupId = entry.stringArg(GroupRoutes.groupIdArg) ?: return@composable
                GroupChatRoute(
                    viewModel = appModule.chatViewModel,
                    groupId = groupId,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(
                route = GroupRoutes.files,
                arguments = listOf(navArgument(GroupRoutes.groupIdArg) { type = NavType.StringType }),
            ) { entry ->
                val groupId = entry.stringArg(GroupRoutes.groupIdArg) ?: return@composable
                GroupFilesRoute(
                    viewModel = appModule.filesViewModel,
                    groupId = groupId,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(AppRoutes.settings) {
                SettingsPlaceholderScreen(
                    onBack = { navController.popBackStack() },
                    onLogout = {
                        appModule.groupsViewModel.resetState()
                        appModule.locationsViewModel.resetState()
                        appModule.chatViewModel.resetState()
                        appModule.filesViewModel.resetState()
                        authViewModel.logout()
                    },
                )
            }
        }

        LaunchedEffect(authState, currentRoute) {
            val authRoutes = setOf(AppRoutes.splash, AppRoutes.login, AppRoutes.changePassword)
            fun navigateToRoot(route: String) {
                if (currentRoute == route) {
                    return
                }
                navController.navigate(route) {
                    popUpTo(AppRoutes.splash) { inclusive = true }
                    launchSingleTop = true
                }
            }

            when (authState) {
                is AuthState.Loading -> Unit
                is AuthState.LoggedOut -> navigateToRoot(AppRoutes.login)
                is AuthState.MustChangePassword -> navigateToRoot(AppRoutes.changePassword)
                is AuthState.LoggedIn -> {
                    if (currentRoute in authRoutes || currentRoute == null) {
                        navigateToRoot(AppRoutes.groups)
                    }
                }
            }
        }
    }
}

private fun NavBackStackEntry.stringArg(name: String): String? {
    val args = arguments ?: return null
    return NavType.StringType[args, name]
}

@Composable
private fun SettingsPlaceholderScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Settings is coming soon.", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onBack) {
            Text("Back")
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onLogout) {
            Text("Logout")
        }
    }
}
