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

    /**
     * What this device saved for [userId]: (kids, big kids, guest). The game player runs in its own process, so it
     * reads this itself instead of only trusting what it was told when it started.
     */
    fun saved(
        context: Context,
        userId: String?,
    ): Triple<Boolean, Boolean, Boolean> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val sameUser = userId.isNullOrBlank() || prefs.getString(KEY_USER, null).equals(userId, ignoreCase = true)
        if (!sameUser) return Triple(false, false, false)
        val kids = prefs.getBoolean(KEY_KIDS, false)
        return Triple(kids, kids && prefs.getBoolean(KEY_BIG, false), prefs.getBoolean(KEY_GUEST, false))
    }

    /**
     * When Benflix opens: bring back what was saved, before anything is drawn (whose sign-in it was is checked
     * a moment later by [restore]). Leaves things alone if Kids or Guest is already on.
     */
    fun restoreEarly(context: Context) {
        if (KidsMode.isActive || GuestMode.isActive) return
        val (kids, big, guest) = saved(context, null)
        if (kids || guest) set(kids, big, guest)
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
