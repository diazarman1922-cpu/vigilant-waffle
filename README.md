# TikTok Unreposter Android Skeleton

Open-source Android native Kotlin skeleton untuk **akun TikTok milik sendiri**. Tujuan app: login lewat WebView app sendiri, simpan session/cookie lokal terenkripsi, lalu menjalankan proses lewat Foreground Service dengan notifikasi progress agar user bisa minimize app dan buka WhatsApp/game lain.

## Status jujur

Project ini **tidak mengklaim sudah pasti bisa hapus repost**. Hasil riset official tidak menemukan API TikTok Developer untuk `remove/unrepost`. Yang official tersedia hanya Login Kit, Content Posting API, dan Research API untuk query reposted videos pada konteks researcher/approval tertentu.

Mode default aman:

```kotlin
// app/src/main/java/com/example/tiktokunreposter/tiktok/TikTokEndpoints.kt
const val ENABLE_UNOFFICIAL_WEB_ENDPOINTS: Boolean = false
```

Kalau `false`:

- login WebView tetap bisa,
- session tetap disimpan encrypted lokal,
- Foreground Service tetap bisa start,
- tapi `fetchRepostedVideos()` dan `removeRepost(videoId)` tidak mengeksekusi endpoint unofficial,
- UI/notifikasi menampilkan pesan: `Remove repost endpoint belum diaktifkan...`.

Endpoint unofficial yang ditemukan dari repo publik disimpan modular di `TikTokEndpoints.kt` dan dipakai oleh `TikTokWebApiClient.kt` **hanya jika developer mengubah flag ke true**. Ini experimental, bisa berubah, bisa kena challenge/rate-limit, dan punya risiko ToS.

## Yang sudah working secara struktur

- Native Android Kotlin project skeleton.
- WebView login ke `https://www.tiktok.com/`.
- Cookie TikTok dari WebView dibaca via `CookieManager` hanya untuk domain TikTok.
- Cookie/session disimpan lokal dengan `EncryptedSharedPreferences`.
- `Clear Session` menghapus encrypted prefs + WebView cookies + queue lokal.
- Foreground Service `dataSync` dengan notifikasi progress.
- Tombol Pause/Resume/Stop di UI dan notifikasi.
- Queue lokal JSON di `filesDir`.
- Client layer modular: `TikTokClient`, `TikTokWebApiClient`, `TikTokEndpoints`, models, exceptions, safe debug.
- Debug mode aman: hanya status code, endpoint name, dan error category; tidak pernah log cookie/token.

## Yang belum terbukti

- Endpoint unofficial TikTok Web belum diuji di project ini dengan akun real.
- `removeRepost(videoId)` belum bisa diklaim working.
- TikTok Web bisa membutuhkan token/signature seperti `msToken`, `verifyFp`, `_signature`, `X-Bogus`, atau challenge lain pada kondisi tertentu. App ini **tidak** membuat signer/bypass anti-bot.
- `secUid` perlu terbaca dari halaman profile WebView. Kalau tidak terbaca, buka profile sendiri lalu tap Save Session lagi.

## Struktur folder final

```text
TikTokUnreposter/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── README.md
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── res/values/strings.xml
│       ├── res/values/styles.xml
│       ├── res/xml/data_extraction_rules.xml
│       └── java/com/example/tiktokunreposter/
│           ├── ui/
│           │   ├── MainActivity.kt
│           │   └── LoginWebViewActivity.kt
│           ├── service/
│           │   ├── RepostRemoveForegroundService.kt
│           │   └── NotificationHelper.kt
│           ├── session/
│           │   └── TikTokSessionManager.kt
│           ├── tiktok/
│           │   ├── ApiDebugMode.kt
│           │   ├── CookieHeaderBuilder.kt
│           │   ├── TikTokApiException.kt
│           │   ├── TikTokApiModels.kt
│           │   ├── TikTokClient.kt
│           │   ├── TikTokClientOfficialOrWeb.kt
│           │   ├── TikTokEndpoints.kt
│           │   ├── TikTokWebApiClient.kt
│           │   └── TikTokWebTokenExtractor.kt
│           ├── data/
│           │   └── RepostQueueRepository.kt
│           └── util/
│               └── SafetyController.kt
```

## API/client flow

1. `LoginWebViewActivity` membuka TikTok Web.
2. User login sendiri di WebView app.
3. User buka profile sendiri, lalu tap `Save Session Locally`.
4. `CookieHeaderBuilder` membaca cookie TikTok dari `CookieManager`, sanitize header, dan menolak cookie kosong/aneh.
5. `TikTokWebTokenExtractor` mencoba baca `secUid`/token yang terlihat secara legal dari DOM/cookie. Tidak bypass signature atau challenge.
6. `TikTokSessionManager` menyimpan cookie + secUid + user-agent lokal terenkripsi.
7. `RepostRemoveForegroundService` start foreground notification.
8. Service memanggil `client.checkLogin()`.
9. Kalau unofficial endpoint OFF, service berhenti aman dengan pesan jelas.
10. Kalau ON, service memanggil `fetchRepostedVideos(cursor)`, enqueue `videoId`, lalu `removeRepost(videoId)` satu-satu.
11. `SafetyController` menerapkan max batch, delay aman, jitter, exponential backoff, dan stop saat login expired/challenge/rate-limit.

## Endpoint official yang tersedia

- Login Kit/OAuth: untuk user authorization dan token OAuth.
- Research API `POST https://open.tiktokapis.com/v2/research/user/reposted_videos/`: untuk query reposted videos dalam Research API, scope `research.data.basic`, bukan API remove/unrepost.
- Content Posting API: untuk posting konten, bukan delete/unrepost.

## Endpoint unofficial yang ditemukan dari sumber publik

Dari repo browser extension publik `gabireze/tiktok-all-reposted-videos-remover`:

```text
GET  https://www.tiktok.com/api/repost/item_list/?aid=1988&count=30&cursor=...&secUid=...
POST https://www.tiktok.com/tiktok/v1/upvote/delete?aid=1988&item_id=...
```

Status: **unofficial**, bukan TikTok Developer API, belum terbukti working di project Android ini.

## Token/signature notes

`TikTokWebTokenExtractor` hanya membaca token yang legal terlihat dari WebView/cookie session user sendiri:

- `tt_csrf_token`
- `csrf_session_id`
- `msToken`
- `s_v_web_id`
- `verifyFp`
- `ttwid`
- `secUid` dari DOM profile page jika terlihat

App ini tidak membuat:

- `_signature`
- `X-Bogus`
- `X-Gnarly`
- captcha solver
- device spoofing
- reverse engineered anti-bot signer

Kalau TikTok meminta signature/challenge, app harus stop aman dan user menyelesaikan manual di WebView. Jangan bypass.

## Cara mengaktifkan experimental unofficial endpoint

Edit file:

```text
app/src/main/java/com/example/tiktokunreposter/tiktok/TikTokEndpoints.kt
```

Ubah:

```kotlin
const val ENABLE_UNOFFICIAL_WEB_ENDPOINTS: Boolean = false
```

menjadi:

```kotlin
const val ENABLE_UNOFFICIAL_WEB_ENDPOINTS: Boolean = true
```

Lalu rebuild APK. Aktifkan hanya untuk akun sendiri dan pengujian pribadi. Jangan agresif; jangan multi-account; jangan bypass challenge.

## Risiko akun dan stabilitas

- Endpoint unofficial bisa berubah kapan saja.
- TikTok bisa mengembalikan HTML challenge, captcha, 401/403/429, atau JSON status error.
- Request bisa gagal walau cookie valid.
- Aktivitas otomatis bisa bertentangan dengan ToS TikTok.
- App ini sengaja punya delay, max batch, backoff, dan stop rules agar tidak agresif.

## Kenapa tidak pakai server eksternal

- Agar cookie/session tidak keluar dari device.
- Agar developer tidak pernah menerima token akun user.
- Agar audit open-source lebih gampang.
- Agar tidak bergantung Cloudflare/backend pihak ketiga.

## Permission

- `INTERNET`: WebView dan request HTTP ke TikTok.
- `POST_NOTIFICATIONS`: Android 13+ agar progress Foreground Service terlihat.
- `FOREGROUND_SERVICE`: menjalankan service yang terlihat user.
- `FOREGROUND_SERVICE_DATA_SYNC`: foreground service type untuk target SDK baru.

Tidak memakai Accessibility Service di versi ini.

## Cara clear session

Di app tekan `Clear Session`. Ini menghapus:

- encrypted shared preferences,
- cookie WebView via `CookieManager.removeAllCookies`,
- queue lokal JSON.

## Cara cek log aman

Filter logcat:

```bash
adb logcat | grep TikTokApiSafeDebug
```

Yang boleh muncul hanya:

- endpoint name,
- HTTP status code,
- error category,
- pesan aman.

Yang tidak boleh muncul:

- Cookie,
- `sessionid`,
- `sid_tt`,
- token,
- password,
- full sensitive URL.

## Cara build di Android Studio

1. Install Android Studio terbaru dengan Android SDK 36 dan JDK 17.
2. Buka folder project ini.
3. Sync Gradle.
4. Run `app` ke Android 8.0+.

## Cara build di Termux

Native Android build di Termux kadang berat dan tergantung device, tapi bisa dicoba:

```bash
pkg update
pkg install openjdk-17 git gradle
cd tiktok-unreposter-skeleton
gradle wrapper --gradle-version 9.4.1
./gradlew assembleDebug
```

Kalau gagal di Termux, alternatif:

- Android Studio di PC/laptop,
- AndroidIDE/AIDE kalau SDK/Gradle-nya cocok,
- GitHub Actions build APK,
- commit Gradle wrapper dari mesin yang punya Gradle.

## Manual checklist

- Login WebView berhasil.
- Cookie tersimpan encrypted.
- Clear session menghapus cookie.
- Foreground Service tetap jalan saat app diminimize.
- Notifikasi muncul.
- Pause/Resume/Stop bekerja.
- Tidak ada cookie/token di logcat.
- App berhenti kalau session expired.
- App berhenti kalau challenge/captcha.
- App berhenti atau backoff saat 429/rate limited.
- App tidak crash kalau network putus.
- Queue bisa resume.
- Endpoint OFF-by-default benar-benar tidak mengirim remove request.
