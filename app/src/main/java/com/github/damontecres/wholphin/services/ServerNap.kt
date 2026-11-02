package com.github.damontecres.wholphin.services

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.Interceptor
import timber.log.Timber
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException

/**
 * Benflix "the server is napping": notices when the Jellyfin server can't be reached
 * (Tower is off, restarting or being worked on), so a friendly screen can show instead of errors.
 */
object ServerNap {
    private val _down = MutableStateFlow(false)
    val down: StateFlow<Boolean> = _down

    @Volatile private var failures = 0

    /** Only requests to the Jellyfin server count (they carry its sign-in header) */
    private fun isServerRequest(request: okhttp3.Request) = request.header("Authorization")?.startsWith("MediaBrowser") == true

    val interceptor =
        Interceptor { chain ->
            val request = chain.request()
            if (!isServerRequest(request)) return@Interceptor chain.proceed(request)
            try {
                val response = chain.proceed(request)
                // 502-504 come from a proxy in front of the server when the server itself is down
                if (response.code in 502..504) noted(false) else noted(true)
                response
            } catch (ex: IOException) {
                // Not "unknown host": that usually means this device has no internet at all
                // (for example watching downloads on a plane), not that the server is down
                if (ex is ConnectException || ex is SocketTimeoutException || ex is NoRouteToHostException) {
                    noted(false)
                }
                throw ex
            }
        }

    private fun noted(ok: Boolean) {
        if (ok) {
            failures = 0
            if (_down.value) Timber.i("Server is back")
            _down.value = false
        } else {
            failures++
            if (failures >= FAILURES_BEFORE_NAP && !_down.value) {
                Timber.i("Server can't be reached")
                _down.value = true
            }
        }
    }

    private const val FAILURES_BEFORE_NAP = 3
}
