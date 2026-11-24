package com.github.damontecres.wholphin.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * The Coral Reef aquarium screensaver (Ben picked it 2026-11-24).
 *
 * Built for the weak 85" TCL the same way the old Windows aquarium screensavers stayed smooth:
 * everything is painted ONCE into pictures when the tank is made (fish, the whole tank, plants),
 * then each frame only stamps those pictures. No blur anywhere; glows are painted in.
 * The tank is drawn in a 960 x 540 "design" space and scaled to the screen, and every
 * picture is painted at the screen's real pixel size so it stays sharp.
 *
 * Rules from Ben: fish only swim side to side and never turn on screen; no hermit crab,
 * seahorse or eel; night version (blue moonlight, glowing coral) from 10 PM to 7 AM.
 */
class ReefTank(
    screenW: Int,
    screenH: Int,
    val night: Boolean,
    lite: Boolean,
) {
    private val k = screenW / W
    private val kh = screenH / H

    private val bg: Bitmap
    private val front: Bitmap
    private val rayA: Bitmap
    private val rayB: Bitmap
    private val cauA: Bitmap
    private val cauB: Bitmap
    private val plants: List<Plant>
    private val dusters: List<Duster>
    private val fish = mutableListOf<Fish>()
    private val school: School
    private val bubbles: List<Bubble>
    private val looseBubbles: List<Bubble>
    private val motes: List<Mote>
    private val tentacles: List<Tentacle>

    private val bmp = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val bubblePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
        }
    private val dot = Paint().apply { style = Paint.Style.FILL }
    private val tentThick =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = 7f
        }
    private val tentThin =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = 3f
        }
    private val tentTip =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = c("#fbe0ff")
        }
    private val anemoneBase = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c("#7a3aa8") }
    private val anemoneGlow: Paint?
    private val tentPath = Path()
    private val tipPath = Path()
    private val rect = RectF()
    private val skew = Matrix()
    private val skewValues = FloatArray(9)

    init {
        val r = Random(System.nanoTime())
        val day = makeBackground()
        bg =
            if (night) {
                nightBackground(day).also { day.recycle() }
            } else {
                day
            }
        front = darken(sprite(W, 160f) { cv -> withSeed(77) { rnd -> drawFront(cv, rnd) } })
        rayA = rays(Random(3), if (night) 4 else 6, if (night) 0.07f else 0.14f)
        rayB = rays(Random(9), if (night) 4 else 6, if (night) 0.05f else 0.11f)
        cauA = caustics(Random(11))
        cauB = caustics(Random(12))
        plants =
            withSeed(5) { rnd ->
                listOf(
                    PlantSpec(300f, 190f, "#5fd38e", "#1e6e45", 9),
                    PlantSpec(560f, 150f, "#7ee0a0", "#2a7a4c", 7),
                    PlantSpec(W - 120f, 200f, "#4cc283", "#1a5f3c", 10),
                    PlantSpec(20f, 170f, "#6ad696", "#22704a", 8),
                    PlantSpec(700f, 120f, "#9be07a", "#3d7a2a", 6),
                    PlantSpec(440f, 110f, "#7fd0a0", "#2a6a4a", 6),
                ).map { p -> Plant(p.x, p.h, darken(grass(rnd, p.h, c(p.c1), c(p.c2), p.blades)), rnd.nextFloat() * 6f) }
            }
        dusters =
            listOf(
                Triple(150f, 392f, "#ff5a8a"),
                Triple(770f, 360f, "#ffb020"),
                Triple(880f, 392f, "#9a6bff"),
            ).map { (x, y, col) -> Duster(x, y, darken(duster(c(col))), r.nextFloat() * 6f) }

        val sp = if (night) 0.7f else 1f
        val farTint = mix(c("#16689a"), 0.5f)
        val midTint = mix(c("#13608f"), 0.12f)
        val nearTint = if (night) Tint(c("#06143a"), 0.5f) else null
        val far =
            if (lite) {
                listOf(
                    FishKind.CHROMIS,
                    FishKind.YELLOW,
                    FishKind.WRASSE,
                )
            } else {
                listOf(FishKind.CHROMIS, FishKind.CHROMIS, FishKind.CHROMIS, FishKind.YELLOW, FishKind.WRASSE)
            }
        val mid =
            if (lite) {
                listOf(
                    FishKind.CLOWN,
                    FishKind.BLUE,
                    FishKind.BUTTER,
                    FishKind.GRAMMA,
                )
            } else {
                listOf(FishKind.CLOWN, FishKind.BLUE, FishKind.BUTTER, FishKind.GRAMMA, FishKind.WRASSE)
            }
        val near = if (lite) listOf(FishKind.ANGEL, FishKind.YELLOW) else listOf(FishKind.ANGEL, FishKind.YELLOW, FishKind.BLUE)
        far.forEach { fish += Fish(fishSet(it, r.f(16f, 22f), farTint), 0, r.f(12f, 20f) * sp, 60f, 340f, r) }
        mid.forEach { fish += Fish(fishSet(it, r.f(32f, 38f), midTint), 1, r.f(22f, 32f) * sp, 70f, 380f, r) }
        near.forEach { fish += Fish(fishSet(it, r.f(54f, 62f), nearTint), 2, r.f(32f, 44f) * sp, 70f, 330f, r) }
        val cardinal = fishSet(FishKind.CARDINAL, 15f, mix(c("#13608f"), 0.15f))
        school =
            School(
                cardinal,
                r.f(0f, W),
                250f,
                26f * sp,
                List(if (lite) 7 else 11) { Member(r.f(-70f, 70f), r.f(-30f, 30f), r.f(0f, 9f)) },
            )
        bubbles = List(if (lite) 18 else 28) { Bubble(650f + r.f(-4f, 4f), r.f(0f, H), r.f(1.5f, 4.5f), r.f(30f, 70f), r.f(0f, 9f)) }
        looseBubbles = List(if (lite) 3 else 6) { Bubble(r.f(0f, W), r.f(0f, H), r.f(1.5f, 4.5f), r.f(30f, 70f), r.f(0f, 9f)) }
        motes =
            List(if (night) (if (lite) 40 else 80) else (if (lite) 25 else 50)) { Mote(r.f(0f, W), r.f(0f, H), r.f(2f, 6f), r.f(0f, 9f)) }
        tentacles = List(40) { i -> Tentacle((-PI + i / 39.0 * PI).toFloat() + r.f(-0.05f, 0.05f), r.f(26f, 42f), r.f(0f, 6f)) }
        tentThick.color = c(if (night) "#c56bff" else "#b25cf0")
        tentThin.color = c(if (night) "#f3c2ff" else "#e9a8ff")
        anemoneGlow =
            if (night) {
                Paint().apply {
                    shader = RadialGradient(780f, 360f, 70f, Color.argb(90, 200, 110, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
                }
            } else {
                null
            }
        bubblePaint.color = if (night) Color.argb(115, 170, 200, 255) else Color.argb(178, 235, 250, 255)
    }

    private var lastT = -1f

    /** Draws one frame. [t] is seconds since the tank started; things only move when [t] moves on */
    fun draw(
        cv: Canvas,
        t: Float,
    ) {
        val dt = if (lastT < 0f) 0f else (t - lastT).coerceIn(0f, 0.1f)
        lastT = t
        cv.save()
        cv.scale(k, kh)
        rect.set(0f, 0f, W, H)
        bmp.alpha = 255
        cv.drawBitmap(bg, null, rect, bmp)

        val ca = if (night) 0.3f else 1f
        val s6 = sin(t * 0.6f)
        stamp(cv, cauA, 0f, 445f, W, 130f, ca * (0.5f + 0.5f * s6))
        stamp(cv, cauB, 0f, 445f, W, 130f, ca * (0.5f - 0.5f * s6))
        stamp(cv, rayA, 0f, 0f, W, H, 0.55f + 0.45f * sin(t * 0.35f))
        stamp(cv, rayB, 0f, 0f, W, H, 0.55f + 0.45f * sin(t * 0.35f + 2f))
        bmp.alpha = 255

        if (night) {
            dot.color = c("#9ff6ff")
            for (m in motes) {
                m.x += m.s * dt * 0.4f
                if (m.x > W) m.x = 0f
                dot.alpha = (max(0f, sin(t * 0.9f + m.ph)) * 204).toInt()
                val y = m.y + sin(t * 0.3f + m.ph) * 4f
                cv.drawRect(m.x, y, m.x + 2f, y + 2f, dot)
            }
        } else {
            dot.color = Color.argb(140, 230, 250, 255)
            for (m in motes) {
                m.x += m.s * dt * 0.6f
                if (m.x > W) m.x = 0f
                val y = m.y + sin(t * 0.4f + m.ph) * 3f
                cv.drawRect(m.x, y, m.x + 1.6f, y + 1.6f, dot)
            }
        }

        for (f in fish) if (f.depth == 0) f.step(cv, t, dt)

        for (d in dusters) {
            val a = sin(t * 0.8f + d.ph) * 0.12f
            cv.save()
            cv.translate(d.x, d.y)
            cv.rotate(Math.toDegrees(a.toDouble()).toFloat())
            rect.set(-25f, -88f, 25f, 2f)
            cv.drawBitmap(d.img, null, rect, bmp)
            cv.restore()
        }

        drawAnemone(cv, t)

        for (p in plants) {
            val cs = sin(t * 0.7f + p.ph) * 0.09f
            skewValues[0] = 1f
            skewValues[1] = cs
            skewValues[2] = p.x - cs * p.h
            skewValues[3] = 0f
            skewValues[4] = 1f
            skewValues[5] = H - 8f - p.h
            skewValues[6] = 0f
            skewValues[7] = 0f
            skewValues[8] = 1f
            skew.setValues(skewValues)
            cv.save()
            cv.concat(skew)
            rect.set(0f, 0f, 110f, p.h)
            cv.drawBitmap(p.img, null, rect, bmp)
            cv.restore()
        }

        school.x += school.v * dt
        if (school.x > W + 120f) {
            school.x = -120f
            school.y = 180f + (sin(t) * 0.5f + 0.5f) * 150f
        }
        for (m in school.members) {
            val img = school.set.right[floor(t * 5f + m.ph).toInt().mod(3)]
            val x = school.x + m.ox + sin(t * 0.8f + m.ph) * 6f
            val y = school.y + m.oy + sin(t * 0.6f + m.ox * 0.05f) * 10f
            rect.set(x, y, x + school.set.w, y + school.set.h)
            cv.drawBitmap(img, null, rect, bmp)
        }

        for (f in fish) if (f.depth == 1) f.step(cv, t, dt)
        drawBubbles(cv, bubbles, dt, t, true)
        drawBubbles(cv, looseBubbles, dt, t, false)
        for (f in fish) if (f.depth == 2) f.step(cv, t, dt)

        rect.set(0f, 380f, W, H)
        cv.drawBitmap(front, null, rect, bmp)
        cv.restore()
    }

    private fun stamp(
        cv: Canvas,
        b: Bitmap,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        alpha: Float,
    ) {
        val a = (alpha.coerceIn(0f, 1f) * 255).toInt()
        if (a <= 2) return
        bmp.alpha = a
        rect.set(x, y, x + w, y + h)
        cv.drawBitmap(b, null, rect, bmp)
    }

    private fun drawAnemone(
        cv: Canvas,
        t: Float,
    ) {
        val ax = 780f
        val ay = 370f
        anemoneGlow?.let { cv.drawRect(ax - 70f, ay - 80f, ax + 70f, ay + 60f, it) }
        tentPath.reset()
        tipPath.reset()
        for (tt in tentacles) {
            val a = tt.a + sin(t * 1.1f + tt.ph) * 0.18f
            val ex = ax + cos(a) * tt.l * 1.2f
            val ey = ay + sin(a) * tt.l
            tentPath.moveTo(ax + cos(tt.a) * 8f, ay + 4f)
            tentPath.quadTo(ax + cos(tt.a) * tt.l * 0.6f, ay + sin(tt.a) * tt.l * 0.5f, ex, ey)
            tipPath.addCircle(ex, ey, 3f, Path.Direction.CW)
        }
        cv.drawPath(tentPath, tentThick)
        cv.drawPath(tentPath, tentThin)
        cv.drawPath(tipPath, tentTip)
        rect.set(ax - 26f, ay - 3f, ax + 26f, ay + 15f)
        cv.drawOval(rect, anemoneBase)
    }

    private fun drawBubbles(
        cv: Canvas,
        list: List<Bubble>,
        dt: Float,
        t: Float,
        stream: Boolean,
    ) {
        for (b in list) {
            b.y -= b.s * dt
            if (b.y < -10f) {
                b.y = H + 5f
                b.x = if (stream) 650f + (sin(t * 13f + b.ph) * 4f) else (b.x * 7.31f + 97f) % W
            }
            cv.drawCircle(b.x + sin(t * 2f + b.ph) * 3f, b.y, b.r, bubblePaint)
        }
    }

    private inner class Fish(
        val set: FishSet,
        val depth: Int,
        val v: Float,
        val y0: Float,
        val y1: Float,
        val r: Random,
    ) {
        // Fish never turn on screen: each crosses, leaves, and comes back in from an edge
        var dir = if (r.nextBoolean()) 1 else -1
        var x = r.f(0f, W - set.w)
        var y = r.f(y0, y1)
        val ph = r.f(0f, 10f)
        val ff = r.f(3f, 5f)

        fun step(
            cv: Canvas,
            t: Float,
            dt: Float,
        ) {
            x += dir * v * dt
            if ((dir > 0 && x > W + 10f) || (dir < 0 && x < -set.w - 10f)) {
                dir = if (r.nextBoolean()) 1 else -1
                x = if (dir > 0) -set.w - r.f(10f, 200f) else W + r.f(10f, 200f)
                y = r.f(y0, y1)
            }
            val pose = POSES[floor(t * ff + ph).toInt().mod(4)]
            val img = if (dir > 0) set.right[pose] else set.left[pose]
            val yy = y + sin(t * 0.5f + ph) * 5f
            rect.set(x, yy, x + set.w, yy + set.h)
            cv.drawBitmap(img, null, rect, bmp)
        }
    }

    // ---------------------------------------------------------------- pictures made once

    private fun sprite(
        w: Float,
        h: Float,
        draw: (Canvas) -> Unit,
    ): Bitmap {
        val b = Bitmap.createBitmap(max(1, ceil(w * k).toInt()), max(1, ceil(h * k).toInt()), Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        cv.scale(k, k)
        draw(cv)
        return b
    }

    private fun darken(b: Bitmap): Bitmap = if (night) tint(b, Tint(c("#06143a"), 0.6f)) else b

    private fun mix(
        color: Int,
        a: Float,
    ): Tint = if (night) Tint(c("#06143a"), max(a, 0.62f)) else Tint(color, a)

    private fun tint(
        src: Bitmap,
        t: Tint?,
    ): Bitmap {
        if (t == null || t.a <= 0f) return src
        val cv = Canvas(src)
        val p =
            Paint().apply {
                color = t.color
                alpha = (t.a * 255).toInt()
                xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
            }
        cv.drawRect(0f, 0f, src.width.toFloat(), src.height.toFloat(), p)
        return src
    }

    private fun mirror(src: Bitmap): Bitmap {
        val m = Matrix().apply { preScale(-1f, 1f) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    private fun fishSet(
        kind: FishKind,
        s: Float,
        t: Tint?,
    ): FishSet {
        val w = s * 2.2f
        val h = s * 1.6f
        val right = listOf(-1f, 0f, 1f).map { tail -> tint(sprite(w, h) { cv -> FishArt.draw(cv, w, h, kind, tail) }, t) }
        return FishSet(right, right.map { mirror(it) }, w, h)
    }

    private fun rays(
        r: Random,
        count: Int,
        alpha: Float,
    ): Bitmap {
        // Soft light, so it is painted at design size and stretched (saves memory on 2 GB TVs)
        val b = Bitmap.createBitmap(W.toInt(), H.toInt(), Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        repeat(count) {
            val x = r.f(40f, W - 40f)
            val w = r.f(40f, 110f)
            val lean = r.f(60f, 160f)
            p.shader =
                LinearGradient(
                    0f,
                    0f,
                    0f,
                    H * 0.9f,
                    Color.argb((alpha * 255).toInt(), 255, 255, 240),
                    Color.argb(0, 255, 255, 240),
                    Shader.TileMode.CLAMP,
                )
            val path =
                Path().apply {
                    moveTo(x, 0f)
                    lineTo(x + w, 0f)
                    lineTo(x + w + lean + w, H * 0.9f)
                    lineTo(x + lean, H * 0.9f)
                    close()
                }
            cv.drawPath(path, p)
        }
        if (night) {
            p.shader = null
            p.color = Color.argb(0, 0, 0, 0)
            tint(b, Tint(c("#aac8ff"), 0.5f))
        }
        return b
    }

    private fun caustics(r: Random): Bitmap {
        val b = Bitmap.createBitmap(W.toInt(), 130, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        val p =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2.2f
                strokeJoin = Paint.Join.ROUND
                color = Color.argb(41, 255, 255, 225)
            }
        repeat(170) {
            val cx = r.f(0f, W)
            val cy = r.f(0f, 130f)
            val rr = r.f(10f, 22f)
            val path = Path()
            for (i in 0..6) {
                val a = i / 6f * TAU
                val q = rr * r.f(0.7f, 1.2f)
                val px = cx + cos(a) * q
                val py = cy + sin(a) * q * 0.45f
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            cv.drawPath(path, p)
        }
        return b
    }

    private fun grass(
        r: Random,
        h: Float,
        c1: Int,
        c2: Int,
        blades: Int,
    ): Bitmap =
        sprite(110f, h) { cv ->
            val fillP = Paint(Paint.ANTI_ALIAS_FLAG)
            val line = stroke(Color.argb(46, 255, 255, 255), 1f)
            repeat(blades) {
                val bx = 55f + r.f(-22f, 22f)
                val top = r.f(h * 0.05f, h * 0.5f)
                val bend = r.f(-34f, 34f)
                val bw = r.f(5f, 9f)
                fillP.shader = LinearGradient(0f, h, 0f, top, c2, c1, Shader.TileMode.CLAMP)
                val p =
                    Path().apply {
                        moveTo(bx - bw / 2, h)
                        quadTo(bx + bend - bw / 2, (h + top) / 2, bx + bend * 0.6f, top)
                        quadTo(bx + bend + bw / 2, (h + top) / 2, bx + bw / 2, h)
                        close()
                    }
                cv.drawPath(p, fillP)
                val l =
                    Path().apply {
                        moveTo(bx, h)
                        quadTo(bx + bend, (h + top) / 2, bx + bend * 0.6f, top)
                    }
                cv.drawPath(l, line)
            }
        }

    private fun duster(col: Int): Bitmap =
        sprite(50f, 90f) { cv ->
            cv.drawRect(23f, 40f, 27f, 90f, fill(c("#8a7a60")))
            val p = stroke(col, 1.6f)
            val white = stroke(c("#fff2f7"), 1.6f)
            for (i in 0 until 18) {
                val a = (-PI + i / 17.0 * PI).toFloat()
                val path =
                    Path().apply {
                        moveTo(25f, 42f)
                        quadTo(25f + cos(a) * 12f, 42f + sin(a) * 18f, 25f + cos(a) * 22f, 42f + sin(a) * 30f)
                    }
                cv.drawPath(path, if (i % 2 == 1) p else white)
            }
        }

    private fun makeBackground(): Bitmap {
        val b = Bitmap.createBitmap(max(1, (W * k).toInt()), max(1, (H * kh).toInt()), Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        cv.scale(k, kh)
        withSeed(41) { r -> ReefArt(cv, r).tank() }
        return b
    }

    private fun nightBackground(day: Bitmap): Bitmap {
        val b = day.copy(Bitmap.Config.ARGB_8888, true)
        val cv = Canvas(b)
        val mul = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY) }
        mul.color = c("#2b4f9c")
        cv.drawRect(0f, 0f, b.width.toFloat(), b.height.toFloat(), mul)
        mul.color = c("#4a5a90")
        cv.drawRect(0f, 0f, b.width.toFloat(), b.height.toFloat(), mul)
        cv.scale(k, kh)
        val add = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD) }
        for (g in GLOWS) {
            add.shader =
                RadialGradient(
                    g.x,
                    g.y,
                    g.r,
                    Color.argb(107, g.red, g.green, g.blue),
                    Color.argb(0, g.red, g.green, g.blue),
                    Shader.TileMode.CLAMP,
                )
            cv.drawRect(g.x - g.r, g.y - g.r, g.x + g.r, g.y + g.r, add)
        }
        add.shader = RadialGradient(690f, -30f, 260f, Color.argb(89, 170, 200, 255), Color.argb(0, 170, 200, 255), Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, W, H, add)
        // Corals that glow under the blue night light
        withSeed(41) { r ->
            val a = ReefArt(cv, r)
            a.polyps(200f, 410f, 22, 30f, c("#3dffcf"), c("#fffbb0"))
            a.polyps(740f, 390f, 16, 26f, c("#ff6fe0"), c("#fff6c0"))
            a.zoas(90f, 440f, 26, 26f)
            a.zoas(860f, 430f, 20, 22f)
        }
        return b
    }

    private fun drawFront(
        cv: Canvas,
        r: Random,
    ) {
        cv.translate(0f, -380f)
        val a = ReefArt(cv, r)
        a.rock(40f, H + 10f, 130f, 96f, c("#4a525a"), c("#1c2329"), 9f)
        a.holes(40f, H + 10f, 130f, 96f, 12)
        a.rock(W - 30f, H + 10f, 120f, 84f, c("#4d545c"), c("#1d242a"), 12f)
        a.holes(W - 30f, H + 10f, 120f, 84f, 12)
        a.staghorn(70f, 470f, (-PI / 2 + 0.25).toFloat(), 26f, 8f, 3, c("#ff5d5d"), c("#ffc4b5"))
        a.polyps(W - 60f, 470f, 12, 22f, c("#b07cff"), c("#ffe27a"))
    }

    private class Tint(
        val color: Int,
        val a: Float,
    )

    private class FishSet(
        val right: List<Bitmap>,
        val left: List<Bitmap>,
        val w: Float,
        val h: Float,
    )

    private class Plant(
        val x: Float,
        val h: Float,
        val img: Bitmap,
        val ph: Float,
    )

    private class PlantSpec(
        val x: Float,
        val h: Float,
        val c1: String,
        val c2: String,
        val blades: Int,
    )

    private class Duster(
        val x: Float,
        val y: Float,
        val img: Bitmap,
        val ph: Float,
    )

    private class Member(
        val ox: Float,
        val oy: Float,
        val ph: Float,
    )

    private class School(
        val set: FishSet,
        var x: Float,
        var y: Float,
        val v: Float,
        val members: List<Member>,
    )

    private class Bubble(
        var x: Float,
        var y: Float,
        val r: Float,
        val s: Float,
        val ph: Float,
    )

    private class Mote(
        var x: Float,
        val y: Float,
        val s: Float,
        val ph: Float,
    )

    private class Tentacle(
        val a: Float,
        val l: Float,
        val ph: Float,
    )

    private class Glow(
        val x: Float,
        val y: Float,
        val r: Float,
        val red: Int,
        val green: Int,
        val blue: Int,
    )

    companion object {
        const val W = 960f
        const val H = 540f
        private val POSES = intArrayOf(0, 1, 2, 1)
        private val GLOWS =
            listOf(
                Glow(120f, 340f, 70f, 255, 170, 60),
                Glow(860f, 310f, 70f, 255, 110, 170),
                Glow(200f, 410f, 45f, 80, 255, 190),
                Glow(740f, 390f, 40f, 255, 120, 220),
                Glow(90f, 440f, 34f, 120, 255, 120),
                Glow(860f, 430f, 30f, 120, 255, 120),
                Glow(310f, 425f, 34f, 90, 255, 150),
                Glow(700f, 432f, 26f, 200, 120, 255),
                Glow(382f, 452f, 60f, 190, 255, 90),
                Glow(492f, 478f, 50f, 255, 210, 90),
            )

        /** 10 PM to 7 AM uses the night tank */
        fun isNightHour(hour: Int) = hour >= 22 || hour < 7

        private inline fun <T> withSeed(
            seed: Int,
            block: (Random) -> T,
        ): T = block(Random(seed))
    }
}

private const val TAU = (PI * 2).toFloat()

private fun c(hex: String): Int = Color.parseColor(hex)

private fun ca(
    color: Int,
    a: Float,
): Int = Color.argb((a * 255).toInt(), Color.red(color), Color.green(color), Color.blue(color))

private fun Random.f(
    a: Float,
    b: Float,
): Float = a + nextFloat() * (b - a)

private fun <T> Random.pick(list: List<T>): T = list[nextInt(list.size)]

private fun fill(color: Int): Paint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        this.color = color
    }

private fun stroke(
    color: Int,
    width: Float,
): Paint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = width
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        this.color = color
    }

/** Everything in the tank that never moves: water, far reef, sand, rocks and coral */
private class ReefArt(
    private val cv: Canvas,
    private val r: Random,
) {
    private val w = ReefTank.W
    private val h = ReefTank.H

    fun tank() {
        val water = Paint()
        water.shader =
            LinearGradient(
                0f,
                0f,
                0f,
                h,
                intArrayOf(c("#3bb2d6"), c("#1878a8"), c("#0d4c78"), c("#093659")),
                floatArrayOf(0f, 0.35f, 0.75f, 1f),
                Shader.TileMode.CLAMP,
            )
        cv.drawRect(0f, 0f, w, h, water)
        water.shader = LinearGradient(0f, 0f, 0f, 46f, Color.argb(115, 220, 250, 255), Color.argb(0, 220, 250, 255), Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, w, 46f, water)
        val ripple = stroke(Color.argb(64, 235, 255, 255), 1.5f)
        repeat(40) {
            val x = r.f(0f, w)
            val y = r.f(2f, 26f)
            cv.drawPath(
                Path().apply {
                    moveTo(x, y)
                    quadTo(x + 20f, y + r.f(-4f, 4f), x + r.f(30f, 60f), y)
                },
                ripple,
            )
        }
        for ((base, col, amp) in listOf(
            Triple(360f, Color.argb(153, 30, 104, 150), 24f),
            Triple(380f, Color.argb(191, 26, 96, 140), 26f),
            Triple(412f, Color.argb(224, 17, 72, 110), 22f),
        )) {
            val p = fill(col)
            val path = Path().apply { moveTo(0f, h) }
            var x = 0f
            while (x <= w) {
                path.lineTo(x, base + sin(x * 0.011f + base) * amp + r.f(-6f, 6f))
                x += 20f
            }
            path.lineTo(w, h)
            path.close()
            cv.drawPath(path, p)
            repeat(8) { staghorn(r.f(0f, w), base + 8f, (-PI / 2).toFloat() + r.f(-0.3f, 0.3f), r.f(14f, 26f), 5f, 3, col, col) }
            repeat(5) {
                val cx = r.f(0f, w)
                val rx = r.f(14f, 30f)
                val ry = r.f(10f, 18f)
                cv.drawArc(RectF(cx - rx, base + 4f - ry, cx + rx, base + 4f + ry), 180f, 180f, true, p)
            }
        }
        val sand = Paint().apply { shader = LinearGradient(0f, 445f, 0f, h, c("#b3ab88"), c("#6d7766"), Shader.TileMode.CLAMP) }
        val sp = Path().apply { moveTo(0f, 460f) }
        var x = 0f
        while (x <= w) {
            sp.lineTo(x, 452f + sin(x * 0.02f) * 6f)
            x += 24f
        }
        sp.lineTo(w, h)
        sp.lineTo(0f, h)
        sp.close()
        cv.drawPath(sp, sand)
        val grains =
            listOf(Color.argb(89, 255, 250, 225), Color.argb(77, 90, 80, 60), Color.argb(128, 200, 190, 160), Color.argb(64, 255, 255, 255))
        val gp = Paint()
        repeat(4200) {
            gp.color = r.pick(grains)
            val gx = r.f(0f, w)
            val gy = r.f(455f, h)
            cv.drawRect(gx, gy, gx + r.f(1f, 2f), gy + r.f(1f, 2f), gp)
        }
        val light = stroke(Color.argb(33, 255, 255, 230), 2f)
        repeat(34) {
            val y = r.f(478f, 535f)
            val sx = r.f(0f, w)
            cv.drawPath(
                Path().apply {
                    moveTo(sx, y)
                    quadTo(sx + 35f, y - 5f, sx + 70f, y)
                },
                light,
            )
        }
        val darkL = stroke(Color.argb(31, 60, 55, 40), 2f)
        repeat(20) {
            val y = r.f(480f, 535f)
            val sx = r.f(0f, w)
            cv.drawPath(
                Path().apply {
                    moveTo(sx, y + 2f)
                    quadTo(sx + 35f, y - 3f, sx + 70f, y + 2f)
                },
                darkL,
            )
        }
        val pebbleCols = listOf(c("#8a8d86"), c("#a39b8a"), c("#6f746f"), c("#c2b8a3"), c("#9a8f7c"))
        val shadow = fill(Color.argb(46, 0, 0, 0))
        val shine = fill(Color.argb(64, 255, 255, 255))
        repeat(60) {
            val px = r.f(0f, w)
            val py = r.f(475f, 538f)
            val pr = r.f(2f, 6f)
            cv.drawOval(RectF(px + 1f - pr, py + pr * 0.5f - pr * 0.4f, px + 1f + pr, py + pr * 0.5f + pr * 0.4f), shadow)
            cv.drawOval(RectF(px - pr, py - pr * 0.65f, px + pr, py + pr * 0.65f), fill(r.pick(pebbleCols)))
            cv.drawCircle(px - pr * 0.3f, py - pr * 0.25f, pr * 0.3f, shine)
        }
        seaFan(235f, 432f, 170f, c("#9c4fd6"))
        seaFan(905f, 410f, 150f, c("#d4527e"))
        seaFan(470f, 452f, 90f, c("#e0773a"))
        rock(165f, 478f, 150f, 92f, c("#8c8f96"), c("#3e4650"), 1f)
        holes(165f, 478f, 150f, 92f, 26)
        rock(800f, 470f, 140f, 108f, c("#8e8a94"), c("#3c4450"), 4f)
        holes(800f, 470f, 140f, 108f, 26)
        staghorn(120f, 400f, (-PI / 2 - 0.15).toFloat(), 32f, 10f, 4, c("#ff8a3a"), c("#ffd2a8"))
        staghorn(860f, 372f, (-PI / 2 + 0.1).toFloat(), 30f, 9f, 4, c("#ff6f8e"), c("#ffd0db"))
        polyps(200f, 410f, 22, 30f, c("#4ee0c0"), c("#ffef7a"))
        polyps(740f, 390f, 16, 26f, c("#ff8fd0"), c("#fff2a0"))
        zoas(90f, 440f, 26, 26f)
        zoas(860f, 430f, 20, 22f)
        mushroom(300f, 420f, 16f, c("#2d9a5a"), c("#9cffb0"))
        mushroom(322f, 432f, 12f, c("#2d9a5a"), c("#9cffb0"))
        mushroom(700f, 432f, 14f, c("#7a2fb8"), c("#e0a8ff"))
        bubbleCoral(60f, 418f, 9, 9f)
        redAlgae(250f, 394f)
        redAlgae(905f, 372f)
        tableCoral(382f, 452f, 52f, c("#b6c86a"), c("#6d7a3a"))
        brainCoral(492f, 492f, 44f, c("#f0c77a"), c("#a4692c"))
        sponges(590f, 492f, c("#ffb43a"), c("#b35d12"))
        pillars(530f, 470f, c("#c9b48a"), c("#7a6a48"))
        starfish(430f, 518f, 20f, 0.4f, c("#ff6a3d"))
        starfish(690f, 528f, 15f, 1.2f, c("#ffb020"))
        starfish(160f, 530f, 12f, 2.1f, c("#d84a8a"))
        shell(316f, 522f, 14f, -0.3f)
        shell(645f, 508f, 11f, 0.5f)
        shell(760f, 535f, 10f, 0.1f)
        rock(650f, 508f, 16f, 12f, c("#7d7f85"), c("#43494f"), 7f)
    }

    private fun blob(
        cx: Float,
        base: Float,
        rw: Float,
        rh: Float,
        seed: Float,
    ): Path {
        val p = Path().apply { moveTo(cx - rw, base) }
        for (i in 0..16) {
            val a = PI + (i / 16.0) * PI
            val n = 1 + sin(i * 2.3 + seed) * 0.08 + sin(i * 5.1 + seed) * 0.05
            p.lineTo(cx + (cos(a) * rw * n).toFloat(), base + (sin(a) * rh * n).toFloat())
        }
        p.close()
        return p
    }

    fun rock(
        cx: Float,
        base: Float,
        rw: Float,
        rh: Float,
        c1: Int,
        c2: Int,
        seed: Float,
    ) {
        val shape = blob(cx, base, rw, rh, seed)
        cv.drawPath(
            shape,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader =
                    RadialGradient(cx - rw * 0.3f, base - rh * 0.8f, rw * 1.1f, c1, c2, Shader.TileMode.CLAMP)
            },
        )
        cv.save()
        cv.clipPath(shape)
        val dark = fill(Color.argb(36, 0, 0, 0))
        val lite = fill(Color.argb(18, 255, 255, 255))
        repeat((rw * 3).toInt()) { cv.drawCircle(cx + r.f(-rw, rw), base - r.f(0f, rh), r.f(1f, 4f), if (r.nextBoolean()) dark else lite) }
        val patches = listOf(Color.argb(64, 255, 110, 170), Color.argb(56, 170, 90, 255), Color.argb(56, 255, 160, 120))
        repeat((rw / 30).toInt()) {
            val px = cx + r.f(-rw * 0.8f, rw * 0.8f)
            val py = base - r.f(rh * 0.2f, rh * 0.9f)
            val ex = r.f(8f, 22f)
            val ey = r.f(4f, 10f)
            cv.save()
            cv.rotate(r.f(0f, 180f), px, py)
            cv.drawOval(RectF(px - ex, py - ey, px + ex, py + ey), fill(r.pick(patches)))
            cv.restore()
        }
        val crack = stroke(Color.argb(64, 0, 0, 0), 1.5f)
        repeat(6) {
            var x = cx + r.f(-rw * 0.7f, rw * 0.7f)
            var y = base - r.f(rh * 0.2f, rh * 0.8f)
            val p = Path().apply { moveTo(x, y) }
            repeat(4) {
                x += r.f(-12f, 12f)
                y += r.f(-2f, 10f)
                p.lineTo(x, y)
            }
            cv.drawPath(p, crack)
        }
        cv.drawRect(
            cx - rw * 1.2f,
            base - rh * 1.2f,
            cx + rw * 1.2f,
            base,
            Paint().apply {
                shader =
                    LinearGradient(0f, base - rh, 0f, base, Color.argb(0, 0, 0, 0), Color.argb(89, 0, 0, 0), Shader.TileMode.CLAMP)
            },
        )
        cv.restore()
    }

    fun holes(
        cx: Float,
        base: Float,
        rw: Float,
        rh: Float,
        n: Int,
    ) {
        val hole = fill(Color.argb(140, 10, 14, 20))
        val rim = stroke(Color.argb(31, 255, 255, 255), 1f)
        repeat(n) {
            val x = cx + r.f(-rw * 0.7f, rw * 0.7f)
            val y = base - r.f(rh * 0.15f, rh * 0.75f)
            val hr = r.f(2f, 6f)
            val o = RectF(x - hr, y - hr * 0.7f, x + hr, y + hr * 0.7f)
            cv.drawOval(o, hole)
            o.offset(0f, 1f)
            cv.drawArc(o, 18f, 144f, false, rim)
        }
    }

    fun staghorn(
        x: Float,
        y: Float,
        ang: Float,
        len: Float,
        wdt: Float,
        depth: Int,
        col: Int,
        tip: Int,
    ) {
        val x2 = x + cos(ang) * len
        val y2 = y + sin(ang) * len
        cv.drawLine(x, y, x2, y2, stroke(col, wdt))
        cv.drawLine(x - wdt * 0.2f, y, x2 - wdt * 0.2f, y2, stroke(Color.argb(46, 255, 255, 255), wdt * 0.3f))
        if (depth > 0) {
            val n = if (depth > 2) 2 else r.pick(listOf(1, 2, 2))
            for (i in 0 until n) {
                staghorn(
                    x2,
                    y2,
                    ang + (
                        if (i ==
                            1
                        ) {
                            1
                        } else {
                            -1
                        }
                    ) * r.f(0.25f, 0.55f),
                    len * r.f(0.68f, 0.82f),
                    wdt * 0.74f,
                    depth - 1,
                    col,
                    tip,
                )
            }
        } else {
            cv.drawCircle(x2, y2, wdt * 0.6f, fill(tip))
        }
    }

    private fun seaFan(
        x: Float,
        y: Float,
        hgt: Float,
        col: Int,
    ) {
        fun br(
            x0: Float,
            y0: Float,
            a: Float,
            l: Float,
            d: Int,
        ) {
            val x1 = x0 + cos(a) * l
            val y1 = y0 + sin(a) * l
            cv.drawLine(x0, y0, x1, y1, stroke(col, max(0.8f, d * 0.7f)))
            if (d > 0) {
                br(x1, y1, a - r.f(0.2f, 0.45f), l * 0.8f, d - 1)
                br(x1, y1, a + r.f(0.2f, 0.45f), l * 0.8f, d - 1)
            }
        }
        br(x, y, (-PI / 2).toFloat(), hgt * 0.22f, 6)
        val lattice = stroke(ca(col, 0.35f), 0.8f)
        repeat(90) {
            val a = r.f((-PI * 0.9).toFloat(), (-PI * 0.1).toFloat())
            val rr = r.f(hgt * 0.25f, hgt * 0.95f)
            val px = x + cos(a) * rr * 0.75f
            val py = y + sin(a) * rr
            cv.drawLine(px, py, px + r.f(-10f, 10f), py + r.f(-10f, 10f), lattice)
        }
    }

    fun polyps(
        x: Float,
        y: Float,
        n: Int,
        spread: Float,
        c1: Int,
        c2: Int,
    ) {
        val p1 = fill(c1)
        val p2 = fill(c2)
        repeat(n) {
            val px = x + r.f(-spread, spread)
            val py = y + r.f(-spread * 0.3f, spread * 0.3f)
            val pr = r.f(3f, 5.5f)
            cv.drawCircle(px, py, pr, p1)
            cv.drawCircle(px, py, pr * 0.45f, p2)
        }
    }

    fun zoas(
        x: Float,
        y: Float,
        n: Int,
        spread: Float,
    ) {
        val o = fill(c("#ff8a2a"))
        val g = fill(c("#57e36a"))
        val m = fill(c("#2a1a08"))
        repeat(n) {
            val px = x + r.f(-spread, spread)
            val py = y + r.f(-spread * 0.25f, spread * 0.25f)
            val pr = r.f(2.5f, 4f)
            cv.drawCircle(px, py, pr, o)
            cv.drawCircle(px, py, pr * 0.55f, g)
            cv.drawCircle(px, py, pr * 0.2f, m)
        }
    }

    private fun mushroom(
        x: Float,
        y: Float,
        rad: Float,
        c1: Int,
        c2: Int,
    ) {
        cv.save()
        cv.translate(x, y)
        cv.scale(1f, 0.45f)
        cv.drawCircle(
            0f,
            0f,
            rad,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = RadialGradient(0f, 0f, rad, c2, c1, Shader.TileMode.CLAMP) },
        )
        val l = stroke(Color.argb(51, 0, 0, 0), 1.5f)
        var a = 0f
        while (a < TAU) {
            cv.drawLine(cos(a) * rad * 0.25f, sin(a) * rad * 0.25f, cos(a) * rad, sin(a) * rad, l)
            a += 0.35f
        }
        cv.drawCircle(0f, 0f, rad * 0.15f, fill(Color.argb(128, 255, 255, 255)))
        cv.restore()
    }

    private fun bubbleCoral(
        x: Float,
        y: Float,
        n: Int,
        rad: Float,
    ) {
        val edge = stroke(Color.argb(64, 80, 70, 30), 1f)
        repeat(n) {
            val px = x + r.f(-rad * 1.6f, rad * 1.6f)
            val py = y - r.f(0f, rad * 1.4f)
            val rr = r.f(rad * 0.35f, rad * 0.6f)
            cv.drawCircle(
                px,
                py,
                rr,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader =
                        RadialGradient(px - rr * 0.35f, py - rr * 0.35f, rr * 1.3f, c("#fffbe8"), c("#c9c28e"), Shader.TileMode.CLAMP)
                },
            )
            cv.drawCircle(px, py, rr, edge)
        }
    }

    private fun redAlgae(
        x: Float,
        y: Float,
    ) {
        fun br(
            x0: Float,
            y0: Float,
            a: Float,
            l: Float,
            d: Int,
        ) {
            val x1 = x0 + cos(a) * l
            val y1 = y0 + sin(a) * l
            cv.drawLine(x0, y0, x1, y1, stroke(c("#c2304a"), 1f + d * 0.8f))
            if (d > 0) {
                br(x1, y1, a - r.f(0.3f, 0.7f), l * 0.75f, d - 1)
                br(x1, y1, a + r.f(0.3f, 0.7f), l * 0.75f, d - 1)
            }
        }
        repeat(3) { br(x + r.f(-8f, 8f), y, (-PI / 2).toFloat() + r.f(-0.4f, 0.4f), 16f, 4) }
    }

    private fun tableCoral(
        x: Float,
        y: Float,
        rad: Float,
        col: Int,
        dark: Int,
    ) {
        cv.drawRect(x - 6f, y, x + 6f, y + 34f, fill(dark))
        cv.drawOval(RectF(x - rad, y - rad * 0.22f, x + rad, y + rad * 0.22f), fill(col))
        cv.drawOval(
            RectF(
                x - rad * 0.1f - rad * 0.8f,
                y - rad * 0.06f - rad * 0.12f,
                x - rad * 0.1f + rad * 0.8f,
                y - rad * 0.06f + rad * 0.12f,
            ),
            fill(Color.argb(56, 255, 255, 255)),
        )
        val l = stroke(dark, 1.5f)
        var a = 0f
        while (a < TAU) {
            cv.drawLine(x + cos(a) * rad * 0.5f, y + sin(a) * rad * 0.11f, x + cos(a) * rad, y + sin(a) * rad * 0.22f, l)
            a += 0.18f
        }
    }

    private fun brainCoral(
        x: Float,
        y: Float,
        rad: Float,
        c1: Int,
        c2: Int,
    ) {
        val dome =
            Path().apply {
                addArc(RectF(x - rad, y - rad * 0.72f, x + rad, y + rad * 0.72f), 180f, 180f)
                close()
            }
        cv.drawPath(
            dome,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader =
                    RadialGradient(x - rad * 0.35f, y - rad * 0.55f, rad * 1.05f, c1, c2, Shader.TileMode.CLAMP)
            },
        )
        cv.save()
        cv.clipPath(dome)
        val maze = stroke(Color.argb(89, 60, 30, 0), 2.2f)
        repeat((rad * 0.9f).toInt()) {
            var px = x + r.f(-rad, rad)
            var py = y - r.f(0f, rad * 0.7f)
            var a = r.f(0f, TAU)
            val p = Path().apply { moveTo(px, py) }
            repeat(8) {
                a += r.f(-1.2f, 1.2f)
                px += cos(a) * 4f
                py += sin(a) * 4f
                p.lineTo(px, py)
            }
            cv.drawPath(p, maze)
        }
        cv.restore()
        cv.drawPath(dome, stroke(Color.argb(64, 0, 0, 0), 1.5f))
    }

    private fun sponges(
        x: Float,
        y: Float,
        col: Int,
        dark: Int,
    ) {
        for ((dx, hh, ww) in listOf(Triple(-22f, 60f, 16f), Triple(0f, 84f, 18f), Triple(20f, 48f, 15f), Triple(36f, 66f, 13f))) {
            val p =
                Paint().apply {
                    shader =
                        LinearGradient(
                            x + dx - ww / 2,
                            0f,
                            x + dx + ww / 2,
                            0f,
                            intArrayOf(dark, col, dark),
                            floatArrayOf(0f, 0.4f, 1f),
                            Shader.TileMode.CLAMP,
                        )
                }
            cv.drawRect(x + dx - ww / 2, y - hh, x + dx + ww / 2, y, p)
            cv.drawOval(RectF(x + dx - ww / 2, y - hh - ww / 5, x + dx + ww / 2, y - hh + ww / 5), fill(col))
            cv.drawOval(RectF(x + dx - ww / 2 + 3f, y - hh - ww / 5 + 2f, x + dx + ww / 2 - 3f, y - hh + ww / 5 - 2f), fill(c("#2a1408")))
        }
    }

    private fun pillars(
        x: Float,
        y: Float,
        col: Int,
        dark: Int,
    ) {
        val dots = fill(Color.argb(89, 255, 255, 255))
        for ((dx, hh, ww) in listOf(Triple(-14f, 70f, 12f), Triple(0f, 96f, 14f), Triple(14f, 58f, 11f))) {
            val p =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader =
                        LinearGradient(
                            x + dx - ww / 2,
                            0f,
                            x + dx + ww / 2,
                            0f,
                            intArrayOf(dark, col, dark),
                            floatArrayOf(0f, 0.45f, 1f),
                            Shader.TileMode.CLAMP,
                        )
                }
            val path =
                Path().apply {
                    moveTo(x + dx - ww / 2, y)
                    lineTo(x + dx - ww / 2, y - hh + ww / 2)
                    arcTo(RectF(x + dx - ww / 2, y - hh, x + dx + ww / 2, y - hh + ww), 180f, 180f)
                    lineTo(x + dx + ww / 2, y)
                    close()
                }
            cv.drawPath(path, p)
            repeat((hh / 3).toInt()) {
                val px = x + dx + r.f(-ww / 2 + 1f, ww / 2 - 2f)
                val py = y - r.f(0f, hh - 4f)
                cv.drawRect(px, py, px + 1.5f, py + 1.5f, dots)
            }
        }
    }

    private fun starfish(
        x: Float,
        y: Float,
        rad: Float,
        rot: Float,
        col: Int,
    ) {
        cv.save()
        cv.translate(x, y)
        cv.rotate(Math.toDegrees(rot.toDouble()).toFloat())
        cv.scale(1f, 0.55f)
        val p = Path()
        for (i in 0 until 10) {
            val a = (-PI / 2 + i * PI / 5).toFloat()
            val rr = if (i % 2 == 1) rad * 0.42f else rad
            if (i == 0) p.moveTo(cos(a) * rr, sin(a) * rr) else p.lineTo(cos(a) * rr, sin(a) * rr)
        }
        p.close()
        cv.drawPath(p, fill(col))
        cv.drawPath(p, stroke(Color.argb(64, 0, 0, 0), 2f))
        val d = fill(Color.argb(115, 255, 255, 255))
        repeat(14) {
            val a = r.f(0f, TAU)
            val dd = r.f(0f, rad * 0.6f)
            cv.drawCircle(cos(a) * dd, sin(a) * dd, 1.3f, d)
        }
        cv.restore()
    }

    private fun shell(
        x: Float,
        y: Float,
        rad: Float,
        rot: Float,
    ) {
        cv.save()
        cv.translate(x, y)
        cv.rotate(Math.toDegrees(rot.toDouble()).toFloat())
        cv.scale(1f, 0.6f)
        val o = RectF(-rad, -rad, rad, rad)
        cv.drawArc(
            o,
            198f,
            144f,
            true,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader =
                    LinearGradient(0f, -rad, 0f, 0f, c("#f7e6d2"), c("#d9a98a"), Shader.TileMode.CLAMP)
            },
        )
        val l = stroke(Color.argb(102, 120, 70, 40), 1.2f)
        var a = 1.15f
        while (a < 1.9f) {
            cv.drawLine(0f, 0f, cos(a * PI.toFloat()) * rad, sin(a * PI.toFloat()) * rad, l)
            a += 0.1f
        }
        cv.restore()
    }
}

private enum class FishKind { CLOWN, BLUE, YELLOW, BUTTER, ANGEL, WRASSE, GRAMMA, CHROMIS, CARDINAL }

/** One detailed side-view reef fish, facing right, in a 220 x 160 box (body 36..200 x 0..100, moved down 30) */
private object FishArt {
    private class Look(
        val body: String,
        val top: String,
        val belly: String,
        val fin: String,
        val tail: String,
        val edge: String,
        val eye: String,
        val roundTail: Boolean = false,
        val snout: Boolean = false,
        val trail: Boolean = false,
        val half: Boolean = false,
        val bars: Boolean = false,
    )

    private val looks =
        mapOf(
            FishKind.CLOWN to Look("oval", "#ff7a12", "#ffa040", "#ff7a12", "#ff7a12", "#111111", "#ff9b3a", roundTail = true),
            FishKind.BLUE to Look("oval", "#1747c9", "#3f8cff", "#14246b", "#ffd21c", "#5ab0ff", "#1a1a1a"),
            FishKind.YELLOW to Look("tall", "#ffd400", "#ffe96a", "#ffd400", "#ffd400", "#ffe96a", "#2b2b2b"),
            FishKind.BUTTER to Look("tall", "#fff3cf", "#ffe48e", "#ffcc1f", "#ffcc1f", "#222222", "#2b2b2b", snout = true),
            FishKind.ANGEL to Look("tall", "#1a3a9a", "#2e6ad6", "#1d43a8", "#ffc928", "#ffd84a", "#ffd84a", trail = true),
            FishKind.WRASSE to Look("slim", "#13b88f", "#c4f5e6", "#7fe0c4", "#ff7ab4", "#ff7ab4", "#dd2233"),
            FishKind.GRAMMA to Look("oval", "#8a3dff", "#a868ff", "#7a2cf0", "#ffd23a", "#ffe27a", "#222222", half = true),
            FishKind.CHROMIS to Look("oval", "#21b5c9", "#b8f3f5", "#3fd4df", "#3fd4df", "#bbffff", "#111111"),
            FishKind.CARDINAL to Look("tall", "#c9d3db", "#f4f7f9", "#5a6470", "#5a6470", "#ffffff", "#ffcc33", bars = true),
        )

    private fun bodyPath(kind: String): Path =
        Path().apply {
            when (kind) {
                "tall" -> {
                    moveTo(198f, 46f)
                    cubicTo(190f, 14f, 140f, 0f, 98f, 4f)
                    cubicTo(64f, 8f, 44f, 30f, 38f, 50f)
                    cubicTo(44f, 70f, 64f, 92f, 98f, 96f)
                    cubicTo(140f, 100f, 190f, 80f, 198f, 46f)
                }

                "slim" -> {
                    moveTo(198f, 50f)
                    cubicTo(188f, 32f, 140f, 28f, 96f, 30f)
                    cubicTo(66f, 32f, 46f, 42f, 38f, 50f)
                    cubicTo(46f, 58f, 66f, 68f, 96f, 70f)
                    cubicTo(140f, 72f, 188f, 66f, 198f, 50f)
                }

                else -> {
                    moveTo(196f, 50f)
                    cubicTo(190f, 20f, 130f, 10f, 90f, 14f)
                    cubicTo(62f, 18f, 44f, 34f, 38f, 50f)
                    cubicTo(44f, 66f, 62f, 82f, 90f, 86f)
                    cubicTo(130f, 90f, 190f, 80f, 196f, 50f)
                }
            }
            close()
        }

    private fun fin(
        cv: Canvas,
        path: Path,
        col: Int,
        rx: Float,
        ry: Float,
        edge: Int?,
    ) {
        cv.drawPath(path, fill(ca(col, 0.92f)))
        cv.save()
        cv.clipPath(path)
        val ray = stroke(Color.argb(51, 0, 0, 0), 1f)
        var a = 0f
        while (a < TAU) {
            cv.drawLine(rx, ry, rx + cos(a) * 200f, ry + sin(a) * 200f, ray)
            a += 0.09f
        }
        cv.restore()
        if (edge != null) cv.drawPath(path, stroke(edge, 2f))
    }

    fun draw(
        cv: Canvas,
        w: Float,
        h: Float,
        kind: FishKind,
        tail: Float,
    ) {
        val o = looks.getValue(kind)
        val r = Random(kind.ordinal * 31 + 7)
        cv.save()
        cv.scale(w / 220f, h / 160f)
        cv.translate(10f, 30f)
        val ty = tail * 7f
        val tailPath =
            Path().apply {
                if (o.roundTail) {
                    moveTo(42f, 50f)
                    cubicTo(26f, 28f, 2f, 26f + ty, 2f, 50f + ty)
                    cubicTo(2f, 74f + ty, 26f, 72f, 42f, 50f)
                } else {
                    moveTo(42f, 50f)
                    cubicTo(30f, 40f, 20f, 30f, 4f, 18f + ty)
                    cubicTo(12f, 36f, 14f, 44f, 16f, 50f + ty * 0.5f)
                    cubicTo(14f, 56f, 12f, 64f, 4f, 82f + ty)
                    cubicTo(20f, 70f, 30f, 60f, 42f, 50f)
                }
                close()
            }
        fin(cv, tailPath, c(o.tail), 46f, 50f, c(o.edge))
        val dorsal =
            Path().apply {
                when {
                    o.trail -> {
                        moveTo(160f, 12f)
                        cubicTo(120f, -18f, 70f, -30f, 22f, -28f)
                        cubicTo(54f, -14f, 72f, 0f, 84f, 14f)
                    }

                    o.body == "tall" -> {
                        moveTo(170f, 18f)
                        cubicTo(140f, -6f, 90f, -8f, 60f, 18f)
                        lineTo(84f, 12f)
                    }

                    o.body == "slim" -> {
                        moveTo(170f, 32f)
                        cubicTo(140f, 20f, 90f, 20f, 60f, 34f)
                    }

                    o.roundTail -> {
                        moveTo(66f, 24f)
                        cubicTo(76f, 4f, 108f, 2f, 122f, 16f)
                        cubicTo(134f, 6f, 160f, 8f, 168f, 24f)
                    }

                    else -> {
                        moveTo(66f, 22f)
                        cubicTo(86f, 2f, 140f, 2f, 170f, 22f)
                    }
                }
                close()
            }
        val anal =
            Path().apply {
                when {
                    o.trail -> {
                        moveTo(160f, 88f)
                        cubicTo(120f, 118f, 70f, 130f, 22f, 128f)
                        cubicTo(54f, 114f, 72f, 100f, 84f, 86f)
                    }

                    o.body == "tall" -> {
                        moveTo(160f, 84f)
                        cubicTo(130f, 108f, 90f, 108f, 62f, 82f)
                    }

                    o.body == "slim" -> {
                        moveTo(160f, 68f)
                        cubicTo(130f, 80f, 90f, 80f, 66f, 66f)
                    }

                    else -> {
                        moveTo(150f, 82f)
                        cubicTo(130f, 100f, 96f, 100f, 78f, 80f)
                    }
                }
                close()
            }
        fin(cv, dorsal, c(o.fin), 120f, 60f, c(o.edge))
        fin(cv, anal, c(o.fin), 120f, 40f, c(o.edge))

        val body = bodyPath(o.body)
        cv.drawPath(
            body,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader =
                    LinearGradient(0f, 0f, 0f, 100f, c(o.top), c(o.belly), Shader.TileMode.CLAMP)
            },
        )
        cv.save()
        cv.clipPath(body)
        if (o.half) {
            cv.drawRect(
                0f,
                0f,
                220f,
                100f,
                Paint().apply {
                    shader =
                        LinearGradient(
                            40f,
                            0f,
                            196f,
                            0f,
                            intArrayOf(c("#ffd23a"), c("#ffcf3a"), c(o.top), c(o.top)),
                            floatArrayOf(0f, 0.42f, 0.55f, 1f),
                            Shader.TileMode.CLAMP,
                        )
                },
            )
        }
        when (kind) {
            FishKind.CLOWN -> {
                for ((x, rw) in listOf(164f to 8f, 114f to 11f, 62f to 7f)) {
                    val e = RectF(x - rw, -4f, x + rw, 104f)
                    cv.drawOval(e, fill(Color.WHITE))
                    cv.drawOval(e, stroke(c("#141414"), 3f))
                }
            }

            FishKind.BLUE -> {
                val swirl =
                    Path().apply {
                        moveTo(186f, 28f)
                        cubicTo(150f, 14f, 100f, 22f, 62f, 44f)
                        cubicTo(96f, 36f, 112f, 46f, 120f, 56f)
                        cubicTo(130f, 40f, 160f, 34f, 186f, 40f)
                        close()
                    }
                cv.drawPath(swirl, fill(c("#0b1640")))
                cv.save()
                cv.rotate(-11.5f, 132f, 48f)
                cv.drawOval(RectF(118f, 41f, 146f, 55f), fill(c(o.top)))
                cv.restore()
            }

            FishKind.YELLOW -> {
                cv.drawOval(RectF(42f, 48f, 50f, 52f), fill(Color.argb(230, 255, 255, 255)))
            }

            FishKind.BUTTER -> {
                val l = stroke(Color.argb(89, 150, 90, 0), 1.5f)
                var i = -60f
                while (i < 200f) {
                    cv.drawLine(i, 0f, i + 60f, 100f, l)
                    i += 9f
                }
                cv.save()
                cv.rotate(5.7f, 176f, 46f)
                cv.drawOval(RectF(169f, -14f, 183f, 106f), fill(c("#16161c")))
                cv.restore()
                cv.drawCircle(70f, 26f, 8f, fill(c("#16161c")))
                cv.drawCircle(70f, 26f, 8f, stroke(Color.WHITE, 2f))
            }

            FishKind.ANGEL -> {
                val l = stroke(c("#ffd84a"), 3f)
                var y = 10f
                while (y < 100f) {
                    cv.drawPath(
                        Path().apply {
                            moveTo(40f, y)
                            cubicTo(90f, y - 6f, 130f, y + 6f, 196f, y - 2f)
                        },
                        l,
                    )
                    y += 11f
                }
                cv.drawRect(166f, 0f, 180f, 100f, fill(c("#0c1430")))
            }

            FishKind.WRASSE -> {
                val l = stroke(c("#ff6fb0"), 2.5f)
                for (y in listOf(40f, 50f, 60f)) {
                    cv.drawPath(
                        Path().apply {
                            moveTo(40f, y)
                            cubicTo(90f, y - 4f, 140f, y + 4f, 196f, y)
                        },
                        l,
                    )
                }
            }

            FishKind.CARDINAL -> {
                val b = fill(c("#15181d"))
                for ((x, w0) in listOf(172f to 7f, 124f to 9f, 62f to 10f)) cv.drawRect(x - w0 / 2, 0f, x + w0 / 2, 100f, b)
                val d = fill(Color.argb(217, 255, 255, 255))
                repeat(18) { cv.drawCircle(r.f(80f, 160f), r.f(15f, 85f), 1.4f, d) }
            }

            else -> {}
        }
        // scales
        val lightScale = stroke(Color.argb(31, 255, 255, 255), 1f)
        val darkScale = stroke(Color.argb(20, 0, 0, 0), 1f)
        var row = 0
        var sy = 8f
        while (sy < 96f) {
            var sx = 52f + (row % 2) * 4.5f
            while (sx < 186f) {
                cv.drawArc(RectF(sx - 4.5f, sy - 4.5f, sx + 4.5f, sy + 4.5f), 54f, 72f, false, lightScale)
                cv.drawArc(RectF(sx - 4.5f, sy - 3.5f, sx + 4.5f, sy + 5.5f), 180f, 54f, false, darkScale)
                sx += 9f
            }
            sy += 7f
            row++
        }
        // side line
        val side = fill(Color.argb(89, 255, 255, 255))
        var lx = 60f
        while (lx < 165f) {
            cv.drawCircle(lx, 40f - sin((lx - 60f) / 105f * PI.toFloat()) * 10f, 0.9f, side)
            lx += 5f
        }
        cv.drawRect(
            0f,
            0f,
            220f,
            100f,
            Paint().apply {
                shader =
                    LinearGradient(
                        0f,
                        30f,
                        0f,
                        60f,
                        intArrayOf(Color.argb(0, 140, 220, 255), Color.argb(31, 140, 220, 255), Color.argb(0, 140, 220, 255)),
                        null,
                        Shader.TileMode.CLAMP,
                    )
            },
        )
        cv.drawRect(
            0f,
            0f,
            220f,
            100f,
            Paint().apply {
                shader =
                    RadialGradient(150f, 28f, 80f, Color.argb(107, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP)
            },
        )
        cv.drawRect(
            0f,
            0f,
            220f,
            100f,
            Paint().apply {
                shader =
                    LinearGradient(0f, 40f, 0f, 100f, Color.argb(0, 0, 0, 0), Color.argb(71, 0, 0, 0), Shader.TileMode.CLAMP)
            },
        )
        cv.drawRect(
            0f,
            0f,
            220f,
            100f,
            Paint().apply {
                shader =
                    LinearGradient(38f, 0f, 110f, 0f, Color.argb(64, 0, 0, 0), Color.argb(0, 0, 0, 0), Shader.TileMode.CLAMP)
            },
        )
        cv.restore()
        cv.drawPath(body, stroke(Color.argb(89, 0, 0, 0), 1.5f))
        if (o.snout) cv.drawOval(RectF(191f, 44.5f, 209f, 51.5f), fill(c(o.belly)))
        cv.drawPath(
            Path().apply {
                moveTo(160f, 26f)
                cubicTo(150f, 40f, 150f, 60f, 160f, 72f)
            },
            stroke(Color.argb(64, 0, 0, 0), 2f),
        )
        val fl = -tail * 3f
        val pec =
            Path().apply {
                moveTo(150f, 58f)
                cubicTo(130f, 50f + fl, 116f, 60f + fl, 114f, 74f + fl)
                cubicTo(130f, 74f, 146f, 68f, 150f, 58f)
                close()
            }
        fin(cv, pec, c(o.fin), 150f, 60f, null)
        cv.drawCircle(178f, 40f, 7.5f, fill(c(o.eye)))
        cv.drawCircle(179f, 40f, 5f, fill(c("#08090f")))
        cv.drawCircle(181f, 38f, 1.8f, fill(Color.WHITE))
        val mx = if (o.snout) 209f else 196f
        cv.drawLine(mx, 52f, mx - if (o.snout) 6f else 7f, 54f, stroke(Color.argb(115, 0, 0, 0), 1.5f))
        cv.restore()
    }
}
