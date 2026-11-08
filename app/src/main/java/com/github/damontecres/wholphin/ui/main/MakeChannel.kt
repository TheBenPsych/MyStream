package com.github.damontecres.wholphin.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.services.CustomChannel
import com.github.damontecres.wholphin.services.MakeChannelPick
import com.github.damontecres.wholphin.ui.components.BenflixChoice
import com.github.damontecres.wholphin.ui.components.BenflixChoiceDialog
import com.github.damontecres.wholphin.ui.components.BenflixPanelDialog
import com.github.damontecres.wholphin.ui.components.ChoiceRow
import com.github.damontecres.wholphin.ui.components.LoadingSpinner
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.tryRequestFocus
import kotlinx.coroutines.delay
import java.util.UUID

/**
 * Approved Live TV "Make a channel": three short steps. 1 what kind (a show, a genre or a collection),
 * 2 which one, 3 how it plays. Then it's a new "No ads" channel. On TV each step is the side panel,
 * on phones a tall sheet from the bottom with big rows.
 */
@Composable
fun MakeChannelFlow(
    loadPicks: suspend (CustomChannel.Kind) -> List<MakeChannelPick>,
    onMake: (CustomChannel) -> Unit,
    onDismiss: () -> Unit,
) {
    var kind by remember { mutableStateOf<CustomChannel.Kind?>(null) }
    var picked by remember { mutableStateOf<MakeChannelPick?>(null) }
    // The choice pop-up closes itself before running the picked row, so a close is only "Back"
    // when nothing was picked by the time the next frame comes
    var backAsked by remember { mutableIntStateOf(0) }
    var stepAtBack by remember { mutableIntStateOf(0) }
    var made by remember { mutableStateOf(false) }
    val step =
        if (kind == null) {
            1
        } else if (picked == null) {
            2
        } else {
            3
        }
    LaunchedEffect(backAsked) {
        if (backAsked == 0) return@LaunchedEffect
        if (made) return@LaunchedEffect
        if (step != stepAtBack) return@LaunchedEffect
        when (step) {
            1 -> onDismiss()
            3 -> picked = null
        }
    }

    fun back() {
        stepAtBack = step
        backAsked++
    }
    val k = kind
    val p = picked
    when {
        k == null -> {
            BenflixChoiceDialog(
                title = "Make a channel",
                subtitle = "Step 1 of 3 · What should it play?",
                choices =
                    listOf(
                        BenflixChoice("One show", { kind = CustomChannel.Kind.SHOW }, note = "Like \"The Simpsons 24/7\""),
                        BenflixChoice("A genre", { kind = CustomChannel.Kind.GENRE }, note = "Like \"Action 24/7\""),
                        BenflixChoice("A collection", { kind = CustomChannel.Kind.COLLECTION }, note = "Like a movie series"),
                    ),
                // Picking a row closes the dialog first, so only a real Back closes the whole flow
                onDismissRequest = ::back,
            )
        }

        p == null -> {
            PickStep(
                kind = k,
                loadPicks = loadPicks,
                onPick = { picked = it },
                onBack = { kind = null },
            )
        }

        else -> {
            val name = CustomChannel.nameFor(p.name)

            fun make(
                what: CustomChannel.What = CustomChannel.What.BOTH,
                inOrder: Boolean = false,
            ) {
                onMake(
                    CustomChannel(
                        id = UUID.randomUUID().toString().take(8),
                        name = name,
                        kind = k,
                        targetId = p.id.takeIf { k != CustomChannel.Kind.GENRE },
                        genre = p.name.takeIf { k == CustomChannel.Kind.GENRE },
                        what = what,
                        inOrder = inOrder,
                    ),
                )
            }
            BenflixChoiceDialog(
                title = name,
                subtitle = "Step 3 of 3 · How should it play?",
                choices =
                    if (k == CustomChannel.Kind.GENRE) {
                        listOf(
                            BenflixChoice(
                                label = "Movies and shows",
                                onClick = {
                                    made = true
                                    make(CustomChannel.What.BOTH)
                                },
                            ),
                            BenflixChoice(
                                label = "Just movies",
                                onClick = {
                                    made = true
                                    make(CustomChannel.What.MOVIES)
                                },
                            ),
                            BenflixChoice(
                                label = "Just shows",
                                onClick = {
                                    made = true
                                    make(CustomChannel.What.SHOWS)
                                },
                            ),
                        )
                    } else {
                        listOf(
                            BenflixChoice(
                                label = "Shuffled",
                                onClick = {
                                    made = true
                                    make(inOrder = false)
                                },
                                note = "A surprise every time",
                            ),
                            BenflixChoice(
                                label = "In order",
                                onClick = {
                                    made = true
                                    make(inOrder = true)
                                },
                                note = "Carries on from a new place each day",
                            ),
                        )
                    },
                onDismissRequest = ::back,
            )
        }
    }
}

/** Step 2: the list of shows, genres or collections, with a box to type a name */
@Composable
private fun PickStep(
    kind: CustomChannel.Kind,
    loadPicks: suspend (CustomChannel.Kind) -> List<MakeChannelPick>,
    onPick: (MakeChannelPick) -> Unit,
    onBack: () -> Unit,
) {
    var all by remember(kind) { mutableStateOf<List<MakeChannelPick>?>(null) }
    LaunchedEffect(kind) { all = loadPicks(kind) }
    var query by remember { mutableStateOf("") }
    val shown =
        remember(all, query) {
            all.orEmpty().filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
        }
    val what =
        when (kind) {
            CustomChannel.Kind.SHOW -> "show"
            CustomChannel.Kind.GENRE -> "genre"
            CustomChannel.Kind.COLLECTION -> "collection"
        }
    BenflixPanelDialog(onDismissRequest = onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Text(
                "Pick a $what",
                color = BenflixColors.Text,
                fontSize = BenflixType.rowName,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            Text(
                "Step 2 of 3",
                color = BenflixColors.Muted,
                fontSize = BenflixType.small,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            val list = all
            if (list == null) {
                Box(Modifier.fillMaxWidth().height(200.dp)) { LoadingSpinner() }
                return@Column
            }
            if (list.isEmpty()) {
                Text(
                    "There aren't any on your server yet.",
                    color = BenflixColors.Muted,
                    fontSize = BenflixType.body,
                    modifier = Modifier.padding(12.dp),
                )
                return@Column
            }
            val first = remember { FocusRequester() }
            // Long lists get a box to type in; the list itself gets focus first so OK picks right away
            if (list.size > 12) {
                com.github.damontecres.wholphin.ui.components.SearchEditTextBox(
                    value = query,
                    onValueChange = { query = it },
                    onSearchClick = {},
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            LaunchedEffect(Unit) {
                delay(80)
                first.tryRequestFocus()
            }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth().height(420.dp),
            ) {
                itemsIndexed(shown, key = { i, it -> "${it.id ?: it.name}-$i" }) { index, pick ->
                    ChoiceRow(
                        choice = BenflixChoice(pick.name, {}),
                        onClick = { onPick(pick) },
                        modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
                    )
                }
            }
        }
    }
}
