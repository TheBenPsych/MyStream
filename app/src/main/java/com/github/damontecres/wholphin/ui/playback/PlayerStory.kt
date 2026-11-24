package com.github.damontecres.wholphin.ui.playback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.detail.title.TitleDetailsPanel
import com.github.damontecres.wholphin.ui.detail.title.TitleOverview
import com.github.damontecres.wholphin.ui.detail.title.TitleTab
import com.github.damontecres.wholphin.ui.detail.title.titleDetails
import org.jellyfin.sdk.model.api.BaseItemKind

/**
 * Glowfin new look "Up in the player shows Story and details" (Ben 2026-11-22): press Up while watching and the
 * same right-hand Story and details panel as the details page slides in: the story of what's playing (the
 * episode's own story for a show), then studio, dates, genres and so on. The video keeps playing; Back closes it.
 */
@Composable
internal fun PlayerStoryPanel(
    item: BaseItem,
    admin: Boolean,
    onDismiss: () -> Unit,
) {
    val d = item.data
    val episode = item.type == BaseItemKind.EPISODE
    val title =
        remember(item) {
            if (episode) {
                val where =
                    listOfNotNull(
                        d.parentIndexNumber?.let { "S$it" },
                        d.indexNumber?.let { "E$it" },
                    ).joinToString(":")
                listOfNotNull(d.seriesName, where.ifBlank { null }, d.name).joinToString(" · ")
            } else {
                d.name.orEmpty()
            }
        }
    val details = remember(item, admin) { titleDetails(item, admin, show = episode) }
    TitleDetailsPanel(
        title = title,
        story = d.overview,
        details =
            if (details.isEmpty()) {
                emptyList()
            } else {
                listOf(
                    TitleTab("About", info = true) { scope ->
                        TitleOverview(scope = scope, title = title, story = null, strip = emptyList(), details = details)
                    },
                )
            },
        onDismiss = onDismiss,
    )
}
