package com.github.damontecres.wholphin.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.components.PhoneRemoteDialog
import com.github.damontecres.wholphin.ui.components.UpdateBang
import com.github.damontecres.wholphin.ui.components.rememberUpdateAlert
import com.github.damontecres.wholphin.ui.nav.TopNavTab
import com.github.damontecres.wholphin.ui.setup.UserIconCardImage
import com.github.damontecres.wholphin.ui.theme.BenflixRed
import java.util.UUID

/**
 * Benflix on phones: what the My Benflix page needs from the bottom menu
 * (every library, the extras, and where Downloads, Settings and Switch profile go).
 * Only set on phones, so the TV page stays as it is.
 */
data class PhoneMenu(
    val libraries: List<TopNavTab>,
    val extras: List<TopNavTab>,
    val onClickDownloads: () -> Unit,
    val onClickSettings: () -> Unit,
    val onClickProfile: () -> Unit,
)

val LocalPhoneMenu = compositionLocalOf<PhoneMenu?> { null }

/**
 * The top of My Benflix on phones (it's a page of its own, no pop-up):
 * who's watching with Settings, round buttons (Downloads, TV remote, Parents, Quick Connect),
 * then every library and the extras as same-size icon tiles, four to a row.
 */
@Composable
fun PhoneMyBenflixHeader(
    menu: PhoneMenu,
    userId: UUID?,
    name: String,
    userImageUrl: String?,
    kids: Boolean,
    guest: Boolean,
    onClickParents: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showRemote by remember { mutableStateOf(false) }
    if (showRemote) PhoneRemoteDialog(onDismiss = { showRemote = false })
    var showQuickConnect by remember { mutableStateOf(false) }
    if (showQuickConnect) {
        com.github.damontecres.wholphin.ui.preferences
            .BenflixQuickConnect(onDismiss = { showQuickConnect = false })
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = modifier.fillMaxWidth().padding(top = 8.dp, end = 8.dp),
    ) {
        // Who's watching (tap to switch), plus a Settings cog
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier =
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = menu.onClickProfile),
            ) {
                userId?.let {
                    UserIconCardImage(id = it, name = name, imageUrl = userImageUrl, modifier = Modifier.size(52.dp))
                }
                Column {
                    Text(
                        text = name,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = stringResource(R.string.phone_switch_profile), fontSize = 13.sp, color = MUTED)
                        Spacer(Modifier.width(5.dp))
                        Text(text = stringResource(R.string.fa_chevron_down), fontFamily = FontAwesome, fontSize = 9.sp, color = MUTED)
                    }
                }
            }
            if (!kids) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(TILE)
                            .clickable(onClick = menu.onClickSettings),
                ) {
                    Text(text = stringResource(R.string.fa_gear), fontFamily = FontAwesome, fontSize = 19.sp, color = Color.White)
                    // Update ready: the "!" leads on to Help inside Settings
                    if (rememberUpdateAlert()) {
                        UpdateBang(size = 14.dp, modifier = Modifier.align(Alignment.TopEnd))
                    }
                }
            }
        }
        // Round buttons
        Row {
            RoundButton(R.string.fa_download, stringResource(R.string.downloads_title), menu.onClickDownloads)
            if (!kids) {
                RoundButton(R.string.fa_mobile_screen, "TV remote") { showRemote = true }
            }
            if (!kids && !guest) {
                RoundButton(R.string.fa_user_shield, "Parents", onClickParents)
            }
            // Sign another TV or phone in with the code it shows
            if (!kids) {
                RoundButton(R.string.fa_link, stringResource(R.string.quick_connect)) { showQuickConnect = true }
            }
            // Keeps the buttons the same size as with four
            repeat(4 - listOf(true, !kids, !kids && !guest, !kids).count { it }) { Spacer(Modifier.weight(1f)) }
        }
        if (menu.libraries.isNotEmpty()) {
            TileGrid(stringResource(R.string.phone_browse), menu.libraries)
        }
        if (menu.extras.isNotEmpty()) {
            TileGrid(stringResource(R.string.phone_extras), menu.extras)
        }
    }
}

@Composable
private fun RowScope.RoundButton(
    faIcon: Int,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier =
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .padding(vertical = 2.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(54.dp).background(TILE, CircleShape),
        ) {
            Text(text = stringResource(faIcon), fontFamily = FontAwesome, fontSize = 21.sp, color = Color.White)
        }
        Text(text = label, fontSize = 12.sp, color = Color(0xFFDDDDDD), maxLines = 1)
    }
}

/** A heading and same-size icon tiles, four to a row */
@Composable
private fun TileGrid(
    title: String,
    tabs: List<TopNavTab>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title.uppercase(),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            color = MUTED,
        )
        tabs.chunked(COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { tab -> IconTile(tab) }
                repeat(COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun RowScope.IconTile(tab: TopNavTab) {
    val shape = RoundedCornerShape(14.dp)
    // "Requests (2)" shows as Requests with a red 2
    val count = Regex("""\s\((\d+)\)$""").find(tab.title)
    val label = if (count != null) tab.title.removeRange(count.range) else tab.title
    Box(
        modifier =
            Modifier
                .weight(1f)
                .aspectRatio(1f / 0.92f)
                .clip(shape)
                .background(if (tab.selected) TILE_SELECTED else TILE, shape)
                .clickable(onClick = tab.onClick),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 4.dp),
        ) {
            Text(
                text = stringResource(tabGlyph(label)),
                fontFamily = FontAwesome,
                fontSize = 20.sp,
                color = Color.White,
            )
            Text(
                text = label,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                color = Color.White,
                fontWeight = if (tab.selected) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        count?.let {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .size(18.dp)
                        .background(BenflixRed, CircleShape),
            ) {
                Text(text = it.groupValues[1], fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

/** An icon for each library or extra, from its name */
private fun tabGlyph(title: String): Int {
    val t = title.lowercase()
    return when {
        "anime" in t && "movie" in t -> R.string.fa_wand_sparkles
        "anime" in t -> R.string.fa_dragon
        "my list" in t || "favorite" in t || "favourite" in t -> R.string.fa_bookmark
        "stand" in t || "comedy" in t -> R.string.fa_microphone
        "music" in t -> R.string.fa_music
        "game" in t -> R.string.fa_gamepad
        "request" in t -> R.string.fa_circle_plus
        "mood" in t -> R.string.fa_face_smile
        "this or that" in t -> R.string.fa_scale_balanced
        "order" in t -> R.string.fa_list_ol
        "movie" in t || "film" in t -> R.string.fa_film
        "show" in t || "tv" in t || "series" in t -> R.string.fa_tv
        "playing" in t -> R.string.fa_circle_play
        "photo" in t || "picture" in t -> R.string.fa_images
        "book" in t -> R.string.fa_bookmark
        "video" in t -> R.string.fa_video
        else -> R.string.fa_open_folder
    }
}

private const val COLUMNS = 4
private val TILE = Color(0xFF24242A)
private val TILE_SELECTED = Color(0xFF3A3A42)
private val MUTED = Color(0xFFA1A1AA)
