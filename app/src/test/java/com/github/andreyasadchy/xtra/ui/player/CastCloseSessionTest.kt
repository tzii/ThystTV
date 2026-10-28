package com.github.andreyasadchy.xtra.ui.player

import android.app.Application
import android.os.Bundle
import android.os.Looper
import android.os.Parcel
import android.view.View
import androidx.fragment.app.FragmentManager
import androidx.core.content.edit
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.prefs
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.MediaSession
import androidx.media3.common.Tracks
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.SettableFuture
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowToast
import org.robolectric.util.ReflectionHelpers
import org.robolectric.Shadows.shadowOf
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.ui.main.MainActivity

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [23, 28])
@ConscryptMode(ConscryptMode.Mode.OFF)
class CastCloseSessionTest {
    private fun currentFragment(controller: MediaController): Pair<Media3Fragment, PlayerViewModel> {
        val context = RuntimeEnvironment.getApplication()
        val fragment = mock<Media3Fragment>(defaultAnswer = Mockito.CALLS_REAL_METHODS,
            useConstructor = UseConstructor.parameterless())
        val activity = mock<MainActivity>()
        val manager = mock<FragmentManager>()
        val model = mock<PlayerViewModel>()
        doReturn(true).whenever(fragment).isAdded
        doReturn(View(context)).whenever(fragment).view
        doReturn(activity).whenever(fragment).activity
        doReturn(context).whenever(fragment).requireContext()
        whenever(activity.supportFragmentManager).thenReturn(manager)
        whenever(manager.findFragmentById(R.id.playerContainer)).thenReturn(fragment)
        ReflectionHelpers.setField(fragment, "viewModel\$delegate", lazyOf(model))
        ReflectionHelpers.setField(fragment, "controllerFuture", Futures.immediateFuture(controller))
        ReflectionHelpers.setField(fragment, "sourceRequest", "accepted-source")
        whenever(controller.applicationLooper).thenReturn(Looper.getMainLooper())
        return fragment to model
    }

    private fun invoke(fragment: Media3Fragment, method: String, type: Class<*>, argument: Any?) =
        Media3Fragment::class.java.getDeclaredMethod(method, type).apply { isAccessible = true }.invoke(fragment, argument)

    private fun fragment(controller: MediaController): Media3Fragment {
        whenever(controller.applicationLooper).thenReturn(Looper.getMainLooper())
        return Media3Fragment().apply {
            PlayerFragment::class.java.getDeclaredField("videoType").apply { isAccessible = true }
                .set(this, PlayerFragment.STREAM)
            Media3Fragment::class.java.getDeclaredField("controllerFuture").apply { isAccessible = true }
                .set(this, Futures.immediateFuture(controller))
            ReflectionHelpers.setField(this, "sourceRequest", "accepted-source")
            ReflectionHelpers.setField(this, "acceptedStart", true)
        }
    }

    @Test fun `explicit close stops media and ends the receiver session`() {
        val controller = mock<MediaController>()
        fragment(controller).closeSession()
        verifyClosed(controller)
    }

    @Test fun `replacement stops old media while retaining the receiver route`() {
        val controller = mock<MediaController>()
        fragment(controller).close()
        verifyCloseRequest(controller, disconnect = false)
    }
    private fun casting(controller: MediaController, remote: Boolean = true, item: MediaItem? = MediaItem.fromUri("https://example.com/vod.m3u8")) {
        whenever(controller.deviceInfo).thenReturn(
            DeviceInfo.Builder(if (remote) DeviceInfo.PLAYBACK_TYPE_REMOTE else DeviceInfo.PLAYBACK_TYPE_LOCAL).build())
        whenever(controller.currentMediaItem).thenReturn(item)
    }

    private fun verifyClosed(controller: MediaController) {
        verifyCloseRequest(controller, disconnect = true)
    }

    private fun verifyCloseRequest(controller: MediaController, disconnect: Boolean) {
        val command = argumentCaptor<SessionCommand>()
        verify(controller).sendCustomCommand(command.capture(), eq(Bundle.EMPTY))
        org.junit.Assert.assertEquals(PlaybackService.CLOSE_SOURCE, command.firstValue.customAction)
        org.junit.Assert.assertEquals("accepted-source", command.firstValue.customExtras.getString(PlaybackService.SOURCE_REQUEST))
        org.junit.Assert.assertEquals(disconnect, command.firstValue.customExtras.getBoolean(PlaybackService.DISCONNECT_CAST))
        verify(controller, never()).pause()
        verify(controller, never()).stop()
        verify(controller, never()).clearMediaItems()
        verify(controller).release()
    }

    @Test fun `app exit with keep casting leaves receiver playback running`() {
        val controller = mock<MediaController>()
        casting(controller)
        fragment(controller).closeOnExit(keepCasting = true)
        verify(controller, never()).pause()
        verify(controller, never()).stop()
        verify(controller, never()).clearMediaItems()
        verify(controller, never()).sendCustomCommand(eq(SessionCommand(PlaybackService.END_CAST_SESSION, Bundle.EMPTY)), any())
        verify(controller).release()
    }

    @Test fun `app exit without the option still ends the receiver session`() {
        val controller = mock<MediaController>()
        casting(controller)
        fragment(controller).closeOnExit(keepCasting = false)
        verifyClosed(controller)
    }

    @Test fun `app exit with the option closes local playback normally`() {
        val controller = mock<MediaController>()
        casting(controller, remote = false)
        fragment(controller).closeOnExit(keepCasting = true)
        verifyClosed(controller)
    }

    @Test fun `app exit with the option closes an empty receiver session`() {
        val controller = mock<MediaController>()
        casting(controller, item = null)
        fragment(controller).closeOnExit(keepCasting = true)
        verifyClosed(controller)
    }

    @Test fun `service survives task removal only for opted-in receiver playback`() {
        fun player(remote: Boolean, item: MediaItem?) = mock<Player>().also {
            whenever(it.deviceInfo).thenReturn(
                DeviceInfo.Builder(if (remote) DeviceInfo.PLAYBACK_TYPE_REMOTE else DeviceInfo.PLAYBACK_TYPE_LOCAL).build())
            whenever(it.currentMediaItem).thenReturn(item)
        }
        val item = MediaItem.fromUri("https://example.com/vod.m3u8")
        assertTrue(PlaybackService.keepsCastingAfterExit(true, player(true, item)))
        assertFalse(PlaybackService.keepsCastingAfterExit(false, player(true, item)))
        assertFalse(PlaybackService.keepsCastingAfterExit(true, player(false, item)))
        assertFalse(PlaybackService.keepsCastingAfterExit(true, player(true, null)))
    }

    @Test fun `rejected download clears started UI and releases controller without closing receiver`() {
        val controller = mock<MediaController>()
        whenever(controller.applicationLooper).thenReturn(Looper.getMainLooper())
        val fragment = mock<Media3Fragment>(defaultAnswer = Mockito.CALLS_REAL_METHODS,
            useConstructor = UseConstructor.parameterless())
        val activity = mock<MainActivity>()
        val model = mock<PlayerViewModel>()
        val context = RuntimeEnvironment.getApplication()
        doReturn(context).whenever(fragment).requireContext()
        doReturn(activity).whenever(fragment).activity
        ReflectionHelpers.setField(fragment, "viewModel\$delegate", lazyOf(model))
        ReflectionHelpers.setField(fragment, "controllerFuture", Futures.immediateFuture(controller))
        Media3Fragment::class.java.getDeclaredMethod("rejectSourceStart", String::class.java, Integer.TYPE).apply {
            isAccessible = true
        }.invoke(fragment, PlaybackService.START_OFFLINE_VIDEO, androidx.media3.session.SessionError.ERROR_NOT_SUPPORTED)
        verify(model).started = false
        verify(activity).discardRejectedPlayer(fragment)
        verify(controller).release()
        verify(controller, never()).pause()
        verify(controller, never()).stop()
        verify(controller, never()).clearMediaItems()
        verify(controller, never()).currentPosition
        assertTrue(ShadowToast.getTextOfLatestToast() == context.getString(R.string.cast_disconnect_download))
    }

    @Test fun `closing before URL resolution only releases the retained receiver controller`() {
        val controller = mock<MediaController>()
        casting(controller)
        val fragment = fragment(controller)
        ReflectionHelpers.setField(fragment, "acceptedStart", false)
        ReflectionHelpers.setField(fragment, "sourceRequest", null)
        fragment.closeSession()
        verify(controller).release()
        verify(controller, never()).pause()
        verify(controller, never()).stop()
        verify(controller, never()).clearMediaItems()
        verify(controller, never()).sendCustomCommand(any(), any())
        verify(controller, never()).currentPosition
    }

    @Test fun `closing while start result is pending sends only a source-guarded close`() {
        val controller = mock<MediaController>()
        casting(controller)
        val fragment = fragment(controller)
        ReflectionHelpers.setField(fragment, "acceptedStart", false)
        val result = SettableFuture.create<SessionResult>()
        whenever(controller.sendCustomCommand(any(), any())).thenReturn(result)
        Media3Fragment::class.java.getDeclaredMethod("sendSourceCommand", SessionCommand::class.java).apply {
            isAccessible = true
        }.invoke(fragment, SessionCommand(PlaybackService.START_VIDEO, Bundle.EMPTY))
        fragment.close()
        val commands = argumentCaptor<SessionCommand>()
        verify(controller, times(2)).sendCustomCommand(commands.capture(), eq(Bundle.EMPTY))
        val start = commands.allValues[0]
        val close = commands.allValues[1]
        org.junit.Assert.assertEquals(PlaybackService.CLOSE_SOURCE, close.customAction)
        org.junit.Assert.assertEquals(start.customExtras.getString(PlaybackService.SOURCE_REQUEST),
            close.customExtras.getString(PlaybackService.SOURCE_REQUEST))
        assertTrue(result.isCancelled)
        verify(controller).release()
        verify(controller, never()).stop()
        verify(controller, never()).clearMediaItems()
        verify(controller, never()).currentPosition
    }

    @Test fun `service closes only its accepted request even if the UI has not received success`() {
        val player = mock<Player>()
        whenever(player.currentTracks).thenReturn(Tracks.EMPTY)
        val session = mock<MediaSession>()
        whenever(session.player).thenReturn(player)
        val handoff = mock<CastPlaybackHandoff>()
        val bridge = mock<PlayerCastBridge>()
        whenever(bridge.handoff).thenReturn(handoff)
        val service = mock<PlaybackService>(defaultAnswer = Mockito.CALLS_REAL_METHODS)
        ReflectionHelpers.setField(service, "mediaSession", session)
        ReflectionHelpers.setField(service, "castBridge", bridge)
        ReflectionHelpers.setField(service, "sourceRequest", "accepted")
        for (request in listOf(null, "rejected", "older")) service.closeSource(request, disconnect = true)
        verifyNoInteractions(player, bridge, handoff)
        service.closeSource("accepted", disconnect = true)
        inOrder(handoff, player, bridge) {
            verify(handoff).cancelPending(clearOwnership = true)
            verify(player).pause()
            verify(player).stop()
            verify(player).clearMediaItems()
            verify(bridge).endSession()
        }
        service.closeSource("accepted", disconnect = true)
        verify(player, times(1)).stop()
    }

    @Test fun `returning screen recovers its own service acceptance after a delayed result`() {
            val controller = mock<MediaController>()
            val (fragment, _) = currentFragment(controller)
            val result = SettableFuture.create<SessionResult>()
            whenever(controller.sendCustomCommand(eq(SessionCommand(PlaybackService.GET_SOURCE_REQUEST, Bundle.EMPTY)), any()))
                .thenReturn(result)
            Media3Fragment::class.java.getDeclaredMethod("refreshSourceOwnership", MediaController::class.java).apply {
                isAccessible = true
            }.invoke(fragment, controller)
            result.set(SessionResult(SessionResult.RESULT_SUCCESS, Bundle().apply {
                putString(PlaybackService.SOURCE_REQUEST, "accepted-source")
            }))
            assertTrue(ReflectionHelpers.getField<Boolean>(fragment, "acceptedStart"))
    }

    @Test fun `cancelled rejection is replayed on return with either retained or empty receiver media`() {
        for (retained in listOf(true, false)) {
            val controller = mock<MediaController>()
            casting(controller, item = if (retained) MediaItem.fromUri("https://example.com/old.mp4") else null)
            val (fragment, model) = currentFragment(controller)
            val first = SettableFuture.create<SessionResult>()
            val replay = SettableFuture.create<SessionResult>()
            val query = SettableFuture.create<SessionResult>()
            whenever(controller.sendCustomCommand(eq(SessionCommand(PlaybackService.START_VIDEO, Bundle.EMPTY)), any()))
                .thenReturn(first, replay)
            whenever(controller.sendCustomCommand(eq(SessionCommand(PlaybackService.GET_SOURCE_REQUEST, Bundle.EMPTY)), any()))
                .thenReturn(query)
            val command = SessionCommand(PlaybackService.START_VIDEO, Bundle().apply {
                putString(PlaybackService.URI, "https://example.com/vod.m3u8")
                putLong(PlaybackService.PLAYBACK_POSITION, 637_000L)
            })
            invoke(fragment, "sendSourceCommand", SessionCommand::class.java, command)
            ReflectionHelpers.getField<PlayerSourceCommands>(fragment, "sourceCommands").cancel()
            first.set(SessionResult(androidx.media3.session.SessionError.ERROR_NOT_SUPPORTED))
            shadowOf(Looper.getMainLooper()).idle()
            assertFalse(ReflectionHelpers.getField<Boolean>(fragment, "rejectedStart"))
            invoke(fragment, "refreshSourceOwnership", MediaController::class.java, controller)
            query.set(SessionResult(SessionResult.RESULT_SUCCESS, Bundle().apply {
                putString(PlaybackService.SOURCE_REQUEST, "retained-source")
            }))
            val sent = argumentCaptor<SessionCommand>()
            verify(controller, times(3)).sendCustomCommand(sent.capture(), eq(Bundle.EMPTY))
            val starts = sent.allValues.filter { it.customAction == PlaybackService.START_VIDEO }
            org.junit.Assert.assertEquals(2, starts.size)
            org.junit.Assert.assertEquals(637_000L, starts.last().customExtras.getLong(PlaybackService.PLAYBACK_POSITION))
            org.junit.Assert.assertNotEquals(starts.first().customExtras.getString(PlaybackService.SOURCE_REQUEST),
                starts.last().customExtras.getString(PlaybackService.SOURCE_REQUEST))
            inOrder(model) { verify(model).started = false; verify(model).started = true }
        }
    }

    @Test fun `recreated rejected screen replays its resolved timestamp with saved positions on or off`() {
        for (savedPositions in listOf(true, false)) {
            RuntimeEnvironment.getApplication().prefs().edit { putBoolean(C.PLAYER_USE_VIDEOPOSITIONS, savedPositions) }
            val controller = mock<MediaController>()
            val (original, _) = currentFragment(controller)
            val command = SessionCommand(PlaybackService.START_VIDEO, Bundle().apply {
                putString(PlaybackService.URI, "https://example.com/resolved-vod.m3u8")
                putLong(PlaybackService.PLAYBACK_POSITION, 637_000L)
            })
            ReflectionHelpers.setField(original, "lastSourceCommand", command)
            ReflectionHelpers.setField(original, "rejectedStart", true)
            val saved = Bundle()
            invoke(original, "saveSourceState", Bundle::class.java, saved)
            val parcel = Parcel.obtain()
            val restored = try {
                saved.writeToParcel(parcel, 0)
                parcel.setDataPosition(0)
                parcel.readBundle(SessionCommand::class.java.classLoader)!!
            } finally { parcel.recycle() }
            val nextController = mock<MediaController>()
            val (recreated, _) = currentFragment(nextController)
            whenever(nextController.sendCustomCommand(any(), any())).thenReturn(SettableFuture.create())
            invoke(recreated, "restoreSourceState", Bundle::class.java, restored)
            Media3Fragment::class.java.getDeclaredMethod("startOrReplay").apply { isAccessible = true }.invoke(recreated)
            val sent = argumentCaptor<SessionCommand>()
            verify(nextController).sendCustomCommand(sent.capture(), eq(Bundle.EMPTY))
            org.junit.Assert.assertEquals(637_000L, sent.firstValue.customExtras.getLong(PlaybackService.PLAYBACK_POSITION))
            org.junit.Assert.assertEquals("https://example.com/resolved-vod.m3u8", sent.firstValue.customExtras.getString(PlaybackService.URI))
        }
    }

    @Test fun `recreated accepted screen does not replay its original start`() {
        val (original, _) = currentFragment(mock())
        ReflectionHelpers.setField(original, "lastSourceCommand", SessionCommand(PlaybackService.START_VIDEO, Bundle().apply {
            putString(PlaybackService.URI, "https://example.com/resolved-vod.m3u8")
            putLong(PlaybackService.PLAYBACK_POSITION, 637_000L)
        }))
        ReflectionHelpers.setField(original, "sourceRequest", "accepted-source")
        ReflectionHelpers.setField(original, "acceptedStart", true)
        val saved = Bundle()
        invoke(original, "saveSourceState", Bundle::class.java, saved)
        val (recreated, _) = currentFragment(mock())
        invoke(recreated, "restoreSourceState", Bundle::class.java, saved)
        org.junit.Assert.assertNull(ReflectionHelpers.getField<SessionCommand?>(recreated, "lastSourceCommand"))
        org.junit.Assert.assertEquals("accepted-source", ReflectionHelpers.getField<String?>(recreated, "sourceRequest"))
    }
}
