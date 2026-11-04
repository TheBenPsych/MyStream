package com.github.damontecres.wholphin.ui.main

import android.content.SharedPreferences

/**
 * Benflix: an oldest-first list of ids kept in [SharedPreferences], so trimming it to the newest few hundred
 * drops the oldest. (A string set has no order, so trimming one dropped random ids, which then popped up again.)
 */
internal object OrderedSeen {
    private const val SEP = "\n"

    private fun listKey(key: String) = "$key.list"

    fun has(
        prefs: SharedPreferences,
        key: String,
    ): Boolean = prefs.contains(listKey(key)) || prefs.contains(key)

    fun read(
        prefs: SharedPreferences,
        key: String,
    ): List<String> =
        prefs
            .getString(listKey(key), null)
            ?.split(SEP)
            ?.filter { it.isNotEmpty() }
            // Older versions kept an unordered set under the plain key
            ?: prefs.getStringSet(key, emptySet()).orEmpty().toList()

    fun add(
        prefs: SharedPreferences,
        key: String,
        ids: List<String>,
        max: Int,
    ) {
        val adding = ids.distinct()
        val list = (read(prefs, key).filter { it !in adding } + adding).takeLast(max)
        prefs
            .edit()
            .putString(listKey(key), list.joinToString(SEP))
            .remove(key)
            .apply()
    }
}
