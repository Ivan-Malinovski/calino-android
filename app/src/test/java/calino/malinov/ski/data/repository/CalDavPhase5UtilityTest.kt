package calino.malinov.ski.data.repository

import calino.malinov.ski.data.caldav.CalDavErrorCode
import calino.malinov.ski.data.caldav.CalDavException
import calino.malinov.ski.data.caldav.DavPrecondition
import calino.malinov.ski.data.caldav.DiscoveredCalendar
import calino.malinov.ski.data.model.CalEvent
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalDavPhase5UtilityTest {

    @Test
    fun `same etag and sequence is the same version`() {
        val report = CalDavConflictResolver.inspect(
            CalDavRecordVersion("uid-1", etag = "W/\"same\"", sequence = 2),
            CalDavRecordVersion("uid-1", etag = "same", sequence = 2),
        )

        assertEquals(CalDavVersionDecision.SameVersion, report.decision)
        assertEquals(CalDavEtagRelation.Same, report.etagRelation)
        assertFalse(report.hasConflict)
    }

    @Test
    fun `different etags with equal sequence are an equal-version conflict`() {
        val report = CalDavConflictResolver.inspect(
            CalDavRecordVersion("uid-1", etag = "local", sequence = 4),
            CalDavRecordVersion("uid-1", etag = "remote", sequence = 4),
        )

        assertEquals(CalDavVersionDecision.EqualVersionConflict, report.decision)
        assertTrue(report.hasConflict)
        assertTrue(report.equalLogicalVersion)
    }

    @Test
    fun `missing etag keeps equal sequence conservative and never calls remote newer`() {
        val report = CalDavConflictResolver.inspect(
            CalDavRecordVersion("uid-1", etag = null, sequence = null),
            CalDavRecordVersion("uid-1", etag = "remote", sequence = 0),
        )

        assertEquals(CalDavEtagRelation.Unknown, report.etagRelation)
        assertEquals(CalDavVersionDecision.EqualVersionConflict, report.decision)
    }

    @Test
    fun `sequence chooses the newer side only when sequences differ`() {
        val local = VersionedCalDavRecord("local", CalDavRecordVersion("uid-1", "a", 5))
        val remote = VersionedCalDavRecord("remote", CalDavRecordVersion("uid-1", "b", 3))

        val result = CalDavConflictResolver.resolve(local, remote)

        val resolved = assertType<CalDavResolution.Resolved<String>>(result)
        assertEquals("local", resolved.record)
        assertEquals(ChosenSide.Local, resolved.chosen)
        assertEquals(CalDavVersionDecision.LocalNewer, resolved.report.decision)
    }

    @Test
    fun `equal-version policy can choose either side without timestamps`() {
        val local = VersionedCalDavRecord("local", CalDavRecordVersion("uid-1", "a", 2))
        val remote = VersionedCalDavRecord("remote", CalDavRecordVersion("uid-1", "b", 2))

        assertType<CalDavResolution.Conflict<String>>(
            CalDavConflictResolver.resolve(local, remote),
        )
        assertEquals(
            "local",
            assertType<CalDavResolution.Resolved<String>>(
                CalDavConflictResolver.resolve(local, remote, CalDavConflictPolicy.LocalWins),
            ).record,
        )
        assertEquals(
            "remote",
            assertType<CalDavResolution.Resolved<String>>(
                CalDavConflictResolver.resolve(local, remote, CalDavConflictPolicy.RemoteWins),
            ).record,
        )
    }

    @Test
    fun `model adapters use the existing uid etag fields`() {
        val event = event().copy(uid = "server-uid", href = "https://dav.test/a/item.ics", etag = "v1")
        assertEquals(
            CalDavRecordVersion("server-uid", "v1", 7),
            event.toCalDavRecordVersion(sequence = 7),
        )
    }

    @Test
    fun `move plan orders unconditional destination put before conditional source delete`() {
        val result = CalDavMovePlanner.plan(
            CalDavMoveRequest(
                members = listOf(event()),
                target = calendar("target"),
                payload = "BEGIN:VCALENDAR\r\nEND:VCALENDAR\r\n",
                source = CalDavMoveSource(
                    calendar = calendar("source"),
                    href = "https://dav.test/source/item.ics",
                    etag = "\"old\"",
                ),
            ),
        )

        assertTrue(result.isValid)
        val plan = result.plan!!
        assertEquals(2, plan.steps.size)
        assertType<CalDavMoveStep.PutDestination>(plan.steps[0])
        assertType<CalDavMoveStep.DeleteSource>(plan.steps[1])
        val put = plan.destination
        assertEquals(DavPrecondition.Unconditional, put.precondition)
        assertEquals("https://dav.test/target/uid-1.ics", put.href)
        val delete = assertType<CalDavMoveStep.DeleteSource>(plan.steps[1])
        assertEquals(DavPrecondition.Match("old"), delete.precondition)
        assertEquals("old", delete.etag)
    }

    @Test
    fun `local-only source produces a destination put with no delete`() {
        val result = CalDavMovePlanner.plan(
            CalDavMoveRequest(
                members = listOf(event().copy(href = null, etag = null)),
                target = calendar("target"),
                payload = "ics",
            ),
        )

        assertTrue(result.isValid)
        val plan = result.plan!!
        assertEquals(1, plan.steps.size)
        assertNull(plan.sourceDeletion)
    }

    @Test
    fun `planner rejects unsafe source and destination choices`() {
        val base = CalDavMoveRequest(
            members = listOf(event()),
            target = calendar("target"),
            payload = "ics",
            source = CalDavMoveSource(calendar("source"), "https://dav.test/source/item.ics", "old"),
        )

        assertEquals(
            CalDavMoveValidationError.TargetReadOnly,
            CalDavMovePlanner.plan(base.copy(target = calendar("target", readOnly = true))).error,
        )
        assertEquals(
            CalDavMoveValidationError.MissingSourceEtag,
            CalDavMovePlanner.plan(base.copy(source = base.source!!.copy(etag = ""))).error,
        )
        assertEquals(
            CalDavMoveValidationError.SourceHrefOutsideCollection,
            CalDavMovePlanner.plan(
                base.copy(source = base.source!!.copy(href = "https://dav.test/elsewhere/item.ics")),
            ).error,
        )
        assertEquals(
            CalDavMoveValidationError.MissingSourceMetadata,
            CalDavMovePlanner.plan(base.copy(source = null)).error,
        )
    }

    @Test
    fun `403 uid conflict is distinct from bare forbidden`() {
        val uidConflict = CalDavMoveFailureClassifier.classify(
            CalDavException(
                code = CalDavErrorCode.Forbidden,
                message = "forbidden",
                status = 403,
                body = "<C:no-uid-conflict xmlns:C=\"urn:ietf:params:xml:ns:caldav\"/>",
            ),
        )
        val bareForbidden = CalDavMoveFailureClassifier.classify(
            CalDavException(
                code = CalDavErrorCode.Forbidden,
                message = "permission denied",
                status = 403,
            ),
        )

        assertEquals(CalDavMoveFailureKind.UidConflict, uidConflict.kind)
        assertTrue(uidConflict.mayUseUidConflictFallback)
        assertEquals(CalDavMoveFailureKind.Forbidden, bareForbidden.kind)
        assertFalse(bareForbidden.mayUseUidConflictFallback)
    }

    @Test
    fun `409 conflict is also classified as a uid conflict`() {
        val failure = CalDavMoveFailureClassifier.classify(
            CalDavException(
                code = CalDavErrorCode.Conflict,
                message = "duplicate UID",
                status = 409,
            ),
        )

        assertEquals(CalDavMoveFailureKind.UidConflict, failure.kind)
    }

    private fun calendar(name: String, readOnly: Boolean = false): DiscoveredCalendar =
        DiscoveredCalendar(
            url = "https://dav.test/$name/",
            displayName = name,
            color = 0L,
            readOnly = readOnly,
            components = setOf("VEVENT"),
        )

    private fun event(): CalEvent = CalEvent(
        id = "uid-1",
        uid = "uid-1",
        title = "Move me",
        color = 0L,
        start = LocalDateTime.of(2026, 9, 10, 10, 0),
        durationMinutes = 60,
        calendarId = "source",
        href = "https://dav.test/source/item.ics",
        etag = "old",
    )

    private inline fun <reified T> assertType(value: Any?): T {
        assertTrue("Expected ${T::class.simpleName}, got ${value?.let { it::class.simpleName }}", value is T)
        return value as T
    }
}
