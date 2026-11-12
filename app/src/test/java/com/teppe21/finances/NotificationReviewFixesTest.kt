package com.teppe21.finances

import com.teppe21.finances.domain.model.RawNotification
import com.teppe21.finances.domain.model.TransactionDirection
import com.teppe21.finances.native.notification.mergeNotificationBody
import com.teppe21.finances.native.notification.parsing.MoneyMentions
import com.teppe21.finances.native.notification.parsing.NotificationRecognizer
import com.teppe21.finances.native.notification.parsing.Recognition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class NotificationReviewFixesTest {

    private val recognizer = NotificationRecognizer(zone = ZoneOffset.UTC)
    private val wallet = "com.google.android.apps.walletnfcrel"
    private val erste = "hu.erstebank.george"
    private val otp = "hu.otpbank.smartbank"

    private fun recognize(pkg: String, title: String?, text: String?) =
        recognizer.recognize(RawNotification(pkg, title, text, null, Instant.parse("2026-09-15T10:10:00Z"), "key"))

    private fun assertRejected(pkg: String, title: String?, text: String?) {
        val r = recognize(pkg, title, text)
        assertTrue("Expected rejection for '$text' but got $r", r is Recognition.Rejected)
    }

    private fun direction(title: String, text: String) =
        ((recognize(otp, title, text) as? Recognition.Recognized) ?: throw AssertionError("not recognized: $title")).transaction.direction

    @Test
    fun `wallet rejects non-payment lines`() {
        listOf(
            "Payment of HUF804.00 with Revolut Mastercard ••1413 was declined",
            "Your HUF12,000 payment with Revolut Mastercard ••1413 was declined",
            "Confirm it's you to pay HUF12,000 with Revolut ••1413",
            "Get €10 back when you pay with your Revolut card",
            "Add your Erste card ••1234 and get 2 000 Ft",
            "Refund of €12.50 to Revolut Mastercard ••1413",
            "Elutasítva: 804 Ft – Revolut ••1413"
        ).forEach { assertRejected(wallet, "SPAR", it) }
    }

    @Test
    fun `wallet hungarian shape with extra english line stays in review`() {
        val r = recognize(wallet, "SPAR", "804 Ft – Revolut Mastercard ••1413\nPaid with Google Wallet")
        assertTrue(((r as Recognition.Recognized).transaction.confidence) < 0.9f)
    }

    @Test
    fun `verification wording is a security message`() {
        assertRejected(wallet, "Revolut", "Fizetés megerősítése: 12 000 Ft – Revolut Mastercard ••1413")
        assertRejected(wallet, "Revolut", "Azonosítás szükséges: 804 Ft, Revolut ••1413")
        assertRejected(erste, "Erste", "A belépési kódot ne adja meg senkinek")
        assertRejected(erste, "Erste", "Ezt a kóddal erősítsd: 12 000 Ft")
    }

    @Test
    fun `erkezett no longer overrides expense`() {
        assertEquals(TransactionDirection.EXPENSE, direction("Új terhelés érkezett", "Összeg: 4 500 Ft. Hely: SPAR"))
        assertEquals(TransactionDirection.EXPENSE, direction("Értesítés érkezett", "Kártyás vásárlás 4 500 Ft"))
        assertEquals(TransactionDirection.INCOME, direction("Átutalás érkezett", "Összeg: 4 500 Ft"))
        assertEquals(TransactionDirection.INCOME, direction("Jóváírás érkezett", "Összeg: 4 500 Ft"))
    }

    @Test
    fun `body merge uses inbox lines only as fallback`() {
        val lines = listOf(" a ", "", "b")
        assertEquals("big", mergeNotificationBody("big", "text", lines))
        assertEquals("text", mergeNotificationBody(null, "text", lines))
        assertEquals("text", mergeNotificationBody(" ", "text", lines))
        assertEquals("a\nb", mergeNotificationBody(null, "", lines))
        assertEquals("", mergeNotificationBody(null, null, emptyList()))
    }

    @Test
    fun `amount before a balance cue on the same line counts`() {
        val m = MoneyMentions.find("Vásárlás: 4 500 Ft, SPAR. Egyenleg: 120 000 Ft")
        assertEquals(1, m.size)
        assertEquals(450000L, m.single().amountMinor)
        assertTrue(MoneyMentions.find("Egyenleg: 120 000 Ft").isEmpty())
    }
}
