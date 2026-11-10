package com.github.damontecres.wholphin.ui.books

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.services.BenflixBooks
import com.github.damontecres.wholphin.services.KidsBook
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.focusRing
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixSpace
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

@HiltViewModel
class BooksKidsViewModel
    @Inject
    constructor(
        private val books: BenflixBooks,
        val navigationManager: NavigationManager,
    ) : ViewModel() {
        val list = MutableStateFlow<List<KidsBook>?>(null)
        val problem = MutableStateFlow<String?>(null)

        fun load() {
            viewModelScope.launchIO {
                try {
                    list.value = books.kidsList()
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    problem.value = ex.message ?: "Couldn't reach Tower"
                }
            }
        }

        fun toggle(b: KidsBook) {
            list.value = list.value?.map { if (it.id == b.id) it.copy(allowed = !b.allowed) else it }
            viewModelScope.launchIO {
                try {
                    books.kidsAllow(b.id, !b.allowed)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    problem.value = "Couldn't save that. Try again."
                    load()
                }
            }
        }
    }

/** Ben's Kids books list (approved: Settings > Kids > Books): turn on any book Kids may see */
@Composable
fun BooksKidsPage(
    modifier: Modifier = Modifier,
    viewModel: BooksKidsViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.load() }
    val list by viewModel.list.collectAsState()
    val problem by viewModel.problem.collectAsState()
    val isTv = rememberIsTv()
    val config = LocalConfiguration.current
    val edge = if (isTv || config.screenWidthDp > config.screenHeightDp) BenflixSpace.tvEdge else BenflixSpace.phoneEdge
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(horizontal = edge, vertical = 16.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        item(key = "title") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                Text("Kids books", color = BenflixColors.Text, fontSize = BenflixType.pageTitle, fontWeight = FontWeight.ExtraBold)
                Text(
                    "Books rated for kids always show on Kids. Turn on any other book here to let Kids see it too.",
                    color = BenflixColors.Soft,
                    fontSize = BenflixType.body,
                )
                problem?.let { Text(it, color = BenflixColors.Warning, fontSize = BenflixType.small) }
            }
        }
        val l = list
        if (l == null) {
            item(key = "loading") { Text("Loading…", color = BenflixColors.Muted, fontSize = BenflixType.body) }
        } else {
            items(l, key = { it.id }) { b ->
                val shape = RoundedCornerShape(10.dp)
                Surface(
                    onClick = { viewModel.toggle(b) },
                    shape = ClickableSurfaceDefaults.shape(shape),
                    colors =
                        ClickableSurfaceDefaults.colors(
                            containerColor = Color.Transparent,
                            contentColor = BenflixColors.Text,
                            focusedContainerColor = BenflixColors.FocusRow,
                            focusedContentColor = BenflixColors.Text,
                        ),
                    border = focusRing(shape),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.padding(8.dp),
                    ) {
                        BookCoverImage(b.cover, b.name, Modifier.width(44.dp).aspectRatio(BOOK_RATIO), audio = b.kind == "audiobook")
                        Column(Modifier.weight(1f)) {
                            Text(b.name, fontSize = BenflixType.body, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOf(b.author, kindWord(b.kind)).filter { it.isNotBlank() }.joinToString(" · "),
                                color = BenflixColors.Muted,
                                fontSize = BenflixType.small,
                                maxLines = 1,
                            )
                        }
                        Switch(checked = b.allowed, onCheckedChange = null)
                    }
                }
            }
        }
    }
}

fun kindWord(kind: String): String =
    when (kind) {
        "manga" -> "Manga"
        "comic" -> "Comic"
        "audiobook" -> "Audiobook"
        else -> "Book"
    }
