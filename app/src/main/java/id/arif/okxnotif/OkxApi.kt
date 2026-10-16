package id.arif.okxnotif

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Harga spot publik — Binance Vision dulu (lolos blokir ISP ID), OKX sebagai cadangan. */
object OkxApi {
    private const val BINANCE = "https://data-api.binance.vision/api/v3/ticker/price?symbol="
    private const val OKX = "https://www.okx.com/api/v5/market/ticker?instId="

    private fun get(url: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 15000
                setRequestProperty("User-Agent", "okx-notif/1.0")
            }
            if (conn.responseCode != 200) return null
            conn.inputStream.bufferedReader().readText()
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    fun last(instId: String): Double? {
        // instId format "HYPE-USDT" -> simbol Binance "HYPEUSDT"
        val sym = instId.replace("-", "")
        get(BINANCE + sym)?.let { body ->
            val m = Regex("\"price\"\\s*:\\s*\"([0-9.]+)\"").find(body)
            val v = m?.groupValues?.get(1)?.toDoubleOrNull()
            if (v != null && v > 0) return v
        }
        get(OKX + instId)?.let { body ->
            val m = Regex("\"last\"\\s*:\\s*\"([0-9.]+)\"").find(body)
            val v = m?.groupValues?.get(1)?.toDoubleOrNull()
            if (v != null && v > 0) return v
        }
        return null
    }
}
