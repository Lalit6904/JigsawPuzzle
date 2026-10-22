package com.lalit.jigsaw

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue

/** Colours, spacing and type used across both screens. */
object Palette {
    const val BG_TOP = 0xFF191D2E.toInt()
    const val BG_BOTTOM = 0xFF0A0C14.toInt()
    const val SURFACE = 0xFF1B1F31.toInt()
    const val SURFACE_HI = 0xFF262B42.toInt()
    const val TRACK = 0xFF2B3049.toInt()
    const val BOARD = 0xFF101220.toInt()
    const val TEXT = 0xFFF5F2EC.toInt()
    const val TEXT_DIM = 0xFF9BA1B9.toInt()
    const val TEXT_FAINT = 0xFF6C7290.toInt()
    const val ACCENT = 0xFFFFB86B.toInt()
    const val ACCENT_DEEP = 0xFFFF8A5C.toInt()
    const val GOOD = 0xFF7DDBB0.toInt()
}

object Type {
    val display: Typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
    val title: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    val body: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    val mono: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
}

fun Context.dp(v: Float): Float = v * resources.displayMetrics.density
fun Context.dpi(v: Float): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

fun Int.withAlpha(alpha: Float): Int {
    val a = (Color.alpha(this) * alpha.coerceIn(0f, 1f)).toInt()
    return Color.argb(a, Color.red(this), Color.green(this), Color.blue(this))
}

/** Text size in sp for canvas drawing. */
fun Context.sp(v: Float): Float =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)
