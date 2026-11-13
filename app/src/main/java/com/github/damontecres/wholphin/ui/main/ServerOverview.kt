package com.github.damontecres.wholphin.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.services.TowerSetupCheck
import com.github.damontecres.wholphin.services.TowerSetupNeed
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.components.BenflixIconImage
import com.github.damontecres.wholphin.ui.components.ConfirmDialog
import com.github.damontecres.wholphin.ui.nav.focusRing
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixShapes
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.touch.rememberIsTv

/**
 * Redesign 2 (Server and Settings, approved sv2-glance + ss-p-tools): the Server page's tools, grouped by job.
 * They show as five tiles at the bottom of Overview and at the top of Tools; picking one shows its tools.
 */
enum class ServerToolGroup(
    val title: String,
    val line: String,
    val icon: BenflixIcon,
) {
    LIBRARY("Library", "Scan for new stuff, the library checkup and posters", BenflixIcon.FILM),
    DOWNLOADS("Downloads", "What's downloading, and stuck ones", BenflixIcon.DOWNLOAD),
    PEOPLE("People", "Messages, Benflix versions and Kids ratings", BenflixIcon.PERSON),
    FIXES("Playback fixes", "Tidy track names and check playback speed", BenflixIcon.PLAY),
    TOWER("Tower", "Setup command, apps, restart, chores, weather town", BenflixIcon.SERVER),
}

/**
 * Opens the Server page on a section (and a tool group): set just before going to the page, read once by it.
 * Settings' "Find a setting" uses it to land on a Server tool.
 */
object ServerPageJump {
    @Volatile
    var section: ServerSection? = null

    @Volatile
    var group: ServerToolGroup? = null

    fun take(): Pair<ServerSection?, ServerToolGroup?> {
        val out = section to group
        section = null
        group = null
        return out
    }
}

/** One line on the "Needs you" card, with its buttons right there (Yes/No, Find another, Go) */
internal data class NeedLine(
    val key: String,
    val text: String,
    val light: Light,
    val supporting: String? = null,
    val poster: String? = null,
    val actions: List<Pair<String, () -> Unit>> = emptyList(),
)

internal fun lightColor(light: Light): Color =
    when (light) {
        Light.GOOD -> BenflixColors.Ok
        Light.WARN -> BenflixColors.Check
        Light.BAD -> BenflixColors.Problem
        Light.UNKNOWN -> BenflixColors.Muted
    }

/** The big line at the top of Overview: "Everything is running" in green, or yellow/red with the reason */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ServerBanner(
    light: Light,
    reason: String?,
    pills: List<Pair<String, Light>>,
    updated: String?,
) {
    val isTv = rememberIsTv()
    val color = lightColor(light)
    val headline =
        when (light) {
            Light.GOOD -> "Everything is running"
            Light.WARN -> "Something needs a look"
            Light.BAD -> "Something isn't working"
            Light.UNKNOWN -> "Checking everything…"
        }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(BenflixShapes.Panel)
                .background(color.copy(alpha = .13f))
                .border(1.dp, color.copy(alpha = .35f), BenflixShapes.Panel)
                .padding(horizontal = if (isTv) 24.dp else 16.dp, vertical = if (isTv) 18.dp else 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(if (isTv) 16.dp else 13.dp)
                    .clip(CircleShape)
                    .background(color),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = headline,
                fontSize = BenflixType.pageTitle,
                fontWeight = FontWeight.ExtraBold,
                color = BenflixColors.Text,
            )
        }
        reason?.takeIf { light != Light.GOOD }?.let {
            Text(
                text = it,
                fontSize = BenflixType.body,
                color = BenflixColors.Soft,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 12.dp),
        ) {
            pills.forEach { (label, l) -> StatusPill(label, l) }
            updated?.let {
                Text(
                    text = it,
                    fontSize = BenflixType.tag,
                    color = BenflixColors.Muted,
                    modifier = Modifier.align(Alignment.CenterVertically).padding(start = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun StatusPill(
    label: String,
    light: Light,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .clip(BenflixShapes.Pill)
                .background(BenflixColors.Raised)
                .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Box(
            Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(lightColor(light)),
        )
        Spacer(Modifier.width(8.dp))
        Text(text = label, fontSize = BenflixType.small, fontWeight = FontWeight.SemiBold, color = BenflixColors.Text)
    }
}

/** "Needs you" and "Watching now", side by side on TV and wide screens, one under the other on phones */
@Composable
internal fun ServerOverviewCards(
    needs: List<NeedLine>,
    watching: List<WatchingNow>?,
    onOpenWatching: () -> Unit,
) {
    val isTv = rememberIsTv()
    val config = LocalConfiguration.current
    val wide = isTv || config.screenWidthDp >= 720
    if (wide) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            NeedsYouCard(needs, Modifier.weight(1f))
            WatchingNowCard(watching, onOpenWatching, Modifier.weight(1f))
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            NeedsYouCard(needs, Modifier.fillMaxWidth())
            WatchingNowCard(watching, onOpenWatching, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun OverviewCard(
    title: String,
    count: Int?,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
            modifier
                .heightIn(min = 120.dp)
                .clip(BenflixShapes.Panel)
                .background(BenflixColors.Panel)
                .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = title, fontSize = BenflixType.rowName, fontWeight = FontWeight.Bold, color = BenflixColors.Text)
            count?.takeIf { it > 0 }?.let {
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "$it",
                    fontSize = BenflixType.tag,
                    fontWeight = FontWeight.Bold,
                    color = BenflixColors.Tray,
                    modifier =
                        Modifier
                            .clip(BenflixShapes.Pill)
                            .background(BenflixColors.Text)
                            .padding(horizontal = 9.dp, vertical = 2.dp),
                )
            }
        }
        content()
    }
}

@Composable
private fun NeedsYouCard(
    needs: List<NeedLine>,
    modifier: Modifier,
) {
    OverviewCard("Needs you", needs.size, modifier) {
        if (needs.isEmpty()) {
            Text(
                text = "All good! Nothing needs you right now.",
                fontSize = BenflixType.body,
                color = BenflixColors.Soft,
            )
        }
        needs.take(MAX_NEEDS).forEach { need ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(BenflixShapes.Card)
                        .background(BenflixColors.Raised)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                if (need.poster != null) {
                    AsyncImage(
                        model = need.poster,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .width(36.dp)
                                .height(54.dp)
                                .clip(BenflixShapes.Small)
                                .background(BenflixColors.PanelHigh),
                    )
                } else {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(lightColor(need.light)),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = need.text,
                        fontSize = BenflixType.body,
                        fontWeight = FontWeight.SemiBold,
                        color = BenflixColors.Text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    need.supporting?.let {
                        Text(
                            text = it,
                            fontSize = BenflixType.small,
                            color = BenflixColors.Muted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                need.actions.forEach { (label, action) ->
                    Spacer(Modifier.width(8.dp))
                    SmallButton(label, onClick = action)
                }
            }
        }
        if (needs.size > MAX_NEEDS) {
            Text(
                text = "And ${needs.size - MAX_NEEDS} more further down this page",
                fontSize = BenflixType.small,
                color = BenflixColors.Muted,
            )
        }
    }
}

@Composable
private fun WatchingNowCard(
    watching: List<WatchingNow>?,
    onOpen: () -> Unit,
    modifier: Modifier,
) {
    OverviewCard("Watching now", watching?.size, modifier) {
        when {
            watching == null -> {
                Text(text = "Checking…", fontSize = BenflixType.body, color = BenflixColors.Soft)
            }

            watching.isEmpty() -> {
                Text(text = "Nobody is watching right now", fontSize = BenflixType.body, color = BenflixColors.Soft)
            }

            else -> {
                watching.take(MAX_WATCHING).forEach { w ->
                    Surface(
                        onClick = onOpen,
                        shape = ClickableSurfaceDefaults.shape(BenflixShapes.Card),
                        colors =
                            ClickableSurfaceDefaults.colors(
                                containerColor = BenflixColors.Raised,
                                focusedContainerColor = BenflixColors.FocusRow,
                            ),
                        border = focusRing(BenflixShapes.Card),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            AsyncImage(
                                model = w.posterUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier =
                                    Modifier
                                        .width(36.dp)
                                        .height(54.dp)
                                        .clip(BenflixShapes.Small)
                                        .background(BenflixColors.PanelHigh),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = w.who,
                                    fontSize = BenflixType.body,
                                    fontWeight = FontWeight.Bold,
                                    color = BenflixColors.Text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = w.what,
                                    fontSize = BenflixType.small,
                                    color = BenflixColors.Soft,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text =
                                        listOfNotNull(
                                            w.device.ifBlank { null },
                                            "Paused".takeIf { w.paused },
                                            w.timeLeft,
                                        ).joinToString(" · "),
                                    fontSize = BenflixType.tag,
                                    color = BenflixColors.Muted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                w.progress?.let {
                                    Spacer(Modifier.height(4.dp))
                                    ProgressLine(it)
                                }
                            }
                        }
                    }
                }
                if (watching.size > MAX_WATCHING) {
                    Text(
                        text = "And ${watching.size - MAX_WATCHING} more under Watching",
                        fontSize = BenflixType.small,
                        color = BenflixColors.Muted,
                    )
                }
            }
        }
    }
}

/** The five tool tiles (Overview's bottom, and the top of Tools) */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ServerToolTiles(
    selected: ServerToolGroup?,
    onPick: (ServerToolGroup) -> Unit,
) {
    val isTv = rememberIsTv()
    val config = LocalConfiguration.current
    val perRow =
        when {
            isTv || config.screenWidthDp >= 900 -> 5
            config.screenWidthDp >= 600 -> 3
            else -> 2
        }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        maxItemsInEachRow = perRow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        ServerToolGroup.entries.forEach { group ->
            val on = group == selected
            Surface(
                onClick = { onPick(group) },
                shape = ClickableSurfaceDefaults.shape(BenflixShapes.Card),
                colors =
                    ClickableSurfaceDefaults.colors(
                        containerColor = if (on) BenflixColors.Text else BenflixColors.Panel,
                        contentColor = if (on) BenflixColors.Tray else BenflixColors.Text,
                        focusedContainerColor = if (on) BenflixColors.Text else BenflixColors.FocusRow,
                        focusedContentColor = if (on) BenflixColors.Tray else BenflixColors.Text,
                    ),
                border = focusRing(BenflixShapes.Card),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                modifier = Modifier.weight(1f).heightIn(min = if (isTv) 118.dp else 104.dp),
            ) {
                Column(Modifier.padding(14.dp)) {
                    BenflixIconImage(
                        group.icon,
                        color = androidx.tv.material3.LocalContentColor.current,
                        size = 24.dp,
                        contentDescription = null,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = group.title,
                        fontSize = BenflixType.body,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = group.line,
                        fontSize = BenflixType.tag,
                        color = if (on) BenflixColors.Tray.copy(alpha = .75f) else BenflixColors.Muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * Approved ss-p-setup: the yellow card when Tower's helper is older than this app needs. Lists what's waiting,
 * shows the whole command with Copy, and "Where do I paste it?" explains Unraid's Terminal.
 */
@Composable
internal fun ServerSetupCard(need: TowerSetupNeed) {
    val context = LocalContext.current
    var where by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    val yellow = BenflixColors.Warning
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(BenflixShapes.Panel)
                .background(yellow.copy(alpha = .12f))
                .border(1.dp, yellow.copy(alpha = .45f), BenflixShapes.Panel)
                .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BenflixIconImage(BenflixIcon.WARNING, color = yellow, size = 22.dp, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Tower needs the setup command",
                fontSize = BenflixType.rowName,
                fontWeight = FontWeight.Bold,
                color = BenflixColors.Text,
            )
        }
        Text(
            text = "This Benflix is newer than the helper on Tower. Run the command once and these start working:",
            fontSize = BenflixType.body,
            color = BenflixColors.Soft,
        )
        need.waiting.forEach {
            Text(text = "•  $it", fontSize = BenflixType.small, color = BenflixColors.Soft)
        }
        Text(
            text = TowerSetupCheck.COMMAND,
            fontSize = BenflixType.small,
            fontFamily = FontFamily.Monospace,
            color = BenflixColors.Text,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(BenflixShapes.Small)
                    .background(BenflixColors.Tray)
                    .padding(12.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SmallButton(if (copied) "Copied" else "Copy", primary = true) {
                val clip = context.getSystemService(android.content.ClipboardManager::class.java)
                clip?.setPrimaryClip(android.content.ClipData.newPlainText("Benflix setup command", TowerSetupCheck.COMMAND))
                copied = true
            }
            SmallButton("Where do I paste it?") { where = true }
        }
    }
    if (where) {
        ConfirmDialog(
            title = "Where do I paste it?",
            body =
                TowerSetupCheck.WHERE +
                    "\n\nOn a TV, Copy only copies on the TV, so it's easiest from Benflix on your phone or the web.",
            onCancel = { where = false },
            onConfirm = { where = false },
        )
    }
}

/** A small white or dark pill button for cards */
@Composable
internal fun SmallButton(
    label: String,
    primary: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(BenflixShapes.Pill),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = if (primary) BenflixColors.Text else BenflixColors.PanelHigh,
                contentColor = if (primary) BenflixColors.Tray else BenflixColors.Text,
                focusedContainerColor = BenflixColors.Text,
                focusedContentColor = BenflixColors.Tray,
            ),
        border = focusRing(BenflixShapes.Pill),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Text(
            text = label,
            fontSize = BenflixType.small,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}

private const val MAX_NEEDS = 6
private const val MAX_WATCHING = 4
