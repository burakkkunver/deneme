package com.rotaasistani.data

import android.content.Context
import com.rotaasistani.core.LatLng
import com.rotaasistani.model.Stop
import com.rotaasistani.model.StopStatus
import org.json.JSONArray
import org.json.JSONObject

/**
 * Uygulama kapanıp açılsa da günün durumu kaybolmasın diye basit JSON kaydı.
 * Gerçek üründe bunun yerini Room + sunucu senkronizasyonu almalı.
 */
class StopStore(context: Context) {

    data class Snapshot(
        val stops: List<Stop>,
        val routeIds: List<String>,
        val depot: LatLng,
        val lastPosition: LatLng?,
        val baselineMeters: Double?,
        val greedyMeters: Double?,
        val optimizedMeters: Double?,
        val plannedOnRoads: Boolean,
    )

    private val prefs = context.getSharedPreferences("rota_asistani_state", Context.MODE_PRIVATE)

    fun save(snapshot: Snapshot) {
        val json = JSONObject().apply {
            put("version", 1)
            put("stops", JSONArray().apply { snapshot.stops.forEach { put(it.toJson()) } })
            put("routeIds", JSONArray(snapshot.routeIds))
            put("depot", snapshot.depot.toJson())
            snapshot.lastPosition?.let { put("lastPosition", it.toJson()) }
            snapshot.baselineMeters?.let { put("baselineMeters", it) }
            snapshot.greedyMeters?.let { put("greedyMeters", it) }
            snapshot.optimizedMeters?.let { put("optimizedMeters", it) }
            put("plannedOnRoads", snapshot.plannedOnRoads)
        }
        prefs.edit().putString(KEY, json.toString()).apply()
    }

    fun load(): Snapshot? {
        val raw = prefs.getString(KEY, null) ?: return null
        return try {
            val json = JSONObject(raw)
            val stops = json.getJSONArray("stops").let { array ->
                List(array.length()) { array.getJSONObject(it).toStop() }
            }
            val routeIds = json.getJSONArray("routeIds").let { array ->
                List(array.length()) { array.getString(it) }
            }
            Snapshot(
                stops = stops,
                routeIds = routeIds,
                depot = json.getJSONObject("depot").toLatLng(),
                lastPosition = json.optJSONObject("lastPosition")?.toLatLng(),
                baselineMeters = json.optionalDouble("baselineMeters"),
                greedyMeters = json.optionalDouble("greedyMeters"),
                optimizedMeters = json.optionalDouble("optimizedMeters"),
                plannedOnRoads = json.optBoolean("plannedOnRoads"),
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun Stop.toJson() = JSONObject().apply {
        put("id", id)
        put("trackingNo", trackingNo)
        put("recipient", recipient)
        put("address", address)
        put("lat", lat)
        put("lon", lon)
        put("desi", desi)
        put("status", status.name)
        put("note", note)
        put("failReason", failReason)
        put("attempts", attempts)
        put("updatedAt", updatedAt)
    }

    private fun JSONObject.toStop() = Stop(
        id = getString("id"),
        trackingNo = getString("trackingNo"),
        recipient = getString("recipient"),
        address = getString("address"),
        lat = getDouble("lat"),
        lon = getDouble("lon"),
        desi = optInt("desi", 1),
        status = StopStatus.valueOf(getString("status")),
        note = optString("note"),
        failReason = optString("failReason"),
        attempts = optInt("attempts"),
        updatedAt = optLong("updatedAt"),
    )

    private fun LatLng.toJson() = JSONObject().apply {
        put("lat", lat)
        put("lon", lon)
    }

    private fun JSONObject.toLatLng() = LatLng(getDouble("lat"), getDouble("lon"))

    private fun JSONObject.optionalDouble(name: String): Double? = if (has(name)) getDouble(name) else null

    private companion object {
        const val KEY = "state"
    }
}
