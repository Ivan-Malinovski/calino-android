package calino.malinov.ski.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import calino.malinov.ski.data.repository.CalinoRepository
import calino.malinov.ski.data.repository.FixtureRepository
import calino.malinov.ski.ui.home.HomeScreen

/**
 * Compatibility entry point for early POC callers. The calendar has one
 * implementation now; new callers should use [HomeScreen] directly.
 */
@Deprecated("Use ui.home.HomeScreen")
@Composable
fun CalinoCalendarHome(
    repository: CalinoRepository,
    modifier: Modifier = Modifier,
) {
    HomeScreen(repository = repository, modifier = modifier)
}

/** Preview only: the fixture repository is constructed here on purpose. */
@Composable
fun CalinoCalendarHomePreview() {
    CalinoCalendarHome(repository = remember { FixtureRepository() })
}
