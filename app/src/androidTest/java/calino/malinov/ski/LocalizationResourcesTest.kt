package calino.malinov.ski

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import calino.malinov.ski.util.formatCalinoDuration
import calino.malinov.ski.util.formatRecurrenceRule
import calino.malinov.ski.util.localizedReason
import calino.malinov.ski.data.repository.WriteResult
import calino.malinov.ski.data.repository.WriteRejectionCode
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalizationResourcesTest {
    private fun inLanguage(tag: String): Context {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configuration = Configuration(context.resources.configuration).apply {
            setLocales(LocaleList.forLanguageTags(tag))
        }
        return context.createConfigurationContext(configuration)
    }

    @Test fun danishUsesResourcesAndLocalizedRecurrenceWeekdays() {
        val context = inLanguage("da")
        assertEquals("Ny begivenhed", context.getString(R.string.host_new_event))
        assertEquals("1 t 30 min", formatCalinoDuration(context, 90))
        assertEquals("Hver mandag", formatRecurrenceRule(context, "FREQ=WEEKLY;BYDAY=MO", LocalDate.of(2026, 5, 18)))
        assertEquals("Opgavepåmindelse", context.getString(R.string.sys_task_reminder))
    }

    @Test fun germanFormatsArgumentsAndCalendarVocabulary() {
        val context = inLanguage("de")
        assertEquals("Neue Aufgabe", context.getString(R.string.host_new_task))
        assertEquals("1 Std. 30 Min.", formatCalinoDuration(context, 90))
        assertEquals("Jeden Montag", formatRecurrenceRule(context, "FREQ=WEEKLY;BYDAY=MO", LocalDate.of(2026, 5, 18)))
        assertEquals("Hinzufügen am 18. Mai", context.getString(R.string.host_add_on, "18. Mai"))
    }

    @Test fun unsupportedLanguageFallsBackToEnglish() {
        assertEquals("New task", inLanguage("fr").getString(R.string.host_new_task))
    }

    @Test fun quantitiesAndComposedLabelsRetainGrammarAndSpacing() {
        val context = inLanguage("da")
        assertEquals("OPGAVER MED FRIST · 1 ÅBEN", context.resources.getQuantityString(R.plurals.cal_tasks_due_heading, 1, 1))
        assertEquals("OPGAVER MED FRIST · 2 ÅBNE", context.resources.getQuantityString(R.plurals.cal_tasks_due_heading, 2, 2))
        assertEquals("Tilføj den ", context.getString(R.string.cal_add_on_prefix))
        assertEquals(" (Kopie)", inLanguage("de").getString(R.string.host_copy_suffix))
    }

    @Test fun typedRefusalsTranslateButUnknownDiagnosticsArePreserved() {
        val context = inLanguage("de")
        val refusal = WriteResult.Rejected(WriteRejectionCode.REPEATING_TASK_NEEDS_DUE_DATE)
        assertEquals(context.getString(R.string.err_repeating_task_needs_due_date), refusal.localizedReason(context))
        assertEquals("Raw server detail", WriteResult.Rejected("Raw server detail").localizedReason(context))
    }

    @Test fun debugPseudolocalesExpandAndMirrorResourceText() {
        val english = inLanguage("en").getString(R.string.host_new_task)
        assertFalse(english == inLanguage("en-XA").getString(R.string.host_new_task))
        val rtl = inLanguage("ar-XB")
        assertFalse(english == rtl.getString(R.string.host_new_task))
        assertEquals(android.view.View.LAYOUT_DIRECTION_RTL, rtl.resources.configuration.layoutDirection)
    }
}
