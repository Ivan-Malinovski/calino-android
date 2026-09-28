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

    @Test fun `server refusing the payload drops instead of retrying`() {
        for (status in listOf(400, 415, 422)) {
            val error = calino.malinov.ski.data.caldav.calDavErrorForStatus(
                status,
                "https://dav.example/cal/task.ics",
            )
            assertEquals(CalDavErrorCode.Rejected, error.code)

            val result = classifyWriteError(error, PendingChangeType.UPDATE)

            assertTrue(result.disposition is WriteDisposition.Drop)
            assertEquals(
                "The server refused this item as invalid ($status). Edit it and save again, or revert the change.",
                result.message,
            )
        }
    }
}
