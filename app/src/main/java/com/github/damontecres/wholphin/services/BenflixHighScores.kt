package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.util.WholphinDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
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
 *
 * Two people whose names start the same (Ben and Benny) get different codes: the first keeps
 * "BEN", the next gets "BE2", and so on. The admin works the codes out and saves each person's
 * code in their settings, so everyone's Benflix uses the same one.
 *
 * Ember Road trail markers ride along: each person saves their own markers, and the admin copies
 * everyone's markers (with their codes) to everyone, the same way as the board.
 */
@Singleton
class BenflixHighScores
    @Inject
    constructor(
        private val api: ApiClient,
        private val displayPreferencesService: DisplayPreferencesService,
    ) {
        /** Copies the board to everyone in the background, even after the Games page is closed */
        private val shareScope = CoroutineScope(SupervisorJob() + WholphinDispatchers.IO)

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
                var myName = shortName(userName)
                val myScores = mine.filterValues { it > 0 }
                try {
                    val prefs = displayPreferencesService.getDisplayPreferences(userId).customPrefs
                    prefs[KEY_CODE]?.takeIf { it.isNotBlank() }?.let { myName = it }
                    val saved = prefs[KEY_MINE]?.let { decodeMine(it) }.orEmpty()
                    // Keep the higher of the server's and this device's score, so another TV's best isn't lost
                    val merged = (saved.keys + myScores.keys).associateWith { maxOf(saved[it] ?: 0, myScores[it] ?: 0) }
                    if (merged != saved) {
                        displayPreferencesService.updateDisplayPreferences(userId) { put(KEY_MINE, encodeMine(merged)) }
                    }
                    if (mine.isNotEmpty()) onMyBests(merged)
                    if (isAdmin) gather() else readBoard(userId, myName, merged)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Could not sync high scores")
                    sorted(myScores.mapValues { (_, score) -> listOf(HighScore(myName, score)) })
                }
            }

        /**
         * Admin only: read every account's scores, all at once (one by one was slow with many accounts).
         * The board is returned straight away; copying it to everyone happens after, in the background.
         */
        private suspend fun gather(): Map<String, List<HighScore>> =
            coroutineScope {
                val users = api.userApi.getUsers().content
                val codes = uniqueCodes(users.map { it.id to it.name })
                val marks = mutableListOf<String>()
                val all =
                    users
                        .map { user ->
                            async {
                                try {
                                    val prefs = displayPreferencesService.getDisplayPreferences(user.id).customPrefs
                                    val scores = prefs[KEY_MINE]?.let { decodeMine(it) }.orEmpty()
                                    val code = codes[user.id] ?: shortName(user.name)
                                    prefs[KEY_EMBER_MARKS]?.takeIf { it.isNotBlank() }?.let { mine ->
                                        synchronized(marks) { marks += mine.split('\n').filter { it.isNotBlank() }.map { "$code>$it" } }
                                    }
                                    code to scores
                                } catch (ex: CancellationException) {
                                    throw ex
                                } catch (ex: Exception) {
                                    Timber.w(ex, "Could not read scores for %s", user.name)
                                    null
                                }
                            }
                        }.awaitAll()
                val board = mutableMapOf<String, MutableList<HighScore>>()
                all.filterNotNull().forEach { (name, scores) ->
                    scores.forEach { (game, score) -> if (score > 0) board.getOrPut(game) { mutableListOf() }.add(HighScore(name, score)) }
                }
                val result = sorted(board)
                val encoded = encodeBoard(result)
                val family = marks.joinToString("\n")
                // Copy the board to everyone without making the Games page wait for it
                shareScope.launch {
                    users
                        .map { user ->
                            async {
                                try {
                                    val current = displayPreferencesService.getDisplayPreferences(user.id).customPrefs
                                    val code = codes[user.id]
                                    if (current[KEY_BOARD] != encoded || current[KEY_CODE] != code || current[KEY_EMBER_FAMILY] != family) {
                                        displayPreferencesService.updateDisplayPreferences(user.id) {
                                            put(KEY_BOARD, encoded)
                                            if (code != null) put(KEY_CODE, code)
                                            put(KEY_EMBER_FAMILY, family)
                                        }
                                    }
                                } catch (ex: CancellationException) {
                                    throw ex
                                } catch (ex: Exception) {
                                    Timber.w(ex, "Could not share the board with %s", user.name)
                                }
                            }
                        }.awaitAll()
                }
                result
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

            /** This person's 3-letter code, worked out by the admin so two Bens don't clash */
            const val KEY_CODE = "benflix_code"

            /** This person's own Ember Road trail markers, one per line */
            const val KEY_EMBER_MARKS = "benflix_ember_marks"

            /** Everyone's Ember Road trail markers, "CODE>marker" per line, shared by the admin */
            const val KEY_EMBER_FAMILY = "benflix_ember_family"

            /**
             * Gives every account a different 3-letter code. Oldest-named first keeps the plain code
             * (BEN), the next with the same letters gets BE2, then BE3 and so on.
             */
            fun uniqueCodes(users: List<Pair<UUID, String?>>): Map<UUID, String> {
                val taken = mutableSetOf<String>()
                val result = mutableMapOf<UUID, String>()
                users.sortedWith(compareBy({ it.second.orEmpty().lowercase() }, { it.first.toString() })).forEach { (id, name) ->
                    val plain = shortName(name)
                    var code = plain
                    var n = 2
                    while (code in taken) {
                        val num = n.toString()
                        code = plain.take((3 - num.length).coerceAtLeast(1)) + num
                        n++
                    }
                    taken += code
                    result[id] = code
                }
                return result
            }

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
            fun encodeBoard(board: Map<String, List<HighScore>>) =
                board.entries.joinToString(";") { (game, list) ->
                    game + ":" + list.joinToString("|") { "${it.name}=${it.score}" }
                }

            fun decodeBoard(value: String): Map<String, List<HighScore>> =
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
