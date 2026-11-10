package com.github.damontecres.wholphin.services

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One book on the Books door: a manga or comic series, an ebook or an audiobook. Comes from the Benflix helper
 * on Tower (benflix_books.py), which looks through Tower's Books, Audiobooks, Manga and Comics folders.
 */
data class BookTile(
    val id: String,
    /** book, audiobook, manga or comic */
    val kind: String,
    val name: String,
    val author: String,
    val series: String,
    val seriesIndex: Double?,
    val year: Int?,
    val cover: String?,
    val added: Long,
    /** not, reading or done */
    val status: String,
    val progress: Float,
    /** The small words under the cover, like "Vol 4 of 12" or "40%" */
    val line: String,
    val isNew: Boolean,
    /** New volumes since you last opened a manga series ("+2") */
    val newCount: Int,
    val want: Boolean,
    /** The same book the other way (the audiobook of an ebook, or the other way round) */
    val linked: String?,
    val readable: Boolean,
    val genres: List<String>,
    val lastAt: Long,
    val vols: Int,
    /** Not on Benflix yet (a Want to read you asked for) */
    val notHere: Boolean = false,
    /** Sent from the TV to read on the phone */
    val sent: Boolean = false,
) {
    val isManga: Boolean get() = kind == "manga" || kind == "comic"
    val isAudio: Boolean get() = kind == "audiobook"
}

data class BookRow(
    val key: String,
    val title: String,
    val tiles: List<BookTile>,
)

data class BooksHome(
    val rows: List<BookRow>,
    val counts: Map<String, Int>,
    val empty: Boolean,
    val canAsk: Boolean,
    val folders: List<String>,
)

data class BookLibrary(
    val titles: List<BookTile>,
    val counts: Map<String, Int>,
    val vols: Map<String, Int>,
)

data class BookVolume(
    val id: String,
    val name: String,
    val done: Boolean,
    val progress: Float,
    val current: Boolean,
    val cover: String?,
    val isNew: Boolean,
    val pdf: Boolean,
)

data class BookChapter(
    val title: String,
    val index: Int,
    val seconds: Int,
    /** Audiobooks: which file the chapter is in, where in that file it starts, and where in the whole book */
    val file: Int = 0,
    val start: Double = 0.0,
    val at: Double = 0.0,
)

data class BookScore(
    val site: String,
    val score: Double,
    val outOf: Int,
)

/** Everything on a book's own page */
data class BookPage(
    val tile: BookTile,
    val desc: String,
    val scores: List<BookScore>,
    val volumes: List<BookVolume>,
    val chapters: List<BookChapter>,
    /** The volume OK opens: the one you're on, else the first you haven't read */
    val next: String?,
    val narrator: String,
    val seconds: Int,
    val secondsLeft: Int,
    val missing: Int,
    val minutes: Int,
    val file: String,
    val otherId: String?,
    val otherKind: String?,
    val placeLine: String,
    val moreBy: List<BookTile>,
    val moreLike: List<BookTile>,
    val canPickCover: Boolean,
    val rtl: Boolean,
    val totalVols: Int,
    val placePct: Float,
    val placeChapter: Int,
    val placePos: Double,
    val placeFile: Int,
    val subjects: List<String>,
    /** Audiobooks: how long each file is, in seconds */
    val fileSeconds: List<Double> = emptyList(),
    val placeCpos: Double = 0.0,
)

/** A series or an author, with its books */
data class BookGroup(
    val name: String,
    val kind: String,
    val cover: String?,
    val line: String,
    val titles: List<BookTile>,
)

/** One volume to read in the manga reader */
data class BookVolInfo(
    val id: String,
    val titleId: String,
    val series: String,
    val name: String,
    val pages: Int,
    val pdf: Boolean,
    val rtl: Boolean,
    val mode: String?,
    val dir: String?,
    val placePage: Int,
    val nextId: String?,
    val nextName: String?,
    val nextCover: String?,
)

data class BookFound(
    val key: String,
    val name: String,
    val author: String,
    val year: Int?,
    val cover: String?,
    val score: Double?,
    val volumes: Int?,
    /** On Benflix already (its id) */
    val have: String?,
    /** Already asked for: its words, like "Downloading" */
    val asked: String?,
    val manga: Boolean,
)

data class BookRequest(
    val id: String,
    val key: String,
    val name: String,
    val author: String,
    val cover: String?,
    val types: List<String>,
    /** waiting, searching, downloading, ready, cant, manga, no, problem */
    val state: String,
    val words: String,
    val titleId: String?,
    val who: String,
    val canRetry: Boolean,
    val problem: String?,
)

data class BookRequests(
    val mine: List<BookRequest>,
    val waiting: List<BookRequest>,
    val others: List<BookRequest>,
    val mangaToAdd: List<BookRequest>,
    val connected: Boolean,
    val problems: List<String>,
)

data class CoverChoice(
    val url: String,
    val thumb: String,
    val label: String,
    val edition: String,
)

data class KidsBook(
    val id: String,
    val name: String,
    val author: String,
    val kind: String,
    val allowed: Boolean,
    val cover: String?,
)

data class BookNews(
    val id: String,
    val text: String,
    val titleId: String,
)

data class BookStats(
    /** Minutes read and listened for each of the last 7 days, oldest first */
    val days: List<Pair<Int, Int>>,
    val yearReadMinutes: Int,
    val yearListenMinutes: Int,
    val yearFinished: Int,
    val topAuthors: List<String>,
)

/**
 * Benflix Books: everything on the Books door comes from the Benflix helper on Tower (benflix_books.py):
 * the library, each book's page, your place in each book (saved per profile, so it follows you between
 * the TV, the phone and the browser), Want to read, and asking for books (Chaptarr behind the scenes).
 */
@Singleton
class BenflixBooks
    @Inject
    constructor(
        private val helper: GameLibraryClient,
    ) {
        /** A full address for a picture or file from the helper, with the sign-in in it (for images and players) */
        fun url(path: String?): String? {
            if (path.isNullOrBlank()) return null
            if (path.startsWith("http")) return path
            val sep = if (path.contains('?')) "&" else "?"
            return helper.address + path + sep + "api_key=" + Uri.encode(helper.token)
        }

        /** The ebook reader page (the same one the browser version uses) */
        fun readerUrl(
            id: String,
            tv: Boolean,
            chapter: Int?,
        ): String =
            helper.address + "/web/reader.html#id=" + Uri.encode(id) + "&token=" + Uri.encode(helper.token) +
                (if (tv) "&tv=1" else "") + (chapter?.let { "&chapter=$it" } ?: "")

        val address: String get() = helper.address
        val token: String get() = helper.token

        suspend fun home(): BooksHome {
            val j = helper.getJson("/api/books/home")
            return BooksHome(
                rows =
                    j.optJSONArray("rows").objects().map { r ->
                        BookRow(r.optString("key"), r.optString("title"), r.optJSONArray("tiles").objects().map(::tile))
                    },
                counts = counts(j.optJSONObject("counts")),
                empty = j.optBoolean("empty"),
                canAsk = j.optBoolean("requests"),
                folders =
                    j.optJSONObject("folders")?.let { f ->
                        f
                            .keys()
                            .asSequence()
                            .flatMap { k -> f.optJSONArray(k).strings().asSequence() }
                            .toList()
                    } ?: emptyList(),
            )
        }

        suspend fun library(): BookLibrary {
            val j = helper.getJson("/api/books/library")
            return BookLibrary(
                titles = j.optJSONArray("titles").objects().map(::tile),
                counts = counts(j.optJSONObject("counts")),
                vols = counts(j.optJSONObject("vols")),
            )
        }

        suspend fun series(): List<BookGroup> =
            helper
                .getJson("/api/books/series")
                .optJSONArray("series")
                .objects()
                .map(::group)

        suspend fun authors(): List<BookGroup> =
            helper
                .getJson("/api/books/authors")
                .optJSONArray("authors")
                .objects()
                .map(::group)

        suspend fun title(id: String): BookPage {
            val j = helper.getJson("/api/books/title/" + Uri.encode(id))
            val place = j.optJSONObject("place")
            return BookPage(
                tile = tile(j),
                desc = j.optString("desc"),
                scores =
                    j.optJSONArray("scores").objects().map {
                        BookScore(it.optString("site"), it.optDouble("score", 0.0), it.optInt("outOf", 10))
                    },
                volumes =
                    j.optJSONArray("volumes").objects().map {
                        BookVolume(
                            id = it.optString("id"),
                            name = it.optString("name"),
                            done = it.optBoolean("done"),
                            progress = it.optDouble("progress", 0.0).toFloat(),
                            current = it.optBoolean("current"),
                            cover = url(it.text("cover")),
                            isNew = it.optBoolean("new"),
                            pdf = it.optBoolean("pdf"),
                        )
                    },
                chapters =
                    j.optJSONArray("chapters").objects().map {
                        BookChapter(
                            title = it.optString("title"),
                            index = it.optInt("index"),
                            seconds = it.optInt("seconds"),
                            file = it.optInt("file"),
                            start = it.optDouble("start", 0.0),
                            at = it.optDouble("at", 0.0),
                        )
                    },
                next = j.text("next"),
                narrator = j.optString("narrator"),
                seconds = j.optInt("seconds"),
                secondsLeft = j.optInt("secondsLeft"),
                missing = j.optInt("missing"),
                minutes = j.optInt("minutes"),
                file = j.optString("file"),
                otherId = j.optJSONObject("other")?.text("id"),
                otherKind = j.optJSONObject("other")?.text("kind"),
                placeLine = j.optString("placeLine"),
                moreBy = j.optJSONArray("moreBy").objects().map(::tile),
                moreLike = j.optJSONArray("moreLike").objects().map(::tile),
                canPickCover = j.optBoolean("canPickCover"),
                rtl = j.optBoolean("rtl"),
                totalVols = j.optInt("totalVols"),
                placePct = place?.optDouble("pct", 0.0)?.toFloat() ?: 0f,
                placeChapter = place?.optInt("chapter", 0) ?: 0,
                placePos = place?.optDouble("pos", 0.0) ?: 0.0,
                placeFile = place?.optInt("file", 0) ?: 0,
                subjects = j.optJSONArray("subjects").strings(),
                fileSeconds =
                    j.optJSONArray("fileSeconds").let { a ->
                        if (a ==
                            null
                        ) {
                            emptyList()
                        } else {
                            (0 until a.length()).map { a.optDouble(it, 0.0) }
                        }
                    },
                placeCpos = place?.optDouble("cpos", 0.0) ?: 0.0,
            )
        }

        suspend fun vol(id: String): BookVolInfo {
            val j = helper.getJson("/api/books/vol/" + Uri.encode(id))
            val next = j.optJSONObject("next")
            val mode = j.optJSONObject("mode")
            return BookVolInfo(
                id = j.optString("id"),
                titleId = j.optString("title"),
                series = j.optString("series"),
                name = j.optString("name"),
                pages = j.optInt("pages"),
                pdf = j.optBoolean("pdf"),
                rtl = j.optBoolean("rtl"),
                mode = mode?.text("mode"),
                dir = mode?.text("dir"),
                placePage = j.optJSONObject("place")?.optInt("page", 0) ?: 0,
                nextId = next?.text("id"),
                nextName = next?.text("name"),
                nextCover = url(next?.text("cover")),
            )
        }

        /** A page picture of a manga volume ([width] 0 = full size) */
        fun pageUrl(
            volId: String,
            page: Int,
            width: Int = 0,
        ): String = url("/api/books/vol/$volId/p/$page" + (if (width > 0) "?w=$width" else ""))!!

        /** The file itself: an ebook, a PDF volume, or chapter [index] of an audiobook */
        fun fileUrl(
            id: String,
            index: Int? = null,
        ): String = url("/api/books/file/$id" + (index?.let { "/$it" } ?: ""))!!

        suspend fun whatNext(id: String): List<BookTile> =
            helper
                .getJson("/api/books/next/" + Uri.encode(id))
                .optJSONArray("picks")
                .objects()
                .map(::tile)

        /** Saves your place (and how long you read or listened since last time) */
        suspend fun savePlace(
            id: String,
            place: JSONObject,
        ) {
            helper.postJson("/api/books/place/" + Uri.encode(id), place)
        }

        suspend fun mark(
            id: String,
            done: Boolean,
            before: Boolean,
        ) {
            helper.postJson("/api/books/mark", JSONObject().put("id", id).put("done", done).put("before", before))
        }

        suspend fun want(
            id: String,
            want: Boolean,
            name: String = "",
            author: String = "",
            cover: String? = null,
            kind: String = "",
        ): Boolean =
            helper
                .postJson(
                    "/api/books/want",
                    JSONObject()
                        .put("id", id)
                        .put("want", want)
                        .put("name", name)
                        .put("author", author)
                        .put("cover", cover ?: JSONObject.NULL)
                        .put("kind", kind),
                ).optBoolean("want")

        suspend fun opened(id: String) {
            helper.postJson("/api/books/opened/" + Uri.encode(id))
        }

        suspend fun sendToPhone(id: String): String = helper.postJson("/api/books/send/" + Uri.encode(id)).optString("words", "Sent.")

        suspend fun news(): List<BookNews> =
            helper.getJson("/api/books/news").optJSONArray("news").objects().map {
                BookNews(it.optString("id"), it.optString("text"), it.optString("title"))
            }

        suspend fun stats(): BookStats {
            val j = helper.getJson("/api/books/stats")
            val y = j.optJSONObject("year") ?: JSONObject()
            return BookStats(
                days = j.optJSONArray("days").objects().map { it.optInt("read") to it.optInt("listen") },
                yearReadMinutes = y.optInt("readMinutes"),
                yearListenMinutes = y.optInt("listenMinutes"),
                yearFinished = y.optInt("finished"),
                topAuthors = j.optJSONArray("topAuthors").strings(),
            )
        }

        // ---------- Asking for books ----------

        suspend fun search(text: String): Pair<List<BookFound>, List<BookFound>> {
            val j = helper.getJson("/api/books/search?q=" + Uri.encode(text))
            return j.optJSONArray("books").objects().map { found(it, false) } to j.optJSONArray("manga").objects().map { found(it, true) }
        }

        suspend fun requests(): BookRequests {
            val j = helper.getJson("/api/books/requests")
            return BookRequests(
                mine = j.optJSONArray("mine").objects().map(::request),
                waiting = j.optJSONArray("waiting").objects().map(::request),
                others = j.optJSONArray("others").objects().map(::request),
                mangaToAdd = j.optJSONArray("mangaToAdd").objects().map(::request),
                connected = j.optBoolean("connected"),
                problems = j.optJSONArray("problems").strings(),
            )
        }

        /** [types] is any of "ebook" and "audiobook" (manga ignores it). Returns Tower's words. */
        suspend fun ask(
            found: BookFound,
            types: List<String>,
        ): String =
            helper
                .postJson(
                    "/api/books/request",
                    JSONObject()
                        .put("key", found.key)
                        .put("name", found.name)
                        .put("author", found.author)
                        .put("year", found.year ?: JSONObject.NULL)
                        .put("cover", found.cover ?: JSONObject.NULL)
                        .put("types", JSONArray(types)),
                ).optString("words", "Asked for.")

        suspend fun answer(
            id: String,
            yes: Boolean,
        ) {
            helper.postJson("/api/books/answer", JSONObject().put("id", id).put("yes", yes))
        }

        suspend fun retry(id: String) {
            helper.postJson("/api/books/retry/" + Uri.encode(id))
        }

        suspend fun forget(id: String) {
            helper.deleteJson("/api/books/request/" + Uri.encode(id))
        }

        // ---------- Ben's cover picker and the Kids list ----------

        suspend fun coverChoices(id: String): List<CoverChoice> =
            helper.getJson("/api/books/covers/" + Uri.encode(id)).optJSONArray("covers").objects().map {
                CoverChoice(it.optString("url"), it.text("thumb") ?: it.optString("url"), it.optString("label"), it.optString("edition"))
            }

        /** [all] = every volume or book of the series gets the same edition's cover */
        suspend fun pickCover(
            id: String,
            choice: CoverChoice,
            all: Boolean,
        ): String =
            helper
                .postJson(
                    "/api/books/cover",
                    JSONObject()
                        .put(
                            "id",
                            id,
                        ).put("url", choice.url)
                        .put("edition", choice.edition)
                        .put("scope", if (all) "all" else "one"),
                ).optString("words", "Cover changed.")

        suspend fun resetCover(id: String) {
            helper.deleteJson("/api/books/cover/" + Uri.encode(id))
        }

        suspend fun kidsList(): List<KidsBook> =
            helper.getJson("/api/books/kids").optJSONArray("titles").objects().map {
                KidsBook(
                    it.optString("id"),
                    it.optString("name"),
                    it.optString("author"),
                    it.optString("kind"),
                    it.optBoolean("allowed"),
                    url(it.text("cover")),
                )
            }

        suspend fun kidsAllow(
            id: String,
            allowed: Boolean,
        ) {
            helper.postJson("/api/books/kids", JSONObject().put("id", id).put("allowed", allowed))
        }

        suspend fun rescan(): String = helper.postJson("/api/books/rescan").optString("words")

        // ---------- reading the answers ----------

        private fun tile(j: JSONObject) =
            BookTile(
                id = j.optString("id"),
                kind = j.optString("kind", "book"),
                name = j.optString("name"),
                author = j.optString("author"),
                series = j.optString("series"),
                seriesIndex = if (j.isNull("seriesIndex")) null else j.optDouble("seriesIndex"),
                year = if (j.isNull("year")) null else j.optInt("year").takeIf { it > 0 },
                cover = url(j.text("cover")),
                added = j.optLong("added"),
                status = j.optString("status", "not"),
                progress = j.optDouble("progress", 0.0).toFloat(),
                line = j.optString("line"),
                isNew = j.optBoolean("new"),
                newCount = j.optInt("newCount"),
                want = j.optBoolean("want"),
                linked = j.text("linked"),
                readable = j.optBoolean("readable", true),
                genres = j.optJSONArray("genres").strings(),
                lastAt = j.optLong("lastAt"),
                vols = j.optInt("vols"),
                notHere = j.optBoolean("notHere"),
                sent = j.optBoolean("sent"),
            )

        private fun group(j: JSONObject) =
            BookGroup(
                name = j.optString("name"),
                kind = j.optString("kind"),
                cover = url(j.text("cover")),
                line = j.optString("line"),
                titles = j.optJSONArray("titles").objects().map(::tile),
            )

        private fun found(
            j: JSONObject,
            manga: Boolean,
        ) = BookFound(
            key = j.optString("key"),
            name = j.optString("name"),
            author = j.optString("author"),
            year = if (j.isNull("year")) null else j.optInt("year"),
            cover = j.text("cover"),
            score = if (j.isNull("score")) null else j.optDouble("score"),
            volumes = if (j.isNull("volumes")) null else j.optInt("volumes"),
            have = j.text("have"),
            asked = j.text("asked"),
            manga = manga,
        )

        private fun request(j: JSONObject) =
            BookRequest(
                id = j.optString("id"),
                key = j.optString("key"),
                name = j.optString("name"),
                author = j.optString("author"),
                cover = j.text("cover"),
                types = j.optJSONArray("types").strings(),
                state = j.optString("state"),
                words = j.optString("words"),
                titleId = j.text("title"),
                who = j.optString("who"),
                canRetry = j.optBoolean("canRetry"),
                problem = j.text("problem"),
            )

        private fun counts(j: JSONObject?): Map<String, Int> =
            j?.let { o -> o.keys().asSequence().associateWith { o.optInt(it) } } ?: emptyMap()
    }

private fun JSONObject.text(key: String): String? = if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() && it != "null" }

private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else (0 until length()).map { optString(it) }
