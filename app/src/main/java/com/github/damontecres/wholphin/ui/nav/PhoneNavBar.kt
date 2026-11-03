package com.github.damontecres.wholphin.ui.nav

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.services.GuestMode
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.setup.UserIconCardImage
import com.github.damontecres.wholphin.ui.theme.BenflixRed

/**
 * Benflix on phones: a slim header with the logo at the top of the screen
 */
@Composable
fun PhoneHeader(modifier: Modifier = Modifier) {
    val kids by KidsMode.active.collectAsState()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        BenflixLogo()
        if (kids) {
            Text(
                text = stringResource(R.string.kids_profile).uppercase(),
                color = Color.Black,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                modifier =
                    Modifier
                        .padding(start = 8.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

/**
 * Benflix on phones: the menu sits along the bottom of the screen where your thumb is.
 * Home, Search, Clips (a feed of trailers), Channels (a TV guide), and My Benflix, which holds your profile,
 * My List, the libraries, Games and settings.
 */
@Composable
fun PhoneNavBar(
    tabs: List<TopNavTab>,
    clipsSelected: Boolean,
    onClickClips: () -> Unit,
    channelsSelected: Boolean,
    onClickChannels: () -> Unit,
    user: JellyfinUser,
    userImageUrl: String?,
    searchSelected: Boolean,
    onClickSearch: () -> Unit,
    onClickSettings: () -> Unit,
    onClickProfile: () -> Unit,
    onClickDownloads: () -> Unit,
    modifier: Modifier = Modifier,
    extras: List<TopNavTab> = emptyList(),
    compact: Boolean = false,
) {
    val kids by KidsMode.active.collectAsState()
    val home = tabs.firstOrNull()
    // Games and every library live under My Benflix
    val libraries = tabs.filter { it !== home }
    var showMine by remember { mutableStateOf(false) }
    var showRemote by remember { mutableStateOf(false) }
    if (showRemote) {
        com.github.damontecres.wholphin.ui.components
            .PhoneRemoteDialog(onDismiss = { showRemote = false })
    }
    val name =
        if (kids) {
            KidsMode.displayName
        } else if (GuestMode.isActive) {
            "Guest"
        } else {
            user.name
        }

    // Benflix floating island: a rounded bar that floats a little above the bottom edge.
    // It shrinks to icons only while you scroll down, and the current tab sits on a soft pill.
    val barHeight by animateDpAsState(if (compact) BAR_HEIGHT_COMPACT else BAR_HEIGHT, label = "barHeight")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .height(barHeight)
                .shadow(10.dp, ISLAND_SHAPE)
                .background(ISLAND, ISLAND_SHAPE)
                .padding(horizontal = 4.dp),
    ) {
        home?.let {
            PhoneNavItem(R.string.fa_house, it.title, it.selected && !searchSelected, compact, it.onClick)
        }
        PhoneNavItem(R.string.fa_magnifying_glass, stringResource(R.string.search), searchSelected, compact, onClickSearch)
        PhoneNavItem(R.string.fa_circle_play, stringResource(R.string.trailer_shuffle), clipsSelected, compact, onClickClips)
        PhoneNavItem(R.string.fa_tv, stringResource(R.string.channels), channelsSelected, compact, onClickChannels)
        val mineSelected = libraries.any { it.selected } || extras.any { it.selected }
        val updateAlert =
            com.github.damontecres.wholphin.ui.components
                .rememberUpdateAlert()
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier =
                Modifier
                    .weight(1f)
                    .clickable { showMine = true }
                    .padding(vertical = 4.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(width = 56.dp, height = 32.dp)
                        .background(if (mineSelected) BenflixRed.copy(alpha = PILL_ALPHA) else Color.Transparent, PILL_SHAPE),
            ) {
                UserIconCardImage(
                    id = user.id,
                    name = name,
                    imageUrl = if (kids) null else userImageUrl,
                    modifier = Modifier.size(24.dp),
                )
                // Benflix: a red "!" when an update is ready (it's under My Benflix > Settings > Help)
                val serverAlert =
                    com.github.damontecres.wholphin.ui.components
                        .rememberServerAlert()
                if (updateAlert || serverAlert) {
                    com.github.damontecres.wholphin.ui.components.UpdateBang(
                        size = 14.dp,
                        modifier = Modifier.align(Alignment.TopEnd),
                    )
                }
                if (updateAlert) {
                    com.github.damontecres.wholphin.ui.components.UpdateTip(
                        text = stringResource(R.string.benflix_update_tip_phone),
                        above = true,
                    )
                }
                // Benflix: the admin's alert on Home (new problem report, disk nearly full)
                if (serverAlert) {
                    com.github.damontecres.wholphin.ui.components.ServerAlertTip(
                        where = "Tap My Benflix, then Settings, then Server.",
                        above = true,
                    )
                }
            }
            if (!compact) {
                Text(
                    text = stringResource(R.string.phone_my_benflix),
                    fontSize = 11.sp,
                    color = if (mineSelected) Color.White else UNSELECTED,
                    fontWeight = if (mineSelected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }

    if (showMine) {
        MyBenflixSheet(
            name = name.orEmpty(),
            user = user,
            userImageUrl = if (kids) null else userImageUrl,
            kids = kids,
            libraries = libraries,
            extras = extras,
            onDismiss = { showMine = false },
            onClickProfile = onClickProfile,
            onClickSettings = onClickSettings,
            onClickDownloads = onClickDownloads,
            onClickRemote = { showRemote = true },
        )
    }
}

/**
 * The My Benflix menu on phones: a sheet from the bottom that fits on one screen.
 * Your name (tap to switch profile) and Settings, three big tiles (My page, Downloads, TV remote),
 * then every library and the extras as small round buttons.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MyBenflixSheet(
    name: String,
    user: JellyfinUser,
    userImageUrl: String?,
    kids: Boolean,
    libraries: List<TopNavTab>,
    extras: List<TopNavTab>,
    onDismiss: () -> Unit,
    onClickProfile: () -> Unit,
    onClickSettings: () -> Unit,
    onClickDownloads: () -> Unit,
    onClickRemote: () -> Unit,
) {
    fun go(action: () -> Unit) {
        onDismiss()
        action()
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            contentAlignment = Alignment.BottomCenter,
            modifier =
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .widthIn(max = 520.dp)
                        .background(SHEET, RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                        .border(1.dp, SHEET_LINE, RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                        // Stops taps on the sheet itself from closing it
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ).verticalScroll(rememberScrollState())
                        .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 24.dp),
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 40.dp, height = 4.dp)
                        .background(Color(0xFF4A4A52), RoundedCornerShape(2.dp)),
                )
                // Who's watching, plus Settings
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier =
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { go(onClickProfile) },
                    ) {
                        UserIconCardImage(
                            id = user.id,
                            name = name,
                            imageUrl = userImageUrl,
                            modifier = Modifier.size(48.dp),
                        )
                        Column {
                            Text(text = name, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                            Text(
                                text = stringResource(R.string.phone_switch_profile),
                                fontSize = 14.sp,
                                color = MUTED,
                                maxLines = 1,
                            )
                        }
                    }
                    if (!kids) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier =
                                Modifier
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(22.dp))
                                    .border(1.dp, Color(0xFF3A3A40), RoundedCornerShape(22.dp))
                                    .clickable { go(onClickSettings) }
                                    .padding(horizontal = 16.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = stringResource(R.string.settings), fontSize = 14.sp, color = Color.White)
                                // Update ready: the "!" leads on to Help inside Settings
                                if (com.github.damontecres.wholphin.ui.components
                                        .rememberUpdateAlert()
                                ) {
                                    Spacer(Modifier.width(8.dp))
                                    com.github.damontecres.wholphin.ui.components
                                        .UpdateBang()
                                }
                            }
                        }
                    }
                }
                // Big tiles
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    extras.firstOrNull()?.let { tab ->
                        SheetTile(R.string.fa_user, tab.title, tab.selected) { go(tab.onClick) }
                    }
                    SheetTile(R.string.fa_download, stringResource(R.string.downloads_title), false) {
                        go(onClickDownloads)
                    }
                    if (!kids) {
                        SheetTile(R.string.fa_mobile_screen, "TV remote", false) { go(onClickRemote) }
                    }
                }
                if (libraries.isNotEmpty()) {
                    SectionTitle(stringResource(R.string.phone_browse))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        libraries.forEach { tab ->
                            SheetChip(tab.title, tab.selected, filled = true) { go(tab.onClick) }
                        }
                    }
                }
                if (extras.size > 1) {
                    SectionTitle(stringResource(R.string.phone_extras))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        extras.drop(1).forEach { tab ->
                            SheetChip(tab.title, tab.selected, filled = false) { go(tab.onClick) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.SheetTile(
    faIcon: Int,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        modifier =
            Modifier
                .weight(1f)
                .height(84.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (selected) Color(0xFF34343B) else TILE)
                .clickable(onClick = onClick)
                .padding(4.dp),
    ) {
        Text(text = stringResource(faIcon), fontFamily = FontAwesome, fontSize = 22.sp, color = Color.White)
        Text(
            text = label,
            fontSize = 14.sp,
            color = Color.White,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SheetChip(
    title: String,
    selected: Boolean,
    filled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .height(40.dp)
                .clip(shape)
                .then(
                    when {
                        selected -> Modifier.background(Color.White, shape)
                        filled -> Modifier.background(CHIP, shape)
                        else -> Modifier.border(1.dp, Color(0xFF3A3A40), shape)
                    },
                ).clickable(onClick = onClick)
                .padding(horizontal = 16.dp),
    ) {
        Text(
            text = title,
            fontSize = 15.sp,
            color = if (selected) Color.Black else Color.White,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.PhoneNavItem(
    faIcon: Int,
    label: String,
    selected: Boolean,
    compact: Boolean,
    onClick: () -> Unit,
) {
    val color = if (selected) Color.White else UNSELECTED
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier =
            Modifier
                .weight(1f)
                .clickable(onClick = onClick)
                .padding(vertical = 4.dp),
    ) {
        // The tab you're on sits on a soft pill in your profile colour
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(width = 56.dp, height = 32.dp)
                    .background(if (selected) BenflixRed.copy(alpha = PILL_ALPHA) else Color.Transparent, PILL_SHAPE),
        ) {
            Text(
                text = stringResource(faIcon),
                fontFamily = FontAwesome,
                fontSize = 20.sp,
                color = color,
            )
        }
        if (!compact) {
            Text(
                text = label,
                fontSize = 11.sp,
                color = color,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title.uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp,
        color = UNSELECTED,
    )
}

private val BAR_HEIGHT = 64.dp
private val BAR_HEIGHT_COMPACT = 48.dp
private val ISLAND = Color(0xF21C1C21)
private val ISLAND_SHAPE = RoundedCornerShape(28.dp)
private val PILL_SHAPE = RoundedCornerShape(16.dp)
private const val PILL_ALPHA = 0.38f

// Benflix Graphite: a clean neutral dark grey (no red tint)
private val SHEET = Color(0xFF18181C)
private val SHEET_LINE = Color(0xFF2E2E34)
private val TILE = Color(0xFF24242A)
private val CHIP = Color(0xFF2C2C33)
private val MUTED = Color(0xFFA1A1AA)
private val UNSELECTED = Color(0xFFB3B3B3)
