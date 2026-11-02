package com.github.damontecres.wholphin.ui.nav

import android.view.Gravity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.ifElse
import com.github.damontecres.wholphin.ui.setup.UserIconCardImage
import com.github.damontecres.wholphin.ui.tryRequestFocus
import kotlinx.coroutines.delay
import java.util.UUID

/** The graphite colour of Benflix pop-ups (same as the phone's My Benflix sheet) */
private val MenuPanel = Color(0xFF18181C)
private val MenuLine = Color(0xFF2C2C33)
private val MenuMuted = Color(0xFF8B8B94)
private val BenflixRed = Color(0xFFE5484D)

private val BrowseCardWidth = 176.dp
private val BrowseGap = 10.dp
private const val BROWSE_COLUMNS = 3
private val MoreWidth = 330.dp

/** Picture-less cards (still loading, or no artwork) get one of these instead */
private val CardColors =
    listOf(
        Color(0xFF7A2A1C) to Color(0xFFD98B3A),
        Color(0xFF1C3A6B) to Color(0xFF4AA3C9),
        Color(0xFF5A1C5E) to Color(0xFFE05AA0),
        Color(0xFF1D4D3A) to Color(0xFF7AD0A0),
        Color(0xFF3B2A12) to Color(0xFFD9B04A),
        Color(0xFF25252B) to Color(0xFF6C6C78),
    )

/**
 * A pop-up that drops down right under its tab in the top bar, with a soft fade.
 * [anchorX] is the left edge of the tab, [top] the bottom of the bar, both in window pixels.
 */
@Composable
private fun TopNavDropdown(
    anchorX: Float,
    top: Float,
    width: Dp,
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    // Keep the panel on screen if the tab is far to the right
    val x =
        with(density) {
            minOf(anchorX, (screenWidth - width - 24.dp).toPx()).coerceAtLeast(0f).toInt()
        }
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.let {
                it.setGravity(Gravity.TOP or Gravity.START)
                it.setDimAmount(0.35f)
                it.setWindowAnimations(android.R.style.Animation_Toast)
                val params = it.attributes
                params.x = x
                params.y = top.toInt()
                it.attributes = params
            }
        }
        Box(
            modifier =
                Modifier
                    .background(MenuPanel, RoundedCornerShape(12.dp))
                    .border(1.dp, MenuLine, RoundedCornerShape(12.dp))
                    .padding(10.dp),
        ) {
            content()
        }
    }
}

/**
 * Browse: every library as a small wide card, showing a backdrop from one of its own titles
 */
@Composable
fun BrowseMenu(
    tabs: List<TopNavTab>,
    anchorX: Float,
    top: Float,
    onDismissRequest: () -> Unit,
) {
    val columns = minOf(BROWSE_COLUMNS, tabs.size.coerceAtLeast(1))
    val width = BrowseCardWidth * columns + BrowseGap * (columns - 1) + 32.dp
    TopNavDropdown(anchorX = anchorX, top = top, width = width, onDismissRequest = onDismissRequest) {
        val start = tabs.indexOfFirst { it.selected }.coerceAtLeast(0)
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            delay(30)
            focusRequester.tryRequestFocus()
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(BrowseGap),
            modifier =
                Modifier
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState())
                    // Room for the focused card to grow without being cut off
                    .padding(6.dp),
        ) {
            tabs.chunked(columns).forEachIndexed { row, rowTabs ->
                Row(horizontalArrangement = Arrangement.spacedBy(BrowseGap)) {
                    rowTabs.forEachIndexed { col, tab ->
                        val index = row * columns + col
                        BrowseCard(
                            tab = tab,
                            index = index,
                            onClick = {
                                onDismissRequest()
                                tab.onClick()
                            },
                            modifier = Modifier.ifElse(index == start, Modifier.focusRequester(focusRequester)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowseCard(
    tab: TopNavTab,
    index: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (from, to) = CardColors[index % CardColors.size]
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        border =
            ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(8.dp)),
            ),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                contentColor = Color.White,
                focusedContentColor = Color.White,
            ),
        modifier =
            modifier
                .width(BrowseCardWidth)
                .aspectRatio(16f / 9f),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(from, to))),
        ) {
            if (tab.imageUrl != null) {
                AsyncImage(
                    model = tab.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // Dark at the bottom so the name is easy to read on any picture
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(0.3f to Color.Transparent, 1f to Color.Black.copy(alpha = .88f))),
            )
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(
                    text = tab.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.weight(1f))
                tab.count?.let {
                    Text(
                        text = "%,d".format(it),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = .7f),
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
            // The library you're in gets a thin red line along the bottom
            if (tab.selected) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(BenflixRed),
                )
            }
        }
    }
}

/**
 * More: My Benflix on top with your picture, then the extras, each with an icon and a short line saying what it does
 */
@Composable
fun MoreMenu(
    tabs: List<TopNavTab>,
    userId: UUID,
    userName: String,
    userImageUrl: String?,
    anchorX: Float,
    top: Float,
    onDismissRequest: () -> Unit,
) {
    TopNavDropdown(anchorX = anchorX, top = top, width = MoreWidth, onDismissRequest = onDismissRequest) {
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            delay(30)
            focusRequester.tryRequestFocus()
        }
        val start = tabs.indexOfFirst { it.selected }.coerceAtLeast(0)
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.width(MoreWidth),
        ) {
            tabs.forEachIndexed { index, tab ->
                if (index > 0 && tabs[index - 1].isProfile) {
                    Box(
                        Modifier
                            .padding(horizontal = 6.dp, vertical = 6.dp)
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MenuLine),
                    )
                    Text(
                        text = stringResource(R.string.more_extras).uppercase(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        color = MenuMuted,
                        modifier = Modifier.padding(start = 12.dp, bottom = 2.dp),
                    )
                }
                MoreRow(
                    tab = tab,
                    onClick = {
                        onDismissRequest()
                        tab.onClick()
                    },
                    leading = {
                        if (tab.isProfile) {
                            UserIconCardImage(
                                id = userId,
                                name = userName,
                                imageUrl = userImageUrl,
                                modifier = Modifier.size(28.dp),
                            )
                        } else {
                            Text(
                                text = tab.glyph.orEmpty(),
                                fontFamily = FontAwesome,
                                fontSize = 16.sp,
                            )
                        }
                    },
                    modifier = Modifier.ifElse(index == start, Modifier.focusRequester(focusRequester)),
                )
            }
        }
    }
}

@Composable
private fun MoreRow(
    tab: TopNavTab,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = Color.White,
                focusedContainerColor = Color.White,
                focusedContentColor = Color.Black,
            ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(28.dp)) { leading() }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = tab.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    if (tab.selected) {
                        Box(
                            Modifier
                                .padding(start = 8.dp)
                                .size(6.dp)
                                .background(BenflixRed, CircleShape),
                        )
                    }
                }
                tab.hint?.let {
                    Text(
                        text = it,
                        fontSize = 12.sp,
                        color = LocalContentColor.current.copy(alpha = .6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** FontAwesome icons for the extras in More */
object MoreGlyphs {
    const val SHUFFLE = ""
    const val LIST = ""
    const val SMILE = ""
    const val MUSIC = ""
}
