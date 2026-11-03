package com.github.damontecres.wholphin.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.services.BenflixCantFind
import com.github.damontecres.wholphin.services.CantFindEntry
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The orange-red used for "Couldn't find it" everywhere */
val CANT_FIND_COLOR = Color(0xFFF07A3E)

/** "Couldn't find it" key for a Seerr title */
fun cantFindKey(
    isMovie: Boolean,
    tmdbId: Int,
): String = (if (isMovie) "MOVIE" else "TV") + "-$tmdbId"

/** For title pages: reads the list and lets Ben mark or undo */
@HiltViewModel
class CantFindViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: android.content.Context,
        private val cantFind: BenflixCantFind,
        private val serverRepository: ServerRepository,
    ) : ViewModel() {
        val entries: StateFlow<Map<String, CantFindEntry>> = cantFind.entries
        val isAdmin: Boolean get() = serverRepository.currentUserDto?.policy?.isAdministrator == true

        fun refresh() {
            viewModelScope.launch(WholphinDispatchers.IO) { cantFind.refresh() }
        }

        fun mark(
            key: String,
            title: String?,
            note: String?,
            askedBy: List<Int>,
        ) {
            viewModelScope.launch(WholphinDispatchers.IO) {
                val ok = cantFind.mark(key, title, note, askedBy)
                showToast(context, if (ok) "Marked as can't find. Everyone who asked will be told." else "Couldn't save that. Try again.")
            }
        }

        fun undo(key: String) {
            viewModelScope.launch(WholphinDispatchers.IO) {
                val ok = cantFind.undo(key)
                showToast(context, if (ok) "OK, it's back to Still looking." else "Couldn't save that. Try again.")
            }
        }
    }

/**
 * Ben picks a ready-made note (easy with a TV remote), types his own, or says nothing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CantFindDialog(
    title: String?,
    onDismiss: () -> Unit,
    onMark: (note: String?) -> Unit,
) {
    val isTv = rememberIsTv()
    var typed by remember { mutableStateOf("") }
    com.github.damontecres.wholphin.ui.components.BasicDialog(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier =
                Modifier
                    .widthIn(max = 560.dp)
                    .padding(if (isTv) 24.dp else 16.dp),
        ) {
            Text(
                text = "CAN'T FIND IT",
                color = CANT_FIND_COLOR,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                fontSize = 14.sp,
            )
            Text(
                text = title ?: "This title",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = if (isTv) 22.sp else 19.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text =
                    "Whoever asked will be told, and the title's page will say so. " +
                        "It keeps searching, so if a copy turns up it still downloads. Pick a note:",
                color = Color(0xFFCFCFD6),
                fontSize = 15.sp,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BenflixCantFind.QUICK_NOTES.forEach { note ->
                    Button(onClick = { onMark(note) }) { Text(note, maxLines = 1) }
                }
                Button(onClick = { onMark(null) }) { Text("No note", maxLines = 1) }
            }
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it.take(BenflixCantFind.MAX_NOTE) },
                singleLine = true,
                placeholder = { androidx.compose.material3.Text("Or type your own note") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (typed.isNotBlank()) onMark(typed) }),
                modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (typed.isNotBlank()) {
                    Button(onClick = { onMark(typed) }) { Text("Save my note", maxLines = 1) }
                }
                Button(onClick = onDismiss) { Text("Cancel", maxLines = 1) }
            }
        }
    }
}

/** Small line under a request: Ben's note and when he marked it */
@Composable
fun CantFindNoteLine(entry: CantFindEntry) {
    val date = remember(entry.date) { BenflixCantFind.niceDate(entry.date) }
    val text =
        listOfNotNull(
            entry.note?.let { "“$it”" }
                ?: if (entry.auto) "Searched for a week with no luck" else "Ben looked but couldn't find a copy",
            date?.let { "marked $it" },
        ).joinToString(" · ")
    Text(
        text = text,
        fontSize = 13.sp,
        color = Color(0xFFE8B79C),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * On a movie or show page: "Ben looked, but couldn't find this yet" for everyone, with Undo for Ben.
 * Hidden once the title is on Benflix.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CantFindBlock(
    isMovie: Boolean,
    tmdbId: Int,
    available: Boolean,
    modifier: Modifier = Modifier,
    viewModel: CantFindViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.refresh() }
    val entries by viewModel.entries.collectAsState()
    val key = cantFindKey(isMovie, tmdbId)
    val entry = entries[key] ?: return
    if (available) return
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier =
            modifier
                .background(CANT_FIND_COLOR.copy(alpha = .16f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.padding(top = 2.dp)) {
                Text(
                    text =
                        if (entry.auto) {
                            "We looked for a week, but couldn't find this yet"
                        } else {
                            "Ben looked, but couldn't find this yet"
                        },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = CANT_FIND_COLOR,
                )
            }
            if (viewModel.isAdmin) {
                Button(onClick = { viewModel.undo(key) }) { Text("Undo", maxLines = 1) }
            }
        }
        CantFindNoteLine(entry)
    }
}
