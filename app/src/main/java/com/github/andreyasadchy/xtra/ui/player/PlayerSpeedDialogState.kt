package com.github.andreyasadchy.xtra.ui.player

object PlayerSpeedDialogState {

    fun initialSpeed(currentSpeed: Float?, savedSpeed: Float): Float {
        return currentSpeed ?: savedSpeed
    }
}
