package com.lalit.jigsaw

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class PuzzleView(
    context: Context,
    private val artwork: Artwork,
    private val difficulty: Difficulty
) : View(context) {

    interface Listener {
        /** The player picked up the first piece. */
        fun onFirstPick()

        fun onProgress(placed: Int, total: Int)

        /** Every piece is in place. */
        fun onSolved()
    }

    var listener: Listener? = null

    /** true = piece snapped into place, false = puzzle finished. */
    var soundListener: ((Boolean) -> Unit)? = null

    private var bitmap: Bitmap? = null
    private var geometry: BoardGeometry? = null
    private var cutSeed = 17L
    private val pieces = ArrayList<Piece>()
    private val renderOrder = ArrayList<Piece>()

    private val boardRect = RectF()
    private val tableRect = RectF()
    private val boardClip = Path()
    private var boardSide = 0f
    private var contentTop = 0f
    private var contentBottom = 0f
    private var insetsReady = false

    private var zTop = 0
    private var placedCount = 0
    private var solved = false
    private var showGuide = true

    private var drag: Piece? = null
    private var dragOffX = 0f
    private var dragOffY = 0f
    private var downX = 0f
    private var downY = 0f

    private var hinted: Piece? = null
    private var hintT = 0f
    private var hintAnimator: ValueAnimator? = null

    private var introT = 0f
    private var introRunning = false
    private var introAnimator: ValueAnimator? = null

    private var revealT = 1f
    private var revealAnimator: ValueAnimator? = null

    private val confetti = ArrayList<Particle>()
    private var lastFrameNanos = 0L

    private val bgPaint = Paint()
    private val boardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val boardStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val imagePaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hintFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hintStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val srcRect = Rect()

    private val dp2 = context.dp(2f)
    private val dp4 = context.dp(4f)
    private val boardRadius = context.dp(14f)

    val total: Int get() = difficulty.count
    val placed: Int get() = placedCount
    val isSolved: Boolean get() = solved

    fun setContentInsets(top: Float, bottom: Float) {
        if (insetsReady && contentTop == top && contentBottom == bottom) return
        contentTop = top
        contentBottom = bottom
        insetsReady = true
        if (width > 0 && height > 0) {
            layoutBoard()
            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        bgPaint.shader = LinearGradient(
            0f, 0f, w * 0.45f, h.toFloat(),
            intArrayOf(Palette.BG_TOP, Palette.BG_BOTTOM),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
        )
        layoutBoard()
    }

    // ---------------------------------------------------------------- layout

    private fun layoutBoard() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val margin = context.dp(12f)
        val bottomLimit = if (contentBottom > 0f) contentBottom else h
        val top = (if (contentTop > 0f) contentTop else 0f) + context.dp(4f)
        val availH = (bottomLimit - top).coerceAtLeast(context.dp(220f))
        val wide = w > h * 1.08f

        if (!wide) {
            // The tray needs room too: a board that eats the whole screen makes
            // the loose pieces pile up on top of each other.
            var side = min(w - margin * 2f, availH * 0.48f)
            val minTable = context.dp(190f)
            if (availH - side < minTable) side = max(context.dp(220f), availH - minTable)
            side = min(side, w - margin * 2f)
            val left = (w - side) / 2f
            boardRect.set(left, top, left + side, top + side)
            tableRect.set(0f, boardRect.bottom + context.dp(8f), w, bottomLimit)
        } else {
            val side = min(availH * 0.94f, w * 0.54f)
            val left = margin
            val bTop = top + (availH - side) / 2f
            boardRect.set(left, bTop, left + side, bTop + side)
            tableRect.set(boardRect.right + context.dp(12f), top, w, bottomLimit)
        }

        boardSide = boardRect.width()
        boardClip.reset()
        boardClip.addRoundRect(boardRect, boardRadius, boardRadius, Path.Direction.CW)

        val current = geometry
        if (current == null) {
            // wait until the HUD has told us where the content area starts
            if (insetsReady) build()
        } else if (abs(current.size - boardSide) > 0.5f) {
            rebuildGeometry()
        }
    }

    private fun build() {
        val src = ArtCache.get(resources, artwork.res, 1200)
        bitmap = src
        srcRect.set(0, 0, src.width, src.height)

        cutSeed = Random(System.nanoTime()).nextLong()
        val geo = BoardGeometry(difficulty.cols, difficulty.rows, boardSide, cutSeed)
        geometry = geo
        pieces.clear()
        pieces.addAll(geo.pieces)
        placedCount = 0
        solved = false
        zTop = 0
        scatter(pieces, Random(System.nanoTime()))
        refreshOrder()
        startIntro()
        listener?.onProgress(0, total)
    }

    /** Rebuild outlines for a new board size, keeping the cut pattern and progress. */
    private fun rebuildGeometry() {
        val geo = BoardGeometry(difficulty.cols, difficulty.rows, boardSide, cutSeed)
        val byKey = HashMap<Int, Piece>()
        geo.pieces.forEach { byKey[it.row * difficulty.cols + it.col] = it }
        for (old in pieces) {
            val fresh = byKey[old.row * difficulty.cols + old.col] ?: continue
            fresh.x = old.x
            fresh.y = old.y
            fresh.placed = old.placed
            fresh.z = old.z
        }
        pieces.clear()
        pieces.addAll(geo.pieces)
        geometry = geo
        scatter(pieces.filter { !it.placed }, Random(7))
        refreshOrder()
    }

    /** Placed pieces first (bottom), then loose ones by stacking order. */
    private fun refreshOrder() {
        renderOrder.clear()
        renderOrder.addAll(pieces)
        renderOrder.sortWith(
            compareBy<Piece> { if (it.placed) 0 else 1 }.thenBy { it.z }
        )
    }

    // --------------------------------------------------------------- scatter

    private fun scatter(list: List<Piece>, rnd: Random) {
        if (list.isEmpty() || boardSide <= 0f) return
        val pad = context.dp(6f)
        val usableW = (tableRect.width() - pad * 2f).coerceAtLeast(1f)
        val usableH = (tableRect.height() - pad * 2f).coerceAtLeast(1f)
        val n = list.size
        val aspect = (usableW / usableH).coerceAtLeast(0.4f)
        var gCols = max(1, sqrt(n * aspect).roundToInt())
        gCols = min(gCols, n)
        val gRows = max(1, ceil(n.toFloat() / gCols).toInt())
        val cw = usableW / gCols
        val ch = usableH / gRows

        list.shuffled(rnd).forEachIndexed { index, p ->
            val bw = p.touch.width() * boardSide
            val bh = p.touch.height() * boardSide
            val gx = index % gCols
            val gy = index / gCols
            // Pieces are usually wider than their grid cell, so keep a little
            // jitter even then — a perfectly aligned grid looks like a second board.
            val jx = max(cw * 0.11f, (cw - bw) * 0.45f)
            val jy = max(ch * 0.11f, (ch - bh) * 0.45f)
            val cx = tableRect.left + pad + (gx + 0.5f) * cw + (rnd.nextFloat() * 2f - 1f) * jx
            val cy = tableRect.top + pad + (gy + 0.5f) * ch + (rnd.nextFloat() * 2f - 1f) * jy

            var left = cx - bw / 2f
            var top = cy - bh / 2f
            val minLeft = tableRect.left + pad - p.touch.left * boardSide
            val maxLeft = tableRect.right - pad - p.touch.right * boardSide
            val minTop = tableRect.top + pad - p.touch.top * boardSide
            val maxTop = tableRect.bottom - pad - p.touch.bottom * boardSide
            left = if (maxLeft >= minLeft) left.coerceIn(minLeft, maxLeft) else (minLeft + maxLeft) / 2f
            top = if (maxTop >= minTop) top.coerceIn(minTop, maxTop) else (minTop + maxTop) / 2f
            p.x = (left - boardRect.left) / boardSide
            p.y = (top - boardRect.top) / boardSide
            p.z = ++zTop
        }
    }

    private fun keepOnScreen(p: Piece) {
        val cell = min(boardSide / difficulty.cols, boardSide / difficulty.rows)
        val minVis = context.dp(26f)
        val left = boardRect.left + p.x * boardSide
        val top = boardRect.top + p.y * boardSide
        if (left > width - minVis) p.x = (width - minVis - boardRect.left) / boardSide
        if (left + cell < minVis) p.x = (minVis - cell - boardRect.left) / boardSide
        if (top > height - minVis) p.y = (height - minVis - boardRect.top) / boardSide
        if (top + cell < minVis) p.y = (minVis - cell - boardRect.top) / boardSide
    }

    // ----------------------------------------------------------- interaction

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (introRunning || solved || boardSide <= 0f) return false
        val ux = (event.x - boardRect.left) / boardSide
        val uy = (event.y - boardRect.top) / boardSide

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val hit = findPiece(event.x, event.y) ?: return false
                drag = hit
                hit.z = ++zTop
                dragOffX = hit.x - ux
                dragOffY = hit.y - uy
                downX = event.x
                downY = event.y
                refreshOrder()
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val p = drag ?: return false
                p.x = ux + dragOffX
                p.y = uy + dragOffY
                keepOnScreen(p)
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val p = drag ?: return false
                drag = null
                val moved = abs(event.x - downX) > dp2 || abs(event.y - downY) > dp2
                if (moved) trySnap(p)
                refreshOrder()
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    /**
     * Picks the topmost piece whose actual outline (not just its bounding box)
     * sits under the finger, so tabs and notches feel solid.
     */
    private fun findPiece(xPx: Float, yPx: Float): Piece? {
        for (i in renderOrder.indices.reversed()) {
            val p = renderOrder[i]
            if (p.placed) continue
            val ux = (xPx - boardRect.left) / boardSide
            val uy = (yPx - boardRect.top) / boardSide
            if (ux < p.x + p.touch.left || ux > p.x + p.touch.right ||
                uy < p.y + p.touch.top || uy > p.y + p.touch.bottom
            ) continue
            val lx = (xPx - boardRect.left - p.x * boardSide).toInt()
            val ly = (yPx - boardRect.top - p.y * boardSide).toInt()
            if (p.hit.contains(lx, ly)) return p
        }
        return null
    }

    private fun trySnap(p: Piece) {
        val dx = (p.x - p.correctX) * boardSide
        val dy = (p.y - p.correctY) * boardSide
        val cell = boardSide / difficulty.cols
        if (hypot(dx, dy) <= cell * 0.36f) {
            val fromX = p.x
            val fromY = p.y
            p.placed = true
            p.z = 0
            placedCount++
            listener?.onProgress(placedCount, total)
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            soundListener?.invoke(true)
            if (hinted === p) clearHint()
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 140
                interpolator = DecelerateInterpolator(1.6f)
                addUpdateListener {
                    val t = it.animatedValue as Float
                    p.x = fromX + (p.correctX - fromX) * t
                    p.y = fromY + (p.correctY - fromY) * t
                    invalidate()
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        p.x = p.correctX
                        p.y = p.correctY
                        refreshOrder()
                        invalidate()
                        if (placedCount == total && !solved) finish()
                    }
                })
            }.start()
        }
    }

    private fun finish() {
        solved = true
        revealT = 0f
        revealAnimator?.cancel()
        revealAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 650
            addUpdateListener {
                revealT = it.animatedValue as Float
                invalidate()
            }
        }.also { it.start() }
        spawnConfetti()
        soundListener?.invoke(false)
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        listener?.onSolved()
    }

    private fun clearHint() {
        hinted = null
        hintAnimator?.cancel()
        hintT = 0f
        invalidate()
    }

    fun hint() {
        if (solved) return
        val candidate = pieces.filter { !it.placed }.maxByOrNull { it.z } ?: return
        clearHint()
        hinted = candidate
        hintAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1700
            addUpdateListener {
                hintT = it.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    hinted = null
                    hintT = 0f
                    invalidate()
                }
            })
        }.also { it.start() }
    }

    fun setGuide(enabled: Boolean) {
        showGuide = enabled
        invalidate()
    }

    fun isGuideOn(): Boolean = showGuide

    fun shuffle() {
        if (solved || introRunning) return
        val loose = pieces.filter { !it.placed }
        if (loose.isEmpty()) return
        scatter(loose, Random(System.nanoTime()))
        clearHint()
        refreshOrder()
        invalidate()
    }

    fun restart() {
        revealAnimator?.cancel()
        introAnimator?.cancel()
        hintAnimator?.cancel()
        hinted = null
        hintT = 0f
        confetti.clear()
        revealT = 1f
        build()
        invalidate()
    }

    // ------------------------------------------------------------- animation

    private fun startIntro() {
        introRunning = true
        introT = 0f
        introAnimator?.cancel()
        introAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 800
            interpolator = DecelerateInterpolator(1.2f)
            addUpdateListener {
                introT = it.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    introRunning = false
                    introT = 1f
                    invalidate()
                }
            })
        }.also { it.start() }
    }

    private fun introProgress(p: Piece): Float {
        if (!introRunning) return 1f
        val n = pieces.size.coerceAtLeast(1)
        val delay = (p.z % n).toFloat() / n * 0.45f
        return ((introT - delay) / 0.55f).coerceIn(0f, 1f)
    }

    // --------------------------------------------------------------- drawing

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        val bmp = bitmap ?: return
        if (boardSide <= 0f) return

        drawBoard(canvas, bmp)
        drawHint(canvas)
        for (p in renderOrder) drawPiece(canvas, bmp, p)
        drawConfetti(canvas)
        if (confetti.isNotEmpty()) postInvalidateOnAnimation()
    }

    private fun drawBoard(canvas: Canvas, bmp: Bitmap) {
        boardPaint.color = Palette.BOARD
        canvas.drawRoundRect(boardRect, boardRadius, boardRadius, boardPaint)

        if (showGuide || solved) {
            canvas.save()
            canvas.clipPath(boardClip)
            imagePaint.alpha = if (solved) 255 else 46
            canvas.drawBitmap(bmp, null, boardRect, imagePaint)
            imagePaint.alpha = 255
            canvas.restore()
        }

        boardStroke.color = 0x2EFFFFFF
        boardStroke.strokeWidth = context.dp(1f)
        canvas.drawRoundRect(boardRect, boardRadius, boardRadius, boardStroke)

        if (solved && revealT < 1f) {
            boardPaint.color = Color.argb(((1f - revealT) * 90).toInt(), 255, 255, 255)
            canvas.drawRoundRect(boardRect, boardRadius, boardRadius, boardPaint)
        }
    }

    private fun drawHint(canvas: Canvas) {
        val p = hinted ?: return
        val pulse = sin(hintT * Math.PI).toFloat().coerceIn(0f, 1f)
        if (pulse <= 0.01f) return

        canvas.save()
        canvas.translate(boardRect.left + p.correctX * boardSide, boardRect.top + p.correctY * boardSide)
        hintFill.color = artwork.accent.withAlpha(0.20f * pulse)
        canvas.drawPath(p.path, hintFill)
        hintStroke.color = artwork.accent.withAlpha(0.95f * pulse)
        hintStroke.strokeWidth = context.dp(2.4f)
        canvas.drawPath(p.path, hintStroke)
        canvas.restore()

        canvas.save()
        canvas.translate(boardRect.left + p.x * boardSide, boardRect.top + p.y * boardSide)
        hintStroke.color = artwork.accent.withAlpha(0.85f * pulse)
        canvas.drawPath(p.path, hintStroke)
        canvas.restore()
    }

    private fun drawPiece(canvas: Canvas, bmp: Bitmap, p: Piece) {
        val t = introProgress(p)
        val eased = easeOutCubic(t)
        val px = if (introRunning) p.correctX + (p.x - p.correctX) * eased else p.x
        val py = if (introRunning) p.correctY + (p.y - p.correctY) * eased else p.y
        val scale = when {
            p === drag -> 1.05f
            introRunning -> 0.62f + 0.38f * eased
            else -> 1f
        }
        val alpha = if (introRunning) t else 1f
        val pivotX = boardSide / difficulty.cols / 2f
        val pivotY = boardSide / difficulty.rows / 2f

        canvas.save()
        canvas.translate(boardRect.left + px * boardSide, boardRect.top + py * boardSide)
        if (scale != 1f) canvas.scale(scale, scale, pivotX, pivotY)

        if (!p.placed) {
            val strength = if (p === drag) 0.30f else 0.15f
            shadowPaint.color = Color.argb((255 * strength * alpha).toInt(), 0, 0, 0)
            canvas.save()
            canvas.translate(0f, dp4)
            canvas.drawPath(p.path, shadowPaint)
            canvas.restore()
        }

        canvas.save()
        canvas.clipPath(p.path)
        imagePaint.alpha = (255 * alpha).toInt()
        canvas.drawBitmap(bmp, srcRect, p.dst, imagePaint)
        imagePaint.alpha = 255
        canvas.restore()

        val edge = if (p.placed) 0.13f else 0.40f
        edgePaint.color = Color.argb((255 * edge * alpha).toInt(), 255, 255, 255)
        edgePaint.strokeWidth = if (p === drag) context.dp(1.8f) else context.dp(1.1f)
        canvas.drawPath(p.path, edgePaint)
        canvas.restore()
    }

    private fun easeOutCubic(t: Float): Float {
        val u = 1f - t
        return 1f - u * u * u
    }

    // -------------------------------------------------------------- confetti

    private class Particle(
        var x: Float, var y: Float,
        var vx: Float, var vy: Float,
        var rot: Float, var vrot: Float,
        var w: Float, var h: Float, var color: Int
    )

    private fun spawnConfetti() {
        confetti.clear()
        val rnd = Random(System.nanoTime())
        val colors = intArrayOf(
            0xFFFFB86B.toInt(), 0xFFFF7E6B.toInt(), 0xFF7DDBB0.toInt(),
            0xFF8FB8FF.toInt(), 0xFFF7D278.toInt(), 0xFFE58FD0.toInt()
        )
        for (i in 0 until 150) {
            confetti.add(
                Particle(
                    x = rnd.nextFloat() * width,
                    y = boardRect.top - rnd.nextFloat() * context.dp(140f),
                    vx = (rnd.nextFloat() - 0.5f) * context.dp(190f),
                    vy = rnd.nextFloat() * context.dp(110f) + context.dp(40f),
                    rot = rnd.nextFloat() * 360f,
                    vrot = (rnd.nextFloat() - 0.5f) * 500f,
                    w = context.dp(4f + rnd.nextFloat() * 7f),
                    h = context.dp(3f + rnd.nextFloat() * 5f),
                    color = colors[rnd.nextInt(colors.size)]
                )
            )
        }
        lastFrameNanos = System.nanoTime()
    }

    private fun drawConfetti(canvas: Canvas) {
        if (confetti.isEmpty()) return
        val now = System.nanoTime()
        val dt = if (lastFrameNanos == 0L) 0.016f else ((now - lastFrameNanos) / 1e9f).coerceIn(0f, 0.05f)
        lastFrameNanos = now
        val gravity = context.dp(1500f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        var alive = 0
        for (p in confetti) {
            p.vy += gravity * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.rot += p.vrot * dt
            if (p.y > height + context.dp(40f)) continue
            alive++
            paint.color = p.color
            canvas.save()
            canvas.translate(p.x, p.y)
            canvas.rotate(p.rot)
            canvas.drawRect(-p.w / 2f, -p.h / 2f, p.w / 2f, p.h / 2f, paint)
            canvas.restore()
        }
        if (alive == 0) confetti.clear()
    }
}
