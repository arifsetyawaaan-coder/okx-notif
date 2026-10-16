package id.arif.okxnotif

import android.content.Context
import android.content.SharedPreferences

/** Posisi spot milik Arif. Default dari monitor VPS (Okt 2026) — bisa diubah di layar utama. */
data class Position(val avg: Double, val qty: Double)

object Prefs {
    private const val FILE = "okx_notif"
    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun getHype(ctx: Context) = Position(
        avg = prefs(ctx).getString("hype_avg", "87.208")!!.toDoubleOrNull() ?: 87.208,
        qty = prefs(ctx).getString("hype_qty", "0.6302")!!.toDoubleOrNull() ?: 0.6302,
    )
    fun getZec(ctx: Context) = Position(
        avg = prefs(ctx).getString("zec_avg", "1405.53")!!.toDoubleOrNull() ?: 1405.53,
        qty = prefs(ctx).getString("zec_qty", "0.03555")!!.toDoubleOrNull() ?: 0.03555,
    )
    fun getIntervalSec(ctx: Context): Long =
        prefs(ctx).getString("interval", "60")!!.toLongOrNull()?.coerceIn(30, 900) ?: 60L
    fun isRunning(ctx: Context): Boolean = prefs(ctx).getBoolean("running", false)

    fun save(ctx: Context, hypeAvg: String, hypeQty: String, zecAvg: String, zecQty: String, interval: String) {
        prefs(ctx).edit()
            .putString("hype_avg", hypeAvg).putString("hype_qty", hypeQty)
            .putString("zec_avg", zecAvg).putString("zec_qty", zecQty)
            .putString("interval", interval)
            .apply()
    }
    fun setRunning(ctx: Context, v: Boolean) {
        prefs(ctx).edit().putBoolean("running", v).apply()
    }
}
