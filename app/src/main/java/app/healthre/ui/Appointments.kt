package app.healthre.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.healthre.Alarms
import app.healthre.AppEnv
import app.healthre.data.Appointment
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale

internal val kbPhone = KeyboardOptions(keyboardType = KeyboardType.Phone)

/** "Today", "Tomorrow", "In 5 days", "3 days ago". */
internal fun whenLabel(epochDay: Long): String {
    val d = epochDay - LocalDate.now().toEpochDay()
    return when {
        d == 0L -> "Today"
        d == 1L -> "Tomorrow"
        d > 1L -> "In $d days"
        d == -1L -> "Yesterday"
        else -> "${-d} days ago"
    }
}

internal fun dateText(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).let {
    "${it.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())}, ${it.dayOfMonth} ${it.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${it.year}"
}

private fun launch(ctx: Context, intent: Intent) {
    try { ctx.startActivity(intent) } catch (e: Exception) { toast(ctx, "No app found to do that") }
}

/** Small calendar-style badge: day number over the month. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DateBadge(epochDay: Long, size: Int = 60) {
    val c = MaterialTheme.colorScheme
    val d = LocalDate.ofEpochDay(epochDay)
    Box(Modifier.size(size.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(c.primaryContainer), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${d.dayOfMonth}", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black), color = c.onPrimaryContainer)
            Text(d.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()), style = MaterialTheme.typography.labelSmall, color = c.onPrimaryContainer)
        }
    }
}

@Composable
private fun AppointmentCard(a: Appointment, onEdit: () -> Unit, onDelete: () -> Unit) {
    val ctx = LocalContext.current
    val c = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxWidth(), shape = corner(28), color = c.surfaceContainerLow) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                DateBadge(a.epochDay)
                Column(Modifier.weight(1f)) {
                    Text(a.title, style = MaterialTheme.typography.titleLarge)
                    if (a.doctor.isNotBlank()) Text(a.doctor, color = c.onSurfaceVariant)
                    Text("%02d:%02d · %s".format(a.minuteOfDay / 60, a.minuteOfDay % 60, whenLabel(a.epochDay)), style = MaterialTheme.typography.labelLarge, color = c.primary)
                }
            }
            if (a.place.isNotBlank()) Text(a.place)
            if (a.notes.isNotBlank()) Text(a.notes, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (a.place.isNotBlank()) IconButton({ launch(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(a.place)))) }) { Icon(Icons.Rounded.Place, "Open in maps") }
                if (a.phone.isNotBlank()) IconButton({ launch(ctx, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + a.phone))) }) { Icon(Icons.Rounded.Call, "Call") }
                IconButton({
                    val start = Alarms.appointmentStart(a).toInstant().toEpochMilli()
                    launch(
                        ctx,
                        Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
                            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start).putExtra(CalendarContract.EXTRA_EVENT_END_TIME, start + 30 * 60_000L)
                            .putExtra(CalendarContract.Events.TITLE, a.title + a.doctor.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty())
                            .putExtra(CalendarContract.Events.EVENT_LOCATION, a.place).putExtra(CalendarContract.Events.DESCRIPTION, a.notes),
                    )
                }) { Icon(Icons.Rounded.EventAvailable, "Add to calendar") }
                Spacer(Modifier.weight(1f))
                IconButton(onEdit) { Icon(Icons.Rounded.Edit, "Edit") }
                IconButton(onDelete) { Icon(Icons.Rounded.Delete, "Delete") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun AppointmentDialog(app: AppEnv, initial: Appointment?, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val dao = app.db.dao()
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var doctor by remember { mutableStateOf(initial?.doctor ?: "") }
    var place by remember { mutableStateOf(initial?.place ?: "") }
    var phone by remember { mutableStateOf(initial?.phone ?: "") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var day by remember { mutableLongStateOf(initial?.epochDay ?: LocalDate.now().plusDays(1).toEpochDay()) }
    var minute by remember { mutableIntStateOf(initial?.minuteOfDay ?: 9 * 60) }
    var dayBefore by remember { mutableStateOf(initial?.remindDayBefore ?: true) }
    var hourBefore by remember { mutableStateOf(initial?.remindHourBefore ?: true) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    val dateState = rememberDatePickerState(initialSelectedDateMillis = LocalDate.ofEpochDay(day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    val timeState = rememberTimePickerState(minute / 60, minute % 60, is24Hour = true)

    if (pickDate) DatePickerDialog(
        onDismissRequest = { pickDate = false },
        confirmButton = {
            TextButton({
                dateState.selectedDateMillis?.let { day = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay() }
                pickDate = false
            }) { Text("OK") }
        },
        dismissButton = { TextButton({ pickDate = false }) { Text("Cancel") } },
    ) { DatePicker(dateState) }

    if (pickTime) AlertDialog(
        onDismissRequest = { pickTime = false },
        title = { Text("Time") },
        text = { TimePicker(timeState) },
        confirmButton = { TextButton({ minute = timeState.hour * 60 + timeState.minute; pickTime = false }) { Text("OK") } },
        dismissButton = { TextButton({ pickTime = false }) { Text("Cancel") } },
    )

    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(if (initial == null) "New appointment" else "Edit appointment") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("What for (check-up, blood test…)") }, singleLine = true)
                AppField(doctor, { doctor = it }, Modifier.fillMaxWidth(), label = { Text("Doctor") }, singleLine = true)
                AppField(place, { place = it }, Modifier.fillMaxWidth(), label = { Text("Clinic or hospital, address") })
                AppField(phone, { phone = it }, Modifier.fillMaxWidth(), label = { Text("Phone") }, singleLine = true, keyboardOptions = kbPhone)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ pickDate = true }, Modifier.weight(1f)) { Icon(Icons.Rounded.CalendarMonth, null); Spacer(Modifier.width(6.dp)); Text(dateText(day)) }
                    OutlinedButton({ pickTime = true }) { Icon(Icons.Rounded.Schedule, null); Spacer(Modifier.width(6.dp)); Text("%02d:%02d".format(minute / 60, minute % 60)) }
                }
                AppField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text("Notes (bring reports, fasting…)") })
                Text("Remind me", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(dayBefore, { dayBefore = !dayBefore }, { Text("1 day before") })
                    FilterChip(hourBefore, { hourBefore = !hourBefore }, { Text("1 hour before") })
                }
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank(), onClick = {
                scope.launch {
                    val a = Appointment(initial?.id ?: 0, title.trim(), doctor.trim(), place.trim(), phone.trim(), day, minute, notes.trim(), dayBefore, hourBefore)
                    val id = if (initial == null) dao.insert(a) else { dao.update(a); a.id }
                    if (!app.demo) Alarms.scheduleAppointment(ctx, a.copy(id = id))
                    onDone()
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onDone) { Text("Cancel") } },
    )
}

/** All appointments: upcoming first, past ones in a collapsed card. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppointmentsScreen(app: AppEnv, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val dao = app.db.dao()
    val scope = rememberCoroutineScope()
    val list by dao.appointments().collectAsState(emptyList())
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Appointment?>(null) }
    var deleting by remember { mutableStateOf<Appointment?>(null) }
    val today = LocalDate.now().toEpochDay()
    val upcoming = list.filter { it.epochDay >= today }
    val past = list.filter { it.epochDay < today }.reversed()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = LocalListBottom.current - 56.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onBack) { Icon(Icons.Rounded.ArrowBack, "Back") }
            Spacer(Modifier.width(12.dp))
            Text("Appointments", style = MaterialTheme.typography.displaySmall)
        }
        Button({ adding = true }, shapes = ButtonDefaults.shapes()) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp)); Text("New appointment") }

        if (upcoming.isEmpty()) Text("No upcoming appointments.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        upcoming.forEach { a -> AppointmentCard(a, { editing = a }, { deleting = a }) }
        if (past.isNotEmpty()) SectionCard("Past", Icons.Rounded.History, collapsible = true) {
            past.forEach { a -> AppointmentCard(a, { editing = a }, { deleting = a }) }
        }
    }

    if (adding) AppointmentDialog(app, null) { adding = false }
    editing?.let { AppointmentDialog(app, it) { editing = null } }
    deleting?.let { a ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete appointment?") },
            text = { Text(a.title) },
            confirmButton = {
                TextButton({
                    scope.launch {
                        if (!app.demo) Alarms.cancelAppointment(ctx, a.id)
                        dao.delete(a); deleting = null
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton({ deleting = null }) { Text("Cancel") } },
        )
    }
}
