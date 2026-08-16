package com.github.andreyasadchy.xtra.ui.player

import com.github.andreyasadchy.xtra.ui.player.PinchDisplayModeController.Event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PinchDisplayModeControllerTest {

    private lateinit var controller: PinchDisplayModeController

    @Before
    fun setUp() {
        controller = PinchDisplayModeController()
    }

    private fun armedTarget(events: List<Event>): Event.Armed? = events.filterIsInstance<Event.Armed>().firstOrNull()

    private fun preview(events: List<Event>): Event.Preview? = events.filterIsInstance<Event.Preview>().firstOrNull()

    @Test
    fun `fit outward previews fill and arms at threshold`() {
        controller.begin(PlayerDisplayMode.FIT)
        val preview = preview(controller.update(1.05f))
        assertEquals(PlayerDisplayMode.FILL, preview?.toward)
        assertEquals(0.625f, preview?.progress ?: -1f, 0.001f)

        val armed = armedTarget(controller.update(1.08f))
        assertEquals(PlayerDisplayMode.FILL, armed?.target)
    }

    @Test
    fun `fit fill-armed disarms only after hysteresis`() {
        controller.begin(PlayerDisplayMode.FIT)
        controller.update(1.08f)
        var events = controller.update(1.05f)
        assertTrue(armedTarget(events) == null && events.filterIsInstance<Event.Disarmed>().isEmpty())
        events = controller.update(1.04f)
        assertTrue(events.filterIsInstance<Event.Disarmed>().isNotEmpty())
        assertEquals(PlayerDisplayMode.FILL, (events.filterIsInstance<Event.Disarmed>().first() as Event.Disarmed).toward)
    }

    @Test
    fun `fit fill-armed can re-arm after disarm`() {
        controller.begin(PlayerDisplayMode.FIT)
        controller.update(1.08f)
        controller.update(1.02f)
        val events = controller.update(1.09f)
        assertEquals(PlayerDisplayMode.FILL, armedTarget(events)?.target)
    }

    @Test
    fun `fit inward has no target`() {
        controller.begin(PlayerDisplayMode.FIT)
        val events = controller.update(0.9f)
        assertTrue(events.filterIsInstance<Event.NoPreview>().isNotEmpty())
    }

    @Test
    fun `fill inward previews fit and arms at threshold`() {
        controller.begin(PlayerDisplayMode.FILL)
        val preview = preview(controller.update(0.95f))
        assertEquals(PlayerDisplayMode.FIT, preview?.toward)
        assertEquals(0.625f, preview?.progress ?: -1f, 0.001f)

        val armed = armedTarget(controller.update(0.92f))
        assertEquals(PlayerDisplayMode.FIT, armed?.target)
    }

    @Test
    fun `fill fit-armed disarms only after hysteresis`() {
        controller.begin(PlayerDisplayMode.FILL)
        controller.update(0.92f)
        var events = controller.update(0.95f)
        assertTrue(events.filterIsInstance<Event.Disarmed>().isEmpty())
        events = controller.update(0.96f)
        assertTrue(events.filterIsInstance<Event.Disarmed>().isNotEmpty())
    }

    @Test
    fun `fill outward has no target`() {
        controller.begin(PlayerDisplayMode.FILL)
        val events = controller.update(1.2f)
        assertTrue(events.filterIsInstance<Event.NoPreview>().isNotEmpty())
    }

    @Test
    fun `stretch outward previews toward fill`() {
        controller.begin(PlayerDisplayMode.STRETCH)
        val preview = preview(controller.update(1.05f))
        assertEquals(PlayerDisplayMode.STRETCH, preview?.from)
        assertEquals(PlayerDisplayMode.FILL, preview?.toward)
        assertEquals(0.625f, preview?.progress ?: -1f, 0.001f)
    }

    @Test
    fun `stretch inward previews toward fit`() {
        controller.begin(PlayerDisplayMode.STRETCH)
        val preview = preview(controller.update(0.95f))
        assertEquals(PlayerDisplayMode.FIT, preview?.toward)
        assertEquals(0.625f, preview?.progress ?: -1f, 0.001f)
    }

    @Test
    fun `stretch outward arms fill and hysteresis disarms to stretch`() {
        controller.begin(PlayerDisplayMode.STRETCH)
        assertEquals(PlayerDisplayMode.FILL, armedTarget(controller.update(1.08f))?.target)
        val events = controller.update(1.04f)
        val disarmed = events.filterIsInstance<Event.Disarmed>().firstOrNull()
        assertEquals(PlayerDisplayMode.FILL, (disarmed as Event.Disarmed).toward)
    }

    @Test
    fun `stretch inward arms fit and hysteresis disarms to stretch`() {
        controller.begin(PlayerDisplayMode.STRETCH)
        assertEquals(PlayerDisplayMode.FIT, armedTarget(controller.update(0.92f))?.target)
        val events = controller.update(0.96f)
        assertTrue(events.filterIsInstance<Event.Disarmed>().isNotEmpty())
    }

    @Test
    fun `release while armed commits fit or fill only`() {
        controller.begin(PlayerDisplayMode.FIT)
        controller.update(1.08f)
        val released = controller.release()
        assertTrue(released is Event.Commit && released.mode == PlayerDisplayMode.FILL)

        controller.begin(PlayerDisplayMode.FILL)
        controller.update(0.92f)
        val secondRelease = controller.release()
        assertTrue(secondRelease is Event.Commit && secondRelease.mode == PlayerDisplayMode.FIT)
    }

    @Test
    fun `release while neutral restores committed mode`() {
        controller.begin(PlayerDisplayMode.STRETCH)
        controller.update(1.05f)
        val released = controller.release()
        assertTrue(released is Event.Restore && released.mode == PlayerDisplayMode.STRETCH)
    }

    @Test
    fun `release after disarm restores committed mode`() {
        controller.begin(PlayerDisplayMode.FIT)
        controller.update(1.08f)
        controller.update(1.04f)
        val released = controller.release()
        assertTrue(released is Event.Restore && released.mode == PlayerDisplayMode.FIT)
    }

    @Test
    fun `cancel restores committed mode from any state`() {
        controller.begin(PlayerDisplayMode.STRETCH)
        controller.update(0.92f)
        val cancelled = controller.cancel()
        assertTrue(cancelled is Event.Cancelled && cancelled.mode == PlayerDisplayMode.STRETCH)
    }

    @Test
    fun `armed fires once per armed target`() {
        controller.begin(PlayerDisplayMode.FIT)
        controller.update(1.08f)
        val events = controller.update(1.10f)
        assertTrue(armedTarget(events) == null)
        val preview = preview(events)
        assertEquals(1f, preview?.progress ?: -1f, 0.0001f)
    }

    @Test
    fun `commit updates the committed mode for the next pinch`() {
        controller.begin(PlayerDisplayMode.FIT)
        controller.update(1.08f)
        controller.release()
        assertEquals(PlayerDisplayMode.FILL, controller.committedMode)

        controller.begin(controller.committedMode)
        val preview = preview(controller.update(0.95f))
        assertEquals(PlayerDisplayMode.FIT, preview?.toward)
    }

    @Test
    fun `begin resets state from a previous sequence`() {
        controller.begin(PlayerDisplayMode.FIT)
        controller.update(1.08f)
        controller.begin(PlayerDisplayMode.FIT)
        val released = controller.release()
        assertTrue(released is Event.Restore)
    }
}
