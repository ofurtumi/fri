package app.fri.ui

import android.Manifest
import android.annotation.SuppressLint
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.fri.data.RouteLog
import app.fri.data.SettingsStore
import app.fri.data.Trip
import app.fri.data.TripStart
import app.fri.data.TripsRepository
import app.fri.service.TrackService
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.launch
import java.time.LocalDate

/** List trips from trips.json, create new ones, pick the active trip. */
@Composable
fun TripsScreen(nav: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { SettingsStore(context) }
    val repo = remember { TripsRepository(context) }

    var trips by remember { mutableStateOf<List<Trip>?>(null) }
    var activeId by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }

    // create form
    var name by remember { mutableStateOf("") }
    var startName by remember { mutableStateOf("") }
    var lat by remember { mutableStateOf("") }
    var lng by remember { mutableStateOf("") }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

    LaunchedEffect(Unit) {
        activeId = settings.currentActiveTrip()?.first
        trips = try {
            repo.load()
        } catch (e: Exception) {
            toast("Could not load trips: ${e.message}")
            emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    fun grabLocation() {
        LocationServices.getFusedLocationProviderClient(context)
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    lat = "%.4f".format(loc.latitude)
                    lng = "%.4f".format(loc.longitude)
                } else {
                    toast("No location fix yet — try again outside")
                }
            }
    }

    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) grabLocation() }

    fun refresh() {
        scope.launch {
            try {
                trips = repo.load(forceRemote = true)
                toast("Trips refreshed from GitHub")
            } catch (e: Exception) {
                // e.g. queued trips.json edits still waiting — remote would be stale
                toast("Refresh failed: ${e.message}")
            }
        }
    }

    fun setActive(trip: Trip) {
        if (TrackService.running) {
            toast("Stop route recording before switching trips")
            return
        }
        scope.launch {
            settings.setActiveTrip(trip.id, trip.name)
            RouteLog.migrateLegacyLog(context, trip.id)
            activeId = trip.id
        }
    }

    fun create() {
        val la = lat.toDoubleOrNull()
        val ln = lng.toDoubleOrNull()
        if (name.isBlank() || startName.isBlank() || la == null || ln == null) {
            toast("Name, start place and coordinates are required")
            return
        }
        busy = true
        scope.launch {
            try {
                val existing = trips ?: emptyList()
                val trip = Trip(
                    id = TripsRepository.newTripId(name, existing),
                    name = name.trim(),
                    start = TripStart(startName.trim(), la, ln),
                    startDate = LocalDate.now().toString(),
                )
                repo.upsert(trip, "New trip: ${trip.name}")
                trips = (existing + trip).sortedBy { it.startDate }
                settings.setActiveTrip(trip.id, trip.name)
                activeId = trip.id
                name = ""; startName = ""; lat = ""; lng = ""
                showCreate = false
                toast("Trip created and set active — queued for publish")
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
        Text("Trips", style = MaterialTheme.typography.headlineMedium)

        when (val list = trips) {
            null -> Text("Loading trips…")
            else -> {
                if (list.isEmpty()) {
                    Text("No trips yet — create the first one below.")
                }
                // newest first, same as the site's sidebar
                list.sortedByDescending { it.startDate }.forEach { trip ->
                    Card {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(trip.name, style = MaterialTheme.typography.titleMedium)
                                if (trip.id == activeId) {
                                    Text("Active", style = MaterialTheme.typography.labelLarge)
                                } else {
                                    OutlinedButton(onClick = { setActive(trip) }) { Text("Set active") }
                                }
                            }
                            Text(
                                "${trip.startDate} · from ${trip.start.name}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                "${RouteLog.pointCount(context, trip.id)} GPS points on this phone",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }

        OutlinedButton(onClick = ::refresh, modifier = Modifier.fillMaxWidth()) {
            Text("Refresh from GitHub")
        }

        HorizontalDivider()

        if (!showCreate) {
            Button(onClick = { showCreate = true }, modifier = Modifier.fillMaxWidth()) {
                Text("New trip")
            }
        } else {
            Card {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("New trip", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(name, { name = it }, label = { Text("Trip name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(startName, { startName = it }, label = { Text("Start place (shown on the map)") }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(lat, { lat = it }, label = { Text("Lat") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(lng, { lng = it }, label = { Text("Lng") }, modifier = Modifier.weight(1f))
                    }
                    OutlinedButton(onClick = {
                        locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }) { Text("Use current location") }
                    Button(onClick = ::create, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text(if (busy) "Creating…" else "Create & set active")
                    }
                }
            }
        }

        OutlinedButton(onClick = { nav.popBackStack() }, modifier = Modifier.fillMaxWidth()) {
            Text("Back")
        }
    }
}
