package com.github.damontecres.wholphin.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.components.BenflixIconImage
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixShapes
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.theme.Glowfin
import com.github.damontecres.wholphin.ui.theme.GlowfinDisplayFont
import com.github.damontecres.wholphin.ui.theme.glowFocus
import com.github.damontecres.wholphin.ui.touch.rememberIsTv

/*
 * Server page redesign (Ben 2026-11-28, "drastically improve, it's overly dense"): the places are tabs along the
 * top, Overview answers "is everything OK?" in big words with five glance tiles, Tools is a row of tiles per group
 * (like Home) and each tool opens on its own page, and Health's cards sit in a grid. Every tool from before is
 * still here; only where it lives changed. Plain colours and outlines only (no blur), so it stays smooth on weak TVs.
 */

/**
 * One tool on the Server page. [group] is its row on Tools (null for Health's cards, which open from the Health
 * tab's grid). [card] is the Tower card it shows, when it is one.
 */
enum class ServerTool(
    val section: ServerSection,
    val group: ServerToolGroup?,
    val title: String,
    val line: String,
    val icon: BenflixIcon,
    val card: String? = null,
) {
    SCAN(ServerSection.TOOLS, ServerToolGroup.LIBRARY, "Scan for new stuff", "Look for new movies and shows now", BenflixIcon.REFRESH),
    CHECKUP(
        ServerSection.TOOLS,
        ServerToolGroup.LIBRARY,
        "Library checkup",
        "Missing episodes, weak copies and other gaps",
        BenflixIcon.CHECK,
    ),
    POSTERS(ServerSection.TOOLS, ServerToolGroup.LIBRARY, "Fix posters", "Missing, blurry or wrong-shaped posters", BenflixIcon.IMAGE),
    SPACE(ServerSection.TOOLS, ServerToolGroup.LIBRARY, "What takes space", "The biggest movies and shows", BenflixIcon.STATS),
    TIDY(ServerSection.TOOLS, ServerToolGroup.LIBRARY, "Tidy-up and play test", "Night-time play test and tidy-up list", BenflixIcon.MOON),
    ONE_PACE(
        ServerSection.TOOLS,
        ServerToolGroup.LIBRARY,
        "One Pace",
        "Sort One Pace into its seasons",
        BenflixIcon.FLAG,
        card = "onepace",
    ),
    SPECIALS(
        ServerSection.TOOLS,
        ServerToolGroup.LIBRARY,
        "Specials & bonus",
        "Specials and bonus videos for shows",
        BenflixIcon.STAR,
        card = "specialsbonus",
    ),
    ANIME_MOVIES(
        ServerSection.TOOLS,
        ServerToolGroup.LIBRARY,
        "Anime movies",
        "Anime movies into their shows",
        BenflixIcon.FILM,
        card = "animemovies",
    ),
    DOWNLOADS(ServerSection.TOOLS, ServerToolGroup.DOWNLOADS, "Downloads", "What's downloading, and stuck ones", BenflixIcon.DOWNLOAD),
    TRACKS(
        ServerSection.TOOLS,
        ServerToolGroup.FIXES,
        "Tidy track names",
        "Sound and subtitle names that read well",
        BenflixIcon.CHAPTERS,
    ),
    CONVERT(
        ServerSection.TOOLS,
        ServerToolGroup.FIXES,
        "Converting",
        "How Tower converts for your TVs",
        BenflixIcon.SWITCH,
        card = "convert",
    ),
    SPEED(ServerSection.TOOLS, ServerToolGroup.FIXES, "Speed check-up", "How fast Tower can send video", BenflixIcon.SPEED),
    STARTUP(ServerSection.TOOLS, ServerToolGroup.FIXES, "Startup speed", "How long this device takes to open", BenflixIcon.HOME),
    GAMES(ServerSection.TOOLS, ServerToolGroup.FIXES, "Games that didn't start", "Games that failed to open", BenflixIcon.PAD),
    VERSIONS(ServerSection.TOOLS, ServerToolGroup.PEOPLE, "Glowfin versions", "Who needs to update", BenflixIcon.PHONE),
    MESSAGES(ServerSection.TOOLS, ServerToolGroup.PEOPLE, "Pop-up messages", "Send a note to someone's screen", BenflixIcon.BELL),
    KIDS_RATINGS(ServerSection.TOOLS, ServerToolGroup.PEOPLE, "Kids ratings", "Kids titles with no rating", BenflixIcon.LOCK),
    KIDS_GAMES(ServerSection.TOOLS, ServerToolGroup.PEOPLE, "Kids games", "Which games Kids can play", BenflixIcon.PAD),
    SETUP(ServerSection.TOOLS, ServerToolGroup.TOWER, "Setup command", "Tower's setup and updates", BenflixIcon.SERVER),
    APPS(ServerSection.TOOLS, ServerToolGroup.TOWER, "Your apps", "Sonarr, Radarr and the rest", BenflixIcon.LIST),
    CHORES(
        ServerSection.TOOLS,
        ServerToolGroup.TOWER,
        "Chores and restart",
        "Jellyfin's chores, and restarting it",
        BenflixIcon.REFRESH,
    ),
    JOBS(ServerSection.TOOLS, ServerToolGroup.TOWER, "Tower's own jobs", "Glowfin's jobs on Tower", BenflixIcon.CALENDAR, card = "jobs"),
    DRIVES(ServerSection.TOOLS, ServerToolGroup.TOWER, "Drive health", "Warning signs on Tower's drives", BenflixIcon.SERVER),
    BACKUP(ServerSection.TOOLS, ServerToolGroup.TOWER, "Safety copy", "A copy of Glowfin's settings", BenflixIcon.LOCK, card = "backup"),
    AWAY(ServerSection.TOOLS, ServerToolGroup.TOWER, "Away check", "Can people reach Tower from outside", BenflixIcon.PHONE, card = "away"),
    ADDONS(ServerSection.TOOLS, ServerToolGroup.TOWER, "Jellyfin add-ons", "Add-ons and their updates", BenflixIcon.PLUS, card = "addons"),
    BOOKS(ServerSection.TOOLS, ServerToolGroup.TOWER, "Book lists", "Lists for the Books page", BenflixIcon.BOOK),
    JELLYFIN(
        ServerSection.TOOLS,
        ServerToolGroup.TOWER,
        "Rarely needed",
        "These stay in Jellyfin",
        BenflixIcon.GEAR,
        card = "jellyfin",
    ),

    // Health's six cards
    HEALTH_NOW(ServerSection.HEALTH, null, "Right now", "How Tower is doing", BenflixIcon.HEART, card = "health"),
    HEALTH_PARTS(ServerSection.HEALTH, null, "Parts", "Processor, cards, drives and fans", BenflixIcon.SERVER, card = "healthparts"),
    HEALTH_DAY(ServerSection.HEALTH, null, "Last 24 hours", "Heat and busyness through the day", BenflixIcon.STATS, card = "health24"),
    HEALTH_FIX(ServerSection.HEALTH, null, "Warn me and fix it", "What Tower fixed by itself", BenflixIcon.WRENCH, card = "healthfix"),
    HEALTH_CRASHES(ServerSection.HEALTH, null, "Crash notebook", "The black box before a crash", BenflixIcon.WARNING, card = "crashes"),
    HEALTH_DIARY(ServerSection.HEALTH, null, "Changes diary", "What changed on Tower", BenflixIcon.NOTE, card = "diary"),
    ;

    companion object {
        fun inGroup(group: ServerToolGroup): List<ServerTool> = entries.filter { it.group == group }

        val health: List<ServerTool> get() = entries.filter { it.section == ServerSection.HEALTH }

        /** The order of the rows on Tools (Playback before People, like the design) */
        val rows: List<ServerToolGroup> =
            listOf(ServerToolGroup.LIBRARY, ServerToolGroup.DOWNLOADS, ServerToolGroup.FIXES, ServerToolGroup.PEOPLE, ServerToolGroup.TOWER)
    }
}

/** A tile's short status: the words, and the dot's colour (null = no dot, just the tool's own line) */
internal data class ToolStatus(
    val words: String,
    val light: Light?,
)

/** The short status for a Tower card tool, from Tower's answer when it has come in */
internal fun TowerCard?.toolStatus(fallback: String): ToolStatus {
    val card = this
    return if (card == null || card.headline.isBlank()) {
        ToolStatus(fallback, null)
    } else {
        ToolStatus(card.headline, towerLight(card.level).takeIf { it != Light.UNKNOWN })
    }
}

/** Overview's top: the dot and the big line, why (when something's wrong), how fresh, and Refresh */
@Composable
internal fun ServerStatusLine(
    light: Light,
    reason: String?,
    status: String?,
    onRefresh: () -> Unit,
) {
    val isTv = rememberIsTv()
    val headline =
        when (light) {
            Light.GOOD -> "Everything is running"
            Light.WARN -> "Something needs a look"
            Light.BAD -> "Something isn't working"
            Light.UNKNOWN -> "Checking everything…"
        }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .size(if (isTv) 16.dp else 13.dp)
                .clip(CircleShape)
                .background(lightColor(light)),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = headline,
                fontSize = if (isTv) BenflixType.pageTitle else 22.sp,
                fontFamily = GlowfinDisplayFont,
                fontWeight = FontWeight.SemiBold,
                color = BenflixColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = listOfNotNull(reason?.takeIf { light != Light.GOOD }, status).joinToString(" · ")
            if (sub.isNotBlank()) {
                Text(
                    text = sub,
                    fontSize = BenflixType.small,
                    color = BenflixColors.Muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        SmallButton("Refresh", onClick = onRefresh)
    }
}

/** The other tabs' top: what the tab is for, how fresh, and Refresh (the tab already says its name) */
@Composable
internal fun ServerTabLine(
    text: String,
    status: String?,
    onRefresh: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = listOfNotNull(text, status).joinToString(" · "),
            fontSize = BenflixType.small,
            color = BenflixColors.Muted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        SmallButton("Refresh", onClick = onRefresh)
    }
}

/** One glance tile on Overview */
internal data class GlanceTile(
    val label: String,
    val icon: BenflixIcon,
    val value: String,
    val line: String,
    val valueLight: Light? = null,
    val lineLight: Light? = null,
    val onClick: () -> Unit,
)

/** Overview's five tiles: five across on TV and wide screens, two across on phones */
@Composable
internal fun ServerGlanceTiles(tiles: List<GlanceTile>) {
    val isTv = rememberIsTv()
    val wide = isTv || LocalConfiguration.current.screenWidthDp >= WIDE_DP
    if (wide) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
            tiles.forEach { GlanceTileCard(it, Modifier.weight(1f)) }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            tiles.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    pair.forEach { GlanceTileCard(it, Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun GlanceTileCard(
    tile: GlanceTile,
    modifier: Modifier,
) {
    val isTv = rememberIsTv()
    val shape = BenflixShapes.Panel
    Surface(
        onClick = tile.onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Glowfin.Raise,
                contentColor = Glowfin.Text,
                focusedContainerColor = BenflixColors.FocusFill,
                focusedContentColor = BenflixColors.OnFocusFill,
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier.glowFocus(shape, spread = 8.dp).heightIn(min = if (isTv) 116.dp else 96.dp),
    ) {
        val ink = LocalContentColor.current
        val onWhite = ink != Glowfin.Text
        Column(Modifier.padding(horizontal = if (isTv) 16.dp else 14.dp, vertical = if (isTv) 14.dp else 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BenflixIconImage(tile.icon, color = ink.copy(alpha = .7f), size = 18.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = tile.label,
                    fontSize = BenflixType.tag,
                    color = ink.copy(alpha = .7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = tile.value,
                fontSize = if (isTv) BenflixType.pageTitle else 24.sp,
                fontFamily = GlowfinDisplayFont,
                fontWeight = FontWeight.SemiBold,
                color = tile.valueLight?.let { lightOn(it, onWhite) } ?: ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(if (isTv) 8.dp else 4.dp))
            Text(
                text = tile.line,
                fontSize = BenflixType.tag,
                color = tile.lineLight?.let { lightOn(it, onWhite) } ?: ink.copy(alpha = .85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The traffic-light colours read badly on a white (lit) tile, so they get darker there */
private fun lightOn(
    light: Light,
    onWhite: Boolean,
) = if (!onWhite) {
    lightColor(light)
} else {
    when (light) {
        Light.GOOD -> BenflixColors.OkOnLight
        Light.WARN -> BenflixColors.CheckOnLight
        Light.BAD -> BenflixColors.ProblemOnLight
        Light.UNKNOWN -> BenflixColors.OnFocusFill
    }
}

/** A row heading on the redesigned page ("Needs you", "Library"): bigger and calmer than the old grey caps */
@Composable
internal fun ServerRowName(
    text: String,
    count: String? = null,
) {
    val isTv = rememberIsTv()
    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = if (isTv) 14.dp else 10.dp, bottom = 2.dp)) {
        Text(
            text = text,
            fontSize = if (isTv) BenflixType.rowName else 19.sp,
            fontFamily = GlowfinDisplayFont,
            fontWeight = FontWeight.SemiBold,
            color = BenflixColors.Text,
        )
        count?.let {
            Spacer(Modifier.width(12.dp))
            Text(text = it, fontSize = BenflixType.small, color = BenflixColors.Muted, modifier = Modifier.padding(bottom = 2.dp))
        }
    }
}

/** "Needs you": every to-do on its own roomy line, with its buttons on the right */
internal fun LazyListScope.needsYouItems(needs: List<NeedLine>) {
    item(key = "needs-title") {
        ServerRowName("Needs you", needs.size.takeIf { it > 0 }?.let { if (it == 1) "1 thing" else "$it things" })
    }
    if (needs.isEmpty()) {
        item(key = "needs-none") {
            NeedRow(NeedLine("none", "All good! Nothing needs you right now.", Light.GOOD))
        }
    }
    items(needs, key = { "need-" + it.key }) { NeedRow(it) }
}

@Composable
private fun NeedRow(need: NeedLine) {
    val isTv = rememberIsTv()
    val compact = !isTv && LocalConfiguration.current.screenWidthDp < WIDE_DP
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(BenflixShapes.Card)
                .background(com.github.damontecres.wholphin.ui.preferences.GlowfinGlass)
                .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (need.poster != null) {
                AsyncImage(
                    model = need.poster,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .width(34.dp)
                            .height(50.dp)
                            .clip(BenflixShapes.Small)
                            .background(BenflixColors.PanelHigh),
                )
            } else {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(lightColor(need.light)),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = need.text,
                    fontSize = BenflixType.body,
                    fontWeight = FontWeight.Medium,
                    color = BenflixColors.Text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                need.supporting?.let {
                    Text(
                        text = it,
                        fontSize = BenflixType.small,
                        color = BenflixColors.Muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (!compact) {
                need.actions.forEach { (label, action) ->
                    Spacer(Modifier.width(8.dp))
                    SmallButton(label, onClick = action)
                }
            }
        }
        // Phones: the buttons go under the words, so long names never get squeezed
        if (compact && need.actions.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                need.actions.forEach { (label, action) -> SmallButton(label, onClick = action) }
            }
        }
    }
}

/** Tools: one row of tiles per group, like Home's rows */
internal fun LazyListScope.toolRowItems(
    status: (ServerTool) -> ToolStatus,
    rowStates: (ServerToolGroup) -> LazyListState,
    tileFocus: (ServerTool) -> FocusRequester,
    onOpen: (ServerTool) -> Unit,
) {
    ServerTool.rows.forEach { group ->
        item(key = "row-title-${group.name}") { ServerRowName(if (group == ServerToolGroup.FIXES) "Playback" else group.title) }
        item(key = "row-${group.name}") {
            val isTv = rememberIsTv()
            LazyRow(
                state = rowStates(group),
                horizontalArrangement = Arrangement.spacedBy(if (isTv) 14.dp else 10.dp),
                // Room for the focus ring around the first and last tiles
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(ServerTool.inGroup(group), key = { it.name }) { tool ->
                    ToolTile(tool, status(tool), onClick = { onOpen(tool) }, modifier = Modifier.focusRequester(tileFocus(tool)))
                }
            }
        }
    }
}

@Composable
private fun ToolTile(
    tool: ServerTool,
    status: ToolStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp? = null,
) {
    val isTv = rememberIsTv()
    val shape = BenflixShapes.Card
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Glowfin.Raise,
                contentColor = Glowfin.Text,
                focusedContainerColor = BenflixColors.FocusFill,
                focusedContentColor = BenflixColors.OnFocusFill,
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier =
            modifier
                .glowFocus(shape, spread = 6.dp)
                .width(width ?: if (isTv) 196.dp else 156.dp)
                .height(if (isTv) 104.dp else 100.dp),
    ) {
        val ink = LocalContentColor.current
        val onWhite = ink != Glowfin.Text
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            BenflixIconImage(tool.icon, color = if (onWhite) Glowfin.Blue else Glowfin.Aqua, size = 22.dp)
            Spacer(Modifier.height(6.dp))
            Text(
                text = tool.title,
                fontSize = if (isTv) BenflixType.body else 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                status.light?.let {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(lightOn(it, onWhite)),
                    )
                    Spacer(Modifier.width(7.dp))
                }
                Text(
                    text = status.words,
                    fontSize = BenflixType.tag,
                    color = ink.copy(alpha = .8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Health: its six cards in a grid (three across on TV, two on phones), each opens on its own page */
@Composable
internal fun HealthCardGrid(
    status: (ServerTool) -> ToolStatus,
    tileFocus: (ServerTool) -> FocusRequester,
    onOpen: (ServerTool) -> Unit,
    viewModel: TowerCardsViewModel =
        androidx.hilt.lifecycle.viewmodel.compose
            .hiltViewModel(),
) {
    LaunchedEffect(Unit) { ServerTool.health.forEach { tool -> tool.card?.let { viewModel.load(it) } } }
    // Read so the grid redraws when Tower answers
    val cards by viewModel.cards.collectAsStateWithLifecycle()
    val isTv = rememberIsTv()
    val perRow = if (isTv || LocalConfiguration.current.screenWidthDp >= WIDE_DP) 3 else 2
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        ServerTool.health.chunked(perRow).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { tool ->
                    HealthCard(
                        tool,
                        status(tool),
                        tool.card?.let { cards[it] } == null,
                        { onOpen(tool) },
                        Modifier.weight(1f).focusRequester(tileFocus(tool)),
                    )
                }
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun HealthCard(
    tool: ServerTool,
    status: ToolStatus,
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val isTv = rememberIsTv()
    val shape = BenflixShapes.Panel
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Glowfin.Raise,
                contentColor = Glowfin.Text,
                focusedContainerColor = BenflixColors.FocusFill,
                focusedContentColor = BenflixColors.OnFocusFill,
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier.glowFocus(shape, spread = 8.dp).height(if (isTv) 150.dp else 140.dp),
    ) {
        val ink = LocalContentColor.current
        val onWhite = ink != Glowfin.Text
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Text(
                text = tool.title,
                fontSize = if (isTv) BenflixType.rowName else 17.sp,
                fontFamily = GlowfinDisplayFont,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val word =
                when {
                    loading -> {
                        "Asking Tower…"
                    }

                    else -> {
                        when (status.light) {
                            Light.GOOD -> "OK"
                            Light.WARN -> "Check"
                            Light.BAD -> "Problem"
                            else -> "No answer yet"
                        }
                    }
                }
            Text(
                text = word,
                fontSize = BenflixType.lead,
                fontWeight = FontWeight.SemiBold,
                color = status.light?.takeIf { !loading }?.let { lightOn(it, onWhite) } ?: ink.copy(alpha = .7f),
                maxLines = 1,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = status.words,
                fontSize = BenflixType.small,
                color = ink.copy(alpha = .8f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** The top of one open tool: where it lives, its name big, what it does, and the way back */
@Composable
internal fun ToolPageHeader(
    tool: ServerTool,
    onBack: () -> Unit,
) {
    val isTv = rememberIsTv()
    val where =
        when {
            tool.section == ServerSection.HEALTH -> "Health"
            tool.group == ServerToolGroup.FIXES -> "Tools · Playback"
            else -> "Tools · " + (tool.group?.title ?: "")
        }
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(text = where, fontSize = BenflixType.small, color = BenflixColors.Muted, maxLines = 1)
            Text(
                text = tool.title,
                fontSize = if (isTv) BenflixType.pageTitle else 24.sp,
                fontFamily = GlowfinDisplayFont,
                fontWeight = FontWeight.SemiBold,
                color = BenflixColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = tool.line,
                fontSize = BenflixType.small,
                color = BenflixColors.Muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        SmallButton(if (tool.section == ServerSection.HEALTH) "All of Health" else "All tools", onClick = onBack)
    }
}

private const val WIDE_DP = 720
