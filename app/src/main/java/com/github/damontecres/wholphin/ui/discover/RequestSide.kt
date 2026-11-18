package com.github.damontecres.wholphin.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.services.BenflixCantFind
import com.github.damontecres.wholphin.services.CantFindEntry
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.music.MusicButton
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType

/*
 * Redesign 2, Requests (approved 2026-11-10, layout "Side list on every tab"): every tab (Movies, Shows, Anime,
 * Music, Books) is a short list on the left and a wall of posters on the right. The list is: Search, then YOURS
 * (Your requests with its count, Waiting for you with an amber number, Ben only), then LISTS. Moving down the
 * list changes the wall straight away. Phones: a second line of pills, Your requests first, then the lists.
 */

/** Picks in the side list that aren't one of the tab's lists */
internal const val PICK_YOURS = "__yours"
internal const val PICK_WAITING = "__waiting"

/** Amber, for the Waiting for you number */
internal val WaitingAmber = Color(0xFFF2B544)

/** One list in the side list (its [key] is what's saved as the pick) */
internal data class SideList(
    val key: String,
    val title: String,
    /** Lists next to each other with the same heading sit under it on TV, like TRENDING > Pop, Rock */
    val heading: String? = null,
    /** The shorter name used under its heading on TV ([title] on phones, which have no headings) */
    val short: String? = null,
)

/**
 * The frame the Music and Books request tabs use. Glowfin R1 (Ben 2026-11-18): the lists are chips across
 * the top (TV, phone and browser alike), and [wall] below draws the one you picked. OK or a tap picks;
 * moving along the chips only moves, so nothing loads as you pass. Your requests and Waiting for you live on
 * the page's top line and the Your requests tab now, so [yoursCount], [waitingCount], [searchLabel] and
 * [extraSide] are only kept for the callers. Search on the top line runs [onSearch].
 */
@Suppress("UNUSED_PARAMETER")
@Composable
internal fun RequestSideFrame(
    isTv: Boolean,
    lists: List<SideList>,
    picked: String,
    onPick: (String) -> Unit,
    yoursCount: Int,
    waitingCount: Int?,
    onSearch: () -> Unit,
    loading: Boolean,
    wall: @Composable (Modifier) -> Unit,
    phoneTop: @Composable () -> Unit = {},
    searchLabel: String = "Search to ask for anything",
    extraSide: LazyListScope.() -> Unit = {},
    extraChips: LazyListScope.() -> Unit = {},
    phoneWall: Boolean = true,
    phoneAlt: @Composable (Modifier) -> Unit = {},
) {
    OnRequestsSearch(onSearch)
    Column(Modifier.fillMaxSize()) {
        if (!isTv) {
            phoneTop()
            if (!phoneWall) {
                phoneAlt(Modifier.fillMaxWidth().weight(1f))
                return@Column
            }
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        ) {
            if (loading && lists.isEmpty()) item(key = "chip-loading") { RequestsChip("Loading…", false, {}) }
            items(lists, key = { "chip-${it.key}" }) { l -> RequestsChip(l.title, picked == l.key, { onPick(l.key) }) }
            extraChips()
        }
        wall(Modifier.fillMaxWidth().weight(1f))
    }
}

/** One line in the TV side list. [onPick] runs when you land on it (the wall follows); [onClick] when you press OK. */
@Composable
internal fun SideEntry(
    label: String,
    selected: Boolean,
    onPick: (() -> Unit)? = null,
    dot: Boolean = false,
    onClick: (() -> Unit)? = null,
    count: Int? = null,
    countColor: Color? = null,
) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        onClick = { (onClick ?: onPick)?.invoke() },
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = if (selected) BenflixColors.Raised else Color.Transparent,
                contentColor = if (selected) BenflixColors.Text else BenflixColors.Soft,
                focusedContainerColor = com.github.damontecres.wholphin.ui.theme.BenflixColors.FocusFill,
                focusedContentColor = com.github.damontecres.wholphin.ui.theme.BenflixColors.OnFocusFill,
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier =
            Modifier
                .fillMaxWidth()
                .onFocusChanged { if (it.isFocused) onPick?.invoke() },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = label,
                fontSize = BenflixType.body,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (count != null) {
                Text(
                    text = count.toString(),
                    fontSize = BenflixType.small,
                    fontWeight = FontWeight.Bold,
                    color = countColor ?: androidx.tv.material3.LocalContentColor.current,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            if (dot) {
                Box(
                    Modifier
                        .padding(start = 8.dp)
                        .size(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(BenflixColors.Warning),
                )
            }
        }
    }
}

/** A setting in the TV side list: its name, with On or Off in words */
@Composable
internal fun SideSwitch(
    label: String,
    on: Boolean,
    onToggle: () -> Unit,
) {
    SideEntry(label = "$label: ${if (on) "On" else "Off"}", selected = false, onClick = onToggle)
}

@Composable
internal fun SideHeading(text: String) {
    Text(
        text = text.uppercase(),
        fontSize = BenflixType.tag,
        fontWeight = FontWeight.Bold,
        color = BenflixColors.Muted,
        modifier = Modifier.padding(start = 12.dp, top = 14.dp, bottom = 2.dp),
    )
}

/** The end of a list: "4 more are already on Benflix ›" (owned titles never show in the lists themselves) */
@Composable
internal fun OwnedMoreButton(
    count: Int,
    open: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (count <= 0) return
    RequestsChip(
        label =
            when {
                open -> "Hide the ones already on Glowfin"
                count == 1 -> "1 more is already on Glowfin ›"
                else -> "$count more are already on Glowfin ›"
            },
        selected = false,
        onClick = onClick,
        modifier = modifier,
    )
}

/**
 * One request in Your requests or Waiting for you, for any kind (movie, show, song, album, book). The pill
 * says where it's at; Waiting for you has Approve and Decline right on the line.
 */
internal data class AskLine(
    val key: String,
    val title: String,
    /** Who asked and what kind, like "Mum · Season 4" or "Ben · album" */
    val sub: String,
    val cover: String?,
    val pill: AskPill,
    val square: Boolean = false,
    val onOpen: (() -> Unit)? = null,
    val onYes: (() -> Unit)? = null,
    val onNo: (() -> Unit)? = null,
    val onRetry: (() -> Unit)? = null,
    val onRemove: (() -> Unit)? = null,
    /** Why it couldn't be found, in plain words (music asks), shown under the pill */
    val why: String = "",
    /** Who asked (Ben's lists): their sea friend shows beside the Yes / No */
    val who: String? = null,
)

/** Your requests (grouped by the five pills, Ready first) or Waiting for you (flat, with Approve / Decline) */
@Composable
internal fun AskLinesWall(
    lines: List<AskLine>,
    grouped: Boolean,
    empty: String,
    modifier: Modifier = Modifier,
    note: String? = null,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 48.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        note?.let { item(key = "note") { Text(it, fontSize = BenflixType.small, color = BenflixColors.Muted) } }
        if (lines.isEmpty()) {
            item(key = "empty") { Text(empty, fontSize = BenflixType.lead, color = BenflixColors.Soft) }
            return@LazyColumn
        }
        if (!grouped) {
            items(lines, key = { "l-${it.key}" }) { AskLineRow(it) }
            return@LazyColumn
        }
        AskState.entries.forEach { state ->
            val group = lines.filter { it.pill.state == state }
            if (group.isEmpty()) return@forEach
            item(key = "g-${state.name}") {
                Text(
                    text = "${state.group} (${group.size})",
                    fontSize = BenflixType.rowName,
                    fontWeight = FontWeight.Bold,
                    color = BenflixColors.Text,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(group, key = { "l-${state.name}-${it.key}" }) { AskLineRow(it) }
        }
    }
}

@Composable
internal fun AskLineRow(line: AskLine) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(BenflixColors.Panel)
                .padding(10.dp),
    ) {
        Box(
            Modifier
                .size(width = 48.dp, height = if (line.square) 48.dp else 72.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(BenflixColors.Raised),
        ) {
            AsyncImage(model = line.cover, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
            if (line.onYes != null && !line.who.isNullOrBlank()) {
                // Glowfin R1 Waiting for you: who asked, with their sea friend
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WhoAskedFace(line.who, 26.dp)
                    Text(
                        text = "${line.who} asked",
                        fontSize = BenflixType.small,
                        fontWeight = FontWeight.SemiBold,
                        color = com.github.damontecres.wholphin.ui.theme.Glowfin.Aqua,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                text = line.title,
                fontSize = BenflixType.body,
                fontWeight = FontWeight.Bold,
                color = BenflixColors.Text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (line.sub.isNotBlank()) {
                Text(line.sub, fontSize = BenflixType.small, color = BenflixColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            AskPillChip(line.pill)
            if (line.why.isNotBlank()) {
                Text(line.why, fontSize = BenflixType.small, color = BenflixColors.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            line.onYes?.let { MusicButton("Yes", BenflixIcon.CHECK, onClick = it, primary = true) }
            line.onNo?.let { MusicButton("No", BenflixIcon.CLOSE, onClick = it) }
            line.onRetry?.let { MusicButton("Try again", BenflixIcon.REFRESH, onClick = it) }
            if (line.onYes == null) line.onOpen?.let { MusicButton("Open", null, onClick = it) }
            line.onRemove?.let { MusicButton(null, BenflixIcon.TRASH, onClick = it) }
        }
    }
}

/** A movie or show request as a line (Ben sees who asked) */
internal fun RequestEntry.toAskLine(
    showWho: Boolean,
    cantFind: CantFindEntry?,
    onOpen: (() -> Unit)?,
    onAnswer: ((Boolean) -> Unit)? = null,
): AskLine {
    val seasons = request.seasons.orEmpty().mapNotNull { it.seasonNumber }
    val kind =
        when {
            item?.type == com.github.damontecres.wholphin.data.model.SeerrItemType.MOVIE -> "Movie"
            seasons.isEmpty() -> "Show"
            seasons.size == 1 -> "Season ${seasons.first()}"
            else -> "${seasons.size} seasons"
        }
    val pending = onAnswer != null
    return AskLine(
        key = "seerr-${request.id}",
        title = item?.title ?: "Unknown title",
        sub = listOfNotNull(if (showWho) who else null, kind).joinToString(" · "),
        cover = item?.posterUrl,
        pill = askPill(cantFind, BenflixCantFind.daysSince(request.updatedAt ?: request.createdAt)),
        onOpen = onOpen,
        onYes = if (pending) ({ onAnswer?.invoke(true) }) else null,
        onNo = if (pending) ({ onAnswer?.invoke(false) }) else null,
        who = who.takeIf { showWho },
    )
}

/** A music request as a line. [onAnswer] set = it's waiting for Ben's Approve. */
internal fun com.github.damontecres.wholphin.services.MusicRequest.toAskLine(
    showWho: Boolean,
    onAnswer: ((Boolean) -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
): AskLine =
    AskLine(
        key = "music-$id",
        title = title,
        sub =
            listOfNotNull(
                who.takeIf { showWho && it.isNotBlank() },
                artist.ifBlank { null },
                if (kind == "album") "album" else "song",
            ).joinToString(" · "),
        cover = cover,
        pill = askPill(),
        square = true,
        onYes = onAnswer?.let { { it(true) } },
        onNo = onAnswer?.let { { it(false) } },
        onRetry = if (canRetry) onRetry else null,
        onRemove = onRemove,
        why = why,
        who = who.takeIf { showWho && it.isNotBlank() },
    )

/** A book request as a line. [onAnswer] set = it's waiting for Ben's yes. */
internal fun com.github.damontecres.wholphin.services.BookRequest.toAskLine(
    showWho: Boolean,
    onOpen: (() -> Unit)?,
    onAnswer: ((Boolean) -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
): AskLine {
    val kinds =
        when {
            types.containsAll(listOf("ebook", "audiobook")) -> "ebook and audiobook"
            types.contains("audiobook") -> "audiobook"
            else -> "ebook"
        }
    return AskLine(
        key = "book-$id",
        title = name,
        sub = listOfNotNull(who.takeIf { showWho && it.isNotBlank() }, author.ifBlank { null }, kinds).joinToString(" · "),
        cover = cover,
        pill = bookPill(state, words),
        onOpen = onOpen,
        onYes = onAnswer?.let { { it(true) } },
        onNo = onAnswer?.let { { it(false) } },
        onRetry = if (canRetry) onRetry else null,
        who = who.takeIf { showWho && it.isNotBlank() },
    )
}
