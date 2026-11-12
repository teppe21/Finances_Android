package com.teppe21.finances

import com.teppe21.finances.domain.model.NotificationStatusCode
import com.teppe21.finances.domain.model.RawNotification
import com.teppe21.finances.domain.model.TransactionDirection
import com.teppe21.finances.native.notification.parsing.NotificationRecognizer
import com.teppe21.finances.native.notification.parsing.Recognition
import com.teppe21.finances.native.notification.parsing.SupportedSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class NotificationRecognitionTest {

    private val recognizer = NotificationRecognizer(zone = ZoneOffset.UTC)
    private val posted = Instant.parse("2026-09-15T10:10:00Z")

    private val revolut = "com.revolut.revolut"
    private val wallet = "com.google.android.apps.walletnfcrel"
    private val otp = "hu.otpbank.smartbank"

    private fun recognize(pkg: String, title: String?, text: String?, bigText: String? = null) =
        recognizer.recognize(RawNotification(pkg, title, text, bigText, posted, "key"))

    private fun accepted(r: Recognition) = (r as? Recognition.Recognized)?.transaction
        ?: throw AssertionError("Expected a recognized transaction but got $r")

    private fun rejectedCode(r: Recognition) = (r as? Recognition.Rejected)?.code
        ?: throw AssertionError("Expected a rejection but got $r")

    // ---- Genuine transactions ------------------------------------------------------

    @Test
    fun `revolut purchase uses the spent amount, not the balance line`() {
        val t = accepted(recognize(revolut, "Coop", "You spent HUF804\nHUF balance: HUF42,285.97"))
        assertEquals(-80400L, t.amountMinor)
        assertEquals("HUF", t.currency)
        assertEquals(TransactionDirection.EXPENSE, t.direction)
        assertEquals("Coop", t.merchant)
        assertEquals(LocalDate.of(2026, 9, 15), t.date)
        assertEquals("Card payment at Coop", t.description)
    }

    @Test
    fun `revolut hungarian purchase and transfer`() {
        val purchase = accepted(recognize(revolut, "Fizetés a következőnek: LIDL", "Elköltöttél 14 500 Ft-ot itt: LIDL."))
        assertEquals(-1450000L, purchase.amountMinor)
        assertEquals("LIDL", purchase.merchant)

        val transfer = accepted(recognize(revolut, "Pénzküldés", "Pénzt küldtél Kovács János részére. Összeg: 10 000 Ft."))
        assertEquals(TransactionDirection.TRANSFER, transfer.direction)
        assertEquals(-1000000L, transfer.amountMinor)
        assertEquals("Kovács János", transfer.merchant)
    }

    @Test
    fun `revolut incoming payment is income with positive amount`() {
        val t = accepted(recognize(revolut, "Money received", "Anna Smith sent you €25.00"))
        assertEquals(TransactionDirection.INCOME, t.direction)
        assertEquals(2500L, t.amountMinor)
        assertEquals("EUR", t.currency)
    }

    @Test
    fun `merchant names containing promotional words are still purchases`() {
        val t = accepted(recognize(revolut, "Bonus Pizza", "You spent HUF2,500"))
        assertEquals(-250000L, t.amountMinor)
        assertEquals("Bonus Pizza", t.merchant)
    }

    @Test
    fun `google wallet contactless payment with card suffix`() {
        val t = accepted(recognize(wallet, "75. SZ. ABC ÁRUHÁZ", "HUF804.00 with Revolut Mastercard ••1413"))
        assertEquals(-80400L, t.amountMinor)
        assertEquals("75. SZ. ABC ÁRUHÁZ", t.merchant)
        assertEquals("Revolut", t.suggestedInstitution)
        assertEquals("1413", t.cardLast4)
    }

    @Test
    fun `google wallet euro payment maps the issuer`() {
        val t = accepted(recognize(wallet, "SPAR Express", "12.50 EUR with Erste Visa ••5678"))
        assertEquals(-1250L, t.amountMinor)
        assertEquals("EUR", t.currency)
        assertEquals("Erste Bank", t.suggestedInstitution)
        assertEquals("5678", t.cardLast4)
    }

    @Test
    fun `hungarian wallet lines are recognised without the word with`() {
        val lines = listOf(
            "804,00 Ft \u2013 Revolut Mastercard \u2022\u20221413",
            "804 Ft, Revolut Mastercard \u2022\u20221413",
            "804\u00A0Ft a k\u00F6vetkez\u0151vel: Revolut Mastercard \u2022\u20221413",
            "804\u202FFt \u2013 Revolut Mastercard \u2022\u20221413"
        )
        for (line in lines) {
            val t = accepted(recognize(wallet, "75. SZ. ABC \u00C1RUH\u00C1Z", line))
            assertEquals(line, -80400L, t.amountMinor)
            assertEquals("Revolut", t.suggestedInstitution)
            assertEquals("1413", t.cardLast4)
            assertTrue("unverified wording must stay in review", t.confidence < 0.9f)
        }
        assertEquals(-1250000L, accepted(recognize(wallet, "SPAR", "12\u00A0500\u00A0Ft, George Visa \u2022\u20225678")).amountMinor)
        assertEquals("Erste Bank", accepted(recognize(wallet, "SPAR", "804 Ft, George Visa")).suggestedInstitution)
    }

    @Test
    fun `wallet line with two amounts is still rejected`() {
        assertEquals(
            NotificationStatusCode.REJECTED_NON_TRANSACTION,
            rejectedCode(recognize(wallet, "SPAR", "804 Ft, 900 Ft \u2013 Revolut Mastercard \u2022\u20221413"))
        )
    }

    @Test
    fun `erste purchase and incoming credit in hungarian`() {
        val erste = "hu.erstebank.george.hungary"
        val buy = accepted(recognize(erste, "Sikeres k\u00E1rty\u00E1s v\u00E1s\u00E1rl\u00E1s", "\u00D6sszeg: -4\u00A0500 Ft. Hely: SPAR. K\u00E1rtya: *1234."))
        assertEquals(-450000L, buy.amountMinor)
        assertEquals("SPAR", buy.merchant)
        assertEquals("Erste Bank", buy.suggestedInstitution)
        assertTrue(buy.confidence < 0.9f)
        val credit = accepted(recognize(erste, "J\u00F3v\u00E1\u00EDr\u00E1s \u00E9rkezett", "\u00D6sszeg: +150\u00A0000 Ft.\nEgyenleg: 200\u00A0000 Ft."))
        assertEquals(15000000L, credit.amountMinor)
        assertEquals(TransactionDirection.INCOME, credit.direction)
        val spend = accepted(recognize(erste, "K\u00F6lt\u00E9s", "\u00D6sszeg: 2\u00A0000 Ft.\nFelhaszn\u00E1lhat\u00F3: 90\u00A0000 Ft."))
        assertEquals(-200000L, spend.amountMinor)
    }

    @Test
    fun `hungarian erste security code is rejected`() {
        assertEquals(
            NotificationStatusCode.REJECTED_SECURITY_MESSAGE,
            rejectedCode(recognize("hu.erstebank.george.hungary", "George biztons\u00E1gi k\u00F3d", "Az \u00D6n k\u00F3dja: 482913. Ne adja ki senkinek!"))
        )
    }

    @Test
    fun `otp card purchase is parsed but needs review until fixtures are verified`() {
        val t = accepted(recognize(otp, "Sikeres kártyás vásárlás", "Összeg: -4.500 Ft. Hely: McDonald's. Kártya: *1234."))
        assertEquals(-450000L, t.amountMinor)
        assertEquals("McDonald's", t.merchant)
        assertEquals("1234", t.cardLast4)
        assertTrue("unverified sources must stay below the auto-accept threshold", t.confidence < 0.9f)
    }

    // ---- Rejections ------------------------------------------------------------------

    @Test
    fun `referral promotion never becomes a transaction`() {
        val code = rejectedCode(
            recognize(revolut, "7 days left to earn", "Get HUF22,500 for each eligible friend you refer by October 6. T&Cs apply. Tap t...")
        )
        assertEquals(NotificationStatusCode.REJECTED_PROMOTIONAL, code)
        assertEquals(
            NotificationStatusCode.REJECTED_PROMOTIONAL,
            rejectedCode(recognize(revolut, "Special Offer", "Invite friends to earn 15,000 HUF bonus reward before campaign ends."))
        )
    }

    @Test
    fun `cashback and reward credits are treated as promotional`() {
        assertEquals(
            NotificationStatusCode.REJECTED_PROMOTIONAL,
            rejectedCode(recognize(revolut, "Cashback", "You received HUF500 cashback reward"))
        )
        assertEquals(
            NotificationStatusCode.REJECTED_PROMOTIONAL,
            rejectedCode(recognize(revolut, "Rewards", "Earn up to HUF5,000 bonus this month"))
        )
    }

    @Test
    fun `amount without transaction wording is not a transaction`() {
        // Wallet-style text arriving from the bank package: no spending verb, no transaction.
        assertEquals(
            NotificationStatusCode.REJECTED_NON_TRANSACTION,
            rejectedCode(recognize(revolut, "Revolut", "HUF804.00 with Revolut Mastercard"))
        )
        assertEquals(
            NotificationStatusCode.REJECTED_NON_TRANSACTION,
            rejectedCode(recognize(revolut, "Revolut", "Your HUF balance is HUF42,285.97"))
        )
    }

    @Test
    fun `security and login messages are rejected before parsing`() {
        assertEquals(
            NotificationStatusCode.REJECTED_SECURITY_MESSAGE,
            rejectedCode(recognize(otp, "OTP Bank belépési kód", "Az Ön belépési kódja: 981245. Ne adja ki senkinek!"))
        )
        assertEquals(
            NotificationStatusCode.REJECTED_SECURITY_MESSAGE,
            rejectedCode(recognize(revolut, "Approve payment", "Approve the HUF12,000 payment at Example Shop in the app"))
        )
        assertEquals(
            NotificationStatusCode.REJECTED_SECURITY_MESSAGE,
            rejectedCode(recognize(revolut, "New login", "You logged in on a new device"))
        )
    }

    @Test
    fun `unknown packages are never parsed, whatever the text says`() {
        for (pkg in listOf("com.whatsapp", "com.facebook.orca", "com.google.android.gm", "com.example.fake.revolut")) {
            val r = recognize(pkg, "Revolut", "You spent HUF5,000 at Shop")
            assertEquals(NotificationStatusCode.REJECTED_UNSUPPORTED_PACKAGE, rejectedCode(r))
            assertNull((r as Recognition.Rejected).source)
        }
    }

    @Test
    fun `transaction wording without a readable amount is malformed`() {
        assertEquals(
            NotificationStatusCode.REJECTED_MALFORMED_TRANSACTION,
            rejectedCode(recognize(revolut, "Coop", "You spent money at Coop"))
        )
    }

    @Test
    fun `hungarian bank message with two unlabelled amounts is not guessed`() {
        val r = recognize("hu.mbhbank.app", "Vásárlás", "Vásárlás 4 500 Ft, díj 120 Ft")
        assertEquals(NotificationStatusCode.REJECTED_MALFORMED_TRANSACTION, rejectedCode(r))
    }

    @Test
    fun `routing is by exact package name only`() {
        assertEquals(SupportedSources.REVOLUT, SupportedSources.forPackage("com.revolut.revolut"))
        assertNull(SupportedSources.forPackage("com.revolut.revolut.debug"))
        assertNull(SupportedSources.forPackage("com.google.android.apps.nbu.paisa.user"))
        assertNull(SupportedSources.forPackage(null))
    }
}
