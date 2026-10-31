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
