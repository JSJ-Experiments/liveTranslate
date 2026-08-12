package com.jadenjsj.livetranslate

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts the API key with a non-exportable Android Keystore key. */
internal class SecretStore(context: Context) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun readApiKey(): String {
        val encoded = preferences.getString(API_KEY, null) ?: return ""
        return runCatching {
            val blob = Base64.decode(encoded, Base64.NO_WRAP)
            val buffer = ByteBuffer.wrap(blob)
            val ivSize = buffer.get().toInt() and 0xff
            require(ivSize in 12..32 && buffer.remaining() > ivSize)
            val iv = ByteArray(ivSize).also(buffer::get)
            val ciphertext = ByteArray(buffer.remaining()).also(buffer::get)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
        }.getOrElse {
            // A restored ciphertext cannot be decrypted if its device-bound key
            // was not restored. Fail closed rather than exposing or crashing.
            preferences.edit().remove(API_KEY).apply()
            ""
        }
    }

    @Synchronized
    fun writeApiKey(value: String) {
        val normalized = value.trim()
        if (normalized.isEmpty()) {
            preferences.edit().remove(API_KEY).apply()
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(normalized.toByteArray(Charsets.UTF_8))
        val blob = ByteBuffer.allocate(1 + cipher.iv.size + ciphertext.size)
            .put(cipher.iv.size.toByte())
            .put(cipher.iv)
            .put(ciphertext)
            .array()
        preferences.edit()
            .putString(API_KEY, Base64.encodeToString(blob, Base64.NO_WRAP))
            .apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val FILE_NAME = "encrypted_credentials"
        const val API_KEY = "api_key_ciphertext"
        const val KEY_ALIAS = "com.jadenjsj.livetranslate.api_key.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
