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
        startForeground(NOTIF_ID, build("Menyiapkan…", "Mengambil harga OKX…"))
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
        val arrow = if (c.chg >= 0) "▲" else "▼"
        val left = c.sym + " $" + fmtPrice(c.px)
        val mid = arrow + " " + "%+.1f".format(Locale.US, c.chg) + "%"
        return if (c.pnl != null) {
            val sign = if (c.pnl < 0) "-" else "+"
            left + " · " + mid + " · " + sign + "$" +
                "%,.2f".format(Locale.US, kotlin.math.abs(c.pnl))
        } else {
            left + " · " + mid
        }
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

    private fun build(title: String, text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(id.arif.okxnotif.R.drawable.ic_stat_chart)
            .setContentTitle(title)
            .setContentText(text.replace("\n", " · "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
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
            val total = data.coins.mapNotNull { it.pnl }.sum()
            val title = "Total PnL " + (if (total < 0) "-" else "+") + "$" +
                "%,.2f".format(Locale.US, kotlin.math.abs(total))
            val bArrow = if (data.btcChg >= 0) "▲" else "▼"
            val btcRow = "BTC $" + fmtPrice(data.btcPx) + " · " + bArrow + " " +
                "%+.1f".format(Locale.US, data.btcChg) + "%"
            val show = data.coins.filter { it.px > 1 }.take(2)
            return title to ([btcRow] + show.map { coinLine(it) }).joinToString("\n")
        }
        val hype = OkxApi.last("HYPE-USDT")
        val zec = OkxApi.last("ZEC-USDT")
        val btc = OkxApi.last("BTC-USDT")
        val btcChg = OkxApi.chg24("BTC-USDT") ?: 0.0
        val hChg = OkxApi.chg24("HYPE-USDT") ?: 0.0
        val zChg = OkxApi.chg24("ZEC-USDT") ?: 0.0
        val hPnl = if (hype != null) (hype - Prefs.getHype(this).avg) * Prefs.getHype(this).qty else null
        val zPnl = if (zec != null) (zec - Prefs.getZec(this).avg) * Prefs.getZec(this).qty else null
        val total = listOfNotNull(hPnl, zPnl).sum()
        val title = "Total PnL " + (if (total < 0) "-" else "+") + "$" +
            "%,.2f".format(Locale.US, kotlin.math.abs(total))
        fun row(sym: String, last: Double?, chg: Double, pnl: Double?): String {
            val arrow = if (chg >= 0) "▲" else "▼"
            val left = if (last != null) sym + " $" + fmtPrice(last) else sym + " -"
            val mid = arrow + " " + "%+.1f".format(Locale.US, chg) + "%"
            return if (pnl != null) {
                val sign = if (pnl < 0) "-" else "+"
                left + " · " + mid + " · " + sign + "$" +
                    "%,.2f".format(Locale.US, kotlin.math.abs(pnl))
            } else left + " · " + mid
        }
        val bArrow = if (btcChg >= 0) "▲" else "▼"
        val bRow = if (btc != null) "BTC $" + fmtPrice(btc) + " · " + bArrow + " " +
            "%+.1f".format(Locale.US, btcChg) + "%" else "BTC -"
        val text = listOf(bRow, row("HYPE", hype, hChg, hPnl), row("ZEC", zec, zChg, zPnl)).joinToString("\n")
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

    private fun build(title: String, text: String, pic: android.graphics.Bitmap? = null): Notification {
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
