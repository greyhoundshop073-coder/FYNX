package com.fynx.app.ui

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores the FYNX access token encrypted with an Android Keystore key. */
object FynxSecureTokenStore {
    private const val PREFS = "fynx_secure_auth"
    private const val KEY_TOKEN = "access_token"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "fynx_access_token_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_LENGTH = 12

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build())
        return generator.generateKey()
    }

    fun save(context: Context, token: String?) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (token.isNullOrBlank()) {
            prefs.edit().remove(KEY_TOKEN).apply()
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encrypted = cipher.doFinal(token.toByteArray(StandardCharsets.UTF_8))
        val packed = ByteArray(IV_LENGTH + encrypted.size)
        System.arraycopy(cipher.iv, 0, packed, 0, IV_LENGTH)
        System.arraycopy(encrypted, 0, packed, IV_LENGTH, encrypted.size)
        prefs.edit().putString(KEY_TOKEN, Base64.encodeToString(packed, Base64.NO_WRAP)).apply()
    }

    fun load(context: Context): String? {
        val encoded = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TOKEN, null) ?: return null
        return try {
            val packed = Base64.decode(encoded, Base64.NO_WRAP)
            if (packed.size <= IV_LENGTH) return null
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, packed, 0, IV_LENGTH))
            }
            String(cipher.doFinal(packed, IV_LENGTH, packed.size - IV_LENGTH), StandardCharsets.UTF_8)
                .takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }
}
