@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.github.damontecres.wholphin.ui.games.ember

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.NonSkippableComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.games.ShowPicker
import com.github.damontecres.wholphin.games.SideShow
import com.github.damontecres.wholphin.games.SideShowItem
import com.github.damontecres.wholphin.ui.games.tvPictureHeight
import com.github.damontecres.wholphin.ui.theme.BenflixTitleFont
import com.github.damontecres.wholphin.ui.tryRequestFocus
import kotlinx.coroutines.delay

/** The journey itself: the picture on top, the words and choices below */
@Composable
fun EmberPlay(
    vm: EmberViewModel,
    isTv: Boolean,
) {
    val e = vm.engine ?: return
    // Reading the version makes everything below redraw when the game changes.
    // The game's state isn't Compose state, so the pieces below are marked NonSkippable: they must redraw with it.
    vm.version
    var menu by remember { mutableStateOf(false) }
    var bag by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var pickerBack by remember { mutableStateOf<(() -> Unit)?>(null) }

    // Watching a show on the side (TV only)
    val context = LocalContext.current
    val show =
        remember {
            if (isTv) {
                vm.sideShowAuth()?.let { (srv, user, token) ->
                    SideShow(context, srv, user, token).takeIf { it.available }
                }
            } else {
                null
            }
        }
    val texture = remember { TextureView(context) }
    var showOn by remember { mutableStateOf(false) }
    var shape by remember { mutableFloatStateOf(16f / 9f) }
    val main = remember { Handler(Looper.getMainLooper()) }
    DisposableEffect(Unit) {
        onDispose {
            show?.stop()
            vm.musicPaused = false
        }
    }

    fun stopShow() {
        show?.stop()
        showOn = false
        vm.musicPaused = false
    }

    fun startShow(item: SideShowItem) {
        val s = show ?: return
        showOn = true
        vm.musicPaused = true
        s.play(
            item = item,
            view = texture,
            onShape = { v -> main.post { shape = v.coerceIn(1f, 2.6f) } },
            onEnded = {
                Thread {
                    val next = runCatching { s.nextEpisode(item) }.getOrNull()
                    main.post { if (next != null) startShow(next) else stopShow() }
                }.start()
            },
        )
    }

    BackHandler {
        when {
            picking -> pickerBack?.invoke() ?: run { picking = false }
            bag -> bag = false
            menu -> menu = false
            else -> menu = true
        }
    }

    val first = remember { FocusRequester() }
    val focusKey = "${e.state.phase}/${e.state.event}/${e.state.stop}/${e.state.minigame}/${e.state.forkOptions}/${e.state.day}"
    LaunchedEffect(focusKey, menu, bag, picking) {
        if (!menu && !bag && !picking) {
            delay(60)
            first.tryRequestFocus()
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (isTv) {
            // Extra room at the bottom: many TVs crop the edge of the picture (overscan), which cut off the last buttons
            Column(
                Modifier.fillMaxSize().padding(start = 32.dp, end = 32.dp, top = 16.dp, bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(Modifier.height(tvPictureHeight(0.44f)), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Scene(vm, e, Modifier.fillMaxHeight())
                    PartyPanel(e, Modifier.weight(1f).fillMaxHeight())
                }
                StatusBar(e)
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PhaseContent(vm, e, isTv, first, onBag = { bag = true })
                }
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box {
                    Scene(vm, e, Modifier.fillMaxWidth())
                    EmberButton("☰", onClick = { menu = true }, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp))
                }
                StatusBar(e)
                PartyRow(e)
                PhaseContent(vm, e, isTv, first, onBag = { bag = true })
            }
        }
        if (showOn) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 16.dp, end = 32.dp)
                    .width(380.dp)
                    .aspectRatio(shape)
                    .border(2.dp, EmberGold, RoundedCornerShape(4.dp))
                    .background(Color.Black),
            ) {
                AndroidView(factory = { texture }, modifier = Modifier.fillMaxSize())
            }
        }
        if (bag) BagPanel(vm, e, isTv) { bag = false }
        if (menu) {
            MenuPanel(
                vm = vm,
                isTv = isTv,
                canShow = show != null,
                showOn = showOn,
                onClose = { menu = false },
                onBag = {
                    menu = false
                    bag = true
                },
                onPickShow = {
                    menu = false
                    picking = true
                },
                onStopShow = {
                    stopShow()
                    menu = false
                },
            )
        }
        if (picking) {
            val pickFocus = remember { FocusRequester() }
            ShowPicker(
                show = show,
                isTv = isTv,
                tint = Color(0xFF5A2A1E),
                firstFocus = pickFocus,
                registerBack = { pickerBack = it },
                onPick = { item ->
                    picking = false
                    startShow(item)
                },
                onCancel = { picking = false },
            )
            LaunchedEffect(Unit) { pickFocus.tryRequestFocus() }
        }
    }
}

private fun sceneFor(e: EmberEngine): Pair<String, SceneOpts> {
    val st = e.state
    val base =
        SceneOpts(
            weather = st.weather,
            party = e.active.size,
            pet = e.hasPet,
            cartLook = st.setup.cart,
            egg = e.eggHere,
            hatched = e.hatched,
            season = e.season,
        )
    return when (st.phase) {
        Phase.EVENT -> {
            val ev = e.currentEvent
            (ev?.scene ?: e.land.scene) to base.copy(creature = ev?.creature?.let { e.story.creature(it)?.art }, cart = ev?.scene == null)
        }

        Phase.STOP, Phase.MARKET -> {
            (e.currentStop?.scene ?: e.land.scene) to base
        }

        Phase.CAMP -> {
            "camp" to base
        }

        Phase.OVER -> {
            (e.endingDef?.scene ?: "peak") to base.copy(weather = "clear")
        }

        Phase.TRAVEL, Phase.MINIGAME -> {
            e.land.scene to base.copy(cart = true)
        }

        else -> {
            e.land.scene to base
        }
    }
}

@Composable
@NonSkippableComposable
private fun Scene(
    vm: EmberViewModel,
    e: EmberEngine,
    modifier: Modifier,
) {
    val (key, opts) = sceneFor(e)
    Box(modifier.aspectRatio(16f / 9f).border(2.dp, Color(0xFF3A2A30))) {
        PixelScene(key, opts.copy(crt = vm.crt), Modifier.fillMaxSize())
        Text(
            when (e.state.phase) {
                Phase.STOP, Phase.MARKET -> e.currentStop?.name.orEmpty()
                Phase.OVER -> e.endingDef?.title.orEmpty()
                else -> e.land.name
            },
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
@NonSkippableComposable
private fun StatusBar(e: EmberEngine) {
    val st = e.state
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .background(EmberPanel, RoundedCornerShape(6.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Stat("Day", "${st.day} · ${e.season}")
        Stat("Food", "${st.food}", warn = st.food < e.active.size * 6)
        Stat("Gold", "${st.gold}")
        Stat("Spirit", "${st.spirit}/10", warn = st.spirit <= 2)
        when {
            e.hatched -> Stat("Dragon", "Ember")
            !e.eggHere -> Stat("Egg", "gone!", warn = true)
            else -> Stat("Egg", "warmth ${st.warmth}/10 · hatches ~${st.hatchIn}d", warn = st.warmth <= 3)
        }
        Stat("Cart", "${st.cart}/10", warn = st.cart <= 3)
        Stat("Herbs", "${st.herbs}")
        Stat("Magic", if (st.wild == 0) "calm" else "wild ${st.wild}", warn = st.wild >= 5)
        if (st.phase == Phase.TRAVEL) Stat("Road", "${st.miles}/${st.legMiles} mi · leg ${e.legIndex + 1}/${st.legs}")
    }
}

@Composable
@NonSkippableComposable
private fun PartyPanel(
    e: EmberEngine,
    modifier: Modifier,
) {
    Column(
        modifier
            .background(EmberPanel, RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("The party", color = EmberGold, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text("★ leads · ties in votes go to the TV", color = EmberDim, fontSize = 11.sp)
        val names = e.story.traits.associate { it.id to it.name }
        e.state.members.forEach { m -> MemberLine(m, names) }
        e.pet?.let { p ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PixelPortrait(null, petId = p.id, size = 32.dp, faded = e.state.petGone)
                Text(
                    p.name + if (e.state.petGone) " (lost)" else "",
                    color = if (e.state.petGone) EmberDim else EmberText,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

@Composable
@NonSkippableComposable
private fun MemberLine(
    m: Member,
    traitNames: Map<String, String> = emptyMap(),
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PixelPortrait(m, size = 32.dp, faded = m.gone)
        Column {
            Text(
                m.name + if (m.leader) " ★" else "",
                color = if (m.gone) EmberDim else EmberText,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            val about =
                listOfNotNull(
                    m.role?.let { EmberSkills[it]?.first },
                    m.traits.firstOrNull()?.let { t -> traitNames[t] },
                ).joinToString(" · ")
            if (about.isNotEmpty()) Text(about, color = EmberDim, fontSize = 12.sp, maxLines = 1)
            Text(
                when {
                    m.gone -> "went home"
                    m.sick -> "♥".repeat(m.health.coerceAtLeast(0)) + " fever"
                    else -> "♥".repeat(m.health.coerceAtLeast(0))
                },
                color =
                    if (m.gone) {
                        EmberDim
                    } else if (m.sick) {
                        EmberBad
                    } else {
                        Color(0xFFE05A6A)
                    },
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
@NonSkippableComposable
private fun PartyRow(e: EmberEngine) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val names = e.story.traits.associate { it.id to it.name }
        e.state.members.forEach { MemberLine(it, names) }
        e.pet?.let { p ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                PixelPortrait(null, petId = p.id, size = 32.dp, faded = e.state.petGone)
            }
        }
    }
}

/** The words and buttons for whatever's happening now */
@Composable
@NonSkippableComposable
private fun PhaseContent(
    vm: EmberViewModel,
    e: EmberEngine,
    isTv: Boolean,
    first: FocusRequester,
    onBag: () -> Unit,
) {
    val st = e.state
    when (st.phase) {
        Phase.TRAVEL -> {
            val text = vm.log?.text?.takeIf { it.isNotBlank() } ?: e.land.blurb
            TypeText(text)
            if (st.day <= 2 && e.legIndex == 0) {
                Text(
                    "New here? Keep going travels one day. Make camp rests everyone and warms the egg. " +
                        "Watch Food and the egg's warmth at the top. Bag & journal shows your things and spells.",
                    color = EmberGold.copy(alpha = 0.85f),
                    fontSize = 14.sp,
                )
            }
            vm.log?.let { ChangeChips(it.changes) }
            val here = vm.family.filter { it.land == st.route.last() }
            if (here.isNotEmpty()) {
                Text(
                    "Trail markers on this road: " + here.joinToString { it.code }.take(80),
                    color = EmberGold,
                    fontSize = 14.sp,
                )
            }
            ActionRow(vm, first, extra = listOf(Act("bag", "Bag & journal")), onExtra = { onBag() })
        }

        Phase.EVENT -> {
            val ev = e.currentEvent ?: return
            Text(ev.title, color = EmberGold, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            TypeText(e.eventText(ev))
            if (e.choices(ev).any { (_, c) -> c.roll != null }) {
                Text(
                    "A % is the chance it works. It's a dice roll, and your skills and traits can raise it.",
                    color = EmberDim,
                    fontSize = 13.sp,
                )
            }
            if (vm.joined.isNotEmpty()) {
                Text(
                    "This is a vote: each phone and the TV picks one. When everyone has voted, the most votes win " +
                        "(a tie goes to the TV). Pick on the TV too, or press Decide now to stop waiting.",
                    color = EmberGold,
                    fontSize = 14.sp,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.widthIn(max = if (isTv) 620.dp else 900.dp)) {
                vm.actions().forEachIndexed { i, a ->
                    val votes = vm.votesFor(a.id)
                    EmberButton(
                        a.label,
                        sub = if (votes.isNotEmpty()) "Votes: " + votes.joinToString() else null,
                        onClick = { vm.doAction(a.id) },
                        focusRequester = if (i == 0) first else null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (vm.joined.isNotEmpty() && vm.tvVote != null) {
                    EmberButton("Decide now (don't wait for everyone)", onClick = vm::decideNow, tint = Color(0xFF5A2A1E))
                }
            }
        }

        Phase.RESULT -> {
            val shown = st.shown
            TypeText(shown?.text.orEmpty())
            shown?.let { ChangeChips(it.changes) }
            vm.voteNote?.let { Text(it, color = EmberDim, fontSize = 13.sp) }
            ActionRow(vm, first)
        }

        Phase.STOP -> {
            StopContent(vm, e, isTv, first, onBag)
        }

        Phase.MARKET -> {
            MarketContent(vm, e, first)
        }

        Phase.FORK -> {
            ForkContent(vm, e, isTv, first)
        }

        Phase.CAMP -> {
            CampContent(vm, e, isTv, first, onBag)
        }

        Phase.MINIGAME -> {
            key(st.minigame, st.day) {
                EmberMiniGame(st.minigame ?: "fish", isTv, vm.minigameInput) { vm.minigameDone(it) }
            }
        }

        Phase.OVER -> {
            RecapContent(vm, e, isTv, first)
        }
    }
}

@Composable
@NonSkippableComposable
private fun ActionRow(
    vm: EmberViewModel,
    first: FocusRequester,
    extra: List<Act> = emptyList(),
    onExtra: (Act) -> Unit = {},
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        vm.actions().forEachIndexed { i, a ->
            EmberButton(
                a.label,
                sub = a.hint.ifBlank { null },
                onClick = { vm.doAction(a.id) },
                focusRequester =
                    if (i ==
                        0
                    ) {
                        first
                    } else {
                        null
                    },
                big = i == 0,
                tint = if (i == 0) Color(0xFF5A2A1E) else EmberPanel,
            )
        }
        extra.forEach { a -> EmberButton(a.label, onClick = { onExtra(a) }) }
    }
}

// -----------------------------------------------------------------------------------------------
// Stops: the text adventure part

@Composable
@NonSkippableComposable
private fun StopContent(
    vm: EmberViewModel,
    e: EmberEngine,
    isTv: Boolean,
    first: FocusRequester,
    onBag: () -> Unit,
) {
    val s = e.currentStop ?: return
    var verb by remember(s.id) { mutableStateOf<Verb?>(null) }
    var with by remember(s.id) { mutableStateOf<String?>(null) }
    var typing by remember(s.id) { mutableStateOf(!isTv) }
    val log = vm.log
    TypeText(log?.text?.takeIf { it.isNotBlank() } ?: e.stopIntro(s))
    log?.let { ChangeChips(it.changes) }
    if (typing) {
        TypeBox(isTv, autoFocus = isTv) { words -> vm.type(words) }
        if (isTv) {
            Text(
                "Typing works really well on a phone: pick Join the TV's game in Ember Road there.",
                color = EmberDim.copy(alpha = 0.7f),
                fontSize = 12.sp,
            )
        }
    }
    val things = e.visibleThings(s)
    when {
        verb == null -> {
            Text("Pick what to do, then what to do it to. Leave when you're ready to go on.", color = EmberDim, fontSize = 13.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Verb.entries.forEachIndexed { i, v ->
                    EmberButton(v.label, onClick = {
                        verb = v
                        with = null
                    }, focusRequester = if (i == 0) first else null)
                }
                if (isTv) EmberButton(if (typing) "Hide typing" else "Type", onClick = { typing = !typing })
                EmberButton("Bag", onClick = onBag)
                if (s.market) EmberButton("Market", onClick = vm::openMarket, tint = Color(0xFF3A4A2A))
                EmberButton(if (s.final) "Finish the journey" else "Leave", onClick = vm::leaveStop, tint = Color(0xFF5A2A1E))
            }
        }

        (verb == Verb.USE || verb == Verb.CAST) && with == null -> {
            Text(
                if (verb ==
                    Verb.USE
                ) {
                    "Use what?"
                } else {
                    "Cast which spell?"
                },
                color = EmberGold,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val options =
                    if (verb == Verb.USE) {
                        e.state.items
                            .distinct()
                            .map { it to (e.story.item(it)?.name ?: it) } + ("" to "Just use it")
                    } else {
                        e.state.spells.map { it to "✦ " + (e.story.spell(it)?.name ?: it) }
                    }
                options.forEachIndexed { i, (id, label) ->
                    EmberButton(label, onClick = { with = id }, focusRequester = if (i == 0) first else null)
                }
                EmberButton("Back", onClick = { verb = null })
            }
            LaunchedEffect(verb) { first.tryRequestFocus() }
        }

        else -> {
            val v = verb!!
            Text(
                when (v) {
                    Verb.LOOK -> "Look at what?"
                    Verb.TALK -> "Talk to who?"
                    Verb.TAKE -> "Take what?"
                    Verb.USE -> if (with.isNullOrEmpty()) "Use what?" else "Use ${e.story.item(with!!)?.name} on what?"
                    Verb.CAST -> "Cast ${e.story.spell(with!!)?.name} on what?"
                },
                color = EmberGold,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val list = if (v == Verb.TALK) things.sortedByDescending { it.person } else things
                list.forEachIndexed { i, t ->
                    EmberButton(t.name, onClick = {
                        vm.act(v, t.id, with?.ifEmpty { null })
                        verb = null
                        with = null
                    }, focusRequester = if (i == 0) first else null)
                }
                EmberButton("Back", onClick = {
                    verb = null
                    with = null
                })
            }
            LaunchedEffect(verb, with) { first.tryRequestFocus() }
        }
    }
}

/** Type a command: "look under the bridge", "use rope on well", "say the secret word" */
@Composable
@NonSkippableComposable
fun TypeBox(
    isTv: Boolean,
    autoFocus: Boolean,
    onSend: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    if (autoFocus) LaunchedEffect(Unit) { focus.tryRequestFocus() }
    val send = {
        if (text.isNotBlank()) {
            onSend(text)
            text = ""
        }
    }
    val voice =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.let { onSend(it) }
        }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        BasicTextField(
            value = text,
            onValueChange = { text = it.replace("\n", "").take(120) },
            singleLine = true,
            textStyle = TextStyle(color = EmberText, fontSize = 17.sp),
            cursorBrush = SolidColor(EmberGold),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { send() }),
            modifier =
                Modifier
                    .weight(1f)
                    .focusRequester(focus)
                    .background(Color(0xFF15101A), RoundedCornerShape(6.dp))
                    .border(1.dp, Color(0xFF4A3A40), RoundedCornerShape(6.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            decorationBox = { inner ->
                Box {
                    if (text.isEmpty()) Text("> type what to do… (look at well)", color = EmberDim.copy(alpha = 0.6f), fontSize = 17.sp)
                    inner()
                }
            },
        )
        EmberButton("Do it", onClick = send)
        if (isTv) {
            var noMic by remember { mutableStateOf(false) }
            EmberButton(if (noMic) "No mic" else "🎤 Say it", onClick = {
                try {
                    voice.launch(
                        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            .putExtra(RecognizerIntent.EXTRA_PROMPT, "What do you want to do?"),
                    )
                } catch (_: ActivityNotFoundException) {
                    noMic = true
                }
            })
        }
    }
}

@Composable
@NonSkippableComposable
private fun MarketContent(
    vm: EmberViewModel,
    e: EmberEngine,
    first: FocusRequester,
) {
    Text("Market · you have ${e.state.gold} gold", color = EmberGold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    vm.log?.let {
        Text(it.text, color = EmberText, fontSize = 16.sp)
        ChangeChips(it.changes)
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        e.shopItems().forEachIndexed { i, item ->
            val price = e.price(item)
            EmberButton(
                "${item.name} · $price gold",
                sub = item.desc,
                enabled = e.state.gold >= price,
                onClick = { vm.buy(item.id) },
                focusRequester = if (i == 0) first else null,
                modifier = Modifier.width(260.dp),
            )
        }
    }
    val sellable =
        e.state.items
            .distinct()
            .filter { e.sellValue(it) > 0 }
    if (sellable.isNotEmpty()) {
        Text("Sell", color = EmberGold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            sellable.forEach { id ->
                EmberButton("${e.story.item(id)?.name ?: id} · +${e.sellValue(id)}", onClick = { vm.sell(id) })
            }
        }
    }
    EmberButton("Done shopping", onClick = vm::closeMarket, tint = Color(0xFF5A2A1E))
}

@Composable
@NonSkippableComposable
private fun ForkContent(
    vm: EmberViewModel,
    e: EmberEngine,
    isTv: Boolean,
    first: FocusRequester,
) {
    val st = e.state
    Text("The road splits. Which way?", color = EmberGold, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    Text(
        st.route.joinToString("  →  ") { e.story.land(it).name } + "  →  ?" + if (e.legIndex < st.legs - 2) "  →  Ember Peak" else "",
        color = EmberDim,
        fontSize = 14.sp,
    )
    val cards: @Composable (Modifier) -> Unit = { mod ->
        st.forkOptions.forEachIndexed { i, id ->
            val land = e.story.land(id)
            val marks = vm.family.filter { it.land == id }
            Column(
                mod
                    .background(EmberPanel, RoundedCornerShape(8.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PixelScene(land.scene, SceneOpts(season = e.season, crt = vm.crt), Modifier.fillMaxWidth(), animate = false)
                Text(land.name, color = EmberGold, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(e.forkLabel(id), color = if (land.tag == "danger") EmberBad else EmberDim, fontSize = 13.sp)
                Text(land.blurb, color = EmberText, fontSize = 14.sp, maxLines = 4)
                if (marks.isNotEmpty()) Text("Markers: " + marks.joinToString { it.code }, color = EmberGold, fontSize = 13.sp)
                EmberButton(
                    "Go this way",
                    onClick = { vm.pickFork(id) },
                    focusRequester =
                        if (i ==
                            0
                        ) {
                            first
                        } else {
                            null
                        },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    if (isTv) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) { cards(Modifier.weight(1f)) }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { cards(Modifier.fillMaxWidth()) }
    }
}

@Composable
@NonSkippableComposable
private fun CampContent(
    vm: EmberViewModel,
    e: EmberEngine,
    isTv: Boolean,
    first: FocusRequester,
    onBag: () -> Unit,
) {
    val shown = e.state.shown
    // The story and its buttons come first; on a TV the movie sits beside them, so nothing is pushed off the screen
    val story: @Composable () -> Unit = {
        TypeText(shown?.text.orEmpty())
        shown?.let { ChangeChips(it.changes) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EmberButton(
                "Pack up and go on",
                sub = "Back on the road",
                onClick = vm::leaveCamp,
                big = true,
                focusRequester = first,
                tint = Color(0xFF5A2A1E),
            )
            EmberButton("Bag & journal", onClick = onBag)
        }
    }
    val movie: @Composable () -> Unit = {
        vm.campMovie?.let { m ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier =
                    Modifier
                        .background(Color(0xFF2A1C18), RoundedCornerShape(8.dp))
                        .padding(10.dp),
            ) {
                AsyncImage(
                    model = m.poster,
                    contentDescription = m.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.width(if (isTv) 70.dp else 60.dp).aspectRatio(2f / 3f),
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f, fill = false)) {
                    Text("Tonight's campfire story is a real movie:", color = EmberDim, fontSize = 13.sp)
                    Text(m.name + (m.year?.let { " ($it)" } ?: ""), color = EmberGold, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    EmberButton(if (m.added) "On My List ✓" else "Add to My List", onClick = vm::addCampMovie, enabled = !m.added)
                }
            }
        }
    }
    if (isTv) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) { story() }
            Box(Modifier.widthIn(max = 380.dp)) { movie() }
        }
    } else {
        story()
        movie()
    }
}

// -----------------------------------------------------------------------------------------------
// The end: a postcard of the journey

@Composable
@NonSkippableComposable
private fun RecapContent(
    vm: EmberViewModel,
    e: EmberEngine,
    isTv: Boolean,
    first: FocusRequester,
) {
    val st = e.state
    val ending = e.endingDef ?: return
    Text(ending.title, fontFamily = BenflixTitleFont, color = EmberGold, fontSize = if (isTv) 30.sp else 26.sp)
    TypeText(if (e.kids && ending.kidsText != null) ending.kidsText else e.fill(ending.text))
    Text(
        "${st.score} points" + if (vm.newBest) "  ·  New best!" else "",
        color = if (vm.newBest) EmberGood else EmberText,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
    )
    val made = e.active.map { it.name }
    val left = st.members.filter { it.gone }
    val lines =
        listOfNotNull(
            "${st.day} days on the road, ${st.route.size} lands",
            "Made it: " + made.joinToString().ifEmpty { "nobody" },
            if (left.isNotEmpty()) "Went home: " + left.joinToString { "${it.name} (${it.goneWhy})" } else null,
            e.pet?.let { if (st.petGone) "${it.name} got lost along the way" else "${it.name} came too" },
            when {
                e.hatched -> "The egg hatched: Ember the dragon came along"
                !e.eggHere -> "The egg was lost"
                else -> "The egg made it"
            },
            "Creatures met: ${st.book.size} · Secrets found: ${st.secrets}",
        )
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF2A1C18), RoundedCornerShape(8.dp))
                .padding(12.dp),
    ) {
        lines.forEach { Text(it, color = EmberText, fontSize = 15.sp) }
        if (st.journal.isNotEmpty()) {
            Text(
                "From the journal",
                color = EmberGold,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 6.dp),
            )
            st.journal.takeLast(8).forEach { Text(it, color = EmberDim, fontSize = 13.sp) }
        }
    }
    if (!vm.markLeft) {
        Text("Leave a trail marker here for the family to find:", color = EmberGold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val choices = remember(st.setup.seed) { vm.markerChoices() }
            choices.forEachIndexed { i, line ->
                EmberButton("\"$line\"", onClick = { vm.leaveMark(line) }, focusRequester = if (i == 0) first else null)
            }
        }
        EmberButton("Skip the marker", onClick = vm::backToTitle)
    } else {
        Text("Marker left! Your family will see it on their journeys.", color = EmberGood, fontSize = 15.sp)
        EmberButton("Back to the start", onClick = vm::backToTitle, big = true, focusRequester = first)
        LaunchedEffect(Unit) { first.tryRequestFocus() }
    }
}

// -----------------------------------------------------------------------------------------------
// Menu and bag

@Composable
@NonSkippableComposable
private fun MenuPanel(
    vm: EmberViewModel,
    isTv: Boolean,
    canShow: Boolean,
    showOn: Boolean,
    onClose: () -> Unit,
    onBag: () -> Unit,
    onPickShow: () -> Unit,
    onStopShow: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.tryRequestFocus() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                Modifier
                    .fillMaxHeight()
                    .width(if (isTv) 400.dp else 300.dp)
                    .background(Color(0xF2120D14))
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
        ) {
            Text("Ember Road", fontFamily = BenflixTitleFont, color = EmberGold, fontSize = 26.sp)
            EmberButton("Back to the game", onClick = onClose, focusRequester = focus, modifier = Modifier.fillMaxWidth())
            EmberButton("Bag & journal", onClick = onBag, modifier = Modifier.fillMaxWidth())
            if (canShow) {
                if (showOn) {
                    EmberButton("Change the show", onClick = onPickShow, modifier = Modifier.fillMaxWidth())
                    EmberButton("Stop the show", onClick = onStopShow, modifier = Modifier.fillMaxWidth())
                } else {
                    EmberButton(
                        "Watch a show on the side",
                        sub = "It plays in the corner while you play",
                        onClick = onPickShow,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            EmberButton(if (vm.music) "Music: on" else "Music: off", onClick = vm::toggleMusic, modifier = Modifier.fillMaxWidth())
            EmberButton(if (vm.crt) "Old TV look: on" else "Old TV look: off", onClick = vm::toggleCrt, modifier = Modifier.fillMaxWidth())
            if (vm.joined.isNotEmpty()) Text("Phones: " + vm.joined.joinToString { it.name }, color = EmberDim, fontSize = 13.sp)
            EmberButton("Save and go to the start", onClick = vm::quit, modifier = Modifier.fillMaxWidth())
            EmberButton("Save and leave Ember Road", onClick = vm::leaveGame, modifier = Modifier.fillMaxWidth())
            Text("The journey saves by itself, and you can carry on from another TV or phone.", color = EmberDim, fontSize = 12.sp)
        }
    }
}

@Composable
@NonSkippableComposable
private fun BagPanel(
    vm: EmberViewModel,
    e: EmberEngine,
    isTv: Boolean,
    onClose: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.tryRequestFocus() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                Modifier
                    .fillMaxWidth(if (isTv) 0.7f else 0.95f)
                    .fillMaxHeight(0.9f)
                    .background(EmberBg, RoundedCornerShape(10.dp))
                    .border(2.dp, Color(0xFF3A2A30), RoundedCornerShape(10.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Bag & journal", color = EmberGold, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                EmberButton("Close", onClick = onClose, focusRequester = focus)
            }
            Text("Items", color = EmberGold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            if (e.state.items.isEmpty()) Text("Nothing but crumbs.", color = EmberDim, fontSize = 14.sp)
            e.state.items
                .groupingBy { it }
                .eachCount()
                .forEach { (id, n) ->
                    val item = e.story.item(id)
                    Text(
                        (item?.name ?: id) + (
                            if (n >
                                1
                            ) {
                                " ×$n"
                            } else {
                                ""
                            }
                        ) + (item?.desc?.let { " · $it" } ?: ""),
                        color = EmberText,
                        fontSize = 14.sp,
                    )
                }
            Text("Herbs: ${e.state.herbs}", color = EmberText, fontSize = 14.sp)
            if (e.state.herbs > 0 && e.active.any { it.sick || it.health < 3 }) {
                EmberButton("Use herbs on whoever needs them", onClick = vm::useHerbs)
            }
            Text("Spells ✦", color = EmberGold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            e.state.spells.forEach { id ->
                val sp = e.story.spell(id)
                Text((sp?.name ?: id) + (sp?.desc?.let { " · $it" } ?: ""), color = EmberText, fontSize = 14.sp)
            }
            Text("Magic backfire chance right now: ${e.backfireChance()}%", color = EmberDim, fontSize = 13.sp)
            Text("Pace: ${e.state.pace} · Food: ${e.state.rations}", color = EmberDim, fontSize = 13.sp)
            Text("Journal", color = EmberGold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            e.state.journal
                .reversed()
                .forEach { Text(it, color = EmberText, fontSize = 13.sp) }
        }
    }
}
