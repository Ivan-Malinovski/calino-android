package calino.malinov.ski.util

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
 * The view the app opens on. Calendar views also supply their zoom level.
 *
 * Month, Week and Day seed the calendar zoom state through [zoomLevel].
 * Range and Agenda open their own routes.
 */
enum class CalinoDefaultView(val label: String, val zoomLevel: Float?) {
    Month("Month", 2f),
    Week("Week", 0f),
    Day("Day", 1f),
    Range("Range", null),
    Agenda("Agenda", null),
    ;

    companion object {
        /** What the calendar has always opened on: the week strip over the day. */
        val Default = Week

        fun fromName(name: String?): CalinoDefaultView =
            entries.firstOrNull { it.name == name } ?: Default
    }
}

/** The number of adjacent days shown by the dedicated range calendar. */
enum class CalinoRangeMode(val label: String, val dayCount: Int) {
    OneDay("1 day", 1),
    ThreeDay("3 days", 3),
    SevenDay("7 days", 7),
    ;

    companion object {
        val Default = ThreeDay
        fun fromName(name: String?): CalinoRangeMode =
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

/** The bounded VEVENT window fetched on either side of the current month. */
enum class CalinoEventSyncRange(val label: String, val months: Long) {
    SixMonths("6 mo", 6),
    OneYear("1 yr", 12),
    TwoYears("2 yr", 24),
    FiveYears("5 yr", 60),
    ;

    companion object {
        val Default = TwoYears
        fun fromName(name: String?): CalinoEventSyncRange =
            entries.firstOrNull { it.name == name } ?: Default
    }
}

/**
 * Which palette the app paints itself in.
 *
 * [System] is the default because it is what the app effectively did before the
 * setting existed for anyone whose phone was in light mode, and it is the
 * answer most people want. The other two are deliberate overrides that beat the
 * system setting.
 *
 * This names a *mode*, not a theme. The web app stores a mode plus a separate
 * theme id for each of light and dark, and that is the shape to grow into once
 * `CalinoThemes` registers more than the two built-in palettes: add
 * `lightThemeId`/`darkThemeId` preferences beside this one rather than adding
 * entries here.
 */
enum class CalinoThemeChoice(val label: String) {
    Light("Light"),
    System("System"),
    Dark("Dark"),
    ;

    companion object {
        val Default = System

        fun fromName(name: String?): CalinoThemeChoice =
            entries.firstOrNull { it.name == name } ?: Default
    }
}
