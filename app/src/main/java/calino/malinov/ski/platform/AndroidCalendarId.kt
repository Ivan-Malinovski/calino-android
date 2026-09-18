package calino.malinov.ski.platform

/**
 * The one place that knows how an imported record's id is spelled.
 *
 * Imported calendars and events share Calino's id space with CalDAV records,
 * so they need a prefix that cannot collide with a CalDAV URL and is cheap to
 * test for. Everything downstream -- the read-only guard in the UI, the
 * projection's self-exclusion, the future write-back -- asks this object
 * rather than splitting the string itself. A second place that knows the
 * format is a second place that can disagree about it.
 *
 * The event form is deliberately the same `id@instant` shape as
 * `ICalMapper.occurrenceId`. Two reasons, both load-bearing:
 *
 * - The provider's `Instances` table returns one row per occurrence, exactly
 *   as `ICalMapper` expands a series before the snapshot exists. Matching the
 *   shape means occurrence-addressing code already in the app keeps working
 *   on imported events with no special case.
 * - A later write-back needs both `ORIGINAL_ID` and `ORIGINAL_INSTANCE_TIME`
 *   to edit one occurrence of a foreign series. Both are recoverable from the
 *   id alone, so there is no side table to fall out of step -- the same
 *   reasoning that put identity in `SYNC_DATA1..5` on the way out.
 */
object AndroidCalendarId {

    /**
     * Marks a record as belonging to a calendar Calino only reads.
     *
     * A CalDAV calendar id is a URL and a CalDAV event id is `uid` or
     * `uid@instant`, so neither can begin with this.
     */
    const val Prefix = "android:"

    /** True when [id] names an imported calendar or event. */
    fun isImported(id: String): Boolean = id.startsWith(Prefix)

    /** The Calino id for the provider calendar row [rowId]. */
    fun calendar(rowId: Long): String = "$Prefix$rowId"

    /**
     * The Calino id for one occurrence: provider event row plus its start.
     *
     * The instant is part of the identity, not decoration. Two occurrences of
     * the same series share a row id and are told apart only by when they
     * begin.
     */
    fun event(eventRowId: Long, beginMillis: Long): String =
        "$Prefix$eventRowId@$beginMillis"

    /** The provider row id behind an imported calendar id, or null. */
    fun calendarRowId(id: String): Long? =
        id.takeIf { isImported(it) }?.removePrefix(Prefix)?.toLongOrNull()

    /**
     * The provider row and occurrence start behind an imported event id.
     *
     * Null when [id] is not one of ours or has been mangled. Callers treat
     * null as "not imported" rather than as an error: a malformed id can only
     * arrive from a bug, and refusing to act on it is safer than guessing.
     */
    fun eventRow(id: String): Pair<Long, Long>? {
        if (!isImported(id)) return null
        val body = id.removePrefix(Prefix)
        val separator = body.lastIndexOf('@')
        if (separator <= 0) return null
        val row = body.substring(0, separator).toLongOrNull() ?: return null
        val begin = body.substring(separator + 1).toLongOrNull() ?: return null
        return row to begin
    }
}
