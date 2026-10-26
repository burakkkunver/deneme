package com.rotaasistani.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import java.util.Locale
import kotlin.math.roundToInt

/** Durum renkleri harita ve listede aynı anlamı taşır. */
object Palette {
    val Next = Color(0xFFEA580C)
    val Active = Color(0xFF2563EB)
    val Delivered = Color(0xFF16A34A)
    val Postponed = Color(0xFFD97706)
    val Failed = Color(0xFFDC2626)
    val Depot = Color(0xFF334155)
    val Route = Color(0xCC2563EB)
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCCFBF1),
    onPrimaryContainer = Color(0xFF042F2E),
    secondary = Color(0xFF2563EB),
    tertiary = Color(0xFFD97706),
    error = Color(0xFFDC2626),
)

@Composable
fun RotaTheme(content: @Composable () -> Unit) {
    // Kuryeler güneş altında çalışıyor: yüksek kontrastlı açık tema.
    MaterialTheme(colorScheme = LightColors, content = content)
}

private val TURKISH = Locale.forLanguageTag("tr-TR")

fun formatDistance(meters: Double): String =
    if (meters < 1_000) "${meters.roundToInt()} m" else String.format(TURKISH, "%.1f km", meters / 1_000)

fun formatDuration(seconds: Double): String {
    val minutes = (seconds / 60).roundToInt()
    return if (minutes < 60) "$minutes dk" else "${minutes / 60} sa ${minutes % 60} dk"
}

fun percentSaved(base: Double, value: Double): Int =
    if (base <= 0) 0 else ((1 - value / base) * 100).roundToInt()
