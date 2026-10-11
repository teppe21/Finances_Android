# Testing

## Unit tests (JVM)

`./gradlew testDebugUnitTest` runs 169 tests in `app/src/test`. They need no device and no Android framework classes (the real `org.json` is a test dependency).

| Suite | Tests | Covers |
| --- | --- | --- |
| `NotificationRecognitionTest` | 15 | Revolut, Google Wallet and OTP payments; balance lines ignored; promotions, referrals, cashback; security and login messages; unknown packages; malformed amounts; ambiguous multi-amount messages; exact package routing |
| `NotificationPipelineTest` | 17 | Same amount at different shops (two rows); wallet + bank merge; card-suffix merge and enrichment; different card suffix (no merge); weak overlap (review); same amount same day (no merge); re-delivered notification; identical purchases seconds apart (review); ambiguous account (pending, unassigned); explicit mapping; unknown card; raw text never persisted; unsupported packages leave no trace; CSV fingerprints; merchant matcher; sign normalization |
| `NotificationReliabilityTest` | 10 | `SerialWorkQueue`: strict order and one-at-a-time, per-item timeout reported as a timeout while the next item still runs, exceptions reported as errors and never as success, a failing failure report, bounded backlog that drops (and counts) instead of queueing, close. `RebindPolicy`: doubling delays, retry budget, window expiry, stable-connection reset, connect/disconnect loop does not reset the budget |
| `MoneyParsingTest` | 5 | Separator styles, malformed input, `19.99` exactness, large values, money mentions in text, formatting precision |
| `AmountParserStrictnessTest` | 7 | Regular grouping only; irregular grouping, leading-zero groups, sign on both ends, trailing separators rejected; explicit comma-decimal and dot-decimal styles; overflow reported; which amounts prove a style |
| `CurrencyAndStatsTest` | 11 | Same data in HUF, EUR and USD; missing rates excluded and counted; pending excluded; cross rates and rounding; manual overrides; trends and category breakdown use the same conversion; account balances; keyword word-start matching; Frankfurter and open.er-api parsing; transfers and refunds |
| `CsvAndReceiptTest` | 8 | Hungarian semicolon CSV with quotes; US numbers with debit/credit columns; skipped rows by reason; Windows-1250; BOM and quoted newlines; date parser; Hungarian receipt; decimal receipt totals |
| `CsvDateAndAmountInferenceTest` | 12 | Year-first, dotted and slash/dash dates; order read from the cell or proved by other rows; mixed and invalid evidence; ambiguous rows skipped with a reason; separator style inferred per file; UTF-8 and Windows-1250 give identical rows |
| `ReceiptTotalExtractionTest` | 11 | Payable-total labels (Hungarian and English, accents, OCR digit slips, label on the previous line); no label means no total; VAT, subtotal, discount, change, cash and tax-id lines excluded; payable beats generic; conflicting totals left empty; dates, times, percentages and barcodes are not amounts; currency; plain four-digit totals |
| `GesturePhysicsTest` | 10 | Momentum projection, rubber-banding (progressive, bounded, symmetric), 1:1 inside the range and resisted outside it, release decisions: slow short drag settles, slow long drag commits, quick flick commits from a short distance, a flick back does not commit, a direction without an action never commits, an unmeasured row never commits; the reduced-motion signal |
| `ImageOrientationTest` | 5 | EXIF orientation to one rotation plus optional mirror; unknown values; camera rotation normalization; dimension swap; decode sample size |
| `ResourceLeaseTest` | 5 | The OCR resource lifecycle helper: released once after success or failure, released immediately when starting throws, **not released while the task runs after cancellation, released when it finishes**, duplicate completion cannot release twice |
| `ViewModelTest` (4 classes) | 26 | Receipt: capture with rotation, account suggestion, empty total, recognition failure, double tap ignored, sample marking, save without image or text, double save blocked, save failure keeps the sheet open. Transactions: empty list, period/category/search filters, sorting with unconvertible amounts, manual entry, delete. Dashboard: loading state, empty database, confirmed-only totals, review count, unconvertible balance is unknown. Accounts: balances and pending counts, add trims input. Review: confirm, confirm without account, discard |
| `SecurityTest` | 8 | PIN verifier accept/reject, random salt, no plaintext, Keystore-pepper requirement, malformed verifiers, iteration count, legacy hash migration, PIN policy, lockout schedule |
| `StocksTest` | 19 | Finnhub parsing (quote, search, profile, candles, market status) incl. empty/null/zero/malformed data; HTTP 401/403/429/5xx, timeout, offline; key only in header; freshness labels; refresh policy; add/remove/reorder/duplicates/cap; persistence; missing key; live vs last close; rate-limit stop; stale cache; isolation from finance data |

The ViewModels take their flows and actions as constructor parameters (the no-argument constructors wire the real app), so the tests use plain flows and small fakes instead of a database.

## Instrumented tests

`./gradlew connectedDebugAndroidTest` needs an emulator or device and runs 33 tests. They use in-memory or throwaway databases with synthetic rows; they never read or change the app's own `finances.db`. Note that Gradle uninstalls the debug app from the device after the run.

| Suite | Tests | Covers |
| --- | --- | --- |
| `MigrationTest` | 2 | Migration 3 to 5 keeps user data and scrubs notification text and OCR text; deleting financial data keeps the watchlist and restores defaults |
| `DatabaseTest` | 15 | Default data is created once and is idempotent; user edits and disabled rules survive re-seeding; the create callback seeds the instance it belongs to; a default the user deleted is not recreated when the database is reopened; reset restores clean defaults; `insertIgnore` keeps the original row on fingerprint or id collision; period, pending and recent queries; duplicate-window query; external-id lookup and deletes; reassigning accounts and categories; inactive accounts; rule ordering; notification diagnostics hold no raw text; the receipt repository drops image reference and OCR text |
| `ComponentUiTest` | 4 | Add-transaction validation and saving an expense as a negative amount; empty state; notification disclosure |
| `ReviewFlowUiTest` | 10 | Confirm and discard collapse the card, then act and show a snackbar; an unassigned transaction cannot be confirmed until an account is chosen; empty queue; a quick swipe right confirms and left discards; a short slow drag settles back without committing; swiping to confirm without an account does nothing; Undo restores a discarded or confirmed transaction |
| `NavigationUiTest` | 2 | Bottom tabs switch between top-level screens; drilling into a screen from More shows Back and Back returns to the list |

## What has been run

On 2026-10-11, on Windows 11 with JDK 17.0.12, Android SDK platforms 34-36 and build-tools 36.0.0, using the repository's Gradle wrapper (8.14.3):

| Command | Result |
| --- | --- |
| `./gradlew testDebugUnitTest` | passed, 169 tests |
| `./gradlew lint` | passed, 0 errors |
| `./gradlew assembleDebug` | passed |
| `./gradlew connectedDebugAndroidTest` on an API 35 x86_64 emulator (Google APIs image) | passed, 33 tests |
| `./gradlew testReleaseUnitTest lintRelease assembleRelease` with a throwaway key | passed (R8 and resource shrinking); `apksigner verify` and `zipalign -c -P 16` succeeded; the minified APK launched on the emulator and the four tabs opened without a crash |

Not run: `scripts/check-16kb-alignment.sh` (needs `readelf`, which this machine does not have; CI runs it), anything on a physical device, camera capture on a real receipt, notification access with real banking apps, TalkBack, and frame-rate measurements. Work through [REAL_DEVICE_TEST_PLAN.md](REAL_DEVICE_TEST_PLAN.md) for those.

## CI

`.github/workflows/android.yml` runs the unit tests, lint, the debug build, **compiles the instrumented tests** and fails if the committed Room schemas under `app/schemas` are out of date. The instrumented tests themselves are not run in CI (no emulator on the runner).
