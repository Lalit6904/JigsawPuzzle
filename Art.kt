package com.lalit.jigsaw

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlin.math.max

/** One selectable picture. */
data class Artwork(
    val id: String,
    val title: String,
    val caption: String,
    val res: Int,
    val accent: Int
)

object Art {
    val items: List<Artwork> = listOf(
        Artwork("dusk", "Amber Dusk", "Mountains at last light", R.drawable.art_dusk, 0xFFF2A86B.toInt()),
        Artwork("sea", "Still Water", "A quiet morning sail", R.drawable.art_sea, 0xFF7FC6D2.toInt()),
        Artwork("night", "Midnight", "Crescent over the hills", R.drawable.art_night, 0xFF9C8FE6.toInt()),
        Artwork("bloom", "Paper Bloom", "A garden of shapes", R.drawable.art_bloom, 0xFFF07A78.toInt()),
        Artwork("shapes", "Bauhaus", "Bold circles and arcs", R.drawable.art_shapes, 0xFFF2C14E.toInt())
    )

    fun byId(id: String?): Artwork = items.firstOrNull { it.id == id } ?: items.first()
}

/**
 * Small decode cache so the gallery and the board can both ask for the same
 * picture without keeping several full-size copies around.
 */
object ArtCache {
    private val cache = LinkedHashMap<String, Bitmap>()
    private const val MAX_ENTRIES = 8

    fun get(res: Resources, resId: Int, maxSize: Int): Bitmap {
        val key = "$resId@$maxSize"
        cache[key]?.let { return it }
        val bmp = decode(res, resId, maxSize)
        cache[key] = bmp
        while (cache.size > MAX_ENTRIES) {
            val oldest = cache.keys.firstOrNull() ?: break
            cache.remove(oldest)
        }
        return bmp
    }

    private fun decode(res: Resources, resId: Int, maxSize: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(res, resId, bounds)
        var sample = 1
        var largest = max(bounds.outWidth, bounds.outHeight)
        while (largest / (sample * 2) >= maxSize) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inScaled = false
        }
        val decoded = BitmapFactory.decodeResource(res, resId, opts)
        if (largest <= maxSize) return decoded
        val scale = maxSize.toFloat() / largest
        val w = (decoded.width * scale).toInt().coerceAtLeast(1)
        val h = (decoded.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(decoded, w, h, true)
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }
}
