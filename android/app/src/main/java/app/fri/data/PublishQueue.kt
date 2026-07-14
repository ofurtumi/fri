package app.fri.data

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import app.fri.work.PublishWorker
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Saving a post never touches the network: it becomes a bundle on disk, and
 * PublishWorker drains the queue whenever there's connectivity. A bundle is a
 * directory: message.txt + files/<repo-path>.
 */
object PublishQueue {
    private fun queueDir(context: Context) = File(context.filesDir, "queue").apply { mkdirs() }

    /**
     * [localFiles] (repo path -> staged file, e.g. a transcoded clip) are moved
     * into the bundle instead of round-tripping through a ByteArray.
     */
    fun enqueue(
        context: Context,
        message: String,
        files: Map<String, ByteArray>,
        localFiles: Map<String, File> = emptyMap(),
    ) {
        val bundle = File(queueDir(context), System.currentTimeMillis().toString())
        val filesDir = File(bundle, "files")
        for ((path, bytes) in files) {
            val target = File(filesDir, path)
            check(target.canonicalPath.startsWith(filesDir.canonicalPath)) { "bad path: $path" }
            target.parentFile?.mkdirs()
            target.writeBytes(bytes)
        }
        for ((path, source) in localFiles) {
            val target = File(filesDir, path)
            check(target.canonicalPath.startsWith(filesDir.canonicalPath)) { "bad path: $path" }
            target.parentFile?.mkdirs()
            if (!source.renameTo(target)) {
                source.copyTo(target, overwrite = true)
                source.delete()
            }
        }
        File(bundle, "message.txt").writeText(message)
        schedule(context)
    }

    fun pending(context: Context): List<Pair<File, String>> =
        queueDir(context).listFiles { f -> f.isDirectory }
            ?.sortedBy { it.name }
            ?.map { it to (File(it, "message.txt").takeIf(File::exists)?.readText() ?: "publish") }
            ?: emptyList()

    fun filesOf(bundle: File): Map<String, ByteArray> {
        val root = File(bundle, "files")
        return root.walkTopDown()
            .filter { it.isFile }
            .associate { it.relativeTo(root).path to it.readBytes() }
    }

    fun schedule(context: Context) {
        val request = OneTimeWorkRequestBuilder<PublishWorker>()
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("publish", ExistingWorkPolicy.REPLACE, request)
    }
}
