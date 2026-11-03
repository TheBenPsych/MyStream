package com.github.damontecres.wholphin.services

import org.jellyfin.sdk.model.api.BaseItemDto
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** One app (Docker container) on Tower, like Sonarr or qBittorrent */
data class TowerApp(
    val name: String,
    val nice: String,
    val running: Boolean,
    val ok: Boolean,
    val status: String,
    val restartable: Boolean,
)

/** One download in Sonarr or Radarr */
data class TowerDownload(
    val app: String,
    val id: Int,
    val title: String,
    val percent: Int,
    val timeLeft: String?,
    val stuck: Boolean,
    val paused: Boolean,
    val queued: Boolean,
    val why: String?,
)

/** What the Benflix helper on Tower says, in one answer */
data class TowerStatus(
    val upSeconds: Long?,
    val cpu: Int?,
    val memory: Int?,
    val gpu: Int?,
    /** null when the helper can't see the apps */
    val apps: List<TowerApp>?,
    val downloads: List<TowerDownload>,
    val sonarr: Boolean,
    val radarr: Boolean,
    val tracksReady: Boolean,
    val problems: List<String>,
)

/** The track tidy tool's state */
data class TrackTidy(
    val ready: Boolean,
    val running: Boolean,
    val mode: String?,
    val folder: String?,
    val lines: List<String>,
    val result: String?,
    val folders: List<String>,
    val undoFiles: List<String>,
    val problem: String?,
)

/**
 * Benflix Server page tools that need Tower itself (admin only): apps, downloads, how busy the server is,
 * Sonarr/Radarr searches and the track tidy tool. They go through the Benflix helper on port 8097
 * (the same one the games use), which checks the person is the admin.
 */
@Singleton
class BenflixTower
    @Inject
    constructor(
        private val helper: GameLibraryClient,
    ) {
        suspend fun status(fresh: Boolean = false): TowerStatus {
            val j = helper.getJson("/api/admin/tower" + if (fresh) "?fresh=1" else "")
            return TowerStatus(
                upSeconds = j.longOrNull("up_seconds"),
                cpu = j.intOrNull("cpu"),
                memory = j.intOrNull("memory"),
                gpu = j.intOrNull("gpu"),
                apps =
                    j.optJSONArray("apps")?.objects()?.map {
                        TowerApp(
                            name = it.optString("name"),
                            nice = it.optString("nice", it.optString("name")),
                            running = it.optBoolean("running"),
                            ok = it.optBoolean("ok"),
                            status = it.optString("status"),
                            restartable = it.optBoolean("restartable"),
                        )
                    },
                downloads =
                    j.optJSONArray("downloads")?.objects().orEmpty().map {
                        TowerDownload(
                            app = it.optString("app"),
                            id = it.optInt("id"),
                            title = it.optString("title"),
                            percent = it.optInt("pct"),
                            timeLeft = it.textOrNull("left"),
                            stuck = it.optBoolean("stuck"),
                            paused = it.optBoolean("paused"),
                            queued = it.optBoolean("queued"),
                            why = it.textOrNull("why"),
                        )
                    },
                sonarr = j.optBoolean("sonarr"),
                radarr = j.optBoolean("radarr"),
                tracksReady = j.optBoolean("tracks"),
                problems = j.optJSONArray("problems")?.strings().orEmpty(),
            )
        }

        /** Each of these returns the helper's message in plain words, like "Sonarr restarted." */
        suspend fun restartApp(name: String): String = message(helper.postJson("/api/admin/apps/$name/restart"))

        suspend fun checkAgain(download: TowerDownload): String =
            message(helper.postJson("/api/admin/downloads/${download.app}/${download.id}/retry"))

        suspend fun replace(download: TowerDownload): String =
            message(helper.postJson("/api/admin/downloads/${download.app}/${download.id}/replace"))

        /** Asks Sonarr to find a show's missing episodes */
        suspend fun searchMissing(show: BaseItemDto): String = message(helper.postJson("/api/admin/search/missing", ids(show)))

        /** Asks Radarr to look for a better copy of a movie */
        suspend fun searchBetter(movie: BaseItemDto): String = message(helper.postJson("/api/admin/search/better", ids(movie)))

        suspend fun tracks(): TrackTidy = tidy(helper.getJson("/api/admin/tracks"))

        /** [mode] is "preview", "apply" or "undo" */
        suspend fun startTracks(
            mode: String,
            folder: String? = null,
            undoFile: String? = null,
        ): TrackTidy =
            tidy(
                helper.postJson(
                    "/api/admin/tracks",
                    JSONObject().put("mode", mode).put("folder", folder).put("undo", undoFile),
                ),
            )

        private fun ids(item: BaseItemDto): JSONObject {
            val p = item.providerIds.orEmpty()

            fun id(key: String) = p.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value
            return JSONObject()
                .put("name", item.name)
                .put("year", item.productionYear)
                .put("tvdb", id("Tvdb"))
                .put("tmdb", id("Tmdb"))
                .put("imdb", id("Imdb"))
        }

        private fun tidy(j: JSONObject) =
            TrackTidy(
                ready = j.optBoolean("ready"),
                running = j.optBoolean("running"),
                mode = j.textOrNull("mode"),
                folder = j.textOrNull("folder"),
                lines = j.optJSONArray("lines")?.strings().orEmpty(),
                result = j.textOrNull("result"),
                folders = j.optJSONArray("folders")?.strings().orEmpty(),
                undoFiles = j.optJSONArray("undo_files")?.strings().orEmpty(),
                problem = j.textOrNull("problem"),
            )

        private fun message(j: JSONObject) = j.optString("message", "Done.")
    }

private fun JSONObject.textOrNull(key: String): String? = if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

private fun JSONObject.intOrNull(key: String): Int? = if (isNull(key)) null else optInt(key)

private fun JSONObject.longOrNull(key: String): Long? = if (isNull(key)) null else optLong(key)

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

private fun JSONArray.strings(): List<String> = (0 until length()).map { optString(it) }
