# TikTok Unreposter Skeleton

Android Kotlin open-source skeleton untuk akun TikTok **milik sendiri**. App login lewat WebView milik app, menyimpan session lokal terenkripsi, lalu menjalankan foreground service dengan notifikasi progress.

Project ini **bukan official TikTok app** dan **tidak mengklaim real mass remove repost sudah working**.

## Status project

Build target saat ini:

- Kotlin Android native
- `minSdk 26`
- `targetSdk 36`
- `compileSdk 36`
- Java/Kotlin target `17`
- Default mode: `MOCK`
- Unofficial endpoint: `OFF` by default

### Working / siap dites lokal

- WebView login TikTok.
- Encrypted local session via AndroidX Security Crypto.
- Cookie hanya dari WebView app sendiri.
- Foreground Service dengan notification progress.
- Pause / Resume / Stop action dari UI dan notification.
- Queue lokal JSON.
- Mock mode full flow tanpa menyentuh TikTok.
- API diagnostics screen.
- Session diagnostics screen.
- Background readiness screen.
- Safe local report: `files/reports/last_run_report.json`.
- SafeLogger: tidak log cookie/token/session.
- GitHub Actions workflow untuk assemble debug APK.

### Belum terbukti working

- Real TikTok remove/unrepost dari Android app ini belum diuji akun sendiri.
- Belum pasti cookie WebView cukup untuk endpoint web unofficial.
- Belum pasti endpoint unofficial masih valid.
- Kalau TikTok butuh captcha, challenge, signature, `X-Bogus`, `X-Gnarly`, atau anti-bot token lain, app sengaja stop aman dan **tidak bypass**.

## Privacy promise

Project ini sengaja dibatasi:

- No external server.
- No Cloudflare.
- No Chrome extension.
- No password storage.
- No cookie/session exfiltration.
- No TikTok app cookie extraction.
- No captcha/2FA/challenge bypass.
- No signature generator.
- No headless browser/signer server.
- No multi-account automation.

## Modes

### `MOCK`

Default. Tidak menyentuh TikTok. Semua data palsu. Cocok untuk test foreground service, notification, queue, pause/resume/stop, dan report.

### `REAL_SAFE`

Cek session/login saja. Tidak fetch repost dan tidak remove repost.

### `REAL_DRY_RUN`

Fetch repost list saja, kalau unofficial endpoint sudah diaktifkan manual. Tidak akan remove apa pun. Hasil report hanya metadata aman: count/status/error category.

### `REAL_UNOFFICIAL_EXPERIMENTAL`

Mode remove real memakai endpoint TikTok Web unofficial dari sumber publik. Tetap OFF by default dan wajib warning dialog. Jangan dipakai sebelum mock, session, dry-run, dan remove-one test lolos.

## Experimental endpoints

File:

```kotlin
app/src/main/java/com/example/tiktokunreposter/tiktok/TikTokEndpoints.kt
```

Default:

```kotlin
val ENABLE_UNOFFICIAL_WEB_ENDPOINTS: Boolean
    get() = BuildConfig.ENABLE_UNOFFICIAL_WEB_ENDPOINTS
```

Flag BuildConfig ada di:

```kotlin
app/build.gradle.kts
```

Default:

```kotlin
buildConfigField("boolean", "ENABLE_UNOFFICIAL_WEB_ENDPOINTS", "false")
```

Untuk testing developer yang paham risiko:

```kotlin
buildConfigField("boolean", "ENABLE_UNOFFICIAL_WEB_ENDPOINTS", "true")
```

Lalu rebuild APK. Ini tidak membuat endpoint official; ini cuma mengizinkan client mengakses endpoint unofficial yang sudah dipisah modular.

## Build Android Studio

1. Open folder `tiktok-unreposter-skeleton`.
2. Pastikan JDK 17 aktif.
3. Sync Gradle.
4. Run `app`.

Kalau Android Studio menanyakan Gradle wrapper, pilih local Gradle atau generate wrapper:

```bash
gradle wrapper --gradle-version 9.4.1 --distribution-type bin
```

## Build command line

Project menyertakan `gradlew` fallback script. Karena environment generator ini tidak punya `gradle-wrapper.jar`, script akan memakai Gradle dari PATH kalau tersedia.

```bash
./gradlew clean assembleDebug
```

Kalau muncul:

```text
gradle-wrapper.jar not found and gradle is not installed
```

Install/generate Gradle wrapper dulu:

```bash
gradle wrapper --gradle-version 9.4.1 --distribution-type bin
./gradlew clean assembleDebug
```

## Build via GitHub Actions

Workflow:

```text
.github/workflows/android-build.yml
```

Yang dilakukan:

- checkout
- setup JDK 17
- setup Android SDK
- install platform Android 36
- setup Gradle 9.4.1
- `chmod +x ./gradlew`
- `./gradlew clean assembleDebug --stacktrace`
- upload APK artifact
- upload `build.log` kalau gagal

Tidak butuh secrets.

## Build via Termux

Termux bisa berat untuk Android native build, tapi bisa dicoba:

```bash
pkg update
pkg install openjdk-17 git gradle
cd tiktok-unreposter-skeleton
./gradlew clean assembleDebug
```

Kalau SDK/AGP tidak jalan di Termux, pakai Android Studio, AndroidIDE/AIDE yang support SDK baru, atau GitHub Actions.

## Manual test plan

### A. Mock mode test

1. Install APK.
2. Buka app.
3. Tap `Start Mock Test`.
4. Minimize app.
5. Buka WhatsApp/game.
6. Pastikan notification progress tetap update.
7. Tap `Pause`.
8. Tap `Resume`.
9. Tap `Stop Immediately`.
10. Tap `Export Safe Report`.

Expected:

- Tidak ada request TikTok.
- Notifikasi muncul.
- Progress berubah.
- Report `containsSensitiveData=false`.

### B. Session test

1. Tap `Login TikTok via WebView`.
2. Login akun sendiri.
3. Tap `I'm logged in — Save Session Locally`.
4. Buka `Session Diagnostics`.
5. Pastikan cookie present tanpa value.
6. Tap `Clear Session`.
7. Pastikan session hilang.

Expected output aman:

```text
TikTok cookies: present
Cookie count: 12
msToken: present/not found
csrf token: present/not found
secUid: present/not found
Session saved: yyyy-MM-dd HH:mm
Sensitive values: hidden
```

### C. REAL_SAFE test

1. Login via WebView.
2. Tap `Start Real Safe Check`.
3. App hanya check session.
4. Tidak boleh fetch repost.
5. Tidak boleh remove apa pun.

### D. REAL_DRY_RUN test

1. Ubah `ENABLE_UNOFFICIAL_WEB_ENDPOINTS=true` di `app/build.gradle.kts`.
2. Rebuild.
3. Login via WebView.
4. Tap `Start Real Dry Run`.
5. App boleh fetch list jika endpoint/session valid.
6. App tidak boleh remove apa pun.
7. Kalau challenge/rate-limit/login expired, app harus stop aman.

### E. Remove one test

1. Ubah `ENABLE_UNOFFICIAL_WEB_ENDPOINTS=true`.
2. Rebuild.
3. Login via WebView.
4. Buka `API Diagnostics`.
5. Tap `Remove 1 Repost Test`.
6. Konfirmasi dua kali.
7. App fetch satu page, remove satu item, lalu stop.

Kalau gagal karena challenge/signature/rate-limit, app stop dan tidak bypass.

### F. Mass remove

Jangan aktifkan sebelum:

- mock pass,
- session pass,
- real safe pass,
- dry-run pass,
- remove-one pass,
- tidak ada challenge/rate-limit.

## Screens

### MainActivity

- Status mode.
- Status session aman.
- Login TikTok via WebView.
- Start Mock Test.
- Start Real Safe Check.
- Start Real Dry Run.
- Start Experimental Remove.
- Pause / Resume / Stop.
- API Diagnostics.
- Session Diagnostics.
- Background Readiness.
- Clear Session.
- Export Safe Report.
- Clear Reports.
- Log aman 20 baris.

### ApiDiagnosticsActivity

Menampilkan mode aktif dan tombol:

- Check Login
- Test Fetch Reposts
- Test Remove One Mock Item
- Remove 1 Repost Test kalau unofficial endpoint aktif
- Show Last Safe Error
- Clear Diagnostics

Output aman:

- endpoint name
- status code
- error category
- elapsed time
- item count
- cursor ada/tidak

Tidak menampilkan cookie/token/session/full sensitive response body.

### SessionDiagnosticsActivity

Menampilkan:

- cookies present/missing
- cookie count
- msToken present/missing
- csrf token present/missing
- secUid present/missing
- saved time

Tidak menampilkan value cookie/token.

### BackgroundReadinessActivity

Checklist:

- notification permission granted?
- battery optimization ignored?
- foreground service declared?
- network available?
- session exists?
- app mode?
- unofficial endpoint enabled?

Tombol:

- Open App Notification Settings
- Open Battery Optimization Settings
- Check Readiness

App tidak memaksa user mematikan battery optimization.

## Permissions

```xml
INTERNET
POST_NOTIFICATIONS
FOREGROUND_SERVICE
FOREGROUND_SERVICE_DATA_SYNC
ACCESS_NETWORK_STATE
```

`ACCESS_NETWORK_STATE` dipakai hanya untuk readiness checklist. Tidak membaca data pribadi.

## Troubleshooting

### Notification tidak muncul

- Android 13+: grant notification permission.
- Buka `Background Readiness`.
- Cek app notification settings.

### Service mati saat app diminimize

- Beberapa vendor Android agresif membunuh background app.
- Buka `Background Readiness`.
- Cek battery optimization settings.

### Login expired

- Buka `Session Diagnostics`.
- Clear session.
- Login ulang via WebView.

### Challenge required / captcha / verification

- App stop aman.
- Selesaikan manual di TikTok WebView.
- App tidak bypass challenge.

### Rate limited

- App stop aman.
- Jangan retry agresif.
- Tunggu manual.

### Dry run tidak fetch

Kemungkinan:

- endpoint unofficial berubah,
- cookie WebView tidak cukup,
- secUid belum kebaca,
- TikTok meminta signature/challenge,
- endpoint disabled.

## Disclaimer

Gunakan hanya untuk akun sendiri. Project ini bukan official TikTok client. Endpoint remove/unrepost official belum tersedia dari TikTok Developer API. Mode experimental bisa gagal, berubah, atau berisiko terhadap akun. App ini tidak bypass sistem keamanan TikTok.
