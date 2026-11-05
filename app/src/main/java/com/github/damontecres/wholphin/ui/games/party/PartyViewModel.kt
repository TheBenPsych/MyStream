package com.github.damontecres.wholphin.ui.games.party

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.ui.games.ember.EmberLinkHost
import com.github.damontecres.wholphin.ui.games.ember.Joined
import com.github.damontecres.wholphin.ui.games.ember.PhoneAction
import com.github.damontecres.wholphin.ui.games.ember.PhoneView
import com.github.damontecres.wholphin.ui.games.puzzles.PuzzlePool
import com.github.damontecres.wholphin.ui.games.puzzles.PuzzleTitle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.abs

/** The four party games (Ben's picks from Idea Box 21) */
enum class PartyKind(
    val title: String,
    val about: String,
    val minPlayers: Int,
    val steps: List<String>,
) {
    QUIZ(
        "Quiz night",
        "10 quick rounds about the movies and shows on Tower. Everyone answers on their own Android phone.",
        1,
        listOf("A question shows on the TV.", "Answer on your phone.", "Fast right answers score more."),
    ),
    FAKE_PLOT(
        "Fake plot",
        "Everyone writes a made-up plot for a real title. Then vote for the one you think is real.",
        3,
        listOf("Write a fake plot on your phone.", "Vote for the real plot.", "Score for finding it and for fooling others."),
    ),
    DRAW(
        "Draw the movie",
        "One phone gets a secret movie and draws it. The drawing shows on the TV and everyone else guesses.",
        3,
        listOf(
            "Your phone shows a secret title: draw it.",
            "Others pick the answer on their phones.",
            "Fast guesses score more, and so does the artist.",
        ),
    ),
    CLOSEST(
        "Closest wins",
        "Guess a number: the year, the length, the IMDb score. Closest guess wins the round.",
        1,
        listOf("A question shows on the TV.", "Type a number on your phone.", "Closest gets 1,000. Exactly right gets a bonus."),
    ),
}

enum class PartyPhase { LOBBY, QUESTION, WRITE, VOTE, DRAWING, REVEAL, OVER }

data class PartyPlayer(
    val id: String,
    val name: String,
    val score: Int = 0,
)

/** One fake (or the real) plot in "Fake plot" */
data class PartyPlot(
    val text: String,
    /** Who wrote it; null = the real plot */
    val by: String?,
)

data class PartyUi(
    val kind: PartyKind = PartyKind.QUIZ,
    val phase: PartyPhase = PartyPhase.LOBBY,
    val players: List<PartyPlayer> = emptyList(),
    val round: Int = 0,
    val rounds: Int = 0,
    val prompt: String = "",
    val image: String? = null,
    /** Wide picture (a scene) rather than a poster */
    val wide: Boolean = false,
    val choices: List<String> = emptyList(),
    /** When the current question ends (System.currentTimeMillis()) */
    val endsAt: Long = 0,
    /** Players who answered this question */
    val answered: Set<String> = emptySet(),
    val reveal: String = "",
    val plots: List<PartyPlot> = emptyList(),
    /** Draw the movie: who is drawing, and the lines so far (each "x,y x,y …" out of 1000) */
    val drawer: String? = null,
    val lines: List<String> = emptyList(),
    val title: PuzzleTitle? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

/**
 * Benflix party games on a TV: phones join over the home Wi-Fi (the same link as Ember Road, with
 * its own name so the two don't mix), answer on their screens, and the TV shows the questions and
 * the scores.
 */
@HiltViewModel
class PartyViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val pool: PuzzlePool,
    ) : ViewModel() {
        private val _ui = MutableStateFlow(PartyUi())
        val ui: StateFlow<PartyUi> = _ui
        private var host: EmberLinkHost? = null
        private var job: Job? = null
        private var titles: List<PuzzleTitle> = emptyList()

        /** Per-question answers: player id to what they sent */
        private val answers = mutableMapOf<String, String>()
        private val answerTimes = mutableMapOf<String, Long>()
        private var answerIndex = -1
        private var answerNumber = 0.0
        private var startedAt = 0L

        /** Fake plot: who voted for which plot (player id to plot index) */
        private val votes = mutableMapOf<String, Int>()
        private var drawOrder: List<String> = emptyList()

        fun setup(kind: PartyKind) {
            if (host != null && _ui.value.kind == kind) return
            _ui.value = PartyUi(kind = kind)
            host?.stop()
            host =
                EmberLinkHost(
                    context = context.applicationContext,
                    onJoin = { j ->
                        _ui.update { u ->
                            if (u.players.any { it.id == j.id }) u else u.copy(players = u.players + PartyPlayer(j.id, j.name))
                        }
                        push()
                    },
                    onLeave = { j ->
                        if (_ui.value.phase ==
                            PartyPhase.LOBBY
                        ) {
                            _ui.update { u -> u.copy(players = u.players.filter { it.id != j.id }) }
                        }
                    },
                    onDo = { j, a -> viewModelScope.launch { onDo(j, a) } },
                    onType = { j, t -> viewModelScope.launch { onType(j, t) } },
                    serviceType = SERVICE_TYPE,
                    label = LABEL,
                    onDraw = { j, line -> viewModelScope.launch { onDraw(j, line) } },
                    maxArg = 1500,
                ).also {
                    it.viewFor = ::viewFor
                    it.start()
                }
            viewModelScope.launch {
                try {
                    titles = pool.titles()
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Party couldn't load titles")
                    _ui.update { it.copy(error = "Couldn't load movies from the server.") }
                }
            }
        }

        fun canStart(): Boolean = _ui.value.players.size >= _ui.value.kind.minPlayers && titles.size >= 12

        fun startGame() {
            if (!canStart()) return
            _ui.update { u -> u.copy(players = u.players.map { it.copy(score = 0) }, round = 0, error = null) }
            job?.cancel()
            job =
                viewModelScope.launch {
                    when (_ui.value.kind) {
                        PartyKind.QUIZ -> runQuiz()
                        PartyKind.CLOSEST -> runClosest()
                        PartyKind.FAKE_PLOT -> runFakePlot()
                        PartyKind.DRAW -> runDraw()
                    }
                    _ui.update { it.copy(phase = PartyPhase.OVER) }
                    push()
                }
        }

        fun backToLobby() {
            job?.cancel()
            _ui.update { it.copy(phase = PartyPhase.LOBBY) }
            push()
        }

        private fun push() = host?.push()

        // ---------- Quiz night ----------

        private data class Question(
            val prompt: String,
            val image: String?,
            val wide: Boolean,
            val choices: List<String>,
            val answer: Int,
            val title: PuzzleTitle,
        )

        private fun quizQuestion(): Question? {
            repeat(30) {
                val t = titles.random()
                val others = titles.filter { it.name != t.name }.shuffled()
                when ((0..3).random()) {
                    0 -> {
                        if (t.stills.isEmpty()) return@repeat
                        val choices = (others.take(3).map { it.name } + t.name).shuffled()
                        return Question("Which one is this scene from?", t.stills.random(), true, choices, choices.indexOf(t.name), t)
                    }

                    1 -> {
                        val actor = t.actors.firstOrNull() ?: return@repeat
                        val wrong =
                            others
                                .flatMap { it.actors }
                                .map { it.name }
                                .filter { n ->
                                    t.actors.none { it.name == n }
                                }.distinct()
                                .shuffled()
                                .take(3)
                        if (wrong.size < 3) return@repeat
                        val choices = (wrong + actor.name).shuffled()
                        return Question("Who is in ${t.label}?", t.poster, false, choices, choices.indexOf(actor.name), t)
                    }

                    2 -> {
                        val four = (others.filter { it.year != null && it.year != t.year }.take(3) + t).filter { it.year != null }
                        if (four.size < 4 || four.map { it.year }.distinct().size < 4) return@repeat
                        val oldest = four.minBy { it.year!! }
                        val choices = four.shuffled().map { it.name }
                        return Question("Which came out FIRST?", null, false, choices, choices.indexOf(oldest.name), oldest)
                    }

                    else -> {
                        val plot = hideName(firstSentences(t.overview, 1), t.name)
                        if (plot.length < 40) return@repeat
                        val choices = (others.take(3).map { it.name } + t.name).shuffled()
                        return Question("Which one is this?\n\"$plot\"", null, false, choices, choices.indexOf(t.name), t)
                    }
                }
            }
            return null
        }

        private suspend fun runQuiz() {
            val rounds = 10
            repeat(rounds) { r ->
                val q = quizQuestion() ?: return
                answers.clear()
                answerTimes.clear()
                answerIndex = q.answer
                startedAt = System.currentTimeMillis()
                _ui.update {
                    it.copy(
                        phase = PartyPhase.QUESTION,
                        round = r + 1,
                        rounds = rounds,
                        prompt = q.prompt,
                        image = q.image,
                        wide = q.wide,
                        choices = q.choices,
                        endsAt = startedAt + QUESTION_MS,
                        answered = emptySet(),
                        title = q.title,
                    )
                }
                push()
                waitForAnswers(QUESTION_MS)
                // Right answers: 500, plus up to 500 more for speed
                val gained =
                    answers
                        .mapNotNull { (id, a) ->
                            if (a == "c${q.answer}") {
                                val took = (answerTimes[id] ?: startedAt) - startedAt
                                id to (500 + (500 * (1f - took.toFloat() / QUESTION_MS)).toInt().coerceIn(0, 500))
                            } else {
                                null
                            }
                        }.toMap()
                award(gained)
                showReveal("It's ${q.choices[q.answer]}!", gained)
            }
        }

        // ---------- Closest wins ----------

        private suspend fun runClosest() {
            val rounds = 8
            repeat(rounds) { r ->
                val t = titles.shuffled().firstOrNull { it.year != null } ?: return
                val options =
                    listOfNotNull(
                        t.year?.let { Triple("What year did ${t.name} come out?", it.toDouble(), "$it") },
                        t.runtimeMin?.takeIf { !t.isSeries }?.let {
                            Triple(
                                "How long is ${t.name}, in minutes?",
                                it.toDouble(),
                                "$it minutes",
                            )
                        },
                        t.rating.takeIf { it > 0 }?.let {
                            Triple("What's the IMDb score of ${t.name}? (like 7.4)", it / 10.0, "${it / 10}.${it % 10}")
                        },
                        t.episodes?.let { Triple("How many episodes of ${t.name} are on Tower?", it.toDouble(), "$it episodes") },
                    )
                val (prompt, value, shown) = options.random()
                answers.clear()
                answerTimes.clear()
                answerNumber = value
                startedAt = System.currentTimeMillis()
                _ui.update {
                    it.copy(
                        phase = PartyPhase.QUESTION,
                        round = r + 1,
                        rounds = rounds,
                        prompt = prompt,
                        image = t.poster,
                        wide = false,
                        choices = emptyList(),
                        endsAt = startedAt + CLOSEST_MS,
                        answered = emptySet(),
                        title = t,
                    )
                }
                push()
                waitForAnswers(CLOSEST_MS)
                val guesses =
                    answers
                        .mapNotNull { (id, a) -> a.replace(',', '.').toDoubleOrNull()?.let { id to it } }
                        .sortedBy { abs(it.second - value) }
                val gained = mutableMapOf<String, Int>()
                guesses.firstOrNull()?.let { (_, best) ->
                    val bestDiff = abs(best - value)
                    guesses.filter { abs(it.second - value) == bestDiff }.forEach { (id, g) ->
                        gained[id] =
                            1000 + if (g == value) 500 else 0
                    }
                    guesses.firstOrNull { abs(it.second - value) > bestDiff }?.let { (_, g2) ->
                        guesses.filter { abs(it.second - value) == abs(g2 - value) }.forEach { (id, _) -> gained[id] = 500 }
                    }
                }
                award(gained)
                val list =
                    guesses.joinToString("   ") { (id, g) ->
                        "${name(id)}: ${if (g % 1.0 == 0.0) g.toInt().toString() else g.toString()}"
                    }
                showReveal("It's $shown!" + if (list.isNotBlank()) "\n$list" else "", gained)
            }
        }

        // ---------- Fake plot ----------

        private suspend fun runFakePlot() {
            val rounds = 3
            val candidates = titles.filter { firstSentences(it.overview, 2).length >= 60 }.shuffled()
            repeat(rounds) { r ->
                val t = candidates.getOrNull(r) ?: return
                answers.clear()
                answerTimes.clear()
                _ui.update {
                    it.copy(
                        phase = PartyPhase.WRITE,
                        round = r + 1,
                        rounds = rounds,
                        prompt = "Write a made-up plot for ${t.label}",
                        image = t.poster,
                        wide = false,
                        choices = emptyList(),
                        endsAt = System.currentTimeMillis() + WRITE_MS,
                        answered = emptySet(),
                        plots = emptyList(),
                        title = t,
                    )
                }
                push()
                waitForAnswers(WRITE_MS)
                val real = hideName(firstSentences(t.overview, 2), t.name)
                val plots = (answers.map { (id, text) -> PartyPlot(text.trim().take(300), id) } + PartyPlot(real, null)).shuffled()
                votes.clear()
                answers.clear()
                _ui.update {
                    it.copy(
                        phase = PartyPhase.VOTE,
                        prompt = "Which is the REAL plot of ${t.label}?",
                        plots = plots,
                        endsAt = System.currentTimeMillis() + VOTE_MS,
                        answered = emptySet(),
                    )
                }
                push()
                waitForAnswers(VOTE_MS)
                val realIndex = plots.indexOfFirst { it.by == null }
                val gained = mutableMapOf<String, Int>()
                votes.forEach { (voter, i) ->
                    if (i == realIndex) {
                        gained[voter] = (gained[voter] ?: 0) + 1000
                    } else {
                        plots[i].by?.let { writer -> gained[writer] = (gained[writer] ?: 0) + 500 }
                    }
                }
                award(gained)
                val fooled =
                    plots.withIndex().filter { it.value.by != null }.mapNotNull { (i, p) ->
                        val n = votes.values.count { it == i }
                        if (n > 0) "${name(p.by!!)} fooled $n" else null
                    }
                showReveal(
                    "The real plot was number ${realIndex + 1}." + if (fooled.isNotEmpty()) "\n" + fooled.joinToString(" · ") else "",
                    gained,
                )
            }
        }

        // ---------- Draw the movie ----------

        private suspend fun runDraw() {
            drawOrder =
                _ui.value.players
                    .map { it.id }
                    .shuffled()
                    .take(6)
            val easy = titles.filter { it.rating >= 70 && !it.isSeries }.ifEmpty { titles }.shuffled()
            drawOrder.forEachIndexed { r, drawer ->
                if (_ui.value.players.none { it.id == drawer }) return@forEachIndexed
                val t = easy.getOrNull(r) ?: return
                val choices =
                    (
                        titles
                            .filter { it.name != t.name }
                            .shuffled()
                            .take(5)
                            .map { it.name } + t.name
                    ).shuffled()
                answers.clear()
                answerTimes.clear()
                answerIndex = choices.indexOf(t.name)
                startedAt = System.currentTimeMillis()
                _ui.update {
                    it.copy(
                        phase = PartyPhase.DRAWING,
                        round = r + 1,
                        rounds = drawOrder.size,
                        prompt = "${name(drawer)} is drawing…",
                        image = null,
                        choices = choices,
                        endsAt = startedAt + DRAW_MS,
                        answered = emptySet(),
                        drawer = drawer,
                        lines = emptyList(),
                        title = t,
                    )
                }
                push()
                waitForAnswers(DRAW_MS)
                val right =
                    answers
                        .filter { it.value == "c$answerIndex" }
                        .keys
                        .sortedBy { answerTimes[it] ?: Long.MAX_VALUE }
                val gained = mutableMapOf<String, Int>()
                right.forEachIndexed { i, id -> gained[id] = (1000 - i * 150).coerceAtLeast(300) }
                if (right.isNotEmpty()) gained[drawer] = right.size * 300
                award(gained)
                showReveal("It was ${t.label}!", gained)
            }
        }

        // ---------- Shared ----------

        /** Waits until everyone who can answer has, or the time runs out */
        private suspend fun waitForAnswers(ms: Long) {
            val end = System.currentTimeMillis() + ms
            while (System.currentTimeMillis() < end) {
                delay(250)
                val u = _ui.value
                val need =
                    u.players
                        .map { it.id }
                        .filter { u.phase != PartyPhase.DRAWING || it != u.drawer }
                if (need.isNotEmpty() && need.all { it in u.answered }) break
            }
        }

        private suspend fun showReveal(
            text: String,
            gained: Map<String, Int>,
        ) {
            val extra = gained.entries.sortedByDescending { it.value }.joinToString("   ") { "${name(it.key)} +${it.value}" }
            _ui.update { it.copy(phase = PartyPhase.REVEAL, reveal = text + if (extra.isNotBlank()) "\n$extra" else "") }
            push()
            delay(REVEAL_MS)
        }

        private fun award(gained: Map<String, Int>) {
            _ui.update { u -> u.copy(players = u.players.map { p -> p.copy(score = p.score + (gained[p.id] ?: 0)) }) }
        }

        private fun name(id: String): String =
            _ui.value.players
                .firstOrNull { it.id == id }
                ?.name ?: "Someone"

        private fun markAnswered(id: String) {
            answerTimes.putIfAbsent(id, System.currentTimeMillis())
            _ui.update { it.copy(answered = it.answered + id) }
            push()
        }

        private fun onDo(
            j: Joined,
            action: String,
        ) {
            val u = _ui.value
            when (u.phase) {
                PartyPhase.QUESTION, PartyPhase.DRAWING -> {
                    if (u.phase == PartyPhase.DRAWING && j.id == u.drawer) return
                    if (j.id in u.answered || !action.startsWith("c")) return
                    answers[j.id] = action
                    markAnswered(j.id)
                }

                PartyPhase.VOTE -> {
                    if (j.id in u.answered) return
                    val i = action.removePrefix("p").toIntOrNull() ?: return
                    if (u.plots.getOrNull(i)?.by == j.id) return
                    votes[j.id] = i
                    markAnswered(j.id)
                }

                else -> {}
            }
        }

        private fun onType(
            j: Joined,
            text: String,
        ) {
            val u = _ui.value
            if (text.isBlank() || j.id in u.answered) return
            when (u.phase) {
                PartyPhase.QUESTION -> {
                    if (u.kind == PartyKind.CLOSEST) {
                        answers[j.id] = text.trim()
                        markAnswered(j.id)
                    }
                }

                PartyPhase.WRITE -> {
                    answers[j.id] = text.trim()
                    markAnswered(j.id)
                }

                else -> {}
            }
        }

        private fun onDraw(
            j: Joined,
            line: String,
        ) {
            val u = _ui.value
            if (u.phase != PartyPhase.DRAWING || j.id != u.drawer) return
            _ui.update { if (line == "clear") it.copy(lines = emptyList()) else it.copy(lines = it.lines + line) }
        }

        /** What each phone shows */
        private fun viewFor(j: Joined): PhoneView {
            val u = _ui.value
            val me = u.players.firstOrNull { it.id == j.id }
            val status = "${u.kind.title} · ${me?.name ?: j.name} · ${me?.score ?: 0} points"
            val done = j.id in u.answered
            return when (u.phase) {
                PartyPhase.LOBBY -> {
                    PhoneView(
                        title = "You're in!",
                        text =
                            "Waiting for the TV to start ${u.kind.title}. ${u.players.size} joined: " +
                                u.players.joinToString(", ") { it.name },
                        status = status,
                    )
                }

                PartyPhase.QUESTION -> {
                    if (u.kind == PartyKind.CLOSEST) {
                        PhoneView(
                            title = "Round ${u.round} of ${u.rounds}",
                            text = if (done) "Locked in: ${answers[j.id]}" else u.prompt,
                            input = if (done) "" else "number",
                            status = status,
                            round = u.round,
                        )
                    } else {
                        PhoneView(
                            title = "Question ${u.round} of ${u.rounds}",
                            text = if (done) "Locked in!" else u.prompt,
                            actions = if (done) emptyList() else u.choices.mapIndexed { i, c -> PhoneAction("c$i", c) },
                            status = status,
                            round = u.round,
                        )
                    }
                }

                PartyPhase.WRITE -> {
                    PhoneView(
                        title = "Round ${u.round} of ${u.rounds}",
                        text = if (done) "Sent! Waiting for the others…" else u.prompt + ". Make it sound real!",
                        input = if (done) "" else "text",
                        status = status,
                        round = u.round,
                    )
                }

                PartyPhase.VOTE -> {
                    PhoneView(
                        title = "Vote",
                        text = if (done) "Vote in! Waiting for the others…" else u.prompt + " (yours isn't shown)",
                        actions =
                            if (done) {
                                emptyList()
                            } else {
                                u.plots.withIndex().filter { it.value.by != j.id }.map { (i, p) ->
                                    PhoneAction("p$i", "${i + 1}. ${p.text}")
                                }
                            },
                        status = status,
                        round = u.round * 100 + 1,
                    )
                }

                PartyPhase.DRAWING -> {
                    if (j.id == u.drawer) {
                        PhoneView(
                            title = "You're drawing!",
                            text = "Draw this so the others can guess it. No letters or numbers!",
                            secret = u.title?.name.orEmpty(),
                            input = "draw",
                            status = status,
                            round = u.round,
                        )
                    } else {
                        PhoneView(
                            title = "Round ${u.round} of ${u.rounds}",
                            text = if (done) "Locked in!" else "What is ${name(u.drawer ?: "")} drawing? Watch the TV.",
                            actions = if (done) emptyList() else u.choices.mapIndexed { i, c -> PhoneAction("c$i", c) },
                            status = status,
                            round = u.round,
                        )
                    }
                }

                PartyPhase.REVEAL -> {
                    PhoneView(title = "Answer", text = u.reveal, status = status, round = u.round)
                }

                PartyPhase.OVER -> {
                    val winner = u.players.maxByOrNull { it.score }
                    PhoneView(
                        title = if (winner?.id == j.id) "You win!" else "Game over",
                        text = u.players.sortedByDescending { it.score }.joinToString("\n") { "${it.name}: ${it.score}" },
                        status = status,
                    )
                }
            }
        }

        override fun onCleared() {
            job?.cancel()
            host?.stop()
            host = null
            super.onCleared()
        }

        companion object {
            const val SERVICE_TYPE = "_benflixparty._udp"
            const val LABEL = "Benflix party"
            private const val QUESTION_MS = 20_000L
            private const val CLOSEST_MS = 25_000L
            private const val WRITE_MS = 90_000L
            private const val VOTE_MS = 40_000L
            private const val DRAW_MS = 70_000L
            private const val REVEAL_MS = 5_000L

            /** The first few sentences of a plot */
            fun firstSentences(
                text: String,
                n: Int,
            ): String =
                text
                    .split(Regex("(?<=[.!?])\\s+"))
                    .take(n)
                    .joinToString(" ")
                    .trim()

            /** Hides the title's own name inside a plot */
            fun hideName(
                text: String,
                name: String,
            ): String = text.replace(Regex(Regex.escape(name), RegexOption.IGNORE_CASE), "…")
        }
    }
