package app.healthre.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.healthre.AppEnv
import app.healthre.data.GlassStyle

@Composable
internal fun Tweak(label: String, value: Float, range: ClosedFloatingPointRange<Float>, unit: String = "", decimals: Int = 0, onChange: (Float) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            // The unit is appended after formatting, so a "%" in it can never be read as a format code.
            Text("%.${decimals}f".format(value) + unit, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value, onChange, valueRange = range)
    }
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = { Switch(checked, onChange) },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
    )
}

@Composable
private fun Group(title: String) = Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

/** Standard or floating bar, and (for the floating one) its layout, corner roundness and selection look. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NavBarEditor(app: AppEnv) {
    val s = app.theme
    val g = s.glass
    fun set(n: GlassStyle) = s.updateGlass(n)

    Choice(listOf("Standard" to false, "Floating" to true), s.floatingNav, { s.updateFloatingNav(it) })
    if (!s.floatingNav) return

    Group("Layout")
    Choice(listOf("Pill" to 0, "Icons" to 1, "Labels" to 2, "Dock" to 3), g.type, { set(g.copy(type = it)) })
    Text(
        when (g.type) {
            0 -> "The selected item expands to show its name."
            1 -> "Compact bar with icons only."
            2 -> "Every item shows its name under the icon."
            else -> "A wide bar that spans the screen."
        },
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Tweak("Bar radius", g.radius, 0f..100f, " %") { set(g.copy(radius = it)) }
    Tweak("Selection radius", g.selRadius, 0f..100f, " %") { set(g.copy(selRadius = it)) }

    Group("Selection look")
    Choice(listOf("Fill" to 0, "Ring" to 1, "Soft" to 2, "Dot" to 3, "Text" to 4), g.selection, { set(g.copy(selection = it)) })
    Text(
        when (g.selection) {
            0 -> "A solid highlight behind the selected item."
            1 -> "A ring around the selected item."
            2 -> "A light frosted tint."
            3 -> "A small dot under the selected item."
            else -> "Only the icon and name change colour."
        },
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The Liquid Glass material of the floating bar. Changes apply live to the bar at the bottom of the screen. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GlassEditor(app: AppEnv) {
    val s = app.theme
    val g = s.glass
    fun set(n: GlassStyle) = s.updateGlass(n)

    if (!s.floatingNav) {
        Text("The glass look is only used by the floating navigation bar.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FilledTonalButton({ s.updateFloatingNav(true) }, shapes = ButtonDefaults.shapes()) { Text("Switch to floating bar") }
    }

    Group("Material")
    Tweak("Blur", g.blur, 0f..30f, " dp") { set(g.copy(blur = it)) }
    Tweak("Refraction height", g.lensHeight, 0f..48f, " dp") { set(g.copy(lensHeight = it)) }
    Tweak("Refraction amount", g.lensAmount, 0f..48f, " dp") { set(g.copy(lensAmount = it)) }
    Tweak("Colour saturation", g.saturation, 0f..2.5f, "", 2) { set(g.copy(saturation = it)) }
    Tweak("Tint", g.tint, 0f..1f, "", 2) { set(g.copy(tint = it)) }
    Toggle("Depth effect", g.depth) { set(g.copy(depth = it)) }
    Toggle("Colour fringing", g.chromatic) { set(g.copy(chromatic = it)) }

    Group("Edge")
    Tweak("Edge line width", g.edgeWidth, 0f..4f, " dp", 1) { set(g.copy(edgeWidth = it)) }
    Tweak("Edge brightness", g.edgeAlpha, 0f..1f, "", 2) { set(g.copy(edgeAlpha = it)) }
    Text("Edge reflection", style = MaterialTheme.typography.bodyLarge)
    Choice(listOf("Directional" to 0, "Ambient" to 1, "Plain" to 2), g.edgeStyle, { set(g.copy(edgeStyle = it)) })

    Group("Shadow")
    Tweak("Outer shadow size", g.shadowRadius, 0f..40f, " dp") { set(g.copy(shadowRadius = it)) }
    Tweak("Outer shadow strength", g.shadowAlpha, 0f..0.5f, "", 2) { set(g.copy(shadowAlpha = it)) }
    Tweak("Inner glow size", g.innerRadius, 0f..40f, " dp") { set(g.copy(innerRadius = it)) }
    Tweak("Inner glow strength", g.innerAlpha, 0f..0.5f, "", 2) { set(g.copy(innerAlpha = it)) }

    OutlinedButton({ set(GlassStyle()) }, shapes = ButtonDefaults.shapes()) { Text("Reset to defaults") }
}
