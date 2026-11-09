package com.github.damontecres.wholphin.services

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.core.content.edit
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.net.URL
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** A movie, episode or song saved on this phone */
data class DownloadedItem(
    val itemId: UUID,
    val title: String,
    val subtitle: String?,
    val userId: UUID?,
    val kids: Boolean,
    val downloadId: Long,
    val fileName: String,
    val addedAt: Long,
    /** A song (shown under Music in Downloads and played by the music player) */
    val music: Boolean = false,
    val albumId: UUID? = null,
    val runtimeMs: Long = 0L,
)

sealed interface DownloadStatus {
    data object None : DownloadStatus

    data class Downloading(
        val percent: Int,
        val waitingForWifi: Boolean,
    ) : DownloadStatus

    data object Done : DownloadStatus

    data object Failed : DownloadStatus
}

/**
 * Benflix on phones: download movies and episodes to watch without internet.
 * Android's own download manager does the work (with a notification), only over Wi-Fi, into Benflix's
 * own folder on the phone, so no storage permission is needed and the files go away if Benflix is uninstalled.
 */
@Singleton
class DownloadService
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
    ) {
        private val prefs = context.getSharedPreferences("benflix_downloads", Context.MODE_PRIVATE)
        private val manager get() = context.getSystemService(DownloadManager::class.java)

        private val _items = MutableStateFlow(load())

        /** Every download on this phone, newest first */
        val items: StateFlow<List<DownloadedItem>> = _items

        private fun folder(): File = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), FOLDER).also { it.mkdirs() }

        fun file(item: DownloadedItem): File = File(folder(), item.fileName)

        fun posterFile(itemId: UUID): File = File(folder(), "$itemId.jpg")

        /** The downloads the current profile can see. Kids only see what was downloaded in Kids. */
        fun visibleItems(all: List<DownloadedItem>): List<DownloadedItem> =
            visibleItems(all, serverRepository.currentUser?.id, KidsMode.isActive)

        /** Kids sees every download made in Kids; a grown-up sees their own */
        fun visibleItems(
            all: List<DownloadedItem>,
            userId: UUID?,
            kids: Boolean,
        ): List<DownloadedItem> =
            if (kids) {
                all.filter { it.kids }
            } else {
                all.filter { it.userId == null || it.userId == userId }
            }

        fun canDownload(item: BaseItem): Boolean =
            item.type == BaseItemKind.MOVIE || item.type == BaseItemKind.EPISODE || item.type == BaseItemKind.AUDIO

        /** The saved copy of a song (or video), once it has finished downloading */
        fun localFile(itemId: UUID): File? {
            val item = find(itemId) ?: return null
            val f = file(item)
            return if (f.exists() && f.length() > 0 && status(item) == DownloadStatus.Done) f else null
        }

        /** Benflix music: save every song of an album or playlist that isn't saved yet */
        suspend fun startAll(items: List<BaseItem>) {
            items.filter { it.type == BaseItemKind.AUDIO }.forEach { start(it) }
        }

        fun find(itemId: UUID): DownloadedItem? = _items.value.firstOrNull { it.itemId == itemId }

        suspend fun start(item: BaseItem) {
            if (find(item.id) != null) return
            val container =
                item.data.mediaSources
                    ?.firstOrNull()
                    ?.container
                    ?.substringBefore(",")
                    ?.ifBlank { null } ?: item.data.container
                    ?.substringBefore(",")
                    ?.ifBlank { null }
                    ?: if (item.type == BaseItemKind.AUDIO) "mp3" else "mkv"
            val music = item.type == BaseItemKind.AUDIO
            val fileName = "${item.id}.$container"
            val title = item.title ?: item.name ?: ""
            val subtitle =
                if (music) {
                    listOfNotNull(
                        item.data.artists
                            ?.joinToString(", ")
                            ?.ifBlank { null },
                        item.data.album,
                    ).joinToString(" · ")
                        .ifBlank { null }
                } else if (item.type == BaseItemKind.EPISODE) {
                    listOfNotNull(
                        item.data.parentIndexNumber?.let { s -> item.data.indexNumber?.let { e -> "S$s:E$e" } },
                        item.name,
                    ).joinToString(" ")
                } else {
                    item.data.productionYear?.toString()
                }
            val request =
                DownloadManager
                    .Request(Uri.parse(api.libraryApi.getDownloadUrl(item.id)))
                    .addRequestHeader("Authorization", "MediaBrowser Token=\"${api.accessToken}\"")
                    .setTitle(title)
                    .setDescription(subtitle ?: "Benflix")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_MOVIES, "$FOLDER/$fileName")
                    .setAllowedOverMetered(false)
                    .setAllowedOverRoaming(false)
            folder()
            val downloadId = manager.enqueue(request)
            val downloaded =
                DownloadedItem(
                    itemId = item.id,
                    title = title,
                    subtitle = subtitle,
                    userId = serverRepository.currentUser?.id,
                    kids = KidsMode.isActive,
                    downloadId = downloadId,
                    fileName = fileName,
                    addedAt = System.currentTimeMillis(),
                    music = music,
                    albumId = item.data.albumId,
                    runtimeMs = (item.data.runTimeTicks ?: 0L) / 10_000L,
                )
            save(listOf(downloaded) + _items.value)
            // Keep a picture to show in the list, even without internet
            withContext(WholphinDispatchers.IO) {
                try {
                    val imageItemId =
                        when {
                            item.type == BaseItemKind.EPISODE -> item.data.seriesId ?: item.id
                            music -> item.data.albumId ?: item.id
                            else -> item.id
                        }
                    val url = api.imageApi.getItemImageUrl(imageItemId, ImageType.PRIMARY, maxHeight = 450)
                    URL(url).openStream().use { input ->
                        posterFile(item.id).outputStream().use { input.copyTo(it) }
                    }
                } catch (ex: Exception) {
                    Timber.w(ex, "Could not save the poster for a download")
                }
            }
        }

        fun status(item: DownloadedItem): DownloadStatus {
            val cursor = manager.query(DownloadManager.Query().setFilterById(item.downloadId))
            cursor.use {
                if (!it.moveToFirst()) {
                    return if (file(item).exists()) DownloadStatus.Done else DownloadStatus.Failed
                }
                val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val done = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                val reason = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                return when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        DownloadStatus.Done
                    }

                    DownloadManager.STATUS_FAILED -> {
                        DownloadStatus.Failed
                    }

                    else -> {
                        DownloadStatus.Downloading(
                            percent = if (total > 0) ((done * 100) / total).toInt() else 0,
                            waitingForWifi =
                                status == DownloadManager.STATUS_PAUSED &&
                                    reason == DownloadManager.PAUSED_QUEUED_FOR_WIFI,
                        )
                    }
                }
            }
        }

        fun status(itemId: UUID): DownloadStatus = find(itemId)?.let { status(it) } ?: DownloadStatus.None

        fun delete(itemId: UUID) {
            val item = find(itemId) ?: return
            try {
                manager.remove(item.downloadId)
            } catch (ex: Exception) {
                Timber.w(ex, "Could not cancel a download")
            }
            file(item).delete()
            posterFile(itemId).delete()
            prefs.edit { remove(positionKey(itemId)) }
            save(_items.value.filterNot { it.itemId == itemId })
        }

        /** Where you stopped watching a download, so it picks up there next time */
        fun position(itemId: UUID): Long = prefs.getLong(positionKey(itemId), 0L)

        fun savePosition(
            itemId: UUID,
            positionMs: Long,
        ) = prefs.edit { putLong(positionKey(itemId), positionMs) }

        private fun positionKey(itemId: UUID) = "pos_$itemId"

        private fun save(list: List<DownloadedItem>) {
            _items.value = list
            prefs.edit {
                putString(
                    KEY_LIST,
                    org.json
                        .JSONArray(
                            list.map {
                                JSONObject()
                                    .put("itemId", it.itemId.toString())
                                    .put("title", it.title)
                                    .put("subtitle", it.subtitle ?: "")
                                    .put("userId", it.userId?.toString() ?: "")
                                    .put("kids", it.kids)
                                    .put("downloadId", it.downloadId)
                                    .put("fileName", it.fileName)
                                    .put("addedAt", it.addedAt)
                                    .put("music", it.music)
                                    .put("albumId", it.albumId?.toString() ?: "")
                                    .put("runtimeMs", it.runtimeMs)
                            },
                        ).toString(),
                )
            }
        }

        private fun load(): List<DownloadedItem> =
            try {
                val array = org.json.JSONArray(prefs.getString(KEY_LIST, "[]"))
                (0 until array.length()).map { i ->
                    val o = array.getJSONObject(i)
                    DownloadedItem(
                        itemId = UUID.fromString(o.getString("itemId")),
                        title = o.getString("title"),
                        subtitle = o.optString("subtitle").ifBlank { null },
                        userId = o.optString("userId").ifBlank { null }?.let(UUID::fromString),
                        kids = o.optBoolean("kids"),
                        downloadId = o.getLong("downloadId"),
                        fileName = o.getString("fileName"),
                        addedAt = o.optLong("addedAt"),
                        music = o.optBoolean("music"),
                        albumId = o.optString("albumId").ifBlank { null }?.let(UUID::fromString),
                        runtimeMs = o.optLong("runtimeMs"),
                    )
                }
            } catch (ex: Exception) {
                Timber.e(ex, "Could not read the downloads list")
                emptyList()
            }

        companion object {
            private const val FOLDER = "benflix"
            private const val KEY_LIST = "items"
        }
    }
