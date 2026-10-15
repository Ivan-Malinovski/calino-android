package calino.malinov.ski.data.jmap

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The reverse converter is checked three ways: against explicit expectations
 * ported from Calino Web's suite, by round trip through [JsCalendarToIcs] (a
 * dropped property cannot survive the loop), and against the paired fixtures.
 */
class IcsToJsCalendarTest {

    private fun resource(name: String): String =
        javaClass.getResourceAsStream("/jmap/$name")!!.bufferedReader().use { it.readText() }

    private fun fixture(name: String) = resource("ics/$name.ics")

    private fun convert(name: String): JSONObject = IcsToJsCalendar.convert(fixture(name)).event

    private fun event(vararg lines: String, extra: String = "", type: String = "VEVENT"): String =
        "BEGIN:VCALENDAR\r\nVERSION:2.0\r\n$extra" +
            "BEGIN:$type\r\nUID:test@example.org\r\n${lines.joinToString("\r\n")}\r\nEND:$type\r\nEND:VCALENDAR\r\n"

    private fun convertLines(vararg lines: String, extra: String = ""): JSONObject =
        IcsToJsCalendar.convert(event(*lines, extra = extra)).event

    private fun JSONObject.obj(key: String): JSONObject = getJSONObject(key)

    // --- explicit expectations ------------------------------------------------------

    @Test
    fun weeklyCountKeepsTypedNestedObjectsAndSchedulingInDraftVocabulary() {
        val result = IcsToJsCalendar.convert(fixture("weekly-count"))
        val event = result.event
        assertEquals("write-weekly-count@example.org", result.uid)
        assertEquals("Event", event.getString("@type"))
        assertEquals("Weekly sync", event.getString("title"))
        assertEquals("Hello\nworld", event.getString("description"))
        assertEquals("2026-10-10T10:00:00", event.getString("start"))
        assertEquals("Europe/Copenhagen", event.getString("timeZone"))
        assertEquals("PT1H", event.getString("duration"))
        assertEquals("mailto:admin@example.org", event.getString("organizerCalendarAddress"))
        val rule = event.obj("recurrenceRule")
        assertEquals("weekly", rule.getString("frequency"))
        assertEquals(4, rule.getInt("count"))
        assertEquals("su", rule.getString("firstDayOfWeek"))
        assertEquals(
            listOf("mo", "we"),
            (0 until rule.getJSONArray("byDay").length()).map { rule.getJSONArray("byDay").getJSONObject(it).getString("day") },
        )
        val bob = event.obj("participants").entryList().map { it.second as JSONObject }
            .first { it.optString("calendarAddress") == "mailto:bob@example.org" }
        assertEquals("Bob", bob.getString("name"))
        assertEquals("needs-action", bob.getString("participationStatus"))
        assertTrue(bob.getBoolean("expectReply"))
        assertTrue(bob.obj("roles").getBoolean("required"))
        for (key in listOf("id", "calendarIds", "isDraft", "isOrigin", "X-CALINO-FOO", "sendTo", "recurrenceRules")) {
            assertFalse("no $key", event.has(key))
        }
    }

    @Test
    fun mapsOrdinalsAndEveryByPartWithoutConflatingBySetPosWithByDay() {
        val monthly = convert("monthly-ordinal").obj("recurrenceRule")
        assertEquals("monthly", monthly.getString("frequency"))
        assertEquals(2, monthly.getInt("interval"))
        assertEquals(6, monthly.getInt("count"))
        val days = monthly.getJSONArray("byDay")
        assertEquals(2, days.length())
        assertEquals("tu", days.getJSONObject(0).getString("day"))
        assertEquals(2, days.getJSONObject(0).getInt("nthOfPeriod"))
        assertEquals("fr", days.getJSONObject(1).getString("day"))
        assertEquals(-1, days.getJSONObject(1).getInt("nthOfPeriod"))
        assertEquals("[1,-1]", monthly.getJSONArray("bySetPosition").toString())

        val yearly = convert("yearly").obj("recurrenceRule")
        assertEquals("[\"10\"]", yearly.getJSONArray("byMonth").toString())
        assertEquals("[10]", yearly.getJSONArray("byMonthDay").toString())
        assertEquals("[283]", yearly.getJSONArray("byYearDay").toString())
        assertEquals("[41]", yearly.getJSONArray("byWeekNo").toString())
        assertEquals("[14]", yearly.getJSONArray("byHour").toString())
    }

    @Test
    fun untilIsExpressedOnTheMasterClock() {
        listOf(
            "until-zoned" to "2026-11-01T10:00:00",
            "until-new-york" to "2026-11-10T10:00:00",
            "until-utc" to "2026-11-01T14:00:00",
            "all-day" to "2026-11-01T00:00:00",
            "floating" to "2026-11-01T10:00:00",
        ).forEach { (name, until) ->
            assertEquals(name, until, convert(name).obj("recurrenceRule").getString("until"))
        }
    }

    @Test
    fun allDayEndIsExclusiveUtcIsExplicitAndFloatingHasNoZone() {
        val allDay = convert("all-day")
        assertEquals("2026-10-10T00:00:00", allDay.getString("start"))
        assertEquals("P3D", allDay.getString("duration"))
        assertTrue(allDay.getBoolean("showWithoutTime"))
        assertTrue(allDay.isNull("timeZone"))
        assertTrue(allDay.has("timeZone"))

        val utc = convert("utc")
        assertEquals("2026-10-10T14:00:00", utc.getString("start"))
        assertEquals("UTC", utc.getString("timeZone"))
        assertEquals("PT1H", utc.getString("duration"))

        val floating = convert("floating")
        assertEquals("2026-10-10T10:00:00", floating.getString("start"))
        assertTrue(floating.isNull("timeZone"))

        assertEquals("P1D", convertLines("DTSTART;VALUE=DATE:20261010").getString("duration"))
        assertEquals("PT0S", convertLines("DTSTART:20261010T100000").getString("duration"))
        assertEquals("P7D", convertLines("DTSTART;VALUE=DATE:20261010", "DURATION:P1W").getString("duration"))
    }

    @Test
    fun zonedElapsedDurationsAreExactAcrossDstAndDifferentEndZones() {
        assertEquals(
            "PT4H",
            convertLines(
                "DTSTART;TZID=Europe/Copenhagen:20261025T010000",
                "DTEND;TZID=Europe/Copenhagen:20261025T040000",
            ).getString("duration"),
        )
        assertEquals(
            "PT1H",
            convertLines(
                "DTSTART;TZID=America/New_York:20261010T100000",
                "DTEND;TZID=Europe/Copenhagen:20261010T170000",
            ).getString("duration"),
        )
    }

    @Test
    fun mapsWindowsAliasesAndVtimezoneXLicLocation() {
        assertEquals(
            "America/New_York",
            convertLines("DTSTART;TZID=\"Eastern Standard Time\":20261010T100000", "DURATION:PT1H").getString("timeZone"),
        )
        val zone = "BEGIN:VTIMEZONE\r\nTZID:Company/Copenhagen\r\nX-LIC-LOCATION:Europe/Copenhagen\r\n" +
            "BEGIN:STANDARD\r\nDTSTART:19700101T000000\r\nTZOFFSETFROM:+0100\r\nTZOFFSETTO:+0100\r\nEND:STANDARD\r\nEND:VTIMEZONE\r\n"
        val converted = convertLines("DTSTART;TZID=Company/Copenhagen:20261010T100000", "DURATION:PT1H", extra = zone)
        assertEquals("2026-10-10T10:00:00", converted.getString("start"))
        assertEquals("Europe/Copenhagen", converted.getString("timeZone"))
    }

    @Test
    fun anUnresolvableZoneIsRejectedRatherThanGuessed() {
        assertThrows(JsCalendarException::class.java) {
            convertLines("DTSTART;TZID=Company/Custom:20261010T100000")
        }
    }

    @Test
    fun combinesRdateExdateAndMinimalDetachedPatches() {
        val converted = convert("overrides")
        val overrides = converted.obj("recurrenceOverrides")
        assertEquals(jsonObject("excluded" to true).toString(), overrides.obj("2026-10-17T10:00:00").toString())
        assertEquals(0, overrides.obj("2026-11-07T10:00:00").length())
        val period = overrides.obj("2026-11-14T10:00:00")
        assertEquals("2026-11-14T10:00:00", period.getString("start"))
        assertEquals("PT2H", period.getString("duration"))
        assertEquals(0, overrides.obj("2026-10-31T10:00:00").length())

        val moved = overrides.obj("2026-10-24T10:00:00")
        assertEquals("Moved", moved.getString("title"))
        assertEquals("2026-10-24T12:00:00", moved.getString("start"))
        assertEquals("-PT30M", moved.getString("alerts/weekly-alarm/trigger/offset"))
        assertEquals(3, moved.length())
        val applied = applyPatch(converted.copy().put("start", "2026-10-24T10:00:00"), moved)
        assertEquals("Moved", applied.getString("title"))
        assertEquals("2026-10-24T12:00:00", applied.getString("start"))
    }

    @Test
    fun aDetachedComponentWithoutAlarmsDeletesThem_andACancelledOneExcludes() {
        val withoutAlarm = fixture("overrides").replace(
            Regex("(RECURRENCE-ID;TZID=Europe/Copenhagen:20261024T100000.*?)BEGIN:VALARM.*?END:VALARM\r?\n", RegexOption.DOT_MATCHES_ALL),
            "$1",
        )
        val changed = IcsToJsCalendar.convert(withoutAlarm).event
            .obj("recurrenceOverrides").obj("2026-10-24T10:00:00")
        assertTrue(changed.isNull("alerts"))
        assertTrue(changed.has("alerts"))

        val cancelled = withoutAlarm.replace(
            "SUMMARY:Moved",
            "SUMMARY:Moved\r\nSTATUS:CANCELLED",
        )
        val excluded = IcsToJsCalendar.convert(cancelled).event
            .obj("recurrenceOverrides").obj("2026-10-24T10:00:00")
        assertEquals(jsonObject("excluded" to true).toString(), excluded.toString())
    }

    @Test
    fun aUtcExdateIsNormalizedToTheMasterZoneAndWinsOverRdate() {
        val converted = convertLines(
            "DTSTART;TZID=Europe/Copenhagen:20261010T100000",
            "DURATION:PT1H",
            "RDATE;TZID=Europe/Copenhagen:20261017T100000",
            "EXDATE:20261017T080000Z",
        )
        assertEquals(
            jsonObject("2026-10-17T10:00:00" to jsonObject("excluded" to true)).toString(),
            converted.obj("recurrenceOverrides").toString(),
        )
    }

    @Test
    fun mapsAlarmsWithRelativeAndAbsoluteTriggersUidAndAcknowledgement() {
        val alerts = convert("alarms").obj("alerts")
        assertEquals(setOf("start-alarm", "end-alarm", "a2"), alerts.keyList().toSet())
        val start = alerts.obj("start-alarm")
        assertEquals("display", start.getString("action"))
        assertEquals("2026-10-06T12:00:00Z", start.getString("acknowledged"))
        assertEquals("-PT15M", start.obj("trigger").getString("offset"))
        assertEquals("start", start.obj("trigger").getString("relativeTo"))
        val end = alerts.obj("end-alarm")
        assertEquals("email", end.getString("action"))
        assertEquals("end", end.obj("trigger").getString("relativeTo"))
        val absolute = alerts.obj("a2").obj("trigger")
        assertEquals("AbsoluteTrigger", absolute.getString("@type"))
        assertEquals("2026-10-10T13:00:00Z", absolute.getString("when"))
    }

    @Test
    fun mapsTextKeywordsCategoriesAndMetadata() {
        val converted = convert("metadata")
        assertEquals("Møde, café; plan\\ning", converted.getString("title"))
        assertEquals("Kontor, 2. sal", converted.obj("locations").obj("l0").getString("name"))
        assertEquals("geo:55.6761,12.5683", converted.obj("locations").obj("l0").getString("coordinates"))
        assertEquals(setOf("work", "team,nordic"), converted.obj("keywords").keyList().toSet())
        assertEquals(
            setOf("https://example.org/concepts/meeting", "tag:example.org:2026:planning"),
            converted.obj("categories").keyList().toSet(),
        )
        assertEquals(3, converted.getInt("priority"))
        assertEquals("secret", converted.getString("privacy"))
        assertEquals("free", converted.getString("freeBusyStatus"))
        assertEquals("tentative", converted.getString("status"))
        assertEquals(7, converted.getInt("sequence"))
        assertEquals("2026-09-01T10:00:00Z", converted.getString("created"))
        assertEquals("2026-10-06T12:00:00Z", converted.getString("updated"))
        assertEquals("blue", converted.getString("color"))
        val relation = converted.obj("relatedTo").obj("sibling@example.org")
        assertTrue(relation.obj("relation").getBoolean("sibling"))
        val links = converted.obj("links").entryList().map { it.second as JSONObject }.associateBy { it.getString("href") }
        assertEquals("describedby", links.getValue("https://example.org/meeting").getString("rel"))
        assertEquals("application/pdf", links.getValue("https://example.org/agenda.pdf").getString("contentType"))
        assertEquals("enclosure", links.getValue("data:text/plain;base64,SGVsbG8=").getString("rel"))
        assertFalse(converted.toString().contains("X-"))
    }

    @Test
    fun rejectsWhatJmapCannotHold() {
        assertThrows(JsCalendarException::class.java) {
            IcsToJsCalendar.convert(event("DTSTART:20261010T100000", type = "VTODO"))
        }
        assertThrows(JsCalendarException::class.java) {
            convertLines("DTSTART:20261010T100000", "RRULE:FREQ=DAILY", "RRULE:FREQ=WEEKLY")
        }
        assertThrows(JsCalendarException::class.java) {
            IcsToJsCalendar.convert("BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nEND:VCALENDAR\r\n")
        }
    }

    @Test
    fun aThisAndFutureOverrideIsRefused() {
        val ics = event("DTSTART:20261010T100000", "RRULE:FREQ=DAILY;COUNT=3") +
            "" // master only; build the range override explicitly below
        val withRange = ics.replace(
            "END:VCALENDAR\r\n",
            "BEGIN:VEVENT\r\nUID:test@example.org\r\nRECURRENCE-ID;RANGE=THISANDFUTURE:20261011T100000\r\n" +
                "DTSTART:20261011T110000\r\nSUMMARY:Later\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n",
        )
        assertThrows(JsCalendarException::class.java) { IcsToJsCalendar.convert(withRange) }
    }

    // --- round trips ------------------------------------------------------------------

    /**
     * The client's view of an event, without identifiers that a trip through
     * iCalendar cannot keep (alarm and location keys) and without defaults.
     */
    private fun semantic(event: JSONObject, ignoring: Set<String> = emptySet()): String {
        val copy = event.copy()
        fun stripTypes(value: Any?): Any? {
            when (value) {
                is JSONObject -> {
                    value.remove("@type")
                    value.keyList().forEach { stripTypes(value.opt(it)) }
                }
                is JSONArray -> (0 until value.length()).forEach { stripTypes(value.opt(it)) }
            }
            return value
        }
        for (field in listOf("alerts", "locations", "links")) {
            val map = copy.optJSONObject(field) ?: continue
            val values = map.entryList().map { (_, value) ->
                val item = (value as JSONObject).copy()
                if (field == "alerts") {
                    // The text of an alarm is not read back, and "start" is the default anchor.
                    item.remove("description")
                    item.remove("title")
                    item.optJSONObject("trigger")?.let { if (it.optString("relativeTo") == "start") it.remove("relativeTo") }
                }
                canonicalJson(stripTypes(item))
            }.sorted()
            copy.put(field, JSONArray(values))
        }
        copy.optJSONObject("recurrenceOverrides")?.let { overrides ->
            val normalized = JSONObject()
            for ((key, patch) in overrides.entryList()) {
                if (key in ignoring) continue
                val rewritten = JSONObject()
                for ((path, value) in (patch as JSONObject).entryList()) {
                    // Alarm keys do not survive iCalendar, and a start equal to its key is redundant.
                    if (path == "start" && value == key) continue
                    rewritten.put(path.replace(Regex("^(alerts|locations|links)/[^/]+/"), "$1/*/"), value)
                }
                normalized.put(key, rewritten)
            }
            copy.put("recurrenceOverrides", normalized)
        }
        if (!copy.optBoolean("showWithoutTime", false)) copy.remove("showWithoutTime")
        // Participant keys are synthetic, and an attendee is a required one unless marked otherwise.
        copy.optJSONObject("participants")?.let { people ->
            val values = people.entryList().map { (_, value) ->
                val person = (value as JSONObject).copy()
                person.optJSONObject("roles")?.let { roles ->
                    if (roles.has("attendee")) {
                        roles.remove("attendee")
                        roles.put("required", true)
                    }
                }
                canonicalJson(stripTypes(person))
            }.sorted()
            copy.put("participants", JSONArray(values))
        }
        // RFC 8984 spells one rule as a list; the draft vocabulary this client writes uses one object.
        copy.optJSONArray("recurrenceRules")?.takeIf { it.length() == 1 }?.let {
            copy.remove("recurrenceRules")
            copy.put("recurrenceRule", it.getJSONObject(0))
        }
        copy.optJSONObject("recurrenceRule")?.let { rule ->
            if (rule.optInt("interval", 1) == 1) rule.remove("interval")
            if (rule.optString("firstDayOfWeek") == "mo") rule.remove("firstDayOfWeek")
        }
        stripTypes(copy)
        return canonicalJson(copy)
    }

    private val fixtureNames = listOf(
        "alarms", "all-day", "attendees", "floating", "metadata", "monthly-ordinal", "overrides",
        "until-new-york", "until-utc", "until-zoned", "utc", "weekly-count", "yearly",
    )

    @Test
    fun everyFixtureSurvivesIcsToJsCalendarToIcsAndBack() {
        for (name in fixtureNames) {
            val once = convert(name)
            val twice = IcsToJsCalendar.convert(JsCalendarToIcs.convert(once)).event
            // An override identical to its master is not stored in iCalendar at all.
            val ignoring = setOf("2026-10-31T10:00:00")
            assertEquals("fixture $name changed on the way round", semantic(once, ignoring), semantic(twice, ignoring))
        }
    }

    @Test
    fun pairedJsCalendarFixturesSurviveAJsCalendarToIcsToJsCalendarLoop() {
        val array = JSONArray(resource("paired-events.json"))
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            val original = item.getJSONObject("event")
            val back = roundTrip(original)
            if (item.getString("name") == "participants") {
                // Delegation links, sendTo and scheduleSequence are keyed by server participant ids
                // and have no iCalendar form; the people themselves must survive.
                fun people(event: JSONObject) = event.obj("participants").entryList().map { (_, v) ->
                    val p = v as JSONObject
                    listOf(
                        JsCalendarToIcs.participantAddress(p)?.removePrefix("mailto:"),
                        p.optString("name"),
                        p.optString("kind"),
                        p.optString("participationStatus"),
                        p.optJSONObject("roles")?.keyList()?.map { if (it == "attendee") "required" else it }?.sorted(),
                    )
                }.sortedBy { it.toString() }
                assertEquals(people(original), people(back))
                continue
            }
            assertEquals("fixture ${item.getString("name")}", semantic(original.copy().also { it.remove("updated") }), semantic(back.copy().also { it.remove("updated") }))
        }
    }

    @Test
    fun capturedStalwartEventsSurviveTheLoop() {
        var checked = 0
        for (name in listOf("east", "west")) {
            val array = JSONArray(resource("paired-events.json"))
            for (index in 0 until array.length()) {
                val fixtureName = array.getJSONObject(index).getString("name")
                val captured = javaClass.getResource("/jmap/stalwart-$fixtureName-$name.json") ?: continue
                val server = JSONObject(captured.readText())
                val first = roundTrip(server)
                // A second pass must be stable: what the client represents is a fixed point.
                val second = roundTrip(first)
                assertEquals("capture $fixtureName-$name is not stable", semantic(first), semantic(second))
                checked++
            }
        }
        assertTrue(checked > 20)
    }

    // --- patches against the server's event ---------------------------------------------

    private val server: JSONObject = JSONObject(
        """
        {"@type":"Event","id":"e1","uid":"patch@test","title":"Old","start":"2026-10-10T10:00:00",
         "timeZone":"UTC","duration":"PT1H","updated":"2026-10-06T12:00:00Z","created":"2026-10-01T12:00:00Z",
         "calendarIds":{"c1":true},"sequence":0,"x-server-extra":"kept"}
        """.trimIndent(),
    )

    @Test
    fun savingAnUntouchedEventProducesNoPatch() {
        val next = roundTrip(server)
        assertEquals(0, eventPatch(server, next).length())
    }

    @Test
    fun aTitleEditPatchesOnlyTheTitle() {
        val next = roundTrip(server).put("title", "New")
        val patch = eventPatch(server, next)
        assertEquals(listOf("title"), patch.keyList())
        assertEquals("New", patch.getString("title"))
    }

    @Test
    fun serverOnlyPropertiesAreNeverDeletedByAnEdit() {
        val next = roundTrip(server).put("title", "New")
        assertFalse(eventPatch(server, next).has("x-server-extra"))
    }

    @Test
    fun removingADescriptionDeletesItByNullPatch() {
        val withDescription = server.copy().put("description", "Hello")
        val next = roundTrip(withDescription).also { it.remove("description") }
        val patch = eventPatch(withDescription, next)
        assertEquals(listOf("description"), patch.keyList())
        assertTrue(patch.isNull("description"))
    }

    @Test
    fun anAlertEditIsAddressedByTheServersOwnKey() {
        val alerted = server.copy().put(
            "alerts",
            jsonObject(
                "srv-alert-1" to jsonObject(
                    "@type" to "Alert",
                    "trigger" to jsonObject("@type" to "OffsetTrigger", "offset" to "-PT15M", "relativeTo" to "start"),
                    "action" to "display",
                ),
            ),
        )
        val next = roundTrip(alerted)
        next.obj("alerts").keyList().forEach { key ->
            next.obj("alerts").obj(key).obj("trigger").put("offset", "-PT30M")
        }
        val patch = eventPatch(alerted, next)
        assertEquals(1, patch.length())
        assertEquals("-PT30M", patch.getString("alerts/srv-alert-1/trigger/offset"))
    }

    @Test
    fun aMembershipChangeIsCarriedAndRecognizedAsMembershipOnly() {
        val next = roundTrip(server).put("calendarIds", jsonObject("c1" to true, "c2" to true))
        val patch = eventPatch(server, next)
        assertEquals(listOf("calendarIds/c2"), patch.keyList())
        assertTrue(onlyMembership(patch))
        assertFalse(onlyMembership(patch.put("title", "x")))
    }

    // --- the parser -----------------------------------------------------------------------

    @Test
    fun theParserUnfoldsDecodesParametersAndRejectsUnbalancedInput() {
        val root = IcsParser.parse(
            "BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nSUMMARY:One\r\n two\r\n" +
                "ATTENDEE;CN=\"Ann; ^'Q^'\";ROLE=REQ-PARTICIPANT:mailto:ann@example.org\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n",
        )
        val vevent = root.components("VEVENT").single()
        assertEquals("Onetwo", vevent.text("SUMMARY"))
        val attendee = vevent.first("ATTENDEE")!!
        assertEquals("Ann; \"Q\"", attendee.parameter("CN"))
        assertEquals("mailto:ann@example.org", attendee.value)
        assertNotNull(attendee.parameter("role"))
        assertNull(attendee.parameter("PARTSTAT"))
        assertThrows(JsCalendarException::class.java) { IcsParser.parse("BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nEND:VCALENDAR\r\n") }
        assertThrows(JsCalendarException::class.java) { IcsParser.parse("BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\n") }
        assertEquals(listOf("a,b", "c"), icsTextList("a\\,b,c"))
        assertEquals("line\nbreak; semi, \\", icsUnescape("line\\nbreak\\; semi\\, \\\\"))
    }
}
