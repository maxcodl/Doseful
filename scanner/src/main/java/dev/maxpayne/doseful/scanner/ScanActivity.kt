package dev.maxpayne.doseful.scanner

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File

/**
 * Takes a photo, runs on-device OCR, and returns the raw text to the caller as extra "text".
 * No network access: this app has no INTERNET permission.
 */
class ScanActivity : ComponentActivity() {
    private val file by lazy { File(cacheDir, "scan.jpg").also { it.createNewFile() } }
    private val uri by lazy { FileProvider.getUriForFile(this, "$packageName.files", file) }

    private val camera = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (!ok) return@registerForActivityResult done(null)
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            .process(InputImage.fromFilePath(this, uri))
            .addOnSuccessListener { done(it.text) }
            .addOnFailureListener { done(null) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) camera.launch(uri)
    }

    private fun done(text: String?) {
        file.delete()
        if (text == null) setResult(Activity.RESULT_CANCELED)
        else setResult(Activity.RESULT_OK, Intent().putExtra("text", text))
        finish()
    }
}
