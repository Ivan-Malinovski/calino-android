package calino.malinov.ski.poc.util

/**
 * How much of a busy day a month cell is allowed to show.
 *
 * A cap laid over the space a cell actually has, never a replacement for it:
 * the grid measures its own capacity, and this only ever lowers the result. A
 * user choosing [Dense] gets everything that fits, not cards drawn past the
 * bottom of the row.
 */
enum class CalinoEventDensity(val label: String, val maxItems: Int) {
    Quiet("Quiet", 2),
    Balanced("Balanced", 4),

    /**
     * Geometry only. A finite sentinel rather than `Int.MAX_VALUE`: the shown
     * count subtracts one to make room for the "+n" line, and arithmetic on a
     * saturated maximum is the kind of thing that reads fine and overflows
     * later.
     */
    Dense("Dense", 99),
    ;

    companion object {
        val Default = Balanced

        fun fromName(name: String?): CalinoEventDensity =
            entries.firstOrNull { it.name == name } ?: Default
    }
}

/**
 * The zoom level the calendar opens on.
 *
 * These are levels of the month-to-day continuum rather than separate screens,
 * so the preference seeds the zoom state; [zoomLevel] is the value that goes
 * into it.
 */
enum class CalinoDefaultView(val label: String, val zoomLevel: Float) {
    Month("Month", 2f),
    Week("Week", 0f),
    Day("Day", 1f),
    ;

    companion object {
        /** What the calendar has always opened on: the week strip over the day. */
        val Default = Week

        fun fromName(name: String?): CalinoDefaultView =
            entries.firstOrNull { it.name == name } ?: Default
    }
}

/**
 * How long a new event runs by default, in minutes. The editor offers these
 * three; anything else a user types is theirs to keep.
 */
enum class CalinoDefaultDuration(val label: String, val minutes: Int) {
    Half("30m", 30),
    Hour("60m", 60),
    HourAndHalf("90m", 90),
    ;

    companion object {
        val Default = Hour

        fun fromName(name: String?): CalinoDefaultDuration =
            entries.firstOrNull { it.name == name } ?: Default
    }
}

/**
 * The reminder a new event carries before anyone edits it. [None] is the
 * default, matching the drafts that used to start with an empty reminder list.
 */
enum class CalinoDefaultReminder(val label: String, val minutesBefore: Int?) {
    None("None", null),
    AtStart("At start", 0),
    TenMinutes("10 min", 10),
    ThirtyMinutes("30 min", 30),
    OneHour("1 hour", 60),
    ;

    companion object {
        val Default = None

        fun fromName(name: String?): CalinoDefaultReminder =
            entries.firstOrNull { it.name == name } ?: Default
    }
}
