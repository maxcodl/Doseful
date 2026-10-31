package app.healthre.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.room.Room
import app.healthre.AppEnv
import app.healthre.data.*
import java.time.LocalDate
import java.time.LocalTime

/** Throwaway data for demo mode: its own in-memory database and profile, wiped on exit. Theme is shared with the real app. */
private class DemoEnv(override val ctx: Context, real: AppEnv) : AppEnv {
    override val demo = true
    override val theme = real.theme
    override val db: Db = Room.inMemoryDatabaseBuilder(ctx, Db::class.java).build()
    override val profile = ProfileStore(ctx, "demo_profile", "demo_avatar.jpg").also {
        it.save(Profile(real.profile.value.name.ifBlank { "Max" }, "", 34, "", 172f, 68f, true, true))
        it.saveMedical(MedicalId("O+", "Penicillin", "Mild asthma", "Dr. Sharma", "+91 90000 00001", "Sam", "+91 90000 00002"))
    }

    fun close() { db.close(); profile.wipe() }
}

/**
 * The real app, running on fake data. [onExit] is called by the header button and by Back.
 * Nothing here touches your data, and no alarms or notifications are scheduled.
 */
@Composable
fun DemoMode(real: AppEnv, onExit: () -> Unit) {
    val ctx = LocalContext.current
    val env = remember { DemoEnv(ctx.applicationContext, real) }
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { seed(env.db.dao()); ready = true }
    DisposableEffect(Unit) { onDispose { env.close() } }
    BackHandler(onBack = onExit)

    if (ready) MainScaffold(env, header = {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Demo mode", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.tertiary)
                Text("Sample data. Your real data is untouched.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilledTonalButton(onExit, Modifier.height(36.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
                Text("Exit demo", style = MaterialTheme.typography.labelMedium)
            }
        }
    })
}

private class DemoMed(val name: String, val strength: String, val form: String, val stock: Int, val refillAt: Int, val times: List<Int>)

private suspend fun seed(d: AppDao) {
    val meds = listOf(
        DemoMed("Metformin", "500 mg", "Tablet", 64, 14, listOf(8 * 60, 20 * 60)),
        DemoMed("Lisinopril", "10 mg", "Tablet", 41, 10, listOf(8 * 60 + 30)),
        DemoMed("Vitamin D3", "2000 IU", "Capsule", 90, 15, listOf(9 * 60)),
        DemoMed("Atorvastatin", "20 mg", "Tablet", 12, 14, listOf(21 * 60)), // low stock, to show the refill state
    )
    val today = LocalDate.now().toEpochDay()
    val nowMin = LocalTime.now().let { it.hour * 60 + it.minute }
    d.insert(Appointment(title = "Check-up", doctor = "Dr. Sharma", place = "City Clinic, 12 Park Road", phone = "+91 90000 00001", epochDay = today + 3, minuteOfDay = 10 * 60 + 30, notes = "Bring last blood test report"))
    for (i in 0 until 8) d.insert(Measurement(type = "weight", value = 69.2f - i * 0.3f, recordedAt = System.currentTimeMillis() - (7 - i) * 86_400_000L))
    for (i in 0 until 6) d.insert(Measurement(type = "bp", value = 118f + (i % 3) * 3, value2 = 78f + (i % 2) * 2, recordedAt = System.currentTimeMillis() - (5 - i) * 86_400_000L))
    var k = 0
    for (m in meds) {
        val mid = d.insert(Medicine(name = m.name, strength = m.strength, form = m.form, stock = m.stock, refillThreshold = m.refillAt, expiry = "08/2027", manufacturer = "Sample Pharma"))
        for (t in m.times) {
            val sid = d.insert(Schedule(medicineId = mid, minuteOfDay = t))
            for (off in 1L..13L) {
                if ((off * 3 + k) % 11 == 0L) continue // a missed dose, so the history is not all perfect
                val status = if ((off * 5 + k * 3) % 7 == 0L) DoseStatus.SKIPPED else DoseStatus.TAKEN
                d.upsert(DoseLog(scheduleId = sid, epochDay = today - off, status = status))
            }
            if (t + 30 < nowMin) d.upsert(DoseLog(scheduleId = sid, epochDay = today, status = DoseStatus.TAKEN))
            k++
        }
    }
}
