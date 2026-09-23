package calino.malinov.ski

import android.app.PendingIntent
import android.content.Context
import androidx.appfunctions.AppFunctionData
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.appfunctions.AppFunctionManager
import androidx.appfunctions.AppFunctionNotSupportedException
import androidx.appfunctions.AppFunctionSearchSpec
import androidx.appfunctions.ExecuteAppFunctionRequest
import androidx.appfunctions.ExecuteAppFunctionResponse
import androidx.appfunctions.metadata.AppFunctionMetadata
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import calino.malinov.ski.platform.assistant.AssistantAccess
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Calino's app functions as Android exposes them, driven through the same
 * AppFunctionManager an assistant uses. The app calling its own functions is
 * the one caller Android allows without the privileged execute permission.
 */
@RunWith(AndroidJUnit4::class)
// API 36 images predate the v2 schema the library generates, so nothing is
// indexed there; see docs/assistant-functions.md.
@SdkSuppress(minSdkVersion = 37)
class AssistantFunctionsTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager = checkNotNull(AppFunctionManager.getInstance(context))

    @After fun turnOff() = AssistantAccess.setEnabled(context, false)

    private suspend fun functions(expectPresent: Boolean): List<AppFunctionMetadata> = withTimeout(10_000) {
        manager.observeAppFunctions(AppFunctionSearchSpec(packageNames = setOf(context.packageName)))
            .first { packages -> packages.flatMap { it.appFunctions }.isNotEmpty() == expectPresent }
            .flatMap { it.appFunctions }
    }

    private suspend fun execute(name: String, fill: AppFunctionData.Builder.() -> Unit): ExecuteAppFunctionResponse {
        val metadata = functions(expectPresent = true).single { it.id.endsWith("#$name") }
        val parameters = AppFunctionData.Builder(metadata.parameters, metadata.components).apply(fill).build()
        return manager.executeAppFunction(ExecuteAppFunctionRequest(context.packageName, metadata.id, parameters))
    }

    @Test fun offByDefaultExposesNothing() = runBlocking {
        AssistantAccess.setEnabled(context, false)
        assertTrue(functions(expectPresent = false).isEmpty())
    }

    @Test fun turningItOnExposesTheDocumentedFunctions() = runBlocking {
        AssistantAccess.setEnabled(context, true)
        val names = functions(expectPresent = true).map { it.id.substringAfter('#') }.toSet()
        assertEquals(setOf("getAgenda", "searchCalendar", "openItem", "openDay", "draftEvent", "draftTask"), names)
    }

    /** The device tests run on the May 2026 fixtures, which are not the person's calendar. */
    @Test fun sampleDataIsNeverOfferedAsThePersonsCalendar() = runBlocking {
        AssistantAccess.setEnabled(context, true)
        val response = execute("searchCalendar") { setString("query", "Design") }
        val error = (response as ExecuteAppFunctionResponse.Error).error
        assertTrue(error is AppFunctionNotSupportedException)
    }

    @Test fun aBlankDraftIsRefused() = runBlocking {
        AssistantAccess.setEnabled(context, true)
        val response = execute("draftTask") { setString("title", "  ") }
        assertTrue((response as ExecuteAppFunctionResponse.Error).error is AppFunctionInvalidArgumentException)
    }

    @Test fun aTaskDraftReturnsAnIntentAndSavesNothing() = runBlocking {
        AssistantAccess.setEnabled(context, true)
        val response = execute("draftTask") { setString("title", "Buy stamps") }
        val success = response as ExecuteAppFunctionResponse.Success
        val intent = success.returnValue.getParcelable(ExecuteAppFunctionResponse.Success.PROPERTY_RETURN_VALUE, PendingIntent::class.java)
        assertNotNull(intent)
        assertEquals(context.packageName, (intent as PendingIntent).creatorPackage)
    }
}
