package com.github.damontecres.wholphin.ui.preferences

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixShapes
import com.github.damontecres.wholphin.ui.theme.Glowfin
import com.github.damontecres.wholphin.ui.theme.GlowfinDisplayFont
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus

/*
 * Glowfin Settings (Ben's pick S1, Nov 2026, new look 2026-11-23): the same look everywhere in Settings,
 * Help and the Server page. A setting shows its current choice in aqua on the right, and the lit one adds one
 * line explaining it. Choices open in a small list on the right that saves straight away. Nothing grows,
 * so it stays smooth on the weak TCL TVs.
 */

/** Behind the scenes is Ben's only, so its name is yellow */
val GlowfinAdminYellow: Color get() = BenflixColors.Check

private val BarWidth = 3.dp

/*
 * Glowfin new look (Ben approved 2026-11-22, Settings part): groups are see-through pills on the left
 * (the open one solid white), and the settings sit in a tall right-hand panel over a dimmed picture.
 * The setting you're on is solid white with the aqua ring, like every other focus in the app.
 * Nothing grows and nothing blurs, so it stays smooth on the weak TCL TVs.
 */
private val PillShape = RoundedCornerShape(99.dp)
private val CardShape = RoundedCornerShape(9.dp)

/** The see-through fill and edge of a pill or setting that isn't lit */
internal val GlowfinGlass = Color.White.copy(alpha = .06f)
private val GlassEdge = Color.White.copy(alpha = .08f)

/** The soft aqua ring around whatever the remote is on */
internal val GlowfinRing get() = Glowfin.Aqua.copy(alpha = .6f)

/** Words on a solid white pill or row */
internal val OnWhiteText get() = Glowfin.Ink
private val OnWhiteValue = Color(0xFF0B7D86)
private val OnWhiteDim = Color(0xFF4A4F72)
private val OnWhiteYellow = Color(0xFF8A5A00)

/** How see-through the page is over the last picture (Settings, Help, Server): the picture shows at about 28% */
private const val DIM_PAGE_ALPHA = .72f

/** The page behind Settings, Help and the Server page on TV: the last picture, dimmed (no blur) */
fun Modifier.glowfinDimmedPage(): Modifier = this.background(Glowfin.Ink.copy(alpha = DIM_PAGE_ALPHA))

/** The tall right-hand panel the settings slide into (like the story panel) */
@Composable
fun GlowfinRightPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .fillMaxHeight()
            .background(Glowfin.Deep.copy(alpha = .94f))
            .drawBehind { drawRect(Glowfin.Line, size = Size(1.dp.toPx(), size.height)) }
            .padding(start = 20.dp, end = 20.dp, top = 8.dp),
    ) {
        content()
    }
}

/** Draws the aqua bar down the row's left edge */
private fun Modifier.aquaBar(on: Boolean): Modifier =
    if (!on) {
        this
    } else {
        drawBehind { drawRect(Glowfin.Aqua, size = Size(BarWidth.toPx(), size.height)) }
    }

/** A big heading at the top of a list or a page ("Settings", "Watching", "Server") */
@Composable
fun GlowfinPageHeading(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Glowfin.Text,
) {
    Text(
        text = text,
        color = color,
        fontFamily = GlowfinDisplayFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = if (rememberIsTv()) 26.sp else 24.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(start = 8.dp, top = 6.dp, bottom = 14.dp),
    )
}

/** The left list (TV): the group pills float straight over the dimmed picture, no strip behind them */
@Composable
fun GlowfinSideListPanel(
    modifier: Modifier = Modifier,
    width: Dp = 280.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .width(width)
            .fillMaxHeight()
            .padding(start = 14.dp, end = 4.dp, top = 20.dp, bottom = 12.dp),
    ) {
        content()
    }
}

/**
 * One row in a left list (a Settings group, a Help part, a Server part, a profile menu line).
 * [selected] is the page showing on the right.
 */
@Composable
fun GlowfinListRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    labelColor: Color = Glowfin.Text,
    note: String? = null,
    noteColor: Color = Glowfin.Aqua,
    supporting: String? = null,
    chevron: Boolean = false,
    onFocused: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    // The open group (and the one the remote is on) is a solid white pill with dark words
    val white = selected || focused
    val shape = if (supporting == null) PillShape else CardShape
    val wordColor =
        when {
            !white -> labelColor
            labelColor == GlowfinAdminYellow -> OnWhiteYellow
            else -> OnWhiteText
        }
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = if (selected) Color.White.copy(alpha = .92f) else GlowfinGlass,
                contentColor = wordColor,
                focusedContainerColor = Color.White,
                focusedContentColor = wordColor,
                pressedContainerColor = Color.White,
                pressedContentColor = wordColor,
            ),
        border =
            ClickableSurfaceDefaults.border(
                border = androidx.tv.material3.Border(androidx.compose.foundation.BorderStroke(1.dp, GlassEdge), shape = shape),
                focusedBorder = androidx.tv.material3.Border(androidx.compose.foundation.BorderStroke(3.dp, GlowfinRing), shape = shape),
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocused()
                },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 14.dp, top = 9.dp, bottom = 9.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = label,
                    color = wordColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                supporting?.let {
                    Text(
                        text = it,
                        color = if (white) OnWhiteDim else Glowfin.Dim,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            note?.let {
                Text(
                    text = it,
                    color = if (white) OnWhiteValue else noteColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            if (chevron) {
                Icon(
                    imageVector = BenflixIcon.CHEVRON.vector,
                    contentDescription = null,
                    tint = if (white) OnWhiteValue else Glowfin.Aqua,
                    modifier = Modifier.padding(start = 6.dp).size(18.dp),
                )
            }
        }
    }
}

/**
 * Some settings only have one line of words under their name. A short one ("On", "4.12", "None hidden")
 * is the current choice, so it goes on the right in aqua; a longer one explains the setting.
 */
internal fun splitSummary(summary: String?): Pair<String?, String?> {
    val s = summary?.trim()?.takeIf { it.isNotEmpty() } ?: return null to null
    return if (s.length <= VALUE_MAX_CHARS && !s.contains(". ")) s to null else null to s
}

private const val VALUE_MAX_CHARS = 26

/**
 * One setting: its name on the left, the current choice in aqua on the right. The one you're on is lifted,
 * gets the aqua bar and one line explaining it (phones always show the line, they have no "lit" row).
 */
@Composable
fun GlowfinSettingRow(
    title: String,
    value: String?,
    explain: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    valueColor: Color = Glowfin.Aqua,
    titleColor: Color = Glowfin.Text,
    enabled: Boolean = true,
) {
    val isTv = rememberIsTv()
    val focused by interactionSource.collectIsFocusedAsState()
    // The setting you're on is solid white with the aqua ring; the rest are see-through cards
    val white = focused && enabled
    val nameColor =
        when {
            !enabled -> Glowfin.Dim
            white -> OnWhiteText
            else -> titleColor
        }
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        enabled = enabled,
        shape = ClickableSurfaceDefaults.shape(CardShape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color.White.copy(alpha = .05f),
                contentColor = nameColor,
                focusedContainerColor = Color.White,
                focusedContentColor = nameColor,
                pressedContainerColor = Color.White,
                pressedContentColor = nameColor,
                disabledContainerColor = Color.White.copy(alpha = .03f),
                disabledContentColor = Glowfin.Dim,
            ),
        border =
            ClickableSurfaceDefaults.border(
                focusedBorder =
                    androidx.tv.material3.Border(
                        androidx.compose.foundation.BorderStroke(3.dp, GlowfinRing),
                        shape = CardShape,
                    ),
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        interactionSource = interactionSource,
        modifier = modifier.fillMaxWidth().padding(vertical = 3.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = nameColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (explain != null && (focused || !isTv)) {
                    Text(
                        text = explain,
                        color = if (white) OnWhiteDim else Glowfin.Dim,
                        fontSize = 13.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            value?.let {
                Text(
                    text = it,
                    color = if (white && valueColor == Glowfin.Aqua) OnWhiteValue else valueColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 16.dp).widthIn(max = 240.dp),
                )
            }
        }
    }
}

/**
 * The small list that opens on the right when you change a setting (like the player's Settings list).
 * Picking one saves it straight away and closes the list; Back closes it without changing anything.
 */
@Composable
fun GlowfinChoiceSheet(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
    subtitles: List<String?>? = null,
    footer: String = "Saves straight away.",
) {
    val isTv = rememberIsTv()
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
            val shape = BenflixShapes.Panel
            Column(
                modifier =
                    Modifier
                        .padding(if (isTv) 28.dp else 12.dp)
                        .fillMaxHeight()
                        .then(if (isTv) Modifier.width(380.dp) else Modifier.fillMaxWidth(.9f))
                        .background(Glowfin.Deep.copy(alpha = .97f), shape)
                        .border(1.dp, Glowfin.Line, shape)
                        .padding(16.dp),
            ) {
                Text(
                    text = title,
                    color = Glowfin.Text,
                    fontFamily = GlowfinDisplayFont,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 6.dp, top = 4.dp, bottom = 12.dp),
                )
                val start = selectedIndex.coerceIn(0, (options.size - 1).coerceAtLeast(0))
                val listState = rememberLazyListState(initialFirstVisibleItemIndex = start)
                val first = remember { FocusRequester() }
                LaunchedEffect(Unit) { first.tryRequestFocus() }
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) {
                    itemsIndexed(options) { index, option ->
                        ChoiceRow(
                            label = option,
                            subtitle = subtitles?.getOrNull(index),
                            picked = index == selectedIndex,
                            onClick = { onPick(index) },
                            modifier = if (index == start) Modifier.focusRequester(first) else Modifier,
                        )
                    }
                }
                Spacer(Modifier.size(8.dp))
                Text(
                    text = footer,
                    color = Glowfin.Dim,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun ChoiceRow(
    label: String,
    subtitle: String?,
    picked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val words = if (focused) OnWhiteText else Glowfin.Text
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(CardShape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = if (picked) GlowfinGlass else Color.Transparent,
                contentColor = words,
                focusedContainerColor = Color.White,
                focusedContentColor = words,
                pressedContainerColor = Color.White,
                pressedContentColor = words,
            ),
        border =
            ClickableSurfaceDefaults.border(
                focusedBorder =
                    androidx.tv.material3.Border(
                        androidx.compose.foundation.BorderStroke(3.dp, GlowfinRing),
                        shape = CardShape,
                    ),
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .onFocusChanged { focused = it.isFocused },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aquaBar(picked && !focused)
                    .padding(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = label,
                    color = words,
                    fontSize = 15.sp,
                    fontWeight = if (picked || focused) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                subtitle?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        color = if (focused) OnWhiteDim else Glowfin.Dim,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (picked) {
                Icon(
                    imageVector = BenflixIcon.CHECK.vector,
                    contentDescription = "Picked",
                    tint = if (focused) OnWhiteValue else Glowfin.Aqua,
                    modifier = Modifier.padding(start = 8.dp).size(18.dp),
                )
            }
        }
    }
}

/** A word button in the Glowfin look: the main one white with dark words, the rest dark; the one you're on gets the aqua ring */
@Composable
fun GlowfinTextButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
) {
    val shape = BenflixShapes.Card
    val container = if (primary) Glowfin.Text else Glowfin.Raise
    val content = if (primary) Glowfin.Ink else Glowfin.Text
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = container,
                contentColor = content,
                focusedContainerColor = if (primary) Glowfin.Text else Glowfin.FocusRow,
                focusedContentColor = content,
                pressedContainerColor = container,
                pressedContentColor = content,
            ),
        border =
            ClickableSurfaceDefaults.border(
                focusedBorder =
                    androidx.tv.material3.Border(
                        androidx.compose.foundation.BorderStroke(2.5.dp, Glowfin.Aqua),
                        shape = shape,
                    ),
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier,
    ) {
        Text(
            text = label,
            color = content,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
        )
    }
}
