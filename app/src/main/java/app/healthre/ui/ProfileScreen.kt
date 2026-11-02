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
import app.healthre.data.CycleEvent
import app.healthre.partnerMode
import app.healthre.partnerName
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
        modifier = Modifier.clip(corner(16)).clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
    )
}

/** You: photo and personal details. App settings live on their own page, opened with the gear. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProfileScreen(app: AppEnv, onOpen: (Int) -> Unit) {
    val ctx = LocalContext.current
    val p = app.profile.value
    val form = remember { ProfileForm(p) }
    val c = MaterialTheme.colorScheme
    val hasPhoto = app.profile.avatarVersion >= 0 && app.profile.avatar() != null

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = LocalListBottom.current - 56.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Profile", Modifier.weight(1f), style = MaterialTheme.typography.displaySmall)
            FilledTonalIconButton({ onOpen(1) }) { Icon(Icons.Rounded.Settings, "Settings") }
        }

        Surface(Modifier.fillMaxWidth(), shape = corner(32), color = c.surfaceContainerLow) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AvatarPicker(app, 132.dp)
                Spacer(Modifier.height(8.dp))
                Text(p.name, style = MaterialTheme.typography.headlineMedium)
                val facts = listOfNotNull(p.age?.let { "$it yrs" }, p.bmi?.let { "BMI %.1f".format(it) }).joinToString("  ·  ")
                if (facts.isNotEmpty()) Text(facts, color = c.onSurfaceVariant)
                if (hasPhoto) TextButton({ app.profile.setAvatar(null) }) { Text("Remove photo") }
            }
        }

        StatsRow(app)
        MedicalIdCard(app)
        AppointmentsSummary(app) { onOpen(2) }
        HealthSummary(app) { onOpen(3) }
        CycleSettings(app)

        SectionCard("Details", Icons.Rounded.Person, collapsible = true) {
            ProfileFields(form)
            Button({ app.profile.save(form.build()); toast(ctx, "Saved") }, modifier = Modifier.fillMaxWidth(), enabled = form.name.isNotBlank(), shapes = ButtonDefaults.shapes()) {
                Text("Save")
            }
        }
    }
}

/** Period tracker switch: shows or hides the Cycle tab. */
@Composable
private fun CycleSettings(app: AppEnv) {
    val ctx = LocalContext.current
    SectionCard("Cycle tracking", Icons.Rounded.WaterDrop, collapsible = true) {
        val cScope = rememberCoroutineScope()
        var wipe by remember { mutableStateOf(false) }
        val transparent = androidx.compose.ui.graphics.Color.Transparent
        fun reschedule() { if (!app.demo) Alarms.scheduleCycle(ctx) }
        ListItem(
            headlineContent = { Text("Period tracker") },
            supportingContent = { Text("Adds a Cycle tab with predictions, insights and remedies. Data stays on this device.") },
            trailingContent = { Switch(app.theme.cycleEnabled, { app.theme.updateCycleEnabled(it); reschedule() }) },
            colors = ListItemDefaults.colors(containerColor = transparent),
        )
        if (app.theme.cycleEnabled) {
            Text("Whose cycle is this?", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            Choice(listOf("Me" to false, "My partner" to true), app.partnerMode, { app.theme.updateCycleFor(if (it) "partner" else "me"); reschedule() })
            if (app.partnerMode) AppField(
                app.theme.cyclePartnerName, { app.theme.updateCyclePartnerName(it) },
                Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Her name (optional)") }, singleLine = true,
            )
            ListItem(
                headlineContent = { Text(if (app.partnerMode) "Daily support tips" else "Period reminders and tips") },
                supportingContent = { Text(if (app.partnerMode) "One to three friendly notifications a day, only when it matters, never at night" else "One to three notifications a day around your period, never at night") },
                trailingContent = { Switch(app.theme.cycleReminder, { app.theme.updateCycleReminder(it); reschedule() }) },
                colors = ListItemDefaults.colors(containerColor = transparent),
            )
            if (app.theme.cycleReminder) ListItem(
                headlineContent = { Text("Romantic evening message") },
                supportingContent = { Text(if (app.partnerMode) "One extra, around 8 pm. Sweet and never pushy; just comfort on period days" else "One extra, around 8 pm. Cosy, flirty ideas for your evening; just comfort on period days") },
                trailingContent = { Switch(app.theme.cycleEvening, { app.theme.updateCycleEvening(it); reschedule() }) },
                colors = ListItemDefaults.colors(containerColor = transparent),
            )
        }
        if (app.theme.cycleEnabled) {
            var next by remember { mutableIntStateOf(0) }
            FilledTonalButton({
                val n = next++
                val day = LocalDate.now().toEpochDay()
                if (app.theme.cycleEvening && n % 5 == 4) { Alarms.testEvening(app, n % 2 == 0); return@FilledTonalButton }
                val e = CycleEvent.entries[n % 5 % CycleEvent.entries.size]
                val (t, x) = if (app.partnerMode) partnerNotification(e, app.partnerName, day, n / 5 % 3) else selfNotification(e, day, n / 5 % 3)
                Alarms.notifyCycle(ctx, t, x)
            }, shapes = ButtonDefaults.shapes()) { Text("Send test tip") }
        }
        SettingRow("Delete cycle data", "Removes all logged periods and symptoms") { wipe = true }
        if (wipe) AlertDialog(
            onDismissRequest = { wipe = false }, title = { Text("Delete cycle data?") },
            text = { Text("All periods, flow and symptom logs will be removed. This cannot be undone.") },
            confirmButton = {
                TextButton({
                    cScope.launch { val d = app.db.dao(); d.clearCycles(); d.clearPeriodDays(); reschedule() }
                    wipe = false
                }) { Text("Delete") }
            },
            dismissButton = { TextButton({ wipe = false }) { Text("Cancel") } },
        )
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

        SectionCard("Privacy", Icons.Rounded.Lock, collapsible = true) {
            val km = ctx.getSystemService(android.app.KeyguardManager::class.java)
            ListItem(
                headlineContent = { Text("App lock") },
                supportingContent = { Text("Ask for your screen lock when Doseful opens") },
                trailingContent = {
                    Switch(app.theme.appLock, { on ->
                        if (on && !km.isDeviceSecure) toast(ctx, "Set a screen lock in your phone settings first") else app.theme.updateAppLock(on)
                    })
                },
                colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            )
        }

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
