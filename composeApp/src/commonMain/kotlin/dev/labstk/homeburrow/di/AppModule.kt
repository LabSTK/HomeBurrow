package dev.labstk.homeburrow.di

import io.ktor.client.HttpClient
import dev.labstk.homeburrow.network.createHttpClient

/**
 * Central manual dependency injection container.
 *
 * All shared dependencies are wired here. Platform-specific entry points
 * (Android [MainActivity], iOS [MainViewController]) create a single [AppModule]
 * instance and pass it down the composition tree.
 *
 * @param baseUrl Base URL of the HomeBurrow API. Typically sourced from a
 *                build-config field or a user-configurable setting.
 * @param httpEngineFactory Platform-specific Ktor engine factory (OkHttp on
 *                          Android, Darwin on iOS) — injected by the platform
 *                          entry point.
 */
class AppModule(
    private val baseUrl: String,
    private val httpEngineFactory: io.ktor.client.engine.HttpClientEngineFactory<*>,
) {
    val httpClient: HttpClient by lazy {
        createHttpClient(baseUrl, httpEngineFactory)
    }
}
