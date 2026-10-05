package calino.malinov.ski.util

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The effective resource locale, including Android's per-app language choice. */
val LocalCalinoLocale: Locale
    @Composable get() = LocalConfiguration.current.locales[0]

/** Recreate display formatters when the resource configuration changes. */
@Composable
fun localizedDateFormatter(pattern: String): DateTimeFormatter {
    val locale = LocalCalinoLocale
    return remember(pattern, locale) { localizedDisplayFormatter(pattern, locale) }
}

/** Android boundary for receivers and Glance as well as Compose. */
fun localizedDisplayFormatter(pattern: String, locale: Locale): DateTimeFormatter {
    // Keep the established English layout; use CLDR order/punctuation for translations.
    val displayPattern = if (locale.language in setOf("da", "de") && '\'' !in pattern) {
        DateFormat.getBestDateTimePattern(locale, pattern.filter(Char::isLetter))
    } else pattern
    return DateTimeFormatter.ofPattern(displayPattern, locale)
}
