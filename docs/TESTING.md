# Testing

## Unit tests (JVM)

`./gradlew testDebugUnitTest` runs 83 tests in `app/src/test`. They need no device and no Android framework classes (the real `org.json` is a test dependency).

| Suite | Tests | Covers |
| --- | --- | --- |
| `NotificationRecognitionTest` | 15 | Revolut, Google Wallet and OTP payments; balance lines ignored; promotions, referrals, cashback; security and login messages; unknown packages; malformed amounts; ambiguous multi-amount messages; exact package routing |
| `NotificationPipelineTest` | 17 | Same amount at different shops (two rows); wallet + bank merge; card-suffix merge and enrichment; different card suffix (no merge); weak overlap (review); same amount same day (no merge); re-delivered notification; identical purchases seconds apart (review); ambiguous account (pending, unassigned); explicit mapping; unknown card; raw text never persisted; unsupported packages leave no trace; CSV fingerprints; merchant matcher; sign normalization |
| `MoneyParsingTest` | 5 | Separator styles, malformed input, `19.99` exactness, large values, money mentions in text, formatting precision |
| `CurrencyAndStatsTest` | 11 | Same data in HUF, EUR and USD; missing rates excluded and counted; pending excluded; cross rates and rounding; manual overrides; trends and category breakdown use the same conversion; account balances; keyword word-start matching; Frankfurter and open.er-api parsing; transfers and refunds |
| `CsvAndReceiptTest` | 8 | Hungarian semicolon CSV with quotes; US numbers with debit/credit columns; skipped rows by reason; Windows-1250; BOM and quoted newlines; date parser; Hungarian receipt; decimal receipt totals |
| `SecurityTest` | 8 | PIN verifier accept/reject, random salt, no plaintext, Keystore-pepper requirement, malformed verifiers, iteration count, legacy hash migration, PIN policy, lockout schedule |
| `StocksTest` | 19 | Finnhub parsing (quote, search, profile, candles, market status) incl. empty/null/zero/malformed data; HTTP 401/403/429/5xx, timeout, offline; key only in header; freshness labels; refresh policy; add/remove/reorder/duplicates/cap; persistence; missing key; live vs last close; rate-limit stop; stale cache; isolation from finance data |

## Instrumented tests

`./gradlew connectedDebugAndroidTest` (device or emulator) runs `MigrationTest`:

- `migrate3To5_keepsUserDataAndScrubsNotificationText`
- `deletingFinancialDataKeepsWatchlistAndRestoresDefaults`

## What has and has not been run

During the October 2026 rework the build environment could not reach Google's Maven repository (`dl.google.com`), so the Android Gradle Plugin, AndroidX and the Android SDK were unavailable. What was run instead:

1. All 83 unit tests, compiled and executed with Kotlin 2.0.21 on JDK 21 in a plain JVM Gradle project that uses the app's own `src/main` and `src/test` files: **83 passed, 0 failed**.
2. A type check of the entire `src/main` source set (all screens and Android code) with the Kotlin 2.0.21 Compose compiler against JetBrains Compose 1.6.11 desktop libraries, Robolectric's `android-all` (API 34) and small stubs for Room, DataStore, CameraX, ML Kit, biometric, activity and navigation APIs: **compiles without errors**. This catches type and API errors but not Room's annotation processing, lint, resource linking or runtime behaviour.

Not yet run: `./gradlew testDebugUnitTest`, `lint`, `assembleDebug`, release builds, the instrumented tests, and anything on a device. Run them before relying on this version, and work through [REAL_DEVICE_TEST_PLAN.md](REAL_DEVICE_TEST_PLAN.md).
