package tv.nelo.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.*
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.provider.Settings
import android.util.Log
import android.view.*
import android.widget.*
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var prefs: LauncherPreferences
    private lateinit var icons: IconCache
    private lateinit var wallpaper: WallpaperStore
    private val worker = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var background: ImageView
    private lateinit var grid: GridView
    private lateinit var dock: LinearLayout
    private lateinit var dockTitle: TextView
    private lateinit var hero: TextView
    private lateinit var empty: TextView
    private lateinit var clock: TextClock
    private lateinit var settingsButton: Button
    private lateinit var adapter: AppAdapter
    private var apps = emptyList<AppEntry>()
    private var visible = emptyList<AppEntry>()
    private var favorites = emptyList<AppEntry>()
    private var favoriteKeys = emptySet<String>()
    private val cards = mutableListOf<AppCard>()
    private var highlighted: AppCard? = null
    private var selectedKey: String? = null
    private var selectedArea = "grid"
    private var dockPage = 0
    private var columns = 5
    private var compactHeight = false
    private var scanVersion = 0
    private var wallpaperVersion = 0
    private var resumed = false
    private var launching = false
    private var receiverRegistered = false
    private var firstCatalog = true
    private var startedAt = 0L
    private val scan = Runnable { refreshApps() }
    private val rotate = object : Runnable {
        override fun run() {
            if (!resumed || !prefs.slideshow) return
            val version = ++wallpaperVersion
            work {
                try {
                    val image = wallpaper.next()
                    ui { if (resumed && version == wallpaperVersion) background.setImageBitmap(image); scheduleRotation() }
                } catch (_: Exception) { ui { prefs.slideshow = false; toast("Rotación pausada: revisa las fotos de tu galería") } }
            }
        }
    }
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            icons.clear(); handler.removeCallbacks(scan); handler.postDelayed(scan, 250)
        }
    }
    override fun onCreate(state: Bundle?) {
        startedAt = SystemClock.elapsedRealtime()
        super.onCreate(state)
        prefs = LauncherPreferences(this); icons = IconCache(applicationContext); wallpaper = WallpaperStore(applicationContext)
        selectedKey = state?.getString("selectedKey"); selectedArea = state?.getString("selectedArea") ?: "grid"
        dockPage = state?.getInt("dockPage") ?: 0
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        buildUi()
    }
    private fun buildUi() {
        compactHeight = resources.configuration.screenHeightDp < 400
        columns = when { resources.configuration.screenWidthDp >= 800 -> 5; resources.configuration.screenWidthDp >= 600 -> 4; else -> 3 }
        val root = FrameLayout(this).apply { background = AuroraDrawable(); clipChildren = false }
        background = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        root.addView(background, FrameLayout.LayoutParams(-1, -1))
        root.addView(View(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x220A101C, 0x110A101C, 0xC909101B.toInt()))
        }, FrameLayout.LayoutParams(-1, -1))
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(40), dp(if (compactHeight) 10 else 22), dp(40), dp(if (compactHeight) 8 else 12)); clipChildren = false; clipToPadding = false }
        root.addView(content, FrameLayout.LayoutParams(-1, -1))
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(label("nelo", 25).apply { typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL); contentDescription = "Nelo TV Launcher" }, LinearLayout.LayoutParams(0, dp(if (compactHeight) 36 else 42), 1f))
        clock = TextClock(this).apply { format12Hour = "h:mm a"; format24Hour = "HH:mm"; textSize = 15f; setTextColor(0xFFDCE3EE.toInt()); setPadding(dp(18), 0, dp(20), 0); visibility = if (prefs.clock) View.VISIBLE else View.GONE }
        header.addView(clock)
        settingsButton = tvButton("Configuración") { settings() }
        header.addView(settingsButton, LinearLayout.LayoutParams(-2, dp(36)))
        content.addView(header)
        hero = label("Tu espacio", if (compactHeight) 24 else 32).apply { typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL); gravity = Gravity.BOTTOM; setPadding(dp(6), 0, 0, dp(if (compactHeight) 2 else 18)); setShadowLayer(dp(3).toFloat(), 0f, dp(1).toFloat(), 0x55000000) }
        content.addView(hero, LinearLayout.LayoutParams(-1, dp(if (compactHeight) 28 else if (resources.configuration.screenHeightDp < 480) 52 else 83)))
        dockTitle = label("Favoritas", 13, 0xFFDBE2EF.toInt()).apply { setPadding(dp(8), 0, 0, 0) }
        content.addView(dockTitle, LinearLayout.LayoutParams(-1, dp(if (compactHeight) 20 else 25)))
        dock = LinearLayout(this).apply { id = View.generateViewId(); gravity = Gravity.CENTER_VERTICAL; background = rounded(0x60151C29, 24); setPadding(dp(12), dp(12), dp(12), dp(2)); clipChildren = false; clipToPadding = false }
        content.addView(dock, LinearLayout.LayoutParams(-1, dp(if (compactHeight) 112 else 124)))
        content.addView(label("Aplicaciones", 14, 0xFFD7DFEC.toInt()).apply { setPadding(dp(8), dp(7), 0, 0) }, LinearLayout.LayoutParams(-1, dp(if (compactHeight) 24 else 34)))
        val body = FrameLayout(this).apply { clipChildren = false; clipToPadding = false }
        content.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        grid = GridView(this).apply {
            id = View.generateViewId(); numColumns = columns; verticalSpacing = dp(9); horizontalSpacing = dp(16)
            stretchMode = GridView.STRETCH_COLUMN_WIDTH; selector = ColorDrawable(Color.TRANSPARENT)
            clipChildren = false; clipToPadding = false; isVerticalScrollBarEnabled = false
            setPadding(dp(8), dp(10), dp(8), dp(14)); descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        }
        adapter = AppAdapter(); grid.adapter = adapter
        grid.onItemClickListener = AdapterView.OnItemClickListener { _, view, index, _ -> visible.getOrNull(index)?.let { launch(it, view as? AppCard) } }
        grid.onItemLongClickListener = AdapterView.OnItemLongClickListener { _, _, index, _ -> visible.getOrNull(index)?.let(::appMenu); true }
        grid.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (grid.hasFocus()) visible.getOrNull(position)?.let { selectedArea = "grid"; selectedKey = it.key; hero.text = it.label; highlight(view as? AppCard) }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) { }
        }
        grid.setOnFocusChangeListener { _, focused ->
            if (focused) {
                selectedArea = "grid"
                visible.getOrNull(grid.selectedItemPosition)?.let { selectedKey = it.key; hero.text = it.label }
                highlight(grid.selectedView as? AppCard)
            } else highlight(null)
        }
        grid.setOnKeyListener { _, key, event ->
            if (key == KeyEvent.KEYCODE_DPAD_UP && event.action == KeyEvent.ACTION_DOWN && grid.selectedItemPosition in 0 until columns) {
                if (cards.isNotEmpty()) cards[grid.selectedItemPosition.coerceAtMost(cards.lastIndex)].requestFocus() else settingsButton.requestFocus()
                true
            } else false
        }
        body.addView(grid, FrameLayout.LayoutParams(-1, -1))
        empty = label("Buscando tus aplicaciones…", 16, 0xFFD8E1EE.toInt()).apply { gravity = Gravity.CENTER }
        body.addView(empty, FrameLayout.LayoutParams(-1, -1))
        setContentView(root); settingsButton.requestFocus()
        settingsButton.setOnKeyListener { _, key, event ->
            if (event.action == KeyEvent.ACTION_DOWN && key == KeyEvent.KEYCODE_DPAD_DOWN) { focusContent(); true } else false
        }
    }
    private fun work(action: () -> Unit) {
        if (worker.isShutdown) return
        worker.execute {
            try { action() }
            catch (_: OutOfMemoryError) { icons.clear(); ui { toast("Memoria ajustada: prueba un fondo más pequeño") } }
            catch (error: Exception) { Log.w("NeloWorker", "Operation failed", error); ui { toast("No se pudo completar la operación. Puedes volver a intentarlo.") } }
        }
    }
    private fun ui(action: () -> Unit) { runOnUiThread { if (!isDestroyed && !isFinishing) action() } }
    private fun toast(message: String) { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
    private fun highlight(card: AppCard?) {
        if (highlighted === card) return
        highlighted?.focusCard(false, prefs.animations); highlighted = card
        card?.focusCard(true, prefs.animations)
    }
    private fun bind(card: AppCard, app: AppEntry) {
        val token = card.bind(app, app.key in favoriteKeys)
        work { val image = icons.load(app); ui { card.setArtwork(image, token) } }
    }
    private fun render(restoreFocus: Boolean) {
        val key = selectedKey; val area = selectedArea
        highlight(null)
        val hidden = prefs.hidden.toSet(); favoriteKeys = prefs.favorites.toSet()
        visible = prefs.sorted(apps).filter { it.key !in hidden }
        favorites = visible.filter { it.key in favoriteKeys }
        adapter.notifyDataSetChanged()
        empty.text = if (prefs.hidden.isNotEmpty()) "No hay aplicaciones visibles. Puedes mostrarlas desde Configuración." else "No se encontraron aplicaciones. Configuración sigue disponible."
        empty.visibility = if (visible.isEmpty()) View.VISIBLE else View.GONE
        grid.visibility = if (visible.isEmpty()) View.INVISIBLE else View.VISIBLE
        renderDock()
        selectedKey = key; selectedArea = area
        val index = visible.indexOfFirst { it.key == key }.coerceAtLeast(0)
        if (visible.isNotEmpty()) grid.setSelection(index)
        if (restoreFocus) grid.post { if (!isDestroyed && resumed) focusContent() }
    }
    private fun renderDock(focusPosition: Int? = null) {
        cards.forEach { it.cancelAnimations() }; cards.clear(); dock.removeAllViews()
        val pages = ((favorites.size + columns - 1) / columns).coerceAtLeast(1)
        dockPage = dockPage.coerceIn(0, pages - 1)
        dockTitle.text = if (pages > 1) getString(R.string.favorites_page, dockPage + 1, pages) else getString(R.string.favorites)
        if (favorites.isEmpty()) {
            dock.addView(tvButton("Añadir favoritas") {
                toast("Mantén OK o pulsa Menú sobre una aplicación para añadirla a favoritas")
                selectedArea = "grid"; if (visible.isNotEmpty()) grid.requestFocus()
            }, LinearLayout.LayoutParams(-2, dp(40)).apply { marginStart = dp(12) })
            return
        }
        val start = dockPage * columns
        for (slot in 0 until columns) {
            val app = favorites.getOrNull(start + slot)
            if (app == null) { dock.addView(View(this), LinearLayout.LayoutParams(0, -1, 1f)); continue }
            val card = AppCard(this).apply { id = View.generateViewId(); isFocusable = true; isClickable = true; isLongClickable = true }
            bind(card, app); cards.add(card)
            dock.addView(card, LinearLayout.LayoutParams(0, -1, 1f).apply { marginStart = dp(5); marginEnd = dp(5) })
            card.setOnFocusChangeListener { _, focused ->
                if (focused) { selectedArea = "dock"; selectedKey = app.key; hero.text = app.label; highlight(card) }
                else card.focusCard(false, prefs.animations)
            }
            card.setOnClickListener { launch(app, card) }
            card.setOnLongClickListener { appMenu(app); true }
            card.setOnKeyListener { _, key, event ->
                if (event.action != KeyEvent.ACTION_DOWN) false
                else when {
                    key == KeyEvent.KEYCODE_DPAD_DOWN -> { selectedArea = "grid"; if (visible.isNotEmpty()) { grid.requestFocus(); grid.setSelection(slot.coerceAtMost(visible.lastIndex)) }; true }
                    key == KeyEvent.KEYCODE_DPAD_UP -> { settingsButton.requestFocus(); true }
                    key == KeyEvent.KEYCODE_DPAD_RIGHT && slot == cards.lastIndex && start + slot < favorites.lastIndex -> { dockPage++; renderDock(0); true }
                    key == KeyEvent.KEYCODE_DPAD_LEFT && slot == 0 && dockPage > 0 -> { dockPage--; renderDock(columns - 1); true }
                    key == KeyEvent.KEYCODE_MENU && event.repeatCount == 0 -> { appMenu(app); true }
                    else -> false
                }
            }
        }
        focusPosition?.let { cards.getOrNull(it.coerceAtMost(cards.lastIndex))?.requestFocus() }
    }
    private fun focusContent() {
        if (selectedArea == "dock") {
            val index = favorites.indexOfFirst { it.key == selectedKey }
            if (index >= 0) {
                val page = index / columns
                if (page != dockPage) { dockPage = page; renderDock() }
                cards.getOrNull(index % columns)?.requestFocus(); return
            }
        }
        if (visible.isNotEmpty()) {
            grid.requestFocus(); grid.setSelection(visible.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0))
        } else settingsButton.requestFocus()
    }
    private fun refreshApps() {
        val version = ++scanVersion
        val restore = grid.hasFocus() || cards.any { it.hasFocus() } || firstCatalog
        work {
            val result = AppCatalog(applicationContext).scan()
            ui {
                if (version != scanVersion) return@ui
                prefs.initialize(result); apps = result; render(restore)
                if (firstCatalog) {
                    firstCatalog = false
                    grid.postOnAnimation { if (!isDestroyed) { Log.i("NeloStartup", "catalog_ready_ms=${SystemClock.elapsedRealtime() - startedAt} apps=${apps.size}"); reportFullyDrawn() } }
                }
            }
        }
    }
    private fun launch(app: AppEntry, card: AppCard?) {
        if (launching) return
        launching = true
        val open = {
            if (resumed && !isDestroyed) {
                try { startActivity(Intent(Intent.ACTION_MAIN).setComponent(app.component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)) }
                catch (_: Exception) { launching = false; toast("No se pudo abrir ${app.label}. Puede estar restringida o haberse desinstalado."); refreshApps() }
            } else launching = false
        }
        if (card != null) { card.press(prefs.animations, open); handler.postDelayed({ launching = false }, 500) }
        else open()
    }
    private fun appMenu(app: AppEntry) {
        val favorite = app.key in prefs.favorites
        AlertDialog.Builder(this).setTitle(app.label).setItems(arrayOf(if (favorite) "Quitar de favoritas" else "Añadir a favoritas", "Mover antes", "Mover después", "Ocultar del inicio", "Información de la aplicación")) { _, index ->
            when (index) {
                0 -> { prefs.favorites = if (favorite) prefs.favorites - app.key else prefs.favorites + app.key; render(true) }
                1 -> move(app, -1)
                2 -> move(app, 1)
                3 -> { prefs.hidden = prefs.hidden + app.key; render(true) }
                4 -> safeStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${app.component.packageName}")))
            }
        }.setNegativeButton("Cerrar", null).show()
    }
    private fun move(app: AppEntry, delta: Int) {
        val subset = if (selectedArea == "dock") favorites else visible
        val neighbor = subset.getOrNull(subset.indexOfFirst { it.key == app.key } + delta) ?: return
        val ordered = prefs.sorted(apps).map { it.key }.toMutableList()
        val first = ordered.indexOf(app.key); val second = ordered.indexOf(neighbor.key)
        if (first < 0 || second < 0) return
        java.util.Collections.swap(ordered, first, second); prefs.order = ordered; selectedKey = app.key; render(true)
    }
    private fun safeStart(intent: Intent) { try { startActivity(intent) } catch (_: Exception) { toast("Esta opción no está disponible en este dispositivo") } }
    private fun settings() {
        AlertDialog.Builder(this).setTitle("Configuración").setItems(arrayOf("Fondo de pantalla", if (prefs.clock) "Reloj: visible" else "Reloj: oculto", if (prefs.animations) "Animaciones: activadas" else "Animaciones: desactivadas", "Aplicaciones ocultas", "Actualizar aplicaciones", "Ajustes de Android", "Acerca de Nelo TV")) { _, index ->
            when (index) {
                0 -> startActivity(Intent(this, WallpaperActivity::class.java))
                1 -> { prefs.clock = !prefs.clock; clock.visibility = if (prefs.clock) View.VISIBLE else View.GONE }
                2 -> { prefs.animations = !prefs.animations; highlighted?.focusCard(false, false); highlighted = null; if (grid.hasFocus()) highlight(grid.selectedView as? AppCard) }
                3 -> hiddenApps()
                4 -> { icons.clear(); refreshApps() }
                5 -> safeStart(Intent(Settings.ACTION_SETTINGS))
                6 -> AlertDialog.Builder(this).setTitle("Nelo TV Launcher 0.2.0").setMessage("Tu espacio, sin publicidad.\n\nKotlin nativo · sin red ni telemetría.\n\nMantén FLauncher como respaldo. Tú decides el launcher predeterminado desde Android.\n\ngithub.com/Nelosama/NeloLauncher").setNeutralButton("Licencias") { _, _ ->
                    val notice = assets.open("OPEN_SOURCE_LICENSES.txt").bufferedReader().use { it.readText() }
                    AlertDialog.Builder(this).setTitle("Licencias").setMessage(notice).setPositiveButton("Cerrar", null).show()
                }.setPositiveButton("Cerrar", null).show()
            }
        }.setNegativeButton("Cerrar", null).show()
    }
    private fun hiddenApps() {
        val hidden = apps.filter { it.key in prefs.hidden }
        if (hidden.isEmpty()) { toast("No hay aplicaciones ocultas"); return }
        AlertDialog.Builder(this).setTitle("Mostrar de nuevo").setItems(hidden.map { it.label }.toTypedArray()) { _, index -> prefs.hidden = prefs.hidden - hidden[index].key; render(true) }.setNeutralButton("Mostrar todas") { _, _ -> prefs.hidden = emptyList(); render(true) }.setNegativeButton("Cerrar", null).show()
    }
    private fun loadWallpaper() {
        val version = ++wallpaperVersion
        work {
            try { val image = wallpaper.load(); ui { if (resumed && version == wallpaperVersion) background.setImageBitmap(image) } }
            catch (_: Exception) { ui { if (version == wallpaperVersion) { background.setImageDrawable(null); toast("Fondo no disponible. Puedes elegir otra foto desde Configuración.") } } }
        }
    }
    private fun scheduleRotation() {
        handler.removeCallbacks(rotate)
        if (resumed && prefs.slideshow) handler.postDelayed(rotate, prefs.rotationMinutes * 60_000L)
    }
    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply { addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED); addAction(Intent.ACTION_PACKAGE_CHANGED); addDataScheme("package") }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(packageReceiver, filter, RECEIVER_NOT_EXPORTED)
        else { @Suppress("DEPRECATION") registerReceiver(packageReceiver, filter) }
        receiverRegistered = true
    }
    override fun onResume() { super.onResume(); resumed = true; launching = false; refreshApps(); loadWallpaper(); scheduleRotation() }
    override fun onPause() { resumed = false; wallpaperVersion++; handler.removeCallbacks(rotate); cards.forEach { it.cancelAnimations() }; (0 until grid.childCount).forEach { (grid.getChildAt(it) as? AppCard)?.cancelAnimations() }; super.onPause() }
    override fun onStop() { if (receiverRegistered) { unregisterReceiver(packageReceiver); receiverRegistered = false }; handler.removeCallbacks(scan); super.onStop() }
    override fun onDestroy() { scanVersion++; wallpaperVersion++; handler.removeCallbacksAndMessages(null); worker.shutdownNow(); icons.clear(); background.setImageDrawable(null); super.onDestroy() }
    override fun onSaveInstanceState(outState: Bundle) { outState.putString("selectedKey", selectedKey); outState.putString("selectedArea", selectedArea); outState.putInt("dockPage", dockPage); super.onSaveInstanceState(outState) }
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (::icons.isInitialized && level >= TRIM_MEMORY_RUNNING_LOW) icons.clear()
        if (::background.isInitialized && level >= TRIM_MEMORY_UI_HIDDEN) background.setImageDrawable(null)
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); if (intent.hasCategory(Intent.CATEGORY_HOME)) focusContent() }
    @Deprecated("Keep HOME open") override fun onBackPressed() { if (cards.isNotEmpty() && selectedArea != "dock") cards.first().requestFocus() else settingsButton.requestFocus() }
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU && event.repeatCount == 0) {
            val app = if (grid.hasFocus()) visible.getOrNull(grid.selectedItemPosition) else if (cards.any { it.hasFocus() }) favorites.firstOrNull { it.key == selectedKey } else null
            if (app == null) settings() else appMenu(app)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
    private inner class AppAdapter : BaseAdapter() {
        override fun getCount() = visible.size
        override fun getItem(position: Int) = visible[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val card = convertView as? AppCard ?: AppCard(this@MainActivity).apply { layoutParams = AbsListView.LayoutParams(-1, dp(if (compactHeight) 96 else 121)); isFocusable = false }
            bind(card, visible[position])
            if (grid.hasFocus() && grid.selectedItemPosition == position) {
                if (highlighted === card) card.focusCard(true, prefs.animations) else highlight(card)
            }
            return card
        }
    }
}
