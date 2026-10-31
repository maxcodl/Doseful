package app.healthre.ui

import android.graphics.Color.TRANSPARENT
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import app.healthre.AppEnv
import app.healthre.R

// Nunito (OFL, bundled): a free rounded typeface. Heavy weights are used for big numbers.
private val Nunito = FontFamily(
    listOf(400, 500, 600, 700, 800, 900).map {
        Font(R.font.nunito, FontWeight(it), variationSettings = FontVariation.Settings(FontVariation.weight(it)))
    },
)

private fun Typography.withFont(f: FontFamily) = copy(
    displayLarge = displayLarge.copy(fontFamily = f), displayMedium = displayMedium.copy(fontFamily = f),
    displaySmall = displaySmall.copy(fontFamily = f), headlineLarge = headlineLarge.copy(fontFamily = f),
    headlineMedium = headlineMedium.copy(fontFamily = f), headlineSmall = headlineSmall.copy(fontFamily = f),
    titleLarge = titleLarge.copy(fontFamily = f), titleMedium = titleMedium.copy(fontFamily = f),
    titleSmall = titleSmall.copy(fontFamily = f), bodyLarge = bodyLarge.copy(fontFamily = f),
    bodyMedium = bodyMedium.copy(fontFamily = f), bodySmall = bodySmall.copy(fontFamily = f),
    labelLarge = labelLarge.copy(fontFamily = f, fontWeight = FontWeight.SemiBold),
    labelMedium = labelMedium.copy(fontFamily = f), labelSmall = labelSmall.copy(fontFamily = f),
)

/** True-black surfaces for OLED panels; containers step up slightly so cards stay distinguishable. */
private fun ColorScheme.amoled() = copy(
    background = Color.Black, surface = Color.Black, surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black, surfaceContainerLow = Color(0xFF101010),
    surfaceContainer = Color(0xFF1B1B1B), surfaceContainerHigh = Color(0xFF232323),
    surfaceContainerHighest = Color(0xFF2C2C2C), surfaceBright = Color(0xFF343434),
)

/** 1.0 = Material default corner sizes. Changed by the "Corner roundness" setting. */
val LocalCornerScale = androidx.compose.runtime.compositionLocalOf { 1f }

/** A rounded shape whose size follows the app-wide corner roundness setting. Use instead of a fixed RoundedCornerShape. */
@Composable
@androidx.compose.runtime.ReadOnlyComposable
fun corner(dp: Int) = androidx.compose.foundation.shape.RoundedCornerShape((dp * LocalCornerScale.current).dp)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HealthReTheme(app: AppEnv, content: @Composable () -> Unit) {
    val store = app.theme
    val dark = when (store.themeMode) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    val ctx = LocalContext.current
    val base = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    val scheme = if (dark && store.amoled) base.amoled() else base

    // Keep system bar icon colours in step with the in-app theme, not just the OS theme.
    val act = ctx as ComponentActivity
    DisposableEffect(dark) {
        val style = SystemBarStyle.auto(TRANSPARENT, TRANSPARENT) { dark }
        act.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        if (Build.VERSION.SDK_INT >= 29) act.window.isNavigationBarContrastEnforced = false
        onDispose {}
    }

    val k = store.uiRadius / 100f
    val shapes = Shapes(
        extraSmall = androidx.compose.foundation.shape.RoundedCornerShape((4 * k).dp),
        small = androidx.compose.foundation.shape.RoundedCornerShape((8 * k).dp),
        medium = androidx.compose.foundation.shape.RoundedCornerShape((12 * k).dp),
        large = androidx.compose.foundation.shape.RoundedCornerShape((16 * k).dp),
        extraLarge = androidx.compose.foundation.shape.RoundedCornerShape((28 * k).dp),
    )
    androidx.compose.runtime.CompositionLocalProvider(LocalCornerScale provides k, LocalAnimScale provides store.animScale) {
        MaterialExpressiveTheme(
            colorScheme = scheme,
            motionScheme = MotionScheme.expressive(),
            typography = Typography().withFont(Nunito),
            shapes = shapes,
            content = content,
        )
    }
}
