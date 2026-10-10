package com.teppe21.finances.core

import com.teppe21.finances.FinancesApp
import com.teppe21.finances.data.local.database.deleteAllFinancialData
import com.teppe21.finances.data.local.database.resetDatabase
import com.teppe21.finances.security.KeystoreSecrets

object AppReset {

    /**
     * Deletes financial records (transactions, accounts, categories, rules, budgets,
     * receipts, notification diagnostics) and account mappings. Keeps the app lock,
     * preferences, exchange rates and the stock watchlist.
     */
    suspend fun deleteFinancialData(app: FinancesApp) {
        deleteAllFinancialData(app.database)
        app.preferencesRepository.clearBankMappings()
    }

    /** Returns the app to its first-launch state, including lock, API key and watchlist. */
    suspend fun fullReset(app: FinancesApp) {
        resetDatabase(app.database)
        app.preferencesRepository.clearAll()
        KeystoreSecrets.deleteAll()
        app.appLock.lock()
    }
}
