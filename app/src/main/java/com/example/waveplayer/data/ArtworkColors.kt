package com.example.waveplayer.data

import android.content.Context
import android.graphics.Color
import android.util.LruCache
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** top/bottom: dark gradient colors (text on top stays white); accent: bright color for controls/theme. */
data class ArtPalette(val top: Int, val bottom: Int, val accent: Int)

object ArtworkColors {
    private val cache = LruCache<String, ArtPalette>(64)

    suspend fun get(context: Context, model: Any): ArtPalette? {
        val key = model.toString()
        cache.get(key)?.let { return it }
        val request = ImageRequest.Builder(context).data(model).allowHardware(false).size(200).build()
        val result = context.imageLoader.execute(request) as? SuccessResult ?: return null
        val bitmap = result.drawable.toBitmap()
        return withContext(Dispatchers.Default) {
            val p = Palette.from(bitmap).maximumColorCount(16).generate()
            val base = (p.vibrantSwatch ?: p.dominantSwatch ?: p.mutedSwatch)?.rgb ?: return@withContext null
            val second = (p.darkMutedSwatch ?: p.darkVibrantSwatch)?.rgb ?: base
            ArtPalette(
                top = ColorUtils.blendARGB(base, Color.BLACK, 0.40f),
                bottom = ColorUtils.blendARGB(second, Color.BLACK, 0.65f),
                accent = ColorUtils.blendARGB(base, Color.WHITE, 0.25f),
            ).also { cache.put(key, it) }
        }
    }
}
