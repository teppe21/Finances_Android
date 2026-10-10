# Receipt scanning

## Flow

1. Scan receipt opens a full-screen camera view. If the camera permission is missing, the screen explains why it is needed and asks only when you tap "Allow camera". A gallery image can be used instead.
2. CameraX captures a still image; ML Kit Text Recognition (bundled Latin model, `com.google.mlkit:text-recognition:16.0.1`) reads the text on the device.
3. `ReceiptParser` looks for:
   - the total after words such as `FIZETENDO`, `VEGOSSZEG`, `OSSZESEN`, `TOTAL`, `AMOUNT DUE`,
   - VAT (`AFA`, `VAT`, `TAX`), payment method (`KESZPENZ`/`CASH`, `BANKKARTYA`/`CARD`), date, a known store chain or the first line as merchant, and simple item lines.
   Amounts go through the same exact parser as CSV import, so `12.50 EUR` stays 12.50.
4. A review sheet shows merchant, total, currency, date, payment method and account. Nothing is saved until you confirm. If the total was not recognized the field is empty.
5. On save, a transaction is created and a receipt record with the merchant, date and amounts is stored. The image and the recognized text are not stored.

## Errors

If the camera cannot take a picture, or recognition fails, an error is shown. Earlier versions silently substituted a sample receipt in that case; that behaviour was removed. The sparkle button loads an explicitly labelled sample receipt for demonstrations, and transactions saved from it carry the note "Demo data".

## Limitations

- Latin script only; Hungarian accents are handled, but faded thermal paper, glare and long receipts reduce accuracy.
- The parser is heuristic. Always check the total before saving.
