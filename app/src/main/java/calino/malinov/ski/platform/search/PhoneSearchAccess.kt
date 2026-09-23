package calino.malinov.ski.platform.search

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Whether the phone's search (Samsung Finder and similar) may search Calino.
 *
 * Like [calino.malinov.ski.platform.assistant.AssistantAccess], the switch is
 * component state: the suggestion provider ships disabled, and a disabled
 * provider cannot be queried, so nothing leaves Calino while this is off.
 *
 * Calino itself is always listed as searchable (the `PhoneSearchAlias`
 * declaration). SearchManager rebuilds its list only when a whole package
 * changes, never for a single component, so a toggled alias would leave
 * Finder's list stale until the next update or reboot.
 */
object PhoneSearchAccess {

    private const val Provider = "calino.malinov.ski.platform.search.PhoneSearchProvider"

    private fun component(context: Context) = ComponentName(context.packageName, Provider)

    fun isEnabled(context: Context): Boolean =
        context.packageManager.getComponentEnabledSetting(component(context)) ==
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED

    fun setEnabled(context: Context, enabled: Boolean) {
        context.packageManager.setComponentEnabledSetting(
            component(context),
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
            PackageManager.DONT_KILL_APP,
        )
    }
}
