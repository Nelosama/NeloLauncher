@file:Suppress("DEPRECATION")
package tv.nelo.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import android.os.Environment
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.GridView
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** The fixture APK is a real external app. Production Nelo includes no testing runtime or demo apps. */
class LauncherSmokeTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    @Test fun homeCatalogAndLegacyPreferences() {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(context.packageName)
        assertNotNull(context.packageManager.resolveActivity(home, 0))
        val apps = AppCatalog(context).scan()
        assertTrue(apps.any { it.component.packageName == "tv.nelo.fixture" })
        assertFalse(apps.any { it.component.packageName == context.packageName })
        assertEquals(apps.size, apps.map { it.component.packageName }.distinct().size)
        val raw = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)
        val old = raw.all
        try {
            val key = apps.first().key
            // Seed the exact JSON format used in 0.1.0, then recreate the new preferences store.
            raw.edit().clear().putString("favorites", "[\"$key\"]").putString("order", "[\"$key\"]").putBoolean("initialized", true).commit()
            val restored = LauncherPreferences(context)
            assertEquals(listOf(key), restored.favorites)
            assertEquals(key, restored.sorted(apps).first().key)
            assertTrue(restored.clock); assertFalse(restored.slideshow)
            restored.hidden = listOf(key); restored.clock = false; restored.rotationMinutes = 5
            val again = LauncherPreferences(context)
            assertEquals(listOf(key), again.hidden); assertFalse(again.clock); assertEquals(5, again.rotationMinutes)
        } finally {
            val editor = raw.edit().clear()
            old.forEach { (key, value) -> when (value) { is String -> editor.putString(key, value); is Boolean -> editor.putBoolean(key, value); is Int -> editor.putInt(key, value) } }
            editor.commit()
        }
    }
    @Test fun photoPreviewPersistenceRotationAndCorruptInput() {
        val store = WallpaperStore(context)
        val previous = store.currentId
        val input = File(context.cacheDir, "large-photo.png")
        val invalid = File(context.cacheDir, "broken.webp")
        val created = mutableListOf<WallpaperStore.Photo>()
        try {
            writePhoto(input)
            val count = store.photos().size
            val preview = store.prepare(Uri.fromFile(input))
            assertTrue(preview.width <= 1280 && preview.height <= 720)
            assertEquals("Preview must not save or apply", previous, store.currentId)
            assertEquals(count, store.photos().size)
            val first = store.add(preview, "Prueba de galería")
            created.add(first)
            val newInstance = WallpaperStore(context)
            assertEquals(first.id, newInstance.currentId)
            assertNotNull(newInstance.load())
            val before = first.file.readBytes()
            invalid.writeText("not a photo")
            try { newInstance.import(Uri.fromFile(invalid)); fail("Corrupt image must be rejected") } catch (_: IllegalArgumentException) { }
            assertEquals(first.id, newInstance.currentId)
            assertTrue(before.contentEquals(first.file.readBytes()))
            created.add(store.add(preview, "Segunda fotografía"))
            assertNotNull(store.next()); assertNotEquals(created.last().id, store.currentId)
            store.clear(); assertNull(store.load()); assertTrue(store.photos().size >= 2)
            preview.recycle()
        } finally {
            created.forEach { store.delete(it) }
            if (previous != "none" && store.photos().any { it.id == previous }) store.select(previous) else store.clear()
            input.delete(); invalid.delete()
        }
    }
    @Test fun dpadFavoriteHideAndExternalReturn() {
        val prefs = LauncherPreferences(context)
        val oldFavorites = prefs.favorites; val oldHidden = prefs.hidden
        val fixture = AppCatalog(context).scan().first { it.component.packageName == "tv.nelo.fixture" }
        prefs.hidden = prefs.hidden - fixture.key
        val activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        try {
            val grid = awaitGrid(activity)
            select(grid, fixture.key)
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_MENU)
            val wasFavorite = fixture.key in prefs.favorites
            click(if (wasFavorite) "Quitar de favoritas" else "Añadir a favoritas")
            waitUntil { (fixture.key in prefs.favorites) != wasFavorite }
            select(grid, fixture.key)
            // Fast navigation must not accumulate pending animations or lose the grid selection.
            repeat(8) { instrumentation.sendKeyDownUpSync(if (it % 2 == 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT) }
            select(grid, fixture.key)
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
            waitUntil { root()?.packageName?.toString() == "tv.nelo.fixture" }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            waitUntil { var focused = false; instrumentation.runOnMainSync { focused = activity.hasWindowFocus() }; focused }
            instrumentation.runOnMainSync { assertEquals(fixture.key, (grid.selectedItem as AppEntry).key) }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_MENU)
            click("Ocultar del inicio")
            waitUntil { fixture.key in prefs.hidden }
            instrumentation.runOnMainSync { assertFalse((0 until grid.count).any { (grid.getItemAtPosition(it) as AppEntry).key == fixture.key }) }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            click("Configuración")
            click("Aplicaciones ocultas")
            click("Nelo Test")
            waitUntil { fixture.key !in prefs.hidden }
        } finally {
            prefs.favorites = oldFavorites; prefs.hidden = oldHidden
            instrumentation.runOnMainSync { activity.finish() }
        }
    }
    @Test fun inboxWithoutPickerPreviewCancelThenApply() {
        val store = WallpaperStore(context)
        val previous = store.currentId
        val originalPhotos = store.photos().map { it.id }.toSet()
        val inbox = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "Inbox").apply { mkdirs() }
        val file = File(inbox, "nelo-test-photo.png")
        writePhoto(file)
        val activity = instrumentation.startActivitySync(Intent(context, WallpaperActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as WallpaperActivity
        try {
            click("Añadir fotografía")
            click("Bandeja de Nelo · sin permisos")
            click("nelo-test-photo.png")
            click("Cancelar", exact = true)
            assertEquals("Cancel must preserve the current wallpaper", previous, store.currentId)
            click("Añadir fotografía")
            click("Bandeja de Nelo · sin permisos")
            click("nelo-test-photo.png")
            click("Aplicar", exact = true)
            waitUntil { store.currentId != previous }
            assertNotNull(WallpaperStore(context).load())
            // Files selected via the inbox never trigger a storage permission request.
            assertEquals(android.content.pm.PackageManager.PERMISSION_DENIED, context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE))
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
            store.photos().filter { it.id !in originalPhotos }.forEach { store.delete(it) }
            if (previous != "none" && store.photos().any { it.id == previous }) store.select(previous) else store.clear()
            file.delete()
        }
    }
    private fun awaitGrid(activity: MainActivity): GridView {
        var grid: GridView? = null
        waitUntil {
            instrumentation.runOnMainSync { grid = descendants(activity.window.decorView).filterIsInstance<GridView>().firstOrNull() }
            var ready = false
            instrumentation.runOnMainSync { ready = grid != null && grid!!.count > 0 }
            ready
        }
        return grid!!
    }
    private fun select(grid: GridView, key: String) {
        instrumentation.runOnMainSync {
            val index = (0 until grid.count).first { (grid.getItemAtPosition(it) as AppEntry).key == key }
            grid.requestFocus(); grid.setSelection(index)
        }
        instrumentation.waitForIdleSync()
    }
    private fun root(): AccessibilityNodeInfo? = instrumentation.uiAutomation.rootInActiveWindow
    private fun click(text: String, exact: Boolean = false) {
        var node: AccessibilityNodeInfo? = null
        waitUntil { node = root()?.findAccessibilityNodeInfosByText(text)?.firstOrNull { !exact || it.text?.toString() == text }; node != null }
        var clickable = node
        while (clickable != null && !clickable.isClickable) clickable = clickable.parent
        assertNotNull("Clickable control: $text", clickable)
        assertTrue(clickable!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync()
    }
    private fun waitUntil(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 45000
        while (SystemClock.uptimeMillis() < deadline) { if (condition()) return; SystemClock.sleep(150) }
        assertTrue("Condition timed out on Android runtime", condition())
    }
    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    private fun writePhoto(file: File) {
        val bitmap = Bitmap.createBitmap(2560, 1440, Bitmap.Config.RGB_565)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply { shader = LinearGradient(0f, 0f, 2560f, 1440f, intArrayOf(0xFF173E61.toInt(), 0xFF44686A.toInt(), 0xFF976E54.toInt()), null, Shader.TileMode.CLAMP) }
        canvas.drawRect(0f, 0f, 2560f, 1440f, paint)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
