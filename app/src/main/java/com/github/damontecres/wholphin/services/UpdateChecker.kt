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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.ensureActive
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
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
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

            private const val KEY_SNOOZE_VERSION = "benflix_update_snoozed_version"
            private const val KEY_SNOOZE_AT = "benflix_update_snoozed_at"
        }

        private val _prompt = MutableStateFlow<Release?>(null)

        /** A newer build to offer in the "Update available" pop-up, or null when there is nothing to show */
        val prompt: StateFlow<Release?> = _prompt

        /** Set when the pop-up's Update button was pressed, so the update page starts installing right away */
        var installWhenOpened = false

        /** "Not now": hide the pop-up and don't ask about this build again for a day */
        fun snooze(release: Release) {
            PreferenceManager.getDefaultSharedPreferences(context).edit {
                putString(KEY_SNOOZE_VERSION, release.version.toString())
                putLong(KEY_SNOOZE_AT, Date().time)
            }
            _prompt.value = null
        }

        fun clearPrompt() {
            _prompt.value = null
        }

        /**
         * If the app hasn't recently checked, check for any updates and if there is one, show a toast message
         *
         * This is safe to call many times because it will only show the toast at most once every 12 hours
         */
        suspend fun maybeShowUpdateToast(
            updateUrl: String,
            showNegativeToast: Boolean = false,
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
                val snoozedVersion = pref.getString(KEY_SNOOZE_VERSION, null)
                val snoozedFor = (now.time - pref.getLong(KEY_SNOOZE_AT, 0)).milliseconds
                if (snoozedVersion == latestRelease.version.toString() && snoozedFor < 24.hours) {
                    Timber.i("Update pop-up snoozed for ${latestRelease.version}")
                } else {
                    // Shown as a pop-up with what's new once a profile is picked
                    _prompt.value = latestRelease
                }
            } else {
                Timber.v("No update available for $installedVersion")
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
                        return@use Release(version, downloadUrl, publishedAt, body, notes)
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
         * Download and install an update
         */
        @SuppressLint("RequestInstallPackagesPolicy")
        suspend fun installRelease(
            release: Release,
            callback: DownloadCallback,
        ) {
            withContext(WholphinDispatchers.IO) {
                cleanup()
                val request =
                    Request
                        .Builder()
                        .url(release.downloadUrl!!)
                        .get()
                        .build()
                okHttpClient.newCall(request).execute().use {
                    if (it.isSuccessful) {
                        Timber.v("Request successful for ${release.downloadUrl}")
                        callback.contentLength(it.body.contentLength())
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            val contentValues =
                                ContentValues().apply {
                                    put(MediaStore.MediaColumns.DISPLAY_NAME, APK_NAME)
                                    put(MediaStore.MediaColumns.MIME_TYPE, APK_MIME_TYPE)
                                    put(
                                        MediaStore.MediaColumns.RELATIVE_PATH,
                                        Environment.DIRECTORY_DOWNLOADS,
                                    )
                                }
                            val resolver = context.contentResolver
                            val uri =
                                resolver.insert(
                                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                                    contentValues,
                                )
                            if (uri != null) {
                                it.body.byteStream().use { input ->
                                    resolver.openOutputStream(uri).use { output ->
                                        copyTo(input, output!!, callback = callback)
                                    }
                                }

                                val intent = Intent(Intent.ACTION_INSTALL_PACKAGE)
                                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                                intent.data = uri
                                context.startActivity(intent)
                            } else {
                                Timber.e("Resolver URI is null, trying fallback")
//                                showToast(context, "Unable to download the apk")
                                val targetFile = fallbackDownload(it, callback)
                                val intent = Intent(Intent.ACTION_INSTALL_PACKAGE)
                                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                                intent.data =
                                    FileProvider.getUriForFile(
                                        context,
                                        context.packageName + ".provider",
                                        targetFile,
                                    )
                                context.startActivity(intent)
                            }
                        } else {
                            val targetFile = fallbackDownload(it, callback)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                val intent = Intent(Intent.ACTION_INSTALL_PACKAGE)
                                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                                intent.data =
                                    FileProvider.getUriForFile(
                                        context,
                                        context.packageName + ".provider",
                                        targetFile,
                                    )
                                context.startActivity(intent)
                            } else {
                                val intent = Intent(Intent.ACTION_VIEW)
                                intent.setDataAndType(Uri.fromFile(targetFile), APK_MIME_TYPE)
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            }
                        }
                    } else {
                        Timber.v("Request failed for ${release.downloadUrl}: ${it.code}")
                        // Usually a 404 while a new build is being published, so the caller tries again
                        throw DownloadFailedException(it.code)
                    }
                }
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
            targetFile.outputStream().use { output ->
                response.body.byteStream().use { input ->
                    copyTo(input, output, callback = callback)
                }
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

@Serializable
data class Release(
    val version: Version,
    val downloadUrl: String?,
    val publishedAt: String?,
    val body: String?,
    val notes: List<String>,
) {
    val content =
        "# ${version}\n" +
            (
                (notes.joinToString("\n", postfix = "\n\n") { "- $it" }.takeIf { notes.isNotEmpty() } ?: "") +
                    (body ?: "")
            ).replace(
                Regex("https://github.com/\\w*/\\w+/pull/(\\d+)"),
                "#$1",
            )
                // Remove the last line for full changelog since it's just a link
                .replace(Regex("\\*\\*Full Changelog\\*\\*.*"), "")
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
