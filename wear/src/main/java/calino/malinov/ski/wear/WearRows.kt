package calino.malinov.ski.wear

import calino.malinov.ski.wearcontract.WearEvent
import calino.malinov.ski.wearcontract.WearSnapshot
import calino.malinov.ski.wearcontract.WearTask

/** Snapshot rows are untyped (`Any`) in the contract; these keep the watch surfaces agreeing on them. */
internal fun rowId(row: Any) = when (row) {
    is WearEvent -> row.occurrenceId
    is WearTask -> row.occurrenceId
    else -> row.hashCode().toString()
}

internal fun rowTitle(row: Any) = when (row) {
    is WearEvent -> row.title
    is WearTask -> row.title
    else -> ""
}

/** Calendar colour as an opaque ARGB int; phone colours may arrive without an alpha byte. */
internal fun rowColor(row: Any): Int = when (row) {
    is WearEvent -> row.color
    is WearTask -> row.color
    else -> 0L
}.toInt() or 0xff000000.toInt()

internal fun WearSnapshot.record(id: String?): Any? = (events + tasks).firstOrNull { rowId(it) == id }
