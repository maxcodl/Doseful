package app.healthre

import android.app.Application
import android.content.Context
import androidx.room.Room
import app.healthre.data.Db
import app.healthre.data.ProfileStore
import app.healthre.data.ThemeSettings

class HealthReApp : Application(), AppEnv {
    override val ctx: Context get() = this
    override val demo = false
    override val db by lazy { Room.databaseBuilder(this, Db::class.java, "healthre.db").fallbackToDestructiveMigration(true).build() }
    override val profile by lazy { ProfileStore(this) }
    override val theme by lazy { ThemeSettings(this) }

    override fun onCreate() {
        super.onCreate()
        Alarms.createChannels(this)
    }
}
