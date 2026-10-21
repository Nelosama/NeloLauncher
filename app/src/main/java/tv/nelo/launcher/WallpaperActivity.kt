package tv.nelo.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.widget.*
import java.io.File
import java.util.concurrent.Executors

class WallpaperActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var store: WallpaperStore
    private lateinit var prefs: LauncherPreferences
    private lateinit var preview: ImageView
    private lateinit var list: ListView
    private lateinit var info: TextView
    private var photos = emptyList<WallpaperStore.Photo>()
    private var busy = false
    private var generation = 0
    private var previewDialog: AlertDialog? = null
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        store = WallpaperStore(applicationContext); prefs = LauncherPreferences(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = AuroraDrawable(); setPadding(dp(36), dp(22), dp(36), dp(24)) }
        root.addView(label("Fondo de pantalla", 27), LinearLayout.LayoutParams(-1, dp(52)))
        val body = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        list = ListView(this).apply { dividerHeight = dp(4) }
        body.addView(list, LinearLayout.LayoutParams(0, -1, .46f))
        val side = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(28), dp(20), 0, dp(20)); gravity = Gravity.CENTER }
        body.addView(side, LinearLayout.LayoutParams(0, -1, .54f))
        preview = PhotoPreviewView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; background = AuroraDrawable(); clipToOutline = true; outlineProvider = android.view.ViewOutlineProvider.BACKGROUND }
        side.addView(preview, LinearLayout.LayoutParams(-1, -2))
        info = label("Fotografías locales · sin descargas", 14, 0xFFC6CFDE.toInt()).apply { setPadding(0, dp(15), 0, 0); gravity = Gravity.CENTER }
        side.addView(info, LinearLayout.LayoutParams(-1, dp(65)))
        setContentView(root); reload()
    }
    private fun toast(message: String) { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
    private fun work(action: () -> Unit) {
        if (!worker.isShutdown) worker.execute {
            try { action() }
            catch (_: OutOfMemoryError) { ui { busy = false; toast("Memoria insuficiente para esa fotografía") } }
            catch (_: Exception) { ui { busy = false; toast("No se pudo completar la operación; elige otra fotografía") } }
        }
    }
    private fun ui(action: () -> Unit) { runOnUiThread { if (!isDestroyed && !isFinishing) action() } }
    private fun reload() {
        val request = ++generation
        work {
            val files = store.photos()
            val bitmap = try { store.load() } catch (_: Exception) { null } catch (_: OutOfMemoryError) { null }
            ui {
                if (request != generation) return@ui
                photos = files; preview.setImageBitmap(bitmap)
                val items = listOf("Añadir fotografía", "Mi galería · ${photos.size}/24", if (prefs.slideshow) "Rotación: activada" else "Rotación: desactivada", "Intervalo: ${prefs.rotationMinutes} minutos", "Restaurar fondo predeterminado", "Cómo copiar fotos sin selector", "Volver al inicio")
                list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, items)
                list.setOnItemClickListener { _, _, position, _ ->
                    if (busy) { toast("Espera a que termine la imagen"); return@setOnItemClickListener }
                    when (position) {
                        0 -> chooseSource()
                        1 -> gallery()
                        2 -> {
                            if (!prefs.slideshow && photos.size < 2) toast("Añade al menos dos fotos para activar la rotación")
                            else { prefs.slideshow = !prefs.slideshow; reload() }
                        }
                        3 -> AlertDialog.Builder(this).setTitle("Cambiar fotografía cada…").setItems(arrayOf("5 minutos", "15 minutos", "30 minutos", "60 minutos")) { _, index -> prefs.rotationMinutes = intArrayOf(5, 15, 30, 60)[index]; reload() }.setNegativeButton("Cancelar", null).show()
                        4 -> { busy = true; work { store.clear(); ui { prefs.slideshow = false; busy = false; reload() } } }
                        5 -> inboxHelp()
                        6 -> finish()
                    }
                }
                info.text = if (prefs.slideshow) "Rotación solo mientras Nelo esté visible\nCada ${prefs.rotationMinutes} minutos" else "Fondo estático · recorte central sin deformación"
                list.requestFocus()
            }
        }
    }
    private fun chooseSource() {
        AlertDialog.Builder(this).setTitle("Añadir fotografía").setItems(arrayOf("Selector de archivos del sistema", "Explorar carpetas del TV", "Bandeja de Nelo · sin permisos")) { _, index ->
            when (index) {
                0 -> systemPicker()
                1 -> browse(false)
                2 -> browse(true)
            }
        }.setNegativeButton("Cancelar", null).show()
    }
    private fun browse(inbox: Boolean) {
        @Suppress("DEPRECATION")
        startActivityForResult(Intent(this, PhotoBrowserActivity::class.java).putExtra("inbox", inbox), 42)
    }
    private fun systemPicker() {
        val options = mutableListOf<Pair<ComponentName, Intent>>()
        for (action in listOf(Intent.ACTION_OPEN_DOCUMENT, Intent.ACTION_GET_CONTENT)) {
            val intent = Intent(action).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*")
                .putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/jpeg", "image/png", "image/webp")).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            for (resolved in packageManager.queryIntentActivities(intent, 0)) {
                val activity = resolved.activityInfo ?: continue
                if (!activity.exported || !activity.enabled || activity.packageName.contains("frameworkpackagestubs") || activity.name.contains("DocumentsStub", true)) continue
                val component = ComponentName(activity.packageName, activity.name)
                if (options.none { it.first == component }) options.add(component to intent.cloneFilter().putExtras(intent))
            }
        }
        if (options.isEmpty()) {
            AlertDialog.Builder(this).setTitle("Selector no disponible").setMessage("Android solo ofrece un acceso provisional o no tiene un selector de imágenes. Puedes explorar carpetas desde Nelo o usar su bandeja sin permisos.").setPositiveButton("Explorar") { _, _ -> browse(false) }.setNeutralButton("Bandeja de Nelo") { _, _ -> browse(true) }.setNegativeButton("Cancelar", null).show()
        } else if (options.size == 1) launchPicker(options.first())
        else AlertDialog.Builder(this).setTitle("Abrir con…").setItems(options.map { pair -> try { packageManager.getActivityInfo(pair.first, 0).loadLabel(packageManager).toString() } catch (_: Exception) { pair.first.packageName } }.toTypedArray()) { _, index -> launchPicker(options[index]) }.setNegativeButton("Cancelar", null).show()
    }
    private fun launchPicker(option: Pair<ComponentName, Intent>) {
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(option.second.setComponent(option.first).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), 41)
        } catch (_: Exception) { toast("Ese selector no se pudo abrir. Usa Explorar carpetas o la bandeja de Nelo.") }
    }
    private fun inboxHelp() {
        val folder = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: filesDir, "Inbox").apply { mkdirs() }
        AlertDialog.Builder(this).setTitle("Bandeja de Nelo").setMessage("Copia JPG, JPEG, PNG o WEBP a:\n\n${folder.path}\n\nDesde tu computadora:\nadb push foto.jpg \"${folder.path}/\"\n\nDespués elige Añadir fotografía → Bandeja de Nelo. Siempre verás una vista previa antes de aplicar.").setPositiveButton("Abrir bandeja") { _, _ -> browse(true) }.setNegativeButton("Cerrar", null).show()
    }
    @Deprecated("Platform result API") override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = when (requestCode) { 41 -> data?.data; 42 -> data?.getStringExtra("localPath")?.let { Uri.fromFile(File(it)) }; else -> null } ?: return
        if (busy) return
        busy = true
        val request = ++generation
        work {
            try {
                val bitmap = store.prepare(uri)
                var name = "Fotografía local"
                try {
                    if (uri.scheme == "file") name = File(uri.path ?: "").name
                    else contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) name = it.getString(0) ?: name }
                } catch (_: Exception) { }
                val displayName = name
                ui {
                    busy = false
                    if (request == generation) photoPreview(bitmap, displayName, null)
                }
            } catch (_: OutOfMemoryError) { ui { busy = false; toast("No hay memoria suficiente para esa foto. Prueba una imagen más pequeña.") } }
            catch (_: Exception) { ui { busy = false; toast("No se pudo leer la foto. El fondo actual se conserva.") } }
        }
    }
    private fun gallery() {
        if (photos.isEmpty()) { toast("Añade tu primera fotografía"); return }
        AlertDialog.Builder(this).setTitle("Mi galería").setItems(photos.map { (if (it.id == store.currentId) "● " else "") + it.label }.toTypedArray()) { _, index ->
            val photo = photos[index]
            busy = true
            work {
                try { val bitmap = store.prepare(Uri.fromFile(photo.file)); ui { busy = false; photoPreview(bitmap, photo.label, photo) } }
                catch (_: OutOfMemoryError) { ui { busy = false; toast("No hay memoria suficiente"); reload() } }
                catch (_: Exception) { ui { busy = false; offerDamaged(photo) } }
            }
        }.setNegativeButton("Cerrar", null).show()
    }
    private fun photoPreview(bitmap: Bitmap, name: String, existing: WallpaperStore.Photo?) {
        val image = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER; setImageBitmap(bitmap); setPadding(dp(12), dp(8), dp(12), dp(8)) }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(image, LinearLayout.LayoutParams(dp(500), dp(245)))
        content.addView(label("Vista previa · el inicio usará recorte central 16:9", 13, 0xFFCDD5E5.toInt()).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-1, dp(32)))
        val dialog = AlertDialog.Builder(this).setTitle(name.take(70)).setView(content).setPositiveButton("Aplicar") { _, _ ->
            busy = true
            work {
                try {
                    if (existing == null) store.add(bitmap, name) else store.select(existing.id)
                    ui { busy = false; toast("Fondo aplicado"); reload() }
                } catch (_: Exception) { ui { busy = false; toast("No se pudo guardar. La galería admite hasta 24 fotos.") } }
            }
        }.setNegativeButton("Cancelar", null)
        if (existing != null) dialog.setNeutralButton("Eliminar copia") { _, _ -> remove(existing) }
        previewDialog = dialog.create().apply { setOnDismissListener { image.setImageDrawable(null); previewDialog = null }; show() }
    }
    private fun offerDamaged(photo: WallpaperStore.Photo) {
        AlertDialog.Builder(this).setTitle("Foto no disponible").setMessage("El archivo no existe o está dañado. Puedes eliminar su copia sin afectar el original.").setPositiveButton("Eliminar copia") { _, _ -> remove(photo) }.setNegativeButton("Cerrar", null).show()
    }
    private fun remove(photo: WallpaperStore.Photo) {
        busy = true
        work { try { store.delete(photo); ui { busy = false; reload() } } catch (_: Exception) { ui { busy = false; toast("No se pudo eliminar la copia") } } }
    }
    override fun onDestroy() { generation++; previewDialog?.dismiss(); preview.setImageDrawable(null); worker.shutdownNow(); super.onDestroy() }
}
