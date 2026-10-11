package com.teppe21.finances.feature.categories

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.teppe21.finances.R
import com.teppe21.finances.domain.model.Account
import com.teppe21.finances.domain.model.AccountType
import com.teppe21.finances.domain.model.Category
import com.teppe21.finances.domain.model.TransactionSource

/** Translated names of the built-in categories, by id. */
@StringRes
private fun builtInName(id: String): Int? = when (id) {
    "food" -> R.string.category_name_food
    "dining" -> R.string.category_name_dining
    "transport" -> R.string.category_name_transport
    "subscriptions" -> R.string.category_name_subscriptions
    "housing" -> R.string.category_name_housing
    "entertainment" -> R.string.category_name_entertainment
    "savings" -> R.string.category_name_savings
    "income" -> R.string.category_name_income
    "health" -> R.string.category_name_health
    "shopping" -> R.string.category_name_shopping
    "transfers" -> R.string.category_name_transfers
    "other" -> R.string.category_name_other
    "personal_transfers" -> R.string.category_name_personal_transfers
    "cash" -> R.string.category_name_cash
    "taxes" -> R.string.category_name_taxes
    "fees" -> R.string.category_name_fees
    "interest" -> R.string.category_name_interest
    "electronics" -> R.string.category_name_electronics
    else -> null
}

/**
 * The name to show for [category]: a built-in category that still has its default name is
 * shown in the app's language; a renamed or custom category keeps the user's name.
 */
fun categoryLabel(category: Category, resources: Resources): String {
    val res = builtInName(category.id)?.takeIf { category.hasDefaultName } ?: return category.displayName()
    return resources.getString(res)
}

@Composable
fun categoryLabel(category: Category): String = categoryLabel(category, LocalContext.current.resources)

@Composable
fun sourceLabel(source: TransactionSource): String = LocalContext.current.getString(
    when (source) {
        TransactionSource.MANUAL -> R.string.source_manual
        TransactionSource.CSV -> R.string.source_csv
        TransactionSource.NOTIFICATION -> R.string.source_notification
        TransactionSource.RECEIPT -> R.string.source_receipt
        TransactionSource.OPEN_BANKING -> R.string.source_open_banking
        TransactionSource.BACKUP -> R.string.source_backup
    }
)

/** Default accounts keep a translated name until the user renames them. */
@Composable
fun accountLabel(account: Account): String {
    val res = when (account.id) {
        "acc_cash" -> R.string.account_default_cash
        "acc_otp" -> R.string.account_default_otp
        "acc_savings" -> R.string.account_default_savings
        else -> null
    }
    return if (res != null && account.name in Account.DEFAULT_NAMES) LocalContext.current.getString(res) else account.getDisplayName()
}

@Composable
fun accountTypeLabel(type: AccountType): String = LocalContext.current.getString(
    when (type) {
        AccountType.BANK -> R.string.account_type_bank
        AccountType.SAVINGS -> R.string.account_type_savings
        AccountType.CREDIT_CARD -> R.string.account_type_credit_card
        AccountType.CASH -> R.string.account_type_cash
        AccountType.INVESTMENT -> R.string.account_type_investment
        AccountType.OTHER -> R.string.account_type_other
    }
)
