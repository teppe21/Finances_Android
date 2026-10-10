# Instructions for Play reviewers

Text for Play Console > App content > App access. No real bank or brokerage account is needed.

---

Finances stores personal finance data on the device only. No login is required.

**Sample data.** Open the app, go to More > Settings > Data management and tap "Add demo data". This adds clearly labelled synthetic accounts and transactions (merchants named "Demo ..."). "Remove demo data" removes them again.

**Transactions.** Bottom bar > Transactions > + to add a transaction. Analytics shows a month view with previous/next navigation.

**Receipt scanning.** Home > Scan receipt. The camera permission is requested only after the explanation screen. The sparkle button loads a labelled sample receipt if you do not have a paper receipt.

**CSV import.** Home > Import CSV and choose any CSV with Date, Description and Amount columns, for example:

```
Date;Description;Amount;Currency
2026-09-01;Demo Grocery;-4590;HUF
2026-09-02;Demo Salary;520000;HUF
```

**Notification access.** More > Notification recognition > Set up notification access shows the disclosure. "Not now" closes it without changes; "Continue" opens the Android settings screen. Without a supported banking app installed, use "Try it without a real notification" (Purchase, Promotion, Login code) to see what is recognized and rejected; nothing is saved by these samples.

**App lock.** Settings > Security > Set PIN (4 to 8 digits; simple PINs like 1234 are refused). Leave the app and return to see the lock screen. "Forgot PIN?" offers a full reset.

**Stocks.** More > Stocks. Quotes need a Finnhub API key owned by the user; the app does not include one. Without a key the screen explains how to get one, and the rest of the app works normally. If you want to test quotes, a free key can be created at https://finnhub.io/register. The feature is read-only market information; there is no trading.

**Data deletion.** Settings > Data management > Delete all financial data (keeps the app lock and settings) or Reset app completely.

**Privacy policy.** Settings > Privacy > Privacy policy.
