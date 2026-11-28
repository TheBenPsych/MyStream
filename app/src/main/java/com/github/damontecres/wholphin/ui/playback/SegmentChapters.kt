package com.github.damontecres.wholphin.ui.playback

import com.github.damontecres.wholphin.data.model.Chapter
import org.jellyfin.sdk.model.api.MediaSegmentDto
import org.jellyfin.sdk.model.api.MediaSegmentType
import org.jellyfin.sdk.model.extensions.ticks
import java.util.UUID

/** Names [SegmentChapters] gives, so its own chapters can be replaced when Tower's times change */
val SEGMENT_CHAPTER_NAMES = setOf("Episode", "Recap", "Intro", "Credits", "Preview")

/**
 * Benflix (Idea Box 30, "Chapter gaps on every episode"): lots of episodes come without chapters, so the
 * simple player's seek bar is one long line. When a video has no chapters of its own, the skip times
 * Tower already found (Recap, Intro, Credits, Preview) are turned into chapters, so the bar gets its gaps
 * and the white pill can say where you are. Files with their own chapters always keep those.
 */
object SegmentChapters {
    /** Two chapter starts closer than this (10 s) count as one */
    private const val CLOSE_TICKS = 100_000_000L

    private fun nameOf(type: MediaSegmentType): String? =
        when (type) {
            MediaSegmentType.RECAP -> "Recap"
            MediaSegmentType.INTRO -> "Intro"
            MediaSegmentType.OUTRO -> "Credits"
            MediaSegmentType.PREVIEW -> "Preview"
            else -> null
        }

    /** Chapters made from [segments], or an empty list when they wouldn't make at least 2 */
    fun from(
        itemId: UUID,
        segments: List<MediaSegmentDto>,
        lengthTicks: Long,
        storyName: String = "Episode",
    ): List<Chapter> {
        if (lengthTicks <= 0) return emptyList()
        val starts = mutableListOf<Pair<Long, String>>()
        starts += 0L to storyName
        segments
            .filter { nameOf(it.type) != null && it.endTicks > it.startTicks }
            .sortedBy { it.startTicks }
            .forEach { seg ->
                val name = nameOf(seg.type) ?: return@forEach
                starts += seg.startTicks.coerceAtLeast(0L) to name
                if (seg.endTicks < lengthTicks - CLOSE_TICKS) starts += seg.endTicks to storyName
            }
        // Later starts win when two land on almost the same spot (an intro that starts at 0:02 replaces "Episode" at 0)
        val tidy = mutableListOf<Pair<Long, String>>()
        for (s in starts.sortedBy { it.first }) {
            if (tidy.isNotEmpty() && s.first - tidy.last().first < CLOSE_TICKS) {
                tidy[tidy.lastIndex] = tidy.last().first to s.second
            } else {
                tidy += s
            }
        }
        // Two story pieces in a row are one piece
        val merged = tidy.filterIndexed { i, s -> i == 0 || tidy[i - 1].second != s.second }
        if (merged.size < 2) return emptyList()
        return merged.mapIndexed { i, (pos, name) ->
            Chapter(itemId = itemId, name = name, position = pos.ticks, tag = null, index = i)
        }
    }
}
