package calino.malinov.ski.data.model

/**
 * Category-driven event styling, matching the web app's `categoryStyles`.
 *
 * A rule names categories (comma separated) and a treatment; the resolved
 * [CategoryAppearance] says how an event carrying one of them is drawn. The
 * rules are ordered, independent properties compose, and the last matching
 * rule wins each property. Matching is the web's: the category's name, trimmed
 * and case-insensitive, never a substring. Kept free of Android so it runs in
 * the plain-JUnit suite.
 */
enum class CategoryStyleTreatment(val key: String) {
    Tentative("tentative"),
    Important("important"),
    Focus("focus"),
    Travel("travel"),
    Deadline("deadline"),
    Done("done");

    companion object {
        fun fromKey(key: String): CategoryStyleTreatment? = entries.firstOrNull { it.key == key }
    }
}

enum class CategoryStyleFont(val key: String) {
    Inherit("inherit"),
    Standard("standard"),
    Hand("hand");

    companion object {
        fun fromKey(key: String): CategoryStyleFont? = entries.firstOrNull { it.key == key }
    }
}

/** [colorArgb] is opaque `0xFFRRGGBB`, the shape event colors already have. */
data class CategoryStyleRule(
    val id: String,
    val categories: String,
    val treatment: CategoryStyleTreatment,
    val font: CategoryStyleFont,
    val colorArgb: Int,
    val enabled: Boolean = true,
)

/** Every property is "not set" by default, so an unstyled event resolves to [None]. */
data class CategoryAppearance(
    /** Card background, mixed into the panel at 13%. */
    val fill: Int? = null,
    /** Greyed "pencil" ink for the title. */
    val pencil: Boolean = false,
    /** A hand-drawn pen loop around the title. */
    val sketch: Int? = null,
    /** A dashed card border. */
    val dashed: Int? = null,
    /** Diagonal hatching across the card. */
    val texture: Int? = null,
    /** Marker underline under the title. */
    val marker: Int? = null,
    /** Title struck through. */
    val done: Boolean = false,
    val font: CategoryStyleFont? = null,
) {
    /** True when only the card surface changes and the title keeps its plain layout. */
    val plainTitle: Boolean
        get() = sketch == null && marker == null && !done && font == null && !pencil

    companion object {
        val None = CategoryAppearance()
    }
}

private fun rgb(hex: String): Int = (0xFF000000L or hex.removePrefix("#").toLong(16)).toInt()

val DefaultCategoryStyleRules: List<CategoryStyleRule> = listOf(
    CategoryStyleRule("important", "important", CategoryStyleTreatment.Important, CategoryStyleFont.Inherit, rgb("b64248")),
    CategoryStyleRule(
        "tentative",
        "not confirmed, unconfirmed, tentative, maybe",
        CategoryStyleTreatment.Travel,
        CategoryStyleFont.Inherit,
        rgb("607fa3"),
    ),
)

/** One-tap starting points in Settings, the web's suggestions. */
val CategoryStylePresets: List<CategoryStyleRule> = listOf(
    CategoryStyleRule("", "draft", CategoryStyleTreatment.Tentative, CategoryStyleFont.Hand, rgb("777985")),
    CategoryStyleRule("", "focus", CategoryStyleTreatment.Focus, CategoryStyleFont.Inherit, rgb("648574")),
    CategoryStyleRule("", "travel", CategoryStyleTreatment.Travel, CategoryStyleFont.Inherit, rgb("607fa3")),
    CategoryStyleRule("", "deadline", CategoryStyleTreatment.Deadline, CategoryStyleFont.Inherit, rgb("ac7332")),
    CategoryStyleRule("", "done", CategoryStyleTreatment.Done, CategoryStyleFont.Inherit, rgb("777985")),
)

/** The category names a rule's comma list holds, lower-cased, blanks dropped. */
fun CategoryStyleRule.categoryNames(): Set<String> =
    categories.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()

fun resolveCategoryAppearance(
    eventCategories: List<String>,
    rules: List<CategoryStyleRule>,
): CategoryAppearance {
    if (eventCategories.isEmpty() || rules.isEmpty()) return CategoryAppearance.None
    val names = eventCategories.map { it.trim().lowercase() }.toSet()
    var result = CategoryAppearance.None
    for (rule in rules) {
        if (!rule.enabled || rule.categoryNames().none { it in names }) continue
        val color = rule.colorArgb
        result = when (rule.treatment) {
            CategoryStyleTreatment.Important -> result.copy(sketch = color)
            CategoryStyleTreatment.Tentative -> result.copy(fill = color, pencil = true)
            CategoryStyleTreatment.Focus -> result.copy(fill = color, pencil = false, dashed = color)
            CategoryStyleTreatment.Travel -> result.copy(texture = color)
            CategoryStyleTreatment.Deadline ->
                result.copy(fill = color, pencil = false, done = false, marker = color)
            CategoryStyleTreatment.Done ->
                result.copy(fill = color, pencil = true, marker = null, done = true)
        }
        if (rule.font != CategoryStyleFont.Inherit) result = result.copy(font = rule.font)
    }
    return result
}

// Storage codec, one rule per line as
// `id \t categories \t treatment \t font \t #rrggbb \t 1|0`, for the same reason
// as the keyword rules: org.json is an Android stub in the unit suite.
private fun String.storable(): String = filterNot { it == '\t' || it == '\n' || it == '\r' }.trim()

private fun Int.hex(): String = "#%06x".format(this and 0xFFFFFF)

fun encodeCategoryStyleRules(rules: List<CategoryStyleRule>): String =
    rules.joinToString("\n") { rule ->
        listOf(
            rule.id.storable(),
            rule.categories.storable(),
            rule.treatment.key,
            rule.font.key,
            rule.colorArgb.hex(),
            if (rule.enabled) "1" else "0",
        ).joinToString("\t")
    }

/**
 * Null means the person never edited the rules, so the defaults apply. An
 * empty string is a deliberately empty list and stays empty; a malformed line
 * is dropped rather than failing the rest, as the web's normalizer does.
 */
fun decodeCategoryStyleRules(stored: String?): List<CategoryStyleRule> {
    if (stored == null) return DefaultCategoryStyleRules
    val seen = HashSet<String>()
    return stored.lineSequence().mapNotNull { line ->
        val parts = line.split('\t')
        if (parts.size != 6) return@mapNotNull null
        val treatment = CategoryStyleTreatment.fromKey(parts[2]) ?: return@mapNotNull null
        val font = CategoryStyleFont.fromKey(parts[3]) ?: return@mapNotNull null
        if (!Regex("#[0-9a-fA-F]{6}").matches(parts[4])) return@mapNotNull null
        if (parts[0].isEmpty() || !seen.add(parts[0])) return@mapNotNull null
        CategoryStyleRule(parts[0], parts[1], treatment, font, rgb(parts[4]), parts[5] == "1")
    }.toList()
}
