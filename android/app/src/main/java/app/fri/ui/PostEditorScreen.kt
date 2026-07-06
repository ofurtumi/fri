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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.fri.data.PostDraft
import app.fri.data.PublishQueue
import app.fri.data.RouteLog
import app.fri.data.Weather
import app.fri.data.WeatherClient
import app.fri.data.resizeToJpeg
import app.fri.data.slugify
import app.fri.data.toMarkdown
import coil.compose.AsyncImage
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import androidx.compose.runtime.rememberCoroutineScope

@Composable
fun PostEditorScreen(nav: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var location by remember { mutableStateOf("") }
    var lat by remember { mutableStateOf("") }
    var lng by remember { mutableStateOf("") }
    var excerpt by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var photos by remember { mutableStateOf(listOf<Uri>()) }
    var wTemp by remember { mutableStateOf("") }
    var wDesc by remember { mutableStateOf("") }
    var wWind by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(10),
    ) { uris -> if (uris.isNotEmpty()) photos = uris }

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
        busy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val slug = slugify(title)
                    val weather = wTemp.toIntOrNull()?.let {
                        Weather(it, wDesc.ifBlank { "unknown" }, wWind.toIntOrNull() ?: 0)
                    }
                    val files = mutableMapOf<String, ByteArray>()
                    val imagePaths = photos.mapIndexed { i, uri ->
                        val name = "photo-${i + 1}.jpg"
                        files["src/content/posts/$slug/$name"] = resizeToJpeg(context, uri)
                        "./$slug/$name"
                    }
                    val draft = PostDraft(title, date, location, la, ln, excerpt, body, weather)
                    files["src/content/posts/$slug.md"] = draft.toMarkdown(imagePaths).toByteArray()
                    RouteLog.toRouteJsonBytes(context)?.let { files["src/data/route.json"] = it }
                    PublishQueue.enqueue(context, "Post: $title", files)
                }
                toast("Queued — publishes when there's signal")
                nav.popBackStack()
            } catch (e: Exception) {
                toast("Failed to queue: ${e.message}")
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
        Text("New post", style = MaterialTheme.typography.headlineMedium)

        OutlinedTextField(title, { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(date, { date = it }, label = { Text("Date (yyyy-mm-dd)") }, modifier = Modifier.fillMaxWidth())
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

        OutlinedButton(onClick = {
            photoPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        }) { Text(if (photos.isEmpty()) "Add photos" else "Change photos (${photos.size})") }

        if (photos.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(photos) { uri ->
                    AsyncImage(model = uri, contentDescription = null, modifier = Modifier.size(96.dp))
                }
            }
        }

        Button(onClick = ::save, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (busy) "Queueing…" else "Save & queue for publish")
        }
    }
}
