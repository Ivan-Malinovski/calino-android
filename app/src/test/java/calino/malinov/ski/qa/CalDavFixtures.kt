package calino.malinov.ski.qa

/**
 * Loads the captured CalDAV responses in `src/test/resources/caldav/`.
 *
 * These are verbatim replies from a real Radicale server, saved so the parsing
 * tests run against what a server actually sends rather than against XML shaped
 * to suit the parser. They contain no credentials.
 */
object CalDavFixtures {
    fun read(name: String): String =
        requireNotNull(javaClass.classLoader?.getResourceAsStream("caldav/$name")) {
            "Missing CalDAV fixture: caldav/$name"
        }.bufferedReader().use { it.readText() }

    val Principal: String get() = read("principal.xml")
    val HomeSet: String get() = read("home-set.xml")
    val Calendars: String get() = read("calendars-propfind.xml")
    val Events: String get() = read("vevent-expanded.xml")
    val Todos: String get() = read("vtodo-expanded.xml")
    val Journals: String get() = read("vjournal.xml")

    /** An unexpanded, infinite weekday series with mis-stamped EXDATEs. */
    val Recurring: String get() = read("vevent-recurring.xml")

    /** The collection the fixture corpus lives in. */
    const val CalendarUrl = "https://caldav.example.test/test-user/fixture-calendar/"
}
