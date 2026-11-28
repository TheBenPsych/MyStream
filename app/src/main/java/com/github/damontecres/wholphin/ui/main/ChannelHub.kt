package com.github.damontecres.wholphin.ui.main

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.github.damontecres.wholphin.services.ChannelPrefs
import com.github.damontecres.wholphin.services.ChannelSchedule
import com.github.damontecres.wholphin.services.FAVOURITES_GROUP
import com.github.damontecres.wholphin.services.group
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixShapes
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.theme.Glowfin
import com.github.damontecres.wholphin.ui.theme.GlowfinDisplayFont
import com.github.damontecres.wholphin.ui.theme.glowFocus
import com.github.damontecres.wholphin.ui.touch.Dialog
import com.github.damontecres.wholphin.ui.tryRequestFocus
import kotlinx.coroutines.delay
import org.jellyfin.sdk.model.api.ImageType

/** How a channel type is named on its tile and in the side guide */
internal fun groupLabel(group: String): String =
    when (group) {
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

private val Muted get() = Glowfin.Dim

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
        focusedBorder = Border(BorderStroke(2.5.dp, BenflixColors.Focus), shape = BenflixShapes.Card),
    )

/**
 * A live channel's logo on a plate, sized so it reads from the sofa. Most stations have dark or
 * coloured logos, so they sit on white. Some (Bloomberg, CBS...) send a white logo with a see-through
 * background, which vanished on white, so those get a dark slate plate instead. The check looks at
 * the small logo picture once and remembers the answer.
 */
@Composable
internal fun ChannelLogo(
    schedule: ChannelSchedule,
    size: Dp,
    /** Glowfin (L1): logos sit on a 3:2 plate; the old square stays for small places */
    wide: Boolean = false,
) {
    val live = schedule.live
    val imageUrlService = LocalImageUrlService.current
    val logo = remember(live.id) { imageUrlService.getItemImageUrl(itemId = live.id, imageType = ImageType.PRIMARY, maxWidth = 300) }
    LogoPlate(
        url = logo,
        modifier =
            Modifier
                .height(size)
                .width(if (wide) size * 1.5f else size),
        corner = 6.dp,
        inset = if (size >= 36.dp) 4.dp else 2.dp,
    )
}

/** The plate itself: white for dark/coloured logos, dark slate for white ones */
@Composable
internal fun LogoPlate(
    url: String?,
    modifier: Modifier = Modifier,
    corner: Dp = 6.dp,
    inset: Dp = 3.dp,
) {
    val context = LocalContext.current
    var light by remember(url) { mutableStateOf(url?.let { LogoTone.known[it] } ?: false) }
    val request =
        remember(url) {
            // Software pictures so the colours can be read; logos are tiny so this costs nothing
            ImageRequest
                .Builder(context)
                .data(url)
                .allowHardware(false)
                .build()
        }
    Box(
        modifier =
            modifier
                .background(if (light) LogoTone.DarkPlate else Color.White, RoundedCornerShape(corner))
                .clip(RoundedCornerShape(corner))
                .padding(inset),
    ) {
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            onSuccess = { state ->
                if (url != null && !LogoTone.known.containsKey(url)) {
                    val isLight = LogoTone.isLightLogo(state.result.image.toBitmap())
                    LogoTone.known[url] = isLight
                    light = isLight
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

internal object LogoTone {
    /** A soft slate, lighter than the cards, so white logos stand out and coloured ones still show */
    val DarkPlate = Color(0xFF3A4064)
    val known = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    /**
     * True when the logo is mostly white/very light marks on a see-through background. Logos that
     * fill their own box (no see-through parts) already carry their background, so they stay on white.
     */
    fun isLightLogo(bitmap: Bitmap): Boolean =
        try {
            val w = bitmap.width
            val h = bitmap.height
            if (w <= 0 || h <= 0) {
                false
            } else {
                val step = maxOf(1, maxOf(w, h) / 48)
                var total = 0
                var solid = 0
                var lightCount = 0
                var y = 0
                while (y < h) {
                    var x = 0
                    while (x < w) {
                        val c = bitmap.getPixel(x, y)
                        total++
                        if (android.graphics.Color.alpha(c) > 140) {
                            solid++
                            val r = android.graphics.Color.red(c)
                            val g = android.graphics.Color.green(c)
                            val b = android.graphics.Color.blue(c)
                            if (r * 299 + g * 587 + b * 114 > 205_000) lightCount++
                        }
                        x += step
                    }
                    y += step
                }
                solid > 0 && solid < total * .92f && lightCount > solid * .55f
            }
        } catch (_: Exception) {
            false
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
        shape = ClickableSurfaceDefaults.shape(BenflixShapes.Small),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = if (playing) Glowfin.Raise else Color.Transparent,
                contentColor = Glowfin.Text,
                focusedContainerColor = Glowfin.FocusRow,
                focusedContentColor = Glowfin.Text,
            ),
        border =
            com.github.damontecres.wholphin.ui.nav
                .focusRing(BenflixShapes.Small),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier.fillMaxWidth().height(52.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
        ) {
            ChannelLogo(schedule, size = 36.dp, wide = true)
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

/** Glowfin (L1): favourite stars are aqua, like the sheet */
internal val StarGold get() = Glowfin.Aqua

/**
 * Glowfin (L1): a Recently watched tile. The logo on a raised 16:9 card (glow ring when the remote is
 * on it, nothing grows), a thin glow line for how far the show on now has got, and the number and
 * what's on underneath.
 */
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
    val shape = BenflixShapes.Card
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.width(width)) {
        Surface(
            onClick = onClick,
            onLongClick = onLongClick,
            shape = ClickableSurfaceDefaults.shape(shape),
            colors = tileColors(Glowfin.Raise),
            border = tileBorder(),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            modifier =
                modifier
                    .glowFocus(shape, spread = 10.dp)
                    .width(width)
                    .aspectRatio(16f / 9f),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ChannelLogo(schedule, size = width * .38f, wide = true)
                if (on != null && !on.noGuide && on.endMs > on.startMs) {
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth(((now - on.startMs).toFloat() / (on.endMs - on.startMs)).coerceIn(0f, 1f))
                            .height(3.dp)
                            .background(Glowfin.GlowAcross),
                    )
                }
            }
        }
        Text(
            text = "${schedule.number}  ·  " + (on?.name() ?: schedule.title),
            fontSize = BenflixType.tag,
            fontWeight = FontWeight.SemiBold,
            color = Glowfin.Dim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
                    .background(Glowfin.Deep, BenflixShapes.Panel)
                    .padding(18.dp),
        ) {
            Text(
                "Channel types",
                fontFamily = GlowfinDisplayFont,
                fontWeight = FontWeight.Bold,
                fontSize = BenflixType.pageTitle,
                color = Glowfin.Text,
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
                        shape = ClickableSurfaceDefaults.shape(BenflixShapes.Small),
                        colors =
                            ClickableSurfaceDefaults.colors(
                                containerColor = Color.Transparent,
                                contentColor = Glowfin.Text,
                                focusedContainerColor = Glowfin.FocusRow,
                                focusedContentColor = Glowfin.Text,
                            ),
                        border =
                            com.github.damontecres.wholphin.ui.nav
                                .focusRing(BenflixShapes.Small),
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
                                color = if (shown) Glowfin.Aqua else Muted,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Glowfin (L1): a round chip on Live TV (the guide's types, Edit types, Add channels, the side guide's
 * types). The picked one is filled with the glow; the remote gets the glow ring, and nothing grows.
 */
@Composable
internal fun LiveChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(50)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = if (selected) Glowfin.Ink else Glowfin.Text.copy(alpha = .88f),
                focusedContainerColor = Color.Transparent,
                focusedContentColor = if (selected) Glowfin.Ink else Glowfin.Text,
                pressedContainerColor = Color.Transparent,
            ),
        border =
            com.github.damontecres.wholphin.ui.nav
                .focusRing(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier.glowFocus(shape, spread = 8.dp).height(34.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .fillMaxHeight()
                    // New look (Part 4): see-through pills like the page tabs; the picked one is near-white
                    .background(if (selected) Color.White.copy(alpha = .92f) else Glowfin.Ink.copy(alpha = .35f), shape)
                    .border(1.dp, Color.White.copy(alpha = if (selected) 0f else .14f), shape)
                    .padding(horizontal = 14.dp),
        ) {
            Text(
                text = label,
                fontSize = BenflixType.tag,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
