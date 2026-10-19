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
        prefs(ctx).getString("interval", "10")!!.toLongOrNull()?.coerceIn(10, 60) ?: 10L
    fun getUrl(ctx: Context): String = prefs(ctx).getString("api_url", "") ?: ""
    fun getToken(ctx: Context): String = prefs(ctx).getString("api_token", "") ?: ""
    fun isRunning(ctx: Context): Boolean = prefs(ctx).getBoolean("running", false)

    /** Cache koin terakhir dari VPS (sym,avg,qty) untuk fallback WS. */
    fun saveCachedCoins(ctx: Context, coins: List<Triple<String, Double, Double>>) {
        prefs(ctx).edit().putString("cached_coins",
            coins.joinToString(";") { "${it.first},${it.second},${it.third}" }).apply()
    }
    fun getCachedCoins(ctx: Context): List<Triple<String, Double, Double>> =
        (prefs(ctx).getString("cached_coins", "") ?: "").split(";").mapNotNull {
            val p = it.split(",")
            if (p.size == 3) {
                val q = p[1].toDoubleOrNull(); val w = p[2].toDoubleOrNull()
                if (q != null && w != null) Triple(p[0], q, w) else null
            } else null
        }

    fun saveAdv(ctx: Context, url: String, token: String, interval: String) {
        prefs(ctx).edit().putString("api_url", url.trim()).putString("api_token", token.trim())
            .putString("interval", interval).apply()
    }
    fun setRunning(ctx: Context, v: Boolean) {
        prefs(ctx).edit().putBoolean("running", v).apply()
    }
}
