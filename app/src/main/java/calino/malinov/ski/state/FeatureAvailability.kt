package calino.malinov.ski.state

import calino.malinov.ski.data.repository.CalinoSnapshot

data class FeatureAvailability(
    val journalEnabled: Boolean = false,
    val contactsEnabled: Boolean = false,
)

/** Detection is one-way: discovering content enables the surface, never hides it. */
fun featureAvailabilityAfter(snapshot: CalinoSnapshot, current: FeatureAvailability): FeatureAvailability =
    FeatureAvailability(
        journalEnabled = current.journalEnabled || snapshot.journals.isNotEmpty(),
        contactsEnabled = current.contactsEnabled || snapshot.contacts.isNotEmpty(),
    )
