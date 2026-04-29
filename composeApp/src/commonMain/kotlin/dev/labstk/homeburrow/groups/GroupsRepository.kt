package dev.labstk.homeburrow.groups

import dev.labstk.homeburrow.auth.ApiException
import dev.labstk.homeburrow.auth.MustChangePasswordException
import dev.labstk.homeburrow.network.models.AddGroupMemberRequest
import dev.labstk.homeburrow.network.models.ApiErrorResponse
import dev.labstk.homeburrow.network.models.CreateGroupRequest
import dev.labstk.homeburrow.network.models.GroupDetailResponse
import dev.labstk.homeburrow.network.models.GroupMemberResponse
import dev.labstk.homeburrow.network.models.GroupSummaryResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess

class GroupsRepository(private val httpClient: HttpClient) {

    suspend fun listGroups(): Result<List<GroupSummaryResponse>> =
        call { httpClient.get("groups") }

    suspend fun createGroup(name: String): Result<GroupDetailResponse> =
        call {
            httpClient.post("groups") {
                contentType(ContentType.Application.Json)
                setBody(CreateGroupRequest(name))
            }
        }

    suspend fun getGroup(groupId: String): Result<GroupDetailResponse> =
        call { httpClient.get("groups/$groupId") }

    suspend fun listMembers(groupId: String): Result<List<GroupMemberResponse>> =
        call { httpClient.get("groups/$groupId/members") }

    suspend fun addMember(groupId: String, userId: String, role: String): Result<GroupMemberResponse> =
        call {
            httpClient.post("groups/$groupId/members") {
                contentType(ContentType.Application.Json)
                setBody(AddGroupMemberRequest(userId = userId, role = role))
            }
        }

    suspend fun removeMember(groupId: String, userId: String): Result<Unit> =
        callUnit { httpClient.delete("groups/$groupId/members/$userId") }

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
}
