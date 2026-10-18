package com.wiggyserver.immichwallpaper.platform

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import com.wiggyserver.immichwallpaper.data.CredentialStore
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts/decrypts the API key. */
interface KeyCrypto {
    fun encrypt(plain: String): String
    fun decrypt(blob: String): String
}

/** AES-256-GCM with a non-exportable key in the Android Keystore (no deprecated security-crypto library). */
class KeystoreCrypto(private val alias: String = "immichwall-api-key") : KeyCrypto {
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    override fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val out = cipher.iv + cipher.doFinal(plain.toByteArray())
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    override fun decrypt(blob: String): String {
        val bytes = Base64.decode(blob, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, IV_BYTES))
        }
        return cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES).decodeToString()
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
    }
}

/** API key stored only as Keystore ciphertext in private prefs. Unreadable (e.g. key reset) → null. */
class KeystoreCredentialStore(context: Context, private val crypto: KeyCrypto = KeystoreCrypto()) : CredentialStore {
    private val prefs = context.getSharedPreferences("credentials", Context.MODE_PRIVATE)

    // Decrypted once per process: every thumbnail request needs the key.
    @Volatile private var cached: String? = null
    @Volatile private var loaded = false

    override fun apiKey(): String? {
        if (!loaded) {
            cached = prefs.getString(KEY, null)?.let { runCatching { crypto.decrypt(it) }.getOrNull() }
            loaded = true
        }
        return cached
    }

    @Synchronized
    override fun setApiKey(key: String?) {
        prefs.edit { if (key.isNullOrBlank()) remove(KEY) else putString(KEY, crypto.encrypt(key)) }
        cached = key?.takeIf { it.isNotBlank() }
        loaded = true
    }

    private companion object {
        const val KEY = "apiKey"
    }
}
