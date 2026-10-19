# OKX Notif (Android)

Notifikasi ongoing di HP seperti gambar: harga + PnL HYPE dan ZEC, refresh tiap menit.

## Cara kerja
- `PriceService` (foreground service) ambil data dari `notif.json` VPS
  (Bearer token) tiap N detik, tampilkan di 1 notifikasi ongoing
  (BigTextStyle, tidak bisa di-swipe). Daftar koin DINAMIS ikut VPS:
  non-USDT bernilai > $1 + TP live (sama seperti dashboard).
- PnL = (harga − avg) × qty, avg/qty ikut dari VPS. Cadangan bila VPS
  tak terjangkau: WebSocket publik OKX + cache koin terakhir.
- `BootReceiver` menyalakan lagi setelah HP restart.

## Build APK (HP tidak perlu SDK)
1. Push repo ini ke GitHub.
2. Tab Actions → workflow "Build APK" jalan otomatis → unduh `okx-notif-apk`.
3. Install APK di HP → buka app → izinkan notifikasi → "Nyalakan notifikasi".

## Catatan
- Refresh minimal 30 detik (hemat baterai default 60 detik).
- Android 13+ minta izin notifikasi sekali.
- Data harga publik OKX (bisa telat beberapa detik vs orderbook).
