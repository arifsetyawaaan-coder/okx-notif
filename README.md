# OKX Notif (Android)

Notifikasi ongoing di HP seperti gambar: harga + PnL HYPE dan ZEC, refresh tiap menit.

## Cara kerja
- `PriceService` (foreground service) ambil harga dari REST publik OKX
  (`/api/v5/market/ticker`, tanpa API key) tiap N detik, tampilkan di 1
  notifikasi ongoing (BigTextStyle, tidak bisa di-swipe).
- PnL = (harga − avg) × qty. Avg/qty default dari posisi VPS Okt 2026
  (HYPE 87.208 × 0.6302, ZEC 1405.53 × 0.03555) — ubah di layar utama.
- `BootReceiver` menyalakan lagi setelah HP restart.

## Build APK (HP tidak perlu SDK)
1. Push repo ini ke GitHub.
2. Tab Actions → workflow "Build APK" jalan otomatis → unduh `okx-notif-apk`.
3. Install APK di HP → buka app → izinkan notifikasi → "Nyalakan notifikasi".

## Catatan
- Refresh minimal 30 detik (hemat baterai default 60 detik).
- Android 13+ minta izin notifikasi sekali.
- Data harga publik OKX (bisa telat beberapa detik vs orderbook).
