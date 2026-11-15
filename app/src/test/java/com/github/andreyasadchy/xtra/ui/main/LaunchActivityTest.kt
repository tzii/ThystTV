package com.github.andreyasadchy.xtra.ui.main

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import com.github.andreyasadchy.xtra.ui.login.LoginActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

/**
 * Going back to ThystTV from its icon must not close the screens above the main one, such as
 * sign-in while the viewer reads a code from an authenticator app.
 */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(application = Application::class, sdk = [34])
class LaunchActivityTest {
    private val context: Application = RuntimeEnvironment.getApplication()

    private fun launchers(category: String) = context.packageManager
        .queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category).setPackage(context.packageName), 0)
        .map { it.activityInfo.name }

    private fun info(cls: Class<*>) = context.packageManager.getActivityInfo(ComponentName(context, cls), 0)

    @Test
    fun `the launchers open a standard entry, not the singleTask main screen`() {
        for (category in listOf(Intent.CATEGORY_LAUNCHER, Intent.CATEGORY_LEANBACK_LAUNCHER)) {
            val names = launchers(category)
            assertTrue("$category: $names", LaunchActivity::class.java.name in names)
            // A launcher intent delivered to a singleTask activity closes everything above it.
            assertFalse("$category: $names", MainActivity::class.java.name in names)
        }
        assertEquals(ActivityInfo.LAUNCH_MULTIPLE, info(LaunchActivity::class.java).launchMode)
        assertTrue(info(LaunchActivity::class.java).exported)
        // Links and notifications still need one main screen, in its own task.
        assertEquals(ActivityInfo.LAUNCH_SINGLE_TASK, info(MainActivity::class.java).launchMode)
    }

    @Test
    fun `sign-in survives resizing, folding and theme changes without being rebuilt`() {
        val handled = info(LoginActivity::class.java).configChanges
        for (change in listOf(
            ActivityInfo.CONFIG_ORIENTATION,
            ActivityInfo.CONFIG_SCREEN_SIZE,
            ActivityInfo.CONFIG_SMALLEST_SCREEN_SIZE,
            ActivityInfo.CONFIG_SCREEN_LAYOUT,
            ActivityInfo.CONFIG_UI_MODE,
            ActivityInfo.CONFIG_KEYBOARD_HIDDEN,
        )) {
            assertEquals("0x${change.toString(16)}", change, handled and change)
        }
    }

    @Test
    fun `a first launch opens the main screen with the launcher intent`() {
        val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        val controller = Robolectric.buildActivity(LaunchActivity::class.java, launch)
        shadowOf(controller.get()).setIsTaskRoot(true)
        controller.create()

        val started = shadowOf(controller.get()).nextStartedActivity
        assertEquals(MainActivity::class.java.name, started.component?.className)
        assertEquals(Intent.ACTION_MAIN, started.action)
        assertTrue(started.hasCategory(Intent.CATEGORY_LAUNCHER))
        // Started from inside the task, so the launcher's task flags are not passed on.
        assertEquals(0, started.flags)
        assertTrue(controller.get().isFinishing)
    }

    @Test
    fun `a launch into a running task only reveals what was open`() {
        val controller = Robolectric.buildActivity(LaunchActivity::class.java,
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER))
        shadowOf(controller.get()).setIsTaskRoot(false)
        controller.create()

        assertNull(shadowOf(controller.get()).nextStartedActivity)
        assertTrue(controller.get().isFinishing)
    }
}
