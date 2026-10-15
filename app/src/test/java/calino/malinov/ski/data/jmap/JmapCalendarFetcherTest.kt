package calino.malinov.ski.data.jmap

import calino.malinov.ski.data.caldav.CalendarFetchMode
import calino.malinov.ski.data.caldav.CalendarResource
import calino.malinov.ski.data.caldav.CollectionCursor
import calino.malinov.ski.data.caldav.DavCredentials
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class JmapCalendarFetcherTest {
    private val fake = FakeJmapServer()
    private val credentials = DavCredentials("ann@example.com", "secret")
    private val events = linkedMapOf<String, JSONObject>()
    private var state = "e1"
    private var pageSize = 2
    private var queryState = "q1"

    private lateinit var fetcher: JmapCalendarFetcher
    private lateinit var calendar: calino.malinov.ski.data.caldav.DiscoveredCalendar

    private fun event(id: String, vararg calendarIds: String, title: String = "Event $id", start: String = "2026-10-10T10:00:00") =
        jsonObject(
            "@type" to "Event",
            "id" to id,
            "uid" to "uid-$id@test",
            "title" to title,
            "start" to start,
            "timeZone" to "UTC",
            "duration" to "PT1H",
            "calendarIds" to jsonObject(*calendarIds.map { it to true }.toTypedArray()),
        )

    private fun put(event: JSONObject) {
        events[event.getString("id")] = event
    }

    @Before
    fun setUp() {
        fake.start()
        fetcher = JmapCalendarFetcher(JmapClientProvider(), ZoneOffset.UTC)
        calendar = discoveredCalendar(fake.baseUrl, "a", "c1", JSONObject(), false, state)
        fake.respond = { name, args, _ -> answer(name, args) }
    }

    @After
    fun tearDown() = fake.stop()

    private val queryFilters = mutableListOf<JSONObject>()
    private var failFirstFilter: String? = null
    private var mismatchOnce = false
    private var changesResponses = ArrayDeque<JSONObject>()
    private var changesRequests = mutableListOf<String>()

    private fun answer(name: String, args: JSONObject): JSONObject? = when (name) {
        "CalendarEvent/get" -> {
            val ids = args.optJSONArray("ids")?.toList()?.filterIsInstance<String>()
            jsonObject(
                "state" to state,
                "list" to jsonArray((ids ?: events.keys.toList()).mapNotNull { events[it] }),
                "notFound" to jsonArray((ids ?: emptyList()).filter { it !in events }),
            )
        }
        "CalendarEvent/query" -> {
            val filter = args.getJSONObject("filter")
            queryFilters += filter
            val unsupported = failFirstFilter
            if (unsupported != null && filter.has(unsupported)) {
                jsonObject("@error" to jsonObject("type" to "unsupportedFilter"))
            } else {
                val calendarId = filter.optString("inCalendar").ifEmpty {
                    filter.getJSONArray("inCalendars").getString(0)
                }
                val all = events.values.filter { it.getJSONObject("calendarIds").optBoolean(calendarId) }.map { it.getString("id") }
                val position = args.getInt("position")
                if (mismatchOnce && position > 0) {
                    mismatchOnce = false
                    queryState = "q2"
                }
                jsonObject(
                    "queryState" to queryState,
                    "position" to position,
                    "ids" to jsonArray(all.drop(position).take(pageSize)),
                    "total" to all.size,
                )
            }
        }
        "CalendarEvent/changes" -> {
            changesRequests += args.getString("sinceState")
            changesResponses.removeFirstOrNull() ?: jsonObject("@error" to jsonObject("type" to "cannotCalculateChanges"))
        }
        else -> null
    }

    private fun fetchAll(): List<CalendarResource> = runBlocking {
        fetcher.fetch(calendar, credentials, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1)).resources
    }

    @Test
    fun pagesThroughTheQueryAndKeepsOnlyMembersOfTheCalendar() {
        (1..5).forEach { put(event("e$it", "c1")) }
        put(event("other", "c2"))
        val resources = fetchAll()
        assertEquals(5, resources.size)
        assertEquals(
            jmapEventUrl(fake.baseUrl, "a", "c1", "e3"),
            resources.first { it.ics.contains("UID:uid-e3@test") }.href,
        )
        assertTrue(resources.all { Regex("[0-9a-f]{64}").matches(it.etag.orEmpty()) })
        assertTrue(resources.first().ics.contains("BEGIN:VEVENT"))
        assertEquals(setOf("c1"), queryFilters.map { it.getString("inCalendar") }.toSet())
    }

    @Test
    fun theWindowIsSentAsUtcInstants() {
        put(event("e1", "c1"))
        fetchAll()
        val filter = queryFilters.first()
        assertEquals("2026-10-01T00:00:00Z", filter.getString("after"))
        assertEquals("2026-11-01T00:00:00Z", filter.getString("before"))
    }

    @Test
    fun theEtagChangesWithTheEventAndNotWithKeyOrder() {
        val first = event("e1", "c1")
        val reordered = JSONObject(first.toString()).also {
            val title = it.remove("title")
            it.put("title", title)
        }
        assertEquals(eventEtag(first), eventEtag(reordered))
        assertFalse(eventEtag(first) == eventEtag(event("e1", "c1", title = "Different")))
    }

    @Test
    fun anExportHasNoTimeFilter() {
        put(event("e1", "c1"))
        val resources = runBlocking { fetcher.fetchAllEvents(calendar, credentials) }
        assertEquals(1, resources.size)
        assertFalse(queryFilters.first().has("after"))
    }

    @Test
    fun fallsBackToInCalendarsAndRemembersIt() {
        put(event("e1", "c1"))
        failFirstFilter = "inCalendar"
        assertEquals(1, fetchAll().size)
        queryFilters.clear()
        assertEquals(1, fetchAll().size)
        assertTrue(queryFilters.all { it.has("inCalendars") })
    }

    @Test
    fun aQueryStateChangeBetweenPagesRestartsTheListing() {
        (1..4).forEach { put(event("e$it", "c1")) }
        mismatchOnce = true
        val resources = fetchAll()
        assertEquals(4, resources.size)
        assertEquals(4, resources.map { it.href }.toSet().size)
    }

    @Test
    fun anEventThatCannotBeConvertedIsReportedNotCached() = runBlocking {
        put(event("good", "c1"))
        put(event("bad", "c1", start = "not a date"))
        val result = fetcher.fetch(calendar, credentials, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1))
        assertEquals(1, result.resources.size)
        assertTrue(result.hadComponentFailures)
    }

    @Test
    fun aFirstSyncCapturesTheStateBeforeListing() = runBlocking {
        put(event("e1", "c1"))
        val result = fetcher.fetchIncremental(
            calendar.copy(syncToken = null), credentials, null,
            LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1), null,
        )
        assertEquals(CalendarFetchMode.Full, result.mode)
        assertEquals("e1", result.cursor.syncToken)
        assertNull(result.cursor.ctag)
        assertEquals(1, result.resources.size)
    }

    @Test
    fun incrementalSyncAppliesCreatedUpdatedDestroyedAndMovedOut() = runBlocking {
        listOf("a", "b", "c", "d").forEach { put(event(it, "c1")) }
        val cached = fetchAll()
        // b is edited, c destroyed, d moved to another calendar, e is new.
        put(event("b", "c1", title = "Edited"))
        events.remove("c")
        put(event("d", "c2"))
        put(event("e", "c1"))
        state = "e2"
        changesResponses += jsonObject(
            "oldState" to "e1", "newState" to "e2", "hasMoreChanges" to false,
            "created" to jsonArray(listOf("e")), "updated" to jsonArray(listOf("b", "d")),
            "destroyed" to jsonArray(listOf("c")),
        )
        val result = fetcher.fetchIncremental(
            calendar, credentials, cached, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1),
            CollectionCursor(syncToken = "e1"),
        )
        assertEquals(CalendarFetchMode.Incremental, result.mode)
        assertEquals("e2", result.cursor.syncToken)
        assertEquals(listOf("e1"), changesRequests)
        val uids = result.resources.map { Regex("UID:(\\S+)").find(it.ics)!!.groupValues[1] }.toSet()
        assertEquals(setOf("uid-a@test", "uid-b@test", "uid-e@test"), uids)
        assertTrue(result.resources.first { it.ics.contains("uid-b@test") }.ics.contains("SUMMARY:Edited"))
    }

    @Test
    fun aChainOfChangePagesIsFollowedToTheEnd() = runBlocking {
        put(event("a", "c1"))
        val cached = fetchAll()
        put(event("b", "c1"))
        put(event("c", "c1"))
        changesResponses += jsonObject(
            "newState" to "e2", "hasMoreChanges" to true, "created" to jsonArray(listOf("b")),
        )
        changesResponses += jsonObject(
            "newState" to "e3", "hasMoreChanges" to false, "created" to jsonArray(listOf("c")),
        )
        val result = fetcher.fetchIncremental(
            calendar, credentials, cached, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1),
            CollectionCursor(syncToken = "e1"),
        )
        assertEquals(listOf("e1", "e2"), changesRequests)
        assertEquals("e3", result.cursor.syncToken)
        assertEquals(3, result.resources.size)
    }

    @Test
    fun aStuckChangeChainFallsBackToAFullListing() = runBlocking {
        put(event("a", "c1"))
        val cached = fetchAll()
        changesResponses += jsonObject("newState" to "e1", "hasMoreChanges" to true)
        val result = fetcher.fetchIncremental(
            calendar, credentials, cached, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1),
            CollectionCursor(syncToken = "e1"),
        )
        assertEquals(CalendarFetchMode.FullFallback, result.mode)
        assertEquals(state, result.cursor.syncToken)
        assertEquals(1, result.resources.size)
    }

    @Test
    fun anExpiredStateFallsBackToAFullListing() = runBlocking {
        put(event("a", "c1"))
        val cached = fetchAll()
        state = "e9"
        val result = fetcher.fetchIncremental(
            calendar, credentials, cached, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1),
            CollectionCursor(syncToken = "gone"),
        )
        assertEquals(CalendarFetchMode.FullFallback, result.mode)
        assertEquals("e9", result.cursor.syncToken)
    }

    @Test
    fun aStoredTokenWithoutACacheListsEverythingFromAFreshState() = runBlocking {
        put(event("a", "c1"))
        val result = fetcher.fetchIncremental(
            calendar, credentials, null, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1),
            CollectionCursor(syncToken = "old"),
        )
        assertEquals(CalendarFetchMode.FullFallback, result.mode)
        assertEquals("e1", result.cursor.syncToken)
        assertTrue(changesRequests.isEmpty())
    }

    @Test
    fun fetchResourceChecksMembership() = runBlocking {
        put(event("a", "c1"))
        put(event("b", "c2"))
        val href = { id: String -> jmapEventUrl(fake.baseUrl, "a", "c1", id) }
        assertNotNull(fetcher.fetchResource(calendar, credentials, href("a")))
        assertNull(fetcher.fetchResource(calendar, credentials, href("b")))
        assertNull(fetcher.fetchResource(calendar, credentials, href("missing")))
    }
}
