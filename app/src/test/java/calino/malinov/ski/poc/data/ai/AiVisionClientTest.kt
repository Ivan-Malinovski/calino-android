package calino.malinov.ski.poc.data.ai

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiVisionClientTest {
    private val client = AiVisionClient()

    @Test fun parsesArrayAndDropsUnknownEnumValues() {
        val result = client.parseCandidates("""[{"title":"Open studio","start":"2026-09-12T19:30","confidence":"certain","kind":"meeting"}]""")
        assertEquals("Open studio", result.single().title)
        assertEquals(LocalDateTime.of(2026, 9, 12, 19, 30), result.single().start)
        assertNull(result.single().confidence)
        assertEquals("event", result.single().kind)
    }

    @Test fun salvagesFencedArrayAndCapsCandidates() {
        val body = (1..7).joinToString(",") { "{\"title\":\"Item $it\",\"kind\":\"task\"}" }
        val result = client.parseCandidates("```json\n[$body]\n```")
        assertEquals(5, result.size)
        assertEquals("task", result.last().kind)
    }

    @Test fun acceptsBareObject() {
        val result = client.parseCandidates("Result: {\"location\":\"Library\",\"allDay\":true} done")
        assertEquals("Library", result.single().location)
        assertEquals(true, result.single().allDay)
    }
}
