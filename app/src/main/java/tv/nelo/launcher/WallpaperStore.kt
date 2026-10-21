package tv.nelo.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File

class WallpaperStore(private val context: Context) {
    private val file get() = File(context.filesDir, "wallpaper.jpg")
    fun exists() = file.exists()
    fun clear() { file.delete() }
    fun load(): Bitmap? = if (file.exists()) decode(file) else null

    // Decode at bounded resolution before saving; never copy an unbounded original into app storage.
    fun import(uri: Uri): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Imagen no compatible" }
        val options = options(bounds.outWidth, bounds.outHeight)
        val decoded = context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("No se pudo leer la imagen")
        val bitmap = fit(decoded)
        val temp = File(context.filesDir, "wallpaper.tmp")
        try {
            temp.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it)) }
            check(temp.renameTo(file)) { "No se pudo guardar el fondo" }
            return bitmap
        } finally { temp.delete() }
    }
    private fun decode(source: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        return BitmapFactory.decodeFile(source.path, options(bounds.outWidth, bounds.outHeight))?.let(::fit)
    }
    private fun options(width: Int, height: Int): BitmapFactory.Options {
        var sample = 1
        while (width / sample > 1280 || height / sample > 720) sample *= 2
        return BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.RGB_565 }
    }
    private fun fit(bitmap: Bitmap): Bitmap {
        val scale = minOf(1f, 1280f / bitmap.width, 720f / bitmap.height)
        if (scale == 1f) return bitmap
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true).also { bitmap.recycle() }
    }
}
