package com.github.damontecres.wholphin.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.components.BenflixIconImage
import com.github.damontecres.wholphin.ui.preferences.GlowfinAdminYellow
import com.github.damontecres.wholphin.ui.setup.UserIconCardImage
import com.github.damontecres.wholphin.ui.theme.Glowfin
import com.github.damontecres.wholphin.ui.theme.GlowfinDisplayFont
import com.github.damontecres.wholphin.ui.theme.glowFocus
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import kotlinx.coroutines.delay

/** What the Me page shows: handed down from the menu, which owns the profile's lines and pop-ups */
class MeModel(
    val user: JellyfinUser,
    val userImageUrl: String?,
    val minutesWatched: Long?,
    val items: List<ProfileMenuItem>,
    val onSwitchProfile: () -> Unit,
)

val LocalMeModel = compositionLocalOf<MeModel?> { null }

/**
 * Ben 2026-11-18: picking your picture on the menu always lands on the first tile (My watching), even when the
 * Me page is already open. Each pick bumps this, and the page puts the focus back on My watching.
 */
object MeFocus {
    var picks by mutableIntStateOf(0)
}

/**
 * Ben 2026-11-18 (approved profile-page-1): your picture opens a full page, like every other menu spot.
 * Your picture, name, this month's hours and Switch profile at the top, then big tiles (3 across on TVs and wide
 * screens, one column on phones), and Sign out quietly at the bottom.
 */
@Composable
fun MePage(modifier: Modifier = Modifier) {
    val model = LocalMeModel.current ?: return
    val tv = rememberIsTv()
    val wide = tv || androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 600
    val tiles = model.items.filter { !it.apart }
    val apart = model.items.filter { it.apart }
    val first = remember { FocusRequester() }
    var firstFocused by remember { mutableStateOf(false) }
    // Ben 2026-11-18: land on My watching (the menu tucks away as the focus leaves it). Weak TVs can take a moment
    // to draw the page, and the focus can fall back onto the menu while it does, so keep trying for a couple of seconds.
    LaunchedEffect(MeFocus.picks) {
        if (!tv) return@LaunchedEffect
        delay(60)
        repeat(FOCUS_TRIES) {
            if (firstFocused) return@LaunchedEffect
            first.tryRequestFocus()
            delay(FOCUS_RETRY_MS)
        }
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(if (wide) 14.dp else 10.dp),
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = if (wide) 32.dp else 0.dp,
                    end = if (wide) 40.dp else 8.dp,
                    top = if (wide) 8.dp else 12.dp,
                    bottom = 32.dp,
                ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (wide) 24.dp else 16.dp),
            modifier = Modifier.padding(bottom = if (wide) 14.dp else 6.dp),
        ) {
            UserIconCardImage(
                id = model.user.id,
                name = model.user.name,
                imageUrl = model.userImageUrl,
                modifier = Modifier.size(if (wide) 104.dp else 68.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = model.user.name.orEmpty(),
                    color = Glowfin.Text,
                    fontFamily = GlowfinDisplayFont,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = if (wide) 34.sp else 24.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                model.minutesWatched?.let {
                    Text(watchedWords(it), color = Glowfin.Dim, fontSize = if (wide) 15.sp else 13.sp, maxLines = 1)
                }
                Spacer(Modifier.height(2.dp))
                MePill(text = "Switch profile", icon = BenflixIcon.SWITCH, onClick = model.onSwitchProfile)
            }
        }
        val perRow = if (wide) 3 else 1
        tiles.chunked(perRow).forEachIndexed { r, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEachIndexed { c, item ->
                    MeTile(
                        item = item,
                        tall = wide,
                        modifier =
                            Modifier
                                .weight(1f)
                                .then(
                                    if (r == 0 && c == 0) {
                                        Modifier
                                            .focusRequester(first)
                                            .onFocusChanged { firstFocused = it.isFocused || it.hasFocus }
                                    } else {
                                        Modifier
                                    },
                                ),
                    )
                }
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        apart.forEach { item ->
            MePill(text = item.title, icon = item.icon, onClick = item.onClick, quiet = true)
        }
    }
}

/** One spot on the Me page: icon, name, a short line, and a pill like "Update ready" */
@Composable
private fun MeTile(
    item: ProfileMenuItem,
    tall: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    val admin = item.title == "Server"
    Surface(
        onClick = item.onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Glowfin.Deep,
                contentColor = Glowfin.Text,
                focusedContainerColor = Glowfin.FocusRow,
                focusedContentColor = Glowfin.Text,
                pressedContainerColor = Glowfin.FocusRow,
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier =
            modifier
                .glowFocus(shape)
                .heightIn(min = if (tall) 92.dp else 64.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = if (tall) Alignment.Top else Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(40.dp).background(Glowfin.Raise, RoundedCornerShape(12.dp)),
            ) {
                BenflixIconImage(item.icon, color = if (admin) GlowfinAdminYellow else Glowfin.Text, size = 22.dp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = item.title,
                    color = if (admin) GlowfinAdminYellow else Glowfin.Text,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                item.line?.let {
                    Text(it, color = Glowfin.Dim, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                item.note?.let {
                    Text(
                        text = it,
                        color = Glowfin.Ink,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        modifier =
                            Modifier
                                .padding(top = 4.dp)
                                .background(Glowfin.Aqua, RoundedCornerShape(50))
                                .padding(horizontal = 8.dp, vertical = 1.dp),
                    )
                }
            }
        }
    }
}

/** Switch profile, and Sign out (quiet) */
@Composable
private fun MePill(
    text: String,
    icon: BenflixIcon,
    onClick: () -> Unit,
    quiet: Boolean = false,
) {
    val shape = RoundedCornerShape(50)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = if (quiet) Color.Transparent else Glowfin.Raise,
                contentColor = if (quiet) Glowfin.Dim else Glowfin.Text,
                focusedContainerColor = Glowfin.FocusRow,
                focusedContentColor = Glowfin.Text,
                pressedContainerColor = Glowfin.FocusRow,
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = Modifier.glowFocus(shape, spread = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
        ) {
            BenflixIconImage(icon, color = if (quiet) Glowfin.Dim else Glowfin.Text, size = 16.dp)
            Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

private const val FOCUS_TRIES = 15
private const val FOCUS_RETRY_MS = 150L
