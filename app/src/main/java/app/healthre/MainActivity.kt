package app.healthre

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import app.healthre.ui.HealthReTheme
import app.healthre.ui.Root
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as HealthReApp
        lifecycleScope.launch(Dispatchers.IO) { Alarms.rescheduleAll(app, app.db.dao()) }
        setContent { HealthReTheme(app) { Root(app) } }
    }
}
