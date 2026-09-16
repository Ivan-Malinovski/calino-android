package calino.malinov.ski.state

import android.app.AlarmManager
import android.content.Context
import android.content.res.Configuration
import android.icu.util.Calendar
import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.app.NotificationManagerCompat
import androidx.core.text.util.LocalePreferences
import calino.malinov.ski.util.CalinoTimeFormat
import calino.malinov.ski.util.CalinoWeekStart
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The orientation reported by the current Android window configuration. */
enum class CalinoOrientation {
    Portrait,
    Landscape,
    Square,
    Unknown,
}

/**
 * Facts Android can provide without location permission or account access.
 *
 * This is intentionally separate from [CalinoPreferences]. A device fact is a
 * default or capability, not a user choice. Keeping both in one object would
 * make a later Android preference change look like a user edit and would also
 * make it impossible to add an explicit override cleanly.
 */
@Immutable
data class CalinoDeviceDefaults(
    /** The first, effective app locale after Android's locale resolution. */
    val locale: Locale,
    /** All preferred locales, in Android's priority order. */
    val locales: List<Locale>,
    val languageTag: String,
    val region: String?,
    /** Java-compatible pattern generated from the locale's yMd skeleton. */
    val shortDatePattern: String,
    /** The locale's date-field order, for a future date-format selector. */
    val dateFormatOrder: String,
    /** The Android clock setting, resolved to one of Calino's two formats. */
    val timeFormat: CalinoTimeFormat,
    /** The closest currently supported Calino week-start choice. */
    val weekStart: CalinoWeekStart,
    /** The raw CLDR answer, retained for when Calino supports more starts. */
    val firstDayOfWeek: DayOfWeek,
    /** CLDR weekend transition data, including partial-day transition times. */
    val weekendOnset: DayOfWeek,
    val weekendOnsetMillis: Int,
    val weekendCease: DayOfWeek,
    val weekendCeaseMillis: Int,
    val timeZone: ZoneId,
    val isDarkMode: Boolean,
    val fontScale: Float,
    val densityDpi: Int,
    val windowWidthDp: Int,
    val windowHeightDp: Int,
    val smallestWidthDp: Int,
    val orientation: CalinoOrientation,
    val touchExplorationEnabled: Boolean,
    val notificationsEnabled: Boolean,
    val exactAlarmsAllowed: Boolean,
) {
    /** A stable display label for a future language picker. */
    val languageDisplayName: String
        get() = locale.getDisplayName(locale)

    /** A compact display label suitable for a future regional setting row. */
    val regionDisplayName: String?
        get() = region?.takeIf { it.isNotBlank() }?.let { locale.getDisplayCountry(locale) }

    /** Formats a date with Android's locale-generated pattern for future UI. */
    fun formatDate(date: LocalDate): String = runCatching {
        date.format(DateTimeFormatter.ofPattern(shortDatePattern, locale))
    }.getOrDefault(date.toString())

    companion object {
        /** Useful for previews and the composition-local fallback. */
        val Fallback = CalinoDeviceDefaults(
            locale = Locale.US,
            locales = listOf(Locale.US),
            languageTag = Locale.US.toLanguageTag(),
            region = Locale.US.country,
            shortDatePattern = "M/d/y",
            dateFormatOrder = "MDY",
            timeFormat = CalinoTimeFormat.TwelveHour,
            weekStart = CalinoWeekStart.Monday,
            firstDayOfWeek = DayOfWeek.MONDAY,
            weekendOnset = DayOfWeek.SATURDAY,
            weekendOnsetMillis = 0,
            weekendCease = DayOfWeek.SUNDAY,
            weekendCeaseMillis = 0,
            timeZone = ZoneId.of("UTC"),
            isDarkMode = false,
            fontScale = 1f,
            densityDpi = 160,
            windowWidthDp = 0,
            windowHeightDp = 0,
            smallestWidthDp = 0,
            orientation = CalinoOrientation.Unknown,
            touchExplorationEnabled = false,
            notificationsEnabled = true,
            exactAlarmsAllowed = true,
        )

        /** Reads the current device facts. Safe to call from a widget process. */
        fun from(context: Context): CalinoDeviceDefaults {
            val appContext = context.applicationContext
            val configuration = appContext.resources.configuration
            val localeList = (0 until configuration.locales.size())
                .map { configuration.locales[it] }
                .ifEmpty { listOf(Locale.getDefault()) }
            val locale = localeList.first()
            // Resource configuration describes the app's language. Android's
            // FORMAT locale is the device/user regional preference and may
            // intentionally differ (for example English UI with Danish date,
            // clock, and week conventions).
            val formatLocale = Locale.getDefault(Locale.Category.FORMAT)
            val weekData = Calendar.getInstance(formatLocale).weekData
            val firstDayOfWeek = localeFirstDayOfWeek(formatLocale)
            val alarmManager = appContext.getSystemService(AlarmManager::class.java)
            val exactAlarmsAllowed = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                runCatching { alarmManager?.canScheduleExactAlarms() ?: false }.getOrDefault(false)
            } else {
                true
            }
            val accessibility = appContext.getSystemService(android.view.accessibility.AccessibilityManager::class.java)

            return CalinoDeviceDefaults(
                locale = locale,
                locales = localeList,
                languageTag = locale.toLanguageTag(),
                region = formatLocale.country.takeIf { it.isNotBlank() },
                shortDatePattern = DateFormat.getBestDateTimePattern(formatLocale, "yMd"),
                dateFormatOrder = DateFormat.getDateFormatOrder(appContext)
                    .joinToString("") { it.uppercaseChar().toString() },
                timeFormat = systemTimeFormat(appContext, formatLocale),
                weekStart = calinoWeekStartFor(firstDayOfWeek),
                firstDayOfWeek = firstDayOfWeek,
                weekendOnset = icuDayOfWeek(weekData.weekendOnset),
                weekendOnsetMillis = weekData.weekendOnsetMillis,
                weekendCease = icuDayOfWeek(weekData.weekendCease),
                weekendCeaseMillis = weekData.weekendCeaseMillis,
                timeZone = ZoneId.systemDefault(),
                isDarkMode = configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES,
                fontScale = configuration.fontScale,
                densityDpi = configuration.densityDpi,
                windowWidthDp = configuration.screenWidthDp,
                windowHeightDp = configuration.screenHeightDp,
                smallestWidthDp = configuration.smallestScreenWidthDp,
                orientation = when (configuration.orientation) {
                    Configuration.ORIENTATION_PORTRAIT -> CalinoOrientation.Portrait
                    Configuration.ORIENTATION_LANDSCAPE -> CalinoOrientation.Landscape
                    Configuration.ORIENTATION_SQUARE -> CalinoOrientation.Square
                    else -> CalinoOrientation.Unknown
                },
                touchExplorationEnabled = accessibility?.isTouchExplorationEnabled == true,
                notificationsEnabled = NotificationManagerCompat.from(appContext).areNotificationsEnabled(),
                exactAlarmsAllowed = exactAlarmsAllowed,
            )
        }
    }
}

/** Maps Android/CLDR's Sunday..Saturday constants to java.time's Monday..Sunday. */
private fun icuDayOfWeek(day: Int): DayOfWeek = when (day) {
    Calendar.SUNDAY -> DayOfWeek.SUNDAY
    Calendar.MONDAY -> DayOfWeek.MONDAY
    Calendar.TUESDAY -> DayOfWeek.TUESDAY
    Calendar.WEDNESDAY -> DayOfWeek.WEDNESDAY
    Calendar.THURSDAY -> DayOfWeek.THURSDAY
    Calendar.FRIDAY -> DayOfWeek.FRIDAY
    Calendar.SATURDAY -> DayOfWeek.SATURDAY
    else -> DayOfWeek.MONDAY
}

/** Resolves Android's `fw` regional override before falling back to CLDR. */
internal fun localeFirstDayOfWeek(locale: Locale): DayOfWeek =
    when (LocalePreferences.getFirstDayOfWeek(locale)) {
        LocalePreferences.FirstDayOfWeek.MONDAY -> DayOfWeek.MONDAY
        LocalePreferences.FirstDayOfWeek.TUESDAY -> DayOfWeek.TUESDAY
        LocalePreferences.FirstDayOfWeek.WEDNESDAY -> DayOfWeek.WEDNESDAY
        LocalePreferences.FirstDayOfWeek.THURSDAY -> DayOfWeek.THURSDAY
        LocalePreferences.FirstDayOfWeek.FRIDAY -> DayOfWeek.FRIDAY
        LocalePreferences.FirstDayOfWeek.SATURDAY -> DayOfWeek.SATURDAY
        LocalePreferences.FirstDayOfWeek.SUNDAY -> DayOfWeek.SUNDAY
        else -> icuDayOfWeek(Calendar.getInstance(locale).weekData.firstDayOfWeek)
    }

/**
 * The explicit per-user clock choice wins. If Android stores "locale default",
 * resolve the FORMAT locale's hour-cycle extension/CLDR default rather than the
 * app resource locale.
 */
internal fun timeFormatFor(setting: String?, formatLocale: Locale): CalinoTimeFormat = when (setting) {
    "24" -> CalinoTimeFormat.TwentyFourHour
    "12" -> CalinoTimeFormat.TwelveHour
    else -> when (LocalePreferences.getHourCycle(formatLocale)) {
        LocalePreferences.HourCycle.H23,
        LocalePreferences.HourCycle.H24,
        -> CalinoTimeFormat.TwentyFourHour
        else -> CalinoTimeFormat.TwelveHour
    }
}

private fun systemTimeFormat(context: Context, formatLocale: Locale): CalinoTimeFormat =
    timeFormatFor(
        Settings.System.getString(context.contentResolver, Settings.System.TIME_12_24),
        formatLocale,
    )

/** Only Monday and Sunday are user-selectable today; preserve the raw answer above. */
fun calinoWeekStartFor(day: DayOfWeek): CalinoWeekStart =
    if (day == DayOfWeek.SUNDAY) CalinoWeekStart.Sunday else CalinoWeekStart.Monday

val LocalCalinoDeviceDefaults = staticCompositionLocalOf { CalinoDeviceDefaults.Fallback }

/**
 * Reads device facts again when the app returns to the foreground. This matters
 * for the Android 12/24-hour clock toggle, which need not recreate the Activity.
 */
@Composable
fun rememberCalinoDeviceDefaults(): CalinoDeviceDefaults {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var refreshNonce by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshNonce++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return remember(context, configuration, refreshNonce) {
        CalinoDeviceDefaults.from(context)
    }
}
