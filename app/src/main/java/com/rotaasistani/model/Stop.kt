package com.rotaasistani.model

import com.rotaasistani.core.LatLng

enum class StopStatus {
    /** Ana turda dağıtılmayı bekliyor. */
    PENDING,
    DELIVERED,

    /** "Sonra tekrar uğra": ana tur bitince ayrı rota ile tekrar denenir. */
    POSTPONED,

    /** Bugün teslim edilemedi, şubeye iade. */
    FAILED,
}

data class Stop(
    val id: String,
    val trackingNo: String,
    val recipient: String,
    val address: String,
    val lat: Double,
    val lon: Double,
    val desi: Int,
    val status: StopStatus = StopStatus.PENDING,
    val note: String = "",
    val failReason: String = "",
    val attempts: Int = 0,
    val updatedAt: Long = 0L,
) {
    val position: LatLng get() = LatLng(lat, lon)
}

val FAIL_REASONS = listOf(
    "Alıcı adreste yok",
    "Adres bulunamadı / eksik adres",
    "Alıcı paketi reddetti",
    "İş yeri kapalı",
    "Paket hasarlı",
    "Diğer",
)

val POSTPONE_NOTES = listOf(
    "Alıcı evde yok, sonra tekrar uğra",
    "Alıcı arandı, 1 saat sonra gel dedi",
    "İş yeri öğle arasında kapalı",
    "Bina girişi kapalı, görevli yok",
)
