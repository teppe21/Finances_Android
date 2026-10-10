# Financial features declaration

Every app on Google Play, including apps in closed or open testing, must complete the Financial features declaration in Play Console (App content > Financial features), even to state that it offers none ([Play Console Help](https://support.google.com/googleplay/android-developer/answer/13849271)). Google's Financial Services policy describes financial products and services as those related to the management or investment of money and cryptocurrencies, including personalized advice ([policy](https://support.google.com/googleplay/android-developer/answer/9876821)).

## What the app does

| Capability | Present |
| --- | --- |
| Records the user's own income and expenses, budgets, categories | yes |
| Reads notifications from banking apps to recognize the user's own card payments | yes |
| Imports the user's own bank statements (CSV) | yes |
| Shows currency exchange rates for conversion | yes |
| Shows stock quotes and price history for a user-chosen watchlist, from the user's own Finnhub key | yes |
| Holds, moves, lends or invests money; payments, transfers, wallets | no |
| Connects to bank accounts or brokerages (credentials, open banking) | no |
| Trading, order execution, crypto, CFDs | no |
| Loans, credit, insurance, buy-now-pay-later | no |
| Personalized investment advice, buy/sell signals | no |

## Recommended answer

The app is a personal budgeting tool with read-only market information. It does not provide a regulated financial service. The likely appropriate answer is to declare that the app offers no financial products or services in the regulated categories (banking, loans, payments and transfers, trading, insurance, crypto), and, if the form offers a category for personal finance management, budgeting or financial information tools, to select that one.

The exact list of options in the form was not available when this was written and the wording changes, so the person submitting must:

1. Open the form in Play Console and read every option.
2. Not select anything related to trading, investing services, payments, wallets, money transfer, loans or banking. The stock watchlist only displays third-party market data and cannot trade.
3. Select a budgeting / personal finance management / financial information option if one exists.
4. Keep a note of the choices made and the date.

A third-party guide claims that apps declaring financial features must be published from an organization developer account. This could not be confirmed in Google's documentation; check the current requirements for the account type you use before submitting.

## Related declarations

- Data safety: see [PLAY_CONSOLE_COMPLIANCE.md](PLAY_CONSOLE_COMPLIANCE.md).
- Notification access is not a restricted permission declaration on Play, but the app relies on a prominent disclosure and user consent, which reviewers may check; see [PLAY_REVIEW_INSTRUCTIONS.md](PLAY_REVIEW_INSTRUCTIONS.md).

Status: **not submitted.** This must be done manually in Play Console.
