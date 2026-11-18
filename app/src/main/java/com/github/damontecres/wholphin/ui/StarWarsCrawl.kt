package com.github.damontecres.wholphin.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.theme.StarWarsDay
import com.github.damontecres.wholphin.ui.theme.StarWarsFont
import java.time.LocalDate
import kotlin.random.Random

/**
 * Benflix Star Wars Day: the opening crawl, the first time Benflix opens on 4 May (approved 2026-11-17).
 * About 13 seconds on a starfield: a blue line ("A short time ago, on a Benflix not so far away…."), the
 * outlined MAY THE 4TH title shrinking away, then our own yellow crawl tilting back into the stars.
 * Any button (or a tap on a phone) skips it. Our own words and drawing, in News Cycle.
 */
object StarWarsCrawlDay {
    private const val PREFS = "benflix_sw"
    private const val KEY = "crawl_shown"

    /** True on 4 May (look not switched off) until the crawl has been shown once today on this TV or phone */
    fun due(context: Context): Boolean {
        if (!StarWarsDay.on()) return false
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return p.getString(KEY, null) != LocalDate.now().toString()
    }

    fun markShown(context: Context) {
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, LocalDate.now().toString())
            .apply()
    }
}

private const val CRAWL_MS = 13_200f
private val IntroBlue = Color(0xFF4BD5EE)

@Composable
fun StarWarsCrawl(onDone: () -> Unit) {
    val context = LocalContext.current
    val t = remember { mutableFloatStateOf(0f) }
    val focus = remember { FocusRequester() }
    val finished = remember { booleanArrayOf(false) }
    val finish = {
        if (!finished[0]) {
            finished[0] = true
            StarWarsCrawlDay.markShown(context)
            onDone()
        }
    }
    LaunchedEffect(Unit) {
        runCatching { focus.requestFocus() }
        val start = withFrameNanos { it }
        while (t.floatValue < CRAWL_MS) {
            withFrameNanos { now -> t.floatValue = (now - start) / 1_000_000f }
        }
        finish()
    }
    BackHandler { finish() }
    val stars =
        remember {
            val r = Random(7)
            List(160) { Triple(r.nextFloat(), r.nextFloat(), .4f + r.nextFloat() * .6f) }
        }
    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = ((CRAWL_MS - t.floatValue) / 1300f).coerceIn(0f, 1f) }
                .background(Color.Black)
                .focusRequester(focus)
                .focusable()
                .onPreviewKeyEvent {
                    if (it.type == KeyEventType.KeyDown) finish()
                    true
                }.pointerInput(Unit) { detectTapGestures { finish() } },
    ) {
        val w = maxWidth.value
        val h = maxHeight.value
        Canvas(Modifier.fillMaxSize()) {
            stars.forEach { (x, y, a) ->
                drawCircle(
                    Color.White.copy(alpha = a),
                    radius = 1.2f * density * a,
                    center =
                        Offset(
                            x * size.width,
                            y * size.height,
                        ),
                )
            }
        }
        // 0 to 3 s: the blue line
        Text(
            text = "A short time ago, on a Glowfin not so far away….",
            color = IntroBlue,
            fontFamily = StarWarsFont,
            fontSize = (w * .022f).coerceIn(13f, 24f).sp,
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp)
                    .graphicsLayer {
                        val s = t.floatValue / 3000f
                        alpha =
                            when {
                                s < .2f -> s / .2f
                                s < .75f -> 1f
                                else -> ((1f - s) / .25f).coerceIn(0f, 1f)
                            }
                    },
        )
        // 3 to 7.5 s: the outlined title shrinks away into the stars
        Text(
            text = "MAY THE 4TH",
            color = StarWarsDay.CrawlYellow,
            fontFamily = StarWarsFont,
            fontSize = (w * .085f).coerceIn(32f, 96f).sp,
            style = TextStyle(drawStyle = Stroke(width = 2.5f * LocalDensity.current.density), letterSpacing = 4.sp),
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .graphicsLayer {
                        val s = ((t.floatValue - 3000f) / 4500f)
                        if (s < 0f || s > 1f) {
                            alpha = 0f
                        } else {
                            val e = s * s
                            val k = 1.3f + (.05f - 1.3f) * e
                            scaleX = k
                            scaleY = k
                            alpha = if (s < .9f) 1f else (1f - s) / .1f
                        }
                    },
        )
        // 4.2 s on: the crawl tilts back and rolls away
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationX = 26f
                    transformOrigin = TransformOrigin(.5f, 1f)
                    cameraDistance = 5f * density
                },
        ) {
            val p = ((t.floatValue - 4200f) / 9000f).coerceIn(0f, 1f)
            val size = (w * .03f).coerceIn(15f, 30f)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth(if (w > h) .62f else .86f)
                        .wrapContentHeight(unbounded = true)
                        .graphicsLayer {
                            // from just below the screen to well above it
                            translationY = (h * (1f - p * 2.4f)) * density
                            alpha = if (t.floatValue < 4200f) 0f else 1f
                        },
            ) {
                val style =
                    TextStyle(
                        color = StarWarsDay.CrawlYellow,
                        fontFamily = StarWarsFont,
                        fontSize = size.sp,
                        lineHeight =
                            (
                                size *
                                    1.35f
                            ).sp,
                    )
                Text("Episode IV", style = style, textAlign = TextAlign.Center)
                Text(
                    "A NEW NIGHT IN",
                    style = style.copy(fontSize = (size * 1.5f).sp),
                    textAlign = TextAlign.Center,
                    modifier =
                        Modifier.padding(
                            bottom =
                                (
                                    size *
                                        .8f
                                ).dp,
                        ),
                )
                CRAWL.forEach {
                    Text(
                        it,
                        style = style,
                        textAlign = TextAlign.Justify,
                        modifier = Modifier.fillMaxWidth().padding(bottom = (size * .8f).dp),
                    )
                }
            }
        }
    }
}

/** The approved crawl, in our own words */
private val CRAWL =
    listOf(
        "It is a time of popcorn. Brave viewers, settled on their couch, have won their first free evening in weeks.",
        "During the battle for the remote, the family found every film of the saga, waiting on Glowfin.",
        "Pursued by snacks and blankets, everyone races home to press Play….",
    )
