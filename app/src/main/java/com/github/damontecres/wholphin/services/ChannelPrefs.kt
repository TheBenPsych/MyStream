package com.github.damontecres.wholphin.services

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

/**
 * The person's Channels picks: starred channels, the last few channels watched and the channel
 * types they hid. Saved on the device and to their account (see BenflixSettingsSync), so they
 * follow the person between the TV and the phone.
 * Channels are saved by [ChannelSchedule.key] (their name), since live channel numbers can change.
 */
object ChannelPrefs {
    const val PREFS = "benflix_channel_prefs"
    private const val FAVOURITES = "favourites"
    private const val RECENT = "recent"
    private const val HIDDEN = "hidden"
    private const val REMINDERS = "reminders"
    private const val RECENT_MAX = 10

    @Volatile private var prefs: SharedPreferences? = null

    private val _favourites = MutableStateFlow<List<String>>(emptyList())
    val favourites: StateFlow<List<String>> = _favourites

    private val _recent = MutableStateFlow<List<String>>(emptyList())

    /** Newest first */
    val recent: StateFlow<List<String>> = _recent

    private val _hidden = MutableStateFlow<Set<String>>(emptySet())

    /** Channel type names (as in [ChannelSchedule.group]) the person doesn't want to see */
    val hidden: StateFlow<Set<String>> = _hidden

    private val _reminders = MutableStateFlow<List<ChannelReminder>>(emptyList())

    /** Shows the person asked to be reminded about (Live TV "Remind me"), on this device only */
    val reminders: StateFlow<List<ChannelReminder>> = _reminders

    fun hasReminder(
        key: String,
        startMs: Long,
    ) = _reminders.value.any { it.key == key && it.startMs == startMs }

    fun toggleReminder(reminder: ChannelReminder) {
        val now = System.currentTimeMillis()
        val kept = _reminders.value.filter { it.startMs > now - REMINDER_KEEP_MS }
        _reminders.value =
            if (kept.any { it.key == reminder.key && it.startMs == reminder.startMs }) {
                kept.filterNot { it.key == reminder.key && it.startMs == reminder.startMs }
            } else {
                kept + reminder
            }
        save(REMINDERS, _reminders.value.map { it.encode() })
    }

    /** Called once when the app starts */
    fun load(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        _favourites.value = read(p, FAVOURITES)
        _recent.value = read(p, RECENT)
        _hidden.value = read(p, HIDDEN).toSet()
        val now = System.currentTimeMillis()
        _reminders.value = read(p, REMINDERS).mapNotNull { ChannelReminder.decode(it) }.filter { it.startMs > now - REMINDER_KEEP_MS }
    }

    fun isFavourite(key: String) = key in _favourites.value

    fun toggleFavourite(key: String) {
        val now = _favourites.value
        _favourites.value = if (key in now) now - key else now + key
        save(FAVOURITES, _favourites.value)
    }

    /** Moves a favourite one place left (-1) or right (+1) in the Favourites row */
    fun moveFavourite(
        key: String,
        by: Int,
    ) {
        val list = _favourites.value.toMutableList()
        val from = list.indexOf(key)
        if (from < 0) return
        val to = (from + by).coerceIn(0, list.lastIndex)
        if (to == from) return
        list.removeAt(from)
        list.add(to, key)
        _favourites.value = list
        save(FAVOURITES, list)
    }

    fun addRecent(key: String) {
        _recent.value = (listOf(key) + _recent.value.filter { it != key }).take(RECENT_MAX)
        save(RECENT, _recent.value)
    }

    fun setHidden(
        group: String,
        hide: Boolean,
    ) {
        _hidden.value = if (hide) _hidden.value + group else _hidden.value - group
        save(HIDDEN, _hidden.value.toList())
    }

    /** Everything, for saving to the person's account */
    fun toJson(): JSONObject =
        JSONObject()
            .put(FAVOURITES, JSONArray(_favourites.value))
            .put(RECENT, JSONArray(_recent.value))
            .put(HIDDEN, JSONArray(_hidden.value.toList().sorted()))

    /** Swap in the picks saved on the person's account (when they pick their profile) */
    fun restore(json: JSONObject) {
        try {
            json.optJSONArray(FAVOURITES)?.let {
                _favourites.value = it.strings()
                save(FAVOURITES, _favourites.value)
            }
            json.optJSONArray(RECENT)?.let {
                _recent.value = it.strings().take(RECENT_MAX)
                save(RECENT, _recent.value)
            }
            json.optJSONArray(HIDDEN)?.let {
                _hidden.value = it.strings().toSet()
                save(HIDDEN, _hidden.value.toList())
            }
        } catch (ex: Exception) {
            Timber.w(ex, "Could not read saved channel picks")
        }
    }

    private fun JSONArray.strings(): List<String> = (0 until length()).mapNotNull { optString(it).takeIf { s -> s.isNotEmpty() } }

    private fun read(
        p: SharedPreferences,
        name: String,
    ): List<String> =
        try {
            p.getString(name, null)?.let { JSONArray(it).strings() }.orEmpty()
        } catch (ex: Exception) {
            emptyList()
        }

    private fun save(
        name: String,
        values: List<String>,
    ) {
        prefs?.edit()?.putString(name, JSONArray(values).toString())?.apply()
    }
}

/** A Live TV "Remind me": the channel, when the show starts and its name */
data class ChannelReminder(
    val key: String,
    val startMs: Long,
    val title: String,
) {
    fun encode() = "$startMs|$key|$title"

    companion object {
        fun decode(text: String): ChannelReminder? {
            val parts = text.split('|', limit = 3)
            if (parts.size < 3) return null
            val start = parts[0].toLongOrNull() ?: return null
            return ChannelReminder(key = parts[1], startMs = start, title = parts[2])
        }
    }
}

/** Old reminders are forgotten 10 minutes after their show started */
private const val REMINDER_KEEP_MS = 10 * 60_000L
