package com.lalit.jigsaw

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A FrameLayout that always measures square. */
class SquareFrameLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, widthMeasureSpec)
    }
}

/** Rounded preview of the selected artwork, with a caption scrim. */
class ArtPreview @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var current: Artwork? = null
    private var previous: Artwork? = null
    private var fade = 1f
    private var fadeAnimator: ValueAnimator? = null

    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val clip = Path()
    private val dst = RectF()
    private val radius = context.dp(20f)

    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.TEXT
        typeface = Type.title
        textSize = context.sp(21f)
    }
    private val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCCFFFFFF.toInt()
        typeface = Type.body
        textSize = context.sp(12f)
    }
    private val scrimPaint = Paint()
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x22FFFFFF
        strokeWidth = context.dp(1f)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, widthMeasureSpec)
    }

    fun setArt(art: Artwork) {
        if (current?.id == art.id) return
        previous = current
        current = art
        fade = 0f
        fadeAnimator?.cancel()
        fadeAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 280
            addUpdateListener {
                fade = it.animatedValue as Float
                invalidate()
            }
        }.also { it.start() }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = RectF(0f, 0f, w, h)
        clip.reset()
        clip.addRoundRect(r, radius, radius, Path.Direction.CW)

        canvas.save()
        canvas.clipPath(clip)
        // backdrop
        bmpPaint.color = Palette.SURFACE
        canvas.drawRect(r, bmpPaint)
        bmpPaint.color = 0xFFFFFFFF.toInt()

        previous?.let { drawArt(canvas, it, r, 1f - fade) }
        current?.let { drawArt(canvas, it, r, if (previous == null) 1f else fade) }

        scrimPaint.shader = LinearGradient(
            0f, h * 0.55f, 0f, h,
            intArrayOf(0x00000000, 0x0A000000, 0xC9000000.toInt()),
            floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawRect(r, scrimPaint)
        canvas.restore()

        canvas.drawRoundRect(r, radius, radius, strokePaint)

        val art = current ?: return
        canvas.drawText(art.title, context.dp(20f), h - context.dp(36f), titlePaint)
        canvas.drawText(art.caption, context.dp(20f), h - context.dp(16f), captionPaint)
    }

    private fun drawArt(canvas: Canvas, art: Artwork, r: RectF, alpha: Float) {
        if (alpha <= 0.01f) return
        val bmp = ArtCache.get(resources, art.res, 1200)
        val bw = bmp.width.toFloat()
        val bh = bmp.height.toFloat()
        val scale = max(r.width() / bw, r.height() / bh)
        val dw = bw * scale
        val dh = bh * scale
        dst.set(r.centerX() - dw / 2f, r.centerY() - dh / 2f, r.centerX() + dw / 2f, r.centerY() + dh / 2f)
        bmpPaint.alpha = (255 * alpha).toInt()
        canvas.drawBitmap(bmp, null, dst, bmpPaint)
        bmpPaint.alpha = 255
    }
}

/** Horizontal strip of artwork thumbnails. */
class ArtPicker @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val items = Art.items
    private var selected = 0
    private var bounceIndex = -1
    private var bounce = 0f
    private var bounceAnimator: ValueAnimator? = null

    var onSelect: ((Int) -> Unit)? = null

    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val clip = Path()
    private val dst = RectF()
    private val tmp = RectF()
    private val itemSize = context.dp(62f)
    private val gap = context.dp(10f)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            MeasureSpec.getSize(widthMeasureSpec),
            (itemSize + context.dp(18f)).toInt()
        )
    }

    private fun itemLeft(index: Int, w: Int): Float {
        val total = items.size * itemSize + (items.size - 1) * gap
        return (w - total) / 2f + index * (itemSize + gap)
    }

    fun setSelected(index: Int, notify: Boolean = false) {
        if (index !in items.indices) return
        selected = index
        bounceIndex = index
        bounceAnimator?.cancel()
        bounceAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 220
            addUpdateListener {
                bounce = it.animatedValue as Float
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    bounce = 0f
                    bounceIndex = -1
                }
            })
        }.also { it.start() }
        if (notify) onSelect?.invoke(index)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width
        val top = (height - itemSize) / 2f - context.dp(2f)
        items.forEachIndexed { i, art ->
            val isSel = i == selected
            val pulse = if (i == bounceIndex) (1f - bounce) * 0.08f else 0f
            val scale = if (isSel) 1f + pulse else 0.90f
            val size = itemSize * scale
            val cx = itemLeft(i, w) + itemSize / 2f
            val cy = top + itemSize / 2f
            val r = RectF(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f)

            clip.reset()
            clip.addRoundRect(r, context.dp(12f), context.dp(12f), Path.Direction.CW)
            canvas.save()
            canvas.clipPath(clip)
            bmpPaint.color = Palette.SURFACE
            canvas.drawRect(r, bmpPaint)
            val bmp = ArtCache.get(resources, art.res, 240)
            val bw = bmp.width.toFloat()
            val bh = bmp.height.toFloat()
            val s = max(r.width() / bw, r.height() / bh)
            val dw = bw * s
            val dh = bh * s
            dst.set(r.centerX() - dw / 2f, r.centerY() - dh / 2f, r.centerX() + dw / 2f, r.centerY() + dh / 2f)
            bmpPaint.alpha = if (isSel) 255 else 170
            canvas.drawBitmap(bmp, null, dst, bmpPaint)
            bmpPaint.alpha = 255
            canvas.restore()

            ring.strokeWidth = if (isSel) context.dp(2.4f) else context.dp(1f)
            ring.color = if (isSel) Palette.ACCENT else 0x33FFFFFF
            tmp.set(r.left - 3f, r.top - 3f, r.right + 3f, r.bottom + 3f)
            canvas.drawRoundRect(tmp,
                context.dp(14f), context.dp(14f), ring)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            val w = width
            val top = (height - itemSize) / 2f
            if (event.y < top - context.dp(8f) || event.y > top + itemSize + context.dp(8f)) return false
            for (i in items.indices) {
                val left = itemLeft(i, w)
                if (event.x >= left - gap / 2f && event.x <= left + itemSize + gap / 2f) {
                    setSelected(i, notify = true)
                    performClick()
                    return true
                }
            }
        } else if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}

/** Four-way difficulty selector with an animated pill. */
class Segmented @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val items = Difficulty.values()
    private var selected = Difficulty.MEDIUM.ordinal
    private var indicator = selected.toFloat()
    private var indicatorAnimator: ValueAnimator? = null

    var onSelect: ((Int) -> Unit)? = null

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Type.title
        textSize = context.sp(13.5f)
        textAlign = Paint.Align.CENTER
    }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Type.body
        textSize = context.sp(9.5f)
        textAlign = Paint.Align.CENTER
    }

    fun setSelected(index: Int, notify: Boolean = false) {
        val clamped = index.coerceIn(0, items.size - 1)
        if (clamped == selected) return
        selected = clamped
        indicatorAnimator?.cancel()
        indicatorAnimator = ValueAnimator.ofFloat(indicator, clamped.toFloat()).apply {
            duration = 220
            addUpdateListener {
                indicator = it.animatedValue as Float
                invalidate()
            }
        }.also { it.start() }
        if (notify) onSelect?.invoke(clamped)
        invalidate()
    }

    fun selection(): Int = selected

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            MeasureSpec.getSize(widthMeasureSpec),
            context.dpi(58f)
        )
    }

    private fun cellWidth(): Float = (width - paddingLeft - paddingRight) / items.size.toFloat()

    override fun onDraw(canvas: Canvas) {
        val r = context.dp(16f)
        val track = RectF(paddingLeft.toFloat(), paddingTop.toFloat(), width - paddingRight.toFloat(), height - paddingBottom.toFloat())
        trackPaint.color = Palette.TRACK
        canvas.drawRoundRect(track, r, r, trackPaint)

        val cw = cellWidth()
        val inset = context.dp(4f)
        val pill = RectF(
            paddingLeft + indicator * cw + inset,
            paddingTop + inset,
            paddingLeft + indicator * cw + cw - inset,
            height - paddingBottom - inset
        )
        pillPaint.shader = LinearGradient(
            pill.left, pill.top, pill.right, pill.bottom,
            Palette.ACCENT, Palette.ACCENT_DEEP, Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(pill, r - inset, r - inset, pillPaint)

        val cy = height / 2f
        items.forEachIndexed { i, d ->
            val active = i == selected
            val cx = paddingLeft + (i + 0.5f) * cw
            labelPaint.color = if (active) 0xFF241505.toInt() else Palette.TEXT
            subPaint.color = if (active) 0x99241505.toInt() else Palette.TEXT_FAINT
            canvas.drawText(d.label, cx, cy - context.dp(1f), labelPaint)
            canvas.drawText("${d.count} pcs", cx, cy + context.dp(13f), subPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            val cw = cellWidth()
            val idx = ((event.x - paddingLeft) / cw).toInt().coerceIn(0, items.size - 1)
            setSelected(idx, notify = true)
            performClick()
            return true
        } else if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}

/** Rounded button used for both primary actions and HUD tools. */
class PillButton @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var text: String = ""
        set(value) { field = value; invalidate() }

    var primary: Boolean = false
        set(value) { field = value; invalidate() }

    private var active = false
    private var pressed = 0f
    private var pressAnimator: ValueAnimator? = null
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Type.title
        textAlign = Paint.Align.CENTER
    }
    private val rect = RectF()

    var onClick: (() -> Unit)? = null

    fun setActive(on: Boolean) {
        if (active == on) return
        active = on
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY)
            MeasureSpec.getSize(widthMeasureSpec) else context.dpi(120f)
        val h = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY)
            MeasureSpec.getSize(heightMeasureSpec) else context.dpi(48f)
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        val scale = 1f - pressed * 0.04f
        canvas.save()
        canvas.scale(scale, scale, width / 2f, height / 2f)
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        val r = height / 2f
        when {
            primary -> {
                fill.shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(),
                    Palette.ACCENT, Palette.ACCENT_DEEP, Shader.TileMode.CLAMP)
                canvas.drawRoundRect(rect, r, r, fill)
            }
            active -> {
                fill.shader = null
                fill.color = Palette.ACCENT.withAlpha(0.18f)
                canvas.drawRoundRect(rect, r, r, fill)
                fill.color = Palette.ACCENT.withAlpha(0.55f)
                fill.style = Paint.Style.STROKE
                fill.strokeWidth = context.dp(1.4f)
                canvas.drawRoundRect(rect.insetCopy(context.dp(0.7f)), r, r, fill)
                fill.style = Paint.Style.FILL
            }
            else -> {
                fill.shader = null
                fill.color = Palette.SURFACE_HI
                canvas.drawRoundRect(rect, r, r, fill)
            }
        }
        label.textSize = if (primary) context.sp(16f) else context.sp(12.5f)
        label.color = when {
            primary -> 0xFF231405.toInt()
            active -> Palette.ACCENT
            else -> Palette.TEXT
        }
        val baseline = height / 2f - (label.descent() + label.ascent()) / 2f
        canvas.drawText(text, width / 2f, baseline, label)
        canvas.restore()
    }

    private fun RectF.insetCopy(d: Float): RectF = RectF(left + d, top + d, right - d, bottom - d)

    private fun animatePress(target: Float) {
        pressAnimator?.cancel()
        pressAnimator = ValueAnimator.ofFloat(pressed, target).apply {
            duration = 110
            addUpdateListener {
                pressed = it.animatedValue as Float
                invalidate()
            }
        }.also { it.start() }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                animatePress(1f)
                return true
            }
            MotionEvent.ACTION_UP -> {
                animatePress(0f)
                if (event.x in 0f..width.toFloat() && event.y in 0f..height.toFloat()) {
                    performClick()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                animatePress(0f)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        onClick?.invoke()
        return true
    }
}

/** Round back button with a chevron. */
class CircleButton @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dp(2f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Palette.TEXT
    }

    var onClick: (() -> Unit)? = null

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = context.dpi(40f)
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        fill.color = Palette.SURFACE_HI.withAlpha(0.85f)
        canvas.drawCircle(width / 2f, height / 2f, width / 2f, fill)
        val cx = width / 2f
        val cy = height / 2f
        val s = width * 0.18f
        canvas.drawLine(cx + s * 0.4f, cy - s, cx - s * 0.5f, cy, stroke)
        canvas.drawLine(cx - s * 0.5f, cy, cx + s * 0.4f, cy + s, stroke)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            if (event.x in 0f..width.toFloat() && event.y in 0f..height.toFloat()) performClick()
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        onClick?.invoke()
        return true
    }
}

/** Thin animated progress line. */
class ProgressLine @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var fraction = 0f
    private var shown = 0f
    private var animator: ValueAnimator? = null
    private val track = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bar = Paint(Paint.ANTI_ALIAS_FLAG)
    private val radius = context.dp(2f)

    fun setFraction(f: Float) {
        fraction = f.coerceIn(0f, 1f)
        animator?.cancel()
        animator = ValueAnimator.ofFloat(shown, fraction).apply {
            duration = 260
            addUpdateListener {
                shown = it.animatedValue as Float
                invalidate()
            }
        }.also { it.start() }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), context.dpi(3f))
    }

    override fun onDraw(canvas: Canvas) {
        val h = height.toFloat()
        track.color = 0x14FFFFFF
        canvas.drawRoundRect(0f, 0f, width.toFloat(), h, radius, radius, track)
        if (shown <= 0f) return
        bar.shader = LinearGradient(0f, 0f, width.toFloat(), 0f,
            Palette.ACCENT, Palette.ACCENT_DEEP, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(0f, 0f, width * shown, h, radius, radius, bar)
    }
}

/** Small helper for the staggered text/cta entrance on the menu. */
fun View.fadeUpIn(delay: Long) {
    alpha = 0f
    translationY = context.dp(14f)
    animate().alpha(1f).translationY(0f).setStartDelay(delay).setDuration(420).start()
}

fun roundRectDrawable(color: Int, radius: Float, stroke: Int? = null, strokeWidth: Float = 0f) =
    android.graphics.drawable.GradientDrawable().apply {
        shape = android.graphics.drawable.GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = radius
        if (stroke != null) setStroke(strokeWidth.roundToInt().coerceAtLeast(1), stroke)
    }
