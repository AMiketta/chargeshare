package app.chargeshare.platform

import app.chargeshare.data.PersistedTokenStore
import app.chargeshare.data.TokenStorage
import app.chargeshare.shared.api.TokenStore
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import cnames.structs.__CFData
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

actual fun createSecureTokenStore(context: PlatformContext): TokenStore =
    PersistedTokenStore(KeychainTokenStorage())

/**
 * Speichert den Token-String als Keychain-Eintrag (`kSecClassGenericPassword`),
 * nur nach dem ersten Entsperren und nur auf diesem Gerät lesbar.
 *
 * Ungetestet auf echter Hardware/Simulator: auf Linux ist nur die Kompilierung möglich.
 */
@OptIn(ExperimentalForeignApi::class)
private class KeychainTokenStorage : TokenStorage {

    override fun read(): String? = memScoped {
        val query = newQuery()
        CFDictionaryAddValue(query, kSecReturnData, kCFBooleanTrue)
        CFDictionaryAddValue(query, kSecMatchLimit, kSecMatchLimitOne)
        val result = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(query, result.ptr)
        CFRelease(query)
        if (status != errSecSuccess) return@memScoped null
        val data: CFDataRef = result.value?.reinterpret<__CFData>() ?: return@memScoped null
        val bytes = CFDataGetBytePtr(data)?.readBytes(CFDataGetLength(data).toInt())
        CFRelease(data)
        bytes?.decodeToString()
    }

    override fun write(value: String?) {
        delete()
        if (value == null) return
        val bytes = value.encodeToByteArray()
        bytes.usePinned { pinned ->
            val data = CFDataCreate(null, pinned.addressOf(0).reinterpret(), bytes.size.convert())
            val query = newQuery()
            CFDictionaryAddValue(query, kSecValueData, data)
            CFDictionaryAddValue(query, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
            SecItemAdd(query, null)
            CFRelease(query)
            CFRelease(data)
        }
    }

    private fun delete() {
        val query = newQuery()
        SecItemDelete(query)
        CFRelease(query)
    }

    private fun newQuery() = CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        .also { query ->
            CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
            addString(query, kSecAttrService, SERVICE)
            addString(query, kSecAttrAccount, ACCOUNT)
        }

    /** Das Dictionary behält den Wert (retain), daher kann der String sofort freigegeben werden. */
    private fun addString(query: CFMutableDictionaryRef?, key: CFStringRef?, text: String) {
        val value = CFStringCreateWithCString(null, text, kCFStringEncodingUTF8)
        CFDictionaryAddValue(query, key, value)
        CFRelease(value)
    }

    private companion object {
        const val SERVICE = "app.chargeshare"
        const val ACCOUNT = "session"
    }
}
