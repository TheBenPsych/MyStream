package com.github.damontecres.wholphin.games

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Where a game's files live on this device, and sending saves back to Tower.
 *
 * Saves for one account and game: files/games/saves/<account>/<game id>/
 *   save.srm   the game's own save (like the battery in a cartridge, or a PlayStation memory card)
 *   save.state "pick up where you left off" (the exact moment you quit)
 *   save.png   a small picture of that moment, for the Continue playing row
 *   save.state1..3 / save.png1..3  the three save slots from the game menu, each with its picture
 *   pending    which of those still need sending to Tower (for when Tower couldn't be reached)
 */
object GameSaves {
    val KINDS = listOf("srm", "state", "png", "state1", "png1", "state2", "png2", "state3", "png3")

    /** Without these sent, a device mustn't pull older saves over its own */
    val MAIN_KINDS = setOf("srm", "state", "png")

    private val http by lazy {
        OkHttpClient
            .Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    fun root(context: Context): File = File(context.filesDir, "games")

    fun systemDir(context: Context): File = File(root(context), "system").apply { mkdirs() }

    fun coresDir(context: Context): File = File(root(context), "cores/${DevicePower.coreAbi()}").apply { mkdirs() }

    fun romsDir(context: Context): File = File(root(context), "roms").apply { mkdirs() }

    fun saveDir(
        context: Context,
        user: String,
        gameId: Int,
    ): File = File(root(context), "saves/${user.filter { it.isLetterOrDigit() || it == '-' }}/$gameId").apply { mkdirs() }

    fun file(
        dir: File,
        kind: String,
    ): File = File(dir, "save.$kind")

    /** Marks saves as waiting to be sent to Tower */
    fun markPending(
        dir: File,
        kinds: Collection<String>,
    ) {
        val now = pending(dir) + kinds
        File(dir, "pending").writeText(now.joinToString(","))
    }

    fun pending(dir: File): Set<String> =
        try {
            File(dir, "pending")
                .takeIf { it.exists() }
                ?.readText()
                ?.split(',')
                ?.filter { it in KINDS }
                ?.toSet()
                .orEmpty()
        } catch (_: Exception) {
            emptySet()
        }

    /**
     * Sends this game's waiting saves to Tower. Blocking; call off the main thread.
     * Returns true when nothing is left waiting.
     */
    fun upload(
        dir: File,
        gameId: Int,
        address: String,
        token: String,
    ): Boolean {
        if (address.isBlank() || token.isBlank()) return false
        val left = pending(dir).toMutableSet()
        for (kind in left.toList()) {
            val f = file(dir, kind)
            if (!f.exists()) {
                left.remove(kind)
                continue
            }
            try {
                val request =
                    Request
                        .Builder()
                        .url("$address/api/save/$gameId/$kind?time=${f.lastModified()}")
                        .header("X-Benflix-Token", token)
                        .put(f.asRequestBody("application/octet-stream".toMediaType()))
                        .build()
                http.newCall(request).execute().use { if (it.isSuccessful) left.remove(kind) }
            } catch (e: Exception) {
                Timber.i("Couldn't send the $kind save to Tower yet: ${e.javaClass.simpleName}")
                break
            }
        }
        val marker = File(dir, "pending")
        if (left.isEmpty()) marker.delete() else marker.writeText(left.joinToString(","))
        return left.isEmpty()
    }

    /** Sends every game's waiting saves for this account (after playing offline) */
    fun uploadAll(
        context: Context,
        user: String,
        address: String,
        token: String,
    ) {
        val base = File(root(context), "saves/${user.filter { it.isLetterOrDigit() || it == '-' }}")
        base.listFiles()?.forEach { dir ->
            val id = dir.name.toIntOrNull() ?: return@forEach
            if (pending(dir).isNotEmpty()) upload(dir, id, address, token)
        }
    }
}
