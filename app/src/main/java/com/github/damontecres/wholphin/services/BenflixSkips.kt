package com.github.damontecres.wholphin.services

import kotlinx.coroutines.CancellationException
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.MediaSegmentDto
import org.jellyfin.sdk.model.api.MediaSegmentType
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** What Tower says about one episode's skip times */
data class TowerSkips(
    /** False while Tower is still checking the season (ask again a bit later) */
    val ready: Boolean,
    val segments: List<MediaSegmentDto>,
    /** Segments Tower isn't sure about: they get a Skip button but are never skipped by themselves */
    val unsure: Set<UUID>,
    /** Segments that come from someone's fix (these beat every other source) */
    val fixed: Set<UUID> = emptySet(),
    /** Kinds someone said this episode or show doesn't have (like no intro) */
    val none: Set<MediaSegmentType>,
    /** What was saved, after a fix */
    val saved: String? = null,
)

/** The fixes someone can make from the player's menu */
enum class SkipFix(
    val key: String,
) {
    INTRO_START("intro_start"),
    INTRO_END("intro_end"),
    CREDITS_START("credits_start"),
    CREDITS_END("credits_end"),
    NO_INTRO("no_intro"),
    NO_INTRO_SHOW("no_intro_show"),
    UNDO("undo"),
}

/**
 * Benflix: skip intro / credits times worked out on Tower by the Benflix helper (benflix_skips.py).
 * Tower compares the sound of every episode in a season, reads chapter names, asks TheIntroDB and
 * looks at the server's own times, then says which time to use and whether it's sure. People can
 * fix a wrong time from the player, and Tower uses that fix for the rest of the show.
 *
 * Returns null when the helper can't be reached (then Benflix uses the server's times as before).
 */
@Singleton
class BenflixSkipsService
    @Inject
    constructor(
        private val helper: GameLibraryClient,
    ) {
        suspend fun skipsFor(itemId: UUID): TowerSkips? =
            try {
                parse(itemId, helper.getJson("/api/skips/${itemId.toString().replace("-", "")}"))
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.i("Tower skip times not available: %s", ex.message)
                null
            }

        /** Saves a fix at [atSeconds] into the video. Throws with a friendly message when it can't. */
        suspend fun fix(
            itemId: UUID,
            fix: SkipFix,
            atSeconds: Double,
        ): TowerSkips? =
            parse(
                itemId,
                helper.postJson(
                    "/api/skips/${itemId.toString().replace("-", "")}/fix",
                    JSONObject().put("kind", fix.key).put("at", atSeconds),
                ),
            )

        companion object {
            fun typeOf(name: String): MediaSegmentType? =
                when (name.lowercase()) {
                    "intro" -> MediaSegmentType.INTRO
                    "outro" -> MediaSegmentType.OUTRO
                    "recap" -> MediaSegmentType.RECAP
                    "preview" -> MediaSegmentType.PREVIEW
                    "commercial" -> MediaSegmentType.COMMERCIAL
                    else -> null
                }

            fun parse(
                itemId: UUID,
                json: JSONObject,
            ): TowerSkips {
                val segments = mutableListOf<MediaSegmentDto>()
                val unsure = mutableSetOf<UUID>()
                val fixed = mutableSetOf<UUID>()
                val list = json.optJSONArray("segments")
                for (i in 0 until (list?.length() ?: 0)) {
                    val s = list!!.getJSONObject(i)
                    val type = typeOf(s.optString("type")) ?: continue
                    val start = s.optDouble("start", -1.0)
                    val end = s.optDouble("end", -1.0)
                    if (start < 0 || end <= start) continue
                    // One id per kind, so a refreshed time doesn't skip again after it was skipped once
                    val id = UUID.nameUUIDFromBytes("benflix-tower-$itemId-$type".toByteArray())
                    segments.add(
                        MediaSegmentDto(
                            id = id,
                            itemId = itemId,
                            type = type,
                            startTicks = (start * 10_000_000L).toLong(),
                            endTicks = (end * 10_000_000L).toLong(),
                        ),
                    )
                    if (!s.optBoolean("sure", false)) unsure.add(id)
                    if (s.optString("source") in setOf("you", "fix")) fixed.add(id)
                }
                val none = mutableSetOf<MediaSegmentType>()
                val noneList = json.optJSONArray("none")
                for (i in 0 until (noneList?.length() ?: 0)) {
                    typeOf(noneList!!.optString(i))?.let { none.add(it) }
                }
                return TowerSkips(
                    ready = json.optBoolean("ready", true),
                    segments = segments,
                    unsure = unsure,
                    fixed = fixed,
                    none = none,
                    saved = json.optString("saved").takeIf { it.isNotBlank() },
                )
            }

            /**
             * Puts Tower's times together with AniSkip's (hand-checked anime times). Fixes people made
             * win over everything; AniSkip wins over Tower's other times for the same kind. Kinds
             * someone said aren't there are dropped.
             */
            fun merge(
                tower: TowerSkips,
                aniSkip: List<MediaSegmentDto>,
            ): List<MediaSegmentDto> {
                val fixedTypes =
                    tower.segments
                        .filter { it.id in tower.fixed }
                        .map { it.type }
                        .toSet()
                val fromAniSkip = aniSkip.filter { it.type !in fixedTypes && it.type !in tower.none }
                val aniTypes = fromAniSkip.map { it.type }.toSet()
                return tower.segments.filter { it.type !in aniTypes } + fromAniSkip
            }
        }
    }
