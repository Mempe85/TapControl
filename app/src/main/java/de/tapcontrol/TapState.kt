package de.tapcontrol

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow

/** Gemeinsamer Zustand zwischen Dienst und Oberfläche (gleicher Prozess). */
object TapState {
    val running = MutableStateFlow(false)
    val lastGesture = MutableStateFlow<String?>(null)
    val lastTapStrength = MutableStateFlow<Float?>(null)

    private const val PREFS = "settings"
    const val KEY_THRESHOLD = "threshold"
    const val KEY_VIBRATE = "vibrate"
    const val KEY_SCREEN_OFF_ONLY = "screen_off_only"

    fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
