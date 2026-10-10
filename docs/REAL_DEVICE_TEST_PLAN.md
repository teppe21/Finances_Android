# Real-device test plan

Run on at least one recent Pixel or emulator image (Android 15/16) and one OEM device (Samsung One UI or Xiaomi HyperOS), with a 16 KB page-size emulator image for one pass. Use synthetic data (Settings > Add demo data) or a test account; nothing here requires real bank or brokerage accounts unless a step says "optional, real app".

Record for each step: device, Android version, result, notes.

## 1. First launch and financial basics

| # | Step | Expected |
| --- | --- | --- |
| 1.1 | Fresh install, open | Dashboard with zero balances and the "No transactions yet" empty state; no sample numbers |
| 1.2 | Add an expense of 1250 HUF and one of 12.50 EUR | Both listed with correct sign and currency; dashboard totals change |
| 1.3 | Delete one transaction | Gone from list and totals |
| 1.4 | Create a category and a keyword, add a matching transaction | Categorized automatically |
| 1.5 | Add an account in EUR with an opening balance | Accounts shows balance = opening + transactions |
| 1.6 | Switch display currency HUF → EUR → USD | Numbers change by the exchange rate, not only the symbol; with automatic rates off and no rates, a banner says amounts are excluded |
| 1.7 | Analytics: previous / next / "This month" | Month title updates, next is disabled on the current month |
| 1.8 | Budgets: add a monthly budget | Spent amount equals this month's confirmed expenses in that category |

## 2. Notifications

| # | Step | Expected |
| --- | --- | --- |
| 2.1 | More > Notification recognition > Set up | Disclosure dialog; "Not now" opens nothing and records nothing |
| 2.2 | Continue | Android's notification access screen opens (or a fallback screen on OEM builds) |
| 2.3 | Grant access, return | Status shows access yes, disclosure yes |
| 2.4 | "Try it": Purchase / Promotion / Login code | Recognized with amount 4,590 Ft; rejected as promotion; rejected as security message; nothing saved |
| 2.5 | Optional, real app: make a small Revolut card payment | One transaction with merchant and amount; status "Expense recorded" |
| 2.6 | Optional, real app: pay contactless with Google Wallet using a Revolut card | One transaction, not two |
| 2.7 | Optional, real app: two purchases of the same amount at different shops | Two transactions |
| 2.8 | Receive a promotional or referral notification from a supported app | No transaction; "Promotion ignored" in recent results |
| 2.9 | Receive a login code | No transaction; "Security message ignored" |
| 2.10 | Message from WhatsApp containing "5000 Ft" | Nothing recorded at all |
| 2.11 | Two accounts with institution "Revolut", then a Revolut payment | Appears in the review queue; confirming assigns it |
| 2.12 | Restart the phone, make a payment | Still recognized (listener rebinds) |
| 2.13 | Screen off and battery saver / OEM battery optimization on | Note whether notifications are still processed |
| 2.14 | Stop using notifications, then revoke in Android settings | No further processing |

## 3. Receipts and CSV

| # | Step | Expected |
| --- | --- | --- |
| 3.1 | Open Scan receipt without camera permission | Explanation first; permission prompt only after "Allow camera" |
| 3.2 | Normal Hungarian receipt | Total, VAT, cash/card recognized; editable before saving |
| 3.3 | Poor lighting / crumpled / long receipt | Total may be missing; field stays empty rather than guessed |
| 3.4 | Foreign receipt in EUR | Currency selectable; decimal total kept |
| 3.5 | Gallery image | Works; app does not lock on return |
| 3.6 | CSV in UTF-8 with semicolons and Hungarian dates | Rows imported |
| 3.7 | CSV in Windows-1250 | Accents correct |
| 3.8 | Malformed CSV (bad dates, text amounts) | Skipped rows listed by reason |
| 3.9 | Import the same CSV twice | Second import adds nothing |

## 4. Security

| # | Step | Expected |
| --- | --- | --- |
| 4.1 | Set PIN 1234 | Refused as too simple |
| 4.2 | Set a valid PIN, press Home, reopen | Lock screen; no financial data visible before it |
| 4.3 | Wrong PIN five times | 30-second lockout; persists after force-stop |
| 4.4 | Enable biometrics | Prompt on open; "Use PIN" works |
| 4.5 | Rotate on lock screen and when unlocked | No unexpected lock or unlock |
| 4.6 | Unlocked, open document picker / settings / browser from the app and return within 5 minutes | Not locked; picker result delivered |
| 4.7 | Recents screen | Preview is blank while lock is on |
| 4.8 | Developer options "Don't keep activities", or kill the process from Android Studio | Locked again on return |
| 4.9 | Delete all financial data | Data gone; app lock still enabled; watchlist kept |
| 4.10 | Forgot PIN > Erase everything | First-launch state |

## 5. Stocks

| # | Step | Expected |
| --- | --- | --- |
| 5.1 | Open Stocks without a key | Setup prompt; rest of the app unaffected |
| 5.2 | Enter an invalid key | "Finnhub rejected this key" |
| 5.3 | Enter a valid free key, search "apple", add AAPL | Row with price, change, timestamp |
| 5.4 | During US market hours | Label "Real-time" only if the timestamp is within 2 minutes; otherwise "Delayed" |
| 5.5 | Outside market hours | "Last close"; no automatic polling (check with a network monitor) |
| 5.6 | Leave the Stocks screen | No further requests |
| 5.7 | Airplane mode, refresh | Offline message; cached quote kept with an aged label |
| 5.8 | Open detail, choose 1M | Chart of real candles, or "not included in your plan" |
| 5.9 | Add 25+ symbols | Cap message |
| 5.10 | Restart app | Watchlist and order preserved |
| 5.11 | Remove a stock | Removed; transactions unaffected |

## 6. Platform

| # | Step | Expected |
| --- | --- | --- |
| 6.1 | Light and dark theme | Readable contrast, status bar icons visible |
| 6.2 | Font size 200%, TalkBack | Labels readable; transaction rows and charts announce content |
| 6.3 | 16 KB page-size emulator | App starts, camera and OCR work |
| 6.4 | Upgrade from 2.0.0 with existing data | Data kept, old PIN works once and is upgraded |
