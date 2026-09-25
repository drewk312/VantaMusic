package com.audiophile.musicplayer.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import android.widget.RemoteViews
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.audiophile.musicplayer.MainActivity
import com.audiophile.musicplayer.R
import com.audiophile.musicplayer.playback.NowPlayingState
import com.audiophile.musicplayer.playback.PlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

data class WidgetTheme(
    val accentColor: Int,
    val secondaryColor: Int,
    val darkBaseColor: Int
)

object VantaWidgetUpdater {
    private const val TAG = "VantaWidgetUpdater"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile private var lastArtworkUrl: String? = null
    @Volatile private var cachedArtworkBitmap: Bitmap? = null
    @Volatile private var cachedTheme: WidgetTheme = defaultTheme()

    @Volatile private var cachedExpandedBg: Bitmap? = null
    @Volatile private var cachedCompactBg: Bitmap? = null
    @Volatile private var cachedPlayBtn: Bitmap? = null
    @Volatile private var cachedPauseBtn: Bitmap? = null

    @Volatile private var lastTitle: String? = null
    @Volatile private var lastArtist: String? = null
    @Volatile private var lastIsPlaying: Boolean? = null
    @Volatile private var lastQuality: String? = null
    @Volatile private var lastIsFavorite: Boolean? = null
    @Volatile private var lastPositionSec: Long = -1L

    private fun defaultTheme(): WidgetTheme = WidgetTheme(
        accentColor = Color.parseColor("#FFE5A93C"),
        secondaryColor = Color.parseColor("#FFA78BFA"),
        darkBaseColor = Color.parseColor("#FF0F0E16")
    )

    fun updateAllWidgets(context: Context, state: NowPlayingState, force: Boolean = false) {
        val appWidgetManager = AppWidgetManager.getInstance(context) ?: return

        val expandedIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, VantaWidgetExpanded::class.java)
        )
        val compactIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, VantaWidgetCompact::class.java)
        )

        if (expandedIds.isEmpty() && compactIds.isEmpty()) return

        val currentQuality = state.qualityInfo?.bestQualityLabel().orEmpty()
        val currentPositionSec = (state.positionMs / 1000).coerceAtLeast(0L)

        if (!force &&
            state.title == lastTitle &&
            state.artist == lastArtist &&
            state.isPlaying == lastIsPlaying &&
            state.artworkUrl == lastArtworkUrl &&
            currentQuality == lastQuality &&
            state.isFavorite == lastIsFavorite &&
            currentPositionSec == lastPositionSec
        ) {
            return
        }

        lastTitle = state.title
        lastArtist = state.artist
        lastIsPlaying = state.isPlaying
        lastQuality = currentQuality
        lastIsFavorite = state.isFavorite
        lastPositionSec = currentPositionSec

        scope.launch {
            val bitmap = resolveArtworkBitmap(context, state.artworkUrl)
            val theme = resolveTheme(bitmap, state.artworkUrl)

            val expandedBg = getExpandedBackground(theme)
            val compactBg = getCompactBackground(theme)
            val playPauseBtn = if (state.isPlaying) getPauseButton(theme) else getPlayButton(theme)

            withContext(Dispatchers.Main) {
                if (expandedIds.isNotEmpty()) {
                    val views = buildExpandedViews(context, state, bitmap, theme, expandedBg, playPauseBtn)
                    for (id in expandedIds) {
                        try {
                            appWidgetManager.updateAppWidget(id, views)
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed updating expanded widget $id: ${e.message}")
                        }
                    }
                }

                if (compactIds.isNotEmpty()) {
                    val views = buildCompactViews(context, state, bitmap, theme, compactBg, playPauseBtn)
                    for (id in compactIds) {
                        try {
                            appWidgetManager.updateAppWidget(id, views)
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed updating compact widget $id: ${e.message}")
                        }
                    }
                }
            }
        }
    }

    private suspend fun resolveArtworkBitmap(context: Context, url: String?): Bitmap? {
        if (url.isNullOrBlank()) {
            cachedArtworkBitmap = null
            lastArtworkUrl = null
            return null
        }
        if (url == lastArtworkUrl && cachedArtworkBitmap != null && !cachedArtworkBitmap!!.isRecycled) {
            return cachedArtworkBitmap
        }

        return try {
            val request = ImageRequest.Builder(context)
                .data(url)
                .size(240, 240)
                .allowHardware(false) // Crucial: RemoteViews crashes with hardware bitmaps
                .build()

            val result = context.imageLoader.execute(request)
            if (result is SuccessResult) {
                val orig = (result.drawable as? BitmapDrawable)?.bitmap
                if (orig != null) {
                    val rounded = getRoundedCornerBitmap(orig, 24f)
                    cachedArtworkBitmap = rounded
                    lastArtworkUrl = url
                    rounded
                } else null
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching artwork bitmap for widget: ${e.message}")
            null
        }
    }

    private fun resolveTheme(bitmap: Bitmap?, url: String?): WidgetTheme {
        if (bitmap == null || bitmap.isRecycled) {
            cachedTheme = defaultTheme()
            return cachedTheme
        }

        return try {
            val palette = Palette.from(bitmap).maximumColorCount(16).generate()
            val vibrant = palette.vibrantSwatch ?: palette.lightVibrantSwatch ?: palette.darkVibrantSwatch
            val muted = palette.mutedSwatch ?: palette.lightMutedSwatch ?: palette.darkMutedSwatch ?: palette.dominantSwatch

            val rawAccent = vibrant?.rgb ?: muted?.rgb ?: Color.parseColor("#FFE5A93C")
            val accent = boostVibrance(rawAccent)
            val secondary = muted?.rgb ?: accent
            val darkBase = ColorUtils.blendARGB(muted?.rgb ?: Color.BLACK, Color.BLACK, 0.80f)

            val theme = WidgetTheme(
                accentColor = accent,
                secondaryColor = secondary,
                darkBaseColor = darkBase
            )
            cachedTheme = theme
            // Invalidate cached backgrounds and buttons on theme update
            cachedExpandedBg = null
            cachedCompactBg = null
            cachedPlayBtn = null
            cachedPauseBtn = null
            theme
        } catch (e: Exception) {
            defaultTheme()
        }
    }

    private fun boostVibrance(color: Int): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        hsl[1] = hsl[1].coerceIn(0.55f, 0.95f) // Vibrant saturation
        hsl[2] = hsl[2].coerceIn(0.50f, 0.76f) // Crisp contrast against dark velvet
        return ColorUtils.HSLToColor(hsl)
    }

    private fun getExpandedBackground(theme: WidgetTheme): Bitmap {
        val existing = cachedExpandedBg
        if (existing != null && !existing.isRecycled) return existing

        val width = 720
        val height = 360
        val cornerRadius = 42f

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Base atmospheric vertical gradient
        val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                theme.darkBaseColor,
                Color.parseColor("#FF07060A"),
                Shader.TileMode.CLAMP
            )
        }
        val rectF = RectF(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, basePaint)

        // 2. Radial ambient light bloom behind artwork
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                width * 0.16f, height * 0.52f, width * 0.65f,
                intArrayOf(
                    ColorUtils.setAlphaComponent(theme.accentColor, 75),
                    ColorUtils.setAlphaComponent(theme.secondaryColor, 35),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.40f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, glowPaint)

        // 3. Glass border with ambient illumination
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                intArrayOf(
                    ColorUtils.setAlphaComponent(theme.accentColor, 100),
                    ColorUtils.setAlphaComponent(Color.WHITE, 40),
                    ColorUtils.setAlphaComponent(theme.accentColor, 50)
                ),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        val borderRect = RectF(1.5f, 1.5f, width - 1.5f, height - 1.5f)
        canvas.drawRoundRect(borderRect, cornerRadius, cornerRadius, borderPaint)

        cachedExpandedBg = bitmap
        return bitmap
    }

    private fun getCompactBackground(theme: WidgetTheme): Bitmap {
        val existing = cachedCompactBg
        if (existing != null && !existing.isRecycled) return existing

        val width = 720
        val height = 180
        val cornerRadius = 36f

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, width.toFloat(), 0f,
                theme.darkBaseColor,
                Color.parseColor("#FF07060A"),
                Shader.TileMode.CLAMP
            )
        }
        val rectF = RectF(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, basePaint)

        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                width * 0.12f, height * 0.5f, width * 0.45f,
                intArrayOf(
                    ColorUtils.setAlphaComponent(theme.accentColor, 65),
                    ColorUtils.setAlphaComponent(theme.secondaryColor, 25),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, glowPaint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                intArrayOf(
                    ColorUtils.setAlphaComponent(theme.accentColor, 90),
                    ColorUtils.setAlphaComponent(Color.WHITE, 30),
                    ColorUtils.setAlphaComponent(theme.accentColor, 40)
                ),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        val borderRect = RectF(1.5f, 1.5f, width - 1.5f, height - 1.5f)
        canvas.drawRoundRect(borderRect, cornerRadius, cornerRadius, borderPaint)

        cachedCompactBg = bitmap
        return bitmap
    }

    private fun getPlayButton(theme: WidgetTheme): Bitmap {
        val existing = cachedPlayBtn
        if (existing != null && !existing.isRecycled) return existing

        val size = 110
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = size / 2f
        val radius = center - 3f

        // Glass disc
        val discPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#381A1826")
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, radius, discPaint)

        // Accent glowing ring
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3.5f
            color = theme.accentColor
        }
        canvas.drawCircle(center, center, radius - 1f, ringPaint)

        // Play triangle
        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.accentColor
            style = Paint.Style.FILL
        }
        val triWidth = size * 0.32f
        val triHeight = size * 0.36f
        val triLeft = center - triWidth * 0.40f
        val triTop = (size - triHeight) / 2f

        val path = Path().apply {
            moveTo(triLeft, triTop)
            lineTo(triLeft + triWidth, center)
            lineTo(triLeft, triTop + triHeight)
            close()
        }
        canvas.drawPath(path, iconPaint)

        cachedPlayBtn = bitmap
        return bitmap
    }

    private fun getPauseButton(theme: WidgetTheme): Bitmap {
        val existing = cachedPauseBtn
        if (existing != null && !existing.isRecycled) return existing

        val size = 110
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = size / 2f
        val radius = center - 3f

        // Solid vibrant glowing disc
        val discPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                center, center * 0.82f, radius,
                intArrayOf(
                    ColorUtils.blendARGB(theme.accentColor, Color.WHITE, 0.22f),
                    theme.accentColor
                ),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(center, center, radius, discPaint)

        // Specular outer ring
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = ColorUtils.setAlphaComponent(Color.WHITE, 130)
        }
        canvas.drawCircle(center, center, radius - 1f, ringPaint)

        // Contrast dark pause bars
        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF0B0A10")
            style = Paint.Style.FILL
        }
        val barWidth = size * 0.11f
        val barHeight = size * 0.38f
        val gap = size * 0.13f
        val top = (size - barHeight) / 2f
        val bottom = top + barHeight
        val left1 = center - gap / 2f - barWidth
        val left2 = center + gap / 2f

        canvas.drawRoundRect(RectF(left1, top, left1 + barWidth, bottom), 4f, 4f, iconPaint)
        canvas.drawRoundRect(RectF(left2, top, left2 + barWidth, bottom), 4f, 4f, iconPaint)

        cachedPauseBtn = bitmap
        return bitmap
    }

    private fun getRoundedCornerBitmap(bitmap: Bitmap, cornerRadiusPx: Float): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = Rect(0, 0, bitmap.width, bitmap.height)
        val rectF = RectF(rect)

        canvas.drawRoundRect(rectF, cornerRadiusPx, cornerRadiusPx, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, rect, rect, paint)
        return output
    }

    private fun formatDuration(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0L)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%d:%02d", minutes, seconds)
    }

    private fun buildExpandedViews(
        context: Context,
        state: NowPlayingState,
        artwork: Bitmap?,
        theme: WidgetTheme,
        background: Bitmap,
        playPauseBitmap: Bitmap
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.vanta_widget_expanded)

        // 1. Atmospheric Theme Background
        views.setImageViewBitmap(R.id.widget_bg, background)

        // 2. Metadata
        val hasTrack = !state.title.isNullOrBlank()
        views.setTextViewText(
            R.id.widget_title,
            if (hasTrack) state.title else "VANTA Music"
        )
        views.setTextViewText(
            R.id.widget_artist,
            if (hasTrack) (state.artist ?: "Unknown Artist") else "Pure Bit-Perfect Audio"
        )
        val albumOrSubtitle = if (hasTrack) {
            state.album?.takeIf { it.isNotBlank() } ?: "Studio Master FLAC"
        } else {
            "Select music to play"
        }
        views.setTextViewText(R.id.widget_album, albumOrSubtitle)

        // 3. Audio Quality Badge
        val (badgeText, badgeBgRes, badgeTextColor) = when {
            state.qualityInfo?.isSony360RealityAudio == true ->
                Triple("360 REALITY", R.drawable.widget_badge_spatial, Color.parseColor("#FF38BDF8"))
            state.qualityInfo?.isDolbyAtmos == true ->
                Triple("DOLBY ATMOS", R.drawable.widget_badge_atmos, Color.parseColor("#FFA78BFA"))
            state.qualityInfo?.isHiRes == true ->
                Triple("24-BIT HI-RES", R.drawable.widget_badge_hires, Color.parseColor("#FFE5A93C"))
            state.qualityInfo?.sampleRateHz != null -> {
                val depth = state.qualityInfo.bitDepth ?: 16
                val khz = String.format(Locale.US, "%.1f", state.qualityInfo.sampleRateHz / 1000.0)
                Triple("$depth-BIT · $khz kHz", R.drawable.widget_badge_flac, Color.parseColor("#FF34D399"))
            }
            else ->
                Triple("STUDIO MASTER", R.drawable.widget_badge_hires, Color.parseColor("#FFE5A93C"))
        }
        views.setTextViewText(R.id.widget_quality, badgeText)
        views.setInt(R.id.widget_quality, "setBackgroundResource", badgeBgRes)
        views.setTextColor(R.id.widget_quality, badgeTextColor)

        // 4. Artwork
        if (artwork != null && !artwork.isRecycled) {
            views.setImageViewBitmap(R.id.widget_artwork, artwork)
        } else {
            views.setImageViewResource(R.id.widget_artwork, R.drawable.widget_artwork_rounded)
        }

        // 5. Progress Bar & Elapsed Time
        val durationMs = state.durationMs.coerceAtLeast(0L)
        val positionMs = state.positionMs.coerceIn(0L, durationMs.coerceAtLeast(1L))
        val progress = if (durationMs > 0L) {
            ((positionMs.toDouble() / durationMs.toDouble()) * 1000).toInt().coerceIn(0, 1000)
        } else 0
        views.setProgressBar(R.id.widget_progress, 1000, progress, false)

        val timeStr = if (durationMs > 0L) {
            "${formatDuration(positionMs)} / ${formatDuration(durationMs)}"
        } else {
            "0:00"
        }
        views.setTextViewText(R.id.widget_time, timeStr)

        // 6. Glowing Play/Pause Disc
        views.setImageViewBitmap(R.id.widget_btn_play_pause, playPauseBitmap)

        // 7. Favorite Button
        val favRes = if (state.isFavorite) R.drawable.ic_widget_heart_filled else R.drawable.ic_widget_heart
        views.setImageViewResource(R.id.widget_btn_favorite, favRes)

        // 8. PendingIntents
        views.setOnClickPendingIntent(
            R.id.widget_root,
            createActivityPendingIntent(context)
        )
        views.setOnClickPendingIntent(
            R.id.widget_btn_prev,
            createServicePendingIntent(context, PlaybackService.ACTION_PLAY_PREVIOUS_FROM_QUEUE, 101)
        )
        views.setOnClickPendingIntent(
            R.id.widget_btn_play_pause,
            createServicePendingIntent(context, PlaybackService.ACTION_TOGGLE_PLAY_PAUSE, 102)
        )
        views.setOnClickPendingIntent(
            R.id.widget_btn_next,
            createServicePendingIntent(context, PlaybackService.ACTION_PLAY_NEXT_FROM_QUEUE, 103)
        )
        views.setOnClickPendingIntent(
            R.id.widget_btn_favorite,
            createServicePendingIntent(context, PlaybackService.ACTION_TOGGLE_FAVORITE, 104)
        )

        return views
    }

    private fun buildCompactViews(
        context: Context,
        state: NowPlayingState,
        artwork: Bitmap?,
        theme: WidgetTheme,
        background: Bitmap,
        playPauseBitmap: Bitmap
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.vanta_widget_compact)

        views.setImageViewBitmap(R.id.widget_compact_bg, background)

        val hasTrack = !state.title.isNullOrBlank()
        views.setTextViewText(
            R.id.widget_compact_title,
            if (hasTrack) state.title else "VANTA Music"
        )
        val subtitle = if (hasTrack) {
            state.artist ?: "Unknown Artist"
        } else {
            "Select music to play"
        }
        views.setTextViewText(R.id.widget_compact_subtitle, subtitle)

        // Audio Quality Badge
        val (badgeText, badgeBgRes, badgeTextColor) = when {
            state.qualityInfo?.isSony360RealityAudio == true ->
                Triple("360", R.drawable.widget_badge_spatial, Color.parseColor("#FF38BDF8"))
            state.qualityInfo?.isDolbyAtmos == true ->
                Triple("ATMOS", R.drawable.widget_badge_atmos, Color.parseColor("#FFA78BFA"))
            state.qualityInfo?.isHiRes == true ->
                Triple("HI-RES", R.drawable.widget_badge_hires, Color.parseColor("#FFE5A93C"))
            else ->
                Triple("FLAC", R.drawable.widget_badge_flac, Color.parseColor("#FF34D399"))
        }
        views.setTextViewText(R.id.widget_compact_badge, badgeText)
        views.setInt(R.id.widget_compact_badge, "setBackgroundResource", badgeBgRes)
        views.setTextColor(R.id.widget_compact_badge, badgeTextColor)

        if (artwork != null && !artwork.isRecycled) {
            views.setImageViewBitmap(R.id.widget_compact_artwork, artwork)
        } else {
            views.setImageViewResource(R.id.widget_compact_artwork, R.drawable.widget_artwork_rounded)
        }

        views.setImageViewBitmap(R.id.widget_compact_btn_play_pause, playPauseBitmap)

        views.setOnClickPendingIntent(
            R.id.widget_compact_root,
            createActivityPendingIntent(context)
        )
        views.setOnClickPendingIntent(
            R.id.widget_compact_btn_prev,
            createServicePendingIntent(context, PlaybackService.ACTION_PLAY_PREVIOUS_FROM_QUEUE, 201)
        )
        views.setOnClickPendingIntent(
            R.id.widget_compact_btn_play_pause,
            createServicePendingIntent(context, PlaybackService.ACTION_TOGGLE_PLAY_PAUSE, 202)
        )
        views.setOnClickPendingIntent(
            R.id.widget_compact_btn_next,
            createServicePendingIntent(context, PlaybackService.ACTION_PLAY_NEXT_FROM_QUEUE, 203)
        )

        return views
    }

    private fun createActivityPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createServicePendingIntent(
        context: Context,
        action: String,
        requestCode: Int
    ): PendingIntent {
        val intent = Intent(context, PlaybackService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
