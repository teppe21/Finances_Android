package com.teppe21.finances.data.local.preferences

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.teppe21.finances.security.KeystoreSecrets
import com.teppe21.finances.security.PinHasher
import com.teppe21.finances.security.PinPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant

private val Context.dataStore by preferencesDataStore(name = "user_preferences")

object LockType {
    const val OFF = "off"
    const val PIN = "pin"
    /** Biometric unlock with the PIN as fallback; a PIN is always required. */
    const val BIOMETRIC = "biometric"
}

data class UserPreferences(
    val theme: String = "system",
    val currency: String = "HUF",
    val appLockType: String = LockType.OFF,
    val hasPin: Boolean = false,
    val autoSyncFxRates: Boolean = true,
    val notificationDisclosureAccepted: Boolean = false,
    val hasStocksApiKey: Boolean = false
) {
    /**
     * Biometric-only locks created by 2.0.0 (no PIN) stay locked; their gate falls back to
     * the device screen lock until the user sets a PIN.
     */
    val lockEnabled: Boolean get() = appLockType == LockType.BIOMETRIC || (appLockType == LockType.PIN && hasPin)
}

sealed interface PinCheck {
    data object Success : PinCheck
    data class Wrong(val attemptsBeforeLockout: Int) : PinCheck
    data class LockedOut(val remaining: Duration) : PinCheck
    data object NoPinSet : PinCheck
}

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val CURRENCY = stringPreferencesKey("currency")
        val APP_LOCK_TYPE = stringPreferencesKey("app_lock_type")
        val PIN_VERIFIER = stringPreferencesKey("pin_verifier_v2")
        // Written by 2.0.0 (salted single-round SHA-256). Migrated on the next successful unlock.
        val LEGACY_PIN_HASH = stringPreferencesKey("pin_hash")
        val LEGACY_PIN_SALT = stringPreferencesKey("pin_salt")
        val FAILED_PIN_ATTEMPTS = intPreferencesKey("failed_pin_attempts")
        val PIN_LOCKED_UNTIL = longPreferencesKey("pin_locked_until")
        val AUTO_SYNC_FX_RATES = booleanPreferencesKey("auto_sync_fx_rates")
        val NOTIFICATION_DISCLOSURE = booleanPreferencesKey("notification_disclosure_v1")
        val STOCKS_API_KEY = stringPreferencesKey("stocks_api_key_enc")
        const val BANK_MAP_PREFIX = "bank_map_"
    }

    private fun Preferences.hasPin(): Boolean =
        !this[Keys.PIN_VERIFIER].isNullOrBlank() ||
            (!this[Keys.LEGACY_PIN_HASH].isNullOrBlank() && !this[Keys.LEGACY_PIN_SALT].isNullOrBlank())

    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            theme = prefs[Keys.THEME] ?: "system",
            currency = prefs[Keys.CURRENCY] ?: "HUF",
            appLockType = prefs[Keys.APP_LOCK_TYPE] ?: LockType.OFF,
            hasPin = prefs.hasPin(),
            autoSyncFxRates = prefs[Keys.AUTO_SYNC_FX_RATES] ?: true,
            notificationDisclosureAccepted = prefs[Keys.NOTIFICATION_DISCLOSURE] ?: false,
            hasStocksApiKey = !prefs[Keys.STOCKS_API_KEY].isNullOrBlank()
        )
    }

    suspend fun setTheme(theme: String) {
        context.dataStore.edit { it[Keys.THEME] = theme }
    }

    suspend fun setCurrency(currency: String) {
        context.dataStore.edit { it[Keys.CURRENCY] = currency }
    }

    suspend fun setAutoSyncFxRates(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_SYNC_FX_RATES] = enabled }
    }

    suspend fun setNotificationDisclosureAccepted(accepted: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFICATION_DISCLOSURE] = accepted }
    }

    // ---- App lock -------------------------------------------------------------------

    /** Sets or changes the PIN and enables PIN lock (keeps biometric mode if already on). */
    suspend fun setPin(pin: String) {
        require(PinPolicy.validateNewPin(pin) == null) { "PIN does not meet policy" }
        val verifier = withContext(Dispatchers.Default) {
            PinHasher.createVerifier(pin.toCharArray(), KeystoreSecrets.pinPepperOrNull())
        }
        context.dataStore.edit {
            it[Keys.PIN_VERIFIER] = verifier
            it.remove(Keys.LEGACY_PIN_HASH)
            it.remove(Keys.LEGACY_PIN_SALT)
            it[Keys.FAILED_PIN_ATTEMPTS] = 0
            it[Keys.PIN_LOCKED_UNTIL] = 0L
            if (it[Keys.APP_LOCK_TYPE] != LockType.BIOMETRIC) it[Keys.APP_LOCK_TYPE] = LockType.PIN
        }
    }

    /** Biometric unlock is only allowed on top of an existing PIN. */
    suspend fun setBiometricEnabled(enabled: Boolean): Boolean {
        var applied = false
        context.dataStore.edit {
            if (it.hasPin()) {
                it[Keys.APP_LOCK_TYPE] = if (enabled) LockType.BIOMETRIC else LockType.PIN
                applied = true
            }
        }
        return applied
    }

    suspend fun disableLock() {
        context.dataStore.edit {
            it[Keys.APP_LOCK_TYPE] = LockType.OFF
            it.remove(Keys.PIN_VERIFIER)
            it.remove(Keys.LEGACY_PIN_HASH)
            it.remove(Keys.LEGACY_PIN_SALT)
            it[Keys.FAILED_PIN_ATTEMPTS] = 0
            it[Keys.PIN_LOCKED_UNTIL] = 0L
        }
    }

    suspend fun verifyPin(input: String, now: Instant = Instant.now()): PinCheck {
        val prefs = context.dataStore.data.first()
        val remaining = PinPolicy.remainingLockout(prefs[Keys.PIN_LOCKED_UNTIL] ?: 0L, now)
        if (!remaining.isZero) return PinCheck.LockedOut(remaining)

        val verifier = prefs[Keys.PIN_VERIFIER]
        val legacyHash = prefs[Keys.LEGACY_PIN_HASH].orEmpty()
        val legacySalt = prefs[Keys.LEGACY_PIN_SALT].orEmpty()
        if (verifier.isNullOrBlank() && (legacyHash.isBlank() || legacySalt.isBlank())) return PinCheck.NoPinSet

        val ok = withContext(Dispatchers.Default) {
            if (!verifier.isNullOrBlank()) {
                val pepper = if (PinHasher.isKeyed(verifier)) KeystoreSecrets.pinPepperOrNull() else null
                PinHasher.verify(input.toCharArray(), verifier, pepper)
            } else {
                PinHasher.verifyLegacySha256(input, legacySalt, legacyHash)
            }
        }

        if (ok) {
            if (verifier.isNullOrBlank()) {
                // Upgrade the 2.0.0 hash to the current verifier.
                val upgraded = withContext(Dispatchers.Default) {
                    PinHasher.createVerifier(input.toCharArray(), KeystoreSecrets.pinPepperOrNull())
                }
                context.dataStore.edit {
                    it[Keys.PIN_VERIFIER] = upgraded
                    it.remove(Keys.LEGACY_PIN_HASH)
                    it.remove(Keys.LEGACY_PIN_SALT)
                }
            }
            resetPinFailures()
            return PinCheck.Success
        }

        var failures = 0
        context.dataStore.edit {
            failures = (it[Keys.FAILED_PIN_ATTEMPTS] ?: 0) + 1
            it[Keys.FAILED_PIN_ATTEMPTS] = failures
            val lockout = PinPolicy.lockoutAfter(failures)
            if (!lockout.isZero) it[Keys.PIN_LOCKED_UNTIL] = now.toEpochMilli() + lockout.toMillis()
        }
        val lockout = PinPolicy.lockoutAfter(failures)
        return if (!lockout.isZero) PinCheck.LockedOut(lockout)
        else PinCheck.Wrong((PinPolicy.FREE_ATTEMPTS - failures).coerceAtLeast(0))
    }

    suspend fun resetPinFailures() {
        context.dataStore.edit {
            it[Keys.FAILED_PIN_ATTEMPTS] = 0
            it[Keys.PIN_LOCKED_UNTIL] = 0L
        }
    }

    // ---- Notification account mappings ---------------------------------------------

    private fun bankKey(institution: String) =
        stringPreferencesKey(Keys.BANK_MAP_PREFIX + institution.lowercase().trim())

    suspend fun getBankMapping(institution: String): String? =
        context.dataStore.data.first()[bankKey(institution)]

    suspend fun setBankMapping(institution: String, accountId: String) {
        context.dataStore.edit { it[bankKey(institution)] = accountId }
    }

    suspend fun clearBankMappings() {
        context.dataStore.edit { prefs ->
            prefs.asMap().keys.filter { it.name.startsWith(Keys.BANK_MAP_PREFIX) }.forEach { prefs.remove(it) }
        }
    }

    // ---- Market data API key ------------------------------------------------------

    /** Encrypts the key with an Android Keystore AES key before storing it. */
    suspend fun setStocksApiKey(key: String) {
        val encrypted = withContext(Dispatchers.Default) { KeystoreSecrets.encrypt(key.trim()) }
        context.dataStore.edit { it[Keys.STOCKS_API_KEY] = encrypted }
    }

    suspend fun getStocksApiKey(): String? {
        val stored = context.dataStore.data.first()[Keys.STOCKS_API_KEY] ?: return null
        return withContext(Dispatchers.Default) { KeystoreSecrets.decrypt(stored) }
    }

    suspend fun clearStocksApiKey() {
        context.dataStore.edit { it.remove(Keys.STOCKS_API_KEY) }
    }

    /** Full application reset: every preference, including the lock and API key. */
    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }
}
