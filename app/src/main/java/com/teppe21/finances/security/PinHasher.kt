package com.teppe21.finances.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Optional second factor bound to the device: an HMAC computed with a non-exportable
 * Android Keystore key. With it, a copied preferences file cannot be brute-forced
 * off the device.
 */
fun interface PinPepper {
    fun apply(input: ByteArray): ByteArray
}

/**
 * PIN verifier: PBKDF2-HMAC-SHA256 with a random 16-byte salt, optionally passed through
 * a device-bound [PinPepper].
 *
 * Stored format: `pbkdf2-sha256$<iterations>$<salt b64>$<hash b64>$<k|n>` where the last
 * field records whether the Keystore pepper was applied.
 */
object PinHasher {
    const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16
    private const val PREFIX = "pbkdf2-sha256"
    private val random = SecureRandom()

    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also { random.nextBytes(it) }

    fun derive(pin: CharArray, salt: ByteArray, iterations: Int = ITERATIONS): ByteArray {
        val spec = PBEKeySpec(pin, salt, iterations, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun createVerifier(pin: CharArray, pepper: PinPepper?, iterations: Int = ITERATIONS): String {
        val salt = newSalt()
        val derived = derive(pin, salt, iterations)
        val final = pepper?.apply(derived) ?: derived
        val enc = Base64.getEncoder()
        return listOf(PREFIX, iterations.toString(), enc.encodeToString(salt), enc.encodeToString(final), if (pepper != null) "k" else "n")
            .joinToString("$")
    }

    /** Constant-time comparison. Returns false for malformed verifiers. */
    fun verify(pin: CharArray, verifier: String, pepper: PinPepper?): Boolean {
        val parts = verifier.split("$")
        if (parts.size != 5 || parts[0] != PREFIX) return false
        val iterations = parts[1].toIntOrNull()?.takeIf { it in 10_000..10_000_000 } ?: return false
        val dec = Base64.getDecoder()
        val salt = runCatching { dec.decode(parts[2]) }.getOrNull() ?: return false
        val expected = runCatching { dec.decode(parts[3]) }.getOrNull() ?: return false
        val keyed = parts[4] == "k"
        if (keyed && pepper == null) return false
        val derived = derive(pin, salt, iterations)
        val actual = if (keyed) pepper!!.apply(derived) else derived
        return MessageDigest.isEqual(actual, expected)
    }

    fun isKeyed(verifier: String): Boolean = verifier.split("$").getOrNull(4) == "k"

    /**
     * Verifies the salted single-round SHA-256 hash written by version 2.0.0, so existing
     * users can unlock once and be migrated to [createVerifier].
     */
    fun verifyLegacySha256(pin: String, salt: String, hexHash: String): Boolean {
        if (salt.isBlank() || hexHash.isBlank()) return false
        val digest = MessageDigest.getInstance("SHA-256").digest("$salt:$pin".toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it) }
        return MessageDigest.isEqual(hex.toByteArray(), hexHash.lowercase().toByteArray())
    }
}
