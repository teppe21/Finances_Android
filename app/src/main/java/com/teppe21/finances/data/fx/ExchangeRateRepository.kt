package com.teppe21.finances.data.fx

import com.teppe21.finances.data.local.dao.ExchangeRateDao
import com.teppe21.finances.data.local.entity.ExchangeRateEntity
import com.teppe21.finances.data.local.preferences.UserPreferencesRepository
import com.teppe21.finances.domain.fx.RateOrigin
import com.teppe21.finances.domain.fx.RateQuote
import com.teppe21.finances.domain.fx.RateTable
import com.teppe21.finances.domain.model.SupportedCurrencies
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.Instant

enum class FxSyncState { IDLE, SYNCING, OK, FAILED, DISABLED }

/**
 * Exchange rates for display-currency conversion.
 *
 * - No built-in default rates: until a fetch succeeds (or the user enters a manual rate)
 *   conversions are unavailable and the UI shows that explicitly.
 * - Manual rates are persisted and override fetched rates for that currency.
 * - Only currency codes are sent to the provider.
 */
class ExchangeRateRepository(
    private val dao: ExchangeRateDao,
    private val provider: ExchangeRateProvider = CompositeRateProvider(),
    private val prefsRepo: UserPreferencesRepository? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val _table = MutableStateFlow(RateTable())
    val tableFlow: StateFlow<RateTable> = _table.asStateFlow()

    private val _syncState = MutableStateFlow(FxSyncState.IDLE)
    val syncStateFlow: StateFlow<FxSyncState> = _syncState.asStateFlow()

    private val _lastFetch = MutableStateFlow<Instant?>(null)
    /** When the app last fetched successfully (not the provider's rate date). */
    val lastFetchFlow: StateFlow<Instant?> = _lastFetch.asStateFlow()

    init {
        scope.launch {
            loadCached()
            refreshRates()
        }
    }

    private suspend fun loadCached() = withContext(Dispatchers.IO) {
        val latest = dao.getByRateDate(LATEST).associate { e ->
            e.targetCurrency.uppercase() to RateQuote(BigDecimal(e.rateText()), e.timestamp, e.provider, RateOrigin.CACHED)
        }
        val manual = dao.getByRateDate(MANUAL).associate { e ->
            e.targetCurrency.uppercase() to RateQuote(BigDecimal(e.rateText()), e.timestamp, e.provider, RateOrigin.MANUAL)
        }
        _table.value = RateTable(latest).withOverrides(manual)
    }

    private suspend fun autoSyncEnabled(): Boolean = try {
        prefsRepo?.preferencesFlow?.first()?.autoSyncFxRates ?: true
    } catch (_: Exception) {
        true
    }

    suspend fun refreshRates(forceOnline: Boolean = false): Result<Unit> = withContext(Dispatchers.IO) {
        if (!forceOnline && !autoSyncEnabled()) {
            _syncState.value = FxSyncState.DISABLED
            return@withContext Result.success(Unit)
        }
        _syncState.value = FxSyncState.SYNCING
        val wanted = SupportedCurrencies.ALL.map { it.code }.toSet()
        val result = provider.getLatestRates(wanted)
        result.fold(
            onSuccess = { fetched ->
                val entities = fetched.rates.map { (code, rate) ->
                    ExchangeRateEntity(
                        baseCurrency = RateTable.BASE,
                        targetCurrency = code,
                        rate = rate.toDouble(),
                        rateDate = LATEST,
                        timestamp = fetched.asOf,
                        provider = fetched.provider
                    )
                }
                dao.clearLatestRates()
                dao.insertRates(entities)
                val fetchedQuotes = fetched.rates.mapValues { (_, rate) ->
                    RateQuote(rate, fetched.asOf, fetched.provider, RateOrigin.FETCHED)
                }
                val manual = _table.value.quotes.filterValues { it.origin == RateOrigin.MANUAL }
                _table.value = RateTable(fetchedQuotes).withOverrides(manual)
                _lastFetch.value = Instant.now()
                _syncState.value = FxSyncState.OK
                Result.success(Unit)
            },
            onFailure = { error ->
                _syncState.value = FxSyncState.FAILED
                Result.failure(error)
            }
        )
    }

    suspend fun setManualRate(targetCurrency: String, rate: BigDecimal): Result<Unit> = withContext(Dispatchers.IO) {
        val code = targetCurrency.uppercase().trim()
        if (code == RateTable.BASE || rate.signum() <= 0) {
            return@withContext Result.failure(IllegalArgumentException("Invalid manual rate"))
        }
        val now = Instant.now()
        dao.insertRates(
            listOf(ExchangeRateEntity(RateTable.BASE, code, rate.toDouble(), MANUAL, now, MANUAL_PROVIDER))
        )
        _table.value = _table.value.withOverrides(mapOf(code to RateQuote(rate, now, MANUAL_PROVIDER, RateOrigin.MANUAL)))
        Result.success(Unit)
    }

    suspend fun clearManualRate(targetCurrency: String) = withContext(Dispatchers.IO) {
        dao.deleteRate(RateTable.BASE, targetCurrency.uppercase(), MANUAL)
        loadCached()
    }

    fun convert(amountMinor: Long, fromCurrency: String, toCurrency: String): Long? =
        _table.value.convert(amountMinor, fromCurrency, toCurrency)

    private fun ExchangeRateEntity.rateText(): String = BigDecimal.valueOf(rate).toPlainString()

    companion object {
        const val LATEST = "latest"
        const val MANUAL = "manual"
        const val MANUAL_PROVIDER = "Manual"
    }
}
