package com.github.damontecres.wholphin.ui.setup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.ReportProblemViewModel
import com.github.damontecres.wholphin.ui.components.BenflixChoice
import com.github.damontecres.wholphin.ui.components.BenflixChoiceDialog
import com.github.damontecres.wholphin.ui.components.LicenseInfo
import com.github.damontecres.wholphin.ui.components.rememberUpdateAlert
import com.github.damontecres.wholphin.ui.main.HowToPage
import com.github.damontecres.wholphin.ui.nav.APP_PROBLEMS
import com.github.damontecres.wholphin.ui.preferences.GlowfinListRow
import com.github.damontecres.wholphin.ui.preferences.GlowfinPageHeading
import com.github.damontecres.wholphin.ui.preferences.GlowfinSideListPanel
import com.github.damontecres.wholphin.ui.theme.Glowfin
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus

private enum class HelpSection(
    val title: String,
    val note: String,
) {
    UPDATES("Updates", "Install the newest Glowfin and see what's new"),
    HOW_TO("How to use", "The remote, the menu, and the handy tricks"),
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

    // Glowfin (S1): the same plain list as Settings, Updates first
    val list: @Composable (Modifier) -> Unit = { m ->
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = m) {
            GlowfinPageHeading("Help")
            HelpSection.entries.forEachIndexed { index, section ->
                GlowfinListRow(
                    label = section.title,
                    selected = isTv && open == section,
                    note = if (section == HelpSection.UPDATES && updateAlert) "Update ready" else null,
                    supporting = if (isTv) null else section.note,
                    chevron = !isTv,
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
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp), modifier = modifier.fillMaxSize().background(Glowfin.Ink)) {
            GlowfinSideListPanel { list(Modifier.fillMaxSize()) }
            open?.let { page(it, Modifier.weight(1f).fillMaxHeight().padding(end = 32.dp)) }
        }
    } else {
        val section = open
        if (section == null) {
            list(modifier.fillMaxSize().background(Glowfin.Ink).padding(16.dp))
        } else {
            BackHandler { open = null }
            page(section, modifier.fillMaxSize().background(Glowfin.Ink))
        }
    }
    if (report) {
        BenflixChoiceDialog(
            title = "Report a problem",
            subtitle = "Pick what's wrong. The owner gets it with the app's log.",
            choices = APP_PROBLEMS.map { problem -> BenflixChoice(problem, { reportViewModel.report(null, "Glowfin app", problem) }) },
            onDismissRequest = { report = false },
        )
    }
}
