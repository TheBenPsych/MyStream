package com.github.damontecres.wholphin.services

import android.content.Context
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** One manga chapter saved on this phone (or still coming down) */
data class MangaSaved(
    val volId: String,
    val titleId: String,
    val series: String,
    /** "Chapter 12" */
    val name: String,
    val num: Double,
    val pages: Int,
    /** How many pages are on the phone so far */
    val got: Int,
    val rtl: Boolean,
    val webtoon: Boolean,
    val userId: String,
    /** The page you were on, for reading with no internet */
    val page: Int = 0,
    val failed: Boolean = false,
    val unit: String = "ch",
) {
    val done: Boolean get() = pages > 0 && got >= pages
}

/**
 * Glowfin on phones (Ben 2026-11-21): manga chapters saved for reading with no internet. Tower shrinks each page
 * to phone size first, so a chapter is a few megabytes. Chapters come down one at a time, in order; anything
 * unfinished carries on the next time the app opens. Kept in the app's own folder (gone when the app is removed).
 */
@Singleton
class MangaOffline
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val books: BenflixBooks,
        private val serverRepository: ServerRepository,
    ) {
        private val dir = File(context.filesDir, "manga")
        private val index = File(dir, "index.json")
        private val scope = CoroutineScope(SupervisorJob() + WholphinDispatchers.IO)
        private val todo = Channel<String>(Channel.UNLIMITED)
        private val http =
            OkHttpClient
                .Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)
                .build()
        private val _items = MutableStateFlow(read())
        val items: StateFlow<List<MangaSaved>> = _items

        init {
            scope.launch { for (id in todo) fetch(id) }
            // Anything that didn't finish last time carries on
            _items.value.filter { !it.done }.forEach { todo.trySend(it.volId) }
        }

        fun entry(volId: String): MangaSaved? = _items.value.firstOrNull { it.volId == volId }

        /** The saved picture of page [n], or null when it isn't on the phone */
        fun pageFile(
            volId: String,
            n: Int,
        ): File? = File(File(dir, safe(volId)), "$n.jpg").takeIf { it.isFile && it.length() > 0 }

        fun coverFile(titleId: String): File = File(dir, safe(titleId) + "-cover.jpg")

        /** The chapter after [volId] in the same series, if it's saved too */
        fun nextSaved(volId: String): MangaSaved? {
            val me = entry(volId) ?: return null
            return _items.value
                .filter { it.titleId == me.titleId && it.userId == me.userId && it.unit == me.unit && it.num > me.num && it.done }
                .minByOrNull { it.num }
        }

        /** Saves these chapters of [page]'s series (ones already saved are skipped) */
        fun save(
            page: BookPage,
            vols: List<BookVolume>,
        ) {
            val userId = serverRepository.currentUser?.id?.toString() ?: return
            val have = _items.value.map { it.volId }.toSet()
            val fresh =
                vols.filter { it.id !in have && !it.pdf }.map {
                    MangaSaved(
                        volId = it.id,
                        titleId = page.tile.id,
                        series = page.tile.name,
                        name = it.name,
                        num = it.num,
                        pages = 0,
                        got = 0,
                        rtl = page.rtl,
                        webtoon = page.webtoon,
                        userId = userId,
                        unit = page.unit.ifBlank { "ch" },
                    )
                }
            _items.update { it + fresh }
            write()
            fresh.forEach { todo.trySend(it.volId) }
            // Ones that failed before get another go
            _items.value.filter { it.failed && it.titleId == page.tile.id }.forEach { f ->
                change(f.volId) { it.copy(failed = false) }
                todo.trySend(f.volId)
            }
        }

        fun delete(volId: String) {
            _items.update { list -> list.filterNot { it.volId == volId } }
            write()
            scope.launch { File(dir, safe(volId)).deleteRecursively() }
        }

        fun deleteSeries(titleId: String) {
            val gone = _items.value.filter { it.titleId == titleId }
            _items.update { list -> list.filterNot { it.titleId == titleId } }
            write()
            scope.launch {
                gone.forEach { File(dir, safe(it.volId)).deleteRecursively() }
                coverFile(titleId).delete()
            }
        }

        /** Remembers the page you're on, so a chapter read with no internet opens where you left it */
        fun setPage(
            volId: String,
            page: Int,
        ) {
            if (entry(volId)?.page == page) return
            change(volId) { it.copy(page = page) }
            write()
        }

        fun bytesUsed(list: List<MangaSaved> = _items.value): Long =
            list.sumOf { s ->
                File(dir, safe(s.volId)).listFiles()?.sumOf { it.length() } ?: 0L
            }

        private suspend fun fetch(volId: String) {
            val start = entry(volId) ?: return
            if (start.done) return
            try {
                val info = books.vol(volId)
                if (info.pdf || info.pages <= 0) throw IllegalStateException("No pages to save")
                change(volId) { it.copy(pages = info.pages, failed = false) }
                val folder = File(dir, safe(volId)).apply { mkdirs() }
                val cover = coverFile(start.titleId)
                if (!cover.isFile) {
                    runCatching { get(books.url("/api/books/cover/${start.titleId}?w=320")!!, cover) }
                }
                for (n in 0 until info.pages) {
                    // Deleted while it was coming down
                    if (entry(volId) == null) {
                        folder.deleteRecursively()
                        return
                    }
                    val file = File(folder, "$n.jpg")
                    if (!file.isFile || file.length() == 0L) get(books.pageUrl(volId, n, SAVE_WIDTH, info.stamp), file)
                    change(volId) { it.copy(got = n + 1) }
                }
                write()
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Manga download %s", volId)
                change(volId) { it.copy(failed = true) }
                write()
            }
        }

        private fun get(
            url: String,
            dest: File,
        ) {
            val part = File(dest.parentFile, dest.name + ".part")
            http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (!r.isSuccessful) throw IllegalStateException("Tower said ${r.code}")
                part.outputStream().use { out -> r.body.byteStream().copyTo(out) }
            }
            if (!part.renameTo(dest)) throw IllegalStateException("Couldn't save the page")
        }

        private fun change(
            volId: String,
            block: (MangaSaved) -> MangaSaved,
        ) = _items.update { list -> list.map { if (it.volId == volId) block(it) else it } }

        private fun safe(id: String) = id.replace(Regex("[^A-Za-z0-9_-]"), "_")

        private fun read(): List<MangaSaved> =
            try {
                val a = JSONArray(index.readText())
                (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { j ->
                    MangaSaved(
                        volId = j.optString("volId"),
                        titleId = j.optString("titleId"),
                        series = j.optString("series"),
                        name = j.optString("name"),
                        num = j.optDouble("num", 0.0),
                        pages = j.optInt("pages"),
                        got = j.optInt("got"),
                        rtl = j.optBoolean("rtl"),
                        webtoon = j.optBoolean("webtoon"),
                        userId = j.optString("userId"),
                        page = j.optInt("page"),
                        unit = j.optString("unit", "ch"),
                    )
                }
            } catch (ex: Exception) {
                emptyList()
            }

        @Synchronized
        private fun write() {
            try {
                dir.mkdirs()
                val a = JSONArray()
                _items.value.forEach { s ->
                    a.put(
                        JSONObject()
                            .put("volId", s.volId)
                            .put("titleId", s.titleId)
                            .put("series", s.series)
                            .put("name", s.name)
                            .put("num", s.num)
                            .put("pages", s.pages)
                            .put("got", s.got)
                            .put("rtl", s.rtl)
                            .put("webtoon", s.webtoon)
                            .put("userId", s.userId)
                            .put("page", s.page)
                            .put("unit", s.unit),
                    )
                }
                val tmp = File(dir, "index.json.tmp")
                tmp.writeText(a.toString())
                tmp.renameTo(index)
            } catch (ex: Exception) {
                Timber.w(ex, "Couldn't save the manga downloads list")
            }
        }

        companion object {
            /** Wide enough to pinch into on a phone, small enough that a chapter is a few megabytes */
            private const val SAVE_WIDTH = 1100
        }
    }
