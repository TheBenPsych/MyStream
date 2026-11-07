package com.github.damontecres.wholphin.ui.main

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.components.ExpandableFaButton
import com.github.damontecres.wholphin.ui.components.ExpandablePlayButton
import com.github.damontecres.wholphin.ui.components.LocalFlatButtons
import com.github.damontecres.wholphin.ui.components.MinButtonSize
import com.github.damontecres.wholphin.ui.nav.BACKDROP_HEIGHT_FRACTION
import com.github.damontecres.wholphin.ui.nav.BACKDROP_WIDTH_FRACTION
import com.github.damontecres.wholphin.ui.tryRequestFocus
import kotlin.time.Duration

enum class FeaturedReason {
    NEW,
    PICK,
    RESUME,
    FAVORITE,
    BECAUSE,
    TONIGHT,
    ON_THIS_DAY,
}

/**
 * An item shown in the rotating banner at the top of the home page
 */
data class FeaturedItem(
    val item: BaseItem,
    val reason: FeaturedReason,
    val becauseOf: String? = null,
)

/**
 * The small colored line above the banner title saying why it is shown, such as "Because you watched ..."
 */
@Composable
fun FeaturedReasonLabel(
    featured: FeaturedItem,
    modifier: Modifier = Modifier,
) {
    val text =
        when (featured.reason) {
            FeaturedReason.NEW -> {
                stringResource(R.string.featured_new)
            }

            FeaturedReason.PICK -> {
                stringResource(R.string.featured_pick)
            }

            FeaturedReason.RESUME -> {
                stringResource(R.string.featured_resume)
            }

            FeaturedReason.FAVORITE -> {
                stringResource(R.string.featured_favorite)
            }

            FeaturedReason.BECAUSE -> {
                stringResource(R.string.featured_because, featured.becauseOf.orEmpty())
            }

            FeaturedReason.TONIGHT -> {
                featured.becauseOf?.let { stringResource(R.string.featured_tonight_for, it) }
                    ?: stringResource(R.string.featured_tonight)
            }

            // Benflix: "Came out 25 years ago today"
            FeaturedReason.ON_THIS_DAY -> {
                featured.becauseOf.orEmpty()
            }
        }
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        modifier = modifier,
    )
}

/**
 * The buttons for the rotating home page banner (TV).
 *
 * Benflix (Ben's pick, 2026-11-04): slim flat buttons at the bottom left, in the same flat look as
 * the details pages: Play (filled red) and More info, then small arrows to flip to the previous or
 * next slide. No dots any more. Long-press Play or More info for the item's menu (My List and more).
 * Surprise me lives in the More menu at the top.
 *
 * The banner's reason label, title, details, and description are drawn by the page header above this.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun HomeHero(
    featured: List<FeaturedItem>,
    index: Int,
    onClickPlay: (BaseItem) -> Unit,
    onClickInfo: (BaseItem) -> Unit,
    onClickFavorite: (BaseItem) -> Unit,
    onClickSurprise: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    onSelectSlide: (Int) -> Unit = {},
    autoAdvanceMs: Long? = null,
    // Phones swipe the banner, so the arrows (and slide count) are TV only
    showDots: Boolean = true,
    onLongClickItem: ((BaseItem) -> Unit)? = null,
) {
    val current = featured.getOrNull(index) ?: return
    val count = featured.size
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        // A little room above, so the focus ring isn't clipped at the top
        modifier =
            modifier
                .padding(start = 8.dp, top = 6.dp, bottom = 16.dp)
                .onFocusChanged { onFocusChanged(it.hasFocus) }
                .focusRestorer(focusRequester),
    ) {
        CompositionLocalProvider(LocalFlatButtons provides true) {
            ExpandablePlayButton(
                title = R.string.play,
                resume = Duration.ZERO,
                icon = Icons.Default.PlayArrow,
                primary = true,
                onClick = { onClickPlay(current.item) },
                onLongClick = onLongClickItem?.let { { it(current.item) } },
                modifier = Modifier.focusRequester(focusRequester),
            )
            ExpandableFaButton(
                title = R.string.more_info,
                iconStringRes = R.string.fa_circle_info,
                onClick = { onClickInfo(current.item) },
                onLongClick = onLongClickItem?.let { { it(current.item) } },
            )
        }
        // Phones swipe the banner instead, so they get no arrows
        if (showDots && count > 1) {
            Spacer(Modifier.width(4.dp))
            SlideArrow(
                glyph = R.string.fa_caret_left,
                description = R.string.benflix_banner_previous,
                onClick = { onSelectSlide((index - 1 + count) % count) },
            )
            SlideArrow(
                glyph = R.string.fa_caret_right,
                description = R.string.benflix_banner_next,
                onClick = { onSelectSlide((index + 1) % count) },
            )
            Text(
                text = "${index + 1} / $count",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = .6f),
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

/** A small square flat button with an arrow, to flip the banner to another slide */
@Composable
private fun SlideArrow(
    glyph: Int,
    description: Int,
    onClick: () -> Unit,
) {
    val label = stringResource(description)
    com.github.damontecres.wholphin.ui.components.Button(
        onClick = onClick,
        shape =
            androidx.tv.material3.ClickableSurfaceDefaults
                .shape(shape = RoundedCornerShape(10.dp)),
        scale =
            androidx.tv.material3.ClickableSurfaceDefaults
                .scale(focusedScale = 1f, pressedScale = .97f),
        contentPadding = PaddingValues(0.dp),
        modifier =
            Modifier
                .size(MinButtonSize)
                .semantics { contentDescription = label },
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text = stringResource(glyph),
                fontFamily = FontAwesome,
                fontSize = 18.sp,
            )
        }
    }
}

private val DOT_RED = Color(0xFFE5484D)

/**
 * Benflix: the little banner dots on phones. Each one can be tapped to jump to that slide.
 */
@Composable
fun SlideDots(
    count: Int,
    index: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    autoAdvanceMs: Long? = null,
) {
    // Benflix: moving down onto the dots always lands on the slide being shown, wherever you come from,
    // and if the banner moves on while you're on the dots, the red one moves with it
    val dotRequesters = remember(count) { List(count) { FocusRequester() } }
    var dotsFocused by remember { mutableStateOf(false) }
    val currentIndex by rememberUpdatedState(index)
    LaunchedEffect(index) {
        if (dotsFocused) dotRequesters.getOrNull(index)?.tryRequestFocus()
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .onFocusChanged { dotsFocused = it.hasFocus }
                .focusProperties {
                    onEnter = { dotRequesters.getOrNull(currentIndex)?.tryRequestFocus() }
                }.focusGroup(),
    ) {
        (0 until count).forEach { i ->
            val interaction = remember { MutableInteractionSource() }
            val focused by interaction.collectIsFocusedAsState()
            val selected = i == index
            val width by animateDpAsState(if (selected) 28.dp else 10.dp)
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .height(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .focusRequester(dotRequesters[i])
                        .onFocusChanged { if (it.isFocused && i != currentIndex) onSelect(i) }
                        .clickable(interactionSource = interaction, indication = null) { onSelect(i) }
                        .padding(horizontal = 3.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .width(width)
                            .height(if (focused) 8.dp else 4.dp)
                            .background(
                                when {
                                    // Benflix red on the one you're on, so it's easy to see
                                    focused -> DOT_RED

                                    selected -> Color.White.copy(alpha = .55f)

                                    else -> Color.White.copy(alpha = .35f)
                                },
                                RoundedCornerShape(4.dp),
                            ),
                ) {
                    if (selected && !focused) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxHeight()
                                    // Benflix: a plain red dot for the slide on screen (it used to fill up like a timer,
                                    // which kept the TV redrawing the whole time Home was open)
                                    .fillMaxWidth()
                                    .background(
                                        if (autoAdvanceMs != null) DOT_RED else Color.White,
                                        RoundedCornerShape(4.dp),
                                    ),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The muted trailer preview shown behind the banner, faded into the background on the left & bottom
 */
@Composable
fun HeroTrailer(
    player: Player,
    modifier: Modifier = Modifier,
    clear: Float = 0f,
) {
    // clear goes 0 -> 1 to shrink the dark shading to just a thin soft edge, so nothing sits over
    // the picture while the page text is hidden
    val background = MaterialTheme.colorScheme.background
    val shade = 1f - clear
    Box(
        modifier =
            modifier
                .fillMaxWidth(BACKDROP_WIDTH_FRACTION)
                .fillMaxHeight(BACKDROP_HEIGHT_FRACTION),
    ) {
        PlayerSurface(
            player = player,
            surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            0f to background,
                            (.06f + .29f * shade) to background.copy(alpha = .4f * shade),
                            (.12f + .48f * shade) to Color.Transparent,
                        ),
                    ),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            (.88f - .28f * shade) to Color.Transparent,
                            1f to background,
                        ),
                    ),
        )
    }
}
