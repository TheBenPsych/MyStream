package com.github.damontecres.wholphin.ui.games.ember

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/*
 * Ember Road's story lives in ONE file: app/src/main/assets/ember/story.json
 *
 * Everything that can happen on the road (events, stops, endings, creatures, items, spells,
 * campfire stories) is written there, so new events can be added without touching the game code.
 * These classes are just the shape of that file.
 *
 * "if" (a condition) and "fx" (what happens) are small JSON objects, read by EmberEngine:
 *   "if": {"gold>=": 5, "has": "rope", "role": "bard", "folk": "elf", "flag": "met_witch", "kids": false}
 *   "fx": {"food": -10, "gold": 5, "item": "+rope", "flag": "met_witch", "rep.goblin": 1, "hurt": 1}
 */

val EmberJson =
    Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = false
        explicitNulls = false
    }

@Serializable
data class Story(
    val lands: List<Land>,
    val events: List<StoryEvent>,
    val stops: List<Stop>,
    val endings: List<Ending>,
    val creatures: List<Creature>,
    val items: List<ItemDef>,
    val spells: List<Spell>,
    val backfires: List<Outcome>,
    val campfire: List<CampStory>,
    val markerLines: List<String>,
    val names: Names,
    val traits: List<Trait>,
) {
    fun land(id: String) = lands.first { it.id == id }

    fun stop(id: String) = stops.first { it.id == id }

    fun event(id: String) = events.first { it.id == id }

    fun item(id: String) = items.firstOrNull { it.id == id }

    fun spell(id: String) = spells.firstOrNull { it.id == id }

    fun creature(id: String) = creatures.firstOrNull { it.id == id }

    fun ending(id: String) = endings.first { it.id == id }

    fun trait(id: String) = traits.firstOrNull { it.id == id }

    companion object {
        fun parse(text: String): Story = EmberJson.decodeFromString(serializer(), text)
    }
}

/** A part of the kingdom the road goes through */
@Serializable
data class Land(
    val id: String,
    val name: String,
    val blurb: String,
    /** Which painted scene to draw (EmberArt) */
    val scene: String,
    /** "safe" (long but easy), "danger" (short but risky), "strange" (magical), "start" or "final" */
    val tag: String,
    val miles: Int,
    /** 0 to 3: how often trouble finds you here */
    val danger: Int = 1,
    /** Weather this land can have */
    val weather: List<String> = listOf("clear", "clear", "cloudy", "rain"),
    /** Which tune plays here (EmberMusic) */
    val music: Int = 0,
    /** Cold lands cool the egg faster */
    val cold: Boolean = false,
    /** The stop scenes at the end of this land (one is picked) */
    val stops: List<String> = emptyList(),
)

@Serializable
data class StoryEvent(
    val id: String,
    val title: String,
    val text: String,
    val kidsText: String? = null,
    /** Lands it can happen in, "*" = anywhere */
    val lands: List<String> = listOf("*"),
    val seasons: List<String> = emptyList(),
    val weight: Int = 2,
    /** Can only happen once per run */
    val once: Boolean = true,
    /** Left out of the Kids version */
    val scary: Boolean = false,
    @SerialName("if")
    val cond: JsonObject = JsonObject(emptyMap()),
    /** Goes before ordinary events when its "if" is true (story chains: the dragon, the hunters) */
    val priority: Boolean = false,
    /** The big obstacle of a land (river, pass, troll bridge): one per land */
    val obstacle: Boolean = false,
    val creature: String? = null,
    val scene: String? = null,
    val choices: List<Choice>,
)

@Serializable
data class Choice(
    val label: String,
    val kidsLabel: String? = null,
    @SerialName("if")
    val cond: JsonObject = JsonObject(emptyMap()),
    val text: String = "",
    val kidsText: String? = null,
    val fx: JsonObject = JsonObject(emptyMap()),
    /** Casting a spell: it can go wrong (risky magic) */
    val spell: String? = null,
    /** A dice roll: [Roll.chance] percent plus bonuses */
    val roll: Roll? = null,
)

@Serializable
data class Roll(
    val chance: Int,
    /** e.g. "role:ranger": 20, "folk:elf": 15, "item:rope": 20, "trait:brave": 10, "spirit": 3 (per point) */
    val bonus: Map<String, Int> = emptyMap(),
    val win: Outcome,
    val lose: Outcome,
)

@Serializable
data class Outcome(
    val text: String,
    val kidsText: String? = null,
    val fx: JsonObject = JsonObject(emptyMap()),
)

/** A stop at the end of a land: a little scene to look around, talk, take, use and cast in */
@Serializable
data class Stop(
    val id: String,
    val name: String,
    val scene: String,
    val intro: String,
    val kidsIntro: String? = null,
    val things: List<Thing>,
    /** Words you can only find by typing them */
    val secrets: List<Secret> = emptyList(),
    /** The town has a market (trade screen) */
    val market: Boolean = false,
    /** Ember Peak: the end of the road */
    val final: Boolean = false,
)

@Serializable
data class Thing(
    val id: String,
    val name: String,
    val aliases: List<String> = emptyList(),
    /** Hidden until a flag is set */
    @SerialName("if")
    val cond: JsonObject = JsonObject(emptyMap()),
    val look: List<Response> = emptyList(),
    val talk: List<Response> = emptyList(),
    val take: List<Response> = emptyList(),
    val use: List<Response> = emptyList(),
    val cast: List<Response> = emptyList(),
    /** A person or creature (shows under Talk) */
    val person: Boolean = false,
)

@Serializable
data class Response(
    val text: String,
    val kidsText: String? = null,
    @SerialName("if")
    val cond: JsonObject = JsonObject(emptyMap()),
    /** For "use": the item used on it. For "cast": the spell. */
    val with: String? = null,
    val fx: JsonObject = JsonObject(emptyMap()),
    /** Only works the first time */
    val once: Boolean = false,
    /** Shown after the first time, for once-only answers */
    val after: String? = null,
)

@Serializable
data class Secret(
    /** Typed text must contain all of these words */
    val words: List<String>,
    val text: String,
    @SerialName("if")
    val cond: JsonObject = JsonObject(emptyMap()),
    val fx: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class Ending(
    val id: String,
    val title: String,
    val text: String,
    val kidsText: String? = null,
    /** Checked in order when the road ends; the first that fits wins */
    @SerialName("if")
    val cond: JsonObject = JsonObject(emptyMap()),
    val order: Int = 50,
    /** Finished the journey (counts for unlocks) */
    val arrived: Boolean = true,
    val scene: String = "peak",
    val bonus: Int = 0,
    /** Only reached when another part of the game says so (sold the egg, everyone left) */
    val special: Boolean = false,
)

@Serializable
data class Creature(
    val id: String,
    val name: String,
    val note: String,
    /** Which sprite to draw (EmberArt) */
    val art: String,
    val friendable: Boolean = false,
)

@Serializable
data class ItemDef(
    val id: String,
    val name: String,
    val desc: String,
    val magic: Boolean = false,
    val value: Int = 5,
    /** Can be bought at markets */
    val shop: Boolean = false,
    val price: Int = 0,
)

@Serializable
data class Spell(
    val id: String,
    val name: String,
    val desc: String,
    /** What happens when cast with nothing special to cast it on */
    val plain: String,
    val fx: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class CampStory(
    val text: String,
    /** Jellyfin genre for the "watch this" pick */
    val genre: String = "Fantasy",
)

@Serializable
data class Names(
    val human: List<String>,
    val elf: List<String>,
    val dwarf: List<String>,
    val fox: List<String>,
    val pets: List<PetDef>,
)

@Serializable
data class PetDef(
    val id: String,
    val name: String,
    val kind: String,
    val desc: String,
)

@Serializable
data class Trait(
    val id: String,
    val name: String,
    val desc: String,
)
