package dev.labstk.homeburrow

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform