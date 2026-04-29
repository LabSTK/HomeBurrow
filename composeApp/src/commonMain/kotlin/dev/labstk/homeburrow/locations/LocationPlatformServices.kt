package dev.labstk.homeburrow.locations

data class DeviceLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double?,
)

interface DeviceLocationProvider {
    suspend fun getCurrentLocation(): Result<DeviceLocation>
}

interface MapLauncher {
    fun openLocation(latitude: Double, longitude: Double, label: String? = null): Result<Unit>
}
