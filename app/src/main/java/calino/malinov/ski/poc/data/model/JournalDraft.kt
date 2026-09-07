package calino.malinov.ski.poc.data.model

import java.time.LocalDate

/**
 * An editor-only Journal draft. Drafts intentionally have no repository
 * representation until they contain some content and are saved.
 */
data class JournalDraft(
    val id: String,
    val date: LocalDate,
) {
    fun commit(title: String, body: String): JournalEntry? {
        val normalizedTitle = title.trim()
        val normalizedBody = body.trim()
        if (normalizedTitle.isBlank() && normalizedBody.isBlank()) return null
        return JournalEntry(id, date, normalizedTitle, normalizedBody)
    }
}
