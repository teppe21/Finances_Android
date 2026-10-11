# Data model

Room database `finances.db`, schema version **6**. Exported schemas live in `app/schemas/com.teppe21.finances.data.local.database.AppDatabase/` (`3.json` to `6.json`, all committed).

## Money

Every amount is a `Long` with two implied decimals, for every currency: 1,000 HUF is `100000`, 12.50 EUR is `1250`. The sign is the effect on the account: money leaving the account is negative. Display precision (HUF without decimals) is applied only when formatting. Parsing uses `BigDecimal`; no floating point is involved between text and storage.

## Tables

| Table | Purpose |
| --- | --- |
| `transactions` | All transactions. Unique index on `fingerprint`. `pending = 1` rows are excluded from balances and statistics; an empty `accountId` means "not assigned yet". `cardLast4` (v5) supports duplicate detection. `direction` is the kind: `INCOME`, `EXPENSE`, `REFUND`, `TRANSFER` (own accounts, exchanges, cash) or `PERSONAL_TRANSFER` (other people, v6); only income, refunds and expenses count in statistics. `transferId` links the two legs of an own-account move. `categorySetByUser` (v6) marks a category the user chose; rules never change it. |
| `accounts` | Name, institution, type, currency, opening balance. The `currentBalanceMinor` column is legacy; balances are computed from transactions. |
| `categories`, `category_rules` | Categories and keyword rules (`contains`, `word`, `exact`, `regex`), higher priority first: user rules 50, built-in 4-30. Built-in definitions: `domain/model/BuiltIns.kt`; matching: [IMPORT_FORMATS.md](IMPORT_FORMATS.md#categorization). |
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
| 5 | 6 | `transactions.categorySetByUser` (set to 1 for manual and receipt rows); categories `personal_transfers`, `cash`, `taxes`, `fees`, `interest`, `electronics` and the `b6_` keyword rules inserted where missing; schema-5 transfer rules re-pointed by id (generic wording to `personal_transfers` at priority 5, `átvezetés` to own transfers at 30, `jóváírás` to priority 4); the `transfers` category renamed "Between own accounts" unless the user renamed it. Existing transactions keep their category and direction until the user runs "Re-apply rules". |

All migrations are additive or data-preserving. There is no destructive fallback: an unknown upgrade path fails instead of silently wiping data. The earlier `fallbackToDestructiveMigrationOnDowngrade` was also removed, so installing an older build over a newer one fails rather than deleting the database.

`MigrationTest` (instrumented) checks 5 → 6 (manual and receipt rows protected, CSV rows unchanged, new categories and rules present, a schema-5 rule the user deleted not recreated, user rules and renamed categories untouched) and 3 → 5: it creates a version 3 database with an account, a notification transaction containing raw text, a manual transaction, a receipt with OCR text and a notification event, migrates to 5 with `runMigrationsAndValidate`, and checks the data and the scrubbing. Schemas 1 and 2 were never exported, so 1→5 and 2→5 are not covered by tests.

## Preferences (DataStore)

Theme, display currency, lock type, PIN verifier (`pbkdf2-sha256$...`), failed attempt count and lockout time, automatic rate fetching, notification consent, encrypted Finnhub key, and bank-to-account mappings (`bank_map_<institution>`).
