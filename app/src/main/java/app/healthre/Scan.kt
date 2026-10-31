package app.healthre

import android.content.Context
import android.content.Intent

data class ScanResult(
    val name: String = "", val strength: String = "", val form: String = "",
    val expiry: String = "", val batch: String = "", val manufacturer: String = "",
)

/**
 * Optional package scanner. OCR lives in a separate add-on APK so Doseful itself stays tiny and
 * has no network permission. The add-on returns raw recognised text; parsing happens here.
 */
/** Releases page: used for the scanner add-on download and for "Check for updates". Opened in the browser. */
const val RELEASES_URL = "https://github.com/maxcodl/Doseful/releases/latest"

object Scanner {
    const val ACTION = "dev.maxpayne.doseful.action.SCAN"
    const val EXTRA_TEXT = "text"

    /** Where users get the add-on. Opened in the browser, so Doseful needs no INTERNET permission. */
    const val DOWNLOAD_URL = RELEASES_URL

    fun intent(): Intent = Intent(ACTION)

    fun installed(ctx: Context) = ctx.packageManager.resolveActivity(intent(), 0) != null

    private val strengthRx = Regex("""\d+(?:[.,]\d+)?\s?(?:mg|mcg|µg|g|ml|iu|%)\b""", RegexOption.IGNORE_CASE)
    private val expiryRx = Regex("""(?:exp(?:iry)?\.?(?:\s*date)?|use before|best before)\s*[:.]?\s*(\d{1,2}\s?[/.-]\s?\d{2,4}|[A-Za-z]{3,9}\.?\s?\d{2,4})""", RegexOption.IGNORE_CASE)
    private val batchRx = Regex("""(?:batch(?:\s*no\.?)?|b\.?\s?no\.?|lot)\s*[:.#]?\s*([A-Z0-9-]{3,})""", RegexOption.IGNORE_CASE)
    private val mfgRx = Regex("""(?:mfd?\.?\s*by|manufactured by|marketed by)\s*[:.]?\s*(.+)""", RegexOption.IGNORE_CASE)
    private val forms = listOf("tablet", "capsule", "syrup", "suspension", "injection", "cream", "ointment", "drops", "inhaler", "gel", "powder", "lozenge")
    private val skip = Regex("""exp|mfg|mfd|batch|lot|price|mrp|each|contains|store|keep|warning|dosage|manufactured|marketed|rx\b""", RegexOption.IGNORE_CASE)

    /** Best-effort; the user always reviews the fields. */
    fun parse(text: String): ScanResult {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val lower = text.lowercase()
        val name = lines.firstOrNull { l -> l.count { it.isLetter() } >= 3 && !skip.containsMatchIn(l) && l.length <= 40 }
            ?.replace(strengthRx, "")?.trim(' ', '-', ',', '.').orEmpty()
        return ScanResult(
            name = name,
            strength = strengthRx.find(text)?.value.orEmpty().replace(',', '.'),
            form = forms.firstOrNull { lower.contains(it) }?.replaceFirstChar { it.uppercase() }.orEmpty(),
            expiry = expiryRx.find(text)?.groupValues?.get(1).orEmpty().trim(),
            batch = batchRx.find(text)?.groupValues?.get(1).orEmpty(),
            manufacturer = mfgRx.find(text)?.groupValues?.get(1).orEmpty().trim().take(60),
        )
    }
}
