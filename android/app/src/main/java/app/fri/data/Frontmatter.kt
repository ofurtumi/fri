package app.fri.data

/** A published post fetched back from the repo, split into editable pieces. */
data class ParsedPost(
    val slug: String,
    val draft: PostDraft,
    val photos: List<String>, // ./<slug>/photo-N.jpg as written in frontmatter
    val videos: List<String>, // ./<slug>/clip-N.mp4
)

/**
 * Minimal parser for exactly the frontmatter toMarkdown() emits (which is the
 * site's schema). Deliberately strict: an unknown top-level key throws,
 * because saving regenerates the whole frontmatter and would silently drop
 * anything we don't model. Better to refuse to edit such a post in the app.
 */
fun parsePostMarkdown(slug: String, text: String): ParsedPost {
    val normalized = text.replace("\r\n", "\n")
    require(normalized.startsWith("---\n")) { "no frontmatter fence" }
    val end = normalized.indexOf("\n---", startIndex = 3)
    require(end > 0) { "unterminated frontmatter" }
    val front = normalized.substring(4, end + 1)
    val body = normalized.substring(end + 4).trimStart('\n').trimEnd()

    val scalars = mutableMapOf<String, String>()
    val weather = mutableMapOf<String, String>()
    val photos = mutableListOf<String>()
    val videos = mutableListOf<String>()

    // Which construct indented lines currently belong to
    var block: String? = null

    for (rawLine in front.lines()) {
        if (rawLine.isBlank()) continue
        if (rawLine.startsWith("  ")) {
            val line = rawLine.trim()
            when (block) {
                "weather" -> {
                    val m = Regex("""^(temp|description|windKmh):\s*(.*)$""").find(line)
                        ?: throw IllegalArgumentException("unexpected weather line: $line")
                    weather[m.groupValues[1]] = m.groupValues[2].trim()
                }
                "photos", "videos" -> {
                    require(line.startsWith("- ")) { "unexpected list line: $line" }
                    val item = line.removePrefix("- ").trim()
                    if (block == "photos") photos.add(item) else videos.add(item)
                }
                else -> throw IllegalArgumentException("indented line outside a block: $line")
            }
            continue
        }

        val m = Regex("""^([A-Za-z][A-Za-z0-9]*):\s*(.*)$""").find(rawLine)
            ?: throw IllegalArgumentException("unparseable frontmatter line: $rawLine")
        val key = m.groupValues[1]
        val value = m.groupValues[2].trim()
        when (key) {
            "title", "date", "trip", "location", "lat", "lng", "excerpt" -> {
                block = null
                scalars[key] = value
            }
            "weather", "photos", "videos" -> {
                require(value.isEmpty()) { "$key must be a block" }
                block = key
            }
            else -> throw IllegalArgumentException("unknown frontmatter key: $key")
        }
    }

    val title = scalars["title"]?.let(::unquote)
        ?: throw IllegalArgumentException("missing title")
    val date = scalars["date"] ?: throw IllegalArgumentException("missing date")
    val lat = scalars["lat"]?.toDoubleOrNull() ?: throw IllegalArgumentException("bad lat")
    val lng = scalars["lng"]?.toDoubleOrNull() ?: throw IllegalArgumentException("bad lng")

    val parsedWeather = if (weather.isEmpty()) null else Weather(
        temp = weather["temp"]?.toIntOrNull() ?: throw IllegalArgumentException("bad weather.temp"),
        description = weather["description"] ?: "",
        windKmh = weather["windKmh"]?.toIntOrNull() ?: 0,
    )

    return ParsedPost(
        slug = slug,
        draft = PostDraft(
            title = title,
            date = date,
            trip = scalars["trip"] ?: "",
            location = scalars["location"] ?: "",
            lat = lat,
            lng = lng,
            excerpt = scalars["excerpt"] ?: "",
            body = body,
            weather = parsedWeather,
        ),
        photos = photos,
        videos = videos,
    )
}

private fun unquote(value: String): String =
    if (value.length >= 2 && value.startsWith("'") && value.endsWith("'")) {
        value.substring(1, value.length - 1).replace("''", "'")
    } else {
        value
    }
