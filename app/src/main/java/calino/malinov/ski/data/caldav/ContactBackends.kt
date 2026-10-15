package calino.malinov.ski.data.caldav

import calino.malinov.ski.data.model.CalendarProtocol
import calino.malinov.ski.data.model.Contact

/**
 * The read half of an address-book backend. Like [CalendarFetching], resources
 * are opaque `(href, etag, vcf)` triples: CardDAV returns the server's bytes, a
 * JMAP backend returns vCard translated from JSContact under a synthetic href.
 */
interface ContactFetching {
    suspend fun fetch(book: DiscoveredAddressBook, credentials: DavCredentials): CardFetchResult

    suspend fun fetchIncremental(
        book: DiscoveredAddressBook,
        credentials: DavCredentials,
        cachedResources: List<CardResource>?,
        storedCursor: CollectionCursor? = null,
    ): IncrementalCardFetchResult
}

/**
 * The conditional write surface for contacts. `prepareContact` patches the
 * cached vCard and is protocol-neutral; [putPrepared] is where a JMAP backend
 * converts to JSContact and diffs against a fresh read.
 */
interface ContactWriting {
    fun prepareContact(book: DiscoveredAddressBook, contact: Contact): PreparedCardWrite

    suspend fun putPrepared(
        book: DiscoveredAddressBook,
        credentials: DavCredentials,
        prepared: PreparedCardWrite,
    ): CardResource

    suspend fun refreshResource(
        book: DiscoveredAddressBook,
        credentials: DavCredentials,
        href: String,
    ): CardResource

    suspend fun delete(
        book: DiscoveredAddressBook,
        credentials: DavCredentials,
        href: String,
        etag: String? = null,
    )
}

/** Sends each call to the backend for the address book's protocol. */
class ProtocolContactFetcher(
    private val carddav: ContactFetching,
    private val jmap: ContactFetching? = null,
) : ContactFetching {
    private fun backend(book: DiscoveredAddressBook): ContactFetching = when (book.protocol) {
        CalendarProtocol.CalDav -> carddav
        CalendarProtocol.Jmap -> jmap ?: throw CalDavException(
            CalDavErrorCode.NotCalDav,
            "This build cannot read JMAP contacts.",
        )
    }

    override suspend fun fetch(book: DiscoveredAddressBook, credentials: DavCredentials) =
        backend(book).fetch(book, credentials)

    override suspend fun fetchIncremental(
        book: DiscoveredAddressBook,
        credentials: DavCredentials,
        cachedResources: List<CardResource>?,
        storedCursor: CollectionCursor?,
    ) = backend(book).fetchIncremental(book, credentials, cachedResources, storedCursor)
}

class ProtocolContactWriter(
    private val carddav: ContactWriting,
    private val jmap: ContactWriting? = null,
) : ContactWriting {
    private fun backend(book: DiscoveredAddressBook): ContactWriting = when (book.protocol) {
        CalendarProtocol.CalDav -> carddav
        CalendarProtocol.Jmap -> jmap ?: throw CalDavException(
            CalDavErrorCode.NotCalDav,
            "This build cannot write JMAP contacts.",
        )
    }

    override fun prepareContact(book: DiscoveredAddressBook, contact: Contact) =
        backend(book).prepareContact(book, contact)

    override suspend fun putPrepared(
        book: DiscoveredAddressBook,
        credentials: DavCredentials,
        prepared: PreparedCardWrite,
    ) = backend(book).putPrepared(book, credentials, prepared)

    override suspend fun refreshResource(
        book: DiscoveredAddressBook,
        credentials: DavCredentials,
        href: String,
    ) = backend(book).refreshResource(book, credentials, href)

    override suspend fun delete(
        book: DiscoveredAddressBook,
        credentials: DavCredentials,
        href: String,
        etag: String?,
    ) = backend(book).delete(book, credentials, href, etag)
}
