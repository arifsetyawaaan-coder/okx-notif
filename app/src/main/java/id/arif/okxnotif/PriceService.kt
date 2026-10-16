package id.arif.okxnotif

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** Foreground service: notifikasi ongoing berisi harga + PnL HYPE/ZEC, refresh berkala. */
class PriceService : Service() {

    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_REFRESH) {
            restartLoop()
            return START_STICKY
        }
        startForeground(NOTIF_ID, buildSimple("Menyiapkan…", "Mengambil harga OKX…"))
        restartLoop()
        return START_STICKY
    }

    private fun restartLoop() {
        job?.cancel()
        job = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                val rows = fetchRows()
                withContext(Dispatchers.Main) {
                    val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                    nm.notify(NOTIF_ID, buildRich(rows))
                }
                delay(Prefs.getIntervalSec(this@PriceService) * 1000L)
            }
        }
    }

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL, "Harga OKX", NotificationManager.IMPORTANCE_LOW)
            ch.description = "Harga dan PnL HYPE/ZEC"
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }

    private fun fmtPrice(x: Double): String = when {
        x >= 1000 -> "%,.0f".format(Locale.US, x)
        x >= 100 -> "%.2f".format(Locale.US, x)
        x >= 1 -> "%.4f".format(Locale.US, x).trimEnd('0').trimEnd('.')
        else -> "%.6f".format(Locale.US, x).trimEnd('0').trimEnd('.')
    }

    private fun line(sym: String, last: Double?, pos: Position): String {
        if (last == null) return sym + ": gagal ambil harga"
        val pnl = (last - pos.avg) * pos.qty
        val pct = (last - pos.avg) / pos.avg * 100
        val arrow = if (pct >= 0) "▲" else "▼"
        val price = fmtPrice(last)
        val pctS = "%+.1f".format(Locale.US, pct) + "%"
        val sign = if (pnl < 0) "-" else "+"
        val pnlS = sign + "$" + "%,.2f".format(Locale.US, kotlin.math.abs(pnl))
        return sym + " $" + price + " · " + arrow + " " + pctS + " · " + pnlS
    }

    private fun regime(chg: Double): String = when {
        chg >= 2 -> "Bull"
        chg <= -2 -> "Bear"
        else -> "Sideway"
    }

    private fun coinLine(c: CoinUi): String {
        val arrow = if ((c.pnlPct ?: c.chg) >= 0) "▲" else "▼"
        val left = c.sym + " $" + fmtPrice(c.px)
        val right = if (c.pnl != null && c.pnlPct != null) {
            val sign = if (c.pnl < 0) "-" else "+"
            arrow + " " + "%+.1f".format(Locale.US, c.pnlPct) + "% · " + sign + "$" +
                "%,.2f".format(Locale.US, kotlin.math.abs(c.pnl))
        } else {
            arrow + " " + "%+.1f".format(Locale.US, c.chg) + "%"
        }
        return left + " · " + right
    }

    private data class RowUi(
        val sym: String,
        val pxS: String,
        val pctS: String,
        val pctUp: Boolean,
        val spark: android.graphics.Bitmap?,
        val sparkUp: Boolean,
    )

    private fun fetchRows(): List<RowUi> {
        val data = ScreenApi.load(Prefs.getUrl(this), Prefs.getToken(this))
        if (data != null && data.coins.isNotEmpty()) {
            val out = mutableListOf<RowUi>()
            val coins = data.coins.filter { it.px > 1 }.take(2)
            for (c in coins) {
                val pct = c.pnlPct ?: c.chg
                val pctS = "%+.1f".format(Locale.US, pct) + "%"
                val pic = c.spark.takeIf { it.size >= 2 }?.let { renderSpark(it, pct >= 0) }
                out.add(RowUi(c.sym, "$" + fmtPrice(c.px), pctS, pct >= 0, pic, pct >= 0))
            }
            val bUp = data.btcChg >= 0
            val bSpark = OkxApi.spark30m("BTC-USDT")?.takeIf { it.size >= 2 }?.let { renderSpark(it, bUp) }
            out.add(RowUi("BTC", "$" + fmtPrice(data.btcPx),
                "%+.1f".format(Locale.US, data.btcChg) + "%", bUp, bSpark, bUp))
            return out
        }
        val out = mutableListOf<RowUi>()
        for (id in listOf("HYPE-USDT", "ZEC-USDT")) {
            val last = OkxApi.last(id)
            val chg = OkxApi.chg24(id) ?: 0.0
            val sp = OkxApi.spark30m(id)?.takeIf { it.size >= 2 }?.let { renderSpark(it, chg >= 0) }
            out.add(RowUi(id.substringBefore("-"), if (last != null) "$" + fmtPrice(last) else "-",
                "%+.1f".format(Locale.US, chg) + "%", chg >= 0, sp, chg >= 0))
        }
        val btc = OkxApi.last("BTC-USDT")
        val bChg = OkxApi.chg24("BTC-USDT") ?: 0.0
        val bSp = OkxApi.spark30m("BTC-USDT")?.takeIf { it.size >= 2 }?.let { renderSpark(it, bChg >= 0) }
        out.add(RowUi("BTC", if (btc != null) "$" + fmtPrice(btc) else "-",
            "%+.1f".format(Locale.US, bChg) + "%", bChg >= 0, bSp, bChg >= 0))
        return out
    }

    private fun openPending(): PendingIntent {
        val root = webUrl()
        return if (root.isNotBlank()) {
            PendingIntent.getActivity(
                this, 0, Intent(Intent.ACTION_VIEW, android.net.Uri.parse("$root/")),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        } else {
            PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }

    private fun refreshPending(): PendingIntent {
        val i = Intent(this, PriceService::class.java).setAction(ACTION_REFRESH)
        return PendingIntent.getService(
            this, 1, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun buildSimple(title: String, text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(id.arif.okxnotif.R.drawable.ic_stat_chart)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openPending())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .build()
    }

    private fun buildRich(rows: List<RowUi>): Notification {
        val title = rows.firstOrNull()?.let { it.sym + " " + it.pxS } ?: "Harga OKX"
        val collapsed = android.widget.RemoteViews(packageName, R.layout.notif_collapsed)
        collapsed.setTextViewText(R.id.colTitle, title)
        collapsed.setTextViewText(R.id.colText, rows.joinToString(" · ") { it.sym + " " + it.pctS })
        val expanded = android.widget.RemoteViews(packageName, R.layout.notif_expanded)
        val syms = intArrayOf(R.id.exSym1, R.id.exSym2, R.id.exSym3)
        val pxs = intArrayOf(R.id.exPx1, R.id.exPx2, R.id.exPx3)
        val pcts = intArrayOf(R.id.exPct1, R.id.exPct2, R.id.exPct3)
        val sparks = intArrayOf(R.id.exSpark1, R.id.exSpark2, R.id.exSpark3)
        val green = android.graphics.Color.parseColor("#22C55E")
        val red = android.graphics.Color.parseColor("#F43F5E")
        rows.take(3).forEachIndexed { i, r ->
            expanded.setTextViewText(syms[i], r.sym)
            expanded.setTextViewText(pxs[i], r.pxS)
            expanded.setTextViewText(pcts[i], r.pctS)
            expanded.setTextColor(pcts[i], if (r.pctUp) green else red)
            if (r.spark != null) expanded.setImageViewBitmap(sparks[i], r.spark)
            else expanded.setViewVisibility(sparks[i], android.view.View.GONE)
        }
        expanded.setOnClickPendingIntent(R.id.btnOpen, openPending())
        expanded.setOnClickPendingIntent(R.id.btnRefresh, refreshPending())
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(id.arif.okxnotif.R.drawable.ic_stat_chart)
            .setContentTitle(title)
            .setContentText(rows.joinToString(" · ") { it.sym + " " + it.pctS })
            .setCustomContentView(collapsed)
            .setCustomBigContentView(expanded)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setContentIntent(openPending())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .build()
    }
    private fun fetchLines(): Pair<String, String> {
        // Utama: JSON VPS (angka SAMA dengan Telegram/monitor). Cadangan: ticker langsung.
        val data = ScreenApi.load(Prefs.getUrl(this), Prefs.getToken(this))
        if (data != null && data.coins.isNotEmpty()) {
            val arrow = if (data.btcChg >= 0) "▲" else "▼"
            val title = "BTC $" + fmtPrice(data.btcPx) + " · " + arrow + " " +
                "%+.1f".format(Locale.US, data.btcChg) + "%"
            val show = data.coins.take(4)
            return title to show.joinToString("\n") { coinLine(it) }
        }
        val hype = OkxApi.last("HYPE-USDT")
        val zec = OkxApi.last("ZEC-USDT")
        val btc = OkxApi.last("BTC-USDT")
        val l1 = line("HYPE", hype, Prefs.getHype(this))
        val l2 = line("ZEC", zec, Prefs.getZec(this))
        val title = if (btc != null) {
            val btcChg = OkxApi.chg24("BTC-USDT") ?: 0.0
            val arrow = if (btcChg >= 0) "▲" else "▼"
            val chgS = "%+.1f".format(Locale.US, btcChg) + "%"
            "BTC $" + fmtPrice(btc) + " · " + arrow + " " + chgS
        } else "Harga OKX"
        val text = if (hype != null && zec != null) "$l1\n$l2" else "$l1\n$l2\n(periksa koneksi)"
        return title to text
    }

    private data class FetchResult(
        val title: String,
        val text: String,
        val spark: List<Double>?,
        val up: Boolean,
    )

    private fun renderSpark(data: List<Double>, up: Boolean): android.graphics.Bitmap {
        val w = 512
        val h = 256
        val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        val cv = android.graphics.Canvas(bmp)
        cv.drawColor(android.graphics.Color.parseColor("#141A24"))
        val mn = data.minOrNull() ?: 0.0
        val mx = data.maxOrNull() ?: 1.0
        val span = (mx - mn).takeIf { it > 0 } ?: 1.0
        fun yOf(v: Double): Float {
            return (h - 16 - ((v - mn) / span * (h - 32))).toFloat()
        }
        val col = if (up) "#22C55E" else "#F43F5E"
        val base = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor(col)
            alpha = 180
            strokeWidth = 3f
            pathEffect = android.graphics.DashPathEffect(floatArrayOf(12f, 9f), 0f)
        }
        val by = yOf(data.first())
        cv.drawLine(0f, by, w.toFloat(), by, base)
        val line = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor(col)
            strokeWidth = 6f
            style = android.graphics.Paint.Style.STROKE
            isAntiAlias = true
        }
        val path = android.graphics.Path()
        data.forEachIndexed { i, v ->
            val x = w.toFloat() * i / (data.size - 1)
            val y = yOf(v)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        cv.drawPath(path, line)
        return bmp
    }

    private fun webUrl(): String {
        val root = Prefs.getUrl(this).trim().trimEnd('/')
            .removeSuffix("/screening.json").trimEnd('/')
        return root
    }

    private fun build(title: String, text: String, pic: android.graphics.Bitmap?): Notification {
        val root = webUrl()
        val open = if (root.isNotBlank()) {
            PendingIntent.getActivity(
                this, 0, Intent(Intent.ACTION_VIEW, android.net.Uri.parse("$root/")),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        } else {
            PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val b = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(id.arif.okxnotif.R.drawable.ic_stat_chart)
            .setContentTitle(title)
            .setContentText(text.replace("\n", " · "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
        if (pic != null) {
            b.setStyle(NotificationCompat.BigPictureStyle().bigPicture(pic).setSummaryText(text))
        }
        return b.build()
    }

    companion object {
        const val CHANNEL = "okx_price"
        const val NOTIF_ID = 1001
        const val ACTION_REFRESH = "id.arif.okxnotif.REFRESH"
    }
}
