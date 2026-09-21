package calino.malinov.ski.data

import org.junit.Assert.assertEquals
import org.junit.Test

class WritableImportedCalendarsTest {
    @Test fun newlyImportedProviderWritableCalendarsDefaultToWritable() {
        assertEquals(
            setOf("work"),
            writableImportsAfterSelection(
                previousImported = emptySet(),
                selectedImported = setOf("work", "holidays"),
                currentlyWritable = emptySet(),
                providerWritable = setOf("work"),
            ),
        )
    }

    @Test fun anExplicitlyDisabledExistingImportStaysDisabled() {
        assertEquals(
            emptySet<String>(),
            writableImportsAfterSelection(
                previousImported = setOf("work"),
                selectedImported = setOf("work"),
                currentlyWritable = emptySet(),
                providerWritable = setOf("work"),
            ),
        )
    }

    @Test fun removingAnImportAlsoRemovesItsWriteChoice() {
        assertEquals(
            setOf("personal"),
            writableImportsAfterSelection(
                previousImported = setOf("work", "personal"),
                selectedImported = setOf("personal"),
                currentlyWritable = setOf("work", "personal"),
                providerWritable = setOf("work", "personal"),
            ),
        )
    }
}
