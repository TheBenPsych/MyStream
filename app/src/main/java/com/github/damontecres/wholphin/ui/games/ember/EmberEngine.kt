package com.github.damontecres.wholphin.ui.games.ember

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * The rules of Ember Road. No Android code in here, so it can be tested on a computer.
 *
 * A run: your party carries the last dragon egg from Hearthvale to Ember Peak.
 * The road is a chain of lands ("legs"). Each day on the road uses food, may bring an event,
 * and halfway through each land there's a big obstacle. At the end of each land there's a stop
 * (a little scene to explore), then you choose which land to cross next.
 */

enum class Phase { TRAVEL, EVENT, RESULT, STOP, MARKET, FORK, CAMP, MINIGAME, OVER }

enum class Verb(
    val label: String,
) {
    LOOK("Look"),
    TALK("Talk"),
    TAKE("Take"),
    USE("Use"),
    CAST("Cast"),
}

@Serializable
data class Member(
    val name: String,
    /** human, elf, dwarf, fox */
    val folk: String,
    val traits: List<String>,
    /** 3 fine, 2 tired, 1 hurt; at 0 they leave the party */
    var health: Int = 3,
    var sick: Boolean = false,
    var gone: Boolean = false,
    var goneWhy: String = "",
    val leader: Boolean = false,
    /** Which pixel portrait (EmberArt) */
    val face: Int = 0,
)

@Serializable
data class RunSetup(
    val members: List<Member>,
    val role: String,
    val pet: String?,
    /** short, medium, long */
    val length: String = "medium",
    /** easy, normal, hard */
    val difficulty: String = "normal",
    val kids: Boolean = false,
    val cart: String = "wagon",
    val seed: Long,
)

/** One finished thing the UI shows: the story text plus the little "-10 food" changes */
@Serializable
data class Shown(
    val text: String,
    val changes: List<String> = emptyList(),
)

@Serializable
data class RunState(
    val version: Int = 1,
    val setup: RunSetup,
    var rng: Long,
    var phase: Phase = Phase.TRAVEL,
    var day: Int = 1,
    var food: Int = 0,
    var gold: Int = 0,
    var spirit: Int = 7,
    var cart: Int = 10,
    /** Wild magic: every spell makes the next more likely to go wrong; rest calms it */
    var wild: Int = 0,
    /** The egg's warmth, 0 to 10. At 0 it goes cold. */
    var warmth: Int = 8,
    /** Days until the egg hatches */
    var hatchIn: Int = 30,
    var herbs: Int = 2,
    var dragon: Int = 0,
    var score: Int = 0,
    var secrets: Int = 0,
    val members: MutableList<Member>,
    var petGone: Boolean = false,
    val items: MutableList<String> = mutableListOf(),
    val spells: MutableList<String> = mutableListOf(),
    val flags: MutableSet<String> = mutableSetOf(),
    val rep: MutableMap<String, Int> = mutableMapOf(),
    /** slow, steady, hard */
    var pace: String = "steady",
    /** little, normal, lots */
    var rations: String = "normal",
    /** Lands chosen so far, in order (the first is Hearthvale) */
    val route: MutableList<String> = mutableListOf(),
    var legs: Int = 5,
    var miles: Int = 0,
    var legMiles: Int = 50,
    var obstacleDone: Boolean = false,
    var forkOptions: List<String> = emptyList(),
    val seen: MutableSet<String> = mutableSetOf(),
    val usedResponses: MutableSet<String> = mutableSetOf(),
    val book: MutableSet<String> = mutableSetOf(),
    var weather: String = "clear",
    var event: String? = null,
    var stop: String? = null,
    var minigame: String? = null,
    /** Names picked for {a} and {b} in the current event */
    var a: String = "",
    var b: String = "",
    var shown: Shown? = null,
    /** Where to go after the RESULT text: travel, stop, fork, over, minigame */
    var next: String = "travel",
    var ending: String? = null,
    var priorityThisLeg: Boolean = false,
    var starving: Int = 0,
    /** Recent lines for the journal */
    val journal: MutableList<String> = mutableListOf(),
    var lastTouched: Long = 0L,
)

/** What happened when a phone or the TV typed a command */
data class TypedResult(
    val understood: Boolean,
    val shown: Shown,
)

class EmberEngine(
    val story: Story,
    val state: RunState,
) {
    // ---------------------------------------------------------------------------------------
    // Random numbers that survive a save (a tiny xorshift, its state is kept in the run)

    private fun nextLong(): Long {
        var x = state.rng
        if (x == 0L) x = 0x9E3779B97F4A7C15uL.toLong()
        x = x xor (x shl 13)
        x = x xor (x ushr 7)
        x = x xor (x shl 17)
        state.rng = x
        return x
    }

    fun rand(n: Int): Int = if (n <= 1) 0 else ((nextLong() ushr 1) % n).toInt()

    fun percent(p: Int): Boolean = rand(100) < p

    private fun <T> pick(list: List<T>): T = list[rand(list.size)]

    private fun <T> shuffled(list: List<T>): List<T> {
        val m = list.toMutableList()
        for (i in m.indices.reversed()) {
            val j = rand(i + 1)
            val t = m[i]
            m[i] = m[j]
            m[j] = t
        }
        return m
    }

    // ---------------------------------------------------------------------------------------
    // Handy reads

    val kids get() = state.setup.kids
    val role get() = state.setup.role
    val active get() = state.members.filter { !it.gone }
    val leader get() = state.members.first { it.leader }
    val land get() = story.land(state.route.last())
    val season: String
        get() {
            val len =
                when (state.setup.length) {
                    "short" -> 5
                    "long" -> 12
                    else -> 8
                }
            return listOf("spring", "summer", "autumn", "winter")[min(3, (state.day - 1) / len)]
        }
    val pet get() = state.setup.pet?.let { id -> story.names.pets.firstOrNull { it.id == id } }
    val hasPet get() = pet != null && !state.petGone
    val eggHere get() = "egg_sold" !in state.flags && "egg_stolen" !in state.flags
    val hatched get() = "hatched" in state.flags

    fun fill(text: String): String =
        text
            .replace("{leader}", leader.name)
            .replace("{a}", state.a.ifEmpty { leader.name })
            .replace("{b}", state.b.ifEmpty { leader.name })
            .replace("{pet}", pet?.name ?: "the cart mule")
            .replace("{land}", land.name)

    private fun textFor(
        normal: String,
        kidsVersion: String?,
    ) = fill(if (kids && kidsVersion != null) kidsVersion else normal)

    fun addJournal(line: String) {
        state.journal.add("Day ${state.day}: $line")
        while (state.journal.size > 40) state.journal.removeAt(0)
    }

    // ---------------------------------------------------------------------------------------
    // Starting a run

    companion object {
        fun legsFor(length: String) =
            when (length) {
                "short" -> 3
                "long" -> 7
                else -> 5
            }

        fun milesFactor(length: String) =
            when (length) {
                "short" -> 0.8
                "long" -> 1.4
                else -> 1.0
            }

        fun newRun(
            story: Story,
            setup: RunSetup,
            now: Long = 0L,
        ): EmberEngine {
            val state =
                RunState(
                    setup = setup,
                    rng = setup.seed xor 0x5DEECE66DL,
                    members = setup.members.map { it.copy() }.toMutableList(),
                )
            val e = EmberEngine(story, state)
            e.begin(now)
            return e
        }
    }

    private fun begin(now: Long) {
        val s = state
        s.legs = legsFor(s.setup.length)
        // Roughly how many days the trip really takes (with the usual delays)
        val expected = s.legs * 50 * milesFactor(s.setup.length) / 12.0 * 1.35
        s.food = (expected * (active.size * 2 + 1) * 0.9).roundToInt()
        s.gold = 30
        s.spirit = 7
        s.herbs = 2
        s.items.add("lantern")
        s.items.add("rope")
        s.spells.addAll(listOf("light", "warm", "calm"))
        when (role) {
            "merchant" -> s.gold += 40
            "healer" -> s.herbs += 3
            "mage" -> s.spells.addAll(listOf("float", "speak"))
            "smith" -> s.items.add("hammer")
            "ranger" -> s.items.add("bow")
            "bard" -> s.items.add("lute")
        }
        when (s.setup.difficulty) {
            "easy" -> {
                s.food += 40
                s.gold += 15
            }

            "hard" -> {
                s.food = (s.food * 0.8).roundToInt()
                s.gold -= 10
            }
        }
        if (kids) s.food += 40
        // A bit longer than the usual trip, so a steady party makes it before it hatches
        s.hatchIn = (expected * 1.2).roundToInt() + 2
        s.route.add("hearthvale")
        startLeg()
        s.lastTouched = now
        addJournal("Set out from Hearthvale with the dragon egg.")
    }

    private fun startLeg() {
        state.miles = 0
        state.legMiles = (land.miles * milesFactor(state.setup.length)).roundToInt()
        state.obstacleDone = false
        state.priorityThisLeg = false
        state.weather = pick(land.weather)
        state.phase = Phase.TRAVEL
    }

    val legIndex get() = state.route.size - 1
    val onFinalLeg get() = land.tag == "final"

    // ---------------------------------------------------------------------------------------
    // Conditions ("if")

    private fun num(name: String): Int =
        when {
            name.startsWith("rep.") -> state.rep[name.removePrefix("rep.")] ?: 0
            else ->
                when (name) {
                    "food" -> state.food
                    "gold" -> state.gold
                    "spirit" -> state.spirit
                    "cart" -> state.cart
                    "wild" -> state.wild
                    "warmth" -> state.warmth
                    "herbs" -> state.herbs
                    "day" -> state.day
                    "party" -> active.size
                    "hurt" -> active.count { it.health <= 1 }
                    "sick" -> active.count { it.sick }
                    "dragon" -> state.dragon
                    "gone" -> state.members.count { it.gone }
                    "leg" -> legIndex
                    "legsLeft" -> state.legs - 1 - legIndex
                    "secrets" -> state.secrets
                    "hatch" -> state.hatchIn
                    "items" -> state.items.size
                    "runes" -> state.flags.count { it.startsWith("rune_") }
                    "progress" -> if (state.legMiles == 0) 0 else state.miles * 100 / state.legMiles
                    else -> 0
                }
        }

    private fun strings(e: JsonElement): List<String> =
        when (e) {
            is JsonArray -> e.mapNotNull { (it as? JsonPrimitive)?.content }
            is JsonPrimitive -> listOf(e.content)
            else -> emptyList()
        }

    fun check(cond: JsonObject): Boolean {
        for ((key, value) in cond) {
            val ok =
                when (key) {
                    "has" -> strings(value).all { it in state.items }
                    "lacks" -> strings(value).none { it in state.items }
                    "knows" -> strings(value).all { it in state.spells }
                    "flag" -> strings(value).all { it in state.flags }
                    "noflag" -> strings(value).none { it in state.flags }
                    "role" -> role in strings(value)
                    "notrole" -> role !in strings(value)
                    "folk" -> strings(value).any { f -> active.any { it.folk == f } }
                    "trait" -> strings(value).any { t -> active.any { t in it.traits } }
                    "pet" -> {
                        val wanted = (value as? JsonPrimitive)?.booleanOrNull
                        if (wanted != null) hasPet == wanted else hasPet && state.setup.pet in strings(value)
                    }
                    "kids" -> kids == (value as? JsonPrimitive)?.booleanOrNull
                    "season" -> season in strings(value)
                    "weather" -> state.weather in strings(value)
                    "egg" -> (value as? JsonPrimitive)?.booleanOrNull == eggHere
                    "hatched" -> (value as? JsonPrimitive)?.booleanOrNull == hatched
                    "petlost" -> (value as? JsonPrimitive)?.booleanOrNull == (pet != null && state.petGone)
                    "difficulty" -> state.setup.difficulty in strings(value)
                    "length" -> state.setup.length in strings(value)
                    "chance" -> percent((value as? JsonPrimitive)?.intOrNull ?: 50)
                    "any" -> (value as? JsonArray)?.any { it is JsonObject && check(it) } ?: true
                    "not" -> !(value is JsonObject && check(value))
                    else -> compare(key, value)
                }
            if (!ok) return false
        }
        return true
    }

    private fun compare(
        key: String,
        value: JsonElement,
    ): Boolean {
        val n = (value as? JsonPrimitive)?.intOrNull ?: return true
        for (op in listOf(">=", "<=", ">", "<", "=")) {
            if (key.endsWith(op)) {
                val v = num(key.removeSuffix(op))
                return when (op) {
                    ">=" -> v >= n
                    "<=" -> v <= n
                    ">" -> v > n
                    "<" -> v < n
                    else -> v == n
                }
            }
        }
        return true
    }

    // ---------------------------------------------------------------------------------------
    // Effects ("fx")

    private fun clampAll() {
        state.spirit = state.spirit.coerceIn(0, 10)
        state.cart = state.cart.coerceIn(0, 10)
        state.wild = state.wild.coerceIn(0, 10)
        state.warmth = state.warmth.coerceIn(0, 10)
        state.food = max(0, state.food)
        state.gold = max(0, state.gold)
        state.herbs = max(0, state.herbs)
        state.dragon = state.dragon.coerceIn(-5, 5)
        state.rep.keys.toList().forEach { state.rep[it] = (state.rep[it] ?: 0).coerceIn(-5, 5) }
    }

    private fun signed(
        n: Int,
        what: String,
    ) = (if (n > 0) "+$n" else "$n") + " " + what

    /** Applies "fx" and returns the short change lines ("+5 gold") */
    fun apply(
        fx: JsonObject,
        why: String = "",
    ): List<String> {
        val out = mutableListOf<String>()
        for ((key, value) in fx) {
            val n = (value as? JsonPrimitive)?.intOrNull ?: 0
            when {
                key == "food" -> {
                    val change = if (kids && n < 0) n / 2 else n
                    state.food += change
                    out += signed(change, "food")
                }

                key == "gold" -> {
                    val before = state.gold
                    state.gold = max(0, state.gold + n)
                    out += signed(state.gold - before, "gold")
                }

                key == "spirit" -> {
                    state.spirit += n
                    out += if (n > 0) "Spirits up" else "Spirits down"
                }

                key == "cart" -> {
                    state.cart += n
                    out += if (n > 0) "Cart mended" else "Cart damaged"
                }

                key == "wild" -> {
                    state.wild += n
                    out += if (n > 0) "Magic feels wilder" else "Magic calms"
                }

                key == "warmth" -> {
                    state.warmth += n
                    out += if (n > 0) "The egg warms" else "The egg cools"
                }

                key == "herbs" -> {
                    state.herbs = max(0, state.herbs + n)
                    out += signed(n, "herbs")
                }

                key == "dragon" -> {
                    state.dragon += n
                    out += if (n > 0) "The dragon will remember this kindly" else "The dragon will remember this"
                }

                key == "hatch" -> {
                    state.hatchIn += n
                    out += if (n < 0) "The egg stirs (hatching sooner)" else "The egg settles"
                }

                key == "score" -> {
                    state.score += n
                }

                key == "secrets" -> {
                    state.secrets += n
                    out += "Secret found!"
                }

                key == "miles" -> {
                    state.miles = (state.miles + n).coerceIn(0, state.legMiles)
                    out += if (n > 0) "Shortcut: $n miles" else "Lost ${-n} miles"
                }

                key == "days" -> {
                    repeat(n) { passDay(quiet = true) }
                    out += if (n == 1) "A day passes" else "$n days pass"
                }

                key.startsWith("rep.") -> {
                    val group = key.removePrefix("rep.")
                    state.rep[group] = (state.rep[group] ?: 0) + n
                    out += (if (n > 0) "Liked by " else "Disliked by ") + groupName(group)
                }

                key == "item" -> {
                    for (s in strings(value)) {
                        val id = s.drop(1)
                        val name = story.item(id)?.name ?: id
                        if (s.startsWith("-")) {
                            if (state.items.remove(id)) out += "Lost: $name"
                        } else if (id !in state.items) {
                            state.items.add(id)
                            out += "Got: $name"
                        }
                    }
                }

                key == "spell" -> {
                    for (id in strings(value)) {
                        if (id !in state.spells) {
                            state.spells.add(id)
                            out += "Learned a spell: ${story.spell(id)?.name ?: id}"
                        }
                    }
                }

                key == "flag" -> {
                    state.flags.addAll(strings(value))
                }

                key == "unflag" -> {
                    state.flags.removeAll(strings(value).toSet())
                }

                key == "creature" -> {
                    for (id in strings(value)) {
                        if (state.book.add(id)) out += "New in the creature book: ${story.creature(id)?.name ?: id}"
                    }
                }

                key == "hurt" -> {
                    val count = if (value is JsonPrimitive && value.content == "all") active.size else n
                    repeat(count) {
                        val m = active.filter { it.health > (if (kids) 1 else 0) }.randomOrNullBy() ?: return@repeat
                        m.health -= 1
                        out += "${m.name} is hurt"
                        checkGone(m, "too hurt to go on")
                    }
                }

                key == "sick" -> {
                    repeat(n) {
                        val m = active.filter { !it.sick }.randomOrNullBy() ?: return@repeat
                        m.sick = true
                        out += "${m.name} feels sick"
                    }
                }

                key == "heal" -> {
                    val list = if (value is JsonPrimitive && value.content == "all") active else active.sortedBy { it.health }.take(n)
                    list.forEach {
                        if (it.health < 3) it.health += 1
                        it.sick = false
                    }
                    out += if (list.size > 1) "Everyone feels better" else list.firstOrNull()?.let { "${it.name} feels better" } ?: ""
                }

                key == "lose" -> {
                    val m = active.filter { !it.leader }.randomOrNullBy()
                    if (m != null) {
                        if (kids) {
                            m.health = 1
                            out += "${m.name} needs a rest in the cart"
                        } else {
                            leave(m, (fx["why"] as? JsonPrimitive)?.content ?: why.ifEmpty { "had to stay behind" })
                            out += "${m.name} left the party"
                        }
                    }
                }

                key == "join" -> {
                    if (active.size < 6) {
                        val folk = strings(value).firstOrNull() ?: "human"
                        val m = newMember(folk)
                        state.members.add(m)
                        out += "${m.name} the ${folkName(folk)} joined the party"
                    }
                }

                key == "rescue" -> {
                    val m = state.members.firstOrNull { it.gone }
                    if (m != null) {
                        m.gone = false
                        m.health = 2
                        m.sick = false
                        out += "${m.name} is back with the party!"
                    }
                }

                key == "petLose" -> {
                    if (hasPet) {
                        state.petGone = true
                        out += "${pet?.name} wandered off"
                    }
                }

                key == "petBack" -> {
                    if (pet != null && state.petGone) {
                        state.petGone = false
                        out += "${pet?.name} is back!"
                    }
                }

                key == "egg" -> {
                    when ((value as? JsonPrimitive)?.content) {
                        "stolen" -> {
                            if (eggHere) {
                                state.flags.add("egg_stolen")
                                out += "The egg was taken!"
                            }
                        }

                        "back" -> {
                            if ("egg_stolen" in state.flags) {
                                state.flags.remove("egg_stolen")
                                out += "The egg is back with you!"
                            }
                        }

                        "sold" -> {
                            state.flags.add("egg_sold")
                            out += "You sold the egg"
                        }
                    }
                }

                key == "ending" -> {
                    state.ending = (value as JsonPrimitive).content
                }

                key == "stop" -> {
                    state.stop = (value as JsonPrimitive).content
                    state.next = "stop"
                }

                key == "minigame" -> {
                    state.minigame = (value as JsonPrimitive).content
                    state.next = "minigame"
                }

                key == "event" -> {
                    // Leads straight into another event
                    state.event = (value as JsonPrimitive).content
                    state.next = "event"
                }

                key == "why" -> {}
            }
        }
        clampAll()
        return out.filter { it.isNotBlank() }
    }

    private fun <T> List<T>.randomOrNullBy(): T? = if (isEmpty()) null else this[rand(size)]

    fun groupName(id: String) =
        when (id) {
            "guard" -> "the King's Guard"
            "wood" -> "the elves of the wood"
            "goblin" -> "the Goblin Market"
            "wizard" -> "the Wizards' Council"
            "dwarf" -> "the mountain dwarves"
            "river" -> "the river folk"
            else -> id
        }

    fun folkName(folk: String) =
        when (folk) {
            "elf" -> "elf"
            "dwarf" -> "dwarf"
            "fox" -> "fox-folk"
            else -> "human"
        }

    fun newMember(folk: String): Member {
        val names =
            when (folk) {
                "elf" -> story.names.elf
                "dwarf" -> story.names.dwarf
                "fox" -> story.names.fox
                else -> story.names.human
            }
        val used = state.members.map { it.name }.toSet()
        val name = names.filter { it !in used }.ifEmpty { names }.let { it[rand(it.size)] }
        val traits = shuffled(story.traits.map { it.id }).take(2)
        return Member(name = name, folk = folk, traits = traits, face = rand(8))
    }

    private fun checkGone(
        m: Member,
        why: String,
    ) {
        if (m.health <= 0) {
            if (kids || m.leader) {
                m.health = 1
            } else {
                leave(m, why)
            }
        }
    }

    private fun leave(
        m: Member,
        why: String,
    ) {
        if (m.leader) return
        m.gone = true
        m.goneWhy = why
        m.health = 0
        addJournal("${m.name} left the party ($why).")
    }

    // ---------------------------------------------------------------------------------------
    // Travelling

    private fun milesPerDay(): Int {
        var m =
            when (state.pace) {
                "slow" -> 8
                "hard" -> 16
                else -> 12
            }
        when (state.weather) {
            "storm", "snow" -> m -= 4
            "rain", "fog" -> m -= 2
        }
        if (state.cart <= 3) m -= 3
        if (state.food == 0) m -= 2
        if ("ranger" == role) m += 1
        return max(3, m)
    }

    private fun eatPerDay(): Int {
        val each =
            when (state.rations) {
                "little" -> 1
                "lots" -> 3
                else -> 2
            }
        return active.size * each + (if (hasPet) 1 else 0)
    }

    /** One day goes by: food, health, weather, the egg. Returns lines worth telling. */
    private fun passDay(quiet: Boolean = false): List<String> {
        val lines = mutableListOf<String>()
        state.day++
        val eat = eatPerDay()
        if (state.food >= eat) {
            state.food -= eat
            state.starving = 0
        } else {
            state.food = 0
            state.starving++
            state.spirit -= 1
            lines += "There isn't enough food. Everyone is hungry."
            if (state.starving >= 2 && percent(50)) {
                apply(JsonObject(mapOf("hurt" to JsonPrimitive(1))), "too hungry to go on").let { lines += it }
            }
        }
        // Weather changes now and then
        if (percent(35)) state.weather = pick(seasonWeather())
        // Tired, hungry or cold people get sick
        var sickChance =
            2 + (if (state.rations == "little") 3 else 0) + (if (state.pace == "hard") 3 else 0) +
                (if (state.weather in listOf("rain", "snow", "storm")) 2 else 0) - (if (role == "healer") 2 else 0)
        if (kids) sickChance /= 2
        for (m in active) {
            if (!m.sick && percent(sickChance)) {
                m.sick = true
                lines += "${m.name} has caught a fever."
            } else if (m.sick && percent(30)) {
                m.health -= 1
                lines += "${m.name}'s fever is getting worse."
                checkGone(m, "too sick to travel")
            }
        }
        if (state.pace == "hard" && percent(25)) {
            state.spirit -= 1
        }
        if (state.rations == "lots" && percent(30)) state.spirit += 1
        // The egg
        if (eggHere && !hatched) {
            val cool = (if (land.cold || season == "winter") 1 else 0) + (if (state.weather in listOf("snow", "storm")) 1 else 0)
            if (cool > 0 && percent(if (kids) 30 else 60)) state.warmth -= cool
            if (cool == 0 && percent(20)) state.warmth -= 1
            state.hatchIn -= 1
            if (state.warmth <= 0 && "egg_cold" !in state.flags) {
                state.flags.add("egg_cold")
                lines += "The egg has gone cold and quiet."
            }
            if (state.warmth > 0) state.flags.remove("egg_cold")
            if (state.hatchIn <= 0 && state.warmth > 0) {
                state.flags.add("hatched")
                state.score += 100
                lines += "CRACK! The egg hatches on the road! A tiny dragon blinks up at you. You name her Ember."
                addJournal("The egg hatched on the road. Hello, Ember!")
            }
        }
        if (state.spirit <= 1 && !kids && active.size > 1 && percent(15)) {
            val m = active.filter { !it.leader }.randomOrNullBy()
            if (m != null) {
                leave(m, "lost hope and went home")
                lines += "${m.name} has lost hope and turns back for home."
            }
        }
        clampAll()
        return if (quiet) emptyList() else lines
    }

    private fun seasonWeather(): List<String> =
        when (season) {
            "summer" -> land.weather + listOf("clear", "hot")
            "autumn" -> land.weather + listOf("rain", "fog", "cloudy")
            "winter" -> land.weather.map { if (it == "rain") "snow" else it } + listOf("snow", "cloudy")
            else -> land.weather
        }

    /** "Keep going": one day on the road */
    fun travel(): Shown {
        if (state.phase != Phase.TRAVEL) return Shown("")
        val lines = passDay().toMutableList()
        state.miles = min(state.legMiles, state.miles + milesPerDay())
        val endNow = checkFailure()
        if (endNow != null) return endNow
        // What happens today?
        val ev =
            when {
                state.miles >= state.legMiles -> null
                !state.obstacleDone && state.miles >= state.legMiles / 2 -> pickObstacle()
                else -> pickEvent()
            }
        if (ev != null) {
            startEvent(ev)
            return Shown(lines.joinToString("\n"))
        }
        if (state.miles >= state.legMiles) {
            arriveAtStop()
            lines += "You reach ${story.stop(state.stop!!).name}."
            return Shown(lines.joinToString("\n"))
        }
        lines += quietDay()
        return Shown(lines.joinToString("\n").trim())
    }

    private fun quietDay(): String {
        val w =
            when (state.weather) {
                "rain" -> "Rain drums on the cart cover."
                "storm" -> "Thunder rolls over ${land.name}."
                "snow" -> "Snow falls softly on the road."
                "fog" -> "Thick fog hides the road ahead."
                "hot" -> "The sun beats down."
                "cloudy" -> "Grey clouds drift overhead."
                else -> "A fine day for travelling."
            }
        val left = state.legMiles - state.miles
        return "$w You cover ${milesPerDay()} miles. $left to go through ${land.name}."
    }

    private fun eventFits(ev: StoryEvent): Boolean {
        if (ev.once && ev.id in state.seen) return false
        if (kids && ev.scary) return false
        if ("*" !in ev.lands && state.route.last() !in ev.lands) return false
        if (ev.seasons.isNotEmpty() && season !in ev.seasons) return false
        return check(ev.cond)
    }

    private fun weighted(list: List<StoryEvent>): StoryEvent? {
        if (list.isEmpty()) return null
        val total = list.sumOf { max(1, it.weight) }
        var r = rand(total)
        for (e in list) {
            r -= max(1, e.weight)
            if (r < 0) return e
        }
        return list.last()
    }

    private fun pickObstacle(): StoryEvent? {
        state.obstacleDone = true
        val list = story.events.filter { it.obstacle && it.lands.contains(state.route.last()) && eventFits(it) }
        return weighted(list) ?: weighted(story.events.filter { it.obstacle && it.lands.contains("*") && eventFits(it) })
    }

    private fun pickEvent(): StoryEvent? {
        if (!state.priorityThisLeg) {
            val p = story.events.filter { it.priority && !it.obstacle && eventFits(it) }
            if (p.isNotEmpty()) {
                state.priorityThisLeg = true
                return weighted(p)
            }
        }
        val chance = 34 + land.danger * 6 + (if (state.setup.length == "short") 10 else 0)
        if (!percent(chance)) return null
        val list = story.events.filter { !it.priority && !it.obstacle && eventFits(it) }
        return weighted(list)
    }

    private fun startEvent(ev: StoryEvent) {
        state.seen.add(ev.id)
        val others = active.filter { !it.leader }.ifEmpty { active }
        val two = shuffled(others)
        state.a = two.getOrNull(0)?.name ?: leader.name
        state.b = two.getOrNull(1)?.name ?: state.a
        state.event = ev.id
        state.phase = Phase.EVENT
        ev.creature?.let { state.book.add(it) }
    }

    /** For tests and the UI: start a specific event */
    fun forceEvent(id: String) = startEvent(story.event(id))

    val currentEvent get() = state.event?.let { story.event(it) }

    fun eventText(ev: StoryEvent) = textFor(ev.text, ev.kidsText)

    /** The choices you can see right now (hidden ones whose "if" isn't met are left out) */
    fun choices(ev: StoryEvent): List<Pair<Int, Choice>> = ev.choices.withIndex().filter { check(it.value.cond) }.map { it.index to it.value }

    fun choiceLabel(c: Choice): String {
        val base = fill(if (kids && c.kidsLabel != null) c.kidsLabel else c.label)
        return if (c.spell != null) "$base ✦" else base
    }

    /** Pick a choice in the current event */
    fun choose(index: Int): Shown {
        val ev = currentEvent ?: return Shown("")
        val c = ev.choices.getOrNull(index) ?: return Shown("")
        state.next = "travel"
        val out = mutableListOf<String>()
        val text: String
        if (c.spell != null && percent(backfireChance())) {
            val bf = pick(story.backfires)
            text = "Your ${story.spell(c.spell)?.name ?: "spell"} goes wrong! " + textFor(bf.text, bf.kidsText)
            state.wild += 1
            out += apply(bf.fx)
        } else if (c.roll != null) {
            if (c.spell != null) state.wild += 1
            val win = percent(rollChance(c.roll))
            val o = if (win) c.roll.win else c.roll.lose
            text = (if (c.text.isNotBlank()) textFor(c.text, c.kidsText) + " " else "") + textFor(o.text, o.kidsText)
            out += apply(c.fx)
            out += apply(o.fx)
        } else {
            if (c.spell != null) state.wild += 1
            text = textFor(c.text, c.kidsText)
            out += apply(c.fx)
        }
        clampAll()
        addJournal(ev.title)
        return finishResult(Shown(text, out))
    }

    fun rollChance(r: Roll): Int {
        var p = r.chance
        for ((k, v) in r.bonus) {
            val parts = k.split(':', limit = 2)
            p +=
                when (parts[0]) {
                    "role" -> if (role == parts.getOrNull(1)) v else 0
                    "folk" -> if (active.any { it.folk == parts.getOrNull(1) }) v else 0
                    "item" -> if (parts.getOrNull(1) in state.items) v else 0
                    "trait" -> if (active.any { parts.getOrNull(1) in it.traits }) v else 0
                    "pet" -> if (hasPet) v else 0
                    "spirit" -> (state.spirit - 5) * v
                    else -> if (parts[0].startsWith("rep.")) (state.rep[parts[0].removePrefix("rep.")] ?: 0) * v else 0
                }
        }
        p +=
            when (state.setup.difficulty) {
                "easy" -> 15
                "hard" -> -10
                else -> 0
            }
        if (kids) p += 25
        return p.coerceIn(5, 95)
    }

    fun backfireChance(): Int {
        var p = 12 + state.wild * 7
        if (role == "mage") p -= 12
        if (kids) p -= 8
        return p.coerceIn(3, 80)
    }

    private fun finishResult(shown: Shown): Shown {
        state.shown = shown
        state.phase = Phase.RESULT
        if (state.ending != null) state.next = "over"
        checkFailure(peek = true)?.let { state.next = "over" }
        return shown
    }

    /** "Continue" after a result */
    fun continueOn(): Shown? {
        state.shown = null
        val next = state.next
        state.next = "travel"
        when {
            next == "over" || state.ending != null -> {
                return endRun()
            }

            next == "event" -> {
                val id = state.event
                if (id != null) {
                    startEvent(story.event(id))
                    return null
                }
            }

            next == "stop" -> {
                state.phase = Phase.STOP
                return null
            }

            next == "minigame" -> {
                state.phase = Phase.MINIGAME
                return null
            }

            next == "fork" -> {
                openFork()
                return null
            }

            next == "back" -> {
                state.phase = Phase.STOP
                return null
            }
        }
        state.event = null
        when {
            state.stop != null -> state.phase = Phase.STOP
            state.miles >= state.legMiles -> arriveAtStop()
            else -> state.phase = Phase.TRAVEL
        }
        return null
    }

    // ---------------------------------------------------------------------------------------
    // Camp

    fun camp(): Shown {
        if (state.phase != Phase.TRAVEL) return Shown("")
        val lines = passDay().toMutableList()
        val out = mutableListOf<String>()
        active.forEach {
            if (it.health < 3) it.health += 1
            if (it.sick) {
                if (state.herbs > 0) {
                    state.herbs--
                    it.sick = false
                    out += "${it.name} took herbs for the fever"
                } else if (percent(35)) {
                    it.sick = false
                }
            }
        }
        state.wild = max(0, state.wild - 2)
        state.spirit += 1
        if (eggHere && !hatched) state.warmth += 3
        out += "Everyone rests"
        if (eggHere && !hatched) out += "The egg warms by the fire"
        if (state.wild == 0) out += "Magic is calm"
        clampAll()
        state.phase = Phase.CAMP
        val story = pick(this.story.campfire)
        lines += "You make camp. " + fill(story.text)
        state.shown = Shown(lines.joinToString("\n"), out)
        addJournal("Made camp in ${land.name}.")
        return state.shown!!
    }

    val campGenre get() = story.campfire.firstOrNull { state.shown?.text?.contains(fill(it.text)) == true }?.genre ?: "Fantasy"

    fun leaveCamp() {
        state.shown = null
        val end = checkFailure()
        if (end != null) return
        state.phase = Phase.TRAVEL
    }

    fun setPace(p: String) {
        state.pace = p
    }

    fun setRations(r: String) {
        state.rations = r
    }

    /** Using herbs from the bag */
    fun useHerbs(): Shown {
        val m = active.filter { it.sick || it.health < 3 }.minByOrNull { it.health }
        if (state.herbs <= 0 || m == null) return Shown("Nobody needs herbs right now.")
        state.herbs--
        m.sick = false
        m.health = min(3, m.health + 1)
        return Shown("${m.name} chews the bitter herbs and feels much better.", listOf("-1 herbs"))
    }

    // ---------------------------------------------------------------------------------------
    // Stops (text adventure scenes)

    private fun arriveAtStop() {
        val stops = land.stops
        state.stop = if (stops.isEmpty()) null else stops[rand(stops.size)]
        state.event = null
        if (state.stop == null) {
            openFork()
        } else {
            state.phase = Phase.STOP
            addJournal("Arrived at ${story.stop(state.stop!!).name}.")
        }
    }

    val currentStop get() = state.stop?.let { story.stop(it) }

    fun stopIntro(s: Stop) = textFor(s.intro, s.kidsIntro)

    fun visibleThings(s: Stop) = s.things.filter { check(it.cond) }

    fun hasVerb(
        t: Thing,
        v: Verb,
    ): Boolean =
        when (v) {
            Verb.LOOK -> true
            Verb.TALK -> t.talk.isNotEmpty()
            Verb.TAKE -> t.take.isNotEmpty()
            Verb.USE -> t.use.isNotEmpty()
            Verb.CAST -> true
        }

    /** Do something in a stop: Look at the well, Use rope on the well, Cast Light on the door */
    fun act(
        verb: Verb,
        thingId: String,
        with: String? = null,
    ): Shown {
        val s = currentStop ?: return Shown("")
        val t = s.things.firstOrNull { it.id == thingId } ?: return Shown("There's nothing like that here.")
        val list =
            when (verb) {
                Verb.LOOK -> t.look
                Verb.TALK -> t.talk
                Verb.TAKE -> t.take
                Verb.USE -> t.use
                Verb.CAST -> t.cast
            }
        if (verb == Verb.CAST && with != null && with !in state.spells) return Shown("You don't know that spell.")
        if (verb == Verb.USE && with != null && with !in state.items) return Shown("You don't have that.")
        val key = { r: Response -> "${s.id}/${t.id}/${verb.name}/${list.indexOf(r)}" }
        val match =
            list.firstOrNull { r ->
                (r.with == null || r.with == with) && check(r.cond) && !(r.once && key(r) in state.usedResponses && r.after == null)
            }
        if (verb == Verb.CAST && with != null) state.wild += 1
        if (match == null) {
            return Shown(
                when (verb) {
                    Verb.LOOK -> "It's just ${t.name}."
                    Verb.TALK -> if (t.person) "${t.name.replaceFirstChar { it.uppercase() }} has nothing more to say." else "${t.name.replaceFirstChar { it.uppercase() }} doesn't answer. It's not much of a talker."
                    Verb.TAKE -> if (t.person) "That would be rude." else "You can't take that."
                    Verb.USE -> if (with != null) "Using ${story.item(with)?.name ?: with} on ${t.name} doesn't do anything." else "You aren't sure how to use that."
                    Verb.CAST -> castPlain(with, t.name)
                },
            )
        }
        if (match.once && key(match) in state.usedResponses) {
            return Shown(fill(match.after ?: match.text))
        }
        // Spells cast on things can go wrong too
        if (verb == Verb.CAST && percent(backfireChance())) {
            val bf = pick(story.backfires)
            val changes = apply(bf.fx)
            return Shown("Your spell goes wrong! " + textFor(bf.text, bf.kidsText), changes)
        }
        if (match.once) state.usedResponses.add(key(match))
        val changes = apply(match.fx)
        val shown = Shown(textFor(match.text, match.kidsText), changes)
        if (state.ending != null) {
            state.shown = shown
            state.phase = Phase.RESULT
            state.next = "over"
        } else if (state.next == "minigame" || state.next == "event") {
            state.shown = shown
            state.phase = Phase.RESULT
        } else {
            state.next = "travel"
        }
        return shown
    }

    private fun castPlain(
        spellId: String?,
        on: String,
    ): String {
        val sp = spellId?.let { story.spell(it) } ?: return "Cast which spell?"
        if (percent(backfireChance())) {
            val bf = pick(story.backfires)
            apply(bf.fx)
            return "Your ${sp.name} spell goes wrong! " + textFor(bf.text, bf.kidsText)
        }
        apply(sp.fx)
        return fill(sp.plain.replace("{on}", on))
    }

    /** Leaving a stop: on to the next fork in the road (or the end) */
    fun leaveStop() {
        val s = currentStop
        state.stop = null
        if (s?.final == true) {
            endRun()
            return
        }
        openFork()
    }

    private fun openFork() {
        if (legIndex >= state.legs - 1) {
            endRun()
            return
        }
        val used = state.route.toSet()
        if (legIndex == state.legs - 2) {
            state.forkOptions = story.lands.filter { it.tag == "final" }.map { it.id }
        } else {
            val pool = story.lands.filter { it.id !in used && it.tag in listOf("safe", "danger", "strange") }
            // Try to offer one of each kind of road
            val byTag = pool.groupBy { it.tag }
            val choices = mutableListOf<Land>()
            for (tag in shuffled(listOf("safe", "danger", "strange"))) {
                byTag[tag]?.let { choices.add(it[rand(it.size)]) }
            }
            state.forkOptions = choices.take(3).map { it.id }.ifEmpty { pool.take(2).map { it.id } }
        }
        state.phase = Phase.FORK
    }

    fun forkLabel(id: String): String =
        when (story.land(id).tag) {
            "safe" -> "safe but long"
            "danger" -> "short but dangerous"
            "strange" -> "the strange road"
            "final" -> "the last climb"
            else -> ""
        }

    fun pickFork(id: String) {
        if (state.phase != Phase.FORK || id !in state.forkOptions) return
        state.route.add(id)
        state.forkOptions = emptyList()
        startLeg()
        addJournal("Took the road into ${land.name}.")
    }

    // ---------------------------------------------------------------------------------------
    // Typing commands ("look under bridge", "use rope on well", "say ithra")

    private val verbWords =
        mapOf(
            Verb.LOOK to listOf("look", "l", "examine", "x", "inspect", "read", "search", "check", "see", "study", "watch"),
            Verb.TALK to listOf("talk", "ask", "speak", "greet", "chat", "hello", "hi", "tell"),
            Verb.TAKE to listOf("take", "get", "grab", "pick", "steal", "collect", "lift"),
            Verb.USE to listOf("use", "put", "give", "open", "push", "pull", "light", "climb", "tie", "pour", "play", "show", "turn", "ring", "unlock", "drink", "eat", "feed", "pay", "move"),
            Verb.CAST to listOf("cast", "magic", "spell"),
        )

    private val fillerWords = setOf("the", "a", "an", "at", "to", "on", "in", "into", "with", "under", "behind", "of", "my", "up", "around", "inside", "about", "for", "from", "please")

    fun typed(raw: String): TypedResult {
        val text = raw.lowercase().replace(Regex("[^a-z0-9' ]"), " ").trim().replace(Regex(" +"), " ")
        if (text.isBlank()) return TypedResult(false, Shown("Type something to do, like \"look at the well\"."))
        val s = currentStop
        // Secret words first (they only work by typing)
        if (s != null) {
            val words = text.split(' ').toSet()
            for ((i, sec) in s.secrets.withIndex()) {
                val k = "${s.id}/secret/$i"
                if (sec.words.all { w -> w in words || text.contains(w) } && check(sec.cond)) {
                    if (k in state.usedResponses) return TypedResult(true, Shown("You've already done that here."))
                    state.usedResponses.add(k)
                    val changes = apply(sec.fx)
                    val shown = Shown(fill(sec.text), changes)
                    if (state.ending != null) {
                        state.shown = shown
                        state.phase = Phase.RESULT
                        state.next = "over"
                    }
                    return TypedResult(true, shown)
                }
            }
        }
        val words = text.split(' ').filter { it !in fillerWords }
        if (words.isEmpty()) return TypedResult(false, Shown("Hmm?"))
        val first = words.first()
        if (first in listOf("help", "verbs", "commands")) {
            return TypedResult(true, Shown("Try things like: look at the statue, talk to the miller, take the key, use rope on well, cast light on door. Some secrets only open to typed words."))
        }
        if (s == null) return TypedResult(false, Shown("You can type commands when you're at a stop."))
        var verb = verbWords.entries.firstOrNull { first in it.value }?.key
        var rest = if (verb != null) words.drop(1) else words
        if (verb == null) verb = Verb.LOOK
        // "pick up" etc.
        rest = rest.filter { it != "up" }
        val things = visibleThings(s)
        fun matchThing(ws: List<String>): Thing? {
            val joined = ws.joinToString(" ")
            return things.firstOrNull { t -> (listOf(t.name) + t.aliases).any { n -> joined.contains(n.lowercase().removePrefix("the ").removePrefix("a ")) } }
                ?: things.firstOrNull { t -> ws.any { w -> w.length > 2 && (listOf(t.name) + t.aliases).any { n -> n.lowercase().split(' ').contains(w) } } }
        }
        return when (verb) {
            Verb.CAST -> {
                val spell = state.spells.firstOrNull { id -> rest.any { it == id || story.spell(id)?.name?.lowercase()?.contains(it) == true } }
                val t = matchThing(rest.filter { w -> spell == null || (w != spell && story.spell(spell)?.name?.lowercase()?.contains(w) != true) })
                if (spell == null) {
                    TypedResult(false, Shown("Which spell? You know: " + state.spells.joinToString { story.spell(it)?.name ?: it } + "."))
                } else if (t == null) {
                    TypedResult(true, Shown(castPlain(spell, "the air")))
                } else {
                    TypedResult(true, act(Verb.CAST, t.id, spell))
                }
            }

            Verb.USE -> {
                val item = state.items.firstOrNull { id -> rest.any { w -> w == id || story.item(id)?.name?.lowercase()?.split(' ')?.contains(w) == true } }
                val itemWords = item?.let { id -> (story.item(id)?.name?.lowercase()?.split(' ') ?: emptyList()) + id }.orEmpty()
                val t = matchThing(rest.filter { it !in itemWords }) ?: if (item == null) matchThing(rest) else null
                when {
                    t != null -> TypedResult(true, act(Verb.USE, t.id, item))
                    item != null -> TypedResult(false, Shown("Use ${story.item(item)?.name} on what?"))
                    else -> TypedResult(false, Shown("You don't see that here."))
                }
            }

            else -> {
                val t = matchThing(rest)
                if (t == null) {
                    if (rest.isEmpty() && verb == Verb.LOOK) {
                        TypedResult(true, Shown(stopIntro(s)))
                    } else {
                        TypedResult(false, Shown("You don't see that here."))
                    }
                } else {
                    TypedResult(true, act(verb, t.id))
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Markets

    fun shopItems() = story.items.filter { it.shop }

    fun price(item: ItemDef): Int {
        var p = item.price
        if (role == "merchant") p = (p * 0.75).roundToInt()
        if ((state.rep["goblin"] ?: 0) >= 2) p -= 1
        return max(1, p)
    }

    fun buy(id: String): Shown {
        val item = story.item(id) ?: return Shown("")
        val p = price(item)
        if (state.gold < p) return Shown("You can't afford that.")
        state.gold -= p
        return when (id) {
            "food_sack" -> {
                state.food += 30
                Shown("You buy a sack of food.", listOf("-$p gold", "+30 food"))
            }

            "herb_bundle" -> {
                state.herbs += 2
                Shown("You buy a bundle of healing herbs.", listOf("-$p gold", "+2 herbs"))
            }

            "repair" -> {
                state.cart = 10
                Shown("A wheelwright fixes up your cart.", listOf("-$p gold", "Cart mended"))
            }

            else -> {
                if (id !in state.items) state.items.add(id)
                Shown("You buy the ${item.name}.", listOf("-$p gold", "Got: ${item.name}"))
            }
        }
    }

    fun sellValue(id: String): Int {
        val item = story.item(id) ?: return 0
        return max(1, if (role == "merchant") (item.value * 1.25).roundToInt() else item.value)
    }

    fun sell(id: String): Shown {
        if (id !in state.items) return Shown("")
        val v = sellValue(id)
        state.items.remove(id)
        state.gold += v
        return Shown("You sell the ${story.item(id)?.name}.", listOf("+$v gold"))
    }

    fun openMarket() {
        if (currentStop?.market == true) state.phase = Phase.MARKET
    }

    fun closeMarket() {
        state.phase = Phase.STOP
    }

    // ---------------------------------------------------------------------------------------
    // Mini-games: score 0 to 10

    fun minigameDone(score: Int): Shown {
        val kind = state.minigame
        state.minigame = null
        state.next = "travel"
        val s = score.coerceIn(0, 10)
        val shown =
            when (kind) {
                "fish" -> {
                    val food = s * 4
                    state.food += food
                    Shown(if (s >= 6) "What a catch! Fish for days." else if (s > 0) "A few fish for the pot." else "Not a single bite.", listOf("+$food food"))
                }

                "herbs" -> {
                    val h = (s + 2) / 3
                    state.herbs += h
                    Shown(if (h > 0) "You gather glowing moonleaf." else "The herbs wilt before you can pick them.", listOf("+$h herbs"))
                }

                "rocks" -> {
                    val dmg = max(0, 4 - s / 2)
                    state.cart -= dmg
                    clampAll()
                    Shown(if (dmg == 0) "Not a scratch! Fine driving." else "The cart takes a few knocks.", if (dmg > 0) listOf("Cart damaged") else emptyList())
                }

                else -> {
                    Shown("")
                }
            }
        state.score += s * 5
        state.shown = shown
        state.phase = Phase.RESULT
        return shown
    }

    // ---------------------------------------------------------------------------------------
    // The end

    /** Has the run ended badly? (everyone gone, or no hope left) */
    private fun checkFailure(peek: Boolean = false): Shown? {
        val fail =
            when {
                !kids && state.members.size > 1 && active.size == 1 && state.spirit == 0 -> "turned_back"
                state.food == 0 && state.starving >= 8 && !kids -> "turned_back"
                else -> null
            }
        if (fail != null) {
            state.ending = fail
            if (!peek) return endRun()
        }
        return null
    }

    fun endRun(): Shown {
        if (state.ending == null) {
            state.ending =
                story.endings
                    .filter { !it.special }
                    .sortedBy { it.order }
                    .firstOrNull { check(it.cond) }
                    ?.id ?: story.endings.first().id
        }
        val ending = story.ending(state.ending!!)
        state.phase = Phase.OVER
        state.score = finalScore(ending)
        addJournal("The journey ended: ${ending.title}.")
        return Shown(textFor(ending.text, ending.kidsText))
    }

    val endingDef get() = state.ending?.let { story.ending(it) }

    fun finalScore(ending: Ending): Int {
        var s = state.score
        if (ending.arrived) {
            s += 500
            s += active.size * 150
            if (hasPet) s += 100
            if (eggHere && "egg_cold" !in state.flags) s += 300
        } else {
            s += legIndex * 60
        }
        s += state.food / 2 + state.gold
        s += state.secrets * 60
        s += state.book.size * 15
        s += ending.bonus
        val par = (state.legs * 50 * milesFactor(state.setup.length) / 12.0).roundToInt() + 2
        if (ending.arrived) s += max(0, par - state.day) * 15
        val mult =
            when (state.setup.difficulty) {
                "easy" -> 0.5
                "hard" -> 2.0
                else -> 1.0
            }
        return (s * mult).roundToInt()
    }

    /** Where on the map this run ended, for family trail markers: land id and 0 to 100 */
    fun markerSpot(): Pair<String, Int> = state.route.last() to (if (state.legMiles == 0) 100 else state.miles * 100 / state.legMiles)
}
