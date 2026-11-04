package com.github.damontecres.wholphin.ui.games.ember

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.services.BenflixHighScores
import com.github.damontecres.wholphin.services.DisplayPreferencesService
import com.github.damontecres.wholphin.services.FavoriteWatchManager
import com.github.damontecres.wholphin.services.GuestMode
import com.github.damontecres.wholphin.services.ImageUrlService
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import kotlin.random.Random

enum class EmberScreen { LOADING, TITLE, LOBBY, SETUP, PLAY, GALLERY, BOOK, HOWTO, PHONE, FAILED }

/** What this profile has found and done across all runs */
@Serializable
data class EmberMeta(
    val endings: MutableSet<String> = mutableSetOf(),
    val book: MutableSet<String> = mutableSetOf(),
    var runs: Int = 0,
    var best: Int = 0,
    val marks: MutableList<Mark> = mutableListOf(),
)

/** A trail marker someone left where their journey ended */
@Serializable
data class Mark(
    val land: String,
    val spot: Int,
    val line: String,
    val ending: String = "",
    val code: String = "",
)

/** A movie picked at the campfire */
data class CampMovie(
    val id: UUID,
    val name: String,
    val year: Int?,
    val poster: String?,
    val added: Boolean = false,
)

/** The party being set up before a journey */
data class DraftMember(
    val name: String,
    val folk: String,
    val traits: List<String>,
    val face: Int,
    /** This person's skill (healer, merchant, ranger, bard, mage, smith) */
    val role: String = "healer",
)

/** The skills, in the order the setup button cycles through them */
val EmberSkills =
    linkedMapOf(
        "healer" to ("Healer" to "Brings extra herbs. Fevers are rarer."),
        "ranger" to ("Ranger" to "Brings a bow. Finds the way faster and hunts well."),
        "bard" to ("Bard" to "Brings a lute. Great at talking the party out of trouble."),
        "merchant" to ("Merchant" to "Brings more gold. Better prices at markets."),
        "mage" to ("Mage" to "Knows two more spells. Magic backfires less."),
        "smith" to ("Smith" to "Brings a hammer. Fixes the cart and opens locked things."),
    )

/** One button on screen (also sent to the phones) */
data class Act(
    val id: String,
    val label: String,
    val hint: String = "",
)

@HiltViewModel
class EmberViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val displayPreferencesService: DisplayPreferencesService,
        private val imageUrlService: ImageUrlService,
        private val favoriteWatchManager: FavoriteWatchManager,
        val navigationManager: NavigationManager,
    ) : ViewModel() {
        private val prefs = context.getSharedPreferences("benflix_ember", Context.MODE_PRIVATE)
        private val gamePrefs = context.getSharedPreferences("benflix_games", Context.MODE_PRIVATE)

        var screen by mutableStateOf(EmberScreen.LOADING)
            private set

        /** Goes up whenever the game changes, so the screen redraws */
        var version by mutableIntStateOf(0)
            private set

        var story: Story? = null
            private set
        var engine: EmberEngine? = null
            private set

        /** A saved journey you can continue */
        var saved by mutableStateOf<RunState?>(null)
            private set

        var meta by mutableStateOf(EmberMeta())
            private set

        /** What just happened (travel, things done in a stop) */
        var log by mutableStateOf<Shown?>(null)
            private set

        var campMovie by mutableStateOf<CampMovie?>(null)
            private set

        var music by mutableStateOf(prefs.getBoolean("music", true))
            private set
        var crt by mutableStateOf(prefs.getBoolean("crt", false))
            private set

        val kids = KidsMode.isActive
        private val guest = GuestMode.isActive

        // ---- phones
        val joined = mutableStateListOf<Joined>()
        private val votes = mutableMapOf<String, String>()
        var tvVote by mutableStateOf<String?>(null)
            private set
        var voteNote by mutableStateOf<String?>(null)
            private set
        private val phoneReplies = mutableMapOf<String, String>()
        private var host: EmberLinkHost? = null

        /** Mini-game presses from phones (fish/herb "tap", rocks "left"/"right") */
        private val _minigameInput = MutableSharedFlow<String>(extraBufferCapacity = 8)
        val minigameInput: SharedFlow<String> = _minigameInput

        // ---- family trail markers
        var family by mutableStateOf<List<Mark>>(emptyList())
            private set
        var myCode by mutableStateOf("???")
            private set

        // ---- setup
        val draft = mutableStateListOf<DraftMember>()
        var draftPet by mutableStateOf<String?>("owl")
        var draftLength by mutableStateOf("medium")
        var draftDifficulty by mutableStateOf("normal")
        var draftCart by mutableStateOf("wagon")

        /** The end of a run: was it a new best, and has a marker been left */
        var newBest by mutableStateOf(false)
            private set
        var markLeft by mutableStateOf(false)
            private set

        val player = EmberMusic()
        private var serverSaveJob: Job? = null

        private val profile: String
            get() {
                val id = serverRepository.currentUser?.id?.toString() ?: "guest"
                return if (kids) {
                    "${id}_kids"
                } else if (guest) {
                    "${id}_guest"
                } else {
                    id
                }
            }
        private val serverSaveKey get() = if (kids) "benflix_ember_save_kids" else "benflix_ember_save"
        private val serverMetaKey get() = if (kids) "benflix_ember_me_kids" else "benflix_ember_me"

        init {
            player.enabled = music
            viewModelScope.launch {
                try {
                    val s =
                        withContext(WholphinDispatchers.IO) {
                            Story.parse(
                                context.assets
                                    .open("ember/story.json")
                                    .bufferedReader()
                                    .use { it.readText() },
                            )
                        }
                    story = s
                    loadLocal(s)
                    screen = EmberScreen.TITLE
                    loadServer(s)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.e(ex, "Ember Road couldn't load its story")
                    screen = EmberScreen.FAILED
                }
            }
        }

        // -------------------------------------------------------------------------------------
        // Saving and loading

        private fun loadLocal(s: Story) {
            prefs.getString("meta_$profile", null)?.let { m ->
                runCatching { EmberJson.decodeFromString(EmberMeta.serializer(), m) }.getOrNull()?.let { meta = it }
            }
            prefs.getString("save_$profile", null)?.let { j ->
                runCatching { EmberJson.decodeFromString(RunState.serializer(), j) }.getOrNull()?.let { if (validSave(s, it)) saved = it }
            }
        }

        private fun validSave(
            s: Story,
            r: RunState,
        ): Boolean = r.phase != Phase.OVER && r.route.all { id -> s.lands.any { it.id == id } }

        /** Continue on another device: the server's save wins if it's newer */
        private suspend fun loadServer(s: Story) {
            val user = serverRepository.currentUser ?: return
            if (guest) return
            try {
                val custom = withContext(WholphinDispatchers.IO) { displayPreferencesService.getDisplayPreferences(user.id).customPrefs }
                custom[BenflixHighScores.KEY_CODE]?.takeIf { it.isNotBlank() }?.let { myCode = it }
                    ?: run { myCode = BenflixHighScores.shortName(user.name) }
                custom[serverMetaKey]?.let { m ->
                    runCatching { EmberJson.decodeFromString(EmberMeta.serializer(), m) }.getOrNull()?.let { remote ->
                        val merged =
                            EmberMeta(
                                endings = (meta.endings + remote.endings).toMutableSet(),
                                book = (meta.book + remote.book).toMutableSet(),
                                runs = maxOf(meta.runs, remote.runs),
                                best = maxOf(meta.best, remote.best),
                                marks = (if (meta.marks.size >= remote.marks.size) meta.marks else remote.marks).toMutableList(),
                            )
                        meta = merged
                        saveMetaLocal()
                    }
                }
                val remoteSave = custom[serverSaveKey]
                if (remoteSave.isNullOrBlank()) {
                    // The run was finished (or never started) on another device
                    val local = saved
                    if (local != null && custom.containsKey(serverSaveKey) && engine == null) {
                        // Another device finished this journey after this one last saved
                        val stamp = custom["${serverSaveKey}_at"]?.toLongOrNull() ?: 0L
                        if (stamp > local.lastTouched) {
                            saved = null
                            prefs.edit { remove("save_$profile") }
                        }
                    }
                } else {
                    runCatching { EmberJson.decodeFromString(RunState.serializer(), remoteSave) }.getOrNull()?.let { r ->
                        if (validSave(s, r) && r.lastTouched > (saved?.lastTouched ?: 0L) && engine == null) saved = r
                    }
                }
                family =
                    custom[BenflixHighScores.KEY_EMBER_FAMILY]
                        .orEmpty()
                        .lines()
                        .mapNotNull { parseFamily(it) }
                        .filter { it.code != myCode }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Ember Road couldn't reach the server for saves")
            }
        }

        private fun parseFamily(line: String): Mark? {
            val code = line.substringBefore('>', "").ifBlank { return null }
            val p = line.substringAfter('>').split('~')
            if (p.size < 3) return null
            return Mark(land = p[0], spot = p[1].toIntOrNull() ?: 50, line = p[2], ending = p.getOrNull(3).orEmpty(), code = code)
        }

        private fun saveMetaLocal() {
            prefs.edit { putString("meta_$profile", EmberJson.encodeToString(EmberMeta.serializer(), meta)) }
        }

        private fun saveRun() {
            val e = engine ?: return
            e.state.lastTouched = System.currentTimeMillis()
            val json = if (e.state.phase == Phase.OVER) null else EmberJson.encodeToString(RunState.serializer(), e.state)
            prefs.edit { if (json == null) remove("save_$profile") else putString("save_$profile", json) }
            queueServerSave(json)
        }

        private fun queueServerSave(json: String?) {
            val user = serverRepository.currentUser ?: return
            if (guest) return
            serverSaveJob?.cancel()
            serverSaveJob =
                viewModelScope.launch {
                    delay(if (json == null) 0 else 2500)
                    try {
                        withContext(WholphinDispatchers.IO) {
                            displayPreferencesService.updateDisplayPreferences(user.id) {
                                put(serverSaveKey, json.orEmpty())
                                put("${serverSaveKey}_at", System.currentTimeMillis().toString())
                                put(serverMetaKey, EmberJson.encodeToString(EmberMeta.serializer(), meta))
                            }
                        }
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "Ember Road couldn't save to the server")
                    }
                }
        }

        /** Something changed: redraw, save, tell the phones */
        private fun changed() {
            version++
            val e = engine
            if (e != null && e.state.phase != Phase.EVENT) {
                votes.clear()
                tvVote = null
            }
            if (e != null) {
                saveRun()
                if (e.state.phase == Phase.OVER && !e.state.flags.contains("_counted")) runOver(e)
            }
            playMusicForScreen()
            host?.push()
        }

        // -------------------------------------------------------------------------------------
        // Screens

        fun goTo(s: EmberScreen) {
            screen = s
            playMusicForScreen()
        }

        fun back(): Boolean {
            when (screen) {
                EmberScreen.GALLERY, EmberScreen.BOOK, EmberScreen.HOWTO, EmberScreen.LOBBY, EmberScreen.SETUP, EmberScreen.PHONE -> {
                    goTo(EmberScreen.TITLE)
                    return true
                }

                else -> {
                    return false
                }
            }
        }

        fun quit() {
            saveRun()
            engine = null
            saved =
                prefs
                    .getString(
                        "save_$profile",
                        null,
                    )?.let { runCatching { EmberJson.decodeFromString(RunState.serializer(), it) }.getOrNull() }
            goTo(EmberScreen.TITLE)
        }

        fun leaveGame() {
            saveRun()
            navigationManager.goBack()
        }

        fun toggleMusic() {
            music = !music
            prefs.edit { putBoolean("music", music) }
            player.enabled = music
            if (music) playMusicForScreen() else player.quiet()
        }

        fun toggleCrt() {
            crt = !crt
            prefs.edit { putBoolean("crt", crt) }
        }

        var musicPaused = false
            set(value) {
                field = value
                if (value) player.quiet() else playMusicForScreen()
            }

        private fun playMusicForScreen() {
            if (!music || musicPaused) return
            val e = engine
            val tune =
                when {
                    screen != EmberScreen.PLAY || e == null -> "title"
                    e.state.phase == Phase.CAMP -> "camp"
                    e.state.phase == Phase.OVER -> "end"
                    e.state.phase == Phase.STOP && e.currentStop?.final == true -> "4"
                    e.state.phase == Phase.STOP || e.state.phase == Phase.MARKET -> "5"
                    else -> e.land.music.toString()
                }
            player.play(tune)
        }

        override fun onCleared() {
            saveRun()
            player.stop()
            host?.stop()
            super.onCleared()
        }

        // -------------------------------------------------------------------------------------
        // Starting and continuing

        fun continueRun() {
            val s = story ?: return
            val r = saved ?: return
            engine = EmberEngine(s, r)
            log = null
            screen = EmberScreen.PLAY
            changed()
        }

        /** "New journey": on a TV, first a chance for phones to join */
        fun newJourney(isTv: Boolean) {
            prepareDraft()
            if (isTv) {
                startHost()
                goTo(EmberScreen.LOBBY)
            } else {
                goTo(EmberScreen.SETUP)
            }
        }

        fun startHost() {
            if (host != null) return
            host =
                EmberLinkHost(
                    context,
                    onJoin = { j -> onMain { if (joined.none { it.id == j.id }) joined.add(j) } },
                    onLeave = { j -> onMain { joined.removeAll { it.id == j.id } } },
                    onDo = { j, a -> onMain { phoneDo(j, a) } },
                    onType = { j, w -> onMain { phoneType(j, w) } },
                ).also {
                    it.viewFor = { j -> phoneView(j) }
                    it.start()
                }
        }

        private fun onMain(block: () -> Unit) {
            viewModelScope.launch(Dispatchers.Main) { block() }
        }

        private fun prepareDraft() {
            val s = story ?: return
            draft.clear()
            val r = Random(System.currentTimeMillis())
            val folks = listOf("human", "elf", "dwarf", "human")
            val startSkills = listOf("healer", "ranger", "bard", "merchant")
            val startTraits = s.traits.shuffled(r).map { it.id }
            repeat(4) { i ->
                val folk = folks[i]
                draft.add(
                    DraftMember(
                        randomName(s, folk, r),
                        folk,
                        listOfNotNull(startTraits.getOrNull(i)),
                        r.nextInt(12),
                        role = startSkills[i],
                    ),
                )
            }
            draftPet =
                s.names.pets
                    .firstOrNull()
                    ?.id
            draftLength = if (kids) "short" else "medium"
            draftDifficulty = if (kids) "easy" else "normal"
            draftCart = "wagon"
            // The family as the party: names from the profiles on this server
            viewModelScope.launch {
                try {
                    val names =
                        withContext(WholphinDispatchers.IO) {
                            api.userApi
                                .getPublicUsers()
                                .content
                                .mapNotNull { it.name?.trim()?.takeIf { n -> n.isNotBlank() } }
                        }
                    val me = serverRepository.currentUser?.name
                    val ordered = (listOfNotNull(me) + names).distinct().filterNot { it.equals("kids", true) }.take(4)
                    ordered.forEachIndexed { i, n -> if (i < draft.size) draft[i] = draft[i].copy(name = n.take(14)) }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.i(ex, "Couldn't get profile names for the party")
                }
            }
        }

        private fun randomName(
            s: Story,
            folk: String,
            r: Random = Random,
        ): String {
            val list =
                when (folk) {
                    "elf" -> s.names.elf
                    "dwarf" -> s.names.dwarf
                    "fox" -> s.names.fox
                    else -> s.names.human
                }
            return list.ifEmpty { listOf("Pip") }.random(r)
        }

        fun renameMember(
            i: Int,
            name: String,
        ) {
            if (i in draft.indices) draft[i] = draft[i].copy(name = name.take(14))
        }

        fun cycleFolk(i: Int) {
            if (i !in draft.indices) return
            val order = listOf("human", "elf", "dwarf", "fox")
            val next = order[(order.indexOf(draft[i].folk) + 1) % order.size]
            draft[i] = draft[i].copy(folk = next, face = Random.nextInt(12))
        }

        /** A new random look, skill and trait. The name stays, so a typed name isn't lost. */
        fun rerollMember(i: Int) {
            val s = story ?: return
            if (i !in draft.indices) return
            val skills = EmberSkills.keys.filterNot { roleLocked(it) }
            draft[i] =
                draft[i].copy(
                    traits = listOf(s.traits.random().id),
                    face = Random.nextInt(12),
                    role = skills.random(),
                )
        }

        /** Next skill for this person (locked ones are skipped) */
        fun cycleSkill(i: Int) {
            if (i !in draft.indices) return
            val skills = EmberSkills.keys.filterNot { roleLocked(it) }
            val now = skills.indexOf(draft[i].role)
            draft[i] = draft[i].copy(role = skills[(now + 1) % skills.size])
        }

        /** Next trait for this person */
        fun cycleTrait(i: Int) {
            val s = story ?: return
            if (i !in draft.indices) return
            val all = s.traits.map { it.id }
            val now = all.indexOf(draft[i].traits.firstOrNull())
            draft[i] = draft[i].copy(traits = listOf(all[(now + 1) % all.size]))
        }

        fun addMember() {
            val s = story ?: return
            if (draft.size >= 4) return
            draft.add(
                DraftMember(
                    randomName(s, "human"),
                    "human",
                    listOf(s.traits.random().id),
                    Random.nextInt(12),
                    role = EmberSkills.keys.filterNot { roleLocked(it) }.random(),
                ),
            )
        }

        fun removeMember(i: Int) {
            if (draft.size > 1 && i in draft.indices) draft.removeAt(i)
        }

        /** Unlocks: Mage after one finished journey, Smith after two, Hard after one, Royal cart after three */
        fun roleLocked(role: String) =
            when (role) {
                "mage" -> meta.runs < 1
                "smith" -> meta.runs < 2
                else -> false
            }

        fun difficultyLocked(d: String) = d == "hard" && meta.runs < 1

        fun cartLocked(c: String) =
            when (c) {
                "snail" -> meta.runs < 1
                "royal" -> meta.runs < 3
                else -> false
            }

        fun startJourney() {
            val s = story ?: return
            if (draft.isEmpty()) return
            val members =
                draft.mapIndexed { i, d ->
                    Member(
                        name = d.name.ifBlank { randomName(s, d.folk) },
                        folk = d.folk,
                        traits = d.traits,
                        leader = i == 0,
                        face = d.face,
                        role = if (roleLocked(d.role)) "healer" else d.role,
                    )
                }
            val setup =
                RunSetup(
                    members = members,
                    role = members.first().role ?: "healer",
                    pet = draftPet,
                    length = draftLength,
                    difficulty = if (difficultyLocked(draftDifficulty)) "normal" else draftDifficulty,
                    kids = kids,
                    cart = draftCart,
                    seed = System.currentTimeMillis(),
                )
            engine = EmberEngine.newRun(s, setup, System.currentTimeMillis())
            log = Shown(s.land("hearthvale").blurb)
            newBest = false
            markLeft = false
            campMovie = null
            screen = EmberScreen.PLAY
            changed()
        }

        // -------------------------------------------------------------------------------------
        // Playing

        private fun passedMarkers(
            e: EmberEngine,
            before: Int,
            land: String,
        ): List<String> {
            if (e.state.route.last() != land || e.state.legMiles == 0) return emptyList()
            val now = e.state.miles * 100 / e.state.legMiles
            return family
                .filter { it.land == land && it.spot in (before + 1)..now }
                .map { "You pass a trail marker left by ${it.code}: \"${it.line}\"" }
        }

        fun travel() {
            val e = engine ?: return
            val land = e.state.route.last()
            val before = if (e.state.legMiles == 0) 0 else e.state.miles * 100 / e.state.legMiles
            val shown = e.travel()
            val marks = passedMarkers(e, before, land)
            log = if (marks.isEmpty()) shown else shown.copy(text = (shown.text + "\n" + marks.joinToString("\n")).trim())
            changed()
        }

        fun camp() {
            val e = engine ?: return
            e.camp()
            log = null
            campMovie = null
            changed()
            loadCampMovie(e.campGenre)
        }

        fun leaveCamp() {
            engine?.leaveCamp()
            log = null
            campMovie = null
            changed()
        }

        fun cyclePace() {
            val e = engine ?: return
            val order = listOf("easy", "steady", "hard")
            e.setPace(order[(order.indexOf(e.state.pace) + 1) % order.size])
            changed()
        }

        fun cycleRations() {
            val e = engine ?: return
            val order = listOf("little", "normal", "lots")
            e.setRations(order[(order.indexOf(e.state.rations) + 1) % order.size])
            changed()
        }

        fun useHerbs() {
            val e = engine ?: return
            log = e.useHerbs()
            changed()
        }

        /** A choice in an event. With phones joined, it's a vote. */
        fun choose(index: Int) {
            val e = engine ?: return
            if (e.state.phase != Phase.EVENT) return
            if (joined.isEmpty()) {
                resolveChoice(index, emptyList())
                return
            }
            tvVote = "c$index"
            version++
            host?.push()
            maybeCount()
        }

        /** "Decide now": count the votes so far */
        fun decideNow() {
            val tv = tvVote ?: votes.values.firstOrNull() ?: return
            count(tv)
        }

        private fun maybeCount() {
            val tv = tvVote ?: return
            if (joined.all { votes.containsKey(it.id) }) count(tv)
        }

        private fun count(tv: String) {
            val all = votes.values.toList() + tv
            val tally = all.groupingBy { it }.eachCount()
            val top = tally.values.maxOrNull() ?: return
            val leaders = tally.filterValues { it == top }.keys
            // Ties go to the TV (the party leader)
            val winner = if (tv in leaders) tv else leaders.first()
            val grumblers =
                joined.filter { votes[it.id] != null && votes[it.id] != winner }.map { it.name } +
                    (if (tv != winner) listOf("The TV") else emptyList())
            val idx = winner.removePrefix("c").toIntOrNull() ?: return
            voteNote = if (all.size > 1) "Votes: " + tally.entries.joinToString { "${it.value} for ${labelOf(it.key)}" } else null
            resolveChoice(idx, grumblers)
        }

        private fun labelOf(action: String): String {
            val e = engine ?: return action
            val ev = e.currentEvent ?: return action
            val i = action.removePrefix("c").toIntOrNull() ?: return action
            return ev.choices.getOrNull(i)?.let { e.choiceLabel(it) } ?: action
        }

        private fun resolveChoice(
            index: Int,
            grumblers: List<String>,
        ) {
            val e = engine ?: return
            val shown = e.choose(index)
            if (grumblers.isNotEmpty()) {
                val g = grumblers.joinToString(" and ")
                e.state.shown =
                    shown.copy(text = shown.text + "\n\n($g grumble${if (grumblers.size == 1) "s" else ""}, but goes along with it.)")
            }
            votes.clear()
            tvVote = null
            changed()
        }

        fun continueOn() {
            val e = engine ?: return
            val shown = e.continueOn()
            log = shown
            voteNote = null
            changed()
        }

        fun act(
            verb: Verb,
            thing: String,
            with: String? = null,
        ) {
            val e = engine ?: return
            log = e.act(verb, thing, with)
            changed()
        }

        /** Typing a command (TV keyboard, phone keyboard or a joined phone) */
        fun type(words: String): Shown? {
            val e = engine ?: return null
            if (e.state.phase != Phase.STOP) return null
            val r = e.typed(words)
            log = r.shown.copy(text = "> ${words.trim()}\n" + r.shown.text)
            changed()
            return r.shown
        }

        fun leaveStop() {
            engine?.leaveStop()
            log = null
            changed()
        }

        fun openMarket() {
            engine?.openMarket()
            log = null
            changed()
        }

        fun closeMarket() {
            engine?.closeMarket()
            log = null
            changed()
        }

        fun buy(id: String) {
            val e = engine ?: return
            log = e.buy(id)
            changed()
        }

        fun sell(id: String) {
            val e = engine ?: return
            log = e.sell(id)
            changed()
        }

        fun pickFork(id: String) {
            val e = engine ?: return
            e.pickFork(id)
            log = Shown(e.land.blurb)
            changed()
        }

        fun minigameDone(score: Int) {
            val e = engine ?: return
            e.minigameDone(score)
            changed()
        }

        // -------------------------------------------------------------------------------------
        // The end of a run

        private fun runOver(e: EmberEngine) {
            e.state.flags.add("_counted")
            val ending = e.endingDef
            val m =
                meta.copy(
                    endings = (meta.endings + listOfNotNull(ending?.id)).toMutableSet(),
                    book = (meta.book + e.state.book).toMutableSet(),
                    runs = meta.runs + 1,
                    best = maxOf(meta.best, e.state.score),
                )
            newBest = e.state.score > meta.best
            meta = m
            saveMetaLocal()
            // The Games page "Highest scores" board (not for Kids or Guest)
            val user = serverRepository.currentUser
            if (user != null && !kids && !guest) {
                val key = "${user.id}_$BOARD_KEY"
                if (e.state.score > gamePrefs.getInt(key, 0)) gamePrefs.edit { putInt(key, e.state.score) }
            }
            prefs.edit { remove("save_$profile") }
            saved = null
            queueServerSave(null)
        }

        /** Trail marker lines to pick from at the end */
        fun markerChoices(): List<String> {
            val s = story ?: return emptyList()
            val e = engine ?: return s.markerLines.take(4)
            val r = Random(e.state.setup.seed)
            return s.markerLines
                .shuffled(r)
                .take(4)
                .map { e.fill(it) }
        }

        fun leaveMark(line: String) {
            val e = engine ?: return
            if (markLeft) return
            markLeft = true
            val (land, spot) = e.markerSpot()
            val mark = Mark(land, spot.coerceIn(1, 99), line.replace('~', '-').replace('\n', ' ').take(80), e.state.ending.orEmpty())
            meta.marks.add(mark)
            while (meta.marks.size > 3) meta.marks.removeAt(0)
            saveMetaLocal()
            val user = serverRepository.currentUser ?: return
            if (guest || kids) return
            val encoded = meta.marks.joinToString("\n") { "${it.land}~${it.spot}~${it.line}~${it.ending}" }
            viewModelScope.launch {
                try {
                    withContext(WholphinDispatchers.IO) {
                        displayPreferencesService.updateDisplayPreferences(user.id) {
                            put(BenflixHighScores.KEY_EMBER_MARKS, encoded)
                            put(serverMetaKey, EmberJson.encodeToString(EmberMeta.serializer(), meta))
                        }
                    }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Couldn't save the trail marker")
                }
            }
        }

        fun backToTitle() {
            engine = null
            log = null
            goTo(EmberScreen.TITLE)
        }

        // -------------------------------------------------------------------------------------
        // Campfire movie pick

        private fun loadCampMovie(genre: String) {
            val user = serverRepository.currentUser ?: return
            viewModelScope.launch {
                try {
                    val dto =
                        withContext(WholphinDispatchers.IO) {
                            api.itemsApi
                                .getItems(
                                    GetItemsRequest(
                                        userId = user.id,
                                        includeItemTypes = listOf(BaseItemKind.MOVIE),
                                        recursive = true,
                                        genres = listOf(genre),
                                        isPlayed = false,
                                        sortBy = listOf(ItemSortBy.RANDOM),
                                        limit = 1,
                                        imageTypes = listOf(ImageType.PRIMARY),
                                        fields = listOf(ItemFields.OVERVIEW),
                                    ),
                                ).content.items
                                .firstOrNull()
                        } ?: return@launch
                    campMovie =
                        CampMovie(
                            id = dto.id,
                            name = dto.name.orEmpty(),
                            year = dto.productionYear,
                            poster =
                                imageUrlService.getItemImageUrl(
                                    itemId = dto.id,
                                    imageType = ImageType.PRIMARY,
                                    maxWidth = 300,
                                    tag = dto.imageTags?.get(ImageType.PRIMARY),
                                ),
                            added = dto.userData?.isFavorite == true,
                        )
                    // Tonight's story is about tonight's movie, so the words and the poster match
                    val gist =
                        dto.overview
                            .orEmpty()
                            .replace("\n", " ")
                            .trim()
                            .let { o ->
                                val end = Regex("[.!?](\\s|$)").find(o)?.range?.first
                                if (end != null) o.substring(0, end + 1) else o
                            }.take(220)
                    val title = dto.name.orEmpty() + (dto.productionYear?.let { " ($it)" } ?: "")
                    engine?.setCampTale(title, gist)
                    changed()
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.i(ex, "No campfire movie")
                }
            }
        }

        fun addCampMovie() {
            val m = campMovie ?: return
            if (guest || m.added) return
            viewModelScope.launch {
                try {
                    withContext(WholphinDispatchers.IO) { favoriteWatchManager.setFavorite(m.id, true) }
                    campMovie = m.copy(added = true)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Couldn't add the campfire movie to My List")
                }
            }
        }

        // -------------------------------------------------------------------------------------
        // The buttons for each part of the game (the TV shows them, and so do the phones)

        fun actions(): List<Act> {
            val e = engine ?: return emptyList()
            return when (e.state.phase) {
                Phase.TRAVEL -> {
                    listOfNotNull(
                        Act("go", "Keep going", "Travel one day down the road"),
                        Act("camp", "Make camp", "Rest a day: heals, warms the egg, calms magic"),
                        Act(
                            "pace",
                            "Pace: ${e.state.pace}",
                            when (e.state.pace) {
                                "easy" -> "Slow but restful. Press to change"
                                "hard" -> "Fast but tiring, more fevers. Press to change"
                                else -> "Normal speed. Press to change"
                            },
                        ),
                        Act(
                            "rations",
                            "Food: ${e.state.rations}",
                            when (e.state.rations) {
                                "little" -> "Saves food, but more fevers. Press to change"
                                "lots" -> "Uses more food, keeps spirits up. Press to change"
                                else -> "2 food each a day. Press to change"
                            },
                        ),
                        if (e.state.herbs > 0 && e.active.any { it.sick || it.health < 3 }) {
                            Act("herbs", "Use herbs", "Heals whoever is sick or hurt")
                        } else {
                            null
                        },
                    )
                }

                Phase.EVENT -> {
                    val ev = e.currentEvent ?: return emptyList()
                    e.choices(ev).map { (i, c) ->
                        val chance = c.roll?.let { " (${e.rollChance(it)}%)" }.orEmpty()
                        Act("c$i", e.choiceLabel(c) + chance)
                    }
                }

                Phase.RESULT, Phase.OVER -> {
                    if (e.state.phase == Phase.RESULT) listOf(Act("cont", "Continue")) else emptyList()
                }

                Phase.STOP -> {
                    val s = e.currentStop ?: return emptyList()
                    e.visibleThings(s).take(6).map { Act("look:${it.id}", "Look at ${it.name}") } +
                        e
                            .visibleThings(s)
                            .filter { it.person }
                            .take(3)
                            .map { Act("talk:${it.id}", "Talk to ${it.name}") } +
                        listOfNotNull(
                            if (s.market) Act("market", "Market") else null,
                            Act("leave", if (s.final) "Finish the journey" else "Leave"),
                        )
                }

                Phase.MARKET -> {
                    e.shopItems().map { Act("buy:${it.id}", "${it.name} (${e.price(it)} gold)") } + Act("close", "Done shopping")
                }

                Phase.FORK -> {
                    e.state.forkOptions.map { Act("fork:$it", "${e.story.land(it).name}: ${e.forkLabel(it)}") }
                }

                Phase.CAMP -> {
                    listOf(Act("leavecamp", "Pack up and go on", "Back on the road"))
                }

                Phase.MINIGAME -> {
                    if (e.state.minigame ==
                        "rocks"
                    ) {
                        listOf(Act("mg:left", "◀ Left"), Act("mg:right", "Right ▶"))
                    } else {
                        listOf(Act("mg:tap", "Now!"))
                    }
                }
            }
        }

        fun doAction(id: String) {
            when {
                id == "go" -> travel()
                id == "camp" -> camp()
                id == "pace" -> cyclePace()
                id == "rations" -> cycleRations()
                id == "herbs" -> useHerbs()
                id.startsWith("c") && id.drop(1).toIntOrNull() != null -> choose(id.drop(1).toInt())
                id == "cont" -> continueOn()
                id.startsWith("look:") -> act(Verb.LOOK, id.removePrefix("look:"))
                id.startsWith("talk:") -> act(Verb.TALK, id.removePrefix("talk:"))
                id == "market" -> openMarket()
                id == "leave" -> leaveStop()
                id.startsWith("buy:") -> buy(id.removePrefix("buy:"))
                id == "close" -> closeMarket()
                id.startsWith("fork:") -> pickFork(id.removePrefix("fork:"))
                id == "leavecamp" -> leaveCamp()
                id.startsWith("mg:") -> _minigameInput.tryEmit(id.removePrefix("mg:"))
            }
        }

        private fun phoneDo(
            j: Joined,
            action: String,
        ) {
            val e = engine ?: return
            if (screen != EmberScreen.PLAY) return
            if (actions().none { it.id == action }) return
            if (e.state.phase == Phase.EVENT && action.startsWith("c")) {
                votes[j.id] = action
                version++
                host?.push()
                maybeCount()
                return
            }
            doAction(action)
        }

        private fun phoneType(
            j: Joined,
            words: String,
        ) {
            val r = type(words)
            phoneReplies[j.id] = r?.text ?: "You can type when the party is at a stop."
            host?.push()
        }

        /** Votes so far for a choice (for the TV to show) */
        fun votesFor(action: String): List<String> =
            joined.filter { votes[it.id] == action }.map { it.name } + (if (tvVote == action) listOf("TV") else emptyList())

        private fun phoneView(j: Joined): PhoneView {
            val e = engine
            if (screen != EmberScreen.PLAY || e == null) {
                return PhoneView(
                    title = "Ember Road",
                    text =
                        if (screen == EmberScreen.LOBBY ||
                            screen == EmberScreen.SETUP
                        ) {
                            "You're in! Waiting for the journey to start on the TV…"
                        } else {
                            "Waiting for the TV…"
                        },
                    status = "${joined.size} phone${if (joined.size == 1) "" else "s"} joined",
                )
            }
            val title =
                when (e.state.phase) {
                    Phase.EVENT -> e.currentEvent?.title.orEmpty()
                    Phase.STOP, Phase.MARKET -> e.currentStop?.name.orEmpty()
                    Phase.FORK -> "A fork in the road"
                    Phase.CAMP -> "Camp"
                    Phase.OVER -> e.endingDef?.title.orEmpty()
                    Phase.MINIGAME -> "Mini-game!"
                    else -> e.land.name
                }
            val text =
                when (e.state.phase) {
                    Phase.EVENT -> {
                        e.currentEvent?.let { e.eventText(it) }.orEmpty()
                    }

                    Phase.RESULT, Phase.CAMP -> {
                        e.state.shown
                            ?.text
                            .orEmpty()
                    }

                    Phase.STOP -> {
                        log?.text ?: e.currentStop?.let { e.stopIntro(it) }.orEmpty()
                    }

                    Phase.OVER -> {
                        "The journey is over. Score: ${e.state.score}"
                    }

                    else -> {
                        log?.text.orEmpty()
                    }
                }
            return PhoneView(
                title = title,
                text = text.take(1500),
                actions = actions().map { PhoneAction(it.id, it.label, it.hint) },
                vote = e.state.phase == Phase.EVENT,
                hint = phoneHint(e, j),
                bag = bagText(e),
                mine = votes[j.id],
                typing = e.state.phase == Phase.STOP,
                reply = phoneReplies[j.id],
                status =
                    "Day ${e.state.day} · Food ${e.state.food} · Gold ${e.state.gold} · Spirit ${e.state.spirit}/10" +
                        if (e.eggHere && !e.hatched) " · Egg warmth ${e.state.warmth}/10" else "",
            )
        }

        /** Plain words for a phone: is this a vote, or does pressing do it straight away? */
        private fun phoneHint(
            e: EmberEngine,
            j: Joined,
        ): String =
            when (e.state.phase) {
                Phase.EVENT -> {
                    when {
                        votes[j.id] != null && tvVote == null -> {
                            "Your vote is in. Waiting for the TV to vote too (or press Decide now on the TV)."
                        }

                        votes[j.id] != null -> {
                            "Your vote is in. Waiting for the other phones. You can still change it."
                        }

                        else -> {
                            "This is a vote. Every phone and the TV picks one. Most votes win, and a tie goes to the TV."
                        }
                    }
                }

                Phase.TRAVEL -> {
                    "Not a vote: these buttons work straight away for the whole party, from any phone or the TV. Agree first!"
                }

                Phase.STOP -> {
                    "Not a vote: Look and Talk happen straight away. You can also type what to do, like \"look under the bridge\"."
                }

                Phase.MARKET -> {
                    "Anyone can buy. The gold is shared by the whole party."
                }

                Phase.FORK -> {
                    "Not a vote: the first road anyone picks is the one you take. Agree first!"
                }

                Phase.CAMP -> {
                    "Resting for the night. Anyone can press Pack up to set off again."
                }

                Phase.RESULT -> {
                    "Anyone can press Continue."
                }

                Phase.MINIGAME -> {
                    "Everyone can play: press the buttons on your phone at the right time."
                }

                Phase.OVER -> {
                    "The journey is over. Pick a trail marker on the TV."
                }
            }

        /** The bag, spells and party in words (for phones) */
        private fun bagText(e: EmberEngine): String {
            val s = e.story
            val items =
                e.state.items
                    .groupingBy { it }
                    .eachCount()
                    .map { (id, n) -> (s.item(id)?.name ?: id) + if (n > 1) " ×$n" else "" }
            val party =
                e.state.members.map { m ->
                    m.name + ": " +
                        listOfNotNull(
                            m.role?.let { EmberSkills[it]?.first },
                            m.traits.firstOrNull()?.let { s.trait(it)?.name },
                        ).joinToString(", ") + if (m.gone) " (went home)" else ""
                }
            return listOf(
                "Party\n" + party.joinToString("\n"),
                "Items\n" + items.joinToString().ifEmpty { "Nothing but crumbs" },
                "Herbs: ${e.state.herbs}",
                "Spells\n" + e.state.spells.joinToString { s.spell(it)?.name ?: it },
                "Journal\n" +
                    e.state.journal
                        .takeLast(6)
                        .reversed()
                        .joinToString("\n"),
            ).joinToString("\n\n")
        }

        /** This phone's name when it joins a TV's game */
        val phoneName: String get() =
            serverRepository.currentUser?.name?.take(20) ?: android.os.Build.MODEL
                .take(20)

        /** Server, user and sign-in for watching a show on the side */
        fun sideShowAuth(): Triple<String, String, String>? {
            if (kids) return null
            val server = serverRepository.currentServer?.url ?: return null
            val user = serverRepository.currentUser ?: return null
            val token = user.accessToken ?: return null
            return Triple(server, user.id.toString(), token)
        }

        companion object {
            /** The "Highest scores" board key */
            const val BOARD_KEY = "EMBER_ROAD"
        }
    }
