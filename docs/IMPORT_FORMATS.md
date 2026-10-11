# CSV import formats

Settings for a statement are detected from the file itself. There are no per-bank profiles, but the columns some banks add (a transaction type, a completion state, a category) are used when present.

## Encoding and structure

- UTF-8 (with or without BOM). If the file is not valid UTF-8 it is read as Windows-1250, the usual encoding of Hungarian bank exports.
- Delimiter: whichever of `;`, `,` or tab appears most often in the header line.
- Quoted fields may contain the delimiter, doubled quotes (`""`) and line breaks.
- Files larger than 10 MB are rejected.

## Columns

The header row is matched case- and accent-insensitively. Each column gets at most one role, and date columns are claimed first, so `Értéknap` (value date) is never taken as the amount column even though it contains `ertek` (value). Before schema 6 it was, and every row of such a file was skipped as "unreadable amount".

| Meaning | Header words |
| --- | --- |
| Date | `datum`, `date`, `konyveles`, `erteknap`, `teljesites` (first matching column; first column if none) |
| Type | `tipus`, `type`, Wise `TransferWise ID` (the `CARD-`/`TRANSFER-`/`BALANCE-` prefix is used) |
| State | `state`, `status`, `allapot` |
| Currency | `penznem`, `currency`, `deviza` |
| Category | `kategoria`, `category` |
| Debit / credit | `terheles`, `kiadas`, `debit` / `jovairas`, `bevetel`, `credit` |
| Signed amount | `osszeg`, `amount`, `ertek` |
| Description | every column matching `leiras`, `description`, `megnevezes`, `partner`, `megjegyzes`, `kozlemeny`, `payee`, `payer`, `merchant`, `reference`, `kedvezmenyezett`, `ellenoldal` (second column if none) |

Account numbers and balances (`szamlaszam`, `account`, `iban`, `balance`, `egyenleg`) are never descriptions. All non-empty description cells are joined with ` · ` (a cell already contained in an earlier one, such as the repeated Wise merchant, is dropped), so a transfer keeps both the partner and the remark: `Bérbeadó Minta · Bérleti díj szeptember`. The merchant name comes from the first non-empty description cell with card noise removed (see below). When a row has no description at all, the bank's type (`Számlavezetési díj`) is used as its text.

If there is no currency column, the currency of the selected account is used.

## Values

- Dates:
  - Year first, always unambiguous: `2026-03-28`, `2026.03.28.`, `2026/03/28`.
  - Dotted with the year last is day-first: `28.03.2026`.
  - `28/03/2026`, `28-03-2026` (and month-first `03/28/2026`) are read from the cell when only one order gives a real date. When both orders are real (`03/04/2026`), the order is taken from other rows of the same file that prove it (a `28/03/2026` row proves day-first). Without such a row, or when the file proves both orders, the row is skipped as "ambiguous date" and counted; it is never guessed.
  - Impossible dates (`2026-02-30`, `31/04/2026`) are skipped and do not count as evidence. A time after the date is ignored.
- Amounts: exact decimal parsing, no floating point. Grouping must be regular (1-3 digits, then groups of exactly three, one separator character) and a decimal separator is followed by one or two digits. `1 249,50`, `1.234,56`, `1,234.56`, `-19,99`, `22,500` and `4.500` (thousands) are accepted; `12,34.56`, `0,500`, `1.234.56`, `12.3456` and `-5-` are rejected. If other cells of the file prove a convention (`1.234,56` proves comma-decimal, `1,234.56` proves dot-decimal) it is applied to the whole file, so in a dot-decimal file a lone `1.500` is rejected instead of being read as 1500. Currency text such as `Ft`, `HUF`, `€` inside the cell is ignored.
- Currencies: HUF (or `Ft`), EUR, USD, GBP, CHF. Other currencies are skipped.

## Skipped rows

Rows with an unreadable or ambiguous date, an unreadable or zero amount, an unsupported currency or fewer than two columns are not imported, and neither are rows the file marks as `REVERTED`, `DECLINED`, `FAILED` or `CANCELLED` (the Revolut `State` column; Hungarian `elutasítva`, `sztornózva`, `visszavonva`). `PENDING` rows are imported. The import screen shows how many rows were skipped for each reason.

## Re-importing

Each row gets a fingerprint from the account, date, amount, currency, description and its occurrence number among identical rows in the file. Importing the same file into the same account again adds nothing; two genuinely identical rows in one file are both kept. CSV rows are not merged with notification transactions.

The description in the fingerprint is the single column earlier versions read (the first description-like column, or `Imported transaction` when it was empty), not the joined description shown in the app. That keeps re-importing a file imported before schema 6 from adding duplicates.

## After the import

A summary shows how many rows were imported, skipped (already imported, or unreadable/not completed), categorized, still uncategorized, and recognized as transfers (between own accounts, and with people). "Review uncategorized" opens the Uncategorized screen (also under More).

## Categorization

Every row goes through the same rules as any other transaction (`CategorizeTransactionUseCase.classify`), with the bank's type and category columns as extra evidence.

### Transaction kinds

| Kind | Meaning | Counted as income or spending |
| --- | --- | --- |
| `EXPENSE`, `INCOME` | By the sign of the amount | yes |
| `TRANSFER` | Between own accounts, currency exchanges, top-ups, balance migrations, savings pockets, cash withdrawals, credit-card repayments | no |
| `PERSONAL_TRANSFER` | Money sent to or received from another person | no |

The kind follows the category: `transfers` ("Between own accounts") and `cash` make a `TRANSFER`, `personal_transfers` ("Transfers with people") a `PERSONAL_TRANSFER`, any other category income or expense by sign. Moving a transaction to another category by hand changes its kind the same way, so "this transfer to a person was really rent" is one category change (or one rule). Transfers have no merchant (a person's name is not a merchant) and never appear in spending by category, top merchants, budgets, averages or the trend. Analytics shows them on their own card: money moved between own accounts (outgoing legs only, so a move between two tracked accounts is not counted twice), sent to people, received from people. When both legs of an own-account move are imported (opposite amount, same currency, another account, at most 3 days apart), they share a `transferId`. Exchanges have two currencies and are not linked.

ATM withdrawals are transfers into cash, on the assumption that cash spending is recorded separately (the default Cash Wallet account). If you never record cash spending, move the `Cash & ATM` transactions to a spending category or add a rule.

### Matching

- Text is folded before matching: lowercase, accents removed (`Átváltás` = `atvaltas`), apostrophes dropped (`McDonald's` = `MCDONALDS`), other punctuation becomes a space, repeated spaces collapse.
- `contains` rules match at the start of a word, so `ber` never matches `Uber`. Keywords of three characters or fewer must be a whole word: `mol` matches `MOL TÖLTŐÁLLOMÁS` and `MOL-nál` but not `Molnár`; `bér` matches `Havi bér` but not `Bérleti díj`.
- A multi-word keyword also matches its words written together: `burger king` matches `BURGERKING`, `media markt` matches `MEDIAMARKT`.
- `word` rules must match a whole word (`dm`, `digi`, `nav`); `exact` rules must equal the whole merchant name or the whole description (`Bolt` is the ride service, `Sarki bolt` is a corner shop); `regex` rules see both the original and the folded text.
- The highest priority wins; on a tie the longer keyword wins (`uber eats` before `uber`), then the newer rule.
- Rules for income-only categories (salary, pension) never apply to money going out: `fizetés` means both "salary" and "payment".
- A row whose bank type says card purchase or direct debit (`CARD_PAYMENT`, `Vásárlás kártyával`, `Csoportos beszedés`, Wise `CARD-`) is never made a transfer by a built-in keyword.

| Priority | Rules |
| --- | --- |
| 50 | Your own rules (always first) |
| 30 | Own-account moves: `Exchanged to/from`, `Currency exchange`, `Átváltás`, `Balance migration`, `Top-up by`, `Saját számlák közötti`, `Átvezetés`, pockets and savings, `Converted` (Wise), credit-card repayment |
| 22 | Bank fees (`számlavezetési`, `kártyadíj`, `fee`, `jutalék`) |
| 20 | Cash (`ATM`, `készpénzfelvétel`, `cash withdrawal`) |
| 15 | The file's own category column, when it names a built-in category (`Groceries`, `Élelmiszer`, `Restaurants`, `Utilities`, `Transfers`) |
| 10 | Merchants and categories: groceries, dining and delivery, transport and fuel, utilities and telecom, subscriptions, health and drugstores, shopping, electronics, entertainment, government and taxes (`Magyar Államkincstár`, `NAV`, `SZJA`), interest, salary |
| 5 | Generic transfer wording: `Átutalás`, `Transfer to/from`, `Payment from`, `Sent money to`, `Received money from` (a person, unless something more specific matched: `Átutalás · MVM Next` is a utility bill) |

The bank's type text (Revolut `EXCHANGE`, `TOPUP`, `ATM`, `FEE`, `TRANSFER`; OTP `Készpénzfelvétel`, `Számlavezetési díj`) is matched together with the description, so it decides when the description alone cannot. When nothing matches, credits fall back to `Income` and debits to `Other / Uncategorized`.

The built-in rules are in `domain/model/BuiltIns.kt`; fresh installs get all of them and the 5 → 6 migration adds the new ones (see [DATA_MODEL.md](DATA_MODEL.md)).

### Merchant names

Card wording and terminal noise are removed from the displayed merchant name (the description keeps the original): `CARD PAYMENT TO`, `Card transaction of 12.50 EUR issued by`, `Kártyás vásárlás`, `VÁSÁRLÁS POS:`, card masks (`*1234`), dates, store and terminal numbers, and trailing towns and country codes. `SPAR 1234 BUDAPEST HU` and `ERSTE KARTYAS VASARLAS SPAR 3311 DEBRECEN` both become `SPAR`, so they group together in top merchants. A name is never cleaned away entirely.

### Resolving what is left

- **Uncategorized** (More, or from the import summary) lists the remaining expense and income rows grouped by merchant. One choice puts the whole group in a category; "Remember for the future" (on by default) also saves the merchant as a keyword rule, editable before saving, and applies it to every existing transaction it matches.
- **Change category** on a transaction offers the same "remember" option, so a rule from one transaction is one tap.
- **Re-apply rules** runs the current rules over all transactions. It never changes a category you chose yourself (manual entries, receipts and anything you changed by hand are marked `categorySetByUser`), and it never moves a categorized transaction back to `Other`: the bank's type column is not stored, so a row it placed at import could otherwise lose its category.

## Measured on synthetic statements

`CategorizationHarnessTest` imports synthetic statements in the style of Revolut, OTP, Erste, MBH and Wise exports (`support/SyntheticStatements.kt`: 90 rows with invented names and amounts, each with the kind and category a person would choose) through the real parser, import mapping and ingestion pipeline, and writes `app/build/reports/categorization-harness.txt`. The column layouts imitate real exports; they are not copies of real files, so a real file can still differ.

| | Before (schema 5 rules and code) | After |
| --- | --- | --- |
| Rows imported (of 90) | 71 (all 19 OTP-style rows skipped: `Értéknap` read as the amount) | 89 (the reverted row is skipped) |
| Uncategorized (`Other`) | 38 of 71 = 53.5 % | 4 of 89 = 4.5 % (all four are merchants a person would also leave there) |
| Transfers and exchanges counted as income or spending | 21 of 21 | 0 |
| Wrong category | 47 = 66.2 % | 0 |

Root causes found with the "before" run: the amount-column bug above; one description column only (MBH-style files read the empty remark column and every row became `Imported transaction`; OTP card rows had an empty partner); the bank's type and state columns ignored; direction taken from the sign only, so a row in the `Transfers` category was still spending; short keywords matching inside words (`mol` in `Molnár` made a transfer to a person "transport"); `McDonald's` and `BURGERKING` not matching their keywords; missing keywords (exchanges, top-ups, ATM, fees, interest, taxes, utilities, electronics). Accent and case folding already worked, rules did run on CSV rows, and merchant cleaning removed nothing the rules needed.

The "after" figure is optimistic: the keyword lists were written while looking at these rows. A **holdout** statement (36 rows, written after the lists were fixed and not used to tune them) gives the honest estimate. On its first run 15 of 36 (41.7 %) stayed in `Other` where a person would leave 5, and 2 rows had the wrong kind (a credit-card repayment counted as spending, and a `To <name>` transfer without a type column). The credit-card repayment pattern was then added because it double-counted spending; the holdout now reports 14 of 36 (38.9 %) and 1 wrong kind. The rest are unknown merchants (`Vapiano`, `Douglas`, `iStyle`, `H&M`), compound words (`Jégbüfé`, `Bankkártya éves díj`) and a mortgage repayment. Keyword lists cannot know every shop; the review screen and one-tap rules are there for that, and each rule sorts every later import of that merchant.
