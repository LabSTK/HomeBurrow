package dev.labstk.homeburrow.locations

import platform.Foundation.NSURL
import platform.UIKit.UIApplication

class IosMapLauncher : MapLauncher {
    override fun openLocation(latitude: Double, longitude: Double, label: String?): Result<Unit> {
        val url = NSURL.URLWithString(
            buildString {
                append("http://maps.apple.com/?ll=$latitude,$longitude")
                if (!label.isNullOrBlank()) {
                    append("&q=${label.replace(" ", "+")}")
                }
            },
        ) ?: return Result.failure(IllegalStateException("Could not open map URL."))

        val app = UIApplication.sharedApplication
        if (!app.canOpenURL(url)) {
            return Result.failure(IllegalStateException("No map app is available on this device."))
        }
        app.openURL(url)
        return Result.success(Unit)
    }
}
