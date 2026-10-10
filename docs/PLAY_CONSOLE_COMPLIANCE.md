# Play Console compliance notes

Notes for filling in Play Console. Nothing here has been submitted.

## Target API level

Google Play requires new apps and updates to target Android 16 (API 36) from 31 August 2026 ([target SDK requirements](https://developer.android.com/google/play/requirements/target-sdk)). The app sets `compileSdk = 36` and `targetSdk = 36`. On API 36, large screens ignore orientation and aspect-ratio locks and edge-to-edge is enforced; the app uses Material 3 `Scaffold` insets, but this needs checking on a tablet and a foldable.

## Data safety form (draft)

Based on the implementation; review against the final merged manifest and the current ML Kit and Finnhub terms before submitting.

| Question | Answer | Reason |
| --- | --- | --- |
| Does the app collect or share user data? | Financial info and app activity are processed on the device only. Data sent off the device: currency codes (exchange rates) and stock symbols / search text (Finnhub, only if the user adds a key). | Google's definition of "collected" covers data transmitted off the device. Symbols and search queries may count as "App activity: in-app search history" when sent to Finnhub. Decide with the current definitions. |
| Is data encrypted in transit? | Yes | All requests use HTTPS. |
| Can users request deletion? | Yes, in the app | Delete all financial data / Reset app completely; no server-side data exists. |
| Financial info (purchase history, other financial info) | Not collected (stays on device) | Stored locally only, never transmitted. |
| Messages / notifications | Not collected | Notification text is processed on the device and not stored or sent. |
| Photos | Not collected | Receipt images are processed on the device and not stored. |
| Device or other IDs | Check ML Kit | ML Kit may send diagnostics under Google's terms; confirm in the ML Kit data disclosure documentation. |
| Analytics, ads, crash reporting SDKs | None | None included. |

## Permissions

`INTERNET`, `CAMERA`, `USE_BIOMETRIC`, and the notification listener service. No SMS, call log, contacts, location, broad storage or `QUERY_ALL_PACKAGES`. Re-check the merged manifest of the release build for permissions added by CameraX or ML Kit.

## Notification access

The app uses `NotificationListenerService`. Before sending the user to the system screen it shows a prominent in-app disclosure with "Not now" and "Continue", lists the apps it reads, what it extracts and stores, that unrelated, promotional and security notifications are rejected, and that nothing is sent off the device. Processing stops when the user withdraws consent in the app.

## Privacy policy

`docs/privacy-policy.html` is ready for hosting (static HTML, no JavaScript). It still contains placeholders for the developer name and contact, which must be filled in. The public URL must be entered in Play Console and can be added to the app with `-Pfinances.privacyPolicyUrl=`. The same text is available offline inside the app. See [PLAY_CONSOLE_FINAL_CHECKLIST.md](PLAY_CONSOLE_FINAL_CHECKLIST.md) for hosting steps.

## Financial features

See [PLAY_FINANCIAL_FEATURES_DECLARATION.md](PLAY_FINANCIAL_FEATURES_DECLARATION.md).

## Content rating and audience

Finance/productivity, no user-generated content shared with others, no ads. Target audience: adults (18+), because the app handles personal finances. Answer the IARC questionnaire accordingly.
