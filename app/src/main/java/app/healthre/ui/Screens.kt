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
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.border
import com.kyant.backdrop.shadow.Shadow
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import androidx.compose.ui.graphics.graphicsLayer
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
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

/** How much room lists leave at the bottom: more when the floating bar and button overlay the content. */
internal val LocalListBottom = androidx.compose.runtime.compositionLocalOf { 96.dp }

/** One destination in the floating bar. Layout follows the bar [type]; the selected look follows [selection]. */
@Composable
private fun NavItem(
    label: String, icon: ImageVector, selected: Boolean, type: Int, selection: Int,
    shape: androidx.compose.ui.graphics.Shape, modifier: Modifier = Modifier, onClick: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val fg = when { !selected -> c.onSurface; selection == 0 -> c.onPrimaryContainer; else -> c.primary }
    val bg = when {
        !selected -> Color.Transparent
        selection == 0 -> c.primaryContainer.copy(alpha = 0.9f)
        selection == 2 -> c.onSurface.copy(alpha = 0.16f)
        else -> Color.Transparent
    }
    val outline = if (selected && selection == 1) Modifier.border(1.5.dp, c.primary, shape) else Modifier
    val dot = if (selected && selection == 3) Modifier.drawBehind {
        drawCircle(c.primary, 2.5.dp.toPx(), Offset(size.width / 2f, size.height - 3.dp.toPx()))
    } else Modifier
    val base = modifier.clip(shape).background(bg).then(outline).then(dot).clickable(onClick = onClick)
    when (type) {
        1 -> Box(base.padding(horizontal = 18.dp, vertical = 12.dp), contentAlignment = Alignment.Center) { Icon(icon, label, tint = fg) }
        2, 3 -> Column(base.padding(horizontal = 14.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, label, tint = fg)
            Text(label, style = MaterialTheme.typography.labelSmall, color = fg, maxLines = 1)
        }
        else -> Row(
            base.padding(horizontal = if (selected) 20.dp else 16.dp, vertical = 12.dp).animateContentSize(),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, label, tint = fg)
            if (selected) Text(label, style = MaterialTheme.typography.labelLarge, color = fg)
        }
    }
}

/**
 * Liquid Glass bar that floats over the content (no bar behind it, so the page scrolls underneath).
 * [backdrop] holds the page content it refracts; [g] is the user's glass style, including bar type and corner radius.
 */
@Composable
private fun FloatingNav(items: List<Pair<String, ImageVector>>, selected: Int, backdrop: com.kyant.backdrop.backdrops.LayerBackdrop, g: GlassStyle, onSelect: (Int) -> Unit) {
    val c = MaterialTheme.colorScheme
    val tint = c.surfaceContainerHigh.copy(alpha = g.tint)
    val edge = when (g.edgeStyle) { 1 -> Highlight.Ambient; 2 -> Highlight.Plain; else -> Highlight.Default }
    // Corners are a percentage of the shape's own size, so they can never exceed fully round (which would break the glass shader).
    val percent = (g.radius / 2f).toInt().coerceIn(0, 50)
    val barShape = RoundedCornerShape(percent)
    val itemShape = RoundedCornerShape(percent)
    val dock = g.type == 3
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = if (dock) 12.dp else 24.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
        // The library only recomputes its lens and edge shader when the size changes, so rebuild the bar when the corner radius changes.
        androidx.compose.runtime.key(percent, g.type) {
        Row(
            Modifier
                .then(if (dock) Modifier.fillMaxWidth() else Modifier)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { barShape },
                    effects = {
                        if (g.saturation != 1f) colorControls(saturation = g.saturation)
                        blur(g.blur.dp.toPx())
                        lens(g.lensHeight.dp.toPx(), g.lensAmount.dp.toPx(), g.depth, g.chromatic)
                    },
                    highlight = if (g.edgeWidth > 0f) ({ edge.copy(width = g.edgeWidth.dp, blurRadius = (g.edgeWidth / 2f).dp, alpha = g.edgeAlpha) }) else null,
                    shadow = if (g.shadowRadius > 0f && g.shadowAlpha > 0f) ({ Shadow(radius = g.shadowRadius.dp, color = Color.Black.copy(alpha = g.shadowAlpha)) }) else null,
                    innerShadow = if (g.innerAlpha > 0f) ({ InnerShadow(radius = g.innerRadius.dp, color = Color.White.copy(alpha = g.innerAlpha)) }) else null,
                    onDrawSurface = { drawRect(tint) },
                )
                .padding(6.dp),
            horizontalArrangement = if (dock) Arrangement.SpaceEvenly else Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { i, (label, icon) ->
                NavItem(label, icon, i == selected, g.type, g.selection, itemShape, if (dock) Modifier.weight(1f) else Modifier) { onSelect(i) }
            }
        }
        }
    }
}

/** The whole signed-in app. The demo reuses it unchanged, with [header] shown above the tabs. */
@Composable
fun MainScaffold(app: AppEnv, header: @Composable ColumnScope.() -> Unit = {}) {
    var tab by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Medicine?>(null) }
    var adding by remember { mutableStateOf(false) }
    var inSettings by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    // Predictive back: from any other tab the gesture previews a shrinking page and returns to Today.
    var backProgress by remember { mutableFloatStateOf(0f) }
    PredictiveBackHandler(enabled = tab != 0) { progress ->
        try {
            progress.collect { backProgress = it.progress }
            tab = 0
            backProgress = 0f
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            backProgress = 0f
        }
    }
    // Back from the Settings page returns to Profile first.
    androidx.activity.compose.BackHandler(enabled = tab == 3 && inSettings) { inSettings = false }
    val floating = app.theme.floatingNav
    val backdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop()
    val navItems = listOf("Today" to Icons.Rounded.Today, "Medicines" to ImageVector.vectorResource(R.drawable.ic_pill), "History" to Icons.Rounded.Insights, "Profile" to Icons.Rounded.Person)
    androidx.compose.runtime.CompositionLocalProvider(LocalListBottom provides if (floating) 200.dp else 96.dp) {
        Scaffold(
            // Standard mode uses a normal bottom bar and button. Floating mode draws both over the page instead.
            bottomBar = {
                if (!floating) NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
                    navItems.forEachIndexed { i, (l, ic) -> NavigationBarItem(tab == i, { tab = i }, { Icon(ic, null) }, label = { Text(l) }) }
                }
            },
            floatingActionButton = {
                if (!floating && tab <= 1) FloatingActionButton({ adding = true }) { Icon(Icons.Rounded.Add, "Add medicine") }
            },
            contentWindowInsets = if (floating) WindowInsets.statusBars else ScaffoldDefaults.contentWindowInsets,
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                Column(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                    header()
                    Box(Modifier.weight(1f).graphicsLayer {
                        val scale = 1f - 0.08f * backProgress
                        scaleX = scale; scaleY = scale; alpha = 1f - 0.3f * backProgress
                    }) {
                        when (tab) {
                            0 -> Today(app)
                            1 -> Medicines(app) { editing = it }
                            2 -> History(app)
                            else -> if (inSettings) SettingsScreen(app) { inSettings = false } else ProfileScreen(app) { inSettings = true }
                        }
                    }
                }
                if (floating) Column(Modifier.align(Alignment.BottomCenter), horizontalAlignment = Alignment.End) {
                    if (tab <= 1) FloatingActionButton({ adding = true }, Modifier.padding(end = 24.dp)) { Icon(Icons.Rounded.Add, "Add medicine") }
                    FloatingNav(navItems, tab, backdrop, app.theme.glass) { tab = it }
                }
            }
        }
    }
    if (adding) MedicineDialog(app, null) { adding = false }
    editing?.let { MedicineDialog(app, it) { editing = null } }
}

private val morning = listOf("Good morning,", "Rise and shine,", "Morning,", "Fresh start,", "Hope you slept well,")
private val afternoon = listOf("Good afternoon,", "Hope your day is going well,", "Afternoon,", "Hey there,")
private val evening = listOf("Good evening,", "Winding down,", "Evening,", "Hope it was a good day,")
private val night = listOf("Good night,", "Still up,", "Take it easy,", "Rest well,")
private val anytime = listOf("Welcome back,", "Hello,", "Hey,", "Nice to see you,", "Stay on track,")

/** A random greeting that fits the time of day, picked once and kept across rotations. */
@Composable
private fun rememberGreeting(): String = rememberSaveable {
    val h = LocalTime.now().hour
    val byTime = when (h) { in 5..11 -> morning; in 12..16 -> afternoon; in 17..20 -> evening; else -> night }
    (byTime + anytime).random()
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StatTile(
    title: String, value: String, icon: ImageVector, shape: androidx.compose.ui.graphics.Shape,
    container: Color, onContainer: Color, modifier: Modifier = Modifier, note: String? = null,
) {
    Surface(modifier, shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Box(Modifier.size(48.dp).clip(shape).background(container), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = onContainer)
            }
            Spacer(Modifier.height(14.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(value, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black), color = MaterialTheme.colorScheme.primary)
            if (note != null) Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
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
    val meds by dao.medicines().collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    val greeting = rememberGreeting()
    val done = list.count { logs[it.schedule.id]?.status == DoseStatus.TAKEN }
    val skipped = list.count { logs[it.schedule.id]?.status == DoseStatus.SKIPPED }
    val lowStock = meds.count { it.refillThreshold > 0 && it.stock <= it.refillThreshold }
    val pending = list.filter { logs[it.schedule.id] == null }
    val next = pending.firstOrNull { it.schedule.minuteOfDay >= nowMin } ?: pending.firstOrNull()
    val name = app.profile.value.name.substringBefore(' ').ifBlank { "there" }
    val c = MaterialTheme.colorScheme

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = LocalListBottom.current), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(Modifier.padding(top = 28.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(greeting, style = MaterialTheme.typography.titleLarge, color = c.onSurfaceVariant)
                    Text(name, style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Black), maxLines = 1)
                }
                Avatar(app, 60.dp)
            }
            Spacer(Modifier.height(12.dp))
        }
        item {
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(32.dp), color = c.primaryContainer) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Doses today", style = MaterialTheme.typography.titleLarge, color = c.onPrimaryContainer)
                    Text(
                        if (list.isEmpty()) "None" else "$done of ${list.size}",
                        style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Black), color = c.onPrimaryContainer,
                    )
                    Spacer(Modifier.height(4.dp))
                    LinearWavyProgressIndicator(
                        progress = { if (list.isEmpty()) 0f else done / list.size.toFloat() }, modifier = Modifier.fillMaxWidth(),
                        color = c.primary, trackColor = c.onPrimaryContainer.copy(alpha = 0.18f),
                    )
                    Text(
                        when {
                            list.isEmpty() -> "Nothing scheduled. Add a medicine with +."
                            pending.isEmpty() -> "All done for today"
                            else -> "${pending.size} still to go"
                        },
                        style = MaterialTheme.typography.bodyLarge, color = c.onPrimaryContainer,
                    )
                }
            }
        }
        item { Text("Overview", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 4.dp, top = 8.dp)) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("Next dose", next?.let { fmt(it.schedule.minuteOfDay) } ?: "--", Icons.Rounded.Alarm, MaterialShapes.Cookie6Sided.toShape(), c.primaryContainer, c.onPrimaryContainer, Modifier.weight(1f).fillMaxHeight(), next?.medicine?.name ?: "Nothing pending")
                    StatTile("Medicines", "${meds.size}", ImageVector.vectorResource(R.drawable.ic_pill), MaterialShapes.Clover4Leaf.toShape(), c.secondaryContainer, c.onSecondaryContainer, Modifier.weight(1f).fillMaxHeight(), "in your list")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("Refills due", "$lowStock", Icons.Rounded.Inventory2, MaterialShapes.Flower.toShape(), c.errorContainer, c.onErrorContainer, Modifier.weight(1f).fillMaxHeight(), if (lowStock == 0) "all stocked" else "running low")
                    StatTile("Skipped", "$skipped", Icons.Rounded.Close, MaterialShapes.Pentagon.toShape(), c.tertiaryContainer, c.onTertiaryContainer, Modifier.weight(1f).fillMaxHeight(), "today")
                }
            }
        }
        if (list.isNotEmpty()) item { Text("Today's schedule", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 4.dp, top = 8.dp)) }
        items(list, key = { it.schedule.id }) { sm ->
            val log = logs[sm.schedule.id]
            val overdue = log == null && sm.schedule.minuteOfDay < nowMin
            val shapes = listOf(MaterialShapes.Cookie6Sided, MaterialShapes.Clover4Leaf, MaterialShapes.Flower, MaterialShapes.Pentagon, MaterialShapes.Cookie9Sided)
            val (statusIcon, bg, fg) = when {
                log?.status == DoseStatus.TAKEN -> Triple(Icons.Rounded.Check, c.primaryContainer, c.onPrimaryContainer)
                log?.status == DoseStatus.SKIPPED -> Triple(Icons.Rounded.Close, c.tertiaryContainer, c.onTertiaryContainer)
                overdue -> Triple(Icons.Rounded.Alarm, c.errorContainer, c.onErrorContainer)
                else -> Triple(ImageVector.vectorResource(R.drawable.ic_pill), c.secondaryContainer, c.onSecondaryContainer)
            }
            ElevatedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(52.dp).clip(shapes[(sm.schedule.id % shapes.size).toInt()].toShape()).background(bg), contentAlignment = Alignment.Center) {
                        Icon(statusIcon, null, tint = fg)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(sm.medicine.name, style = MaterialTheme.typography.titleLarge)
                        Text("${fmt(sm.schedule.minuteOfDay)} · ${sm.medicine.unitDose} ${sm.medicine.strength}".trim())
                        when {
                            log != null -> Text(log.status.name.lowercase(), color = MaterialTheme.colorScheme.primary)
                            overdue -> Text("overdue", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    if (log == null) {
                        FilledTonalIconButton({ scope.launch { Alarms.skipDose(ctx, dao, sm.schedule.id, today, app.demo) } }) { Icon(Icons.Rounded.Close, "Skip") }
                        Spacer(Modifier.width(8.dp))
                        FilledIconButton({ scope.launch { Alarms.takeDose(ctx, dao, sm.schedule.id, today, app.demo) } }) { Icon(Icons.Rounded.Check, "Taken") }
                    } else TextButton({ scope.launch { Alarms.undoDose(ctx, dao, sm.schedule.id, today, app.demo) } }) { Text("Undo") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun Medicines(app: AppEnv, onEdit: (Medicine) -> Unit) {
    val ctx = LocalContext.current
    val dao = app.db.dao()
    val meds by dao.medicines().collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    var deleting by remember { mutableStateOf<Medicine?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = LocalListBottom.current), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Medicines", style = MaterialTheme.typography.displaySmall) }
        if (meds.isEmpty()) item { Text("No medicines yet.") }
        items(meds, key = { it.id }) { m ->
            val low = m.refillThreshold > 0 && m.stock <= m.refillThreshold
            Card(
                { onEdit(m) }, Modifier.fillMaxWidth(),
                colors = if (low) CardDefaults.cardColors(MaterialTheme.colorScheme.errorContainer) else CardDefaults.cardColors(),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(52.dp).clip(MaterialShapes.Cookie9Sided.toShape())
                            .background(if (low) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            ImageVector.vectorResource(R.drawable.ic_pill), null,
                            tint = if (low) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                    Spacer(Modifier.width(14.dp))
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
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = LocalListBottom.current), verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
