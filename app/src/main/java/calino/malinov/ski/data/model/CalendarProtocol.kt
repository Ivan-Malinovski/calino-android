package calino.malinov.ski.data.model

/**
 * Which wire protocol an account's calendars and contacts are read and written
 * over. It is detected once when the account connects, persisted with it, and
 * can be overridden by the person from the account sheet.
 *
 * Everything above the backends stays iCalendar-shaped regardless: a JMAP
 * backend translates at its boundary, so the raw-text cache, the write queue
 * and the recurrence editing never learn which protocol produced a resource.
 */
enum class CalendarProtocol {
    CalDav,
    Jmap,
}
