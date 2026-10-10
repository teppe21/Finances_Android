package com.teppe21.finances

import com.teppe21.finances.security.PinHasher
import com.teppe21.finances.security.PinPepper
import com.teppe21.finances.security.PinPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class SecurityTest {

    // Test-only stand-in for the Android Keystore HMAC key.
    private fun pepper(keyByte: Int) = PinPepper { input ->
        Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(ByteArray(32) { keyByte.toByte() }, "HmacSHA256"))
            doFinal(input)
        }
    }

    @Test
    fun `verifier accepts the right pin and rejects others`() {
        val verifier = PinHasher.createVerifier("482916".toCharArray(), pepper(1), iterations = 20_000)
        assertTrue(PinHasher.verify("482916".toCharArray(), verifier, pepper(1)))
        assertFalse(PinHasher.verify("482917".toCharArray(), verifier, pepper(1)))
        assertFalse(PinHasher.verify("".toCharArray(), verifier, pepper(1)))
    }

    @Test
    fun `verifier contains no plaintext pin and uses a random salt`() {
        val a = PinHasher.createVerifier("482916".toCharArray(), null, iterations = 20_000)
        val b = PinHasher.createVerifier("482916".toCharArray(), null, iterations = 20_000)
        assertFalse(a.contains("482916"))
        assertNotEquals("same PIN must produce different verifiers", a, b)
        assertTrue(a.startsWith("pbkdf2-sha256$20000$"))
        assertTrue(PinHasher.verify("482916".toCharArray(), a, null))
    }

    @Test
    fun `keyed verifier cannot be checked without the device key`() {
        val verifier = PinHasher.createVerifier("482916".toCharArray(), pepper(1), iterations = 20_000)
        assertTrue(PinHasher.isKeyed(verifier))
        assertFalse(PinHasher.verify("482916".toCharArray(), verifier, null))
        assertFalse(PinHasher.verify("482916".toCharArray(), verifier, pepper(2)))
    }

    @Test
    fun `malformed verifiers never verify`() {
        for (bad in listOf("", "abc", "pbkdf2-sha256\$1\$AA==\$AA==\$n", "sha256\$x\$y\$z\$n", "pbkdf2-sha256\$20000\$!!\$AA==\$n")) {
            assertFalse(PinHasher.verify("1234".toCharArray(), bad, null))
        }
    }

    @Test
    fun `default iteration count is a slow derivation`() {
        assertTrue(PinHasher.ITERATIONS >= 100_000)
    }

    @Test
    fun `legacy sha256 hashes from 2_0_0 still verify for migration`() {
        val salt = "0123456789abcdef"
        val hash = MessageDigest.getInstance("SHA-256").digest("$salt:4829".toByteArray()).joinToString("") { "%02x".format(it) }
        assertTrue(PinHasher.verifyLegacySha256("4829", salt, hash))
        assertFalse(PinHasher.verifyLegacySha256("4828", salt, hash))
        assertFalse(PinHasher.verifyLegacySha256("4829", "", hash))
    }

    @Test
    fun `pin policy`() {
        assertNull(PinPolicy.validateNewPin("4829"))
        assertNull(PinPolicy.validateNewPin("48291736"))
        assertNotNull(PinPolicy.validateNewPin("123"))
        assertNotNull(PinPolicy.validateNewPin("123456789"))
        assertNotNull(PinPolicy.validateNewPin("12a4"))
        assertNotNull(PinPolicy.validateNewPin("0000"))
        assertNotNull(PinPolicy.validateNewPin("1234"))
        assertNotNull(PinPolicy.validateNewPin("9876"))
    }

    @Test
    fun `lockout grows after repeated failures and is capped`() {
        assertEquals(Duration.ZERO, PinPolicy.lockoutAfter(4))
        assertEquals(Duration.ofSeconds(30), PinPolicy.lockoutAfter(5))
        assertEquals(Duration.ofSeconds(60), PinPolicy.lockoutAfter(6))
        assertEquals(Duration.ofSeconds(120), PinPolicy.lockoutAfter(7))
        assertEquals(Duration.ofMinutes(15), PinPolicy.lockoutAfter(50))
        val now = Instant.parse("2026-09-15T10:00:00Z")
        assertEquals(Duration.ofSeconds(10), PinPolicy.remainingLockout(now.toEpochMilli() + 10_000, now))
        assertEquals(Duration.ZERO, PinPolicy.remainingLockout(now.toEpochMilli() - 1, now))
    }
}
