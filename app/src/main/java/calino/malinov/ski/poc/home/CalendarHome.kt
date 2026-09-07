package calino.malinov.ski.poc.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import calino.malinov.ski.poc.data.repository.FixtureRepository
import calino.malinov.ski.poc.ui.home.HomeScreen

/**
 * Compatibility entry point for early POC callers. The calendar has one
 * implementation now; new callers should use [HomeScreen] directly.
 */
@Deprecated("Use ui.home.HomeScreen")
@Composable
fun CalinoCalendarHome(
    repository: FixtureRepository = remember { FixtureRepository() },
    modifier: Modifier = Modifier,
) {
    HomeScreen(repository = repository, modifier = modifier)
}

@Composable
fun CalinoCalendarHomePreview() {
    CalinoCalendarHome()
}
