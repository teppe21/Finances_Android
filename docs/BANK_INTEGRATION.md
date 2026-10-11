# Bank notification integration

Finances can create transactions from the notifications of a small set of banking and wallet apps. This document describes what is supported, how a notification is processed and how to add a source.

## Consent and access

1. In the app: More > Notification recognition > Set up notification access.
2. A disclosure explains which apps are read, what is extracted, what is stored and that nothing is sent off the device. Only **Continue** records consent and opens Android's notification access screen; **Not now** or dismissing the dialog does neither.
3. The listener processes nothing until the in-app consent flag is set, even if Android access was granted some other way. "Stop using notifications" clears the flag immediately and opens the Android screen so access can be revoked there too.

The settings intent tries the per-app detail screen (Android 11+), then the general notification access list, then the main Settings screen. Some OEM builds (MIUI/HyperOS, older Samsung) only support the later fallbacks; this needs checking on real devices.

## Supported sources

| Source | Packages | Fixture status | Auto-accept |
| --- | --- | --- | --- |
| Revolut | `com.revolut.revolut` | real samples | yes, when the account is unambiguous |
| Google Wallet | `com.google.android.apps.walletnfcrel` | real samples | yes, when the card issuer maps to one account |
| OTP Bank | `hu.otpbank.smartbank`, `hu.otpbank.mobilbank` | synthetic samples | no, review queue |
| Wise | `com.transferwise.android` | synthetic samples | no, review queue |
| Erste Bank (George) | `hu.erstebank.george.hungary`, `hu.erstebank.mobilebank` | none | no, review queue |
| MBH Bank | `hu.mbhbank.app`, `hu.takarek.mobilbank` | none | no, review queue |

"Real samples" means the parser tests use notification text captured from those apps (for example `Coop` / `You spent HUF804` / `HUF balance: HUF42,285.97`, and `75. SZ. ABC ÁRUHÁZ` / `HUF804.00 with Revolut Mastercard ••1413`). The Hungarian bank and Wise package names and patterns were carried over from the earlier version and still need checking against real notifications.

The generic parser of earlier versions, which accepted CIB, Raiffeisen, UniCredit and Gránit notifications with any amount, was removed.

## Processing steps

1. **Routing.** `SupportedSources.forPackage(packageName)` must return a source. Titles, icons and app labels are never used for routing. Unsupported packages are dropped before their text is read and no record is written.
2. **Extraction.** Title, text and big text only; group summary notifications are skipped.
3. **Security filter.** Words such as code, passcode, password, verification, login, approve, "belépési kód", "jóváhagy" reject the notification (`REJECTED_SECURITY_MESSAGE`). A soft cue ("security", "biztonság") plus a bare 4 to 8 digit number also rejects.
4. **Promotional filter.** Strong cues (refer, invite, eligible friend, T&Cs, days left, giveaway, "ajánlj", "nyeremény", ...) reject immediately. Weak cues (bonus, reward, cashback, offer, ...) reject when there is no payment wording, and also reject incoming credits, so a cashback "You received HUF500" is not recorded as income. A merchant name containing a weak cue ("Bonus Pizza") does not block a real card payment.
5. **Source parser.** Each source requires its own payment wording: "You spent", "Elköltöttél", "Fizetés a következőnek", "You sent", "sent you", "refund"; a wallet line of the form `<amount> with <card>`; Hungarian bank words such as "vásárlás", "terhelés", "jóváírás" with an "Összeg:" label. The amount is the first currency amount after the payment wording; lines mentioning balance, available amount or limit are ignored. A Hungarian bank message with several unlabelled amounts is rejected rather than guessed.
6. **Validation.** Expenses and outgoing transfers must be negative, income and refunds positive, and amounts above 100 million in major units are treated as misreads.
7. **Account matching.** HIGH: the user mapped the bank to an account. MEDIUM: exactly one active account has that institution. LOW: anything else; the transaction is stored unassigned and pending. Wallet payments use the institution of the card issuer named in the notification.
8. **Duplicate detection.** See below.
9. **Persistence.** The transaction (amount, currency, generated description such as "Card payment at Coop", merchant, card suffix, time) and a `NotificationEvent` with package, time and status code.

## Duplicate rules

| Evidence | Result |
| --- | --- |
| Same external id | duplicate, not stored |
| Same fingerprint (same notification delivered again) | duplicate, not stored |
| Wallet + issuing bank, same amount/currency/direction, within 3 minutes, matching merchant or card suffix, no conflict | merged; the existing row gains the missing merchant or card suffix |
| Same pair without merchant/card evidence, within 90 seconds | stored as pending "possible duplicate" |
| Same app, same amount, within 2 minutes, merchants not conflicting | stored as pending "possible duplicate" |
| Conflicting merchant or card suffix | separate transactions |
| Same amount and date only | separate transactions |

## Status codes

`ACCEPTED_EXPENSE`, `ACCEPTED_INCOME`, `ACCEPTED_TRANSFER`, `ACCEPTED_REFUND`, `REJECTED_PROMOTIONAL`, `REJECTED_SECURITY_MESSAGE`, `REJECTED_UNSUPPORTED_PACKAGE` (returned but never stored), `REJECTED_NON_TRANSACTION`, `REJECTED_MALFORMED_TRANSACTION`, `DUPLICATE_EXISTING_TRANSACTION`, `PENDING_POSSIBLE_DUPLICATE`, `PENDING_ACCOUNT_REVIEW`, `PENDING_LOW_CONFIDENCE`, and, when processing itself failed, `PROCESSING_TIMEOUT` and `PROCESSING_ERROR` (nothing was recorded for that notification). Events are deleted after 30 days.

## Adding a source

1. Capture real notification text on a test device (do not commit personal data; replace merchants and amounts).
2. Add a `NotificationSource` in `SupportedSources` with the exact package names and `verifiedFixtures = false`.
3. Implement a `SourceParser` that returns `NotATransaction` unless the text has payment wording.
4. Add tests to `NotificationRecognitionTest` and `NotificationPipelineTest` for genuine payments, promotions, security messages and balance lines.
5. Set `verifiedFixtures = true` only after the patterns have been checked against real notifications.
