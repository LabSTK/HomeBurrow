package dev.labstk.homeburrow.network

import dev.labstk.homeburrow.auth.TokenStorage
import dev.labstk.homeburrow.network.models.AccessTokenResponse
import dev.labstk.homeburrow.network.models.RefreshRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.plugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.AttributeKey
import kotlinx.serialization.json.Json

private val RetryAfterRefresh = AttributeKey<Boolean>("RetryAfterRefresh")

/**
 * Configures and returns the shared Ktor [HttpClient].
 *
 * - Attaches `Authorization: Bearer <token>` from [tokenStorage] on every request.
 * - On HTTP 401: attempts one token refresh via `POST /auth/refresh`, saves the new
 *   access token, and retries the original request exactly once.
 * - If the refresh fails the tokens are cleared (user must log in again).
 *
 * @param baseUrl  Base URL of the HomeBurrow API (e.g. "http://192.168.1.10:8000").
 * @param engineFactory Platform-specific engine factory injected by [AppModule].
 * @param tokenStorage  Secure token store used to read/write access and refresh tokens.
 */
fun createHttpClient(
    baseUrl: String,
    engineFactory: HttpClientEngineFactory<*>,
    tokenStorage: TokenStorage,
): HttpClient {
    val normalizedBase = baseUrl.trimEnd('/')

    val client = HttpClient(engineFactory) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }

        defaultRequest {
            url("$normalizedBase/")
            header(HttpHeaders.Accept, ContentType.Application.Json)
        }
    }

    // Install auth header + 401 refresh interceptor after client creation so we can
    // reference the client itself inside the intercept block.
    client.plugin(HttpSend).intercept { request ->
        val isRetry = request.attributes.getOrNull(RetryAfterRefresh) == true

        val accessToken = tokenStorage.getAccessToken()
        if (accessToken != null && !request.headers.contains(HttpHeaders.Authorization)) {
            request.headers[HttpHeaders.Authorization] = "Bearer $accessToken"
        }

        val call = execute(request)

        if (call.response.status != HttpStatusCode.Unauthorized || isRetry) {
            return@intercept call
        }

        val refreshToken = tokenStorage.getRefreshToken() ?: return@intercept call

        val refreshRequest = HttpRequestBuilder().apply {
            method = HttpMethod.Post
            url.takeFrom("$normalizedBase/auth/refresh")
            contentType(ContentType.Application.Json)
            setBody(RefreshRequest(refreshToken))
            attributes.put(RetryAfterRefresh, true)
        }

        val refreshCall = execute(refreshRequest)
        if (!refreshCall.response.status.isSuccess()) {
            tokenStorage.clear()
            return@intercept call
        }

        val newAccessToken = refreshCall.response.body<AccessTokenResponse>().accessToken
        tokenStorage.saveTokens(newAccessToken, refreshToken)

        request.headers[HttpHeaders.Authorization] = "Bearer $newAccessToken"
        request.attributes.put(RetryAfterRefresh, true)
        execute(request)
    }

    return client
}


