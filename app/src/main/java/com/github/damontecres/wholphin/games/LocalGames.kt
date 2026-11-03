package com.github.damontecres.wholphin.games

import android.content.Context
import android.content.Intent
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.services.GameLibraryClient
import com.github.damontecres.wholphin.services.GameLibraryException
import com.github.damontecres.wholphin.services.GameStreamReturn
import com.github.damontecres.wholphin.services.KidsMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/** A game Benflix can play on this device: from the server's list, or one already downloaded */
data class GameRef(
    val id: Int,
    val name: String,
    val slug: String,
    val consoleName: String,
    val coverUrl: String?,
    val size: Long = 0,
)

/** A game kept on this device */
data class DownloadedGame(
    val ref: GameRef,
    val startFile: String,
    val bytes: Long,
    val lastPlayed: Long,
    /** "Keep for trips": never cleared out to make space */
    val pinned: Boolean,
)

/** What's happening while a game gets ready, shown on its card */
data class GamePrep(
    val gameId: Int,
    val text: String,
    val fraction: Float? = null,
)

/** Everything [GameActivity] needs to start a game */
data class PreparedGame(
    val ref: GameRef,
    val console: LocalConsole,
    val core: File,
    val rom: File,
    val saveDir: File,
)

/**
 * Gets games ready to play on this TV or phone: downloads the emulator (from libretro), the game's files,
 * BIOS files and saves (from the games helper on Tower), keeps track of what's downloaded, and clears out
 * old games when space runs low.
 */
@Singleton
class LocalGames
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val client: GameLibraryClient,
        private val serverRepository: ServerRepository,
    ) {
        private val prefs = context.getSharedPreferences("benflix_local_games", Context.MODE_PRIVATE)
        private val http =
            OkHttpClient
                .Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()

        private val _prep = MutableStateFlow<GamePrep?>(null)
        val prep: StateFlow<GamePrep?> = _prep

        private var biosList: List<Pair<String, String>>? = null

        /**
         * Benflix: the Kids tile uses the grown-up's account, so its saves are kept apart on this device and
         * not sent to Tower (where they'd mix with the grown-up's saves).
         */
        private val kidsTile: Boolean get() = KidsMode.isActive

        val user: String get() =
            (serverRepository.currentUser?.id?.toString() ?: "guest").let { if (kidsTile) "$it-kids" else it }

        fun saveDir(gameId: Int): File = GameSaves.saveDir(context, user, gameId)

        /** True if there's a "pick up where you left off" save on this device */
        fun hasLocalResume(gameId: Int): Boolean = GameSaves.file(saveDir(gameId), "state").exists()

        /** True if Tower has a "pick up where you left off" save (null if Tower can't be reached) */
        suspend fun hasServerResume(gameId: Int): Boolean? =
            if (kidsTile) {
                false
            } else {
                try {
                    client.getJson("/api/save/$gameId").has("state")
                } catch (_: Exception) {
                    null
                }
            }

        // ---------- What's downloaded ----------

        fun downloaded(): List<DownloadedGame> =
            prefs.all.keys
                .filter { it.startsWith("game_") }
                .mapNotNull { key -> downloaded(key.removePrefix("game_").toIntOrNull() ?: return@mapNotNull null) }
                .sortedByDescending { it.lastPlayed }

        fun downloaded(id: Int): DownloadedGame? {
            val json = prefs.getString("game_$id", null) ?: return null
            return try {
                val j = JSONObject(json)
                val start = j.getString("start")
                if (!File(gameDir(id), start).exists()) return null
                DownloadedGame(
                    ref =
                        GameRef(
                            id = id,
                            name = j.optString("name"),
                            slug = j.optString("slug"),
                            consoleName = j.optString("console"),
                            coverUrl = j.optString("cover").ifBlank { null },
                            size = j.optLong("bytes"),
                        ),
                    startFile = start,
                    bytes = j.optLong("bytes"),
                    lastPlayed = j.optLong("played"),
                    pinned = j.optBoolean("pinned"),
                )
            } catch (_: Exception) {
                null
            }
        }

        private fun record(
            ref: GameRef,
            start: String,
            bytes: Long,
            pinned: Boolean,
        ) {
            val j =
                JSONObject()
                    .put("name", ref.name)
                    .put("slug", ref.slug)
                    .put("console", ref.consoleName)
                    .put("cover", ref.coverUrl ?: "")
                    .put("start", start)
                    .put("bytes", bytes)
                    .put("played", System.currentTimeMillis())
                    .put("pinned", pinned)
            prefs.edit().putString("game_${ref.id}", j.toString()).apply()
        }

        fun setPinned(
            id: Int,
            pinned: Boolean,
        ) {
            val g = downloaded(id) ?: return
            record(g.ref, g.startFile, g.bytes, pinned)
        }

        /** Deletes a downloaded game (its saves stay) */
        fun remove(id: Int) {
            gameDir(id).deleteRecursively()
            prefs.edit().remove("game_$id").apply()
        }

        private fun gameDir(id: Int) = File(GameSaves.romsDir(context), id.toString())

        // ---------- Getting a game ready ----------

        /** Downloads whatever is missing, then returns what [launch] needs. Throws [GameLibraryException]. */
        suspend fun prepare(
            ref: GameRef,
            pinned: Boolean? = null,
        ): PreparedGame =
            withContext(Dispatchers.IO) {
                val console =
                    LocalConsole.forSlug(ref.slug)
                        ?: throw GameLibraryException("${ref.consoleName} games can't play on this device yet.")
                try {
                    val core = ensureCore(ref, console)
                    ensureBios(ref, console)
                    val rom = ensureGame(ref, console, pinned)
                    syncSaves(ref)
                    PreparedGame(ref = ref, console = console, core = core, rom = rom, saveDir = saveDir(ref.id))
                } finally {
                    _prep.value = null
                }
            }

        private fun say(
            ref: GameRef,
            text: String,
            fraction: Float? = null,
        ) {
            _prep.value = GamePrep(ref.id, text, fraction)
        }

        private fun coreFile(console: LocalConsole) = File(GameSaves.coresDir(context), "${console.core}_libretro_android.so")

        /**
         * Benflix speed: quietly downloads the emulators for the consoles in the library that this device can
         * play, one at a time and only on Wi-Fi or a wired connection, so the first game of a console starts
         * straight away instead of waiting for its emulator.
         */
        suspend fun warmCores(slugs: Collection<String>) =
            withContext(Dispatchers.IO) {
                if (!onUnmeteredNetwork()) return@withContext
                val power = DevicePower.get(context)
                val wanted =
                    slugs
                        .mapNotNull { LocalConsole.forSlug(it) }
                        .filter { power.fit(it.power) != DevicePower.Fit.TOO_MUCH }
                        .distinctBy { it.core }
                        .filter { !coreFile(it).let { f -> f.exists() && f.length() > 0 } }
                for (console in wanted) {
                    try {
                        synchronized(lockFor(console)) {
                            if (!coreFile(console).let { f -> f.exists() && f.length() > 0 }) downloadCore(console)
                        }
                        Timber.i("Emulator ready ahead of time: %s", console.core)
                    } catch (e: Exception) {
                        Timber.w(e, "Getting emulator %s ahead of time failed", console.core)
                    }
                }
            }

        private fun onUnmeteredNetwork(): Boolean =
            try {
                val cm = context.getSystemService(android.net.ConnectivityManager::class.java)
                val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
                caps != null && caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            } catch (e: Exception) {
                false
            }

        /** One lock per emulator, so a game never waits for a different console's emulator to download */
        private val coreLocks = java.util.concurrent.ConcurrentHashMap<String, Any>()

        private fun lockFor(console: LocalConsole): Any = coreLocks.getOrPut(console.core) { Any() }

        /** The emulator, from the libretro buildbot (only the first time each one is needed) */
        private fun ensureCore(
            ref: GameRef,
            console: LocalConsole,
        ): File {
            val target = coreFile(console)
            if (target.exists() && target.length() > 0) return target
            say(ref, "Getting the ${console.title} player…")
            synchronized(lockFor(console)) {
                if (target.exists() && target.length() > 0) return target
                downloadCore(console)
            }
            if (!target.exists()) throw GameLibraryException("The ${console.title} player download was empty.")
            return target
        }

        private fun downloadCore(console: LocalConsole) {
            val target = coreFile(console)
            val url = "$BUILDBOT/${DevicePower.coreAbi()}/${console.core}_libretro_android.so.zip"
            try {
                http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw GameLibraryException(
                            "Couldn't download the ${console.title} player (${response.code}).",
                        )
                    }
                    ZipInputStream(response.body.byteStream()).use { zip ->
                        while (true) {
                            val entry = zip.nextEntry ?: break
                            if (entry.name.endsWith(".so")) {
                                val tmp = File(target.path + ".part")
                                tmp.outputStream().use { zip.copyTo(it) }
                                tmp.renameTo(target)
                                break
                            }
                        }
                    }
                }
            } catch (e: GameLibraryException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Downloading emulator ${console.core} failed")
                throw GameLibraryException("Couldn't download the ${console.title} player. Check this device has internet.")
            }
        }

        /** BIOS files from Games/bios on Tower, if it has any this console can use */
        private suspend fun ensureBios(
            ref: GameRef,
            console: LocalConsole,
        ) {
            if (console.bios.isEmpty()) return
            try {
                val list =
                    biosList ?: client
                        .getJson("/api/bios")
                        .optJSONArray("files")
                        ?.let { arr ->
                            (0 until arr.length()).map { arr.getJSONObject(it).let { f -> f.optString("name") to f.optString("path") } }
                        }.orEmpty()
                        .also { biosList = it }
                val system = GameSaves.systemDir(context)
                for ((name, path) in list) {
                    val lower = name.lowercase(Locale.US)
                    if (lower !in console.bios) continue
                    val target =
                        File(if (console.biosFolder.isEmpty()) system else File(system, console.biosFolder).apply { mkdirs() }, lower)
                    if (target.exists()) continue
                    say(ref, "Getting BIOS files…")
                    client.download("/api/bios/file?path=" + android.net.Uri.encode(path), target)
                }
            } catch (e: Exception) {
                Timber.i("BIOS files not fetched: ${e.message}")
            }
        }

        /** The game's files: already on this device, or downloaded now */
        private suspend fun ensureGame(
            ref: GameRef,
            console: LocalConsole,
            pinned: Boolean?,
        ): File {
            val dir = gameDir(ref.id)
            downloaded(ref.id)?.let { have ->
                record(have.ref.copy(coverUrl = ref.coverUrl ?: have.ref.coverUrl), have.startFile, have.bytes, pinned ?: have.pinned)
                return File(dir, have.startFile)
            }
            say(ref, "Getting ready…")
            val manifest = client.getJson("/api/rom/${ref.id}")
            val files =
                manifest.optJSONArray("files")?.let { arr ->
                    (0 until arr.length()).map { arr.getJSONObject(it).let { f -> f.getString("name") to f.optLong("size") } }
                } ?: throw GameLibraryException("Tower didn't list this game's files.")
            var start = manifest.optString("start").ifBlank { files.firstOrNull()?.first ?: "" }
            val total = files.sumOf { it.second }.coerceAtLeast(1)
            makeSpace(total, keep = ref.id)
            dir.mkdirs()
            var before = 0L
            for ((name, size) in files) {
                val target = File(dir, name)
                if (target.exists() && target.length() == size) {
                    before += size
                    continue
                }
                val startedAt = before
                client.download("/api/rom/${ref.id}/file?name=" + android.net.Uri.encode(name), target) { got ->
                    say(ref, "Downloading ${mb(startedAt + got)} of ${mb(total)}", ((startedAt + got).toFloat() / total).coerceIn(0f, 1f))
                }
                before += size
            }
            val lower = start.lowercase(Locale.US)
            if (lower.endsWith(".7z") || lower.endsWith(".rar")) {
                dir.deleteRecursively()
                throw GameLibraryException("This game is a .7z or .rar file. Re-pack it as .zip (or unzipped) in RomM to play it here.")
            }
            // PlayStation and arcade games stay zipped (arcade emulators read the .zip set itself)
            if (lower.endsWith(".zip") && console.key != "psx" && console.key != "arcade") {
                say(ref, "Unpacking…")
                start = unzipBiggest(File(dir, start), dir) ?: throw GameLibraryException("That game's .zip file looks empty.")
            }
            record(ref, start, dirSize(dir), pinned ?: false)
            return File(dir, start)
        }

        private fun unzipBiggest(
            zipFile: File,
            dir: File,
        ): String? {
            var bestName: String? = null
            var bestSize = -1L
            ZipInputStream(zipFile.inputStream().buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    val name = File(entry.name).name
                    if (name.endsWith(".txt", true) || name.endsWith(".nfo", true)) continue
                    val out = File(dir, "unzipped_$name")
                    out.outputStream().use { zip.copyTo(it) }
                    if (out.length() > bestSize) {
                        bestName?.let { File(dir, it).delete() }
                        bestName = out.name
                        bestSize = out.length()
                    } else {
                        out.delete()
                    }
                }
            }
            if (bestName != null) zipFile.delete()
            return bestName
        }

        /** Clears out the games played longest ago (not ones kept for trips) until [need] bytes fit */
        private fun makeSpace(
            need: Long,
            keep: Int,
        ) {
            val roms = GameSaves.romsDir(context)
            val reserve = 300L * 1024 * 1024

            fun cacheSize() = downloaded().filter { !it.pinned }.sumOf { it.bytes }
            val budget = minOf(4L * 1024 * 1024 * 1024, ((roms.usableSpace + cacheSize()) * 0.5).toLong())
            val oldest = downloaded().filter { !it.pinned && it.ref.id != keep }.sortedBy { it.lastPlayed }.toMutableList()
            while ((roms.usableSpace < need + reserve || cacheSize() + need > budget) && oldest.isNotEmpty()) {
                val g = oldest.removeAt(0)
                Timber.i("Clearing out ${g.ref.name} to make space")
                remove(g.ref.id)
            }
            if (roms.usableSpace < need + reserve) {
                throw GameLibraryException(
                    "Not enough space on this device for this game (it needs ${mb(need)}). Remove some apps, " +
                        "or games kept for trips, and try again.",
                )
            }
        }

        /** Takes Tower's saves when they're newer than this device's (for example, played on another TV) */
        private suspend fun syncSaves(ref: GameRef) {
            if (kidsTile) return
            val dir = saveDir(ref.id)
            val pending = GameSaves.pending(dir)
            // Anything this device still has to send goes first, so it isn't overwritten
            if (pending.isNotEmpty()) {
                GameSaves.upload(dir, ref.id, client.address, client.token)
                // Slots an older Tower helper doesn't know yet can wait; the main saves can't
                if (GameSaves.pending(dir).any { it in GameSaves.MAIN_KINDS }) return
            }
            val info =
                try {
                    client.getJson("/api/save/${ref.id}")
                } catch (_: Exception) {
                    return
                }
            for (kind in GameSaves.KINDS) {
                val server = info.optJSONObject(kind) ?: continue
                val time = server.optLong("time")
                val local = GameSaves.file(dir, kind)
                if (local.exists() && local.lastModified() >= time - 2000) continue
                say(ref, "Getting your saves…")
                try {
                    if (client.download("/api/save/${ref.id}/$kind", local)) local.setLastModified(time)
                } catch (e: Exception) {
                    Timber.i("Couldn't fetch the $kind save: ${e.message}")
                }
            }
        }

        /** Sends saves made while Tower couldn't be reached */
        suspend fun sendWaitingSaves() =
            withContext(Dispatchers.IO) {
                if (kidsTile) return@withContext
                try {
                    GameSaves.uploadAll(context, user, client.address, client.token)
                } catch (e: Exception) {
                    Timber.i("Waiting saves not sent: ${e.message}")
                }
            }

        /** Opens the game player */
        fun launch(
            from: Context,
            game: PreparedGame,
            resume: Boolean,
        ) {
            val intent =
                Intent(from, GameActivity::class.java)
                    .putExtra(GameActivity.EXTRA_CORE, game.core.absolutePath)
                    .putExtra(GameActivity.EXTRA_ROM, game.rom.absolutePath)
                    .putExtra(GameActivity.EXTRA_SYSTEM, GameSaves.systemDir(context).absolutePath)
                    .putExtra(GameActivity.EXTRA_SAVES, game.saveDir.absolutePath)
                    .putExtra(GameActivity.EXTRA_GAME_ID, game.ref.id)
                    .putExtra(GameActivity.EXTRA_NAME, game.ref.name)
                    .putExtra(GameActivity.EXTRA_CONSOLE, game.console.key)
                    .putExtra(GameActivity.EXTRA_RESUME, resume)
                    .putExtra(GameActivity.EXTRA_ADDRESS, client.address)
                    .putExtra(GameActivity.EXTRA_LOCAL_SAVES, kidsTile)
                    .putExtra(GameActivity.EXTRA_TOKEN, client.token)
                    .putExtra(GameActivity.EXTRA_SERVER, client.jellyfinUrl)
                    .putExtra(GameActivity.EXTRA_USER, client.userId)
                    .putExtra(GameActivity.EXTRA_COVER, game.ref.coverUrl ?: "")
            if (from !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            // Coming back from the game returns to the Games tab, not "Who's watching?"
            GameStreamReturn.pending = true
            from.startActivity(intent)
        }

        companion object {
            private const val BUILDBOT = "https://buildbot.libretro.com/nightly/android/latest"

            fun mb(bytes: Long): String =
                if (bytes >= 1024L * 1024 * 1024) {
                    "%.1f GB".format(Locale.US, bytes / 1_073_741_824.0)
                } else {
                    "${(bytes / 1_048_576L).coerceAtLeast(1)} MB"
                }

            private fun dirSize(dir: File): Long = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        }
    }
