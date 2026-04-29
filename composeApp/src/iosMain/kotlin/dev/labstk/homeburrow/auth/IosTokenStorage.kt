package dev.labstk.homeburrow.auth

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.CoreFoundation.CFBridgingRelease
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.NSData
import platform.Foundation.NSMutableDictionary
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * iOS Keychain-backed token storage.
 *
 * Uses the Security framework's SecItem API via toll-free CF/NS bridging.
 * Each token is stored as a generic password item scoped to [SERVICE].
 */
@OptIn(ExperimentalForeignApi::class)
class IosTokenStorage : TokenStorage {

    companion object {
        private const val SERVICE = "dev.labstk.homeburrow"
        private const val KEY_ACCESS = "access_token"
        private const val KEY_REFRESH = "refresh_token"
    }

    override fun saveTokens(accessToken: String, refreshToken: String) {
        save(KEY_ACCESS, accessToken)
        save(KEY_REFRESH, refreshToken)
    }

    override fun getAccessToken(): String? = load(KEY_ACCESS)
    override fun getRefreshToken(): String? = load(KEY_REFRESH)

    override fun clear() {
        delete(KEY_ACCESS)
        delete(KEY_REFRESH)
    }

    private fun save(account: String, value: String) {
        delete(account) // delete first so we can add fresh
        val valueData = (value as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: return
        val query = baseQuery(account)
        query.setObject(valueData, forKey = kSecValueData!!)
        SecItemAdd(query as CFDictionaryRef, null)
    }

    private fun load(account: String): String? = memScoped {
        val query = baseQuery(account)
        query.setObject(kCFBooleanTrue!!, forKey = kSecReturnData!!)
        query.setObject(kSecMatchLimitOne!!, forKey = kSecMatchLimit!!)
        val result = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(query as CFDictionaryRef, result.ptr)
        if (status != errSecSuccess) return@memScoped null
        @Suppress("UNCHECKED_CAST")
        val data = CFBridgingRelease(result.value) as? NSData ?: return@memScoped null
        NSString.create(data = data, encoding = NSUTF8StringEncoding) as? String
    }

    private fun delete(account: String) {
        SecItemDelete(baseQuery(account) as CFDictionaryRef)
    }

    private fun baseQuery(account: String): NSMutableDictionary {
        val dict = NSMutableDictionary()
        dict.setObject(kSecClassGenericPassword!!, forKey = kSecClass!!)
        dict.setObject(SERVICE, forKey = kSecAttrService!!)
        dict.setObject(account, forKey = kSecAttrAccount!!)
        return dict
    }
}
