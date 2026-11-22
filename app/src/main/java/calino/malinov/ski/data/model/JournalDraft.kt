package calino.malinov.ski.data.model

import java.time.LocalDate

/**
 * An editor-only Journal draft. Drafts intentionally have no repository
 * representation until they contain some content and are saved.
 */
data class JournalDraft(
    val id: String,
    val date: LocalDate,
) {
    fun commit(title: String, body: String, categories: List<String> = emptyList()): JournalEntry? {
        val normalizedTitle = title.trim()
        val normalizedBody = body.trim()
        if (normalizedTitle.isBlank() && normalizedBody.isBlank()) return null
        // The tags travel with the entry. Dropping them here kept a new entry's
        // categories out of the first write, while an edit of an existing one
        // kept them -- the kind of difference only a real save would show.
        return JournalEntry(
            id = id,
            date = date,
            title = normalizedTitle,
            body = normalizedBody,
            categories = categories.map(String::trim).filter(String::isNotEmpty).distinct(),
        )
    }
}
