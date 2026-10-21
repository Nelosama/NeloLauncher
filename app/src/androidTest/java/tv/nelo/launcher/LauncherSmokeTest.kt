@file:Suppress("DEPRECATION")
package tv.nelo.launcher

import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assert.*
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.GridView
import android.widget.TextView
import java.io.File

/** Runs on API 30 with AndroidX/JUnit in the test APK only; no test runtime ships in the launcher. */
class LauncherSmokeTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    @Test fun testHomeIntentAndCatalog() {
        val context = instrumentation.targetContext
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(context.packageName)
        assertNotNull("HOME must resolve", context.packageManager.resolveActivity(home, 0))
        val apps = AppCatalog(context).scan()
        assertTrue("Emulator must provide launchable applications", apps.isNotEmpty())
        assertEquals(apps.size, apps.map { it.component.packageName }.distinct().size)
        assertFalse(apps.any { it.component.packageName == context.packageName })
        for (app in apps) assertTrue(context.packageManager.getActivityInfo(app.component, 0).exported)
    }

    @Test fun testPreferencesSurviveRecreationAndSort() {
        val context = instrumentation.targetContext
        val prefs = LauncherPreferences(context)
        val oldFavorites = prefs.favorites; val oldOrder = prefs.order
        try {
            val a = AppEntry(ComponentName("test.a", "test.a.Main"), "A")
            val b = AppEntry(ComponentName("test.b", "test.b.Main"), "B")
            prefs.favorites = listOf(b.key)
            prefs.order = listOf(b.key, a.key)
            val restored = LauncherPreferences(context)
            assertEquals(listOf(b.key), restored.favorites)
            assertEquals(listOf(b, a), restored.sorted(listOf(a, b)))
        } finally { prefs.favorites = oldFavorites; prefs.order = oldOrder }
    }

    @Test fun testWallpaperBoundedAndInvalidImportPreservesPrevious() {
        val context = instrumentation.targetContext
        val store = WallpaperStore(context)
        val input = File(context.cacheDir, "test-wallpaper.png")
        val invalid = File(context.cacheDir, "invalid-image.txt")
        val previous = File(context.filesDir, "wallpaper.jpg").let { if (it.exists()) it.readBytes() else null }
        try {
            Bitmap.createBitmap(2560, 1440, Bitmap.Config.RGB_565).also { image ->
                input.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }; image.recycle()
            }
            val imported = store.import(Uri.fromFile(input))
            assertTrue(imported.width <= 1280 && imported.height <= 720)
            imported.recycle()
            val saved = File(context.filesDir, "wallpaper.jpg").readBytes()
            invalid.writeText("not an image")
            try { store.import(Uri.fromFile(invalid)); fail("Invalid image must fail") } catch (_: IllegalArgumentException) { }
            assertTrue(saved.contentEquals(File(context.filesDir, "wallpaper.jpg").readBytes()))
            val loaded = store.load()
            assertNotNull(loaded); loaded?.recycle()
        } finally {
            input.delete(); invalid.delete()
            if (previous != null) File(context.filesDir, "wallpaper.jpg").writeBytes(previous) else store.clear()
        }
    }

    @Test fun testDpadMenuSettingsAndHomeReturn() {
        val context = instrumentation.targetContext
        val activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        try {
            val deadline = android.os.SystemClock.uptimeMillis() + 15000
            var ready = false
            while (!ready && android.os.SystemClock.uptimeMillis() < deadline) {
                instrumentation.runOnMainSync { ready = descendants(activity.window.decorView).filterIsInstance<GridView>().first().count > 0 }
                if (!ready) android.os.SystemClock.sleep(100)
            }
            assertTrue("Catalog rendered", ready)
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val grid = descendants(activity.window.decorView).filterIsInstance<GridView>().first()
                grid.requestFocus(); grid.setSelection(0)
            }
            instrumentation.waitForIdleSync()
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_MENU)
            instrumentation.waitForIdleSync()
            val preferences = LauncherPreferences(context)
            var selected = ""
            instrumentation.runOnMainSync {
                val grid = descendants(activity.window.decorView).filterIsInstance<GridView>().first()
                selected = (grid.selectedItem as AppEntry).key
            }
            val wasFavorite = selected in preferences.favorites
            val menuText = if (wasFavorite) "Quitar de favoritas" else "Añadir a favoritas"
            val node = instrumentation.uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText(menuText).firstOrNull()
            assertNotNull("Application menu must open", node)
            assertTrue(node!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            instrumentation.waitForIdleSync()
            assertEquals(!wasFavorite, selected in preferences.favorites)
            // Restore the user's preference after the assertion.
            preferences.favorites = if (wasFavorite) preferences.favorites + selected else preferences.favorites - selected
            instrumentation.runOnMainSync {
                descendants(activity.window.decorView).filterIsInstance<Button>().first { it.text == "Ajustes" }.performClick()
            }
            instrumentation.waitForIdleSync()
            assertTrue(instrumentation.uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText("Elegir fondo local").isNotEmpty())
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            instrumentation.runOnMainSync {
                val grid = descendants(activity.window.decorView).filterIsInstance<GridView>().first()
                val settingsPosition = (0 until grid.count).firstOrNull {
                    (grid.getItemAtPosition(it) as AppEntry).component.packageName == "com.android.settings"
                }
                assertNotNull("Settings is installed as the external launch fixture", settingsPosition)
                grid.requestFocus(); grid.setSelection(settingsPosition!!)
            }
            instrumentation.waitForIdleSync()
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
            val externalDeadline = android.os.SystemClock.uptimeMillis() + 20000
            while (instrumentation.uiAutomation.rootInActiveWindow?.packageName != "com.android.settings" && android.os.SystemClock.uptimeMillis() < externalDeadline) {
                android.os.SystemClock.sleep(100)
            }
            assertEquals("com.android.settings", instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString())
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            var returned = false
            val returnDeadline = android.os.SystemClock.uptimeMillis() + 15000
            while (!returned && android.os.SystemClock.uptimeMillis() < returnDeadline) {
                instrumentation.runOnMainSync { returned = activity.hasWindowFocus() }
                if (!returned) android.os.SystemClock.sleep(100)
            }
            instrumentation.runOnMainSync {
                assertFalse(activity.isFinishing)
                assertTrue("Focus must return from the external application", activity.hasWindowFocus())
                assertTrue(descendants(activity.window.decorView).filterIsInstance<TextView>().any { it.text.contains("Aplicaciones") })
            }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
}
