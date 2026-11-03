package com.github.damontecres.wholphin.games

/** How the on-screen touch buttons are laid out on phones */
enum class TouchLayout {
    /** D-pad, B A, Select Start (NES, Game Boy) */
    TWO_BUTTONS,

    /** D-pad, B A, L R, Select Start */
    GBA,

    /** D-pad, Y X B A, L R, Select Start */
    SNES,

    /** D-pad, A B C, Start (Mega Drive / Genesis) */
    GENESIS,

    /** D-pad, square triangle cross circle, L1 R1 L2 R2, Select Start */
    PLAYSTATION,

    /** D-pad, one Fire button, Select Reset (Atari 2600) */
    ONE_BUTTON,

    /** D-pad, buttons 1 to 6, Coin Start (arcade) */
    ARCADE,
}

/** How much power a console needs from the TV or phone, see [DevicePower] */
enum class PowerNeed {
    LIGHT,
    MEDIUM,
    HEAVY,
}

/**
 * A console Benflix can play on the device itself.
 *
 * @param core the libretro emulator, downloaded from the libretro buildbot the first time it's needed
 * @param bios BIOS file names it can use (lower case), fetched from Games/bios on the server if there
 */
data class LocalConsole(
    val key: String,
    val title: String,
    val slugs: Set<String>,
    val core: String,
    val touch: TouchLayout,
    val power: PowerNeed,
    val remoteFriendly: Boolean = false,
    val bios: Set<String> = emptySet(),
    /** Sub-folder of the system folder the emulator looks in for its BIOS files ("" = the system folder itself) */
    val biosFolder: String = "",
    val options: Map<String, String> = emptyMap(),
    val help: List<String>,
) {
    companion object {
        /** What a plain TV remote does in a game */
        const val REMOTE_HELP =
            "Remote: arrows move, OK is A, Back is B (hold Back for the menu), Play/Pause is Start. " +
                "No Play/Pause? The menu has Press Start and Press Select."

        val ALL =
            listOf(
                LocalConsole(
                    key = "nes",
                    title = "NES",
                    slugs = setOf("nes", "famicom", "fds"),
                    core = "fceumm",
                    touch = TouchLayout.TWO_BUTTONS,
                    power = PowerNeed.LIGHT,
                    remoteFriendly = true,
                    bios = setOf("disksys.rom"),
                    help = listOf(REMOTE_HELP, "Controller: bottom button B, right button A"),
                ),
                LocalConsole(
                    key = "snes",
                    title = "SNES",
                    slugs = setOf("snes", "sfam"),
                    core = "snes9x",
                    touch = TouchLayout.SNES,
                    power = PowerNeed.LIGHT,
                    help = listOf("Controller buttons sit where they are on a Super Nintendo pad"),
                ),
                LocalConsole(
                    key = "genesis",
                    title = "Sega Genesis",
                    slugs = setOf("genesis", "megadrive", "genesis-slash-megadrive", "sms", "gamegear", "segacd", "sega-cd"),
                    core = "genesis_plus_gx",
                    touch = TouchLayout.GENESIS,
                    power = PowerNeed.LIGHT,
                    bios = setOf("bios_cd_u.bin", "bios_cd_e.bin", "bios_cd_j.bin", "bios_md.bin"),
                    help = listOf("Controller: left button A, bottom button B, right button C"),
                ),
                LocalConsole(
                    key = "gb",
                    title = "Game Boy",
                    slugs = setOf("gb", "gbc"),
                    core = "gambatte",
                    touch = TouchLayout.TWO_BUTTONS,
                    power = PowerNeed.LIGHT,
                    remoteFriendly = true,
                    help = listOf(REMOTE_HELP, "Controller: bottom button B, right button A"),
                ),
                LocalConsole(
                    key = "gba",
                    title = "Game Boy Advance",
                    slugs = setOf("gba"),
                    core = "mgba",
                    touch = TouchLayout.GBA,
                    power = PowerNeed.LIGHT,
                    remoteFriendly = true,
                    bios = setOf("gba_bios.bin"),
                    help =
                        listOf(
                            REMOTE_HELP,
                            "Remote L and R: channel down and up, or 3 and 4, if your remote has them",
                            "Controller: bottom button B, right button A, shoulder buttons L and R",
                        ),
                ),
                LocalConsole(
                    key = "psx",
                    title = "PlayStation",
                    slugs = setOf("psx", "ps1", "playstation"),
                    core = "pcsx_rearmed",
                    touch = TouchLayout.PLAYSTATION,
                    power = PowerNeed.MEDIUM,
                    bios =
                        setOf(
                            "scph5501.bin",
                            "scph1001.bin",
                            "scph7001.bin",
                            "scph101.bin",
                            "scph5500.bin",
                            "scph5502.bin",
                            "scph1000.bin",
                        ),
                    help =
                        listOf(
                            "Buttons sit where they are on a PlayStation pad",
                            "Put scph5501.bin in Games/bios on Tower for the best results",
                        ),
                ),
                LocalConsole(
                    key = "atari2600",
                    title = "Atari 2600",
                    slugs = setOf("atari2600", "atari-2600", "a2600", "2600"),
                    core = "stella2014",
                    touch = TouchLayout.ONE_BUTTON,
                    power = PowerNeed.LIGHT,
                    remoteFriendly = true,
                    help =
                        listOf(
                            "Remote: arrows move, OK fires, Play/Pause is Reset (starts most games), the menu has Press Select",
                            "Controller: bottom button fires, Start is Reset, Select is Select",
                        ),
                ),
                LocalConsole(
                    key = "atari7800",
                    title = "Atari 7800",
                    slugs = setOf("atari7800", "atari-7800", "a7800", "7800"),
                    core = "prosystem",
                    touch = TouchLayout.TWO_BUTTONS,
                    power = PowerNeed.LIGHT,
                    remoteFriendly = true,
                    help = listOf(REMOTE_HELP, "Controller: bottom button is button 1, right button is button 2"),
                ),
                LocalConsole(
                    key = "lynx",
                    title = "Atari Lynx",
                    slugs = setOf("lynx", "atari-lynx", "atarilynx"),
                    core = "handy",
                    touch = TouchLayout.GBA,
                    power = PowerNeed.LIGHT,
                    remoteFriendly = true,
                    bios = setOf("lynxboot.img"),
                    help =
                        listOf(
                            REMOTE_HELP,
                            "Option 1 and 2 are L and R (channel down and up on the remote)",
                            "Put lynxboot.img in Games/bios on Tower if a game won't start",
                        ),
                ),
                LocalConsole(
                    key = "pce",
                    title = "TurboGrafx-16",
                    slugs =
                        setOf(
                            "tg16",
                            "turbografx16--1",
                            "turbografx-16",
                            "turbografx16",
                            "pce",
                            "pc-engine",
                            "pcengine",
                            "tg-cd",
                            "turbografx-cd",
                            "pce-cd",
                            "pcecd",
                        ),
                    core = "mednafen_pce_fast",
                    touch = TouchLayout.TWO_BUTTONS,
                    power = PowerNeed.LIGHT,
                    remoteFriendly = true,
                    bios = setOf("syscard3.pce"),
                    help =
                        listOf(
                            REMOTE_HELP,
                            "Controller: bottom button is II, right button is I, Start is Run",
                            "CD games need syscard3.pce in Games/bios on Tower",
                        ),
                ),
                LocalConsole(
                    key = "ngp",
                    title = "Neo Geo Pocket",
                    slugs = setOf("ngp", "ngpc", "neo-geo-pocket", "neo-geo-pocket-color", "neogeopocket", "neogeopocketcolor"),
                    core = "mednafen_ngp",
                    touch = TouchLayout.TWO_BUTTONS,
                    power = PowerNeed.LIGHT,
                    remoteFriendly = true,
                    help = listOf(REMOTE_HELP, "Controller: bottom button B, right button A, Start is Option"),
                ),
                LocalConsole(
                    key = "wonderswan",
                    title = "WonderSwan",
                    slugs = setOf("wonderswan", "wonderswan-color", "wonderswancolor", "ws", "wsc"),
                    core = "mednafen_wswan",
                    touch = TouchLayout.TWO_BUTTONS,
                    power = PowerNeed.LIGHT,
                    remoteFriendly = true,
                    help = listOf(REMOTE_HELP, "Controller: bottom button B, right button A"),
                ),
                LocalConsole(
                    key = "virtualboy",
                    title = "Virtual Boy",
                    slugs = setOf("virtualboy", "virtual-boy", "vb"),
                    core = "mednafen_vb",
                    touch = TouchLayout.GBA,
                    power = PowerNeed.LIGHT,
                    help = listOf("Shown flat in red and black. Controller: bottom button B, right button A, shoulders L and R"),
                ),
                LocalConsole(
                    key = "colecovision",
                    title = "ColecoVision",
                    slugs = setOf("colecovision", "coleco"),
                    core = "gearcoleco",
                    touch = TouchLayout.TWO_BUTTONS,
                    power = PowerNeed.LIGHT,
                    bios = setOf("colecovision.rom"),
                    help =
                        listOf(
                            "Needs colecovision.rom in Games/bios on Tower",
                            "Controller: bottom and right buttons fire, Select and Start are keypad 1 and 2 (pick the game mode)",
                        ),
                ),
                LocalConsole(
                    key = "arcade",
                    title = "Arcade",
                    slugs = setOf("arcade", "fbneo", "mame", "cps1", "cps2", "cps3", "neogeoaes", "neogeomvs", "neo-geo"),
                    core = "fbneo",
                    touch = TouchLayout.ARCADE,
                    power = PowerNeed.LIGHT,
                    bios = setOf("neogeo.zip"),
                    biosFolder = "fbneo",
                    help =
                        listOf(
                            "Select puts in a coin, Start starts",
                            "Games must be FinalBurn Neo .zip sets. Neo Geo games also need neogeo.zip in Games/bios on Tower",
                        ),
                ),
            )

        /** Consoles on the way, shown as "coming later" instead of hidden */
        val LATER = setOf("n64")

        fun forSlug(slug: String): LocalConsole? = ALL.firstOrNull { slug.lowercase() in it.slugs }
    }
}

/** What each libretro button is called on the real console's pad (only the buttons the console has) */
fun buttonLabels(touch: TouchLayout): Map<Int, String> =
    when (touch) {
        TouchLayout.TWO_BUTTONS -> {
            mapOf(RetroButton.A to "A", RetroButton.B to "B", RetroButton.START to "Start", RetroButton.SELECT to "Select")
        }

        TouchLayout.GBA -> {
            mapOf(
                RetroButton.A to "A",
                RetroButton.B to "B",
                RetroButton.L to "L",
                RetroButton.R to "R",
                RetroButton.START to "Start",
                RetroButton.SELECT to "Select",
            )
        }

        TouchLayout.SNES -> {
            mapOf(
                RetroButton.A to "A",
                RetroButton.B to "B",
                RetroButton.X to "X",
                RetroButton.Y to "Y",
                RetroButton.L to "L",
                RetroButton.R to "R",
                RetroButton.START to "Start",
                RetroButton.SELECT to "Select",
            )
        }

        TouchLayout.GENESIS -> {
            mapOf(RetroButton.Y to "A", RetroButton.B to "B", RetroButton.A to "C", RetroButton.START to "Start")
        }

        TouchLayout.ONE_BUTTON -> {
            mapOf(RetroButton.B to "Fire", RetroButton.START to "Reset", RetroButton.SELECT to "Select")
        }

        TouchLayout.ARCADE -> {
            mapOf(
                RetroButton.B to "1",
                RetroButton.A to "2",
                RetroButton.R to "3",
                RetroButton.Y to "4",
                RetroButton.X to "5",
                RetroButton.L to "6",
                RetroButton.START to "Start",
                RetroButton.SELECT to "Coin",
            )
        }

        TouchLayout.PLAYSTATION -> {
            mapOf(
                RetroButton.B to "✕",
                RetroButton.A to "○",
                RetroButton.Y to "□",
                RetroButton.X to "△",
                RetroButton.L to "L1",
                RetroButton.R to "R1",
                RetroButton.L2 to "L2",
                RetroButton.R2 to "R2",
                RetroButton.START to "Start",
                RetroButton.SELECT to "Select",
            )
        }
    }
