package com.github.damontecres.wholphin.games

import android.content.Context
import android.os.Build
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Benflix Idea Box 24 "Didn't start" list: when a game shows only a black screen, can't load, or closes by itself in
 * its first half a minute, Benflix quietly tells Tower. The main profile sees the list on the Server page, so bad
 * copies get replaced without anyone having to say.
 *
 * A game that closes by itself takes the app's game process with it, so a note is left when a game starts and
 * cleared once it has run [OK_AFTER_MS] (or when the player quits on purpose). A note still there next time a
 * game starts means the last one closed by itself.
 */
object GameProblems {
    const val OK_AFTER_MS = 30_000L

    private const val PREFS = "benflix_game_problems"
    private const val KEY = "starting"

    private val http by lazy {
        OkHttpClient
            .Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /** Call when a game starts: sends any game that closed by itself last time, then leaves a note for this one */
    fun starting(
        context: Context,
        gameId: Int,
        name: String,
        console: String,
        address: String,
        token: String,
    ) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY, null)?.let { left ->
            try {
                val j = JSONObject(left)
                send(j.optInt("id"), j.optString("name"), j.optString("console"), "Closed by itself", address, token)
            } catch (_: Exception) {
            }
        }
        prefs
            .edit()
            .putString(
                KEY,
                JSONObject()
                    .put("id", gameId)
                    .put("name", name)
                    .put("console", console)
                    .toString(),
            ).commit()
    }

    /** The game ran fine (or the player left on purpose): no note to send */
    fun fine(context: Context) {
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY)
            .commit()
    }

    /** True when nearly every pixel of [pixels] (from RetroNative.snapshot: width, height, then the pixels) is black */
    fun looksBlack(pixels: IntArray?): Boolean {
        if (pixels == null || pixels.size < 3) return true
        var lit = 0
        val step = ((pixels.size - 2) / 4000).coerceAtLeast(1)
        var seen = 0
        var i = 2
        while (i < pixels.size) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            if (r + g + b > DARK) lit++
            seen++
            i += step
        }
        return seen == 0 || lit * 100 < seen
    }

    /** Tells Tower (runs on the calling thread, so call it off the main thread) */
    fun send(
        gameId: Int,
        name: String,
        console: String,
        what: String,
        address: String,
        token: String,
    ) {
        if (gameId <= 0 || address.isBlank() || token.isBlank()) return
        try {
            val body =
                JSONObject()
                    .put("id", gameId)
                    .put("name", name)
                    .put("console", console)
                    .put("what", what)
                    .put("device", "${Build.MANUFACTURER} ${Build.MODEL}".trim())
            val request =
                Request
                    .Builder()
                    .url("$address/api/games/problems")
                    .header("X-Benflix-Token", token)
                    .post(body.toString().toRequestBody("application/json".toMediaType()))
                    .build()
            http.newCall(request).execute().close()
        } catch (e: Exception) {
            Timber.i("Couldn't tell Tower a game didn't start: %s", e.javaClass.simpleName)
        }
    }

    /** Red + green + blue under this counts as black */
    private const val DARK = 36
}
