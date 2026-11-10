package com.github.damontecres.wholphin.ui.main

import android.widget.Toast
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.services.BenflixBooks
import com.github.damontecres.wholphin.services.BookListKeys
import com.github.damontecres.wholphin.ui.preferences.StringInput
import com.github.damontecres.wholphin.ui.preferences.StringInputDialog
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Benflix Server page (Tools > Book lists): the free NYT key and the free Hardcover token that fill
 * Requests > Books (NYT best sellers, Book of the week, Top 10). Typed once; Tower tries each straight
 * away and gathers the lists once a day. The keys stay on Tower.
 */
@HiltViewModel
class BookListKeysViewModel
    @Inject
    constructor(
        private val books: BenflixBooks,
    ) : ViewModel() {
        val keys = MutableStateFlow<BookListKeys?>(null)
        val problem = MutableStateFlow<String?>(null)
        val busy = MutableStateFlow(false)

        fun load() {
            viewModelScope.launch(WholphinDispatchers.IO) {
                try {
                    keys.value = books.listKeys()
                    problem.value = null
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    problem.value = ex.message ?: "Couldn't reach Tower."
                }
            }
        }

        fun save(
            which: String,
            value: String,
            onDone: (String) -> Unit,
        ) {
            busy.value = true
            viewModelScope.launch(WholphinDispatchers.IO) {
                val words =
                    try {
                        val (message, now) = books.setListKey(which, value)
                        keys.value = now
                        message
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        ex.message ?: "Couldn't reach Tower."
                    } finally {
                        busy.value = false
                    }
                withContext(Dispatchers.Main) { onDone(words) }
            }
        }
    }

internal fun LazyListScope.bookListItems() {
    item { SectionTitle("Book lists") }
    item(key = "book-lists-nyt") { BookKeyRow("nyt") }
    item(key = "book-lists-hardcover") { BookKeyRow("hardcover") }
}

@Composable
private fun BookKeyRow(
    which: String,
    viewModel: BookListKeysViewModel = hiltViewModel(key = "book-list-keys"),
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.load() }
    val keys by viewModel.keys.collectAsState()
    val problem by viewModel.problem.collectAsState()
    val busy by viewModel.busy.collectAsState()
    var typing by remember { mutableStateOf(false) }
    val done: (String) -> Unit = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    val nyt = which == "nyt"
    val name = if (nyt) "NYT Books key" else "Hardcover token"
    val k = keys
    when {
        k == null && problem != null -> {
            ServerActionRow(
                headline = "Couldn't open the book list keys",
                supporting = problem,
                leading = { Dot(Light.UNKNOWN) },
                onClick = { viewModel.load() },
                actions = listOf("Try again" to { viewModel.load() }),
            )
        }

        k == null -> {
            if (nyt) LoadingBox("Asking Tower…")
        }

        else -> {
            val set = if (nyt) k.nytSet else k.hardcoverSet
            val ok = if (nyt) k.nytOk else k.hardcoverOk
            val bad = if (nyt) k.nytProblem else k.hardcoverProblem
            val what =
                if (nyt) {
                    "NYT best sellers (Fiction, Nonfiction, audiobooks) and Book of the week."
                } else {
                    "Top 10 books right now, Next in your series, scores and reader quotes."
                }
            ServerActionRow(
                headline =
                    when {
                        !set -> "$name: not added yet"
                        ok == false -> "$name: not working"
                        else -> "$name: working"
                    },
                supporting =
                    when {
                        !set -> "$what Free to get. Type it once and Tower does the rest."
                        ok == false -> bad ?: "Tower couldn't use it."
                        nyt -> "$what ${k.nytCount} best sellers right now."
                        else -> "$what ${k.topCount} trending books right now. Hardcover tokens run out on January 1st."
                    },
                leading = {
                    Dot(
                        if (!set) {
                            Light.UNKNOWN
                        } else if (ok == false) {
                            Light.BAD
                        } else {
                            Light.GOOD
                        },
                    )
                },
                onClick = { typing = true },
                actions =
                    if (busy) {
                        emptyList()
                    } else if (set) {
                        listOf("Change" to { typing = true }, "Remove" to { viewModel.save(which, "", done) })
                    } else {
                        listOf((if (nyt) "Type the key" else "Paste the token") to { typing = true })
                    },
            )
        }
    }
    if (typing) {
        StringInputDialog(
            input =
                StringInput(
                    title = if (nyt) "Your NYT Books API key" else "Your Hardcover API token",
                    value = "",
                    keyboardOptions =
                        KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            imeAction = ImeAction.Done,
                        ),
                    onSubmit = {},
                ),
            onSave = { typed ->
                typing = false
                if (typed.isNotBlank()) viewModel.save(which, typed.trim(), done)
            },
            onDismissRequest = { typing = false },
        )
    }
}
