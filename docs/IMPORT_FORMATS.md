# CSV import formats

Settings for a statement are detected from the file itself; there are no bank-specific profiles.

## Encoding and structure

- UTF-8 (with or without BOM). If the file is not valid UTF-8 it is read as Windows-1250, the usual encoding of Hungarian bank exports.
- Delimiter: whichever of `;`, `,` or tab appears most often in the header line.
- Quoted fields may contain the delimiter, doubled quotes (`""`) and line breaks.
- Files larger than 10 MB are rejected.

## Columns

The header row is matched case- and accent-insensitively:

| Meaning | Header words |
| --- | --- |
| Date | `datum`, `date`, `konyveles`, `erteknap` (first column if none) |
| Description | `leiras`, `description`, `partner`, `megjegyzes`, `kozlemeny`, `payee`, `merchant` (second column if none) |
| Signed amount | `osszeg`, `amount`, `ertek` |
| Debit / credit | `terheles`, `kiadas`, `debit` / `jovairas`, `bevetel`, `credit` |
| Currency | `penznem`, `currency`, `deviza` |
| Category | `kategoria`, `category` (kept as a suggestion only) |

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

Rows with an unreadable or ambiguous date, an unreadable or zero amount, an unsupported currency or fewer than two columns are not imported. The import screen shows how many rows were skipped for each reason.

## Re-importing

Each row gets a fingerprint from the account, date, amount, currency, description and its occurrence number among identical rows in the file. Importing the same file into the same account again adds nothing; two genuinely identical rows in one file are both kept. CSV rows are not merged with notification transactions.
