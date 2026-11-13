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
)

/**
 * The frame every Requests tab uses. [wall] draws whatever the pick is (a list's posters, Your requests,
 * Waiting for you or Genres). [waitingCount] null = this person doesn't approve (only Ben does).
 */
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
    if (isTv) {
        Row(Modifier.fillMaxSize()) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 32.dp),
                modifier = Modifier.width(240.dp).fillMaxHeight(),
            ) {
                item(key = "search") { SideEntry(searchLabel, selected = false, onClick = onSearch) }
                item(key = "side-yours-h") { SideHeading("Yours") }
                item(key = "side-yours") {
                    SideEntry("Your requests", selected = picked == PICK_YOURS, count = yoursCount, onPick = { onPick(PICK_YOURS) })
                }
                if (waitingCount != null) {
                    item(key = "side-waiting") {
                        SideEntry(
                            "Waiting for you",
                            selected = picked == PICK_WAITING,
                            count = waitingCount,
                            countColor = if (waitingCount > 0) WaitingAmber else null,
                            onPick = { onPick(PICK_WAITING) },
                        )
                    }
                }
                item(key = "side-lists-h") { SideHeading("Lists") }
                if (loading && lists.isEmpty()) {
                    item(key = "side-loading") { SideEntry("Loading…", selected = false, onPick = {}) }
                }
                items(lists, key = { "side-${it.key}" }) { l ->
                    SideEntry(l.title, selected = picked == l.key, onPick = { onPick(l.key) })
                }
                extraSide()
            }
            wall(Modifier.weight(1f).fillMaxHeight())
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            phoneTop()
            if (!phoneWall) {
                phoneAlt(Modifier.fillMaxWidth().weight(1f))
                return@Column
            }
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            ) {
                item(key = "chip-mine") { RequestsChip("Your requests $yoursCount", picked == PICK_YOURS, { onPick(PICK_YOURS) }) }
                if (waitingCount != null) {
                    item(key = "chip-waiting") {
                        RequestsChip(
                            if (waitingCount > 0) "Waiting for you $waitingCount  ●" else "Waiting for you 0",
                            picked == PICK_WAITING,
                            { onPick(PICK_WAITING) },
                        )
                    }
                }
                items(lists, key = { "chip-${it.key}" }) { l -> RequestsChip(l.title, picked == l.key, { onPick(l.key) }) }
                extraChips()
            }
            wall(Modifier.fillMaxWidth().weight(1f))
        }
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
                open -> "Hide the ones already on Benflix"
                count == 1 -> "1 more is already on Benflix ›"
                else -> "$count more are already on Benflix ›"
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
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            line.onYes?.let { MusicButton("Approve", BenflixIcon.CHECK, onClick = it, primary = true) }
            line.onNo?.let { MusicButton("Decline", BenflixIcon.CLOSE, onClick = it) }
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
            state == "manga" -> "manga"
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
    )
}
