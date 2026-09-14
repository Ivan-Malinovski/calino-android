package calino.malinov.ski.data.model

/**
 * A connected CalDAV account and the collections discovered under it.
 *
 * Server reads and conditional writes are real; the repository overlay shows
 * accepted local changes immediately while the durable queue handles retries.
 * [CalDavForm.password] is deliberately absent from this type so a stored
 * account can never carry a secret.
 */
data class CalDavAccount(
    val id: String,
    val displayName: String,
    val serverUrl: String,
    val username: String,
    val calendars: List<CalDavCalendar>,
    val addressBooks: List<ContactAddressBook> = emptyList(),
)

/**
 * One collection under an account. [color] matches the fixture calendar
 * palette. [ctag] is a change hint; [syncToken] is the RFC 6578 cursor. Both
 * are nullable because servers may not advertise either property, and a
 * cursor is only useful after a complete resource snapshot has been read.
 */
data class CalDavCalendar(
    val id: String,
    val name: String,
    val color: Long,
    val enabled: Boolean = true,
    val readOnly: Boolean = false,
    val ctag: String? = null,
    val syncToken: String? = null,
    /** Display visibility in the calendar UI, separate from source syncing. */
    val visible: Boolean = true,
    /** Whether this collection's tasks appear on calendar surfaces. */
    val showTasksInViews: Boolean = true,
)

/**
 * The add-account sheet's draft. Held in plain `remember` state, never in
 * `rememberSaveable`, so the password is not written into saved instance state.
 */
data class CalDavForm(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val displayName: String = "",
)
