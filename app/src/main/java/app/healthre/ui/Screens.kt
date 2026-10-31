package app.healthre.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import app.healthre.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.healthre.Alarms
import app.healthre.AppEnv
import app.healthre.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime

internal fun fmt(min: Int) = "%02d:%02d".format(min / 60, min % 60)
private fun bit(d: LocalDate) = 1 shl (d.dayOfWeek.value - 1)
internal fun toast(c: Context, s: String) = Toast.makeText(c, s, Toast.LENGTH_LONG).show()
internal val kbNum = KeyboardOptions(keyboardType = KeyboardType.Number)
internal val kbDec = KeyboardOptions(keyboardType = KeyboardType.Decimal)

@Composable
fun Root(app: AppEnv) {
    if (!app.profile.value.onboarded) {
        Onboarding(app)
        return
    }
    MainScaffold(app)
}

/** The whole signed-in app. The demo reuses it unchanged, with [header] shown above the tabs. */
@Composable
fun MainScaffold(app: AppEnv, header: @Composable ColumnScope.() -> Unit = {}) {
    var tab by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Medicine?>(null) }
    var adding by remember { mutableStateOf(false) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                listOf("Today" to Icons.Rounded.Today, "Medicines" to ImageVector.vectorResource(R.drawable.ic_pill), "History" to Icons.Rounded.Insights, "Profile" to Icons.Rounded.Person)
                    .forEachIndexed { i, (l, ic) -> NavigationBarItem(tab == i, { tab = i }, { Icon(ic, null) }, label = { Text(l) }) }
            }
        },
        floatingActionButton = {
            if (tab <= 1) FloatingActionButton({ adding = true }) { Icon(Icons.Rounded.Add, "Add medicine") }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            header()
            Box(Modifier.weight(1f)) {
                when (tab) {
                    0 -> Today(app)
                    1 -> Medicines(app) { editing = it }
                    2 -> History(app)
                    else -> ProfileScreen(app)
                }
            }
        }
    }
    if (adding) MedicineDialog(app, null) { adding = false }
    editing?.let { MedicineDialog(app, it) { editing = null } }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun Today(app: AppEnv) {
    val ctx = LocalContext.current
    val dao = app.db.dao()
    val day = remember { LocalDate.now() }
    val today = day.toEpochDay()
    val nowMin = remember { LocalTime.now().let { it.hour * 60 + it.minute } }
    val data by remember { combine(dao.schedules(), dao.logs(today)) { s, l -> s.filter { it.schedule.daysMask and bit(day) != 0 } to l.associateBy { it.scheduleId } } }
        .collectAsState(emptyList<ScheduleWithMedicine>() to emptyMap())
    val (list, logs) = data
    val scope = rememberCoroutineScope()
    val done = list.count { logs[it.schedule.id]?.status == DoseStatus.TAKEN }
    val name = app.profile.value.name.substringBefore(' ')

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (name.isBlank()) "Today" else "Hi, $name", Modifier.weight(1f), style = MaterialTheme.typography.displaySmall)
                Avatar(app, 52.dp)
            }
            Spacer(Modifier.height(8.dp))
            LinearWavyProgressIndicator(progress = { if (list.isEmpty()) 0f else done / list.size.toFloat() }, modifier = Modifier.fillMaxWidth())
            Text("$done of ${list.size} doses taken", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelLarge)
        }
        if (list.isEmpty()) item { Text("Nothing scheduled today. Add a medicine with +.") }
        items(list, key = { it.schedule.id }) { sm ->
            val log = logs[sm.schedule.id]
            val overdue = log == null && sm.schedule.minuteOfDay < nowMin
            ElevatedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(sm.medicine.name, style = MaterialTheme.typography.titleLarge)
                        Text("${fmt(sm.schedule.minuteOfDay)} · ${sm.medicine.unitDose} ${sm.medicine.strength}".trim())
                        when {
                            log != null -> Text(log.status.name.lowercase(), color = MaterialTheme.colorScheme.primary)
                            overdue -> Text("overdue", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    if (log == null) {
                        FilledTonalIconButton({ scope.launch { Alarms.skipDose(dao, sm.schedule.id, today) } }) { Icon(Icons.Rounded.Close, "Skip") }
                        Spacer(Modifier.width(8.dp))
                        FilledIconButton({ scope.launch { Alarms.takeDose(ctx, dao, sm.schedule.id, today, app.demo) } }) { Icon(Icons.Rounded.Check, "Taken") }
                    } else TextButton({ scope.launch { Alarms.undoDose(dao, sm.schedule.id, today) } }) { Text("Undo") }
                }
            }
        }
    }
}

@Composable
internal fun Medicines(app: AppEnv, onEdit: (Medicine) -> Unit) {
    val ctx = LocalContext.current
    val dao = app.db.dao()
    val meds by dao.medicines().collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    var deleting by remember { mutableStateOf<Medicine?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Medicines", style = MaterialTheme.typography.displaySmall) }
        if (meds.isEmpty()) item { Text("No medicines yet.") }
        items(meds, key = { it.id }) { m ->
            val low = m.refillThreshold > 0 && m.stock <= m.refillThreshold
            Card(
                { onEdit(m) }, Modifier.fillMaxWidth(),
                colors = if (low) CardDefaults.cardColors(MaterialTheme.colorScheme.errorContainer) else CardDefaults.cardColors(),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(m.name, style = MaterialTheme.typography.titleLarge)
                        Text("${m.strength} ${m.form}".trim())
                        Text(if (low) "Refill soon · ${m.stock} left" else "${m.stock} left")
                        if (m.expiry.isNotBlank()) Text("Expires ${m.expiry}", style = MaterialTheme.typography.labelMedium)
                    }
                    IconButton({ deleting = m }) { Icon(Icons.Rounded.Delete, "Delete") }
                }
            }
        }
    }
    deleting?.let { m ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${m.name}?") },
            text = { Text("Its schedules and dose history are deleted too.") },
            confirmButton = {
                TextButton({
                    scope.launch {
                        if (!app.demo) dao.schedulesOf(m.id).forEach { Alarms.cancel(ctx, it.id) }
                        dao.delete(m); deleting = null
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton({ deleting = null }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun MedicineDialog(app: AppEnv, initial: Medicine?, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val dao = app.db.dao()
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var strength by remember { mutableStateOf(initial?.strength ?: "") }
    var form by remember { mutableStateOf(initial?.form ?: "") }
    var unit by remember { mutableStateOf(initial?.unitDose ?: "1") }
    var stock by remember { mutableStateOf(initial?.stock?.toString() ?: "") }
    var threshold by remember { mutableStateOf(initial?.refillThreshold?.toString() ?: "") }
    var expiry by remember { mutableStateOf(initial?.expiry ?: "") }
    var mfr by remember { mutableStateOf(initial?.manufacturer ?: "") }
    var batch by remember { mutableStateOf(initial?.batch ?: "") }
    val times: SnapshotStateList<Int> = remember { mutableStateListOf<Int>().also { if (initial == null) it.add(8 * 60) } }
    var mask by remember { mutableIntStateOf(0b1111111) }
    val picker = rememberTimePickerState(8, 0, is24Hour = true)
    var pickTime by remember { mutableStateOf(false) }

    LaunchedEffect(initial) {
        if (initial != null) {
            val ss = dao.schedulesOf(initial.id)
            times.clear(); times.addAll(ss.map { it.minuteOfDay }.sorted())
            mask = ss.firstOrNull()?.daysMask ?: 0b1111111
        }
    }

    if (pickTime) AlertDialog(
        onDismissRequest = { pickTime = false },
        title = { Text("Add time") },
        text = { TimePicker(picker) },
        confirmButton = {
            TextButton({ val t = picker.hour * 60 + picker.minute; if (t !in times) times.add(t); pickTime = false }) { Text("Add") }
        },
        dismissButton = { TextButton({ pickTime = false }) { Text("Cancel") } },
    )

    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(if (initial == null) "Add medicine" else "Edit medicine") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppField(strength, { strength = it }, Modifier.weight(1f), label = { Text("Strength") }, singleLine = true)
                    AppField(form, { form = it }, Modifier.weight(1f), label = { Text("Form") }, singleLine = true)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppField(unit, { unit = it }, Modifier.weight(1f), label = { Text("Dose") }, singleLine = true, keyboardOptions = kbNum)
                    AppField(stock, { stock = it }, Modifier.weight(1f), label = { Text("Stock") }, singleLine = true, keyboardOptions = kbNum)
                    AppField(threshold, { threshold = it }, Modifier.weight(1f), label = { Text("Refill at") }, singleLine = true, keyboardOptions = kbNum)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppField(expiry, { expiry = it }, Modifier.weight(1f), label = { Text("Expiry") }, singleLine = true)
                    AppField(batch, { batch = it }, Modifier.weight(1f), label = { Text("Batch") }, singleLine = true)
                }
                AppField(mfr, { mfr = it }, label = { Text("Manufacturer") }, singleLine = true)

                Text("Days", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEachIndexed { i, d ->
                        FilterChip((mask and (1 shl i)) != 0, { mask = mask xor (1 shl i) }, { Text(d) })
                    }
                }
                Text("Times", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    times.sorted().forEach { t ->
                        InputChip(false, { times.remove(t) }, { Text(fmt(t)) }, trailingIcon = { Icon(Icons.Rounded.Close, "Remove", Modifier.size(18.dp)) })
                    }
                }
                OutlinedButton({ pickTime = true }) { Text("Add time") }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && mask != 0, onClick = {
                scope.launch {
                    val m = Medicine(
                        initial?.id ?: 0, name.trim(), strength.trim(), form.trim(), unit.trim().ifBlank { "1" },
                        stock.toIntOrNull() ?: 0, threshold.toIntOrNull() ?: 0, mfr.trim(), expiry.trim(), batch.trim(), initial?.notes ?: "",
                    )
                    val id = if (initial == null) dao.insert(m) else { dao.update(m); initial.id }
                    // Keep existing schedule rows (and their dose history) for unchanged times.
                    val old = dao.schedulesOf(id)
                    old.filter { it.minuteOfDay !in times }.forEach { if (!app.demo) Alarms.cancel(ctx, it.id); dao.delete(it) }
                    old.filter { it.minuteOfDay in times }.forEach { dao.update(it.copy(daysMask = mask)) }
                    times.distinct().filter { t -> old.none { it.minuteOfDay == t } }.forEach { dao.insert(Schedule(medicineId = id, minuteOfDay = it, daysMask = mask)) }
                    if (!app.demo) Alarms.rescheduleAll(ctx, dao)
                    onDone()
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onDone) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun History(app: AppEnv) {
    val dao = app.db.dao()
    val days = 14
    val today = remember { LocalDate.now() }
    val rows by remember {
        combine(dao.schedules(), dao.logsBetween(today.toEpochDay() - days + 1, today.toEpochDay())) { s, l ->
            (0 until days).map { i ->
                val d = today.minusDays(i.toLong())
                val ids = s.filter { it.schedule.daysMask and bit(d) != 0 }.map { it.schedule.id }.toSet()
                val logs = l.filter { it.epochDay == d.toEpochDay() && it.scheduleId in ids }
                Triple(d, logs.count { it.status == DoseStatus.TAKEN }, ids.size)
            }
        }
    }.collectAsState(emptyList())

    val taken = rows.sumOf { it.second }
    val total = rows.sumOf { it.third }
    // Streak: consecutive fully-taken days, counted back from the latest finished day (today counts only once complete).
    val skipToday = rows.firstOrNull()?.let { it.third > 0 && it.second < it.third } == true
    val streak = rows.drop(if (skipToday) 1 else 0).takeWhile { it.third == 0 || it.second == it.third }.count { it.third > 0 }

    val card = MaterialTheme.colorScheme.surfaceContainerLow
    val cardShape = RoundedCornerShape(28.dp)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("History", style = MaterialTheme.typography.displaySmall) }
        item {
            Surface(Modifier.fillMaxWidth(), shape = cardShape, color = card) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularWavyProgressIndicator(progress = { if (total == 0) 0f else taken / total.toFloat() }, modifier = Modifier.size(104.dp))
                        Text(if (total == 0) "–" else "${taken * 100 / total}%", style = MaterialTheme.typography.headlineSmall)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Adherence", style = MaterialTheme.typography.titleLarge)
                        Text("Last $days days", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        Text("$taken of $total doses taken", style = MaterialTheme.typography.bodyLarge)
                        Text("Streak: $streak day${if (streak == 1) "" else "s"}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        item {
            Surface(Modifier.fillMaxWidth(), shape = cardShape, color = card) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Day by day", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Row(Modifier.fillMaxWidth().height(110.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        rows.reversed().forEach { (d, t, n) ->
                            val frac = if (n == 0) 0.06f else (t / n.toFloat()).coerceAtLeast(0.06f)
                            val color = when {
                                n == 0 -> MaterialTheme.colorScheme.outlineVariant
                                t == n -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.tertiary
                            }
                            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                                    Box(Modifier.fillMaxWidth().fillMaxHeight(frac).clip(RoundedCornerShape(6.dp)).background(color))
                                }
                                Text(d.dayOfWeek.name.first().toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        item {
            Surface(Modifier.fillMaxWidth(), shape = cardShape, color = card) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    rows.forEach { (d, t, n) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (d == today) "Today" else "${d.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }}, ${d.dayOfMonth}/${d.monthValue}",
                                Modifier.width(96.dp), style = MaterialTheme.typography.bodyLarge,
                            )
                            LinearProgressIndicator(progress = { if (n == 0) 0f else t / n.toFloat() }, Modifier.weight(1f).height(8.dp).clip(CircleShape))
                            Text(
                                if (n == 0) "–" else "$t/$n", Modifier.width(52.dp), textAlign = TextAlign.End,
                                color = if (n > 0 && t == n) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}
