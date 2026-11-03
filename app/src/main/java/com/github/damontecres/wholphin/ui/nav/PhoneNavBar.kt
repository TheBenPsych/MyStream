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
 * Home, Search, Clips (a feed of trailers), Channels (a TV guide), and My Benflix, a page with your profile,
 * every library, Games, the extras and your rows (My List, Liked, history).
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
                    .clickable { extras.firstOrNull()?.onClick?.invoke() }
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

private val BAR_HEIGHT = 64.dp
private val BAR_HEIGHT_COMPACT = 48.dp
private val ISLAND = Color(0xF21C1C21)
private val ISLAND_SHAPE = RoundedCornerShape(28.dp)
private val PILL_SHAPE = RoundedCornerShape(16.dp)
private const val PILL_ALPHA = 0.38f

private val UNSELECTED = Color(0xFFB3B3B3)
