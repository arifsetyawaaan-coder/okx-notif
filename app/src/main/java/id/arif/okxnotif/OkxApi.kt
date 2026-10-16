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

    /** Naik-turun 24 jam (%) dari ticker OKX (butuh open24h yang tidak ada di Binance). */
    fun chg24(instId: String): Double? {
        val body = get(OKX + instId) ?: return null
        val last = Regex("\"last\"\\s*:\\s*\"([0-9.]+)\"").find(body)
            ?.groupValues?.get(1)?.toDoubleOrNull() ?: return null
        val open = Regex("\"open24h\"\\s*:\\s*\"([0-9.]+)\"").find(body)
            ?.groupValues?.get(1)?.toDoubleOrNull() ?: return null
        if (open == 0.0) return null
        return (last - open) / open * 100
    }

    /** 48 close 30m publik untuk BigPicture fallback tanpa VPS. */
    fun spark30m(instId: String): List<Double>? {
        val sym = instId.replace("-", "")
        get("https://data-api.binance.vision/api/v3/klines?symbol=$sym&interval=30m&limit=48")?.let { body ->
            val closes = Regex("\\[\\d+,\"[0-9.]+\",\"[0-9.]+\",\"[0-9.]+\",\"([0-9.]+)\"")
                .findAll(body).mapNotNull { it.groupValues.get(1).toDoubleOrNull() }
                .filter { it > 0 }.toList()
            if (closes.size >= 2) return closes
        }
        get("https://www.okx.com/api/v5/market/candles?instId=$instId&bar=30m&limit=48")?.let { body ->
            val closes = Regex("\"c\"\\s*:\\s*\"([0-9.]+)\"").findAll(body)
                .mapNotNull { it.groupValues.get(1).toDoubleOrNull() }
                .filter { it > 0 }.toList()
            if (closes.size < 2) {
                val rows = Regex("\\[\"[0-9.]+\",\"[0-9.]+\",\"[0-9.]+\",\"[0-9.]+\",\"([0-9.]+)\"")
                    .findAll(body).mapNotNull { it.groupValues.get(1).toDoubleOrNull() }
                    .filter { it > 0 }.toList()
                if (rows.size >= 2) return rows.reversed()
                return null
            }
            return closes.reversed()
        }
        return null
    }
}
