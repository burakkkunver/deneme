package com.rotaasistani.data

import com.rotaasistani.core.LatLng
import com.rotaasistani.model.Stop
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Sunum için sahte paket listesi üretir. Gerçek müşteri verisi kullanılmaz;
 * isimler ve adresler rastgeledir.
 */
object DemoData {
    val DEFAULT_CENTER = LatLng(40.9923, 29.1244) // Ataşehir
    val DEFAULT_DEPOT = LatLng(40.9862, 29.1180)
    const val DEFAULT_COUNT = 65
    const val DEFAULT_SEED = 2026L

    private const val METERS_PER_DEGREE = 111_320.0

    private val firstNames = listOf(
        "Ayşe", "Mehmet", "Fatma", "Mustafa", "Zeynep", "Ahmet", "Elif", "Ali", "Emine", "Hüseyin",
        "Merve", "Hasan", "Büşra", "İbrahim", "Esra", "Murat", "Selin", "Emre", "Deniz", "Can",
        "Ece", "Burak", "Gizem", "Oğuz", "Seda", "Kerem", "Derya", "Onur", "Yasemin", "Tolga",
    )
    private const val INITIALS = "ABÇDEFGHIİKLMNOÖPRSŞTUÜVYZ"

    private data class Building(val position: LatLng, val street: Int, val number: Int)

    fun generate(
        center: LatLng,
        count: Int = DEFAULT_COUNT,
        seed: Long = DEFAULT_SEED,
        radiusMeters: Double = 1_100.0,
    ): List<Stop> {
        val random = Random(seed)
        val buildings = ArrayList<Building>()
        return List(count) { index ->
            // Gerçek hayattaki gibi bazı paketler aynı binaya gider.
            val building = if (buildings.isNotEmpty() && random.nextDouble() < 0.2) {
                buildings[random.nextInt(buildings.size)]
            } else {
                Building(
                    position = randomInDisk(center, radiusMeters, random),
                    street = random.nextInt(1, 60),
                    number = random.nextInt(1, 90),
                ).also { buildings += it }
            }
            val position = jitter(building.position, 4.0, random)
            Stop(
                id = "S${index + 1}",
                trackingNo = String.format(Locale.US, "KRG-%06d", 310_000 + index * 7 + random.nextInt(7)),
                recipient = "${firstNames[random.nextInt(firstNames.size)]} ${INITIALS[random.nextInt(INITIALS.length)]}.",
                address = "${building.street}. Sokak No:${building.number} D:${random.nextInt(1, 25)}",
                lat = position.lat,
                lon = position.lon,
                desi = random.nextInt(1, 30),
            )
        }
    }

    private fun randomInDisk(center: LatLng, radiusMeters: Double, random: Random): LatLng {
        val r = radiusMeters * sqrt(random.nextDouble())
        val theta = 2 * PI * random.nextDouble()
        return offset(center, r * cos(theta), r * sin(theta))
    }

    private fun jitter(point: LatLng, meters: Double, random: Random): LatLng =
        offset(point, (random.nextDouble() * 2 - 1) * meters, (random.nextDouble() * 2 - 1) * meters)

    private fun offset(point: LatLng, northMeters: Double, eastMeters: Double) = LatLng(
        lat = point.lat + northMeters / METERS_PER_DEGREE,
        lon = point.lon + eastMeters / (METERS_PER_DEGREE * cos(Math.toRadians(point.lat))),
    )
}
