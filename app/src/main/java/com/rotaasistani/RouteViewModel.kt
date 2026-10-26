package com.rotaasistani

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rotaasistani.core.Geo
import com.rotaasistani.core.LatLng
import com.rotaasistani.core.RoutePlanner
import com.rotaasistani.data.DemoData
import com.rotaasistani.data.StopStore
import com.rotaasistani.model.Stop
import com.rotaasistani.model.StopStatus
import com.rotaasistani.routing.OsrmClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** MAIN: ana tur, POSTPONED: ana tur bitti, ertelenenler turu, DONE: gün bitti. */
enum class Phase { MAIN, POSTPONED, DONE }

data class UiMessage(val text: String, val undoStopId: String? = null, val id: Long = System.nanoTime())

data class UiState(
    val stops: List<Stop> = emptyList(),
    /** Aktif turun sırası (MAIN'de bekleyenler, POSTPONED'da ertelenenler). */
    val routeIds: List<String> = emptyList(),
    val depot: LatLng = DemoData.DEFAULT_DEPOT,
    /** Son işlem yapılan durak; GPS yoksa rota buradan devam eder. */
    val lastPosition: LatLng? = null,
    val courierLocation: LatLng? = null,
    val routeLine: List<LatLng> = emptyList(),
    val routeLineOnRoads: Boolean = false,
    val remainingMeters: Double = 0.0,
    val remainingSeconds: Double = 0.0,
    /** Karşılaştırma: liste sırasıyla / "hep en yakına git" ile / optimize rota. */
    val baselineMeters: Double? = null,
    val greedyMeters: Double? = null,
    val optimizedMeters: Double? = null,
    val plannedOnRoads: Boolean = false,
    val isOptimizing: Boolean = false,
    val fitRequest: Int = 0,
    val focus: LatLng? = null,
    val focusRequest: Int = 0,
    val message: UiMessage? = null,
) {
    val phase: Phase
        get() = when {
            stops.any { it.status == StopStatus.PENDING } -> Phase.MAIN
            stops.any { it.status == StopStatus.POSTPONED } -> Phase.POSTPONED
            else -> Phase.DONE
        }

    val activeStatus: StopStatus?
        get() = when (phase) {
            Phase.MAIN -> StopStatus.PENDING
            Phase.POSTPONED -> StopStatus.POSTPONED
            Phase.DONE -> null
        }

    val activeRoute: List<Stop>
        get() {
            val status = activeStatus ?: return emptyList()
            val byId = stops.associateBy { it.id }
            return routeIds.mapNotNull { byId[it] }.filter { it.status == status }
        }

    /** Aktif durumda olup henüz rotaya girmemiş duraklar. */
    val unrouted: List<Stop>
        get() {
            val status = activeStatus ?: return emptyList()
            val ids = routeIds.toSet()
            return stops.filter { it.status == status && it.id !in ids }
        }

    fun count(status: StopStatus) = stops.count { it.status == status }
}

class RouteViewModel(application: Application) : AndroidViewModel(application) {

    private val store = StopStore(application)
    private val osrm = OsrmClient(userAgent = "${application.packageName}/prototip")

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var optimizeJob: Job? = null
    private var geometryJob: Job? = null

    init {
        val saved = store.load()
        if (saved != null && saved.stops.isNotEmpty()) {
            _state.value = UiState(
                stops = saved.stops,
                routeIds = saved.routeIds,
                depot = saved.depot,
                lastPosition = saved.lastPosition,
                baselineMeters = saved.baselineMeters,
                greedyMeters = saved.greedyMeters,
                optimizedMeters = saved.optimizedMeters,
                plannedOnRoads = saved.plannedOnRoads,
                fitRequest = 1,
            )
            refreshGeometry()
        } else {
            loadDefaultDemo()
        }
    }

    // region Demo verisi

    fun loadDefaultDemo() = loadDemo(DemoData.DEFAULT_CENTER, DemoData.DEFAULT_DEPOT, "Ataşehir", DemoData.DEFAULT_SEED)

    fun loadDemoAroundMe() {
        val me = _state.value.courierLocation
        if (me == null) {
            show("Konum henüz alınamadı. Konum izni ve GPS açık mı?")
            return
        }
        loadDemo(center = me, depot = me, label = "Konumunuz", seed = System.currentTimeMillis())
    }

    private fun loadDemo(center: LatLng, depot: LatLng, label: String, seed: Long) {
        optimizeJob?.cancel()
        geometryJob?.cancel()
        val stops = DemoData.generate(center, seed = seed)
        _state.update {
            UiState(
                stops = stops,
                depot = depot,
                courierLocation = it.courierLocation,
                fitRequest = it.fitRequest + 1,
                focusRequest = it.focusRequest,
                message = UiMessage("$label çevresinde ${stops.size} demo paket yüklendi"),
            )
        }
        persist()
        optimizeRoute()
    }

    /** Sunumda 60 paketi tek tek işaretlemeden 2. turu göstermek için. */
    fun demoFinishMainRound() {
        val s = _state.value
        if (s.phase != Phase.MAIN) {
            show("Ana tur zaten tamamlandı")
            return
        }
        val pending = s.activeRoute + s.unrouted
        val postponeIds = if (s.count(StopStatus.POSTPONED) == 0 && pending.size >= 8) {
            // Rota boyunca dağılmış 4 paket "evde yok" olsun.
            (1..4).map { pending[it * pending.size / 5].id }.toSet()
        } else {
            emptySet()
        }
        val pendingIds = pending.map { it.id }.toSet()
        val last = pending.lastOrNull { it.id !in postponeIds } ?: pending.last()
        val now = System.currentTimeMillis()
        _state.update { state ->
            state.copy(
                stops = state.stops.map { stop ->
                    when {
                        stop.id in postponeIds -> stop.copy(
                            status = StopStatus.POSTPONED,
                            note = "Demo: alıcı evde yoktu",
                            attempts = stop.attempts + 1,
                            updatedAt = now,
                        )
                        stop.id in pendingIds -> stop.copy(status = StopStatus.DELIVERED, updatedAt = now)
                        else -> stop
                    }
                },
                routeIds = emptyList(),
                lastPosition = last.position,
                message = UiMessage("Ana tur tamamlandı (demo). Ertelenen paketler için rota oluşturabilirsiniz."),
            )
        }
        afterChange(focusNext = false)
    }

    // endregion

    // region Rota

    fun optimizeRoute() {
        val snapshot = _state.value
        val status = snapshot.activeStatus ?: return
        val targets = snapshot.stops.filter { it.status == status }
        if (targets.isEmpty()) return
        val start = startPoint(snapshot, targets)
        optimizeJob?.cancel()
        _state.update { it.copy(isOptimizing = true) }
        optimizeJob = viewModelScope.launch {
            val points = listOf(start) + targets.map { it.position }
            val roadMatrix = withContext(Dispatchers.IO) { osrm.distanceMatrix(points) }
            val matrix = roadMatrix ?: Geo.distanceMatrix(points, DETOUR_FACTOR)
            val plan = withContext(Dispatchers.Default) { RoutePlanner.plan(matrix) }
            val baseline = RoutePlanner.pathCost(matrix, 0, targets.indices.map { it + 1 })
            val greedy = RoutePlanner.pathCost(matrix, 0, RoutePlanner.nearestNeighborOrder(matrix))
            val orderedIds = plan.order.map { targets[it - 1].id }
            _state.update { s ->
                // Hesaplama sürerken durumu değişen duraklar rotaya girmesin.
                val stillActive = s.stops.filter { it.status == status }.map { it.id }.toSet()
                s.copy(
                    routeIds = orderedIds.filter { it in stillActive },
                    isOptimizing = false,
                    baselineMeters = baseline,
                    greedyMeters = greedy,
                    optimizedMeters = plan.cost,
                    plannedOnRoads = roadMatrix != null,
                    fitRequest = s.fitRequest + 1,
                    message = UiMessage(
                        "Rota hazır: ${targets.size} durak · " +
                            if (roadMatrix != null) "yol ağı mesafeleriyle" else "kuş uçuşu tahminle (yol servisi yok)",
                    ),
                )
            }
            persist()
            refreshGeometry()
        }
    }

    private fun refreshGeometry() {
        val s = _state.value
        val route = s.activeRoute
        geometryJob?.cancel()
        if (route.isEmpty()) {
            _state.update {
                it.copy(routeLine = emptyList(), routeLineOnRoads = false, remainingMeters = 0.0, remainingSeconds = 0.0)
            }
            return
        }
        val points = listOf(startPoint(s, route)) + route.map { it.position }
        val approxMeters = Geo.pathLengthMeters(points) * DETOUR_FACTOR
        _state.update {
            it.copy(
                routeLine = points,
                routeLineOnRoads = false,
                remainingMeters = approxMeters,
                remainingSeconds = approxMeters / FALLBACK_SPEED_MPS + route.size * SERVICE_SECONDS,
            )
        }
        geometryJob = viewModelScope.launch {
            delay(300)
            val geometry = withContext(Dispatchers.IO) { osrm.route(points) } ?: return@launch
            _state.update {
                it.copy(
                    routeLine = geometry.points,
                    routeLineOnRoads = true,
                    remainingMeters = geometry.distanceMeters,
                    remainingSeconds = geometry.durationSeconds + route.size * SERVICE_SECONDS,
                )
            }
        }
    }

    /** GPS duraklara yakınsa GPS, değilse son teslim noktası, o da yoksa şube. */
    private fun startPoint(s: UiState, stops: List<Stop>): LatLng {
        val gps = s.courierLocation
        if (gps != null && stops.isNotEmpty()) {
            val centroid = LatLng(stops.map { it.lat }.average(), stops.map { it.lon }.average())
            if (Geo.haversineMeters(gps, centroid) < GPS_MAX_DISTANCE_M) return gps
        }
        return s.lastPosition ?: s.depot
    }

    // endregion

    // region Kurye işlemleri

    fun markDelivered(id: String) {
        val stop = find(id) ?: return
        val now = System.currentTimeMillis()
        _state.update { s ->
            s.copy(
                stops = s.stops.map { if (it.id == id) it.copy(status = StopStatus.DELIVERED, updatedAt = now) else it },
                routeIds = s.routeIds - id,
                lastPosition = stop.position,
                message = UiMessage("${stop.recipient} · teslim edildi", undoStopId = id),
            )
        }
        afterChange()
    }

    fun postpone(id: String, note: String) {
        val stop = find(id) ?: return
        val secondRound = stop.status == StopStatus.POSTPONED
        val now = System.currentTimeMillis()
        _state.update { s ->
            s.copy(
                stops = s.stops.map {
                    if (it.id == id) {
                        it.copy(
                            status = StopStatus.POSTPONED,
                            note = note.ifBlank { it.note },
                            attempts = it.attempts + 1,
                            updatedAt = now,
                        )
                    } else {
                        it
                    }
                },
                // Ertelenenler turunda tekrar ertelenen paket rotanın sonuna gider.
                routeIds = if (secondRound) (s.routeIds - id) + id else s.routeIds - id,
                lastPosition = stop.position,
                message = UiMessage(
                    if (secondRound) "Rotanın sonuna alındı" else "Ertelendi · ana tur bitince tekrar uğranacak",
                    undoStopId = if (secondRound) null else id,
                ),
            )
        }
        afterChange()
    }

    fun markFailed(id: String, reason: String, note: String) {
        val stop = find(id) ?: return
        val now = System.currentTimeMillis()
        _state.update { s ->
            s.copy(
                stops = s.stops.map {
                    if (it.id == id) {
                        it.copy(
                            status = StopStatus.FAILED,
                            failReason = reason,
                            note = note.ifBlank { it.note },
                            attempts = it.attempts + 1,
                            updatedAt = now,
                        )
                    } else {
                        it
                    }
                },
                routeIds = s.routeIds - id,
                lastPosition = stop.position,
                message = UiMessage("Teslim edilemedi · şubeye iade: $reason", undoStopId = id),
            )
        }
        afterChange()
    }

    /** Yanlışlıkla yapılan işlemi geri alır; paket rotanın başına döner. */
    fun revertToPending(id: String) {
        if (find(id) == null) return
        _state.update { s ->
            s.copy(
                stops = s.stops.map { if (it.id == id) it.copy(status = StopStatus.PENDING, failReason = "") else it },
                routeIds = listOf(id) + (s.routeIds - id),
                message = UiMessage("Geri alındı"),
            )
        }
        afterChange()
    }

    /** Kurye sırayı kendisi değiştirmek isterse. */
    fun makeNext(id: String) {
        val s = _state.value
        val stop = find(id) ?: return
        if (stop.status != s.activeStatus) return
        _state.update { it.copy(routeIds = listOf(id) + (it.routeIds - id)) }
        afterChange()
    }

    fun onLocation(location: LatLng) {
        val previous = _state.value.courierLocation
        if (previous != null && Geo.haversineMeters(previous, location) < LOCATION_MIN_MOVE_M) return
        _state.update { it.copy(courierLocation = location) }
    }

    fun requestFit() = _state.update { it.copy(fitRequest = it.fitRequest + 1) }

    fun focusOn(id: String) {
        val stop = find(id) ?: return
        _state.update { it.copy(focus = stop.position, focusRequest = it.focusRequest + 1) }
    }

    fun consumeMessage(id: Long) = _state.update { if (it.message?.id == id) it.copy(message = null) else it }

    // endregion

    private fun afterChange(focusNext: Boolean = true) {
        if (focusNext) {
            _state.value.activeRoute.firstOrNull()?.let { next ->
                _state.update { it.copy(focus = next.position, focusRequest = it.focusRequest + 1) }
            }
        }
        persist()
        refreshGeometry()
    }

    private fun find(id: String) = _state.value.stops.firstOrNull { it.id == id }

    private fun show(text: String) = _state.update { it.copy(message = UiMessage(text)) }

    private fun persist() {
        val s = _state.value
        store.save(
            StopStore.Snapshot(
                stops = s.stops,
                routeIds = s.routeIds,
                depot = s.depot,
                lastPosition = s.lastPosition,
                baselineMeters = s.baselineMeters,
                greedyMeters = s.greedyMeters,
                optimizedMeters = s.optimizedMeters,
                plannedOnRoads = s.plannedOnRoads,
            ),
        )
    }

    private companion object {
        /** Şehir içinde yol mesafesi kuş uçuşunun ~1.3 katı. */
        const val DETOUR_FACTOR = 1.3

        /** Yol servisi yokken ortalama şehir içi hız: 18 km/sa. */
        const val FALLBACK_SPEED_MPS = 5.0

        /** Durak başına park etme + teslim süresi. */
        const val SERVICE_SECONDS = 150.0
        const val GPS_MAX_DISTANCE_M = 5_000.0
        const val LOCATION_MIN_MOVE_M = 15.0
    }
}
