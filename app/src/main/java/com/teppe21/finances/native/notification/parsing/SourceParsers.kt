package com.teppe21.finances.native.notification.parsing

import com.teppe21.finances.core.utils.TextNormalizer
import com.teppe21.finances.domain.model.TransactionDirection

/** Text of a notification as seen by a parser. Never persisted. */
class NotificationText(title: String?, text: String?, bigText: String?) {
    val title: String = title?.trim().orEmpty()
    /** Prefer the expanded text; fall back to the collapsed text. */
    val body: String = (bigText?.takeIf { it.isNotBlank() } ?: text).orEmpty().trim()
    val full: String = listOf(this.title, body).filter { it.isNotBlank() }.joinToString("\n")
    val folded: String = Cues.fold(full)
    val foldedBody: String = Cues.fold(body)
}

/** What a parser recognized, before account matching and deduplication. */
data class ParsedFields(
    val direction: TransactionDirection,
    val amountMinor: Long,
    val currency: String,
    val merchant: String?,
    val description: String,
    val suggestedInstitution: String?,
    val cardLast4: String?,
    val confidence: Float
)

sealed interface ParseOutcome {
    data class Parsed(val fields: ParsedFields) : ParseOutcome
    /** Text had no positive transaction semantics for this source. */
    data object NotATransaction : ParseOutcome
    /** Transaction wording was present but the amount/currency could not be read reliably. */
    data object Malformed : ParseOutcome
}

interface SourceParser {
    fun parse(text: NotificationText): ParseOutcome
}

/**
 * Unicode-aware word boundary. Java's \\b is ASCII-only on recent JDKs while Android's
 * ICU engine is Unicode-aware, so patterns use this explicit form to behave the same
 * on both (Hungarian words often start with accented letters).
 */
private const val BOUNDARY =
    "(?:(?<![\\p{L}\\p{N}])(?=[\\p{L}\\p{N}])|(?<=[\\p{L}\\p{N}])(?![\\p{L}\\p{N}]))"

internal fun rx(pattern: String, multiline: Boolean = false): Regex {
    val options = mutableSetOf(RegexOption.IGNORE_CASE)
    if (multiline) options += RegexOption.MULTILINE
    return Regex(pattern.replace("\\b", BOUNDARY), options)
}

internal object ParserSupport {

    private val CARD_SUFFIX = rx("(?:[•*·]{1,4}\\s?|\\bending in\\s|\\bk[aá]rtya:?\\s*\\*?)(\\d{4})(?!\\d)")

    private val GENERIC_TITLES = setOf(
        "revolut", "wise", "google wallet", "google pay", "wallet", "payment", "payments", "card payment",
        "otp bank", "otp", "erste", "george", "mbh", "mbh bank", "fizetes", "kartyas fizetes",
        "vasarlas", "sikeres vasarlas", "sikeres kartyas vasarlas", "terheles", "jovairas",
        "utalas", "atutalas", "penzkuldes", "transfer", "money sent", "money received", "refund"
    )

    fun isGenericTitle(title: String): Boolean {
        val folded = Cues.fold(title).trim().trimEnd('!', '.', ':')
        if (folded.isBlank()) return true
        return folded in GENERIC_TITLES || folded.startsWith("fizetes a kovetkezonek")
    }

    fun cleanMerchant(raw: String?): String? {
        if (raw == null) return null
        val cleaned = TextNormalizer.cleanMerchantName(
            raw.replace(Regex("\\s+"), " ").trim().trimEnd('.', ',', '!', ';')
        )
        if (cleaned.isBlank() || cleaned.length > 60) return null
        // A "merchant" made only of digits or currency text is a parsing artefact.
        if (MoneyMentions.find(cleaned).isNotEmpty() || cleaned.none { it.isLetter() }) return null
        return cleaned
    }

    fun firstAfter(mentions: List<MoneyMention>, position: Int): MoneyMention? =
        mentions.firstOrNull { it.start >= position }

    fun cardLast4(text: String): String? =
        CARD_SUFFIX.find(text)?.groupValues?.get(1)

    fun signedFor(direction: TransactionDirection, absMinor: Long): Long = when (direction) {
        TransactionDirection.EXPENSE, TransactionDirection.TRANSFER -> -absMinor
        else -> absMinor
    }
}

/**
 * Revolut. Fixture-backed patterns:
 *  - title = merchant, body "You spent HUF804" (+ optional balance line)
 *  - "Fizetés a következőnek: LIDL" / "Elköltöttél 14 500 Ft-ot itt: LIDL."
 *  - transfers "You sent ... to X" / "Pénzt küldtél X részére"
 *  - incoming "X sent you ..." / "You received ... from X", refunds.
 */
object RevolutParser : SourceParser {

    private val REFUND = rx("\\b(refund(?:ed)?|visszat[eé]r[ií]t[eé]s)")
    private val INCOMING = rx("(\\bsent you\\b|\\byou(?:'ve)? received\\b|\\bkapt[aá]l\\b|\\bj[oó]v[aá][ií]r[aá]s\\b|\\b[eé]rkezett\\b)")
    private val OUTGOING_TRANSFER = rx("(\\byou sent\\b|\\bsent to\\b|\\btransfer(?:red)? to\\b|p[eé]nzt k[uü]ldt[eé]l|\\belk[uü]ldt[eé]l\\b)")
    private val ATM = rx("(cash withdrawal|\\batm\\b|k[eé]szp[eé]nzfelv[eé]tel)")
    private val EXPENSE = rx("(\\byou spent\\b|\\byou paid\\b|\\bpaid\\b|card payment|\\bspent\\b|elk[oö]lt[oö]tt[eé]l|fizett[eé]l|sikeres fizet[eé]s|fizet[eé]s a k[oö]vetkez[oő]nek)")

    private val MERCHANT_PATTERNS = listOf(
        rx("k[oö]vetkez[oő]nek:?\\s*([^\\n,]+)"),
        rx("\\bitt:\\s*([^\\n,]+)"),
        rx("k[uü]ldt[eé]l\\s+(.+?)\\s+r[eé]sz[eé]re"),
        rx("\\b(?:at|to)\\s+([^\\n]+?)(?:\\s+with\\b|\\s+on\\b|\\s+using\\b|[.\\n]|$)"),
        rx("\\bfrom\\s+([^\\n]+?)(?:\\s+on\\b|[.\\n]|$)"),
        rx("^(.+?)\\s+sent you\\b", multiline = true)
    )

    override fun parse(text: NotificationText): ParseOutcome {
        val full = text.full
        val match = REFUND.find(full)?.let { TransactionDirection.REFUND to it }
            ?: INCOMING.find(full)?.let { TransactionDirection.INCOME to it }
            ?: OUTGOING_TRANSFER.find(full)?.let { TransactionDirection.TRANSFER to it }
            ?: ATM.find(full)?.let { TransactionDirection.EXPENSE to it }
            ?: EXPENSE.find(full)?.let { TransactionDirection.EXPENSE to it }
            ?: return ParseOutcome.NotATransaction

        val (direction, verb) = match
        val mentions = MoneyMentions.find(full)
        // The amount must come from the same sentence as the transaction verb
        // (or right after it), never from a balance line.
        val amount = ParserSupport.firstAfter(mentions, verb.range.first)
            ?: mentions.firstOrNull()
            ?: return ParseOutcome.Malformed

        val isAtm = ATM.containsMatchIn(full)
        val merchant = when {
            isAtm -> "Cash withdrawal"
            !ParserSupport.isGenericTitle(text.title) && direction == TransactionDirection.EXPENSE ->
                ParserSupport.cleanMerchant(text.title)
            else -> null
        } ?: MERCHANT_PATTERNS.firstNotNullOfOrNull { ParserSupport.cleanMerchant(it.find(full)?.groupValues?.get(1)) }

        val description = describe(direction, merchant, isAtm)
        return ParseOutcome.Parsed(
            ParsedFields(
                direction = direction,
                amountMinor = ParserSupport.signedFor(direction, amount.amountMinor),
                currency = amount.currency,
                merchant = merchant,
                description = description,
                suggestedInstitution = "Revolut",
                cardLast4 = ParserSupport.cardLast4(full),
                confidence = if (merchant != null) 0.95f else 0.9f
            )
        )
    }
}

/**
 * Google Wallet contactless payments. Fixture-backed pattern: title = merchant,
 * body "HUF804.00 with Revolut Mastercard ••1413".
 */
object GoogleWalletParser : SourceParser {

    private val PAYMENT_LINE = rx("^\\s*(.+?)\\s+with\\s+(.+?)\\s*$")

    override fun parse(text: NotificationText): ParseOutcome {
        val line = text.body.lines().firstOrNull { PAYMENT_LINE.matches(it) } ?: return ParseOutcome.NotATransaction
        val m = PAYMENT_LINE.find(line) ?: return ParseOutcome.NotATransaction
        val amountPart = m.groupValues[1]
        val cardPart = m.groupValues[2]
        val mentions = MoneyMentions.find(amountPart)
        // The whole left side must be exactly one amount, nothing else.
        val amount = mentions.singleOrNull() ?: return ParseOutcome.NotATransaction
        if (amountPart.substring(amount.start, amount.end).trim() != amountPart.trim()) {
            return ParseOutcome.NotATransaction
        }

        val merchant = if (ParserSupport.isGenericTitle(text.title)) null else ParserSupport.cleanMerchant(text.title)
        val foldedCard = Cues.fold(cardPart)
        val issuer = SupportedSources.CARD_ISSUER_KEYWORDS.firstOrNull { (keyword, _) ->
            Cues.containsCue(foldedCard, keyword)
        }?.second

        return ParseOutcome.Parsed(
            ParsedFields(
                direction = TransactionDirection.EXPENSE,
                amountMinor = -amount.amountMinor,
                currency = amount.currency,
                merchant = merchant,
                description = if (merchant != null) "Contactless payment at $merchant" else "Contactless payment",
                suggestedInstitution = issuer,
                cardLast4 = ParserSupport.cardLast4(cardPart),
                confidence = if (merchant != null) 0.95f else 0.85f
            )
        )
    }
}

/**
 * Hungarian bank push messages ("Sikeres kártyás vásárlás", "Összeg: -4.500 Ft",
 * "Hely: SPAR", "Kártya: *1234"). Used for OTP Bank.
 */
object OtpParser : SourceParser by HungarianBankParser("OTP Bank")

class HungarianBankParser(private val institution: String) : SourceParser {

    private val INCOME = rx("(j[oó]v[aá][ií]r[aá]s|be[eé]rkez[oő]|munkab[eé]r|j[oó]v[aá][ií]rtunk)")
    private val TRANSFER = rx("(\\b[aá]tutal[aá]s|\\butal[aá]s\\b|[aá]tvezet[eé]s)")
    private val EXPENSE = rx("(v[aá]s[aá]rl[aá]s|k[aá]rty[aá]s fizet[eé]s|\\bterhel[eé]s|k[eé]szp[eé]nzfelv[eé]tel|\\bfizet[eé]s\\b)")
    private val AMOUNT_LABEL = rx("([oö]sszeg|amount)\\s*:")
    private val MERCHANT_LABEL = rx("(?:hely|partner|elfogad[oó]hely|kedvezm[eé]nyezett|merchant)\\s*:\\s*([^\\n,]+?)(?:\\.\\s|\\.$|,|\\n|$)")

    override fun parse(text: NotificationText): ParseOutcome {
        val full = text.full
        val direction = when {
            INCOME.containsMatchIn(full) -> TransactionDirection.INCOME
            EXPENSE.containsMatchIn(full) -> TransactionDirection.EXPENSE
            TRANSFER.containsMatchIn(full) -> TransactionDirection.TRANSFER
            else -> return ParseOutcome.NotATransaction
        }
        val mentions = MoneyMentions.find(full)
        val labelled = AMOUNT_LABEL.find(full)?.let { ParserSupport.firstAfter(mentions, it.range.last) }
        val amount = labelled ?: mentions.firstOrNull() ?: return ParseOutcome.Malformed
        if (labelled == null && mentions.size > 1) {
            // Several amounts and no label: refuse to guess which one is the transaction.
            return ParseOutcome.Malformed
        }
        // An explicit "+" on an expense (or "-" on income) contradicts the wording.
        if ((direction == TransactionDirection.EXPENSE && amount.explicitSign > 0) ||
            (direction == TransactionDirection.INCOME && amount.explicitSign < 0)
        ) {
            return ParseOutcome.Malformed
        }

        val merchant = ParserSupport.cleanMerchant(MERCHANT_LABEL.find(full)?.groupValues?.get(1))
        val description = describe(direction, merchant, isAtm = Cues.containsCue(text.folded, "keszpenzfelvetel"))
        return ParseOutcome.Parsed(
            ParsedFields(
                direction = direction,
                amountMinor = ParserSupport.signedFor(direction, amount.amountMinor),
                currency = amount.currency,
                merchant = merchant,
                description = description,
                suggestedInstitution = institution,
                cardLast4 = ParserSupport.cardLast4(full),
                confidence = if (labelled != null && merchant != null) 0.9f else 0.8f
            )
        )
    }
}

/** Wise card and transfer messages ("You spent 12.50 EUR at Tesco"). */
object WiseParser : SourceParser {

    private val EXPENSE = rx("(\\byou spent\\b|\\byou paid\\b|card payment)")
    private val TRANSFER = rx("(\\byou sent\\b|\\btransfer to\\b)")
    private val INCOME = rx("(\\byou received\\b|\\bsent you\\b|\\bhas arrived\\b|\\bmoney received\\b)")
    private val MERCHANT = rx("\\b(?:at|to|from)\\s+([^\\n]+?)(?:\\s+with\\b|\\s+on\\b|[.\\n]|$)")

    override fun parse(text: NotificationText): ParseOutcome {
        val full = text.full
        val match = INCOME.find(full)?.let { TransactionDirection.INCOME to it }
            ?: TRANSFER.find(full)?.let { TransactionDirection.TRANSFER to it }
            ?: EXPENSE.find(full)?.let { TransactionDirection.EXPENSE to it }
            ?: return ParseOutcome.NotATransaction
        val (direction, verb) = match
        val mentions = MoneyMentions.find(full)
        val amount = ParserSupport.firstAfter(mentions, verb.range.first) ?: return ParseOutcome.Malformed
        val merchant = ParserSupport.cleanMerchant(MERCHANT.find(full, verb.range.first)?.groupValues?.get(1))
        return ParseOutcome.Parsed(
            ParsedFields(
                direction = direction,
                amountMinor = ParserSupport.signedFor(direction, amount.amountMinor),
                currency = amount.currency,
                merchant = merchant,
                description = describe(direction, merchant, isAtm = false),
                suggestedInstitution = "Wise",
                cardLast4 = ParserSupport.cardLast4(full),
                confidence = 0.85f
            )
        )
    }
}

internal fun describe(direction: TransactionDirection, merchant: String?, isAtm: Boolean): String = when {
    isAtm -> "Cash withdrawal"
    direction == TransactionDirection.EXPENSE -> if (merchant != null) "Card payment at $merchant" else "Card payment"
    direction == TransactionDirection.TRANSFER -> if (merchant != null) "Transfer to $merchant" else "Outgoing transfer"
    direction == TransactionDirection.INCOME -> if (merchant != null) "Incoming payment from $merchant" else "Incoming payment"
    direction == TransactionDirection.REFUND -> if (merchant != null) "Refund from $merchant" else "Refund"
    else -> "Transaction"
}
