package com.github.damontecres.wholphin.ui.books

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.github.damontecres.wholphin.services.BenflixBooks
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.ScreensaverService
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import dagger.hilt.android.lifecycle.HiltViewModel
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class BookReaderViewModel
    @Inject
    constructor(
        val books: BenflixBooks,
        val navigationManager: NavigationManager,
        val screensaver: ScreensaverService,
    ) : ViewModel()

/**
 * The ebook reader (approved design): the same reader page the browser version uses, from Tower's helper,
 * in a web view. Paged, two columns on TV, Dark by default, Aa settings, Contents, a "What next?" card at
 * the end. The TV screensaver comes on after 15 minutes without a page turn.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BookReaderPage(
    id: String,
    chapter: Int?,
    modifier: Modifier = Modifier,
    viewModel: BookReaderViewModel = hiltViewModel(),
) {
    val isTv = rememberIsTv()
    val url = remember(id, chapter) { viewModel.books.readerUrl(id, isTv, chapter) }
    val holder = remember { arrayOfNulls<WebView>(1) }
    val main = remember { Handler(Looper.getMainLooper()) }
    // Reading is not "doing nothing": the normal screensaver waits, the reader asks for it after 15 minutes
    DisposableEffect(Unit) {
        viewModel.screensaver.keepScreenOn(true)
        onDispose {
            holder[0]?.evaluateJavascript("window.readerSave && readerSave()", null)
            viewModel.screensaver.keepScreenOn(false)
        }
    }
    BackHandler {
        val web = holder[0]
        if (web == null) {
            viewModel.navigationManager.goBack()
        } else {
            // The reader closes its own panel or bar first; with nothing open it saves your place and closes
            web.evaluateJavascript("window.readerBack ? readerBack() : false") { answer ->
                if (answer != "true") {
                    web.evaluateJavascript("window.readerClose ? readerClose() : BenflixApp.close()", null)
                }
            }
        }
    }
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(Color.parseColor("#121318"))
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = true
                isFocusable = true
                isFocusableInTouchMode = true
                webViewClient = WebViewClient()
                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun close() {
                            main.post { viewModel.navigationManager.goBack() }
                        }

                        @JavascriptInterface
                        fun idle() {
                            main.post { viewModel.screensaver.start() }
                        }

                        @JavascriptInterface
                        fun awake() {
                            main.post { viewModel.screensaver.keepScreenOn(true) }
                        }

                        @JavascriptInterface
                        fun openTitle(id: String) {
                            main.post { viewModel.navigationManager.navigateTo(Destination.BookTitle(id)) }
                        }
                    },
                    "BenflixApp",
                )
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
                loadUrl(url)
                post { requestFocus() }
                holder[0] = this
                Timber.d("Book reader open")
            }
        },
        onRelease = {
            holder[0] = null
            it.destroy()
        },
        modifier = modifier.fillMaxSize().background(BenflixColors.Tray),
    )
}
