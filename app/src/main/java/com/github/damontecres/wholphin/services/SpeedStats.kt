package com.github.damontecres.wholphin.services

/**
 * A few timings Benflix remembers so the hidden speed meter (tap "Installed version" 5 times
 * in Settings) can show where time goes. Nothing here is sent anywhere.
 */
object SpeedStats {
    /** How long Home took to show its top rows, in milliseconds */
    @Volatile var homeTopRowsMs: Long? = null

    /** Whether the last Home was shown instantly from what was saved last time */
    @Volatile var homeFromSaved: Boolean = false

    /** How long the first Clips video took to be ready, in milliseconds */
    @Volatile var clipsFirstMs: Long? = null
}
