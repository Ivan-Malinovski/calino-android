package calino.malinov.ski.poc.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startup() = rule.collect(
        packageName = TargetPackage,
        includeInStartupProfile = true,
    ) {
        startFixtureCalendar()
    }

    @Test
    fun criticalJourneys() = rule.collect(
        packageName = TargetPackage,
        includeInStartupProfile = false,
    ) {
        startFixtureCalendar().exerciseCalendarJourneys()
    }
}
