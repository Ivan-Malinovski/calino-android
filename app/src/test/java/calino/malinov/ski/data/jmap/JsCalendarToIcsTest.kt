package calino.malinov.ski.data.jmap

import calino.malinov.ski.data.caldav.ICalMapper
import java.time.LocalDate
import java.time.ZoneId
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The converter is checked against pairs: a JSCalendar object and the
 * iCalendar it came from (hand-authored, or captured from a live Stalwart over
 * both CalDAV and JMAP). They are compared by meaning -- decoded values,
 * default properties dropped, recurrence parts and list values order-free --
 * not by line layout.
 */
class JsCalendarToIcsTest {

    private val basic = JSONObject(
        """{"@type":"Event","uid":"unit-test","title":"Appointment","start":"2026-10-10T10:00:00","timeZone":"UTC","duration":"PT1H"}""",
    )

    private fun event(vararg changes: Pair<String, Any?>): JSONObject =
        basic.copy().also { copy -> changes.forEach { (k, v) -> if (v == null) copy.remove(k) else copy.put(k, v) } }

    private fun json(text: String) = JSONObject(text)

    /** Unfolded, so assertions do not depend on where a long line breaks. */
    private fun convert(event: JSONObject) = JsCalendarToIcs.convert(event).replace("\r\n ", "")

    private fun resource(name: String): String =
        javaClass.getResourceAsStream("/jmap/$name")!!.bufferedReader().use { it.readText() }

    // --- semantic comparison ----------------------------------------------------

    private class Node(val name: String) {
        val props = mutableListOf<Prop>()
        val children = mutableListOf<Node>()
    }

    private class Prop(val name: String, val params: Map<String, String>, val value: String)

    private fun parse(ics: String): Node {
        val lines = ics.replace("\r\n", "\n").replace("\n ", "").replace("\n\t", "").split('\n').filter { it.isNotBlank() }
        val root = Node("root")
        val stack = ArrayDeque(listOf(root))
        for (line in lines) {
            val (head, value) = splitContentLine(line)
            val pieces = splitOutsideQuotes(head, ';')
            val name = pieces[0].lowercase()
            if (name == "begin") {
                val node = Node(value.lowercase())
                stack.last().children += node
                stack += node
            } else if (name == "end") {
                stack.removeLast()
            } else {
                val params = pieces.drop(1).associate {
                    val key = it.substringBefore('=').lowercase()
                    key to it.substringAfter('=').removeSurrounding("\"")
                }
                stack.last().props += Prop(name, params, value)
            }
        }
        return root.children.single()
    }

    private fun splitContentLine(line: String): Pair<String, String> {
        var quoted = false
        for ((index, char) in line.withIndex()) {
            if (char == '"') quoted = !quoted
            if (char == ':' && !quoted) return line.substring(0, index) to line.substring(index + 1)
        }
        error("No value in content line: $line")
    }

    private fun splitOutsideQuotes(text: String, separator: Char): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        for (char in text) {
            if (char == '"') quoted = !quoted
            if (char == separator && !quoted) {
                out += current.toString()
                current.clear()
            } else {
                current.append(char)
            }
        }
        return out + current.toString()
    }

    private val ignored = setOf("dtstamp", "last-modified", "prodid", "calscale")
    private val defaults = mapOf("status" to "CONFIRMED", "transp" to "OPAQUE", "class" to "PUBLIC", "sequence" to "0", "priority" to "0")

    private fun normalize(node: Node): String {
        val props = node.props.flatMap { prop ->
            if (prop.name in ignored || prop.name.startsWith("x-")) return@flatMap emptyList()
            // JMAP alerts carry no text or recipients: Stalwart drops them, and the
            // converter fills in what RFC 5545 requires for DISPLAY and EMAIL.
            if (node.name == "valarm" && prop.name in setOf("description", "summary", "attendee")) return@flatMap emptyList()
            val params = prop.params.filter { (key, value) ->
                !(key == "related" && value == "START") && key != "value" && key != "schedule-sequence"
            }.toSortedMap()
            if (params.isEmpty() && prop.value == defaults[prop.name]) return@flatMap emptyList()
            when (prop.name) {
                "rdate", "exdate" -> prop.value.split(',').map { "${prop.name}$params=$it" }
                "rrule" -> listOf("rrule$params=" + prop.value.split(';').sorted().joinToString(";"))
                "categories" -> listOf("categories$params=" + prop.value.split(',').sorted().joinToString(","))
                else -> listOf("${prop.name}$params=${prop.value}")
            }
        }.sorted()
        val children = node.children.filter { it.name != "vtimezone" }.map(::normalize).sorted()
        return "${node.name}\n${props.joinToString("\n")}\n[${children.joinToString("\n")}]"
    }

    private fun semantic(ics: String) = normalize(parse(ics))

    private fun fixtures(): List<Triple<String, JSONObject, Unit>> {
        val array = JSONArray(resource("paired-events.json"))
        return (0 until array.length()).map {
            val item = array.getJSONObject(it)
            Triple(item.getString("name"), item.getJSONObject("event"), Unit)
        }
    }

    // --- paired fixtures ----------------------------------------------------------

    @Test
    fun pairedFixturesMatchTheOriginalIcalendarSemantically() {
        val all = fixtures()
        assertEquals(17, all.size)
        for ((name, event, _) in all) {
            assertEquals("fixture $name", semantic(resource("$name.ics")), semantic(convert(event)))
        }
    }

    @Test
    fun pairedFixturesAreAcceptedByCalinoWithTheOriginalMeaning() {
        val mapper = ICalMapper(ZoneId.of("UTC"))
        val start = LocalDate.of(2026, 1, 1)
        val end = LocalDate.of(2027, 12, 31)
        for ((name, event, _) in fixtures()) {
            fun events(ics: String) = mapper.parse(ics, "cal", 0L, "https://x/$name", windowStart = start, windowEnd = end)
                .events.map { listOf(it.title, it.start, it.durationMinutes, it.allDay, it.date, it.recurrence, it.categories.sorted(), it.reminders.size) }
                .sortedBy { it.toString() }
            val converted = events(convert(event))
            assertTrue("fixture $name produced events", converted.isNotEmpty())
            assertEquals("fixture $name", events(resource("$name.ics")), converted)
        }
    }

    @Test
    fun capturedStalwartResponsesReplayOffline() {
        val names = listOf("east", "west").flatMap { side ->
            fixtures().map { it.first to side }
        }
        var replayed = 0
        for ((name, side) in names) {
            val captured = javaClass.getResource("/jmap/stalwart-$name-$side.json") ?: continue
            val event = JSONObject(captured.readText())
            assertEquals("capture $name-$side", semantic(resource("stalwart-$name-$side.ics")), semantic(convert(event)))
            replayed++
        }
        assertTrue("captures were found", replayed > 20)
    }

    // --- wire format ----------------------------------------------------------------

    @Test
    fun escapesTextAndParametersAndFoldsAtSeventyFiveOctets() {
        val title = "Møde 😀, plan; next\\line\n".repeat(12)
        val ics = JsCalendarToIcs.convert(
            event(
                "title" to title,
                "participants" to json("""{"p":{"email":"a@example.org","name":"A \"quoted\"; name^\nNew line","roles":{"attendee":true}}}"""),
            ),
            prodId = "Test, product",
            calendarName = "Team; calendar",
        )
        assertTrue(ics.contains("\r\n "))
        assertFalse(ics.replace("\r\n", "").contains('\n'))
        ics.split("\r\n").forEach { assertTrue(it.toByteArray().size <= 75) }
        assertTrue(ics.contains("X-WR-CALNAME:Team\\; calendar"))
        val unfolded = ics.replace("\r\n ", "")
        assertTrue(unfolded.contains("CN=\"A ^'quoted^'; name^^^nNew line\""))
        assertEquals(convert(event("title" to title)), convert(JSONObject(event("title" to title).toString())))
    }

    @Test
    fun uriAndOptionValuesCannotInjectContentLines() {
        val ics = JsCalendarToIcs.convert(
            event("links" to json("""{"p":{"href":"https://example.org/\r\nSTATUS:CANCELLED"}}""")),
            prodId = "Test\r\nSTATUS:CANCELLED",
        )
        assertFalse(ics.lines().any { it.startsWith("STATUS:") })
        assertTrue(ics.contains("https://example.org/%0D%0ASTATUS:CANCELLED"))
    }

    @Test
    fun acceptsEtcUtcReplyToAndEmailFallback() {
        val ics = convert(
            event(
                "timeZone" to "Etc/UTC",
                "replyTo" to json("""{"imip":"mailto:owner@example.org"}"""),
                "participants" to json("""{"p":{"email":"a@example.org","roles":{"required":true}}}"""),
            ),
        )
        assertTrue(ics.contains("DTSTART:20261010T100000Z"))
        assertTrue(ics.contains("ORGANIZER:mailto:owner@example.org"))
        assertTrue(ics.contains("ATTENDEE;ROLE=REQ-PARTICIPANT:mailto:a@example.org"))
    }

    @Test
    fun addsNominalDaysBeforeElapsedTimeAcrossDst() {
        for ((duration, end) in listOf("P1D" to "20260329T120000", "PT24H" to "20260329T130000", "P1DT1H" to "20260329T130000")) {
            val ics = convert(event("start" to "2026-03-28T12:00:00", "timeZone" to "Europe/Copenhagen", "duration" to duration))
            assertTrue("$duration -> $end", ics.contains("DTEND;TZID=Europe/Copenhagen:$end"))
        }
    }

    @Test
    fun handlesGapsAndFolds() {
        for ((start, duration, end) in listOf(
            Triple("2026-03-29T01:30:00", "PT1H", "20260329T033000"),
            Triple("2026-03-29T02:30:00", "PT1H", "20260329T043000"),
            Triple("2026-10-25T02:30:00", "PT2H", "20261025T033000"),
        )) {
            val ics = convert(event("start" to start, "duration" to duration, "timeZone" to "Europe/Copenhagen"))
            assertTrue("$start +$duration", ics.contains("DTEND;TZID=Europe/Copenhagen:$end"))
        }
    }

    @Test
    fun convertsTheEndInstantIntoTheEndZone() {
        val variants = listOf(
            "endTimeZone" to "America/New_York",
            "locations" to json("""{"end":{"relativeTo":"end","timeZone":"America/New_York"}}"""),
            "locations" to json("""{"end":{"rel":"end","timeZone":"America/New_York"}}"""),
        )
        for (change in variants) {
            val ics = convert(event("timeZone" to "Europe/Copenhagen", "duration" to "PT8H", change))
            assertTrue(ics.contains("DTEND;TZID=America/New_York:20261010T120000"))
        }
    }

    @Test
    fun zeroDurationIsOneExclusiveDayWhenAllDayAndDurationWhenTimed() {
        val allDay = convert(event("showWithoutTime" to true, "duration" to "PT0S"))
        assertTrue(allDay.contains("DTEND;VALUE=DATE:20261011"))
        assertTrue(allDay.contains("DTSTAMP:19700101T000000Z"))
        val timed = convert(event("duration" to "PT0S"))
        assertTrue(timed.contains("DURATION:PT0S"))
        assertFalse(timed.contains("DTEND"))
        val mapped = ICalMapper(ZoneId.of("UTC")).parse(timed, "c", 0L, "h").events.single()
        // The mapper gives every zero-length event its default length; it must accept the DURATION.
        assertEquals("Appointment", mapped.title)
    }

    @Test
    fun mapsEveryRecurrenceSelectorAndPrefersTheSingularRule() {
        val rule = json(
            """{"frequency":"yearly","interval":2,"count":4,"byDay":[{"day":"tu","nthOfPeriod":2}],"byMonthDay":[1,-1],"byMonth":["3","10"],
            |"bySetPosition":[2],"byYearDay":[10],"byWeekNo":[2],"byHour":[10],"byMinute":[15],"bySecond":[30],"firstDayOfWeek":"su"}""".trimMargin(),
        )
        val ics = convert(event("recurrenceRule" to rule, "recurrenceRules" to JSONArray("""[{"frequency":"daily"}]""")))
        assertEquals(1, Regex("RRULE:").findAll(ics).count())
        listOf(
            "FREQ=YEARLY", "INTERVAL=2", "COUNT=4", "BYDAY=2TU", "BYMONTHDAY=1,-1", "BYMONTH=3,10", "BYSETPOS=2",
            "BYYEARDAY=10", "BYWEEKNO=2", "BYHOUR=10", "BYMINUTE=15", "BYSECOND=30", "WKST=SU",
        ).forEach { assertTrue(it, ics.contains(it)) }
    }

    @Test
    fun keepsUnboundedExclusionRulesAndEnumeratesFiniteOnes() {
        val multiple = convert(
            event(
                "recurrenceRules" to JSONArray("""[{"frequency":"daily","count":3},{"frequency":"yearly","count":2}]"""),
                "excludedRecurrenceRules" to JSONArray("""[{"frequency":"weekly","byDay":[{"day":"su"}]}]"""),
            ),
        )
        assertEquals(2, Regex("RRULE:").findAll(multiple).count())
        assertTrue(multiple.contains("EXRULE:FREQ=WEEKLY;BYDAY=SU"))

        val finite = convert(
            event(
                "recurrenceRule" to json("""{"frequency":"daily","count":4}"""),
                "excludedRecurrenceRules" to JSONArray("""[{"frequency":"daily","interval":2,"count":2}]"""),
            ),
        )
        assertTrue(finite.contains("EXDATE:20261010T100000Z"))
        assertTrue(finite.contains("EXDATE:20261012T100000Z"))
        assertFalse(finite.contains("EXRULE"))
    }

    @Test
    fun ownerIsTheOrganizerAndKeepsBothRolesWhenBothApply() {
        val ics = convert(
            event("participants" to json("""{"p":{"calendarAddress":"mailto:owner@example.org","roles":{"owner":true,"attendee":true}}}""")),
        )
        assertTrue(ics.contains("ORGANIZER:mailto:owner@example.org"))
        assertTrue(ics.contains("ATTENDEE;ROLE=REQ-PARTICIPANT:mailto:owner@example.org"))
    }

    @Test
    fun writesVirtualLocationsAndBinaryAttachments() {
        val ics = convert(
            event(
                "virtualLocations" to json("""{"a":{"uri":"https://example.org/meeting","name":"Video, meeting"}}"""),
                "links" to json("""{"p":{"rel":"enclosure","href":"data:application/octet-stream,%FF%00%41"}}"""),
            ),
        )
        assertTrue(ics.contains("URL:https://example.org/meeting"))
        assertTrue(ics.contains("CONFERENCE;VALUE=URI;LABEL=\"Video, meeting\":https://example.org/meeting"))
        assertTrue(ics.replace("\r\n ", "").contains("ATTACH;VALUE=BINARY;ENCODING=BASE64;FMTTYPE=application/octet-stream:/wBB"))
    }

    @Test
    fun rejectsUnsupportedInput() {
        assertThrows(JsCalendarException::class.java) { convert(event("uid" to null)) }
        assertThrows(JsCalendarException::class.java) { convert(event("start" to null)) }
        assertThrows(JsCalendarException::class.java) { convert(event("timeZone" to "Mars/Olympus")) }
        assertThrows(JsCalendarException::class.java) { convert(event("duration" to "1 hour")) }
        assertThrows(JsCalendarException::class.java) {
            convert(event("recurrenceRule" to json("""{"frequency":"yearly","rscale":"hebrew"}""")))
        }
    }

    // --- PatchObjects and recurrence membership ----------------------------------------

    @Test
    fun appliesEscapedPathsReplacementAndNullDeletionWithoutMutatingTheInput() {
        val source = event(
            "alerts" to json("""{"a/b~c":{"description":"Delete me","trigger":{"@type":"OffsetTrigger","offset":"-PT15M"}}}"""),
            "recurrenceRule" to json("""{"frequency":"weekly","count":4}"""),
            "recurrenceOverrides" to json(
                """{"2026-10-17T10:00:00":{"alerts/a~1b~0c/trigger/offset":"-PT30M","alerts/a~1b~0c/description":null,"title":"Changed","description":null}}""",
            ),
        )
        val before = source.toString()
        val ics = convert(source)
        val components = ics.split("BEGIN:VEVENT").drop(1)
        assertEquals(2, components.size)
        assertFalse(components[0].contains("RDATE"))
        assertTrue(components[1].contains("SUMMARY:Changed"))
        assertTrue(components[1].contains("DTSTART:20261017T100000Z"))
        assertTrue(components[1].contains("TRIGGER;RELATED=START:-PT30M"))
        assertFalse(components[1].contains("RRULE"))
        assertEquals(before, source.toString())
    }

    @Test
    fun rejectsAnInvalidPatchObjectInItsEntirety() {
        val source = event(
            "alerts" to json("""{"a":{"trigger":{"offset":"-PT15M"}}}"""),
            "recurrenceRules" to JSONArray("""[{"frequency":"weekly"}]"""),
        )
        val before = source.toString()
        listOf(
            """{"missing/title":"Bad"}""",
            """{"alerts/a/trigger/offset":"-PT5M","alerts":{}}""",
            """{"recurrenceRules/0/frequency":"daily"}""",
            """{"title~2":"Bad"}""",
        ).forEach { patch ->
            assertThrows(patch, JsCalendarException::class.java) { applyPatch(source, JSONObject(patch)) }
        }
        assertEquals(before, source.toString())
        val replaced = applyPatch(source, json("""{"keywords":{"x":true},"recurrenceRules":null}"""))
        assertFalse(replaced.has("recurrenceRules"))
        assertTrue(replaced.getJSONObject("keywords").getBoolean("x"))
    }

    @Test
    fun distinguishesRuleMembershipFromAddedDatesAndDoesNotDetachEmptyPatches() {
        val ics = convert(
            event(
                "start" to "2026-10-13T10:00:00",
                "recurrenceRule" to json("""{"frequency":"monthly","count":2,"byDay":[{"day":"tu","nthOfPeriod":2}]}"""),
                "recurrenceOverrides" to json(
                    """{"2026-11-10T10:00:00":{"title":"Second"},"2026-11-11T10:00:00":{},"2026-12-08T10:00:00":{"title":"After count"}}""",
                ),
            ),
        )
        assertEquals(3, Regex("BEGIN:VEVENT").findAll(ics).count())
        assertEquals(2, Regex("RDATE:").findAll(ics).count())
        assertTrue(ics.contains("RDATE:20261208T100000Z"))
        assertFalse(ics.contains("RDATE:20261110T100000Z"))
    }

    @Test
    fun recurrenceIdsUseTheMastersDateKindAndZone() {
        val ics = convert(
            event(
                "showWithoutTime" to true,
                "duration" to "P1D",
                "recurrenceRule" to json("""{"frequency":"daily","count":3}"""),
                "recurrenceOverrides" to json(
                    """{"2026-10-11T10:00:00":{"showWithoutTime":false,"timeZone":"America/New_York","start":"2026-10-11T12:00:00"}}""",
                ),
            ),
        )
        assertTrue(ics.contains("RECURRENCE-ID;VALUE=DATE:20261011"))
        assertTrue(ics.contains("DTSTART;TZID=America/New_York:20261011T120000"))
    }

    @Test
    fun comparesAZonedUntilInLocalTimeAtAnAmbiguousFold() {
        val ics = convert(
            event(
                "start" to "2026-10-24T02:30:00",
                "timeZone" to "Europe/Copenhagen",
                "recurrenceRule" to json("""{"frequency":"daily","until":"2026-10-25T02:30:00"}"""),
                "recurrenceOverrides" to json("""{"2026-10-25T02:30:00":{"title":"Last occurrence"}}"""),
            ),
        )
        assertTrue(ics.contains("UNTIL=20261025T003000Z"))
        assertFalse(ics.contains("RDATE"))
        assertTrue(ics.contains("RECURRENCE-ID;TZID=Europe/Copenhagen:20261025T023000"))
    }

    @Test
    fun mapperAcceptsAConvertedOverride() {
        val ics = convert(
            event(
                "recurrenceRule" to json("""{"frequency":"weekly","count":3}"""),
                "recurrenceOverrides" to json("""{"2026-10-17T10:00:00":{"title":"Moved","start":"2026-10-17T12:00:00"}}"""),
            ),
        )
        val parsed = ICalMapper(ZoneId.of("UTC")).parse(
            ics, "c", 0L, "h", windowStart = LocalDate.of(2026, 10, 1), windowEnd = LocalDate.of(2026, 11, 30),
        )
        assertNotNull(parsed.events.firstOrNull { it.title == "Moved" })
        assertEquals(3, parsed.events.size)
    }
}
