package dev.labstk.homeburrow.locations

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLAuthorizationStatusRestricted
import platform.CoreLocation.kCLLocationAccuracyNearestTenMeters
import platform.Foundation.NSError
import platform.darwin.NSObject
import kotlin.coroutines.resume

@OptIn(ExperimentalForeignApi::class)
class IosDeviceLocationProvider : DeviceLocationProvider {
    private var activeManager: CLLocationManager? = null
    private var activeDelegate: CLLocationManagerDelegateProtocol? = null

    override suspend fun getCurrentLocation(): Result<DeviceLocation> {
        if (!CLLocationManager.locationServicesEnabled()) {
            return Result.failure(IllegalStateException("Enable location services to continue."))
        }

        return suspendCancellableCoroutine { continuation ->
            val manager = CLLocationManager().apply {
                desiredAccuracy = kCLLocationAccuracyNearestTenMeters
            }
            activeManager = manager

            val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {
                private fun finish(result: Result<DeviceLocation>) {
                    if (continuation.isActive) {
                        continuation.resume(result)
                    }
                    cleanup()
                }

                override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
                    val latest = didUpdateLocations.lastOrNull() as? CLLocation
                        ?: return finish(Result.failure(IllegalStateException("Could not obtain your location. Try again.")))
                    val coordinates = latest.coordinate.useContents { latitude to longitude }
                    finish(
                        Result.success(
                            DeviceLocation(
                                latitude = coordinates.first,
                                longitude = coordinates.second,
                                accuracyMeters = latest.horizontalAccuracy,
                            ),
                        ),
                    )
                }

                override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
                    finish(Result.failure(IllegalStateException("Could not obtain your location. Check permissions and try again.")))
                }

                override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
                    when (CLLocationManager.authorizationStatus()) {
                        kCLAuthorizationStatusAuthorizedAlways,
                        kCLAuthorizationStatusAuthorizedWhenInUse,
                            -> manager.requestLocation()

                        kCLAuthorizationStatusDenied,
                        kCLAuthorizationStatusRestricted,
                            -> finish(Result.failure(IllegalStateException("Location permission is required.")))
                    }
                }
            }

            activeDelegate = delegate
            manager.delegate = delegate
            continuation.invokeOnCancellation { cleanup() }

            when (CLLocationManager.authorizationStatus()) {
                kCLAuthorizationStatusAuthorizedAlways,
                kCLAuthorizationStatusAuthorizedWhenInUse,
                    -> manager.requestLocation()

                kCLAuthorizationStatusNotDetermined ->
                    manager.requestWhenInUseAuthorization()

                kCLAuthorizationStatusDenied,
                kCLAuthorizationStatusRestricted,
                    -> {
                        continuation.resume(Result.failure(IllegalStateException("Location permission is required.")))
                        cleanup()
                    }
            }
        }
    }

    private fun cleanup() {
        activeManager?.delegate = null
        activeManager = null
        activeDelegate = null
    }
}
