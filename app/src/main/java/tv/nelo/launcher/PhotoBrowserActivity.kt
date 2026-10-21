package tv.nelo.launcher

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.storage.StorageManager
import android.view.Gravity
import android.widget.*
import java.io.File
import java.util.concurrent.Executors

/** Internal D-pad browser: private inbox always works; public storage access is explicit and optional. */
class PhotoBrowserActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var title: TextView
    private lateinit var list: ListView
    private lateinit var hint: TextView
    private var root: File? = null
    private var folder: File? = null
    private var version = 0
    private var entries = emptyList<File>()
    private val inbox get() = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: filesDir, "Inbox").apply { mkdirs() }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(40), dp(24), dp(40), dp(20)); background = AuroraDrawable() }
        title = label("Fotografías locales", 25)
        content.addView(title, LinearLayout.LayoutParams(-1, dp(50)))
        hint = label("OK para abrir · Atrás para subir de carpeta", 14, 0xFFB8C6D8.toInt())
        content.addView(hint, LinearLayout.LayoutParams(-1, dp(46)))
        list = ListView(this).apply { dividerHeight = dp(3); isVerticalScrollBarEnabled = true }
        content.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(content)
        val savedPath = state?.getString("folder")
        val savedRoot = state?.getString("root")
        if (savedPath != null && savedRoot != null && allowed(savedRoot)) { root = File(savedRoot); show(File(savedPath)) }
        else if (intent.getBooleanExtra("inbox", false)) { root = inbox; show(inbox) }
        else showRoots()
    }
    private fun allowed(path: String) = path == inbox.path || (Build.VERSION.SDK_INT <= 32 && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
    private fun showRoots() {
        root = null; folder = null; version++
        title.text = "Fotografías locales"
        hint.text = "La bandeja de Nelo funciona sin permisos. Puedes copiar fotos por ADB."
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, listOf("Bandeja de Nelo · sin permisos", "Explorar almacenamiento del TV"))
        list.setOnItemClickListener { _, _, position, _ ->
            if (position == 0) { root = inbox; show(inbox) }
            else if (Build.VERSION.SDK_INT >= 33) {
                AlertDialog.Builder(this).setMessage("En Android 13 o posterior usa el selector del sistema o la bandeja de Nelo. En Android TV 11 puedes explorar con permiso de lectura.").setPositiveButton("Entendido", null).show()
            } else if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) publicRoots()
            else AlertDialog.Builder(this).setTitle("Explorar tus fotografías").setMessage("Este acceso permite leer fotos en Imágenes, Descargas y unidades disponibles. Se usa solo al explorar; la bandeja de Nelo no lo necesita.").setPositiveButton("Continuar") { _, _ -> requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 7) }.setNegativeButton("Cancelar", null).show()
        }
        list.requestFocus(); list.setSelection(0)
    }
    private fun publicRoots() {
        val paths = mutableListOf(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), Environment.getExternalStorageDirectory())
        if (Build.VERSION.SDK_INT >= 30) {
            val manager = getSystemService(StorageManager::class.java)
            manager.storageVolumes.filter { !it.isPrimary }.mapNotNull { it.directory }.forEach { paths.add(it) }
        }
        val roots = paths.distinctBy { it.absolutePath }
        title.text = "Elige una carpeta"
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, roots.map { it.path })
        list.setOnItemClickListener { _, _, position, _ -> root = roots[position]; show(roots[position]) }
        list.setSelection(0)
    }
    private fun show(directory: File) {
        val boundary = root ?: return
        // A path restored from state must remain below the selected root; symlinks cannot escape it.
        val canonical = try { directory.canonicalFile } catch (_: Exception) { boundary }
        if (canonical != boundary.canonicalFile && !canonical.path.startsWith(boundary.canonicalPath + File.separator)) return
        folder = canonical
        title.text = canonical.name.ifBlank { "Fotografías" }
        hint.text = "Leyendo carpeta…"
        val request = ++version
        worker.execute {
            val children = try { canonical.listFiles()?.filter { !it.name.startsWith('.') && (it.isDirectory || it.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp")) }?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }) } catch (_: Exception) { null }
            runOnUiThread {
                if (isDestroyed || request != version) return@runOnUiThread
                entries = children.orEmpty()
                hint.text = if (entries.isEmpty()) "Sin fotos accesibles. Copia JPG, PNG o WEBP a ${inbox.path}" else "${canonical.path} · OK para abrir"
                list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, entries.map { (if (it.isDirectory) "▸  " else "    ") + it.name })
                list.setOnItemClickListener { _, _, position, _ ->
                    val file = entries.getOrNull(position) ?: return@setOnItemClickListener
                    if (file.isDirectory) show(file)
                    else { setResult(RESULT_OK, Intent().putExtra("localPath", file.path)); finish() }
                }
                list.requestFocus(); if (entries.isNotEmpty()) list.setSelection(0)
            }
        }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 7 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) publicRoots()
        else { Toast.makeText(this, "Puedes seguir usando la bandeja de Nelo sin permiso", Toast.LENGTH_LONG).show(); showRoots() }
    }
    @Deprecated("Platform Back handling") override fun onBackPressed() {
        val current = folder; val boundary = root
        if (current != null && boundary != null && current.canonicalFile != boundary.canonicalFile) show(current.parentFile ?: boundary)
        else if (current != null || root != null) showRoots() else super.onBackPressed()
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putString("folder", folder?.path); outState.putString("root", root?.path); super.onSaveInstanceState(outState) }
    override fun onDestroy() { version++; worker.shutdownNow(); super.onDestroy() }
}
