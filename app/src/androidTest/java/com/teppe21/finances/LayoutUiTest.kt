package com.teppe21.finances

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import com.teppe21.finances.core.ui.QUICK_RETURN_HEADER_TAG
import com.teppe21.finances.core.ui.QuickReturnScaffold
import com.teppe21.finances.feature.analytics.DONUT_CENTRE_TAG
import com.teppe21.finances.feature.analytics.donutHoleDiameter
import com.teppe21.finances.feature.analytics.donutRingSize
import com.teppe21.finances.feature.dashboard.QuickActionItem
import com.teppe21.finances.feature.dashboard.QuickActions
import kotlin.math.sqrt
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.teppe21.finances.core.theme.FinancesTheme
import com.teppe21.finances.core.utils.CurrencyFormatter
import com.teppe21.finances.core.ui.CurrencyChips
import com.teppe21.finances.core.ui.FloatingTabBar
import com.teppe21.finances.core.ui.ListRow
import com.teppe21.finances.core.ui.TabBarItem
import com.teppe21.finances.core.ui.TransactionRow
import com.teppe21.finances.domain.model.CategoryBreakdown
import com.teppe21.finances.domain.model.Transaction
import com.teppe21.finances.domain.model.TransactionDirection
import com.teppe21.finances.domain.model.TransactionSource
import com.teppe21.finances.feature.analytics.SpendingDonutChartCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

/**
 * Layout guards for the cases the English, default-scale suite cannot see: a narrow screen, the
 * largest font scales, a very long merchant, a very large amount, a one-character merchant and the
 * Hungarian strings. They read the real text layout (did a line overflow, how many lines) rather than
 * pixels, so they hold on any emulator.
 */
@RunWith(AndroidJUnit4::class)
class LayoutUiTest {

    @get:Rule
    val rule = createComposeRule()

    /** Composes [content] in a column [widthDp] wide at [fontScale], optionally in another [locale] and the dark theme. */
    private fun setScaled(
        fontScale: Float,
        widthDp: Int = 360,
        locale: Locale? = null,
        dark: Boolean = false,
        content: @Composable () -> Unit
    ) {
        rule.setContent {
            val base = LocalContext.current
            val configuration = Configuration(LocalConfiguration.current).also { c -> locale?.let { c.setLocale(it) } }
            val context = if (locale != null) base.createConfigurationContext(configuration) else base
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalContext provides context,
                LocalConfiguration provides configuration,
                LocalDensity provides Density(density.density, fontScale)
            ) {
                FinancesTheme(darkTheme = dark) {
                    Surface { Box(Modifier.width(widthDp.dp)) { content() } }
                }
            }
        }
    }

    private fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        val node = fetchSemanticsNode()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        return results.first()
    }

    /**
     * True when text is cut: a line ends in an ellipsis, or a single line wants more width than its box has. (hasVisualOverflow is no
     * use for single-line text that does not wrap: it is always true there.)
     */
    private fun SemanticsNodeInteraction.isCut(): Boolean {
        val layout = textLayout()
        val boxWidth = fetchSemanticsNode().size.width
        val ellipsized = (0 until layout.lineCount).any { layout.isLineEllipsized(it) }
        // A one-line text that does not wrap (softWrap = false) reports the width it was clipped to as its
        // line width, so compare the width the text wants with the box it was given.
        val clipped = layout.lineCount == 1 && layout.multiParagraph.maxIntrinsicWidth > boxWidth + 1f
        return ellipsized || clipped
    }

    private fun assertWholeOnOneLine(node: SemanticsNodeInteraction, what: String) {
        node.assertIsDisplayed()
        val layout = node.textLayout()
        assertEquals("$what must stay on one line", 1, layout.lineCount)
        assertFalse("$what must not be cut", node.isCut())
    }

    private fun transaction(
        id: String = "t",
        merchant: String,
        amount: Long,
        direction: TransactionDirection = TransactionDirection.EXPENSE,
        currency: String = "HUF"
    ) = Transaction(
        id = id, accountId = "acc", date = LocalDate.of(2026, 10, 10), amountMinor = amount, currency = currency,
        direction = direction, description = merchant, merchant = merchant, categoryId = "food",
        source = TransactionSource.CSV, fingerprint = "fp_$id", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH
    )

    private val hugeAmount = CurrencyFormatter.formatSigned(-98_765_432_100L, "HUF")
    private val longMerchant = "Very Long Merchant Name Kereskedelmi és Szolgáltató Korlátolt Felelősségű Társaság Budapest"

    // ---- amounts are never truncated -------------------------------------------------------------

    @Test
    fun listRowKeepsAHugeAmountWholeAtScale15OnANarrowScreen() {
        setScaled(fontScale = 1.5f, widthDp = 300) {
            ListRow(title = longMerchant, subtitle = "1 transaction", amount = hugeAmount)
        }
        assertWholeOnOneLine(rule.onNodeWithText(hugeAmount, useUnmergedTree = true), "the amount")
        // ...and it is inside the 300 dp the row has, not pushed past its edge.
        val bounds = rule.onNodeWithText(hugeAmount, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("amount ends at ${bounds.right}", bounds.right <= 301.dp)
    }

    @Test
    fun aVeryLongMerchantIsEllipsizedAndNeverTakesTheAmount() {
        setScaled(fontScale = 1.0f) {
            TransactionRow(transaction("a", longMerchant, -459_000L), "HUF", null, onClick = {}, categoryName = "Food & Groceries")
        }
        assertWholeOnOneLine(rule.onNodeWithText(CurrencyFormatter.formatSigned(-459_000L, "HUF"), useUnmergedTree = true), "the amount")
        assertTrue("a long merchant name is ellipsized, not wrapped without limit", rule.onNodeWithText(longMerchant, useUnmergedTree = true).isCut())
    }

    @Test
    fun aOneCharacterMerchantAndAHugeAmountShareTheRow() {
        setScaled(fontScale = 1.5f) {
            TransactionRow(transaction("b", "X", -98_765_432_100L), "HUF", null, onClick = {}, categoryName = "Shopping")
        }
        rule.onNodeWithText("X", useUnmergedTree = true).assertIsDisplayed()
        assertWholeOnOneLine(rule.onNodeWithText(hugeAmount, useUnmergedTree = true), "the amount")
    }

    @Test
    fun theDateOfATransactionIsNotCutAtScale15() {
        setScaled(fontScale = 1.5f) {
            TransactionRow(transaction("c", "Spotify", -1_499L, currency = "EUR"), "HUF", 547_300L, onClick = {}, categoryName = "Subscriptions")
        }
        // The line under the name carries the category, the date and the original amount; it may use two lines.
        assertFalse("category, date and original amount must all be shown", rule.onNodeWithText("Subscriptions", substring = true, useUnmergedTree = true).isCut())
    }

    // ---- transfers --------------------------------------------------------------------------------

    @Test
    fun aTransferRowSaysItIsATransferToAScreenReader() {
        setScaled(fontScale = 1.0f, locale = Locale.ENGLISH) {
            TransactionRow(
                transaction("tr", "Exchanged to EUR", -30_000_000L, direction = TransactionDirection.TRANSFER),
                "HUF", null, onClick = {}, categoryName = "Between own accounts"
            )
        }
        val description = rule.onNodeWithText("Exchanged to EUR", useUnmergedTree = false)
            .fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ").orEmpty()
        assertTrue("description was: $description", description.startsWith("Transfer between own accounts"))
    }

    // ---- tab bar ----------------------------------------------------------------------------------

    private fun assertTabLabelsFit(labels: List<String>) {
        setScaled(fontScale = 1.5f) {
            FloatingTabBar(labels.map { TabBarItem(it, Icons.Default.Home) }, selectedIndex = 0, onSelect = {})
        }
        labels.forEach { label ->
            assertFalse("tab '$label' is cut", rule.onNodeWithText(label, useUnmergedTree = true).isCut())
        }
    }

    @Test
    fun englishTabBarLabelsAreNotClippedAtScale15() = assertTabLabelsFit(listOf("Overview", "Transactions", "Analytics", "More"))

    @Test
    fun hungarianTabBarLabelsAreNotClippedAtScale15() = assertTabLabelsFit(listOf("Áttekintés", "Tranzakciók", "Elemzések", "Továbbiak"))

    // ---- chips ------------------------------------------------------------------------------------

    @Test
    fun currencyChipsWrapInsteadOfBeingSqueezedAtScale15() {
        setScaled(fontScale = 1.5f, widthDp = 296) {
            CurrencyChips(selected = "HUF", onSelect = {})
        }
        listOf("HUF", "EUR", "USD", "GBP", "CHF").forEach { code ->
            val node = rule.onNodeWithText(code, useUnmergedTree = true)
            node.assertIsDisplayed()
            assertEquals("'$code' must not stack letters on separate lines", 1, node.textLayout().lineCount)
        }
    }

    // ---- donut chart ------------------------------------------------------------------------------

    private val breakdown = listOf(
        CategoryBreakdown("housing", "Housing & Utilities", totalMinor = 18_500_000L, percentage = 83.0f, transactionCount = 1),
        CategoryBreakdown("transport", "Transport & Fuel", totalMinor = 1_200_000L, percentage = 5.4f, transactionCount = 1)
    )

    private fun assertDonutTotalFits(total: Long, scale: Float = 1.5f, selected: String? = null) {
        val shown = CurrencyFormatter.format(selected?.let { id -> breakdown.first { it.categoryId == id }.totalMinor } ?: total, "HUF")
        setScaled(fontScale = scale) {
            SpendingDonutChartCard(breakdown, total, selectedCategoryId = selected, currency = "HUF", onCategoryClick = {})
        }
        assertFalse("the total in the middle of the ring is cut at $scale", rule.onNodeWithText(shown, useUnmergedTree = true).isCut())
    }

    /** The text column in the middle must fit inside the hole: its corners are no farther from its centre than the hole's radius. */
    private fun assertDonutCentreInsideTheHole(total: Long, scale: Float, selected: String?) {
        setScaled(fontScale = scale) {
            SpendingDonutChartCard(breakdown, total, selectedCategoryId = selected, currency = "HUF", onCategoryClick = {})
        }
        val bounds = rule.onNodeWithTag(DONUT_CENTRE_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val w = (bounds.right - bounds.left).value.toDouble()
        val h = (bounds.bottom - bounds.top).value.toDouble()
        val halfDiagonal = sqrt(w * w + h * h) / 2.0
        val holeRadius = donutHoleDiameter(donutRingSize(scale)).value / 2.0
        assertTrue("centre text ($halfDiagonal dp from its middle to a corner) must fit the hole (radius $holeRadius dp) at font scale $scale", halfDiagonal <= holeRadius)
    }

    @Test
    fun theDonutTotalFitsItsHoleAtScale15() = assertDonutTotalFits(22_284_300L)

    @Test
    fun aHugeDonutTotalStepsDownInsteadOfBeingCutAtScale15() = assertDonutTotalFits(98_765_432_100L)

    @Test
    fun theDonutTotalFitsItsHoleAtScale20() {
        // The centre text used to be capped at 1.3; it now follows the user's scale because the ring grows with it.
        assertDonutTotalFits(22_284_300L, 2.0f)
    }

    @Test
    fun theDonutCentreFitsItsHoleAtScale10() = assertDonutCentreInsideTheHole(22_284_300L, 1.0f, "housing")

    @Test
    fun theDonutCentreFitsItsHoleAtScale15WithASelectedCategory() = assertDonutCentreInsideTheHole(22_284_300L, 1.5f, "housing")

    @Test
    fun theDonutCentreFitsItsHoleAtScale20WithAHugeTotalAndASelectedCategory() =
        assertDonutCentreInsideTheHole(98_765_432_100L, 2.0f, "housing")

    @Test
    fun theDonutIsDescribedToScreenReaders() {
        // Expected text comes from the string resource in the locale the test composes in, so it holds on any device language.
        val total = 19_700_000L
        var expected = ""
        setScaled(fontScale = 1.0f, locale = Locale.ENGLISH) {
            expected = stringResource(
                R.string.analytics_donut_description,
                CurrencyFormatter.format(total, "HUF"),
                breakdown.joinToString("; ") { "${it.categoryName} ${CurrencyFormatter.formatPercent(it.percentage)}" }
            )
            SpendingDonutChartCard(breakdown, total, selectedCategoryId = null, currency = "HUF", onCategoryClick = {})
        }
        rule.onNodeWithContentDescription(expected, useUnmergedTree = true).assertExists()
        assertTrue("every category and its share is spoken", breakdown.all { expected.contains(it.categoryName) })
    }

    // ---- dashboard shortcuts ----------------------------------------------------------------------

    private fun SemanticsNodeInteraction.breaksInsideAWord(text: String): Boolean {
        val layout = textLayout()
        return (1 until layout.lineCount).any { line ->
            val start = layout.getLineStart(line)
            start > 0 && !text[start - 1].isWhitespace()
        }
    }

    private fun assertQuickActionsReadable(scale: Float, locale: Locale?) {
        val titles = mutableListOf<String>()
        setScaled(fontScale = scale, locale = locale) {
            val items = listOf(
                QuickActionItem(stringResource(R.string.add_transaction), Icons.Default.Home) {},
                QuickActionItem(stringResource(R.string.scan_receipt), Icons.Default.Home) {},
                QuickActionItem(stringResource(R.string.import_statement), Icons.Default.Home) {}
            )
            titles.clear()
            titles.addAll(items.map { it.title })
            QuickActions(items)
        }
        titles.forEach { title ->
            val node = rule.onNodeWithText(title, useUnmergedTree = true)
            node.assertIsDisplayed()
            assertFalse("'$title' is cut at font scale $scale", node.isCut())
            assertFalse("'$title' breaks inside a word at font scale $scale", node.breaksInsideAWord(title))
        }
    }

    @Test
    fun englishDashboardShortcutsKeepTheUsersFontSizeAtScale15() = assertQuickActionsReadable(1.5f, Locale.ENGLISH)

    @Test
    fun hungarianDashboardShortcutsKeepTheUsersFontSizeAtScale15() = assertQuickActionsReadable(1.5f, Locale("hu", "HU"))

    @Test
    fun dashboardShortcutsAreReadableAtNormalScale() {
        assertQuickActionsReadable(1.0f, Locale.ENGLISH)
    }

    @Test
    fun hungarianDashboardShortcutsAreReadableAtScale20() = assertQuickActionsReadable(2.0f, Locale("hu", "HU"))

    // ---- search and filters header on Transactions --------------------------------------------------

    private fun setQuickReturnList(pinned: Boolean) {
        setScaled(fontScale = 1.0f) {
            val listState = rememberLazyListState()
            QuickReturnScaffold(listState, pinned = pinned, header = { Text("Search and filters", Modifier.padding(16.dp)) }) { padding ->
                LazyColumn(state = listState, contentPadding = padding, modifier = Modifier.testTag("list")) {
                    items(80) { Text("Row $it", Modifier.fillMaxWidth().height(48.dp)) }
                }
            }
        }
    }

    @Test
    fun theHeaderIsThereAtTheTopLeavesWhenScrollingDownAndReturnsOnTheFirstScrollUp() {
        setQuickReturnList(pinned = false)
        rule.onNodeWithTag(QUICK_RETURN_HEADER_TAG).assertIsDisplayed()
        repeat(3) { rule.onNodeWithTag("list").performTouchInput { swipeUp() } }
        rule.waitForIdle()
        rule.onNodeWithTag(QUICK_RETURN_HEADER_TAG).assertIsNotDisplayed()
        rule.onNodeWithTag("list").performTouchInput { swipeDown(startY = height * 0.3f, endY = height * 0.6f) }
        rule.waitForIdle()
        rule.onNodeWithTag(QUICK_RETURN_HEADER_TAG).assertIsDisplayed()
    }

    @Test
    fun aPinnedHeaderStaysWhileScrolling() {
        setQuickReturnList(pinned = true)
        repeat(3) { rule.onNodeWithTag("list").performTouchInput { swipeUp() } }
        rule.waitForIdle()
        rule.onNodeWithTag(QUICK_RETURN_HEADER_TAG).assertIsDisplayed()
    }

    // ---- Hungarian and dark -----------------------------------------------------------------------

    @Test
    fun aTransferRowIsHungarianUnderTheHungarianLocaleAndItsDetailsAreNotCut() {
        setScaled(fontScale = 1.5f, locale = Locale("hu", "HU")) {
            TransactionRow(
                transaction("hu", "Átváltás EUR", -30_000_000L, direction = TransactionDirection.TRANSFER).copy(pending = true),
                "HUF", null, onClick = {}, categoryName = "Saját számlák között"
            )
        }
        // Spoken description: kind, name, amount, details, in Hungarian.
        val description = rule.onNodeWithText("Átváltás EUR", useUnmergedTree = false)
            .fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ").orEmpty()
        assertTrue("description was: $description", description.startsWith("Átvezetés saját számlák között"))
        assertTrue("description was: $description", description.contains("ellenőrzésre vár"))
        // On screen the quiet line (category, date, status) may take two lines but is not cut.
        assertFalse("the details line is cut", rule.onNodeWithText("Saját számlák között", substring = true, useUnmergedTree = true).isCut())
    }

    @Test
    fun amountsStayWholeInTheDarkThemeAtScale15() {
        setScaled(fontScale = 1.5f, widthDp = 300, dark = true) {
            ListRow(title = longMerchant, subtitle = "1 transaction", amount = hugeAmount)
        }
        assertWholeOnOneLine(rule.onNodeWithText(hugeAmount, useUnmergedTree = true), "the amount")
    }
}
