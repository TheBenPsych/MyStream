package com.github.damontecres.wholphin.services

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.github.damontecres.wholphin.BuildConfig
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.services.UpdateChecker.Companion.ASSET_NAME
import com.github.damontecres.wholphin.services.hilt.StandardOkHttpClient
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.util.Version
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

/**
 * Where Benflix checks for new builds
 */
const val BENFLIX_UPDATE_URL = "https://api.github.com/repos/TheBenPsych/MyStream/releases/tags/latest-build"

/**
 * Installs set up before Benflix still have Wholphin's update address saved, so send those to Benflix's builds
 */
fun benflixUpdateUrl(updateUrl: String): String =
    if (updateUrl.isBlank() || updateUrl.contains("damontecres/Wholphin", ignoreCase = true)) {
        BENFLIX_UPDATE_URL
    } else {
        updateUrl
    }

/**
 * Checks if an app update is available
 */
@Singleton
class UpdateChecker
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        @param:StandardOkHttpClient private val okHttpClient: OkHttpClient,
    ) {
        companion object {
            const val ASSET_NAME = "Benflix"
            const val APK_NAME = "$ASSET_NAME.apk"

            private const val APK_MIME_TYPE = "application/vnd.android.package-archive"

            private val NOTE_REGEX = Regex("<!-- app-note:(.+) -->")

            val ACTIVE = BuildConfig.UPDATING_ENABLED

            /** The version the "New update ready" tip last showed for, and when */
            private const val KEY_SNOOZE_VERSION = "benflix_update_snoozed_version"
            private const val KEY_SNOOZE_AT = "benflix_update_snoozed_at"

            /** The update that was downloaded quietly ahead of time: its version, where it is, and when */
            private const val KEY_READY_VERSION = "benflix_update_ready_version"
            private const val KEY_READY_URI = "benflix_update_ready_uri"
            private const val KEY_READY_AT = "benflix_update_ready_at"
            private const val KEY_READY_SIZE = "benflix_update_ready_size"

            /** When this TV or phone first saw an update for the version it has (used when the build history is missing) */
            private const val KEY_FIRST_SEEN_FOR = "benflix_update_first_seen_for"
            private const val KEY_FIRST_SEEN_AT = "benflix_update_first_seen_at"

            /** A quiet download is replaced by a newer build at most this often, so it isn't downloading all day */
            private val READY_REFRESH = 1.days
        }

        private val prefs get() = PreferenceManager.getDefaultSharedPreferences(context)

        /** Only one download at a time (the quiet one and the one from the update page share the same file) */
        private val downloadLock = Mutex()

        private val _prompt = MutableStateFlow<Release?>(null)

        /** A newer build that is ready to install, or null when there is nothing new */
        val prompt: StateFlow<Release?> = _prompt

        /** Set when an Update button was pressed, so the update page starts installing right away */
        var installWhenOpened = false

        /**
         * If the app hasn't recently checked, check for any updates and if there is one, show a toast message
         *
         * This is safe to call many times because it will only show the toast at most once every 12 hours
         */
        suspend fun maybeShowUpdateToast(
            updateUrl: String,
            showNegativeToast: Boolean = false,
            isPlaying: () -> Boolean = { false },
        ) {
            val pref = PreferenceManager.getDefaultSharedPreferences(context)
            val now = Date()
            val lastUpdateCheck =
                pref.getLong(
                    context.getString(R.string.pref_key_update_last_check),
                    0,
                )
            val timeSince = (now.time - lastUpdateCheck).milliseconds
            Timber.v("Last successful update check was $timeSince ago")
            val installedVersion = getInstalledVersion()
            val latestRelease = getLatestRelease(updateUrl)
            if (latestRelease != null && latestRelease.version.isGreaterThan(installedVersion)) {
                Timber.v("Update available $installedVersion => ${latestRelease.version}")
                pref.edit {
                    putLong(context.getString(R.string.pref_key_update_last_check), now.time)
                }
                // Benflix: no pop-up. A red "!" shows the way to Settings > Help, and a tip says so
                // for a few seconds (at most once a day for the same version).
                val version = latestRelease.version.toString()
                val tipVersion = pref.getString(KEY_SNOOZE_VERSION, null)
                val tipAgo = (now.time - pref.getLong(KEY_SNOOZE_AT, 0)).milliseconds
                val showTip = tipVersion != version || tipAgo >= 24.hours
                if (showTip) {
                    pref.edit {
                        putString(KEY_SNOOZE_VERSION, version)
                        putLong(KEY_SNOOZE_AT, now.time)
                    }
                }
                _prompt.value = latestRelease
                com.github.damontecres.wholphin.ui.components.UpdateNotice
                    .set(version, showTip, daysBehind(latestRelease, installedVersion))
                // Benflix: get the update ready ahead of time, so updating later is one press
                try {
                    downloadQuietly(latestRelease, isPlaying)
                } catch (ex: kotlinx.coroutines.CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Quiet update download didn't finish, it will try again next time")
                }
            } else {
                Timber.v("No update available for $installedVersion")
                com.github.damontecres.wholphin.ui.components.UpdateNotice
                    .set(null, false, 0)
                if (showNegativeToast) {
                    showToast(
                        context,
                        "No updates available, $installedVersion is the latest!",
                        Toast.LENGTH_LONG,
                    )
                }
            }
        }

        /**
         * Get the currently installed version
         */
        fun getInstalledVersion(): Version {
            val pkgInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            return Version.fromString(pkgInfo.versionName!!)
        }

        suspend fun getRelease(version: Version): Release? {
            // Benflix only publishes the newest build, so show that build's notes
            val url = BENFLIX_UPDATE_URL
            return withContext(WholphinDispatchers.IO) {
                val request =
                    Request
                        .Builder()
                        .url(url)
                        .get()
                        .build()
                getRelease(request)
            }
        }

        /**
         * Get the latest released version
         */
        suspend fun getLatestRelease(updateUrl: String): Release? =
            withContext(WholphinDispatchers.IO) {
                val request =
                    Request
                        .Builder()
                        .url(benflixUpdateUrl(updateUrl))
                        .get()
                        .build()
                getRelease(request)
            }

        private fun getRelease(request: Request): Release? {
            return okHttpClient.newCall(request).execute().use {
                if (it.isSuccessful) {
                    val result = Json.parseToJsonElement(it.body.string())
                    val name = result.jsonObject["name"]?.jsonPrimitive?.contentOrNull
                    val version = Version.tryFromString(name)
                    val publishedAt =
                        result.jsonObject["published_at"]?.jsonPrimitive?.contentOrNull
                    val body = result.jsonObject["body"]?.jsonPrimitive?.contentOrNull
                    val downloadUrl =
                        result.jsonObject["assets"]
                            ?.jsonArray
                            ?.let { assets -> getDownloadUrl(assets, BuildConfig.DEBUG) }
                    Timber.v("version=$version, downloadUrl=$downloadUrl")
                    if (version != null) {
                        val notes =
                            if (body.isNotNullOrBlank()) {
                                NOTE_REGEX
                                    .findAll(body)
                                    .map { m ->
                                        m.groupValues[1]
                                    }.toList()
                            } else {
                                emptyList()
                            }
                        val changes = if (body.isNotNullOrBlank()) parseReleaseChanges(body) else emptyList()
                        return@use Release(version, downloadUrl, publishedAt, body, notes, changes)
                    } else {
                        Timber.w("Update version parsing failed. name=$name")
                    }
                } else {
                    Timber.w("Update check failed ${it.code}: ${it.message}")
                }
                return@use null
            }
        }

        /**
         * Download an update and return where it was saved, ready for [installIntent].
         *
         * The installer is opened by the update page itself (from the screen you're looking at), not from here:
         * on some Android TVs a launch from the background download was quietly ignored the first time,
         * so the page sat there and you had to download again.
         */
        suspend fun downloadRelease(
            release: Release,
            callback: DownloadCallback,
        ): Uri =
            withContext(WholphinDispatchers.IO) {
                downloadLock.withLock {
                    // Already downloaded quietly ahead of time, so there's nothing to wait for
                    readyDownload(release.version)?.let { return@withLock it }
                    downloading = true
                    try {
                        downloadReleaseFile(release, callback).also { saveReady(release.version, it) }
                    } finally {
                        downloading = false
                    }
                }
            }

        /**
         * Benflix: download the update in the background so it's ready in one press. Only on Wi-Fi or a cable
         * (not mobile data), never while something is playing (it stops if a show starts), and a waiting
         * download is swapped for a newer build at most once a day.
         */
        private suspend fun downloadQuietly(
            release: Release,
            isPlaying: () -> Boolean,
        ) {
            if (release.downloadUrl == null) return
            // Older Android needs a permission to save the file, which is only asked for on the update page
            if (!hasPermissions()) return
            if (isPlaying() || isMeteredNetwork()) return
            if (readyDownload(release.version) != null) return
            val readyVersion = prefs.getString(KEY_READY_VERSION, null)?.let { Version.tryFromString(it) }
            val readyAgo = (System.currentTimeMillis() - prefs.getLong(KEY_READY_AT, 0)).milliseconds
            if (readyVersion != null && readyVersion.isGreaterThan(getInstalledVersion()) &&
                readyAgo < READY_REFRESH && readyDownload(readyVersion) != null
            ) {
                return
            }
            withContext(WholphinDispatchers.IO) {
                downloadLock.withLock {
                    if (readyDownload(release.version) != null) return@withLock
                    downloading = true
                    try {
                        Timber.i("Downloading update %s quietly", release.version)
                        val uri =
                            downloadReleaseFile(
                                release,
                                object : DownloadCallback {
                                    override fun contentLength(contentLength: Long) = Unit

                                    override fun bytesDownloaded(bytes: Long) {
                                        // A show started: stop, so the download doesn't get in its way
                                        if (isPlaying()) throw IOException("Stopped the quiet download for playback")
                                    }
                                },
                            )
                        saveReady(release.version, uri)
                        Timber.i("Update %s is downloaded and ready", release.version)
                    } catch (ex: Exception) {
                        // Don't leave half a file behind
                        clearReady()
                        cleanup()
                        throw ex
                    } finally {
                        downloading = false
                    }
                }
            }
        }

        private fun isMeteredNetwork(): Boolean =
            try {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                cm.isActiveNetworkMetered
            } catch (ex: Exception) {
                true
            }

        private fun saveReady(
            version: Version,
            uri: Uri,
        ) {
            prefs.edit {
                putString(KEY_READY_VERSION, version.toString())
                putString(KEY_READY_URI, uri.toString())
                putLong(KEY_READY_AT, System.currentTimeMillis())
                putLong(KEY_READY_SIZE, fileSize(uri))
            }
        }

        private fun clearReady() {
            prefs.edit {
                remove(KEY_READY_VERSION)
                remove(KEY_READY_URI)
                remove(KEY_READY_AT)
                remove(KEY_READY_SIZE)
            }
        }

        /**
         * The finished download of [version], if it's still there, whole and readable, otherwise null.
         * A file that changed size since it was downloaded (cut short or half overwritten) is thrown away, so it's
         * downloaded fresh instead of Android saying "There was a problem parsing the package".
         */
        fun readyDownload(version: Version): Uri? {
            if (prefs.getString(KEY_READY_VERSION, null) != version.toString()) return null
            val uri = prefs.getString(KEY_READY_URI, null)?.let { Uri.parse(it) } ?: return null
            return try {
                val ok = context.contentResolver.openInputStream(uri)?.use { it.read() >= 0 } == true
                val savedSize = prefs.getLong(KEY_READY_SIZE, -1L)
                val size = fileSize(uri)
                when {
                    !ok -> null
                    savedSize > 0 && size > 0 && size != savedSize -> {
                        Timber.w("The ready update file changed size (%d, expected %d), downloading again", size, savedSize)
                        clearReady()
                        null
                    }
                    else -> uri
                }
            } catch (ex: Exception) {
                Timber.w(ex, "The ready update file is gone")
                null
            }
        }

        /** The size of a downloaded file in bytes, or -1 if Android doesn't say */
        private fun fileSize(uri: Uri): Long =
            try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
            } catch (ex: Exception) {
                -1L
            }

        /** Stop if the download came out shorter or longer than GitHub said, instead of keeping a broken file */
        private fun checkComplete(
            copied: Long,
            expected: Long,
        ) {
            if (expected > 0 && copied != expected) {
                throw IOException("The update download stopped early ($copied of $expected bytes)")
            }
        }

        /**
         * How many days this TV or phone has been missing updates: since the first build newer than the one
         * installed came out (from the build history), or since an update was first seen here.
         */
        fun daysBehind(
            release: Release,
            installed: Version,
        ): Int {
            val now = System.currentTimeMillis()
            val firstNewer =
                release.changes
                    .filter { it.version.isGreaterThan(installed) }
                    .mapNotNull { c ->
                        try {
                            c.publishedAt?.let { Instant.parse(it).toEpochMilli() }
                        } catch (ex: Exception) {
                            null
                        }
                    }.minOrNull()
            val seenFor = prefs.getString(KEY_FIRST_SEEN_FOR, null)
            if (seenFor != installed.toString()) {
                prefs.edit {
                    putString(KEY_FIRST_SEEN_FOR, installed.toString())
                    putLong(KEY_FIRST_SEEN_AT, now)
                }
            }
            val since = firstNewer ?: prefs.getLong(KEY_FIRST_SEEN_AT, now)
            return ((now - since).coerceAtLeast(0L) / 1.days.inWholeMilliseconds).toInt()
        }

        /** True while an update is downloading, so the leftover-file cleanup doesn't delete it */
        @Volatile
        private var downloading = false

        /** Delete the update file from the last update, unless an update is downloading right now */
        fun cleanupLeftoverDownload() {
            if (downloading) return
            // Keep an update that was downloaded ahead of time and isn't installed yet
            val ready = prefs.getString(KEY_READY_VERSION, null)?.let { Version.tryFromString(it) }
            if (ready != null && ready.isGreaterThan(getInstalledVersion())) return
            clearReady()
            cleanup()
        }

        private suspend fun downloadReleaseFile(
            release: Release,
            callback: DownloadCallback,
        ): Uri =
            run {
                cleanup()
                val request =
                    Request
                        .Builder()
                        .url(release.downloadUrl!!)
                        .get()
                        .build()
                okHttpClient.newCall(request).execute().use {
                    if (!it.isSuccessful) {
                        Timber.v("Request failed for ${release.downloadUrl}: ${it.code}")
                        // Usually a 404 while a new build is being published, so the caller tries again
                        throw DownloadFailedException(it.code)
                    }
                    Timber.v("Request successful for ${release.downloadUrl}")
                    callback.contentLength(it.body.contentLength())
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val resolver = context.contentResolver
                        val contentValues =
                            ContentValues().apply {
                                put(MediaStore.MediaColumns.DISPLAY_NAME, APK_NAME)
                                put(MediaStore.MediaColumns.MIME_TYPE, APK_MIME_TYPE)
                                put(
                                    MediaStore.MediaColumns.RELATIVE_PATH,
                                    Environment.DIRECTORY_DOWNLOADS,
                                )
                                // Hidden until the whole file is written, so nothing reads half a file
                                put(MediaStore.MediaColumns.IS_PENDING, 1)
                            }
                        val uri =
                            resolver.insert(
                                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                                contentValues,
                            )
                        if (uri != null) {
                            try {
                                val copied =
                                    it.body.byteStream().use { input ->
                                        resolver.openOutputStream(uri, "wt").use { output ->
                                            copyTo(input, output!!, callback = callback)
                                        }
                                    }
                                checkComplete(copied, it.body.contentLength())
                            } catch (ex: Exception) {
                                try {
                                    resolver.delete(uri, null, null)
                                } catch (deleteEx: Exception) {
                                    Timber.w(deleteEx, "Could not delete the partial download")
                                }
                                throw ex
                            }
                            resolver.update(
                                uri,
                                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                                null,
                                null,
                            )
                            return@use uri
                        }
                        Timber.e("Resolver URI is null, trying fallback")
                        val targetFile = fallbackDownload(it, callback)
                        return@use FileProvider.getUriForFile(
                            context,
                            context.packageName + ".provider",
                            targetFile,
                        )
                    }
                    val targetFile = fallbackDownload(it, callback)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        FileProvider.getUriForFile(
                            context,
                            context.packageName + ".provider",
                            targetFile,
                        )
                    } else {
                        Uri.fromFile(targetFile)
                    }
                }
            }

        /**
         * The intent that opens Android's install screen for a file from [downloadRelease].
         * [plainView] is a second way to open it, for TVs where the first one doesn't show up.
         */
        @SuppressLint("RequestInstallPackagesPolicy")
        fun installIntent(
            uri: Uri,
            plainView: Boolean = false,
        ): Intent =
            if (plainView || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, APK_MIME_TYPE)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } else {
                Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                    data = uri
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                    putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, context.packageName)
                }
            }

        private suspend fun fallbackDownload(
            response: Response,
            callback: DownloadCallback,
        ): File {
            val downloadDir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            downloadDir.mkdirs()
            val targetFile = File(downloadDir, APK_NAME)
            try {
                val copied =
                    targetFile.outputStream().use { output ->
                        response.body.byteStream().use { input ->
                            copyTo(input, output, callback = callback)
                        }
                    }
                checkComplete(copied, response.body.contentLength())
            } catch (ex: Exception) {
                // Don't leave half a file where the installer could pick it up
                targetFile.delete()
                throw ex
            }
            return targetFile
        }

        /**
         * Whether Android lets Benflix open the installer ("Install unknown apps" switched on for Benflix)
         */
        fun canInstallPackages(): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                context.packageManager.canRequestPackageInstalls()

        /**
         * Check if the app has permission to write the download
         */
        fun hasPermissions(): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
                (
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    ) == PackageManager.PERMISSION_GRANTED &&
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                        ) == PackageManager.PERMISSION_GRANTED
                )

        /**
         * Delete previously downloaded APKs
         */
        fun cleanup() {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    context.contentResolver
                        .query(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                            arrayOf(
                                MediaStore.MediaColumns._ID,
                                MediaStore.Files.FileColumns.DISPLAY_NAME,
                            ),
                            "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ? AND ${MediaStore.MediaColumns.MIME_TYPE} = ?",
                            arrayOf(context.getString(R.string.app_name) + "%", APK_MIME_TYPE),
                            null,
                        )?.use { cursor ->
                            while (cursor.moveToNext()) {
                                val id = cursor.getString(0)
                                val displayName = cursor.getString(1)
                                Timber.v("id=$id, displayName=$displayName")
                            }
                        }
                    val deletedRows =
                        context.contentResolver.delete(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                            "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ? AND ${MediaStore.MediaColumns.MIME_TYPE} = ?",
                            arrayOf("$ASSET_NAME%", APK_MIME_TYPE),
                        )
                    Timber.i("Deleted $deletedRows rows")
                } else {
                    val downloadDir =
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val targetFile = File(downloadDir, APK_NAME)
                    if (targetFile.exists()) {
                        targetFile.delete()
                    }
                }
            } catch (ex: Exception) {
                Timber.e(ex, "Exception during cleanup")
            }
        }
    }

/** One change (a merged pull request's title) and the build it first shipped in */
@Serializable
data class ReleaseChange(
    val version: Version,
    /** When that build was published, for example "2026-11-01T23:40:00Z" (null if unknown) */
    val publishedAt: String?,
    val title: String,
)

/** All the changes that shipped in one build, with the day it came out */
data class VersionChanges(
    val version: Version,
    val publishedAt: String?,
    val titles: List<String>,
) {
    /** For example "Nov 1, 2026", in the TV's own time zone */
    val dateText: String? get() = formatReleaseDate(publishedAt)
}

private val CHANGE_REGEX = Regex("<!-- app-change:(.+?) -->")

/**
 * Reads the build history the GitHub build writes into the release notes, one line per change:
 * `<!-- app-change:1.0.105|2026-11-01T23:40:00Z|Friendly change title -->`
 */
fun parseReleaseChanges(body: String): List<ReleaseChange> =
    CHANGE_REGEX
        .findAll(body)
        .mapNotNull { m ->
            val parts = m.groupValues[1].split("|", limit = 3)
            if (parts.size < 3) return@mapNotNull null
            val version = Version.tryFromString(parts[0].trim()) ?: return@mapNotNull null
            val title = parts[2].trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            ReleaseChange(version, parts[1].trim().takeIf { it.isNotEmpty() }, title)
        }.toList()

/** "2026-11-01T23:40:00Z" -> "Nov 1, 2026" in the device's time zone (null if it can't be read) */
fun formatReleaseDate(publishedAt: String?): String? {
    if (publishedAt.isNullOrBlank()) return null
    return try {
        Instant
            .parse(publishedAt)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault()))
    } catch (ex: Exception) {
        null
    }
}

@Serializable
data class Release(
    val version: Version,
    val downloadUrl: String?,
    val publishedAt: String?,
    val body: String?,
    val notes: List<String>,
    /** Every recent build's changes (newest first), so the app can show just what's new since the installed version */
    val changes: List<ReleaseChange> = emptyList(),
) {
    /**
     * The changes newer than [installed], grouped by build, newest build first.
     * Pass null to get the whole recent history.
     * Older releases without a build history fall back to the newest build's own list.
     */
    fun changesSince(installed: Version?): List<VersionChanges> {
        if (changes.isEmpty()) {
            return if (notes.isEmpty()) emptyList() else listOf(VersionChanges(version, publishedAt, notes))
        }
        return changes
            .filter { installed == null || it.version.isGreaterThan(installed) }
            .groupBy { it.version }
            .map { (v, list) -> VersionChanges(v, list.first().publishedAt, list.map { it.title }.distinct()) }
            .sortedWith { a, b ->
                when {
                    a.version.isGreaterThan(b.version) -> -1
                    b.version.isGreaterThan(a.version) -> 1
                    else -> 0
                }
            }
    }

    /** Markdown for the update page: each build since [installed] with its date and what changed */
    fun content(installed: Version? = null): String {
        val groups = changesSince(installed)
        if (groups.isEmpty()) return "# $version\n\nSmall fixes and improvements."
        return groups.joinToString("\n\n") { g ->
            val header = "### Version ${g.version}" + (g.dateText?.let { " · $it" } ?: "")
            header + "\n" + g.titles.joinToString("\n") { "- $it" }
        }
    }
}

/** The update file could not be downloaded (for example a 404 while GitHub is publishing a new build) */
class DownloadFailedException(
    val code: Int,
) : IOException("Error downloading the update: HTTP $code")

interface DownloadCallback {
    fun contentLength(contentLength: Long)

    fun bytesDownloaded(bytes: Long)
}

suspend fun copyTo(
    input: InputStream,
    out: OutputStream,
    bufferSize: Int = 64 * 1024,
    callback: DownloadCallback,
): Long =
    withContext(WholphinDispatchers.IO) {
        var bytesCopied: Long = 0
        val buffer = ByteArray(bufferSize)
        var bytes = input.read(buffer)
        while (bytes >= 0) {
            // Stop right away when the download is cancelled, instead of finishing it in the background
            ensureActive()
            out.write(buffer, 0, bytes)
            bytesCopied += bytes
            callback.bytesDownloaded(bytesCopied)
            bytes = input.read(buffer)
        }
        return@withContext bytesCopied
    }

fun getDownloadUrl(
    assets: JsonArray,
    debug: Boolean,
    supportedABIs: List<String> = Build.SUPPORTED_ABIS.toList(),
): String? {
    val abiSuffix = supportedABIs.firstOrNull().let { if (it != null) "-$it" else "" }
    val releaseSuffix = if (debug) "-debug" else "-release"
    val preferredNames =
        buildList {
            add("$ASSET_NAME${releaseSuffix}$abiSuffix.apk")
            add("$ASSET_NAME$releaseSuffix.apk")
            if (!debug) {
                if (abiSuffix.isNotNullOrBlank()) {
                    add("$ASSET_NAME$abiSuffix.apk")
                }
                add("$ASSET_NAME.apk")
            }
        }
    var preferredAsset: JsonObject? = null
    outer@ for (name in preferredNames) {
        for (asset in assets) {
            val assetName =
                asset.jsonObject["name"]?.jsonPrimitive?.contentOrNull
            if (name == assetName) {
                preferredAsset = asset.jsonObject
                break@outer
            }
        }
    }
    return preferredAsset
        ?.get("browser_download_url")
        ?.jsonPrimitive
        ?.contentOrNull
}
