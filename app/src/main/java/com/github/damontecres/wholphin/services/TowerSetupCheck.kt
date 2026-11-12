package com.github.damontecres.wholphin.services

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** Benflix: Tower's helper is older than this app needs, and what is waiting for the setup command */
data class TowerSetupNeed(
    val have: Int,
    val need: Int,
    /** Plain words for each thing that starts working once the setup command has run */
    val waiting: List<String>,
)

/**
 * Redesign 2 (Server and Settings, approved ss-p-setup): when the helper on Tower came from an older setup than
 * this app needs, the Server page shows a yellow card with the setup command (Copy + "Where do I paste it?") and
 * Ben gets a note on Home. The helper says which setup it came from on /health ("setup"); helpers from before
 * this check don't say, which counts as 0. Can't reach Tower (away from home, Tower off) = no card.
 */
@Singleton
class TowerSetupCheck
    @Inject
    constructor(
        private val helper: GameLibraryClient,
    ) {
        private val _need = MutableStateFlow<TowerSetupNeed?>(null)

        /** null = Tower is up to date (or couldn't be asked) */
        val need: StateFlow<TowerSetupNeed?> = _need

        @Volatile
        private var checkedAt = 0L

        /** Asks Tower (at most once a minute unless [fresh]) and returns what's waiting, or null */
        suspend fun check(fresh: Boolean = false): TowerSetupNeed? {
            val now = System.currentTimeMillis()
            if (!fresh && now - checkedAt < MIN_GAP_MS) return _need.value
            checkedAt = now
            val have =
                try {
                    helper.getJson("/health").optInt("setup", 0)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.i(ex, "Setup check: couldn't ask Tower")
                    return _need.value
                }
            _need.value =
                if (have >= SETUP_NEEDED) {
                    null
                } else {
                    TowerSetupNeed(
                        have = have,
                        need = SETUP_NEEDED,
                        waiting = WAITING.filterKeys { it > have }.values.flatten(),
                    )
                }
            return _need.value
        }

        companion object {
            /**
             * Bump this (and SETUP_VERSION in server/benflix-games-helper.py, and SETUP_NEEDED in web server.js)
             * whenever a change only works after the setup command runs again, and say what it is in [WAITING].
             */
            const val SETUP_NEEDED = 1

            /** For each setup number, what starts working once Tower has it */
            val WAITING: Map<Int, List<String>> =
                mapOf(
                    1 to
                        listOf(
                            "Benflix can tell you when Tower needs this command (this card)",
                            "Anything from the last few updates that said it needs the setup command again",
                        ),
                )

            /** The command, exactly as Ben pastes it into Unraid's Terminal */
            const val COMMAND =
                "true; curl -fsSL https://raw.githubusercontent.com/TheBenPsych/MyStream/main/server/" +
                    "benflix-games-helper-setup.sh | bash ; true"

            const val WHERE =
                "On your computer, open Unraid in the browser (http://10.0.0.58), click the Terminal button " +
                    "(the >_ icon at the top right), paste the command, and press Enter. It takes about a minute, " +
                    "and Benflix keeps working while it runs."

            private const val MIN_GAP_MS = 60_000L
        }
    }
