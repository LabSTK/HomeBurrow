package dev.labstk.homeburrow.network.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreateGroupRequest(
    val name: String,
)

@Serializable
data class GroupSummaryResponse(
    val id: String,
    val name: String,
    @SerialName("created_by") val createdBy: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("my_role") val myRole: String,
)

@Serializable
data class GroupDetailResponse(
    val id: String,
    val name: String,
    @SerialName("created_by") val createdBy: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("my_role") val myRole: String,
)

@Serializable
data class GroupMemberResponse(
    @SerialName("user_id") val userId: String,
    val email: String,
    @SerialName("display_name") val displayName: String,
    val role: String,
    @SerialName("joined_at") val joinedAt: String,
)

@Serializable
data class AddGroupMemberRequest(
    @SerialName("user_id") val userId: String,
    val role: String = "member",
)
