# Development guide

For installing the app on a phone see [INSTALLATION.md](INSTALLATION.md). Architecture background: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Repository structure

```
app/                         the single Android module (:app)
  src/main/java/com/teppe21/finances/
    feature/                 Compose screens + ViewModels (dashboard, transactions, accounts, ...)
    stocks/                  watchlist: ui/, domain/, data/ (Finnhub provider, Room stores)
    domain/                  models and use cases (ingestion, dedup, stats, FX)
    native/                  notification recognition, receipt parser/OCR, CSV parser
    data/                    Room database, DAOs, repositories, preferences, FX
    security/                PIN hashing, app lock, Keystore secrets
    core/                    theme, shared UI components, navigation, money, utils
  src/test/                  JVM unit tests
  src/androidTest/           instrumented tests (Room migration)
  schemas/                   exported Room schemas (committed)
docs/                        topic documentation and the static privacy policy
scripts/                     check-16kb-alignment.sh
.github/workflows/           android.yml (CI), release.yml (manual signed build)
```

## Toolchain

| | Version (from the build files) |
| --- | --- |
| JDK | 17 (the Gradle wrapper itself can run on a newer JDK; the build targets 17) |
| Gradle | 8.14.3 (wrapper) |
| Android Gradle Plugin | 8.13.2 |
| Kotlin / KSP | 2.0.21 / 2.0.21-1.0.28 |
| Room | 2.6.1 |
| SDK | compileSdk 36, targetSdk 36, minSdk 26 |

Install the Android SDK with platform 36 and a matching build-tools package (Android Studio does this on first sync; or use `sdkmanager "platforms;android-36" "build-tools;36.0.0"`). Point Gradle at it with `ANDROID_HOME` or a git-ignored `local.properties` (`sdk.dir=...`, template: `local.properties.example`). Nothing machine-specific is committed.

## Android Studio

Open the repository root (the folder containing `settings.gradle.kts`), let Gradle sync, choose the `app` run configuration. Use the bundled or a JDK 17 as the Gradle JDK.

## Building and testing

All commands run from the repository root and match CI:

```bash
./gradlew testDebugUnitTest          # JVM unit tests (no device)
./gradlew lint                       # Android lint, all variants
./gradlew assembleDebug              # debug APK
./gradlew connectedDebugAndroidTest  # instrumented tests: needs an emulator/device
./gradlew assembleRelease bundleRelease   # needs signing values, see below
```

Reports: `app/build/reports/tests/` and `app/build/reports/lint-results-*.html`.

Dependency resolution needs `dl.google.com` and Maven Central. In a sandbox that blocks Google Maven, Gradle fails on the first plugin (`Plugin [id: 'com.android.application' ...] was not found`); there is then no Android build result at all, and plain-Kotlin compilation of selected files is not a substitute.

## Room schema and migrations

`ksp { arg("room.schemaLocation", "$projectDir/schemas") }` writes the schema JSON to `app/schemas/`, which is committed and also used as `androidTest` assets. When you change an entity:

1. Bump `version` in `AppDatabase`.
2. Add a `Migration` and register it; never use destructive fallback for user data.
3. Build once so the new `N.json` is exported, and commit it.
4. Extend `app/src/androidTest/.../MigrationTest.kt` and run `./gradlew connectedDebugAndroidTest` on an emulator.

The current schema version is the highest file in `app/schemas/com.teppe21.finances.data.local.database.AppDatabase/`.

## Notification parser fixtures

Parsers live in `native/notification/parsing/` and are exercised by `NotificationRecognitionTest` and `NotificationPipelineTest`. Fixtures are plain strings in those tests. Use text copied from your own real notifications with names, card suffixes and unrelated amounts replaced by synthetic ones; never commit real personal payloads.

### Adding a supported notification source

1. Write a `SourceParser` in `SourceParsers.kt` (payment wording plus a readable amount; reject security codes, promotions and balance-only lines).
2. Add a `NotificationSource` to `SupportedSources` with its **exact** package name(s). Set `verifiedFixtures = true` only when you have tested against real notification text; otherwise results always go to the review queue.
3. Add accepted and rejected fixtures to the tests; the "other apps are dropped" test must still pass.
4. Update the source table in [README.md](README.md) and [docs/BANK_INTEGRATION.md](docs/BANK_INTEGRATION.md).

## Stock provider

`StocksTest` runs the Finnhub provider, freshness labels and the watchlist repository against fakes in `src/test/.../support/Fakes.kt`; no network or key is needed. To try the real provider, run the debug app, open **Settings > Market data** and enter your own key. The key is stored encrypted with an Android Keystore key and is never read from build files, so there is nothing to put in `local.properties` and nothing to commit. Never paste a key into code, tests, issues or logs. Whether history (candles) is available depends on your Finnhub plan; see [docs/STOCKS.md](docs/STOCKS.md).

## Release signing

Release tasks need `FINANCES_KEYSTORE_PATH`, `FINANCES_KEYSTORE_PASSWORD`, `FINANCES_KEY_ALIAS`, `FINANCES_KEY_PASSWORD` (environment or git-ignored `keystore.properties`) and otherwise stop with an error; there is no debug-key fallback. To check a result:

```bash
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

The manual `release.yml` workflow reads the same values from repository secrets. CI (`android.yml`) never uses signing material.

## 16 KB page-size check

```bash
scripts/check-16kb-alignment.sh app/build/outputs/apk/release/app-release.apk
```

It needs `readelf` (binutils) and checks that native libraries have LOAD segments aligned to at least 16 KB; with build-tools 35+ it also runs `zipalign -P 16`. Background: [docs/16KB_COMPATIBILITY.md](docs/16KB_COMPATIBILITY.md).

## CI

`.github/workflows/android.yml` runs on pushes and pull requests to `main` and manually (`workflow_dispatch`): Checkout, JDK 17, Android SDK, wrapper check, `testDebugUnitTest`, `lint`, `assembleDebug`. `gradlew` must stay executable in Git (`git ls-files -s gradlew` shows `100755`); a `100644` mode caused the first CI run to fail with `Permission denied` (exit 126).

## Known verification gaps

Not yet verified on real Android tooling or devices: lint results, R8 release build and AAB, instrumented migration test, signature checks, 16 KB check on a real release APK, notification access on target phones, camera/OCR, app-lock lifecycle, and the Stocks provider with a real key. See [docs/TESTING.md](docs/TESTING.md) and [docs/REAL_DEVICE_TEST_PLAN.md](docs/REAL_DEVICE_TEST_PLAN.md).
