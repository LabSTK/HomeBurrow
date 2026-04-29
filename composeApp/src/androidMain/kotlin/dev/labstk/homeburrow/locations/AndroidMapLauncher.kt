package dev.labstk.homeburrow.locations

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri

class AndroidMapLauncher(
    private val context: Context,
) : MapLauncher {

    override fun openLocation(latitude: Double, longitude: Double, label: String?): Result<Unit> {
        val queryLabel = Uri.encode(label ?: "Location")
        val uri = "geo:$latitude,$longitude?q=$latitude,$longitude($queryLabel)".toUri()
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return if (intent.resolveActivity(context.packageManager) == null) {
            Result.failure(IllegalStateException("No map app is available on this device."))
        } else {
            context.startActivity(intent)
            Result.success(Unit)
        }
    }
}
