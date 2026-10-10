package com.teppe21.finances.native.notification.parsing

/**
 * Security / authentication messages: OTP codes, login alerts, 3-D Secure approval
 * requests. These are rejected before any parsing and never become financial events.
 */
object SecurityFilter {

    private val STRONG_CUES = listOf(
        "code", "codes", "passcode", "password", "pin", "otp code", "one-time", "one time password",
        "verification", "verify", "2fa", "two-factor", "two factor", "authentication", "authenticate",
        "log in", "login", "logged in", "sign in", "signed in", "new device",
        "approve", "approval", "authorise", "authorize", "confirm in the app", "confirm the payment",
        "kod", "kodja", "kodod", "jelszo", "egyszer hasznalatos", "ellenorzo",
        "hitelesit*", "jovahagy*", "bejelentkez*", "belepes*", "belepesi", "uj eszkoz",
        "biztonsagi kod", "megerosites szukseges", "erositsd meg", "erositse meg"
    )

    private val CODE_PATTERN = Regex("(?<![\\d.,])\\d{4,8}(?![\\d.,])")
    private val SOFT_CUES = listOf("security", "biztonsag*", "suspicious", "gyanus")

    fun isSecurityMessage(folded: String): Boolean {
        if (Cues.anyCue(folded, STRONG_CUES)) return true
        // "Security alert ... 482913" style messages: soft cue plus a bare numeric code.
        return Cues.anyCue(folded, SOFT_CUES) && CODE_PATTERN.containsMatchIn(folded)
    }
}

/**
 * Promotional / referral / reward messages.
 *
 * Strong cues reject the notification outright. Weak cues ("bonus", "reward", ...) only
 * reject it when the parser did not find a genuine outgoing payment, because merchant
 * names can legitimately contain such words ("Bonus Pizza").
 */
object PromotionalFilter {

    val STRONG_CUES = listOf(
        "refer", "referral", "referrals", "invite", "invites", "eligible friend", "friends",
        "t&cs", "t&c", "terms and conditions", "terms apply", "days left", "day left",
        "limited time", "giveaway", "sweepstake*", "promo code", "sign up", "upgrade to",
        "ajanl*", "baratod*", "baratai*", "hivd meg", "hivj meg", "hivja meg", "nyeremeny*",
        "nyerj*", "sorsolas*", "akcio*", "promoci*", "feltetelek", "napod van", "nap van hatra"
    )

    val WEAK_CUES = listOf(
        "earn", "earned", "reward", "rewards", "bonus", "cashback", "cash back", "promotion",
        "offer", "offers", "win", "deal", "discount", "free", "jutalom*", "bonusz*", "kedvezmeny*",
        "ajandek*", "ingyen*", "visszateritunk"
    )

    fun hasStrongCue(folded: String): Boolean = Cues.anyCue(folded, STRONG_CUES)

    fun hasWeakCue(folded: String): Boolean = Cues.anyCue(folded, WEAK_CUES)
}
