package com.teppe21.finances.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android Keystore helpers. Keys are generated inside the Keystore and cannot be exported.
 * Whether they are backed by secure hardware depends on the device; the app does not
 * require or claim it.
 */
object KeystoreSecrets {
    private const val PROVIDER = "AndroidKeyStore"
    private const val PIN_HMAC_ALIAS = "finances_pin_hmac_v1"
    private const val SECRET_AES_ALIAS = "finances_secret_aes_v1"

    private fun keyStore(): KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

    private fun hmacKey(): SecretKey {
        (keyStore().getKey(PIN_HMAC_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, PROVIDER)
        generator.init(KeyGenParameterSpec.Builder(PIN_HMAC_ALIAS, KeyProperties.PURPOSE_SIGN).build())
        return generator.generateKey()
    }

    private fun aesKey(): SecretKey {
        (keyStore().getKey(SECRET_AES_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(SECRET_AES_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    /** PIN pepper backed by the Keystore, or null if the Keystore is unusable on this device. */
    fun pinPepperOrNull(): PinPepper? = runCatching {
        val key = hmacKey()
        PinPepper { input ->
            Mac.getInstance("HmacSHA256").run {
                init(key)
                doFinal(input)
            }
        }
    }.getOrNull()

    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, aesKey())
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val enc = Base64.getEncoder()
        return enc.encodeToString(cipher.iv) + ":" + enc.encodeToString(ct)
    }

    fun decrypt(stored: String): String? = runCatching {
        val (ivB64, ctB64) = stored.split(":", limit = 2)
        val dec = Base64.getDecoder()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, aesKey(), GCMParameterSpec(128, dec.decode(ivB64)))
        String(cipher.doFinal(dec.decode(ctB64)), Charsets.UTF_8)
    }.getOrNull()

    fun deleteAll() {
        runCatching {
            val ks = keyStore()
            ks.deleteEntry(PIN_HMAC_ALIAS)
            ks.deleteEntry(SECRET_AES_ALIAS)
        }
    }
}
