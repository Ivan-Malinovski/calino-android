package calino.malinov.ski.poc.data.model

/**
 * A connected CalDAV account and the collections discovered under it.
 *
 * The prototype never speaks to a real server: an account is created from a
 * simulated discovery and lives only for the process. Nothing here is written
 * to disk, and [CalDavForm.password] is deliberately absent from this type so a
 * stored account can never carry a secret.
 */
data class CalDavAccount(
    val id: String,
    val displayName: String,
    val serverUrl: String,
    val username: String,
    val calendars: List<CalDavCalendar>,
)

/** One collection under an account. [color] matches the fixture calendar palette. */
data class CalDavCalendar(
    val id: String,
    val name: String,
    val color: Long,
    val enabled: Boolean = true,
    val readOnly: Boolean = false,
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
