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
    private const val RECENT_MAX = 5

    @Volatile private var prefs: SharedPreferences? = null

    private val _favourites = MutableStateFlow<List<String>>(emptyList())
    val favourites: StateFlow<List<String>> = _favourites

    private val _recent = MutableStateFlow<List<String>>(emptyList())

    /** Newest first */
    val recent: StateFlow<List<String>> = _recent

    private val _hidden = MutableStateFlow<Set<String>>(emptySet())

    /** Channel type names (as in [ChannelSchedule.group]) the person doesn't want to see */
    val hidden: StateFlow<Set<String>> = _hidden

    /** Called once when the app starts */
    fun load(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        _favourites.value = read(p, FAVOURITES)
        _recent.value = read(p, RECENT)
        _hidden.value = read(p, HIDDEN).toSet()
    }

    fun isFavourite(key: String) = key in _favourites.value

    fun toggleFavourite(key: String) {
        val now = _favourites.value
        _favourites.value = if (key in now) now - key else now + key
        save(FAVOURITES, _favourites.value)
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
