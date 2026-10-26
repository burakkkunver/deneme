package com.rotaasistani.core

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLng(val lat: Double, val lon: Double)

object Geo {
    private const val EARTH_RADIUS_M = 6_371_000.0

    /** İki nokta arası kuş uçuşu mesafe (metre). */
    fun haversineMeters(a: LatLng, b: LatLng): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_M * asin(min(1.0, sqrt(h)))
    }

    /**
     * Kuş uçuşu mesafe matrisi. [detourFactor] sokak ağının kuş uçuşuna göre
     * ne kadar uzun olduğunu kabaca temsil eder (şehir içi için ~1.3).
     */
    fun distanceMatrix(points: List<LatLng>, detourFactor: Double = 1.0): Array<DoubleArray> =
        Array(points.size) { i ->
            DoubleArray(points.size) { j ->
                if (i == j) 0.0 else haversineMeters(points[i], points[j]) * detourFactor
            }
        }

    fun pathLengthMeters(points: List<LatLng>): Double =
        points.zipWithNext { a, b -> haversineMeters(a, b) }.sum()
}
