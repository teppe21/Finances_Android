package com.teppe21.finances.native.receipt

import com.teppe21.finances.core.money.AmountParser
import com.teppe21.finances.core.utils.TextNormalizer
import com.teppe21.finances.domain.model.PaymentMethod
import com.teppe21.finances.domain.model.ReceiptItem
import com.teppe21.finances.domain.model.ReceiptScan
import java.time.DateTimeException
import java.time.LocalDate
import java.util.UUID

/**
 * Extracts merchant, date, total, VAT and payment method from OCR text.
 *
 * The total is only ever read from a line labelled as the amount payable. When no such line
 * exists, or the labelled lines disagree, the total is left empty so the user types it in;
 * the largest number on the receipt is never used as a stand-in.
 */
object ReceiptParser {

    /** Labels that mean "the amount to pay": highest priority. Digit look-alikes cover OCR slips. */
    private val STRONG_TOTAL_LABELS = listOf(
        Regex("fizetend[o0]"),
        Regex("v[e3]g[o0]sszeg"),
        Regex("fizet[e3]si\\s*[o0]sszeg"),
        Regex("grand\\s*t[o0]tal"),
        Regex("amount\\s*due"),
        Regex("total\\s*due"),
        Regex("balance\\s*due"),
        Regex("total\\s*to\\s*pay")
    )

    /** Generic total labels: used only when no strong label is present. */
    private val TOTAL_LABELS = listOf(
        Regex("(?<![a-z0-9])[o0]sszes[e3]n(?![a-z0-9])"),
        Regex("(?<![a-z0-9])t[o0]tal(?![a-z0-9])"),
        Regex("(?<![a-z0-9])summa(?![a-z0-9])")
    )

    /** A line containing any of these is never a payable total (subtotal, change, tendered cash, discounts). */
    private val NON_TOTAL_WORDS = listOf(
        "subtotal", "sub total", "sub-total", "reszosszeg", "netto", "net total",
        "kedvezmeny", "engedmeny", "discount", "visszajar", "valto", "change",
        "keszpenz", "kesz penz", "cash", "tendered", "kerekites", "rounding",
        "adoszam", "tax id", "tax no", "vat no", "szamla szam", "nyugta szam", "receipt no"
    )

    private val TAX_WORDS = listOf("afa", "vat", "tax")

    private val TAX_LABELS = listOf("afa", "vat", "tax")
    private val CASH_KEYWORDS = listOf("keszpenz", "keszp.", "cash")
    private val CARD_KEYWORDS = listOf("bankkartya", "kartya", "card", "visa", "mastercard", "pos")

    private val KNOWN_CHAINS = listOf(
        "lidl", "aldi", "spar", "interspar", "tesco", "auchan", "penny", "coop", "cba",
        "prima", "mol", "shell", "omv", "orlen", "dm", "rossmann", "muller", "ikea",
        "decathlon", "obi", "praktiker", "bauhaus", "benu", "kulcs patika", "gyogyszertar",
        "mcdonalds", "burger king", "kfc", "subway", "starbucks"
    )

    /**
     * A money amount: grouped (`1 249`, `1.249,50`, `12,345.60`) or plain (`1249`, `12.50`), not part of a
     * longer digit run. Dates, times and barcodes are screened out afterwards.
     */
    private val AMOUNT = Regex("(?<![\\d.,])(?:\\d{1,3}(?:[ \\u00A0.,']\\d{3})+|\\d+)(?:[.,]\\d{1,2})?(?![\\d])")
    private val CURRENCY_TOKEN = Regex("(?i)(?<![a-z])(ft|huf|eur|usd|gbp|chf)(?![a-z])|€|\\$|£")
    private val DATE_LIKE = Regex("\\d{4}[./-]\\d{1,2}[./-]\\d{1,2}|\\d{1,2}[./-]\\d{1,2}[./-]\\d{2,4}|\\d{1,2}:\\d{2}")

    fun parse(rawText: String, imageUri: String? = null): ReceiptScan {
        val lines = rawText.replace("\r\n", "\n").lines().map { it.trim() }.filter { it.isNotEmpty() }

        val merchant = extractMerchant(lines)
        val totalMinor = extractTotal(lines)
        val currency = extractCurrency(lines, totalMinor)
        val taxMinor = extractTax(lines)
        val paymentMethod = extractPaymentMethod(rawText)
        val date = extractDate(lines)
        val items = extractItems(lines)

        var confidence = 0.0f
        if (totalMinor != null && totalMinor > 0L) confidence += 0.45f
        if (!merchant.isNullOrBlank()) confidence += 0.25f
        if (date != null) confidence += 0.15f
        if (paymentMethod != PaymentMethod.UNKNOWN) confidence += 0.10f
        if (items.isNotEmpty()) confidence += 0.05f

        return ReceiptScan(
            id = UUID.randomUUID().toString(),
            imageUri = imageUri,
            merchant = merchant,
            date = date ?: LocalDate.now(),
            totalMinor = totalMinor,
            subtotalMinor = null,
            taxMinor = taxMinor,
            currency = currency,
            paymentMethod = paymentMethod,
            items = items,
            rawOcrText = rawText,
            confidence = confidence.coerceIn(0.1f, 1.0f)
        )
    }

    private fun fold(line: String): String = TextNormalizer.removeDiacritics(line).lowercase()

    private fun extractMerchant(lines: List<String>): String? {
        // 1. Check known chains in first 8 lines
        for (i in 0 until minOf(lines.size, 8)) {
            val norm = fold(lines[i])
            for (chain in KNOWN_CHAINS) {
                if (norm.contains(chain)) {
                    return chain.replaceFirstChar { it.uppercase() }
                }
            }
        }

        // 2. Fallback to clean top line
        val skip = listOf("nyugta", "szamla", "penztar", "egyszerusitett", "adoszam", "blokk", "receipt")
        for (i in 0 until minOf(lines.size, 4)) {
            val raw = lines[i]
            val norm = fold(raw)
            if (raw.length > 2 && skip.none { norm.contains(it) }) {
                return TextNormalizer.cleanMerchantName(raw)
            }
        }

        return null
    }

    private data class TotalCandidate(val priority: Int, val minor: Long)

    /** The payable total in minor units, or null when it cannot be read with confidence. */
    internal fun extractTotal(lines: List<String>): Long? {
        val candidates = mutableListOf<TotalCandidate>()
        for ((index, line) in lines.withIndex()) {
            val folded = fold(line)
            if (NON_TOTAL_WORDS.any { folded.contains(it) }) continue

            val strong = STRONG_TOTAL_LABELS.mapNotNull { it.find(folded) }.minByOrNull { it.range.first }
            val generic = TOTAL_LABELS.mapNotNull { it.find(folded) }.minByOrNull { it.range.first }
            val label = strong ?: generic ?: continue
            // "AFA OSSZESEN" is a VAT subtotal; "TOTAL incl. VAT" is the payable amount.
            if (TAX_WORDS.any { word ->
                    val at = folded.indexOf(word)
                    at in 0 until label.range.first
                }
            ) continue

            val afterLabel = line.drop(label.range.last + 1)
            var amounts = amountsIn(afterLabel)
            if (amounts.isEmpty() && index + 1 < lines.size && isAmountOnlyLine(lines[index + 1])) {
                amounts = amountsIn(lines[index + 1])
            }
            // The payable figure is the right-most amount on a "LABEL ... 1 249 Ft" line.
            val minor = amounts.lastOrNull()?.takeIf { it > 0L } ?: continue
            candidates += TotalCandidate(if (strong != null) 2 else 1, minor)
        }

        val best = candidates.maxOfOrNull { it.priority } ?: return null
        val values = candidates.filter { it.priority == best }.map { it.minor }.distinct()
        // Two different payable totals at the same priority: ask the user instead of picking one.
        return values.singleOrNull()
    }

    /** Currency token on the total's own line wins; otherwise the receipt's single currency; otherwise HUF. */
    private fun extractCurrency(lines: List<String>, totalMinor: Long?): String {
        fun normalize(token: String): String? = when (token.uppercase()) {
            "FT", "HUF" -> "HUF"
            "EUR", "€" -> "EUR"
            "USD", "$" -> "USD"
            "GBP", "£" -> "GBP"
            "CHF" -> "CHF"
            else -> null
        }

        val tokens = lines.flatMap { line -> CURRENCY_TOKEN.findAll(line).mapNotNull { normalize(it.value) }.toList() }
        val distinct = tokens.distinct()
        if (distinct.size == 1) return distinct.single()
        if (distinct.size > 1 && totalMinor != null) {
            // Several currencies on one receipt: trust the one printed on a total line.
            for (line in lines) {
                val folded = fold(line)
                if (STRONG_TOTAL_LABELS.any { it.containsMatchIn(folded) } || TOTAL_LABELS.any { it.containsMatchIn(folded) }) {
                    CURRENCY_TOKEN.find(line)?.let { m -> normalize(m.value)?.let { return it } }
                }
            }
        }
        return "HUF"
    }

    /** All plausible money amounts in [text], in order, as minor units. Dates, times, percentages and barcodes are skipped. */
    private fun amountsIn(text: String): List<Long> {
        val withoutDates = DATE_LIKE.replace(text, " ")
        val result = mutableListOf<Long>()
        for (match in AMOUNT.findAll(withoutDates)) {
            val next = withoutDates.getOrNull(match.range.last + 1)
            if (next == '%') continue
            val digits = match.value.count { it.isDigit() }
            // Long digit runs without any separator are barcodes, tax numbers or card numbers.
            if (digits >= 9 && match.value.none { !it.isDigit() }) continue
            AmountParser.parseMinor(match.value.trim())?.let { result += it }
        }
        return result
    }

    private fun isAmountOnlyLine(line: String): Boolean {
        val remainder = CURRENCY_TOKEN.replace(AMOUNT.replace(line, ""), "").trim(':', ' ', '\t', '-', '.')
        return remainder.isEmpty() && amountsIn(line).isNotEmpty()
    }

    private fun extractPaymentMethod(text: String): PaymentMethod {
        val norm = fold(text)
        val hasCash = CASH_KEYWORDS.any { norm.contains(it) }
        val hasCard = CARD_KEYWORDS.any { norm.contains(it) }

        return when {
            hasCash && !hasCard -> PaymentMethod.CASH
            hasCard && !hasCash -> PaymentMethod.CARD
            else -> PaymentMethod.UNKNOWN
        }
    }

    private fun extractTax(lines: List<String>): Long? {
        for (line in lines) {
            val norm = fold(line)
            if (TAX_LABELS.any { norm.contains(it) }) {
                val amount = amountsIn(line).lastOrNull()
                if (amount != null) return amount
            }
        }
        return null
    }

    private val YEAR_FIRST_DATE = Regex("(20\\d{2})[./-](\\d{1,2})[./-](\\d{1,2})")
    private val DAY_FIRST_DATE = Regex("(?<!\\d)(\\d{1,2})\\.(\\d{1,2})\\.(20\\d{2})(?!\\d)")

    private fun extractDate(lines: List<String>): LocalDate? {
        for (line in lines) {
            YEAR_FIRST_DATE.find(line)?.let { m ->
                dateOrNull(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())?.let { return it }
            }
            DAY_FIRST_DATE.find(line)?.let { m ->
                dateOrNull(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt())?.let { return it }
            }
        }
        return null
    }

    private fun dateOrNull(year: Int, month: Int, day: Int): LocalDate? =
        try {
            LocalDate.of(year, month, day)
        } catch (_: DateTimeException) {
            null
        }

    private fun extractItems(lines: List<String>): List<ReceiptItem> {
        val items = mutableListOf<ReceiptItem>()
        val regex = Regex("^([A-Za-zÁÉÍÓÖŐÚÜŰáéíóöőúüű0-9\\s/.,%-]{3,40})\\s+(\\d{1,3}(?:[.,\\s]\\d{3})*(?:[.,]\\d{1,2})?)\\s*(?:Ft|HUF)?$", RegexOption.IGNORE_CASE)

        for (line in lines) {
            val match = regex.find(line)
            if (match != null) {
                val name = match.groupValues[1].trim()
                val norm = fold(name)

                if (STRONG_TOTAL_LABELS.any { it.containsMatchIn(norm) } ||
                    TOTAL_LABELS.any { it.containsMatchIn(norm) } ||
                    TAX_LABELS.any { norm.contains(it) } ||
                    norm.contains("nyugta")
                ) {
                    continue
                }

                val priceMinor = AmountParser.parseMinor(match.groupValues[2].trim())
                if (priceMinor != null && priceMinor > 0L) {
                    items.add(ReceiptItem(name = name, totalPriceMinor = priceMinor))
                }
            }
        }

        return items
    }
}
