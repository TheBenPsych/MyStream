package com.github.damontecres.wholphin.ui.games.ember

import java.io.File

/**
 * Plays lots of pretend Ember Road runs with random choices, to check the story file never gets
 * stuck or crashes, and to see how long runs take and which endings people reach.
 */
object EmberSimulator {
    fun loadStory(): Story {
        val file =
            listOf("src/main/assets/ember/story.json", "app/src/main/assets/ember/story.json")
                .map { File(it) }
                .firstOrNull { it.exists() } ?: error("story.json not found")
        return Story.parse(file.readText())
    }

    data class Result(
        val ending: String,
        val days: Int,
        val score: Int,
        val actions: Int,
        val events: Int,
        val stops: Int,
        val party: Int,
        val secrets: Int,
    )

    private val roles = listOf("healer", "merchant", "ranger", "bard", "mage", "smith")

    fun play(
        story: Story,
        seed: Long,
        length: String,
        difficulty: String,
        kids: Boolean,
        role: String = roles[(seed % roles.size).toInt().let { if (it < 0) -it else it }],
    ): Result {
        val r = java.util.Random(seed)
        val folks = listOf("human", "elf", "dwarf", "fox")
        val members =
            (0 until 4).map { i ->
                Member(
                    name = "P$i",
                    folk = folks[r.nextInt(4)],
                    traits = story.traits.shuffled(r).take(2).map { it.id },
                    leader = i == 0,
                )
            }
        val e =
            EmberEngine.newRun(
                story,
                RunSetup(members = members, role = role, pet = story.names.pets[r.nextInt(4)].id, length = length, difficulty = difficulty, kids = kids, seed = seed),
            )
        var actions = 0
        var events = 0
        var stops = 0
        while (e.state.phase != Phase.OVER) {
            actions++
            check(actions < 5000) { "Run $seed got stuck in ${e.state.phase} (event ${e.state.event}, stop ${e.state.stop})" }
            when (e.state.phase) {
                Phase.TRAVEL -> {
                    // A sensible-ish player: camp when hurt or the egg is cold, eat less when food is low
                    e.setRations(if (e.state.food < 30) "little" else "normal")
                    e.setPace(if (e.state.hatchIn < 6) "hard" else "steady")
                    if ((e.active.any { it.sick || it.health <= 1 } || e.state.warmth <= 3) && r.nextInt(3) == 0) e.camp() else e.travel()
                }

                Phase.CAMP -> {
                    e.leaveCamp()
                }

                Phase.EVENT -> {
                    events++
                    val ev = e.currentEvent!!
                    val choices = e.choices(ev)
                    check(choices.isNotEmpty()) { "Event ${ev.id} had no choices" }
                    e.choose(choices[r.nextInt(choices.size)].first)
                }

                Phase.RESULT -> {
                    e.continueOn()
                }

                Phase.MINIGAME -> {
                    e.minigameDone(r.nextInt(11))
                }

                Phase.STOP -> {
                    stops++
                    val s = e.currentStop!!
                    // Poke around a bit: random verbs on random things, and try typing the secrets
                    repeat(6 + r.nextInt(8)) {
                        if (e.state.phase != Phase.STOP) return@repeat
                        val things = e.visibleThings(s)
                        val t = things[r.nextInt(things.size)]
                        val verb = Verb.entries[r.nextInt(Verb.entries.size)]
                        val with =
                            when (verb) {
                                Verb.USE -> if (e.state.items.isNotEmpty() && r.nextBoolean()) e.state.items[r.nextInt(e.state.items.size)] else null
                                Verb.CAST -> e.state.spells[r.nextInt(e.state.spells.size)]
                                else -> null
                            }
                        e.act(verb, t.id, with)
                        if (e.state.phase == Phase.RESULT) e.continueOn()
                        if (e.state.phase == Phase.MINIGAME) {
                            e.minigameDone(r.nextInt(11))
                            e.continueOn()
                        }
                    }
                    if (e.state.phase == Phase.STOP && r.nextInt(3) == 0) {
                        s.secrets.forEach { sec -> e.typed("say " + sec.words.joinToString(" ")) }
                        if (e.state.phase == Phase.RESULT) e.continueOn()
                    }
                    if (e.state.phase == Phase.STOP && s.market && r.nextBoolean()) {
                        e.openMarket()
                        if (e.state.food < 60) e.buy("food_sack")
                        e.closeMarket()
                    }
                    if (e.state.phase == Phase.STOP) e.leaveStop()
                }

                Phase.MARKET -> {
                    e.closeMarket()
                }

                Phase.FORK -> {
                    val opts = e.state.forkOptions
                    check(opts.isNotEmpty()) { "Fork with no roads" }
                    e.pickFork(opts[r.nextInt(opts.size)])
                }

                Phase.OVER -> {}
            }
        }
        return Result(e.state.ending!!, e.state.day, e.state.score, actions, events, stops, e.active.size, e.state.secrets)
    }

    fun report(
        story: Story,
        runs: Int = 300,
    ): String {
        val sb = StringBuilder()
        for (length in listOf("short", "medium", "long")) {
            for ((difficulty, kids) in listOf("easy" to false, "normal" to false, "hard" to false, "normal" to true)) {
                val results = (1..runs).map { play(story, it * 7919L + length.hashCode() + difficulty.hashCode() + (if (kids) 5 else 0), length, difficulty, kids) }
                val endings = results.groupingBy { it.ending }.eachCount().entries.sortedByDescending { it.value }
                sb.appendLine(
                    "$length $difficulty${if (kids) " KIDS" else ""}: days ${results.map { it.days }.average().toInt()} " +
                        "events ${"%.1f".format(results.map { it.events }.average())} stops ${"%.1f".format(results.map { it.stops }.average())} " +
                        "score ${results.map { it.score }.average().toInt()} party ${"%.1f".format(results.map { it.party }.average())}",
                )
                sb.appendLine("   " + endings.joinToString { "${it.key} ${it.value * 100 / runs}%" })
            }
        }
        return sb.toString()
    }
}
