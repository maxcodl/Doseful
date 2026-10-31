package dev.maxpayne.doseful.scanner

import android.app.Activity
import android.content.Intent
import android.net.Uri
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
    private val camera = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) recognize() else done(null)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            photoFile().apply { delete(); createNewFile() }
            camera.launch(photoUri())
        }
    }

    // Looked up on demand (no lazy state), so it still works if the activity was recreated while the camera was open.
    private fun photoFile() = File(cacheDir, "scan.jpg")
    private fun photoUri(): Uri = FileProvider.getUriForFile(this, "$packageName.files", photoFile())

    private fun recognize() {
        try {
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                .process(InputImage.fromFilePath(this, photoUri()))
                .addOnSuccessListener { done(it.text) }
                .addOnFailureListener { done(null) }
        } catch (e: Exception) {
            done(null)
        }
    }

    private fun done(text: String?) {
        photoFile().delete()
        if (text == null) setResult(Activity.RESULT_CANCELED)
        else setResult(Activity.RESULT_OK, Intent().putExtra("text", text))
        finish()
    }
}
