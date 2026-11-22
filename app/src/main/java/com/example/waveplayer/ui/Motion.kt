package com.example.waveplayer.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween

/**
 * Animation tokens: few, short, one easing. Nothing here runs on its own; callers only use these durations.
 *  Quick      small state changes and exits
 *  Standard   enters, line highlight, content swaps
 *  Emphasized artwork and title changes, lyric re-centering
 *  Ambient    slow color shifts (accent, artwork background)
 *  DiscRotationMs  one full turn of the album disc (linear, only while audio is playing)
 */
object Motion {
    const val QuickMs = 200
    const val StandardMs = 300
    const val EmphasizedMs = 450
    const val AmbientMs = 700
    const val DiscRotationMs = 24_000

    fun <T> quick(): FiniteAnimationSpec<T> = tween(QuickMs, easing = FastOutSlowInEasing)
    fun <T> standard(): FiniteAnimationSpec<T> = tween(StandardMs, easing = FastOutSlowInEasing)
    fun <T> emphasized(): FiniteAnimationSpec<T> = tween(EmphasizedMs, easing = FastOutSlowInEasing)
    fun <T> ambient(): FiniteAnimationSpec<T> = tween(AmbientMs, easing = FastOutSlowInEasing)
}
