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

- Dates: `2026-03-28`, `2026.03.28.`, `2026/03/28`, `28.03.2026`, optionally followed by a time. Slash formats with the day or month first (`03/04/2026`) are ambiguous and rejected.
- Amounts: exact decimal parsing. `1 249,50`, `1.234,56`, `1,234.56`, `-19,99`, `22,500` (thousands) and `4.500` (thousands) are supported; a single separator followed by one or two digits is a decimal separator. Currency text such as `Ft`, `HUF`, `€` inside the cell is ignored.
- Currencies: HUF (or `Ft`), EUR, USD, GBP, CHF. Other currencies are skipped.

## Skipped rows

Rows with an unreadable date, an unreadable or zero amount, an unsupported currency or fewer than two columns are not imported. The import screen shows how many rows were skipped for each reason.

## Re-importing

Each row gets a fingerprint from the account, date, amount, currency, description and its occurrence number among identical rows in the file. Importing the same file into the same account again adds nothing; two genuinely identical rows in one file are both kept. CSV rows are not merged with notification transactions.
