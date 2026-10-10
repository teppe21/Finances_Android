# Installing Finances

Finances is an Android app (minimum Android 8.0, API 26). There are four ways to get it onto a phone. Pick the one that matches what you have.

| Route | You need | Use it for |
| --- | --- | --- |
| [1. Signed release APK](#1-signed-release-apk) | a release APK signed with the upload/release key | everyday use |
| [2. Debug APK](#2-debug-apk-for-testing) | a debug build | testing only |
| [3. ADB](#3-installing-with-adb) | a USB cable or wireless debugging | developers, repeatable installs |
| [4. Build from source](#4-building-from-source) | JDK 17 and the Android SDK | producing any of the above |

## Where to get an APK

No verified release artifact is published for this repository yet, and there is no download URL to trust. Until one exists, the APK has to be built (route 4) and, for a release, signed with your own key (see [docs/RELEASE.md](docs/RELEASE.md)). Do not install a `Finances` APK that someone sends you from anywhere else: an APK can read everything the app can, and the notification access this app asks for is a powerful permission. Only install files you built yourself or that come from a source whose signing certificate you have verified.

## 1. Signed release APK

1. Build and sign it (route 4), or obtain it from a source you verified.
2. Check the signature before copying it to the phone:
   ```bash
   apksigner verify --print-certs app-release.apk
   ```
   Compare the certificate fingerprint with the one you expect. A debug certificate (`CN=Android Debug`) means this is **not** a release.
3. Copy the file to the phone (USB file transfer, a cable-free tool you trust, or a private cloud folder).
4. Open it with the phone's file manager and tap **Install**.

## 2. Debug APK for testing

`./gradlew assembleDebug` produces `app/build/outputs/apk/debug/app-debug.apk`. It is signed with a throw-away debug key, is debuggable, and is **not** a release: never distribute it and do not keep real financial data in it. A debug build and a release build have different signatures, so one cannot be installed over the other (see [Updating](#updating)).

## 3. Installing with ADB

Enable **Developer options > USB debugging** on the phone, connect it and accept the fingerprint prompt, then:

```bash
adb devices                       # the phone must be listed as "device"
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`-r` replaces an existing installation of the same signature and keeps its data. Use `adb uninstall com.teppe21.finances` to remove the app (this deletes its data).

## Allowing installation from a file

When you open an APK from a file manager or browser, Android asks you to allow that app to install unknown apps (Settings > Apps > Special access > Install unknown apps; the exact path varies by manufacturer). Allow it only for the app you are installing from, and switch it off again afterwards. ADB installs do not need this.

## Installation and signature errors

| Message | Likely cause |
| --- | --- |
| "App not installed" / `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | the installed copy was signed with a different key (for example debug vs. release). Uninstalling is the only fix, which deletes the app's data |
| `INSTALL_FAILED_VERSION_DOWNGRADE` | the APK's `versionCode` is lower than the installed one |
| `INSTALL_PARSE_FAILED_NO_CERTIFICATES` or "package appears to be invalid" | the APK is unsigned or damaged; an unsigned release build cannot be installed |
| `INSTALL_FAILED_OLDER_SDK` | Android older than 8.0 |
| Blocked by Play Protect | expected for an app Google has not seen; decide deliberately and only for a file you built or verified |

## Updating

Install the new APK over the old one. It must be signed with the **same key** and have a higher `versionCode`; data is kept. There is no cloud backup (`allowBackup="false"`) and the app currently has **no export function**, so before any uninstall or switch between debug and release builds there is no supported way to save your records. Treat debug builds as disposable.

## Uninstalling

All financial data lives only in the app's private storage on the phone. Uninstalling the app, clearing its storage, or installing a differently signed build removes it permanently, and it cannot be recovered.

## First launch

The app has no sign-up and no onboarding wizard; it opens on the Dashboard with an empty database (no dummy balances). A sensible order:

1. **Currency.** Settings > Currency: choose the display currency (HUF, EUR, USD, GBP or CHF). Exchange rates are fetched automatically unless you turn that off, in which case enter manual rates there. Without a rate, a transaction in another currency is left out of totals and the screen says so.
2. **Accounts.** Create at least one account with its currency and opening balance.
3. **App lock (optional).** Settings > Security: set a 4 to 8 digit PIN and, if the phone supports it, biometric unlock. There is no default PIN and no PIN recovery.
4. **Notification recognition (optional).** More > Notification automation shows a disclosure first; Android's notification-access screen opens only after you tap Continue. The app reads notifications only from the explicitly supported apps listed in the [README](README.md#automatic-transaction-recognition) and drops all others. Recognized payments may land in a review queue instead of being booked directly. Do not connect real financial accounts for a demo; use **Settings > Data management > Add demo data** instead, and remove it afterwards.
5. **Receipt scanning (optional).** The camera permission is requested only when you open the scanner. Check every recognized value before saving.
6. **CSV import (optional).** More > CSV import. Format details: [docs/IMPORT_FORMATS.md](docs/IMPORT_FORMATS.md).
7. **Stock watchlist (optional).** Settings > Market data (or the Stocks tab) asks for your own [Finnhub](https://finnhub.io) API key; the app ships no key. Without one, or with an invalid one, the screen explains what is missing. Whether quotes are real-time or delayed depends on your Finnhub plan and is labelled from each quote's own timestamp. See [docs/STOCKS.md](docs/STOCKS.md).
8. **Privacy policy.** Settings > Privacy > Privacy policy, also in [docs/privacy-policy.html](docs/privacy-policy.html).
9. **Deleting data.** Settings > Data management > *Delete all financial data* or *Reset app completely*.

### Known limitations

- No export or backup of your data (see above).
- Existing transactions cannot be edited; delete and re-enter them.
- Notification recognition covers only the listed apps; the Hungarian bank and Wise package names are unverified against real devices.
- Currency conversion uses the latest rate, not the rate of each transaction date.
- The app has not yet been tested on a physical device; see [docs/REAL_DEVICE_TEST_PLAN.md](docs/REAL_DEVICE_TEST_PLAN.md).

## 4. Building from source

Requirements: JDK 17 and the Android SDK with platform 36 (see [DEVELOPMENT.md](DEVELOPMENT.md)). Run everything from the repository root:

```bash
./gradlew testDebugUnitTest     # JVM unit tests
./gradlew lint                  # Android lint
./gradlew assembleDebug         # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease       # needs signing values, see below
./gradlew bundleRelease         # Play bundle (.aab), needs signing values
```

Release tasks stop with an error unless all four signing values are present; there is no fallback to the debug key. Provide `FINANCES_KEYSTORE_PATH`, `FINANCES_KEYSTORE_PASSWORD`, `FINANCES_KEY_ALIAS` and `FINANCES_KEY_PASSWORD` as environment variables or in a git-ignored `keystore.properties` (template: `keystore.properties.example`). Create your own key with `keytool`; never commit keystores or passwords. Details: [docs/RELEASE.md](docs/RELEASE.md).

Building needs network access to `dl.google.com` (Google Maven) and Maven Central. If your network blocks them, the build fails at dependency resolution (`Plugin ... was not found`), and nothing about the app can be claimed as built or tested.
