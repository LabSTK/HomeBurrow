package dev.labstk.homeburrow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.labstk.homeburrow.auth.AndroidTokenStorage
import dev.labstk.homeburrow.di.AppModule
import io.ktor.client.engine.okhttp.OkHttp

class MainActivity : ComponentActivity() {

    private val appModule by lazy {
        AppModule(
            baseUrl = BuildConfig.API_BASE_URL,
            engineFactory = OkHttp,
            tokenStorage = AndroidTokenStorage(applicationContext),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            App(appModule)
        }
    }
}