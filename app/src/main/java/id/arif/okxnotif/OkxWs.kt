package id.arif.okxnotif

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Snapshot harga live via OKX WebSocket publik — jalur cadangan bila JSON VPS tak terjangkau. */
object OkxWs {
    private const val WS_URL = "wss://ws.okx.com:8443/ws/v5/public"
    private const val TIMEOUT_SEC = 12L

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    data class Tick(val last: Double, val open24h: Double)

    /** Ambil satu tick ticker terakhir per instId. Null bila tak ada data sama sekali. */
    fun snapshot(instIds: List<String>): Map<String, Tick>? {
        val got = ConcurrentHashMap<String, Tick>()
        val latch = CountDownLatch(1)
        val done = AtomicBoolean(false)
        fun finish() {
            if (done.compareAndSet(false, true)) latch.countDown()
        }
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val args = instIds.joinToString(",") {
                    "{\"channel\":\"tickers\",\"instId\":\"$it\"}"
                }
                webSocket.send("{\"op\":\"subscribe\",\"args\":[$args]}")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val o = JSONObject(text)
                    if ("error" == o.optString("event")) {
                        finish()
                        return
                    }
                    val arg = o.optJSONObject("arg") ?: return
                    if (arg.optString("channel") != "tickers") return
                    val inst = arg.optString("instId")
                    val d = o.optJSONArray("data")?.optJSONObject(0) ?: return
                    val last = d.optString("last").toDoubleOrNull() ?: return
                    val open = d.optString("open24h").toDoubleOrNull() ?: return
                    if (last <= 0 || open <= 0) return
                    if (got.putIfAbsent(inst, Tick(last, open)) == null &&
                        got.size >= instIds.size
                    ) {
                        finish()
                    }
                } catch (_: Exception) {
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                finish()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                finish()
            }
        }
        val ws = try {
            client.newWebSocket(Request.Builder().url(WS_URL).build(), listener)
        } catch (_: Exception) {
            return null
        }
        try {
            latch.await(TIMEOUT_SEC, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
        }
        ws.close(1000, "done")
        return if (got.isEmpty()) null else HashMap(got)
    }
}
