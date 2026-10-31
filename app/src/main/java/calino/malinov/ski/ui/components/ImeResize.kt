package calino.malinov.ski.ui.components

import android.app.Activity
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
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
 * Ends the content at the keyboard, plus [clearance] for an action pill that
 * has risen above it.
 *
 * The pill floats over the bottom of whatever it is lifted above, so a field
 * that is only scrolled to the keyboard's edge sits right under it. The extra
 * room is derived per frame from the keyboard's own inset, capped at
 * [clearance], so it opens and closes with the keyboard animation and is zero
 * while no keyboard is showing (the content's own tail spacer covers that).
 */
@Composable
fun Modifier.imePaddingAbovePill(clearance: Dp): Modifier {
    val ime = WindowInsets.ime
    val density = LocalDensity.current
    return imePadding().layout { measurable, constraints ->
        val reserve = minOf(ime.getBottom(density), clearance.roundToPx())
        val placeable = measurable.measure(constraints.offset(vertical = -reserve))
        layout(placeable.width, placeable.height + reserve) { placeable.place(0, 0) }
    }
}
