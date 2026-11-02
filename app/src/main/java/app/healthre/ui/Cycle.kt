package app.healthre.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.healthre.AppEnv
import app.healthre.data.*
import app.healthre.partnerMode
import app.healthre.partnerName
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private fun dayText(d: Long) = LocalDate.ofEpochDay(d).let { "${it.dayOfMonth} ${it.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())}" }

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CycleScreen(app: AppEnv) {
    val dao = app.db.dao()
    val cycles by remember { dao.cycles() }.collectAsState(emptyList())
    val days by remember { dao.periodDays() }.collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    var today by remember { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    // The tab stays composed, so refresh the date when the app comes back after midnight.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { today = LocalDate.now().toEpochDay() }
    val info = remember(cycles, today) { CycleMath.info(cycles, today) }
    val warnings = remember(cycles, days, today) { CycleMath.warnings(cycles, days, today) }
    val last = cycles.maxByOrNull { it.startDay }
    val ongoing = CycleMath.ongoing(cycles, today)
    val endedToday = last != null && last.endDay != null && today in last.startDay..last.endDay
    var editDay by remember { mutableStateOf<Long?>(null) }
    var confirmRemove by remember { mutableStateOf<PeriodCycle?>(null) }
    val c = MaterialTheme.colorScheme
    val partner = app.partnerMode
    val her = app.partnerName
    val herCap = if (her == "her") "Her" else her

    fun save(block: suspend () -> Unit) { scope.launch { block() } }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).padding(bottom = LocalListBottom.current - 56.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Cycle", style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Black), modifier = Modifier.padding(start = 4.dp, top = 28.dp))

        // Hero
        Surface(Modifier.fillMaxWidth().entrance(1), shape = corner(32), color = c.primaryContainer) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (info == null) {
                    Text("Start tracking", style = MaterialTheme.typography.titleLarge, color = c.onPrimaryContainer)
                    Text("No periods yet", style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Black), color = c.onPrimaryContainer)
                    Text(if (partner) "Log when $her period starts and ends. You will get predictions and tips on how to support $her." else "Log when your period starts and ends. Predictions appear after the first one and sharpen after three cycles.", style = MaterialTheme.typography.bodyLarge, color = c.onPrimaryContainer)
                } else {
                    val delta = info.nextStart - today
                    Text(if (partner) "Day ${info.cycleDay}" + (if (ongoing) " · may need extra support" else "") else "Day ${info.cycleDay} · ${info.phase.label}", style = MaterialTheme.typography.titleLarge, color = c.onPrimaryContainer)
                    Text(
                        when { delta > 0 -> "In $delta day${if (delta == 1L) "" else "s"}"; delta == 0L -> "Due today"; else -> "${-delta} day${if (delta == -1L) "" else "s"} late" },
                        style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Black), color = c.onPrimaryContainer,
                    )
                    Text(
                        (if (partner) "$herCap next period expected " else "Next period expected ") + "${dayText(info.nextStart)} · " + listOf("low", "medium", "high")[info.confidence] + " confidence" +
                            if (info.confidence == 0) ". Log more cycles to improve." else "",
                        style = MaterialTheme.typography.bodyLarge, color = c.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!endedToday) Button(
                        onClick = {
                            if (ongoing && last != null) save { dao.update(last.copy(endDay = today)) }
                            else if (cycles.none { it.startDay == today }) save { dao.insert(PeriodCycle(startDay = today)) }
                        },
                        shapes = ButtonDefaults.shapes(),
                    ) { Text(if (partner) (if (ongoing) "$herCap period ended today" else "$herCap period started today") else if (ongoing) "Period ended today" else "Period started today") }
                    FilledTonalButton({ editDay = today }, shapes = ButtonDefaults.shapes()) { Text("Log today") }
                }
            }
        }

        // Warnings
        if (warnings.isNotEmpty()) SectionCard("Heads up", Icons.Rounded.Warning) {
            warnings.forEach { w ->
                Surface(Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = corner(20), color = if (w.severe) c.errorContainer else c.tertiaryContainer) {
                    Column(Modifier.padding(14.dp)) {
                        Text(w.title, style = MaterialTheme.typography.titleMedium, color = if (w.severe) c.onErrorContainer else c.onTertiaryContainer)
                        Text(w.detail, style = MaterialTheme.typography.bodyMedium, color = if (w.severe) c.onErrorContainer else c.onTertiaryContainer)
                    }
                }
            }
            Text(if (partner) "This is not a diagnosis. Irregular cycles are common. If it keeps happening, gently encourage $her to see a doctor." else "This is not a diagnosis. Irregular cycles are common, but worth discussing with a doctor if they continue. See remedies below.", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }

        // Calendar
        SectionCard("Calendar", Icons.Rounded.CalendarMonth) {
            var month by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
            val ym = YearMonth.parse(month)
            val avgP = info?.avgPeriod ?: CycleMath.DEFAULT_PERIOD
            val period = remember(cycles, days, today) {
                buildSet {
                    cycles.forEach { cy -> val end = cy.endDay ?: minOf(today, cy.startDay + avgP - 1); for (d in cy.startDay..maxOf(end, cy.startDay)) add(d) }
                    days.filter { it.flow >= 2 }.forEach { add(it.epochDay) }
                }
            }
            val predicted = remember(info, ongoing) { if (info == null || ongoing) emptySet() else info.predictedPeriod.toSet() }
            val fertile = remember(info, partner) { if (partner) emptySet() else info?.fertile?.toSet() ?: emptySet() }
            val logged = remember(days) { days.filter { it.symptoms.isNotBlank() || it.note.isNotBlank() || it.flow > 0 }.map { it.epochDay }.toSet() }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton({ month = ym.minusMonths(1).toString() }) { Icon(Icons.Rounded.ChevronLeft, "Previous month") }
                Text("${ym.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${ym.year}", Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
                IconButton({ month = ym.plusMonths(1).toString() }) { Icon(Icons.Rounded.ChevronRight, "Next month") }
            }
            MonthGrid(ym, today, period, predicted, fertile, logged) { editDay = it }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Legend(c.primary, "Period"); Legend(null, "Predicted"); if (!partner) Legend(c.tertiaryContainer, "Fertile")
            }
        }

        // Analytics
        SectionCard("Insights", Icons.Rounded.Insights) {
            if (info == null || info.lengths.isEmpty()) {
                Text("Log at least two periods to see cycle length trends.", style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
            } else {
                val recent = info.lengths.takeLast(12)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Stat("Avg cycle", "${info.avgCycle} d", Modifier.weight(1f))
                    Stat("Avg period", "${info.avgPeriod} d", Modifier.weight(1f))
                    Stat("Range", "${recent.min()}–${recent.max()}", Modifier.weight(1f))
                }
                Text("Cycle length (days)", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
                LengthBars(recent, Modifier.fillMaxWidth().height(120.dp))
                Text("Shaded band is the typical 21–35 day range.", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
            }
            val counts = remember(days) {
                days.filter { it.epochDay >= today - 90 }.flatMap { it.symptoms.split(',').filter(String::isNotBlank) }
                    .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(5)
            }
            if (counts.isNotEmpty()) {
                Text("Top symptoms (90 days)", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
                counts.forEach { (k, n) ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                        Text(k, Modifier.width(110.dp), style = MaterialTheme.typography.bodyMedium)
                        LinearProgressIndicator(progress = { n / counts.first().value.toFloat() }, Modifier.weight(1f).height(8.dp).clip(CircleShape))
                        Text("$n", Modifier.width(32.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        if (partner) SectionCard("Relationship meme", Icons.Rounded.EmojiEmotions) {
            var m by rememberSaveable { mutableIntStateOf((today % memes.size).toInt()) }
            val meme = memes[m % memes.size]
            Surface(Modifier.fillMaxWidth(), shape = corner(24), color = c.tertiaryContainer) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(fill(meme.top, her), style = MaterialTheme.typography.titleMedium, color = c.onTertiaryContainer)
                    Text(fill(meme.bottom, her), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black), color = c.onTertiaryContainer)
                }
            }
            TextButton({ m = (m + 1) % memes.size }, Modifier.padding(top = 4.dp)) { Text("another one") }
        }

        if (partner) SectionCard("How to support $her", Icons.Rounded.Favorite, collapsible = true) {
            var sel by rememberSaveable { mutableIntStateOf(if (ongoing) 1 else 0) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                supportGroups.forEachIndexed { i, g -> FilterChip(sel == i, { sel = i }, { Text(g.title) }) }
            }
            supportGroups[sel].tips.forEach { t ->
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.Favorite, null, Modifier.padding(top = 3.dp).size(14.dp), tint = c.primary)
                    Text(fill(t, her), Modifier.padding(start = 10.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Surface(Modifier.fillMaxWidth().padding(top = 14.dp), shape = corner(20), color = c.errorContainer) {
                Column(Modifier.padding(14.dp)) {
                    Text("When to be concerned", style = MaterialTheme.typography.titleSmall, color = c.onErrorContainer)
                    Text(fill(PARTNER_DOCTOR, her), style = MaterialTheme.typography.bodyMedium, color = c.onErrorContainer)
                }
            }
            Text(CYCLE_DISCLAIMER, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
        } else
SectionCard("Remedies and self-care", Icons.Rounded.Healing, collapsible = true) {
            var sel by rememberSaveable { mutableIntStateOf(0) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                remedyGroups.forEachIndexed { i, g -> FilterChip(sel == i, { sel = i }, { Text(g.title) }) }
            }
            val g = remedyGroups[sel]
            g.items.forEach { r ->
                Column(Modifier.padding(top = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(r.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        AssistChip({}, { Text(r.evidence, style = MaterialTheme.typography.labelSmall) })
                    }
                    Text(r.detail, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
                }
            }
            Surface(Modifier.fillMaxWidth().padding(top = 14.dp), shape = corner(20), color = c.errorContainer) {
                Column(Modifier.padding(14.dp)) {
                    Text("See a doctor if", style = MaterialTheme.typography.titleSmall, color = c.onErrorContainer)
                    Text(g.seeDoctor, style = MaterialTheme.typography.bodyMedium, color = c.onErrorContainer)
                }
            }
            Text(CYCLE_DISCLAIMER, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
        }

        // History
        if (cycles.isNotEmpty()) SectionCard("History", Icons.Rounded.History, collapsible = true) {
            val sorted = cycles.sortedByDescending { it.startDay }
            sorted.forEachIndexed { i, cy ->
                val len = sorted.getOrNull(i - 1)?.let { (it.startDay - cy.startDay).toInt() }
                ListItem(
                    headlineContent = { Text(dayText(cy.startDay) + (cy.endDay?.let { " – " + dayText(it) } ?: " – ongoing")) },
                    supportingContent = {
                        Text(listOfNotNull(cy.endDay?.let { "${it - cy.startDay + 1} day period" }, len?.let { "$it day cycle" }).joinToString(" · ").ifBlank { "In progress" })
                    },
                    trailingContent = { IconButton({ confirmRemove = cy }) { Icon(Icons.Rounded.Delete, "Delete") } },
                    colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                )
            }
        }
    }

    editDay?.let { d ->
        DayDialog(d, today, cycles, days.firstOrNull { it.epochDay == d }, onDismiss = { editDay = null }, onSave = { pd -> save { if (pd.flow == 0 && pd.symptoms.isBlank() && pd.note.isBlank()) dao.clearPeriodDay(d) else dao.upsert(pd) } },
            onStart = { save { dao.insert(PeriodCycle(startDay = d)) } },
            onEnd = { cy -> save { dao.update(cy.copy(endDay = d)) } },
            onRemove = { cy -> save { dao.delete(cy) } })
    }
    confirmRemove?.let { cy ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null }, title = { Text("Delete this period?") },
            text = { Text(dayText(cy.startDay) + " will be removed from your history.") },
            confirmButton = { TextButton({ save { dao.delete(cy) }; confirmRemove = null }) { Text("Delete") } },
            dismissButton = { TextButton({ confirmRemove = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = corner(20), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black), color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun Legend(fill: androidx.compose.ui.graphics.Color?, label: String) {
    val c = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(12.dp).clip(CircleShape).then(if (fill != null) Modifier.background(fill) else Modifier.border(1.5.dp, c.primary, CircleShape)))
        Text(label, style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
    }
}

@Composable
private fun MonthGrid(ym: YearMonth, today: Long, period: Set<Long>, predicted: Set<Long>, fertile: Set<Long>, logged: Set<Long>, onPick: (Long) -> Unit) {
    val c = MaterialTheme.colorScheme
    val lead = ym.atDay(1).dayOfWeek.value - 1
    val total = (lead + ym.lengthOfMonth() + 6) / 7 * 7
    Row {
        listOf("M", "T", "W", "T", "F", "S", "S").forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant) }
    }
    for (row in 0 until total / 7) Row {
        for (col in 0 until 7) {
            val n = row * 7 + col - lead + 1
            if (n < 1 || n > ym.lengthOfMonth()) { Spacer(Modifier.weight(1f).aspectRatio(1f)); continue }
            val d = ym.atDay(n).toEpochDay()
            val isPeriod = d in period
            val bg = when { isPeriod -> c.primary; d in fertile -> c.tertiaryContainer; else -> androidx.compose.ui.graphics.Color.Transparent }
            val fg = when { isPeriod -> c.onPrimary; d in fertile -> c.onTertiaryContainer; d in predicted -> c.primary; else -> c.onSurface }
            Box(
                Modifier.weight(1f).aspectRatio(1f).padding(2.dp).clip(CircleShape).background(bg)
                    .then(if (d in predicted && !isPeriod) Modifier.border(1.5.dp, c.primary, CircleShape) else if (d == today) Modifier.border(1.dp, c.outline, CircleShape) else Modifier)
                    .clickable { onPick(d) },
                contentAlignment = Alignment.Center,
            ) {
                Text("$n", style = MaterialTheme.typography.bodyMedium, color = fg, fontWeight = if (d == today) FontWeight.Bold else null)
                if (d in logged) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).size(4.dp).clip(CircleShape).background(fg))
            }
        }
    }
}

@Composable
private fun LengthBars(lens: List<Int>, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Canvas(modifier) {
        val top = maxOf(40, (lens.max() + 2)).toFloat()
        fun y(v: Float) = size.height * (1f - v / top)
        drawRect(c.tertiaryContainer.copy(alpha = 0.6f), Offset(0f, y(35f)), Size(size.width, y(21f) - y(35f)))
        val slot = size.width / lens.size
        val w = slot * 0.6f
        lens.forEachIndexed { i, l ->
            val color = if (l in 21..35) c.primary else c.error
            drawRoundRect(color, Offset(slot * i + (slot - w) / 2, y(l.toFloat())), Size(w, size.height - y(l.toFloat())), CornerRadius(6.dp.toPx()))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayDialog(
    day: Long, today: Long, cycles: List<PeriodCycle>, existing: PeriodDay?, onDismiss: () -> Unit, onSave: (PeriodDay) -> Unit,
    onStart: () -> Unit, onEnd: (PeriodCycle) -> Unit, onRemove: (PeriodCycle) -> Unit,
) {
    var flow by remember { mutableIntStateOf(existing?.flow ?: 0) }
    var symptoms by remember { mutableStateOf(existing?.symptoms?.split(',')?.filter(String::isNotBlank)?.toSet() ?: emptySet()) }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    val cover = cycles.firstOrNull { day >= it.startDay && day <= (it.endDay ?: (it.startDay + 9)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dateText(day)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Period", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (day > today) Text("Future days can only hold notes.", style = MaterialTheme.typography.bodySmall)
                    else if (cover == null) AssistChip({ onStart(); onDismiss() }, { Text("Period starts here") }, leadingIcon = { Icon(Icons.Rounded.WaterDrop, null, Modifier.size(18.dp)) })
                    else {
                        if (day > cover.startDay) AssistChip({ onEnd(cover); onDismiss() }, { Text("Period ends here") })
                        AssistChip({ onRemove(cover); onDismiss() }, { Text("Remove period") }, leadingIcon = { Icon(Icons.Rounded.Delete, null, Modifier.size(18.dp)) })
                    }
                }
                Text("Flow", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    flowOptions.forEachIndexed { i, l -> FilterChip(flow == i, { flow = i }, { Text(l) }) }
                }
                Text("Symptoms", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    symptomOptions.forEach { s -> FilterChip(s in symptoms, { symptoms = if (s in symptoms) symptoms - s else symptoms + s }, { Text(s) }) }
                }
                AppField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("Note") })
            }
        },
        confirmButton = { TextButton({ onSave(PeriodDay(existing?.id ?: 0, day, flow, symptoms.joinToString(","), note.trim())); onDismiss() }) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}
