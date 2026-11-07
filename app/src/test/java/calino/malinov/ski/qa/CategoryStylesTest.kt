package calino.malinov.ski.qa

import calino.malinov.ski.data.model.CategoryAppearance
import calino.malinov.ski.data.model.CategoryStyleFont
import calino.malinov.ski.data.model.CategoryStyleRule
import calino.malinov.ski.data.model.CategoryStyleTreatment
import calino.malinov.ski.data.model.DefaultCategoryStyleRules
import calino.malinov.ski.data.model.decodeCategoryStyleRules
import calino.malinov.ski.data.model.encodeCategoryStyleRules
import calino.malinov.ski.data.model.resolveCategoryAppearance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The web's `categoryStyles.test.ts`, rule for rule, plus the storage codec. */
class CategoryStylesTest {
    private val blue = 0xFF607FA3.toInt()
    private val red = 0xFFB64248.toInt()
    private val grey = 0xFF777985.toInt()

    private fun rule(
        categories: String,
        treatment: CategoryStyleTreatment,
        font: CategoryStyleFont = CategoryStyleFont.Inherit,
        color: Int = grey,
        enabled: Boolean = true,
        id: String = categories + treatment.key,
    ) = CategoryStyleRule(id, categories, treatment, font, color, enabled)

    private fun resolve(vararg categories: String, rules: List<CategoryStyleRule> = DefaultCategoryStyleRules) =
        resolveCategoryAppearance(categories.toList(), rules)

    @Test fun tentativeSynonymsUseTextureByDefault() {
        for (name in listOf("not confirmed", "unconfirmed", "tentative", "maybe")) {
            assertEquals(name, CategoryAppearance(texture = blue), resolve(name))
        }
    }

    @Test fun matchesWholeNamesIgnoringCaseAndWhitespace() {
        assertEquals(CategoryAppearance(sketch = red), resolve("  IMPORTANT "))
        assertEquals(CategoryAppearance.None, resolve("Very Important"))
        assertEquals(CategoryAppearance.None, resolve())
    }

    @Test fun sketchAndTextureCompose() {
        assertEquals(CategoryAppearance(sketch = red, texture = blue), resolve("important", "maybe"))
    }

    @Test fun lastMatchingRuleWinsEachProperty() {
        val pencil = rule("maybe", CategoryStyleTreatment.Tentative, CategoryStyleFont.Hand)
        val first = rule("maybe", CategoryStyleTreatment.Important, color = 0xFFFF0000.toInt(), id = "a")
        val second = first.copy(id = "b", colorArgb = 0xFF0000FF.toInt())
        val a = resolve("maybe", rules = listOf(pencil, first, second))
        assertEquals(0xFF0000FF.toInt(), a.sketch)
        assertEquals(CategoryStyleFont.Hand, a.font)
        assertTrue(a.pencil)
        assertEquals(0xFFFF0000.toInt(), resolve("maybe", rules = listOf(second, first, pencil)).sketch)
        // A rule that asks for a font overrides one before it; "inherit" never clears one.
        assertEquals(
            CategoryStyleFont.Standard,
            resolve("maybe", rules = listOf(pencil, first.copy(font = CategoryStyleFont.Standard))).font,
        )
    }

    @Test fun disabledRulesAndBlankCategoryListsMatchNothing() {
        val rules = listOf(
            rule("maybe", CategoryStyleTreatment.Important, enabled = false),
            rule(", ,", CategoryStyleTreatment.Tentative),
        )
        assertEquals(CategoryAppearance.None, resolve("maybe", rules = rules))
    }

    @Test fun underlineAndStrikeFollowRuleOrder() {
        val done = rule("maybe", CategoryStyleTreatment.Done)
        val deadline = rule("maybe", CategoryStyleTreatment.Deadline)
        val doneLast = resolve("maybe", rules = listOf(deadline, done))
        assertTrue(doneLast.done)
        assertNull(doneLast.marker)
        val deadlineLast = resolve("maybe", rules = listOf(done, deadline))
        assertTrue(!deadlineLast.done)
        assertEquals(grey, deadlineLast.marker)
    }

    @Test fun eachTreatmentSetsItsOwnProperties() {
        assertEquals(CategoryAppearance(fill = grey, pencil = true), resolve("x", rules = listOf(rule("x", CategoryStyleTreatment.Tentative))))
        assertEquals(CategoryAppearance(fill = grey, dashed = grey), resolve("x", rules = listOf(rule("x", CategoryStyleTreatment.Focus))))
        assertEquals(CategoryAppearance(fill = grey, marker = grey), resolve("x", rules = listOf(rule("x", CategoryStyleTreatment.Deadline))))
        assertEquals(CategoryAppearance(fill = grey, pencil = true, done = true), resolve("x", rules = listOf(rule("x", CategoryStyleTreatment.Done))))
    }

    @Test fun surfaceOnlyAppearancesKeepThePlainTitle() {
        assertTrue(resolve("maybe").plainTitle)
        assertTrue(!resolve("important").plainTitle)
    }

    @Test fun codecRoundTripsAndKeepsOrder() {
        val rules = listOf(
            rule("work, health", CategoryStyleTreatment.Focus, CategoryStyleFont.Hand, 0xFF648574.toInt(), id = "1"),
            rule("later", CategoryStyleTreatment.Done, enabled = false, id = "2"),
        )
        assertEquals(rules, decodeCategoryStyleRules(encodeCategoryStyleRules(rules)))
    }

    @Test fun neverEditedMeansDefaultsButEmptyStaysEmpty() {
        assertEquals(DefaultCategoryStyleRules, decodeCategoryStyleRules(null))
        assertEquals(emptyList<CategoryStyleRule>(), decodeCategoryStyleRules(encodeCategoryStyleRules(emptyList())))
    }

    @Test fun malformedAndDuplicateLinesAreDroppedWithoutLosingTheRest() {
        val good = encodeCategoryStyleRules(listOf(rule("a", CategoryStyleTreatment.Focus, id = "ok")))
        val stored = listOf(
            "bad\tonly\tthree",
            "x\ta\tnope\tinherit\t#112233\t1",
            "y\ta\tfocus\tcomic\t#112233\t1",
            "z\ta\tfocus\tinherit\tred\t1",
            "\ta\tfocus\tinherit\t#112233\t1",
            good,
            good,
        ).joinToString("\n")
        assertEquals(listOf("ok"), decodeCategoryStyleRules(stored).map { it.id })
    }

    @Test fun separatorsInCategoryNamesCannotSplitALine() {
        val stored = encodeCategoryStyleRules(listOf(rule("a\tb\nc", CategoryStyleTreatment.Focus, id = "1")))
        val decoded = decodeCategoryStyleRules(stored)
        assertEquals(1, decoded.size)
        assertEquals("abc", decoded[0].categories)
    }
}
