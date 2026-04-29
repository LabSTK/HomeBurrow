package dev.labstk.homeburrow.di

import dev.labstk.homeburrow.auth.AuthRepository
import dev.labstk.homeburrow.auth.AuthViewModel
import dev.labstk.homeburrow.auth.TokenStorage
import dev.labstk.homeburrow.groups.GroupsRepository
import dev.labstk.homeburrow.groups.GroupsViewModel
import dev.labstk.homeburrow.locations.DeviceLocationProvider
import dev.labstk.homeburrow.locations.LocationsRepository
import dev.labstk.homeburrow.locations.LocationsViewModel
import dev.labstk.homeburrow.locations.MapLauncher
import dev.labstk.homeburrow.network.createHttpClient
import io.ktor.client.engine.HttpClientEngineFactory

/**
 * Central manual dependency injection container.
 *
 * Platform-specific entry points (Android `MainActivity`, iOS `MainViewController`)
 * create a single [AppModule] instance and pass it into the composition tree.
 *
 * @param baseUrl        Base URL of the HomeBurrow API.
 * @param engineFactory  Platform-specific Ktor engine (OkHttp on Android, Darwin on iOS).
 * @param tokenStorage   Platform-specific secure token storage implementation.
 */
class AppModule(
    private val baseUrl: String,
    private val engineFactory: HttpClientEngineFactory<*>,
    val tokenStorage: TokenStorage,
    private val deviceLocationProvider: DeviceLocationProvider,
    private val mapLauncher: MapLauncher,
) {
    val httpClient by lazy {
        createHttpClient(baseUrl, engineFactory, tokenStorage)
    }

    val authRepository by lazy {
        AuthRepository(httpClient)
    }

    val authViewModel by lazy {
        AuthViewModel(authRepository, tokenStorage)
    }

    val groupsRepository by lazy {
        GroupsRepository(httpClient)
    }

    val groupsViewModel by lazy {
        GroupsViewModel(groupsRepository)
    }

    val locationsRepository by lazy {
        LocationsRepository(httpClient)
    }

    val locationsViewModel by lazy {
        LocationsViewModel(locationsRepository, deviceLocationProvider, mapLauncher)
    }
}
