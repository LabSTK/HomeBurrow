package dev.labstk.homeburrow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.labstk.homeburrow.auth.AndroidTokenStorage
import dev.labstk.homeburrow.di.AppModule
import dev.labstk.homeburrow.locations.AndroidDeviceLocationProvider
import dev.labstk.homeburrow.locations.AndroidMapLauncher
import dev.labstk.homeburrow.locations.hasLocationPermission
import dev.labstk.homeburrow.locations.locationPermissions
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
        if (hasLocationPermission()) {
            return
        }
        requestPermissions(
            locationPermissions,
            LOCATION_PERMISSION_REQUEST_CODE,
        )
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 2001
    }
}
