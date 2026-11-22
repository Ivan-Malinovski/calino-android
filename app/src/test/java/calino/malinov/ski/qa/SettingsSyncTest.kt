package calino.malinov.ski.qa

import calino.malinov.ski.data.caldav.DavCredentials
import calino.malinov.ski.data.caldav.RemoteSettings
import calino.malinov.ski.data.caldav.SettingsCalendarClient
import calino.malinov.ski.data.caldav.SettingsCollection
import calino.malinov.ski.data.caldav.SettingsRemote
import calino.malinov.ski.data.model.CategoryStyleFont
import calino.malinov.ski.data.model.CategoryStyleRule
import calino.malinov.ski.data.model.CategoryStyleTreatment
import calino.malinov.ski.data.settings.SettingsEvent
import calino.malinov.ski.data.settings.SettingsSyncOutcome
import calino.malinov.ski.data.settings.SettingsSyncPayload
import calino.malinov.ski.data.settings.SettingsSyncState
import calino.malinov.ski.data.settings.SettingsSyncer
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Category styles travel through the web app's `Calino Settings` calendar, so
 * these checks are about the *format and the conflict rules*: a payload the
 * browser wrote must survive a phone's push, and two devices must converge
 * without either losing the other's settings.
 */
class SettingsSyncTest {

    private fun rule(
        id: String = "rule-1",
        categories: String = "work, standup",
        treatment: CategoryStyleTreatment = CategoryStyleTreatment.Focus,
        font: CategoryStyleFont = CategoryStyleFont.Hand,
        color: Int = 0xFFB07D4F.toInt(),
        enabled: Boolean = true,
    ) = CategoryStyleRule(id, categories, treatment, font, color, enabled)

    // --- payload ---------------------------------------------------------------

    @Test
    fun `rules survive a round trip through the payload`() {
        val rules = listOf(
            rule(),
            rule(id = "rule-2", categories = "done", treatment = CategoryStyleTreatment.Done, color = 0xFF777985.toInt(), enabled = false),
        )
        val payload = SettingsSyncPayload.empty(Instant.parse("2026-11-22T10:00:00Z")).withCategoryStyleRules(rules)
        val reparsed = SettingsSyncPayload.parse(payload.toJson())

        assertEquals(1, reparsed?.version)
        assertEquals(Instant.parse("2026-11-22T10:00:00Z"), reparsed?.syncedAt)
        assertEquals(rules, reparsed?.categoryStyleRules())
    }

    @Test
    fun `a settings object this app does not own is carried through a push`() {
        // The web app writes far more than the phone knows about. None of it may
        // be dropped, or a phone would silently reset a browser's preferences.
        val remote = """
            {"version":1,"syncedAt":"2026-11-20T08:00:00Z","settings":{
              "timezone":"Europe/Copenhagen",
              "firstDayOfWeek":1,
              "themeMode":"auto",
              "adjustableTheme":{"light":{"canvas":"#f7f4ee"}}
            },"categories":[{"id":"c1","name":"Work","color":"#b07d4f"}]}
        """.trimIndent()
        val pushed = SettingsSyncPayload.parse(remote)!!.withCategoryStyleRules(listOf(rule()))

        val out = JSONObject(pushed.toJson())
        val settings = out.getJSONObject("settings")
        assertEquals("Europe/Copenhagen", settings.getString("timezone"))
        assertEquals(1, settings.getInt("firstDayOfWeek"))
        assertEquals("auto", settings.getString("themeMode"))
        assertEquals("#f7f4ee", settings.getJSONObject("adjustableTheme").getJSONObject("light").getString("canvas"))
        // The web app's category list is not this app's business and stays put.
        assertEquals("b07d4f", out.getJSONArray("categories").getJSONObject(0).getString("color").removePrefix("#"))
        // And this app's rules are there alongside it.
        assertEquals(1, settings.getJSONArray("categoryStyleRules").length())
        assertEquals("#b07d4f", settings.getJSONArray("categoryStyleRules").getJSONObject(0).getString("color"))
    }

    @Test
    fun `a payload without rules leaves the device's rules alone`() {
        val payload = SettingsSyncPayload.parse("""{"version":1,"syncedAt":"2026-11-20T08:00:00Z","settings":{}}""")
        assertNull(payload?.categoryStyleRules())
    }

    @Test
    fun `an empty rule list is a deliberate empty list`() {
        val payload = SettingsSyncPayload.parse(
            """{"version":1,"syncedAt":"2026-11-20T08:00:00Z","settings":{"categoryStyleRules":[]}}""",
        )
        assertEquals(emptyList<CategoryStyleRule>(), payload?.categoryStyleRules())
    }

    @Test
    fun `an unreadable payload is not an empty one`() {
        assertNull(SettingsSyncPayload.parse("not json at all"))
        assertNull(SettingsSyncPayload.parse("""{"version":1}"""))
        assertNull(SettingsSyncPayload.parse(null))
    }

    @Test
    fun `a rule with an unknown treatment or a broken colour is skipped, not fatal`() {
        val payload = SettingsSyncPayload.parse(
            """{"version":1,"syncedAt":"2026-11-20T08:00:00Z","settings":{"categoryStyleRules":[
                 {"id":"a","categories":"x","treatment":"sparkle","font":"hand","color":"#112233","enabled":true},
                 {"id":"b","categories":"y","treatment":"focus","font":"hand","color":"not-a-colour","enabled":true},
                 {"id":"c","categories":"z","treatment":"done","font":"hand","color":"#112233","enabled":true}
               ]}}""",
        )
        assertEquals(listOf("c"), payload?.categoryStyleRules()?.map { it.id })
    }

    // --- the settings event ----------------------------------------------------

    @Test
    fun `a long payload survives folding and unfolding`() {
        val big = SettingsSyncPayload.empty(Instant.parse("2026-11-22T10:00:00Z"))
            .withCategoryStyleRules((1..40).map { rule(id = "rule-$it", categories = "kategori-$it, endnu en kategori") })
        val json = big.toJson()
        val ics = SettingsEvent.build(json, Instant.parse("2026-11-22T10:00:00Z"))

        assertTrue("the fixture is long enough to fold", json.length > 700)
        ics.split("\r\n").forEach { line ->
            assertTrue("line over 75 octets: ${line.take(20)}… (${line.length})", line.toByteArray(Charsets.UTF_8).size <= 75)
        }
        assertEquals(json, SettingsEvent.payload(ics))
        assertEquals(Instant.parse("2026-11-22T10:00:00Z"), SettingsEvent.stamp(ics))
    }

    @Test
    fun `text outside latin-1 survives the base64 leg`() {
        val json = """{"version":1,"syncedAt":"2026-11-22T10:00:00Z","settings":{"note":"blåbær og ærter → ✓"}}"""
        val ics = SettingsEvent.build(json, Instant.parse("2026-11-22T10:00:00Z"))
        assertEquals(json, SettingsEvent.payload(ics))
    }

    @Test
    fun `an event without an attach has no payload`() {
        assertNull(SettingsEvent.payload("BEGIN:VCALENDAR\r\nEND:VCALENDAR\r\n"))
        assertNull(SettingsEvent.stamp("BEGIN:VCALENDAR\r\nEND:VCALENDAR\r\n"))
    }

    @Test
    fun `the event is invisible to a calendar view`() {
        val ics = SettingsEvent.build("{}", Instant.parse("2026-11-22T10:00:00Z"))
        assertTrue(ics.contains("UID:calino-settings"))
        assertTrue(ics.contains("DTSTART:19700101T000000Z"))
        assertTrue(ics.contains("TRANSP:TRANSPARENT"))
        assertTrue(ics.contains("CLASS:PRIVATE"))
    }

    // --- the sync decision -----------------------------------------------------

    private class FakeRemote(
        var stored: String? = null,
        var etag: String? = null,
        var conflictOnce: Boolean = false,
    ) : SettingsRemote {
        val writes = mutableListOf<String>()
        var reads = 0

        override suspend fun ensure(homeUrl: String, credentials: DavCredentials) =
            SettingsCollection("$homeUrl/calino-settings/", "$homeUrl/calino-settings/calino-settings.ics")

        override suspend fun read(collection: SettingsCollection, credentials: DavCredentials): RemoteSettings? {
            reads++
            val body = stored ?: return null
            return RemoteSettings(body, etag)
        }

        override suspend fun write(
            collection: SettingsCollection,
            credentials: DavCredentials,
            ics: String,
            etag: String?,
        ): SettingsCalendarClient.WriteOutcome {
            if (conflictOnce) {
                conflictOnce = false
                return SettingsCalendarClient.WriteOutcome.Conflict
            }
            writes += ics
            stored = ics
            this.etag = "\"etag-${writes.size}\""
            return SettingsCalendarClient.WriteOutcome.Written(this.etag)
        }
    }

    private class FakeState(
        override val enabled: Boolean = true,
        override var lastPulledAt: Instant? = null,
        override var rulesChangedAt: Instant? = null,
        private var rules: List<CategoryStyleRule> = emptyList(),
        override var collection: SettingsCollection? = null,
    ) : SettingsSyncState {
        var applied: List<CategoryStyleRule>? = null

        override fun localRules(): List<CategoryStyleRule> = rules

        fun localRulesOverride(next: List<CategoryStyleRule>) { rules = next }

        override fun rememberPulled(at: Instant, rules: List<CategoryStyleRule>) {
            lastPulledAt = at
            this.rules = rules
            applied = rules
        }

        override fun rememberPushed(at: Instant) {
            rulesChangedAt = null
            lastPulledAt = at
        }
    }

    private val credentials = DavCredentials("calino", "calinopass")
    private val home = "http://127.0.0.1:5232/calino"

    private fun remoteEvent(rules: List<CategoryStyleRule>, stamp: String, extraSettings: String = ""): String {
        val json = """{"version":1,"syncedAt":"$stamp","settings":{"timezone":"Europe/Copenhagen"$extraSettings}
            |,"categories":[{"id":"c1","name":"Work","color":"#b07d4f"}]}""".trimMargin()
        val withRules = if (rules.isEmpty()) json else SettingsSyncPayload.parse(json)!!.withCategoryStyleRules(rules).toJson()
        return SettingsEvent.build(withRules, Instant.parse(stamp))
    }

    @Test
    fun `an empty server is seeded from this device`() = runBlocking {
        val remote = FakeRemote()
        val state = FakeState(rules = listOf(rule()))
        val outcome = SettingsSyncer(remote, state) { Instant.parse("2026-11-22T10:00:00Z") }
            .synchronize(home, credentials)

        assertEquals(SettingsSyncOutcome.Pushed(1), outcome)
        assertEquals(1, remote.writes.size)
        val written = SettingsEvent.payload(remote.writes.single())!!
        assertEquals(1, SettingsSyncPayload.parse(written)?.categoryStyleRules()?.size)
    }

    @Test
    fun `a newer remote wins`() = runBlocking {
        val remote = FakeRemote(remoteEvent(listOf(rule(id = "remote-rule", color = 0xFF00AA55.toInt())), "2026-11-22T09:00:00Z"))
        val state = FakeState(lastPulledAt = Instant.parse("2026-11-21T00:00:00Z"), rules = listOf(rule()))
        val outcome = SettingsSyncer(remote, state) { Instant.parse("2026-11-22T10:00:00Z") }
            .synchronize(home, credentials)

        assertEquals(SettingsSyncOutcome.Pulled(1), outcome)
        assertEquals(listOf("remote-rule"), state.applied?.map { it.id })
        assertTrue(remote.writes.isEmpty())
    }

    @Test
    fun `a local edit is pushed over an older remote`() = runBlocking {
        val remote = FakeRemote(remoteEvent(listOf(rule(id = "remote-rule")), "2026-11-20T09:00:00Z"))
        val state = FakeState(
            lastPulledAt = Instant.parse("2026-11-21T00:00:00Z"),
            rulesChangedAt = Instant.parse("2026-11-22T08:00:00Z"),
            rules = listOf(rule(id = "mine")),
        )
        val outcome = SettingsSyncer(remote, state) { Instant.parse("2026-11-22T10:00:00Z") }
            .synchronize(home, credentials)

        assertEquals(SettingsSyncOutcome.Pushed(1), outcome)
        val written = SettingsEvent.payload(remote.writes.single())!!
        val payload = SettingsSyncPayload.parse(written)!!
        assertEquals(listOf("mine"), payload.categoryStyleRules()?.map { it.id })
        // The browser's settings and categories came along untouched.
        assertEquals("Europe/Copenhagen", payload.settings.getString("timezone"))
        assertNotNull(JSONObject(written).optJSONArray("categories"))
    }

    @Test
    fun `an edit made after a push is pushed again, not overwritten by our own write`() = runBlocking {
        // The order that matters in practice: the device writes its rules, the
        // person changes one a moment later, and the next cycle must send it.
        // Treating our own push as "never pulled" made the remote win instead,
        // which silently reverted the edit.
        val remote = FakeRemote()
        val state = FakeState(rules = listOf(rule(id = "first")))
        SettingsSyncer(remote, state) { Instant.parse("2026-11-22T10:00:00Z") }.synchronize(home, credentials)

        state.rulesChangedAt = Instant.parse("2026-11-22T10:05:00Z")
        state.let { it.localRulesOverride(listOf(rule(id = "edited"))) }
        val outcome = SettingsSyncer(remote, state) { Instant.parse("2026-11-22T10:06:00Z") }
            .synchronize(home, credentials)

        assertEquals(SettingsSyncOutcome.Pushed(1), outcome)
        val written = SettingsSyncPayload.parse(SettingsEvent.payload(remote.writes.last())!!)!!
        assertEquals(listOf("edited"), written.categoryStyleRules()?.map { it.id })
    }

    @Test
    fun `nothing moves when both sides agree`() = runBlocking {
        val remote = FakeRemote(remoteEvent(listOf(rule()), "2026-11-21T00:00:00Z"))
        val state = FakeState(lastPulledAt = Instant.parse("2026-11-21T00:00:00Z"), rules = listOf(rule()))
        val outcome = SettingsSyncer(remote, state) { Instant.parse("2026-11-22T10:00:00Z") }
            .synchronize(home, credentials)

        assertEquals(SettingsSyncOutcome.Unchanged, outcome)
        assertTrue(remote.writes.isEmpty())
    }

    @Test
    fun `a conflict is retried once against the fresh remote`() = runBlocking {
        val remote = FakeRemote(remoteEvent(listOf(rule(id = "other-device")), "2026-11-18T09:00:00Z"), conflictOnce = true)
        val state = FakeState(
            lastPulledAt = Instant.parse("2026-11-19T00:00:00Z"),
            rulesChangedAt = Instant.parse("2026-11-22T08:00:00Z"),
            rules = listOf(rule(id = "mine")),
        )
        val outcome = SettingsSyncer(remote, state) { Instant.parse("2026-11-22T10:00:00Z") }
            .synchronize(home, credentials)

        assertEquals(SettingsSyncOutcome.Pushed(1), outcome)
        assertEquals(1, remote.writes.size)
        assertEquals(2, remote.reads)
        val written = SettingsSyncPayload.parse(SettingsEvent.payload(remote.writes.single())!!)!!
        assertEquals(listOf("mine"), written.categoryStyleRules()?.map { it.id })
        assertEquals("Europe/Copenhagen", written.settings.getString("timezone"))
    }

    @Test
    fun `disabled settings sync touches nothing at all`() = runBlocking {
        val remote = FakeRemote()
        val state = FakeState(enabled = false, rules = listOf(rule()))
        val outcome = SettingsSyncer(remote, state) { Instant.parse("2026-11-22T10:00:00Z") }
            .synchronize(home, credentials)

        assertEquals(SettingsSyncOutcome.Skipped, outcome)
        assertEquals(0, remote.reads)
        assertTrue(remote.writes.isEmpty())
    }
}
