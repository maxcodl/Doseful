package app.healthre.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.healthre.AppEnv
import app.healthre.data.Profile
import androidx.compose.foundation.background

/** Expressive shape used for the profile picture, everywhere. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun avatarShape(): Shape = MaterialShapes.Cookie9Sided.toShape()

@Composable
fun Avatar(app: AppEnv, size: Dp, modifier: Modifier = Modifier) {
    val v = app.profile.avatarVersion
    val bmp = remember(v) { app.profile.avatar()?.asImageBitmap() }
    Box(
        modifier.size(size).clip(avatarShape()).background(MaterialTheme.colorScheme.tertiaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (bmp != null) Image(bmp, "Profile picture", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text(
            app.profile.value.name.take(1).uppercase().ifBlank { "?" },
            style = MaterialTheme.typography.headlineMedium.copy(fontSize = (size.value / 2.4f).sp),
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

private val Float.sp get() = androidx.compose.ui.unit.TextUnit(this, androidx.compose.ui.unit.TextUnitType.Sp)

/** Avatar with a camera badge (outside the shape clip so it is never cut off); tapping opens the system photo picker. */
@Composable
fun AvatarPicker(app: AppEnv, size: Dp = 128.dp) {
    val pick = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) runCatching { app.profile.setAvatar(uri) } }
    Box(Modifier.size(size)) {
        Avatar(app, size, Modifier.clip(avatarShape()).clickable {
            pick.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
        })
        Surface(
            Modifier.align(Alignment.BottomEnd).size(40.dp),
            shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.primary,
            border = androidx.compose.foundation.BorderStroke(3.dp, MaterialTheme.colorScheme.surface),
        ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.PhotoCamera, "Choose photo", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimary) } }
    }
}

/** Filled, rounded text field with no underline: reads well on both normal and AMOLED surfaces. */
@Composable
fun AppField(
    value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null, singleLine: Boolean = false,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default,
) {
    val c = MaterialTheme.colorScheme
    val none = androidx.compose.ui.graphics.Color.Transparent
    TextField(
        value, onValueChange, modifier, label = label, singleLine = singleLine, keyboardOptions = keyboardOptions,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = none, unfocusedIndicatorColor = none, disabledIndicatorColor = none, errorIndicatorColor = none,
            focusedContainerColor = c.surfaceContainerHighest, unfocusedContainerColor = c.surfaceContainerHighest,
        ),
    )
}

/** Titled, rounded group with a coloured shape icon. With [collapsible] the header toggles the content open and closed. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SectionCard(title: String, icon: ImageVector? = null, collapsible: Boolean = false, startOpen: Boolean = !collapsible, content: @Composable ColumnScope.() -> Unit) {
    val c = MaterialTheme.colorScheme
    var open by androidx.compose.runtime.saveable.rememberSaveable(title) { mutableStateOf(startOpen) }
    val rotation by androidx.compose.animation.core.animateFloatAsState(if (open) 180f else 0f, label = "chevron")
    Surface(Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp), color = c.surfaceContainerLow) {
        Column(Modifier.padding(20.dp)) {
            Row(
                Modifier.fillMaxWidth().then(
                    if (collapsible) Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).clickable { open = !open } else Modifier,
                ),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (icon != null) Box(Modifier.size(40.dp).clip(MaterialShapes.Cookie6Sided.toShape()).background(c.primaryContainer), contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(22.dp), tint = c.onPrimaryContainer)
                }
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                if (collapsible) Icon(Icons.Rounded.ExpandMore, if (open) "Collapse" else "Expand", Modifier.rotate(rotation))
            }
            androidx.compose.animation.AnimatedVisibility(
                open, enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut(),
            ) {
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
            }
        }
    }
}

/** A slowly rotating expressive shape, optionally holding content. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SpinningShape(shape: androidx.compose.ui.graphics.Shape, size: Dp, color: androidx.compose.ui.graphics.Color, seconds: Int = 24, content: @Composable BoxScope.() -> Unit = {}) {
    val rot by rememberInfiniteTransition(label = "spin").animateFloat(
        0f, 360f, infiniteRepeatable(tween(seconds * 1000, easing = LinearEasing), RepeatMode.Restart), label = "rot",
    )
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().graphicsLayer { rotationZ = rot }.clip(shape).background(color))
        content()
    }
}

/** Connected expressive toggle-button group. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> Choice(options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        options.forEachIndexed { i, (label, value) ->
            val shapes = when (i) {
                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
            }
            val c = MaterialTheme.colorScheme
            ToggleButton(
                selected == value, { onSelect(value) }, Modifier.weight(1f), shapes = shapes,
            ) { Text(label) }
        }
    }
}

/** Editable profile state shared by onboarding and the Profile tab. Height/weight are shown in the chosen unit system. */
class ProfileForm(p: Profile) {
    var name by mutableStateOf(p.name)
    var email by mutableStateOf(p.email)
    var age by mutableStateOf(p.age?.toString() ?: "")
    var gender by mutableStateOf(p.gender)
    var metric by mutableStateOf(p.metric)
    var height by mutableStateOf(disp(p.metric, p.heightCm, 2.54f, "%.0f", "%.1f"))
    var weight by mutableStateOf(disp(p.metric, p.weightKg, 0.45359f, "%.1f", "%.1f"))

    private fun disp(m: Boolean, v: Float?, k: Float, fm: String, fi: String) =
        v?.let { if (m) fm.format(it) else fi.format(it / k) } ?: ""

    fun build() = Profile(
        name.trim(), email.trim(), age.toIntOrNull(), gender,
        height.toFloatOrNull()?.let { if (metric) it else it * 2.54f },
        weight.toFloatOrNull()?.let { if (metric) it else it * 0.45359f },
        metric, true,
    )

    fun setUnits(m: Boolean) {
        if (m == metric) return
        val b = build(); metric = m
        height = disp(m, b.heightCm, 2.54f, "%.0f", "%.1f")
        weight = disp(m, b.weightKg, 0.45359f, "%.1f", "%.1f")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileFields(f: ProfileForm, showEmail: Boolean = true) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppField(f.name, { f.name = it }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true)
        if (showEmail) AppField(f.email, { f.email = it }, Modifier.fillMaxWidth(), label = { Text("Email (optional)") }, singleLine = true)
        Choice(listOf("Female" to "Female", "Male" to "Male", "Other" to "Other"), f.gender, { f.gender = if (f.gender == it) "" else it })
        Choice(listOf("Metric" to true, "Imperial" to false), f.metric, { f.setUnits(it) })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppField(f.age, { f.age = it }, Modifier.weight(1f), label = { Text("Age") }, singleLine = true, keyboardOptions = kbNum)
            AppField(f.height, { f.height = it }, Modifier.weight(1f), label = { Text(if (f.metric) "Height cm" else "Height in") }, singleLine = true, keyboardOptions = kbDec)
            AppField(f.weight, { f.weight = it }, Modifier.weight(1f), label = { Text(if (f.metric) "Weight kg" else "Weight lb") }, singleLine = true, keyboardOptions = kbDec)
        }
    }
}

/** Theme mode + AMOLED switch. Changes apply instantly. */
@Composable
fun AppearanceSection(app: AppEnv) {
    val s = app.theme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Choice(listOf("System" to 0, "Light" to 1, "Dark" to 2), s.themeMode, { s.updateTheme(it) })
        val darkNow = s.themeMode == 2 || (s.themeMode == 0 && androidx.compose.foundation.isSystemInDarkTheme())
        ListItem(
            headlineContent = { Text("AMOLED black") },
            trailingContent = { Switch(s.amoled, { s.updateAmoled(it) }) },
            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        )
        Text("Navigation bar", style = MaterialTheme.typography.titleSmall)
        Choice(listOf("Standard" to false, "Floating" to true), s.floatingNav, { s.updateFloatingNav(it) })
    }
}
