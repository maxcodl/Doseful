package app.healthre.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Shown while the app lock is on and the phone's screen lock has not been confirmed. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LockScreen(onUnlock: () -> Unit) {
    val c = MaterialTheme.colorScheme
    LaunchedEffect(Unit) { onUnlock() }
    Surface(Modifier.fillMaxSize(), color = c.background) {
        Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            SpinningShape(MaterialShapes.Cookie12Sided.toShape(), 160.dp, c.primaryContainer, 30) {
                Icon(Icons.Rounded.Lock, null, Modifier.size(64.dp), tint = c.onPrimaryContainer)
            }
            Spacer(Modifier.height(24.dp))
            Text("Doseful is locked", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(24.dp))
            Button(onUnlock, shapes = ButtonDefaults.shapes()) { Text("Unlock") }
        }
    }
}
