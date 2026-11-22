package calino.malinov.ski.qa

import calino.malinov.ski.ui.surfaces.CalinoSearchCommand
import calino.malinov.ski.ui.surfaces.matchingCommands
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The search sheet does things as well as finding them. A query is matched
 * against every word of a command's label, so the palette answers what the
 * person typed instead of offering a menu to read.
 */
class SearchCommandsTest {
    private val commands = listOf(
        CalinoSearchCommand("command:new-event", "New event"),
        CalinoSearchCommand("command:new-task", "New task"),
        CalinoSearchCommand("command:sync", "Sync now"),
        CalinoSearchCommand("route:Settings", "Go to Settings"),
        CalinoSearchCommand("route:Tasks", "Go to Tasks"),
    )

    private fun ids(query: String) = matchingCommands(commands, query).map { it.id }

    @Test fun everyWordOfTheQueryHasToBeInTheLabel() {
        assertEquals(listOf("command:new-task"), ids("new task"))
        assertEquals(listOf("command:new-task"), ids("task new"))
        assertEquals(listOf("command:sync"), ids("sync now"))
        // "sync" alone is still a command; "settings" alone is the route.
        assertEquals(listOf("command:sync"), ids("sync"))
        assertEquals(listOf("route:Settings"), ids("settings"))
    }

    @Test fun aPartiallyMatchingQueryDoesNotAnswerWithTheWrongCommand() {
        // "new event" must not answer with "New task", and vice versa.
        assertEquals(listOf("command:new-event"), ids("new event"))
        assertTrue("new event" !in ids("task new"))
        assertEquals(emptyList<String>(), ids("now event"))
    }

    @Test fun matchingIgnoresCaseAndSurroundingSpace() {
        assertEquals(listOf("route:Tasks"), ids("  TASKS  "))
        assertEquals(listOf("command:sync"), ids("SyNc"))
    }

    @Test fun aBlankQueryNamesNoCommand() {
        // The sheet offers its four reachable commands itself; matching must not
        // claim them (that would make an empty query look like a match list).
        assertEquals(emptyList<String>(), ids(""))
        assertEquals(emptyList<String>(), ids("   "))
    }

    @Test fun nothingIsOfferedWhenNothingMatches() {
        assertEquals(emptyList<String>(), ids("quux"))
    }
}
