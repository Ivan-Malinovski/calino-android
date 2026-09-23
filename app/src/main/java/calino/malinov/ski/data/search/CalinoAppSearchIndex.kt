package calino.malinov.ski.data.search

import android.content.Context
import androidx.appsearch.app.AppSearchSchema
import androidx.appsearch.app.AppSearchSession
import androidx.appsearch.app.GenericDocument
import androidx.appsearch.app.PutDocumentsRequest
import androidx.appsearch.app.RemoveByDocumentIdRequest
import androidx.appsearch.app.SearchSpec
import androidx.appsearch.app.SetSchemaRequest
import androidx.appsearch.platformstorage.PlatformStorage
import calino.malinov.ski.data.model.CalEvent
import calino.malinov.ski.data.model.CalTask
import calino.malinov.ski.data.model.Contact
import calino.malinov.ski.data.model.JournalEntry
import calino.malinov.ski.data.model.derivedDisplayName
import calino.malinov.ski.data.repository.CalinoSnapshot
import com.google.common.util.concurrent.ListenableFuture
import java.io.Closeable
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.resumeWithException

/**
 * The deliberately small payload kept in Calino's app-private AppSearch
 * database. Search results always resolve back to current repository models.
 */
data class CalinoIndexedRecord(
    val stableId: String,
    val searchText: String,
)

/**
 * Makes a retrieval-only view of an authoritative snapshot. Fixture exclusion
 * is owned by [CalinoContainer]; this mapper also applies calendar and feature
 * availability so hidden records never enter the index.
 */
fun appSearchRecords(
    snapshot: CalinoSnapshot,
    journalsEnabled: Boolean,
    contactsEnabled: Boolean,
): List<CalinoIndexedRecord> {
    val calendars = snapshot.calendars.associateBy { it.id }
    val visibleCalendars = calendars.values.filter { it.visible }.map { it.id }.toSet()
    val taskCalendars = calendars.values.filter { it.visible && it.showTasksInViews }.map { it.id }.toSet()

    val records = buildList {
        snapshot.events.asSequence()
            .filter { it.calendarId in visibleCalendars }
            .forEach { event -> add(event.toIndexedRecord(calendars[event.calendarId]?.name)) }

        snapshot.tasks.asSequence()
            .filter { it.calendarId in taskCalendars }
            .forEach(::addTask)

        if (journalsEnabled) {
            snapshot.journals.asSequence()
                .filter { journal -> journal.isFromVisibleCalendar(snapshot, visibleCalendars) }
                .forEach { journal -> addJournal(journal) }
        }

        if (contactsEnabled) {
            val enabledBooks = snapshot.addressBooks.filter { it.enabled }.map { it.id }.toSet()
            snapshot.contacts.asSequence()
                .filter { contact ->
                    if (contact.accountId.isBlank()) true
                    else contact.addressBookId in enabledBooks
                }
                .forEach(::addContact)
        }
    }
    return records
}

private fun CalEvent.toIndexedRecord(calendarName: String?) = CalinoIndexedRecord(
    stableId = eventSearchId(this),
    searchText = listOf(title, notes, location, categories.joinToString(" "), calendarName)
        .filterNotNull().joinToString(" ").normalizedIndexText(),
)

private fun CalTask.toIndexedRecord() = CalinoIndexedRecord(
    stableId = taskSearchId(this),
    searchText = listOf(title, notes, category).filterNotNull().joinToString(" ").normalizedIndexText(),
)

private fun MutableList<CalinoIndexedRecord>.addTask(task: CalTask) = add(task.toIndexedRecord())

private fun MutableList<CalinoIndexedRecord>.addJournal(journal: JournalEntry) = add(
    CalinoIndexedRecord(
        stableId = journalSearchId(journal),
        searchText = listOf(journal.title, journal.body).joinToString(" ").normalizedIndexText(),
    ),
)

private fun MutableList<CalinoIndexedRecord>.addContact(contact: Contact) = add(
    CalinoIndexedRecord(
        stableId = contactSearchId(contact),
        searchText = contact.searchableFields().joinToString(" ").normalizedIndexText(),
    ),
)

private fun JournalEntry.isFromVisibleCalendar(
    snapshot: CalinoSnapshot,
    visibleCalendarIds: Set<String>,
): Boolean {
    if (href.isNullOrBlank()) return true
    return sourceCalendarId(snapshot)?.let { it in visibleCalendarIds } == true
}

private fun JournalEntry.sourceCalendarId(snapshot: CalinoSnapshot): String? {
    val resource = href ?: return null
    // A VJOURNAL's model has no calendarId field. Its cached DAV href is a
    // resource below the collection URL, so the collection identity can be
    // recovered without storing the href or raw iCalendar in AppSearch.
    return snapshot.calendars.asSequence()
        .map { it.id }
        .filter { calendarId ->
            val collection = calendarId.trimEnd('/')
            resource == collection || resource.startsWith("$collection/")
        }
        .maxByOrNull(String::length)
}

private fun Contact.searchableFields(): List<String> = buildList {
    add(derivedDisplayName())
    addAll(emails.map { it.value })
    addAll(phones.map { it.value })
    add(nickname)
    add(organization)
    add(department)
    add(title)
    addresses.forEach { address ->
        addAll(listOf(address.street, address.city, address.region, address.postalCode, address.country))
    }
    add(note)
    addAll(categories)
    addAll(urls.map { it.value })
}

private const val MaxIndexedTextChars = 32_000

private fun String.normalizedIndexText(): String = normalizeSearchText(take(MaxIndexedTextChars))

private fun sha256(parts: List<String>): String {
    val digest = MessageDigest.getInstance("SHA-256")
    parts.forEach { part ->
        val bytes = part.toByteArray(StandardCharsets.UTF_8)
        digest.update(bytes.size.toString().toByteArray(StandardCharsets.US_ASCII))
        digest.update(0)
        digest.update(bytes)
        digest.update(0)
    }
    return digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
}

private fun identitySearchId(type: String, vararg identities: String): String =
    "$type:${sha256(identities.toList())}"

fun eventSearchId(event: CalEvent): String = identitySearchId("event", event.calendarId, event.id)
fun taskSearchId(task: CalTask): String = identitySearchId("task", task.calendarId, task.id)
fun journalSearchId(journal: JournalEntry): String =
    identitySearchId("journal", journal.href.orEmpty(), journal.id)
fun contactSearchId(contact: Contact): String =
    identitySearchId("contact", contact.accountId, contact.addressBookId, contact.id)

/** App-private AppSearch storage used by search retrieval and snapshot reconciliation. */
class CalinoAppSearchIndex(
    context: Context,
    private val databaseName: String = DatabaseName,
) : Closeable {
    private val application = context.applicationContext
    private val mutex = Mutex()
    @Volatile private var appSearchSession: AppSearchSession? = null

    /**
     * Replaces changed retrieval documents and removes records no longer
     * present in the current, visible repository snapshot.
     */
    suspend fun reconcile(
        snapshot: CalinoSnapshot,
        journalsEnabled: Boolean,
        contactsEnabled: Boolean,
    ) = withContext(NonCancellable) {
        mutex.withLock {
            reconcileLocked(snapshot, journalsEnabled, contactsEnabled)
        }
    }

    /** Reconciles first, then retrieves AppSearch candidates for the query. */
    suspend fun searchRecords(
        query: String,
        snapshot: CalinoSnapshot,
        journalsEnabled: Boolean,
        contactsEnabled: Boolean,
    ): Set<String> = withContext(NonCancellable) {
        mutex.withLock {
            reconcileLocked(snapshot, journalsEnabled, contactsEnabled)
            val normalized = normalizeSearchText(query)
            if (normalized.isBlank()) return@withLock emptySet()
            queryLocked(normalized)
        }
    }

    private suspend fun reconcileLocked(
        snapshot: CalinoSnapshot,
        journalsEnabled: Boolean,
        contactsEnabled: Boolean,
    ) {
        val session = session()
        val desired = appSearchRecords(snapshot, journalsEnabled, contactsEnabled)
        val existing = allDocuments(session).associateBy { it.id }
        val desiredById = desired.associateBy { it.stableId }
        val changed = desired.filter { existing[it.stableId]?.getPropertyString(SearchTextProperty) != it.searchText }
        if (changed.isNotEmpty()) {
            val result = session.putAsync(
                PutDocumentsRequest.Builder()
                    .addGenericDocuments(changed.map(::toGenericDocument))
                    .build(),
            ).awaitAppSearch()
            check(result.isSuccess) { "AppSearch could not write every record document." }
        }

        val staleIds = existing.keys - desiredById.keys
        if (staleIds.isNotEmpty()) {
            val result = session.removeAsync(
                RemoveByDocumentIdRequest.Builder(Namespace)
                    .addIds(staleIds)
                    .build(),
            ).awaitAppSearch()
            check(result.isSuccess) { "AppSearch could not remove every stale record document." }
        }
    }

    private suspend fun queryLocked(normalizedQuery: String): Set<String> {
        val searchResults = session().search(
            normalizedQuery,
            SearchSpec.Builder()
                .setTermMatch(SearchSpec.TERM_MATCH_PREFIX)
                .addFilterNamespaces(listOf(Namespace))
                .addFilterSchemas(listOf(RecordSchema))
                .setResultCountPerPage(PageSize)
                .setSnippetCount(0)
                .build(),
        )
        val matches = LinkedHashSet<String>()
        try {
            while (true) {
                val page = searchResults.nextPageAsync.awaitAppSearch()
                page.forEach { result ->
                    result.genericDocument.id.takeIf { it.startsWith("event:") || it.startsWith("task:") || it.startsWith("journal:") || it.startsWith("contact:") }
                        ?.let(matches::add)
                }
                if (page.size < PageSize) break
            }
        } finally {
            searchResults.close()
        }
        return matches
    }

    private suspend fun allDocuments(session: AppSearchSession): List<GenericDocument> {
        val searchResults = session.search(
            IndexMarker,
            SearchSpec.Builder()
                .setTermMatch(SearchSpec.TERM_MATCH_EXACT_ONLY)
                .addFilterNamespaces(listOf(Namespace))
                .addFilterSchemas(listOf(RecordSchema))
                .setResultCountPerPage(PageSize)
                .setSnippetCount(0)
                .build(),
        )
        val documents = mutableListOf<GenericDocument>()
        try {
            while (true) {
                val page = searchResults.nextPageAsync.awaitAppSearch()
                documents += page.map { it.genericDocument }
                if (page.size < PageSize) break
            }
        } finally {
            searchResults.close()
        }
        return documents
    }

    private suspend fun session(): AppSearchSession {
        appSearchSession?.let { return it }
        val opened = PlatformStorage.createSearchSessionAsync(
            PlatformStorage.SearchContext.Builder(application, databaseName).build(),
        ).awaitAppSearch()
        opened.setSchemaAsync(
            SetSchemaRequest.Builder()
                .addSchemas(recordSchema())
                // Platform AppSearch shows every schema on system surfaces
                // unless told otherwise. This one holds journal and contact
                // text too, so it must never be displayed.
                .setSchemaTypeDisplayedBySystem(RecordSchema, false)
                .build(),
        ).awaitAppSearch()
        appSearchSession = opened
        return opened
    }

    private fun toGenericDocument(record: CalinoIndexedRecord): GenericDocument =
        GenericDocument.Builder<GenericDocument.Builder<*>>(Namespace, record.stableId, RecordSchema)
            .setPropertyString(SearchTextProperty, record.searchText)
            .setPropertyString(IndexMarkerProperty, IndexMarker)
            .build()

    override fun close() {
        appSearchSession?.close()
        appSearchSession = null
    }

    private fun recordSchema() = AppSearchSchema.Builder(RecordSchema)
        .addProperty(indexedString(SearchTextProperty, AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_PREFIXES))
        .addProperty(indexedString(IndexMarkerProperty, AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_EXACT_TERMS))
        .build()

    private fun indexedString(name: String, indexingType: Int) =
        AppSearchSchema.StringPropertyConfig.Builder(name)
            .setIndexingType(indexingType)
            .setTokenizerType(AppSearchSchema.StringPropertyConfig.TOKENIZER_TYPE_PLAIN)
            .build()

    companion object {
        private const val DatabaseName = "calino-private-search"
        private const val Namespace = "calino-records-v1"
        private const val RecordSchema = "CalinoLocalRecord"
        private const val PageSize = 100
        private const val SearchTextProperty = "searchText"
        private const val IndexMarkerProperty = "indexMarker"
        private const val IndexMarker = "calinoindexmarker"
    }
}

private val DirectExecutor = Executor { command -> command.run() }

private suspend fun <T> ListenableFuture<T>.awaitAppSearch(): T = suspendCancellableCoroutine { continuation ->
    addListener({
        try {
            if (continuation.isActive) continuation.resumeWith(Result.success(get()))
        } catch (cancelled: CancellationException) {
            if (continuation.isActive) continuation.resumeWithException(cancelled)
        } catch (failure: ExecutionException) {
            if (continuation.isActive) continuation.resumeWithException(failure.cause ?: failure)
        } catch (failure: Throwable) {
            if (continuation.isActive) continuation.resumeWithException(failure)
        }
    }, DirectExecutor)
    continuation.invokeOnCancellation { cancel(true) }
}
