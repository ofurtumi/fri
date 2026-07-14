package app.fri.data

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

/**
 * Per-trip GPS trace, appended locally by TrackService as JSON-lines (cheap
 * appends, survives crashes) and serialized to src/data/routes/<tripId>.json
 * on publish. The app owns those files in the repo — single author, no merge
 * worries. Local logs are kept after publish; each publish is a full snapshot.
 */
object RouteLog {
    private fun file(context: Context, tripId: String) =
        File(context.filesDir, "track-$tripId.jsonl")

    /** Repo path the trace publishes to; must match routeFor() in src/lib/trips.ts. */
    fun repoPath(tripId: String) = "src/data/routes/$tripId.json"

    fun append(context: Context, tripId: String, lat: Double, lng: Double, timeMillis: Long) {
        val line = buildJsonObject {
            put("lat", lat)
            put("lng", lng)
            put("t", timeMillis / 1000)
        }
        file(context, tripId).appendText(line.toString() + "\n")
    }

    fun pointCount(context: Context, tripId: String): Int {
        val f = file(context, tripId)
        if (!f.exists()) return 0
        return f.useLines { lines -> lines.count { it.isNotBlank() } }
    }

    /** Full trace as the routes/<tripId>.json payload, or null if nothing logged yet. */
    fun toRouteJsonBytes(context: Context, tripId: String): ByteArray? {
        val f = file(context, tripId)
        if (!f.exists()) return null
        val points = f.readLines().filter { it.isNotBlank() }.mapNotNull { line ->
            // The service may be mid-append while we read; skip a torn last line
            runCatching { Json.parseToJsonElement(line) }.getOrNull()
        }
        if (points.isEmpty()) return null
        val arr = buildJsonArray { for (p in points) add(p) }
        return arr.toString().toByteArray()
    }

    /** Adopt the pre-trips global log (track.jsonl) into [tripId] once. */
    fun migrateLegacyLog(context: Context, tripId: String) {
        val legacy = File(context.filesDir, "track.jsonl")
        if (legacy.exists() && !file(context, tripId).exists()) {
            legacy.renameTo(file(context, tripId))
        }
    }
}
