package com.github.damontecres.wholphin.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.setup.UserIconCardImage

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
    val name = if (kids) KidsMode.displayName else user.name

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .height(BAR_HEIGHT)
                .background(Color(0xF2141414)),
    ) {
        home?.let {
            PhoneNavItem(R.string.fa_house, it.title, it.selected && !searchSelected, it.onClick)
        }
        PhoneNavItem(R.string.fa_magnifying_glass, stringResource(R.string.search), searchSelected, onClickSearch)
        PhoneNavItem(R.string.fa_circle_play, stringResource(R.string.clips_title), clipsSelected, onClickClips)
        PhoneNavItem(R.string.fa_tv, stringResource(R.string.channels), channelsSelected, onClickChannels)
        val mineSelected = libraries.any { it.selected } || extras.any { it.selected }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier =
                Modifier
                    .weight(1f)
                    .clickable { showMine = true }
                    .padding(vertical = 6.dp),
        ) {
            UserIconCardImage(
                id = user.id,
                name = name,
                imageUrl = if (kids) null else userImageUrl,
                modifier =
                    Modifier
                        .size(26.dp)
                        .then(
                            if (mineSelected) {
                                Modifier.border(2.dp, Color.White, RoundedCornerShape(4.dp))
                            } else {
                                Modifier
                            },
                        ),
            )
            Text(
                text = stringResource(R.string.phone_my_benflix),
                fontSize = 12.sp,
                color = if (mineSelected) Color.White else UNSELECTED,
                fontWeight = if (mineSelected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
            )
        }
    }

    if (showMine) {
        BasicDialog(onDismissRequest = { showMine = false }) {
            LazyColumn(modifier = Modifier.padding(vertical = 8.dp)) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showMine = false
                                    onClickProfile.invoke()
                                }.padding(horizontal = 24.dp, vertical = 12.dp),
                    ) {
                        UserIconCardImage(
                            id = user.id,
                            name = name,
                            imageUrl = if (kids) null else userImageUrl,
                            modifier = Modifier.size(40.dp),
                        )
                        Column {
                            Text(text = name.orEmpty(), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(
                                text = stringResource(R.string.phone_switch_profile),
                                fontSize = 14.sp,
                                color = UNSELECTED,
                            )
                        }
                    }
                }
                // My page (Continue watching, My List, Liked, Server) first, then Downloads
                extras.firstOrNull()?.let { tab ->
                    item {
                        BrowseRow(tab.title, tab.selected) {
                            showMine = false
                            tab.onClick.invoke()
                        }
                    }
                }
                item {
                    BrowseRow(stringResource(R.string.downloads_title), false) {
                        showMine = false
                        onClickDownloads.invoke()
                    }
                }
                // Benflix: use the phone as the TV remote
                if (!kids) {
                    item {
                        BrowseRow("TV remote", false) {
                            showMine = false
                            showRemote = true
                        }
                    }
                }
                item { SectionTitle(stringResource(R.string.phone_browse)) }
                items(libraries) { tab ->
                    BrowseRow(tab.title, tab.selected) {
                        showMine = false
                        tab.onClick.invoke()
                    }
                }
                if (extras.size > 1) {
                    item { SectionTitle(stringResource(R.string.phone_extras)) }
                    items(extras.drop(1)) { tab ->
                        BrowseRow(tab.title, tab.selected) {
                            showMine = false
                            tab.onClick.invoke()
                        }
                    }
                }
                if (!kids) {
                    item {
                        BrowseRow(stringResource(R.string.settings), false) {
                            showMine = false
                            onClickSettings.invoke()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.PhoneNavItem(
    faIcon: Int,
    label: String,
    selected: Boolean,
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
                .padding(vertical = 6.dp),
    ) {
        Text(
            text = stringResource(faIcon),
            fontFamily = FontAwesome,
            fontSize = 24.sp,
            color = color,
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = color,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

@Composable
private fun BrowseRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 24.dp, vertical = 14.dp),
    ) {
        Text(
            text = title,
            fontSize = 18.sp,
            color = if (selected) Color.White else UNSELECTED,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
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
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 2.dp),
    )
}

private val BAR_HEIGHT = 64.dp
private val UNSELECTED = Color(0xFFB3B3B3)
