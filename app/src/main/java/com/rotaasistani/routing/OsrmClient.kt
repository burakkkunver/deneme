package com.rotaasistani.routing

import com.rotaasistani.core.Geo
import com.rotaasistani.core.LatLng
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * OSRM yol ağı servisi istemcisi. Varsayılan adres OSRM'in herkese açık demo
 * sunucusudur; yalnızca prototip içindir. Üretimde şirketin kendi OSRM /
 * Valhalla / GraphHopper sunucusu veya ticari bir rota API'si kullanılmalı.
 *
 * Tüm fonksiyonlar hata durumunda null döner; çağıran taraf kuş uçuşu
 * tahmine geri düşer, böylece internet olmadan da uygulama çalışır.
 */
class OsrmClient(
    private val userAgent: String,
    private val baseUrl: String = "https://router.project-osrm.org",
) {
    data class Route(val points: List<LatLng>, val distanceMeters: Double, val durationSeconds: Double)

    /** Noktanın bağlandığı (yapıştığı) yol: adı, yol üzerindeki konum ve uzaklık. */
    data class Snap(val name: String, val location: LatLng, val distanceMeters: Double)

    /** Tüm noktaları tek istekte en yakın yola bağlar. */
    fun snap(points: List<LatLng>): List<Snap>? {
        if (points.isEmpty() || points.size > MAX_TABLE_POINTS) return null
        return try {
            val json = query("$baseUrl/table/v1/driving/${coordinates(points)}?destinations=0") ?: return null
            val sources = json.getJSONArray("sources")
            List(sources.length()) { i ->
                val waypoint = sources.getJSONObject(i)
                val location = waypoint.getJSONArray("location")
                Snap(
                    name = waypoint.optString("name"),
                    location = LatLng(lat = location.getDouble(1), lon = location.getDouble(0)),
                    distanceMeters = waypoint.optDouble("distance", 0.0),
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Yol ağı üzerinden metre cinsinden mesafe matrisi. */
    fun distanceMatrix(points: List<LatLng>): Array<DoubleArray>? {
        if (points.size < 2 || points.size > MAX_TABLE_POINTS) return null
        return try {
            val json = query("$baseUrl/table/v1/driving/${coordinates(points)}?annotations=distance") ?: return null
            val rows = json.getJSONArray("distances")
            Array(points.size) { i ->
                val row = rows.getJSONArray(i)
                DoubleArray(points.size) { j ->
                    if (row.isNull(j)) {
                        Geo.haversineMeters(points[i], points[j]) * UNREACHABLE_PENALTY
                    } else {
                        row.getDouble(j)
                    }
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Verilen sırayla durakları bağlayan, sokakları takip eden çizgi.
     * continue_straight=false: kurye durakta geri dönebilir; OSRM'in varsayılanı
     * her durakta düz devam etmeye zorlayıp blok etrafında gereksiz tur attırır.
     */
    fun route(points: List<LatLng>): Route? {
        if (points.size < 2) return null
        val line = ArrayList<LatLng>()
        var distance = 0.0
        var duration = 0.0
        var from = 0
        while (from < points.size - 1) {
            val to = minOf(from + MAX_ROUTE_POINTS - 1, points.size - 1)
            val part = routeChunk(points.subList(from, to + 1)) ?: return null
            line.addAll(if (line.isEmpty()) part.points else part.points.drop(1))
            distance += part.distanceMeters
            duration += part.durationSeconds
            from = to
        }
        return Route(line, distance, duration)
    }

    private fun routeChunk(points: List<LatLng>): Route? = try {
        val json = query("$baseUrl/route/v1/driving/${coordinates(points)}?overview=full&geometries=geojson&steps=false&continue_straight=false")
        if (json == null) {
            null
        } else {
            val route = json.getJSONArray("routes").getJSONObject(0)
            val coordinates = route.getJSONObject("geometry").getJSONArray("coordinates")
            val line = List(coordinates.length()) { k ->
                val pair = coordinates.getJSONArray(k)
                LatLng(lat = pair.getDouble(1), lon = pair.getDouble(0))
            }
            Route(line, route.getDouble("distance"), route.getDouble("duration"))
        }
    } catch (e: Exception) {
        null
    }

    private fun query(url: String): JSONObject? {
        val json = JSONObject(get(url))
        return if (json.optString("code") == "Ok") json else null
    }

    private fun coordinates(points: List<LatLng>) =
        points.joinToString(";") { String.format(Locale.US, "%.6f,%.6f", it.lon, it.lat) }

    private fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 7_000
            connection.readTimeout = 12_000
            connection.setRequestProperty("User-Agent", userAgent)
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw IOException("HTTP $code")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        // OSRM demo sunucusunun tablo sınırı 100 koordinat.
        const val MAX_TABLE_POINTS = 100
        const val MAX_ROUTE_POINTS = 80
        const val UNREACHABLE_PENALTY = 3.0
    }
}
