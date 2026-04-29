package dev.labstk.homeburrow.auth

/**
 * Platform-specific secure token storage.
 *
 * Android: EncryptedSharedPreferences (Jetpack Security Crypto)
 * iOS: Keychain via Security framework
 */
interface TokenStorage {
    fun saveTokens(accessToken: String, refreshToken: String)
    fun getAccessToken(): String?
    fun getRefreshToken(): String?
    fun clear()
}
