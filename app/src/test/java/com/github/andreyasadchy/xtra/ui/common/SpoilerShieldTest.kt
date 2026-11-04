package com.github.andreyasadchy.xtra.ui.common

import com.github.andreyasadchy.xtra.model.ui.Bookmark
import com.github.andreyasadchy.xtra.model.ui.Clip
import com.github.andreyasadchy.xtra.model.ui.OfflineVideo
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.model.ui.Video
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SpoilerShieldTest {
    private val stream = Stream(
        id = "s1", channelId = "c1", channelLogin = "caster", channelName = "Caster",
        gameId = "g1", gameName = "Counter-Strike", title = "TEAM X WINS", thumbnailURL = "https://example.test/t.jpg",
    )
    private val subject = stream.spoilerSubject()

    private fun present(
        rules: SpoilerShieldRules,
        item: Stream = stream,
        hideTitles: Boolean = true,
        hideThumbnails: Boolean = true,
        revealed: Boolean = false,
    ) = SpoilerShield.presentation(rules, item.spoilerSubject(), hideTitles, hideThumbnails, revealed)

    @Test fun `channels match by id and fall back to login when an id is missing`() {
        assertTrue(ProtectedChannel(id = "c1").matches(subject))
        assertFalse(ProtectedChannel(id = "other", login = "caster").matches(subject))
        assertTrue(ProtectedChannel(id = "c1").matches(Stream(channelId = "c1").spoilerSubject()))
        assertTrue(ProtectedChannel(login = "CASTER").matches(Stream(channelLogin = "caster").spoilerSubject()))
        assertTrue(ProtectedChannel(id = "c1", login = "caster").matches(Stream(channelLogin = "caster").spoilerSubject()))
        assertFalse(ProtectedChannel().matches(Stream().spoilerSubject()))
    }

    @Test fun `categories match by id and fall back to name when an id is missing`() {
        assertTrue(ProtectedCategory(id = "g1").matches(subject))
        assertFalse(ProtectedCategory(id = "other", name = "Counter-Strike").matches(subject))
        assertTrue(ProtectedCategory(id = "g1", name = "counter-strike").matches(Stream(gameName = "Counter-Strike").spoilerSubject()))
        assertFalse(ProtectedCategory().matches(Stream().spoilerSubject()))
    }

    @Test fun `unprotected streams are untouched`() {
        assertEquals(SpoilerPresentation(), present(SpoilerShieldRules()))
        assertEquals(SpoilerPresentation(), present(SpoilerShieldRules(channels = listOf(ProtectedChannel(id = "other")))))
    }

    @Test fun `protected streams hide both and can be revealed`() {
        val rules = SpoilerShieldRules(categories = listOf(ProtectedCategory(id = "g1")))
        assertEquals(SpoilerPresentation(hideTitle = true, hideThumbnail = true, canToggle = true), present(rules))
        assertEquals(SpoilerPresentation(canToggle = true, revealed = true), present(rules, revealed = true))
    }

    @Test fun `options apply independently and nothing to hide offers no eye`() {
        val rules = SpoilerShieldRules(channels = listOf(ProtectedChannel(id = "c1")))
        assertEquals(SpoilerPresentation(hideThumbnail = true, canToggle = true), present(rules, hideTitles = false))
        assertEquals(SpoilerPresentation(hideTitle = true, canToggle = true), present(rules, hideThumbnails = false))
        assertEquals(SpoilerPresentation(), present(rules, hideTitles = false, hideThumbnails = false))
        assertEquals(SpoilerPresentation(), present(rules, item = Stream(channelId = "c1", title = " ")))
    }

    @Test fun `toggling adds a rule and removes every match`() {
        val added = SpoilerShieldRules().togglingChannel(subject).togglingCategory(subject)
        assertEquals(listOf(ProtectedChannel("c1", "caster", "Caster")), added.channels)
        assertEquals(listOf(ProtectedCategory("g1", "Counter-Strike")), added.categories)
        assertTrue(added.togglingChannel(subject).togglingCategory(subject).isEmpty)
    }

    @Test fun `rules survive storage and damaged values protect nothing`() {
        val rules = SpoilerShieldRules(listOf(ProtectedChannel("c1", "caster", "Caster")), listOf(ProtectedCategory("g1", "Counter-Strike")))
        assertEquals(rules, SpoilerShield.decode(SpoilerShield.encode(rules)))
        for (raw in listOf(null, "", "not json", """{"channels":"x"}""")) {
            assertEquals(SpoilerShieldRules(), SpoilerShield.decode(raw))
        }
    }

    @Test fun `every card type follows the same channel and category rules`() {
        val subjects = listOf(
            Video(id = "v1", channelId = "c1", channelLogin = "caster", gameId = "g1", gameName = "Counter-Strike", title = "Final").spoilerSubject(),
            Clip(id = "k1", channelId = "c1", channelLogin = "caster", gameId = "g1", gameName = "Counter-Strike", title = "Final").spoilerSubject(),
            Bookmark(videoId = "v1", userId = "c1", userLogin = "caster", gameId = "g1", gameName = "Counter-Strike", title = "Final").spoilerSubject(),
            OfflineVideo(channelId = "c1", channelLogin = "caster", gameId = "g1", gameName = "Counter-Strike", name = "Final").spoilerSubject(),
        )
        val byChannel = SpoilerShieldRules(channels = listOf(ProtectedChannel(id = "c1")))
        val byCategory = SpoilerShieldRules(categories = listOf(ProtectedCategory(id = "g1")))
        for (item in subjects) {
            assertEquals("Final", item.title)
            assertTrue(byChannel.protects(item))
            assertTrue(byCategory.protects(item))
            assertFalse(SpoilerShieldRules().protects(item))
        }
        assertEquals("reveals stay per card type", subjects.size, subjects.map { it.key }.toSet().size)
    }

    @Test fun `expired rules protect nothing and only allocate when something expired`() {
        val rules = SpoilerShieldRules(
            channels = listOf(ProtectedChannel(id = "c1", expiresAt = 100L), ProtectedChannel(id = "c2")),
            categories = listOf(ProtectedCategory(id = "g1", expiresAt = 200L)),
        )
        assertSame(rules, rules.active(99L))
        assertTrue(rules.active(99L).protectsChannel(subject))
        val afterChannel = rules.active(100L)
        assertFalse(afterChannel.protectsChannel(subject))
        assertEquals(listOf(ProtectedChannel(id = "c2")), afterChannel.channels)
        assertTrue(afterChannel.protectsCategory(subject))
        assertFalse(rules.active(200L).protects(subject))
    }

    @Test fun `toggling records the time limit and adding again replaces it`() {
        val timed = SpoilerShieldRules().togglingChannel(subject, 500L).togglingCategory(subject, 600L)
        assertEquals(500L, timed.channels.single().expiresAt)
        assertEquals(600L, timed.categories.single().expiresAt)
        val extended = timed
            .withChannel(ProtectedChannel(id = "c1", name = "Caster", expiresAt = null))
            .withCategory(ProtectedCategory(name = "counter-strike"))
        assertEquals(listOf(ProtectedChannel(id = "c1", name = "Caster")), extended.channels)
        assertEquals("a typed name replaces the same category", listOf(ProtectedCategory(name = "counter-strike")), extended.categories)
        assertEquals("same id replaces", 1, timed.withCategory(ProtectedCategory(id = "g1")).categories.size)
        assertEquals(
            "typed name replaces a typed name in any case",
            1,
            SpoilerShieldRules().withCategory(ProtectedCategory(name = "Chess")).withCategory(ProtectedCategory(name = "chess")).categories.size,
        )
    }

    @Test fun `a typed login protects before any id is known`() {
        val rules = SpoilerShieldRules().withChannel(ProtectedChannel(login = "caster", name = "CASTER"))
        assertTrue(rules.protects(subject))
    }

    @Test fun `a typed display name protects, though it isn't the channel's login`() {
        val rules = SpoilerShieldRules().withChannel(ProtectedChannel(login = "some name", name = "Some Name"))
        assertTrue(rules.protects(SpoilerSubject(key = "a", channelLogin = "somename", channelName = "Some Name")))
        assertFalse(rules.protects(SpoilerSubject(key = "b", channelLogin = "other", channelName = "Other")))
        assertTrue(rules.protects(SpoilerSubject(key = "c", channelLogin = "somename", channelName = " some name ")))
    }

    @Test fun `picking a channel from a search replaces it typed by display name, so its time limit holds`() {
        val rules = SpoilerShieldRules()
            .withChannel(ProtectedChannel(login = "some name", name = "Some Name"))
            .withChannel(ProtectedChannel(id = "c9", login = "somename", name = "Some Name", expiresAt = 100L))
        assertEquals(listOf(ProtectedChannel("c9", "somename", "Some Name", 100L)), rules.channels)
        val subject = SpoilerSubject(key = "a", channelId = "c9", channelLogin = "somename", channelName = "Some Name")
        assertFalse(rules.active(200L).protects(subject))
    }

    @Test fun `two channels with ids stay separate even with the same display name`() {
        val rules = SpoilerShieldRules()
            .withChannel(ProtectedChannel(id = "c1", login = "first", name = "Same Name"))
            .withChannel(ProtectedChannel(id = "c2", login = "second", name = "Same Name"))
        assertEquals(2, rules.channels.size)
    }

    @Test fun `a rule from a search goes by its id, not a matching name`() {
        val rules = SpoilerShieldRules().withChannel(ProtectedChannel(id = "c1", login = "caster", name = "Same Name"))
        assertFalse(rules.protects(SpoilerSubject(key = "a", channelId = "c2", channelLogin = "other", channelName = "Same Name")))
    }

    @Test fun `rules saved before time limits still load`() {
        val rules = SpoilerShield.decode("""{"channels":[{"id":"c1","login":"caster","name":"Caster"}],"categories":[{"id":"g1","name":"Chess"}]}""")
        assertEquals(listOf(ProtectedChannel("c1", "caster", "Caster")), rules.channels)
        assertEquals(listOf(ProtectedCategory("g1", "Chess")), rules.categories)
        val timed = SpoilerShieldRules(channels = listOf(ProtectedChannel(id = "c1", expiresAt = 7L)))
        assertEquals(timed, SpoilerShield.decode(SpoilerShield.encode(timed)))
    }
}
