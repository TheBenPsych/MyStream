package com.github.damontecres.wholphin.services

import org.json.JSONObject
import java.util.UUID

/**
 * A channel the person made themselves (approved Live TV "Make a channel"): one show, one genre or one
 * collection that plays nonstop, like "The Simpsons 24/7". It sits with the 10 "No ads" channels.
 */
data class CustomChannel(
    val id: String,
    val name: String,
    val kind: Kind,
    /** The show or collection (for [Kind.SHOW] and [Kind.COLLECTION]) */
    val targetId: UUID? = null,
    /** The genre's name (for [Kind.GENRE]) */
    val genre: String? = null,
    /** For a genre: movies, shows or both */
    val what: What = What.BOTH,
    /** Episodes (or collection movies) in order instead of shuffled */
    val inOrder: Boolean = false,
) {
    enum class Kind { SHOW, GENRE, COLLECTION }

    enum class What { MOVIES, SHOWS, BOTH }

    /** What favourites and Recently watched save it as */
    val key: String get() = KEY_PREFIX + id

    fun encode(): String =
        JSONObject()
            .put("id", id)
            .put("name", name)
            .put("kind", kind.name)
            .put("target", targetId?.toString())
            .put("genre", genre)
            .put("what", what.name)
            .put("inOrder", inOrder)
            .toString()

    companion object {
        const val KEY_PREFIX = "mine:"

        /** At most this many home-made channels, so the guide stays quick */
        const val MAX = 10

        fun decode(text: String): CustomChannel? =
            try {
                val o = JSONObject(text)
                CustomChannel(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    kind = Kind.valueOf(o.getString("kind")),
                    targetId = o.optString("target").takeIf { it.isNotEmpty() && it != "null" }?.let(UUID::fromString),
                    genre = o.optString("genre").takeIf { it.isNotEmpty() && it != "null" },
                    what = runCatching { What.valueOf(o.optString("what")) }.getOrDefault(What.BOTH),
                    inOrder = o.optBoolean("inOrder", false),
                )
            } catch (ex: Exception) {
                null
            }

        /** The name it gets, like "The Simpsons 24/7" */
        fun nameFor(picked: String): String = "$picked 24/7"
    }
}
