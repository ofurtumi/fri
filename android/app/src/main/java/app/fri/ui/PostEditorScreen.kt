package app.fri.ui

import android.Manifest
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.fri.data.GitHubClient
import app.fri.data.PostDraft
import app.fri.data.PublishQueue
import app.fri.data.RouteLog
import app.fri.data.SettingsStore
import app.fri.data.Trip
import app.fri.data.TripsRepository
import app.fri.data.Weather
import app.fri.data.WeatherClient
import app.fri.data.parsePostMarkdown
import app.fri.data.resizeToJpeg
import app.fri.data.slugify
import app.fri.data.toMarkdown
import app.fri.data.transcodeClip
import coil.compose.AsyncImage
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import androidx.compose.runtime.rememberCoroutineScope

/** Media the post already has in the repo when editing; kept as-is, new media is add-only. */
private fun nextIndex(existing: List<String>, prefix: String): Int =
    existing.mapNotNull { Regex("""$prefix(\d+)\.""").find(it)?.groupValues?.get(1)?.toIntOrNull() }
        .maxOrNull()?.plus(1) ?: 1

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostEditorScreen(nav: NavController, editSlug: String? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val editing = editSlug != null

    var title by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var location by remember { mutableStateOf("") }
    var lat by remember { mutableStateOf("") }
    var lng by remember { mutableStateOf("") }
    var excerpt by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var photos by remember { mutableStateOf(listOf<Uri>()) }
    var videos by remember { mutableStateOf(listOf<Uri>()) }
    var existingPhotos by remember { mutableStateOf(listOf<String>()) }
    var existingVideos by remember { mutableStateOf(listOf<String>()) }
    var wTemp by remember { mutableStateOf("") }
    var wDesc by remember { mutableStateOf("") }
    var wWind by remember { mutableStateOf("") }
    var trips by remember { mutableStateOf(listOf<Trip>()) }
    var tripId by remember { mutableStateOf("") }
    var tripMenuOpen by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(!editing) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<String?>(null) }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

    LaunchedEffect(Unit) {
        trips = try {
            TripsRepository(context).load()
        } catch (e: Exception) {
            emptyList()
        }
        if (editing) {
            try {
                val settings = SettingsStore(context).current()
                check(settings.configured) { "configure the repo in settings first" }
                val text = withContext(Dispatchers.IO) {
                    GitHubClient(settings).getFileText("src/content/posts/$editSlug.md")
                } ?: error("post not found in the repo")
                val parsed = parsePostMarkdown(editSlug!!, text)
                title = parsed.draft.title
                date = parsed.draft.date
                tripId = parsed.draft.trip
                location = parsed.draft.location
                lat = parsed.draft.lat.toString()
                lng = parsed.draft.lng.toString()
                excerpt = parsed.draft.excerpt
                body = parsed.draft.body
                existingPhotos = parsed.photos
                existingVideos = parsed.videos
                parsed.draft.weather?.let {
                    wTemp = it.temp.toString()
                    wDesc = it.description
                    wWind = it.windKmh.toString()
                }
                ready = true
            } catch (e: Exception) {
                toast("Can't edit this post here: ${e.message}")
                nav.popBackStack()
            }
        } else {
            tripId = SettingsStore(context).currentActiveTrip()?.first ?: ""
        }
    }

    val mediaPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(10),
    ) { uris ->
        if (uris.isNotEmpty()) {
            val (vids, imgs) = uris.partition { uri ->
                context.contentResolver.getType(uri)?.startsWith("video/") == true
            }
            photos = imgs
            videos = vids
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

    fun fetchWeather() {
        val la = lat.toDoubleOrNull()
        val ln = lng.toDoubleOrNull()
        if (la == null || ln == null) {
            toast("Set coordinates first")
            return
        }
        scope.launch {
            val w = withContext(Dispatchers.IO) { WeatherClient().fetch(date, la, ln) }
            if (w == null) {
                toast("No weather data — fill it in by hand")
            } else {
                wTemp = w.temp.toString()
                wDesc = w.description
                wWind = w.windKmh.toString()
            }
        }
    }

    fun save() {
        val la = lat.toDoubleOrNull()
        val ln = lng.toDoubleOrNull()
        if (title.isBlank() || location.isBlank() || la == null || ln == null) {
            toast("Title, location and coordinates are required")
            return
        }
        if (!Regex("""\d{4}-\d{2}-\d{2}""").matches(date)) {
            toast("Date must be yyyy-mm-dd")
            return
        }
        if (tripId.isBlank()) {
            toast("Pick a trip first (create one under Trips)")
            return
        }
        busy = true
        scope.launch {
            try {
                // Editing must never re-derive the slug — a title change would
                // otherwise create a second post instead of updating this one
                val slug = editSlug ?: slugify(title)

                // Transcoding needs the main looper, so it runs here (not in IO)
                // and reports into the progress line
                val clipFiles = mutableMapOf<String, File>()
                val videoPaths = existingVideos.toMutableList()
                val clipStart = nextIndex(existingVideos, "clip-")
                videos.forEachIndexed { i, uri ->
                    val name = "clip-${clipStart + i}.mp4"
                    val staged = File(context.cacheDir, "transcode/$name")
                    progress = "Transcoding clip ${i + 1}/${videos.size}…"
                    transcodeClip(context, uri, staged) { pct ->
                        progress = "Transcoding clip ${i + 1}/${videos.size}: $pct%"
                    }
                    clipFiles["src/content/posts/$slug/$name"] = staged
                    videoPaths += "./$slug/$name"
                }

                progress = "Queueing…"
                withContext(Dispatchers.IO) {
                    val weather = wTemp.toIntOrNull()?.let {
                        Weather(it, wDesc.ifBlank { "unknown" }, wWind.toIntOrNull() ?: 0)
                    }
                    val files = mutableMapOf<String, ByteArray>()
                    val photoPaths = existingPhotos.toMutableList()
                    val photoStart = nextIndex(existingPhotos, "photo-")
                    photos.forEachIndexed { i, uri ->
                        val name = "photo-${photoStart + i}.jpg"
                        files["src/content/posts/$slug/$name"] = resizeToJpeg(context, uri)
                        photoPaths += "./$slug/$name"
                    }
                    val draft = PostDraft(title, date, tripId, location, la, ln, excerpt, body, weather)
                    files["src/content/posts/$slug.md"] = draft.toMarkdown(photoPaths, videoPaths).toByteArray()
                    if (!editing) {
                        RouteLog.toRouteJsonBytes(context, tripId)?.let {
                            files[RouteLog.repoPath(tripId)] = it
                        }
                    }
                    PublishQueue.enqueue(
                        context,
                        if (editing) "Edit post: $title" else "Post: $title",
                        files,
                        clipFiles,
                    )
                }
                toast("Queued — publishes when there's signal")
                nav.popBackStack()
            } catch (e: Exception) {
                toast("Failed to queue: ${e.message}")
            } finally {
                busy = false
                progress = null
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
        Text(if (editing) "Edit post" else "New post", style = MaterialTheme.typography.headlineMedium)

        if (!ready) {
            Text("Loading post from the repo…")
            return@Column
        }

        OutlinedTextField(title, { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(date, { date = it }, label = { Text("Date (yyyy-mm-dd)") }, modifier = Modifier.fillMaxWidth())

        ExposedDropdownMenuBox(expanded = tripMenuOpen, onExpandedChange = { tripMenuOpen = it }) {
            OutlinedTextField(
                value = trips.find { it.id == tripId }?.name ?: tripId.ifBlank { "No trip" },
                onValueChange = {},
                readOnly = true,
                label = { Text("Trip") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = tripMenuOpen) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
            )
            ExposedDropdownMenu(expanded = tripMenuOpen, onDismissRequest = { tripMenuOpen = false }) {
                trips.sortedByDescending { it.startDate }.forEach { trip ->
                    DropdownMenuItem(
                        text = { Text(trip.name) },
                        onClick = {
                            tripId = trip.id
                            tripMenuOpen = false
                        },
                    )
                }
            }
        }

        OutlinedTextField(location, { location = it }, label = { Text("Place name") }, modifier = Modifier.fillMaxWidth())

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(lat, { lat = it }, label = { Text("Lat") }, modifier = Modifier.weight(1f))
            OutlinedTextField(lng, { lng = it }, label = { Text("Lng") }, modifier = Modifier.weight(1f))
        }
        OutlinedButton(onClick = {
            locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }) { Text("Use current location") }

        OutlinedTextField(excerpt, { excerpt = it }, label = { Text("Excerpt (one line, used in previews)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            body,
            { body = it },
            label = { Text("Post (markdown)") },
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
        )

        Card {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Weather", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(wTemp, { wTemp = it }, label = { Text("°C") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(wWind, { wWind = it }, label = { Text("Wind km/h") }, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(wDesc, { wDesc = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = ::fetchWeather) { Text("Fetch from Open-Meteo") }
                Text(
                    "Edit freely after fetching — what you save is what the site shows.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        if (editing && (existingPhotos.isNotEmpty() || existingVideos.isNotEmpty())) {
            Text(
                "Already published: ${existingPhotos.size} photos, ${existingVideos.size} clips (kept as-is; new picks are added after them).",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        OutlinedButton(onClick = {
            mediaPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo),
            )
        }) {
            val picked = photos.size + videos.size
            Text(if (picked == 0) "Add photos & clips" else "Change selection ($picked)")
        }

        if (photos.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(photos) { uri ->
                    AsyncImage(model = uri, contentDescription = null, modifier = Modifier.size(96.dp))
                }
            }
        }
        if (videos.isNotEmpty()) {
            Text(
                "${videos.size} clip(s) — transcoded to ~270p on save, so keep them short.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        progress?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

        Button(onClick = ::save, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (busy) "Working…" else "Save & queue for publish")
        }
    }
}
