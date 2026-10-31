package app.healthre

import android.app.KeyguardManager
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import app.healthre.ui.HealthReTheme
import app.healthre.ui.LockScreen
import app.healthre.ui.Root
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var unlocked by mutableStateOf(false)
    private var stoppedAt = 0L

    private val credential = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) unlocked = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as HealthReApp
        lifecycleScope.launch(Dispatchers.IO) { Alarms.rescheduleAll(app, app.db.dao()) }
        setContent {
            HealthReTheme(app) {
                if (app.theme.appLock && !unlocked) LockScreen(onUnlock = ::askForScreenLock) else Root(app)
            }
        }
    }

    /** Uses the phone's own PIN, pattern or biometric prompt; Doseful stores no password. */
    private fun askForScreenLock() {
        val km = getSystemService(KeyguardManager::class.java)
        val intent = if (km.isDeviceSecure) km.createConfirmDeviceCredentialIntent("Unlock Doseful", null) else null
        if (intent == null) unlocked = true else credential.launch(intent)
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        // Lock again after 30 seconds in the background.
        if (stoppedAt != 0L && SystemClock.elapsedRealtime() - stoppedAt > 30_000) unlocked = false
    }
}
