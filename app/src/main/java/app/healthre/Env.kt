package app.healthre

import android.content.Context
import app.healthre.data.Db
import app.healthre.data.ProfileStore
import app.healthre.data.ThemeSettings

/** Everything the UI needs. The real app and the demo implement it differently, so the screens are identical. */
interface AppEnv {
    val ctx: Context
    val db: Db
    val profile: ProfileStore
    val theme: ThemeSettings
    /** True in demo mode: fake data, and no real alarms or notifications are touched. */
    val demo: Boolean
}

/** True when the Cycle tab tracks someone else (a partner): chosen in Profile, else Male gender. */
val AppEnv.partnerMode: Boolean
    get() = theme.cycleFor == "partner" || (theme.cycleFor == "" && profile.value.gender == "Male")

/** Partner's name for notifications and copy, or "her". */
val AppEnv.partnerName: String get() = theme.cyclePartnerName.trim().ifBlank { "her" }
