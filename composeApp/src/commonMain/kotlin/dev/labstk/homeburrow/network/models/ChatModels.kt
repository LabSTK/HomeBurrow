package dev.labstk.homeburrow.network.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SendGroupMessageRequest(
    val body: String,
)

@Serializable
data class GroupMessageResponse(
    val id: String,
    @SerialName("group_id") val groupId: String,
    @SerialName("sender_user_id") val senderUserId: String,
    @SerialName("sender_display_name") val senderDisplayName: String,
    val body: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("edited_at") val editedAt: String? = null,
)

@Serializable
data class ListMessagesResponse(
    val items: List<GroupMessageResponse>,
    @SerialName("has_more") val hasMore: Boolean,
    @SerialName("next_before") val nextBefore: String? = null,
)
