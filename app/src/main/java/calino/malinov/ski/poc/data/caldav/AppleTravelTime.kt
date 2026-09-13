package calino.malinov.ski.poc.data.caldav

import biweekly.component.VEvent
import java.time.Duration

/** Apple's interoperable travel-time extension, expressed as an RFC 5545 duration. */
internal const val AppleTravelDurationProperty = "X-APPLE-TRAVEL-DURATION"

internal fun VEvent.readAppleTravelTimeMinutes(): Int? {
    val raw = getExperimentalProperty(AppleTravelDurationProperty)?.value?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: return null
    val duration = runCatching { Duration.parse(raw) }.getOrNull()
        ?.takeIf { !it.isZero && !it.isNegative }
        ?: return null
    // The app models whole minutes. Round a positive partial minute up, as the
    // web app does, so a server value such as PT30S does not disappear as zero.
    val seconds = duration.seconds
    return ((seconds + 59L) / 60L).takeIf { it in 1..Int.MAX_VALUE }?.toInt()
}

internal fun VEvent.writeAppleTravelTimeMinutes(minutes: Int?) {
    removeExperimentalProperties(AppleTravelDurationProperty)
    minutes?.takeIf { it > 0 }?.let { value ->
        addExperimentalProperty(AppleTravelDurationProperty, Duration.ofMinutes(value.toLong()).toString())
    }
}
