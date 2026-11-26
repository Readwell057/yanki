# Yankı — MP3 ve Video Oynatıcı (Android)

Kotlin + Jetpack Compose (Material 3) + AndroidX Media3 (ExoPlayer + MediaSessionService).

## APK'yı GitHub ile derleme (bilgisayarda Android Studio gerekmez)
1. GitHub'da yeni bir depo (repository) oluştur.
2. Bu zip'in içindekileri (`.github` klasörü dahil) depoya yükle.
   - Telefonda `.github` klasörü görünmüyorsa: depoda **Add file → Create new file**,
     dosya adına `.github/workflows/build.yml` yaz ve `GITHUB-ACTIONS-build.yml` dosyasının içeriğini yapıştır.
3. Depoda **Actions** sekmesine gir → **APK Derle** → **Run workflow**
   (ya da yüklemeden sonra otomatik başlar).
4. Bitince (≈5-8 dk) işleme tıkla → en altta **Artifacts → Yanki-APK** dosyasını indir, zip'i aç.
5. `app-debug.apk` dosyasını telefona/tablete kur ("bilinmeyen kaynaklardan kuruluma izin ver" gerekebilir).

## Android Studio ile
Klasörü aç → Sync → Run. (Gradle 8.9, JDK 17)

## Özellikler
Ses: tüm şarkılar / klasörler / sanatçılar / çalma listeleri, arka plan + bildirim + kilit ekranı kontrolü,
karışık, döngü, 0.75x–2.0x hız, uyku zamanlayıcısı, ses odağı.
Video: MP4/MKV/WebM (donanım hızlandırmalı), parlaklık/ses jestleri, çift dokunarak ±10 sn, ekran kilidi,
Sığdır/Doldur/Yakınlaştır, .srt altyazı, ses dili seçimi, PiP, sadece ses modu.
Ortak: kaldığın yerden devam, anlık arama, favoriler. Telefonda alt menü, tablette iki bölmeli düzen.
