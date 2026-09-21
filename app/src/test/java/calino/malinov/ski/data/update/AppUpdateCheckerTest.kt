package calino.malinov.ski.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateCheckerTest {
    @Test
    fun semanticVersionsCompareNumerically() {
        assertTrue(isNewerVersion("v0.10.0", "0.9.9"))
        assertTrue(isNewerVersion("1.0.0", "0.99.999"))
        assertFalse(isNewerVersion("0.3.0", "0.3.0-debug"))
        assertFalse(isNewerVersion("0.2.9", "0.3.0"))
        assertFalse(isNewerVersion("not-a-version", "0.3.0"))
    }

    @Test
    fun latestReleaseResponseIsParsedAndRestrictedToCalinoGitHub() {
        val release = parseGitHubRelease(
            """{
                "tag_name":"v0.4.0",
                "name":"Calino 0.4.0",
                "html_url":"https://github.com/Ivan-Malinovski/calino-android/releases/tag/v0.4.0",
                "draft":false,
                "prerelease":false
            }""",
        )

        assertEquals("0.4.0", release.version)
        assertEquals("Calino 0.4.0", release.title)
        assertTrue(isCalinoReleaseUrl(release.releaseUrl))
    }

    @Test(expected = IllegalStateException::class)
    fun anotherHostCannotSupplyTheUpdateLink() {
        parseGitHubRelease(
            """{
                "tag_name":"v9.9.9",
                "html_url":"https://example.com/fake.apk",
                "draft":false,
                "prerelease":false
            }""",
        )
    }

    @Test(expected = IllegalStateException::class)
    fun prereleasesDoNotBecomeUserUpdates() {
        parseGitHubRelease(
            """{
                "tag_name":"v0.4.0-beta",
                "html_url":"https://github.com/Ivan-Malinovski/calino-android/releases/tag/v0.4.0-beta",
                "draft":false,
                "prerelease":true
            }""",
        )
    }
}
