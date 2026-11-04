package com.github.damontecres.wholphin.services

import timber.log.Timber
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Benflix: the little trailer players (details page trailer, Home banner preview, Clips) each hold
 * one of the TV's video decoders, even while paused or stopping. Cheap TVs only have one or two of
 * those, so a movie started right after a trailer could get the slow backup decoder and play with a
 * low frame rate until it was restarted. Before a movie or episode starts, all of them let go.
 */
object PreviewPlayers {
    private val holders = CopyOnWriteArraySet<() -> Unit>()

    /** [free] releases the player; it runs on the main thread. Returns a function that unregisters it */
    fun register(free: () -> Unit): () -> Unit {
        holders += free
        return { holders -= free }
    }

    /** Release every trailer player now. Call on the main thread, before making the movie's player */
    fun freeAll() {
        holders.forEach {
            try {
                it()
            } catch (ex: Exception) {
                Timber.w(ex, "Couldn't free a trailer player")
            }
        }
    }
}
