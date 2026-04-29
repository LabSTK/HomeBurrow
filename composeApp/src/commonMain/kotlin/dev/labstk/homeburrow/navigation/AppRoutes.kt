package dev.labstk.homeburrow.navigation

object AppRoutes {
    const val splash = "splash"
    const val login = "auth/login"
    const val changePassword = "auth/change-password"
    const val groups = "groups"
    const val settings = "settings"
}

object GroupRoutes {
    const val groupIdArg = "groupId"
    const val detail = "groups/{$groupIdArg}"
    const val locations = "groups/{$groupIdArg}/locations"
    const val chat = "groups/{$groupIdArg}/chat"
    const val files = "groups/{$groupIdArg}/files"

    fun detail(groupId: String): String = "groups/$groupId"
    fun locations(groupId: String): String = "groups/$groupId/locations"
    fun chat(groupId: String): String = "groups/$groupId/chat"
    fun files(groupId: String): String = "groups/$groupId/files"
}

