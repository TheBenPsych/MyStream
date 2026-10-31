package com.github.damontecres.wholphin.ui.nav

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.ui.ifElse
import com.github.damontecres.wholphin.ui.setup.UserIconCardImage
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.ui.util.LocalClock

/**
 * One entry in the [TopNavBar]
 */
data class TopNavTab(
    val title: String,
    val selected: Boolean,
    val onClick: () -> Unit,
)

/**
 * A streaming-service style navigation bar across the top of the screen: brand name, page tabs, search,
 * settings, the clock, and the current profile
 */
@Composable
fun TopNavBar(
    tabs: List<TopNavTab>,
    user: JellyfinUser,
    userImageUrl: String?,
    showClock: Boolean,
    searchSelected: Boolean,
    onClickSearch: () -> Unit,
    onClickSettings: () -> Unit,
    onClickProfile: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val selectedIndex = tabs.indexOfFirst { it.selected }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .height(TopNavBarHeight)
                .padding(horizontal = 40.dp)
                .focusGroup()
                .focusProperties {
                    // Entering the bar from the page lands on the current tab
                    onEnter = { focusRequester.tryRequestFocus() }
                },
    ) {
        Text(
            text = stringResource(R.string.app_name).uppercase(),
            color = MaterialTheme.colorScheme.primary,
            fontSize = 26.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(end = 16.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            contentPadding = PaddingValues(horizontal = 4.dp),
            modifier = Modifier.weight(1f),
        ) {
            itemsIndexed(tabs) { index, tab ->
                TopNavTabButton(
                    tab = tab,
                    modifier =
                        Modifier.ifElse(
                            index == selectedIndex || (selectedIndex < 0 && index == 0),
                            Modifier.focusRequester(focusRequester),
                        ),
                )
            }
        }
        TopNavIconButton(
            icon = Icons.Default.Search,
            contentDescription = stringResource(R.string.search),
            selected = searchSelected,
            onClick = onClickSearch,
        )
        TopNavIconButton(
            icon = Icons.Default.Settings,
            contentDescription = stringResource(R.string.settings),
            selected = false,
            onClick = onClickSettings,
        )
        if (showClock) {
            val timeString by LocalClock.current.timeString
            Text(
                text = timeString,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        IconButton(
            onClick = onClickProfile,
            colors =
                IconButtonDefaults.colors(
                    containerColor = Color.Transparent,
                    focusedContainerColor = Color.White,
                ),
            modifier = Modifier.size(40.dp),
        ) {
            UserIconCardImage(
                id = user.id,
                name = user.name,
                imageUrl = userImageUrl,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

@Composable
private fun TopNavTabButton(
    tab: TopNavTab,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    Button(
        onClick = tab.onClick,
        interactionSource = interactionSource,
        shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
        colors =
            ButtonDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = if (tab.selected) Color.White else Color(0xFFB3B3B8),
                focusedContainerColor = Color.White,
                focusedContentColor = Color.Black,
            ),
        modifier = modifier.height(36.dp),
    ) {
        Text(
            text = tab.title,
            fontSize = 15.sp,
            fontWeight = if (tab.selected || focused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun TopNavIconButton(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        colors =
            IconButtonDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = if (selected) Color.White else Color(0xFFB3B3B8),
                focusedContainerColor = Color.White,
                focusedContentColor = Color.Black,
            ),
        modifier = modifier.size(40.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * Builds the tab name for a nav drawer item, using "My List" for favorites
 */
@Composable
fun topNavTitle(item: NavDrawerItem): String =
    if (item is NavDrawerItem.Favorites) {
        stringResource(R.string.my_list)
    } else {
        item.name(LocalContext.current)
    }

val TopNavBarHeight = 64.dp
