package com.github.andreyasadchy.xtra.ui.main

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * The home screen and TV launcher entry. It opens [MainActivity] and closes itself.
 *
 * [MainActivity] is `singleTask`, so when it received the launcher intent itself, Android
 * closed every screen above it first. Going back to ThystTV from its icon closed the sign-in
 * screen that way, and a viewer who had left to read a code from an authenticator app had to
 * start again. A launch into a running task now only brings that task forward, as in most
 * apps, and returns to whatever was open: sign-in, Settings or the main screen.
 */
class LaunchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Android adds this activity on top of a running task; closing it reveals that task.
        if (isTaskRoot) {
            startActivity(Intent(intent).setClass(this, MainActivity::class.java).setFlags(0))
        }
        finish()
    }
}
