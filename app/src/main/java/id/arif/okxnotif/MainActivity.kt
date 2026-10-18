package id.arif.okxnotif

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
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

/** Layar app = setting saja. Detail harga/PnL ada di web; notif tetap jalan. */
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
        var url by remember { mutableStateOf(Prefs.getUrl(ctx)) }
        var token by remember { mutableStateOf(Prefs.getToken(ctx)) }
        var interval by remember { mutableStateOf(Prefs.getIntervalSec(ctx).toString()) }
        var running by remember { mutableStateOf(Prefs.isRunning(ctx)) }
        var savedAt by remember { mutableStateOf("") }
        var cek by remember { mutableStateOf("Mengetes koneksi...") }
        val scope = rememberCoroutineScope()

        suspend fun testKoneksi() {
            val d = withContext(Dispatchers.IO) { ScreenApi.load(url.trim(), token.trim()) }
            cek = if (d != null) "Koneksi OK · data " + d.wib
            else "Gagal — " + ScreenApi.lastError.ifBlank { "tidak terjangkau" }
        }
        LaunchedEffect(Unit) { testKoneksi() }

        fun openWeb() {
            val u = url.trim().ifBlank { Prefs.getUrl(ctx) }
            if (u.isBlank()) {
                cek = "Gagal — URL belum diisi"
                return
            }
            val base = u.substringBefore("/screening.json").trimEnd('/')
            try {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(base)))
            } catch (_: Exception) {
                cek = "Tidak bisa membuka browser"
            }
        }

        fun startService() {
            val i = Intent(ctx, PriceService::class.java)
            androidx.core.content.ContextCompat.startForegroundService(ctx, i)
            Prefs.setRunning(ctx, true)
            running = true
        }

        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(12.dp, 12.dp, 12.dp, 0.dp),
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("OKX Pulse", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Color.White)
            }
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
                            Text("Web detail", fontWeight = FontWeight.Bold, color = Color.White)
                            val okFlag = cek.startsWith("Koneksi OK")
                            val badFlag = cek.startsWith("Gagal") || cek.startsWith("Tidak")
                            Text(cek, color = if (okFlag) Green else if (badFlag) Red else Muted, fontSize = 13.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AnimOutlinedButton(onClick = { openWeb() }, text = "Buka Web")
                                AnimOutlinedButton(onClick = { scope.launch { testKoneksi() } }, text = "Test koneksi")
                            }
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
                                    if (running) {
                                        ctx.stopService(Intent(ctx, PriceService::class.java))
                                        startService()
                                    }
                                    scope.launch { testKoneksi() }
                                }) { Text("Simpan") }
                                if (running) AnimOutlinedButton(onClick = {
                                    ctx.stopService(Intent(ctx, PriceService::class.java))
                                    Prefs.setRunning(ctx, false)
                                    running = false
                                }, text = "Matikan")
                                else AnimOutlinedButton(onClick = {
                                    Prefs.saveAdv(ctx, url, token, interval)
                                    startService()
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
