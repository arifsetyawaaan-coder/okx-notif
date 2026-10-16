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

    private fun line(sym: String, last: Double?, pos: Position): String {
        if (last == null) return "$sym: gagal ambil harga"
        val pnl = (last - pos.avg) * pos.qty
        val pct = (last - pos.avg) / pos.avg * 100
        val arrow = if (pct >= 0) "▲" else "▼"
        val price = if (last >= 1000) "%,.2f".format(last) else "%.4f".format(last).trimEnd('0').trimEnd('.')
        return "$sym $$$price  $arrow ${"%+.1f".format(pct)}% (${"%+.2f".format(pnl)})"
    }

    private fun fetchLines(): Pair<String, String> {
        val hype = OkxApi.last("HYPE-USDT")
        val zec = OkxApi.last("ZEC-USDT")
        val l1 = line("HYPE", hype, Prefs.getHype(this))
        val l2 = line("ZEC", zec, Prefs.getZec(this))
        val title = if (hype != null && zec != null) "HYPE/ZEC — OKX" else "OKX — periksa koneksi"
        return title to "$l1\n$l2"
    }

    private fun build(title: String, text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
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
