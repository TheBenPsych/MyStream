package com.github.damontecres.wholphin.ui.door

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.theme.BenflixDoor
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * Navigation redesign (Nov 2026, approved s4-panel): a door's places (Home, All movies, Genres, Guide…) live in
 * the slide-out panel of the Benflix rail (TV) or in a sheet (phones), not inside the door any more. Each door
 * tells this what places it has whenever it's open; they're also saved on this TV or phone, so the panel can
 * list them for a door that hasn't been opened since the app started.
 */
object DoorPlaces {
    /** The places each door has (the first one is always the door's own Home) */
    val sections = mutableStateMapOf<BenflixDoor, List<DoorSection>>()

    private val frames = ConcurrentHashMap<BenflixDoor, DoorFrameState>()

    /** A place picked from outside the door, waiting for the door to open and show it */
    private val pending = ConcurrentHashMap<BenflixDoor, String>()

    @Volatile private var loaded = false

    /** The door's places, or just its Home when it hasn't told us yet */
    fun of(door: BenflixDoor): List<DoorSection> = sections[door]?.takeIf { it.isNotEmpty() } ?: listOf(doorHome())

    /** The door that's on screen right now has this frame */
    fun frame(door: BenflixDoor): DoorFrameState? = frames[door]

    internal fun attach(state: DoorFrameState) {
        frames[state.door] = state
    }

    internal fun detach(state: DoorFrameState) {
        frames.remove(state.door, state)
    }

    /** Opens [key] the next time [door] shows its page */
    fun openLater(
        door: BenflixDoor,
        key: String,
    ) {
        pending[door] = key
    }

    internal fun takePending(door: BenflixDoor): String? = pending.remove(door)

    internal fun update(
        context: Context,
        door: BenflixDoor,
        list: List<DoorSection>,
    ) {
        if (list.isEmpty() || sections[door] == list) return
        sections[door] = list
        try {
            context
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(door.name, list.joinToString("\n") { "${it.key}|${it.label}|${it.icon.name}|${it.count ?: ""}" })
                .apply()
        } catch (ex: Exception) {
            Timber.w(ex, "Could not keep %s's places", door)
        }
    }

    /** Reads the places saved last time (once) */
    fun load(context: Context) {
        if (loaded) return
        loaded = true
        try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            BenflixDoor.entries.forEach { door ->
                if (sections.containsKey(door)) return@forEach
                val saved = prefs.getString(door.name, null) ?: return@forEach
                val list =
                    saved.split("\n").mapNotNull { line ->
                        val parts = line.split("|")
                        if (parts.size < 3) return@mapNotNull null
                        val icon = BenflixIcon.entries.firstOrNull { it.name == parts[2] } ?: BenflixIcon.LIST
                        DoorSection(parts[0], parts[1], icon, parts.getOrNull(3)?.toIntOrNull())
                    }
                if (list.isNotEmpty()) sections[door] = list
            }
        } catch (ex: Exception) {
            Timber.w(ex, "Could not read the doors' places")
        }
    }

    private const val PREFS = "benflix_door_places"
}
