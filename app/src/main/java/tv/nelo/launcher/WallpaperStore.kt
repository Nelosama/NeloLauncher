package tv.nelo.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.File
import java.util.UUID

/** Only optimized private copies are kept. Preparing a photo never changes the active wallpaper. */
class WallpaperStore(private val context: Context) {
    data class Photo(val id: String, val file: File, val label: String)
    private val prefs = context.getSharedPreferences("wallpapers", Context.MODE_PRIVATE)
    private val directory get() = File(context.filesDir, "wallpapers").apply { mkdirs() }
    private val legacy get() = File(context.filesDir, "wallpaper.jpg")
    val currentId: String get() = prefs.getString("current", if (legacy.exists()) "legacy" else "none") ?: "none"
    fun exists() = currentId != "none" && photos().any { it.id == currentId }
    fun photos(): List<Photo> {
        val result = directory.listFiles()?.filter { it.isFile && it.extension == "jpg" }?.sortedBy { it.name }?.map {
            Photo(it.nameWithoutExtension, it, prefs.getString("label_${it.nameWithoutExtension}", "Fotografía local") ?: "Fotografía local")
        }.orEmpty()
        return (if (legacy.exists()) listOf(Photo("legacy", legacy, "Fondo de Nelo 0.1.0")) else emptyList()) + result
    }
    fun prepare(uri: Uri): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outMimeType in setOf("image/jpeg", "image/png", "image/webp")) { "Usa JPG, PNG o WEBP válidos" }
        return if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val ratio = minOf(1f, 1280f / info.size.width, 720f / info.size.height)
                decoder.setTargetSize((info.size.width * ratio).toInt().coerceAtLeast(1), (info.size.height * ratio).toInt().coerceAtLeast(1))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.memorySizePolicy = ImageDecoder.MEMORY_POLICY_LOW_RAM
                decoder.setOnPartialImageListener { false }
            }
        } else {
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 1280 || bounds.outHeight / (sample * 2) >= 720) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.RGB_565 }
            val source = context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, options) } ?: error("Imagen no disponible")
            val ratio = minOf(1f, 1280f / source.width, 720f / source.height)
            if (ratio == 1f) source else Bitmap.createScaledBitmap(source, (source.width * ratio).toInt().coerceAtLeast(1), (source.height * ratio).toInt().coerceAtLeast(1), true).also { source.recycle() }
        }
    }
    /** Must be called on a worker. Synchronous metadata commit follows a completed file rename. */
    fun add(bitmap: Bitmap, label: String): Photo {
        require(photos().size < 24) { "La galería admite 24 fotos; elimina una copia antes de añadir otra" }
        val id = System.currentTimeMillis().toString() + "-" + UUID.randomUUID().toString().take(8)
        val destination = File(directory, "$id.jpg")
        val temp = File(directory, "$id.tmp")
        try {
            temp.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it)) }
            check(temp.renameTo(destination)) { "No se pudo guardar la imagen" }
            check(prefs.edit().putString("label_$id", label.take(100)).putString("current", id).commit())
            return Photo(id, destination, label)
        } finally { temp.delete() }
    }
    fun import(uri: Uri): Bitmap = prepare(uri).also { add(it, "Fotografía local") }
    fun select(id: String) { require(photos().any { it.id == id && it.file.canRead() }); check(prefs.edit().putString("current", id).commit()) }
    fun clear() { check(prefs.edit().putString("current", "none").commit()) }
    fun delete(photo: Photo) {
        if (currentId == photo.id) clear()
        check(!photo.file.exists() || photo.file.delete())
        check(prefs.edit().remove("label_${photo.id}").commit())
    }
    fun load(): Bitmap? = photos().firstOrNull { it.id == currentId }?.let { prepare(Uri.fromFile(it.file)) }
    fun next(): Bitmap? {
        val candidates = photos()
        if (candidates.isEmpty()) return null
        val position = candidates.indexOfFirst { it.id == currentId }
        for (offset in 1..candidates.size) {
            val photo = candidates[(position + offset).mod(candidates.size)]
            try { val image = prepare(Uri.fromFile(photo.file)); select(photo.id); return image } catch (_: Exception) { /* Skip damaged files. */ }
        }
        error("No hay fotografías válidas para la rotación")
    }
}
