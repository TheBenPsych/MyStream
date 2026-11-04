@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.github.damontecres.wholphin.ui.games.ember

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.theme.BenflixTitleFont
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus

/**
 * Benflix "Ember Road": an old-school pixel adventure. Carry a dragon egg across a wild magical
 * land to Ember Peak, half trail journey (food, weather, a cart, a party) and half text adventure
 * (look, talk, take, use, cast, or type what you want to do). Choices change each run.
 */
@Composable
fun EmberRoadPage(
    modifier: Modifier = Modifier,
    viewModel: EmberViewModel = hiltViewModel(),
) {
    val isTv = rememberIsTv()
    // Phones can join from the moment a TV opens the game
    LaunchedEffect(isTv) { if (isTv) viewModel.startHost() }
    // Music stops when the app goes to the background
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs =
            LifecycleEventObserver { _, e ->
                if (e == Lifecycle.Event.ON_STOP) viewModel.musicPaused = true
                if (e == Lifecycle.Event.ON_START) viewModel.musicPaused = false
            }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    BackHandler(
        enabled =
            viewModel.screen != EmberScreen.TITLE && viewModel.screen != EmberScreen.PLAY && viewModel.screen != EmberScreen.LOADING,
    ) {
        if (!viewModel.back()) viewModel.leaveGame()
    }
    Box(
        modifier
            .fillMaxSize()
            .background(EmberBg),
    ) {
        when (viewModel.screen) {
            EmberScreen.LOADING -> {
                Text("Loading Ember Road…", color = EmberText, fontSize = 20.sp, modifier = Modifier.align(Alignment.Center))
            }

            EmberScreen.FAILED -> {
                Text(
                    "Ember Road couldn't start. Try updating Benflix.",
                    color = EmberText,
                    fontSize = 20.sp,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            EmberScreen.TITLE -> {
                TitleScreen(viewModel, isTv)
            }

            EmberScreen.LOBBY -> {
                LobbyScreen(viewModel)
            }

            EmberScreen.SETUP -> {
                SetupScreen(viewModel, isTv)
            }

            EmberScreen.PLAY -> {
                EmberPlay(viewModel, isTv)
            }

            EmberScreen.GALLERY -> {
                GalleryScreen(viewModel, isTv)
            }

            EmberScreen.BOOK -> {
                BookScreen(viewModel, isTv)
            }

            EmberScreen.HOWTO -> {
                HowToScreen(viewModel, isTv)
            }

            EmberScreen.PHONE -> {
                EmberPhoneScreen(viewModel)
            }
        }
    }
}

/** A page with a title that scrolls on phones and fits on TVs */
@Composable
private fun EmberPage(
    title: String,
    isTv: Boolean,
    scroll: Boolean = true,
    content: @Composable () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier =
            Modifier
                .let { if (scroll) it.fillMaxSize().verticalScroll(rememberScrollState()) else it.fillMaxWidth() }
                .padding(horizontal = if (isTv) 48.dp else 16.dp, vertical = if (isTv) 28.dp else 16.dp),
    ) {
        Text(title, fontFamily = BenflixTitleFont, fontSize = if (isTv) 34.sp else 28.sp, color = EmberGold, textAlign = TextAlign.Center)
        content()
    }
}

// -----------------------------------------------------------------------------------------------
// Title

@Composable
private fun TitleScreen(
    vm: EmberViewModel,
    isTv: Boolean,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.tryRequestFocus() }
    val s = vm.story ?: return
    val saved = vm.saved
    val buttons: @Composable (Modifier) -> Unit = { mod ->
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = mod) {
            if (saved != null) {
                val land =
                    s.lands
                        .firstOrNull { it.id == saved.route.lastOrNull() }
                        ?.name
                        .orEmpty()
                EmberButton(
                    "Continue the journey",
                    onClick = vm::continueRun,
                    big = true,
                    sub = "Day ${saved.day} in $land",
                    focusRequester = first,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            EmberButton(
                if (saved != null) "New journey (starts over)" else "New journey",
                onClick = { vm.newJourney(isTv) },
                big = saved == null,
                focusRequester = if (saved == null) first else null,
                modifier = Modifier.fillMaxWidth(),
            )
            if (!isTv) {
                EmberButton("Join the TV's game", sub = "Vote, type and play along from this phone", onClick = {
                    vm.goTo(EmberScreen.PHONE)
                }, modifier = Modifier.fillMaxWidth())
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                EmberButton(
                    "Endings ${vm.meta.endings.size}/${s.endings.size}",
                    onClick = { vm.goTo(EmberScreen.GALLERY) },
                    modifier = Modifier.weight(1f),
                )
                EmberButton(
                    "Creatures ${vm.meta.book.size}/${s.creatures.size}",
                    onClick = { vm.goTo(EmberScreen.BOOK) },
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                EmberButton("How to play", onClick = { vm.goTo(EmberScreen.HOWTO) }, modifier = Modifier.weight(1f))
                EmberButton(if (vm.music) "Music: on" else "Music: off", onClick = vm::toggleMusic, modifier = Modifier.weight(1f))
                EmberButton(if (vm.crt) "Old TV: on" else "Old TV: off", onClick = vm::toggleCrt, modifier = Modifier.weight(1f))
            }
            if (vm.meta.best > 0) Text("Your best: ${vm.meta.best} points · ${vm.meta.runs} journeys", color = EmberDim, fontSize = 14.sp)
            if (isTv) {
                // A quiet hint, not a sales pitch
                Text(
                    "Psst… Ember Road works really well on a phone too. Phones can join this game from Games on Benflix.",
                    color = EmberDim.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                )
            }
        }
    }
    if (isTv) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 28.dp),
        ) {
            Column(Modifier.weight(1.3f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TitleArt(vm)
            }
            buttons(Modifier.weight(1f))
        }
    } else {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
        ) {
            TitleArt(vm)
            buttons(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun TitleArt(vm: EmberViewModel) {
    Box {
        PixelScene("title", SceneOpts(crt = vm.crt, cart = true, party = 4), Modifier.fillMaxWidth().border(2.dp, Color(0xFF3A2A30)))
        Column(Modifier.align(Alignment.TopCenter).padding(top = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("EMBER ROAD", fontFamily = BenflixTitleFont, fontSize = 40.sp, color = EmberGold, fontWeight = FontWeight.Bold)
            Text("Carry the dragon egg to Ember Peak", color = EmberText, fontSize = 15.sp)
        }
    }
}

// -----------------------------------------------------------------------------------------------
// Phones joining (TV only)

@Composable
private fun LobbyScreen(vm: EmberViewModel) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.tryRequestFocus() }
    EmberPage("Playing on the couch?", isTv = true) {
        Text(
            "Everyone can join from their phone: open Benflix on the phone, go to Games, pick Ember Road and then " +
                "\"Join the TV's game\". Phones vote on choices, type commands and play the mini-games. " +
                "Phones can join later too.",
            color = EmberText,
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 800.dp),
        )
        Text(
            if (vm.joined.isEmpty()) "Waiting for phones…" else "Joined: " + vm.joined.joinToString { it.name },
            color = if (vm.joined.isEmpty()) EmberDim else EmberGood,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EmberButton(
                if (vm.joined.isEmpty()) {
                    "Skip, just the TV"
                } else {
                    "Go on with ${vm.joined.size} phone" +
                        (if (vm.joined.size == 1) "" else "s")
                },
                onClick = { vm.goTo(EmberScreen.SETUP) },
                big = true,
                focusRequester = first,
            )
            if (vm.joined.isEmpty()) EmberButton("Back", onClick = { vm.goTo(EmberScreen.TITLE) })
        }
    }
}

// -----------------------------------------------------------------------------------------------
// Setting up a journey

private val roleInfo =
    linkedMapOf(
        "healer" to ("Healer" to "Starts with extra herbs. Fevers are rarer."),
        "merchant" to ("Merchant" to "Starts with more gold. Better prices at markets."),
        "ranger" to ("Ranger" to "Starts with a bow. Good at hunting and tracking."),
        "bard" to ("Bard" to "Starts with a lute. Great at talking your way out."),
        "mage" to ("Mage" to "Knows two more spells. Magic backfires less."),
        "smith" to ("Smith" to "Starts with a hammer. Fixes the cart and opens things."),
    )

private val folkName = mapOf("human" to "Human", "elf" to "Elf", "dwarf" to "Dwarf", "fox" to "Fox folk")

@Composable
private fun SetupScreen(
    vm: EmberViewModel,
    isTv: Boolean,
) {
    var step by remember { mutableIntStateOf(0) }
    val s = vm.story ?: return
    val first = remember(step) { FocusRequester() }
    LaunchedEffect(step) { first.tryRequestFocus() }
    BackHandler(enabled = step > 0) { step-- }
    val titles = listOf("Who's coming?", "Your party's skill", "A pet for the road", "The journey")
    EmberPage(titles[step], isTv) {
        Text("Step ${step + 1} of 4", color = EmberDim, fontSize = 14.sp)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.widthIn(max = 900.dp).fillMaxWidth()) {
            when (step) {
                0 -> {
                    Text(
                        "Your family is the party. Change any name, pick their folk, or roll someone new. The first person leads.",
                        color = EmberText,
                        fontSize = 16.sp,
                    )
                    vm.draft.forEachIndexed { i, d ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .background(EmberPanel, RoundedCornerShape(8.dp))
                                    .padding(10.dp),
                        ) {
                            PixelPortrait(Member(d.name, d.folk, d.traits, face = d.face), size = if (isTv) 56.dp else 48.dp)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                NameField(d.name, isTv) { vm.renameMember(i, it) }
                                Text(
                                    (if (i == 0) "Leader · " else "") + d.traits.mapNotNull { t -> s.trait(t)?.name }.joinToString(", "),
                                    color = EmberDim,
                                    fontSize = 13.sp,
                                )
                            }
                            if (isTv) {
                                EmberButton(
                                    folkName[d.folk] ?: d.folk,
                                    onClick = { vm.cycleFolk(i) },
                                    focusRequester =
                                        if (i ==
                                            0
                                        ) {
                                            first
                                        } else {
                                            null
                                        },
                                )
                                EmberButton("Roll", onClick = { vm.rerollMember(i) })
                                if (vm.draft.size > 1) EmberButton("✕", onClick = { vm.removeMember(i) })
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    EmberButton(folkName[d.folk] ?: d.folk, onClick = { vm.cycleFolk(i) })
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        EmberButton("Roll", onClick = { vm.rerollMember(i) })
                                        if (vm.draft.size > 1) EmberButton("✕", onClick = { vm.removeMember(i) })
                                    }
                                }
                            }
                        }
                    }
                    if (vm.draft.size < 4) EmberButton("Add someone", onClick = vm::addMember)
                }

                1 -> {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        roleInfo.entries.forEachIndexed { i, (id, info) ->
                            val locked = vm.roleLocked(id)
                            EmberButton(
                                (if (vm.draftRole == id) "● " else "") + info.first,
                                sub =
                                    if (locked) {
                                        (
                                            if (id ==
                                                "mage"
                                            ) {
                                                "Finish 1 journey to unlock"
                                            } else {
                                                "Finish 2 journeys to unlock"
                                            }
                                        )
                                    } else {
                                        info.second
                                    },
                                enabled = !locked,
                                onClick = { vm.draftRole = id },
                                focusRequester = if (i == 0) first else null,
                                modifier = Modifier.width(if (isTv) 270.dp else 300.dp),
                            )
                        }
                    }
                }

                2 -> {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        s.names.pets.forEachIndexed { i, p ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PixelPortrait(null, petId = p.id, size = 44.dp)
                                Spacer(Modifier.width(6.dp))
                                EmberButton(
                                    (if (vm.draftPet == p.id) "● " else "") + p.name,
                                    sub = p.desc,
                                    onClick = { vm.draftPet = p.id },
                                    focusRequester = if (i == 0) first else null,
                                    modifier = Modifier.width(if (isTv) 300.dp else 250.dp),
                                )
                            }
                        }
                        EmberButton(
                            (
                                if (vm.draftPet ==
                                    null
                                ) {
                                    "● "
                                } else {
                                    ""
                                }
                            ) + "No pet",
                            sub = "Just the family",
                            onClick = { vm.draftPet = null },
                        )
                    }
                }

                else -> {
                    Text("How long?", color = EmberGold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("short" to "About 15 min", "medium" to "About 30 min", "long" to "An hour or more").forEachIndexed {
                            i,
                            (id, sub),
                            ->
                            EmberButton(
                                (if (vm.draftLength == id) "● " else "") + id.replaceFirstChar { it.uppercase() },
                                sub = sub,
                                onClick = { vm.draftLength = id },
                                focusRequester = if (i == 0) first else null,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    if (!vm.kids) {
                        Text("How hard?", color = EmberGold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("easy" to "Half points", "normal" to "The real thing", "hard" to "Double points").forEach { (id, sub) ->
                                val locked = vm.difficultyLocked(id)
                                EmberButton(
                                    (if (vm.draftDifficulty == id) "● " else "") + id.replaceFirstChar { it.uppercase() },
                                    sub = if (locked) "Finish 1 journey to unlock" else sub,
                                    enabled = !locked,
                                    onClick = { vm.draftDifficulty = id },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                    Text("Your cart", color = EmberGold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("wagon" to "Trusty wagon", "snail" to "Giant snail", "royal" to "Royal coach").forEach { (id, label) ->
                            val locked = vm.cartLocked(id)
                            EmberButton(
                                (if (vm.draftCart == id) "● " else "") + label,
                                sub = if (locked) (if (id == "snail") "Finish 1 journey" else "Finish 3 journeys") else null,
                                enabled = !locked,
                                onClick = { vm.draftCart = id },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    PixelScene(
                        "fields",
                        SceneOpts(cart = true, party = vm.draft.size, pet = vm.draftPet != null, cartLook = vm.draftCart, crt = vm.crt),
                        Modifier.fillMaxWidth(if (isTv) 0.5f else 1f),
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EmberButton(if (step == 0) "Cancel" else "Back", onClick = { if (step == 0) vm.goTo(EmberScreen.TITLE) else step-- })
            if (step < 3) {
                EmberButton("Next", onClick = { step++ }, big = true, tint = Color(0xFF5A2A1E))
            } else {
                EmberButton("Set out!", onClick = vm::startJourney, big = true, tint = Color(0xFF5A2A1E))
            }
        }
    }
}

@Composable
fun NameField(
    value: String,
    isTv: Boolean,
    onChange: (String) -> Unit,
) {
    BasicTextField(
        value = value,
        onValueChange = { onChange(it.replace("\n", "").take(14)) },
        singleLine = true,
        textStyle = TextStyle(color = EmberText, fontSize = if (isTv) 18.sp else 17.sp, fontWeight = FontWeight.Bold),
        cursorBrush = SolidColor(EmberGold),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {}),
        modifier =
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF15101A), RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

// -----------------------------------------------------------------------------------------------
// Endings, creatures, how to play

@Composable
private fun GalleryScreen(
    vm: EmberViewModel,
    isTv: Boolean,
) {
    val s = vm.story ?: return
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.tryRequestFocus() }
    EmberPage("Endings found: ${vm.meta.endings.size} of ${s.endings.size}", isTv) {
        EmberButton("Back", onClick = { vm.goTo(EmberScreen.TITLE) }, focusRequester = first)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            s.endings.sortedBy { it.order }.forEach { e ->
                val found = e.id in vm.meta.endings
                Column(
                    Modifier
                        .walkable(isTv)
                        .width(if (isTv) 250.dp else 165.dp)
                        .background(EmberPanel, RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (found) {
                        PixelScene(e.scene, SceneOpts(), Modifier.fillMaxWidth(), animate = false)
                    } else {
                        Box(
                            Modifier.fillMaxWidth().height(if (isTv) 130.dp else 88.dp).background(Color(0xFF0C080E)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("?", color = EmberDim, fontSize = 32.sp, fontFamily = BenflixTitleFont)
                        }
                    }
                    Text(
                        if (found) e.title else "???",
                        color = if (found) EmberGold else EmberDim,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun BookScreen(
    vm: EmberViewModel,
    isTv: Boolean,
) {
    val s = vm.story ?: return
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.tryRequestFocus() }
    val found = vm.meta.book + (vm.engine?.state?.book ?: emptySet())
    EmberPage("Creature book: ${found.size} of ${s.creatures.size}", isTv) {
        EmberButton("Back", onClick = { vm.goTo(EmberScreen.TITLE) }, focusRequester = first)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            s.creatures.forEach { c ->
                val met = c.id in found
                Column(
                    Modifier
                        .walkable(isTv)
                        .width(if (isTv) 200.dp else 160.dp)
                        .background(EmberPanel, RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    PixelCreature(c.art, met, Modifier.fillMaxWidth())
                    Text(
                        if (met) c.name else "???",
                        color = if (met) EmberGold else EmberDim,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    if (met) Text(c.note, color = EmberText, fontSize = 13.sp)
                }
            }
        }
    }
}

private val howToLines =
    listOf(
        "The goal" to
            "Carry the dragon egg from Hearthvale to Ember Peak. Keep it warm (camp by a fire), keep your family fed, and get there before it hatches. Or don't. There are 12 endings.",
        "On the road" to
            "Pick Keep going to travel a day. Things happen: pick what to do. Choices with a % are a dice roll. Make camp to rest, heal and warm the egg.",
        "Pace and food" to
            "A hard pace is faster but tiring. Eating little saves food but people get sick. Watch the food number!",
        "Stops" to
            "At towns and odd places, Look at things, Talk to people, Take, Use items and Cast spells. You can also type what you want to do, like \"look under the bridge\" or \"use rope on well\". Some secrets only work by typing.",
        "Magic" to
            "Spells are marked ✦. Magic is powerful but risky: the more you cast, the wilder it gets and the more it backfires. Camping calms it down.",
        "Roads" to "At each fork, pick a road: safe but long, short but dangerous, or strange. Every journey is different.",
        "Your party" to "Nobody dies on Ember Road, but people can get fed up and go home. Keep spirits up!",
        "Phones" to
            "On a TV, phones can join from Games on Benflix: they vote on choices (most votes win, ties go to the TV), type commands and play the mini-games.",
        "Trail markers" to "At the end, leave a marker where your journey ended. The family will find it on their own journeys.",
    )

/** TV: a card the remote can land on (so the page scrolls down to it), with a gold edge while it's picked */
@Composable
private fun Modifier.walkable(isTv: Boolean): Modifier {
    if (!isTv) return this
    var focused by remember { mutableStateOf(false) }
    return this
        .onFocusChanged { focused = it.isFocused }
        .focusable()
        .border(2.dp, if (focused) EmberGold else Color.Transparent, RoundedCornerShape(8.dp))
}

@Composable
private fun HowToScreen(
    vm: EmberViewModel,
    isTv: Boolean,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.tryRequestFocus() }
    // TV: the remote can't scroll plain words, so the page is laid out in two columns and shrinks to fit the screen
    val page: @Composable () -> Unit = {
        EmberPage("How to play", isTv, scroll = !isTv) {
            @Composable
            fun Part(
                part: List<Pair<String, String>>,
                modifier: Modifier,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
                    part.forEach { (h, b) ->
                        Text(h, color = EmberGold, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(b, color = EmberText, fontSize = 16.sp)
                    }
                }
            }
            if (isTv) {
                Row(horizontalArrangement = Arrangement.spacedBy(36.dp), modifier = Modifier.fillMaxWidth()) {
                    Part(howToLines.take(5), Modifier.weight(1f))
                    Part(howToLines.drop(5), Modifier.weight(1f))
                }
            } else {
                Part(howToLines, Modifier.widthIn(max = 900.dp))
            }
            EmberButton("Back", onClick = { vm.goTo(EmberScreen.TITLE) }, focusRequester = first)
        }
    }
    if (isTv) {
        com.github.damontecres.wholphin.ui.games
            .FitScreen(isTv = true) { page() }
    } else {
        page()
    }
}
