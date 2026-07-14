package app.fri.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class TripStart(val name: String, val lat: Double, val lng: Double)

@Serializable
data class TripStat(val label: String, val value: String)

/** One trip in src/data/trips.json — mirror of the site's Trip type in src/lib/trips.ts. */
@Serializable
data class Trip(
    val id: String,
    val name: String,
    val start: TripStart,
    val startDate: String, // yyyy-mm-dd
    val stats: List<TripStat> = emptyList(),
)

/**
 * Sole reader/writer of trips.json on the app side. The local cache file is
 * authoritative once seeded from the repo: every mutation edits the cache and
 * queues the full file as a snapshot, so bundles drained oldest-first always
 * converge on the cache state even when several edits queue up offline.
 */
class TripsRepository(private val context: Context) {

    private fun cacheFile() = File(context.filesDir, "trips.json")

    suspend fun load(forceRemote: Boolean = false): List<Trip> = mutex.withLock {
        val cache = cacheFile()
        val cached = cache.takeIf(File::exists)
            ?.let { runCatching { parse(it.readText()) }.getOrNull() }
        if (!forceRemote && !cached.isNullOrEmpty()) return cached
        // A pending snapshot means remote is behind the cache; reading remote
        // now would resurrect stale state. An empty/unreadable cache with no
        // pending edits holds nothing worth protecting, so retry the remote
        // instead of trusting it (e.g. app set up before trips.json was pushed).
        if (pendingContainsTrips(context)) {
            check(!forceRemote) { "trips.json edits are still waiting to publish" }
            return cached ?: emptyList()
        }
        val settings = SettingsStore(context).current()
        if (!settings.configured) return cached ?: emptyList()
        val remote = withContext(Dispatchers.IO) { GitHubClient(settings).getFileText(REPO_PATH) }
            ?: return cached ?: emptyList() // nothing on the branch yet — don't seed the cache
        val trips = parse(remote)
        cache.writeText(encode(trips))
        return trips
    }

    /** Add or replace a trip (matched by id) and queue the new trips.json. */
    suspend fun upsert(trip: Trip, message: String) = mutex.withLock {
        val trips = currentCache().filter { it.id != trip.id } + trip
        val sorted = trips.sortedBy { it.startDate }
        val text = encode(sorted)
        cacheFile().writeText(text)
        withContext(Dispatchers.IO) {
            PublishQueue.enqueue(context, message, mapOf(REPO_PATH to text.toByteArray()))
        }
    }

    suspend fun updateStats(tripId: String, stats: List<TripStat>, message: String) {
        val trip = load().find { it.id == tripId } ?: error("unknown trip: $tripId")
        upsert(trip.copy(stats = stats), message)
    }

    private fun currentCache(): List<Trip> =
        cacheFile().takeIf(File::exists)?.readText()?.let(::parse) ?: emptyList()

    companion object {
        const val REPO_PATH = "src/data/trips.json"

        // Process-wide: two screens must never interleave cache read/write
        private val mutex = Mutex()

        private val json = Json {
            ignoreUnknownKeys = true
            prettyPrint = true
            prettyPrintIndent = "  "
        }

        fun parse(text: String): List<Trip> =
            json.decodeFromString(ListSerializer(Trip.serializer()), text)

        fun encode(trips: List<Trip>): String =
            json.encodeToString(ListSerializer(Trip.serializer()), trips) + "\n"

        fun newTripId(name: String, existing: List<Trip>): String {
            val base = slugify(name).ifBlank { "trip" }
            var id = base
            var n = 2
            while (existing.any { it.id == id }) id = "$base-${n++}"
            return id
        }

        /** True if any queued bundle would publish trips.json. */
        fun pendingContainsTrips(context: Context): Boolean =
            PublishQueue.pending(context).any { (bundle, _) ->
                File(bundle, "files/$REPO_PATH").exists()
            }
    }
}
