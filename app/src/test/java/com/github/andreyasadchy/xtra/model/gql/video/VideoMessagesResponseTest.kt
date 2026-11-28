package com.github.andreyasadchy.xtra.model.gql.video

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class VideoMessagesResponseTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `a deleted VoD decodes as a missing video instead of failing`() {
        val response = json.decodeFromString<VideoMessagesResponse>("""{"data":{"video":null}}""")

        assertNotNull(response.data)
        assertNull(response.data!!.video)
        assertNull(response.errors)
    }

    @Test
    fun `a failed request keeps its errors and no data`() {
        val response = json.decodeFromString<VideoMessagesResponse>(
            """{"errors":[{"message":"service timeout"}],"data":null}"""
        )

        assertNull(response.data)
        assertEquals("service timeout", response.errors!!.single().message)
    }

    @Test
    fun `comments still decode when the VoD exists`() {
        val response = json.decodeFromString<VideoMessagesResponse>(
            """{"data":{"video":{"comments":{"edges":[{"node":{"id":"c1","contentOffsetSeconds":12},"cursor":"abc"}],"pageInfo":{"hasNextPage":false}}}}}"""
        )

        val comments = response.data!!.video!!.comments
        assertEquals("c1", comments.edges.single().node.id)
        assertEquals(12, comments.edges.single().node.contentOffsetSeconds)
    }
}
