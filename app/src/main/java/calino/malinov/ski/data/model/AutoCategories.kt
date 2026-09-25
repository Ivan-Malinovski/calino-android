package calino.malinov.ski.data.model

import calino.malinov.ski.data.parser.PocQuickAddKind
import calino.malinov.ski.platform.AndroidCalendarId

/**
 * Keyword auto-categorization, matching the web app's `applyAutoCategories`.
 *
 * A rule names the category it adds, not an id: Android has no category
 * definitions beyond names, and `CATEGORIES` itself carries names. Matching is
 * the web's: title only, case-insensitive substring, no word boundaries, every
 * matching rule applies. It only ever adds; a category the person chose is
 * never removed. Kept free of Android so it runs in the plain-JUnit suite.
 */
data class AutoCategoryRule(
    val id: String,
    val keywords: List<String>,
    val category: String,
)

/** Categories whose keywords occur in [title], in keyword order, without duplicates. */
fun autoCategoriesFor(title: String, rules: List<AutoCategoryRule>): List<String> {
    val lowerTitle = title.lowercase()
    if (lowerTitle.isBlank()) return emptyList()
    // Keyword -> category, later rules overwriting earlier ones, as the web's Map does.
    val byKeyword = LinkedHashMap<String, String>()
    for (rule in rules) {
        val category = rule.category.trim()
        if (category.isEmpty()) continue
        for (keyword in rule.keywords) {
            val needle = keyword.trim().lowercase()
            if (needle.isNotEmpty()) byKeyword[needle] = category
        }
    }
    val matched = LinkedHashSet<String>()
    for ((needle, category) in byKeyword) {
        if (lowerTitle.contains(needle)) matched += category
    }
    return matched.toList()
}

/** [existing] plus the categories [title] matches, deduplicated, existing order first. */
fun applyAutoCategories(
    existing: List<String>,
    title: String,
    rules: List<AutoCategoryRule>,
): List<String> = (existing + autoCategoriesFor(title, rules)).distinct()

// Storage codec. SharedPreferences holds plain strings, and org.json is an
// Android stub in the unit suite, so rules are one per line as
// `id \t category \t keyword\u001Fkeyword`. Separators are stripped from values
// on the way in, so a stored line can never be split differently on the way out.
private const val KeywordSeparator = '\u001F'

private fun String.storable(): String =
    filterNot { it == '\t' || it == '\n' || it == '\r' || it == KeywordSeparator }.trim()

fun encodeAutoCategoryRules(rules: List<AutoCategoryRule>): String =
    rules.joinToString("\n") { rule ->
        listOf(
            rule.id.storable(),
            rule.category.storable(),
            rule.keywords.map { it.storable() }.filter { it.isNotEmpty() }
                .joinToString(KeywordSeparator.toString()),
        ).joinToString("\t")
    }

fun decodeAutoCategoryRules(stored: String?): List<AutoCategoryRule> =
    stored.orEmpty().lineSequence().mapNotNull { line ->
        val parts = line.split('\t')
        if (parts.size != 3 || parts[0].isEmpty() || parts[1].isEmpty()) return@mapNotNull null
        AutoCategoryRule(
            id = parts[0],
            keywords = parts[2].split(KeywordSeparator).filter { it.isNotEmpty() },
            category = parts[1],
        )
    }.toList()

/** Category names the person defined, in their order. */
fun encodeCategoryNames(names: List<String>): String =
    names.map { it.storable() }.filter { it.isNotEmpty() }.distinct().joinToString("\n")

fun decodeCategoryNames(stored: String?): List<String> =
    stored.orEmpty().lines().map { it.trim() }.filter { it.isNotEmpty() }.distinct()

/**
 * Re-runs [rules] against the draft's title. Called by the editor only when the
 * title changes (and once for a new draft), so opening and saving a record
 * without touching its title never adds anything.
 *
 * Categories the person chose are never removed. A rule category that stops
 * matching as the title is typed is taken back off, and one the person
 * dismissed stays off. Tasks carry one category, so a rule only fills an empty
 * slot. Device-calendar targets are skipped: that writer does not store
 * categories.
 */
fun EditorDraft.withAutoCategories(rules: List<AutoCategoryRule>): EditorDraft {
    if (kind == PocQuickAddKind.Journal) return this
    if (AndroidCalendarId.isImported(calendarId)) return this
    val chosen = categories.filterNot { it in autoCategories }
    val matched = autoCategoriesFor(title, rules).filterNot { it in dismissedAutoCategories || it in chosen }
    return when (kind) {
        PocQuickAddKind.Task -> {
            val auto = if (chosen.isEmpty()) matched.take(1) else emptyList()
            copy(categories = chosen + auto, autoCategories = auto.toSet())
        }
        else -> copy(categories = chosen + matched, autoCategories = matched.toSet())
    }
}

/** A chip tap. Taking a rule category off dismisses it; tapping one on makes it the person's own. */
fun EditorDraft.withCategoryToggled(category: String, single: Boolean): EditorDraft {
    val on = category in categories
    val next = when {
        single -> if (on) emptyList() else listOf(category)
        on -> categories - category
        else -> categories + category
    }
    val removed = categories.filterNot { it in next }
    return copy(
        categories = next,
        autoCategories = autoCategories - category - removed.toSet(),
        dismissedAutoCategories = dismissedAutoCategories - category +
            removed.filter { it in autoCategories },
    )
}
