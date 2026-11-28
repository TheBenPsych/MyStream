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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
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
import com.github.damontecres.wholphin.ui.setup.SeaColours
import com.github.damontecres.wholphin.ui.setup.SeaFriend
import com.github.damontecres.wholphin.ui.setup.SeaFriendImage
import com.github.damontecres.wholphin.ui.theme.BenflixFont
import com.github.damontecres.wholphin.ui.theme.BenflixRed
import com.github.damontecres.wholphin.ui.theme.BenflixShapes
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.theme.Button
import com.github.damontecres.wholphin.ui.theme.Glowfin
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import kotlinx.coroutines.delay

/**
 * Glowfin G1 party lobby: one card per player with a sea friend (picked from their name, so it stays the same
 * each time), then dashed open spots up to 4 on TV (2 on phones), so you can see room for more.
 */
@Composable
private fun LobbyCards(
    names: List<String>,
    isTv: Boolean,
) {
    val perRow = if (isTv) 4 else 2
    // Phones only fill the last row (a whole extra row of open spots pushed Start off the screen)
    val open = (perRow - names.size % perRow).takeIf { names.size < 8 && it > 0 && (isTv || it < perRow) } ?: 0
    val spots: List<String?> = names + List(if (names.isEmpty()) perRow else open) { null }
    val shape = RoundedCornerShape(14.dp)
    spots.chunked(perRow).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
            row.forEach { name ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                    modifier =
                        Modifier
                            .weight(1f)
                            .aspectRatio(if (isTv) 1.6f else 2f)
                            .then(
                                if (name != null) {
                                    Modifier.background(Glowfin.Raise, shape)
                                } else {
                                    Modifier.border(2.dp, Glowfin.Line, shape)
                                },
                            ).padding(10.dp),
                ) {
                    if (name != null) {
                        val pick = name.lowercase().hashCode().let { if (it < 0) -it else it }
                        SeaFriendImage(
                            friend = SeaFriend.entries[pick % SeaFriend.entries.size],
                            colour = (pick / 7) % SeaColours.size,
                            contentDescription = null,
                            modifier = Modifier.size(if (isTv) 64.dp else 40.dp).clip(CircleShape),
                        )
                        Text(name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Glowfin.Text, maxLines = 1)
                    } else {
                        Text("Open spot", fontSize = 16.sp, color = Glowfin.Dim)
                        Text("Join party on a phone", fontSize = 13.sp, color = Glowfin.Dim, textAlign = TextAlign.Center)
                    }
                }
            }
            repeat(perRow - row.size) { Box(Modifier.weight(1f)) }
        }
    }
}

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
                Text(
                    ui.kind.title,
                    fontFamily = BenflixFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = BenflixType.pageTitle,
                    color = Color.White,
                )
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
            "Open Glowfin on your phone, go to Games, and press Join party. Phones need to be on the same Wi-Fi as this ${if (isTv) "TV" else "phone"}.",
            fontSize = 18.sp,
            lineHeight = 26.sp,
            color = Color.White,
        )
    }
    // Glowfin G1: a card per player with their sea friend, and open spots still to fill
    Text(
        "PLAYERS (${ui.players.size}) · ${ui.kind.minPlayers} TO 8",
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = Glowfin.Dim,
        modifier = Modifier.fillMaxWidth(),
    )
    LobbyCards(ui.players.map { it.name }, isTv)
    ui.error?.let { Text(it, fontSize = 16.sp, color = BenflixRed) }
    val need = ui.kind.minPlayers - ui.players.size
    Button(
        onClick = { viewModel.startGame() },
        modifier = Modifier.focusRequester(focus),
        scale = ButtonDefaults.scale(focusedScale = 1f),
    ) {
        Text(if (need > 0) "Needs $need more ${if (need == 1) "player" else "players"}" else "Start with ${ui.players.size}")
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
    // Players still here who need to answer (not the artist, not anyone whose phone left)
    val answering = ui.players.count { !it.away && !(ui.phase == PartyPhase.DRAWING && it.id == ui.drawer) }
    Text(
        "Round ${ui.round} of ${ui.rounds}  ·  $left s  ·  ${ui.answered.size} of $answering answered",
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
            // Caption this: the scene stays up while everyone votes
            if (ui.kind == PartyKind.CAPTION) {
                ui.image?.let { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .width(if (isTv) tvPictureHeight(0.26f) * (16f / 9f) else 260.dp)
                                .aspectRatio(16f / 9f)
                                .background(PuzzlePanel, BenflixShapes.Card),
                    )
                }
            }
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
                            .background(PuzzlePanel, BenflixShapes.Card),
                )
            }
            ChoicesShown(ui.choices)
        }
    }
    PlayerChips(ui.players.filter { !it.away }.map { (if (it.id in ui.answered) "✓ " else "") + it.name })
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
                modifier = Modifier.background(Color(0xFF2A2A2E), BenflixShapes.Card).padding(horizontal = 14.dp, vertical = 8.dp),
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
    val top = ui.players.maxOfOrNull { it.score } ?: 0
    val winners = ui.players.filter { it.score == top }
    Text(
        when {
            top == 0 -> "Game over: nobody scored"
            winners.size > 1 -> "It's a tie: " + winners.joinToString(" and ") { it.name }
            else -> "${winners[0].name} wins!"
        },
        fontFamily = BenflixFont,
        fontWeight = FontWeight.Bold,
        fontSize = BenflixType.pageTitle,
        color = PuzzleGold,
        textAlign = TextAlign.Center,
    )
    Scoreboard(ui.players)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = {
            viewModel.startGame()
        }, modifier = Modifier.focusRequester(focus), scale = ButtonDefaults.scale(focusedScale = 1f)) { Text("Play again") }
        Button(onClick = { viewModel.backToLobby() }, scale = ButtonDefaults.scale(focusedScale = 1f)) { Text("Back to players") }
    }
}

@Composable
private fun Scoreboard(players: List<PartyPlayer>) {
    Panel {
        Text("SCORES", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = .6f))
        players.sortedByDescending { it.score }.forEachIndexed { i, p ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "${i + 1}.  ${p.name}" + if (p.away) "  (left)" else "",
                    fontSize = 20.sp,
                    color = Color.White.copy(alpha = if (p.away) .5f else 1f),
                    modifier = Modifier.weight(1f),
                )
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
                .background(Color.White.copy(alpha = .06f), BenflixShapes.Card)
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
            .background(Color.White, BenflixShapes.Card),
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
        Text("Glowfin Party", fontFamily = BenflixFont, fontWeight = FontWeight.Bold, fontSize = BenflixType.pageTitle, color = Color.White)
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
            tvs.forEach { t ->
                Button(
                    onClick = { client.join(t) },
                    modifier = Modifier.fillMaxWidth(),
                    scale = ButtonDefaults.scale(focusedScale = 1f),
                ) { Text(t.name) }
            }
            Button(onClick = onLeave, scale = ButtonDefaults.scale(focusedScale = 1f)) { Text("Back") }
            return@Column
        }
        if (v.status.isNotBlank()) Text(v.status, fontSize = 14.sp, color = Color.White.copy(alpha = .6f))
        if (v.title.isNotBlank()) Text(v.title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = PuzzleGold)
        if (v.secret.isNotBlank()) {
            Text(
                v.secret,
                fontSize = 26.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.Black,
                color = Color.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().background(PuzzleGold, BenflixShapes.Card).padding(12.dp),
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
                scale = ButtonDefaults.scale(focusedScale = 1f),
            ) { Text(a.label, fontSize = 17.sp, modifier = Modifier.padding(vertical = 4.dp)) }
        }
        Button(onClick = {
            client.leave()
            onLeave()
        }, scale = ButtonDefaults.scale(focusedScale = 1f)) { Text("Leave the game") }
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
                .background(PuzzlePanel, BenflixShapes.Card)
                .border(1.dp, Color(0xFF44444C), BenflixShapes.Card)
                .padding(14.dp),
    )
    Button(onClick = send, modifier = Modifier.fillMaxWidth(), scale = ButtonDefaults.scale(focusedScale = 1f)) {
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
    }, scale = ButtonDefaults.scale(focusedScale = 1f)) { Text("Clear drawing") }
}

private fun encode(points: List<Offset>): String =
    points.joinToString(" ") { "${it.x.toInt().coerceIn(0, 1000)},${it.y.toInt().coerceIn(0, 1000)}" }
