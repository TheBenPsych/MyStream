@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.github.damontecres.wholphin.ui.games.ember

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.tryRequestFocus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

// Ember Road's colours: warm night, gold and ember red
val EmberBg = Color(0xFF120D14)
val EmberPanel = Color(0xFF231A26)
val EmberGold = Color(0xFFFFC94D)
val EmberText = Color(0xFFF3EAD8)
val EmberDim = Color(0xFFB8A99A)
val EmberGood = Color(0xFF7BD88F)
val EmberBad = Color(0xFFFF7A6B)

/** The old-game text box at the bottom of the journey screen */
val EmberBox = Color(0xFF0D0A10)

/** The little boxes behind each number in the party panel */
val EmberPanelDark = Color(0xFF17111A)

/** Draws pixel art into a small picture that's blown up with sharp pixels */
private class BitmapSink(
    val bmp: ImageBitmap,
) : PixelSink {
    private val canvas =
        androidx.compose.ui.graphics
            .Canvas(bmp)
    private val paint = Paint()

    /** Draw everything in one colour (for creature silhouettes) */
    var only: Int? = null

    override fun rect(
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        argb: Int,
    ) {
        val col = only?.let { if ((argb ushr 24) > 0x80) it else return } ?: argb
        paint.color = Color(col)
        canvas.drawRect(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), paint)
    }

    fun clear(col: Int) {
        paint.color = Color(col)
        canvas.drawRect(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat(), paint)
    }
}

/** A frame counter that ticks about 12 times a second (or stays still) */
@Composable
fun rememberTick(animate: Boolean = true): Int {
    var t by remember { mutableIntStateOf(0) }
    if (animate) {
        LaunchedEffect(Unit) {
            while (true) {
                delay(83)
                t++
            }
        }
    }
    return t
}

/** One of Ember Road's painted pixel scenes */
@Composable
fun PixelScene(
    key: String,
    opts: SceneOpts,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
) {
    val tick = rememberTick(animate)
    val sink = remember { BitmapSink(ImageBitmap(EmberArt.W, EmberArt.H)) }
    Canvas(modifier.aspectRatio(16f / 9f)) {
        EmberArt.scene(sink, key, tick, opts)
        drawImage(
            sink.bmp,
            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
            filterQuality = FilterQuality.None,
        )
        if (opts.crt) {
            // Old TV: soft dark corners on top of the scanlines
            drawRect(
                androidx.compose.ui.graphics.Brush.radialGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)),
                    center = Offset(size.width / 2, size.height / 2),
                    radius = size.width * 0.75f,
                ),
            )
        }
    }
}

/** A 16 x 16 pixel portrait */
@Composable
fun PixelPortrait(
    member: Member?,
    petId: String? = null,
    size: Dp,
    modifier: Modifier = Modifier,
    faded: Boolean = false,
) {
    val sink = remember { BitmapSink(ImageBitmap(16, 16)) }
    Canvas(modifier.size(size)) {
        if (petId != null) {
            EmberArt.petPortrait(sink, petId, 0, 0)
        } else if (member != null) {
            EmberArt.portrait(sink, member.folk, member.face, 0, 0, hurt = member.sick || member.health <= 1)
        }
        drawImage(
            sink.bmp,
            dstSize = IntSize(this.size.width.toInt(), this.size.height.toInt()),
            filterQuality = FilterQuality.None,
            alpha = if (faded) 0.35f else 1f,
        )
    }
}

/** A creature on a dark square, or its black shape if it hasn't been met yet */
@Composable
fun PixelCreature(
    art: String,
    found: Boolean,
    modifier: Modifier = Modifier,
) {
    val tick = rememberTick(found)
    val sink = remember { BitmapSink(ImageBitmap(48, 40)) }
    Canvas(modifier.aspectRatio(48f / 40f)) {
        sink.only = null
        sink.clear(0xFF2A2030.toInt())
        if (!found) sink.only = 0xFF0C080E.toInt()
        val (x, y) =
            when (art) {
                "giant" -> 9 to 30
                "golem" -> 12 to 20
                "troll" -> 14 to 28
                "dragon" -> 0 to 8
                "goblin" -> 16 to 22
                "hunter" -> 16 to 22
                else -> 14 to 14
            }
        if (art == "dragon") {
            EmberArt.creature(sink, "babydragon", 16, 12, tick)
        } else {
            EmberArt.creature(sink, art, x, y, tick)
        }
        drawImage(sink.bmp, dstSize = IntSize(size.width.toInt(), size.height.toInt()), filterQuality = FilterQuality.None)
    }
}

/** Ember Road's button: dark with a gold outline when picked */
@Composable
fun EmberButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    big: Boolean = false,
    sub: String? = null,
    focusRequester: FocusRequester? = null,
    tint: Color = EmberPanel,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
        // a small grow only: wide buttons that grow a lot spill past the screen edge
        scale = ButtonDefaults.scale(focusedScale = 1.03f),
        colors =
            ButtonDefaults.colors(
                containerColor = tint,
                contentColor = EmberText,
                focusedContainerColor = EmberGold,
                focusedContentColor = Color(0xFF1A1010),
                disabledContainerColor = tint.copy(alpha = 0.4f),
                disabledContentColor = EmberDim.copy(alpha = 0.6f),
            ),
        contentPadding = ButtonDefaults.ContentPadding,
        modifier = modifier.then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
    ) {
        Column {
            Text(text, fontSize = if (big) 19.sp else 16.sp, fontWeight = FontWeight.SemiBold)
            if (sub != null) {
                Text(
                    sub,
                    fontSize = 13.sp,
                    color =
                        androidx.tv.material3.LocalContentColor.current
                            .copy(alpha = 0.75f),
                )
            }
        }
    }
}

/**
 * One choice in the text box: a slim row, a ▶ arrow on the one the remote is on (it doesn't grow, so it never
 * spills off the screen), and who voted for it underneath.
 */
@Composable
fun ChoiceRow(
    text: String,
    onClick: () -> Unit,
    isTv: Boolean,
    modifier: Modifier = Modifier,
    sub: String? = null,
    picked: Boolean = false,
    focusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color(0xFF1D1629),
                contentColor = EmberText,
                focusedContainerColor = Color(0xFF3A2F1A),
                focusedContentColor = EmberGold,
                pressedContainerColor = Color(0xFF3A2F1A),
                pressedContentColor = EmberGold,
            ),
        border =
            ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(1.dp, if (picked) EmberGold.copy(alpha = 0.6f) else Color(0xFF3B2D55))),
                focusedBorder = Border(BorderStroke(2.dp, EmberGold)),
            ),
        modifier =
            modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 6.dp, end = 12.dp, top = if (isTv) 8.dp else 9.dp, bottom = if (isTv) 8.dp else 9.dp),
        ) {
            Text(
                if (focused) {
                    "▶"
                } else if (picked) {
                    "●"
                } else {
                    ""
                },
                fontSize = if (isTv) 14.sp else 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(22.dp),
            )
            Column {
                Text(text, fontSize = if (isTv) 19.sp else 16.sp, fontWeight = FontWeight.SemiBold)
                if (sub != null) {
                    Text(
                        sub,
                        fontSize = if (isTv) 13.sp else 12.sp,
                        color =
                            androidx.tv.material3.LocalContentColor.current
                                .copy(alpha = 0.75f),
                    )
                }
            }
        }
    }
}

/** Words that appear a few letters at a time, like an old game */
@Composable
fun TypeText(
    text: String,
    modifier: Modifier = Modifier,
    size: Int = 18,
    color: Color = EmberText,
) {
    var shown by remember(text) { mutableIntStateOf(0) }
    LaunchedEffect(text) {
        while (shown < text.length) {
            delay(16)
            shown = (shown + 3).coerceAtMost(text.length)
        }
    }
    Box(modifier) {
        // The full text keeps the space, so nothing jumps while it types
        Text(text, fontSize = size.sp, lineHeight = (size * 1.4f).sp, color = Color.Transparent)
        Text(text.take(shown), fontSize = size.sp, lineHeight = (size * 1.4f).sp, color = color)
    }
}

/** "+5 food", "-1 gold" and so on, green for good and red for bad */
@Composable
fun ChangeChips(changes: List<String>) {
    if (changes.isEmpty()) return
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        changes.forEach { c ->
            val bad =
                c.startsWith("-") || c.contains("lost", true) || c.contains("left", true) || c.contains("damaged", true) ||
                    c.contains("stolen", true)
            Text(
                c,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (bad) EmberBad else EmberGood,
                modifier =
                    Modifier
                        .background((if (bad) EmberBad else EmberGood).copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

// -----------------------------------------------------------------------------------------------
// Mini-games (score 0 to 10)

/**
 * Fishing and herb picking: a marker sweeps back and forth; press when it's in the glowing spot.
 * Rocks: steer the cart left and right past falling rocks.
 */
@Composable
fun EmberMiniGame(
    kind: String,
    isTv: Boolean,
    phoneInput: SharedFlow<String>,
    onDone: (Int) -> Unit,
) {
    when (kind) {
        "rocks" -> RocksGame(isTv, phoneInput, onDone)
        else -> TimingGame(kind, isTv, phoneInput, onDone)
    }
}

@Composable
private fun TimingGame(
    kind: String,
    isTv: Boolean,
    phoneInput: SharedFlow<String>,
    onDone: (Int) -> Unit,
) {
    val rounds = 5
    var round by remember { mutableIntStateOf(0) }
    var hits by remember { mutableIntStateOf(0) }
    var pos by remember { mutableFloatStateOf(0f) }
    var zone by remember { mutableFloatStateOf(0.5f) }
    var flash by remember { mutableStateOf<Boolean?>(null) }
    var done by remember { mutableStateOf(false) }
    val zoneW = if (kind == "fish") 0.16f else 0.2f
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.tryRequestFocus() }
    LaunchedEffect(round) {
        zone = Random.nextFloat() * (1f - zoneW)
        flash = null
        var t = 0f
        val speed = 0.018f + round * 0.004f
        while (!done) {
            delay(16)
            t += speed
            // fish wobble, herbs hop
            pos =
                if (kind == "fish") {
                    ((sin(t * 2.2) + 1) / 2).toFloat()
                } else {
                    val raw = abs((t * 0.9f) % 2f - 1f)
                    (raw * 5).toInt().coerceAtMost(4) / 4f
                }
        }
    }
    val herbSpot = (zone / (1f - zoneW)).coerceIn(0f, 1f).let { (it * 4).toInt() }
    val press = {
        if (!done && flash == null) {
            val hit = if (kind == "fish") pos in zone..(zone + zoneW) else (pos * 4).roundToInt() == herbSpot
            if (hit) hits++
            flash = hit
            if (round + 1 >= rounds) {
                done = true
            }
        }
    }
    LaunchedEffect(flash) {
        if (flash != null) {
            delay(700)
            if (done) onDone((hits * 2).coerceIn(0, 10)) else round++
        }
    }
    LaunchedEffect(Unit) { phoneInput.collect { if (it == "tap") press() } }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .focusRequester(focus)
                .onKeyEvent {
                    if (it.type == KeyEventType.KeyUp &&
                        (it.key == Key.DirectionCenter || it.key == Key.Enter || it.key == Key.NumPadEnter || it.key == Key.ButtonA)
                    ) {
                        press()
                        true
                    } else {
                        false
                    }
                }.focusable(),
    ) {
        Text(
            if (kind ==
                "fish"
            ) {
                "Fishing! Press when the fish is in the gold ring."
            } else {
                "Herb picking! Press when your hand is over the glowing herb."
            },
            color = EmberText,
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
        )
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(if (isTv) 70.dp else 60.dp)
                .background(Color(0xFF1C2A3A), RoundedCornerShape(8.dp)),
        ) {
            val w = size.width
            val h = size.height
            if (kind == "fish") {
                drawRect(Color(0xFF2D6C9A), Offset(0f, 0f), Size(w, h))
                drawRect(EmberGold.copy(alpha = 0.55f), Offset(zone * w, 6f), Size(zoneW * w, h - 12f))
                val x = pos * w
                drawRect(Color(0xFFE0A060), Offset(x - 14f, h / 2 - 7f), Size(24f, 14f))
                drawRect(Color(0xFFE0A060), Offset(x + 10f, h / 2 - 10f), Size(8f, 20f))
            } else {
                for (i in 0 until 5) {
                    val cx = w * (i + 0.5f) / 5
                    val glow = i == herbSpot
                    drawRect(if (glow) Color(0xFF8AFFB0) else Color(0xFF3E7A46), Offset(cx - 10f, h / 2 - 4f), Size(20f, h / 2))
                    drawRect(if (glow) Color(0xFFE8FFF0) else Color(0xFF2E5A34), Offset(cx - 16f, h / 2 - 12f), Size(32f, 10f))
                }
                val hx = w * (pos * 4 + 0.5f) / 5
                drawRect(Color(0xFFF1C9A0), Offset(hx - 12f, 4f), Size(24f, 18f))
            }
        }
        Text(
            when (flash) {
                true -> "Got it!"
                false -> "Missed!"
                null -> "Round ${round + 1} of $rounds · $hits caught"
            },
            color =
                when (flash) {
                    true -> EmberGood
                    false -> EmberBad
                    null -> EmberDim
                },
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        if (!isTv) EmberButton("Now!", onClick = press, big = true, modifier = Modifier.fillMaxWidth())
        if (isTv) Text("Press OK on the remote", color = EmberDim, fontSize = 14.sp)
    }
}

@Composable
private fun RocksGame(
    isTv: Boolean,
    phoneInput: SharedFlow<String>,
    onDone: (Int) -> Unit,
) {
    var lane by remember { mutableIntStateOf(1) }
    var rocks by remember { mutableStateOf(listOf<Pair<Int, Float>>()) }
    var hits by remember { mutableIntStateOf(0) }
    var dodged by remember { mutableIntStateOf(0) }
    var over by remember { mutableStateOf(false) }
    val total = 14
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.tryRequestFocus() }
    LaunchedEffect(Unit) {
        var spawned = 0
        var tick = 0
        while (!over) {
            delay(30)
            tick++
            if (spawned < total && tick % 28 == 0) {
                rocks = rocks + (Random.nextInt(3) to 0f)
                spawned++
            }
            val moved = rocks.map { it.first to it.second + 0.022f }
            val landed = moved.filter { it.second >= 0.85f }
            landed.forEach { if (it.first == lane) hits++ else dodged++ }
            rocks = moved.filter { it.second < 0.85f }
            if (spawned >= total && rocks.isEmpty()) {
                over = true
                delay(500)
                onDone((10 - hits * 2).coerceIn(0, 10))
            }
        }
    }
    LaunchedEffect(Unit) {
        phoneInput.collect {
            if (it == "left") lane = (lane - 1).coerceAtLeast(0)
            if (it == "right") lane = (lane + 1).coerceAtMost(2)
        }
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .focusRequester(focus)
                .onKeyEvent {
                    if (it.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (it.key) {
                        Key.DirectionLeft -> {
                            lane = (lane - 1).coerceAtLeast(0)
                            true
                        }

                        Key.DirectionRight -> {
                            lane = (lane + 1).coerceAtMost(2)
                            true
                        }

                        else -> {
                            false
                        }
                    }
                }.focusable(),
    ) {
        Text(
            "Rocky road! Steer the cart left and right to dodge the falling rocks.",
            color = EmberText,
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
        )
        Canvas(
            Modifier
                .fillMaxWidth(if (isTv) 0.5f else 1f)
                .height(if (isTv) 150.dp else 180.dp)
                .background(Color(0xFF6C645A), RoundedCornerShape(8.dp))
                .border(2.dp, Color(0xFF4A443C), RoundedCornerShape(8.dp)),
        ) {
            val lw = size.width / 3
            for (i in 1..2) drawRect(Color(0xFF8A8278), Offset(lw * i - 1f, 0f), Size(2f, size.height))
            rocks.forEach { (l, y) ->
                drawRect(Color(0xFF3A342E), Offset(lw * l + lw / 2 - 16f, y * size.height), Size(32f, 26f))
                drawRect(Color(0xFF9A9288), Offset(lw * l + lw / 2 - 12f, y * size.height + 2f), Size(12f, 8f))
            }
            val cy = size.height * 0.86f
            drawRect(Color(0xFFE9E1C7), Offset(lw * lane + lw / 2 - 20f, cy - 18f), Size(40f, 18f))
            drawRect(Color(0xFF7A5230), Offset(lw * lane + lw / 2 - 20f, cy), Size(40f, 10f))
        }
        Text("Dodged $dodged · Hit $hits", color = if (hits > 0) EmberBad else EmberDim, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        if (!isTv) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                EmberButton("◀ Left", onClick = { lane = (lane - 1).coerceAtLeast(0) }, big = true, modifier = Modifier.weight(1f))
                EmberButton("Right ▶", onClick = { lane = (lane + 1).coerceAtMost(2) }, big = true, modifier = Modifier.weight(1f))
            }
        } else {
            Text("Use ◀ ▶ on the remote", color = EmberDim, fontSize = 14.sp)
        }
    }
}

/** A small "label: value" stat for the status bar */
@Composable
fun Stat(
    label: String,
    value: String,
    warn: Boolean = false,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 4.dp)) {
        Text("$label ", fontSize = 13.sp, color = EmberDim)
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (warn) EmberBad else EmberText)
    }
}
