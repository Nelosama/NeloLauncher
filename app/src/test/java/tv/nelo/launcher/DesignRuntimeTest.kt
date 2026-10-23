@file:Suppress("DEPRECATION")
package tv.nelo.launcher

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.GridView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File
import java.time.Duration

/** API 30 view-runtime checks. These are not emulator or physical-device screenshots. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], qualifiers = "w960dp-h540dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class DesignRuntimeTest {
    @Test fun homeRendersNativeViewsAndKeepsFocusAfterResume() = verifyHome(1920, 1080)

    @Test
    @Config(sdk = [30], qualifiers = "w640dp-h360dp-land-xhdpi")
    fun homeAt720pKeepsFocusAfterResume() = verifyHome(1280, 720)

    private fun verifyHome(width: Int, height: Int) {
        val context = RuntimeEnvironment.getApplication()
        val pm = Shadows.shadowOf(context.packageManager)
        val names = listOf("Vídeo local", "Música", "Fotografías", "Biblioteca", "Archivo", "Radio", "Juegos", "Configuración", "Colección", "Reproductor")
        val colors = intArrayOf(0xFF3A7BC2.toInt(), 0xFFD44472.toInt(), 0xFFDFAC4C.toInt(), 0xFF64A19C.toInt(), 0xFF795AB4.toInt())
        val entries = names.mapIndexed { index, name ->
            val component = ComponentName("test.local.app$index", "test.local.app$index.Home")
            val info = pm.addActivityIfNotPresent(component)
            info.exported = true; info.enabled = true; info.nonLocalizedLabel = name
            pm.addResolveInfoForIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), ResolveInfo().apply { activityInfo = info; nonLocalizedLabel = name })
            val icon = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
            OriginalTestIcon(colors[index % colors.size], index).apply { setBounds(0, 0, 128, 128); draw(Canvas(icon)) }
            pm.addActivityIcon(component, android.graphics.drawable.BitmapDrawable(context.resources, icon))
            AppEntry(component, name)
        }
        val prefs = LauncherPreferences(context)
        prefs.favorites = entries.take(5).map { it.key }; prefs.order = entries.map { it.key }; prefs.hidden = emptyList(); prefs.animations = false
        context.getSharedPreferences("launcher", 0).edit().putBoolean("initialized", true).commit()
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup().visible().windowFocusChanged(true)
        val activity = controller.get()
        try {
            val root = activity.window.decorView
            val grid = descendants(root).filterIsInstance<GridView>().first()
            await { grid.count == entries.size }
            root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, width, height)
            grid.requestFocus(); grid.setSelection(2)
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
            root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, width, height)
            screenshot(root, "home-native-api30-${width}x${height}.png")
            assertEquals("Grid selection after layout (${grid.width}x${grid.height}, children=${grid.childCount}, focus=${grid.hasFocus()}, touch=${grid.isInTouchMode})", entries[2].key, (grid.selectedItem as? AppEntry)?.key)
            assertTrue(descendants(root).filterIsInstance<AppCard>().isNotEmpty())
            assertEquals("Only the focused card may retain a border", 1, descendants(root).filterIsInstance<AppCard>().count { it.face.foreground != null })
            // Image output is produced by the actual Android View hierarchy and native Skia canvas.
            screenshot(root, "home-native-api30-${width}x${height}.png")
            controller.pause().stop().start().resume().visible()
            await { grid.count == entries.size }
            assertEquals(entries[2].key, (grid.selectedItem as AppEntry).key)
            assertEquals(android.content.pm.PackageManager.PERMISSION_DENIED, context.packageManager.checkPermission(android.Manifest.permission.INTERNET, context.packageName))
            grid.performItemClick(grid.selectedView, 2, 2)
            assertEquals(entries[2].component, Shadows.shadowOf(activity).nextStartedActivity.component)
            activity.onKeyDown(android.view.KeyEvent.KEYCODE_MENU, android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MENU))
            val menu = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
            menu.listView.performItemClick(null, 3, 3)
            await { grid.count == entries.size - 1 }
            assertEquals(listOf(entries[2].key), prefs.hidden)
            prefs.hidden = emptyList()
            controller.pause().stop().start().resume().visible()
            await { grid.count == entries.size }
        } finally { controller.pause().stop().destroy() }
    }
    @Test fun prepareDoesNotApplyAndGallerySurvivesStoreRecreation() {
        val context = RuntimeEnvironment.getApplication()
        val store = WallpaperStore(context)
        val file = File(context.cacheDir, "original-test-photo.png")
        val image = Bitmap.createBitmap(1920, 1080, Bitmap.Config.RGB_565)
        Canvas(image).drawColor(0xFF274F6B.toInt())
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val bitmap = store.prepare(android.net.Uri.fromFile(file))
        assertTrue(bitmap.width <= 1280 && bitmap.height <= 720)
        assertEquals("none", store.currentId)
        val legacy = File(context.filesDir, "wallpaper.jpg")
        legacy.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        assertEquals("legacy", WallpaperStore(context).currentId)
        assertNotNull(WallpaperStore(context).load())
        val saved = store.add(bitmap, "Foto original de prueba")
        assertEquals(saved.id, WallpaperStore(context).currentId)
        assertNotNull(WallpaperStore(context).load())
        val invalid = File(context.cacheDir, "invalid.jpg").apply { writeText("broken") }
        try { store.prepare(android.net.Uri.fromFile(invalid)); fail("Broken file must fail") } catch (_: IllegalArgumentException) { }
        assertEquals(saved.id, store.currentId)
        store.clear(); assertNull(store.load()); assertEquals(2, store.photos().size)
    }
    @Test fun wallpaperUiPreviewsBeforeApplyingAndFiltersDocumentStubs() {
        val context = RuntimeEnvironment.getApplication()
        val store = WallpaperStore(context)
        val component = ComponentName("com.google.android.tv.frameworkpackagestubs", "com.google.android.tv.frameworkpackagestubs.Stubs\$DocumentsStub")
        val manager = Shadows.shadowOf(context.packageManager)
        val info = manager.addActivityIfNotPresent(component).apply { exported = true; enabled = true }
        val pickerIntent = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*")
        manager.addResolveInfoForIntent(pickerIntent, ResolveInfo().apply { activityInfo = info })
        assertTrue(context.packageManager.queryIntentActivities(pickerIntent, 0).any { it.activityInfo.packageName == component.packageName })
        val controller = Robolectric.buildActivity(WallpaperActivity::class.java).setup().visible().windowFocusChanged(true)
        val activity = controller.get()
        try {
            org.robolectric.util.ReflectionHelpers.callInstanceMethod<Unit>(activity, "systemPicker")
            val fallback = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
            assertEquals("Selector no disponible", Shadows.shadowOf(fallback).title.toString())
            assertNull(Shadows.shadowOf(activity).nextStartedActivityForResult)
            fallback.dismiss()
            val photo = File(context.cacheDir, "preview-photo.png")
            val bitmap = Bitmap.createBitmap(1280, 720, Bitmap.Config.RGB_565)
            Canvas(bitmap).drawColor(0xFF365F72.toInt())
            photo.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            fun preparePreview() {
                org.robolectric.util.ReflectionHelpers.callInstanceMethod<Unit>(activity, "onActivityResult",
                    org.robolectric.util.ReflectionHelpers.ClassParameter.from(Int::class.javaPrimitiveType, 42),
                    org.robolectric.util.ReflectionHelpers.ClassParameter.from(Int::class.javaPrimitiveType, android.app.Activity.RESULT_OK),
                    org.robolectric.util.ReflectionHelpers.ClassParameter.from(Intent::class.java, Intent().putExtra("localPath", photo.path)))
                await { org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()?.isShowing == true }
            }
            preparePreview()
            assertEquals("none", store.currentId)
            org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog().getButton(android.app.AlertDialog.BUTTON_NEGATIVE).performClick()
            assertEquals("none", store.currentId)
            preparePreview()
            org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog().getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick()
            await { store.currentId != "none" }
            assertNotNull(store.load())
            assertEquals("preview-photo.png", store.photos().first().label)
            val decor = activity.window.decorView
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
            decor.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY))
            decor.layout(0, 0, 1920, 1080)
            screenshot(decor, "wallpaper-native-api30.png")
        } finally { controller.pause().stop().destroy() }
    }
    @Test fun internalInboxBrowsesWebpWithoutStoragePermission() {
        val context = RuntimeEnvironment.getApplication()
        Shadows.shadowOf(context).denyPermissions(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        val folder = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES), "Inbox").apply { mkdirs() }
        val file = File(folder, "test-inbox.webp")
        val bitmap = Bitmap.createBitmap(640, 360, Bitmap.Config.RGB_565)
        Canvas(bitmap).drawColor(0xFF355577.toInt())
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, it) }
        val controller = Robolectric.buildActivity(PhotoBrowserActivity::class.java, Intent(context, PhotoBrowserActivity::class.java).putExtra("inbox", true)).setup().visible()
        try {
            val activity = controller.get()
            val list = descendants(activity.window.decorView).filterIsInstance<android.widget.ListView>().first()
            await { list.adapter != null && list.adapter.count > 0 }
            val index = (0 until list.adapter.count).first { list.adapter.getItem(it).toString().contains(file.name) }
            list.performItemClick(null, index, index.toLong())
            assertEquals(android.app.Activity.RESULT_OK, Shadows.shadowOf(activity).resultCode)
            assertEquals(file.canonicalPath, File(Shadows.shadowOf(activity).resultIntent.getStringExtra("localPath")!!).canonicalPath)
            assertNotNull(WallpaperStore(context).prepare(android.net.Uri.fromFile(file)))
            assertEquals(android.content.pm.PackageManager.PERMISSION_DENIED, context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE))
        } finally { controller.pause().stop().destroy() }
    }
    @Test fun rotationSkipsDamagedPhotosAndPreferencesSurviveRecreation() {
        val context = RuntimeEnvironment.getApplication()
        val store = WallpaperStore(context)
        val image = Bitmap.createBitmap(64, 36, Bitmap.Config.RGB_565)
        val first = store.add(image, "Primera")
        val damaged = store.add(image, "Dañada")
        val last = store.add(image, "Última")
        damaged.file.writeText("invalid jpeg")
        store.select(first.id)
        assertNotNull(store.next())
        assertEquals(last.id, WallpaperStore(context).currentId)
        assertNotNull(store.next())
        assertEquals(first.id, store.currentId)
        store.delete(damaged)
        assertEquals(2, WallpaperStore(context).photos().size)
        val prefs = LauncherPreferences(context)
        prefs.favorites = listOf("app/a", "app/b")
        prefs.order = listOf("app/b", "app/a")
        prefs.hidden = listOf("app/b")
        prefs.slideshow = true
        prefs.rotationMinutes = 5
        val restored = LauncherPreferences(context)
        assertEquals(listOf("app/a", "app/b"), restored.favorites)
        assertEquals(listOf("app/b", "app/a"), restored.order)
        assertEquals(listOf("app/b"), restored.hidden)
        assertTrue(restored.slideshow)
        assertEquals(5, restored.rotationMinutes)
        image.recycle()
    }
    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 15_000_000_000L
        while (System.nanoTime() < deadline) { Shadows.shadowOf(Looper.getMainLooper()).idle(); if (condition()) return; Thread.sleep(20) }
        assertTrue("Android view runtime condition", condition())
    }
    private fun screenshot(root: View, name: String) {
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val folder = File("build/outputs/native-view-renders").apply { mkdirs() }
        File(folder, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    private class OriginalTestIcon(private val color: Int, private val index: Int) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun draw(canvas: Canvas) {
            val rect = RectF(bounds)
            paint.color = color; canvas.drawRoundRect(rect, 22f, 22f, paint)
            paint.color = Color.WHITE; paint.style = Paint.Style.STROKE; paint.strokeWidth = bounds.width() * .06f
            val cx = bounds.exactCenterX(); val cy = bounds.exactCenterY(); val r = bounds.width() * .23f
            when (index % 4) {
                0 -> { val path = Path().apply { moveTo(cx-r*.65f,cy-r); lineTo(cx+r,cy); lineTo(cx-r*.65f,cy+r); close() }; paint.style = Paint.Style.FILL; canvas.drawPath(path,paint) }
                1 -> { canvas.drawLine(cx-r*.4f,cy+r,cx-r*.4f,cy-r,paint); canvas.drawLine(cx-r*.4f,cy-r,cx+r,cy-r*.6f,paint); canvas.drawCircle(cx-r*.7f,cy+r,r*.35f,paint) }
                2 -> { canvas.drawRoundRect(cx-r,cy-r,cx+r,cy+r,10f,10f,paint); canvas.drawCircle(cx,cy,r*.5f,paint) }
                else -> { canvas.drawCircle(cx,cy,r,paint); canvas.drawLine(cx-r*.6f,cy,cx+r*.6f,cy,paint) }
            }
            paint.style = Paint.Style.FILL
        }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(filter: ColorFilter?) { paint.colorFilter = filter }
        override fun getOpacity() = PixelFormat.OPAQUE
    }
}
