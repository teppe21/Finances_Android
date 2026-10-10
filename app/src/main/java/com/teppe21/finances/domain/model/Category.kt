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
        val BUILT_IN_NAMES = mapOf(
            "food" to "Food & Groceries",
            "dining" to "Dining & Restaurants",
            "transport" to "Transport & Fuel",
            "subscriptions" to "Subscriptions",
            "housing" to "Housing & Utilities",
            "entertainment" to "Entertainment",
            "savings" to "Savings",
            "income" to "Income",
            "health" to "Health & Pharmacy",
            "shopping" to "Shopping",
            "transfers" to "Transfers",
            "other" to "Other / Uncategorized"
        )

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

data class CategoryRule(
    val id: String,
    val categoryId: String,
    val pattern: String,
    val matchType: String = "contains",
    val priority: Int = 10,
    val isActive: Boolean = true,
    val createdAt: Instant = Instant.now()
)
