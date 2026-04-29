package dev.labstk.homeburrow.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Configures and returns the shared Ktor [HttpClient].
 *
 * - Content negotiation with kotlinx.serialization JSON (ignores unknown keys
 *   so the client stays compatible with future API additions).
 * - Sets Accept and Content-Type headers globally.
 * - 401 / 403 MUST_CHANGE_PASSWORD handling will be added in Phase 1.
 *
 * @param baseUrl Base URL of the HomeBurrow API (e.g. "http://192.168.1.10:8000").
 * @param engineFactory Platform-specific engine factory injected by [AppModule].
 */
fun createHttpClient(baseUrl: String, engineFactory: io.ktor.client.engine.HttpClientEngineFactory<*>): HttpClient {
    return HttpClient(engineFactory) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }

        defaultRequest {
            url(baseUrl)
            header(HttpHeaders.Accept, ContentType.Application.Json)
        }
    }
}
