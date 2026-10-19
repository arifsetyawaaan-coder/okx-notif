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
        startForeground(NOTIF_ID, build(titleNow(), "Mengambil harga OKX…"))
        job?.cancel()
        job = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                val (title, text) = fetchLines()
                withContext(Dispatchers.Main) {
                    val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                    nm.notify(NOTIF_ID, build(title, text))
                }
                delay(Prefs.getIntervalSec(this@PriceService) * 1000L)
            }
        }
        return START_STICKY
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

    /** Judul notif: "OKX Asset • HH:mm:ss WIB" — waktu update terakhir (jam perangkat, zona WIB). */
    private fun titleNow(): String {
        val f = java.text.SimpleDateFormat("HH:mm:ss", Locale.US)
        f.timeZone = java.util.TimeZone.getTimeZone("Asia/Jakarta")
        return "OKX Asset • " + f.format(java.util.Date()) + " WIB"
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

    /** Baris koin: aset aktif tampilkan persen PnL; tanpa posisi tampilkan chg24. */
    private fun coinLine(c: CoinUi): String {
        val left = c.sym + " $" + fmtPrice(c.px)
        val pnl = c.pnl
        val pct = c.pnlPct
        if (pnl != null && pct != null) {
            val arrow = if (pnl < 0) "▼" else "▲"
            val sign = if (pnl < 0) "-" else "+"
            return left + " · " + arrow + " " + "%+.2f".format(Locale.US, pct) + "%" +
                " · " + sign + "$" + "%,.2f".format(Locale.US, kotlin.math.abs(pnl))
        }
        val arrow = if (c.chg >= 0) "▲" else "▼"
        return left + " · " + arrow + " " + "%+.1f".format(Locale.US, c.chg) + "%"
    }

    /** Baris total di atas BTC: "Total PnL · ▲ +x.xx% · ±$x.xx". */
    private fun totalLine(total: Double, pct: Double): String {
        val arrow = if (pct < 0) "▼" else "▲"
        val sign = if (total < 0) "-" else "+"
        return "Total PnL · " + arrow + " " + "%+.2f".format(Locale.US, pct) + "%" +
            " · " + sign + "$" + "%,.2f".format(Locale.US, kotlin.math.abs(total))
    }

    private fun openPending(): PendingIntent {
        val root = webUrl()
        return if (root.isNotBlank()) {
            PendingIntent.getActivity(
                this, 0, Intent(Intent.ACTION_VIEW, android.net.Uri.parse("$root/?refresh=1")),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        } else {
            PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }

    private fun build(title: String, text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(id.arif.okxnotif.R.drawable.ic_stat_chart)
            .setContentTitle(title)
            .setContentText(text.substringBefore("\n"))
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openPending())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .build()
    }
    private fun fetchLines(): Pair<String, String> {
        // Utama: notif.json VPS (ringan; VPS refresh via OKX CLI tiap app minta). Cadangan: OKX WebSocket publik.
        val data = ScreenApi.load(Prefs.getUrl(this), Prefs.getToken(this))
        if (data != null && data.coins.isNotEmpty()) {
            Prefs.saveCachedCoins(this, data.coins.mapNotNull {
                if (it.avg != null && it.qty != null) Triple(it.sym, it.avg, it.qty) else null
            })
            val pos = data.coins.filter { it.pnl != null }
            val total = pos.mapNotNull { it.pnl }.sum()
            val cost = pos.map { (it.avg ?: 0.0) * (it.qty ?: 0.0) }.sum()
            val totPct = if (cost > 0) total / cost * 100 else 0.0
            val totalRow = totalLine(total, totPct)
            val bArrow = if (data.btcChg >= 0) "▲" else "▼"
            val btcRow = "BTC $" + fmtPrice(data.btcPx) + " · " + bArrow + " " +
                "%+.2f".format(Locale.US, data.btcChg) + "%"
            val show = data.coins.filter { it.px > 1 }
            val text = (listOf(totalRow, btcRow) + show.map { coinLine(it) }).joinToString("\n")
            return titleNow() to text
        }
        // Cadangan: OKX WebSocket publik + cache koin terakhir — hanya jalur OKX.
        val cached = Prefs.getCachedCoins(this)
        val syms = (listOf("BTC") + cached.map { it.first }).distinct()
        val snap = OkxWs.snapshot(syms.map { "$it-USDT" })
        if (snap == null) {
            val why = ScreenApi.lastError.ifBlank { "VPS & WS tak terjangkau" }
            return titleNow() to "Menunggu data OKX · " + why
        }
        val btc = snap["BTC-USDT"]
        fun posRow(sym: String, t: OkxWs.Tick?, pos: Position): String {
            if (t == null) return sym + " —"
            val pnl = (t.last - pos.avg) * pos.qty
            val pct = if (pos.avg > 0) (t.last - pos.avg) / pos.avg * 100 else 0.0
            val arrow = if (pnl < 0) "▼" else "▲"
            val sign = if (pnl < 0) "-" else "+"
            return sym + " $" + fmtPrice(t.last) + " · " + arrow + " " +
                "%+.2f".format(Locale.US, pct) + "%" + " · " + sign + "$" +
                "%,.2f".format(Locale.US, kotlin.math.abs(pnl))
        }
        val rows = cached.map { (sym, avg, qty) -> posRow(sym, snap["$sym-USDT"], Position(avg, qty)) }
        val pnls = cached.mapNotNull { (sym, avg, qty) ->
            snap["$sym-USDT"]?.let { (it.last - avg) * qty }
        }
        val total = pnls.sum()
        val cost = cached.sumOf { it.second * it.third }
        val totPct = if (cost > 0) total / cost * 100 else 0.0
        val totalRow = totalLine(total, totPct)
        val bRow = if (btc != null) {
            val chg = (btc.last - btc.open24h) / btc.open24h * 100
            "BTC $" + fmtPrice(btc.last) + " · " + (if (chg >= 0) "▲" else "▼") + " " +
                "%+.2f".format(Locale.US, chg) + "%"
        } else {
            "BTC —"
        }
        val text = (listOf(totalRow, bRow) + rows).joinToString("\n")
        return titleNow() to text
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
            .removeSuffix("/screening.json").removeSuffix("/notif.json").trimEnd('/')
        return root
    }

    private fun build(title: String, text: String, pic: android.graphics.Bitmap? = null): Notification {
        val root = webUrl()
        val open = if (root.isNotBlank()) {
            PendingIntent.getActivity(
                this, 0, Intent(Intent.ACTION_VIEW, android.net.Uri.parse("$root/?refresh=1")),
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
