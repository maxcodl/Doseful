package app.healthre.ui

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Multiplies every animation duration. 1 = normal, higher = slower, 0 = instant. Set in Settings > Theme. */
val LocalAnimScale = compositionLocalOf { 1.5f }

/** A smooth tween whose duration and delay follow the animation speed setting. */
fun <T> tw(ms: Int, scale: Float, delayMs: Int = 0, easing: Easing = EaseInOutCubic): TweenSpec<T> =
    tween((ms * scale).toInt(), (delayMs * scale).toInt(), easing)

/** Fades and slides an item up when it first appears. Items with a higher [index] start slightly later, so lists cascade in. */
@Composable
fun Modifier.entrance(index: Int): Modifier {
    val scale = LocalAnimScale.current
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay((index.coerceAtMost(8) * 70 * scale).toLong())
        shown = true
    }
    val a by animateFloatAsState(if (shown) 1f else 0f, tw(650, scale, easing = EaseOutCubic), label = "entrance")
    return this.graphicsLayer {
        alpha = a
        translationY = (1f - a) * 36.dp.toPx()
    }
}
