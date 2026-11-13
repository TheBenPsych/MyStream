package com.github.damontecres.wholphin.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.type
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
import com.github.damontecres.wholphin.services.BENFLIX_GROUP
import com.github.damontecres.wholphin.services.ChannelPrefs
import com.github.damontecres.wholphin.services.ChannelSchedule
import com.github.damontecres.wholphin.services.FAVOURITES_GROUP
import com.github.damontecres.wholphin.services.group
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixFont
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.touch.Dialog
import com.github.damontecres.wholphin.ui.tryRequestFocus
import kotlinx.coroutines.delay
import org.jellyfin.sdk.model.api.ImageType

/** How a channel type is named on its tile and in the side guide */
internal fun groupLabel(group: String): String =
    when (group) {
        BENFLIX_GROUP -> "Benflix · No ads"
        FAVOURITES_GROUP -> "★ Favourites"
        else -> group
    }

/** Channels matching what was typed: a channel number, or part of a channel's name */
internal fun searchChannels(
    list: List<ChannelSchedule>,
    query: String,
): List<ChannelSchedule> {
    val q = query.trim()
    if (q.isEmpty()) return emptyList()
    q.toIntOrNull()?.let { n ->
        return list.filter { it.number == n } + list.filter { it.number != n && it.number.toString().startsWith(q) }
    }
    return list.filter { it.title.contains(q, ignoreCase = true) }
}

/** The number on a remote's number button, or null for any other button */
internal fun digitOf(event: KeyEvent): Int? {
    val code = event.key.nativeKeyCode
    return when (code) {
        in android.view.KeyEvent.KEYCODE_0..android.view.KeyEvent.KEYCODE_9 -> code - android.view.KeyEvent.KEYCODE_0
        in android.view.KeyEvent.KEYCODE_NUMPAD_0..android.view.KeyEvent.KEYCODE_NUMPAD_9 -> code - android.view.KeyEvent.KEYCODE_NUMPAD_0
        else -> null
    }
}

private val Muted = Color(0xFF8B8B94)

@Composable
private fun tileColors(container: Color) =
    ClickableSurfaceDefaults.colors(
        containerColor = container,
        contentColor = Color.White,
        focusedContainerColor = container,
        focusedContentColor = Color.White,
    )

@Composable
private fun tileBorder() =
    ClickableSurfaceDefaults.border(
        focusedBorder = Border(BorderStroke(3.dp, BenflixColors.Focus), shape = RoundedCornerShape(10.dp)),
    )

/** A channel's logo on white (live channels), or its coloured letter badge (Benflix channels) */
@Composable
internal fun ChannelLogo(
    schedule: ChannelSchedule,
    size: Dp,
) {
    val live = schedule.live
    if (live != null) {
        val imageUrlService = LocalImageUrlService.current
        val logo = remember(live.id) { imageUrlService.getItemImageUrl(itemId = live.id, imageType = ImageType.PRIMARY, maxWidth = 80) }
        Box(
            modifier =
                Modifier
                    .size(size)
                    .background(Color.White, RoundedCornerShape(6.dp))
                    .clip(RoundedCornerShape(6.dp))
                    .padding(2.dp),
        ) {
            AsyncImage(
                model =
                    com.github.damontecres.wholphin.ui.cards
                        .rememberFadeIn(logo),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    } else {
        val (from, to) = BadgeColors[Math.floorMod(schedule.channel?.ordinal ?: schedule.custom?.id.hashCode(), BadgeColors.size)]
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(size).background(Brush.linearGradient(listOf(from, to)), RoundedCornerShape(6.dp)),
        ) {
            Text(
                text = schedule.title.take(1).uppercase(),
                fontFamily = BenflixFont,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * .62f).sp,
                color = Color.White,
            )
        }
    }
}

/** One line for a channel: logo, number and name, and what's on now. Used by search and the side guide */
@Composable
internal fun ChannelRow(
    schedule: ChannelSchedule,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    playing: Boolean = false,
) {
    val now = System.currentTimeMillis()
    val on = schedule.programAt(now)
    val favourites by ChannelPrefs.favourites.collectAsState()
    Surface(
        onClick = onClick,
        onLongClick = { ChannelPrefs.toggleFavourite(schedule.key) },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = if (playing) Color(0xFF2A2A32) else Color(0xFF1C1C21),
                contentColor = Color.White,
                focusedContainerColor = com.github.damontecres.wholphin.ui.theme.BenflixColors.FocusFill,
                focusedContentColor = com.github.damontecres.wholphin.ui.theme.BenflixColors.OnFocusFill,
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier.fillMaxWidth().height(52.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
        ) {
            ChannelLogo(schedule, size = 30.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    text = "${schedule.number}  ${schedule.title}",
                    fontSize = BenflixType.small,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (playing) "Playing now" else on?.name() ?: "",
                    fontSize = BenflixType.tag,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
            if (schedule.key in favourites) Text("★", fontSize = BenflixType.small, color = StarGold)
        }
    }
}

internal val StarGold = Color(0xFFE0A54A)

/** A Recently watched card: logo, number and name, and what's on now with how far in it is */
@Composable
internal fun RecentCard(
    schedule: ChannelSchedule,
    width: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    val now = System.currentTimeMillis()
    val on = schedule.programAt(now)
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = tileColors(Color(0xFF1C1C21)),
        border = tileBorder(),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier.width(width).height(64.dp),
    ) {
        Box(Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
            ) {
                ChannelLogo(schedule, size = 30.dp)
                Column {
                    Text(
                        text = "${schedule.number}  ${schedule.title}",
                        fontSize = BenflixType.tag,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = on?.name().orEmpty(),
                        fontSize = BenflixType.tag,
                        color = Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (on != null && !on.noGuide && on.endMs > on.startMs) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(((now - on.startMs).toFloat() / (on.endMs - on.startMs)).coerceIn(0f, 1f))
                        .height(3.dp)
                        .background(com.github.damontecres.wholphin.ui.theme.BenflixColors.LiveTv),
                )
            }
        }
    }
}

/** Pick which channel types show on the Channels page and in the side guide (saved per profile) */
@Composable
internal fun HideTypesDialog(
    groups: List<String>,
    hidden: Set<String>,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier =
                Modifier
                    .widthIn(max = 420.dp)
                    .background(Color(0xFF17171B), RoundedCornerShape(10.dp))
                    .padding(18.dp),
        ) {
            Text(
                "Channel types",
                fontFamily = BenflixFont,
                fontWeight = FontWeight.Bold,
                fontSize = BenflixType.pageTitle,
                color = Color.White,
            )
            Text("Turn off the types you never watch.", fontSize = BenflixType.small, color = Muted)
            val first = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                delay(80)
                first.tryRequestFocus()
            }
            androidx.compose.foundation.lazy.LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.height(360.dp),
            ) {
                items(groups, key = { it }) { group ->
                    val shown = group !in hidden
                    Surface(
                        onClick = { ChannelPrefs.setHidden(group, shown) },
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
                        colors =
                            ClickableSurfaceDefaults.colors(
                                containerColor = Color(0xFF222228),
                                contentColor = Color.White,
                                focusedContainerColor = com.github.damontecres.wholphin.ui.theme.BenflixColors.FocusFill,
                                focusedContentColor = com.github.damontecres.wholphin.ui.theme.BenflixColors.OnFocusFill,
                            ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .then(if (group == groups.first()) Modifier.focusRequester(first) else Modifier),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            Text(
                                groupLabel(group),
                                fontSize = BenflixType.small,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = if (shown) "Shown" else "Hidden",
                                fontSize = BenflixType.tag,
                                fontWeight = FontWeight.Bold,
                                color = if (shown) Color(0xFF3FBF7A) else Muted,
                            )
                        }
                    }
                }
            }
        }
    }
}
