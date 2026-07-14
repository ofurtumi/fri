package app.fri.data

/** Everything the editor collects; turned into markdown matching the site's content schema. */
data class PostDraft(
    val title: String,
    val date: String, // yyyy-mm-dd
    val trip: String, // trip id from trips.json, may be blank on old posts
    val location: String,
    val lat: Double,
    val lng: Double,
    val excerpt: String,
    val body: String,
    val weather: Weather?,
)

fun slugify(text: String): String = text
    .lowercase()
    .replace('á', 'a').replace('é', 'e').replace('í', 'i').replace('ó', 'o')
    .replace('ú', 'u').replace('ý', 'y').replace('þ', 't').replace('ð', 'd')
    .replace('æ', 'a').replace('ö', 'o')
    .replace(Regex("[^a-z0-9]+"), "-")
    .trim('-')

/**
 * Frontmatter must match src/content.config.ts on the site — a mismatch
 * fails the site build, which is the safety net. Key order is fixed so
 * parsePostMarkdown() round-trips what this emits.
 */
fun PostDraft.toMarkdown(
    imagePaths: List<String>,
    videoPaths: List<String> = emptyList(),
): String = buildString {
    appendLine("---")
    appendLine("title: '${title.replace("'", "''")}'")
    appendLine("date: $date")
    if (trip.isNotBlank()) appendLine("trip: $trip")
    appendLine("location: ${location.trim()}")
    appendLine("lat: $lat")
    appendLine("lng: $lng")
    if (excerpt.isNotBlank()) appendLine("excerpt: ${excerpt.trim()}")
    weather?.let {
        appendLine("weather:")
        appendLine("  temp: ${it.temp}")
        appendLine("  description: ${it.description}")
        appendLine("  windKmh: ${it.windKmh}")
    }
    if (imagePaths.isNotEmpty()) {
        appendLine("photos:")
        for (path in imagePaths) appendLine("  - $path")
    }
    if (videoPaths.isNotEmpty()) {
        appendLine("videos:")
        for (path in videoPaths) appendLine("  - $path")
    }
    appendLine("---")
    appendLine()
    appendLine(body.trim())
}
