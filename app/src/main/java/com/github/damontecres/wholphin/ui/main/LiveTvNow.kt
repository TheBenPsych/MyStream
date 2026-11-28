package com.github.damontecres.wholphin.ui.main

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.services.ChannelProgram
import com.github.damontecres.wholphin.services.ChannelSchedule
import com.github.damontecres.wholphin.services.group
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.detail.title.TitleButton
import com.github.damontecres.wholphin.ui.detail.title.TitleButtonView
import com.github.damontecres.wholphin.ui.door.DoorTag
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixShapes
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.theme.Glowfin
import com.github.damontecres.wholphin.ui.theme.GlowfinDisplayFont
import kotlinx.coroutines.delay
import org.jellyfin.sdk.model.api.ImageType

/*
 * Glowfin new look, part 4 (Ben approved 2026-11-22): Live TV opens on what's on now, big. The show fills the
 * screen with the channel badge, a red LIVE tag and when it ends, a solid white Watch live button whose line
 * shows how far through the show it is, then Full guide and Favourite. The guide peeks at the bottom; Down (or
 * Full guide) opens it full screen, and the top shrinks to one line about the show you're on.
 * Ben's note: pick big events and sports on now first.
 * Made for the TCL TVs: one picture at the screen's size, plain painted shades, no blur.
 */

/** A program on now, and the channel it's on */
internal typealias LivePick = Pair<ChannelSchedule, ChannelProgram>

/** Words in a show's name that mean a game or match is on */
private val SPORT_WORDS =
    listOf(
        " vs ",
        " vs. ",
        " v ",
        " at ",
        "football",
        "basketball",
        "baseball",
        "hockey",
        "soccer",
        "nfl",
        "nba",
        "mlb",
        "nhl",
        "mls",
        "ncaa",
        "ufc",
        "boxing",
        "wrestling",
        "golf",
        "tennis",
        "racing",
        "nascar",
        "grand prix",
        "formula 1",
        "f1 ",
        "match",
        "game ",
        "playoff",
        "bowl",
        "cup",
        "olympic",
    )

/** Words in a show's name that mean a big one-off event */
private val EVENT_WORDS =
    listOf(
        "live",
        "premiere",
        "finale",
        "special",
        "awards",
        "parade",
        "election",
        "debate",
        "breaking",
        "concert",
        "championship",
        "final",
        "countdown",
        "ceremony",
    )

/**
 * The show Live TV opens on (Ben's note: big events and sports on now first). Each channel's show on now gets
 * points: sports and games most, then big events and premieres, then a little for favourites and channels you
 * watched lately. Ties keep the guide's own order. Channels with no guide only win when nothing else is on.
 */
internal fun pickLiveNow(
    list: List<ChannelSchedule>,
    favourites: Collection<String>,
    recent: List<String>,
    now: Long,
): LivePick? {
    var best: LivePick? = null
    var bestScore = Int.MIN_VALUE
    list.forEach { schedule ->
        val program = schedule.programAt(now) ?: return@forEach
        val score = liveScore(schedule, program, favourites, recent)
        if (score > bestScore) {
            best = schedule to program
            bestScore = score
        }
    }
    return best
}

private fun liveScore(
    schedule: ChannelSchedule,
    program: ChannelProgram,
    favourites: Collection<String>,
    recent: List<String>,
): Int {
    if (program.noGuide) return -100 + (if (schedule.key in favourites) 5 else 0)
    val data = program.item.data
    val name = " " + program.name().lowercase() + " "
    var score = 0
    val sports = data.isSports == true || schedule.group() == "Sports" || SPORT_WORDS.any { name.contains(it) }
    if (sports) score += 40
    if (data.isLive == true) score += 25
    if (data.isPremiere == true) score += 15
    if (EVENT_WORDS.any { name.contains(it) }) score += 12
    if (schedule.key in favourites) score += 6
    val recentAt = recent.indexOf(schedule.key)
    if (recentAt >= 0) score += (4 - recentAt).coerceAtLeast(1)
    // A show that's nearly over is a poor first pick: it'll be gone in a minute
    if (program.endMs - System.currentTimeMillis() < 5 * 60_000L) score -= 20
    return score
}

/** "Sports" or "News" etc. for the facts line, from the show itself or the channel's type */
private fun programKind(
    schedule: ChannelSchedule,
    program: ChannelProgram,
): String? {
    val data = program.item.data
    return when {
        data.isSports == true -> "Sports"
        data.isNews == true -> "News"
        data.isMovie == true -> "Movie"
        data.isKids == true -> "Kids"
        else -> schedule.group().takeIf { it != "Live TV" }
    }
}

/**
 * The picture behind the whole Live TV page: the show on now (or the one the remote is on), at the screen's own
 * size, with a soft shade on the left for the words and a fade at the bottom for the guide. In the full guide
 * ([deep]) it's turned well down so the guide reads clearly. A new picture waits a moment, so moving quickly
 * through the guide doesn't load a picture for every show.
 */
@Composable
internal fun LiveArt(
    pick: () -> LivePick?,
    deep: () -> Boolean,
    pageHeight: Dp,
) {
    val imageUrlService = LocalImageUrlService.current
    val density = LocalDensity.current
    val pair = pick()
    val heightPx = with(density) { pageHeight.roundToPx() }
    // The show's own picture by its id, else the channel's logo (drawn small on the right)
    val urls =
        remember(pair?.second?.item?.id, pair?.first?.live?.id, heightPx) {
            val (schedule, program) = pair ?: return@remember emptyList()
            listOfNotNull(
                if (program.noGuide) {
                    null
                } else {
                    imageUrlService.getItemImageUrl(itemId = program.item.id, imageType = ImageType.PRIMARY, fillHeight = heightPx)
                },
                imageUrlService.getItemImageUrl(itemId = schedule.live.id, imageType = ImageType.PRIMARY, maxWidth = 300),
            )
        }
    var attempt by remember(urls) { mutableIntStateOf(0) }
    val wanted = urls.getOrNull(attempt)
    val wantedIsLogo = urls.isNotEmpty() && attempt == urls.lastIndex
    var shown by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    LaunchedEffect(wanted) {
        if (wanted == shown?.first) return@LaunchedEffect
        delay(ART_WAIT_MS)
        shown = wanted?.let { it to wantedIsLogo }
    }
    Box(Modifier.fillMaxSize()) {
        Crossfade(targetState = shown, animationSpec = tween(ART_FADE_MS), label = "liveArt") { picture ->
            if (picture != null) {
                val (url, isLogo) = picture
                if (isLogo) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize().padding(start = 0.dp),
                    ) {
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier =
                                Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 80.dp)
                                    .fillMaxWidth(.28f)
                                    .fillMaxHeight(.36f),
                        )
                    }
                } else {
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter,
                        onError = { if (attempt < urls.lastIndex) attempt++ },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        if (deep()) {
            Box(Modifier.fillMaxSize().background(BenflixColors.Tray.copy(alpha = .72f)))
        } else {
            Box(Modifier.fillMaxSize().background(LIVE_LEFT_SHADE))
            Box(Modifier.fillMaxSize().background(LIVE_BOTTOM_SHADE))
        }
    }
}

/**
 * The top of Live TV (new look): the channel badge, the show's name, the facts line, the red LIVE tag with when
 * it ends, two lines of story, and Watch live (solid white, its line shows how far through the show it is),
 * Full guide and Favourite. Down from the buttons opens the full guide.
 */
@Composable
internal fun LiveNowHero(
    pick: LivePick,
    now: Long,
    starred: Boolean,
    height: Dp,
    onWatch: () -> Unit,
    onGuide: () -> Unit,
    onFavourite: () -> Unit,
    onFocus: () -> Unit,
    watchFocus: FocusRequester,
) {
    val (schedule, program) = pick
    val progress =
        if (program.noGuide || program.endMs <= program.startMs) {
            null
        } else {
            ((now - program.startMs).toFloat() / (program.endMs - program.startMs)).coerceIn(0f, 1f)
        }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom),
        modifier =
            Modifier
                .fillMaxWidth()
                .height(height)
                .padding(bottom = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ChannelLogo(schedule, size = 30.dp, wide = true)
            Text(
                text = "${schedule.number}  ${schedule.title}",
                fontFamily = GlowfinDisplayFont,
                fontSize = BenflixType.body,
                fontWeight = FontWeight.Bold,
                color = Glowfin.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = program.name(),
            fontFamily = GlowfinDisplayFont,
            fontWeight = FontWeight.ExtraBold,
            fontSize = BenflixType.pageTitle,
            lineHeight = 33.sp,
            color = Glowfin.Text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 520.dp),
        )
        val facts =
            remember(program, schedule) {
                listOfNotNull(
                    "Now",
                    if (program.noGuide) null else "${clock(program.startMs)} to ${clock(program.endMs)}",
                    programKind(schedule, program),
                    program.item.data.parentIndexNumber
                        ?.let { s ->
                            program.item.data.indexNumber
                                ?.let { e -> "S$s E$e" }
                        },
                ).joinToString("  ·  ")
            }
        Text(
            text = facts,
            fontSize = BenflixType.body,
            fontWeight = FontWeight.SemiBold,
            color = Glowfin.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LiveRedTag()
            if (!program.noGuide) DoorTag("Ends ${clock(program.endMs)}")
        }
        program.item.data.overview?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                fontSize = BenflixType.body,
                color = BenflixColors.Soft,
                lineHeight = 21.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 470.dp),
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier =
                Modifier
                    .padding(top = 4.dp)
                    .onPreviewKeyEvent { event ->
                        // Down from the buttons: the full guide
                        if (event.key == Key.DirectionDown && event.type == KeyEventType.KeyDown) {
                            onGuide()
                            true
                        } else {
                            false
                        }
                    },
        ) {
            TitleButtonView(
                button = TitleButton("Watch live", BenflixIcon.PLAY, onWatch, progress = progress),
                primary = true,
                big = false,
                onFocus = { if (it) onFocus() },
                modifier = Modifier.focusRequester(watchFocus),
            )
            TitleButtonView(
                button = TitleButton("Full guide", BenflixIcon.CALENDAR, onGuide),
                primary = false,
                big = false,
                onFocus = { if (it) onFocus() },
            )
            TitleButtonView(
                button = TitleButton(if (starred) "Favourite" else "Add to favourites", BenflixIcon.STAR, onFavourite),
                primary = false,
                big = false,
                onFocus = { if (it) onFocus() },
            )
        }
    }
}

/**
 * The full guide's top (new look): one short line about the show the remote is on, so the guide gets the
 * screen. The channel badge, the show's name, its times, and LIVE when it's on now.
 */
@Composable
internal fun LiveMiniLine(
    pick: () -> LivePick?,
    now: Long,
) {
    Box(Modifier.fillMaxWidth().height(MINI_LINE_HEIGHT), contentAlignment = Alignment.CenterStart) {
        val (schedule, program) = pick() ?: return@Box
        val live = now >= program.startMs && now < program.endMs
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ChannelLogo(schedule, size = 26.dp, wide = true)
            Text(
                text = program.name(),
                fontFamily = GlowfinDisplayFont,
                fontSize = BenflixType.lead,
                fontWeight = FontWeight.Bold,
                color = Glowfin.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 380.dp),
            )
            val times =
                listOfNotNull(
                    "${schedule.number} ${schedule.title}",
                    if (program.noGuide) null else "${clock(program.startMs)} to ${clock(program.endMs)}",
                ).joinToString("  ·  ")
            Text(
                text = times,
                fontSize = BenflixType.small,
                fontWeight = FontWeight.SemiBold,
                color = BenflixColors.Soft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (live) LiveRedTag()
        }
    }
}

/** The red LIVE tag with a white dot (new look) */
@Composable
internal fun LiveRedTag(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier =
            modifier
                .background(LIVE_RED, BenflixShapes.Pill)
                .padding(horizontal = 9.dp, vertical = 2.dp),
    ) {
        Box(Modifier.width(6.dp).height(6.dp).background(Color.White, BenflixShapes.Pill))
        Text(
            text = "LIVE",
            fontSize = BenflixType.tag,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
        )
    }
}

/** How tall the full guide's one-line top is */
internal val MINI_LINE_HEIGHT = 40.dp

private val LIVE_RED = Color(0xFFE5484D)
private const val ART_WAIT_MS = 350L
private const val ART_FADE_MS = 260

private val LIVE_LEFT_SHADE
    get() =
        Brush.horizontalGradient(
            0f to BenflixColors.Tray.copy(alpha = .88f),
            .38f to BenflixColors.Tray.copy(alpha = .55f),
            .66f to Color.Transparent,
        )

private val LIVE_BOTTOM_SHADE
    get() =
        Brush.verticalGradient(
            .45f to Color.Transparent,
            .72f to BenflixColors.Tray.copy(alpha = .75f),
            1f to BenflixColors.Tray.copy(alpha = .96f),
        )
