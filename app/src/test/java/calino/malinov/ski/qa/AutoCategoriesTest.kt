package calino.malinov.ski.qa

import calino.malinov.ski.data.model.AutoCategoryRule
import calino.malinov.ski.data.model.EditorDraft
import calino.malinov.ski.data.model.applyAutoCategories
import calino.malinov.ski.data.model.autoCategoriesFor
import calino.malinov.ski.data.model.decodeAutoCategoryRules
import calino.malinov.ski.data.model.decodeCategoryNames
import calino.malinov.ski.data.model.encodeAutoCategoryRules
import calino.malinov.ski.data.model.encodeCategoryNames
import calino.malinov.ski.data.model.withAutoCategories
import calino.malinov.ski.data.model.withCategoryToggled
import calino.malinov.ski.data.parser.PocQuickAddKind
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class AutoCategoriesTest {
    private val rules = listOf(
        AutoCategoryRule("r1", listOf("standup", "sync"), "Work"),
        AutoCategoryRule("r2", listOf("gym"), "Health"),
    )

    @Test fun matchesCaseInsensitiveSubstring() {
        assertEquals(listOf("Work"), autoCategoriesFor("Team STANDUP", rules))
        assertEquals(listOf("Work"), autoCategoriesFor("Resync backlog", rules))
    }

    @Test fun multipleRulesApply() {
        assertEquals(listOf("Work", "Health"), autoCategoriesFor("Sync at the gym", rules))
    }

    @Test fun noMatchOrBlankTitleAddsNothing() {
        assertEquals(emptyList<String>(), autoCategoriesFor("Dinner", rules))
        assertEquals(emptyList<String>(), autoCategoriesFor("  ", rules))
    }

    @Test fun keepsExistingAndDoesNotDuplicate() {
        assertEquals(
            listOf("Personal", "Work"),
            applyAutoCategories(listOf("Personal", "Work"), "standup", rules),
        )
    }

    @Test fun blankKeywordsAndCategoriesAreIgnored() {
        val odd = listOf(
            AutoCategoryRule("a", listOf("", " "), "Work"),
            AutoCategoryRule("b", listOf("x"), " "),
        )
        assertEquals(emptyList<String>(), autoCategoriesFor("x marks", odd))
    }

    @Test fun sharedKeywordGoesToTheLaterRuleLikeTheWeb() {
        val clash = listOf(
            AutoCategoryRule("a", listOf("run"), "Health"),
            AutoCategoryRule("b", listOf("run"), "Sports"),
        )
        assertEquals(listOf("Sports"), autoCategoriesFor("Morning run", clash))
    }
}

class AutoCategoryCodecTest {
    @Test fun rulesRoundTripAndStripSeparators() {
        val rules = listOf(
            AutoCategoryRule("r1", listOf("stand\tup", "sync"), "Work"),
            AutoCategoryRule("r2", listOf("gym"), "Health\nClub"),
        )
        val decoded = decodeAutoCategoryRules(
            encodeAutoCategoryRules(rules),
        )
        assertEquals(
            listOf(
                AutoCategoryRule("r1", listOf("standup", "sync"), "Work"),
                AutoCategoryRule("r2", listOf("gym"), "HealthClub"),
            ),
            decoded,
        )
    }

    @Test fun malformedOrMissingStorageDecodesEmpty() {
        assertEquals(emptyList<AutoCategoryRule>(), decodeAutoCategoryRules(null))
        assertEquals(emptyList<AutoCategoryRule>(), decodeAutoCategoryRules("junk\nx\ty"))
    }

    @Test fun categoryNamesRoundTripInOrder() {
        val names = listOf("Work", " Gym ", "", "Work")
        assertEquals(
            listOf("Work", "Gym"),
            decodeCategoryNames(
                encodeCategoryNames(names),
            ),
        )
    }
}

class DraftAutoCategoriesTest {
    private val rules = listOf(
        AutoCategoryRule("r1", listOf("standup"), "Work"),
        AutoCategoryRule("r2", listOf("gym"), "Health"),
    )
    private fun event(title: String, categories: List<String> = emptyList()) = EditorDraft(
        kind = PocQuickAddKind.Event, title = title, date = LocalDate.of(2026, 5, 18), categories = categories,
    )

    @Test fun matchAppearsAndLeavesAsTheTitleIsTyped() {
        val typed = event("standup").withAutoCategories(rules)
        assertEquals(listOf("Work"), typed.categories)
        val retyped = typed.copy(title = "stand").withAutoCategories(rules)
        assertEquals(emptyList<String>(), retyped.categories)
    }

    @Test fun chosenCategoryIsNeverTakenOff() {
        val draft = event("standup", listOf("Work")).withAutoCategories(rules)
        assertEquals(emptySet<String>(), draft.autoCategories)
        assertEquals(listOf("Work"), draft.copy(title = "lunch").withAutoCategories(rules).categories)
    }

    @Test fun dismissedMatchStaysOff() {
        val dismissed = event("standup").withAutoCategories(rules).withCategoryToggled("Work", single = false)
        assertEquals(emptyList<String>(), dismissed.categories)
        assertEquals(setOf("Work"), dismissed.dismissedAutoCategories)
        assertEquals(
            listOf("Health"),
            dismissed.copy(title = "standup then gym").withAutoCategories(rules).categories,
        )
    }

    @Test fun tappingARuleCategoryOnMakesItTheirs() {
        val dismissed = event("standup").withAutoCategories(rules).withCategoryToggled("Work", single = false)
        val back = dismissed.withCategoryToggled("Work", single = false)
        assertEquals(listOf("Work"), back.categories)
        assertEquals(emptySet<String>(), back.autoCategories)
        assertEquals(listOf("Work"), back.copy(title = "lunch").withAutoCategories(rules).categories)
    }

    @Test fun taskFillsOnlyAnEmptySlot() {
        val task = event("standup at the gym").copy(kind = PocQuickAddKind.Task)
        assertEquals(listOf("Work"), task.withAutoCategories(rules).categories)
        assertEquals(listOf("Admin"), task.copy(categories = listOf("Admin")).withAutoCategories(rules).categories)
    }

    @Test fun deviceCalendarsAndJournalsAreLeftAlone() {
        assertEquals(emptyList<String>(), event("standup").copy(calendarId = "android:4").withAutoCategories(rules).categories)
        assertEquals(
            emptyList<String>(),
            event("standup").copy(kind = PocQuickAddKind.Journal).withAutoCategories(rules).categories,
        )
    }
}
