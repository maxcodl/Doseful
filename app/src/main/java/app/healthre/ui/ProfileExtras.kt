package app.healthre.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.healthre.AppEnv
import app.healthre.data.*
import kotlinx.coroutines.flow.combine
import java.time.LocalDate

private data class Stats(val adherence: Int? = null, val streak: Int = 0, val month: Int = 0)

private fun computeStats(sched: List<ScheduleWithMedicine>, logs: List<DoseLog>, today: LocalDate): Stats {
    fun bit(d: LocalDate) = 1 shl (d.dayOfWeek.value - 1)
    val rows = (0 until 14).map { i ->
        val d = today.minusDays(i.toLong())
        val ids = sched.filter { it.schedule.daysMask and bit(d) != 0 }.map { it.schedule.id }.toSet()
        logs.count { it.epochDay == d.toEpochDay() && it.scheduleId in ids && it.status == DoseStatus.TAKEN } to ids.size
    }
    val total = rows.sumOf { it.second }
    val taken = rows.sumOf { it.first }
    val skipToday = rows.first().let { it.second > 0 && it.first < it.second }
    val streak = rows.drop(if (skipToday) 1 else 0).takeWhile { it.second == 0 || it.first == it.second }.count { it.second > 0 }
    val monthStart = today.withDayOfMonth(1).toEpochDay()
    return Stats(if (total == 0) null else taken * 100 / total, streak, logs.count { it.status == DoseStatus.TAKEN && it.epochDay >= monthStart })
}

@Composable
private fun MiniStat(label: String, value: String, icon: ImageVector, shape: Shape, bg: Color, fg: Color, modifier: Modifier) {
    Surface(modifier, shape = corner(24), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(vertical = 16.dp, horizontal = 8.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.size(40.dp).clip(shape).background(bg), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(22.dp), tint = fg) }
            Text(value, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black), color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

/** Adherence over 14 days, current streak and doses taken this month. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun StatsRow(app: AppEnv) {
    val dao = app.db.dao()
    val today = remember { LocalDate.now() }
    val from = minOf(today.withDayOfMonth(1).toEpochDay(), today.toEpochDay() - 13)
    val stats by remember { combine(dao.schedules(), dao.logsBetween(from, today.toEpochDay())) { s, l -> computeStats(s, l, today) } }.collectAsState(Stats())
    val c = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MiniStat("Adherence", stats.adherence?.let { "$it%" } ?: "–", Icons.Rounded.Insights, MaterialShapes.Cookie6Sided.toShape(), c.primaryContainer, c.onPrimaryContainer, Modifier.weight(1f))
        MiniStat("Streak", "${stats.streak}d", Icons.Rounded.LocalFireDepartment, MaterialShapes.Flower.toShape(), c.tertiaryContainer, c.onTertiaryContainer, Modifier.weight(1f))
        MiniStat("This month", "${stats.month}", Icons.Rounded.CheckCircle, MaterialShapes.Clover4Leaf.toShape(), c.secondaryContainer, c.onSecondaryContainer, Modifier.weight(1f))
    }
}

private val bloodTypes = listOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MedicalForm(m: MedicalId, onSave: (MedicalId) -> Unit, onCancel: () -> Unit) {
    var blood by remember { mutableStateOf(m.blood) }
    var allergies by remember { mutableStateOf(m.allergies) }
    var conditions by remember { mutableStateOf(m.conditions) }
    var doctor by remember { mutableStateOf(m.doctor) }
    var doctorPhone by remember { mutableStateOf(m.doctorPhone) }
    var emName by remember { mutableStateOf(m.emergencyName) }
    var emPhone by remember { mutableStateOf(m.emergencyPhone) }
    Text("Blood type", style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        bloodTypes.forEach { t -> FilterChip(blood == t, { blood = if (blood == t) "" else t }, { Text(t) }) }
    }
    AppField(allergies, { allergies = it }, Modifier.fillMaxWidth(), label = { Text("Allergies") })
    AppField(conditions, { conditions = it }, Modifier.fillMaxWidth(), label = { Text("Conditions") })
    AppField(doctor, { doctor = it }, Modifier.fillMaxWidth(), label = { Text("Doctor") }, singleLine = true)
    AppField(doctorPhone, { doctorPhone = it }, Modifier.fillMaxWidth(), label = { Text("Doctor's phone") }, singleLine = true, keyboardOptions = kbPhone)
    AppField(emName, { emName = it }, Modifier.fillMaxWidth(), label = { Text("Emergency contact") }, singleLine = true)
    AppField(emPhone, { emPhone = it }, Modifier.fillMaxWidth(), label = { Text("Emergency phone") }, singleLine = true, keyboardOptions = kbPhone)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button({ onSave(MedicalId(blood, allergies.trim(), conditions.trim(), doctor.trim(), doctorPhone.trim(), emName.trim(), emPhone.trim())) }) { Text("Save") }
        TextButton(onCancel) { Text("Cancel") }
    }
}

/** One labelled line on the ID card, with an icon. Skipped when empty. */
@Composable
private fun CardLine(icon: ImageVector, label: String, value: String, big: Boolean) {
    if (value.isBlank()) return
    val fg = MaterialTheme.colorScheme.onPrimaryContainer
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, Modifier.padding(top = 2.dp), tint = fg)
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = fg.copy(alpha = 0.75f))
            Text(value, style = if (big) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge, color = fg)
        }
    }
}

@Composable
private fun CardCall(label: String, name: String, phone: String, big: Boolean) {
    if (name.isBlank() && phone.isBlank()) return
    val ctx = LocalContext.current
    val c = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = c.onPrimaryContainer.copy(alpha = 0.75f))
            Text(name.ifBlank { phone }, style = if (big) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge, color = c.onPrimaryContainer)
            if (name.isNotBlank() && phone.isNotBlank()) Text(phone, style = MaterialTheme.typography.bodyMedium, color = c.onPrimaryContainer.copy(alpha = 0.85f))
        }
        if (phone.isNotBlank()) FilledIconButton(
            { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))) },
            Modifier.size(if (big) 56.dp else 44.dp),
        ) { Icon(Icons.Rounded.Call, "Call $label") }
    }
}

/** The Medical ID as an ID card: photo, name, blood type badge, allergies, conditions and who to call. [big] is the emergency view. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MedicalCard(app: AppEnv, m: MedicalId, big: Boolean = false) {
    val c = MaterialTheme.colorScheme
    val p = app.profile.value
    val photo = if (big) 84.dp else 64.dp
    Surface(Modifier.fillMaxWidth(), shape = corner(28), color = c.primaryContainer) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.MedicalServices, null, tint = c.onPrimaryContainer)
                Text("MEDICAL ID", style = MaterialTheme.typography.labelLarge.copy(letterSpacing = androidx.compose.ui.unit.TextUnit(2f, androidx.compose.ui.unit.TextUnitType.Sp)), color = c.onPrimaryContainer)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Avatar(app, photo)
                Column(Modifier.weight(1f)) {
                    Text(
                        p.name.ifBlank { "Your name" },
                        style = (if (big) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.titleLarge).copy(fontWeight = FontWeight.Black),
                        color = c.onPrimaryContainer,
                    )
                    val facts = listOfNotNull(p.age?.let { "$it yrs" }, p.gender.ifBlank { null }, p.bmi?.let { "BMI %.1f".format(it) }).joinToString("  ·  ")
                    if (facts.isNotEmpty()) Text(facts, style = MaterialTheme.typography.bodyMedium, color = c.onPrimaryContainer.copy(alpha = 0.85f))
                }
                if (m.blood.isNotBlank()) Box(Modifier.size(photo).clip(MaterialShapes.Cookie9Sided.toShape()).background(c.primary), contentAlignment = Alignment.Center) {
                    Text(m.blood, style = (if (big) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge).copy(fontWeight = FontWeight.Black), color = c.onPrimary)
                }
            }
            HorizontalDivider(color = c.onPrimaryContainer.copy(alpha = 0.2f))
            CardLine(Icons.Rounded.Warning, "Allergies", m.allergies, big)
            CardLine(Icons.Rounded.HealthAndSafety, "Conditions", m.conditions, big)
            CardCall("Doctor", m.doctor, m.doctorPhone, big)
            CardCall("Emergency contact", m.emergencyName, m.emergencyPhone, big)
        }
    }
}

/** Large, high-contrast view of the card, meant to be handed to or read by someone helping you. */
@Composable
private fun EmergencyView(app: AppEnv, m: MedicalId, onClose: () -> Unit) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.statusBarsPadding().navigationBarsPadding().padding(16.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                MedicalCard(app, m, big = true)
                Button(onClose, Modifier.fillMaxWidth()) { Text("Close") }
            }
        }
    }
}

/** Your Medical ID card. Stays on the phone. */
@Composable
internal fun MedicalIdCard(app: AppEnv) {
    val m = app.profile.medical
    var editing by remember { mutableStateOf(false) }
    var full by remember { mutableStateOf(false) }
    SectionCard("Medical ID", Icons.Rounded.MedicalServices, collapsible = true, startOpen = true) {
        if (editing) MedicalForm(m, { app.profile.saveMedical(it); editing = false }) { editing = false }
        else {
            if (m.isEmpty) {
                Text("Add your blood type, allergies and an emergency contact to get your ID card.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                FilledTonalButton({ editing = true }) { Text("Add") }
            } else {
                MedicalCard(app, m)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton({ full = true }) { Icon(Icons.Rounded.Fullscreen, null); Spacer(Modifier.width(6.dp)); Text("Full screen") }
                    OutlinedButton({ editing = true }) { Text("Edit") }
                }
            }
        }
    }
    if (full) EmergencyView(app, m) { full = false }
}

/** The next appointment, with shortcuts to the full list. */
@Composable
internal fun AppointmentsSummary(app: AppEnv, onOpen: () -> Unit) {
    val list by app.db.dao().appointments().collectAsState(emptyList())
    val today = LocalDate.now().toEpochDay()
    val next = list.firstOrNull { it.epochDay >= today }
    SectionCard("Appointments", Icons.Rounded.Event, collapsible = true, startOpen = true) {
        if (next == null) Text("No upcoming appointments.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            DateBadge(next.epochDay, 52)
            Column {
                Text(next.title, style = MaterialTheme.typography.titleMedium)
                if (next.doctor.isNotBlank()) Text(next.doctor, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("%02d:%02d · %s".format(next.minuteOfDay / 60, next.minuteOfDay % 60, whenLabel(next.epochDay)), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
        FilledTonalButton(onOpen) { Text(if (next == null) "Add an appointment" else "All appointments") }
    }
}

/** The latest reading of each thing you track. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HealthSummary(app: AppEnv, onOpen: () -> Unit) {
    val all by app.db.dao().measurements().collectAsState(emptyList())
    val latest = Vital.entries.mapNotNull { v -> all.lastOrNull { it.type == v.key }?.let { v to it } }
    SectionCard("Health log", Icons.Rounded.MonitorHeart, collapsible = true, startOpen = true) {
        if (latest.isEmpty()) Text("Track weight, blood pressure, blood sugar, heart rate or temperature.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        else FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            latest.forEach { (v, m) -> AssistChip({ onOpen() }, { Text("${v.label} ${m.shown()} ${v.unit}") }) }
        }
        FilledTonalButton(onOpen) { Text(if (latest.isEmpty()) "Start a log" else "Open log") }
    }
}
