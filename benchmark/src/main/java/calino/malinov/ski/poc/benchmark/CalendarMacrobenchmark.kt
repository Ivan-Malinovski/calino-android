package calino.malinov.ski.poc.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class CalendarMacrobenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun coldStartup() = rule.measureRepeated(
        packageName = TargetPackage,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
        startupMode = StartupMode.COLD,
        iterations = 10,
        setupBlock = { pressHome() },
    ) {
        startFixtureCalendar()
    }

    @Test
    fun calendarJourneys() = rule.measureRepeated(
        packageName = TargetPackage,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = { startFixtureCalendar() },
    ) {
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).exerciseCalendarJourneys()
    }
}
