package com.github.andreyasadchy.xtra.ui.common

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.edit
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import coil3.ImageLoader
import coil3.fetch.Fetcher
import coil3.request.ImageRequest
import coil3.request.target
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.Clip
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.databinding.FragmentChannelBinding
import com.github.andreyasadchy.xtra.ui.channel.ChannelPagerFragment
import com.github.andreyasadchy.xtra.ui.team.TeamMembersAdapter
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.prefs
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.shadows.ShadowDialog
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(application = Application::class, sdk = [28], qualifiers = "w360dp-h800dp-mdpi")
class SpoilerShieldCardsTest {
    private val context: Context get() = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.BaseDarkTheme)
    private val stream = Stream(
        id = "s1", channelId = "c1", channelLogin = "caster", channelName = "Caster",
        gameId = "g1", gameName = "Chess", title = "Secret result", viewerCount = 123,
        createdAt = "2026-11-02T20:00:00Z",
    )

    @Before fun setup() {
        context.prefs().edit { clear() }
        SpoilerShield.saveRules(context.prefs(), SpoilerShieldRules(channels = listOf(ProtectedChannel(id = "c1"))))
    }

    @Test fun `a place with no reveal control gets the hidden label for a protected title`() {
        val protected = SpoilerSubject(key = "d1", channelId = "c1")
        val other = SpoilerSubject(key = "d2", channelId = "c2")
        assertEquals(context.getString(R.string.spoiler_shield_title_hidden), SpoilerShield.titleOrHiddenLabel(context, protected, "Secret"))
        assertEquals("Open", SpoilerShield.titleOrHiddenLabel(context, other, "Open"))
        context.prefs().edit { putBoolean(C.SPOILER_SHIELD_HIDE_TITLES, false) }
        assertEquals("Secret", SpoilerShield.titleOrHiddenLabel(context, protected, "Secret"))
    }

    @Test fun `a channel known only by name offers no rule that could never match`() {
        val cards = SpoilerShieldCards(mock())
        assertFalse(cards.hasActions(SpoilerSubject(key = "d", channelName = "Caster")))
        assertTrue(cards.hasActions(SpoilerSubject(key = "d", channelName = "Caster", channelId = "c1")))
        assertTrue(cards.hasActions(SpoilerSubject(key = "d", channelLogin = "caster")))
        assertTrue("its category can still be protected", cards.hasActions(SpoilerSubject(key = "d", channelName = "Caster", gameName = "Chess")))
    }

    /** Unprotected cards load with no placeholder, and rely on this to drop a revealed preview. */
    @Test fun `a card's next image request clears what it showed before it can draw`() {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).apply { get().setTheme(R.style.BaseDarkTheme) }.setup().get()
        val list = FrameLayout(activity).also(activity::setContentView)
        val loader = ImageLoader.Builder(activity).components {
            add(Fetcher.Factory<Any> { _, _, _ -> Fetcher { awaitCancellation() } })
        }.build()
        fun card(attached: Boolean) = ImageView(activity).apply {
            setImageDrawable(ColorDrawable(Color.RED))
            if (attached) list.addView(this)
        }
        fun load(view: ImageView) = loader.enqueue(ImageRequest.Builder(activity).data("https://example.invalid/next").target(view).build())
        val rebound = card(attached = true)
        val reboundRequest = load(rebound)
        assertNull(rebound.drawable)
        val fromPool = card(attached = false)
        val poolRequest = load(fromPool)
        list.addView(fromPool)
        assertNull(fromPool.drawable)
        reboundRequest.dispose()
        poolRequest.dispose()
    }

    private fun cards(context: Context): List<View> {
        val fragment = mock<Fragment> { on { requireContext() } doReturn context }
        val parent = FrameLayout(context)
        return listOf(
            StreamsAdapter(fragment, {}).onCreateViewHolder(parent, 0).apply { bind(stream) }.itemView,
            StreamsCompactAdapter(fragment, {}).onCreateViewHolder(parent, 0).apply { bind(stream) }.itemView,
            TeamMembersAdapter(fragment, {}).onCreateViewHolder(parent, 0).apply { bind(stream) }.itemView,
        )
    }

    @Test fun `all live card adapters hide the title and offer protection from child targets`() {
        for (root in cards(context)) {
            assertEquals(context.getString(R.string.spoiler_shield_title_hidden), root.findViewById<TextView>(R.id.title).text)
            assertEquals(View.VISIBLE, root.findViewById<View>(R.id.spoilerReveal).visibility)
            for (id in listOf(R.id.username, R.id.userImage, R.id.gameName)) {
                assertTrue("long-press target $id", root.findViewById<View>(id).isLongClickable)
            }
        }
    }

    @Test fun `vod and clip cards hide the title preview and length until revealed`() {
        val fragment = mock<Fragment> { on { requireContext() } doReturn context }
        val parent = FrameLayout(context)
        val video = Video(
            id = "v1", channelId = "c1", channelLogin = "caster", channelName = "Caster", gameId = "g1", gameName = "Chess",
            title = "Secret result", thumbnailURL = "https://example.test/%{width}x%{height}.jpg", durationSeconds = 3600,
        )
        val clip = Clip(
            id = "k1", channelId = "c1", channelLogin = "caster", channelName = "Caster", gameId = "g1", gameName = "Chess",
            title = "Secret result", thumbnailURL = "https://example.test/t.jpg", durationSeconds = 30,
        )
        val cards = listOf(
            VideosAdapter(fragment, {}, {}).onCreateViewHolder(parent, 0).apply { bind(video) }.itemView,
            ClipsAdapter(fragment, {}).onCreateViewHolder(parent, 0).apply { bind(clip) }.itemView,
        )
        for (root in cards) {
            assertEquals(context.getString(R.string.spoiler_shield_title_hidden), root.findViewById<TextView>(R.id.title).text)
            assertEquals(View.VISIBLE, root.findViewById<View>(R.id.spoilerReveal).visibility)
            assertTrue(root.findViewById<android.widget.ImageView>(R.id.thumbnail).drawable is android.graphics.drawable.ColorDrawable)
            assertEquals("a protected card does not reveal its length", View.GONE, root.findViewById<View>(R.id.duration).visibility)
        }
    }

    @Test fun `an unprotected vod card shows everything and no eye`() {
        val fragment = mock<Fragment> { on { requireContext() } doReturn context }
        val video = Video(id = "v2", channelId = "other", title = "Plain", durationSeconds = 60)
        val root = VideosAdapter(fragment, {}, {}).onCreateViewHolder(FrameLayout(context), 0).apply { bind(video) }.itemView
        assertEquals("Plain", root.findViewById<TextView>(R.id.title).text)
        assertEquals(View.GONE, root.findViewById<View>(R.id.spoilerReveal).visibility)
        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.duration).visibility)
    }

    @Test fun `compact reveal target stays inside the card at normal and large fonts in both directions`() {
        for (font in listOf(1f, 2f)) {
            val scaled = ContextThemeWrapper(context.createConfigurationContext(
                Configuration(context.resources.configuration).apply { fontScale = font },
            ), R.style.BaseDarkTheme)
            for (root in cards(scaled).drop(1)) {
                for (direction in listOf(View.LAYOUT_DIRECTION_LTR, View.LAYOUT_DIRECTION_RTL)) {
                    root.layoutDirection = direction
                    root.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                    root.layout(0, 0, root.measuredWidth, root.measuredHeight)
                    val eye = root.findViewById<View>(R.id.spoilerReveal)
                    val bounds = Rect(0, 0, eye.width, eye.height)
                    (root as ViewGroup).offsetDescendantRectToMyCoords(eye, bounds)
                    assertTrue("48dp reveal target", eye.width >= 48 && eye.height >= 48)
                    assertTrue("font=$font direction=$direction eye=$bounds card height=${root.height}",
                        bounds.top >= 0 && bounds.bottom <= root.height && bounds.left >= 0 && bounds.right <= root.width)
                }
            }
        }
    }

    @Test fun `removal from an old dialog preserves a subsequently added rule`() {
        val removed = SpoilerShield.rules(context.prefs())
        val added = ProtectedCategory(id = "g2", name = "Other game")
        SpoilerShield.updateRules(context.prefs()) { it.copy(categories = it.categories + added) }
        SpoilerShield.updateRules(context.prefs()) { it.without(removed) }
        assertEquals(SpoilerShieldRules(categories = listOf(added)), SpoilerShield.rules(context.prefs()))
    }

    @Test fun `an open add dialog preserves other edits and does not undo an already added rule`() {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).apply {
            get().setTheme(R.style.BaseDarkTheme)
        }.setup()
        try {
            val cards = SpoilerShieldCards(mock<RecyclerView.Adapter<*>>())
            cards.showActions(activity.get(), stream.spoilerSubject())
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            val anotherChannel = ProtectedChannel(id = "c2")
            SpoilerShield.updateRules(context.prefs()) {
                it.copy(channels = it.channels + anotherChannel).togglingCategory(stream.spoilerSubject())
            }
            dialog.listView.performItemClick(dialog.listView.getChildAt(1), 1, 1)
            shadowOf(Looper.getMainLooper()).idle()
            val duration = ShadowDialog.getLatestDialog() as AlertDialog
            duration.listView.performItemClick(duration.listView.getChildAt(0), 0, 0)
            shadowOf(Looper.getMainLooper()).idle()
            val updated = SpoilerShield.rules(context.prefs())
            assertEquals(listOf(ProtectedChannel(id = "c1"), anotherChannel), updated.channels)
            assertTrue(updated.protectsCategory(stream.spoilerSubject()))
        } finally {
            activity.pause().stop().destroy()
        }
    }

    @Test fun `channel title responds to rules after a temporary reveal without reloading`() {
        val fragment = ChannelPagerFragment()
        val binding = FragmentChannelBinding.bind(fragment.onCreateView(LayoutInflater.from(context), null, null))
        try {
            ReflectionHelpers.setField(fragment, "spoilerStream", stream)
            ReflectionHelpers.callInstanceMethod<Unit>(fragment, "bindSpoilerTitle")
            assertEquals(context.getString(R.string.spoiler_shield_title_hidden), binding.title.text)
            binding.title.performClick()
            assertEquals(stream.title, binding.title.text)
            SpoilerShield.updateRules(context.prefs()) { SpoilerShieldRules() }
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(stream.title, binding.title.text)
            assertFalse(binding.title.isClickable)
            SpoilerShield.updateRules(context.prefs()) { it.togglingChannel(stream.spoilerSubject()) }
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(context.getString(R.string.spoiler_shield_title_hidden), binding.title.text)
        } finally {
            fragment.onDestroyView()
        }
    }
}
