package com.teppe21.finances.native.csv

import com.teppe21.finances.core.money.AmountParser
import com.teppe21.finances.core.money.Money
import com.teppe21.finances.core.money.SeparatorStyle
import com.teppe21.finances.core.utils.TextNormalizer
import com.teppe21.finances.domain.model.SupportedCurrencies
import com.teppe21.finances.domain.model.TransactionDirection
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.time.DateTimeException
import java.time.LocalDate

data class CsvColumnMapping(
    val dateCol: Int = 0,
    val descCol: Int = 1,
    val amountCol: Int? = null,
    val debitCol: Int? = null,
    val creditCol: Int? = null,
    val currencyCol: Int? = null,
    val categoryCol: Int? = null
)

data class ParsedCsvRow(
    /** 1-based line number of the record in the file, for error messages. */
    val line: Int,
    val date: LocalDate,
    val description: String,
    val merchant: String?,
    /** Signed amount, scale 2. */
    val amountMinor: Long,
    val currency: String,
    val direction: TransactionDirection,
    val categorySuggestion: String?
)

enum class CsvSkipReason {
    INVALID_DATE,
    /** A numeric day/month date that is valid both ways and the file gives no evidence which. */
    AMBIGUOUS_DATE,
    INVALID_AMOUNT,
    ZERO_AMOUNT,
    UNSUPPORTED_CURRENCY,
    TOO_FEW_COLUMNS
}

/** Order of the first two numbers in a `dd/MM/yyyy` or `MM/dd/yyyy` style date. */
enum class DateOrder { DAY_FIRST, MONTH_FIRST }

/** Outcome of reading one date cell. */
sealed interface DateReading {
    data class Valid(val date: LocalDate) : DateReading
    /** Valid as both day-first and month-first, and no order was supplied. */
    data object Ambiguous : DateReading
    data object Invalid : DateReading
}

data class CsvParseResult(
    val rows: List<ParsedCsvRow>,
    val skipped: Map<CsvSkipReason, Int>,
    val charset: String
) {
    val skippedCount: Int get() = skipped.values.sum()
}

/**
 * Bank statement CSV import. Rows that cannot be read exactly (unknown date format,
 * unreadable amount, unsupported currency) are skipped and counted, never guessed.
 */
object CsvParser {

    private const val MAX_BYTES = 10 * 1024 * 1024

    /** UTF-8 when valid (BOM stripped), otherwise Windows-1250, common for Hungarian banks. */
    fun decode(bytes: ByteArray): Pair<String, String> {
        val utf8 = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            val text = utf8.decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
            text to "UTF-8"
        } catch (e: CharacterCodingException) {
            String(bytes, Charset.forName("windows-1250")) to "windows-1250"
        }
    }

    /** RFC 4180 style records: quoted fields may contain delimiters, quotes ("") and newlines. */
    fun records(text: String, delimiter: Char): List<Pair<Int, List<String>>> {
        val out = mutableListOf<Pair<Int, List<String>>>()
        val field = StringBuilder()
        var fields = mutableListOf<String>()
        var inQuotes = false
        var line = 1
        var recordLine = 1
        var i = 0
        fun endRecord() {
            fields.add(field.toString().trim())
            field.clear()
            if (fields.any { it.isNotEmpty() }) out += recordLine to fields
            fields = mutableListOf()
        }
        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes && c == '"' && i + 1 < text.length && text[i + 1] == '"' -> { field.append('"'); i++ }
                c == '"' -> inQuotes = !inQuotes
                !inQuotes && c == delimiter -> { fields.add(field.toString().trim()); field.clear() }
                !inQuotes && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    endRecord()
                    line++
                    recordLine = line
                }
                else -> {
                    if (c == '\n') line++
                    field.append(c)
                }
            }
            i++
        }
        if (field.isNotEmpty() || fields.isNotEmpty()) endRecord()
        return out
    }

    fun tokenizeLine(line: String, delimiter: Char): List<String> =
        records(line, delimiter).firstOrNull()?.second ?: emptyList()

    fun detectDelimiter(content: String): Char {
        val firstLine = content.lineSequence().firstOrNull { it.isNotBlank() } ?: return ','
        val counts = listOf(';', ',', '\t').associateWith { d -> firstLine.count { it == d } }
        return counts.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.key ?: ','
    }

    fun detectMapping(headers: List<String>): CsvColumnMapping {
        val norm = headers.map { TextNormalizer.removeDiacritics(it).lowercase().trim().replace("\"", "") }

        fun findIndex(vararg keywords: String): Int =
            norm.indexOfFirst { col -> keywords.any { kw -> col.contains(kw) } }

        val dateIdx = findIndex("datum", "date", "konyveles", "erteknap")
        val descIdx = findIndex("leiras", "description", "partner", "megjegyzes", "kozlemeny", "payee", "merchant")
        val amountIdx = findIndex("osszeg", "amount", "ertek")
        val debitIdx = findIndex("terheles", "kiadas", "debit")
        val creditIdx = findIndex("jovairas", "bevetel", "credit")
        val currIdx = findIndex("penznem", "currency", "deviza")
        val catIdx = findIndex("kategoria", "category")

        return CsvColumnMapping(
            dateCol = if (dateIdx != -1) dateIdx else 0,
            descCol = if (descIdx != -1) descIdx else 1,
            amountCol = amountIdx.takeIf { it != -1 },
            debitCol = debitIdx.takeIf { it != -1 },
            creditCol = creditIdx.takeIf { it != -1 },
            currencyCol = currIdx.takeIf { it != -1 },
            categoryCol = catIdx.takeIf { it != -1 }
        )
    }

    fun parse(
        inputStream: InputStream,
        defaultCurrency: String = "HUF",
        mappingOverride: CsvColumnMapping? = null
    ): CsvParseResult {
        val bytes = inputStream.readBytesLimited(MAX_BYTES)
        val (text, charset) = decode(bytes)
        return parseText(text, defaultCurrency, mappingOverride, charset)
    }

    fun parseText(
        text: String,
        defaultCurrency: String = "HUF",
        mappingOverride: CsvColumnMapping? = null,
        charset: String = "UTF-8"
    ): CsvParseResult {
        val delimiter = detectDelimiter(text)
        val records = records(text.removePrefix("\uFEFF"), delimiter)
        if (records.size < 2) return CsvParseResult(emptyList(), emptyMap(), charset)

        val mapping = mappingOverride ?: detectMapping(records.first().second)
        val rows = mutableListOf<ParsedCsvRow>()
        val skipped = mutableMapOf<CsvSkipReason, Int>()
        fun skip(reason: CsvSkipReason) { skipped[reason] = (skipped[reason] ?: 0) + 1 }

        val dataRecords = records.drop(1).filter { it.second.size >= 2 }
        // File-level evidence: only cells that can be read one way prove the order or separator
        // style for the cells that could be read either way. Invalid cells prove nothing.
        val dateOrder = inferDateOrder(dataRecords.map { it.second.getOrNull(mapping.dateCol) })
        val amountColumns = listOfNotNull(mapping.amountCol, mapping.debitCol, mapping.creditCol)
        val amountStyle = inferAmountStyle(
            dataRecords.flatMap { (_, cells) -> amountColumns.map { cells.getOrNull(it) } }
        )

        for ((line, cells) in records.drop(1)) {
            if (cells.size < 2) { skip(CsvSkipReason.TOO_FEW_COLUMNS); continue }

            val date = when (val reading = readDate(cells.getOrNull(mapping.dateCol), dateOrder)) {
                is DateReading.Valid -> reading.date
                DateReading.Ambiguous -> { skip(CsvSkipReason.AMBIGUOUS_DATE); continue }
                DateReading.Invalid -> { skip(CsvSkipReason.INVALID_DATE); continue }
            }

            val currency = mapping.currencyCol?.let { col ->
                val raw = cells.getOrNull(col)?.trim()?.uppercase().orEmpty()
                when {
                    raw.isEmpty() -> defaultCurrency
                    raw == "FT" -> "HUF"
                    SupportedCurrencies.isValid(raw) -> raw
                    else -> null
                }
            } ?: if (mapping.currencyCol == null) defaultCurrency else { skip(CsvSkipReason.UNSUPPORTED_CURRENCY); continue }

            val amountMinor: Long? = if (mapping.debitCol != null && mapping.creditCol != null) {
                val debitRaw = cells.getOrNull(mapping.debitCol)
                val creditRaw = cells.getOrNull(mapping.creditCol)
                val debit = if (debitRaw.isNullOrBlank()) 0L else parseAmount(debitRaw, amountStyle)
                val credit = if (creditRaw.isNullOrBlank()) 0L else parseAmount(creditRaw, amountStyle)
                if (debit == null || credit == null) null else credit - kotlin.math.abs(debit)
            } else if (mapping.amountCol != null) {
                parseAmount(cells.getOrNull(mapping.amountCol), amountStyle)
            } else {
                null
            }
            if (amountMinor == null) { skip(CsvSkipReason.INVALID_AMOUNT); continue }
            if (amountMinor == 0L) { skip(CsvSkipReason.ZERO_AMOUNT); continue }

            val rawDesc = cells.getOrNull(mapping.descCol)?.trim().orEmpty().ifBlank { "Imported transaction" }
            rows += ParsedCsvRow(
                line = line,
                date = date,
                description = rawDesc,
                merchant = TextNormalizer.cleanMerchantName(rawDesc).ifBlank { null },
                amountMinor = amountMinor,
                currency = currency,
                direction = if (amountMinor > 0) TransactionDirection.INCOME else TransactionDirection.EXPENSE,
                categorySuggestion = mapping.categoryCol?.let { cells.getOrNull(it) }?.ifBlank { null }
            )
        }
        return CsvParseResult(rows, skipped, charset)
    }

    private val ISO_DATE = Regex("^(\\d{4})[-./](\\d{1,2})[-./](\\d{1,2})(?:[ T].*)?$")
    private val DOTTED_DATE = Regex("^(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4})(?:\\s.*)?$")
    private val SLASH_DASH_DATE = Regex("^(\\d{1,2})([/-])(\\d{1,2})\\2(\\d{4})(?:[ T].*)?$")

    /**
     * Reads one date cell.
     * - Year first (`2026-03-28`, `2026.03.28.`, `2026/03/28`): unambiguous.
     * - Dotted year last (`28.03.2026`): always day-first, the only convention used with dots.
     * - `28/03/2026` and `28-03-2026`: the order is read from the cell when only one order gives
     *   a real date, otherwise from [order]; if neither decides, the result is [DateReading.Ambiguous].
     * Impossible dates (`2026-02-30`) and anything else are [DateReading.Invalid].
     */
    fun readDate(raw: String?, order: DateOrder? = null): DateReading {
        val s = raw?.trim()?.trimEnd('.') ?: return DateReading.Invalid
        ISO_DATE.find(s)?.let { m ->
            return date(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
                ?.let { DateReading.Valid(it) } ?: DateReading.Invalid
        }
        DOTTED_DATE.find(s)?.let { m ->
            return date(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt())
                ?.let { DateReading.Valid(it) } ?: DateReading.Invalid
        }
        SLASH_DASH_DATE.find(s)?.let { m ->
            val first = m.groupValues[1].toInt()
            val second = m.groupValues[3].toInt()
            val year = m.groupValues[4].toInt()
            val dayFirst = date(year, second, first)
            val monthFirst = date(year, first, second)
            return when {
                dayFirst == null && monthFirst == null -> DateReading.Invalid
                dayFirst == null -> DateReading.Valid(monthFirst!!)
                monthFirst == null -> DateReading.Valid(dayFirst)
                dayFirst == monthFirst -> DateReading.Valid(dayFirst)
                order == DateOrder.DAY_FIRST -> DateReading.Valid(dayFirst)
                order == DateOrder.MONTH_FIRST -> DateReading.Valid(monthFirst)
                else -> DateReading.Ambiguous
            }
        }
        return DateReading.Invalid
    }

    /** The date in [raw], or null when it is invalid or still ambiguous without [order]. */
    fun parseDate(raw: String?, order: DateOrder? = null): LocalDate? =
        (readDate(raw, order) as? DateReading.Valid)?.date

    /**
     * The day/month order a file uses, proved only by cells that are real dates in exactly one
     * order (`28/03/2026` is day-first, `03/28/2026` month-first). Returns null when there is no
     * such cell, or when cells prove both orders (a mixed file), so nothing is guessed.
     */
    fun inferDateOrder(cells: List<String?>): DateOrder? {
        val proofs = cells.mapNotNull { raw ->
            val m = SLASH_DASH_DATE.find(raw?.trim()?.trimEnd('.') ?: return@mapNotNull null) ?: return@mapNotNull null
            val first = m.groupValues[1].toInt()
            val second = m.groupValues[3].toInt()
            val year = m.groupValues[4].toInt()
            val dayFirst = date(year, second, first)
            val monthFirst = date(year, first, second)
            when {
                dayFirst != null && monthFirst == null -> DateOrder.DAY_FIRST
                monthFirst != null && dayFirst == null -> DateOrder.MONTH_FIRST
                else -> null
            }
        }.toSet()
        return proofs.singleOrNull()
    }

    private fun date(year: Int, month: Int, day: Int): LocalDate? =
        try {
            LocalDate.of(year, month, day)
        } catch (e: DateTimeException) {
            null
        }

    /**
     * The decimal/grouping convention of a whole file, proved only by amounts that can be read
     * one way (`1.234,56`, `12,5`, `804.00`). Null when nothing proves it or the cells disagree.
     */
    fun inferAmountStyle(cells: List<String?>): SeparatorStyle? =
        cells.mapNotNull { AmountParser.styleProvedBy(cleanAmount(it)) }.toSet().singleOrNull()

    private fun cleanAmount(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return raw.trim()
            .replace(Regex("(?i)\\b(HUF|EUR|USD|GBP|CHF|Ft)\\b"), "")
            .replace("€", "").replace("$", "").replace("£", "")
            .trim()
    }

    /**
     * Signed amount in scale-2 minor units, or null if the cell is not an exact number.
     * [style] is the file's proven convention, if any; without it only self-evident shapes parse.
     */
    fun parseAmount(raw: String?, style: SeparatorStyle? = null): Long? {
        val clean = cleanAmount(raw) ?: return null
        val value = AmountParser.parse(clean, style ?: SeparatorStyle.AUTO) ?: return null
        return runCatching { Money.toMinor(value) }.getOrNull()
    }

    private fun InputStream.readBytesLimited(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (true) {
            val n = read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            require(out.size() <= limit) { "File is larger than ${limit / (1024 * 1024)} MB" }
        }
        return out.toByteArray()
    }
}
