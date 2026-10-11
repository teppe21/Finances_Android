package com.teppe21.finances.domain.model

/**
 * Built-in categories and keyword rules. Fresh installs get all of them; the 5 -> 6 migration
 * adds the categories and the `b6_` rules that are missing and re-points a few schema-5 rules.
 *
 * Rule ids are derived from category and keyword. Do not remove or re-target a shipped keyword:
 * a later migration could not tell a rule the user deleted from one that never existed.
 */
object BuiltIns {

    /** Categories whose transactions move money between the user's own accounts or into cash. */
    val OWN_TRANSFER_CATEGORIES = setOf("transfers", "cash")
    const val PERSONAL_TRANSFER_CATEGORY = "personal_transfers"
    const val UNCATEGORIZED = "other"
    const val INCOME_FALLBACK = "income"

    /** The transaction kind a category implies for an amount with this sign. */
    fun kindFor(categoryId: String?, signedAmountMinor: Long): TransactionDirection = when {
        categoryId in OWN_TRANSFER_CATEGORIES -> TransactionDirection.TRANSFER
        categoryId == PERSONAL_TRANSFER_CATEGORY -> TransactionDirection.PERSONAL_TRANSFER
        signedAmountMinor > 0 -> TransactionDirection.INCOME
        else -> TransactionDirection.EXPENSE
    }

    data class CategorySeed(val id: String, val name: String, val icon: String, val color: String, val isIncome: Boolean = false)

    val categories = listOf(
        CategorySeed("food", "Food & Groceries", "shopping-cart", "#10B981"),
        CategorySeed("dining", "Dining & Restaurants", "utensils", "#F59E0B"),
        CategorySeed("transport", "Transport & Fuel", "car", "#EF4444"),
        CategorySeed("subscriptions", "Subscriptions", "gamepad-2", "#0284C7"),
        CategorySeed("housing", "Housing & Utilities", "home", "#3B82F6"),
        CategorySeed("entertainment", "Entertainment", "film", "#D97706"),
        CategorySeed("savings", "Savings", "piggy-bank", "#06B6D4"),
        CategorySeed("income", "Income", "wallet", "#10B981", isIncome = true),
        CategorySeed("health", "Health & Pharmacy", "heart-pulse", "#14B8A6"),
        CategorySeed("shopping", "Shopping", "shopping-bag", "#F97316"),
        CategorySeed("transfers", "Between own accounts", "arrow-left-right", "#6366F1"),
        CategorySeed("other", "Other / Uncategorized", "help-circle", "#6B7280"),
        // Added in schema 6.
        CategorySeed("personal_transfers", "Transfers with people", "users", "#8B5CF6"),
        CategorySeed("cash", "Cash & ATM", "banknote", "#64748B"),
        CategorySeed("taxes", "Government & Taxes", "landmark", "#0F766E"),
        CategorySeed("fees", "Bank fees", "receipt", "#B45309"),
        CategorySeed("interest", "Interest", "percent", "#0891B2"),
        CategorySeed("electronics", "Electronics", "cpu", "#7C3AED")
    )

    val SCHEMA_6_CATEGORY_IDS = setOf("personal_transfers", "cash", "taxes", "fees", "interest", "electronics")

    /** The keyword rules seeded by schema 5, with their original ids. */
    private val schema5Rules: List<CategoryRule> = buildList {
        fun add(prefix: String, cat: String, kws: List<String>) =
            kws.forEachIndexed { i, kw -> add(CategoryRule("${prefix}_$i", cat, kw, "contains", CategoryRule.PRIORITY_MERCHANT)) }
        add("rule_food", "food", listOf("lidl", "spar", "interspar", "aldi", "tesco", "auchan", "penny", "coop", "cba", "pékség", "lipóti", "príma"))
        add("rule_dining", "dining", listOf("wolt", "foodora", "mcdonalds", "kfc", "burger king", "starbucks", "étterem", "kávézó", "pizza", "bistro"))
        add("rule_trans", "transport", listOf("mol", "shell", "omv", "orlen", "lukoil", "bkk", "máv", "autópálya", "parkolás", "uber"))
        add("rule_sub", "subscriptions", listOf("netflix", "spotify", "google", "apple", "youtube", "patreon", "steam", "disney", "hbo", "chatgpt"))
        add("rule_inc", "income", listOf("fizetés", "munkabér", "bér", "salary", "payroll", "jóváírás"))
        add("rule_transfer", "transfers", listOf("átutalás", "utalás", "átutalt", "átvezetés", "transfer", "pénzküldés", "bank transfer"))
    }

    /**
     * Schema-5 rules that schema 6 changes in place: generic transfer wording now means a
     * transfer with another person and sits below merchant keywords, "átvezetés" (an internal
     * transfer) becomes a strong own-account rule, and the bare "jóváírás" (credit) drops
     * below the transfer wording so an incoming transfer is not called income.
     */
    val schema6Updates: List<CategoryRule> = listOf(
        CategoryRule("rule_transfer_0", PERSONAL_TRANSFER_CATEGORY, "átutalás", "contains", CategoryRule.PRIORITY_GENERIC_TRANSFER),
        CategoryRule("rule_transfer_1", PERSONAL_TRANSFER_CATEGORY, "utalás", "contains", CategoryRule.PRIORITY_GENERIC_TRANSFER),
        CategoryRule("rule_transfer_2", PERSONAL_TRANSFER_CATEGORY, "átutalt", "contains", CategoryRule.PRIORITY_GENERIC_TRANSFER),
        CategoryRule("rule_transfer_3", "transfers", "átvezetés", "contains", CategoryRule.PRIORITY_OWN_TRANSFER),
        CategoryRule("rule_transfer_4", PERSONAL_TRANSFER_CATEGORY, "transfer", "contains", CategoryRule.PRIORITY_GENERIC_TRANSFER),
        CategoryRule("rule_transfer_5", PERSONAL_TRANSFER_CATEGORY, "pénzküldés", "contains", CategoryRule.PRIORITY_GENERIC_TRANSFER),
        CategoryRule("rule_transfer_6", PERSONAL_TRANSFER_CATEGORY, "bank transfer", "contains", CategoryRule.PRIORITY_GENERIC_TRANSFER),
        CategoryRule("rule_inc_5", "income", "jóváírás", "contains", CategoryRule.PRIORITY_WEAK_INCOME),
        // "fizetés" also means "payment"; it stays an income keyword, which now only applies to credits.
        CategoryRule("rule_inc_0", "income", "fizetés", "contains", CategoryRule.PRIORITY_MERCHANT)
    )

    private data class Group(val category: String, val priority: Int, val keywords: List<String>, val matchType: String = "contains")

    /** Added in schema 6. `word` rules must match a whole word, `exact` the whole merchant or description. */
    private val schema6Groups = listOf(
        Group("transfers", CategoryRule.PRIORITY_OWN_TRANSFER, listOf(
            "exchanged to", "exchanged from", "exchange to", "exchange from", "currency exchange", "converted",
            "átváltás", "devizaváltás", "valutaváltás", "deviza átváltás", "balance migration",
            "top-up by", "topped up", "topup", "saját számlák közötti", "saját számlák között",
            "saját számlára", "saját számláról", "own account", "between own accounts", "to pocket", "from pocket",
            "to savings", "from savings", "savings vault", "megtakarítási számlára", "megtakarítási számláról", "lekötés",
            // Paying off a credit card: the purchases were already counted when they were made.
            "hitelkártya törlesztés", "credit card repayment"
        )),
        Group("transfers", CategoryRule.PRIORITY_OWN_TRANSFER, listOf("exchange"), "word"),
        Group("cash", CategoryRule.PRIORITY_CASH, listOf("atm", "készpénzfelvétel", "cash withdrawal", "cash at", "bankautomata")),
        Group("fees", CategoryRule.PRIORITY_FEES, listOf(
            "számlavezetési", "kártyadíj", "kártya díj", "kártya éves díja", "zárlati díj", "tranzakciós díj",
            "kezelési költség", "készpénzfelvételi díj", "atm fee", "fee", "monthly fee", "commission", "jutalék", "díjcsomag"
        )),
        Group("interest", CategoryRule.PRIORITY_MERCHANT, listOf("kamat", "hitelkamat", "betéti kamat", "interest")),
        Group("taxes", CategoryRule.PRIORITY_MERCHANT, listOf(
            "magyar államkincstár", "államkincstár", "nemzeti adó", "adó befizetés", "adóbefizetés", "szja",
            "gépjárműadó", "iparűzési", "önkormányzat", "illeték", "járulék", "kormányhivatal", "nemzeti útdíj"
        )),
        Group("taxes", CategoryRule.PRIORITY_MERCHANT, listOf("nav", "áfa", "mák"), "word"),
        Group("housing", CategoryRule.PRIORITY_MERCHANT, listOf(
            "mvm", "elmű", "émász", "főgáz", "főtáv", "vízművek", "díjnet", "telekom", "vodafone", "one hungary",
            "yettel", "telenor", "invitel", "közös költség", "lakbér", "bérleti díj", "albérlet", "rezsi",
            "fűtés", "fkf", "hulladék", "nkm", "e.on", "tigáz", "netfone"
        )),
        Group("housing", CategoryRule.PRIORITY_MERCHANT, listOf("digi", "eon", "upc"), "word"),
        Group("food", CategoryRule.PRIORITY_MERCHANT, listOf(
            "kaufland", "reál", "match", "billa", "hofer", "kifli", "roksh", "zöldséges", "hentes", "piac",
            "élelmiszer", "abc", "csemege", "spar express", "pékáru"
        )),
        Group("dining", CategoryRule.PRIORITY_MERCHANT, listOf(
            "uber eats", "bolt food", "netpincér", "mcdonald", "kifőzde", "büfé", "söröző", "kocsma", "pub",
            "cafe", "coffee", "costa", "burger", "subway", "gyros", "kebab", "sushi", "kantin", "menza",
            "cukrászda", "fagyizó", "lángos", "étkezde", "vendéglő", "csárda", "bisztró", "restaurant", "trattoria",
            "pizzeria", "dominos", "domino s", "five guys", "pret a manger"
        )),
        Group("transport", CategoryRule.PRIORITY_MERCHANT, listOf(
            "bubi", "volánbusz", "volan", "gysev", "flixbus", "ryanair", "wizz air", "wizzair", "easyjet", "lufthansa",
            "taxi", "főtaxi", "freenow", "free now", "e-matrica", "ematrica", "hu-go", "parking", "parkolo",
            "benzinkút", "töltőállomás", "bkv", "avia", "lime", "tier", "mav-start", "bolt.eu"
        )),
        Group("transport", CategoryRule.PRIORITY_MERCHANT, listOf("bolt"), "exact"),
        Group("subscriptions", CategoryRule.PRIORITY_MERCHANT, listOf(
            "amazon prime", "prime video", "icloud", "microsoft", "adobe", "playstation", "xbox", "nintendo",
            "openai", "dropbox", "duolingo", "tidal", "deezer", "canva", "github", "notion", "audible",
            "skyshowtime", "magentatv", "max.com", "hbo max", "rtl+"
        )),
        Group("health", CategoryRule.PRIORITY_MERCHANT, listOf(
            "rossmann", "müller", "benu", "gyógyszertár", "patika", "pharmacy", "drogéria", "drogerie", "orvos",
            "fogorvos", "klinika", "medicover", "doktor", "optika", "rendelő", "egészségpénztár"
        )),
        Group("health", CategoryRule.PRIORITY_MERCHANT, listOf("dm"), "word"),
        Group("shopping", CategoryRule.PRIORITY_MERCHANT, listOf(
            "decathlon", "ikea", "zara", "pepco", "jysk", "praktiker", "bauhaus", "amazon", "aliexpress", "temu",
            "shein", "emag", "about you", "zalando", "deichmann", "tchibo", "libri", "líra", "bookline", "primark",
            "sinsay", "reserved", "mango", "takko", "action", "tedi", "játékbolt"
        )),
        Group("shopping", CategoryRule.PRIORITY_MERCHANT, listOf("obi", "kik"), "word"),
        Group("electronics", CategoryRule.PRIORITY_MERCHANT, listOf(
            "earfun", "media markt", "alza", "euronics", "extreme digital", "edigital", "ipon", "aqua.hu",
            "samsung", "xiaomi", "anker", "apple store", "logitech", "jbl", "bose", "elektronika", "electronics"
        )),
        Group("entertainment", CategoryRule.PRIORITY_MERCHANT, listOf(
            "cinema city", "mozi", "jegy.hu", "eventim", "tixa", "színház", "múzeum", "koncert", "bowling",
            "escape room", "strand", "fürdő", "aquaworld", "palatinus"
        )),
        Group("income", CategoryRule.PRIORITY_MERCHANT, listOf(
            "bérjövedelem", "wage", "cafeteria", "nyugdíj", "pension", "családi pótlék", "gyed", "gyes", "csed"
        )),
        Group(PERSONAL_TRANSFER_CATEGORY, CategoryRule.PRIORITY_GENERIC_TRANSFER, listOf(
            "azonnali átutalás", "transfer to", "transfer from", "payment from", "sent money to",
            "received money from", "money sent", "money received", "revolut user"
        ))
    )

    /** Ids derive from category and keyword, so editing the lists never renumbers other rules. */
    val schema6Rules: List<CategoryRule> = schema6Groups.flatMap { g ->
        g.keywords.map { kw ->
            val slug = com.teppe21.finances.core.utils.TextNormalizer.normalizeSearch(kw).replace(' ', '_')
            CategoryRule("b6_${g.category}_$slug", g.category, kw, g.matchType, g.priority)
        }
    }

    /** Category names banks and budgeting apps put in a CSV category column, folded. */
    private val bankCategoryLabels = mapOf(
        "groceries" to "food", "grocery" to "food", "elelmiszer" to "food", "bevasarlas elelmiszer" to "food",
        "restaurants" to "dining", "restaurant" to "dining", "eating out" to "dining", "etterem" to "dining", "vendeglatas" to "dining",
        "transport" to "transport", "travel" to "transport", "fuel" to "transport", "kozlekedes" to "transport", "uzemanyag" to "transport",
        "utilities" to "housing", "bills" to "housing", "housing" to "housing", "rezsi" to "housing", "lakhatas" to "housing",
        "health" to "health", "healthcare" to "health", "egeszseg" to "health",
        "shopping" to "shopping", "vasarlas" to "shopping",
        "entertainment" to "entertainment", "szorakozas" to "entertainment",
        "subscriptions" to "subscriptions", "elofizetes" to "subscriptions", "elofizetesek" to "subscriptions",
        "transfers" to PERSONAL_TRANSFER_CATEGORY, "transfer" to PERSONAL_TRANSFER_CATEGORY, "atutalas" to PERSONAL_TRANSFER_CATEGORY,
        "cash" to "cash", "keszpenz" to "cash", "atm" to "cash",
        "fees" to "fees", "dijak" to "fees", "salary" to "income", "income" to "income", "fizetes" to "income", "jovedelem" to "income",
        "taxes" to "taxes", "ado" to "taxes", "adok" to "taxes"
    )

    /** The built-in category a bank's own category label names, or null ("General" names nothing). */
    fun categoryForBankLabel(label: String?): String? =
        bankCategoryLabels[com.teppe21.finances.core.utils.TextNormalizer.normalizeSearch(label)]

    /** Every built-in rule as a fresh install creates it. */
    val allRules: List<CategoryRule>
        get() {
            val updates = schema6Updates.associateBy { it.id }
            return schema5Rules.map { updates[it.id] ?: it } + schema6Rules
        }
}
