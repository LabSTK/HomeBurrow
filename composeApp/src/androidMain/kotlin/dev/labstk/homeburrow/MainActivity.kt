package dev.labstk.homeburrow

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.labstk.homeburrow.auth.AndroidTokenStorage
import dev.labstk.homeburrow.di.AppModule
import dev.labstk.homeburrow.locations.AndroidDeviceLocationProvider
import dev.labstk.homeburrow.locations.AndroidMapLauncher
import io.ktor.client.engine.okhttp.OkHttp

class MainActivity : ComponentActivity() {

    private val appModule by lazy {
        AppModule(
            baseUrl = BuildConfig.API_BASE_URL,
            engineFactory = OkHttp,
            tokenStorage = AndroidTokenStorage(applicationContext),
            deviceLocationProvider = AndroidDeviceLocationProvider(applicationContext),
            mapLauncher = AndroidMapLauncher(applicationContext),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ensureLocationPermissions()

        setContent {
            App(appModule)
        }
    }

    private fun ensureLocationPermissions() {
        val hasFineLocation = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarseLocation = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasFineLocation || hasCoarseLocation) {
            return
        }
        requestPermissions(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
            LOCATION_PERMISSION_REQUEST_CODE,
        )
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 2001
    }
}
