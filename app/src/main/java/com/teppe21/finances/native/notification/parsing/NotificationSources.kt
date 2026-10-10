package com.teppe21.finances.native.notification.parsing

enum class SourceType { BANK, WALLET }

/**
 * A supported notification source. Routing is by exact Android package name only;
 * titles, icons and merchant names are never used to pick a source.
 */
data class NotificationSource(
    val id: String,
    val displayName: String,
    /** Institution name used to match accounts (Account.institution). */
    val institution: String,
    val type: SourceType,
    val packages: Set<String>,
    val parser: SourceParser,
    /**
     * True when the parser has fixtures taken from real notifications. Sources without
     * verified fixtures always send recognized transactions to the review queue.
     */
    val verifiedFixtures: Boolean
)

object SupportedSources {

    val REVOLUT = NotificationSource(
        id = "revolut",
        displayName = "Revolut",
        institution = "Revolut",
        type = SourceType.BANK,
        packages = setOf("com.revolut.revolut"),
        parser = RevolutParser,
        verifiedFixtures = true
    )

    val GOOGLE_WALLET = NotificationSource(
        id = "google_wallet",
        displayName = "Google Wallet",
        institution = "Google Wallet",
        type = SourceType.WALLET,
        packages = setOf("com.google.android.apps.walletnfcrel"),
        parser = GoogleWalletParser,
        verifiedFixtures = true
    )

    val OTP = NotificationSource(
        id = "otp",
        displayName = "OTP Bank",
        institution = "OTP Bank",
        type = SourceType.BANK,
        packages = setOf("hu.otpbank.smartbank", "hu.otpbank.mobilbank"),
        parser = OtpParser,
        verifiedFixtures = false
    )

    val WISE = NotificationSource(
        id = "wise",
        displayName = "Wise",
        institution = "Wise",
        type = SourceType.BANK,
        packages = setOf("com.transferwise.android"),
        parser = WiseParser,
        verifiedFixtures = false
    )

    val ERSTE = NotificationSource(
        id = "erste",
        displayName = "Erste Bank (George)",
        institution = "Erste Bank",
        type = SourceType.BANK,
        packages = setOf("hu.erstebank.george.hungary", "hu.erstebank.mobilebank"),
        parser = HungarianBankParser("Erste Bank"),
        verifiedFixtures = false
    )

    val MBH = NotificationSource(
        id = "mbh",
        displayName = "MBH Bank",
        institution = "MBH Bank",
        type = SourceType.BANK,
        packages = setOf("hu.mbhbank.app", "hu.takarek.mobilbank"),
        parser = HungarianBankParser("MBH Bank"),
        verifiedFixtures = false
    )

    val ALL: List<NotificationSource> = listOf(REVOLUT, GOOGLE_WALLET, OTP, WISE, ERSTE, MBH)

    private val byPackage: Map<String, NotificationSource> =
        ALL.flatMap { source -> source.packages.map { it to source } }.toMap()

    fun forPackage(packageName: String?): NotificationSource? =
        packageName?.let { byPackage[it] }

    fun byId(id: String?): NotificationSource? = ALL.firstOrNull { it.id == id }

    /** Institution names a wallet may show for the card it charged. */
    val CARD_ISSUER_KEYWORDS: List<Pair<String, String>> = listOf(
        "revolut" to "Revolut",
        "otp" to "OTP Bank",
        "erste" to "Erste Bank",
        "mbh" to "MBH Bank",
        "wise" to "Wise"
    )
}
