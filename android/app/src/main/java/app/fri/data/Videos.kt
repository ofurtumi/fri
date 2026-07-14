package app.fri.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Commits go through the Git Data API as inline base64 JSON, so a clip has to
 * stay small: commit() briefly holds ~3-4x the file size in memory and GitHub
 * rejects oversized request bodies. 30MB is minutes of 270p H.264 — plenty.
 */
const val MAX_CLIP_BYTES = 30L * 1024 * 1024

/**
 * Transcode [uri] to a small H.264+AAC mp4 with a ~270px short edge (the
 * deliberate old-digicam look, and tiny commits). Runs media3 Transformer on
 * the main looper as it requires; call from any dispatcher. [onProgress] gets
 * 0-100 on the main thread.
 */
suspend fun transcodeClip(
    context: Context,
    uri: Uri,
    outFile: File,
    onProgress: (Int) -> Unit = {},
): File {
    outFile.parentFile?.mkdirs()
    outFile.delete()

    // Rotation-corrected display size decides the Presentation height:
    // 270 short edge means height 270 for landscape, 480(=270*16/9) for portrait
    val portrait = withContext(Dispatchers.IO) { isPortrait(context, uri) }
    val targetHeight = if (portrait) 480 else 270

    withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        if (cont.isActive) cont.resume(Unit)
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException,
                    ) {
                        if (cont.isActive) cont.resumeWithException(exportException)
                    }
                })
                .build()

            val item = EditedMediaItem.Builder(MediaItem.fromUri(uri))
                .setEffects(
                    Effects(emptyList(), listOf(Presentation.createForHeight(targetHeight))),
                )
                .build()
            transformer.start(item, outFile.absolutePath)

            val handler = Handler(Looper.getMainLooper())
            val holder = ProgressHolder()
            val poll = object : Runnable {
                override fun run() {
                    if (!cont.isActive) return
                    if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                        onProgress(holder.progress)
                    }
                    handler.postDelayed(this, 500)
                }
            }
            handler.post(poll)

            cont.invokeOnCancellation {
                handler.post {
                    transformer.cancel()
                    outFile.delete()
                }
            }
        }
    }

    if (outFile.length() == 0L) throw IOException("transcode produced an empty file")
    if (outFile.length() > MAX_CLIP_BYTES) {
        val mb = outFile.length() / (1024 * 1024)
        outFile.delete()
        throw IOException("clip is still ${mb}MB after transcoding — too big to publish, trim it first")
    }
    return outFile
}

private fun isPortrait(context: Context, uri: Uri): Boolean {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            ?.toIntOrNull() ?: return false
        val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            ?.toIntOrNull() ?: return false
        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            ?.toIntOrNull() ?: 0
        if (rotation == 90 || rotation == 270) w > h else h > w
    } catch (e: Exception) {
        false
    } finally {
        retriever.release()
    }
}
