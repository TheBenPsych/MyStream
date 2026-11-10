package com.github.damontecres.wholphin.ui.books

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.services.BenflixBooks
import com.github.damontecres.wholphin.services.BookVolInfo
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.ScreensaverService
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** One page, two pages side by side, or one long strip (approved: picked per series) */
enum class PageMode(
    val key: String,
    val label: String,
) {
    ONE("page", "One page"),
    TWO("spread", "Two pages"),
    SCROLL("scroll", "Long scroll"),
    ;

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: ONE
    }
}

data class PageReaderState(
    val loaded: Boolean = false,
    val name: String = "",
    val series: String = "",
    val pages: Int = 0,
    /** PDF pages are drawn on the phone or TV; manga pages are pictures from Tower */
    val pdf: Boolean = false,
    val rtl: Boolean = false,
    val mode: PageMode = PageMode.ONE,
    val startPage: Int = 0,
    val vol: BookVolInfo? = null,
    val problem: String? = null,
)

/**
 * The manga, comic and PDF reader: works out the pages, remembers the mode and direction for the series,
 * saves your page, and draws PDF pages (PdfRenderer) after fetching the file once.
 */
@HiltViewModel
class PageReaderViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        val books: BenflixBooks,
        val navigationManager: NavigationManager,
        val screensaver: ScreensaverService,
    ) : ViewModel() {
        private val _state = MutableStateFlow(PageReaderState())
        val state: StateFlow<PageReaderState> = _state
        private var id: String = ""
        private var book = false
        private var pdf: PdfRenderer? = null
        private var pdfFd: ParcelFileDescriptor? = null
        private val pdfLock = Mutex()
        private val bitmaps = LruCache<String, Bitmap>(PDF_CACHE)
        private var readSince = System.currentTimeMillis()
        private var lastSaved = -1

        fun load(
            id: String,
            book: Boolean,
        ) {
            if (this.id == id && _state.value.loaded) return
            this.id = id
            this.book = book
            viewModelScope.launchIO {
                try {
                    if (book) {
                        val page = books.title(id)
                        val n = openPdf(id)
                        _state.value =
                            PageReaderState(
                                loaded = true,
                                name = page.tile.name,
                                pages = n,
                                pdf = true,
                                startPage = (page.placePct * n).toInt().coerceIn(0, (n - 1).coerceAtLeast(0)),
                            )
                    } else {
                        val v = books.vol(id)
                        val n = if (v.pdf) openPdf(id) else v.pages
                        _state.value =
                            PageReaderState(
                                loaded = true,
                                name = v.name,
                                series = v.series,
                                pages = n,
                                pdf = v.pdf,
                                rtl = (v.dir ?: if (v.rtl) "rtl" else "ltr") == "rtl",
                                mode = PageMode.of(v.mode),
                                startPage = v.placePage.coerceIn(0, (n - 1).coerceAtLeast(0)),
                                vol = v,
                            )
                    }
                    books.opened(_state.value.vol?.titleId ?: id)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Page reader")
                    _state.value = PageReaderState(loaded = true, problem = ex.message ?: "Couldn't open it")
                }
            }
        }

        /** Fetches the PDF once (kept in the cache folder) and opens it */
        private suspend fun openPdf(id: String): Int =
            withContext(Dispatchers.IO) {
                val dir = File(context.cacheDir, "books").apply { mkdirs() }
                val file = File(dir, id.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".pdf")
                if (!file.exists() || file.length() == 0L) {
                    val tmp = File(dir, file.name + ".part")
                    val http = OkHttpClient.Builder().readTimeout(120, TimeUnit.SECONDS).build()
                    http.newCall(Request.Builder().url(books.fileUrl(id)).build()).execute().use { r ->
                        if (!r.isSuccessful) throw IllegalStateException("Tower said ${r.code}")
                        tmp.outputStream().use { out -> r.body.byteStream().copyTo(out) }
                    }
                    tmp.renameTo(file)
                    // Keep the cache small: only the 5 newest PDFs stay
                    dir
                        .listFiles { f ->
                            f.name.endsWith(".pdf")
                        }?.sortedByDescending { it.lastModified() }
                        ?.drop(5)
                        ?.forEach { it.delete() }
                }
                pdfLock.withLock {
                    val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    pdfFd = fd
                    val r = PdfRenderer(fd)
                    pdf = r
                    r.pageCount
                }
            }

        /** Draws PDF page [n] about [width] pixels wide */
        suspend fun pdfPage(
            n: Int,
            width: Int,
        ): Bitmap? =
            withContext(Dispatchers.IO) {
                bitmaps.get("$n-$width")?.let { return@withContext it }
                pdfLock.withLock {
                    val r = pdf ?: return@withLock null
                    if (n !in 0 until r.pageCount) return@withLock null
                    val pg = r.openPage(n)
                    try {
                        val w = width.coerceIn(400, 2000)
                        val h = (w.toFloat() * pg.height / pg.width).toInt().coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(android.graphics.Color.WHITE)
                        pg.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmaps.put("$n-$width", bmp)
                        bmp
                    } finally {
                        pg.close()
                    }
                }
            }

        fun pageUrl(
            n: Int,
            width: Int,
        ): String = books.pageUrl(id, n, width)

        fun setMode(
            mode: PageMode,
            rtl: Boolean,
        ) {
            _state.value = _state.value.copy(mode = mode, rtl = rtl)
            viewModelScope.launchIO {
                try {
                    books.savePlace(
                        id,
                        JSONObject().put("readerMode", JSONObject().put("mode", mode.key).put("dir", if (rtl) "rtl" else "ltr")),
                    )
                } catch (ex: Exception) {
                    Timber.w(ex, "Reader mode")
                }
            }
        }

        /** Saves the page you're on (after each turn, a moment later) */
        fun save(
            page: Int,
            finished: Boolean = false,
        ) {
            val st = _state.value
            if (st.pages <= 0 || (page == lastSaved && !finished)) return
            lastSaved = page
            val secs = ((System.currentTimeMillis() - readSince) / 1000).toInt()
            readSince = System.currentTimeMillis()
            val pct = if (finished || page >= st.pages - 1) 1.0 else page.toDouble() / st.pages
            viewModelScope.launchIO {
                try {
                    withContext(NonCancellable) {
                        books.savePlace(
                            id,
                            JSONObject()
                                .put("page", page)
                                .put("pages", st.pages)
                                .put("pct", pct)
                                .put("seconds", secs.coerceAtMost(SECONDS_CAP))
                                .put("finished", finished || pct >= 1.0),
                        )
                    }
                } catch (ex: Exception) {
                    Timber.w(ex, "Couldn't save the page")
                }
            }
        }

        fun openNext(nextId: String) {
            navigationManager.goBack()
            navigationManager.navigateTo(Destination.PageReader(nextId))
        }

        override fun onCleared() {
            super.onCleared()
            try {
                pdf?.close()
                pdfFd?.close()
            } catch (ex: Exception) {
                Timber.d(ex, "PDF close")
            }
            bitmaps.evictAll()
        }

        companion object {
            private const val PDF_CACHE = 24
            private const val SECONDS_CAP = 3000
        }
    }
