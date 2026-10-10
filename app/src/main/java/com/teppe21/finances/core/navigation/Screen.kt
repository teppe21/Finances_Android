package com.teppe21.finances.core.navigation

sealed class Screen(val route: String) {
    data object Dashboard : Screen("dashboard")
    data object Transactions : Screen("transactions?add={add}") {
        const val BASE = "transactions"
        fun withAddDialog() = "transactions?add=true"
    }
    data object Analytics : Screen("analytics")
    data object Accounts : Screen("accounts")
    data object More : Screen("more")
    data object ScanReceipt : Screen("scan_receipt")
    data object NotificationAutomation : Screen("notification_automation")
    data object Review : Screen("review")
    data object CsvImport : Screen("csv_import")
    data object Budgets : Screen("budgets")
    data object Recurring : Screen("recurring")
    data object Categories : Screen("categories")
    data object Settings : Screen("settings")
    data object Stocks : Screen("stocks")
    data object StockDetail : Screen("stock/{symbol}") {
        fun of(symbol: String) = "stock/" + java.net.URLEncoder.encode(symbol, "UTF-8")
    }
    data object StocksSetup : Screen("stocks_setup")
    data object PrivacyPolicy : Screen("privacy_policy")
}
