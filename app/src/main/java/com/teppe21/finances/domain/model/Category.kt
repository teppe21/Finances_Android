package com.teppe21.finances.domain.model

import java.time.Instant

data class Category(
    val id: String,
    val name: String,
    val icon: String? = null,
    val color: String? = null,
    val isIncome: Boolean = false,
    val isDefault: Boolean = false,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
) {
    /**
     * English display name. Built-in categories map by id; legacy Hungarian names from
     * databases created by early versions are mapped to their English equivalents.
     */
    fun displayName(): String = BUILT_IN_NAMES[id] ?: LEGACY_NAMES[name] ?: name

    companion object {
        val BUILT_IN_NAMES: Map<String, String> = BuiltIns.categories.associate { it.id to it.name }

        private val LEGACY_NAMES = mapOf(
            "Élelmiszer" to "Food & Groceries",
            "Étkezés / Étterem" to "Dining & Restaurants",
            "Étkezés" to "Dining & Restaurants",
            "Tankolás / Közlekedés" to "Transport & Fuel",
            "Közlekedés" to "Transport & Fuel",
            "Előfizetések" to "Subscriptions",
            "Rezsi / Szolgáltatás" to "Housing & Utilities",
            "Lakhatás" to "Housing & Utilities",
            "Szórakozás" to "Entertainment",
            "Megtakarítás" to "Savings",
            "Bevétel" to "Income",
            "Fizetés" to "Income",
            "Egészség / Patika" to "Health & Pharmacy",
            "Egészség" to "Health & Pharmacy",
            "Bevásárlás" to "Shopping",
            "Átutalások" to "Transfers",
            "Átutalás" to "Transfers",
            "Utalás" to "Transfers",
            "Egyéb / Ismeretlen" to "Other / Uncategorized",
            "Egyéb" to "Other / Uncategorized",
            "Ismeretlen" to "Other / Uncategorized"
        )
    }
}

/**
 * A keyword rule. [matchType] is `contains` (the keyword starts a word; keywords of three
 * characters or fewer must be a whole word), `word` (whole word), `exact` (the whole merchant
 * name or description) or `regex`. Higher [priority] wins; on a tie the longer keyword wins.
 */
data class CategoryRule(
    val id: String,
    val categoryId: String,
    val pattern: String,
    val matchType: String = "contains",
    val priority: Int = PRIORITY_MERCHANT,
    val isActive: Boolean = true,
    val createdAt: Instant = Instant.now()
) {
    val isUserRule: Boolean get() = priority >= PRIORITY_USER

    companion object {
        /** Rules the user created always beat built-in ones. */
        const val PRIORITY_USER = 50
        /** Exchanges, own-account moves, top-ups, balance migrations. */
        const val PRIORITY_OWN_TRANSFER = 30
        const val PRIORITY_FEES = 22
        const val PRIORITY_CASH = 20
        /** The bank's own category column, when a CSV has one. */
        const val PRIORITY_BANK_CATEGORY = 15
        const val PRIORITY_MERCHANT = 10
        /** "Átutalás", "Transfer to": only when nothing more specific matched. */
        const val PRIORITY_GENERIC_TRANSFER = 5
        const val PRIORITY_WEAK_INCOME = 4
    }
}
