package dev.labstk.homeburrow.network.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GroupFileResponse(
    val id: String,
    @SerialName("group_id") val groupId: String,
    @SerialName("uploader_user_id") val uploaderUserId: String,
    @SerialName("original_filename") val originalFilename: String,
    @SerialName("mime_type") val mimeType: String,
    @SerialName("size_bytes") val sizeBytes: Int,
    @SerialName("created_at") val createdAt: String,
)
