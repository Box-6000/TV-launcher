package com.launch.tv.ui

import android.graphics.Matrix
import android.graphics.drawable.AnimatedImageDrawable
import android.media.MediaPlayer
import android.os.Build
import android.view.TextureView
import android.widget.ImageView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

@Composable
fun MotionWallpaper(file: File) {
    val ext = file.extension.lowercase()
    if (ext == "gif" || ext == "webp") {
        StillMotion(file)
    } else {
        VideoMotion(file)
    }
}

@Composable
private fun VideoMotion(file: File) {
    val player = remember(file.absolutePath) { MediaPlayer() }
    var veil by remember(file.absolutePath) { mutableFloatStateOf(0f) }
    var phase by remember(file.absolutePath) { mutableIntStateOf(0) }
    val opening by produceState<ImageBitmap?>(null, file.absolutePath) {
        value = withContext(Dispatchers.IO) { openingFrame(file) }
    }
    DisposableEffect(file.absolutePath) {
        onDispose {
            runCatching {
                player.reset()
                player.release()
            }
        }
    }
    LaunchedEffect(file.absolutePath) {
        while (isActive) {
            delay(40)
            val duration = runCatching { player.duration }.getOrDefault(-1)
            val position = runCatching { player.currentPosition }.getOrDefault(0)
            if (duration < 1500) continue
            when (phase) {
                0 -> if (duration - position < 420) phase = 1
                1 -> {
                    veil = (veil + 0.16f).coerceAtMost(1f)
                    if (veil >= 0.98f) {
                        runCatching { player.seekTo(0) }
                        phase = 2
                    }
                }
                else -> {
                    veil = (veil - 0.16f).coerceAtLeast(0f)
                    if (veil <= 0.02f) phase = 0
                }
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
            TextureView(context).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {
                        runCatching {
                            player.setDataSource(file.absolutePath)
                            player.setSurface(android.view.Surface(surface))
                            player.isLooping = false
                            player.setVolume(0f, 0f)
                            player.setOnPreparedListener { ready ->
                                cover(this@apply, ready.videoWidth, ready.videoHeight)
                                ready.start()
                            }
                            player.setOnSeekCompleteListener { it.start() }
                            player.setOnCompletionListener {
                                it.seekTo(0)
                                it.start()
                            }
                            player.prepareAsync()
                        }
                    }

                    override fun onSurfaceTextureSizeChanged(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {
                        if (player.isPlaying) cover(this@apply, player.videoWidth, player.videoHeight)
                    }

                    override fun onSurfaceTextureDestroyed(surface: android.graphics.SurfaceTexture): Boolean {
                        runCatching { player.setSurface(null) }
                        return true
                    }

                    override fun onSurfaceTextureUpdated(surface: android.graphics.SurfaceTexture) = Unit
                }
            }
        },
        )
        val still = opening
        if (still != null && veil > 0.01f) {
            Image(
                still,
                null,
                Modifier.fillMaxSize().graphicsLayer { alpha = veil },
                contentScale = ContentScale.Crop,
            )
        }
    }
}

private fun openingFrame(file: File): ImageBitmap? {
    val retriever = android.media.MediaMetadataRetriever()
    return try {
        retriever.setDataSource(file.absolutePath)
        val frame = if (Build.VERSION.SDK_INT >= 27) {
            val width = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1920
            val height = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 1080
            retriever.getScaledFrameAtTime(0, android.media.MediaMetadataRetriever.OPTION_CLOSEST, width, height)
        } else {
            retriever.getFrameAtTime(0, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        }
        frame?.asImageBitmap()
    } catch (_: Throwable) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}

private fun cover(view: TextureView, videoW: Int, videoH: Int) {
    if (videoW <= 0 || videoH <= 0 || view.width == 0 || view.height == 0) return
    val viewAspect = view.width.toFloat() / view.height.toFloat()
    val videoAspect = videoW.toFloat() / videoH.toFloat()
    val scaleX: Float
    val scaleY: Float
    if (videoAspect > viewAspect) {
        scaleY = 1f
        scaleX = videoAspect / viewAspect
    } else {
        scaleX = 1f
        scaleY = viewAspect / videoAspect
    }
    val matrix = Matrix()
    matrix.setScale(scaleX, scaleY, view.width / 2f, view.height / 2f)
    view.setTransform(matrix)
}

@Composable
private fun StillMotion(file: File) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            if (Build.VERSION.SDK_INT >= 28) {
                ImageView(context).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    val drawable = android.graphics.ImageDecoder.decodeDrawable(
                        android.graphics.ImageDecoder.createSource(file),
                    )
                    setImageDrawable(drawable)
                    if (drawable is AnimatedImageDrawable) drawable.start()
                }
            } else {
                val movie = file.inputStream().use { android.graphics.Movie.decodeStream(it) }
                if (movie == null) {
                    ImageView(context)
                } else {
                    MovieView(context, movie)
                }
            }
        },
        onRelease = { view ->
            if (Build.VERSION.SDK_INT >= 28 && view is ImageView) {
                val drawable = view.drawable
                if (drawable is AnimatedImageDrawable) drawable.stop()
            }
        },
    )
}

private class MovieView(
    context: android.content.Context,
    private val movie: android.graphics.Movie,
) : android.view.View(context) {
    private val started = android.os.SystemClock.uptimeMillis()

    override fun onDraw(canvas: android.graphics.Canvas) {
        val duration = movie.duration().coerceAtLeast(1)
        movie.setTime(((android.os.SystemClock.uptimeMillis() - started) % duration).toInt())
        val scale = max(width / movie.width().coerceAtLeast(1).toFloat(), height / movie.height().coerceAtLeast(1).toFloat())
        canvas.save()
        canvas.translate((width - movie.width() * scale) / 2f, (height - movie.height() * scale) / 2f)
        canvas.scale(scale, scale)
        movie.draw(canvas, 0f, 0f)
        canvas.restore()
        invalidate()
    }
}
