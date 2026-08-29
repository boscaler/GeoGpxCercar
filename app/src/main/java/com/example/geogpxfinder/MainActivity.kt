package com.example.geogpxfinder

import android.app.AlertDialog
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

data class GpxMatch(val file: File, val distanceKm: Double)

class MainActivity : AppCompatActivity() {

    private var centerLat: Double? = null
    private var centerLon: Double? = null
    private var matches: List<GpxMatch> = emptyList()

    private lateinit var txtCenter: TextView
    private lateinit var txtStatus: TextView
    private lateinit var editRadius: EditText
    private lateinit var listResults: ListView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        txtCenter = findViewById(R.id.txtCenter)
        txtStatus = findViewById(R.id.txtStatus)
        editRadius = findViewById(R.id.editRadius)
        listResults = findViewById(R.id.listResults)

        try {
            parseGeoIntent(intent)
        } catch (e: Exception) {
            showError(e)
        }

        findViewById<Button>(R.id.btnSearch).setOnClickListener {
            try {
                ensureAllFilesAccessThen { doSearch() }
            } catch (e: Exception) {
                showError(e)
            }
        }

        listResults.setOnItemClickListener { _, _, position, _ ->
            openInOruxMaps(matches[position].file)
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

    private fun distanciaKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0] / 1000.0
    }
   
    private fun parseCoordenada(q: String?): Pair<Double, Double>? {
        if (q == null) return null
        val parts = q.split(",")
        if (parts.size < 2) return null
        val lat = parts[0].trim().toDoubleOrNull() ?: return null
        val lon = parts[1].trim().toDoubleOrNull() ?: return null
        return Pair(lat, lon)
    }
    
    private fun buscarFitxersGpx(carpeta: java.io.File): List<java.io.File> {
        val trobats = mutableListOf<java.io.File>()
        val fitxers = carpeta.listFiles() ?: return trobats
        for (f in fitxers) {
            if (f.isDirectory) {
                trobats.addAll(buscarFitxersGpx(f))
            } else if (f.name.endsWith(".gpx", ignoreCase = true)) {
                trobats.add(f)
            }
        }
        return trobats
    }
   
    private fun gpxTeUnPuntDinsRadi(fitxer: java.io.File, latCentre: Double, lonCentre: Double, radiKm: Double): Boolean {
        try {
            val text = fitxer.readText()
            val regex = Regex("lat=\"([-0-9.]+)\"\\s+lon=\"([-0-9.]+)\"")
            for (match in regex.findAll(text)) {
                val lat = match.groupValues[1].toDoubleOrNull() ?: continue
                val lon = match.groupValues[2].toDoubleOrNull() ?: continue
                if (distanciaKm(latCentre, lonCentre, lat, lon) <= radiKm) {
                    return true
                }
            }
        } catch (e: Exception) {
            // fitxer il·legible, l'ignorem
        }
        return false
    }
    
    private fun showError(e: Throwable) {
        AlertDialog.Builder(this)
            .setTitle("Error")
            .setMessage(e.toString() + "\n\n" + e.stackTrace.take(6).joinToString("\n"))
            .setPositiveButton("OK", null)
            .show()
    }

    /** Interpreta un intent geo:lat,lon o geo:0,0?q=lat,lon */
    private fun parseGeoIntent(intent: Intent?) {
        val data: Uri? = intent?.data
        txtStatus.text = "Intent rebut: ${intent?.action} / data=$data"
        if (data == null || data.scheme != "geo") return

        val q = data?.schemeSpecificPart
            ?.substringBefore("?")
            Toast.makeText(this, "data = $data", Toast.LENGTH_LONG).show()
        val radiusInput = EditText(this)
        radiusInput.setText("5")
        radiusInput.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL

        AlertDialog.Builder(this)
            .setTitle("Radi de cerca (km)")
            .setView(radiusInput)
            .setPositiveButton("Buscar") { _, _ ->
                val radiusKm = radiusInput.text.toString().toDoubleOrNull() ?: 5.0
                val coord = parseCoordenada(q)
                if (coord == null) {
                    AlertDialog.Builder(this)
                        .setTitle("Error")
                        .setMessage("No s'ha pogut llegir la coordenada")
                        .setPositiveButton("OK", null)
                        .show()
                } else {
                    val (lat, lon) = coord
                    val carpeta = java.io.File(android.os.Environment.getExternalStorageDirectory(), "oruxmaps/tracklogs")
                    val gpxFiles = buscarFitxersGpx(carpeta)
                    Toast.makeText(this, "GPX trobats: ${gpxFiles.size}, carpeta existeix: ${carpeta.exists()}", Toast.LENGTH_LONG).show()
                    val trobats = gpxFiles.filter { gpxTeUnPuntDinsRadi(it, lat, lon, radiusKm) }

                    val missatge = if (trobats.isEmpty()) {
                        "No s'ha trobat cap ruta dins el radi indicat"
                    } else {
                        trobats.joinToString("\n") { it.name }
                    }

                    AlertDialog.Builder(this)
                        .setTitle("Rutes trobades (${trobats.size})")
                        .setMessage(missatge)
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
            .setNegativeButton("Cancel·lar", null)
            .show()    
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
            try {
                val found = withContext(Dispatchers.IO) {
                    scanTracklogs(lat, lon, radius)
                }
                matches = found.sortedBy { it.distanceKm }
                txtStatus.text = "${matches.size} ruta/es trobades (toca per obrir a OruxMaps)"
                val labels = matches.map { "${it.file.name}  (%.2f km)".format(it.distanceKm) }
                listResults.adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_list_item_1, labels)
            } catch (e: Exception) {
                showError(e)
            }
        }
    }

    private fun scanTracklogs(lat: Double, lon: Double, radiusKm: Double): List<GpxMatch> {
        val baseDir = File(Environment.getExternalStorageDirectory(), "oruxmaps/tracklogs")
        if (!baseDir.exists()) return emptyList()

        val result = mutableListOf<GpxMatch>()
        baseDir.walkTopDown()
            .filter { it.isFile && it.extension.equals("gpx", ignoreCase = true) }
            .forEach { file ->
                val minDist = closestPointDistanceKm(file, lat, lon)
                if (minDist != null && minDist <= radiusKm) {
                    result.add(GpxMatch(file, minDist))
                }
            }
        return result
    }

    private fun closestPointDistanceKm(file: File, lat: Double, lon: Double): Double? {
        var minDist: Double? = null
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
                    eventType = parser.next()
                }
            }
        } catch (e: Exception) {
            return null
        }
        return minDist
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
}
