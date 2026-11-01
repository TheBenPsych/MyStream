package com.github.damontecres.wholphin.services

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
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
    }
