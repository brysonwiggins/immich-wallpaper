package com.wiggyserver.immichwallpaper.platform

import android.content.Context
import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class) // plain Application: skip the app graph and WorkManager
class KeystoreCredentialStoreTest {
    /** Reversible stand-in for the Keystore (Robolectric has no AndroidKeyStore provider). */
    private class FakeCrypto : KeyCrypto {
        var broken = false
        override fun encrypt(plain: String) = "enc:" + plain.reversed()
        override fun decrypt(blob: String): String {
            check(!broken) { "key invalidated" }
            return blob.removePrefix("enc:").reversed()
        }
    }

    private val context: Context = RuntimeEnvironment.getApplication()
    private val crypto = FakeCrypto()
    private val store = KeystoreCredentialStore(context, crypto)

    @Test
    fun `stores only ciphertext and reads it back`() {
        store.setApiKey("secret-key")
        assertEquals("secret-key", store.apiKey())
        val raw = context.getSharedPreferences("credentials", Context.MODE_PRIVATE).all.values.joinToString()
        assertFalse(raw.contains("secret-key"))
    }

    @Test
    fun `blank clears the key`() {
        store.setApiKey("secret-key")
        store.setApiKey(" ")
        assertNull(store.apiKey())
    }

    @Test
    fun `an unreadable key reads as missing`() {
        store.setApiKey("secret-key")
        crypto.broken = true
        assertNull(KeystoreCredentialStore(context, crypto).apiKey()) // next process start
    }

    @Test
    fun `a new process reads the saved key back`() {
        store.setApiKey("secret-key")
        assertEquals("secret-key", KeystoreCredentialStore(context, crypto).apiKey())
    }
}
