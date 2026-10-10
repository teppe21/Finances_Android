# Privacy

This describes what the app actually does with data, as implemented. The user-facing policy is [privacy-policy.html](privacy-policy.html) (the same text is shown in the app under Settings > Privacy policy).

## On the device

| Data | Where | Notes |
| --- | --- | --- |
| Transactions, accounts, categories, rules, budgets | Room database | app-private storage |
| Notification results | Room `notification_events` | package, time, status code; 30-day retention; no text |
| Receipts | Room `receipts` | merchant, date, amounts; no image, no OCR text |
| Exchange rates | Room `exchange_rates` | rates and their dates |
| Watchlist and last quotes | Room `watchlist`, `stock_quotes` | separate from finance tables |
| Settings, PIN verifier, Finnhub key | DataStore | key encrypted with a Keystore AES key |

Nothing is written to shared storage. The app does not log notification text, amounts, merchants, PINs or API keys; the only log line is the exception type when notification processing fails.

## Backup

`android:allowBackup="false"`, and both `backup_rules.xml` (Android 11 and lower) and `data_extraction_rules.xml` (Android 12+) exclude every domain from cloud backup and device-to-device transfer. Keystore keys are never backed up by Android. Consequence: moving to a new phone or reinstalling loses the data. There is no export feature yet.

## Network

| Destination | Sent | When |
| --- | --- | --- |
| `api.frankfurter.dev` | requested currency codes (HUF, USD, GBP, CHF) | app start and on "Update now", if automatic fetching is on |
| `open.er-api.com` | nothing beyond the URL path `EUR` | only if Frankfurter fails |
| `finnhub.io` | API key (header), symbols, search text | only with a key, while using Stocks |
| `finnhub.io/register` (browser) | nothing from the app | if the user taps the link |

Every request also exposes the device IP address to the receiving service. There are no analytics, crash-reporting or advertising SDKs. ML Kit text recognition uses the bundled model; Google ML Kit may still send its own diagnostics according to Google's terms, which should be reviewed for the Data Safety form.

## Permissions

`INTERNET`, `CAMERA` (runtime, only from the receipt screen), `USE_BIOMETRIC`, and the notification listener binding (`BIND_NOTIFICATION_LISTENER_SERVICE`, granted by the user in Android settings). No SMS, contacts, call log, location, storage, `QUERY_ALL_PACKAGES` or `POST_NOTIFICATIONS`. CameraX and ML Kit may merge additional manifest entries; check the merged manifest of a release build.

## Deletion

- Settings > Delete all financial data: transactions, accounts (recreated empty), categories and rules (defaults recreated), budgets, recurring rules, receipts, notification results and bank-to-account mappings. Keeps the lock, settings, rates, watchlist and API key.
- Settings > Reset app completely: everything above plus rates, watchlist, quotes, all preferences (including PIN and API key) and the Keystore keys.
- Uninstalling or clearing app storage removes all data.
