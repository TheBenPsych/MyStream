package com.github.damontecres.wholphin.ui.setup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.ReportProblemViewModel
import com.github.damontecres.wholphin.ui.components.BenflixChoice
import com.github.damontecres.wholphin.ui.components.BenflixChoiceDialog
import com.github.damontecres.wholphin.ui.components.LicenseInfo
import com.github.damontecres.wholphin.ui.components.rememberUpdateAlert
import com.github.damontecres.wholphin.ui.door.doorFocusBorder
import com.github.damontecres.wholphin.ui.main.HowToPage
import com.github.damontecres.wholphin.ui.nav.APP_PROBLEMS
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus

private enum class HelpSection(
    val title: String,
    val note: String,
) {
    UPDATES("Updates", "Install the newest Benflix and see what's new"),
    HOW_TO("How to use", "The remote, the doors, and the handy tricks"),
    REPORT("Report a problem", "Tell the owner something isn't working"),
    ABOUT("About", "Version, credits and licences"),
}

/**
 * Benflix redesign: Help and updates works like Settings: the list on the left, the page on the
 * right (phones: the list first, tap one to open it).
 */
@Composable
fun HelpAndUpdatesPage(
    preferences: UserPreferences,
    modifier: Modifier = Modifier,
    reportViewModel: ReportProblemViewModel = hiltViewModel(),
) {
    val isTv = rememberIsTv()
    var open by rememberSaveable { mutableStateOf(if (isTv) HelpSection.UPDATES else null) }
    var report by remember { mutableStateOf(false) }
    val updateAlert = rememberUpdateAlert()
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.tryRequestFocus() }

    val list: @Composable (Modifier) -> Unit = { m ->
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = m) {
            Text(
                text = "Help and updates",
                color = BenflixColors.Text,
                fontSize = BenflixType.rowName,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            HelpSection.entries.forEachIndexed { index, section ->
                HelpRow(
                    section = section,
                    selected = open == section,
                    alert = section == HelpSection.UPDATES && updateAlert,
                    onClick = { if (section == HelpSection.REPORT) report = true else open = section },
                    modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
                )
            }
        }
    }
    val page: @Composable (HelpSection, Modifier) -> Unit = { section, m ->
        Box(m) {
            when (section) {
                HelpSection.UPDATES -> {
                    InstallUpdatePage(preferences, Modifier.fillMaxSize())
                }

                HelpSection.HOW_TO -> {
                    HowToPage(Modifier.fillMaxSize())
                }

                HelpSection.ABOUT -> {
                    LicenseInfo(Modifier.fillMaxSize())
                }

                HelpSection.REPORT -> {}
            }
        }
    }
    if (isTv) {
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp), modifier = modifier.fillMaxSize().padding(start = 24.dp, top = 16.dp)) {
            list(Modifier.width(300.dp).fillMaxHeight())
            open?.let { page(it, Modifier.weight(1f).fillMaxHeight()) }
        }
    } else {
        val section = open
        if (section == null) {
            list(modifier.fillMaxSize().padding(16.dp))
        } else {
            BackHandler { open = null }
            page(section, modifier.fillMaxSize())
        }
    }
    if (report) {
        BenflixChoiceDialog(
            title = "Report a problem",
            subtitle = "Pick what's wrong. The owner gets it with the app's log.",
            choices = APP_PROBLEMS.map { problem -> BenflixChoice(problem, { reportViewModel.report(null, "Benflix app", problem) }) },
            onDismissRequest = { report = false },
        )
    }
}

@Composable
private fun HelpRow(
    section: HelpSection,
    selected: Boolean,
    alert: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = if (selected) BenflixColors.Raised else androidx.compose.ui.graphics.Color.Transparent,
                contentColor = BenflixColors.Text,
                focusedContainerColor = com.github.damontecres.wholphin.ui.theme.BenflixColors.FocusFill,
                focusedContentColor = com.github.damontecres.wholphin.ui.theme.BenflixColors.OnFocusFill,
            ),
        border = doorFocusBorder(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row {
                Text(section.title, fontSize = BenflixType.body, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (alert) Text("Update ready", fontSize = BenflixType.tag, fontWeight = FontWeight.ExtraBold)
            }
            Text(
                section.note,
                fontSize = BenflixType.small,
                color =
                    androidx.tv.material3.LocalContentColor.current
                        .copy(alpha = .7f),
            )
        }
    }
}
