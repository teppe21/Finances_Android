package com.teppe21.finances

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.teppe21.finances.core.theme.FinancesTheme
import com.teppe21.finances.core.ui.EmptyState
import com.teppe21.finances.domain.model.Account
import com.teppe21.finances.domain.model.AccountType
import com.teppe21.finances.domain.model.Category
import com.teppe21.finances.domain.model.Transaction
import com.teppe21.finances.domain.model.TransactionDirection
import com.teppe21.finances.domain.model.TransactionSource
import com.teppe21.finances.feature.notifications.NotificationDisclosureDialog
import com.teppe21.finances.feature.notifications.ReviewScreen
import com.teppe21.finances.feature.notifications.ReviewViewModel
import com.teppe21.finances.feature.transactions.AddTransactionDialog
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate

/**
 * Compose UI tests on a device or emulator. They use semantics (text and content descriptions),
 * not layout, and run composables against fake state so they never touch the app's database.
 */
@RunWith(AndroidJUnit4::class)
class ComponentUiTest {

    @get:Rule
    val rule = createComposeRule()

    private val bank = Account("acc_bank", "Main bank", "Example Bank", AccountType.BANK)
    private val food = Category("food", "Food & Groceries")

    @Test
    fun addTransactionRequiresAValidAmountBeforeSaving() {
        var saved: Triple<String, Long, TransactionDirection>? = null
        rule.setContent {
            FinancesTheme(darkTheme = false) {
                AddTransactionDialog(
                    accounts = listOf(bank),
                    categories = listOf(food),
                    defaultCurrency = "HUF",
                    onDismiss = {},
                    onConfirm = { account, minor, _, direction, _, _, _, _ -> saved = Triple(account, minor, direction) }
                )
            }
        }

        rule.onNodeWithText("Save").assertIsNotEnabled()

        // Three decimals are not a valid amount: the field explains it and Save stays disabled.
        rule.onNodeWithText("Amount (HUF)").performTextInput("12.345")
        rule.onNodeWithText("Use digits with up to two decimals, e.g. 1250 or 12.50").assertIsDisplayed()
        rule.onNodeWithText("Save").assertIsNotEnabled()
    }

    @Test
    fun addTransactionSavesAnExpenseAsANegativeAmount() {
        var saved: Triple<String, Long, TransactionDirection>? = null
        rule.setContent {
            FinancesTheme(darkTheme = false) {
                AddTransactionDialog(
                    accounts = listOf(bank),
                    categories = listOf(food),
                    defaultCurrency = "HUF",
                    onDismiss = {},
                    onConfirm = { account, minor, _, direction, _, _, _, _ -> saved = Triple(account, minor, direction) }
                )
            }
        }
        rule.onNodeWithText("Amount (HUF)").performTextInput("12,50")
        rule.onNodeWithText("Save").assertIsEnabled().performClick()
        assertEquals(Triple("acc_bank", -1250L, TransactionDirection.EXPENSE), saved)
    }

    @Test
    fun emptyStateShowsItsMessage() {
        rule.setContent {
            FinancesTheme(darkTheme = false) { EmptyState("Nothing to review", "Recognized transactions will appear here.") }
        }
        rule.onNodeWithText("Nothing to review").assertIsDisplayed()
        rule.onNodeWithText("Recognized transactions will appear here.").assertIsDisplayed()
    }

    @Test
    fun notificationDisclosureOffersBothChoices() {
        var continued = false
        var declined = false
        rule.setContent {
            FinancesTheme(darkTheme = false) {
                NotificationDisclosureDialog(onNotNow = { declined = true }, onContinue = { continued = true })
            }
        }
        rule.onNodeWithText("Notification access").assertIsDisplayed()
        rule.onNodeWithText("Not now").performClick()
        assertTrue(declined)
        assertFalse(continued)
    }
}

@RunWith(AndroidJUnit4::class)
class ReviewFlowUiTest {

    @get:Rule
    val rule = createComposeRule()

    private val bank = Account("acc_bank", "Main bank", "Example Bank", AccountType.BANK)

    private fun pending(id: String, accountId: String = "acc_bank") = Transaction(
        id = id, accountId = accountId, date = LocalDate.of(2026, 3, 28), amountMinor = -80_400L,
        direction = TransactionDirection.EXPENSE, description = "Coffee $id", merchant = "Coffee $id",
        source = TransactionSource.NOTIFICATION, fingerprint = "fp_$id", pending = true
    )

    @Test
    fun confirmingAPendingTransactionCollapsesItAndReportsTheOutcome() {
        val items = MutableStateFlow(listOf(pending("a")))
        val confirmed = mutableListOf<Transaction>()
        val processed = mutableListOf<String>()
        val vm = ReviewViewModel(
            pendingFlow = items,
            accountsFlow = MutableStateFlow(listOf(bank)),
            updateTransaction = { confirmed += it; items.value = emptyList() },
            deleteTransaction = { },
            markProcessed = { processed += it },
            clock = { Instant.EPOCH }
        )
        rule.setContent { FinancesTheme(darkTheme = false) { ReviewScreen(viewModel = vm) } }

        rule.onNodeWithText("Coffee a").assertIsDisplayed()
        rule.onNodeWithText("Confirm").performClick()

        // The card plays its exit animation first, then the confirmation runs.
        rule.waitUntil(5_000) { confirmed.isNotEmpty() }
        assertEquals("acc_bank", confirmed.single().accountId)
        assertFalse(confirmed.single().pending)
        assertEquals(listOf("a"), processed)

        rule.waitUntil(5_000) {
            rule.onAllNodesWithTextCount("Confirmed. It now counts toward your totals.") > 0
        }
        rule.onNodeWithText("Nothing to review").assertIsDisplayed()
    }

    @Test
    fun discardingRemovesTheTransactionWithoutCountingIt() {
        val items = MutableStateFlow(listOf(pending("b")))
        val deleted = mutableListOf<String>()
        val vm = ReviewViewModel(
            pendingFlow = items,
            accountsFlow = MutableStateFlow(listOf(bank)),
            updateTransaction = { error("a discarded transaction must not be updated") },
            deleteTransaction = { deleted += it; items.value = emptyList() },
            markProcessed = { }
        )
        rule.setContent { FinancesTheme(darkTheme = false) { ReviewScreen(viewModel = vm) } }

        rule.onNodeWithText("Discard").performClick()
        rule.waitUntil(5_000) { deleted.isNotEmpty() }
        assertEquals(listOf("b"), deleted)
        rule.waitUntil(5_000) { rule.onAllNodesWithTextCount("Discarded. Nothing was added.") > 0 }
    }

    @Test
    fun anUnassignedTransactionCannotBeConfirmedUntilAnAccountIsChosen() {
        val vm = ReviewViewModel(
            pendingFlow = MutableStateFlow(listOf(pending("c", accountId = ""))),
            accountsFlow = MutableStateFlow(listOf(bank)),
            updateTransaction = { },
            deleteTransaction = { },
            markProcessed = { }
        )
        rule.setContent { FinancesTheme(darkTheme = false) { ReviewScreen(viewModel = vm) } }

        rule.onNodeWithText("Choose the account this belongs to.").assertIsDisplayed()
        rule.onNodeWithText("Confirm").assertIsNotEnabled()
        rule.onNodeWithText("Select").performClick()
        rule.onNodeWithText("Main bank").performClick()
        rule.onNodeWithText("Confirm").assertIsEnabled()
    }

    @Test
    fun anEmptyQueueShowsTheEmptyState() {
        val vm = ReviewViewModel(
            pendingFlow = MutableStateFlow(emptyList()),
            accountsFlow = MutableStateFlow(listOf(bank)),
            updateTransaction = { },
            deleteTransaction = { },
            markProcessed = { }
        )
        rule.setContent { FinancesTheme(darkTheme = false) { ReviewScreen(viewModel = vm) } }
        rule.onNodeWithText("Nothing to review").assertIsDisplayed()
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(text: String): Int =
        onAllNodesWithText(text).fetchSemanticsNodes().size
}

/** Drives the real navigation with the app's own wiring, without changing any data. */
@RunWith(AndroidJUnit4::class)
class NavigationUiTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun label(res: Int) = rule.activity.getString(res)

    @Test
    fun bottomTabsSwitchBetweenTopLevelScreens() {
        rule.waitUntil(10_000) { rule.onAllNodesWithText(label(R.string.nav_analytics)).fetchSemanticsNodes().isNotEmpty() }

        rule.onNodeWithText(label(R.string.nav_analytics)).performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText(label(R.string.net_balance)).fetchSemanticsNodes().isNotEmpty() }

        rule.onNodeWithText(label(R.string.nav_transactions)).performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText(label(R.string.filter_this_month)).fetchSemanticsNodes().isNotEmpty() }

        rule.onNodeWithText(label(R.string.nav_home)).performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText(label(R.string.scan_receipt)).fetchSemanticsNodes().isNotEmpty() }
        // The tab bar stays on every top-level screen.
        rule.onNodeWithText(label(R.string.nav_more)).assertIsDisplayed()
    }

    @Test
    fun drillingIntoAScreenShowsBackAndBackReturnsHome() {
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Stocks").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Stocks").performClick()

        rule.waitUntil(5_000) { rule.onAllNodesWithContentDescription(label(R.string.back)).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription(label(R.string.back)).performClick()

        rule.waitUntil(5_000) { rule.onAllNodesWithText(label(R.string.nav_home)).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(label(R.string.scan_receipt)).assertIsDisplayed()
    }
}
