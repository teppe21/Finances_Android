# Architecture

Finances is a single Gradle module (`:app`). The code is split by responsibility rather than by Gradle module, and the parts that hold the important logic avoid Android APIs so they can be tested on the JVM.

## Packages

| Package | Contents | Android dependencies |
| --- | --- | --- |
| `core.money` | Exact amount parsing (`AmountParser`) and minor-unit helpers | none |
| `core.utils` | Currency formatting, cross-rate conversion, text normalization | none |
| `core.net` | Minimal HTTP client interface and `HttpURLConnection` implementation | none |
| `core.ui` | Shared Compose components (transaction row, banners, cards) | Compose |
| `domain.model` | Transactions, accounts, categories, notification status codes | none |
| `domain.usecase` | Ingestion, duplicate detection, account matching, notification processing, statistics, categorization, recurring detection | none |
| `domain.fx` | `RateTable`: exchange rates with their date, source and origin | none |
| `native.notification.parsing` | Supported sources, security/promotional filters, source parsers, `NotificationRecognizer` | none |
| `native.notification` | `NotificationListenerService` and settings intents | Android |
| `native.csv`, `native.receipt` | CSV and receipt text parsers (ML Kit wrapper is separate) | none / ML Kit |
| `security` | PIN verifier and policy (pure), Keystore helpers and lock controller (Android) | partly |
| `stocks.domain` | Market data provider interface, freshness rules, watchlist repository | none |
| `stocks.data` | Finnhub provider, Room-backed watchlist and quote cache | partly |
| `data` | Room database, DAOs, DataStore preferences, repositories, exchange-rate providers | Android |
| `feature.*`, `stocks.ui` | Compose screens and ViewModels | Android |

Dependencies are created once in `FinancesApp` (a manual container). ViewModels read them from `FinancesApp.instance`.

## Transaction ingestion

```
Manual entry ─┐
CSV import ───┤
Receipt scan ─┼─> IngestTransactionUseCase ─> DuplicateDetector ─> categorize ─> TransactionStore.insertIfAbsent
Notification ─┘        (sign + merchant normalization, fingerprints)
```

`TransactionStore.insertIfAbsent` maps to a Room `@Insert(onConflict = IGNORE)`. Rows are never replaced, so a fingerprint collision can no longer delete an existing transaction (the old code used `REPLACE` with a unique fingerprint index).

Fingerprints are source-specific: a random value for manual entries, the receipt id for receipts, a hash of account, date, amount, currency, description and occurrence index for CSV rows, and a hash of package, notification key, post time, amount, currency and direction for notifications.

## Notification pipeline

```
StatusBarNotification
  -> package allowlist (unsupported packages dropped, nothing recorded)
  -> title / text / bigText only
  -> SecurityFilter (codes, logins, approvals)
  -> PromotionalFilter (strong cues)
  -> source parser (positive payment wording + amount, balance lines ignored)
  -> validation (sign matches direction, plausible size)
  -> AccountMatcher (HIGH: user mapping, MEDIUM: single account for the bank, LOW: review)
  -> IngestTransactionUseCase (duplicate detection)
  -> NotificationEvent with a sanitized status code
```

`BankNotificationListenerService` does the Android part. Supported notifications go through a bounded `SerialWorkQueue` (one worker, a 64-item backlog that drops rather than grows, a 20 s timeout per item), so a wallet and a bank notification arriving together are compared deterministically and a stuck item cannot pile up work. An item that fails or times out is recorded as `PROCESSING_ERROR` or `PROCESSING_TIMEOUT` (package and time only, never text) and never touches existing transactions; replays are safe because notification fingerprints are deterministic. After `onListenerDisconnected` the service asks Android to rebind through `RebindPolicy` (at most five requests per ten minutes with exponential backoff, reset by a connection that stays up for a minute). Everything after text extraction is `ProcessNotificationUseCase`, which is unit-tested end to end with an in-memory store. Details: [BANK_INTEGRATION.md](BANK_INTEGRATION.md).

## Currency conversion

`ExchangeRateRepository` publishes a `RateTable` (EUR-based quotes, each with provider, rate date and origin: fetched, cached or manual). Screens convert through the table; `convert` returns `null` when a rate is missing and the statistics use case then excludes and counts the transaction. There are no built-in default rates.

## App lock

`AppLockController` lives in the `Application` so the unlocked state survives rotation but not process death. `MainActivity.onStop` locks unless the app itself just started an external activity (`expectExternalActivity()` before the document picker, gallery, system settings or browser); returning later than five minutes locks anyway. `MainActivity` renders nothing until preferences are loaded, so protected screens never flash before the lock gate.

## Stocks

```
StocksScreen / StockDetailScreen
  -> StocksViewModel (polls only while STARTED)
  -> StockRepository
       ├─ WatchlistStore (Room table `watchlist`)
       ├─ QuoteCache (Room table `stock_quotes`)
       └─ MarketDataProvider -> FinnhubProvider -> HttpClient
```

The stock tables are separate from all financial tables and the repository has no access to transactions or accounts. See [STOCKS.md](STOCKS.md).
