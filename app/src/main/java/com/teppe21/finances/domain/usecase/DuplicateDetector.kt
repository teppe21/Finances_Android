package com.teppe21.finances.domain.usecase

import com.teppe21.finances.core.utils.TextNormalizer
import com.teppe21.finances.domain.model.Transaction
import com.teppe21.finances.domain.model.TransactionDirection
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

enum class DeduplicationStatus {
    UNIQUE,
    EXACT_DUPLICATE,
    POSSIBLE_DUPLICATE
}

enum class DuplicateReason {
    NONE,
    EXTERNAL_ID,
    FINGERPRINT,
    CROSS_SOURCE_MATCH,
    SAME_SOURCE_REPEAT,
    CROSS_SOURCE_WEAK
}

data class DuplicateCheck(
    val status: DeduplicationStatus,
    val matchedTransactionId: String? = null,
    val reason: DuplicateReason = DuplicateReason.NONE
)

/** Everything the detector needs to know about an incoming transaction. */
data class DedupCandidate(
    val fingerprint: String,
    val externalId: String?,
    val sourcePackage: String?,
    val occurredAt: Instant?,
    val amountMinor: Long,
    val currency: String,
    val direction: TransactionDirection,
    val merchant: String?,
    val cardLast4: String?
)

/**
 * Decides whether two notification sources may describe the same purchase
 * (for example a wallet tap and the issuing bank's card notification).
 */
fun interface SourcePairPolicy {
    fun compatible(candidatePackage: String, existingPackage: String, candidateIssuer: String?): Boolean
}

/**
 * Duplicate detection in decreasing order of evidence:
 *  1. external transaction id,
 *  2. deterministic fingerprint (same notification delivered twice, same CSV row re-imported),
 *  3. cross-source correlation between explicitly compatible sources, which needs a short
 *     time window plus a matching merchant or card suffix and no conflicting signal.
 *
 * Equal amount, currency and date alone are never enough to merge two transactions.
 * Weak overlaps are reported as POSSIBLE_DUPLICATE so a person can decide.
 */
class DuplicateDetector(
    private val pairPolicy: SourcePairPolicy = SourcePairPolicy { _, _, _ -> false },
    private val crossSourceWindow: Duration = Duration.ofMinutes(3),
    private val weakWindow: Duration = Duration.ofSeconds(90),
    private val sameSourceWindow: Duration = Duration.ofMinutes(2)
) {

    fun check(
        candidate: DedupCandidate,
        exactByExternalId: Transaction?,
        exactByFingerprint: Transaction?,
        nearby: List<Transaction>,
        candidateIssuer: String? = null
    ): DuplicateCheck {
        if (!candidate.externalId.isNullOrBlank() && exactByExternalId != null) {
            return DuplicateCheck(DeduplicationStatus.EXACT_DUPLICATE, exactByExternalId.id, DuplicateReason.EXTERNAL_ID)
        }
        if (exactByFingerprint != null) {
            return DuplicateCheck(DeduplicationStatus.EXACT_DUPLICATE, exactByFingerprint.id, DuplicateReason.FINGERPRINT)
        }
        val pkg = candidate.sourcePackage ?: return DuplicateCheck(DeduplicationStatus.UNIQUE)
        val at = candidate.occurredAt ?: return DuplicateCheck(DeduplicationStatus.UNIQUE)

        var possible: DuplicateCheck? = null
        for (existing in nearby) {
            if (existing.amountMinor != candidate.amountMinor) continue
            if (!existing.currency.equals(candidate.currency, ignoreCase = true)) continue
            if (existing.direction != candidate.direction) continue
            val existingPkg = existing.sourceAppPackage ?: continue
            val existingAt = existing.valueDate ?: continue
            val gap = Duration.between(existingAt, at).abs()

            val merchantRelation = MerchantMatcher.relation(candidate.merchant, existing.merchant)
            val cardRelation = when {
                candidate.cardLast4.isNullOrBlank() || existing.cardLast4.isNullOrBlank() -> Relation.UNKNOWN
                candidate.cardLast4 == existing.cardLast4 -> Relation.MATCH
                else -> Relation.CONFLICT
            }
            val conflicting = merchantRelation == Relation.CONFLICT || cardRelation == Relation.CONFLICT

            if (existingPkg == pkg) {
                // The same app announcing the same amount twice within seconds is usually a
                // re-posted notification, but two identical coffees are possible too.
                if (gap <= sameSourceWindow && !conflicting && possible == null) {
                    possible = DuplicateCheck(DeduplicationStatus.POSSIBLE_DUPLICATE, existing.id, DuplicateReason.SAME_SOURCE_REPEAT)
                }
                continue
            }

            if (!pairPolicy.compatible(pkg, existingPkg, candidateIssuer)) continue
            if (conflicting) continue
            val strong = merchantRelation == Relation.MATCH || cardRelation == Relation.MATCH
            if (strong && gap <= crossSourceWindow) {
                return DuplicateCheck(DeduplicationStatus.EXACT_DUPLICATE, existing.id, DuplicateReason.CROSS_SOURCE_MATCH)
            }
            if (gap <= weakWindow && possible == null) {
                possible = DuplicateCheck(DeduplicationStatus.POSSIBLE_DUPLICATE, existing.id, DuplicateReason.CROSS_SOURCE_WEAK)
            }
        }
        return possible ?: DuplicateCheck(DeduplicationStatus.UNIQUE)
    }

    /** Window used when loading nearby transactions from storage. */
    fun searchDates(date: LocalDate): Pair<LocalDate, LocalDate> = date.minusDays(1) to date.plusDays(1)
}

enum class Relation { MATCH, CONFLICT, UNKNOWN }

object MerchantMatcher {

    private val NOISE = setOf("kft", "zrt", "bt", "nyrt", "ltd", "llc", "inc", "gmbh", "hu", "the")

    private fun tokens(value: String?): List<String> =
        TextNormalizer.normalizeSearch(value).split(' ').filter { it.isNotBlank() && it !in NOISE }

    fun relation(a: String?, b: String?): Relation {
        val ta = tokens(a)
        val tb = tokens(b)
        if (ta.isEmpty() || tb.isEmpty()) return Relation.UNKNOWN
        val ja = ta.joinToString(" ")
        val jb = tb.joinToString(" ")
        if (ja == jb) return Relation.MATCH
        val shorter = if (ja.length <= jb.length) ja else jb
        val longer = if (shorter == ja) jb else ja
        if (shorter.length >= 3 && (longer.startsWith("$shorter ") || longer.contains(" $shorter ") || longer.endsWith(" $shorter"))) {
            return Relation.MATCH
        }
        val sa = ta.toSet()
        val sb = tb.toSet()
        val overlap = sa.intersect(sb).count { it.length >= 2 }
        val union = sa.union(sb).size
        return if (union > 0 && overlap.toDouble() / union >= 0.5) Relation.MATCH else Relation.CONFLICT
    }
}

/** Deterministic fingerprints, one scheme per source. */
object Fingerprints {

    private fun sha256(raw: String): String =
        MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    /** Same notification delivered twice -> same fingerprint. Contains no readable text. */
    fun notification(
        packageName: String,
        notificationKey: String?,
        postedAt: Instant,
        amountMinor: Long,
        currency: String,
        direction: TransactionDirection
    ): String = "ntf:" + sha256("$packageName|${notificationKey.orEmpty()}|${postedAt.toEpochMilli()}|$amountMinor|${currency.uppercase()}|$direction")

    /**
     * CSV rows: re-importing the same file is idempotent, while two identical rows in one
     * file are kept apart by their occurrence index.
     */
    fun csvRow(
        accountId: String,
        date: LocalDate,
        amountMinor: Long,
        currency: String,
        description: String,
        occurrence: Int
    ): String = "csv:" + sha256("$accountId|$date|$amountMinor|${currency.uppercase()}|${TextNormalizer.normalizeSearch(description)}|$occurrence")

    fun receipt(receiptId: String): String = "rcpt:$receiptId"

    fun manual(uniqueId: String): String = "man:$uniqueId"
}
