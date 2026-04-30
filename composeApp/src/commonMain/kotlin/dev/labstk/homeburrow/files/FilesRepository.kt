package dev.labstk.homeburrow.files

import dev.labstk.homeburrow.auth.ApiException
import dev.labstk.homeburrow.auth.MustChangePasswordException
import dev.labstk.homeburrow.network.models.ApiErrorResponse
import dev.labstk.homeburrow.network.models.GroupFileResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.decodeURLPart
import io.ktor.http.isSuccess

data class DownloadedGroupFile(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DownloadedGroupFile) return false

        return fileName == other.fileName &&
            mimeType == other.mimeType &&
            bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = fileName.hashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}

class FilesRepository(private val httpClient: HttpClient) {

    suspend fun listFiles(groupId: String): Result<List<GroupFileResponse>> =
        call { httpClient.get("groups/$groupId/files") }

    suspend fun uploadFile(
        groupId: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
    ): Result<GroupFileResponse> = call {
        val safeName = fileName.replace("\"", "_")
        httpClient.submitFormWithBinaryData(
            url = "groups/$groupId/files",
            formData = formData {
                append(
                    key = "upload",
                    value = bytes,
                    headers = Headers.build {
                        append(HttpHeaders.ContentDisposition, "filename=\"$safeName\"")
                        append(HttpHeaders.ContentType, mimeType)
                    },
                )
            },
        )
    }

    suspend fun downloadFile(
        groupId: String,
        fileId: String,
        fallbackFileName: String,
    ): Result<DownloadedGroupFile> {
        return try {
            val response = httpClient.get("groups/$groupId/files/$fileId/download")
            if (!response.status.isSuccess()) {
                return Result.failure(toException(response))
            }

            val payload = response.body<ByteArray>()
            val fileName = response.headers[HttpHeaders.ContentDisposition]
                ?.let(::extractFileNameFromContentDisposition)
                ?: fallbackFileName
            val mimeType = response.headers[HttpHeaders.ContentType] ?: "application/octet-stream"

            Result.success(
                DownloadedGroupFile(
                    fileName = fileName,
                    mimeType = mimeType,
                    bytes = payload,
                ),
            )
        } catch (e: Exception) {
            Result.failure(e)
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

    private fun extractFileNameFromContentDisposition(value: String): String? {
        val filenameStar = Regex("filename\\*=UTF-8''([^;]+)")
            .find(value)
            ?.groupValues
            ?.getOrNull(1)
        if (!filenameStar.isNullOrBlank()) {
            return filenameStar.decodeURLPart()
        }

        return Regex("filename=\"([^\"]+)\"")
            .find(value)
            ?.groupValues
            ?.getOrNull(1)
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
