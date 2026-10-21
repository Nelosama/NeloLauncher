package tv.nelo.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.*
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

class MainActivity : Activity() {
    private lateinit var prefs: LauncherPreferences
    private lateinit var icons: IconCache
    private lateinit var wallpaper: WallpaperStore
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var background: ImageView
    private lateinit var grid: GridView
    private lateinit var empty: TextView
    private lateinit var section: TextView
    private lateinit var allButton: Button
    private lateinit var favoritesButton: Button
    private lateinit var adapter: AppAdapter
    private var apps = emptyList<AppEntry>()
    private var visible = emptyList<AppEntry>()
    private var favoritesOnly = false
    private var selectedKey: String? = null
    private var receiverRegistered = false
    private var scanVersion = 0
    private var wallpaperVersion = 0
    private var wallpaperLoaded = false
    private var wallpaperBusy = false
    private var startedAt = 0L
    private var frameReported = false
    private var highlighted: View? = null
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { icons.clear(); refreshApps() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        startedAt = SystemClock.elapsedRealtime()
        super.onCreate(savedInstanceState)
        prefs = LauncherPreferences(this)
        icons = IconCache(applicationContext)
        wallpaper = WallpaperStore(applicationContext)
        favoritesOnly = savedInstanceState?.getBoolean("favoritesOnly") ?: false
        selectedKey = savedInstanceState?.getString("selectedKey")
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        buildUi()
    }

    private fun buildUi() {
        val root = FrameLayout(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.rgb(23, 43, 76), Color.rgb(10, 13, 23), Color.rgb(30, 22, 43)))
        }
        background = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        root.addView(background, FrameLayout.LayoutParams(-1, -1))
        root.addView(View(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x33050914, 0x99050914.toInt(), 0xE6050914.toInt()))
        }, FrameLayout.LayoutParams(-1, -1))
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(42), dp(24), dp(42), dp(18)) }
        root.addView(content, FrameLayout.LayoutParams(-1, -1))
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(text("nelo", 30, Color.WHITE).apply { typeface = Typeface.create("sans-serif-light", Typeface.NORMAL); contentDescription = "Nelo TV" }, LinearLayout.LayoutParams(0, dp(60), 1f))
        val clock = TextClock(this).apply { format12Hour = "h:mm a"; format24Hour = "HH:mm"; textSize = 19f; setTextColor(0xFFCDD5E5.toInt()); setPadding(0, 0, dp(25), 0) }
        header.addView(clock)
        header.addView(button("Ajustes") { settings() })
        content.addView(header)
        content.addView(text("Tu espacio. Tus aplicaciones.", 15, 0xFFB0BDD2.toInt()), LinearLayout.LayoutParams(-1, dp(37)))
        val tabs = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        allButton = button("Aplicaciones") { showFavorites(false) }
        favoritesButton = button("Favoritas") { showFavorites(true) }
        tabs.addView(allButton)
        tabs.addView(favoritesButton)
        content.addView(tabs, LinearLayout.LayoutParams(-1, dp(52)))
        section = text("Aplicaciones", 18, Color.WHITE).apply { setPadding(dp(5), dp(12), 0, 0) }
        content.addView(section, LinearLayout.LayoutParams(-1, dp(46)))
        val body = FrameLayout(this)
        content.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        grid = GridView(this).apply {
            id = View.generateViewId()
            numColumns = if (resources.configuration.screenWidthDp < 800) 4 else 5
            verticalSpacing = dp(16); horizontalSpacing = dp(16)
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            clipChildren = false; clipToPadding = false
            setPadding(dp(10), dp(12), dp(10), dp(18))
            selector = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
            isVerticalScrollBarEnabled = false
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        }
        adapter = AppAdapter()
        grid.adapter = adapter
        grid.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ -> visible.getOrNull(position)?.let(::launch) }
        grid.onItemLongClickListener = AdapterView.OnItemLongClickListener { _, _, position, _ -> visible.getOrNull(position)?.let(::appMenu); true }
        grid.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedKey = visible.getOrNull(position)?.key
                highlight(if (grid.hasFocus()) view else null)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) { highlight(null) }
        }
        grid.setOnFocusChangeListener { _, focused -> highlight(if (focused) grid.selectedView else null) }
        allButton.nextFocusDownId = grid.id
        favoritesButton.nextFocusDownId = grid.id
        grid.nextFocusUpId = allButton.id
        body.addView(grid, FrameLayout.LayoutParams(-1, -1))
        empty = text("Buscando aplicaciones…", 18, 0xFFCDD5E5.toInt()).apply { gravity = Gravity.CENTER }
        body.addView(empty, FrameLayout.LayoutParams(-1, -1))
        content.addView(text("OK para abrir  ·  Mantén OK o pulsa Menú para organizar", 12, 0xFF9AA9C0.toInt()).apply { gravity = Gravity.CENTER_VERTICAL }, LinearLayout.LayoutParams(-1, dp(29)))
        setContentView(root)
        allButton.requestFocus()
    }

    private fun text(value: String, size: Int, color: Int) = TextView(this).apply { text = value; textSize = size.toFloat(); setTextColor(color); gravity = Gravity.CENTER_VERTICAL }
    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        id = View.generateViewId(); text = label; textSize = 14f; isAllCaps = false
        setTextColor(Color.WHITE); minHeight = dp(40); minimumHeight = dp(40)
        setPadding(dp(19), 0, dp(19), 0)
        background = shape(0x443C5276, 14f)
        setOnFocusChangeListener { view, focused ->
            view.background = shape(if (focused) 0xFF526F9D.toInt() else 0x443C5276, 14f, focused)
        }
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(-2, dp(42)).apply { marginEnd = dp(12) }
    }
    private fun shape(color: Int, radius: Float, border: Boolean = false) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
        if (border) setStroke(dp(2), 0xFFE3EDFF.toInt())
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun dp(value: Float) = (value * resources.displayMetrics.density).toInt()
    private fun toast(value: String) { Toast.makeText(this, value, Toast.LENGTH_LONG).show() }
    private fun backgroundTask(action: () -> Unit) {
        try { worker.execute(action) } catch (_: RejectedExecutionException) { /* Activity already destroyed. */ }
    }
    private fun ui(action: () -> Unit) { runOnUiThread { if (!isDestroyed && !isFinishing) action() } }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED); addAction(Intent.ACTION_PACKAGE_CHANGED); addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(packageReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else {
            @Suppress("DEPRECATION")
            registerReceiver(packageReceiver, filter)
        }
        receiverRegistered = true
    }
    override fun onResume() {
        super.onResume()
        refreshApps()
        if (!wallpaperLoaded) loadWallpaper()
    }
    override fun onStop() {
        if (receiverRegistered) { unregisterReceiver(packageReceiver); receiverRegistered = false }
        super.onStop()
    }
    override fun onDestroy() {
        scanVersion++; wallpaperVersion++
        worker.shutdownNow()
        background.setImageDrawable(null)
        icons.clear()
        super.onDestroy()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("favoritesOnly", favoritesOnly); outState.putString("selectedKey", selectedKey)
        super.onSaveInstanceState(outState)
    }
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (::icons.isInitialized && level >= TRIM_MEMORY_RUNNING_LOW) icons.clear()
        if (::background.isInitialized && level >= TRIM_MEMORY_UI_HIDDEN) {
            background.setImageDrawable(null); wallpaperLoaded = false
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) { showFavorites(false); if (visible.isNotEmpty()) grid.requestFocus() }
    }
    @Deprecated("HOME must remain available")
    override fun onBackPressed() {
        if (favoritesOnly) showFavorites(false) else allButton.requestFocus()
    }
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU && event.repeatCount == 0) {
            if (grid.hasFocus()) visible.getOrNull(grid.selectedItemPosition)?.let(::appMenu) else settings()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun refreshApps() {
        val version = ++scanVersion
        backgroundTask {
            try {
                val result = AppCatalog(applicationContext).scan()
                ui {
                    if (version == scanVersion) {
                        prefs.initialize(result)
                        apps = result
                        render()
                        if (!frameReported) {
                            grid.postOnAnimation {
                                Log.i("NeloStartup", "catalog_ready_ms=${SystemClock.elapsedRealtime() - startedAt} apps=${apps.size}")
                                reportFullyDrawn(); frameReported = true
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("NeloCatalog", "Unable to scan launchable activities", e)
                ui { if (version == scanVersion) { empty.text = "No se pudieron leer las aplicaciones. Abre Ajustes y vuelve a intentar."; empty.visibility = View.VISIBLE } }
            }
        }
    }
    private fun showFavorites(value: Boolean) { favoritesOnly = value; render() }
    private fun render() {
        val previousKey = selectedKey
        highlight(null)
        visible = prefs.sorted(apps).filter { !favoritesOnly || it.key in prefs.favorites }
        section.text = (if (favoritesOnly) "Favoritas" else "Aplicaciones") + "  ·  ${visible.size}"
        allButton.text = if (!favoritesOnly) "● Aplicaciones" else "Aplicaciones"
        favoritesButton.text = if (favoritesOnly) "● Favoritas" else "Favoritas"
        adapter.notifyDataSetChanged()
        empty.text = if (favoritesOnly) "Añade favoritas: mantén OK sobre una aplicación." else "No hay aplicaciones disponibles. Puedes abrir los ajustes del sistema."
        empty.visibility = if (visible.isEmpty()) View.VISIBLE else View.GONE
        grid.visibility = if (visible.isEmpty()) View.INVISIBLE else View.VISIBLE
        val position = visible.indexOfFirst { it.key == previousKey }.coerceAtLeast(0)
        if (visible.isNotEmpty()) grid.setSelection(position)
        else if (grid.hasFocus()) allButton.requestFocus()
    }
    private fun launch(app: AppEntry) {
        try {
            startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(app.component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
        } catch (_: Exception) { toast("No se pudo abrir ${app.label}. Puede haberse desinstalado o estar restringida."); refreshApps() }
    }
    private fun appMenu(app: AppEntry) {
        val favorite = app.key in prefs.favorites
        AlertDialog.Builder(this).setTitle(app.label).setItems(arrayOf(if (favorite) "Quitar de favoritas" else "Añadir a favoritas", "Mover antes", "Mover después", "Información de la aplicación")) { _, which ->
            when (which) {
                0 -> { prefs.favorites = if (favorite) prefs.favorites - app.key else prefs.favorites + app.key; render() }
                1 -> move(app, -1)
                2 -> move(app, 1)
                3 -> safeStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${app.component.packageName}")))
            }
        }.setNegativeButton("Cerrar", null).show()
    }
    private fun move(app: AppEntry, delta: Int) {
        val index = visible.indexOfFirst { it.key == app.key }
        val neighbor = visible.getOrNull(index + delta) ?: return
        val ordered = prefs.sorted(apps).map { it.key }.toMutableList()
        val source = ordered.indexOf(app.key); val target = ordered.indexOf(neighbor.key)
        if (source < 0 || target < 0) return
        java.util.Collections.swap(ordered, source, target)
        prefs.order = ordered; selectedKey = app.key; render()
    }
    private fun safeStart(intent: Intent) {
        try { startActivity(intent) } catch (_: Exception) { toast("Esta opción no está disponible en este dispositivo.") }
    }
    private fun settings() {
        val animationLabel = if (prefs.animations) "Animaciones: activadas" else "Animaciones: desactivadas"
        AlertDialog.Builder(this).setTitle("Nelo TV · Ajustes").setItems(arrayOf("Elegir fondo local", "Restaurar fondo", animationLabel, "Actualizar aplicaciones", "Ajustes de Android", "Elegir launcher de inicio", "Acerca de Nelo TV")) { _, which ->
            when (which) {
                0 -> pickWallpaper()
                1 -> {
                    if (wallpaperBusy) toast("Espera a que termine la importación")
                    else { wallpaperVersion++; background.setImageDrawable(null); wallpaperLoaded = true; backgroundTask { wallpaper.clear() } }
                }
                2 -> { prefs.animations = !prefs.animations; highlight(null); highlight(grid.selectedView) }
                3 -> { icons.clear(); refreshApps() }
                4 -> safeStart(Intent(Settings.ACTION_SETTINGS))
                5 -> safeStart(Intent(Settings.ACTION_HOME_SETTINGS))
                6 -> AlertDialog.Builder(this).setTitle("Nelo TV 0.1.0").setMessage("Launcher nativo Kotlin. Sin publicidad, red ni telemetría.\n\nMantén FLauncher y el launcher original instalados como recuperación.\n\nCódigo y licencias: github.com/Nelosama/NeloLauncher\n\nLicencia MIT. Kotlin: Apache 2.0.").setNeutralButton("Licencias") { _, _ -> showLicenses() }.setPositiveButton("Cerrar", null).show()
            }
        }.setNegativeButton("Cerrar", null).show()
    }
    private fun showLicenses() {
        val notice = assets.open("OPEN_SOURCE_LICENSES.txt").bufferedReader().use { it.readText() }
        AlertDialog.Builder(this).setTitle("Licencias de código abierto").setMessage(notice).setPositiveButton("Cerrar", null).show()
    }
    private fun pickWallpaper() {
        if (wallpaperBusy) { toast("Ya hay una imagen en proceso"); return }
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, 41)
        } catch (_: Exception) { toast("No hay selector de archivos. Instala uno compatible con Android TV o consulta la importación por ADB del README.") }
    }
    @Deprecated("Platform result API avoids extra runtime dependencies")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 41 || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val version = ++wallpaperVersion
        wallpaperBusy = true
        toast("Preparando fondo…")
        backgroundTask {
            try {
                val image = wallpaper.import(uri)
                ui { if (version == wallpaperVersion) { background.setImageBitmap(image); wallpaperLoaded = true; toast("Fondo guardado") } }
            } catch (_: OutOfMemoryError) { ui { toast("La imagen es demasiado grande. Prueba otra de hasta 1280 × 720.") } }
            catch (_: Exception) { ui { toast("No se pudo importar la imagen. El fondo anterior se conserva.") } }
            finally { ui { wallpaperBusy = false } }
        }
    }
    private fun loadWallpaper() {
        if (wallpaperBusy) return
        val version = ++wallpaperVersion
        backgroundTask {
            try { val image = wallpaper.load(); ui { if (version == wallpaperVersion) { background.setImageBitmap(image); wallpaperLoaded = true } } }
            catch (_: OutOfMemoryError) { ui { wallpaperLoaded = true } }
            catch (_: Exception) { ui { wallpaperLoaded = true; toast("Fondo no disponible; se utiliza el fondo predeterminado.") } }
        }
    }
    private fun highlight(view: View?) {
        highlighted?.let { previous ->
            previous.animate().cancel(); previous.scaleX = 1f; previous.scaleY = 1f
            previous.background = shape(0x55202F4B, 18f)
        }
        highlighted = view
        view?.let {
            it.background = shape(0xDD334E76.toInt(), 18f, true)
            val scale = if (prefs.animations) 1.04f else 1f
            it.animate().scaleX(scale).scaleY(scale).setDuration(if (prefs.animations) 130 else 0).start()
        }
    }

    private inner class AppAdapter : BaseAdapter() {
        override fun getCount() = visible.size
        override fun getItem(position: Int) = visible[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val tile = convertView as? LinearLayout ?: LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                layoutParams = AbsListView.LayoutParams(-1, dp(126))
                setPadding(dp(12), dp(12), dp(12), dp(8))
                addView(ImageView(this@MainActivity).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    clipToOutline = true
                    outlineProvider = object : ViewOutlineProvider() {
                        override fun getOutline(view: View, outline: Outline) { outline.setRoundRect(0, 0, view.width, view.height, dp(15).toFloat()) }
                    }
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }, LinearLayout.LayoutParams(dp(72), dp(72)))
                addView(text("", 13, Color.WHITE).apply {
                    gravity = Gravity.CENTER; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
                }, LinearLayout.LayoutParams(-1, dp(30)))
            }
            tile.animate().cancel(); tile.scaleX = 1f; tile.scaleY = 1f
            tile.background = shape(0x55202F4B, 18f)
            val app = visible[position]
            tile.tag = app.key
            tile.contentDescription = app.label + if (app.key in prefs.favorites) ", favorita" else ""
            (tile.getChildAt(1) as TextView).text = (if (app.key in prefs.favorites) "★ " else "") + app.label
            val image = tile.getChildAt(0) as ImageView
            image.setImageResource(R.drawable.ic_nelo)
            backgroundTask {
                val bitmap = icons.load(app)
                ui { if (tile.tag == app.key && bitmap != null) image.setImageBitmap(bitmap) }
            }
            if (grid.hasFocus() && grid.selectedItemPosition == position) highlight(tile)
            return tile
        }
    }
}
