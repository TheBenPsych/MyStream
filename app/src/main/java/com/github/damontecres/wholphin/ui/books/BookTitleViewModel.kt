package com.github.damontecres.wholphin.ui.books

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.services.AudiobookService
import com.github.damontecres.wholphin.services.BenflixBooks
import com.github.damontecres.wholphin.services.BookPage
import com.github.damontecres.wholphin.services.CoverChoice
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber
import javax.inject.Inject

/** One book's page: loads it, opens the right reader or the audiobook player, Want to read, marking as read, covers */
@HiltViewModel
class BookTitleViewModel
    @Inject
    constructor(
        val books: BenflixBooks,
        val audiobooks: AudiobookService,
        val navigationManager: NavigationManager,
        private val aniListSync: com.github.damontecres.wholphin.services.AniListSync,
        val mangaOffline: com.github.damontecres.wholphin.services.MangaOffline,
    ) : ViewModel() {
        private val _page = MutableStateFlow<BookPage?>(null)
        val page: StateFlow<BookPage?> = _page
        val problem = MutableStateFlow<String?>(null)
        val note = MutableStateFlow<String?>(null)
        val covers = MutableStateFlow<List<CoverChoice>?>(null)

        /** Send to Kindle: non-null while the Kindle address box is open (holds the address so far) */
        val kindleAsk = MutableStateFlow<String?>(null)
        private var id: String? = null

        fun load(id: String) {
            this.id = id
            viewModelScope.launchIO {
                try {
                    val p = books.title(id)
                    _page.value = p
                    problem.value = null
                    // Manga: chapters finished (here or on another device) tick off on AniList and MyAnimeList
                    if (p.tile.isManga && p.unit == "ch" && p.readTo > 0) {
                        aniListSync.mangaRead(p.tile.name, p.anilist, p.mal, p.readTo)
                    }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Book page")
                    if (_page.value == null) problem.value = ex.message ?: "Couldn't reach Tower"
                }
            }
        }

        fun reload() {
            id?.let { load(it) }
        }

        /** The main button: read (or keep reading), or listen */
        fun start(p: BookPage) {
            val t = p.tile
            when {
                t.isManga -> {
                    p.next?.let { navigationManager.navigateTo(Destination.PageReader(it)) }
                }

                t.isAudio -> {
                    listen(p, null)
                }

                p.file == ".pdf" -> {
                    navigationManager.navigateTo(Destination.PageReader(t.id, book = true))
                }

                else -> {
                    navigationManager.navigateTo(Destination.BookReader(t.id))
                }
            }
        }

        fun listen(
            p: BookPage,
            chapter: Int?,
        ) {
            viewModelScope.launchIO {
                try {
                    audiobooks.play(p, chapter)
                    navigationManager.navigateTo(Destination.AudiobookPlayer)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Audiobook")
                    note.value = "Couldn't start the audiobook."
                }
            }
        }

        fun openVolume(v: com.github.damontecres.wholphin.services.BookVolume) {
            navigationManager.navigateTo(Destination.PageReader(v.id))
        }

        /** Phones: saves the next [count] unread chapters (or every chapter) for reading with no internet */
        fun saveForOffline(
            p: BookPage,
            count: Int?,
        ) {
            val from = p.volumes.indexOfFirst { it.id == p.next }.coerceAtLeast(0)
            val picked = if (count == null) p.volumes else p.volumes.drop(from).take(count)
            mangaOffline.save(p, picked)
            note.value =
                "Saving ${picked.size} " + (if (picked.size == 1) "chapter" else "chapters") + " to this phone. See them in Downloads."
        }

        fun deleteOffline(p: BookPage) {
            mangaOffline.deleteSeries(p.tile.id)
            note.value = "Removed from this phone. It's still on Glowfin."
        }

        /** The same book the other way: the audiobook of this ebook, or the other way round */
        fun openOther(p: BookPage) {
            val other = p.otherId ?: return
            viewModelScope.launchIO {
                try {
                    val o = books.title(other)
                    if (o.tile.isAudio) {
                        listen(o, null)
                    } else {
                        start(o)
                    }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    note.value = "Couldn't open it."
                }
            }
        }

        fun toggleWant(p: BookPage) {
            viewModelScope.launchIO {
                try {
                    books.want(p.tile.id, !p.tile.want)
                    reload()
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    note.value = "Couldn't change Want to read."
                }
            }
        }

        /** Marks a book (or a volume, and maybe every volume before it) as read or not */
        fun mark(
            id: String,
            done: Boolean,
            before: Boolean,
        ) {
            viewModelScope.launchIO {
                try {
                    books.mark(id, done, before)
                    reload()
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    note.value = "Couldn't mark it."
                }
            }
        }

        fun sendToPhone(p: BookPage) {
            viewModelScope.launchIO {
                note.value =
                    try {
                        books.sendToPhone(p.tile.id)
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        "Couldn't send it."
                    }
            }
        }

        /** Send to Kindle: asks for the Kindle's address the first time, then Tower emails the EPUB */
        fun sendToKindle(
            p: BookPage,
            changeAddress: Boolean = false,
        ) {
            viewModelScope.launchIO {
                try {
                    val address = books.kindleAddress()
                    if (address.isBlank() || changeAddress) {
                        kindleAsk.value = address
                    } else {
                        note.value = "Sending to your Kindle…"
                        note.value = books.sendToKindle(p.tile.id)
                    }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    note.value = ex.message?.takeIf { it.isNotBlank() } ?: "Couldn't send it."
                }
            }
        }

        fun saveKindleAddress(
            p: BookPage,
            address: String,
        ) {
            kindleAsk.value = null
            viewModelScope.launchIO {
                try {
                    books.setKindleAddress(address.trim())
                    if (address.isNotBlank()) {
                        note.value = "Sending to your Kindle…"
                        note.value = books.sendToKindle(p.tile.id)
                    }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    note.value = ex.message?.takeIf { it.isNotBlank() } ?: "Couldn't send it."
                }
            }
        }

        fun loadCovers(p: BookPage) {
            covers.value = emptyList()
            viewModelScope.launchIO {
                covers.value =
                    try {
                        books.coverChoices(p.tile.id)
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        note.value = "Couldn't find other covers."
                        null
                    }
            }
        }

        fun pickCover(
            p: BookPage,
            c: CoverChoice,
            all: Boolean,
        ) {
            covers.value = null
            viewModelScope.launchIO {
                note.value =
                    try {
                        books.pickCover(p.tile.id, c, all)
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        "Couldn't change the cover."
                    }
                reload()
            }
        }

        fun resetCover(p: BookPage) {
            covers.value = null
            viewModelScope.launchIO {
                try {
                    books.resetCover(p.tile.id)
                    note.value = "Back to the first cover."
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    note.value = "Couldn't change the cover."
                }
                reload()
            }
        }
    }
