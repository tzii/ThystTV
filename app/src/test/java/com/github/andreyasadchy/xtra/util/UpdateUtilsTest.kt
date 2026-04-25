package com.github.andreyasadchy.xtra.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateUtilsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `version comparison treats v1_2_0 as newer than 1_1_6`() {
        assertTrue(UpdateUtils.isVersionNewer("v1.2.0", "1.1.6"))
    }

    @Test
    fun `version comparison rejects equal installed version`() {
        assertFalse(UpdateUtils.isVersionNewer("1.1.6", "1.1.6"))
    }

    @Test
    fun `version comparison rejects older release tag`() {
        assertFalse(UpdateUtils.isVersionNewer("v1.1.5", "1.1.6"))
    }

    @Test
    fun `release without apk asset returns no update`() {
        val release = releaseJson(
            tagName = "v1.2.0",
            assets = """
                [
                  {
                    "name": "notes.txt",
                    "content_type": "text/plain",
                    "browser_download_url": "https://example.com/notes.txt"
                  }
                ]
            """.trimIndent()
        )

        assertNull(UpdateUtils.getAvailableUpdate(release, "1.1.6"))
    }

    @Test
    fun `release parser selects apk asset and preserves metadata`() {
        val release = releaseJson(
            tagName = "v1.2.0",
            name = "ThystTV 1.2.0",
            publishedAt = "2026-04-25T12:00:00Z",
            body = "Player polish focused release.",
            htmlUrl = "https://github.com/tzii/ThystTV/releases/tag/v1.2.0",
            assets = """
                [
                  {
                    "name": "source.zip",
                    "content_type": "application/zip",
                    "browser_download_url": "https://example.com/source.zip"
                  },
                  {
                    "name": "ThystTV-1.2.0.apk",
                    "content_type": "application/vnd.android.package-archive",
                    "browser_download_url": "https://example.com/ThystTV-1.2.0.apk"
                  }
                ]
            """.trimIndent()
        )

        val update = UpdateUtils.getAvailableUpdate(release, "1.1.6")

        assertNotNull(update)
        assertEquals("1.2.0", update!!.versionName)
        assertEquals("v1.2.0", update.tagName)
        assertEquals("ThystTV 1.2.0", update.title)
        assertEquals("2026-04-25T12:00:00Z", update.publishedAt)
        assertEquals("Player polish focused release.", update.releaseNotes)
        assertEquals("https://github.com/tzii/ThystTV/releases/tag/v1.2.0", update.releaseUrl)
        assertEquals("https://example.com/ThystTV-1.2.0.apk", update.downloadUrl)
    }

    @Test
    fun `release api url resolver replaces legacy xtra default`() {
        assertEquals(
            UpdateUtils.DEFAULT_RELEASE_API_URL,
            UpdateUtils.resolveReleaseApiUrl("https://api.github.com/repos/crackededed/xtra/releases/tags/latest")
        )
    }

    @Test
    fun `release api url resolver preserves custom url`() {
        val customUrl = "https://example.com/releases/latest"

        assertEquals(customUrl, UpdateUtils.resolveReleaseApiUrl(customUrl))
    }

    private fun releaseJson(
        tagName: String,
        name: String = "ThystTV $tagName",
        publishedAt: String = "2026-04-25T12:00:00Z",
        body: String = "Release notes",
        htmlUrl: String = "https://github.com/tzii/ThystTV/releases/tag/$tagName",
        assets: String
    ): JsonObject = json.decodeFromString(
        """
        {
          "tag_name": "$tagName",
          "name": "$name",
          "published_at": "$publishedAt",
          "body": "$body",
          "html_url": "$htmlUrl",
          "assets": $assets
        }
        """.trimIndent()
    )
}
