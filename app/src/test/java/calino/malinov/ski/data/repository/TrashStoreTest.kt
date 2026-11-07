package calino.malinov.ski.data.repository

import java.io.File
import java.time.Duration
import java.time.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TrashStoreTest {
    private lateinit var dir: File
    private var now = Instant.parse("2026-05-18T10:00:00Z")

    @Before fun setUp() { dir = File.createTempFile("calino-trash", "").also { it.delete(); it.mkdirs() } }
    @After fun tearDown() { dir.deleteRecursively() }

    private fun entry(id: String, account: String = "a", at: Instant = now, body: String = "BEGIN:VCALENDAR\r\nX-FOREIGN:keep \"me\"\r\nEND:VCALENDAR\r\n") =
        TrashEntry(id, TrashKind.Task, "Title $id", account, "https://x/c/", "https://x/c/$id.ics", "uid-$id", listOf("r-$id"), body, at)

    private fun file() = FileTrashStore(File(dir, "trash.json.gz")) { now }

    @Test fun `round trip keeps every field and the raw body`() {
        val original = entry("1")
        file().add(original)
        val reloaded = file()
        assertEquals(listOf(original), reloaded.list())
        assertEquals(original, reloaded.get("1"))
    }

    @Test fun `newest entry is listed first`() {
        val store = file()
        store.add(entry("1")); store.add(entry("2"))
        assertEquals(listOf("2", "1"), store.list().map { it.id })
    }

    @Test fun `entries expire after thirty days`() {
        val store = file()
        store.add(entry("old", at = now.minus(Duration.ofDays(31))))
        store.add(entry("fresh", at = now.minus(Duration.ofDays(29))))
        assertNull(store.get("old"))
        assertNotNull(store.get("fresh"))
        now = now.plus(Duration.ofDays(2))
        assertTrue(file().list().isEmpty())
    }

    @Test fun `store is capped and drops the oldest`() {
        val store = InMemoryTrashStore { now }
        repeat(TrashStore.MaxEntries + 5) { store.add(entry("e$it")) }
        val ids = store.list().map { it.id }
        assertEquals(TrashStore.MaxEntries, ids.size)
        assertEquals("e${TrashStore.MaxEntries + 4}", ids.first())
        assertTrue("e0" !in ids)
    }

    @Test fun `remove and clear persist`() {
        val store = file()
        store.add(entry("1")); store.add(entry("2"))
        store.remove("1")
        assertEquals(listOf("2"), file().list().map { it.id })
        store.clear()
        assertTrue(file().list().isEmpty())
    }

    @Test fun `removing accounts drops only their entries`() {
        val store = file()
        store.add(entry("1", account = "keep")); store.add(entry("2", account = "gone"))
        store.removeAccountsExcept(setOf("keep"))
        assertEquals(listOf("1"), file().list().map { it.id })
    }

    @Test fun `a corrupt file starts empty instead of crashing`() {
        File(dir, "trash.json.gz").writeBytes(byteArrayOf(1, 2, 3))
        assertTrue(file().list().isEmpty())
    }
}
