package com.github.damontecres.wholphin.ui.setup

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import com.github.damontecres.wholphin.services.LogoEffect
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Benflix: records every holiday scene on Who's watching as pictures, which the "Record holiday scenes"
 * workflow turns into short looping videos (extras/scenes). Playing a video is far less work for a TV than
 * drawing the scene live.
 *
 * Only runs when RECORD_SCENES=1 (it takes a few minutes). Each loop is [LOOP_S] seconds; the first second
 * is blended with the second after the loop's end, so the video joins up with no jump.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RecordHolidayScenes {
    @Test
    fun record() {
        assumeTrue(System.getenv("RECORD_SCENES") == "1")
        val out = File(System.getenv("SCENES_OUT") ?: "build/scenes")
        val only =
            System
                .getenv("SCENES_ONLY")
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
        LogoEffect.entries
            .filter { it != LogoEffect.NONE && (only == null || it.name in only) }
            .forEach { record(it, File(out, it.name)) }
    }

    private fun record(
        effect: LogoEffect,
        dir: File,
    ) {
        dir.mkdirs()
        val painter = ScenePainter(effect)
        val picture = ImageBitmap(SCENE_W, SCENE_H)
        val loopFrames = LOOP_S * FPS
        val blendFrames = BLEND_S * FPS
        val start = mutableListOf<Bitmap>()
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        for (n in 0 until loopFrames + blendFrames) {
            painter.paint(picture, START_S + n / FPS.toFloat())
            val frame = picture.asAndroidBitmap()
            when {
                n < blendFrames -> {
                    start += frame.copy(Bitmap.Config.ARGB_8888, false)
                }

                n < loopFrames -> {
                    save(frame, dir, n)
                }

                else -> {
                    // Just past the loop's end: fade into the matching start picture, so the end leads into the start
                    val k = n - loopFrames
                    val mixed = frame.copy(Bitmap.Config.ARGB_8888, true)
                    paint.alpha = (255 * k / blendFrames.toFloat()).toInt()
                    Canvas(mixed).drawBitmap(start[k], 0f, 0f, paint)
                    save(mixed, dir, k)
                    mixed.recycle()
                }
            }
        }
        println("Recorded ${effect.name}: $loopFrames pictures")
    }

    private fun save(
        bitmap: Bitmap,
        dir: File,
        n: Int,
    ) {
        File(dir, "f%04d.png".format(n)).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    companion object {
        const val FPS = 25
        const val LOOP_S = 12
        const val BLEND_S = 1

        /** Same start as the live scene, so nothing is empty in the first moment */
        const val START_S = 4f
    }
}
