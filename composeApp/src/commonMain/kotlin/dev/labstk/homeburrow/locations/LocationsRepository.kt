package dev.labstk.homeburrow.locations

import dev.labstk.homeburrow.auth.ApiException
import dev.labstk.homeburrow.auth.MustChangePasswordException
import dev.labstk.homeburrow.network.models.ApiErrorResponse
import dev.labstk.homeburrow.network.models.CurrentLocationResponse
import dev.labstk.homeburrow.network.models.LocationSharingResponse
import dev.labstk.homeburrow.network.models.PostCurrentLocationRequest
import dev.labstk.homeburrow.network.models.SetLocationSharingRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess

class LocationsRepository(private val httpClient: HttpClient) {

    suspend fun listCurrentLocations(groupId: String): Result<List<CurrentLocationResponse>> =
        call { httpClient.get("groups/$groupId/locations/current") }

    suspend fun postCurrentLocation(
        groupId: String,
        latitude: Double,
        longitude: Double,
        accuracy: Double?,
    ): Result<CurrentLocationResponse> = call {
        httpClient.post("groups/$groupId/locations/current") {
            contentType(ContentType.Application.Json)
            setBody(PostCurrentLocationRequest(latitude = latitude, longitude = longitude, accuracy = accuracy))
        }
    }

    suspend fun setLocationSharing(groupId: String, sharingEnabled: Boolean): Result<LocationSharingResponse> =
        call {
            httpClient.put("groups/$groupId/me/location-sharing") {
                contentType(ContentType.Application.Json)
                setBody(SetLocationSharingRequest(sharingEnabled = sharingEnabled))
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
}
