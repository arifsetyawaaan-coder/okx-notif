package id.arif.okxnotif

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private val Green = Color(0xFF26A69A)
private val Red = Color(0xFFEF5350)
private val Bg = Color(0xFF0A0E14)
private val CardBg = Color(0xFF141A24)
private val Muted = Color(0xFF8B93A7)

private fun fmtPx(x: Double): String = when {
    x >= 1000 -> "%,.0f".format(Locale.US, x)
    x >= 100 -> "%.2f".format(Locale.US, x)
    x >= 1 -> "%.4f".format(Locale.US, x).trimEnd('0').trimEnd('.')
    else -> "%.6f".format(Locale.US, x).trimEnd('0').trimEnd('.')
}

@Composable
fun Sparkline(data: List<Double>, up: Boolean, modifier: Modifier = Modifier) {
    val color = if (up) Green else Red
    Canvas(modifier = modifier.height(44.dp).fillMaxWidth()) {
        if (data.size < 2) return@Canvas
        val mn = data.minOrNull() ?: return@Canvas
        val mx = data.maxOrNull() ?: return@Canvas
        val span = (mx - mn).takeIf { it > 0 } ?: 1.0
        val path = Path()
        data.forEachIndexed { i, v ->
            val x = size.width * i / (data.size - 1)
            val y = size.height - ((v - mn) / span * size.height * 0.9f + size.height * 0.05f).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = color, style = Stroke(width = 3f))
        val last = data.last()
        val lx = size.width
        val ly = size.height - ((last - mn) / span * size.height * 0.9f + size.height * 0.05f).toFloat()
        drawCircle(color, radius = 6f, center = Offset(lx - 3f, ly))
    }
}

@Composable
fun AnimOutlinedButton(onClick: () -> Unit, text: String) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.93f else 1f, label = "press")
    OutlinedButton(
        onClick = onClick,
        interactionSource = interaction,
        modifier = Modifier.graphicsLayer(scaleX = scale, scaleY = scale)) { Text(text) }
}

@Composable
fun AnimCard(content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, label = "card")
    Card(
        colors = CardDefaults.cardColors(containerColor = CardBg),
        interactionSource = interaction,
        onClick = {},
        modifier = Modifier.graphicsLayer(scaleX = scale, scaleY = scale)) { content() }
}

@Composable
fun CoinCard(c: CoinUi) {
    val up = (c.pnlPct ?: c.chg) >= 0
    val posColor = if (up) Green else Red
    AnimCard {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(c.sym, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                Text(
                    "${if (c.chg >= 0) "▲" else "▼"} ${"%+.1f".format(Locale.US, c.chg)}%",
                    color = if (c.chg >= 0) Green else Red, fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.height(2.dp))
            Text("$${fmtPx(c.px)}", fontSize = 30.sp, fontWeight = FontWeight.Bold,
                color = Color.White, fontFamily = FontFamily.Monospace)
            if (c.pnl != null && c.pnlPct != null) {
                val s = if (c.pnl < 0) "-" else "+"
                Text("$s$${"%,.2f".format(Locale.US, kotlin.math.abs(c.pnl))}  (${"%+.1f".format(Locale.US, c.pnlPct)}%)",
                    color = posColor, fontSize = 19.sp, fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.height(6.dp))
            Sparkline(c.spark, up)
            Spacer(Modifier.height(6.dp))
            val stats = buildList {
                add("RSI ${"%.0f".format(c.rsi)}")
                add("Pos ${"%.0f".format(c.ratio)}%")
                c.funding?.let { add("Fund ${"%.4f".format(it)}") }
                c.tpDist?.let { add("TP ${"%+.1f".format(it)}%") }
                c.tp?.let { add("TP@${fmtPx(it)}") }
            }
            Text(stats.joinToString(" · "), color = Muted, fontSize = 12.sp,
                fontFamily = FontFamily.Monospace)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {

    private val permReq = registerForActivityResult(
        ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyDeepLink(intent)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permReq.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                background = Bg, surface = Bg, primary = Green)) {
                Screen()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyDeepLink(intent)
    }

    private fun applyDeepLink(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "okxnotif" || uri.host != "config") return
        val url = uri.getQueryParameter("url")?.trim().orEmpty()
        val token = uri.getQueryParameter("token")?.trim().orEmpty()
        val interval = uri.getQueryParameter("interval")?.trim().orEmpty()
        if (url.isNotBlank() || token.isNotBlank()) {
            Prefs.saveAdv(this,
                url.ifBlank { Prefs.getUrl(this) },
                token.ifBlank { Prefs.getToken(this) },
                interval.ifBlank { Prefs.getIntervalSec(this).toString() })
        }
    }

    @Composable
    fun Screen() {
        val ctx = LocalContext.current
        var data by remember { mutableStateOf<ScreenData?>(null) }
        var refreshing by remember { mutableStateOf(false) }
        var err by remember { mutableStateOf<String?>(null) }
        var page by remember { mutableStateOf("home") }
        var url by remember { mutableStateOf(Prefs.getUrl(ctx)) }
        var token by remember { mutableStateOf(Prefs.getToken(ctx)) }
        var interval by remember { mutableStateOf(Prefs.getIntervalSec(ctx).toString()) }
        var running by remember { mutableStateOf(Prefs.isRunning(ctx)) }
        var savedAt by remember { mutableStateOf("") }
        val scope = rememberCoroutineScope()

        suspend fun load() {
            refreshing = true
            err = null
            val d = withContext(Dispatchers.IO) {
                ScreenApi.load(Prefs.getUrl(ctx), Prefs.getToken(ctx))
            }
            if (d == null) err = ScreenApi.lastError.ifBlank { "Gagal ambil JSON" }
            else data = d
            refreshing = false
        }
        LaunchedEffect(Unit) { load() }

        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(12.dp, 12.dp, 12.dp, 0.dp),
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("OKX Pulse", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Color.White)
                if (page == "home") AnimOutlinedButton(onClick = { page = "settings" }, text = "⚙ Setting")
                else AnimOutlinedButton(onClick = { page = "home" }, text = "← Kembali")
            }
            androidx.compose.animation.AnimatedVisibility(page == "home") {
                PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = { scope.launch { load() } },
                    modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        item {
                            val total = data?.coins?.mapNotNull { it.pnl }?.sum()
                            Card(colors = CardDefaults.cardColors(containerColor = CardBg)) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(
                                        if (total == null) "Total PnL —"
                                        else "Total ${if (total < 0) "-" else "+"}$${"%,.2f".format(Locale.US, kotlin.math.abs(total))}",
                                        fontSize = 24.sp, fontWeight = FontWeight.Bold,
                                        color = if ((total ?: 0.0) < 0) Red else Green,
                                        fontFamily = FontFamily.Monospace)
                                    Text(data?.wib ?: "Tarik ke bawah untuk refresh",
                                        color = Muted, fontSize = 12.sp)
                                }
                            }
                        }
                        err?.let { item { Text(it, color = Red, fontSize = 13.sp) } }
                        data?.let { d ->
                            val active = d.coins.filter { (it.qty ?: 0.0) > 0 }
                            items(active, key = { it.sym }) { CoinCard(it) }
                            item {
                                Card(colors = CardDefaults.cardColors(containerColor = CardBg)) {
                                    Column(Modifier.padding(14.dp)) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("BTC", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                                            Text(
                                                "${if (d.btcChg >= 0) "▲" else "▼"} ${"%+.2f".format(Locale.US, d.btcChg)}%",
                                                color = if (d.btcChg >= 0) Green else Red, fontSize = 13.sp,
                                                fontFamily = FontFamily.Monospace)
                                        }
                                        Spacer(Modifier.height(2.dp))
                                        Text("$${fmtPx(d.btcPx)}", fontSize = 30.sp, fontWeight = FontWeight.Bold,
                                            color = Color.White, fontFamily = FontFamily.Monospace)
                                        Text("Kondisi pasar", color = Muted, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AnimOutlinedButton(onClick = { scope.launch { load() } }, text = "Refresh")
                                AnimOutlinedButton(onClick = {
                                    scope.launch {
                                        Prefs.saveAdv(ctx, url, token, interval)
                                        load()
                                        val i = android.content.Intent(ctx, PriceService::class.java)
                                        if (running) ctx.stopService(i)
                                        androidx.core.content.ContextCompat.startForegroundService(ctx, i)
                                        Prefs.setRunning(ctx, true)
                                        running = true
                                    }
                                }, text = if (running) "Restart notif" else "Nyalakan notif")
                                if (running) AnimOutlinedButton(onClick = {
                                    ctx.stopService(android.content.Intent(ctx, PriceService::class.java))
                                    Prefs.setRunning(ctx, false)
                                    running = false
                                }, text = "Matikan")
                            }
                        }
                    }
                }
            }
            androidx.compose.animation.AnimatedVisibility(page == "settings") {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = CardBg)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Koneksi VPS", fontWeight = FontWeight.Bold, color = Color.White)
                                OutlinedTextField(url, { url = it }, label = { Text("URL JSON") }, singleLine = true)
                                OutlinedTextField(token, { token = it }, label = { Text("Token") }, singleLine = true)
                                OutlinedTextField(interval, { interval = it }, label = { Text("Refresh detik (10-60)") }, singleLine = true)
                            }
                        }
                    }
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = CardBg)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Notifikasi", fontWeight = FontWeight.Bold, color = Color.White)
                                Text(if (running) "Status: nyala" else "Status: mati", color = Muted, fontSize = 13.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = {
                                        Prefs.saveAdv(ctx, url, token, interval)
                                        savedAt = java.text.SimpleDateFormat("HH.mm.ss", Locale.US).format(java.util.Date())
                                    }) { Text("Simpan") }
                                    if (running) AnimOutlinedButton(onClick = {
                                        ctx.stopService(android.content.Intent(ctx, PriceService::class.java))
                                        Prefs.setRunning(ctx, false)
                                        running = false
                                    }, text = "Matikan")
                                    else AnimOutlinedButton(onClick = {
                                        scope.launch {
                                            Prefs.saveAdv(ctx, url, token, interval)
                                            val i = android.content.Intent(ctx, PriceService::class.java)
                                            androidx.core.content.ContextCompat.startForegroundService(ctx, i)
                                            Prefs.setRunning(ctx, true)
                                            running = true
                                        }
                                    }, text = "Nyalakan")
                                }
                                if (savedAt.isNotBlank()) Text("Tersimpan $savedAt", color = Green, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
