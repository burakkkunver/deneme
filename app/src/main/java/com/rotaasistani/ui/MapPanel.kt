package com.rotaasistani.ui

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.location.Location
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rotaasistani.UiState
import com.rotaasistani.core.LatLng
import com.rotaasistani.model.StopStatus
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.IMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

/**
 * OpenStreetMap haritası (osmdroid). API anahtarı gerektirmediği için
 * prototipte tercih edildi; üretimde Google Maps SDK veya MapLibre olabilir.
 */
@Composable
fun MapPanel(
    state: UiState,
    hasLocationPermission: Boolean,
    onStopClick: (String) -> Unit,
    onLocation: (LatLng) -> Unit,
    onFit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val currentOnLocation by rememberUpdatedState(onLocation)
    val currentOnStopClick by rememberUpdatedState(onStopClick)
    val permission = rememberUpdatedState(hasLocationPermission)

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            isTilesScaledToDpi = true
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(15.0)
            controller.setCenter(GeoPoint(state.depot.lat, state.depot.lon))
        }
    }
    val locationOverlay = remember {
        object : MyLocationNewOverlay(GpsMyLocationProvider(context), mapView) {
            override fun onLocationChanged(location: Location?, source: IMyLocationProvider?) {
                super.onLocationChanged(location, source)
                if (location != null) {
                    mapView.post { currentOnLocation(LatLng(location.latitude, location.longitude)) }
                }
            }
        }
    }
    val icons = remember { HashMap<String, BitmapDrawable>() }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    mapView.onResume()
                    if (permission.value) locationOverlay.enableMyLocation()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    locationOverlay.disableMyLocation()
                    mapView.onPause()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            locationOverlay.disableMyLocation()
            mapView.onDetach()
        }
    }

    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) locationOverlay.enableMyLocation()
    }

    LaunchedEffect(state.stops, state.routeIds, state.routeLine, state.depot) {
        renderOverlays(mapView, locationOverlay, state, icons) { currentOnStopClick(it) }
    }

    LaunchedEffect(state.fitRequest) {
        if (state.fitRequest > 0) {
            val points = state.stops.map { it.position } + state.depot
            fitTo(mapView, points, (48 * context.resources.displayMetrics.density).toInt())
        }
    }

    LaunchedEffect(state.focusRequest) {
        val focus = state.focus
        if (state.focusRequest > 0 && focus != null) {
            if (mapView.zoomLevelDouble < 16.0) mapView.controller.setZoom(16.5)
            mapView.controller.animateTo(GeoPoint(focus.lat, focus.lon))
        }
    }

    Box(modifier) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        MapLegend(Modifier.align(Alignment.TopStart).padding(8.dp))

        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SmallFloatingActionButton(onClick = {
                locationOverlay.myLocation?.let { mapView.controller.animateTo(it) }
            }) {
                Icon(Icons.Default.LocationOn, contentDescription = "Konumuma git")
            }
            SmallFloatingActionButton(onClick = onFit) {
                Icon(Icons.Default.Place, contentDescription = "Tüm durakları göster")
            }
        }

        Text(
            text = "© OpenStreetMap katkıda bulunanlar",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .background(Color.White.copy(alpha = 0.8f))
                .padding(horizontal = 4.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun MapLegend(modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(8.dp), color = Color.White.copy(alpha = 0.9f), shadowElevation = 2.dp) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            LegendRow(Palette.Next, "Sıradaki")
            LegendRow(Palette.Active, "Rotada")
            LegendRow(Palette.Delivered, "Teslim")
            LegendRow(Palette.Postponed, "Ertelendi")
            LegendRow(Palette.Failed, "İade")
        }
    }
}

@Composable
private fun LegendRow(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

private fun renderOverlays(
    mapView: MapView,
    locationOverlay: Overlay,
    state: UiState,
    icons: MutableMap<String, BitmapDrawable>,
    onStopClick: (String) -> Unit,
) {
    val density = mapView.context.resources.displayMetrics.density
    mapView.overlays.clear()

    if (state.routeLine.size >= 2) {
        val line = Polyline(mapView).apply {
            setPoints(state.routeLine.map { GeoPoint(it.lat, it.lon) })
            outlinePaint.color = Palette.Route.toArgb()
            outlinePaint.strokeWidth = 6f * density
            outlinePaint.strokeCap = Paint.Cap.ROUND
            outlinePaint.strokeJoin = Paint.Join.ROUND
            outlinePaint.isAntiAlias = true
            setOnClickListener { _, _, _ -> true }
        }
        mapView.overlays.add(line)
    }

    mapView.overlays.add(
        marker(mapView, state.depot, "Ş", Palette.Depot, big = false, title = "Şube", icons = icons, onClick = null),
    )

    val order = state.activeRoute.withIndex().associate { (index, stop) -> stop.id to index + 1 }
    // Sonradan eklenen üstte çizilir: önce kapanan duraklar, en son "sıradaki".
    val sorted = state.stops.sortedBy { stop -> order[stop.id]?.let { order.size - it } ?: -1 }
    for (stop in sorted) {
        val number = order[stop.id]
        val (label, color) = when {
            number == 1 -> "1" to Palette.Next
            number != null -> number.toString() to Palette.Active
            stop.status == StopStatus.DELIVERED -> "✓" to Palette.Delivered
            stop.status == StopStatus.POSTPONED -> "↻" to Palette.Postponed
            stop.status == StopStatus.FAILED -> "!" to Palette.Failed
            else -> "•" to Palette.Active
        }
        mapView.overlays.add(
            marker(
                mapView,
                stop.position,
                label,
                color,
                big = number == 1,
                title = stop.recipient,
                icons = icons,
                onClick = { onStopClick(stop.id) },
            ),
        )
    }

    mapView.overlays.add(locationOverlay)
    mapView.invalidate()
}

private fun marker(
    mapView: MapView,
    position: LatLng,
    label: String,
    color: Color,
    big: Boolean,
    title: String,
    icons: MutableMap<String, BitmapDrawable>,
    onClick: (() -> Unit)?,
): Marker {
    val argb = color.toArgb()
    val icon = icons.getOrPut("$label|$argb|$big") { numberIcon(mapView.context.resources, label, argb, big) }
    return Marker(mapView).apply {
        this.position = GeoPoint(position.lat, position.lon)
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        setIcon(icon)
        this.title = title
        setOnMarkerClickListener { _, _ ->
            onClick?.invoke()
            true
        }
    }
}

private fun numberIcon(resources: Resources, label: String, color: Int, big: Boolean): BitmapDrawable {
    val density = resources.displayMetrics.density
    val size = ((if (big) 36 else 26) * density).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val radius = size / 2f
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        this.color = android.graphics.Color.WHITE
    }
    canvas.drawCircle(radius, radius, radius - 2f * density, fill)
    canvas.drawCircle(radius, radius, radius - 2f * density, stroke)
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = android.graphics.Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        textSize = (if (label.length >= 3) 0.34f else 0.44f) * size
    }
    val baseline = radius - (text.descent() + text.ascent()) / 2
    canvas.drawText(label, radius, baseline, text)
    return BitmapDrawable(resources, bitmap)
}

private fun fitTo(mapView: MapView, points: List<LatLng>, paddingPx: Int) {
    if (points.isEmpty()) return
    val box = BoundingBox(
        points.maxOf { it.lat },
        points.maxOf { it.lon },
        points.minOf { it.lat },
        points.minOf { it.lon },
    )
    if (mapView.width > 0 && mapView.height > 0) {
        mapView.zoomToBoundingBox(box, true, paddingPx)
    } else {
        mapView.addOnFirstLayoutListener { _, _, _, _, _ -> mapView.zoomToBoundingBox(box, false, paddingPx) }
    }
}
