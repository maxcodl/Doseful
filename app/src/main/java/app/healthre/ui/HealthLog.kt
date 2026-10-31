package app.healthre.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.healthre.AppEnv
import app.healthre.data.Measurement
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Things you can log. [dual] readings have two numbers (blood pressure: top/bottom). */
internal enum class Vital(val key: String, val label: String, val unit: String, val dual: Boolean = false) {
    Weight("weight", "Weight", "kg"),
    Pressure("bp", "Blood pressure", "mmHg", true),
    Sugar("sugar", "Blood sugar", "mg/dL"),
    Heart("heart", "Heart rate", "bpm"),
    Temp("temp", "Temperature", "°C"),
}

private fun num(v: Float) = "%.1f".format(v).removeSuffix(".0")

internal fun Measurement.shown(): String {
    val vital = Vital.entries.firstOrNull { it.key == type }
    return if (vital?.dual == true) "${value.toInt()}/${value2.toInt()}" else num(value)
}

private val stamp = DateTimeFormatter.ofPattern("d MMM, HH:mm")
private fun Measurement.whenText(): String = Instant.ofEpochMilli(recordedAt).atZone(ZoneId.systemDefault()).format(stamp)

@Composable
private fun LineChart(values: List<Float>, second: List<Float>?, modifier: Modifier = Modifier) {
    val line = MaterialTheme.colorScheme.primary
    val line2 = MaterialTheme.colorScheme.tertiary
    val grid = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val all = values + (second ?: emptyList())
        val lo = all.min()
        val hi = all.max().let { if (it == lo) lo + 1f else it }
        val pad = 8.dp.toPx()
        fun x(i: Int) = pad + (size.width - 2 * pad) * i / (values.size - 1)
        fun y(v: Float) = size.height - pad - (size.height - 2 * pad) * (v - lo) / (hi - lo)
        for (g in 0..2) drawLine(grid, Offset(0f, pad + (size.height - 2 * pad) * g / 2), Offset(size.width, pad + (size.height - 2 * pad) * g / 2), 1.dp.toPx())
        fun draw(vs: List<Float>, color: Color) {
            val path = Path()
            vs.forEachIndexed { i, v -> if (i == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v)) }
            drawPath(path, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            vs.forEachIndexed { i, v -> drawCircle(color, 4.dp.toPx(), Offset(x(i), y(v))) }
        }
        second?.let { draw(it, line2) }
        draw(values, line)
    }
}

@Composable
private fun AddReadingDialog(app: AppEnv, vital: Vital, onDone: () -> Unit) {
    val dao = app.db.dao()
    val scope = rememberCoroutineScope()
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val va = a.replace(',', '.').toFloatOrNull()
    val vb = b.replace(',', '.').toFloatOrNull()
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Add ${vital.label.lowercase()}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppField(a, { a = it }, Modifier.fillMaxWidth(), label = { Text(if (vital.dual) "Top number (systolic)" else "${vital.label} (${vital.unit})") }, singleLine = true, keyboardOptions = kbDec)
                if (vital.dual) AppField(b, { b = it }, Modifier.fillMaxWidth(), label = { Text("Bottom number (diastolic)") }, singleLine = true, keyboardOptions = kbDec)
                AppField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("Note (optional)") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(enabled = va != null && (!vital.dual || vb != null), onClick = {
                scope.launch {
                    dao.insert(Measurement(type = vital.key, value = va!!, value2 = vb ?: 0f, note = note.trim()))
                    // A new weight also updates the weight on your profile, so BMI stays current.
                    if (vital == Vital.Weight) app.profile.save(app.profile.value.copy(weightKg = va))
                    onDone()
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onDone) { Text("Cancel") } },
    )
}

/** Log and chart weight, blood pressure, blood sugar, heart rate and temperature. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HealthLogScreen(app: AppEnv, onBack: () -> Unit) {
    val dao = app.db.dao()
    val scope = rememberCoroutineScope()
    val all by dao.measurements().collectAsState(emptyList())
    var key by rememberSaveable { mutableStateOf(Vital.Weight.key) }
    val vital = Vital.entries.first { it.key == key }
    val items = all.filter { it.type == vital.key }
    val recent = items.takeLast(20)
    var adding by remember { mutableStateOf(false) }
    val c = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = LocalListBottom.current - 56.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onBack) { Icon(Icons.Rounded.ArrowBack, "Back") }
            Spacer(Modifier.width(12.dp))
            Text("Health log", style = MaterialTheme.typography.displaySmall)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Vital.entries.forEach { v -> FilterChip(v == vital, { key = v.key }, { Text(v.label) }) }
        }

        Surface(Modifier.fillMaxWidth(), shape = corner(28), color = c.surfaceContainerLow) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(vital.label, style = MaterialTheme.typography.titleMedium, color = c.primary)
                val last = items.lastOrNull()
                if (last == null) Text("No readings yet.", color = c.onSurfaceVariant)
                else {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(last.shown(), style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Black))
                        Text(vital.unit, Modifier.padding(bottom = 10.dp), color = c.onSurfaceVariant)
                    }
                    if (items.size >= 2 && !vital.dual) {
                        val diff = last.value - items[items.size - 2].value
                        Text((if (diff > 0) "+" else "") + num(diff) + " since the previous reading", color = c.onSurfaceVariant)
                    }
                    if (recent.size >= 2) LineChart(recent.map { it.value }, if (vital.dual) recent.map { it.value2 } else null, Modifier.fillMaxWidth().height(140.dp))
                }
            }
        }
        Button({ adding = true }, shapes = ButtonDefaults.shapes()) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp)); Text("Add reading") }

        if (items.isNotEmpty()) Surface(Modifier.fillMaxWidth(), shape = corner(28), color = c.surfaceContainerLow) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                items.asReversed().forEach { m ->
                    ListItem(
                        headlineContent = { Text(m.shown() + " " + vital.unit) },
                        supportingContent = { Text(m.whenText() + m.note.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()) },
                        trailingContent = { IconButton({ scope.launch { dao.delete(m) } }) { Icon(Icons.Rounded.Delete, "Delete") } },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        }
    }
    if (adding) AddReadingDialog(app, vital) { adding = false }
}
