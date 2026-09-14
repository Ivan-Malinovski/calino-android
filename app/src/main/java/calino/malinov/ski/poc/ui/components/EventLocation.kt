package calino.malinov.ski.poc.ui.components

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import calino.malinov.ski.poc.design.CalinoColors
import calino.malinov.ski.poc.design.CalinoMotion

/**
 * Builds the standard geo query used by Android map applications.
 *
 * Returning null for blank input keeps the eligibility rule in one place and
 * prevents a card with an empty LOCATION property from exposing a dead action.
 */
internal fun eventLocationIntent(location: String?): Intent? {
    val uri = eventLocationUri(location) ?: return null
    return Intent(
        Intent.ACTION_VIEW,
        Uri.parse(uri),
    )
}

/** The encoded URI string is kept separate so the eligibility and encoding rules are testable on the JVM. */
internal fun eventLocationUri(location: String?): String? {
    val normalized = location?.trim()?.takeIf(String::isNotEmpty) ?: return null
    return "geo:0,0?q=${percentEncodeGeoQuery(normalized)}"
}

private fun percentEncodeGeoQuery(value: String): String = buildString {
    value.toByteArray(Charsets.UTF_8).forEach { byte ->
        val unsigned = byte.toInt() and 0xff
        val unreserved = unsigned in 'A'.code..'Z'.code ||
            unsigned in 'a'.code..'z'.code ||
            unsigned in '0'.code..'9'.code ||
            unsigned == '-'.code || unsigned == '.'.code ||
            unsigned == '_'.code || unsigned == '~'.code
        if (unreserved) {
            append(unsigned.toChar())
        } else {
            append('%')
            append("0123456789ABCDEF"[unsigned ushr 4])
            append("0123456789ABCDEF"[unsigned and 0x0f])
        }
    }
}

/** Starts a user-selectable map handler, or safely does nothing if none exists. */
internal fun openEventLocation(context: Context, location: String?): Boolean {
    val viewIntent = eventLocationIntent(location) ?: return false
    return try {
        val chooser = Intent.createChooser(viewIntent, "Open location in maps")
        if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}

/**
 * The map action for full event cards. The painted glyph is compact, but the
 * control keeps a 44dp lane. Its visibility is animated so a location edited
 * in the detail card does not pop the trailing control in or out.
 */
@Composable
fun EventLocationButton(
    location: String?,
    modifier: Modifier = Modifier,
    onOpen: ((String) -> Unit)? = null,
) {
    val normalized = location?.trim().orEmpty()
    val context = LocalContext.current
    AnimatedVisibility(
        visible = normalized.isNotEmpty(),
        modifier = modifier,
        enter = fadeIn(tween(CalinoMotion.ContentEnterMillis)) +
            scaleIn(tween(CalinoMotion.ContentEnterMillis), initialScale = .8f),
        exit = fadeOut(tween(CalinoMotion.ContentExitMillis)) +
            scaleOut(tween(CalinoMotion.ContentExitMillis), targetScale = .8f),
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .calinoPressable(onClick = {
                    if (onOpen != null) onOpen(normalized)
                    else openEventLocation(context, normalized)
                })
                .semantics { contentDescription = "Open event location in maps" },
            contentAlignment = Alignment.Center,
        ) {
            CalinoIcon(
                CalinoIcon.Pin,
                tint = CalinoColors.Accent,
                modifier = Modifier.size(19.dp),
                contentDescription = null,
            )
        }
    }
}
