package com.github.damontecres.wholphin.services

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

/** One team someone can follow (Tower's benflix_teams.py lists them all) */
data class SportsTeam(
    val id: String,
    val league: String,
    /** "Kansas City Chiefs" */
    val name: String,
    /** "Chiefs" */
    val nick: String,
    /** "KC" */
    val short: String,
    /** The team's colour, like "#E31837" */
    val colour: String,
    /** The ways its name shows up in a guide */
    val words: List<String>,
)

/** A league's teams, for the picker */
data class SportsLeague(
    val key: String,
    val name: String,
    val teams: List<SportsTeam>,
)

/** A game (or show) about one of your teams in the Live TV guide */
data class TeamGame(
    val programId: String,
    val channelId: UUID?,
    val channel: String,
    val title: String,
    val episode: String,
    val startMs: Long,
    val endMs: Long,
    val teams: List<String>,
) {
    fun liveAt(now: Long) = now in startMs until endMs
}

/**
 * Glowfin "Follow your teams" (Idea Box 28, Ben 2026-11-24). Each person picks their teams on Live TV's Sports
 * tab (or Settings > Watching); Tower keeps the picks. Their games come first on the Sports tab, get a gold
 * "Your team" tag in the guide, and a corner card says "Your team is on" when one starts.
 */
object BenflixTeams {
    private val _mine = MutableStateFlow<List<SportsTeam>>(emptyList())

    /** The teams this person follows */
    val mine: StateFlow<List<SportsTeam>> = _mine

    private val _notNow = MutableStateFlow(false)

    /** They pressed Not now on the first-time card: it shrinks to a small "+ Pick teams" tile */
    val notNow: StateFlow<Boolean> = _notNow

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded

    private var leagues: List<SportsLeague> = emptyList()
    private var loadedFor: UUID? = null
    private var matcher: Regex? = null

    private fun team(json: JSONObject): SportsTeam {
        val words = json.optJSONArray("words") ?: JSONArray()
        return SportsTeam(
            id = json.optString("id"),
            league = json.optString("league"),
            name = json.optString("name"),
            nick = json.optString("nick"),
            short = json.optString("short"),
            colour = json.optString("colour", "#2b2b2b"),
            words = (0 until words.length()).map { words.optString(it) }.filter { it.isNotBlank() },
        )
    }

    private fun teams(array: JSONArray?): List<SportsTeam> =
        (0 until (array?.length() ?: 0)).mapNotNull {
            array?.optJSONObject(it)?.let(::team)
        }

    private fun setMine(list: List<SportsTeam>) {
        _mine.value = list
        val words = list.flatMap { it.words }.distinct().sortedByDescending { it.length }
        // The same rule as Tower: whole words, and "Michigan" never matches "Michigan State"
        matcher =
            if (words.isEmpty()) {
                null
            } else {
                Regex(
                    "(?<![\\w])(" + words.joinToString("|") { Regex.escape(it) } + ")(?![\\w])(?!\\s+State\\b)",
                    RegexOption.IGNORE_CASE,
                )
            }
    }

    /** True when a guide entry is about one of your teams (for the gold "Your team" tag) */
    fun isMine(text: String?): Boolean = text != null && matcher?.containsMatchIn(text) == true

    /** The leagues and your picks (once per person, unless [force]) */
    suspend fun load(
        helper: GameLibraryClient,
        user: UUID?,
        force: Boolean = false,
    ): List<SportsLeague> {
        if (!force && loadedFor == user && leagues.isNotEmpty()) return leagues
        if (loadedFor != user) {
            setMine(emptyList())
            _notNow.value = false
            _loaded.value = false
        }
        val json = helper.getJson("/api/teams")
        val list = json.optJSONArray("leagues") ?: JSONArray()
        leagues =
            (0 until list.length()).mapNotNull { i ->
                list.optJSONObject(i)?.let { SportsLeague(it.optString("key"), it.optString("name"), teams(it.optJSONArray("teams"))) }
            }
        setMine(teams(json.optJSONArray("mine")))
        _notNow.value = json.optBoolean("notNow")
        loadedFor = user
        _loaded.value = true
        return leagues
    }

    suspend fun save(
        helper: GameLibraryClient,
        picked: List<SportsTeam>,
    ) {
        setMine(picked)
        helper.postJson("/api/teams", JSONObject().put("teams", JSONArray(picked.map { it.id })))
    }

    suspend fun notNow(helper: GameLibraryClient) {
        _notNow.value = true
        helper.postJson("/api/teams", JSONObject().put("notNow", true))
    }

    /** Your teams' games in the guide for the next 7 days, soonest first */
    suspend fun games(helper: GameLibraryClient): List<TeamGame> {
        val json = helper.getJson("/api/teams/games")
        val list = json.optJSONArray("games") ?: return emptyList()
        return (0 until list.length()).mapNotNull { i ->
            val g = list.optJSONObject(i) ?: return@mapNotNull null
            val start = parseTime(g.optString("start")) ?: return@mapNotNull null
            val end = parseTime(g.optString("end")) ?: (start + 3 * 3600_000L)
            val teamIds = g.optJSONArray("teams")
            TeamGame(
                programId = g.optString("id"),
                channelId = g.optString("channelId").let(::parseId),
                channel = g.optString("channel"),
                title = g.optString("title"),
                episode = g.optString("episode"),
                startMs = start,
                endMs = end,
                teams = (0 until (teamIds?.length() ?: 0)).map { teamIds!!.optString(it) },
            )
        }
    }

    private fun parseTime(text: String): Long? =
        try {
            // Jellyfin sends "2026-11-24T01:00:00.0000000Z"
            Instant.parse(text.replace(Regex("\\.(\\d{3})\\d*Z$"), ".$1Z")).toEpochMilli()
        } catch (_: Exception) {
            null
        }

    private fun parseId(text: String): UUID? =
        try {
            val hex = text.replace("-", "")
            if (hex.length != 32) {
                null
            } else {
                UUID.fromString(
                    "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}",
                )
            }
        } catch (_: Exception) {
            null
        }
}
