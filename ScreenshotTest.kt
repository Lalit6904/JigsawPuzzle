package com.lalit.jigsaw

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {

    private val outDir = File("build/reports/screenshots").apply { mkdirs() }

    @Test
    fun menuScreen() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        idle(1500)
        shot(activity, "01-menu")
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    fun landscapeScreen() {
        val activity = launchGame("shapes", Difficulty.MEDIUM.name)
        idle(1200)
        shot(activity, "06-landscape")
    }

    @Test
    fun gameScreen() {
        val activity = launchGame("dusk", Difficulty.MEDIUM.name)
        idle(1200)
        shot(activity, "02-game-start")
    }

    @Test
    fun expertScreen() {
        val activity = launchGame("night", Difficulty.EXPERT.name)
        idle(1200)
        shot(activity, "02b-expert-start")
    }

    @Test
    fun hintScreen() {
        val activity = launchGame("sea", Difficulty.HARD.name)
        idle(1200)
        val pv = findPuzzle(activity.findViewById(android.R.id.content))
        pv?.hint()
        if (pv != null) {
            val hf = PuzzleView::class.java.getDeclaredField("hinted").apply { isAccessible = true }
            val tf = PuzzleView::class.java.getDeclaredField("hintT").apply { isAccessible = true }
            val af = PuzzleView::class.java.getDeclaredField("hintAnimator").apply { isAccessible = true }
            val target = hf.get(pv)
            println("hinted immediately = ${target != null}")
            // Robolectric jumps animators to their end state, so stop the pulse
            // and hold it mid-way to capture what the player actually sees.
            (af.get(pv) as android.animation.ValueAnimator?)?.cancel()
            hf.set(pv, target)
            tf.setFloat(pv, 0.5f)
            pv.invalidate()
        }
        idle(150)
        shot(activity, "02c-hint")
    }

    @Test
    fun solvingThePuzzle() {
        val activity = launchGame("bloom", Difficulty.EASY.name)
        idle(1200)

        val puzzle = activity.findViewById<View>(android.R.id.content)
            .let { findPuzzle(it) } ?: error("puzzle view not attached")

        val pieces = piecesOf(puzzle)
        println("pieces=${pieces.size} placedBefore=${puzzle.placed}")

        var first = true
        for (p in pieces) {
            front(puzzle, p)
            dragToCorrect(puzzle, p)
            idle(260)
            if (first) {
                first = false
                shot(activity, "03-game-one-placed")
            }
        }

        idle(2500)
        println("placed=${puzzle.placed} solved=${puzzle.isSolved}")
        shot(activity, "04-game-solved")
        idle(1500)
        shot(activity, "05-result-card")

        val again = findButton(activity.window.decorView, "Play again") ?: error("no replay button")
        again.performClick()
        idle(1400)
        println("after restart placed=${puzzle.placed} solved=${puzzle.isSolved}")
        shot(activity, "07-after-restart")
    }

    @Test
    fun toolsToggle() {
        val activity = launchGame("dusk", Difficulty.HARD.name)
        idle(1200)
        val guide = findButton(activity.window.decorView, "Guide") ?: error("no guide button")
        guide.performClick()
        idle(300)
        shot(activity, "08-guide-off")
        val mix = findButton(activity.window.decorView, "Mix") ?: error("no mix button")
        mix.performClick()
        idle(300)
        println("mix ok")
    }

    // ------------------------------------------------------------- helpers

    private fun launchGame(art: String, difficulty: String): GameActivity {
        val intent = Intent(
            org.robolectric.RuntimeEnvironment.getApplication(),
            GameActivity::class.java
        ).putExtra(GameActivity.EXTRA_ART, art)
            .putExtra(GameActivity.EXTRA_DIFFICULTY, difficulty)
        return Robolectric.buildActivity(GameActivity::class.java, intent).setup().get()
    }

    private fun findPuzzle(view: View): PuzzleView? {
        if (view is PuzzleView) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) findPuzzle(view.getChildAt(i))?.let { return it }
        }
        return null
    }

    private fun findButton(view: View, label: String): PillButton? {
        if (view is PillButton && view.text == label) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) findButton(view.getChildAt(i), label)?.let { return it }
        }
        return null
    }

    @Suppress("UNCHECKED_CAST")
    private fun piecesOf(view: PuzzleView): List<Piece> {
        val field = PuzzleView::class.java.getDeclaredField("pieces")
        field.isAccessible = true
        return ArrayList(field.get(view) as List<Piece>)
    }

    /** Raises a piece above the pile, like a player lifting it out. */
    private fun front(view: PuzzleView, piece: Piece) {
        val zTop = view.javaClass.getDeclaredField("zTop").apply { isAccessible = true }
        val next = (zTop.getInt(view) + 1)
        zTop.setInt(view, next)
        piece.z = next
        val order = view.javaClass.getDeclaredField("renderOrder").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val list = order.get(view) as MutableList<Piece>
        list.remove(piece)
        list.add(piece)
    }

    private fun dragToCorrect(view: PuzzleView, piece: Piece) {
        val boardSide = boardSideOf(view)
        val left = boardLeftOf(view)
        val top = boardTopOf(view)
        val grabX = left + (piece.x + piece.touch.centerX()) * boardSide
        val grabY = top + (piece.y + piece.touch.centerY()) * boardSide
        val targetX = left + (piece.correctX + piece.touch.centerX()) * boardSide
        val targetY = top + (piece.correctY + piece.touch.centerY()) * boardSide

        val t0 = android.os.SystemClock.uptimeMillis()
        send(view, t0, MotionEvent.ACTION_DOWN, grabX, grabY)
        var step = 1
        val steps = 6
        while (step <= steps) {
            val f = step.toFloat() / steps
            send(
                view, t0 + step * 16L, MotionEvent.ACTION_MOVE,
                grabX + (targetX - grabX) * f, grabY + (targetY - grabY) * f
            )
            step++
        }
        send(view, t0 + (steps + 1) * 16L, MotionEvent.ACTION_UP, targetX, targetY)
    }

    private fun send(view: View, time: Long, action: Int, x: Float, y: Float) {
        val e = MotionEvent.obtain(time, time, action, x, y, 0)
        view.dispatchTouchEvent(e)
        e.recycle()
    }

    private fun boardSideOf(view: PuzzleView): Float = field(view, "boardSide")
    private fun boardLeftOf(view: PuzzleView): Float =
        (view.javaClass.getDeclaredField("boardRect").apply { isAccessible = true }
            .get(view) as android.graphics.RectF).left
    private fun boardTopOf(view: PuzzleView): Float =
        (view.javaClass.getDeclaredField("boardRect").apply { isAccessible = true }
            .get(view) as android.graphics.RectF).top

    private fun field(view: PuzzleView, name: String): Float =
        (view.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(view) as Float)

    private fun idle(ms: Long) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    }

    private fun shot(activity: Activity, name: String) {
        val decor = activity.window.decorView
        if (decor.width == 0 || decor.height == 0) {
            val w = 1080
            val h = 2340
            decor.measure(
                View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
            )
            decor.layout(0, 0, w, h)
        }
        idle(120)
        val bmp = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
        decor.draw(Canvas(bmp))
        val file = File(outDir, "$name.png")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("screenshot -> ${file.absolutePath} (${decor.width}x${decor.height})")
    }
}
