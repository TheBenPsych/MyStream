package com.github.damontecres.wholphin.ui.components

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSizeIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.LocalTrailer
import com.github.damontecres.wholphin.data.model.Trailer
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.PreviewTvSpec
import com.github.damontecres.wholphin.ui.data.ChooseVersionParams
import com.github.damontecres.wholphin.ui.data.SortAndDirection
import com.github.damontecres.wholphin.ui.ifElse
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.PreviewInteractionSource
import com.github.damontecres.wholphin.ui.theme.WholphinTheme
import com.github.damontecres.wholphin.ui.tryRequestFocus
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Standard row of [ExpandablePlayButton] including Play (or Resume & Restart), Mark played, & More
 */
@Composable
fun ExpandablePlayButtons(
    title: String,
    resumePosition: Duration,
    watched: Boolean,
    favorite: Boolean,
    canDelete: Boolean,
    trailers: List<Trailer>?,
    playOnClick: (position: Duration) -> Unit,
    watchOnClick: () -> Unit,
    favoriteOnClick: () -> Unit,
    moreOnClick: () -> Unit,
    trailerOnClick: (Trailer) -> Unit,
    onConfirmDelete: () -> Unit,
    chooseVersionParams: ChooseVersionParams?,
    buttonOnFocusChanged: (FocusState) -> Unit,
    modifier: Modifier = Modifier,
    likes: Boolean? = null,
    rateOnClick: ((Boolean?) -> Unit)? = null,
    downloadItem: com.github.damontecres.wholphin.data.model.BaseItem? = null,
    goToShowOnClick: (() -> Unit)? = null,
    seasonWatched: Boolean? = null,
    seasonWatchOnClick: (() -> Unit)? = null,
    shuffleSeasonOnClick: (() -> Unit)? = null,
) {
    val resources = LocalResources.current
    // Benflix look A: flat buttons; the rarer ones (version, favourite, delete) live in More
    val flat = LocalFlatButtons.current
    val firstFocus = remember { FocusRequester() }
    val restartInteractionSource = remember { MutableInteractionSource() }
    val restartButtonFocused by restartInteractionSource.collectIsFocusedAsState()
    LaunchedEffect(resumePosition) {
        // Reset focus if the restart button was focuses and it is going to disappear
        if (restartButtonFocused && resumePosition == Duration.ZERO) {
            firstFocus.tryRequestFocus()
        }
    }

    var chooseVersion by remember { mutableStateOf<DialogParams?>(null) }
    val isTv =
        com.github.damontecres.wholphin.ui.touch
            .rememberIsTv()

    // Benflix on phones: a big full-width Play button, like the Netflix phone app
    androidx.compose.foundation.layout.Column(modifier = if (isTv) Modifier else modifier) {
        if (!isTv) {
            androidx.tv.material3.Button(
                onClick = { playOnClick.invoke(resumePosition) },
                colors =
                    if (flat) {
                        androidx.tv.material3.ButtonDefaults.colors(
                            containerColor = com.github.damontecres.wholphin.ui.theme.BenflixRed,
                            contentColor = com.github.damontecres.wholphin.ui.theme.OnBenflixRed,
                            focusedContainerColor = com.github.damontecres.wholphin.ui.theme.BenflixRed,
                            focusedContentColor = com.github.damontecres.wholphin.ui.theme.OnBenflixRed,
                        )
                    } else {
                        androidx.tv.material3.ButtonDefaults.colors(
                            containerColor = androidx.compose.ui.graphics.Color.White,
                            contentColor = androidx.compose.ui.graphics.Color.Black,
                            focusedContainerColor = com.github.damontecres.wholphin.ui.theme.BenflixColors.FocusFill,
                            focusedContentColor = com.github.damontecres.wholphin.ui.theme.BenflixColors.OnFocusFill,
                        )
                    },
                // Phones have no remote focus: don't let the button grow wider than the page
                scale =
                    androidx.tv.material3.ButtonDefaults
                        .scale(focusedScale = 1f),
                shape =
                    androidx.tv.material3.ButtonDefaults
                        .shape(
                            androidx.compose.foundation.shape
                                .RoundedCornerShape(if (flat) 12.dp else 6.dp),
                        ),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                androidx.compose.foundation.layout.Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    androidx.tv.material3.Icon(
                        imageVector = BenflixPlayFilled,
                        contentDescription = null,
                    )
                    androidx.tv.material3.Text(
                        text =
                            androidx.compose.ui.res.stringResource(
                                if (resumePosition > Duration.ZERO) R.string.resume else R.string.play,
                            ),
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
            // Benflix: save it on the phone to watch without internet
            downloadItem?.let {
                com.github.damontecres.wholphin.ui.downloads
                    .DownloadButton(it)
            }
            // Benflix: send it to the TV instead
            downloadItem?.let { PlayOnTvButton(item = it, position = resumePosition) }
        }
        // Phones: always show each button's name, since taps don't focus them
        CompositionLocalProvider(LocalAlwaysShowButtonLabels provides !isTv) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(8.dp),
                modifier =
                    if (isTv) {
                        modifier
                            .focusGroup()
                            .focusRestorer(firstFocus)
                    } else {
                        Modifier
                    },
            ) {
                if (isTv) {
                    item("play") {
                        ExpandablePlayButton(
                            title = if (resumePosition > Duration.ZERO) R.string.resume else R.string.play,
                            resume = resumePosition,
                            icon = BenflixPlayFilled,
                            onClick = playOnClick,
                            primary = true,
                            modifier =
                                Modifier
                                    .onFocusChanged(buttonOnFocusChanged)
                                    .focusRequester(firstFocus),
                        )
                    }
                }
                if (resumePosition > Duration.ZERO) {
                    item("restart") {
                        ExpandablePlayButton(
                            title = R.string.restart,
                            resume = Duration.ZERO,
                            icon = BenflixIcon.REFRESH.vector,
                            onClick = playOnClick,
                            modifier = Modifier.onFocusChanged(buttonOnFocusChanged),
                            mirrorIcon = true,
                            interactionSource = restartInteractionSource,
                        )
                    }
                }

                // Benflix: one press back to the show's own page (from an episode or season)
                if (goToShowOnClick != null) {
                    item("go_to_show") {
                        ExpandableFaButton(
                            title = R.string.go_to_show,
                            iconStringRes = R.string.fa_tv,
                            onClick = goToShowOnClick,
                            modifier = Modifier.onFocusChanged(buttonOnFocusChanged),
                        )
                    }
                }

                if (!flat && chooseVersionParams != null && chooseVersionParams.mediaSources.size > 1) {
                    item {
                        ExpandableFaButton(
                            title = R.string.version,
                            iconStringRes = R.string.fa_file_video,
                            onClick = {
                                chooseVersion =
                                    chooseVersionParams(
                                        resources,
                                        chooseVersionParams.mediaSources.orEmpty(),
                                        chooseVersionParams.chosenStreams
                                            ?.source
                                            ?.id
                                            ?.toUUIDOrNull(),
                                    ) { idx ->
                                        val source = chooseVersionParams.mediaSources[idx]
                                        chooseVersionParams.onChooseVersion.invoke(source)
                                    }
                            },
                            modifier = Modifier.onFocusChanged(buttonOnFocusChanged),
                        )
                    }
                }

                // Watched button
                item("watched") {
                    ExpandableFaButton(
                        title = if (watched) R.string.mark_unwatched else R.string.mark_watched,
                        iconStringRes = if (watched) R.string.fa_eye else R.string.fa_eye_slash,
                        onClick = watchOnClick,
                        modifier = Modifier.onFocusChanged(buttonOnFocusChanged),
                    )
                }

                // Benflix: the whole season at once, on the season page
                if (seasonWatched != null && seasonWatchOnClick != null) {
                    item("season_watched") {
                        ExpandableFaButton(
                            title = if (seasonWatched) R.string.benflix_mark_season_unwatched else R.string.benflix_mark_season_watched,
                            iconStringRes = R.string.fa_check,
                            onClick = seasonWatchOnClick,
                            modifier = Modifier.onFocusChanged(buttonOnFocusChanged),
                        )
                    }
                }
                if (shuffleSeasonOnClick != null) {
                    item("shuffle_season") {
                        ExpandableFaButton(
                            title = R.string.benflix_shuffle_season,
                            iconStringRes = R.string.fa_shuffle,
                            onClick = shuffleSeasonOnClick,
                            modifier = Modifier.onFocusChanged(buttonOnFocusChanged),
                        )
                    }
                }

                // Favorite button
                if (!flat) {
                    item("favorite") {
                        ExpandableFaButton(
                            title = if (favorite) R.string.remove_favorite else R.string.add_favorite,
                            iconStringRes = R.string.fa_heart,
                            onClick = favoriteOnClick,
                            iconColor = if (favorite) Color.Red else Color.Unspecified,
                            modifier = Modifier.onFocusChanged(buttonOnFocusChanged),
                        )
                    }
                }

                if (rateOnClick != null) {
                    item("like") {
                        LikeButton(likes, rateOnClick, Modifier.onFocusChanged(buttonOnFocusChanged))
                    }
                    item("dislike") {
                        DislikeButton(likes, rateOnClick, Modifier.onFocusChanged(buttonOnFocusChanged))
                    }
                }

                if (trailers != null) {
                    item("trailers") {
                        TrailerButton(
                            trailers = trailers,
                            trailerOnClick = trailerOnClick,
                            modifier = Modifier.onFocusChanged(buttonOnFocusChanged),
                        )
                    }
                }
                if (canDelete && !flat) {
                    item("delete") {
                        DeleteButton(
                            title = title,
                            onConfirmDelete = onConfirmDelete,
                            modifier =
                                Modifier
                                    .onFocusChanged(buttonOnFocusChanged),
                        )
                    }
                }

                // More button
                item("more") {
                    ExpandablePlayButton(
                        title = R.string.more,
                        resume = Duration.ZERO,
                        icon = BenflixIcon.MORE_VERT.vector,
                        onClick = { moreOnClick.invoke() },
                        modifier = Modifier.onFocusChanged(buttonOnFocusChanged),
                    )
                }
            }
        }
    }
    chooseVersion?.let { params ->
        DialogPopup(
            showDialog = true,
            title = params.title,
            dialogItems = params.items,
            onDismissRequest = { chooseVersion = null },
            dismissOnClick = true,
            waitToLoad = params.fromLongClick,
        )
    }
}

val LikedColor = Color(0xFF46D369)
val DislikedColor = Color(0xFFFF8A3D)

/**
 * Thumbs up button. Pressing it again clears the rating.
 */
@Composable
fun LikeButton(
    likes: Boolean?,
    rateOnClick: (Boolean?) -> Unit,
    modifier: Modifier = Modifier,
) = ExpandableFaButton(
    title = if (likes == true) R.string.liked else R.string.like,
    iconStringRes = R.string.fa_thumbs_up,
    onClick = { rateOnClick.invoke(if (likes == true) null else true) },
    iconColor = if (likes == true) LikedColor else Color.Unspecified,
    modifier = modifier,
)

/**
 * Thumbs down button. Pressing it again clears the rating.
 */
@Composable
fun DislikeButton(
    likes: Boolean?,
    rateOnClick: (Boolean?) -> Unit,
    modifier: Modifier = Modifier,
) = ExpandableFaButton(
    title = if (likes == false) R.string.disliked else R.string.dislike,
    iconStringRes = R.string.fa_thumbs_down,
    onClick = { rateOnClick.invoke(if (likes == false) null else false) },
    iconColor = if (likes == false) DislikedColor else Color.Unspecified,
    modifier = modifier,
)

val MinButtonSize = 40.dp

/**
 * Benflix on phones: taps don't move focus, so an expandable button would never show its name.
 * A scrolling row of buttons on a phone sets this to true so every button always shows its name.
 * Always false on TV, where the name still appears when the button is focused.
 */
val LocalAlwaysShowButtonLabels = staticCompositionLocalOf { false }

/**
 * Benflix look A: flat buttons. Each button is a rounded rectangle that always shows its name
 * (no round icon that grows when focused). Pages that use the new look set this to true.
 */
val LocalFlatButtons = staticCompositionLocalOf { false }

/** Benflix look A: the rounded rectangle shape of a flat button */
private val FlatButtonShape =
    androidx.compose.foundation.shape
        .RoundedCornerShape(10.dp)

@Composable
private fun flatShape() =
    if (LocalFlatButtons.current) {
        androidx.tv.material3.ClickableSurfaceDefaults
            .shape(shape = FlatButtonShape)
    } else {
        androidx.tv.material3.ClickableSurfaceDefaults
            .shape(shape = androidx.compose.foundation.shape.CircleShape)
    }

/**
 * Benflix redesign: buttons are dark grey with white words, and the one you're on turns white with
 * dark words (easy to spot from the sofa, and nothing moves)
 */
@Composable
private fun primaryColors() =
    androidx.tv.material3.ClickableSurfaceDefaults.colors(
        containerColor = com.github.damontecres.wholphin.ui.theme.BenflixColors.FocusRow,
        contentColor = com.github.damontecres.wholphin.ui.theme.BenflixColors.Text,
        focusedContainerColor = com.github.damontecres.wholphin.ui.theme.BenflixColors.Text,
        focusedContentColor = com.github.damontecres.wholphin.ui.theme.BenflixColors.Tray,
        pressedContainerColor = com.github.damontecres.wholphin.ui.theme.BenflixColors.Text,
        pressedContentColor = com.github.damontecres.wholphin.ui.theme.BenflixColors.Tray,
    )

@Composable
private fun whiteColors() =
    androidx.tv.material3.ClickableSurfaceDefaults.colors(
        containerColor = com.github.damontecres.wholphin.ui.theme.BenflixColors.Text,
        contentColor = com.github.damontecres.wholphin.ui.theme.BenflixColors.Tray,
        focusedContainerColor = com.github.damontecres.wholphin.ui.theme.BenflixColors.Text,
        focusedContentColor = com.github.damontecres.wholphin.ui.theme.BenflixColors.Tray,
        pressedContainerColor = com.github.damontecres.wholphin.ui.theme.BenflixColors.Soft,
        pressedContentColor = com.github.damontecres.wholphin.ui.theme.BenflixColors.Tray,
    )

@Composable
private fun whiteBorder() =
    androidx.tv.material3.ClickableSurfaceDefaults.border(
        focusedBorder =
            androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(3.dp, BenflixColors.Focus),
                inset = (-4).dp,
                shape = FlatButtonShape,
            ),
    )

@Composable
private fun primaryBorder() =
    androidx.tv.material3.ClickableSurfaceDefaults.border(
        focusedBorder =
            androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(3.dp, BenflixColors.Focus),
                shape = FlatButtonShape,
            ),
    )

/** Benflix look A: flat buttons don't grow when focused, so nothing around them moves */
@Composable
private fun flatScale() =
    if (LocalFlatButtons.current) {
        androidx.tv.material3.ClickableSurfaceDefaults
            .scale(focusedScale = 1f, pressedScale = 0.97f)
    } else {
        androidx.tv.material3.ClickableSurfaceDefaults
            .scale()
    }

/** Benflix look A: flat buttons get a little more room left and right for their name */
private val FlatButtonPadding = PaddingValues(start = 10.dp, top = 4.dp, end = 12.dp, bottom = 4.dp)

/**
 * An icon button typically used in a row for playing media
 *
 * Only shows the icon until focused when it expands to show the title
 */
@Composable
fun ExpandablePlayButton(
    @StringRes title: Int,
    resume: Duration,
    icon: ImageVector,
    onClick: (position: Duration) -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    mirrorIcon: Boolean = false,
    enabled: Boolean = true,
    primary: Boolean = false,
    label: String? = null,
    onLongClick: (() -> Unit)? = null,
) = ExpandablePlayButton(
    title = title,
    resume = resume,
    primary = primary,
    label = label,
    onLongClick = onLongClick,
    icon = {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier =
                Modifier
                    .size(28.dp)
                    .ifElse(mirrorIcon, Modifier.graphicsLayer { scaleX = -1f }),
        )
    },
    onClick = onClick,
    modifier = modifier,
    interactionSource = interactionSource,
    enabled = enabled,
)

@Composable
fun ExpandablePlayButton(
    @StringRes title: Int,
    resume: Duration,
    icon: Painter,
    onClick: (position: Duration) -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    mirrorIcon: Boolean = false,
    enabled: Boolean = true,
    primary: Boolean = false,
) = ExpandablePlayButton(
    title = title,
    resume = resume,
    primary = primary,
    icon = {
        Icon(
            painter = icon,
            contentDescription = null,
            modifier =
                Modifier
                    .size(28.dp)
                    .ifElse(mirrorIcon, Modifier.graphicsLayer { scaleX = -1f }),
        )
    },
    onClick = onClick,
    modifier = modifier,
    interactionSource = interactionSource,
    enabled = enabled,
)

@Composable
fun ExpandablePlayButton(
    @StringRes title: Int,
    resume: Duration,
    icon: @Composable BoxScope.() -> Unit,
    onClick: (position: Duration) -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    enabled: Boolean = true,
    primary: Boolean = false,
    label: String? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val isFocused = interactionSource.collectIsFocusedAsState().value
    val flat = LocalFlatButtons.current
    // Benflix: a greyed-out button still takes focus (pressing it does nothing). On TV a truly
    // disabled button can't be focused, so pressing Down skipped it and the page never showed it.
    Button(
        onClick = { if (enabled) onClick.invoke(resume) },
        onLongClick = onLongClick?.takeIf { enabled },
        enabled = true,
        modifier =
            modifier.alpha(if (enabled) 1f else DISABLED_ALPHA).requiredSizeIn(
                minWidth = MinButtonSize,
                minHeight = MinButtonSize,
                maxHeight = MinButtonSize,
            ),
        shape = flatShape(),
        scale = flatScale(),
        colors = if (flat && primary) primaryColors() else buttonDefaultColors(),
        border = if (flat && primary) primaryBorder() else buttonDefaultBorder(),
        contentPadding = if (flat) FlatButtonPadding else DefaultButtonPadding,
        interactionSource = interactionSource,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .padding(start = 2.dp)
                    .height(MinButtonSize),
        ) {
            icon.invoke(this)
        }
        AnimatedVisibility(flat || isFocused || LocalAlwaysShowButtonLabels.current) {
            Spacer(Modifier.size(8.dp))
            Text(
                text = label ?: stringResource(title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(end = 4.dp),
            )
        }
    }
}

/**
 * Similar to [ExpandablePlayButton], but uses a [FontAwesome] string instead of an Icon
 */
@Composable
fun ExpandableFaButton(
    @StringRes title: Int,
    @StringRes iconStringRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    iconColor: Color = Color.Unspecified,
    enabled: Boolean = true,
    primary: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    // Benflix redesign: a solid white button (Requests "Ask for it"), with the white outline just outside it
    white: Boolean = false,
) {
    val isFocused = interactionSource.collectIsFocusedAsState().value
    val flat = LocalFlatButtons.current
    // Benflix: greyed out but still focusable, so D-pad focus never jumps over it (see ExpandablePlayButton)
    Button(
        onClick = { if (enabled) onClick.invoke() },
        onLongClick = onLongClick?.takeIf { enabled },
        colors =
            when {
                white -> whiteColors()
                flat && primary -> primaryColors()
                else -> buttonDefaultColors()
            },
        border =
            when {
                white -> whiteBorder()
                flat && primary -> primaryBorder()
                else -> buttonDefaultBorder()
            },
        enabled = true,
        modifier =
            modifier.alpha(if (enabled) 1f else DISABLED_ALPHA).requiredSizeIn(
                minWidth = MinButtonSize,
                minHeight = MinButtonSize,
                maxHeight = MinButtonSize,
            ),
        shape = flatShape(),
        scale = flatScale(),
        contentPadding = if (flat) FlatButtonPadding else DefaultButtonPadding,
        interactionSource = interactionSource,
    ) {
        Box(
            modifier =
                Modifier
                    .size(32.dp),
        ) {
            Text(
                text = stringResource(iconStringRes),
                style = MaterialTheme.typography.titleSmall,
                color = iconColor,
                fontSize = 16.sp,
                fontFamily = FontAwesome,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        AnimatedVisibility(flat || isFocused || LocalAlwaysShowButtonLabels.current) {
            Text(
                text = stringResource(title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(end = 4.dp),
            )
        }
    }
}

@Composable
fun TrailerButton(
    trailers: List<Trailer>,
    trailerOnClick: (Trailer) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDialog by remember { mutableStateOf(false) }
    ExpandableFaButton(
        title =
            if (trailers.isEmpty()) {
                R.string.no_trailers
            } else if (trailers.size == 1 || trailers.any { it is LocalTrailer }) {
                R.string.play_trailer
            } else {
                R.string.trailers_title
            },
        iconStringRes = R.string.fa_film,
        enabled = trailers.isNotEmpty(),
        onClick = {
            // A downloaded (Trailarr) trailer plays straight away, no "local or YouTube" pop-up
            val local = trailers.firstOrNull { it is LocalTrailer }
            if (local != null) {
                trailerOnClick.invoke(local)
            } else if (trailers.size == 1) {
                trailerOnClick.invoke(trailers.first())
            } else {
                showDialog = true
            }
        },
        modifier = modifier,
    )
    if (showDialog) {
        TrailerDialog(
            onDismissRequest = { showDialog = false },
            trailers = trailers,
            onClick = trailerOnClick,
        )
    }
}

@Composable
fun DeleteButton(
    onConfirmDelete: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val focused by interactionSource.collectIsFocusedAsState()
    val iconTint by
        animateColorAsState(
            targetValue =
                if (focused) {
                    Color.Red.copy(alpha = .8f)
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(
                        alpha = 0.8f,
                    )
                },
            animationSpec =
                tween(
                    easing = LinearEasing,
                ),
        )
    ExpandablePlayButton(
        title = R.string.delete,
        resume = Duration.ZERO,
        icon = {
            Icon(
                imageVector = BenflixIcon.TRASH.vector,
                contentDescription = null,
                tint = if (iconTint.isSpecified) iconTint else LocalContentColor.current,
                modifier =
                    Modifier
                        .padding(start = 2.dp)
                        .size(24.dp),
            )
        },
        onClick = { showDeleteDialog = true },
        interactionSource = interactionSource,
        modifier = modifier,
    )
    if (showDeleteDialog) {
        ConfirmDeleteDialog(
            itemTitle = title,
            onCancel = { showDeleteDialog = false },
            onConfirm = {
                onConfirmDelete.invoke()
                showDeleteDialog = false
            },
        )
    }
}

@PreviewTvSpec
@Composable
private fun ExpandablePlayButtonsPreview() {
    WholphinTheme(true) {
        ExpandablePlayButtons(
            title = "Movie",
            resumePosition = 10.seconds,
            watched = false,
            favorite = false,
            playOnClick = {},
            watchOnClick = {},
            favoriteOnClick = {},
            moreOnClick = {},
            buttonOnFocusChanged = {},
            trailers = listOf(),
            trailerOnClick = {},
            canDelete = true,
            onConfirmDelete = {},
            chooseVersionParams = null,
            modifier = Modifier,
        )
    }
}

@PreviewTvSpec
@Composable
private fun ViewOptionsPreview() {
    val source = remember { PreviewInteractionSource() }
    WholphinTheme {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            ExpandablePlayButton(
                title = R.string.play,
                resume = Duration.ZERO,
                icon = BenflixPlayFilled,
                onClick = {},
                interactionSource = source,
            )
            ExpandablePlayButton(
                title = R.string.play,
                resume = Duration.ZERO,
                icon = painterResource(R.drawable.baseline_pause_24),
                onClick = {},
                interactionSource = source,
            )
            ExpandableFaButton(
                title = R.string.play,
                iconStringRes = R.string.fa_eye,
                onClick = {},
                modifier = Modifier,
                interactionSource = source,
            )

            Row {
                ExpandableFaButton(
                    title = R.string.mark_unwatched,
                    iconStringRes = R.string.fa_eye,
                    onClick = {},
                    modifier = Modifier,
                )
                DeleteButton(
                    onConfirmDelete = {},
                    title = "Movie",
                )
                SortByButton(
                    sortOptions = listOf(),
                    current = SortAndDirection(ItemSortBy.DEFAULT, SortOrder.ASCENDING),
                    onSortChange = {},
                )
            }
            DeleteButton(
                onConfirmDelete = {},
                title = "Movie",
                interactionSource = source,
            )
        }
    }
}

/** Benflix: how see-through a greyed-out (does nothing) button is */
private const val DISABLED_ALPHA = .45f
