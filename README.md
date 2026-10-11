# Finances

[![Android CI](https://github.com/teppe21/Finances_Android/actions/workflows/android.yml/badge.svg)](https://github.com/teppe21/Finances_Android/actions/workflows/android.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

A privacy-first personal finance app for Android (Kotlin, Jetpack Compose) that keeps your data on the phone and can pick up card payments from supported banking notifications.

*Co-developed with Claude Code (AI assistant); see [Built with AI assistance](#built-with-ai-assistance).*

## Download the Android app

**[Download the latest beta APK (GitHub Releases)](https://github.com/teppe21/Finances_Android/releases)** · [Installation guide / Telepítési útmutató](INSTALLATION.md) · [Development guide](DEVELOPMENT.md)

> **Personal beta for testing only.** The current download is a **debug-signed APK** (`Finances-…-debug.apk`) published as a GitHub *pre-release*. It is not a production-signed release and is not on Google Play. So far it has been tried on an emulator and in automated tests, not on a physical phone. On the Releases page open the newest entry, tap the `.apk` under **Assets**, and allow installing from your browser or Files app when Android asks.

Signed production releases will be published in the same place once a release key is set up. You can also build the app yourself ([DEVELOPMENT.md](DEVELOPMENT.md)).

Finances keeps your transactions on the phone and tries to take some of the typing out of bookkeeping: it can pick up card payments from the notifications of a few banking apps, read the total from a paper receipt, and import bank statements in CSV format. It also has a small stock watchlist that shows market quotes from a provider you connect yourself.

This repository contains only the Android app. It used to live in the `android-native/` folder of the [teppe21/finances](https://github.com/teppe21/finances) repository next to an older web dashboard; it was moved here so it can be built and released on its own.

## Screenshots

<img height="500" alt="IMG_20261011_221401" src="https://github.com/user-attachments/assets/f2bae5ee-84ca-4791-ad52-1e1800652b13" />
<img height="500" alt="IMG_20261011_221309" src="https://github.com/user-attachments/assets/831c2007-2817-4032-9dd4-781f6cc3553e" />
<img height="500" alt="IMG_20261011_221314" src="https://github.com/user-attachments/assets/b1206d11-382b-433c-a875-4b41ae1eab5f" />
<img height="500" alt="IMG_20261011_221344" src="https://github.com/user-attachments/assets/3c3ef43f-f30b-4015-a01d-58a3d0e8e431" />
<img height="500" alt="IMG_20261011_221353" src="https://github.com/user-attachments/assets/dc646e7b-f69f-4681-90a8-c244b8ccdd10" />



## Built with AI assistance

**This app was co-developed with Claude Code**, Anthropic's AI coding assistant. A large part of the code, tests and documentation was written by Claude working under my direction; I set the requirements, made the product and design decisions, and reviewed and tested the result. The commit history was cleaned up for publication and does not list Claude as an author, so this section is the authoritative statement of how the project was made.

## Overview

All financial records live in a Room database in the app's private storage. There is no account system and no backend. The app talks to the network for two things only: exchange rates (currency codes only) and, if you add an API key, stock quotes. Android backup and device transfer are switched off for the app's data, so uninstalling it deletes everything.

Transactions reach the database through one ingestion pipeline whatever their source (manual entry, notification, receipt, CSV). The pipeline normalizes the sign of the amount, cleans the merchant name, checks for duplicates, applies category rules and writes the row without ever replacing an existing one.

## Features

### Transaction tracking

Manual entry with decimal amounts in HUF, EUR, USD, GBP or CHF, accounts with their own currency and opening balance, and account balances computed from the opening balance plus every confirmed transaction. Transactions can be searched, filtered by period, category and recurrence, sorted, and deleted.

### Automatic transaction recognition

With Android notification access (granted only after an in-app disclosure and an explicit "Continue"), the app reads notifications from a fixed list of apps, matched by exact package name:

| App | Package | Status |
| --- | --- | --- |
| Revolut | `com.revolut.revolut` | Patterns tested with real notification text |
| Google Wallet | `com.google.android.apps.walletnfcrel` | Patterns tested with real notification text |
| OTP Bank | `hu.otpbank.smartbank`, `hu.otpbank.mobilbank` | Recognized transactions go to the review queue |
| Wise | `com.transferwise.android` | Recognized transactions go to the review queue |
| Erste Bank (George) | `hu.erstebank.george.hungary`, `hu.erstebank.mobilebank` | Recognized transactions go to the review queue |
| MBH Bank | `hu.mbhbank.app`, `hu.takarek.mobilbank` | Recognized transactions go to the review queue |

Notifications from every other app are dropped before their text is read. For supported apps, security messages (login codes, approval requests) and promotions (referral offers, cashback, rewards) are rejected, and a transaction is only created when the text contains source-specific payment wording plus a readable amount. Balance lines are never used as the amount. Raw notification text is not stored; only the recognized transaction and a sanitized status code (for example `REJECTED_PROMOTIONAL`) are kept, the status records for 30 days.

When the target account is unclear (no account for that bank, or several), when a parse comes from a source without verified samples, or when the transaction may duplicate another notification, it is saved as pending and appears in a review queue. Pending transactions are not counted in balances or statistics until you confirm them.

Package names for the Hungarian banks and Wise come from the earlier version of the project and have not been checked against the Play Store listings; verify them on a device before relying on them.

### Duplicate detection

A wallet tap often produces two notifications for one purchase: one from Google Wallet and one from the card's bank. The detector merges them only between compatible apps (a wallet and the bank that issued the card), within three minutes, and only with a matching merchant or card suffix and no conflicting signal. Two payments of the same amount at different shops, or with different card suffixes, stay separate. Weak overlaps are flagged for review instead of being merged. The same notification delivered twice is recognized by a hashed fingerprint. Equal amount and date alone never merge anything.

### Receipt scanning

The camera is requested only when you open the scanner, after a short explanation. Text recognition runs on the device with Google ML Kit (Latin script). The parser looks for the total, VAT, payment method and date in common Hungarian and English receipt wording. You confirm or correct every value before saving; if the total cannot be read the field stays empty. The photo and the recognized text are not stored.

### Automatic categorization

Built-in keyword rules in Hungarian and English (groceries, dining and delivery, transport and fuel, utilities, subscriptions, health, shopping, electronics, government and taxes, fees, interest, salary) and your own rules map merchants to categories. Matching ignores case, accents, apostrophes and card-terminal noise; keywords match at word starts, and keywords of three letters or fewer only as whole words, so `ber` does not catch `Uber` and `mol` does not catch `Molnár`. Your rules take precedence over the built-in ones.

Transfers are their own kind and never count as income or spending: moves between your own accounts, currency exchanges, top-ups, balance migrations and cash withdrawals are "between own accounts", money sent to or received from people is "transfers with people". Analytics shows both on a separate card. After a CSV import a summary lists what was imported, skipped, categorized, left uncategorized and recognized as a transfer. The Uncategorized screen groups what is left by merchant: one choice sorts the group and can be kept as a rule that also sorts every matching existing transaction; the same "remember" option is on every transaction's category. "Re-apply rules" runs the rules over existing transactions and never changes a category you chose yourself. Details: [docs/IMPORT_FORMATS.md](docs/IMPORT_FORMATS.md#categorization).

### Multi-currency support

Amounts are stored as 64-bit integers with two implied decimals in their original currency. For the display currency the app converts with the latest European Central Bank reference rates (via the Frankfurter API, with open.er-api.com as fallback) or with manual rates you enter. If a rate is missing, the transaction is left out of totals and the screen says so; the number is never shown with the wrong currency symbol. Conversions use the latest rate, not the historical rate of each transaction date.

### Analytics

Month-by-month view with previous, next and current-month navigation, plus 3-month, 6-month, yearly and custom ranges. Income, expenses, net, average and largest expense, category breakdown, top merchants and a six-month trend, all in the display currency and all without transfers; transfers between own accounts and with people are shown separately.

### CSV import

Semicolon, comma or tab separated files, quoted fields (including line breaks), UTF-8 or Windows-1250 encoding, Hungarian (`2026.03.28.`, `1 249,50`) and international (`2026-03-28`, `1,234.56`) formats, a single amount column or separate debit and credit columns, and the type, state and category columns some banks add (Revolut, Wise, Hungarian bank exports). Rows with an unreadable date or amount, an unsupported currency, or that the bank marks as reverted or declined, are skipped and counted rather than guessed. Re-importing the same file into the same account does not create duplicates; two identical rows inside one file are both kept.

### Budgets and recurring payments

Monthly budgets per category in a currency of your choice, compared with this month's confirmed expenses. Recurring payments are detected from repeated payments to the same merchant about a month apart.

### Stock watchlist

A read-only watchlist of up to 25 symbols using [Finnhub](https://finnhub.io) market data with your own API key. Search by symbol or company name, a quote per row with daily change and percentage, a detail screen with open, high, low, previous close and a price chart for 1D, 1W, 1M, 3M and 1Y when your Finnhub plan includes historical data.

Every quote is labelled from the provider's own timestamp: "Real-time" only when the price is at most two minutes old during an open US session, otherwise "Delayed", "Last close" or "Stale". Quotes refresh about once a minute only while the Stocks screen is visible and the US market is open, with back-off on errors and a pause when Finnhub reports its rate limit. If your plan does not include history for a symbol, the chart explains that instead of drawing anything. Watched stocks never touch accounts, transactions or balances, and the feature is market information only, not investment advice. See [docs/STOCKS.md](docs/STOCKS.md).

## Privacy

Personal financial data stays on the device. The network requests the app makes are listed in [docs/PRIVACY.md](docs/PRIVACY.md); the public policy text is in [docs/privacy-policy.html](docs/privacy-policy.html) and also inside the app (Settings > Privacy policy).

## Security

- Optional app lock with a 4 to 8 digit PIN, stored as a PBKDF2-HMAC-SHA256 verifier (120,000 iterations, random salt) that is additionally passed through an HMAC with a non-exportable Android Keystore key. No default PIN exists.
- Increasing lockouts after five wrong PINs, persisted across restarts.
- Optional biometric unlock through AndroidX `BiometricPrompt`, with the PIN as fallback.
- The app locks as soon as you leave it; pickers and settings screens the app opens itself do not lock it for up to five minutes. Screenshots and the Recents preview are blocked while the lock is on.
- The Finnhub API key is encrypted with an Android Keystore AES key.

Whether Keystore keys are hardware-backed depends on the phone; the app does not check or claim it. Details are in [docs/SECURITY.md](docs/SECURITY.md).

## Technology

| | |
| --- | --- |
| Language | Kotlin 2.0.21 |
| UI | Jetpack Compose (BOM 2024.02.01), Material 3 |
| Storage | Room 2.6.1 (schema version 6), DataStore Preferences |
| Camera and OCR | CameraX 1.4.2, ML Kit Text Recognition 16.0.1 |
| Build | Android Gradle Plugin 8.13.2, Gradle 8.14.3, KSP 2.0.21-1.0.28, JDK 17 |
| SDK levels | minSdk 26, compileSdk 36, targetSdk 36 |

## Architecture

Single-module app with a light layering: Compose screens and ViewModels in `feature/` and `stocks/ui/`, plain-Kotlin domain logic in `domain/`, `core/`, `native/` parsers and `stocks/domain/`, and Room/DataStore/network code in `data/` and `stocks/data/`. Dependencies are wired by hand in `FinancesApp`. The notification pipeline, deduplication, money parsing, currency conversion, PIN hashing and market-data parsing have no Android dependencies, which is what makes them unit-testable. See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) and [docs/DATA_MODEL.md](docs/DATA_MODEL.md).

## Testing

JVM unit tests in `app/src/test` (224) cover categorization and transfer detection (with a harness over synthetic bank statements), notification recognition and rejection, the notification pipeline including deduplication and account matching, the bounded notification queue and rebind policy, exact money parsing, currency conversion and statistics, CSV and receipt parsing, OCR orientation and resource lifecycle helpers, the ViewModels, the PIN verifier and lockout policy, and the Finnhub provider, freshness labels and watchlist repository. Instrumented tests in `app/src/androidTest` (59) cover the Room DAOs, default-data seeding, the migrations to the current schema, the main Compose flows and layout guards at large font scales, in Hungarian and in dark theme. See [docs/TESTING.md](docs/TESTING.md) for what each suite covers and what has been run.

## Build

Requirements: JDK 17 and an Android SDK with platform 36. From the repository root:

```bash
./gradlew testDebugUnitTest      # JVM unit tests
./gradlew lint                   # Android lint
./gradlew assembleDebug          # debug APK (testing only, not a release)
```

Instrumented tests (`./gradlew connectedDebugAndroidTest`) need a device or emulator. Release builds need your own upload keystore; without one the release tasks stop with an explanation instead of producing a debug-signed file. See [INSTALLATION.md](INSTALLATION.md), [DEVELOPMENT.md](DEVELOPMENT.md) and [docs/RELEASE.md](docs/RELEASE.md).

## Google Play preparation

The repository contains the pieces that can be prepared in code: target SDK 36, release signing without a debug fallback, R8 shrinking, no unused dangerous permissions, the notification disclosure, a privacy policy, 16 KB page-size work and reviewer instructions with synthetic data. Several steps can only be done in Play Console or with real credentials; they are listed in [docs/PLAY_CONSOLE_FINAL_CHECKLIST.md](docs/PLAY_CONSOLE_FINAL_CHECKLIST.md).

## Current status

Unreleased (pre-1.0 quality bar). The Android CI workflow runs the JVM unit tests, Android lint, the debug build, compiles the instrumented tests and checks the committed Room schemas on every push to `main`; it also builds a release APK with a throwaway key to check R8 and 16 KB alignment. In a local run on 2026-10-11 the unit tests, lint (debug and release), the debug build, a minified release build signed with a throwaway key (not a distributable release), and the 59 instrumented tests on an Android 15 emulator passed (details in [docs/TESTING.md](docs/TESTING.md)). Not yet done: the device test plan in [docs/REAL_DEVICE_TEST_PLAN.md](docs/REAL_DEVICE_TEST_PLAN.md) on a physical phone, frame-rate measurements and TalkBack checks. No signed release has been published. The supported-app list is short and recognition will miss notification formats it has not seen. There is no data export, and apart from their category, existing transactions cannot be edited yet.

## Project background

The project started as a React web dashboard for personal finances and grew a native Android client, which became the main app. The web dashboard stays in the original repository; this repository continues the Android app only.

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [Design language and motion](docs/MOTION_DESIGN.md)
- [Data model and migrations](docs/DATA_MODEL.md)
- [Bank notification integration](docs/BANK_INTEGRATION.md)
- [CSV import formats](docs/IMPORT_FORMATS.md)
- [Receipt OCR](docs/RECEIPT_OCR.md)
- [Stock watchlist and market data](docs/STOCKS.md)
- [Privacy](docs/PRIVACY.md) and the [privacy policy](docs/privacy-policy.html)
- [Security](docs/SECURITY.md)
- [Testing](docs/TESTING.md)
- [Real-device test plan](docs/REAL_DEVICE_TEST_PLAN.md)
- [Release process](docs/RELEASE.md)
- [Contributing](CONTRIBUTING.md) and [Security policy](SECURITY.md)
- [16 KB page-size compatibility](docs/16KB_COMPATIBILITY.md)
- [Play Console compliance notes](docs/PLAY_CONSOLE_COMPLIANCE.md)
- [Financial features declaration](docs/PLAY_FINANCIAL_FEATURES_DECLARATION.md)
- [Play Console final checklist](docs/PLAY_CONSOLE_FINAL_CHECKLIST.md)
- [Play review instructions](docs/PLAY_REVIEW_INSTRUCTIONS.md)
- [Play Store listing draft](docs/PLAY_STORE_LISTING.md)

## License

Released under the [MIT License](LICENSE). Third-party libraries keep their own licenses.
