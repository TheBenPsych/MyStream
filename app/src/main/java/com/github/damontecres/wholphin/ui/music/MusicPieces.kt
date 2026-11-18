package com.github.damontecres.wholphin.ui.music

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.ImageUrlService
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.components.BenflixHeartFilled
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.components.BenflixIconImage
import com.github.damontecres.wholphin.ui.components.BenflixPlayFilled
import com.github.damontecres.wholphin.ui.door.doorFocusBorder
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixShapes
import com.github.damontecres.wholphin.ui.theme.BenflixType
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.extensions.ticks
import java.util.UUID
import kotlin.time.Duration

/** The square picture for an album, song (its album's cover), playlist or artist */
fun ImageUrlService.musicCover(
    item: BaseItem,
    px: Int,
): String? =
    if (item.type == BaseItemKind.AUDIO) {
        // The song's own picture when its album has none yet (Tower looks for missing album covers every hour)
        val ownOnly = item.data.albumPrimaryImageTag == null && item.data.imageTags?.get(ImageType.PRIMARY) != null
        item.data.albumId?.takeIf { !ownOnly }?.let { albumId ->
            getItemImageUrl(
                itemId = albumId,
                imageType = ImageType.PRIMARY,
                fillWidth = px,
                fillHeight = px,
                tag = item.data.albumPrimaryImageTag,
            )
        } ?: getItemImageUrl(item, ImageType.PRIMARY, fillWidth = px, fillHeight = px)
    } else {
        getItemImageUrl(item, ImageType.PRIMARY, fillWidth = px, fillHeight = px)
    }

/** An album cover by album id (for the made-for-you collages) */
fun ImageUrlService.albumCover(
    albumId: UUID,
    px: Int,
): String? = getItemImageUrl(itemId = albumId, imageType = ImageType.PRIMARY, fillWidth = px, fillHeight = px)

/** A square cover, or a dark square with a note when there's no picture */
@Composable
fun CoverImage(
    url: String?,
    modifier: Modifier = Modifier,
    shape: Shape = BenflixShapes.Card,
) {
    var failed by remember(url) { mutableStateOf(false) }
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .clip(shape)
                .background(BenflixColors.PanelHigh),
    ) {
        if (url != null && !failed) {
            AsyncImage(
                // Redesign 2: covers fade in over the dark square
                model =
                    com.github.damontecres.wholphin.ui.cards
                        .rememberFadeIn(url),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = { failed = true },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            BenflixIconImage(BenflixIcon.NOTE, color = BenflixColors.Muted, size = 28.dp)
        }
    }
}

/** The focus look for music cards: the white outline only, like the rest of the redesign */
@Composable
private fun musicSurfaceColors() =
    ClickableSurfaceDefaults.colors(
        containerColor = Color.Transparent,
        focusedContainerColor = Color.Transparent,
        pressedContainerColor = Color.Transparent,
    )

/**
 * One square card in a row: the cover, then its name and a small line under it (artist, "Playlist", song
 * count). Artists are round.
 */
@Composable
fun MusicCard(
    item: BaseItem,
    size: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    line: String? = musicCardLine(item),
    onFocus: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val imageUrlService = LocalImageUrlService.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val url = remember(item.id, px) { imageUrlService.musicCover(item, px) }
    val round = item.type == BaseItemKind.MUSIC_ARTIST
    val shape = if (round) CircleShape else BenflixShapes.Card
    // Redesign 2 (rc-q-hold): holding any card opens the same menu as a poster, unless the page has its own
    var timesHeld by remember(item.id) { androidx.compose.runtime.mutableIntStateOf(0) }
    if (onLongClick == null && timesHeld > 0) {
        com.github.damontecres.wholphin.ui.components
            .PosterHoldMenu(item, timesHeld)
    }
    MusicCardFrame(
        name = item.title.orEmpty(),
        line = line,
        size = size,
        shape = shape,
        centred = round,
        onClick = onClick,
        onFocus = onFocus,
        onLongClick = onLongClick ?: { timesHeld += 1 },
        modifier = modifier,
    ) {
        // Redesign 2 (rc-p-noart): no cover = the dark card with the big white name and a violet line
        com.github.damontecres.wholphin.ui.cards.CardPicture(
            model = url,
            name = item.title.orEmpty(),
            door = BenflixColors.Music,
            small = size < 140.dp,
            centred = round,
            modifier = Modifier.fillMaxSize().clip(shape),
        )
    }
}

fun musicCardLine(item: BaseItem): String? =
    when (item.type) {
        BaseItemKind.MUSIC_ALBUM -> item.data.albumArtist ?: item.data.artists?.firstOrNull()
        BaseItemKind.AUDIO -> item.data.artists?.joinToString(", ")
        BaseItemKind.PLAYLIST -> item.data.childCount?.let { songCount(it) } ?: "Playlist"
        else -> null
    }

fun songCount(n: Int) = if (n == 1) "1 song" else "$n songs"

/** The frame every music card shares: a focusable square, then the words under it */
@Composable
fun MusicCardFrame(
    name: String,
    line: String?,
    size: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = BenflixShapes.Card,
    centred: Boolean = false,
    onFocus: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    picture: @Composable () -> Unit,
) {
    Column(
        horizontalAlignment = if (centred) Alignment.CenterHorizontally else Alignment.Start,
        modifier = modifier.width(size),
    ) {
        Surface(
            onClick = onClick,
            onLongClick = onLongClick,
            shape = ClickableSurfaceDefaults.shape(shape),
            colors = musicSurfaceColors(),
            border = doorFocusBorder(shape),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            modifier =
                Modifier
                    .size(size)
                    .onFocusChanged { if (it.isFocused) onFocus?.invoke() },
        ) {
            picture()
        }
        // Redesign 2 (rc-p-phone): the name in up to 2 lines, then the artist or song count in grey
        com.github.damontecres.wholphin.ui.cards.CardNameUnder(
            name = name,
            line = line,
            centred = centred,
        )
    }
}

/**
 * A made-for-you cover (approved m2c-collage): four song covers in a square, with a violet strip along the
 * bottom holding the name in dark words.
 */
@Composable
fun MixCover(
    name: String,
    coverUrls: List<String?>,
    modifier: Modifier = Modifier,
    size: Dp,
) {
    // One cover fills the square, two or three repeat to make four; none (a holiday still being filled) gets
    // a colour of its own with the name, instead of a grey square
    val urls = coverUrls.filterNotNull().take(4)
    val four = if (urls.size in 2..3) (urls + urls).take(4) else urls
    val empty = urls.isEmpty()
    Box(
        modifier =
            modifier
                .clip(BenflixShapes.Card)
                .then(
                    if (empty) {
                        Modifier.background(Brush.linearGradient(mixTint(name)))
                    } else {
                        Modifier.background(BenflixColors.PanelHigh)
                    },
                ),
    ) {
        val half = size / 2
        if (empty) {
            Text(
                text = name,
                color = Color.White,
                fontSize = BenflixType.body,
                lineHeight = BenflixType.body,
                fontWeight = FontWeight.Black,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = size * 0.1f, end = size * 0.1f, top = size * 0.14f),
            )
        }
        if (four.size == 1) {
            AsyncImage(model = four[0], contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        if (four.size > 1) {
            four.forEachIndexed { i, url ->
                Box(
                    Modifier
                        .padding(start = if (i % 2 == 1) half else 0.dp, top = if (i > 1) half else 0.dp)
                        .size(half),
                ) {
                    AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
        }
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(size * 0.3f)
                    // Glowfin (M1): the name strip is the logo's glow, violet to aqua, with white words
                    .background(MixStrip)
                    .padding(horizontal = size * 0.07f),
        ) {
            Text(
                text = name,
                color = Color.White,
                fontSize = BenflixType.small,
                lineHeight = BenflixType.small,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The mix name strip: Glowfin's glow, a little see-through so the covers show under it */
private val MixStrip =
    Brush.horizontalGradient(
        0f to
            com.github.damontecres.wholphin.ui.theme.Glowfin.Violet
                .copy(alpha = 0.92f),
        0.6f to
            com.github.damontecres.wholphin.ui.theme.Glowfin.Blue
                .copy(alpha = 0.92f),
        1f to
            com.github.damontecres.wholphin.ui.theme.Glowfin.Aqua
                .copy(alpha = 0.92f),
    )

/** A colour pair for a mix with no covers yet (Glowfin: night blues and violets only, picked by name) */
private fun mixTint(name: String): List<Color> {
    val tints =
        listOf(
            listOf(Color(0xFF3B2C6E), com.github.damontecres.wholphin.ui.theme.Glowfin.Violet),
            listOf(Color(0xFF12305A), com.github.damontecres.wholphin.ui.theme.Glowfin.Blue),
            listOf(Color(0xFF0E3B47), Color(0xFF2BB5C0)),
            listOf(Color(0xFF2A1F5C), Color(0xFF6C6FE8)),
        )
    return tints[(name.lowercase().hashCode() and 0x7fffffff) % tints.size]
}

/** Liked songs: the glow (violet to aqua) with a big white heart */
@Composable
fun LikedCover(modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .clip(BenflixShapes.Card)
                .background(com.github.damontecres.wholphin.ui.theme.Glowfin.Glow),
    ) {
        Image(
            imageVector = BenflixHeartFilled,
            contentDescription = null,
            colorFilter = ColorFilter.tint(Color.White),
            modifier = Modifier.fillMaxSize(0.34f),
        )
    }
}

/** A dark tile at the end of a row: "Ask for music", "Import a playlist" */
@Composable
fun ActionCover(
    words: String,
    icon: BenflixIcon,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .clip(BenflixShapes.Card)
                .background(BenflixColors.Raised),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BenflixIconImage(icon, color = BenflixColors.Music, size = 30.dp)
            Text(
                text = words,
                color = BenflixColors.Soft,
                fontSize = BenflixType.small,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, start = 8.dp, end = 8.dp),
            )
        }
    }
}

/**
 * Glowfin (M1): four small bars bouncing, violet to aqua, for the song that's playing now (instead of its
 * number). [color] tints them all one colour instead (Kids, or a coloured background).
 */
@Composable
fun PlayingBars(
    playing: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    color: Color? = null,
) {
    // Speed check (Ben 2026-11-18): the bars used to be rebuilt and laid out again 60 times a second; now they're
    // only drawn, and when the music is paused nothing runs at all
    val heights =
        if (playing) {
            val t = rememberInfiniteTransition(label = "playingBars")
            listOf(0, 140, 280, 420).map { delay ->
                t.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1f,
                    animationSpec =
                        infiniteRepeatable(
                            animation = tween(durationMillis = 420, delayMillis = delay, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse,
                        ),
                    label = "bar$delay",
                )
            }
        } else {
            null
        }
    val colors = if (color != null) List(BAR_COUNT) { color } else PlayingBarColors
    androidx.compose.foundation.Canvas(modifier.size(size)) {
        val gap = size.toPx() * 0.1f
        val barWidth = (this.size.width - gap * (BAR_COUNT - 1)) / BAR_COUNT
        val corner =
            androidx.compose.ui.geometry
                .CornerRadius(1.dp.toPx())
        for (i in 0 until BAR_COUNT) {
            val f = heights?.get(i)?.value ?: PAUSED_BARS[i]
            val h = this.size.height * f
            drawRoundRect(
                color = colors[i],
                topLeft =
                    androidx.compose.ui.geometry
                        .Offset(i * (barWidth + gap), this.size.height - h),
                size =
                    androidx.compose.ui.geometry
                        .Size(barWidth, h),
                cornerRadius = corner,
            )
        }
    }
}

private const val BAR_COUNT = 4
private val PAUSED_BARS = floatArrayOf(0.5f, 0.85f, 0.4f, 0.65f)

/** Violet, two steps of blue, aqua: the logo's glow across the four bars */
private val PlayingBarColors =
    listOf(
        com.github.damontecres.wholphin.ui.theme.Glowfin.Violet,
        androidx.compose.ui.graphics.lerp(
            com.github.damontecres.wholphin.ui.theme.Glowfin.Violet,
            com.github.damontecres.wholphin.ui.theme.Glowfin.Blue,
            0.6f,
        ),
        com.github.damontecres.wholphin.ui.theme.Glowfin.Blue,
        com.github.damontecres.wholphin.ui.theme.Glowfin.Aqua,
    )

/** "3:45" */
fun songLength(d: Duration?): String {
    if (d == null) return ""
    val s = d.inWholeSeconds
    return "%d:%02d".format(s / 60, s % 60)
}

fun BaseItem.songLength(): String = songLength(data.runTimeTicks?.ticks)

/** "43 min" or "3 hr 10 min" */
fun totalLength(songs: List<BaseItem>): String {
    val mins = songs.sumOf { it.data.runTimeTicks ?: 0L } / 600_000_000L
    return when {
        mins < 60 -> "$mins min"
        mins % 60 == 0L -> "${mins / 60} hr"
        else -> "${mins / 60} hr ${mins % 60} min"
    }
}

/**
 * One song in a list (approved sr-num on albums, sr-cover on playlists and mixed lists): the number (or a
 * tiny cover and the artist), the name, a violet heart if liked, and the length. The playing song shows
 * small violet bouncing bars instead of its number, and its name turns violet.
 */
@Composable
fun SongRow(
    song: BaseItem,
    number: Int,
    withCover: Boolean,
    isCurrent: Boolean,
    isPlaying: Boolean,
    liked: Boolean,
    onClick: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    big: Boolean = false,
    dimmed: Boolean = false,
    onFocus: (() -> Unit)? = null,
    /** Music redesign 3: on phones, swiping the song to the right adds it to the queue */
    onSwipeQueue: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(8.dp)
    val imageUrlService = LocalImageUrlService.current
    val swipe =
        remember {
            androidx.compose.animation.core
                .Animatable(0f)
        }
    val swipeScope = androidx.compose.runtime.rememberCoroutineScope()
    val swipeAt = with(LocalDensity.current) { 88.dp.toPx() }
    // Music redesign 3: on phones every song can be swiped right to go on the queue (TV: More > Add to Up next)
    val context = androidx.compose.ui.platform.LocalContext.current
    val touch =
        !com.github.damontecres.wholphin.ui.touch
            .rememberIsTv()
    val swipeQueue =
        onSwipeQueue ?: if (touch && song.type == org.jellyfin.sdk.model.api.BaseItemKind.AUDIO) {
            {
                swipeScope.launch {
                    com.github.damontecres.wholphin.services
                        .musicService(context)
                        .addToUpNext(song)
                    com.github.damontecres.wholphin.ui.components.BenflixNotices
                        .pill("Added \"${song.title.orEmpty()}\" to the queue")
                }
            }
        } else {
            null
        }
    val onSwipe = androidx.compose.runtime.rememberUpdatedState(swipeQueue)
    val swipeMod =
        if (swipeQueue == null) {
            Modifier
        } else {
            Modifier
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val far = swipe.value >= swipeAt
                            if (far) onSwipe.value?.invoke()
                            swipeScope.launch { swipe.animateTo(0f) }
                        },
                        onDragCancel = { swipeScope.launch { swipe.animateTo(0f) } },
                    ) { change, dx ->
                        change.consume()
                        swipeScope.launch { swipe.snapTo((swipe.value + dx).coerceIn(0f, swipeAt * 1.3f)) }
                    }
                }.graphicsLayer { translationX = swipe.value }
        }

    val coverPx = with(LocalDensity.current) { 44.dp.roundToPx() }
    val cover = remember(song.id, withCover) { if (withCover) imageUrlService.musicCover(song, coverPx) else null }
    Surface(
        onClick = onClick,
        onLongClick = onMore,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = BenflixColors.Text,
                focusedContainerColor = BenflixColors.FocusRow,
                focusedContentColor = BenflixColors.Text,
            ),
        border =
            ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(2.dp, BenflixColors.Focus), shape = shape),
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier =
            modifier
                .fillMaxWidth()
                .then(swipeMod)
                .onFocusChanged { if (it.isFocused) onFocus?.invoke() },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = if (big) 10.dp else 6.dp),
        ) {
            val nameColor =
                when {
                    // Glowfin (M1): the playing song's name is aqua
                    isCurrent -> com.github.damontecres.wholphin.ui.theme.Glowfin.Aqua

                    dimmed -> BenflixColors.Muted

                    else -> BenflixColors.Text
                }
            if (withCover) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(if (big) 48.dp else 40.dp)) {
                    CoverImage(cover, Modifier.fillMaxSize(), shape = RoundedCornerShape(6.dp))
                    if (isCurrent) {
                        Box(Modifier.fillMaxSize().background(Color(0x99000000), RoundedCornerShape(6.dp)))
                        PlayingBars(isPlaying)
                    }
                }
            } else {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.width(28.dp)) {
                    if (isCurrent) {
                        PlayingBars(isPlaying)
                    } else {
                        Text(
                            text = number.toString(),
                            color = BenflixColors.Muted,
                            fontSize = if (big) BenflixType.lead else BenflixType.body,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title.orEmpty(),
                    color = nameColor,
                    fontSize = if (big) BenflixType.rowName else BenflixType.body,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (withCover) {
                    Text(
                        text =
                            song.data.artists
                                ?.joinToString(", ")
                                .orEmpty(),
                        color = BenflixColors.Muted,
                        fontSize = BenflixType.small,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (liked) {
                Image(
                    imageVector = BenflixHeartFilled,
                    contentDescription = "Liked",
                    colorFilter = ColorFilter.tint(BenflixColors.Music),
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                text = song.songLength(),
                color = BenflixColors.Muted,
                fontSize = BenflixType.small,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/**
 * A pill button on music pages: Play is white with dark words (the main thing to press), the others dark.
 * [label] null = just the icon (round).
 */
@Composable
fun MusicButton(
    label: String?,
    icon: BenflixIcon?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    big: Boolean = false,
    filledPlay: Boolean = false,
    filledHeart: Boolean = false,
) {
    val shape = RoundedCornerShape(50)
    val isTv =
        com.github.damontecres.wholphin.ui.touch
            .rememberIsTv()
    val bg = if (primary) BenflixColors.Text else BenflixColors.Raised
    val fg = if (primary) BenflixColors.Tray else BenflixColors.Text
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = bg,
                contentColor = fg,
                focusedContainerColor = if (primary) BenflixColors.Text else BenflixColors.FocusRow,
                focusedContentColor = fg,
            ),
        border =
            ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(3.dp, BenflixColors.Focus), inset = 3.dp, shape = shape),
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            modifier =
                Modifier
                    // On phones at least 48 x 48 so a finger can't miss it (a leftover from the music build)
                    .then(if (isTv) Modifier else Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp))
                    .padding(
                        horizontal = if (label == null) (if (big) 14.dp else 11.dp) else (if (big) 26.dp else 20.dp),
                        vertical = if (big) 14.dp else 10.dp,
                    ),
        ) {
            val iconSize = if (big) 24.dp else 20.dp
            when {
                filledPlay -> {
                    Image(
                        BenflixPlayFilled,
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(fg),
                        modifier = Modifier.size(iconSize),
                    )
                }

                filledHeart -> {
                    Image(
                        BenflixHeartFilled,
                        contentDescription = label ?: "Liked",
                        colorFilter = ColorFilter.tint(BenflixColors.Music),
                        modifier = Modifier.size(iconSize),
                    )
                }

                icon != null -> {
                    BenflixIconImage(icon, color = fg, size = iconSize, contentDescription = if (label == null) icon.name else null)
                }
            }
            if (label != null) {
                Text(
                    text = label,
                    color = fg,
                    fontSize = if (big) BenflixType.rowName else BenflixType.body,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** A row name, like on the Movies door */
@Composable
fun MusicRowName(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = BenflixColors.Text,
        fontSize = BenflixType.lead,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** A small capital label in violet, like "MADE FOR YOU · NEW EVERY MORNING" */
@Composable
fun MusicKicker(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = BenflixColors.Music,
) {
    Text(
        text = text.uppercase(),
        color = color,
        fontSize = BenflixType.tag,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.14.em,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}
