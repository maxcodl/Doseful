package app.healthre.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.res.painterResource
import app.healthre.R
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.healthre.Alarms
import app.healthre.AppEnv
import kotlinx.coroutines.launch

private const val PAGES = 5

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Onboarding(app: AppEnv) {
    val pager = rememberPagerState { PAGES }
    val scope = rememberCoroutineScope()
    val form = remember { ProfileForm(app.profile.value) }
    val page = pager.currentPage
    val canNext = page != 3 || form.name.isNotBlank()
    var demo by remember { mutableStateOf(false) }
    if (demo) {
        DemoMode(app) { demo = false; scope.launch { pager.scrollToPage(1) } }
        return
    }

    Scaffold(
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton({ scope.launch { pager.animateScrollToPage(page - 1) } }, enabled = page > 0) { Text("Back") }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    repeat(PAGES) { i ->
                        val w by animateDpAsState(if (i == page) 28.dp else 10.dp, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "dot")
                        Box(
                            Modifier.padding(horizontal = 3.dp).height(10.dp).width(w).clip(CircleShape)
                                .background(if (i == page) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                        )
                    }
                }
                if (page < PAGES - 1) {
                    Button({ scope.launch { pager.animateScrollToPage(page + 1) } }, enabled = canNext, shapes = ButtonDefaults.shapes()) {
                        Text("Next"); Spacer(Modifier.width(6.dp)); Icon(Icons.Rounded.ArrowForward, null, Modifier.size(18.dp))
                    }
                } else {
                    Button({ app.profile.save(form.build().copy(onboarded = true)) }, enabled = form.name.isNotBlank(), shapes = ButtonDefaults.shapes()) {
                        Text("Start")
                    }
                }
            }
        },
    ) { pad ->
        HorizontalPager(pager, Modifier.fillMaxSize().padding(pad), userScrollEnabled = canNext) { p ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                when (p) {
                    0 -> Welcome { app.profile.save(form.build().copy(onboarded = false)); demo = true }
                    1 -> Features()
                    2 -> Look(app)
                    3 -> ProfilePage(app, form)
                    else -> Permissions()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Welcome(onDemo: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Box(contentAlignment = Alignment.Center) {
        SpinningShape(MaterialShapes.Sunny.toShape(), 300.dp, c.secondaryContainer, 40)
        SpinningShape(MaterialShapes.Cookie12Sided.toShape(), 210.dp, c.primaryContainer, 28) {
            Icon(painterResource(R.drawable.ic_pill), null, Modifier.size(120.dp), tint = c.onPrimaryContainer)
        }
    }
    Spacer(Modifier.height(32.dp))
    Text("Doseful", style = MaterialTheme.typography.displayMedium)
    Spacer(Modifier.height(8.dp))
    Text("Your medicines, on time.\nPrivate by design.", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
    Spacer(Modifier.height(24.dp))
    FilledTonalButton(onDemo, shapes = ButtonDefaults.shapes(), contentPadding = ButtonDefaults.MediumContentPadding) {
        Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("Try the demo")
    }
    Text("Sample data, nothing is saved.", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Features() {
    Text("Built around you", style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
    Spacer(Modifier.height(24.dp))
    val items = listOf(
        Triple(Icons.Rounded.AlarmOn, "Reminders that arrive", "Exact alarms with Taken and Skip right in the notification."),
        Triple(Icons.Rounded.WifiOff, "Fully offline", "No account, no cloud, no internet permission at all."),
        Triple(Icons.Rounded.Inventory2, "Stock and refills", "Know when a pack is running low before it runs out."),
        Triple(Icons.Rounded.Insights, "Adherence at a glance", "Streaks and history so you can see how you are doing."),
    )
    val shapes = listOf(MaterialShapes.Cookie6Sided, MaterialShapes.Clover4Leaf, MaterialShapes.Flower, MaterialShapes.Pentagon)
    items.forEachIndexed { i, (icon, title, text) ->
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            SpinningShape(shapes[i].toShape(), 64.dp, MaterialTheme.colorScheme.tertiaryContainer, 30 + i * 6) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Look(app: AppEnv) {
    Text("Make it yours", style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
    Spacer(Modifier.height(8.dp))
    Text("Changes apply right away. You can change this later in Profile.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(24.dp))
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Theme", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            AppearanceSection(app)
        }
    }
}

@Composable
private fun ProfilePage(app: AppEnv, form: ProfileForm) {
    Text("About you", style = MaterialTheme.typography.displaySmall)
    Spacer(Modifier.height(4.dp))
    Text("Stored only on this device.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(20.dp))
    AvatarPicker(app, 140.dp)
    if (app.profile.avatarVersion >= 0 && app.profile.avatar() != null) TextButton({ app.profile.setAvatar(null) }) { Text("Remove photo") }
    Spacer(Modifier.height(16.dp))
    SectionCard("Details") { ProfileFields(form, showEmail = false) }
}

@Composable
private fun Permissions() {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val notifOk = remember(tick) { Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED }
    val exactOk = remember(tick) { Alarms.canExact(ctx) }
    val bgOk = remember(tick) { Alarms.backgroundAllowed(ctx) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }

    Text("Almost there", style = MaterialTheme.typography.displaySmall)
    Spacer(Modifier.height(8.dp))
    Text("Three permissions make reminders reliable.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(24.dp))
    PermissionCard(Icons.Rounded.Notifications, "Notifications", "Show dose and refill reminders.", notifOk) {
        if (Build.VERSION.SDK_INT >= 33) ask.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    Spacer(Modifier.height(12.dp))
    PermissionCard(Icons.Rounded.Alarm, "Exact alarms", "Fire reminders at the exact minute, even in battery saver.", exactOk) {
        ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}")))
    }
    Spacer(Modifier.height(12.dp))
    PermissionCard(Icons.Rounded.BatterySaver, "Run in background", "Stops battery savers from delaying or killing your reminders.", bgOk) {
        Alarms.requestBackground(ctx)
    }
}

@Composable
private fun PermissionCard(icon: ImageVector, title: String, text: String, granted: Boolean, onAllow: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(if (granted) c.primaryContainer else c.surfaceContainerHigh)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(32.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
            if (granted) Icon(Icons.Rounded.CheckCircle, "Allowed", tint = c.primary)
            else FilledTonalButton(onAllow, shapes = ButtonDefaults.shapes()) { Text("Allow") }
        }
    }
}
