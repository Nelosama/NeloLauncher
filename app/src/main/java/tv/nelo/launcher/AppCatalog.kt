package tv.nelo.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import org.json.JSONArray

data class AppEntry(val component: ComponentName, val label: String) {
    val key: String get() = component.flattenToString()
}

class AppCatalog(private val context: Context) {
    // The queries in the manifest expose launchable activities only; QUERY_ALL_PACKAGES is unnecessary.
    fun scan(): List<AppEntry> {
        val pm = context.packageManager
        val found = linkedMapOf<String, AppEntry>()
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (resolved in pm.queryIntentActivities(intent, 0)) {
                val info = resolved.activityInfo ?: continue
                if (!info.exported || !info.enabled || info.packageName == context.packageName) continue
                // Prefer the TV entry point when a package exposes TV and phone activities.
                val label = try { resolved.loadLabel(pm).toString() } catch (_: Exception) { info.packageName }
                found.putIfAbsent(info.packageName, AppEntry(ComponentName(info.packageName, info.name), label))
            }
        }
        return found.values.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }
}

class LauncherPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)
    var animations: Boolean
        get() = prefs.getBoolean("animations", true)
        set(value) { prefs.edit().putBoolean("animations", value).apply() }
    var clock: Boolean
        get() = prefs.getBoolean("clock", true)
        set(value) { prefs.edit().putBoolean("clock", value).apply() }
    var slideshow: Boolean
        get() = prefs.getBoolean("slideshow", false)
        set(value) { prefs.edit().putBoolean("slideshow", value).apply() }
    var rotationMinutes: Int
        get() = prefs.getInt("rotationMinutes", 15).coerceIn(5, 60)
        set(value) { prefs.edit().putInt("rotationMinutes", value.coerceIn(5, 60)).apply() }
    var hidden: List<String>
        get() = read("hidden")
        set(value) { write("hidden", value) }
    var favorites: List<String>
        get() = read("favorites")
        set(value) { write("favorites", value) }
    var order: List<String>
        get() = read("order")
        set(value) { write("order", value) }
    fun initialize(apps: List<AppEntry>) {
        if (!prefs.getBoolean("initialized", false)) {
            val preferred = setOf("com.netflix.ninja", "com.amazon.amazonvideo.livingroom", "com.spotify.tv.android", "me.efesser.flauncher")
            favorites = apps.filter { it.component.packageName in preferred }.map { it.key }
            prefs.edit().putBoolean("initialized", true).apply()
        }
    }
    fun sorted(apps: List<AppEntry>): List<AppEntry> {
        val positions = order.withIndex().associate { it.value to it.index }
        return apps.sortedWith(compareBy<AppEntry> { positions[it.key] ?: Int.MAX_VALUE }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }
    private fun read(key: String): List<String> = try {
        val array = JSONArray(prefs.getString(key, "[]"))
        List(array.length()) { array.getString(it) }.distinct()
    } catch (_: Exception) { emptyList() }
    private fun write(key: String, values: List<String>) {
        prefs.edit().putString(key, JSONArray(values.distinct()).toString()).apply()
    }
}

class IconCache(private val context: Context) {
    private val cache = object : LruCache<String, Bitmap>(3 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    @Synchronized fun clear() = cache.evictAll()
    // Called only by the background executor. Never retain package-manager Drawable objects.
    fun load(app: AppEntry): Bitmap? {
        synchronized(this) { cache.get(app.key)?.let { return it } }
        return try {
            val pm = context.packageManager
            val banner = try { pm.getActivityBanner(app.component) ?: pm.getApplicationBanner(app.component.packageName) } catch (_: Exception) { null }
            val drawable = banner ?: pm.getActivityIcon(app.component)
            val width = if (banner != null) 320 else 128
            val height = if (banner != null) 180 else 128
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            drawable.setBounds(0, 0, width, height)
            drawable.draw(Canvas(bitmap))
            synchronized(this) { cache.put(app.key, bitmap) }
            bitmap
        } catch (_: OutOfMemoryError) { clear(); null }
        catch (_: Exception) { null }
    }
}
