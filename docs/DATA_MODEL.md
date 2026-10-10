# Data model

Room database `finances.db`, schema version **5**. Exported schemas live in `app/schemas/com.teppe21.finances.data.local.database.AppDatabase/`; `3.json` and `4.json` are committed and `5.json` is written by the first build (commit it).

## Money

Every amount is a `Long` with two implied decimals, for every currency: 1,000 HUF is `100000`, 12.50 EUR is `1250`. The sign is the effect on the account: money leaving the account is negative. Display precision (HUF without decimals) is applied only when formatting. Parsing uses `BigDecimal`; no floating point is involved between text and storage.

## Tables

| Table | Purpose |
| --- | --- |
| `transactions` | All transactions. Unique index on `fingerprint`. `pending = 1` rows are excluded from balances and statistics; an empty `accountId` means "not assigned yet". `cardLast4` (v5) supports duplicate detection. |
| `accounts` | Name, institution, type, currency, opening balance. The `currentBalanceMinor` column is legacy; balances are computed from transactions. |
| `categories`, `category_rules` | Categories and keyword rules (`contains`, `exact`, `regex`), higher priority first. |
| `notification_events` | Package, time, source name, status code, linked transaction id. No notification text (removed in v4). Pruned after 30 days. |
| `receipts` | Merchant, date, totals, payment method. Image reference and OCR text are cleared (v5). |
| `budgets` | Monthly limit per category; `currency` added in v5 (null = HUF for older rows). |
| `recurring_rules` | Present in the schema; detection currently runs in memory. |
| `exchange_rates` | EUR-based rates, `rateDate` = `latest` (fetched) or `manual`; `timestamp` = rate date from the provider. |
| `watchlist`, `stock_quotes` | Stock feature only (v5). Never joined with finance tables. |

## Migrations

| From | To | Change |
| --- | --- | --- |
| 1 | 2 | `exchange_rates` table |
| 2 | 3 | indices on `externalId` and `(amountMinor, currency, date)` |
| 3 | 4 | `notification_events` rebuilt without `title` and `text`, `reasonCode` added |
| 4 | 5 | `transactions.cardLast4`, `budgets.currency`, `watchlist` and `stock_quotes` tables; descriptions of notification transactions replaced by the merchant (older versions copied raw notification text there); receipt `rawOcrText` and `imageUri` cleared |

All migrations are additive or data-preserving. There is no destructive fallback: an unknown upgrade path fails instead of silently wiping data. The earlier `fallbackToDestructiveMigrationOnDowngrade` was also removed, so installing an older build over a newer one fails rather than deleting the database.

`MigrationTest` (instrumented) creates a version 3 database with an account, a notification transaction containing raw text, a manual transaction, a receipt with OCR text and a notification event, migrates to 5 with `runMigrationsAndValidate`, and checks the data and the scrubbing. Schemas 1 and 2 were never exported, so 1→5 and 2→5 are not covered by tests.

## Preferences (DataStore)

Theme, display currency, lock type, PIN verifier (`pbkdf2-sha256$...`), failed attempt count and lockout time, automatic rate fetching, notification consent, encrypted Finnhub key, and bank-to-account mappings (`bank_map_<institution>`).
