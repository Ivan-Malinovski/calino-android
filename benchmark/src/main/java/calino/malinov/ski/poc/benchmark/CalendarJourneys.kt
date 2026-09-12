package calino.malinov.ski.poc.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until

internal const val TargetPackage = "calino.malinov.ski.poc"
private const val TimeoutMillis = 5_000L

internal fun MacrobenchmarkScope.startFixtureCalendar(): UiDevice {
    pressHome()
    startActivityAndWait()
    return UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).also { device ->
        check(device.wait(Until.hasObject(By.res("calendar-home")), TimeoutMillis)) {
            "Fixture calendar did not become usable"
        }
    }
}

/** Shared deterministic input used by profile generation and frame benchmarks. */
internal fun UiDevice.exerciseCalendarJourneys() {
    val width = displayWidth
    val height = displayHeight
    val centerX = width / 2
    val pagerY = (height * .68f).toInt()
    repeat(3) {
        swipe((width * .82f).toInt(), pagerY, (width * .18f).toInt(), pagerY, 18)
        waitForIdle()
    }
    repeat(3) {
        swipe((width * .18f).toInt(), pagerY, (width * .82f).toInt(), pagerY, 18)
        waitForIdle()
    }

    findObject(By.descContains("Change calendar zoom"))?.let { zoom ->
        zoom.click()
        waitForIdle()
        swipe((width * .82f).toInt(), (height * .30f).toInt(), (width * .18f).toInt(), (height * .30f).toInt(), 18)
        waitForIdle()
        zoom.click()
        waitForIdle()
    }

    swipe(centerX, (height * .82f).toInt(), centerX, (height * .48f).toInt(), 24)
    waitForIdle()
    findObject(By.text("Design review"))?.let { event ->
        event.click()
        waitForIdle()
        pressBack()
        waitForIdle()
    }
}
