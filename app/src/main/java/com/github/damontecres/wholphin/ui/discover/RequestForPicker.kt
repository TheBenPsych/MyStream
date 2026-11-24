package com.github.damontecres.wholphin.ui.discover

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.services.GuestMode
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.services.RequestFor
import com.github.damontecres.wholphin.services.RequestForViewModel
import com.github.damontecres.wholphin.ui.components.DialogItem
import com.github.damontecres.wholphin.ui.components.DialogPopup

/** Whether this profile gets Ben's "Request for" picker (admins only; never Kids or Guest) */
@Composable
fun rememberCanRequestFor(isAdmin: Boolean): Boolean {
    val kids by KidsMode.active.collectAsState()
    val guest by GuestMode.active.collectAsState()
    return isAdmin && !kids && !guest
}

/**
 * Ben's "For: Me" picker (Ben 2026-11-16): pick who the next ask is for, so it's theirs and they get told
 * when it's ready. Glowfin new look (Ben 2026-11-22): it lives on the ask page now, next to Ask for it,
 * instead of at the top of every Requests tab. [forName] is who it's for now ("Me", or a name).
 */
class RequestForState(
    val forName: String,
    val open: () -> Unit,
)

/** The For picker's state, and its pop-up (drawn here, opened with [RequestForState.open]) */
@Composable
fun rememberRequestFor(): RequestForState {
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
    return RequestForState(
        forName = picked?.name ?: "Me",
        open = {
            if (people.isEmpty()) viewModel.load()
            open = true
        },
    )
}
