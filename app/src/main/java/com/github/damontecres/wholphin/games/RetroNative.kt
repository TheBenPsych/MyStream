package com.github.damontecres.wholphin.games

/**
 * The Benflix game player's C side (app/src/main/cpp/benflix_retro.c). It opens a downloaded emulator,
 * runs the game one frame at a time and draws it. Only used inside [GameActivity], on its GL thread
 * (except [setButtons] and [setStick], which are safe from anywhere).
 */
object RetroNative {
    init {
        System.loadLibrary("benflixretro")
    }

    external fun setOption(
        key: String,
        value: String,
    )

    /** Opens the emulator and starts the game. False if it couldn't (the reason is in the log). */
    external fun load(
        corePath: String,
        gamePath: String,
        systemDir: String,
        saveDir: String,
    ): Boolean

    /** [frames per second, sound sample rate] */
    external fun timing(): DoubleArray

    external fun run(frames: Int)

    /** Copies the sound made since last time into [out] (stereo pairs). Returns how many values. */
    external fun drainAudio(out: ShortArray): Int

    external fun setButtons(
        port: Int,
        mask: Int,
    )

    external fun setStick(
        port: Int,
        stick: Int,
        x: Int,
        y: Int,
    )

    external fun reset()

    external fun saveState(): ByteArray?

    external fun loadState(data: ByteArray): Boolean

    external fun getSaveRam(): ByteArray?

    external fun setSaveRam(data: ByteArray): Boolean

    /** The last picture: [width, height, ARGB pixels...] */
    external fun snapshot(): IntArray?

    /** Benflix: how many discs this game has (0 when it can't swap discs) */
    external fun discCount(): Int

    /** The disc in now, counting from 0 */
    external fun discNow(): Int

    /** Takes the disc out, puts in disc [index] (from 0) and closes the lid */
    external fun swapDisc(index: Int): Boolean

    /** A finger on the game picture at [x], [y] (pixels inside the game view), or lifted when [down] is false */
    external fun setPointer(
        x: Float,
        y: Float,
        down: Boolean,
    )

    external fun unload()

    external fun glInit()

    external fun glResize(
        width: Int,
        height: Int,
    )

    external fun glDraw()
}

/** libretro's button numbers (SNES layout: B bottom, A right, Y left, X top) */
object RetroButton {
    const val B = 0
    const val Y = 1
    const val SELECT = 2
    const val START = 3
    const val UP = 4
    const val DOWN = 5
    const val LEFT = 6
    const val RIGHT = 7
    const val A = 8
    const val X = 9
    const val L = 10
    const val R = 11
    const val L2 = 12
    const val R2 = 13
    const val L3 = 14
    const val R3 = 15

    fun bit(button: Int): Int = 1 shl button
}
