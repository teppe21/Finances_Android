# Receipt scanning

## Flow

1. Scan receipt opens a full-screen camera view. If the camera permission is missing, the screen explains why it is needed and asks only when you tap "Allow camera". A gallery image can be used instead.
2. CameraX captures a still image in memory. The JPEG is decoded off the main thread at no more than about 2560 px on its longest side, and CameraX's `rotationDegrees` is handed to ML Kit, which applies it once (the bytes are not pre-rotated). A gallery image has its EXIF orientation (rotation and mirror) applied once to the decoded bitmap instead. ML Kit Text Recognition (bundled Latin model, `com.google.mlkit:text-recognition:16.0.1`) then reads the text on the device. One recognizer is created per scan; it and the bitmap are released only after ML Kit has finished with them, also when the scan is cancelled.
3. `ReceiptParser` looks for:
   - the total only on a line labelled as the amount payable: `FIZETENDO`, `VEGOSSZEG`, `GRAND TOTAL`, `AMOUNT DUE` and similar rank first, plain `OSSZESEN` / `TOTAL` second (accents, case and common OCR digit slips such as `FIZETEND0` are tolerated; the amount may be on the next line). Lines about VAT totals, subtotals, discounts, change, cash tendered and tax numbers are never a total, dates, times, percentages and barcodes are not amounts, and if two different totals have the same rank the field is left empty. There is no "largest number" fallback,
   - VAT (`AFA`, `VAT`, `TAX`), payment method (`KESZPENZ`/`CASH`, `BANKKARTYA`/`CARD`), date, a known store chain or the first line as merchant, and simple item lines.
   Amounts go through the same exact parser as CSV import, so `12.50 EUR` stays 12.50.
4. A review sheet shows merchant, total, currency, date, payment method and account. Nothing is saved until you confirm. If the total was not recognized the field is empty.
5. On save, a transaction is created and a receipt record with the merchant, date and amounts is stored. The image and the recognized text are not stored.

## Errors

If the camera cannot take a picture, or recognition fails, an error is shown. Earlier versions silently substituted a sample receipt in that case; that behaviour was removed. The sparkle button loads an explicitly labelled sample receipt for demonstrations, and transactions saved from it carry the note "Demo data".

## Limitations

- Latin script only; Hungarian accents are handled, but faded thermal paper, glare and long receipts reduce accuracy.
- The parser is heuristic. Always check the total before saving.
