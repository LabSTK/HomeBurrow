package dev.labstk.homeburrow.auth

import platform.Foundation.NSUserDefaults

/**
 * iOS token storage backed by NSUserDefaults.
 */
class IosTokenStorage : TokenStorage {
    private val defaults = NSUserDefaults.standardUserDefaults

    companion object {
        private const val KEY_ACCESS = "access_token"
        private const val KEY_REFRESH = "refresh_token"
    }

    override fun saveTokens(accessToken: String, refreshToken: String) {
        defaults.setObject(accessToken, forKey = KEY_ACCESS)
        defaults.setObject(refreshToken, forKey = KEY_REFRESH)
    }

    override fun getAccessToken(): String? = defaults.stringForKey(KEY_ACCESS)
    override fun getRefreshToken(): String? = defaults.stringForKey(KEY_REFRESH)

    override fun clear() {
        defaults.removeObjectForKey(KEY_ACCESS)
        defaults.removeObjectForKey(KEY_REFRESH)
    }
}
