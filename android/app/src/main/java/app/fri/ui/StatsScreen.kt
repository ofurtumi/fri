package app.fri.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.fri.data.SettingsStore
import app.fri.data.TripStat
import app.fri.data.TripsRepository
import kotlinx.coroutines.launch

/** Edits the active trip's stats inside src/data/trips.json — the key-value panel next to the map. */
@Composable
fun StatsScreen(nav: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { TripsRepository(context) }
    val rows = remember { mutableStateListOf<Pair<String, String>>() }
    var tripId by remember { mutableStateOf<String?>(null) }
    var tripName by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

    LaunchedEffect(Unit) {
        val active = SettingsStore(context).currentActiveTrip()
        if (active == null) {
            toast("Pick an active trip first")
            loaded = true
            return@LaunchedEffect
        }
        tripId = active.first
        tripName = active.second
        try {
            val trip = repo.load().find { it.id == active.first }
            trip?.stats?.forEach { rows.add(it.label to it.value) }
        } catch (e: Exception) {
            toast("Could not load current stats: ${e.message}")
        }
        loaded = true
    }

    fun save() {
        val id = tripId ?: return
        busy = true
        scope.launch {
            try {
                val stats = rows
                    .filter { (label, _) -> label.isNotBlank() }
                    .map { (label, value) -> TripStat(label.trim(), value.trim()) }
                repo.updateStats(id, stats, "Update stats: $tripName")
                toast("Queued — publishes when there's signal")
                nav.popBackStack()
            } catch (e: Exception) {
                toast("Failed: ${e.message}")
            } finally {
                busy = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            if (tripName.isBlank()) "Trip stats" else "Stats — $tripName",
            style = MaterialTheme.typography.headlineMedium,
        )

        if (!loaded) {
            Text("Loading current stats…")
        } else if (tripId == null) {
            OutlinedButton(onClick = { nav.navigate("trips") }) { Text("Choose a trip") }
        } else {
            rows.forEachIndexed { i, (label, value) ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        label,
                        { rows[i] = it to value },
                        label = { Text("Stat") },
                        modifier = Modifier.weight(1.2f),
                    )
                    OutlinedTextField(
                        value,
                        { rows[i] = label to it },
                        label = { Text("Value") },
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { rows.removeAt(i) }) { Text("✕") }
                }
            }
            OutlinedButton(onClick = { rows.add("" to "") }) { Text("Add stat") }
            Button(onClick = ::save, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(if (busy) "Queueing…" else "Save & queue for publish")
            }
        }
    }
}
