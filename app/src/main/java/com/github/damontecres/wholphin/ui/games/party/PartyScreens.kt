package com.github.damontecres.wholphin.ui.games.party

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.ui.games.FitScreen
import com.github.damontecres.wholphin.ui.games.ember.EmberLinkClient
import com.github.damontecres.wholphin.ui.games.ember.FoundEmberTv
import com.github.damontecres.wholphin.ui.games.ember.PhoneView
import com.github.damontecres.wholphin.ui.games.puzzles.PuzzleBg
import com.github.damontecres.wholphin.ui.games.puzzles.PuzzleGold
import com.github.damontecres.wholphin.ui.games.puzzles.PuzzleGreen
import com.github.damontecres.wholphin.ui.games.puzzles.PuzzlePanel
import com.github.damontecres.wholphin.ui.games.tvPictureHeight
import com.github.damontecres.wholphin.ui.theme.BenflixRed
import com.github.damontecres.wholphin.ui.theme.BenflixTitleFont
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import kotlinx.coroutines.delay

/** The TV (or phone) hosting a party game: lobby, questions, scores */
@Composable
fun PartyHostScreen(
    kind: PartyKind,
    modifier: Modifier = Modifier,
    viewModel: PartyViewModel = hiltViewModel(),
) {
    LaunchedEffect(kind) { viewModel.setup(kind) }
    val ui by viewModel.ui.collectAsState()
    val isTv = rememberIsTv()
    Box(
        contentAlignment = Alignment.TopCenter,
        modifier =
            modifier
                .fillMaxSize()
                .background(PuzzleBg)
                .padding(horizontal = if (isTv) 56.dp else 16.dp, vertical = if (isTv) 28.dp else 12.dp),
    ) {
        FitScreen(isTv) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.widthIn(max = 1100.dp).fillMaxWidth(),
            ) {
                Text(ui.kind.title.uppercase(), fontFamily = BenflixTitleFont, fontSize = if (isTv) 40.sp else 30.sp, color = Color.White)
                when (ui.phase) {
                    PartyPhase.LOBBY -> Lobby(ui, isTv, viewModel)
                    PartyPhase.OVER -> Over(ui, viewModel)
                    PartyPhase.REVEAL -> Reveal(ui)
                    else -> Live(ui, isTv)
                }
            }
        }
    }
}

@Composable
private fun Lobby(
    ui: PartyUi,
    isTv: Boolean,
    viewModel: PartyViewModel,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.tryRequestFocus() }
    Text(ui.kind.about, fontSize = 20.sp, lineHeight = 28.sp, color = Color.White.copy(alpha = .9f), textAlign = TextAlign.Center)
    Panel {
        Text("JOIN ON YOUR PHONES", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = .6f))
        Text(
            "Open Benflix on your phone, go to Games, and press Join party. Phones need to be on the same Wi-Fi as this ${if (isTv) "TV" else "phone"}.",
            fontSize = 18.sp,
            lineHeight = 26.sp,
            color = Color.White,
        )
    }
    Panel {
        Text("PLAYERS (${ui.players.size})", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = .6f))
        if (ui.players.isEmpty()) {
            Text("Nobody yet…", fontSize = 18.sp, color = Color.White.copy(alpha = .7f))
        } else {
            PlayerChips(ui.players.map { it.name })
        }
    }
    ui.error?.let { Text(it, fontSize = 16.sp, color = BenflixRed) }
    val need = ui.kind.minPlayers - ui.players.size
    Button(onClick = { viewModel.startGame() }, modifier = Modifier.focusRequester(focus)) {
        Text(if (need > 0) "Needs $need more ${if (need == 1) "player" else "players"}" else "▶ Start with ${ui.players.size}")
    }
}

@Composable
private fun Live(
    ui: PartyUi,
    isTv: Boolean,
) {
    val left by produceState(0L, ui.endsAt) {
        while (true) {
            value = ((ui.endsAt - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
            delay(250)
        }
    }
    Text(
        "Round ${ui.round} of ${ui.rounds}  ·  $left s  ·  ${ui.answered.size} of ${ui.players.size - if (ui.phase == PartyPhase.DRAWING) 1 else 0} answered",
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        color = PuzzleGold,
    )
    Text(
        ui.prompt,
        fontSize = if (isTv) 26.sp else 20.sp,
        lineHeight = if (isTv) 34.sp else 27.sp,
        color = Color.White,
        textAlign = TextAlign.Center,
    )
    when (ui.phase) {
        PartyPhase.VOTE -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ui.plots.forEachIndexed { i, p ->
                    Panel { Text("${i + 1}.  ${p.text}", fontSize = 18.sp, lineHeight = 25.sp, color = Color.White) }
                }
            }
        }

        PartyPhase.DRAWING -> {
            DrawingBoard(ui.lines, Modifier.width(if (isTv) tvPictureHeight(0.5f) * 1.5f else 320.dp))
            ChoicesShown(ui.choices)
        }

        else -> {
            ui.image?.let { url ->
                val w = if (ui.wide) tvPictureHeight(0.42f) * (16f / 9f) else tvPictureHeight(0.42f) * (2f / 3f)
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .width(
                                if (isTv) {
                                    w
                                } else if (ui.wide) {
                                    320.dp
                                } else {
                                    150.dp
                                },
                            ).aspectRatio(if (ui.wide) 16f / 9f else 2f / 3f)
                            .background(PuzzlePanel, RoundedCornerShape(10.dp)),
                )
            }
            ChoicesShown(ui.choices)
        }
    }
    PlayerChips(ui.players.map { (if (it.id in ui.answered) "✓ " else "") + it.name })
}

@Composable
private fun ChoicesShown(choices: List<String>) {
    if (choices.isEmpty()) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        choices.forEachIndexed { i, c ->
            Text(
                "${'A' + i}  $c",
                fontSize = 18.sp,
                color = Color.White,
                modifier = Modifier.background(Color(0xFF2A2A2E), RoundedCornerShape(8.dp)).padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun Reveal(ui: PartyUi) {
    Text(
        ui.reveal,
        fontSize = 24.sp,
        lineHeight = 33.sp,
        fontWeight = FontWeight.SemiBold,
        color = PuzzleGreen,
        textAlign = TextAlign.Center,
    )
    Scoreboard(ui.players)
}

@Composable
private fun Over(
    ui: PartyUi,
    viewModel: PartyViewModel,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.tryRequestFocus() }
    val winner = ui.players.maxByOrNull { it.score }
    Text(winner?.let { "${it.name} wins!" } ?: "Game over", fontFamily = BenflixTitleFont, fontSize = 44.sp, color = PuzzleGold)
    Scoreboard(ui.players)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = { viewModel.startGame() }, modifier = Modifier.focusRequester(focus)) { Text("Play again") }
        Button(onClick = { viewModel.backToLobby() }) { Text("Back to players") }
    }
}

@Composable
private fun Scoreboard(players: List<PartyPlayer>) {
    Panel {
        Text("SCORES", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = .6f))
        players.sortedByDescending { it.score }.forEachIndexed { i, p ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("${i + 1}.  ${p.name}", fontSize = 20.sp, color = Color.White, modifier = Modifier.weight(1f))
                Text("${p.score}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
private fun PlayerChips(names: List<String>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        names.forEach {
            Text(
                it,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier.background(Color(0xFF33333A), RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun Panel(content: @Composable () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = .06f), RoundedCornerShape(12.dp))
                .padding(horizontal = 18.dp, vertical = 14.dp),
    ) { content() }
}

/** Draws the lines a phone sent ("x,y x,y …", each out of 1000) on a white board */
@Composable
fun DrawingBoard(
    lines: List<String>,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier
            .aspectRatio(4f / 3f)
            .background(Color.White, RoundedCornerShape(12.dp)),
    ) {
        lines.forEach { line ->
            val pts =
                line.split(' ').mapNotNull { p ->
                    val xy = p.split(',')
                    val x = xy.getOrNull(0)?.toFloatOrNull() ?: return@mapNotNull null
                    val y = xy.getOrNull(1)?.toFloatOrNull() ?: return@mapNotNull null
                    Offset(x / 1000f * size.width, y / 1000f * size.height)
                }
            if (pts.size == 1) {
                drawCircle(Color.Black, radius = 5.dp.toPx(), center = pts[0])
            } else if (pts.size > 1) {
                val path =
                    Path().apply {
                        moveTo(pts[0].x, pts[0].y)
                        pts.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                drawPath(path, Color.Black, style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

/** A phone joining a party game on a TV */
@Composable
fun PartyJoinScreen(
    phoneName: String,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var tvs by remember { mutableStateOf<List<FoundEmberTv>>(emptyList()) }
    var view by remember { mutableStateOf<PhoneView?>(null) }
    val main = remember { Handler(Looper.getMainLooper()) }
    val client =
        remember {
            EmberLinkClient(
                context = context.applicationContext,
                phoneName = phoneName,
                onFound = { list -> main.post { tvs = list } },
                onView = { v -> main.post { view = v } },
                serviceType = PartyViewModel.SERVICE_TYPE,
                label = PartyViewModel.LABEL,
            )
        }
    DisposableEffect(client) {
        client.start()
        onDispose { client.stop() }
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier =
            modifier
                .fillMaxSize()
                .background(PuzzleBg)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
    ) {
        Text("BENFLIX PARTY", fontFamily = BenflixTitleFont, fontSize = 30.sp, color = Color.White)
        val v = view
        if (v == null) {
            Text(
                if (tvs.isEmpty()) {
                    "Looking for a party game… Start one on the TV first (Games › Party), and make sure this phone is on the same Wi-Fi."
                } else {
                    "Pick the TV to join:"
                },
                fontSize = 18.sp,
                lineHeight = 25.sp,
                color = Color.White,
            )
            tvs.forEach { t -> Button(onClick = { client.join(t) }, modifier = Modifier.fillMaxWidth()) { Text(t.name) } }
            Button(onClick = onLeave) { Text("Back") }
            return@Column
        }
        if (v.status.isNotBlank()) Text(v.status, fontSize = 14.sp, color = Color.White.copy(alpha = .6f))
        if (v.title.isNotBlank()) Text(v.title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = PuzzleGold)
        if (v.secret.isNotBlank()) {
            Text(
                v.secret,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                color = Color.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().background(PuzzleGold, RoundedCornerShape(10.dp)).padding(12.dp),
            )
        }
        if (v.text.isNotBlank()) Text(v.text, fontSize = 18.sp, lineHeight = 26.sp, color = Color.White)
        when (v.input) {
            "number" -> SendBox(v.round, number = true, hint = "Your number") { client.type(it) }
            "text" -> SendBox(v.round, number = false, hint = "Your made-up plot") { client.type(it) }
            "draw" -> DrawPad(v.round, onLine = { client.draw(it) })
        }
        v.actions.forEach { a ->
            Button(
                onClick = { client.press(a.id) },
                colors = ButtonDefaults.colors(containerColor = Color(0xFF2A2A2E), contentColor = Color.White),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(a.label, fontSize = 17.sp, modifier = Modifier.padding(vertical = 4.dp)) }
        }
        Button(onClick = {
            client.leave()
            onLeave()
        }) { Text("Leave the game") }
    }
}

@Composable
private fun SendBox(
    round: Int,
    number: Boolean,
    hint: String,
    onSend: (String) -> Unit,
) {
    var text by remember(round) { mutableStateOf("") }
    val send = {
        if (text.isNotBlank()) onSend(text.trim())
    }
    BasicTextField(
        value = text,
        onValueChange = {
            text =
                if (number) it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(8) else it.replace("\n", " ").take(300)
        },
        singleLine = number,
        minLines = if (number) 1 else 3,
        textStyle = TextStyle(color = Color.White, fontSize = 20.sp),
        cursorBrush = SolidColor(PuzzleGold),
        keyboardOptions =
            KeyboardOptions(
                keyboardType = if (number) KeyboardType.Decimal else KeyboardType.Text,
                imeAction = ImeAction.Send,
            ),
        keyboardActions = KeyboardActions(onSend = { send() }),
        decorationBox = { inner ->
            Box {
                if (text.isEmpty()) Text(hint, color = Color.White.copy(alpha = .4f), fontSize = 20.sp)
                inner()
            }
        },
        modifier =
            Modifier
                .fillMaxWidth()
                .background(PuzzlePanel, RoundedCornerShape(10.dp))
                .border(1.dp, Color(0xFF44444C), RoundedCornerShape(10.dp))
                .padding(14.dp),
    )
    Button(onClick = send, modifier = Modifier.fillMaxWidth()) {
        Text("Send", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

/** The phone's drawing pad: each finger stroke is sent to the TV as it's drawn */
@Composable
private fun DrawPad(
    round: Int,
    onLine: (String) -> Unit,
) {
    val lines = remember(round) { mutableStateListOf<String>() }
    var current by remember(round) { mutableStateOf<List<Offset>>(emptyList()) }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f),
    ) {
        DrawingBoard(lines + listOfNotNull(current.takeIf { it.isNotEmpty() }?.let { encode(it) }), Modifier.fillMaxSize())
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(round) {
                    detectDragGestures(
                        onDragStart = { current = listOf(Offset(it.x / size.width * 1000f, it.y / size.height * 1000f)) },
                        onDrag = { change, _ ->
                            change.consume()
                            current = current + Offset(change.position.x / size.width * 1000f, change.position.y / size.height * 1000f)
                            // Long strokes go to the TV in pieces so it draws along live
                            if (current.size >= 24) {
                                val piece = encode(current)
                                lines.add(piece)
                                onLine(piece)
                                current = listOf(current.last())
                            }
                        },
                        onDragEnd = {
                            if (current.isNotEmpty()) {
                                val piece = encode(current)
                                lines.add(piece)
                                onLine(piece)
                            }
                            current = emptyList()
                        },
                    )
                },
        )
    }
    Button(onClick = {
        lines.clear()
        onLine("clear")
    }) { Text("Clear drawing") }
}

private fun encode(points: List<Offset>): String =
    points.joinToString(" ") { "${it.x.toInt().coerceIn(0, 1000)},${it.y.toInt().coerceIn(0, 1000)}" }
