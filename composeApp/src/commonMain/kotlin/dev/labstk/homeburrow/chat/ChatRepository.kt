package dev.labstk.homeburrow.chat

import dev.labstk.homeburrow.auth.ApiException
import dev.labstk.homeburrow.auth.MustChangePasswordException
import dev.labstk.homeburrow.network.models.ApiErrorResponse
import dev.labstk.homeburrow.network.models.GroupMessageResponse
import dev.labstk.homeburrow.network.models.ListMessagesResponse
import dev.labstk.homeburrow.network.models.SendGroupMessageRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess

class ChatRepository(private val httpClient: HttpClient) {

    suspend fun listMessages(
        groupId: String,
        before: String? = null,
        limit: Int = DEFAULT_PAGE_SIZE,
    ): Result<ListMessagesResponse> = call {
        httpClient.get("groups/$groupId/messages") {
            parameter("limit", limit.coerceIn(1, MAX_PAGE_SIZE))
            before?.let { parameter("before", it) }
        }
    }

    suspend fun sendMessage(
        groupId: String,
        body: String,
    ): Result<GroupMessageResponse> = call {
        httpClient.post("groups/$groupId/messages") {
            contentType(ContentType.Application.Json)
            setBody(SendGroupMessageRequest(body = body))
        }
    }

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

    private suspend fun toException(response: HttpResponse): Exception {
        val error = try {
            response.body<ApiErrorResponse>()
        } catch (_: Exception) {
            null
        }

        return when {
            response.status == HttpStatusCode.Forbidden && error?.code == "MUST_CHANGE_PASSWORD" ->
                MustChangePasswordException()

            else ->
                ApiException(
                    error?.code ?: "UNKNOWN",
                    error?.message ?: "Request failed: ${response.status}",
                )
        }
    }

    companion object {
        private const val DEFAULT_PAGE_SIZE = 50
        private const val MAX_PAGE_SIZE = 100
    }
}
