package com.github.andreyasadchy.xtra.ui.following

import android.app.Application
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.FragmentMediaPagerBinding
import com.google.android.material.tabs.TabLayoutMediator
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(application = Application::class, sdk = [28])
class FollowPagerFragmentLifecycleTest {
    @Test fun `destroying pager view detaches mediator and fragment adapter lifecycle observer`() {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        try {
            val context = ContextThemeWrapper(activity.get(), R.style.BaseDarkTheme)
            val fragment = FollowPagerFragment()
            val owner = object : LifecycleOwner {
                override val lifecycle = LifecycleRegistry(this).apply { currentState = Lifecycle.State.CREATED }
            }
            // Keep the fragment lifecycle alive while replacing its view, as navigation does.
            repeat(2) {
                val binding = FragmentMediaPagerBinding.bind(fragment.onCreateView(LayoutInflater.from(context), null, null))
                val adapter = object : FragmentStateAdapter(activity.get().supportFragmentManager, owner.lifecycle) {
                    override fun getItemCount() = 1
                    override fun createFragment(position: Int) = Fragment()
                }
                val baselineObservers = owner.lifecycle.observerCount
                binding.viewPager.adapter = adapter
                val mediator = TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, _ -> tab.text = "Following" }
                mediator.attach()
                ReflectionHelpers.setField(fragment, "tabLayoutMediator", mediator)
                assertTrue(mediator.isAttached)
                assertTrue(owner.lifecycle.observerCount > baselineObservers)

                fragment.onDestroyView()

                assertFalse(mediator.isAttached)
                assertNull(binding.viewPager.adapter)
                assertEquals("A surviving fragment lifecycle must not retain its old pager", baselineObservers, owner.lifecycle.observerCount)
                assertEquals(Lifecycle.State.CREATED, owner.lifecycle.currentState)
                assertNull(ReflectionHelpers.getField<Any?>(fragment, "tabLayoutMediator"))
                assertNull(ReflectionHelpers.getField<Any?>(fragment, "_binding"))
            }
        } finally {
            activity.pause().stop().destroy()
        }
    }
}
