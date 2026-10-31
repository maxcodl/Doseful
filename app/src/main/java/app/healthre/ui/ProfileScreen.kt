package app.healthre.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.healthre.Alarms
import app.healthre.RELEASES_URL
import app.healthre.AppEnv
import app.healthre.data.Backup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProfileScreen(app: AppEnv) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val p = app.profile.value
    val form = remember { ProfileForm(p) }

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
    val hasPhoto = app.profile.avatarVersion >= 0 && app.profile.avatar() != null

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Profile", style = MaterialTheme.typography.displaySmall)

        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AvatarPicker(app, 132.dp)
                Spacer(Modifier.height(8.dp))
                Text(p.name, style = MaterialTheme.typography.headlineMedium)
                val facts = listOfNotNull(p.age?.let { "$it yrs" }, p.bmi?.let { "BMI %.1f".format(it) }).joinToString("  ·  ")
                if (facts.isNotEmpty()) Text(facts, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (hasPhoto) TextButton({ app.profile.setAvatar(null) }) { Text("Remove photo") }
            }
        }

        SectionCard("Details") {
            ProfileFields(form)
            Button({ app.profile.save(form.build()); toast(ctx, "Saved") }, modifier = Modifier.fillMaxWidth(), enabled = form.name.isNotBlank(), shapes = ButtonDefaults.shapes()) {
                Text("Save profile")
            }
        }

        SectionCard("Appearance") { AppearanceSection(app) }

        SectionCard("Backup") {
            Text("A JSON file with your profile, photo, medicines and history.", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton({ export.launch("doseful-backup-${LocalDate.now()}.json") }, shapes = ButtonDefaults.shapes()) { Text("Export") }
                OutlinedButton({ confirmImport = true }, shapes = ButtonDefaults.shapes()) { Text("Restore") }
            }
        }

        SectionCard("Reminders") {
            if (!Alarms.canExact(ctx)) {
                Text("Exact alarms are off, so reminders may be late.", color = MaterialTheme.colorScheme.error)
                OutlinedButton({ ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}"))) }, shapes = ButtonDefaults.shapes()) { Text("Allow exact alarms") }
            }
            OutlinedButton({ ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)) }, shapes = ButtonDefaults.shapes()) { Text("Notification settings") }
        }

        SectionCard("About") {
            val version = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?" }
            Text("Doseful $version", style = MaterialTheme.typography.titleMedium)
            Text(
                "Doseful never connects to the internet, so it can not check for updates by itself. Tap below to see the latest release in your browser.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton({ ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_URL))) }, shapes = ButtonDefaults.shapes()) { Text("Check for updates") }
        }

        Text(
            "Free software under GPL-3.0. No network permission, no accounts.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
    if (confirmImport) AlertDialog(
        onDismissRequest = { confirmImport = false },
        title = { Text("Restore backup?") },
        text = { Text("This replaces ALL data on this device with the backup contents. This cannot be undone.") },
        confirmButton = { TextButton({ confirmImport = false; import.launch(arrayOf("application/json", "*/*")) }) { Text("Choose file") } },
        dismissButton = { TextButton({ confirmImport = false }) { Text("Cancel") } },
    )
}
