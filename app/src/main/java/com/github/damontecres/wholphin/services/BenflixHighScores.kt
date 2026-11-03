package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.util.WholphinDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.model.UUID
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** One spot on the "Highest scores" board: an account's 3-letter name and its best score */
data class HighScore(
    val name: String,
    val score: Int,
)

/**
 * Benflix "Highest scores": every account's best score in each game, shown arcade style with
 * the first 3 letters of their name.
 *
 * Jellyfin only lets each person read their own settings, so:
 * - each person's Benflix saves their own best scores into their own Jellyfin display settings;
 * - when an admin opens the Games page, Benflix gathers everyone's scores and copies the whole
 *   board into every person's settings (only an admin is allowed to do that, like Notes from Ben);
 * - everyone else reads the board from their own settings, with their own newest scores mixed in.
 * So other people's newest scores show up after the admin next opens Games.
 */
@Singleton
class BenflixHighScores
    @Inject
    constructor(
        private val api: ApiClient,
        private val displayPreferencesService: DisplayPreferencesService,
    ) {
        /**
         * Saves this account's best scores (game id to score) to the server, then returns the board:
         * game id to the scores, highest first.
         */
        suspend fun sync(
            userId: UUID,
            userName: String?,
            isAdmin: Boolean,
            mine: Map<String, Int>,
            /** This account's bests from every device, so a new TV or phone shows the right "Your best" */
            onMyBests: (Map<String, Int>) -> Unit = {},
        ): Map<String, List<HighScore>> =
            withContext(WholphinDispatchers.IO) {
                val myName = shortName(userName)
                val myScores = mine.filterValues { it > 0 }
                try {
                    val saved =
                        displayPreferencesService
                            .getDisplayPreferences(userId)
                            .customPrefs[KEY_MINE]
                            ?.let { decodeMine(it) }
                            .orEmpty()
                    // Keep the higher of the server's and this device's score, so another TV's best isn't lost
                    val merged = (saved.keys + myScores.keys).associateWith { maxOf(saved[it] ?: 0, myScores[it] ?: 0) }
                    if (merged != saved) {
                        displayPreferencesService.updateDisplayPreferences(userId) { put(KEY_MINE, encodeMine(merged)) }
                    }
                    if (mine.isNotEmpty()) onMyBests(merged)
                    if (isAdmin) gatherAndShare() else readBoard(userId, myName, merged)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Could not sync high scores")
                    sorted(myScores.mapValues { (_, score) -> listOf(HighScore(myName, score)) })
                }
            }

        /** Admin only: read every account's scores and copy the board to everyone */
        private suspend fun gatherAndShare(): Map<String, List<HighScore>> {
            val users = api.userApi.getUsers().content
            val board = mutableMapOf<String, MutableList<HighScore>>()
            users.forEach { user ->
                try {
                    val scores =
                        displayPreferencesService
                            .getDisplayPreferences(user.id)
                            .customPrefs[KEY_MINE]
                            ?.let { decodeMine(it) }
                            .orEmpty()
                    val name = shortName(user.name)
                    scores.forEach { (game, score) -> if (score > 0) board.getOrPut(game) { mutableListOf() }.add(HighScore(name, score)) }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Could not read scores for %s", user.name)
                }
            }
            val result = sorted(board)
            val encoded = encodeBoard(result)
            users.forEach { user ->
                try {
                    val current = displayPreferencesService.getDisplayPreferences(user.id).customPrefs[KEY_BOARD]
                    if (current != encoded) displayPreferencesService.updateDisplayPreferences(user.id) { put(KEY_BOARD, encoded) }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Could not share the board with %s", user.name)
                }
            }
            return result
        }

        /** Everyone else: the board the admin last shared, with this account's own newest scores mixed in */
        private suspend fun readBoard(
            userId: UUID,
            myName: String,
            mine: Map<String, Int>,
        ): Map<String, List<HighScore>> {
            val shared =
                displayPreferencesService
                    .getDisplayPreferences(userId)
                    .customPrefs[KEY_BOARD]
                    ?.let { decodeBoard(it) }
                    .orEmpty()
            val board = shared.mapValues { it.value.toMutableList() }.toMutableMap()
            mine.forEach { (game, score) ->
                if (score <= 0) return@forEach
                val list = board.getOrPut(game) { mutableListOf() }
                val existing = list.indexOfFirst { it.name == myName }
                if (existing < 0) {
                    list.add(HighScore(myName, score))
                } else if (list[existing].score < score) {
                    list[existing] = HighScore(myName, score)
                }
            }
            return sorted(board)
        }

        private fun sorted(board: Map<String, List<HighScore>>) = board.mapValues { (_, list) -> list.sortedByDescending { it.score } }

        companion object {
            private const val KEY_MINE = "benflix_scores"
            private const val KEY_BOARD = "benflix_scoreboard"

            /** The first 3 letters (or digits) of a name, in capitals, arcade style */
            fun shortName(name: String?): String =
                name
                    .orEmpty()
                    .filter { it.isLetterOrDigit() }
                    .take(3)
                    .uppercase()
                    .ifEmpty { "???" }

            private fun encodeMine(scores: Map<String, Int>) = scores.entries.joinToString(",") { "${it.key}=${it.value}" }

            private fun decodeMine(value: String): Map<String, Int> =
                value
                    .split(',')
                    .mapNotNull { part ->
                        val game = part.substringBefore('=', "").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                        val score = part.substringAfter('=').toIntOrNull() ?: return@mapNotNull null
                        game to score
                    }.toMap()

            // game:NAM=score|NAM=score;game:...
            private fun encodeBoard(board: Map<String, List<HighScore>>) =
                board.entries.joinToString(";") { (game, list) ->
                    game + ":" + list.joinToString("|") { "${it.name}=${it.score}" }
                }

            private fun decodeBoard(value: String): Map<String, List<HighScore>> =
                value
                    .split(';')
                    .mapNotNull { part ->
                        val game = part.substringBefore(':', "").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                        val list =
                            part.substringAfter(':').split('|').mapNotNull { entry ->
                                val name = entry.substringBefore('=', "").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                                val score = entry.substringAfter('=').toIntOrNull() ?: return@mapNotNull null
                                HighScore(name, score)
                            }
                        game to list
                    }.toMap()
        }
    }
