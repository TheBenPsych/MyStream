package com.github.damontecres.wholphin.ui.detail.discover

/**
 * Glowfin (Ben, 2026-11-21): who a studio belongs to, so a request page can say "Columbia Pictures · part of
 * Sony Pictures" and Ben can tell a big studio film from a small one at a glance. The film database only
 * lists company names, so this short list of well known studios is kept here. The browser version has the
 * same list (server/web/js/ask.js): change both together.
 *
 * Each line is: owner | kind | name starts, separated by ";". A company matches when its name starts with one
 * of them (so "Sony Pictures Animation" matches "sony pictures").
 */
object StudioOwners {
    data class Owner(
        val owner: String,
        val kind: String,
    )

    private const val HOLLYWOOD = "Big Hollywood studio"
    private const val STREAMING = "Streaming service"
    private const val INDIE = "Independent studio"
    private const val JAPAN = "Big Japanese studio"
    private const val INDIA = "Big Indian studio"
    private const val KOREA = "Big Korean studio"
    private const val EUROPE = "Big European studio"
    private const val CHINA = "Big Chinese studio"
    private const val BRITISH_TV = "British TV"

    private val TABLE =
        listOf(
            "Sony Pictures|$HOLLYWOOD|columbia pictures;sony pictures;tristar;tri-star;screen gems;stage 6 films;affirm films;3000 pictures;destination films;left bank pictures",
            "Sony|$JAPAN|aniplex;crunchyroll;funimation;a-1 pictures;cloverworks",
            "Disney|$HOLLYWOOD|walt disney;disney;pixar;marvel studios;marvel entertainment;marvel animation;marvel television;lucasfilm;20th century;twentieth century;searchlight pictures;fox searchlight;touchstone;hollywood pictures;abc studios;abc signature;abc;fx productions;fx networks;fx;hulu;national geographic;blue sky studios",
            "Universal|$HOLLYWOOD|universal pictures;universal television;universal animation;universal 1440;universal content;universal international;focus features;dreamworks animation;illumination;working title;nbc;peacock;syfy;usa network;sky studios;carnival films;telemundo",
            "Warner Bros.|$HOLLYWOOD|warner bros;new line cinema;dc studios;dc films;dc entertainment;hbo;castle rock entertainment;cartoon network;adult swim;williams street;hanna-barbera;turner;tnt;tbs;discovery",
            "Paramount|$HOLLYWOOD|paramount;nickelodeon;mtv;comedy central;cbs;showtime;bet;republic pictures;skydance",
            "Amazon|$HOLLYWOOD|amazon;metro-goldwyn-mayer;mgm;orion pictures;united artists;prime video",
            "Apple|$STREAMING|apple",
            "Netflix|$STREAMING|netflix",
            "Lionsgate|$HOLLYWOOD|lionsgate;lions gate;summit entertainment;starz;entertainment one",
            "A24|$INDIE|a24",
            "Neon|$INDIE|neon",
            "Blumhouse|$INDIE|blumhouse",
            "Legendary|$INDIE|legendary",
            "Annapurna|$INDIE|annapurna",
            "Amblin|$INDIE|amblin",
            "Toho|$JAPAN|toho",
            "Toei|$JAPAN|toei",
            "Kadokawa|$JAPAN|kadokawa",
            "Bandai Namco|$JAPAN|sunrise;bandai namco",
            "Nippon TV|$JAPAN|studio ghibli;madhouse;nippon television;nippon tv",
            "Sega Sammy|$JAPAN|tms entertainment",
            "Shueisha|$JAPAN|shueisha",
            "Yash Raj Films|$INDIA|yash raj",
            "Dharma Productions|$INDIA|dharma productions",
            "T-Series|$INDIA|t-series",
            "Red Chillies Entertainment|$INDIA|red chillies",
            "Zee|$INDIA|zee studios;zee entertainment",
            "Reliance|$INDIA|reliance entertainment;jio studios;viacom18",
            "Eros|$INDIA|eros international",
            "Hombale Films|$INDIA|hombale",
            "Lyca Productions|$INDIA|lyca",
            "Sun Pictures|$INDIA|sun pictures",
            "Excel Entertainment|$INDIA|excel entertainment",
            "CJ ENM|$KOREA|cj entertainment;cj enm;studio dragon",
            "Showbox|$KOREA|showbox",
            "Canal+|$EUROPE|studiocanal;canal+",
            "Gaumont|$EUROPE|gaumont",
            "Pathé|$EUROPE|pathé;pathe",
            "Constantin Film|$EUROPE|constantin film",
            "BBC|$BRITISH_TV|bbc",
            "ITV|$BRITISH_TV|itv",
            "Channel 4|$BRITISH_TV|channel 4;film4",
            "China Film Group|$CHINA|china film",
            "Tencent|$CHINA|tencent",
        ).flatMap { line ->
            val (owner, kind, starts) = line.split("|")
            starts.split(";").map { it to Owner(owner, kind) }
        }.sortedByDescending { it.first.length }

    /** Who [company] belongs to, or null for a studio that isn't on the list */
    fun of(company: String): Owner? {
        val name = company.trim().lowercase()
        return TABLE.firstOrNull { (start, _) -> name == start || name.startsWith("$start ") || name.startsWith("$start,") }?.second
    }

    /** "Columbia Pictures · part of Sony Pictures", or just the name when it is its own owner or not on the list */
    fun line(company: String): String {
        val owner = of(company) ?: return company
        val same = company.trim().lowercase().startsWith(owner.owner.lowercase().trimEnd('.'))
        return if (same) company else "$company · part of ${owner.owner}"
    }
}
