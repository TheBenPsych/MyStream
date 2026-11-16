package com.github.damontecres.wholphin.services

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.model.api.UpdateUserItemDataDto
import timber.log.Timber
import java.util.UUID

/**
 * Benflix (Idea Box 25) "Also counts for": two people watching together on one profile. In the player's
 * Settings panel Ben ticks the other person, and when each movie or episode stops, their profile gets the
 * same spot (or the same "watched" tick), so their Continue watching and Next up keep up.
 *
 * Only the main profile (an admin) can do this, since Jellyfin only lets an admin save another person's progress.
 * The ticks last until the player is closed, so a run of episodes all count.
 */
object AlsoCountsFor {
    data class Person(
        val id: UUID,
        val name: String,
    )

    private val _chosen = MutableStateFlow<List<Person>>(emptyList())
    val chosen: StateFlow<List<Person>> = _chosen

    /** The other family profiles (null while loading) */
    private val _family = MutableStateFlow<List<Person>?>(null)
    val family: StateFlow<List<Person>?> = _family

    /** Watched this far (or more) counts as finished */
    private const val FINISHED = 0.9

    fun toggle(person: Person) {
        _chosen.value =
            if (_chosen.value.any { it.id == person.id }) {
                _chosen.value.filterNot { it.id == person.id }
            } else {
                _chosen.value + person
            }
    }

    /** The player closed: nobody is ticked next time */
    fun clear() {
        _chosen.value = emptyList()
    }

    /** Reads the other family profiles once (switched-off accounts are left out) */
    suspend fun loadFamily(
        api: ApiClient,
        myId: UUID?,
    ) {
        if (_family.value != null) return
        _family.value =
            try {
                api.userApi
                    .getUsers(isDisabled = false)
                    .content
                    .filter { it.id != myId && it.policy?.isDisabled != true }
                    .map { Person(it.id, it.name.orEmpty().ifBlank { "Someone" }) }
                    .sortedBy { it.name.lowercase() }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Also counts for: family")
                emptyList()
            }
    }

    /**
     * Gives each of [people] the same progress on [itemId]: watched when it was nearly finished,
     * otherwise the same resume spot. Runs after "stopped" was sent for the person watching.
     */
    suspend fun copyTo(
        api: ApiClient,
        people: List<Person>,
        itemId: UUID,
        positionMs: Long,
        durationMs: Long,
    ) {
        if (people.isEmpty() || positionMs <= 0) return
        val finished = durationMs > 0 && positionMs >= durationMs * FINISHED
        people.forEach { person ->
            try {
                if (finished) {
                    api.playStateApi.markPlayedItem(itemId, person.id)
                } else {
                    api.itemsApi.updateItemUserData(
                        itemId = itemId,
                        userId = person.id,
                        data =
                            UpdateUserItemDataDto(
                                playbackPositionTicks = positionMs * TICKS_PER_MS,
                                lastPlayedDate = java.time.LocalDateTime.now(),
                            ),
                    )
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Also counts for %s", person.name)
            }
        }
    }

    private const val TICKS_PER_MS = 10_000L
}
