package dev.labstk.homeburrow.locations

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class AndroidDeviceLocationProvider(
    private val context: Context,
) : DeviceLocationProvider {

    override suspend fun getCurrentLocation(): Result<DeviceLocation> {
        if (!context.hasLocationPermission()) {
            return Result.failure(IllegalStateException("Location permission is required."))
        }

        val locationManager = context.getSystemService(LocationManager::class.java)
            ?: return Result.failure(IllegalStateException("Location service is unavailable."))

        val provider = when {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> return Result.failure(IllegalStateException("Enable location services to continue."))
        }

        return try {
            val location = requestSingleLocation(locationManager, provider)
            if (location == null) {
                Result.failure(IllegalStateException("Could not obtain your location. Try again."))
            } else {
                Result.success(
                    DeviceLocation(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracyMeters = location.accuracy.toDouble(),
                    ),
                )
            }
        } catch (_: SecurityException) {
            Result.failure(IllegalStateException("Location permission is required."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun requestSingleLocation(
        locationManager: LocationManager,
        provider: String,
    ): Location? = suspendCancellableCoroutine { continuation ->
        if (!context.hasLocationPermission()) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        val cancellationSignal = CancellationSignal()
        continuation.invokeOnCancellation { cancellationSignal.cancel() }

        requestCurrentLocation(
            locationManager = locationManager,
            provider = provider,
            cancellationSignal = cancellationSignal,
            onLocation = { location ->
                if (continuation.isActive) {
                    continuation.resume(location)
                }
            },
            onDenied = {
                if (continuation.isActive) {
                    continuation.resume(null)
                }
            },
        )
    }

    @SuppressLint("MissingPermission")
    private fun requestCurrentLocation(
        locationManager: LocationManager,
        provider: String,
        cancellationSignal: CancellationSignal,
        onLocation: (Location?) -> Unit,
        onDenied: () -> Unit,
    ) {
        if (!context.hasLocationPermission()) {
            onDenied()
            return
        }

        try {
            locationManager.getCurrentLocation(
                provider,
                cancellationSignal,
                ContextCompat.getMainExecutor(context),
                { location -> onLocation(location) },
            )
        } catch (_: SecurityException) {
            onDenied()
        }
    }
}
