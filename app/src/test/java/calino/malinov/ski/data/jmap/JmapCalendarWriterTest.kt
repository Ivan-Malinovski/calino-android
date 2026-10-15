package calino.malinov.ski.data.jmap

import calino.malinov.ski.data.caldav.CachedCalendar
import calino.malinov.ski.data.caldav.CalDavErrorCode
import calino.malinov.ski.data.caldav.CalDavException
import calino.malinov.ski.data.caldav.CalDavWriter
import calino.malinov.ski.data.caldav.CalendarCache
import calino.malinov.ski.data.caldav.CalendarResource
import calino.malinov.ski.data.caldav.DavCredentials
import calino.malinov.ski.data.caldav.DavPrecondition
import calino.malinov.ski.data.caldav.DiscoveredCalendar
import calino.malinov.ski.data.caldav.PreparedCalendarWrite
import calino.malinov.ski.data.model.CalEvent
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class JmapCalendarWriterTest {
    private val fake = FakeJmapServer()
    private val credentials = DavCredentials("ann@example.com", "secret")
    private val cache = MemoryCache()
    private val events = linkedMapOf<String, JSONObject>()
    private var state = "e1"
    private var nextId = 1

    private val setCalls = mutableListOf<JSONObject>()
    private val queryFilters = mutableListOf<JSONObject>()
    private var rejectScheduling = false
    private var rejectUidFilter = false
    private var createFailure: JSONObject? = null
    private var updateFailure: JSONObject? = null

    private lateinit var calendar: DiscoveredCalendar
    private lateinit var other: DiscoveredCalendar
    private lateinit var writer: JmapCalendarWriter

    @Before
    fun setUp() {
        fake.start()
        val clients = JmapClientProvider()
        val fetcher = JmapCalendarFetcher(clients)
        writer = JmapCalendarWriter(clients, fetcher, CalDavWriter(cache = cache), cache)
        calendar = discoveredCalendar(fake.baseUrl, "a", "c1", JSONObject(), false, state)
        other = discoveredCalendar(fake.baseUrl, "a", "c2", JSONObject(), false, state)
        listOf(calendar, other).forEach { emptyCache(it) }
        fake.respond = { name, args, _ -> answer(name, args) }
    }

    @After
    fun tearDown() = fake.stop()

    // --- the fake server ------------------------------------------------------------------

    private fun answer(name: String, args: JSONObject): JSONObject? = when (name) {
        "CalendarEvent/get" -> {
            val ids = args.optJSONArray("ids")?.toList()?.filterIsInstance<String>() ?: events.keys.toList()
            jsonObject(
                "state" to state,
                "list" to jsonArray(ids.mapNotNull { events[it] }),
                "notFound" to jsonArray(ids.filter { it !in events }),
            )
        }
        "CalendarEvent/query" -> {
            val filter = args.getJSONObject("filter")
            queryFilters += filter
            if (filter.has("uid") && rejectUidFilter) {
                jsonObject("@error" to jsonObject("type" to "unsupportedFilter"))
            } else {
                val matches = events.values.filter { event ->
                    when {
                        filter.has("uid") -> event.optString("uid") == filter.getString("uid")
                        else -> event.getJSONObject("calendarIds").optBoolean(filter.getString("inCalendar"))
                    }
                }
                jsonObject(
                    "queryState" to "q",
                    "position" to args.optInt("position", 0),
                    "ids" to jsonArray(matches.map { it.getString("id") }.drop(args.optInt("position", 0))),
                    "total" to matches.size,
                )
            }
        }
        "CalendarEvent/set" -> set(args)
        else -> null
    }

    private fun set(args: JSONObject): JSONObject {
        setCalls += args
        if (rejectScheduling && args.has("sendSchedulingMessages")) {
            return jsonObject("@error" to jsonObject("type" to "invalidArguments"))
        }
        val result = jsonObject("oldState" to state, "newState" to "e${++nextId}")
        args.optJSONObject("create")?.let { creates ->
            val created = JSONObject()
            val notCreated = JSONObject()
            for (clientId in creates.keyList()) {
                val failure = createFailure
                if (failure != null) {
                    notCreated.put(clientId, failure)
                    continue
                }
                val id = "srv${nextId++}"
                events[id] = creates.getJSONObject(clientId).copy().put("id", id).put("updated", "2026-10-06T12:00:00Z")
                created.put(clientId, jsonObject("id" to id))
            }
            result.put("created", created).put("notCreated", notCreated)
        }
        args.optJSONObject("update")?.let { updates ->
            val notUpdated = JSONObject()
            for (id in updates.keyList()) {
                val failure = updateFailure
                val current = events[id]
                when {
                    failure != null -> notUpdated.put(id, failure)
                    current == null -> notUpdated.put(id, jsonObject("type" to "notFound"))
                    else -> events[id] = applyPatch(current, updates.getJSONObject(id))
                }
            }
            result.put("updated", JSONObject()).put("notUpdated", notUpdated)
        }
        args.optJSONArray("destroy")?.let { ids ->
            val notDestroyed = JSONObject()
            for (id in ids.toList().filterIsInstance<String>()) {
                if (events.remove(id) == null) notDestroyed.put(id, jsonObject("type" to "notFound"))
            }
            result.put("destroyed", ids).put("notDestroyed", notDestroyed)
        }
        return result
    }

    // --- helpers --------------------------------------------------------------------------

    private fun emptyCache(calendar: DiscoveredCalendar) = cache.save(
        CachedCalendar(calendar.url, Instant.EPOCH, LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1), emptyList()),
    )

    private fun serverEvent(
        id: String,
        vararg calendarIds: String,
        title: String = "Event $id",
        participants: JSONObject? = null,
    ): JSONObject = jsonObject(
        "@type" to "Event",
        "id" to id,
        "uid" to "uid-$id@test",
        "title" to title,
        "start" to "2026-10-10T10:00:00",
        "timeZone" to "UTC",
        "duration" to "PT1H",
        "updated" to "2026-10-06T12:00:00Z",
        "calendarIds" to jsonObject(*calendarIds.map { it to true }.toTypedArray()),
    ).also { event -> participants?.let { event.put("participants", it) } }.also { events[id] = it }

    private val alice = jsonObject(
        "p1" to jsonObject(
            "@type" to "Participant", "calendarAddress" to "mailto:alice@example.org", "name" to "Alice",
            "participationStatus" to "needs-action", "expectReply" to true, "roles" to jsonObject("required" to true),
        ),
    )

    private fun href(calendar: DiscoveredCalendar, id: String) = jmapEventUrl(calendar.url, "a", calendarIdOf(calendar), id)

    private fun calendarIdOf(calendar: DiscoveredCalendar) = parseJmapUrl(calendar.url, calendar.url, "a", event = false).calendarId

    /** What the repository would hold: the event as the server sent it, in iCalendar form. */
    private fun cached(event: JSONObject, calendar: DiscoveredCalendar = this.calendar): CalendarResource {
        val resource = CalendarResource(href(calendar, event.getString("id")), eventEtag(event), JsCalendarToIcs.convert(event))
        cache.saveResource(calendar.url, resource)
        return resource
    }

    /** An edit prepared the way the app does: a patch over the cached bytes. */
    private fun edit(resource: CalendarResource, from: String, to: String) = PreparedCalendarWrite(
        href = resource.href,
        body = resource.ics.replace(from, to).also { assertTrue("$from present", it != resource.ics) },
        precondition = DavPrecondition.Match(requireNotNull(resource.etag)),
        expectedEtag = resource.etag,
    )

    private fun put(prepared: PreparedCalendarWrite, in_: DiscoveredCalendar = calendar) =
        runBlocking { writer.putPrepared(in_, credentials, prepared) }

    private fun delete(resource: CalendarResource, etag: String? = resource.etag, from: DiscoveredCalendar = calendar) =
        runBlocking { writer.delete(from, credentials, resource.href, "uid", etag) }

    private fun newEvent(title: String = "Fresh", uid: String = "fresh@test"): PreparedCalendarWrite = writer.prepareEvent(
        calendar,
        CalEvent(
            id = uid, uid = uid, title = title, color = 0L,
            start = LocalDateTime.of(2026, 10, 12, 9, 0), durationMinutes = 30, calendarId = calendar.url,
        ),
    )

    private fun JSONObject.obj(key: String) = getJSONObject(key)

    private fun failure(block: () -> Unit): CalDavException = assertThrows(CalDavException::class.java) { block() }

    // --- create ---------------------------------------------------------------------------

    @Test
    fun aNewEventIsCreatedInTheCalendarAndGetsTheServersIdAsItsHref() {
        val prepared = newEvent()
        assertEquals(DavPrecondition.New, prepared.precondition)
        val written = put(prepared)

        val create = setCalls.single().obj("create").obj("event")
        assertEquals("Fresh", create.getString("title"))
        assertEquals("fresh@test", create.getString("uid"))
        assertEquals(jsonObject("c1" to true).toString(), create.obj("calendarIds").toString())
        assertFalse(create.has("id"))

        val stored = events.values.single()
        assertEquals(href(calendar, stored.getString("id")), written.href)
        assertEquals(eventEtag(stored), written.etag)
        assertTrue(written.ics.contains("SUMMARY:Fresh"))
        assertEquals(written.etag, cache.loadResource(calendar.url, written.href)?.etag)
    }

    @Test
    fun aLostResponseCreateIsRecognizedByItsUidAndNotDuplicated() {
        val existing = serverEvent("e1", "c1", title = "Same")
        val prepared = PreparedCalendarWrite(
            calendar.url + "x.ics", JsCalendarToIcs.convert(existing), DavPrecondition.New, null,
        )
        val written = put(prepared)
        assertEquals(href(calendar, "e1"), written.href)
        assertTrue("no create was sent", setCalls.isEmpty())
        assertEquals(1, events.size)
    }

    @Test
    fun aCreateOverADifferentEventWithTheSameUidIsAConflict() {
        val existing = serverEvent("e1", "c1", title = "Theirs")
        val body = JsCalendarToIcs.convert(existing.copy().put("title", "Mine"))
        val error = failure { put(PreparedCalendarWrite(calendar.url + "x.ics", body, DavPrecondition.New, null)) }
        assertEquals(CalDavErrorCode.PreconditionFailed, error.code)
        assertTrue(setCalls.isEmpty())
    }

    @Test
    fun aServerWithoutAUidFilterIsScannedInstead_andRemembered() {
        rejectUidFilter = true
        val existing = serverEvent("e1", "c1", title = "Same")
        val body = JsCalendarToIcs.convert(existing)
        put(PreparedCalendarWrite(calendar.url + "x.ics", body, DavPrecondition.New, null))
        assertTrue(setCalls.isEmpty())
        queryFilters.clear()
        put(PreparedCalendarWrite(calendar.url + "x.ics", body, DavPrecondition.New, null))
        assertTrue("the rejected filter is not tried again", queryFilters.none { it.has("uid") })
    }

    @Test
    fun rejectedPropertiesAndDuplicatesMapToTheQueuesVocabulary() {
        createFailure = jsonObject("type" to "invalidProperties", "properties" to jsonArray(listOf("start")))
        val invalid = failure { put(newEvent()) }
        assertEquals(CalDavErrorCode.Rejected, invalid.code)
        assertEquals(400, invalid.status)
        assertTrue(invalid.message.contains("start"))

        createFailure = jsonObject("type" to "alreadyExists")
        assertEquals(409, failure { put(newEvent()) }.status)

        createFailure = jsonObject("type" to "forbidden")
        assertEquals(CalDavErrorCode.Forbidden, failure { put(newEvent()) }.code)
    }

    @Test
    fun aTaskCannotBeStoredAsACalendarEvent() {
        val body = "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nBEGIN:VTODO\r\nUID:t@test\r\nSUMMARY:Task\r\nEND:VTODO\r\nEND:VCALENDAR\r\n"
        val error = failure { put(PreparedCalendarWrite(calendar.url + "t.ics", body, DavPrecondition.New, null)) }
        assertEquals(CalDavErrorCode.Rejected, error.code)
        assertTrue(setCalls.isEmpty())
    }

    // --- update ---------------------------------------------------------------------------

    @Test
    fun anEditSendsOnlyTheChangedPropertiesAndReturnsTheServersNewVersion() {
        val server = serverEvent("e1", "c1", title = "Old")
        val resource = cached(server)
        val written = put(edit(resource, "SUMMARY:Old", "SUMMARY:New"))

        val update = setCalls.single().obj("update").obj("e1")
        assertEquals(listOf("title"), update.keyList())
        assertEquals("New", update.getString("title"))
        assertEquals("New", events.getValue("e1").getString("title"))
        assertEquals(resource.href, written.href)
        assertEquals(eventEtag(events.getValue("e1")), written.etag)
        assertFalse(written.etag == resource.etag)
        assertEquals(written.etag, cache.loadResource(calendar.url, written.href)?.etag)
    }

    @Test
    fun anEditPreparedByTheAppPatchesTheCachedBytes() {
        val server = serverEvent("e1", "c1", title = "Old")
        val resource = cached(server)
        val prepared = writer.prepareEvent(
            calendar,
            CalEvent(
                id = "uid-e1@test", uid = "uid-e1@test", title = "Renamed", color = 0L,
                start = LocalDateTime.ofInstant(Instant.parse("2026-10-10T10:00:00Z"), java.time.ZoneId.systemDefault()), durationMinutes = 60, calendarId = calendar.url,
                href = resource.href, etag = resource.etag,
            ),
        )
        assertEquals(DavPrecondition.Match(resource.etag!!), prepared.precondition)
        put(prepared)
        assertEquals("Renamed", events.getValue("e1").getString("title"))
        assertEquals("only the title changed: ${setCalls.single()}", setOf("title"), setCalls.single().obj("update").obj("e1").keyList().toSet())
    }

    @Test
    fun aStaleEtagIsRefusedBeforeAnythingIsSent() {
        val server = serverEvent("e1", "c1", title = "Old")
        val resource = cached(server)
        events["e1"] = server.copy().put("title", "Changed by someone else")
        val error = failure { put(edit(resource, "SUMMARY:Old", "SUMMARY:Mine")) }
        assertEquals(CalDavErrorCode.PreconditionFailed, error.code)
        assertEquals(412, error.status)
        assertTrue(setCalls.isEmpty())
        assertEquals("Changed by someone else", events.getValue("e1").getString("title"))
    }

    @Test
    fun anEditOfAnEventThatIsGoneIsNotFound() {
        val resource = cached(serverEvent("e1", "c1", title = "Old"))
        events.clear()
        val error = failure { put(edit(resource, "SUMMARY:Old", "SUMMARY:New")) }
        assertEquals(CalDavErrorCode.NotFound, error.code)
    }

    @Test
    fun anEditThatChangesNothingSendsNothing() {
        val resource = cached(serverEvent("e1", "c1"))
        val written = put(PreparedCalendarWrite(resource.href, resource.ics, DavPrecondition.Match(resource.etag!!), resource.etag))
        assertTrue(setCalls.isEmpty())
        assertEquals(resource.etag, written.etag)
    }

    @Test
    fun propertiesTheServerHoldsThatCalinoDoesNotModelSurviveAnEdit() {
        val server = serverEvent("e1", "c1", title = "Old").put("x-vendor", "keep me").also { events["e1"] = it }
        put(edit(cached(server), "SUMMARY:Old", "SUMMARY:New"))
        assertEquals("keep me", events.getValue("e1").getString("x-vendor"))
        assertFalse(setCalls.single().obj("update").obj("e1").has("x-vendor"))
    }

    // --- scheduling -----------------------------------------------------------------------

    @Test
    fun editingAnEventWithAttendeesAsksTheServerToSendSchedulingMessages() {
        val resource = cached(serverEvent("e1", "c1", title = "Old", participants = alice))
        put(edit(resource, "SUMMARY:Old", "SUMMARY:New"))
        assertTrue(setCalls.single().optBoolean("sendSchedulingMessages"))
    }

    @Test
    fun anEventWithoutAttendeesDoesNotAskForScheduling() {
        put(edit(cached(serverEvent("e1", "c1", title = "Old")), "SUMMARY:Old", "SUMMARY:New"))
        assertFalse(setCalls.single().has("sendSchedulingMessages"))
    }

    @Test
    fun aServerThatRefusesSchedulingIsRetriedWithoutItAndRemembered() {
        rejectScheduling = true
        val first = cached(serverEvent("e1", "c1", title = "Old", participants = alice))
        put(edit(first, "SUMMARY:Old", "SUMMARY:New"))
        assertEquals(2, setCalls.size)
        assertTrue(setCalls[0].has("sendSchedulingMessages"))
        assertFalse(setCalls[1].has("sendSchedulingMessages"))
        assertEquals("New", events.getValue("e1").getString("title"))

        setCalls.clear()
        val second = cached(serverEvent("e2", "c1", title = "Old", participants = alice))
        put(edit(second, "SUMMARY:Old", "SUMMARY:Newer"))
        assertEquals(1, setCalls.size)
        assertFalse(setCalls.single().has("sendSchedulingMessages"))
    }

    @Test
    fun anRsvpIsAPatchOfTheOneParticipantsStatus() {
        val resource = cached(serverEvent("e1", "c1", participants = alice))
        put(edit(resource, "PARTSTAT=NEEDS-ACTION", "PARTSTAT=ACCEPTED"))
        val update = setCalls.single().obj("update").obj("e1")
        assertEquals(setOf("participants/p1/participationStatus"), update.keyList().toSet())
        assertEquals("accepted", update.getString("participants/p1/participationStatus"))
        assertEquals("accepted", events.getValue("e1").obj("participants").obj("p1").getString("participationStatus"))
    }

    // --- delete ---------------------------------------------------------------------------

    @Test
    fun deletingTheOnlyCopyDestroysTheEvent() {
        val resource = cached(serverEvent("e1", "c1"))
        delete(resource)
        assertEquals(listOf("e1"), setCalls.single().getJSONArray("destroy").toList())
        assertTrue(events.isEmpty())
        assertNull(cache.loadResource(calendar.url, resource.href))
    }

    @Test
    fun deletingWithAStaleEtagIsRefused() {
        val resource = cached(serverEvent("e1", "c1"))
        assertEquals(412, failure { delete(resource, etag = "0".repeat(64)) }.status)
        assertEquals(412, failure { delete(resource, etag = null) }.status)
        assertTrue(setCalls.isEmpty())
        assertEquals(1, events.size)
    }

    @Test
    fun deletingSomethingAlreadyGoneSucceeds() {
        val resource = cached(serverEvent("e1", "c1"))
        events.clear()
        delete(resource)
        assertTrue(setCalls.isEmpty())
    }

    @Test
    fun deletingAnEventWithAttendeesRequestsCancellations() {
        val resource = cached(serverEvent("e1", "c1", participants = alice))
        delete(resource)
        assertTrue(setCalls.single().optBoolean("sendSchedulingMessages"))
    }

    // --- moves ----------------------------------------------------------------------------

    @Test
    fun movingAnEventAddsTheNewMembershipThenDropsTheOldOneWithoutRecreatingIt() {
        val server = serverEvent("e1", "c1", title = "Travelling")
        val source = cached(server)
        // The destination write the repository performs first: the same event, "created" in c2.
        val prepared = PreparedCalendarWrite(other.url + "e.ics", source.ics, DavPrecondition.New, null)
        val written = put(prepared, other)

        assertEquals("one set call, an update, no create", 1, setCalls.size)
        assertEquals(jsonObject("calendarIds/c2" to true).toString(), setCalls.single().obj("update").obj("e1").toString())
        assertEquals(href(other, "e1"), written.href)
        assertEquals(setOf("c1", "c2"), events.getValue("e1").obj("calendarIds").keyList().toSet())

        // The cleanup the repository performs second, with the etag it read from the source.
        delete(source)
        assertEquals(1, events.size)
        assertEquals(listOf("c2"), events.getValue("e1").obj("calendarIds").keyList())
        assertTrue("dropping a membership is not a destroy", setCalls.last().has("update"))
        assertFalse(setCalls.last().has("sendSchedulingMessages"))
    }

    @Test
    fun aMoveThatAlsoEditsTheEventStillLetsTheSourceBeReleased() {
        val server = serverEvent("e1", "c1", title = "Old")
        val source = cached(server)
        val edited = source.ics.replace("SUMMARY:Old", "SUMMARY:Edited while moving")
        put(PreparedCalendarWrite(other.url + "e.ics", edited, DavPrecondition.New, null), other)
        assertEquals("Edited while moving", events.getValue("e1").getString("title"))
        // The event's validator changed with the edit; the source release must not trip on it.
        delete(source)
        assertEquals(listOf("c2"), events.getValue("e1").obj("calendarIds").keyList())
    }

    @Test
    fun movingAnEventWithAttendeesDoesNotNotifyThem() {
        val source = cached(serverEvent("e1", "c1", participants = alice))
        put(PreparedCalendarWrite(other.url + "e.ics", source.ics, DavPrecondition.New, null), other)
        assertFalse(setCalls.single().has("sendSchedulingMessages"))
    }

    @Test
    fun aRetriedMoveDestinationWriteIsRecognizedAsAlreadyDone() {
        val source = cached(serverEvent("e1", "c1", "c2"))
        val written = put(PreparedCalendarWrite(other.url + "e.ics", source.ics, DavPrecondition.New, null), other)
        assertEquals(href(other, "e1"), written.href)
        assertTrue(setCalls.isEmpty())
    }

    // --- refresh --------------------------------------------------------------------------

    @Test
    fun refreshingReadsTheServersCurrentVersionIntoTheCache() {
        val server = serverEvent("e1", "c1", title = "Old")
        val resource = cached(server)
        events["e1"] = server.copy().put("title", "Newer")
        val refreshed = runBlocking { writer.refreshResource(calendar, credentials, resource.href, "uid-e1@test") }
        assertTrue(refreshed.ics.contains("SUMMARY:Newer"))
        assertEquals(eventEtag(events.getValue("e1")), refreshed.etag)
        assertEquals(refreshed.etag, cache.loadResource(calendar.url, resource.href)?.etag)
    }

    @Test
    fun refreshingAnEventThatMovedFindsItByUid() {
        val server = serverEvent("e1", "c1")
        // Re-created under a new server id, as another client or a restore would.
        events.clear()
        serverEvent("e9", "c1").put("uid", "uid-e1@test").also { events["e9"] = it }
        val refreshed = runBlocking { writer.refreshResource(calendar, credentials, href(calendar, "e1"), "uid-e1@test") }
        assertEquals(href(calendar, "e9"), refreshed.href)
        assertNotNull(server)
    }

    @Test
    fun refreshingAnEventThatIsGoneIsNotFound() {
        val error = failure { runBlocking { writer.refreshResource(calendar, credentials, href(calendar, "e1"), "uid-e1@test") } }
        assertEquals(CalDavErrorCode.NotFound, error.code)
    }

    @Test
    fun aResourceFromAnotherAccountOrCalendarIsRefused() {
        val resource = cached(serverEvent("e1", "c1"))
        val foreign = PreparedCalendarWrite(
            href(other, "e1"), resource.ics.replace("SUMMARY:Event e1", "SUMMARY:X"),
            DavPrecondition.Match(resource.etag!!), resource.etag,
        )
        assertEquals(CalDavErrorCode.Rejected, failure { put(foreign) }.code)
        assertTrue(setCalls.isEmpty())
    }

    // --- cache ----------------------------------------------------------------------------

    private class MemoryCache : CalendarCache {
        private val entries = linkedMapOf<String, CachedCalendar>()

        override fun load(calendarUrl: String): CachedCalendar? = entries[calendarUrl]
        override fun save(entry: CachedCalendar) {
            entries[entry.calendarUrl] = entry
        }
        override fun evictExcept(calendarUrls: Set<String>) {
            entries.keys.retainAll(calendarUrls)
        }
        override fun loadResource(calendarUrl: String, href: String): CalendarResource? =
            entries[calendarUrl]?.resources?.firstOrNull { it.href == href }
        override fun saveResource(calendarUrl: String, resource: CalendarResource) {
            val entry = entries[calendarUrl] ?: return
            entries[calendarUrl] = entry.copy(resources = entry.resources.filterNot { it.href == resource.href } + resource)
        }
        override fun deleteResource(calendarUrl: String, href: String) {
            entries[calendarUrl]?.let { entry ->
                entries[calendarUrl] = entry.copy(resources = entry.resources.filterNot { it.href == href })
            }
        }
    }
}
