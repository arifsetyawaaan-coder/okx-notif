package id.arif.okxnotif

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Widget layar utama: BTC + koin aktif + PnL, ketuk untuk buka app. */
class SpotWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) render(ctx, mgr, id, loading = true)
        CoroutineScope(Dispatchers.IO).launch {
            val d = try {
                ScreenApi.load(Prefs.getUrl(ctx), Prefs.getToken(ctx))
            } catch (_: Exception) { null }
            CoroutineScope(Dispatchers.Main).launch {
                val m = AppWidgetManager.getInstance(ctx)
                for (id in ids) render(ctx, m, id, loading = false, d)
            }
        }
    }

    companion object {
        fun render(ctx: Context, mgr: AppWidgetManager, id: Int, loading: Boolean, d: ScreenData? = null) {
            val v = RemoteViews(ctx.packageName, R.layout.widget_spot)
            val open = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            v.setOnClickPendingIntent(R.id.widget_root, open)
            if (loading || d == null) {
                v.setTextViewText(R.id.widget_btc, if (loading) "⏳ memuat…" else "⚠️ ${ScreenApi.lastError.ifBlank { "gagal muat" }}")
                v.setTextViewText(R.id.widget_coins, "")
                v.setTextViewText(R.id.widget_pnl, "")
            } else {
                val up = d.btcChg >= 0
                v.setTextViewText(R.id.widget_btc,
                    "₿ ${fmtPx(d.btcPx)} (${if (up) "+" else ""}${fmtPct(d.btcChg)})")
                val lines = d.coins.take(4).joinToString("\n") { c ->
                    val cu = if (c.chg >= 0) "+" else ""
                    var s = "${c.sym} ${fmtPx(c.px)} ($cu${fmtPct(c.chg)})"
                    if (c.pnl != null) s += " PnL ${if (c.pnl >= 0) "+" else ""}${fmtUsd(c.pnl)}"
                    s
                }
                v.setTextViewText(R.id.widget_coins, lines)
                val tot = d.coins.mapNotNull { it.pnl }.sum()
                v.setTextViewText(R.id.widget_pnl,
                    if (d.coins.any { it.pnl != null }) "Σ PnL ${if (tot >= 0) "+" else ""}${fmtUsd(tot)}" else d.wib)
            }
            mgr.updateAppWidget(id, v)
        }

        private fun fmtPx(x: Double) = if (x >= 1000) "%,.0f".format(x)
            else if (x >= 100) "%,.1f".format(x) else "%,.4f".format(x)
        private fun fmtPct(x: Double) = "%.2f%%".format(x)
        private fun fmtUsd(x: Double) = "$%,.2f".format(x)

        fun refreshAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, SpotWidget::class.java))
            if (ids.isNotEmpty()) SpotWidget().onUpdate(ctx, mgr, ids)
        }
    }
}
