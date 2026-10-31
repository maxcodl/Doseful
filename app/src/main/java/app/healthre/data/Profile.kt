package app.healthre.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.io.File

data class Profile(
    val name: String = "",
    val email: String = "",
    val age: Int? = null,
    val gender: String = "",
    val heightCm: Float? = null,
    val weightKg: Float? = null,
    val metric: Boolean = true,
    val onboarded: Boolean = false,
) {
    val bmi: Float? get() = if (heightCm != null && weightKg != null && heightCm > 0) weightKg / ((heightCm / 100) * (heightCm / 100)) else null

    fun toJson() = JSONObject().put("name", name).put("email", email).put("age", age ?: JSONObject.NULL)
        .put("gender", gender).put("heightCm", heightCm?.toDouble() ?: JSONObject.NULL)
        .put("weightKg", weightKg?.toDouble() ?: JSONObject.NULL).put("metric", metric).put("onboarded", onboarded)

    companion object {
        fun fromJson(j: JSONObject) = Profile(
            j.optString("name"), j.optString("email"),
            if (j.isNull("age")) null else j.optInt("age"), j.optString("gender"),
            if (j.isNull("heightCm")) null else j.optDouble("heightCm").toFloat(),
            if (j.isNull("weightKg")) null else j.optDouble("weightKg").toFloat(),
            j.optBoolean("metric", true), j.optBoolean("onboarded", true),
        )
    }
}

/** Emergency information shown on the Profile page. All fields are optional. */
data class MedicalId(
    val blood: String = "",
    val allergies: String = "",
    val conditions: String = "",
    val doctor: String = "",
    val doctorPhone: String = "",
    val emergencyName: String = "",
    val emergencyPhone: String = "",
) {
    val isEmpty get() = listOf(blood, allergies, conditions, doctor, doctorPhone, emergencyName, emergencyPhone).all { it.isBlank() }

    fun toJson() = JSONObject().put("blood", blood).put("allergies", allergies).put("conditions", conditions).put("doctor", doctor)
        .put("doctorPhone", doctorPhone).put("emergencyName", emergencyName).put("emergencyPhone", emergencyPhone)

    companion object {
        fun fromJson(j: JSONObject) = MedicalId(
            j.optString("blood"), j.optString("allergies"), j.optString("conditions"), j.optString("doctor"),
            j.optString("doctorPhone"), j.optString("emergencyName"), j.optString("emergencyPhone"),
        )
    }
}

class ProfileStore(ctx: Context, prefsName: String = "profile", avatarName: String = "avatar.jpg") {
    private val appCtx = ctx.applicationContext
    private val sp = ctx.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    private val avatarFile = File(appCtx.filesDir, avatarName)

    var avatarVersion by mutableIntStateOf(0)
        private set

    var medical by mutableStateOf(
        MedicalId(
            sp.getString("m_blood", "") ?: "", sp.getString("m_allergies", "") ?: "", sp.getString("m_conditions", "") ?: "",
            sp.getString("m_doctor", "") ?: "", sp.getString("m_doctor_phone", "") ?: "",
            sp.getString("m_em_name", "") ?: "", sp.getString("m_em_phone", "") ?: "",
        ),
    )
        private set

    fun saveMedical(m: MedicalId) {
        sp.edit().putString("m_blood", m.blood).putString("m_allergies", m.allergies).putString("m_conditions", m.conditions)
            .putString("m_doctor", m.doctor).putString("m_doctor_phone", m.doctorPhone)
            .putString("m_em_name", m.emergencyName).putString("m_em_phone", m.emergencyPhone).apply()
        medical = m
    }

    /** Erases everything this store holds (used to discard demo data). */
    fun wipe() { sp.edit().clear().apply(); avatarFile.delete() }

    fun avatar(): Bitmap? = if (avatarFile.exists()) BitmapFactory.decodeFile(avatarFile.path) else null

    /** Decodes [uri] (EXIF-aware on API 28+), centre-crops to a 384px square and stores it app-privately. */
    fun setAvatar(uri: Uri?) {
        if (uri == null) { avatarFile.delete() } else {
            val src: Bitmap = if (Build.VERSION.SDK_INT >= 28) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(appCtx.contentResolver, uri)) { d, info, _ ->
                    d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val s = 1024f / maxOf(info.size.width, info.size.height)
                    if (s < 1f) d.setTargetSize((info.size.width * s).toInt(), (info.size.height * s).toInt())
                }
            } else appCtx.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it)!! }
            val side = minOf(src.width, src.height)
            val sq = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
            val out = Bitmap.createScaledBitmap(sq, 384, 384, true)
            avatarFile.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        }
        avatarVersion++
    }

    fun avatarBase64(): String? = if (avatarFile.exists()) Base64.encodeToString(avatarFile.readBytes(), Base64.NO_WRAP) else null

    fun setAvatarBase64(b64: String) {
        if (b64.isBlank()) avatarFile.delete() else avatarFile.writeBytes(Base64.decode(b64, Base64.NO_WRAP))
        avatarVersion++
    }

    var value by mutableStateOf(
        Profile(
            sp.getString("name", "") ?: "", sp.getString("email", "") ?: "",
            sp.getString("age", "")?.toIntOrNull(), sp.getString("gender", "") ?: "",
            sp.getString("height", "")?.toFloatOrNull(), sp.getString("weight", "")?.toFloatOrNull(),
            sp.getBoolean("metric", true), sp.getBoolean("onboarded", false),
        ),
    )
        private set

    fun save(p: Profile) {
        sp.edit().putString("name", p.name).putString("email", p.email).putString("age", p.age?.toString() ?: "")
            .putString("gender", p.gender).putString("height", p.heightCm?.toString() ?: "")
            .putString("weight", p.weightKg?.toString() ?: "").putBoolean("metric", p.metric)
            .putBoolean("onboarded", p.onboarded).apply()
        value = p
    }
}

/** Look of the floating Liquid Glass navigation bar. Distances are in dp. */
data class GlassStyle(
    val blur: Float = 10f,
    val lensHeight: Float = 16f,
    val lensAmount: Float = 24f,
    val depth: Boolean = false,
    val chromatic: Boolean = false,
    val saturation: Float = 1.5f,
    val tint: Float = 0.55f,
    val edgeWidth: Float = 1f,
    val edgeAlpha: Float = 1f,
    /** 0 = directional, 1 = ambient, 2 = plain. */
    val edgeStyle: Int = 0,
    val shadowRadius: Float = 16f,
    val shadowAlpha: Float = 0.12f,
    val innerRadius: Float = 16f,
    val innerAlpha: Float = 0f,
    /** 0 = pill (selected item expands), 1 = icons only, 2 = icons with labels, 3 = full-width dock. */
    val type: Int = 0,
    /** Corner roundness in percent: 0 = square, 100 = fully round. A percentage can never exceed the bar's own size. */
    val radius: Float = 100f,
    /** Selected item look: 0 = filled, 1 = outlined, 2 = soft glass, 3 = dot, 4 = colour only. */
    val selection: Int = 0,
    /** Corner roundness of the selected item in percent (0 = square, 100 = fully round), independent of the bar. */
    val selRadius: Float = 100f,
)

/** Theme choice. One instance is shared by the real app and the demo, because it is a device preference. */
class ThemeSettings(ctx: Context) {
    private val sp = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** 0 = system, 1 = light, 2 = dark. */
    var themeMode by mutableIntStateOf(sp.getInt("theme", 0))
        private set
    var amoled by mutableStateOf(sp.getBoolean("amoled", false))
        private set

    /** Ask for the device screen lock (PIN, pattern or biometric) when Doseful opens. */
    var appLock by mutableStateOf(sp.getBoolean("app_lock", false))
        private set

    fun updateAppLock(b: Boolean) { sp.edit().putBoolean("app_lock", b).apply(); appLock = b }

    /** True (default) = reminders ring like an alarm; false = a normal notification sound. */
    var reminderAlarm by mutableStateOf(sp.getBoolean("reminder_alarm", true))
        private set

    fun updateReminderAlarm(b: Boolean) { sp.edit().putBoolean("reminder_alarm", b).apply(); reminderAlarm = b }

    /** Multiplies animation durations. 1 = normal, higher = slower, 0 = instant. */
    var animScale by mutableStateOf(sp.getFloat("anim_scale", 1.5f))
        private set

    fun updateAnimScale(v: Float) { sp.edit().putFloat("anim_scale", v).apply(); animScale = v }

    /** Scales the corner radius of every card, panel, field and dialog. 100 = Material default, 0 = square. */
    var uiRadius by mutableStateOf(sp.getFloat("ui_radius", 100f))
        private set

    fun updateUiRadius(v: Float) { sp.edit().putFloat("ui_radius", v).apply(); uiRadius = v }

    /** False = standard bottom bar, true = floating pill. */
    var floatingNav by mutableStateOf(sp.getBoolean("floating_nav", false))
        private set

    var glass by mutableStateOf(readGlass())
        private set

    private fun readGlass(): GlassStyle {
        val d = GlassStyle()
        return GlassStyle(
            sp.getFloat("g_blur", d.blur), sp.getFloat("g_lens_h", d.lensHeight), sp.getFloat("g_lens_a", d.lensAmount),
            sp.getBoolean("g_depth", d.depth), sp.getBoolean("g_chroma", d.chromatic), sp.getFloat("g_sat", d.saturation),
            sp.getFloat("g_tint", d.tint), sp.getFloat("g_edge_w", d.edgeWidth), sp.getFloat("g_edge_a", d.edgeAlpha),
            sp.getInt("g_edge_style", d.edgeStyle), sp.getFloat("g_sh_r", d.shadowRadius), sp.getFloat("g_sh_a", d.shadowAlpha),
            sp.getFloat("g_in_r", d.innerRadius), sp.getFloat("g_in_a", d.innerAlpha),
            sp.getInt("g_type", d.type), sp.getFloat("g_radius_pct", d.radius), sp.getInt("g_sel", d.selection), sp.getFloat("g_sel_radius", d.selRadius),
        )
    }

    fun updateGlass(g: GlassStyle) {
        sp.edit().putFloat("g_blur", g.blur).putFloat("g_lens_h", g.lensHeight).putFloat("g_lens_a", g.lensAmount)
            .putBoolean("g_depth", g.depth).putBoolean("g_chroma", g.chromatic).putFloat("g_sat", g.saturation)
            .putFloat("g_tint", g.tint).putFloat("g_edge_w", g.edgeWidth).putFloat("g_edge_a", g.edgeAlpha)
            .putInt("g_edge_style", g.edgeStyle).putFloat("g_sh_r", g.shadowRadius).putFloat("g_sh_a", g.shadowAlpha)
            .putFloat("g_in_r", g.innerRadius).putFloat("g_in_a", g.innerAlpha)
            .putInt("g_type", g.type).putFloat("g_radius_pct", g.radius).putInt("g_sel", g.selection).putFloat("g_sel_radius", g.selRadius).apply()
        glass = g
    }

    fun updateFloatingNav(b: Boolean) { sp.edit().putBoolean("floating_nav", b).apply(); floatingNav = b }
    fun updateTheme(m: Int) { sp.edit().putInt("theme", m).apply(); themeMode = m }
    fun updateAmoled(b: Boolean) { sp.edit().putBoolean("amoled", b).apply(); amoled = b }
}
