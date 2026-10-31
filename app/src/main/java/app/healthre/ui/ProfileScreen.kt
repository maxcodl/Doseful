package app.healthre.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.healthre.Alarms
import app.healthre.AppEnv
import app.healthre.RELEASES_URL
import app.healthre.SOURCE_URL
import app.healthre.TELEGRAM_URL
import app.healthre.TELEGRAM_USER
import app.healthre.data.Backup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** One tappable settings line: title, optional detail, and an icon on the right. */
@Composable
private fun SettingRow(title: String, detail: String? = null, trailing: ImageVector = Icons.Rounded.ChevronRight, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = detail?.let { { Text(it) } },
        trailingContent = { Icon(trailing, null) },
        modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
    )
}

/** You: photo and personal details. App settings live on their own page, opened with the gear. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProfileScreen(app: AppEnv, onOpenSettings: () -> Unit) {
    val ctx = LocalContext.current
    val p = app.profile.value
    val form = remember { ProfileForm(p) }
    val c = MaterialTheme.colorScheme
    val hasPhoto = app.profile.avatarVersion >= 0 && app.profile.avatar() != null

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = LocalListBottom.current - 56.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Profile", Modifier.weight(1f), style = MaterialTheme.typography.displaySmall)
            FilledTonalIconButton(onOpenSettings) { Icon(Icons.Rounded.Settings, "Settings") }
        }

        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(32.dp), color = c.surfaceContainerLow) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AvatarPicker(app, 132.dp)
                Spacer(Modifier.height(8.dp))
                Text(p.name, style = MaterialTheme.typography.headlineMedium)
                val facts = listOfNotNull(p.age?.let { "$it yrs" }, p.bmi?.let { "BMI %.1f".format(it) }).joinToString("  ·  ")
                if (facts.isNotEmpty()) Text(facts, color = c.onSurfaceVariant)
                if (hasPhoto) TextButton({ app.profile.setAvatar(null) }) { Text("Remove photo") }
            }
        }

        SectionCard("Details", Icons.Rounded.Person, collapsible = true, startOpen = true) {
            ProfileFields(form)
            Button({ app.profile.save(form.build()); toast(ctx, "Saved") }, modifier = Modifier.fillMaxWidth(), enabled = form.name.isNotBlank(), shapes = ButtonDefaults.shapes()) {
                Text("Save")
            }
        }
    }
}

/** App settings, separate from the profile: look, reminders, backup and about. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(app: AppEnv, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = MaterialTheme.colorScheme
    fun open(url: String) = ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(uri)!!.use { it.write(Backup.export(app).toByteArray()) } } }
                .onSuccess { toast(ctx, "Backup saved") }.onFailure { toast(ctx, "Backup failed: ${it.message}") }
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) { Backup.import(app, ctx.contentResolver.openInputStream(uri)!!.bufferedReader().readText()) } }
                .onSuccess { toast(ctx, "Backup restored") }.onFailure { toast(ctx, "Restore failed: ${it.message}") }
        }
    }
    var confirmImport by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = LocalListBottom.current - 56.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onBack) { Icon(Icons.Rounded.ArrowBack, "Back") }
            Spacer(Modifier.width(12.dp))
            Text("Settings", style = MaterialTheme.typography.displaySmall)
        }

        SectionCard("Theme", Icons.Rounded.Palette, collapsible = true) { AppearanceSection(app) }
        SectionCard("Navigation bar", Icons.Rounded.ViewDay, collapsible = true) { NavBarEditor(app) }
        SectionCard("Glass", Icons.Rounded.BlurOn, collapsible = true) { GlassEditor(app) }

        SectionCard("Reminders", Icons.Rounded.Notifications, collapsible = true) {
            Choice(listOf("Alarm" to true, "Notification" to false), app.theme.reminderAlarm, { app.theme.updateReminderAlarm(it) })
            FilledTonalButton({ Alarms.notifyTest(ctx, app.theme.reminderAlarm) }, shapes = ButtonDefaults.shapes()) { Text("Send test reminder") }
            if (!Alarms.canExact(ctx)) SettingRow("Exact alarms are off", "Reminders may be late") {
                ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}")))
            }
            if (!Alarms.backgroundAllowed(ctx)) SettingRow("Background activity is limited", "Battery saver may delay reminders") { Alarms.requestBackground(ctx) }
            SettingRow("Persistent notification", "Show or hide today's summary") { Alarms.openChannelSettings(ctx, Alarms.CH_STATUS) }
            SettingRow("Notification settings") {
                ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
            }
        }

        SectionCard("Backup", Icons.Rounded.Backup, collapsible = true) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton({ export.launch("doseful-backup-${LocalDate.now()}.json") }, shapes = ButtonDefaults.shapes()) { Text("Export") }
                OutlinedButton({ confirmImport = true }, shapes = ButtonDefaults.shapes()) { Text("Restore") }
            }
        }

        SectionCard("About", Icons.Rounded.Info, collapsible = true) {
            val version = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?" }
            Text("Doseful $version", style = MaterialTheme.typography.titleMedium)
            SettingRow("Check for updates", trailing = Icons.Rounded.OpenInNew) { open(RELEASES_URL) }
            SettingRow("Source code", "GPL-3.0", Icons.Rounded.OpenInNew) { open(SOURCE_URL) }
            SettingRow("Contact developer", "@$TELEGRAM_USER", Icons.Rounded.OpenInNew) { open(TELEGRAM_URL) }
        }

        // The heart and the coffee cup are icons tinted with the Material You palette, so they follow the wallpaper colours.
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text("Made with", style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Rounded.Favorite, "love", Modifier.size(22.dp), tint = c.primary)
            Spacer(Modifier.width(8.dp))
            Text("and", style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Rounded.Coffee, "coffee", Modifier.size(22.dp), tint = c.tertiary)
            Spacer(Modifier.width(8.dp))
            Text("by Max", style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceVariant)
        }
    }
    if (confirmImport) AlertDialog(
        onDismissRequest = { confirmImport = false },
        title = { Text("Restore backup?") },
        text = { Text("This replaces ALL data on this device with the backup contents. This cannot be undone.") },
        confirmButton = { TextButton({ confirmImport = false; import.launch(arrayOf("application/json", "*/*")) }) { Text("Choose file") } },
        dismissButton = { TextButton({ confirmImport = false }) { Text("Cancel") } },
    )
}
