package com.github.damontecres.wholphin.services

import android.content.Context
import timber.log.Timber
import java.util.UUID

/**
 * Keeps Kids / Big kids / Guest on disk, so when Android closes the app (or "Sign in automatically"
 * brings back the borrowed grown-up sign-in) a child doesn't come back on the full grown-up profile.
 */
object ProfileModes {
    private const val PREFS = "benflix_profile_mode"
    private const val KEY_USER = "user"
    private const val KEY_KIDS = "kids"
    private const val KEY_BIG = "big"
    private const val KEY_GUEST = "guest"

    /** Turn Kids / Guest on or off in memory only (saved with [save] once the switch worked) */
    fun set(
        kids: Boolean,
        big: Boolean,
        guest: Boolean,
    ) {
        KidsMode.set(kids, big)
        GuestMode.set(guest)
    }

    /** Save the current Kids / Guest state for this sign-in */
    fun save(
        context: Context,
        userId: UUID,
    ) {
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_USER, userId.toString())
            .putBoolean(KEY_KIDS, KidsMode.isActive)
            .putBoolean(KEY_BIG, KidsMode.isActive && KidsMode.isBig)
            .putBoolean(KEY_GUEST, GuestMode.isActive)
            .apply()
    }

    /** Bring back the saved Kids / Guest state if it was saved for this sign-in, otherwise turn them off */
    fun restore(
        context: Context,
        userId: UUID,
    ) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val sameUser = prefs.getString(KEY_USER, null) == userId.toString()
        val kids = sameUser && prefs.getBoolean(KEY_KIDS, false)
        val big = kids && prefs.getBoolean(KEY_BIG, false)
        val guest = sameUser && prefs.getBoolean(KEY_GUEST, false)
        if (KidsMode.isActive != kids || KidsMode.isBig != big) KidsMode.set(kids, big)
        if (GuestMode.isActive != guest) GuestMode.set(guest)
        Timber.i("Restored profile mode: kids=%s, big=%s, guest=%s", kids, big, guest)
    }
}
