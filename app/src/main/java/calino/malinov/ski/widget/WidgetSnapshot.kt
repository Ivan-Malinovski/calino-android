package calino.malinov.ski.widget

import calino.malinov.ski.data.CalinoContainer
import calino.malinov.ski.data.repository.CalinoSnapshot

/**
 * The snapshot restored from disk by [CalinoContainer.ensureCachedData].
 *
 * Cache restoration is synchronous specifically so both the app and widgets
 * get a correct first frame. Keeping this small suspend seam avoids churn in
 * widget action call sites while making an honestly empty cache return at once.
 */
suspend fun awaitCachedSnapshot(container: CalinoContainer): CalinoSnapshot =
    container.activeRepository.snapshot()
