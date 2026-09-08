package calino.malinov.ski.poc.state

/** Origin carried by an overlay so dismiss/back restores the visible surface. */
enum class PocReturnTarget { Calendar, DayModal, Tasks, Journal, Settings, Detail, TaskDetail }

fun PocReturnTarget.restoresDayModal(): Boolean = this == PocReturnTarget.DayModal

fun PocReturnTarget.restoresTasks(): Boolean = this == PocReturnTarget.Tasks

fun PocReturnTarget.restoresJournal(): Boolean = this == PocReturnTarget.Journal
