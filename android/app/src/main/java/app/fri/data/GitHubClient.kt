package app.fri.data

import android.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Talks to the GitHub Git Data API so the app never needs a local git clone:
 * upload blobs -> build a tree -> create a commit -> move the branch ref.
 * One commit can carry the post, its photos, route.json and stats.json
 * atomically. All calls are blocking; run on Dispatchers.IO.
 */
data class RepoEntry(val name: String, val path: String, val type: String, val size: Long)

class GitHubClient(private val settings: RepoSettings) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }
    private val jsonType = "application/json".toMediaType()
    private val base = "https://api.github.com/repos/${settings.owner}/${settings.repo}"

    private fun builder(url: String): Request.Builder = Request.Builder()
        .url(url)
        .header("Authorization", "Bearer ${settings.token}")
        .header("Accept", "application/vnd.github+json")
        .header("X-GitHub-Api-Version", "2022-11-28")

    private fun execute(request: Request): String {
        client.newCall(request).execute().use { res ->
            val body = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                throw IOException("GitHub ${res.code} on ${request.url.encodedPath}: ${body.take(300)}")
            }
            return body
        }
    }

    private fun field(body: String, vararg path: String): String {
        var el = json.parseToJsonElement(body)
        for (p in path) el = el.jsonObject[p] ?: throw IOException("missing '$p' in GitHub response")
        return el.jsonPrimitive.content
    }

    /** Text content of a file on the branch, or null if it doesn't exist. */
    fun getFileText(path: String): String? {
        val req = builder("$base/contents/$path?ref=${settings.branch}").get().build()
        client.newCall(req).execute().use { res ->
            if (res.code == 404) return null
            val body = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw IOException("GitHub ${res.code}: ${body.take(300)}")
            val content = field(body, "content").replace("\n", "")
            return String(Base64.decode(content, Base64.DEFAULT), Charsets.UTF_8)
        }
    }

    /** Entries of a directory on the branch; empty if the directory doesn't exist. */
    fun listDirectory(path: String): List<RepoEntry> {
        val req = builder("$base/contents/$path?ref=${settings.branch}").get().build()
        client.newCall(req).execute().use { res ->
            if (res.code == 404) return emptyList()
            val body = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw IOException("GitHub ${res.code}: ${body.take(300)}")
            return json.parseToJsonElement(body).jsonArray.map { el ->
                val o = el.jsonObject
                RepoEntry(
                    name = o["name"]?.jsonPrimitive?.content ?: "",
                    path = o["path"]?.jsonPrimitive?.content ?: "",
                    type = o["type"]?.jsonPrimitive?.content ?: "",
                    size = o["size"]?.jsonPrimitive?.long ?: 0L,
                )
            }
        }
    }

    /** Commit [files] (repo path -> bytes) as one commit on the branch. */
    fun commit(message: String, files: Map<String, ByteArray>) {
        require(files.isNotEmpty()) { "nothing to commit" }

        val headSha = field(
            execute(builder("$base/git/ref/heads/${settings.branch}").get().build()),
            "object", "sha",
        )
        val baseTreeSha = field(
            execute(builder("$base/git/commits/$headSha").get().build()),
            "tree", "sha",
        )

        val entries = files.map { (path, bytes) ->
            val blobBody = buildJsonObject {
                put("content", Base64.encodeToString(bytes, Base64.NO_WRAP))
                put("encoding", "base64")
            }
            val blobSha = field(
                execute(
                    builder("$base/git/blobs")
                        .post(blobBody.toString().toRequestBody(jsonType))
                        .build(),
                ),
                "sha",
            )
            path to blobSha
        }

        val treeBody = buildJsonObject {
            put("base_tree", baseTreeSha)
            putJsonArray("tree") {
                for ((path, blobSha) in entries) {
                    add(
                        buildJsonObject {
                            put("path", path)
                            put("mode", "100644")
                            put("type", "blob")
                            put("sha", blobSha)
                        },
                    )
                }
            }
        }
        val treeSha = field(
            execute(builder("$base/git/trees").post(treeBody.toString().toRequestBody(jsonType)).build()),
            "sha",
        )

        val commitBody = buildJsonObject {
            put("message", message)
            put("tree", treeSha)
            putJsonArray("parents") { add(headSha) }
            if (settings.authorName.isNotBlank() && settings.authorEmail.isNotBlank()) {
                put(
                    "author",
                    buildJsonObject {
                        put("name", settings.authorName)
                        put("email", settings.authorEmail)
                    },
                )
            }
        }
        val commitSha = field(
            execute(builder("$base/git/commits").post(commitBody.toString().toRequestBody(jsonType)).build()),
            "sha",
        )

        val refBody = buildJsonObject { put("sha", commitSha) }
        execute(
            builder("$base/git/refs/heads/${settings.branch}")
                .patch(refBody.toString().toRequestBody(jsonType))
                .build(),
        )
    }
}
