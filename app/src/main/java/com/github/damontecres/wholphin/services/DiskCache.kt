package com.github.damontecres.wholphin.services

import android.content.Context
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Saves slow-to-build things (like which movies belong to which collection) as small files,
 * so Benflix can reuse them the next time instead of asking the server again.
 *
 * Everything here is a cache: a missing, old or broken file just means "build it again".
 */
@Singleton
class DiskCache
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        private val json =
            Json {
                ignoreUnknownKeys = true
                isLenient = true
                encodeDefaults = false
            }

        private val dir: File
            get() = File(context.cacheDir, "benflix-cache").apply { mkdirs() }

        private fun file(name: String) = File(dir, name.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".json")

        init {
            // Benflix: names include the day or profile, so old files would pile up. Once a day,
            // off the main thread, files not written for a month are deleted.
            CoroutineScope(SupervisorJob() + WholphinDispatchers.IO).launch { cleanUp() }
        }

        private fun cleanUp() {
            try {
                val marker = File(dir, CLEANUP_MARKER)
                val now = System.currentTimeMillis()
                if (marker.exists() && now - marker.lastModified() < CLEANUP_EVERY_MS) return
                var deleted = 0
                dir.listFiles()?.forEach { f ->
                    if (f.isFile && f.name != CLEANUP_MARKER && now - f.lastModified() > MAX_FILE_AGE_MS && f.delete()) {
                        deleted++
                    }
                }
                if (!marker.exists()) marker.createNewFile()
                marker.setLastModified(now)
                if (deleted > 0) Timber.i("Deleted %s old cache files", deleted)
            } catch (ex: Exception) {
                Timber.w(ex, "Could not clean up old cache files")
            }
        }

        /** The saved value, or null if there is none or it is older than [maxAgeMs] */
        fun <T> read(
            name: String,
            serializer: KSerializer<T>,
            maxAgeMs: Long,
        ): T? {
            val f = file(name)
            if (!f.exists() || System.currentTimeMillis() - f.lastModified() > maxAgeMs) return null
            return try {
                json.decodeFromString(serializer, f.readText())
            } catch (ex: Exception) {
                Timber.w(ex, "Ignoring broken cache file %s", name)
                f.delete()
                null
            }
        }

        fun <T> write(
            name: String,
            serializer: KSerializer<T>,
            value: T,
        ) {
            try {
                val f = file(name)
                val tmp = File(f.parentFile, f.name + ".tmp")
                tmp.writeText(json.encodeToString(serializer, value))
                tmp.renameTo(f)
            } catch (ex: Exception) {
                Timber.w(ex, "Could not save cache file %s", name)
            }
        }

        private companion object {
            const val CLEANUP_MARKER = "last-cleanup"
            const val CLEANUP_EVERY_MS = 24L * 60 * 60 * 1000
            const val MAX_FILE_AGE_MS = 30L * 24 * 60 * 60 * 1000
        }
    }
