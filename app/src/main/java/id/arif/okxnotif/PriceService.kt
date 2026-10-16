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

    private fun fetchLines(): Pair<String, String> {
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

    private fun build(title: String, text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(id.arif.okxnotif.R.drawable.ic_stat_chart)
            .setContentTitle(title)
            .setContentText(text.replace("\n", " · "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .build()
    }

    companion object {
        const val CHANNEL = "okx_price"
        const val NOTIF_ID = 1001
    }
}
