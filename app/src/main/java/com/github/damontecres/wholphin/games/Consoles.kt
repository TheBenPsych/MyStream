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
    val options: Map<String, String> = emptyMap(),
    val help: List<String>,
) {
    companion object {
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
                    help = listOf("Remote: arrows move, OK is A, Play/Pause is Start", "Controller: bottom button B, right button A"),
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
                    help = listOf("Remote: arrows move, OK is A, Play/Pause is Start", "Controller: bottom button B, right button A"),
                ),
                LocalConsole(
                    key = "gba",
                    title = "Game Boy Advance",
                    slugs = setOf("gba"),
                    core = "mgba",
                    touch = TouchLayout.GBA,
                    power = PowerNeed.LIGHT,
                    bios = setOf("gba_bios.bin"),
                    help = listOf("Controller: bottom button B, right button A, shoulder buttons L and R"),
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
                    help = listOf("Buttons sit where they are on a PlayStation pad", "Put scph5501.bin in Games/bios on Tower for the best results"),
                ),
            )

        /** Consoles on the way, shown as "coming later" instead of hidden */
        val LATER = setOf("n64")

        fun forSlug(slug: String): LocalConsole? = ALL.firstOrNull { slug.lowercase() in it.slugs }
    }
}
