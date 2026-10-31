package com.github.damontecres.wholphin.ui.nav

import androidx.compose.foundation.background
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
 * Home, Search, Browse (your libraries + settings), Games, and your profile.
 */
@Composable
fun PhoneNavBar(
    tabs: List<TopNavTab>,
    gamesTitle: String,
    user: JellyfinUser,
    userImageUrl: String?,
    searchSelected: Boolean,
    onClickSearch: () -> Unit,
    onClickSettings: () -> Unit,
    onClickProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val kids by KidsMode.active.collectAsState()
    val home = tabs.firstOrNull()
    val games = tabs.firstOrNull { it.title == gamesTitle }
    val libraries = tabs.filter { it !== home && it !== games }
    var showBrowse by remember { mutableStateOf(false) }

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
        PhoneNavItem(
            R.string.fa_bars,
            stringResource(R.string.phone_browse),
            libraries.any { it.selected },
        ) { showBrowse = true }
        games?.let {
            PhoneNavItem(R.string.fa_gamepad, it.title, it.selected, it.onClick)
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier =
                Modifier
                    .weight(1f)
                    .clickable(onClick = onClickProfile)
                    .padding(vertical = 6.dp),
        ) {
            UserIconCardImage(
                id = user.id,
                name = if (kids) "Kids" else user.name,
                imageUrl = if (kids) null else userImageUrl,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = stringResource(R.string.phone_profiles),
                fontSize = 11.sp,
                color = UNSELECTED,
                maxLines = 1,
            )
        }
    }

    if (showBrowse) {
        BasicDialog(onDismissRequest = { showBrowse = false }) {
            LazyColumn(modifier = Modifier.padding(vertical = 8.dp)) {
                items(libraries) { tab ->
                    BrowseRow(tab.title, tab.selected) {
                        showBrowse = false
                        tab.onClick.invoke()
                    }
                }
                if (!kids) {
                    item {
                        BrowseRow(stringResource(R.string.settings), false) {
                            showBrowse = false
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
            fontSize = 20.sp,
            color = color,
        )
        Text(
            text = label,
            fontSize = 11.sp,
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

private val BAR_HEIGHT = 60.dp
private val UNSELECTED = Color(0xFFB3B3B3)
