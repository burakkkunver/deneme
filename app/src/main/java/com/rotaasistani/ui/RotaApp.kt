package com.rotaasistani.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rotaasistani.Phase
import com.rotaasistani.RouteViewModel
import com.rotaasistani.UiState
import com.rotaasistani.model.FAIL_REASONS
import com.rotaasistani.model.POSTPONE_NOTES
import com.rotaasistani.model.Stop
import com.rotaasistani.model.StopStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RotaApp(viewModel: RouteViewModel, hasLocationPermission: Boolean) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showList by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var postponeId by rememberSaveable { mutableStateOf<String?>(null) }
    var failId by rememberSaveable { mutableStateOf<String?>(null) }
    var showSummary by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message?.id) {
        val message = state.message ?: return@LaunchedEffect
        val undoId = message.undoStopId
        val result = snackbar.showSnackbar(
            message = message.text,
            actionLabel = if (undoId != null) "Geri al" else null,
            duration = if (undoId != null) SnackbarDuration.Long else SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed && undoId != null) viewModel.revertToPending(undoId)
        viewModel.consumeMessage(message.id)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Rota Asistanı", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            subtitle(state),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showList = !showList }) {
                        if (showList) {
                            Icon(Icons.Default.Place, contentDescription = "Harita")
                        } else {
                            Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Liste")
                        }
                    }
                    IconButton(onClick = viewModel::optimizeRoute, enabled = !state.isOptimizing) {
                        Icon(Icons.Default.Refresh, contentDescription = "Rotayı yeniden hesapla")
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menü")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Yeni demo: Ataşehir (65 paket)") },
                                onClick = { menuOpen = false; viewModel.loadDefaultDemo() },
                            )
                            DropdownMenuItem(
                                text = { Text("Yeni demo: konumum çevresinde") },
                                onClick = { menuOpen = false; viewModel.loadDemoAroundMe() },
                            )
                            DropdownMenuItem(
                                text = { Text("Demo: ana turu hızlıca bitir") },
                                onClick = { menuOpen = false; viewModel.demoFinishMainRound() },
                            )
                            DropdownMenuItem(
                                text = { Text("Gün sonu özeti") },
                                onClick = { menuOpen = false; showSummary = true },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            StatsBar(state)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                MapPanel(
                    state = state,
                    hasLocationPermission = hasLocationPermission,
                    onStopClick = { selectedId = it },
                    onLocation = viewModel::onLocation,
                    onFit = viewModel::requestFit,
                    modifier = Modifier.fillMaxSize(),
                )
                if (state.isOptimizing) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
                }
                if (showList) {
                    StopList(
                        state = state,
                        onSelect = {
                            showList = false
                            selectedId = it
                            viewModel.focusOn(it)
                        },
                        onMakeNext = viewModel::makeNext,
                        onRevert = viewModel::revertToPending,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            BottomPanel(
                state = state,
                onDelivered = viewModel::markDelivered,
                onPostpone = { postponeId = it },
                onFail = { failId = it },
                onNavigate = { openNavigation(context, it) },
                onPlan = viewModel::optimizeRoute,
                onSummary = { showSummary = true },
            )
        }
    }

    val selected = selectedId?.let { id -> state.stops.firstOrNull { it.id == id } }
    if (selected != null) {
        StopDetailDialog(
            stop = selected,
            state = state,
            onDismiss = { selectedId = null },
            onMakeNext = { viewModel.makeNext(it); selectedId = null },
            onDelivered = { viewModel.markDelivered(it); selectedId = null },
            onPostpone = { postponeId = it; selectedId = null },
            onFail = { failId = it; selectedId = null },
            onRevert = { viewModel.revertToPending(it); selectedId = null },
            onNavigate = { openNavigation(context, it) },
        )
    }

    val postponing = postponeId?.let { id -> state.stops.firstOrNull { it.id == id } }
    if (postponing != null) {
        PostponeDialog(
            stop = postponing,
            secondRound = state.phase == Phase.POSTPONED,
            onConfirm = { note -> viewModel.postpone(postponing.id, note); postponeId = null },
            onDismiss = { postponeId = null },
        )
    }

    val failing = failId?.let { id -> state.stops.firstOrNull { it.id == id } }
    if (failing != null) {
        FailDialog(
            stop = failing,
            onConfirm = { reason, note -> viewModel.markFailed(failing.id, reason, note); failId = null },
            onDismiss = { failId = null },
        )
    }

    if (showSummary) SummaryDialog(state, onDismiss = { showSummary = false })
}

private fun subtitle(state: UiState): String {
    val route = state.activeRoute.size + state.unrouted.size
    return when (state.phase) {
        Phase.MAIN -> "Ana tur · $route durak kaldı"
        Phase.POSTPONED -> "2. tur (ertelenenler) · $route durak"
        Phase.DONE -> "Gün tamamlandı"
    }
}

@Composable
private fun StatsBar(state: UiState) {
    val total = state.stops.size
    val delivered = state.count(StopStatus.DELIVERED)
    val postponed = state.count(StopStatus.POSTPONED)
    val failed = state.count(StopStatus.FAILED)
    Surface(color = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatPill("Teslim", "$delivered/$total", Palette.Delivered)
                Spacer(Modifier.width(6.dp))
                StatPill("Ertelenen", "$postponed", Palette.Postponed)
                Spacer(Modifier.width(6.dp))
                StatPill("İade", "$failed", Palette.Failed)
                Spacer(Modifier.weight(1f))
                if (state.remainingMeters > 0) {
                    val approx = if (state.routeLineOnRoads) "" else "≈ "
                    Text(
                        "$approx${formatDistance(state.remainingMeters)} · ${formatDuration(state.remainingSeconds)}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            LinearProgressIndicator(
                progress = { if (total == 0) 0f else (delivered + failed).toFloat() / total },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                color = Palette.Delivered,
            )
            savingsText(state)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

private fun savingsText(state: UiState): String? {
    val optimized = state.optimizedMeters ?: return null
    val baseline = state.baselineMeters ?: return null
    val greedy = state.greedyMeters ?: return null
    val source = if (state.plannedOnRoads) "" else " (tahmini)"
    val parts = mutableListOf("Plan ${formatDistance(optimized)}$source")
    val vsList = percentSaved(baseline, optimized)
    if (vsList > 0) parts += "liste sırasına göre %$vsList"
    val vsGreedy = percentSaved(greedy, optimized)
    if (vsGreedy > 0) parts += "\"hep en yakına git\"e göre %$vsGreedy daha kısa"
    return parts.joinToString(" · ")
}

@Composable
private fun StatPill(label: String, value: String, color: Color) {
    Row(
        Modifier
            .background(Color.White.copy(alpha = 0.7f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(4.dp))
        Text("$label $value", style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun BottomPanel(
    state: UiState,
    onDelivered: (String) -> Unit,
    onPostpone: (String) -> Unit,
    onFail: (String) -> Unit,
    onNavigate: (Stop) -> Unit,
    onPlan: () -> Unit,
    onSummary: () -> Unit,
) {
    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            val route = state.activeRoute
            when {
                state.phase == Phase.DONE -> {
                    Text("Tüm paketler işlendi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${state.count(StopStatus.DELIVERED)} teslim · ${state.count(StopStatus.FAILED)} şubeye iade",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onSummary, modifier = Modifier.fillMaxWidth()) { Text("Gün sonu özeti") }
                }

                route.isEmpty() && state.isOptimizing -> Text("Rota hesaplanıyor…")

                route.isEmpty() && state.phase == Phase.POSTPONED -> {
                    Text("Ana tur tamamlandı", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${state.unrouted.size} ertelenen paket var. Bulunduğun yerden bu paketler için en kısa rota çizilecek.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onPlan, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Text("Ertelenenler için rota oluştur")
                    }
                }

                route.isEmpty() -> {
                    Text("${state.unrouted.size} paket rotalanmayı bekliyor", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onPlan, modifier = Modifier.fillMaxWidth()) { Text("Rotayı oluştur") }
                }

                else -> NextStop(
                    stop = route.first(),
                    following = route.getOrNull(1),
                    remaining = route.size,
                    secondRound = state.phase == Phase.POSTPONED,
                    enabled = !state.isOptimizing,
                    onDelivered = onDelivered,
                    onPostpone = onPostpone,
                    onFail = onFail,
                    onNavigate = onNavigate,
                )
            }
        }
    }
}

@Composable
private fun NextStop(
    stop: Stop,
    following: Stop?,
    remaining: Int,
    secondRound: Boolean,
    enabled: Boolean,
    onDelivered: (String) -> Unit,
    onPostpone: (String) -> Unit,
    onFail: (String) -> Unit,
    onNavigate: (Stop) -> Unit,
) {
    Text(
        if (secondRound) "ERTELENEN PAKET · kalan $remaining" else "SIRADAKİ DURAK · kalan $remaining",
        style = MaterialTheme.typography.labelMedium,
        color = Palette.Next,
        fontWeight = FontWeight.Bold,
    )
    Text(
        "${stop.recipient} · ${stop.trackingNo}",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    Text("${stop.address} · ${stop.desi} desi", style = MaterialTheme.typography.bodyMedium)
    if (stop.note.isNotBlank()) {
        Text(
            "Not (${stop.attempts}. deneme): ${stop.note}",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.Postponed,
            fontWeight = FontWeight.Medium,
        )
    }
    if (following != null) {
        Text(
            "Ardından: ${following.address}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = { onDelivered(stop.id) },
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(containerColor = Palette.Delivered),
            modifier = Modifier.weight(1.3f).height(52.dp),
        ) {
            Icon(Icons.Default.Check, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Teslim edildi")
        }
        FilledTonalButton(
            onClick = { onPostpone(stop.id) },
            enabled = enabled,
            modifier = Modifier.weight(1f).height(52.dp),
        ) {
            Text(if (secondRound) "Sona al" else "Sonra dön")
        }
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = { onFail(stop.id) },
            enabled = enabled,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.Failed),
            modifier = Modifier.weight(1f),
        ) {
            Icon(Icons.Default.Close, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("Teslim edilemedi")
        }
        OutlinedButton(onClick = { onNavigate(stop) }, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.LocationOn, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("Navigasyon")
        }
    }
}

@Composable
private fun StopList(
    state: UiState,
    onSelect: (String) -> Unit,
    onMakeNext: (String) -> Unit,
    onRevert: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(
        "Rota" to state.activeRoute + state.unrouted,
        "Teslim" to state.stops.filter { it.status == StopStatus.DELIVERED },
        "Ertelenen" to state.stops.filter { it.status == StopStatus.POSTPONED },
        "İade" to state.stops.filter { it.status == StopStatus.FAILED },
    )
    Surface(modifier, color = MaterialTheme.colorScheme.surface) {
        Column {
            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp) {
                tabs.forEachIndexed { index, (title, items) ->
                    Tab(selected = tab == index, onClick = { tab = index }, text = { Text("$title (${items.size})") })
                }
            }
            val items = tabs[tab].second
            if (items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Bu listede paket yok", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(items, key = { _, stop -> stop.id }) { index, stop ->
                        StopRow(
                            stop = stop,
                            number = if (tab == 0) index + 1 else null,
                            canRevert = stop.status != state.activeStatus,
                            onSelect = onSelect,
                            onMakeNext = onMakeNext,
                            onRevert = onRevert,
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun StopRow(
    stop: Stop,
    number: Int?,
    canRevert: Boolean,
    onSelect: (String) -> Unit,
    onMakeNext: (String) -> Unit,
    onRevert: (String) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onSelect(stop.id) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val (label, color) = badge(stop, number)
        Box(Modifier.size(32.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
            Text(label, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("${stop.recipient} · ${stop.trackingNo}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(stop.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (stop.failReason.isNotBlank()) {
                Text(stop.failReason, style = MaterialTheme.typography.bodySmall, color = Palette.Failed)
            }
            if (stop.note.isNotBlank()) {
                Text("Not: ${stop.note}", style = MaterialTheme.typography.bodySmall, color = Palette.Postponed)
            }
        }
        when {
            number != null && number > 1 -> TextButton(onClick = { onMakeNext(stop.id) }) { Text("Sıradaki yap") }
            canRevert -> TextButton(onClick = { onRevert(stop.id) }) { Text("Geri al") }
        }
    }
}

private fun badge(stop: Stop, number: Int?): Pair<String, Color> = when {
    number == 1 -> "1" to Palette.Next
    number != null -> number.toString() to Palette.Active
    stop.status == StopStatus.DELIVERED -> "✓" to Palette.Delivered
    stop.status == StopStatus.POSTPONED -> "↻" to Palette.Postponed
    stop.status == StopStatus.FAILED -> "!" to Palette.Failed
    else -> "•" to Palette.Active
}

@Composable
private fun StopDetailDialog(
    stop: Stop,
    state: UiState,
    onDismiss: () -> Unit,
    onMakeNext: (String) -> Unit,
    onDelivered: (String) -> Unit,
    onPostpone: (String) -> Unit,
    onFail: (String) -> Unit,
    onRevert: (String) -> Unit,
    onNavigate: (Stop) -> Unit,
) {
    val active = stop.status == state.activeStatus
    val position = state.activeRoute.indexOfFirst { it.id == stop.id }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stop.recipient) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${stop.trackingNo} · ${stop.desi} desi")
                Text(stop.address)
                Text(
                    when (stop.status) {
                        StopStatus.PENDING -> if (position >= 0) "Rotada ${position + 1}. sırada" else "Bekliyor"
                        StopStatus.DELIVERED -> "Teslim edildi"
                        StopStatus.POSTPONED -> if (position >= 0) "Ertelendi · 2. turda ${position + 1}. sırada" else "Ertelendi"
                        StopStatus.FAILED -> "Teslim edilemedi: ${stop.failReason}"
                    },
                    fontWeight = FontWeight.Medium,
                )
                if (stop.note.isNotBlank()) Text("Not: ${stop.note}", color = Palette.Postponed)
                Spacer(Modifier.height(8.dp))
                if (active) {
                    if (position != 0) {
                        OutlinedButton(onClick = { onMakeNext(stop.id) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Sıradaki durak yap")
                        }
                    }
                    Button(
                        onClick = { onDelivered(stop.id) },
                        colors = ButtonDefaults.buttonColors(containerColor = Palette.Delivered),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Teslim edildi") }
                    FilledTonalButton(onClick = { onPostpone(stop.id) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Sonra dön (not ekle)")
                    }
                    OutlinedButton(
                        onClick = { onFail(stop.id) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.Failed),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Teslim edilemedi") }
                } else {
                    OutlinedButton(onClick = { onRevert(stop.id) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Geri al (bekleyenlere ekle)")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onNavigate(stop) }) { Text("Navigasyon") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Kapat") } },
    )
}

@Composable
private fun PostponeDialog(stop: Stop, secondRound: Boolean, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var note by rememberSaveable(stop.id) { mutableStateOf(stop.note) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (secondRound) "Rotanın sonuna al" else "Sonra tekrar uğra") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("${stop.recipient} · ${stop.address}", fontWeight = FontWeight.Medium)
                Text(
                    if (secondRound) {
                        "Paket bu turun en sonuna alınır."
                    } else {
                        "Paket ertelenenlere alınır. Ana tur bitince ertelenen paketler için ayrıca en kısa rota çizilir."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                POSTPONE_NOTES.forEach { suggestion ->
                    SuggestionChip(onClick = { note = suggestion }, label = { Text(suggestion) })
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Not (ör. 17:00 sonrası gel)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(note.trim()) }) { Text("Kaydet") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}

@Composable
private fun FailDialog(stop: Stop, onConfirm: (String, String) -> Unit, onDismiss: () -> Unit) {
    var reason by rememberSaveable(stop.id) { mutableStateOf(FAIL_REASONS.first()) }
    var note by rememberSaveable(stop.id) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Teslim edilemedi") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("${stop.recipient} · ${stop.address}", fontWeight = FontWeight.Medium)
                Text(
                    "Paket bugün tekrar denenmez, şubeye iade olarak işaretlenir. Bugün tekrar uğramak için \"Sonra dön\"ü kullan.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(4.dp))
                FAIL_REASONS.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = option == reason, onClick = { reason = option }, role = Role.RadioButton)
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == reason, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(option)
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Not (isteğe bağlı)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(reason, note.trim()) }) { Text("Kaydet") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}

@Composable
private fun SummaryDialog(state: UiState, onDismiss: () -> Unit) {
    val failed = state.stops.filter { it.status == StopStatus.FAILED }
    val postponed = state.stops.filter { it.status == StopStatus.POSTPONED }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Gün sonu özeti") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Toplam paket: ${state.stops.size}")
                Text("Teslim edildi: ${state.count(StopStatus.DELIVERED)}", color = Palette.Delivered)
                Text("Hâlâ ertelenmiş: ${postponed.size}", color = Palette.Postponed)
                Text("Şubeye iade: ${failed.size}", color = Palette.Failed)
                if (failed.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text("İade edilecekler", fontWeight = FontWeight.SemiBold)
                    failed.forEach { Text("• ${it.trackingNo} – ${it.failReason}${if (it.note.isNotBlank()) " (${it.note})" else ""}") }
                }
                if (postponed.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text("Ertelenenler", fontWeight = FontWeight.SemiBold)
                    postponed.forEach { Text("• ${it.trackingNo} – ${it.note.ifBlank { "not yok" }}") }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Gerçek sistemde bu özet şubeye otomatik iletilir ve iade paketleri şubede hazır beklenir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tamam") } },
    )
}

/** Google Maps, Yandex Navigasyon vb. hangisi yüklüyse onunla açar. */
private fun openNavigation(context: Context, stop: Stop) {
    val uri = Uri.parse("geo:${stop.lat},${stop.lon}?q=${stop.lat},${stop.lon}(${Uri.encode(stop.recipient)})")
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "Navigasyon uygulaması bulunamadı", Toast.LENGTH_SHORT).show()
    }
}
