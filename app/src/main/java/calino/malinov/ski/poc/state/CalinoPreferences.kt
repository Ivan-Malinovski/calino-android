package calino.malinov.ski.poc.state

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import calino.malinov.ski.poc.util.CalinoTimeFormat

/**
 * The user's display preferences, and the callbacks that change them.
 *
 * Settings rows used to hold their own `rememberSaveable` state, so choosing a
 * clock moved a segmented control and nothing else. Reading the preference
 * from one composition local means every surface that writes a time --
 * agenda, day rail, editor, event detail -- follows the choice.
 */
@Immutable
data class CalinoPreferences(
    val timeFormat: CalinoTimeFormat = CalinoTimeFormat.Default,
    val setTimeFormat: (CalinoTimeFormat) -> Unit = {},
)

val LocalCalinoPreferences = staticCompositionLocalOf { CalinoPreferences() }

/** Shorthand for the common case: the clock the app is currently set to. */
val LocalTimeFormat: CalinoTimeFormat
    @Composable get() = LocalCalinoPreferences.current.timeFormat

/**
 * Preference storage. Backed by [android.content.SharedPreferences] in the
 * app; the in-memory default keeps unit tests and previews off disk.
 */
interface CalinoPreferenceStore {
    fun loadTimeFormat(): CalinoTimeFormat
    fun saveTimeFormat(format: CalinoTimeFormat)

    object InMemory : CalinoPreferenceStore {
        private var value = CalinoTimeFormat.Default
        override fun loadTimeFormat() = value
        override fun saveTimeFormat(format: CalinoTimeFormat) { value = format }
    }
}

class SharedPreferencesPreferenceStore(context: Context) : CalinoPreferenceStore {

    private val prefs = context.applicationContext
        .getSharedPreferences("calino_preferences", Context.MODE_PRIVATE)

    override fun loadTimeFormat(): CalinoTimeFormat =
        CalinoTimeFormat.fromName(prefs.getString(TimeFormatKey, null))

    override fun saveTimeFormat(format: CalinoTimeFormat) {
        prefs.edit().putString(TimeFormatKey, format.name).apply()
    }

    private companion object { const val TimeFormatKey = "time_format" }
}

/**
 * Reads the stored preferences into Compose state and writes changes straight
 * back, so a choice survives both recomposition and a restart.
 */
@Composable
fun rememberCalinoPreferences(store: CalinoPreferenceStore): CalinoPreferences {
    var timeFormat by androidx.compose.runtime.remember(store) {
        mutableStateOf(store.loadTimeFormat())
    }
    return CalinoPreferences(
        timeFormat = timeFormat,
        setTimeFormat = { format ->
            timeFormat = format
            store.saveTimeFormat(format)
        },
    )
}
