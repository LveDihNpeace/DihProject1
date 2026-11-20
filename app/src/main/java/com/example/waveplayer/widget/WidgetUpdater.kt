package com.example.waveplayer.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.widget.RemoteViews
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.waveplayer.MainActivity
import com.example.waveplayer.PlaybackService
import com.example.waveplayer.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Last known player state, persisted so the widget can render even when the player service is not running. */
object WidgetState {
    data class State(val title: String, val artist: String, val artUri: String?, val playing: Boolean, val hasQueue: Boolean)

    private const val PREFS = "widget_state"

    fun save(ctx: Context, s: State) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("title", s.title).putString("artist", s.artist).putString("art", s.artUri)
            .putBoolean("playing", s.playing).putBoolean("queue", s.hasQueue).apply()
    }

    fun load(ctx: Context): State {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return State(
            p.getString("title", "") ?: "", p.getString("artist", "") ?: "", p.getString("art", null),
            p.getBoolean("playing", false), p.getBoolean("queue", false),
        )
    }
}

object WidgetUpdater {
    const val ACTION_PLAY_PAUSE = "com.example.waveplayer.action.PLAY_PAUSE"
    const val ACTION_NEXT = "com.example.waveplayer.action.NEXT"
    const val ACTION_PREV = "com.example.waveplayer.action.PREV"
    private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    private fun serviceIntent(ctx: Context, action: String, code: Int): PendingIntent =
        PendingIntent.getService(ctx, code, Intent(ctx, PlaybackService::class.java).setAction(action), FLAGS)

    private fun openAppIntent(ctx: Context): PendingIntent =
        PendingIntent.getActivity(
            ctx, 100,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            FLAGS,
        )

    suspend fun update(context: Context) = withContext(Dispatchers.Default) {
        val ctx = context.applicationContext
        val mgr = AppWidgetManager.getInstance(ctx)
        val ids = mgr.getAppWidgetIds(ComponentName(ctx, PlayerWidgetProvider::class.java))
        if (ids.isEmpty()) return@withContext

        val st = WidgetState.load(ctx)
        val art = st.artUri?.let { loadArt(ctx, it) }
        val open = openAppIntent(ctx)

        val v = RemoteViews(ctx.packageName, R.layout.widget_player)
        v.setTextViewText(R.id.w_title, st.title.ifBlank { "WavePlayer" })
        v.setTextViewText(R.id.w_artist, if (st.hasQueue) st.artist else "Ketuk untuk memilih lagu")
        if (art != null) v.setImageViewBitmap(R.id.w_art, art) else v.setImageViewResource(R.id.w_art, R.drawable.ic_widget_art)
        v.setImageViewResource(R.id.w_play, if (st.playing) R.drawable.ic_pause else R.drawable.ic_play)

        v.setOnClickPendingIntent(R.id.w_root, open)
        v.setOnClickPendingIntent(R.id.w_art, open)
        // Transport buttons control the one player in PlaybackService; with nothing queued they open the app instead.
        v.setOnClickPendingIntent(R.id.w_prev, if (st.hasQueue) serviceIntent(ctx, ACTION_PREV, 1) else open)
        v.setOnClickPendingIntent(R.id.w_play, if (st.hasQueue) serviceIntent(ctx, ACTION_PLAY_PAUSE, 2) else open)
        v.setOnClickPendingIntent(R.id.w_next, if (st.hasQueue) serviceIntent(ctx, ACTION_NEXT, 3) else open)

        mgr.updateAppWidget(ids, v)
    }

    private suspend fun loadArt(ctx: Context, uri: String): Bitmap? = runCatching {
        val req = ImageRequest.Builder(ctx).data(uri).size(256).allowHardware(false).build()
        val drawable = (ctx.imageLoader.execute(req) as? SuccessResult)?.drawable ?: return@runCatching null
        rounded(drawable.toBitmap(256, 256), 36f)
    }.getOrNull()

    private fun rounded(src: Bitmap, radius: Float): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        c.drawRoundRect(RectF(0f, 0f, src.width.toFloat(), src.height.toFloat()), radius, radius, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        c.drawBitmap(src, 0f, 0f, paint)
        return out
    }
}
