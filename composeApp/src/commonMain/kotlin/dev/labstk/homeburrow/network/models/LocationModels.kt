package dev.labstk.homeburrow.network.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PostCurrentLocationRequest(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Double? = null,
)

@Serializable
data class CurrentLocationResponse(
    @SerialName("user_id") val userId: String,
    @SerialName("group_id") val groupId: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Double? = null,
    @SerialName("recorded_at") val recordedAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("sharing_enabled") val sharingEnabled: Boolean,
)

@Serializable
data class SetLocationSharingRequest(
    @SerialName("sharing_enabled") val sharingEnabled: Boolean,
)

@Serializable
data class LocationSharingResponse(
    @SerialName("sharing_enabled") val sharingEnabled: Boolean,
)
