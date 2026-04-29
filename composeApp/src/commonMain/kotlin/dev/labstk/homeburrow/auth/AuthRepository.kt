package dev.labstk.homeburrow.auth

import dev.labstk.homeburrow.network.models.AccessTokenResponse
import dev.labstk.homeburrow.network.models.ApiErrorResponse
import dev.labstk.homeburrow.network.models.ChangePasswordRequest
import dev.labstk.homeburrow.network.models.LoginRequest
import dev.labstk.homeburrow.network.models.RefreshRequest
import dev.labstk.homeburrow.network.models.TokenResponse
import dev.labstk.homeburrow.network.models.UserResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess

/** Machine-readable API error codes surfaced to callers. */
class ApiException(val code: String, message: String) : Exception(message)

/** The current user must change their password before using the API. */
class MustChangePasswordException : Exception("Password change required")

class AuthRepository(private val httpClient: HttpClient) {

    suspend fun login(email: String, password: String): Result<TokenResponse> =
        call { httpClient.post("auth/login") { contentType(ContentType.Application.Json); setBody(LoginRequest(email, password)) } }

    suspend fun refresh(refreshToken: String): Result<AccessTokenResponse> =
        call { httpClient.post("auth/refresh") { contentType(ContentType.Application.Json); setBody(RefreshRequest(refreshToken)) } }

    suspend fun logout(refreshToken: String): Result<Unit> =
        callUnit { httpClient.post("auth/logout") { contentType(ContentType.Application.Json); setBody(mapOf("refresh_token" to refreshToken)) } }

    suspend fun me(): Result<UserResponse> =
        call { httpClient.get("auth/me") }

    suspend fun changePassword(oldPassword: String, newPassword: String): Result<Unit> =
        callUnit { httpClient.post("auth/change-password") { contentType(ContentType.Application.Json); setBody(ChangePasswordRequest(oldPassword, newPassword)) } }

    // ── helpers ──────────────────────────────────────────────────────────────

    private suspend inline fun <reified T> call(crossinline block: suspend () -> HttpResponse): Result<T> {
        return try {
            val response = block()
            if (response.status.isSuccess()) {
                Result.success(response.body<T>())
            } else {
                Result.failure(toException(response))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun callUnit(block: suspend () -> HttpResponse): Result<Unit> {
        return try {
            val response = block()
            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(toException(response))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun toException(response: HttpResponse): Exception {
        val error = try { response.body<ApiErrorResponse>() } catch (_: Exception) { null }
        return when {
            response.status == HttpStatusCode.Forbidden && error?.code == "MUST_CHANGE_PASSWORD" ->
                MustChangePasswordException()
            else ->
                ApiException(error?.code ?: "UNKNOWN", error?.message ?: "Request failed: ${response.status}")
        }
    }
}
