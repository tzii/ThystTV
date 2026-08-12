package com.github.andreyasadchy.xtra.repository

import com.github.andreyasadchy.xtra.model.chat.Emote
import com.github.andreyasadchy.xtra.model.misc.StvChannelResponse
import com.github.andreyasadchy.xtra.model.misc.StvGlobalResponse
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

private val json = Json { ignoreUnknownKeys = true }

private const val VALID_EMOTE = """{"name":"Valid","data":{"host":{"url":"//cdn.7tv.app/emote","files":[{"name":"1x.webp","format":"WEBP"},{"name":"1x.gif","format":"GIF"}]}}}"""

private const val PARSER_FIXTURE = """{"id":"set","emotes":[
    $VALID_EMOTE,
    {"name":"  ","data":{"host":{"url":"//cdn.7tv.app/emote","files":[{"name":"1x.webp","format":"WEBP"}]}}},
    {"name":"NoData"},
    {"name":"NoHost","data":{}},
    {"name":"BlankUrl","data":{"host":{"url":"  "}}},
    {"name":"BlankFile","data":{"host":{"url":"//cdn.7tv.app/other","files":[{"name":"  ","format":"WEBP"}]}}}
]}"""

class StvChannelEmoteResolverTest {

    @Test
    fun `deserializes embedded and id-only channel responses`() {
        val embedded = json.decodeFromString<StvChannelResponse>(
            """{"emote_set":{"id":"embedded","emotes":[]}}"""
        )
        assertEquals("embedded", embedded.emoteSet?.id)

        val idOnly = json.decodeFromString<StvChannelResponse>(
            """{"emote_set_id":"fallback"}"""
        )
        assertNull(idOnly.emoteSet)
        assertEquals("fallback", idOnly.emoteSetId)
    }

    @Test
    fun `parser keeps webp selection and drops malformed entries`() {
        val response = json.decodeFromString<StvGlobalResponse>(PARSER_FIXTURE)

        val webp = parseStvEmotes(response.emotes, useWebp = true, source = Emote.CHANNEL_STV)
        // Blank-name, missing-data, missing-host, and blank-host-url entries are dropped;
        // a blank filename keeps the emote through the synthesized fallback URL.
        assertEquals(listOf("Valid", "BlankFile"), webp.map { it.name })
        assertEquals("https://cdn.7tv.app/emote/1x.webp", webp.first().url1x)
        assertEquals("webp", webp.first().format)
        assertEquals("https://cdn.7tv.app/other/1x.webp", webp[1].url1x)

        val raster = parseStvEmotes(response.emotes, useWebp = false, source = Emote.CHANNEL_STV)
        assertEquals(listOf("Valid", "BlankFile"), raster.map { it.name })
        assertTrue(raster.first().url1x?.endsWith("1x.gif") == true || raster.first().url1x?.endsWith("1x.png") == true)
    }

    @Test
    fun `non-empty embedded set returns its id without fetching`() = runTest {
        val (result, requested) = resolve("""{"emote_set":{"id":"embedded-set","emotes":[$VALID_EMOTE]}}""")

        assertEquals("embedded-set", result.first)
        assertEquals(listOf("Valid"), result.second.map { it.name })
        assertTrue(requested.isEmpty())
    }

    @Test
    fun `blank embedded id falls back to top-level id without fetching`() = runTest {
        val (result, requested) = resolve("""{"emote_set":{"id":"  ","emotes":[$VALID_EMOTE]},"emote_set_id":"top-id"}""")

        assertEquals("top-id", result.first)
        assertTrue(requested.isEmpty())
    }

    @Test
    fun `empty embedded set fetches the top-level id once`() = runTest {
        val (result, requested) = resolve("""{"emote_set":{"id":"embedded","emotes":[]},"emote_set_id":"top-id"}""")

        assertEquals(listOf("top-id"), requested)
        assertEquals("fetched", result.first)
    }

    @Test
    fun `id-only response fetches the referenced set once`() = runTest {
        val (result, requested) = resolve("""{"emote_set_id":"top-id"}""")

        assertEquals(listOf("top-id"), requested)
        assertEquals("fetched", result.first)
    }

    @Test
    fun `blank fetched id keeps the requested id`() = runTest {
        val (result, _) = resolve(
            """{"emote_set_id":"top-id"}""",
            fetched = StvGlobalResponse(id = "  ", emotes = emptyList()),
        )

        assertEquals("top-id", result.first)
    }

    @Test
    fun `missing data and blank ids resolve to null without fetching`() = runTest {
        val (result, requested) = resolve("""{"emote_set":{"id":"  ","emotes":[]}}""")

        assertNull(result.first)
        assertTrue(result.second.isEmpty())
        assertTrue(requested.isEmpty())
    }

    @Test
    fun `valid entry survives malformed embedded siblings without fetching`() = runTest {
        val (result, requested) = resolve("""{"emote_set":{"id":"embedded-set","emotes":[
            $VALID_EMOTE,
            {"name":"","data":{"host":{"url":"//cdn.7tv.app/emote","files":[{"name":"1x.webp","format":"WEBP"}]}}}
        ]}}""")

        assertEquals("embedded-set", result.first)
        assertEquals(listOf("Valid"), result.second.map { it.name })
        assertTrue(requested.isEmpty())
    }

    @Test
    fun `malformed user json throws before resolution`() {
        assertThrows(SerializationException::class.java) {
            json.decodeFromString<StvChannelResponse>("not json")
        }
    }

    @Test
    fun `emote set url targets the documented endpoint`() {
        assertEquals("https://7tv.io/v3/emote-sets/set-123", stvEmoteSetUrl("set-123"))
    }

    private suspend fun resolve(
        userJson: String,
        fetched: StvGlobalResponse = StvGlobalResponse(id = "fetched", emotes = emptyList()),
    ): Pair<Pair<String?, List<Emote>>, List<String>> {
        val requested = mutableListOf<String>()
        val response = json.decodeFromString<StvChannelResponse>(userJson)
        val result = resolveStvChannelEmotes(response, useWebp = true) { setId ->
            requested += setId
            fetched
        }
        return result to requested
    }
}
