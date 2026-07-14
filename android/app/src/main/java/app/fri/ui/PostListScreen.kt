package app.fri.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import app.fri.data.ParsedPost
import app.fri.data.SettingsStore
import app.fri.data.Trip
import app.fri.data.TripsRepository
import app.fri.data.parsePostMarkdown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

private sealed interface Loaded {
    data class Ok(val post: ParsedPost) : Loaded
    data class Failed(val slug: String, val reason: String) : Loaded
}

/** Published posts fetched from the repo, grouped by trip; tap one to edit it. */
@Composable
fun PostListScreen(nav: NavController) {
    val context = LocalContext.current
    var posts by remember { mutableStateOf<List<Loaded>?>(null) }
    var trips by remember { mutableStateOf<List<Trip>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

    LaunchedEffect(Unit) {
        val settings = SettingsStore(context).current()
        if (!settings.configured) {
            toast("Configure the repo in settings first")
            error = "Not connected to the repo."
            return@LaunchedEffect
        }
        try {
            trips = TripsRepository(context).load()
            posts = withContext(Dispatchers.IO) {
                val client = GitHubClient(settings)
                val slugs = client.listDirectory("src/content/posts")
                    .filter { it.type == "file" && it.name.endsWith(".md") }
                    .map { it.name.removeSuffix(".md") }
                val limit = Semaphore(4)
                coroutineScope {
                    slugs.map { slug ->
                        async {
                            limit.withPermit {
                                try {
                                    val text = client.getFileText("src/content/posts/$slug.md")
                                        ?: return@withPermit Loaded.Failed(slug, "missing")
                                    Loaded.Ok(parsePostMarkdown(slug, text))
                                } catch (e: Exception) {
                                    Loaded.Failed(slug, e.message ?: "unparseable")
                                }
                            }
                        }
                    }.awaitAll()
                }
            }
        } catch (e: Exception) {
            error = "Could not load posts: ${e.message}"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Posts", style = MaterialTheme.typography.headlineMedium)

        when {
            error != null -> Text(error!!)
            posts == null -> Text("Loading posts from the repo…")
            else -> {
                val ok = posts!!.filterIsInstance<Loaded.Ok>().map(Loaded.Ok::post)
                val failed = posts!!.filterIsInstance<Loaded.Failed>()
                val tripNames = trips.associate { it.id to it.name }

                if (ok.isEmpty() && failed.isEmpty()) Text("No posts in the repo yet.")

                val groups = ok
                    .groupBy { it.draft.trip }
                    .entries
                    // newest trips first, blank/unknown trip ids last
                    .sortedByDescending { (id, _) -> trips.find { it.id == id }?.startDate ?: "" }

                groups.forEach { (groupTripId, groupPosts) ->
                    Text(
                        tripNames[groupTripId] ?: if (groupTripId.isBlank()) "No trip" else groupTripId,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    groupPosts.sortedByDescending { it.draft.date }.forEach { post ->
                        Card(
                            Modifier
                                .fillMaxWidth()
                                .clickable { nav.navigate("post/${post.slug}") },
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(post.draft.title, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${post.draft.date} · ${post.draft.location}" +
                                        (if (post.photos.isNotEmpty()) " · ${post.photos.size} photos" else "") +
                                        (if (post.videos.isNotEmpty()) " · ${post.videos.size} clips" else ""),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }

                failed.forEach { f ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(f.slug, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Can't edit in the app: ${f.reason}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }

        OutlinedButton(onClick = { nav.popBackStack() }, modifier = Modifier.fillMaxWidth()) {
            Text("Back")
        }
    }
}
