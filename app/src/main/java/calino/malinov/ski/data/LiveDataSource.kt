package calino.malinov.ski.data

/**
 * Which sources make the app live rather than a sample.
 *
 * The frozen May 2026 fixtures exist so a fresh install is never an empty
 * shell, and they are only right while they are the *only* thing shown: their
 * dates are fixed, so a real calendar beside them puts the person's events
 * months away from the day the app opens on.
 *
 * The rule is therefore "any real source at all", and a calendar imported from
 * the device's own provider is one. It is the person's own data, read from the
 * phone. Leaving it out kept the sample calendar, kept the app's clock pinned to
 * 18 May 2026, and left the imported calendars listed in the sidebar with
 * nothing in them -- the events were on their real dates, off screen.
 *
 * It is a named rule with a test of its own because it is read in a dozen
 * places and a missing term is invisible: nothing crashes, the person simply
 * sees demo data drawn over their own.
 */
object LiveDataSource {
    fun isLive(
        hasAccounts: Boolean,
        hasWebcalSubscriptions: Boolean,
        hasImportedCalendars: Boolean,
    ): Boolean = hasAccounts || hasWebcalSubscriptions || hasImportedCalendars
}
