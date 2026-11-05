package com.github.damontecres.wholphin.ui.components

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.services.PreviewPlayers
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import kotlinx.coroutines.delay
import timber.log.Timber

/**
 * Benflix: YouTube trailers play in a pop-up inside the app (no jumping out to the YouTube app,
 * which Fire TVs and some Google TVs don't even have). It uses YouTube's own embedded player.
 *
 * TV remote: OK = pause/play, left/right = 10 seconds back/forward, Back = close.
 * Phone: YouTube's own touch controls, plus a Close button.
 */
object YouTubeTrailer {
    data class Request(
        val videoId: String,
        val title: String,
        val url: String,
    )

    internal var current by mutableStateOf<Request?>(null)

    fun show(
        videoId: String,
        title: String,
        url: String,
    ) {
        current = Request(videoId, title, url)
    }

    fun close() {
        current = null
    }

    private val ID = Regex("^[A-Za-z0-9_-]{11}$")

    /**
     * The 11 letter YouTube video id in [url], or null when it isn't a YouTube link. Knows
     * youtube.com/watch?v=, youtu.be/, /embed/, /shorts/, and the old Kodi "plugin://...videoid=" style.
     */
    fun videoId(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val trimmed = url.trim()
        if (ID.matches(trimmed)) return null // a bare id isn't a link; don't guess
        val uri =
            try {
                trimmed.toUri()
            } catch (_: Exception) {
                return null
            }
        val host = uri.host?.lowercase().orEmpty()
        val candidate =
            when {
                trimmed.startsWith("plugin://plugin.video.youtube", true) ->
                    uri.getQueryParameter("videoid") ?: uri.getQueryParameter("video_id")

                host == "youtu.be" -> uri.pathSegments.firstOrNull()

                host.endsWith("youtube.com") || host.endsWith("youtube-nocookie.com") -> {
                    val segments = uri.pathSegments
                    when (segments.firstOrNull()) {
                        "watch" -> uri.getQueryParameter("v")
                        "embed", "shorts", "v", "live" -> segments.getOrNull(1)
                        else -> uri.getQueryParameter("v")
                    }
                }

                else -> null
            }
        return candidate?.takeIf { ID.matches(it) }
    }

    /** Opens the link in another app (the YouTube app or a browser). False when nothing can open it */
    fun openOutside(
        context: Context,
        url: String,
    ): Boolean =
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
            true
        } catch (ex: ActivityNotFoundException) {
            Timber.w(ex, "Nothing can open $url")
            false
        } catch (ex: Exception) {
            Timber.w(ex, "Couldn't open $url")
            false
        }
}

/** Put once at the top of the app, above everything else. Shows the pop-up while a trailer is asked for */
@Composable
fun YouTubeTrailerLayer() {
    val request = YouTubeTrailer.current ?: return
    // Trailer players on the page behind let go of the video decoder first (cheap TVs have only one)
    LaunchedEffect(request) { PreviewPlayers.freeAll() }
    Dialog(
        onDismissRequest = { YouTubeTrailer.close() },
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
    ) {
        YouTubeTrailerPopup(request = request, onClose = { YouTubeTrailer.close() })
    }
}

private class PlayerBridge {
    private val main = Handler(Looper.getMainLooper())
    var ready by mutableStateOf(false)
    var playing by mutableStateOf(false)
    var ended by mutableStateOf(false)
    var error by mutableIntStateOf(0)
    var position by mutableFloatStateOf(0f)
    var duration by mutableFloatStateOf(0f)

    @JavascriptInterface
    fun onReady() {
        main.post { ready = true }
    }

    @JavascriptInterface
    fun onState(state: Int) {
        main.post {
            // YouTube: 0 ended, 1 playing, 2 paused, 3 buffering
            playing = state == 1 || state == 3
            ended = state == 0
        }
    }

    @JavascriptInterface
    fun onError(code: Int) {
        main.post { error = if (code == 0) -1 else code }
    }

    @JavascriptInterface
    fun onTime(
        pos: Float,
        dur: Float,
    ) {
        main.post {
            position = pos
            duration = dur
        }
    }
}

@Composable
private fun YouTubeTrailerPopup(
    request: YouTubeTrailer.Request,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val isTv = rememberIsTv()
    val bridge = remember(request) { PlayerBridge() }
    var webView by remember(request) { mutableStateOf<WebView?>(null) }
    var noWebView by remember(request) { mutableStateOf(false) }
    val keyFocus = remember { FocusRequester() }
    val buttonFocus = remember { FocusRequester() }

    fun js(code: String) {
        webView?.evaluateJavascript(code, null)
    }

    // Closes by itself at the end, like a trailer on the details page
    LaunchedEffect(bridge.ended) {
        if (bridge.ended) {
            delay(800)
            onClose()
        }
    }
    // No answer from YouTube at all (no internet, YouTube blocked): say so instead of a black box
    LaunchedEffect(request) {
        delay(20_000)
        if (!bridge.ready && bridge.error == 0) bridge.error = -1
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        js("bf.pause()")
        webView?.onPause()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { webView?.onResume() }
    DisposableEffect(request) {
        onDispose {
            webView?.let {
                it.stopLoading()
                it.loadUrl("about:blank")
                (it.parent as? ViewGroup)?.removeView(it)
                it.destroy()
            }
            webView = null
        }
    }

    val failed = bridge.error != 0 || noWebView
    LaunchedEffect(failed) {
        delay(150)
        try {
            if (failed) buttonFocus.requestFocus() else keyFocus.requestFocus()
        } catch (_: IllegalStateException) {
        }
    }

    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = if (isTv) .92f else .96f))
                .focusRequester(keyFocus)
                .onKeyEvent { event ->
                    if (failed || event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (event.key) {
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Spacebar,
                        Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause,
                        -> {
                            js("bf.toggle()")
                            true
                        }

                        Key.DirectionLeft, Key.MediaRewind -> {
                            js("bf.seek(-10)")
                            true
                        }

                        Key.DirectionRight, Key.MediaFastForward -> {
                            js("bf.seek(10)")
                            true
                        }

                        // Up/down do nothing, but are swallowed so focus doesn't wander off
                        Key.DirectionUp, Key.DirectionDown -> true

                        else -> false
                    }
                }.focusable(),
    ) {
        val videoWidth =
            if (isTv) {
                min(maxWidth * .8f, maxHeight * .7f * 16f / 9f)
            } else {
                min(maxWidth, maxHeight * .78f * 16f / 9f)
            }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (isTv) 14.dp else 10.dp),
            modifier = Modifier.width(videoWidth),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = if (isTv) 0.dp else 12.dp),
            ) {
                Text(
                    text = request.title,
                    color = Color.White,
                    fontSize = if (isTv) 22.sp else 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (!isTv) {
                    Button(onClick = onClose) { Text("Close") }
                }
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .width(videoWidth)
                        .height(videoWidth * 9f / 16f)
                        .clip(RoundedCornerShape(if (isTv) 10.dp else 0.dp))
                        .background(Color.Black),
            ) {
                if (!noWebView) {
                    AndroidView(
                        factory = { ctx ->
                            try {
                                makeWebView(ctx, request, bridge, isTv).also { webView = it }
                            } catch (ex: Exception) {
                                // Some TVs have a broken or missing WebView
                                Timber.w(ex, "No WebView for YouTube trailer")
                                noWebView = true
                                android.view.View(ctx)
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (!bridge.ready && !failed) {
                    Text("Loading trailer…", color = Color(0xFFB8B8C0), fontSize = 15.sp)
                }
                if (failed) {
                    FailedMessage(
                        request = request,
                        isTv = isTv,
                        buttonFocus = buttonFocus,
                        onClose = onClose,
                        context = context,
                    )
                }
            }
            if (isTv && !failed) {
                // Simple progress line, since YouTube's own controls are hidden on TV
                val fraction =
                    if (bridge.duration > 0f) (bridge.position / bridge.duration).coerceIn(0f, 1f) else 0f
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = .2f)),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth(fraction)
                                .height(4.dp)
                                .background(Color(0xFFE5484D)),
                    )
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "${clock(bridge.position)} / ${clock(bridge.duration)}",
                        color = Color(0xFFB8B8C0),
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text =
                            (if (bridge.playing) "OK  Pause" else "OK  Play") +
                                "     ◀ ▶  10 seconds     Back  Close",
                        color = Color(0xFFB8B8C0),
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun FailedMessage(
    request: YouTubeTrailer.Request,
    isTv: Boolean,
    buttonFocus: FocusRequester,
    onClose: () -> Unit,
    context: Context,
) {
    var nothingToOpen by remember { mutableStateOf(false) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color(0xFF16161A))
                .padding(16.dp),
    ) {
        Box(modifier = Modifier.weight(1f))
        Text(
            text =
                if (nothingToOpen) {
                    "There's no YouTube app on this device to open it in."
                } else {
                    "This trailer can't play inside Benflix. YouTube only lets some videos play on YouTube itself."
                },
            color = Color.White,
            fontSize = if (isTv) 18.sp else 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 520.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!nothingToOpen) {
                Button(
                    onClick = {
                        if (YouTubeTrailer.openOutside(context, request.url)) onClose() else nothingToOpen = true
                    },
                    modifier = Modifier.focusRequester(buttonFocus),
                ) { Text("Open in YouTube") }
                Button(onClick = onClose) { Text("Close") }
            } else {
                Button(onClick = onClose, modifier = Modifier.focusRequester(buttonFocus)) { Text("Close") }
            }
        }
        Box(modifier = Modifier.weight(1f).size(1.dp))
    }
}

private fun clock(seconds: Float): String {
    val s = seconds.toInt().coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface", "AddJavascriptInterface")
private fun makeWebView(
    context: Context,
    request: YouTubeTrailer.Request,
    bridge: PlayerBridge,
    isTv: Boolean,
): WebView =
    WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        setBackgroundColor(android.graphics.Color.BLACK)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        // On TV the remote talks to Benflix, not the web page (that's how OK/left/right work)
        if (isTv) {
            isFocusable = false
            isFocusableInTouchMode = false
        }
        webChromeClient = WebChromeClient()
        webViewClient =
            object : WebViewClient() {
                // Links inside the player (YouTube logo, title) don't open a browser inside the pop-up
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: android.webkit.WebResourceRequest?,
                ): Boolean = request?.isForMainFrame == true
            }
        addJavascriptInterface(bridge, "Benflix")
        // YouTube wants to know which app is showing the video (an "https://<app id>" address),
        // otherwise it refuses with "video player configuration error"
        val origin = "https://${context.packageName}"
        loadDataWithBaseURL(origin, playerPage(request.videoId, origin, controls = !isTv), "text/html", "utf-8", null)
    }

private fun playerPage(
    videoId: String,
    origin: String,
    controls: Boolean,
): String =
    """
    <!doctype html>
    <html><head>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="referrer" content="strict-origin-when-cross-origin">
    <style>html,body{margin:0;padding:0;width:100%;height:100%;background:#000;overflow:hidden}
    #p{position:absolute;top:0;left:0;width:100%;height:100%}</style>
    </head><body>
    <div id="p"></div>
    <script>
    var player = null;
    var bf = {
      toggle: function(){ if(!player||!player.getPlayerState) return;
        var s = player.getPlayerState(); if (s === 1 || s === 3) player.pauseVideo(); else player.playVideo(); },
      pause: function(){ if(player&&player.pauseVideo) player.pauseVideo(); },
      seek: function(d){ if(!player||!player.getCurrentTime) return;
        var t = Math.max(0, player.getCurrentTime() + d); player.seekTo(t, true); }
    };
    function onYouTubeIframeAPIReady(){
      player = new YT.Player('p', {
        width: '100%', height: '100%', videoId: '$videoId',
        playerVars: { autoplay: 1, controls: ${if (controls) 1 else 0}, rel: 0, playsinline: 1, fs: 0,
          iv_load_policy: 3, disablekb: 1, origin: '$origin' },
        events: {
          onReady: function(e){ Benflix.onReady(); e.target.setVolume(100); e.target.playVideo(); },
          onStateChange: function(e){ Benflix.onState(e.data); },
          onError: function(e){ Benflix.onError(e.data || 0); }
        }
      });
      setInterval(function(){
        if (player && player.getCurrentTime) {
          Benflix.onTime(player.getCurrentTime() || 0, player.getDuration() || 0);
        }
      }, 500);
    }
    </script>
    <script src="https://www.youtube.com/iframe_api" onerror="Benflix.onError(0)"></script>
    </body></html>
    """.trimIndent()
