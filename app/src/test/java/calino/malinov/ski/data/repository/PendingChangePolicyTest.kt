package calino.malinov.ski.data.repository

import calino.malinov.ski.data.caldav.CalDavErrorCode
import calino.malinov.ski.data.caldav.CalDavException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingChangePolicyTest {

    @Test fun `404 update does not assert that the calendar itself is gone`() {
        val error = CalDavException(
            code = CalDavErrorCode.NotFound,
            message = "Not found",
            status = 404,
        )

        val result = classifyWriteError(error, PendingChangeType.UPDATE)

        assertTrue(result.disposition is WriteDisposition.Drop)
        assertEquals(
            "The server could not find that calendar or task. Refresh and try again.",
            result.message,
        )
    }
}
