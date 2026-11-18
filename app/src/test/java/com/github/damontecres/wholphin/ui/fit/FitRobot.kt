package com.github.damontecres.wholphin.ui.fit

import android.app.Application
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.toSize
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import org.robolectric.Shadows.shadowOf
import java.io.File

/**
 * Benflix "fit robot" (Ben 2026-11-18, "Various text cut off, send bots to check for more").
 *
 * Draws pages with long, awkward names (long English titles, Japanese original names, many tabs) at
 * TV size (960 x 540, like a 1080p TV) and phone size, takes a picture of each, and looks at every
 * piece of text on the page:
 *  - CUT: part of the words is hidden by the edge of the box they sit in (the "Closest matches" line
 *    with its top sliced off, "les" for Episodes)
 *  - SPILL: the words are bigger than their own space and spill out without a "…"
 *  - OVERLAP: two pieces of text drawn on top of each other (the Japanese name under the rating line)
 * Text at the edge of a row that scrolls sideways is normal (the row keeps going) and is not flagged.
 *
 * Pictures (problems boxed in red) and report.md land in app/build/fit-robot. The fit-robot workflow
 * publishes them to the fit-results branch. Findings don't fail the build: a person looks at them.
 */
class FitActivity : ComponentActivity()

object FitRobot {
    val outDir: File by lazy { File(System.getProperty("benflix.fitOut") ?: "build/fit-robot").apply { mkdirs() } }

    /** Make Benflix think it's on a TV (or not). The answer is remembered, so reset it first. */
    fun pretendTv(tv: Boolean) {
        val app: Application = ApplicationProvider.getApplicationContext()
        shadowOf(app.packageManager).setSystemFeature(PackageManager.FEATURE_LEANBACK, tv)
        runCatching {
            val cls = Class.forName("com.github.damontecres.wholphin.ui.touch.PhoneFitKt")
            cls.getDeclaredField("isTvCached").apply { isAccessible = true }.set(null, null)
        }
        val info =
            ActivityInfo().apply {
                name = FitActivity::class.java.name
                packageName = app.packageName
            }
        shadowOf(app.packageManager).addOrUpdateActivity(info)
    }

    class Finding(
        val kind: String,
        val text: String,
        val box: Rect,
        val detail: String,
    )

    private class TextBox(
        val text: String,
        val visible: Rect,
        val node: SemanticsNode,
    )

    fun check(
        root: SemanticsNode,
        mustShow: List<String> = emptyList(),
    ): List<Finding> {
        val found = mutableListOf<Finding>()
        val boxes = mutableListOf<TextBox>()
        val screen =
            root.layoutInfo.coordinates.size
                .toSize()

        fun walk(node: SemanticsNode) {
            val words = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
            val coords = node.layoutInfo.coordinates
            if (!words.isNullOrBlank() && coords.isAttached) {
                val pos = coords.positionInRoot()
                val full = Rect(pos, coords.size.toSize())
                val vis = coords.boundsInRoot()
                val onScreen = vis.width >= 1f && vis.height >= 1f && vis.left < screen.width && vis.top < screen.height
                if (onScreen) {
                    boxes += TextBox(words, vis, node)
                    val cutV = full.height - vis.height
                    val cutH = full.width - vis.width
                    // Cut at the bottom of a list that keeps going down is normal; cut at the top never is
                    val topCut = vis.top > full.top + 1f
                    if (cutV > 3f &&
                        (
                            topCut ||
                                !inScroller(
                                    node,
                                    SemanticsProperties.VerticalScrollAxisRange,
                                    startCut = false,
                                    minHeight = screen.height * .4f,
                                )
                        )
                    ) {
                        val where = if (topCut) "top" else "bottom"
                        found += Finding("CUT", words, vis, "${cutV.toInt()}px of its $where is hidden")
                    }
                    if (cutH > 3f &&
                        !inScroller(node, SemanticsProperties.HorizontalScrollAxisRange, startCut = vis.left > full.left + 1f)
                    ) {
                        val where = if (vis.left > full.left + 1f) "left" else "right"
                        found += Finding("CUT", words, vis, "${cutH.toInt()}px of its $where side is hidden")
                    }
                    val layouts = mutableListOf<TextLayoutResult>()
                    runCatching {
                        node.config
                            .getOrNull(SemanticsActions.GetTextLayoutResult)
                            ?.action
                            ?.invoke(layouts)
                    }
                    layouts.firstOrNull()?.let { r ->
                        val dotted = (0 until r.lineCount).any { r.isLineEllipsized(it) }
                        if (r.hasVisualOverflow && !dotted) {
                            found += Finding("SPILL", words, vis, "bigger than its space with no …")
                        }
                    }
                }
            }
            node.children.forEach { walk(it) }
        }
        walk(root)
        // Two pieces of text on top of each other
        for (i in boxes.indices) {
            for (j in i + 1 until boxes.size) {
                val a = boxes[i]
                val b = boxes[j]
                val over = a.visible.intersect(b.visible)
                if (over.width > 4f && over.height > minOf(a.visible.height, b.visible.height) * .3f) {
                    found += Finding("OVERLAP", "${a.text} / ${b.text}", a.visible, "drawn on top of each other")
                }
            }
        }
        // Words the page must show somewhere (like the original Japanese name), not quietly leave out
        mustShow.forEach { words ->
            if (boxes.none { it.text.contains(words) }) {
                found += Finding("MISSING", words, Rect.Zero, "not on the page at all")
            }
        }
        return found
    }

    /** Text cut at the edge of a row or list that scrolls (and has more in that direction) is normal */
    private fun inScroller(
        node: SemanticsNode,
        axis: androidx.compose.ui.semantics.SemanticsPropertyKey<androidx.compose.ui.semantics.ScrollAxisRange>,
        startCut: Boolean,
        /** Small scrolling boxes (like a title page's tab area) must still show whole rows: only big lists count */
        minHeight: Float = 0f,
    ): Boolean {
        var p = node.parent
        while (p != null) {
            p.config.getOrNull(axis)?.let { range ->
                if (p.boundsInRoot.height < minHeight) return false
                val value = range.value()
                val max = range.maxValue()
                return if (startCut) value > 0f else value < max
            }
            p = p.parent
        }
        return false
    }

    /** Draw [content], take its picture, check it, and add it to the report */
    fun <A : ComponentActivity> run(
        rule: AndroidComposeTestRule<ActivityScenarioRule<A>, A>,
        device: String,
        scene: String,
        content: @Composable () -> Unit,
        after: (() -> Unit)? = null,
        mustShow: List<String> = emptyList(),
    ): List<Finding> {
        rule.setContent(content)
        rule.mainClock.advanceTimeBy(1500)
        rule.waitForIdle()
        after?.invoke()
        rule.mainClock.advanceTimeBy(1500)
        rule.waitForIdle()
        val root = rule.onRoot(useUnmergedTree = true).fetchSemanticsNode()
        val findings = check(root, mustShow)
        val name = "$device-$scene"
        runCatching {
            val bmp =
                rule
                    .onRoot()
                    .captureToImage()
                    .asAndroidBitmap()
                    .copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(bmp)
            val paint =
                Paint().apply {
                    color = android.graphics.Color.RED
                    style = Paint.Style.STROKE
                    strokeWidth = 4f
                }
            findings.forEach { f -> canvas.drawRect(f.box.left, f.box.top, f.box.right, f.box.bottom, paint) }
            File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }.onFailure { println("fit robot: no picture for $name: $it") }
        synchronized(this) {
            val report = File(outDir, "report.md")
            val lines =
                buildString {
                    append("\n## $name: ")
                    append(if (findings.isEmpty()) "all text fits\n" else "${findings.size} problem(s)\n")
                    findings.forEach { append("- **${it.kind}** \"${it.text.take(80)}\": ${it.detail}\n") }
                }
            report.appendText(lines)
            println("fit robot $name:$lines")
        }
        return findings
    }
}
