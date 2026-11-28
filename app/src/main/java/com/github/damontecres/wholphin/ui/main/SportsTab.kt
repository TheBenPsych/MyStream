package com.github.damontecres.wholphin.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.services.BenflixTeams
import com.github.damontecres.wholphin.services.ChannelSchedule
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.SportsLeague
import com.github.damontecres.wholphin.services.SportsTeam
import com.github.damontecres.wholphin.services.TeamGame
import com.github.damontecres.wholphin.services.group
import com.github.damontecres.wholphin.ui.components.BenflixNotices
import com.github.damontecres.wholphin.ui.door.MatchHelperEntryPoint
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.nav.focusRing
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixShapes
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.theme.Glowfin
import com.github.damontecres.wholphin.ui.theme.GlowfinDisplayFont
import com.github.damontecres.wholphin.ui.theme.glowFocus
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The gold of the "Your team" tag (Glowfin warn/gold) */
internal val TEAM_GOLD = Color(0xFFF2B33D)

@Composable
private fun teamsEntry(): MatchHelperEntryPoint {
    val context = LocalContext.current
    return remember(context) { EntryPointAccessors.fromApplication(context.applicationContext, MatchHelperEntryPoint::class.java) }
}

/** "#E31837" -> the colour (grey when it can't be read) */
private fun teamColour(text: String): Color =
    try {
        Color(android.graphics.Color.parseColor(text))
    } catch (_: Exception) {
        Color(0xFF2B2B2B)
    }

private val dayFormat = DateTimeFormatter.ofPattern("EEEE h:mm a", Locale.US)

/** "on now", "Today 8:15 PM", "Thursday 8:15 PM" */
private fun whenText(
    game: TeamGame,
    now: Long,
): String {
    if (game.liveAt(now)) return "on now"
    val zone = ZoneId.systemDefault()
    val start = Instant.ofEpochMilli(game.startMs).atZone(zone)
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val text = dayFormat.format(start)
    return if (start.toLocalDate() == today) "Today " + text.substringAfter(' ') else text
}

/**
 * Live TV's Sports tab (Idea Box 28, Ben's pick 2026-11-24): Your teams first (a card to pick them the first
 * time, or your teams with Change), then Your team games from the guide for the next 7 days, then what's on
 * the sports channels now. Picking opens one page with a pill for each league.
 */
@Composable
internal fun SportsTab(
    channels: List<ChannelSchedule>,
    onWatch: (ChannelSchedule) -> Unit,
    navigationManager: NavigationManager,
    modifier: Modifier = Modifier,
) {
    val entry = teamsEntry()
    val user = entry.servers().currentUser?.id
    val mine by BenflixTeams.mine.collectAsState()
    val notNow by BenflixTeams.notNow.collectAsState()
    var leagues by remember { mutableStateOf<List<SportsLeague>>(emptyList()) }
    var offline by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var games by remember { mutableStateOf<List<TeamGame>>(emptyList()) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(user) {
        try {
            leagues = BenflixTeams.load(entry.helper(), user)
            offline = false
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Timber.i("Teams not available: %s", ex.message)
            offline = true
        }
    }
    LaunchedEffect(mine) {
        while (mine.isNotEmpty()) {
            games =
                try {
                    BenflixTeams.games(entry.helper())
                } catch (ex: CancellationException) {
                    throw ex
                } catch (_: Exception) {
                    games
                }
            delay(120_000L)
        }
        games = emptyList()
    }
    if (picking) {
        BackHandler { picking = false }
        TeamPicker(
            leagues = leagues,
            picked = mine,
            onDone = { list ->
                picking = false
                scope.launch {
                    try {
                        BenflixTeams.save(entry.helper(), list)
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (_: Exception) {
                        BenflixNotices.pill("Couldn't save your teams. Is Tower on?")
                    }
                }
            },
            modifier = modifier,
        )
        return
    }
    val isTv = rememberIsTv()
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (isTv) {
            delay(150)
            first.tryRequestFocus()
        }
    }
    val now = System.currentTimeMillis()
    val play = { game: TeamGame ->
        val schedule = channels.firstOrNull { it.live.id == game.channelId } ?: channels.firstOrNull { it.title.equals(game.channel, true) }
        if (schedule != null) {
            onWatch(schedule)
        } else {
            game.channelId?.let { navigationManager.navigateTo(Destination.Playback(it, 0L)) }
        }
    }
    val sports = remember(channels) { channels.filter { it.group() == "Sports" } }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 12.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(if (isTv) 22.dp else 18.dp),
    ) {
        item(key = "teams") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                RowName("Your teams")
                when {
                    offline -> {
                        Text(
                            "Couldn't reach Tower for your teams. Try again in a minute.",
                            color = BenflixColors.Muted,
                            fontSize = BenflixType.body,
                        )
                    }

                    mine.isEmpty() && !notNow -> {
                        FollowCard(
                            first = first,
                            onPick = { picking = true },
                            onNotNow = { scope.launch { runCatching { BenflixTeams.notNow(entry.helper()) } } },
                        )
                    }

                    else -> {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(end = 32.dp)) {
                            items(mine, key = { it.id }) { team ->
                                TeamBadge(
                                    team = team,
                                    picked = false,
                                    size = 64.dp,
                                    onClick = { picking = true },
                                    modifier = if (team == mine.first()) Modifier.focusRequester(first) else Modifier,
                                )
                            }
                            item(key = "change") {
                                AddBadge(
                                    label = if (mine.isEmpty()) "Pick teams" else "Change",
                                    onClick = { picking = true },
                                    modifier = if (mine.isEmpty()) Modifier.focusRequester(first) else Modifier,
                                )
                            }
                        }
                    }
                }
            }
        }
        if (mine.isNotEmpty()) {
            item(key = "games") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    RowName("Your team games")
                    if (games.isEmpty()) {
                        Text(
                            "None of your teams are in the guide this week. Glowfin keeps looking, and tells you when one is on.",
                            color = BenflixColors.Muted,
                            fontSize = BenflixType.body,
                        )
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(end = 32.dp)) {
                            items(games, key = { it.programId + it.startMs }) { game ->
                                GameCard(
                                    title = game.title,
                                    line = "${game.channel} · ${whenText(game, now)}",
                                    live = game.liveAt(now),
                                    yourTeam = true,
                                    onClick = { play(game) },
                                )
                            }
                        }
                    }
                }
            }
        }
        if (sports.isNotEmpty()) {
            item(key = "now") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    RowName("On now")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(end = 32.dp)) {
                        items(sports, key = { it.key }) { s ->
                            val on = s.programAt(now)
                            val name = on?.name() ?: "Live"
                            GameCard(
                                title = name,
                                line = s.title,
                                live = true,
                                yourTeam = BenflixTeams.isMine(name),
                                onClick = { onWatch(s) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The row names on the Sports tab: the same name style as every other row */
@Composable
private fun RowName(text: String) {
    Text(
        text = text,
        color = BenflixColors.Text,
        fontSize = BenflixType.lead,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
    )
}

/** The first-time card: Follow your teams, with Pick teams and Not now */
@Composable
private fun FollowCard(
    first: FocusRequester,
    onPick: () -> Unit,
    onNotNow: () -> Unit,
) {
    val shape = BenflixShapes.Panel
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        modifier =
            Modifier
                .widthIn(max = 620.dp)
                .background(Glowfin.Raise.copy(alpha = .92f), shape)
                .border(1.dp, Glowfin.Line, shape)
                .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Follow your teams", color = Glowfin.Text, fontSize = BenflixType.lead, fontWeight = FontWeight.Bold)
            Text(
                "Pick them once. Their games come first here, get a gold Your team tag, and Glowfin tells you when one starts.",
                color = BenflixColors.Soft,
                fontSize = BenflixType.body,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
                LiveChip("Pick teams", selected = true, onClick = onPick, modifier = Modifier.focusRequester(first))
                LiveChip("Not now", selected = false, onClick = onNotNow)
            }
        }
    }
}

/** A round team badge in the team's colour, with its short name inside and its name under it */
@Composable
private fun TeamBadge(
    team: SportsTeam,
    picked: Boolean,
    size: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(size + 30.dp)) {
        Box {
            Surface(
                onClick = onClick,
                shape = ClickableSurfaceDefaults.shape(CircleShape),
                colors =
                    ClickableSurfaceDefaults.colors(
                        containerColor = teamColour(team.colour),
                        focusedContainerColor = teamColour(team.colour),
                        contentColor = Color.White,
                        focusedContentColor = Color.White,
                    ),
                border = focusRing(CircleShape),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                modifier = modifier.glowFocus(CircleShape, spread = 8.dp).size(size),
            ) {
                Box(Modifier.fillMaxSize().border(2.dp, Color.White.copy(alpha = .15f), CircleShape), contentAlignment = Alignment.Center) {
                    Text(
                        team.short,
                        fontFamily = GlowfinDisplayFont,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = BenflixType.small,
                        maxLines = 1,
                    )
                }
            }
            if (picked) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 4.dp, y = (-4).dp)
                            .size(24.dp)
                            .background(Glowfin.Aqua, CircleShape)
                            .border(2.dp, Glowfin.Ink, CircleShape),
                ) {
                    Text("✓", color = Glowfin.Ink, fontWeight = FontWeight.Black, fontSize = BenflixType.tag)
                }
            }
        }
        Text(
            team.nick,
            color = BenflixColors.Text,
            fontSize = BenflixType.tag,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** The dashed "+" badge at the end of Your teams (Change, or Pick teams after Not now) */
@Composable
private fun AddBadge(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(94.dp)) {
        Surface(
            onClick = onClick,
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            colors =
                ClickableSurfaceDefaults.colors(
                    containerColor = Color.White.copy(alpha = .06f),
                    focusedContainerColor = Color.White,
                    contentColor = BenflixColors.Muted,
                    focusedContentColor = Glowfin.Ink,
                ),
            border = focusRing(CircleShape),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            modifier = modifier.glowFocus(CircleShape, spread = 8.dp).size(64.dp),
        ) {
            Box(Modifier.fillMaxSize().border(1.5.dp, Color.White.copy(alpha = .35f), CircleShape), contentAlignment = Alignment.Center) {
                Text("+", fontSize = BenflixType.pageTitle, fontWeight = FontWeight.Light)
            }
        }
        Text(
            label,
            color = BenflixColors.Muted,
            fontSize = BenflixType.tag,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** A game card: what's on, the channel and when, LIVE when it's on now, and the gold Your team tag */
@Composable
private fun GameCard(
    title: String,
    line: String,
    live: Boolean,
    yourTeam: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Glowfin.Deep,
                focusedContainerColor = Glowfin.Raise,
                contentColor = Glowfin.Text,
                focusedContentColor = Glowfin.Text,
            ),
        border = focusRing(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = Modifier.glowFocus(shape, spread = 8.dp).width(232.dp).height(120.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            modifier =
                Modifier
                    .fillMaxSize()
                    .border(1.dp, if (yourTeam) TEAM_GOLD.copy(alpha = .6f) else Glowfin.Line, shape)
                    .padding(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (live) {
                    Text(
                        "LIVE",
                        color = Color.White,
                        fontSize = BenflixType.tag,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.background(Color(0xFFF06A6A), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp),
                    )
                }
                if (yourTeam) YourTeamTag()
                Spacer(Modifier.weight(1f))
            }
            Column {
                Text(title, fontSize = BenflixType.body, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(line, color = BenflixColors.Muted, fontSize = BenflixType.tag, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** The small gold "Your team" tag */
@Composable
internal fun YourTeamTag(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Text(
        "Your team",
        color = Color(0xFFFCE3B0),
        fontSize = BenflixType.tag,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier =
            modifier
                .background(TEAM_GOLD.copy(alpha = .18f), shape)
                .border(1.dp, TEAM_GOLD, shape)
                .padding(horizontal = 8.dp),
    )
}

/** Picking teams: a pill for each league, the teams as round badges, and what you picked on the right */
@Composable
private fun TeamPicker(
    leagues: List<SportsLeague>,
    picked: List<SportsTeam>,
    onDone: (List<SportsTeam>) -> Unit,
    modifier: Modifier,
) {
    val chosen = remember { mutableStateListOf<SportsTeam>().apply { addAll(picked) } }
    var league by remember { mutableStateOf(leagues.firstOrNull()?.key) }
    val isTv = rememberIsTv()
    val firstPill = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (isTv) {
            delay(120)
            firstPill.tryRequestFocus()
        }
    }
    Column(modifier = modifier.fillMaxSize().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Your teams",
            color = Glowfin.Text,
            fontSize = BenflixType.pageTitle,
            fontWeight = FontWeight.Bold,
            fontFamily = GlowfinDisplayFont,
        )
        Text("Pick as many as you like. Each person picks their own.", color = BenflixColors.Soft, fontSize = BenflixType.body)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 24.dp)) {
            items(leagues, key = { it.key }) { lg ->
                LiveChip(
                    lg.name,
                    selected = lg.key == league,
                    onClick = { league = lg.key },
                    modifier = if (lg == leagues.first()) Modifier.focusRequester(firstPill) else Modifier,
                )
            }
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            val teams = leagues.firstOrNull { it.key == league }?.teams.orEmpty()
            LazyVerticalGrid(
                columns = GridCells.Adaptive(if (isTv) 96.dp else 84.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp, start = 4.dp),
                modifier = Modifier.weight(1f).fillMaxSize(),
            ) {
                items(teams, key = { it.id }) { team ->
                    val on = chosen.any { it.id == team.id }
                    TeamBadge(
                        team = team,
                        picked = on,
                        size = if (isTv) 66.dp else 56.dp,
                        onClick = { if (on) chosen.removeAll { it.id == team.id } else chosen.add(team) },
                    )
                }
            }
            // What you picked, and Done
            val shape = RoundedCornerShape(14.dp)
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier =
                    Modifier
                        .padding(top = 8.dp, end = 8.dp)
                        .width(if (isTv) 190.dp else 140.dp)
                        .background(Glowfin.Deep.copy(alpha = .92f), shape)
                        .border(1.dp, Glowfin.Line, shape)
                        .padding(14.dp),
            ) {
                Text("PICKED", color = BenflixColors.Muted, fontSize = BenflixType.tag, fontWeight = FontWeight.SemiBold)
                Text(
                    if (chosen.isEmpty()) "No teams yet" else chosen.joinToString(", ") { it.nick },
                    color = Glowfin.Text,
                    fontSize = BenflixType.small,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
                LiveChip("Done", selected = true, onClick = { onDone(chosen.toList()) })
            }
        }
    }
}

/**
 * "Your team is on": a corner card when one of your teams' games starts (checked every 5 minutes while
 * Glowfin is open). OK on the card turns it on. Each game gets one card per device. Never for Kids.
 */
@Composable
fun TeamsLayer(navigationManager: NavigationManager) {
    val entry = teamsEntry()
    val kids by KidsMode.active.collectAsState()
    val user = entry.servers().currentUser?.id
    val told = remember { mutableSetOf<String>() }
    LaunchedEffect(user, kids) {
        if (kids || user == null) return@LaunchedEffect
        // Give the app a moment to start before asking Tower
        delay(30_000L)
        runCatching { BenflixTeams.load(entry.helper(), user) }
        while (true) {
            if (BenflixTeams.mine.value.isNotEmpty()) {
                val games = runCatching { BenflixTeams.games(entry.helper()) }.getOrDefault(emptyList())
                val now = System.currentTimeMillis()
                games
                    .filter { it.liveAt(now) && now - it.startMs < 20 * 60_000L && (it.programId + it.startMs) !in told }
                    .take(2)
                    .forEach { game ->
                        told.add(game.programId + game.startMs)
                        BenflixNotices.card(
                            BenflixNotices.Card(
                                id = "team-${game.programId}",
                                title = "Your team is on",
                                line = "${game.title} · ${game.channel}",
                                onOpen = { game.channelId?.let { navigationManager.navigateTo(Destination.Playback(it, 0L)) } },
                            ),
                        )
                    }
            }
            delay(5 * 60_000L)
        }
    }
}
