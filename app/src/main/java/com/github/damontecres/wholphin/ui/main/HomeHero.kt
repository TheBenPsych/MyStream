package com.github.damontecres.wholphin.ui.main

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.components.ExpandableFaButton
import com.github.damontecres.wholphin.ui.components.ExpandablePlayButton
import com.github.damontecres.wholphin.ui.nav.BACKDROP_HEIGHT_FRACTION
import com.github.damontecres.wholphin.ui.nav.BACKDROP_WIDTH_FRACTION
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
 * The buttons & page indicator for the rotating home page banner.
 *
 * The banner's reason label, title, details, and description are drawn by the page header above this.
 */
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
) {
    val current = featured.getOrNull(index) ?: return
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier =
            modifier
                .padding(start = 8.dp, bottom = 16.dp)
                .onFocusChanged { onFocusChanged(it.hasFocus) },
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.focusRestorer(focusRequester),
        ) {
            HeroPillButton(
                title = stringResource(R.string.play),
                icon = {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(26.dp),
                    )
                },
                containerColor = Color.White,
                contentColor = Color.Black,
                onClick = { onClickPlay(current.item) },
                modifier = Modifier.focusRequester(focusRequester),
            )
            HeroPillButton(
                title = stringResource(R.string.more_info),
                icon = {
                    Text(
                        text = stringResource(R.string.fa_circle_info),
                        fontFamily = FontAwesome,
                        fontSize = 18.sp,
                    )
                },
                containerColor = Color(0x99787882),
                contentColor = Color.White,
                onClick = { onClickInfo(current.item) },
            )
            ExpandableFaButton(
                title = if (current.item.favorite) R.string.in_my_list else R.string.my_list,
                iconStringRes = if (current.item.favorite) R.string.fa_check else R.string.fa_plus,
                onClick = { onClickFavorite(current.item) },
            )
            ExpandableFaButton(
                title = R.string.surprise_me,
                iconStringRes = R.string.fa_dice,
                onClick = onClickSurprise,
            )
        }
        if (featured.size > 1) {
            SlideDots(count = featured.size, index = index, onSelect = onSelectSlide)
        }
    }
}

/**
 * Benflix: the little banner dots. Each one can be picked with the remote (move down onto them and press OK,
 * or just move left/right across them) or tapped on a phone to jump to that slide.
 */
@Composable
fun SlideDots(
    count: Int,
    index: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
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
                        .onFocusChanged { if (it.isFocused) onSelect(i) }
                        .clickable(interactionSource = interaction, indication = null) { onSelect(i) }
                        .padding(horizontal = 3.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .width(width)
                            .height(if (focused) 8.dp else 4.dp)
                            .background(
                                if (selected || focused) Color.White else Color.White.copy(alpha = .35f),
                                RoundedCornerShape(4.dp),
                            ),
                )
            }
        }
    }
}

/**
 * A Netflix-style banner button that always shows its label. Turns bright white with dark text when focused.
 */
@Composable
fun HeroPillButton(
    title: String,
    icon: @Composable () -> Unit,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
        contentPadding = PaddingValues(start = 18.dp, end = 24.dp, top = 8.dp, bottom = 8.dp),
        colors =
            ButtonDefaults.colors(
                containerColor = containerColor,
                contentColor = contentColor,
                focusedContainerColor = Color.White,
                focusedContentColor = Color.Black,
            ),
        modifier = modifier.height(48.dp),
    ) {
        icon()
        Spacer(Modifier.width(10.dp))
        Text(
            text = title,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
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

/**
 * Benflix on phones: the banner is a rounded card holding the whole wide wallpaper picture,
 * with the title logo on it, like the Netflix phone app. Tap it to open the details.
 */
@Composable
fun PhoneHeroCard(
    featured: FeaturedItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val item = featured.item
    val imageUrlService = com.github.damontecres.wholphin.ui.LocalImageUrlService.current
    val backdropUrl =
        androidx.compose.runtime.remember(item.id) {
            imageUrlService.getItemImageUrl(item, org.jellyfin.sdk.model.api.ImageType.BACKDROP, fillWidth = 1280)
        }
    val logoUrl =
        com.github.damontecres.wholphin.ui.components
            .rememberLogoUrl(item)
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp),
    ) {
        FeaturedReasonLabel(featured = featured)
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1C1C22))
                    .clickable(onClick = onClick),
        ) {
            coil3.compose.AsyncImage(
                model = backdropUrl,
                contentDescription = item.title,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.45f to Color.Transparent,
                                1f to Color.Black.copy(alpha = .85f),
                            ),
                        ),
            )
            com.github.damontecres.wholphin.ui.components.TitleOrLogo(
                title = item.title,
                logoImageUrl = logoUrl,
                showLogo = true,
                logoHeight = 56.dp,
                logoMaxWidth = 240.dp,
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                        .fillMaxWidth(.7f),
            )
        }
    }
}
