package com.github.damontecres.wholphin.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.services.TowerSetupCheck
import com.github.damontecres.wholphin.services.TowerSetupNeed
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.components.BenflixIconImage
import com.github.damontecres.wholphin.ui.components.ConfirmDialog
import com.github.damontecres.wholphin.ui.nav.focusRing
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixShapes
import com.github.damontecres.wholphin.ui.theme.BenflixType

/**
 * Redesign 2 (Server and Settings, approved sv2-glance + ss-p-tools): the Server page's tools, grouped by job.
 * Since the 2026-11-28 redesign each group is one row of tool tiles on Tools (see ServerTool).
 */
enum class ServerToolGroup(
    val title: String,
    val line: String,
    val icon: BenflixIcon,
) {
    LIBRARY("Library", "Scan for new stuff, the library checkup and posters", BenflixIcon.FILM),
    DOWNLOADS("Downloads", "What's downloading, and stuck ones", BenflixIcon.DOWNLOAD),
    PEOPLE("People", "Messages, Glowfin versions and Kids ratings", BenflixIcon.PERSON),
    FIXES("Playback fixes", "Tidy track names, converting and playback speed", BenflixIcon.PLAY),
    TOWER("Tower", "Setup command, apps, drive health, restart, chores, Jellyfin add-ons", BenflixIcon.SERVER),
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
            text = "This Glowfin is newer than the helper on Tower. Run the command once and these start working:",
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
                clip?.setPrimaryClip(android.content.ClipData.newPlainText("Glowfin setup command", TowerSetupCheck.COMMAND))
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
                    "\n\nOn a TV, Copy only copies on the TV, so it's easiest from Glowfin on your phone or the web.",
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
                focusedContainerColor = com.github.damontecres.wholphin.ui.theme.BenflixColors.FocusFill,
                focusedContentColor = com.github.damontecres.wholphin.ui.theme.BenflixColors.OnFocusFill,
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
