package com.teppe21.finances

import com.teppe21.finances.domain.model.Account
import com.teppe21.finances.domain.model.NotificationStatusCode
import com.teppe21.finances.domain.model.RawNotification
import com.teppe21.finances.domain.model.TransactionDirection
import com.teppe21.finances.domain.model.TransactionSource
import com.teppe21.finances.domain.usecase.DeduplicationStatus
import com.teppe21.finances.domain.usecase.DuplicateDetector
import com.teppe21.finances.domain.usecase.Fingerprints
import com.teppe21.finances.domain.usecase.IngestTransactionUseCase
import com.teppe21.finances.domain.usecase.IngestionInput
import com.teppe21.finances.domain.usecase.MerchantMatcher
import com.teppe21.finances.domain.usecase.ProcessNotificationUseCase
import com.teppe21.finances.domain.usecase.Relation
import com.teppe21.finances.domain.usecase.WalletBankPairPolicy
import com.teppe21.finances.native.notification.parsing.NotificationRecognizer
import com.teppe21.finances.support.InMemoryTransactionStore
import com.teppe21.finances.support.NoRules
import com.teppe21.finances.support.RecordingEventSink
import com.teppe21.finances.support.account
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class NotificationPipelineTest {

    private val revolut = "com.revolut.revolut"
    private val wallet = "com.google.android.apps.walletnfcrel"

    private class Harness(accounts: List<Account>, private val mappings: Map<String, String> = emptyMap()) {
        val store = InMemoryTransactionStore()
        val events = RecordingEventSink()
        val ingest = IngestTransactionUseCase(store, NoRules, DuplicateDetector(WalletBankPairPolicy))
        val useCase = ProcessNotificationUseCase(
            recognizer = NotificationRecognizer(zone = ZoneOffset.UTC),
            accounts = { accounts },
            mappedAccountId = { mappings[it] },
            ingest = ingest,
            events = events
        )

        suspend fun post(pkg: String, title: String, text: String, at: String, key: String = "k-$at") =
            useCase.process(RawNotification(pkg, title, text, null, Instant.parse(at), key))
    }

    private fun defaultHarness() = Harness(listOf(account("acc_revolut", "Revolut"), account("acc_otp", "OTP Bank")))

    @Test
    fun `two purchases with the same amount at different shops stay separate`() = runTest {
        val h = defaultHarness()
        h.post(wallet, "Shop A", "HUF804.00 with Revolut Mastercard ••1413", "2026-09-15T10:10:00Z")
        h.post(revolut, "Shop B", "You spent HUF804", "2026-09-15T10:12:00Z")
        assertEquals(2, h.store.rows.size)
        assertTrue(h.store.rows.none { it.pending })
    }

    @Test
    fun `wallet and bank notification for the same purchase are merged`() = runTest {
        val h = defaultHarness()
        val first = h.post(wallet, "Shop A", "HUF804.00 with Revolut Mastercard ••1413", "2026-09-15T10:10:00Z")
        val second = h.post(revolut, "Shop A", "You spent HUF804", "2026-09-15T10:10:30Z")
        assertEquals(NotificationStatusCode.ACCEPTED_EXPENSE, first)
        assertEquals(NotificationStatusCode.DUPLICATE_EXISTING_TRANSACTION, second)
        assertEquals(1, h.store.rows.size)
    }

    @Test
    fun `card suffix match merges even when the bank omits the merchant`() = runTest {
        val h = defaultHarness()
        h.post(revolut, "Revolut", "You spent HUF804 with card ••1413", "2026-09-15T10:10:00Z")
        val second = h.post(wallet, "75. SZ. ABC ÁRUHÁZ", "HUF804.00 with Revolut Mastercard ••1413", "2026-09-15T10:10:20Z")
        assertEquals(NotificationStatusCode.DUPLICATE_EXISTING_TRANSACTION, second)
        assertEquals(1, h.store.rows.size)
        // The surviving row is enriched with the merchant the wallet knew.
        assertEquals("75. SZ. ABC ÁRUHÁZ", h.store.rows.single().merchant)
    }

    @Test
    fun `same amount with a different card suffix is not merged`() = runTest {
        val h = defaultHarness()
        h.post(wallet, "Shop A", "HUF804.00 with Revolut Mastercard ••1413", "2026-09-15T10:10:00Z")
        h.post(revolut, "Revolut", "You spent HUF804 with card ••9999", "2026-09-15T10:10:20Z")
        assertEquals(2, h.store.rows.size)
    }

    @Test
    fun `cross source overlap without merchant or card evidence goes to review`() = runTest {
        val h = defaultHarness()
        h.post(wallet, "Google Wallet", "HUF804.00 with Revolut Mastercard", "2026-09-15T10:10:00Z")
        val code = h.post(revolut, "Revolut", "You spent HUF804", "2026-09-15T10:10:40Z")
        assertEquals(NotificationStatusCode.PENDING_POSSIBLE_DUPLICATE, code)
        assertEquals(2, h.store.rows.size)
        // Neither row is counted until reviewed: the merchant-less wallet row has low
        // confidence, the bank row is a possible duplicate.
        assertTrue(h.store.rows.all { it.pending })
    }

    @Test
    fun `same amount on the same day without timing evidence is never merged`() = runTest {
        val store = InMemoryTransactionStore()
        val ingest = IngestTransactionUseCase(store, NoRules, DuplicateDetector(WalletBankPairPolicy))
        repeat(2) {
            ingest.execute(
                IngestionInput(
                    accountId = "acc", date = LocalDate.of(2026, 9, 15), amountMinor = -80400,
                    direction = TransactionDirection.EXPENSE, description = "Coffee", merchant = "Coffee",
                    source = TransactionSource.MANUAL
                )
            )
        }
        assertEquals(2, store.rows.size)
        assertTrue(store.rows.none { it.pending })
    }

    @Test
    fun `the same notification delivered twice is stored once`() = runTest {
        val h = defaultHarness()
        h.post(revolut, "Coop", "You spent HUF804", "2026-09-15T10:10:00Z", key = "same")
        val again = h.post(revolut, "Coop", "You spent HUF804", "2026-09-15T10:10:00Z", key = "same")
        assertEquals(NotificationStatusCode.DUPLICATE_EXISTING_TRANSACTION, again)
        assertEquals(1, h.store.rows.size)
    }

    @Test
    fun `two identical purchases seconds apart are kept but flagged for review`() = runTest {
        val h = defaultHarness()
        h.post(revolut, "Coffee Bar", "You spent HUF990", "2026-09-15T10:10:00Z")
        val second = h.post(revolut, "Coffee Bar", "You spent HUF990", "2026-09-15T10:10:40Z")
        assertEquals(NotificationStatusCode.PENDING_POSSIBLE_DUPLICATE, second)
        assertEquals(2, h.store.rows.size)
    }

    @Test
    fun `ambiguous account assignment never touches an account`() = runTest {
        val h = Harness(listOf(account("r1", "Revolut", "Revolut EUR"), account("r2", "Revolut", "Revolut HUF")))
        val code = h.post(revolut, "Coop", "You spent HUF804", "2026-09-15T10:10:00Z")
        assertEquals(NotificationStatusCode.PENDING_ACCOUNT_REVIEW, code)
        val row = h.store.rows.single()
        assertTrue(row.pending)
        assertTrue(row.isUnassigned)
    }

    @Test
    fun `explicit mapping resolves ambiguity`() = runTest {
        val h = Harness(listOf(account("r1", "Revolut"), account("r2", "Revolut")), mapOf("Revolut" to "r2"))
        assertEquals(NotificationStatusCode.ACCEPTED_EXPENSE, h.post(revolut, "Coop", "You spent HUF804", "2026-09-15T10:10:00Z"))
        assertEquals("r2", h.store.rows.single().accountId)
    }

    @Test
    fun `wallet payment with an unknown card goes to account review`() = runTest {
        val h = defaultHarness()
        val code = h.post(wallet, "Shop", "HUF1,200.00 with Visa ••0001", "2026-09-15T10:10:00Z")
        assertEquals(NotificationStatusCode.PENDING_ACCOUNT_REVIEW, code)
    }

    @Test
    fun `raw notification text is never persisted`() = runTest {
        val h = defaultHarness()
        val secretLine = "HUF balance: HUF42,285.97"
        h.post(revolut, "Coop", "You spent HUF804\n$secretLine", "2026-09-15T10:10:00Z")
        h.post(revolut, "7 days left to earn", "Get HUF22,500 for each eligible friend you refer", "2026-09-15T11:00:00Z")
        val tx = h.store.rows.single()
        val stored = listOfNotNull(tx.description, tx.merchant, tx.notes, tx.fingerprint).joinToString("|")
        assertFalse(stored.contains("balance"))
        assertFalse(stored.contains("42,285"))
        assertFalse(stored.contains("You spent"))
        // Events carry only package, timestamps and a status code.
        h.events.events.forEach { e ->
            assertTrue(e.reasonCode in NotificationStatusCode.values().map { it.name })
            assertFalse(e.toString().contains("eligible friend"))
            assertFalse(e.toString().contains("balance"))
        }
    }

    @Test
    fun `unsupported packages leave no trace`() = runTest {
        val h = defaultHarness()
        val code = h.post("com.whatsapp", "Mum", "Sent you 5000 Ft for groceries", "2026-09-15T10:10:00Z")
        assertEquals(NotificationStatusCode.REJECTED_UNSUPPORTED_PACKAGE, code)
        assertTrue(h.events.events.isEmpty())
        assertTrue(h.store.rows.isEmpty())
    }

    @Test
    fun `rejected notifications from supported apps record only a status code`() = runTest {
        val h = defaultHarness()
        h.post(revolut, "Security", "Your verification code is 482913", "2026-09-15T10:10:00Z")
        assertEquals(NotificationStatusCode.REJECTED_SECURITY_MESSAGE.name, h.events.events.single().reasonCode)
        assertTrue(h.store.rows.isEmpty())
    }

    @Test
    fun `csv fingerprints keep identical rows apart but make re-import idempotent`() = runTest {
        val date = LocalDate.of(2026, 9, 1)
        val a = Fingerprints.csvRow("acc", date, -50000, "HUF", "Bakery", 0)
        val b = Fingerprints.csvRow("acc", date, -50000, "HUF", "Bakery", 1)
        val again = Fingerprints.csvRow("acc", date, -50000, "HUF", "Bakery", 0)
        assertTrue(a != b)
        assertEquals(a, again)

        val store = InMemoryTransactionStore()
        val ingest = IngestTransactionUseCase(store, NoRules)
        suspend fun import(fp: String) = ingest.execute(
            IngestionInput(
                accountId = "acc", date = date, amountMinor = -50000, direction = TransactionDirection.EXPENSE,
                description = "Bakery", source = TransactionSource.CSV, fingerprint = fp
            )
        )
        assertEquals(DeduplicationStatus.UNIQUE, import(a).status)
        assertEquals(DeduplicationStatus.UNIQUE, import(b).status)
        assertEquals(DeduplicationStatus.EXACT_DUPLICATE, import(again).status)
        assertEquals(2, store.rows.size)
    }

    @Test
    fun `merchant matcher`() {
        assertEquals(Relation.MATCH, MerchantMatcher.relation("Coop", "COOP Szeged"))
        assertEquals(Relation.MATCH, MerchantMatcher.relation("75. SZ. ABC ÁRUHÁZ", "75. Sz. ABC"))
        assertEquals(Relation.CONFLICT, MerchantMatcher.relation("Shop A", "Shop B"))
        assertEquals(Relation.UNKNOWN, MerchantMatcher.relation(null, "Shop B"))
    }

    @Test
    fun `sign follows direction regardless of input sign`() {
        assertEquals(-500L, IngestTransactionUseCase.normalizedAmount(TransactionDirection.EXPENSE, 500))
        assertEquals(500L, IngestTransactionUseCase.normalizedAmount(TransactionDirection.INCOME, -500))
        assertEquals(500L, IngestTransactionUseCase.normalizedAmount(TransactionDirection.REFUND, -500))
        assertEquals(-500L, IngestTransactionUseCase.normalizedAmount(TransactionDirection.TRANSFER, -500))
    }
}
