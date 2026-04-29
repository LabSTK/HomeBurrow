package dev.labstk.homeburrow.locations

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.function.Consumer
import kotlin.coroutines.resume

class AndroidDeviceLocationProvider(
    private val context: Context,
) : DeviceLocationProvider {

    override suspend fun getCurrentLocation(): Result<DeviceLocation> {
        val hasFine = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) {
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
        } catch (e: SecurityException) {
            Result.failure(IllegalStateException("Location permission is required."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun requestSingleLocation(
        locationManager: LocationManager,
        provider: String,
    ): Location? = suspendCancellableCoroutine { continuation ->
        val cancellationSignal = CancellationSignal()
        continuation.invokeOnCancellation { cancellationSignal.cancel() }

        locationManager.getCurrentLocation(
            provider,
            cancellationSignal,
            ContextCompat.getMainExecutor(context),
            Consumer { location ->
                if (continuation.isActive) {
                    continuation.resume(location)
                }
            },
        )
    }
}
