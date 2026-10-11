package com.teppe21.finances.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teppe21.finances.FinancesApp
import com.teppe21.finances.R
import com.teppe21.finances.core.AppReset
import com.teppe21.finances.data.fx.FxSyncState
import com.teppe21.finances.data.local.database.clearSampleData
import com.teppe21.finances.data.local.database.loadSampleData
import com.teppe21.finances.data.local.preferences.PinCheck
import com.teppe21.finances.data.local.preferences.UserPreferences
import com.teppe21.finances.domain.fx.RateTable
import com.teppe21.finances.security.PinPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant

class SettingsViewModel : ViewModel() {

    private val app = FinancesApp.instance
    private val prefsRepo = app.preferencesRepository
    private val fxRepo = app.exchangeRateRepository

    val preferences: StateFlow<UserPreferences?> = prefsRepo.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val rateTable: StateFlow<RateTable> = fxRepo.tableFlow
    val syncState: StateFlow<FxSyncState> = fxRepo.syncStateFlow
    val lastFetch: StateFlow<Instant?> = fxRepo.lastFetchFlow

    /** A string resource to show once. */
    private val _message = MutableStateFlow<Int?>(null)
    val message: StateFlow<Int?> = _message.asStateFlow()

    fun consumeMessage() {
        _message.value = null
    }

    fun setTheme(theme: String) = viewModelScope.launch { prefsRepo.setTheme(theme) }

    fun setCurrency(currency: String) = viewModelScope.launch { prefsRepo.setCurrency(currency) }

    fun setAutoSyncFxRates(enabled: Boolean) = viewModelScope.launch {
        prefsRepo.setAutoSyncFxRates(enabled)
        if (enabled) fxRepo.refreshRates(forceOnline = true)
    }

    fun refreshExchangeRates() = viewModelScope.launch {
        val result = fxRepo.refreshRates(forceOnline = true)
        _message.value = if (result.isSuccess) R.string.settings_msg_rates_updated else R.string.settings_msg_rates_failed
    }

    fun setManualRate(currency: String, rate: BigDecimal) = viewModelScope.launch {
        fxRepo.setManualRate(currency, rate)
    }

    fun clearManualRate(currency: String) = viewModelScope.launch { fxRepo.clearManualRate(currency) }

    // ---- App lock -------------------------------------------------------------------

    /** Returns why the PIN was not saved (a string resource), or null when it was. */
    suspend fun savePin(pin: String, confirmation: String): Int? {
        PinPolicy.validateNewPin(pin)?.let { problem ->
            return when (problem) {
                PinPolicy.Problem.LENGTH -> R.string.pin_error_length
                PinPolicy.Problem.REPEATED_DIGIT -> R.string.pin_error_repeated
                PinPolicy.Problem.SEQUENCE -> R.string.pin_error_sequence
            }
        }
        if (pin != confirmation) return R.string.pin_error_mismatch
        prefsRepo.setPin(pin)
        // The user just proved knowledge of the PIN; do not lock them out of the current session.
        app.appLock.markUnlocked()
        return null
    }

    suspend fun checkPin(pin: String): PinCheck = prefsRepo.verifyPin(pin)

    fun setBiometric(enabled: Boolean) = viewModelScope.launch {
        if (!prefsRepo.setBiometricEnabled(enabled)) _message.value = R.string.settings_msg_set_pin_first
        app.appLock.markUnlocked()
    }

    fun disableLock() = viewModelScope.launch { prefsRepo.disableLock() }

    // ---- Data -----------------------------------------------------------------------

    fun loadDemoData() = viewModelScope.launch {
        loadSampleData(app.database)
        _message.value = R.string.settings_msg_demo_added
    }

    fun clearDemoData() = viewModelScope.launch {
        clearSampleData(app.database)
        _message.value = R.string.settings_msg_demo_removed
    }

    fun deleteFinancialData(onComplete: () -> Unit = {}) = viewModelScope.launch {
        AppReset.deleteFinancialData(app)
        _message.value = R.string.settings_msg_data_deleted
        onComplete()
    }

    fun fullReset() = viewModelScope.launch { AppReset.fullReset(app) }
}
