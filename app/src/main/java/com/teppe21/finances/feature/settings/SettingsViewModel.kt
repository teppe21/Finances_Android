package com.teppe21.finances.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teppe21.finances.FinancesApp
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

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

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
        _message.value = if (result.isSuccess) "Exchange rates updated." else "Could not reach the exchange-rate service."
    }

    fun setManualRate(currency: String, rate: BigDecimal) = viewModelScope.launch {
        fxRepo.setManualRate(currency, rate)
    }

    fun clearManualRate(currency: String) = viewModelScope.launch { fxRepo.clearManualRate(currency) }

    // ---- App lock -------------------------------------------------------------------

    /** Returns an error message, or null when the PIN was saved. */
    suspend fun savePin(pin: String, confirmation: String): String? {
        PinPolicy.validateNewPin(pin)?.let { return it }
        if (pin != confirmation) return "The PINs do not match."
        prefsRepo.setPin(pin)
        // The user just proved knowledge of the PIN; do not lock them out of the current session.
        app.appLock.markUnlocked()
        return null
    }

    suspend fun checkPin(pin: String): PinCheck = prefsRepo.verifyPin(pin)

    fun setBiometric(enabled: Boolean) = viewModelScope.launch {
        if (!prefsRepo.setBiometricEnabled(enabled)) _message.value = "Set a PIN first."
        app.appLock.markUnlocked()
    }

    fun disableLock() = viewModelScope.launch { prefsRepo.disableLock() }

    // ---- Data -----------------------------------------------------------------------

    fun loadDemoData() = viewModelScope.launch {
        loadSampleData(app.database)
        _message.value = "Demo data added. Every demo row is marked \"Demo data\"."
    }

    fun clearDemoData() = viewModelScope.launch {
        clearSampleData(app.database)
        _message.value = "Demo data removed."
    }

    fun deleteFinancialData(onComplete: () -> Unit = {}) = viewModelScope.launch {
        AppReset.deleteFinancialData(app)
        _message.value = "Financial data deleted. App lock and settings were kept."
        onComplete()
    }

    fun fullReset() = viewModelScope.launch { AppReset.fullReset(app) }
}
