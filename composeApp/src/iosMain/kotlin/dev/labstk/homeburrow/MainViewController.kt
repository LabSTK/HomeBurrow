package dev.labstk.homeburrow

import androidx.compose.ui.window.ComposeUIViewController
import dev.labstk.homeburrow.auth.IosTokenStorage
import dev.labstk.homeburrow.di.AppModule
import io.ktor.client.engine.darwin.Darwin

private val appModule = AppModule(
    baseUrl = "http://localhost:8000",
    engineFactory = Darwin,
    tokenStorage = IosTokenStorage(),
)

fun MainViewController() = ComposeUIViewController { App(appModule) }