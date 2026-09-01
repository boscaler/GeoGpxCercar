package com.example.geogpxfinder

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.File
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class GpxMatch(val file: File, val distanceKm: Double, val dateMillis: Long?)

class MainActivity : AppCompatActivity() {

    private var centerLat: Double? = null
    private var centerLon: Double? = null
    private var matches: List<GpxMatch> = emptyList()
    private lateinit var txtCenter: TextView
    private lateinit var txtStatus: TextView
    private lateinit var editRadius: EditText
    private lateinit var listResults: ListView
    private lateinit var btnSort: Button
    private lateinit var btnOpenSelected: Button
    private lateinit var btnSelectAll: Button
    private lateinit var btnHelp: Button
    private lateinit var btnClearSelected: Button
    private lateinit var btnDeleteSelected: Button
    private var sortByDate = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        supportActionBar?.hide()
        window.insetsController?.setSystemBarsAppearance(
            android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
            android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
        )

        txtCenter = findViewById(R.id.txtCenter)
        txtStatus = findViewById(R.id.txtStatus)
        editRadius = findViewById(R.id.editRadius)
        listResults = findViewById(R.id.listResults)
        btnSort = findViewById(R.id.btnSort)
        btnOpenSelected = findViewById(R.id.btnOpenSelected)
        btnSelectAll = findViewById(R.id.btnSelectAll)
        btnHelp = findViewById(R.id.btnHelp)
        btnClearSelected = findViewById(R.id.btnClearSelected)
        try {
            parseGeoIntent(intent)
        } catch (e: Exception) {
            showError(e)
        }

        findViewById<Button>(R.id.btnSearch).setOnClickListener {
            ensureAllFilesAccessThen { doSearch() }
        }

        btnSort.setOnClickListener {
            sortByDate = !sortByDate
            btnSort.text = if (sortByDate) "Ordenar per distància" else "Ordenar per data"
            applySort()
        }

        btnOpenSelected.setOnClickListener {
            openSelected()
        }

        btnSelectAll.setOnClickListener {
            for (i in 0 until listResults.count) {
                listResults.setItemChecked(i, true)
            }
        }

        btnHelp.setOnClickListener {
            mostrarAjuda()
        }

        btnClearSelected.setOnClickListener {
            clearSelectedFolder()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        try {
            parseGeoIntent(intent)
        } catch (e: Exception) {
            showError(e)
        }
    }

    /** Interpreta un intent geo:lat,lon o geo:0,0?q=lat,lon */
    private fun parseGeoIntent(intent: Intent?) {
        val data: Uri? = intent?.data
        if (data == null || data.scheme != "geo") return

        val q = try { data.getQueryParameter("q") } catch (e: Exception) { null }
        val raw = if (!q.isNullOrBlank()) q else data.schemeSpecificPart.substringBefore("?")

        val parts = raw.split(",")
        if (parts.size >= 2) {
            val lat = parts[0].trim().toDoubleOrNull()
            val lon = parts[1].trim().split("(")[0].trim().toDoubleOrNull()
            if (lat != null && lon != null) {
                centerLat = lat
                centerLon = lon
                txtCenter.text = "Centre: %.6f, %.6f".format(lat, lon)
            }
        }
    }

    private fun ensureAllFilesAccessThen(action: () -> Unit) {
        if (Environment.isExternalStorageManager()) {
            action()
        } else {
            Toast.makeText(this, "Cal donar permís d'accés a tots els fitxers", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }
    }

    private fun doSearch() {
        val lat = centerLat
        val lon = centerLon
        if (lat == null || lon == null) {
            Toast.makeText(this, "No hi ha cap coordenada rebuda (obre l'app des d'OruxMaps)", Toast.LENGTH_LONG).show()
            return
        }
        val radius = editRadius.text.toString().toDoubleOrNull()
        if (radius == null || radius <= 0) {
            Toast.makeText(this, "Radi no vàlid", Toast.LENGTH_SHORT).show()
            return
        }

        txtStatus.text = "Cercant..."
        listResults.adapter = null

        CoroutineScope(Dispatchers.Main).launch {
            val found = withContext(Dispatchers.IO) {
                scanTracklogs(lat, lon, radius)
            }
            matches = found
            applySort()
        }
    }

    private fun applySort() {
        val sorted = if (sortByDate) {
            matches.sortedByDescending { it.dateMillis ?: 0L }
        } else {
            matches.sortedBy { it.distanceKm }
        }
        matches = sorted
        txtStatus.text = "${matches.size} ruta/es trobades (marca les que vulguis i prem \"Copiar a seleccionades\")"
        val labels = matches.map {
            val dataText = it.dateMillis?.let { d ->
                java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date(d))
            } ?: "sense data"
            "${it.file.name}  (%.2f km, $dataText)".format(it.distanceKm)
        }
        listResults.adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_list_item_multiple_choice, labels)
    }
    private fun openSelected() {
        val checked = listResults.checkedItemPositions
        val selectedFiles = mutableListOf<File>()
        for (i in 0 until checked.size()) {
            val position = checked.keyAt(i)
            if (checked.valueAt(i)) {
                selectedFiles.add(matches[position].file)
            }
        }
        if (selectedFiles.isEmpty()) {
            Toast.makeText(this, "No has marcat cap ruta", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val destDir = File(Environment.getExternalStorageDirectory(), "oruxmaps/tracklogs/_seleccionades")
            destDir.mkdirs()
            var copiats = 0
            for (f in selectedFiles) {
                val dest = File(destDir, f.name)
                f.copyTo(dest, overwrite = true)
                copiats++
            }
            Toast.makeText(this, "$copiats ruta/es copiades a _seleccionades. Obrint OruxMaps...", Toast.LENGTH_LONG).show()
            android.os.Handler(mainLooper).postDelayed({
                moveTaskToBack(true)
            }, 2500)
        } catch (e: Exception) {
            showError(e)
        }
    }

    private fun clearSelectedFolder() {
        val destDir = File(Environment.getExternalStorageDirectory(), "oruxmaps/tracklogs/_seleccionades")
        val fitxers = destDir.listFiles()
        if (fitxers == null || fitxers.isEmpty()) {
            Toast.makeText(this, "La carpeta _seleccionades ja és buida", Toast.LENGTH_SHORT).show()
            return
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Buidar _seleccionades")
            .setMessage("Segur que vols eliminar els ${fitxers.size} fitxer/s de la carpeta _seleccionades?")
            .setPositiveButton("Buidar") { _, _ ->
                var esborrats = 0
                for (f in fitxers) {
                    if (f.delete()) esborrats++
                }
                Toast.makeText(this, "$esborrats fitxer/s eliminats de _seleccionades", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel·lar", null)
            .show()
    }

    private fun deleteFiles(files: List<File>) {
        var esborrats = 0
        for (f in files) {
            if (f.delete()) esborrats++
        }
        matches = matches.filterNot { it.file in files }
        applySort()
        Toast.makeText(this, "$esborrats ruta/es eliminades", Toast.LENGTH_SHORT).show()
    }
    private fun scanTracklogs(lat: Double, lon: Double, radiusKm: Double): List<GpxMatch> {
        val baseDir = File(Environment.getExternalStorageDirectory(), "oruxmaps/tracklogs")
        if (!baseDir.exists()) return emptyList()

        val result = mutableListOf<GpxMatch>()
        baseDir.walkTopDown()
            .filter { it.isFile && it.extension.equals("gpx", ignoreCase = true) && !it.path.contains("_seleccionades") }
            .forEach { file ->
                val res = closestPointDistanceKm(file, lat, lon)
                if (res != null && res.first <= radiusKm) {
                    result.add(GpxMatch(file, res.first, res.second))
                }
            }
        return result
    }

    /** Llegeix els trkpt/wpt del gpx i retorna la distància mínima al centre, o null si cap punt és vàlid */
    private fun closestPointDistanceKm(file: File, lat: Double, lon: Double): Pair<Double, Long?>? {
        var minDist: Double? = null
        var dateMillis: Long? = null
        val dateFormats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ssXXX"
        )
        try {
            val parser: XmlPullParser = android.util.Xml.newPullParser()
            file.inputStream().use { input ->
                parser.setInput(input, null)
                var eventType = parser.eventType
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG &&
                        (parser.name == "trkpt" || parser.name == "wpt" || parser.name == "rtept")) {
                        val ptLat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                        val ptLon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
                        if (ptLat != null && ptLon != null) {
                            val d = haversineKm(lat, lon, ptLat, ptLon)
                            if (minDist == null || d < minDist!!) minDist = d
                        }
                    }
                    if (eventType == XmlPullParser.START_TAG && parser.name == "time" && dateMillis == null) {
                        val text = try { parser.nextText() } catch (e: Exception) { null }
                        if (text != null) {
                            for (fmt in dateFormats) {
                                try {
                                    val sdf = java.text.SimpleDateFormat(fmt, java.util.Locale.getDefault())
                                    sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                                    dateMillis = sdf.parse(text)?.time
                                    if (dateMillis != null) break
                                } catch (e: Exception) { }
                            }
                        }
                    }
                    eventType = parser.next()
                }
            }
        } catch (e: Exception) {
            return null
        }
        return if (minDist != null) Pair(minDist!!, dateMillis) else null
    }

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
    private fun obrirOruxMaps() {
        try {
            val intent = packageManager.getLaunchIntentForPackage("com.orux.oruxmaps")
            if (intent != null) {
                startActivity(intent)
            } else {
                Toast.makeText(this, "No s'ha trobat OruxMaps instal·lat", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            showError(e)
        }
    }
    private fun openInOruxMaps(file: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/gpx+xml")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "No s'ha pogut obrir a OruxMaps: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    private fun mostrarAjuda() {
        val text = """
            Com funciona Geo GPX Cercar:

            1. Envia una coordenada des d'OruxMaps a aquesta app (Compartir mapa / Comparteix la posició del mapa / Com geo: Intent).
            2. Indica el radi de cerca en km (per defecte, 5).
            3. Prem "Cercar": es buscaran totes les rutes GPX de /oruxmaps/tracklogs (i subcarpetes) que tinguin algun punt dins d'aquest radi.
            4. Pots ordenar els resultats per distància a la coordenada enviada o per la data interna de la ruta amb el botó corresponent.
            5. Marca les rutes que t'interessin (o prem "Seleccionar totes").
            6. Prem "Copiar a seleccionades": es copiaran a una carpeta especial i s'obrirà OruxMaps automàticament perquè les importis totes de cop.
            7. "Eliminar seleccionades" buida aquesta carpeta especial (no toca les rutes originals).
        """.trimIndent()

        android.app.AlertDialog.Builder(this)
            .setTitle("Ajuda")
            .setMessage(text)
            .setPositiveButton("D'acord", null)
            .show()
    }
    private fun showError(e: Throwable) {
        android.app.AlertDialog.Builder(this)
            .setTitle("Error")
            .setMessage(e.toString() + "\n\n" + e.stackTrace.take(6).joinToString("\n"))
            .setPositiveButton("OK", null)
            .show()
    }
}
