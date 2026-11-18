package com.github.damontecres.wholphin.ui.discover

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.services.GuestMode
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.services.RequestFor
import com.github.damontecres.wholphin.services.RequestForViewModel
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.components.DialogItem
import com.github.damontecres.wholphin.ui.components.DialogPopup
import com.github.damontecres.wholphin.ui.components.ExpandablePlayButton
import kotlin.time.Duration

/** Whether this profile gets Ben's "Request for" picker (admins only; never Kids or Guest) */
@Composable
fun rememberCanRequestFor(isAdmin: Boolean): Boolean {
    val kids by KidsMode.active.collectAsState()
    val guest by GuestMode.active.collectAsState()
    return isAdmin && !kids && !guest
}

/**
 * Ben's "For: Me" button (Ben 2026-11-16): pick who the next ask is for, so it's theirs and they get told
 * when it's ready. Shows next to Ask for it on title pages and at the top of each Requests tab.
 */
@Composable
fun RequestForButton(modifier: Modifier = Modifier) {
    val viewModel: RequestForViewModel = hiltViewModel()
    val people by viewModel.people.collectAsState()
    val picked by viewModel.picked.collectAsState()
    val problem by viewModel.problem.collectAsState()
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // Starts on Me again if it's been a while since the last ask
        RequestFor.current()
        if (people.isEmpty()) viewModel.load()
    }
    ExpandablePlayButton(
        title = R.string.request,
        label = "For: ${picked?.name ?: "Me"}",
        resume = Duration.ZERO,
        icon = BenflixIcon.PERSON.vector,
        primary = picked != null,
        onClick = {
            if (people.isEmpty()) viewModel.load()
            open = true
        },
        modifier = modifier,
    )
    DialogPopup(
        showDialog = open,
        title = "Who is this request for?",
        dialogItems =
            buildList {
                add(
                    DialogItem(
                        headlineContent = { Text("Me") },
                        supportingContent = { Text("Asks the normal way") },
                        selected = picked == null,
                        onClick = { RequestFor.pick(null) },
                    ),
                )
                people.forEach { person ->
                    add(
                        DialogItem(
                            headlineContent = { Text(person.name) },
                            supportingContent = {
                                Text(
                                    if (person.seerr == null) {
                                        "Music and books only (not in Seerr yet)"
                                    } else {
                                        "It goes on their list and they're told when it's ready"
                                    },
                                )
                            },
                            selected = picked?.id == person.id,
                            onClick = { RequestFor.pick(person) },
                        ),
                    )
                }
                if (people.isEmpty()) {
                    add(
                        DialogItem(
                            headlineContent = { Text(problem?.let { "Couldn't get the family list" } ?: "Getting the family list…") },
                            supportingContent = problem?.let { { Text(it) } },
                            enabled = problem != null,
                            onClick = { viewModel.load() },
                        ),
                    )
                }
            },
        onDismissRequest = { open = false },
        waitToLoad = false,
    )
}
