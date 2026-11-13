package com.github.damontecres.wholphin.ui.main

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.services.GuestMode
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.components.BenflixIconImage
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixDoor
import com.github.damontecres.wholphin.ui.theme.BenflixType
import java.util.UUID

/**
 * Redesign 2, section 11 (Ben's marks, 2026-11-10): the smaller Homes.
 * - Kids (kd-family): three big coloured doors (Movies, Shows, Games), each naming a couple of titles from this
 *   profile, then one big Keep watching row with names under the pictures. Big kids looks the same.
 * - Easy mode (ez-family): the normal Home made 25% bigger, with the four doors this person opens most (the rest
 *   under More) and one Keep watching row.
 * - Guest (gs-family): four doors (Movies, Shows, Live TV, Games), then "Popular here this week". Nothing saved,
 *   no Keep watching, news, requests or admin.
 */
enum class FamilyKind { KIDS, EASY, GUEST }

/** Which smaller Home this profile gets, or null for the grown-up Today board */
fun familyKindNow(easy: Boolean): FamilyKind? =
    when {
        KidsMode.isActive -> FamilyKind.KIDS
        GuestMode.isActive -> FamilyKind.GUEST
        easy -> FamilyKind.EASY
        else -> null
    }

/** One picture in the Keep watching (or Popular) row: the name always shows under it */
data class FamilyCard(
    val id: String,
    val title: String,
    val image: String?,
    val destination: Destination,
)

/** What the smaller Homes show under the doors, for one profile ([key] says whose) */
data class FamilyHomeData(
    val key: String = "",
    val cards: List<FamilyCard> = emptyList(),
    val lines: Map<BenflixDoor, String> = emptyMap(),
    val loaded: Boolean = false,
)

/** The doors each smaller Home shows, and (Easy mode only) the ones under More */
data class FamilyDoors(
    val shown: List<BenflixDoor>,
    val more: List<BenflixDoor>,
)

/**
 * Counts how often each door is opened on this device, per profile, so Easy mode can show the four used most.
 * Kept on the device only (it's just a tidy order, nothing private).
 */
object DoorUse {
    private const val PREFS = "benflix_door_use"

    private fun key(
        userId: UUID?,
        door: BenflixDoor,
    ) = "${userId ?: "none"}-${door.name}"

    fun opened(
        context: Context,
        userId: UUID?,
        door: BenflixDoor,
    ) {
        if (GuestMode.isActive) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putInt(key(userId, door), prefs.getInt(key(userId, door), 0) + 1).apply()
    }

    fun count(
        context: Context,
        userId: UUID?,
        door: BenflixDoor,
    ): Int = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(key(userId, door), 0)
}

/** The doors for [kind]. Easy mode picks the four this person opens most (a tie keeps the usual order). */
fun familyDoors(
    kind: FamilyKind,
    context: Context,
    userId: UUID?,
): FamilyDoors =
    when (kind) {
        FamilyKind.KIDS -> {
            FamilyDoors(listOf(BenflixDoor.MOVIES, BenflixDoor.SHOWS, BenflixDoor.GAMES), emptyList())
        }

        FamilyKind.GUEST -> {
            FamilyDoors(listOf(BenflixDoor.MOVIES, BenflixDoor.SHOWS, BenflixDoor.LIVE_TV, BenflixDoor.GAMES), emptyList())
        }

        FamilyKind.EASY -> {
            val ranked = BenflixDoor.entries.sortedByDescending { DoorUse.count(context, userId, it) }
            // Movies always stays, so the remote always starts in the same place
            val top = (listOf(BenflixDoor.MOVIES) + ranked).distinct().take(EASY_DOORS)
            FamilyDoors(
                shown = BenflixDoor.entries.filter { it in top },
                more = BenflixDoor.entries.filter { it !in top },
            )
        }
    }

private const val EASY_DOORS = 4

/** Easy mode makes this Home 25% bigger */
private const val EASY_SCALE = 1.25f

private fun doorIcon(door: BenflixDoor): BenflixIcon =
    when (door) {
        BenflixDoor.MOVIES -> BenflixIcon.FILM
        BenflixDoor.SHOWS -> BenflixIcon.TV
        BenflixDoor.LIVE_TV -> BenflixIcon.LIVE
        BenflixDoor.GAMES -> BenflixIcon.PAD
        BenflixDoor.MUSIC -> BenflixIcon.NOTE
        BenflixDoor.BOOKS -> BenflixIcon.BOOK
    }

@Composable
fun FamilyHome(
    kind: FamilyKind,
    greeting: String,
    doors: FamilyDoors,
    data: FamilyHomeData,
    wide: Boolean,
    labelAlpha: (Int) -> Float,
    listAlpha: () -> Float,
    doorModifier: (BenflixDoor) -> Modifier,
    onDoor: (BenflixDoor) -> Unit,
    onCard: (FamilyCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale = if (kind == FamilyKind.EASY) EASY_SCALE else 1f
    var showMore by remember { mutableStateOf(false) }
    val rowTitle = if (kind == FamilyKind.GUEST) "Popular here this week" else "Keep watching"
    val empty =
        when (kind) {
            FamilyKind.GUEST -> "Open a door to find something to watch."
            FamilyKind.KIDS -> "Nothing started yet. Open a door and pick something fun!"
            FamilyKind.EASY -> "Nothing started yet. Open a door to find something to watch."
        }
    val header: @Composable () -> Unit = {
        Column {
            Text(
                text = greeting,
                color = BenflixColors.Text,
                fontSize = BenflixType.pageTitle * scale,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (kind == FamilyKind.GUEST) {
                Text(
                    text = "Nothing you watch is saved",
                    color = BenflixColors.Muted,
                    fontSize = BenflixType.small,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
    val row: @Composable (posterHeight: Dp) -> Unit = { posterHeight ->
        Column(Modifier.graphicsLayer { alpha = listAlpha() }) {
            Text(
                text = rowTitle,
                color = BenflixColors.Text,
                fontSize = BenflixType.rowName * scale,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            if (data.loaded && data.cards.isEmpty()) {
                Text(text = empty, color = BenflixColors.Soft, fontSize = BenflixType.body * scale)
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(end = 24.dp),
                ) {
                    items(data.cards, key = { it.id }) { card ->
                        FamilyPoster(card = card, height = posterHeight, scale = scale, onClick = { onCard(card) })
                    }
                }
            }
        }
    }

    if (wide) {
        Column(
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier =
                modifier
                    .fillMaxSize()
                    .padding(end = 24.dp, top = 8.dp, bottom = 20.dp),
        ) {
            header()
            Row(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
            ) {
                doors.shown.forEach { door ->
                    FamilyDoor(
                        door = door,
                        line = data.lines[door] ?: if (kind == FamilyKind.KIDS) null else door.description,
                        scale = scale,
                        labelAlpha = labelAlpha(door.ordinal),
                        onClick = { onDoor(door) },
                        modifier = doorModifier(door).weight(1f).fillMaxHeight(),
                    )
                }
                if (doors.more.isNotEmpty()) {
                    MoreDoor(
                        count = doors.more.size,
                        scale = scale,
                        onClick = { showMore = true },
                        modifier = Modifier.weight(0.5f).fillMaxHeight().graphicsLayer { alpha = listAlpha() },
                    )
                }
            }
            row(if (kind == FamilyKind.EASY) 150.dp else 140.dp)
        }
    } else {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier =
                modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(end = 8.dp, top = 8.dp, bottom = 24.dp),
        ) {
            header()
            // Phones: Kids gets its three doors side by side; four doors go two by two
            val perRow = if (doors.shown.size == 3) 3 else 2
            doors.shown.chunked(perRow).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    pair.forEach { door ->
                        FamilyDoor(
                            door = door,
                            line = if (perRow == 3) null else data.lines[door] ?: if (kind == FamilyKind.KIDS) null else door.description,
                            scale = scale,
                            labelAlpha = labelAlpha(door.ordinal),
                            onClick = { onDoor(door) },
                            small = true,
                            modifier =
                                doorModifier(door)
                                    .weight(1f)
                                    .aspectRatio(if (perRow == 3) 0.85f else 1.35f / scale),
                        )
                    }
                }
            }
            if (doors.more.isNotEmpty()) {
                MoreDoor(
                    count = doors.more.size,
                    scale = scale,
                    onClick = { showMore = true },
                    modifier = Modifier.fillMaxWidth().height(56.dp * scale).graphicsLayer { alpha = listAlpha() },
                )
            }
            row(if (kind == FamilyKind.EASY) 160.dp else 150.dp)
        }
    }

    if (showMore) {
        BasicDialog(onDismissRequest = { showMore = false }) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(8.dp)) {
                Text(
                    text = "More doors",
                    color = BenflixColors.Text,
                    fontSize = BenflixType.rowName * scale,
                    fontWeight = FontWeight.Bold,
                )
                doors.more.forEach { door ->
                    FamilyDoor(
                        door = door,
                        line = door.description,
                        scale = scale,
                        labelAlpha = 1f,
                        onClick = {
                            showMore = false
                            onDoor(door)
                        },
                        small = true,
                        modifier = Modifier.fillMaxWidth().height(96.dp * scale),
                    )
                }
            }
        }
    }
}

/** One big coloured door: icon at the top, name and a short line at the bottom. Focus = white outline only. */
@Composable
private fun FamilyDoor(
    door: BenflixDoor,
    line: String?,
    scale: Float,
    labelAlpha: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    small: Boolean = false,
) {
    val shape = RoundedCornerShape(if (small) 18.dp else 14.dp)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = door.color,
                contentColor = door.onColor,
                focusedContainerColor = door.color,
                focusedContentColor = door.onColor,
                pressedContainerColor = door.color,
                pressedContentColor = door.onColor,
            ),
        border =
            ClickableSurfaceDefaults.border(
                focusedBorder = Border(border = BorderStroke(4.dp, BenflixColors.Text), inset = 0.dp, shape = shape),
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = labelAlpha }
                .padding(if (small) 14.dp else 22.dp),
        ) {
            BenflixIconImage(
                icon = doorIcon(door),
                color = door.onColor,
                size = (if (small) 32.dp else 44.dp) * scale,
                modifier = Modifier.align(Alignment.TopStart),
            )
            Column(Modifier.align(Alignment.BottomStart)) {
                Text(
                    text = door.title,
                    color = door.onColor,
                    fontSize = (if (small) 22.sp else 34.sp) * scale,
                    lineHeight = (if (small) 24.sp else 36.sp) * scale,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                )
                if (!line.isNullOrBlank()) {
                    Text(
                        text = line,
                        color = door.onColor.copy(alpha = .85f),
                        fontSize = (if (small) 12.sp else 15.sp) * scale,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

/** Easy mode's "More" tile: the doors not shown on Home */
@Composable
private fun MoreDoor(
    count: Int,
    scale: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = BenflixColors.PanelHigh,
                contentColor = BenflixColors.Text,
                focusedContainerColor = BenflixColors.FocusRow,
                focusedContentColor = BenflixColors.Text,
            ),
        border =
            ClickableSurfaceDefaults.border(
                focusedBorder = Border(border = BorderStroke(4.dp, BenflixColors.Text), inset = 0.dp, shape = shape),
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier,
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().padding(10.dp),
        ) {
            Text(
                text = "More",
                fontSize = 24.sp * scale,
                fontWeight = FontWeight.ExtraBold,
                color = BenflixColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (count == 1) "1 more door" else "$count more doors",
                fontSize = BenflixType.small,
                color = BenflixColors.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A poster with its name underneath (2 lines at most). Focus = white outline on the picture. */
@Composable
private fun FamilyPoster(
    card: FamilyCard,
    height: Dp,
    scale: Float,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    val width = height * 2f / 3f
    Column(Modifier.width(width)) {
        Surface(
            onClick = onClick,
            shape = ClickableSurfaceDefaults.shape(shape),
            colors =
                ClickableSurfaceDefaults.colors(
                    containerColor = BenflixColors.PanelHigh,
                    focusedContainerColor = BenflixColors.PanelHigh,
                ),
            border =
                ClickableSurfaceDefaults.border(
                    focusedBorder = Border(border = BorderStroke(3.dp, BenflixColors.Text), inset = 0.dp, shape = shape),
                ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            modifier = Modifier.width(width).height(height),
        ) {
            Box(Modifier.fillMaxSize()) {
                // No picture: the dark card with the name on it, like every other row
                Text(
                    text = card.title,
                    color = BenflixColors.Text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.Center).padding(8.dp),
                )
                if (card.image != null) {
                    AsyncImage(
                        model = card.image,
                        contentDescription = card.title,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .clip(shape)
                                .background(BenflixColors.PanelHigh),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = card.title,
            color = BenflixColors.Soft,
            fontSize = BenflixType.small * scale,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = BenflixType.small * scale * 1.2f,
        )
    }
}

/** The Kids / Big kids / Guest badge in the top bar */
@Composable
fun ProfileBadge(
    scale: Float = 1f,
    showGuestLine: Boolean = false,
) {
    val kids by KidsMode.active.collectAsState()
    val guest by GuestMode.active.collectAsState()
    if (!kids && !guest) return
    val (text, bg, fg) =
        if (kids) {
            Triple(KidsMode.displayName, KIDS_BADGE, Color(0xFF0B0C10))
        } else {
            Triple("Guest", BenflixColors.Line, BenflixColors.Soft)
        }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = text,
            color = fg,
            fontSize = 14.sp * scale,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .background(bg, RoundedCornerShape(50))
                    .padding(horizontal = 12.dp * scale, vertical = 4.dp * scale),
        )
        if (guest && showGuestLine) {
            Text(
                text = "Nothing you watch is saved",
                color = BenflixColors.Muted,
                fontSize = 13.sp * scale,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The Kids badge colour (warm amber, as on the sheet) */
private val KIDS_BADGE = Color(0xFFFFB547)
