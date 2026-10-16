package id.arif.okxnotif

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** REST publik OKX — tanpa API key. */
object OkxApi {
    private const val BASE = "https://www.okx.com/api/v5/market/ticker?instId="

    fun last(instId: String): Double? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(BASE + instId).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 15000
                setRequestProperty("User-Agent", "okx-notif/1.0")
            }
            if (conn.responseCode != 200) return null
            val body = conn.inputStream.bufferedReader().readText()
            val arr = JSONObject(body).optJSONArray("data") ?: return null
            if (arr.length() == 0) return null
            arr.getJSONObject(0).optString("last").toDoubleOrNull()
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}
