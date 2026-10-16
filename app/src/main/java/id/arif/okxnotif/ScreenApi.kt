package id.arif.okxnotif

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Satu baris data dari screening.json VPS (display murni, tanpa hitung). */
data class CoinUi(
    val sym: String,
    val px: Double,
    val chg: Double,
    val ratio: Double,
    val rsi: Double,
    val funding: Double?,
    val avg: Double?,
    val qty: Double?,
    val pnl: Double?,
    val pnlPct: Double?,
    val tp: Double?,
    val tpDist: Double?,
    val spark: List<Double>,
)

data class ScreenData(val wib: String, val btcPx: Double, val btcChg: Double, val coins: List<CoinUi>)

object ScreenApi {
    var lastError: String = ""
    fun load(baseUrl: String, token: String): ScreenData? {
        if (baseUrl.isBlank()) {
            lastError = "URL kosong — isi di Setting"
            return null
        }
        var conn: HttpURLConnection? = null
        return try {
            var root = baseUrl.trim().trimEnd('/')
            if (root.endsWith("/screening.json")) root = root.removeSuffix("/screening.json")
            val url = "$root/screening.json"
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20000
                readTimeout = 20000
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("User-Agent", "okx-notif/1.0")
            }
            val code = conn.responseCode
            if (code == 401) {
                lastError = "Token ditolak (401) — cek token di Setting"
                return null
            }
            if (code == 404) {
                lastError = "URL salah (404)"
                return null
            }
            if (code != 200) {
                lastError = "Server jawab $code"
                return null
            }
            parse(JSONObject(conn.inputStream.bufferedReader().readText()))
        } catch (_: java.net.SocketTimeoutException) {
            lastError = "Timeout — cek koneksi/WARP"
            null
        } catch (_: Exception) {
            lastError = "Koneksi gagal — cek URL + internet"
            null
        } finally {
            conn?.disconnect()
        }
    }

    fun parse(o: JSONObject): ScreenData {
        val coins = mutableListOf<CoinUi>()
        val arr = o.optJSONArray("coins") ?: JSONObject().let { org.json.JSONArray() }
        for (i in 0 until arr.length()) {
            val c = arr.optJSONObject(i) ?: continue
            if (c.has("error")) continue
            val sp = c.optJSONArray("spark") ?: org.json.JSONArray()
            val spark = (0 until sp.length()).mapNotNull {
                try { sp.getDouble(it) } catch (_: Exception) { null }
            }
            coins.add(CoinUi(
                sym = c.optString("sym"), px = c.optDouble("px"), chg = c.optDouble("chg24"),
                ratio = c.optDouble("ratio"), rsi = c.optDouble("rsi4h"),
                funding = if (c.isNull("funding")) null else c.optDouble("funding"),
                avg = if (c.isNull("avg")) null else c.optDouble("avg"),
                qty = if (c.isNull("qty")) null else c.optDouble("qty"),
                pnl = if (c.isNull("pnl")) null else c.optDouble("pnl"),
                pnlPct = if (c.isNull("pnlPct")) null else c.optDouble("pnlPct"),
                tp = if (c.isNull("tp")) null else c.optDouble("tp"),
                tpDist = if (c.isNull("tpDist")) null else c.optDouble("tpDist"),
                spark = spark,
            ))
        }
        val btc = o.optJSONObject("btc")
        return ScreenData(
            wib = o.optString("wib"),
            btcPx = btc?.optDouble("px") ?: 0.0,
            btcChg = btc?.optDouble("chg24") ?: 0.0,
            coins = coins,
        )
    }
}
