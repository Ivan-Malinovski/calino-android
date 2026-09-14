package calino.malinov.ski.data.caldav

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VCardPatcherTest {

    private val patcher = VCardPatcher()

    @Test
    fun `queued contact update keeps remote properties while applying local changes`() {
        val base = card(
            "FN:Original",
            "EMAIL:original@example.com",
            "X-FOREIGN:base",
        )
        val local = card(
            "FN:Local edit",
            "EMAIL:original@example.com",
            "X-FOREIGN:base",
        )
        val current = card(
            "FN:Remote edit",
            "EMAIL:remote@example.com",
            "X-FOREIGN:remote",
            "TEL:+4512345678",
        )

        val rebased = patcher.rebaseResource(current, local, base)!!

        assertTrue(rebased.contains("FN:Local edit"))
        assertFalse(rebased.contains("FN:Remote edit"))
        assertTrue(rebased.contains("EMAIL:remote@example.com"))
        assertTrue(rebased.contains("X-FOREIGN:remote"))
        assertTrue(rebased.contains("TEL:+4512345678"))
    }

    @Test
    fun `rebase refuses malformed or mismatched cards`() {
        val base = card("FN:Original")
        assertNull(patcher.rebaseResource("not a card", base, base))
        val mismatched = card("FN:Local").replace("UID:person-1", "UID:other")
        assertNull(patcher.rebaseResource(base, mismatched, base))
    }

    private fun card(vararg lines: String): String = buildString {
        append("BEGIN:VCARD\nVERSION:3.0\nUID:person-1\n")
        lines.forEach { append(it).append('\n') }
        append("END:VCARD\n")
    }
}
