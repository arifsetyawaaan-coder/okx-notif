package id.arif.okxnotif

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Widget watchlist ala OKX: 9 koin + sparkline + chg, data publik OKX (tanpa auth). */
class WatchlistWidget : AppWidgetProvider {

    companion object {
        val COINS = listOf("BTC", "HYPE", "NEAR", "PUMP", "ARB", "ASTER", "UNI", "JTO", "SOL")
        val COLORS = mapOf(
            "BTC" to "#F7931A", "HYPE" to "#4DD8D8", "NEAR" to "#00C08B",
            "PUMP" to "#00D1A1", "ARB" to "#28A0F0", "ASTER" to "#E8B84B",
            "UNI" to "#FF007A", "JTO" to "#AB9FF2", "SOL" to "#9945FF")

        data class Row(val sym: String, val px: Double, val chg: Double, val spark: List<Double>)

        fun fetch(sym: String): Row? {
            return try {
                val t = get("https://www.okx.com/api/v5/market/ticker?instId=$sym-USDT")
                    .getJSONArray("data").getJSONObject(0)
                val px = t.getString("last").toDouble()
                val chg = (px - t.getString("open24h").toDouble()) / t.getString("open24h").toDouble() * 100
                val c = get("https://www.okx.com/api/v5/market/candles?instId=$sym-USDT&bar=1H&limit=30")
                    .getJSONArray("data")
                val closes = (0 until c.length()).map { c.getJSONArray(it).getString(4).toDouble() }.reversed()
                Row(sym, px, chg, closes)
            } catch (_: Exception) { null }
        }

        private fun get(u: String): JSONObject {
            val conn = (URL(u).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000; readTimeout = 15000
                setRequestProperty("User-Agent", "okx-notif-widget/1.0")
            }
            return try { JSONObject(conn.inputStream.bufferedReader().readText()) }
            finally { conn.disconnect() }
        }

        fun sparkBitmap(data: List<Double>, up: Boolean, w: Int = 220, h: Int = 90): Bitmap {
            val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val cv = Canvas(b)
            val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor(if (up) "#22C55E" else "#EF4444")
                strokeWidth = 4f; style = Paint.Style.STROKE
            }
            val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor(if (up) "#226B3E" else "#7A2E2E")
                strokeWidth = 2f; style = Paint.Style.STROKE
                pathEffect = DashPathEffect(floatArrayOf(8f, 6f), 0f)
            }
            if (data.size >= 2) {
                val mn = data.min(); val mx = data.max(); val rg = (mx - mn).ifZero(1.0)
                val pts = data.mapIndexed { i, v ->
                    (i.toFloat() / (data.size - 1) * w) to (h - 6 - ((v - mn) / rg * (h - 12)).toFloat())
                }
                for (i in 0 until pts.size - 1)
                    cv.drawLine(pts[i].first, pts[i].second, pts[i + 1].first, pts[i + 1].second, line)
                val midY = h - 6 - (((data.first() - mn) / rg * (h - 12))).toFloat()
                cv.drawLine(0f, midY, w.toFloat(), midY, dash)
            }
            return b
        }

        fun tileBitmap(sym: String, size: Int = 96): Bitmap {
            val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val cv = Canvas(b)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor(COLORS[sym] ?: "#888888")
            }
            cv.drawCircle(size / 2f, size / 2f, size / 2f, p)
            val t = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE; textSize = size * 0.42f; textAlign = Paint.Align.CENTER
            }
            cv.drawText(sym.take(1), size / 2f, size / 2f + size * 0.15f, t)
            return b
        }

        fun fmtPx(x: Double) = when {
            x >= 1000 -> "%,.1f".format(x)
            x >= 100 -> "%,.2f".format(x)
            x >= 1 -> "%,.4f".format(x).trimEnd('0')
            else -> x.toString()
        }

        private fun Double.ifZero(d: Double) = if (this == 0.0) d else this
    }

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) {
            val v = RemoteViews(ctx.packageName, R.layout.widget_watchlist)
            v.removeAllViews(R.id.widget_list)
            val loading = RemoteViews(ctx.packageName, R.layout.widget_row)
            loading.setTextViewText(R.id.row_sym, "⏳ memuat watchlist…")
            v.addView(R.id.widget_list, loading)
            val open = PendingIntent.getActivity(ctx, 0,
                Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            v.setOnClickPendingIntent(R.id.widget_root, open)
            mgr.updateAppWidget(id, v)
        }
        CoroutineScope(Dispatchers.IO).launch {
            val rows = COINS.mapNotNull { fetch(it) }
            CoroutineScope(Dispatchers.Main).launch {
                val m = AppWidgetManager.getInstance(ctx)
                for (id in ids) {
                    val v = RemoteViews(ctx.packageName, R.layout.widget_watchlist)
                    v.removeAllViews(R.id.widget_list)
                    if (rows.isEmpty()) {
                        val e = RemoteViews(ctx.packageName, R.layout.widget_row)
                        e.setTextViewText(R.id.row_sym, "⚠️ gagal muat — cek internet")
                        v.addView(R.id.widget_list, e)
                    } else for (r in rows) {
                        val row = RemoteViews(ctx.packageName, R.layout.widget_row)
                        row.setImageViewBitmap(R.id.row_icon, tileBitmap(r.sym))
                        row.setTextViewText(R.id.row_sym, r.sym)
                        val up = r.chg >= 0
                        row.setImageViewBitmap(R.id.row_spark, sparkBitmap(r.spark, up))
                        row.setTextViewText(R.id.row_px, fmtPx(r.px))
                        row.setTextViewText(R.id.row_chg,
                            "${if (up) "+" else ""}${"%.2f".format(r.chg)}%")
                        row.setTextColor(R.id.row_chg,
                            Color.parseColor(if (up) "#22C55E" else "#EF4444"))
                        v.addView(R.id.widget_list, row)
                    }
                    val open = PendingIntent.getActivity(ctx, 0,
                        Intent(ctx, MainActivity::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    v.setOnClickPendingIntent(R.id.widget_root, open)
                    m.updateAppWidget(id, v)
                }
            }
        }
    }
}
