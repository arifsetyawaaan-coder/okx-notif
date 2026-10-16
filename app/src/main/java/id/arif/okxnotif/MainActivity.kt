package id.arif.okxnotif

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var eHypeAvg: EditText
    private lateinit var eHypeQty: EditText
    private lateinit var eZecAvg: EditText
    private lateinit var eZecQty: EditText
    private lateinit var eInterval: EditText
    private lateinit var btnToggle: Button
    private lateinit var tvState: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        fun label(s: String) = TextView(this).apply { text = s; root.addView(this) }
        fun field(v: String) = EditText(this).apply {
            setText(v); root.addView(this)
        }
        label("HYPE avg / qty")
        eHypeAvg = field(Prefs.getHype(this).avg.toString())
        eHypeQty = field(Prefs.getHype(this).qty.toString())
        label("ZEC avg / qty")
        eZecAvg = field(Prefs.getZec(this).avg.toString())
        eZecQty = field(Prefs.getZec(this).qty.toString())
        label("Refresh detik (30-900)")
        eInterval = field(Prefs.getIntervalSec(this).toString())
        tvState = TextView(this)
        root.addView(tvState)
        val btnSave = Button(this).apply { text = "Simpan" }
        btnToggle = Button(this)
        root.addView(btnSave)
        root.addView(btnToggle)
        setContentView(root)

        btnSave.setOnClickListener {
            Prefs.save(this, eHypeAvg.text.toString(), eHypeQty.text.toString(),
                eZecAvg.text.toString(), eZecQty.text.toString(), eInterval.text.toString())
            Toast.makeText(this, "Tersimpan", Toast.LENGTH_SHORT).show()
        }
        btnToggle.setOnClickListener {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
                return@setOnClickListener
            }
            toggle()
        }
        refreshUi()
    }

    private fun toggle() {
        val intent = Intent(this, PriceService::class.java)
        if (Prefs.isRunning(this)) {
            stopService(intent)
            Prefs.setRunning(this, false)
        } else {
            Prefs.save(this, eHypeAvg.text.toString(), eHypeQty.text.toString(),
                eZecAvg.text.toString(), eZecQty.text.toString(), eInterval.text.toString())
            ContextCompat.startForegroundService(this, intent)
            Prefs.setRunning(this, true)
        }
        refreshUi()
    }

    private fun refreshUi() {
        val on = Prefs.isRunning(this)
        btnToggle.text = if (on) "Matikan notifikasi" else "Nyalakan notifikasi"
        tvState.text = if (on) "Status: notifikasi aktif" else "Status: mati"
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && Prefs.isRunning(ctx)) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, PriceService::class.java))
        }
    }
}
