package calino.malinov.ski.ui.components

import android.app.Activity
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalView
import kotlin.math.roundToInt
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.offset
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Hands the keyboard inset to `imePadding` alone while [active].
 *
 * The activity leaves `windowSoftInputMode` unspecified, so when the keyboard
 * starts to appear over a field that sits at the very bottom of the screen --
 * the docked shelf's -- the system also pans the whole window to reveal it.
 * The pan is computed before `imePadding` has lifted the shelf and is not
 * taken back afterwards, so the shelf was lifted twice and ended up at the top
 * of the screen until the window was laid out again. Every other screen keeps
 * the default, which is why this is scoped to the shelf being typed into.
 */
@Composable
fun ImeResizeWhile(active: Boolean) {
    var context = LocalContext.current
    while (context is ContextWrapper && context !is Activity) context = context.baseContext
    val window = (context as? Activity)?.window
    DisposableEffect(active, window) {
        if (!active || window == null) return@DisposableEffect onDispose {}
        // Window.setSoftInputMode ignores an unspecified mode, so neither the
        // switch nor the way back goes through it.
        fun apply(mode: Int) {
            val attrs = window.attributes
            attrs.softInputMode = mode
            window.attributes = attrs
        }
        val previous = window.attributes.softInputMode
        apply((previous and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST.inv()) or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        onDispose { apply(previous) }
    }
}

/**
 * Ends the content exactly at the keyboard's top edge.
 *
 * `imePadding` takes the whole keyboard height off the bottom, but the keyboard
 * is measured from the *window's* bottom and the host card does not end there
 * (a bottom sheet floats a few dozen dp above it), so it took off more than the
 * keyboard actually covers and left a dead band above the keyboard. This takes
 * off only the overlap between this slot and the keyboard, read from the slot's
 * own position, so it is exact for whatever card hosts it. An action pill that
 * rides above the keyboard floats over the content; the content's own tail
 * spacer lets the last row scroll clear of it.
 */
@Composable
fun Modifier.imeOverlapPadding(): Modifier {
    val ime = WindowInsets.ime
    val density = LocalDensity.current
    val rootView = LocalView.current.rootView
    var slotBottom by remember { mutableFloatStateOf(Float.NaN) }
    return onGloballyPositioned { slotBottom = it.positionInWindow().y + it.size.height }
        .layout { measurable, constraints ->
            val imeBottom = ime.getBottom(density)
            val overlap = when {
                imeBottom <= 0 -> 0
                // Not positioned yet: assume the slot reaches the window's bottom.
                slotBottom.isNaN() -> imeBottom
                else -> (slotBottom - (rootView.height - imeBottom)).roundToInt().coerceIn(0, imeBottom)
            }
            val placeable = measurable.measure(constraints.offset(vertical = -overlap))
            layout(
                placeable.width,
                (placeable.height + overlap).coerceIn(constraints.minHeight, constraints.maxHeight),
            ) { placeable.place(0, 0) }
        }
}
