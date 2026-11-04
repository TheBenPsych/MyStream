package com.github.damontecres.wholphin.util

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.github.damontecres.wholphin.WholphinApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import timber.log.Timber
import kotlin.coroutines.CoroutineContext

/**
 * A general [CoroutineExceptionHandler] which can optionally show a [Toast] when an exception is thrown
 *
 * @param autoToast automatically show a toast with the exception's message
 */
class ExceptionHandler(
    private val autoToast: Boolean = false,
) : CoroutineExceptionHandler {
    override val key: CoroutineContext.Key<*>
        get() = CoroutineExceptionHandler

    override fun handleException(
        context: CoroutineContext,
        exception: Throwable,
    ) {
        if (exception is CancellationException) {
            // Don't log/toast cancellations
            return
        }
        Timber.e(exception, "Exception in coroutine")

        if (autoToast) {
            // Benflix: post the message to the main thread instead of waiting for it. Waiting
            // (runBlocking) could freeze the app if this ran on the main thread itself.
            val text = "Error: ${exception.message}"
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(WholphinApplication.instance, text, Toast.LENGTH_LONG).show()
            }
        }
    }
}
