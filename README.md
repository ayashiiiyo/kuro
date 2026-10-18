# Kuro (黒) — Minimalist Monochrome Music Streaming

> An ultra-lightweight, zero-bloat music streaming app for Android inspired by Apple Music & Spotify aesthetics, powered by **NewPipeExtractor** and **AndroidX Media3**.

![Android](https://img.shields.io/badge/Platform-Android-black?style=for-the-badge&logo=android)
![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-black?style=for-the-badge&logo=jetpackcompose)
![License](https://img.shields.io/badge/License-GPL%20v3-black?style=for-the-badge)

---

## ✨ Features

- **🖤 OLED Monochrome Theme:** Deep true-black (`#000000`) and pure-white (`#FFFFFF`) palette crafted for battery efficiency on OLED screens.
- **⚡ Ultra-Lightweight & Zero-Login:** Powered by `NewPipeExtractor`. No Google Play Services required, zero ads, no sign-in needed.
- **🎵 Background Playback:** Full background audio support with `MediaSessionService` (Media3 / ExoPlayer), system notification controls, and Bluetooth/headset integration.
- **🔍 Instant Search & Trending:** Explore trending music charts or search for songs, artists, and albums instantly.
- **📱 Floating Mini Player & Sleek Sheet:** Smooth transition between bottom navigation mini player and full-screen album artwork playback view.
- **❤️ Local Library & Favorites:** Save your favorite tracks locally without needing an account.
- **🤖 CI/CD Auto-Build:** Automated APK compilation through GitHub Actions.

---

## 🛠️ Tech Stack

- **UI Framework:** Kotlin + Jetpack Compose (Material 3)
- **Audio Engine:** AndroidX Media3 (ExoPlayer) + MediaSessionService
- **Scraper Engine:** [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor)
- **Image Loader:** Coil Compose
- **Networking:** OkHttp3
- **Build System:** Gradle Kotlin DSL (AGP 8.4+, Java 17)

---

## 🚀 Building from Source

All builds are handled in **GitHub Actions** to keep compilation fast and cloud-native.

If building locally with Android Studio:
```bash
git clone https://github.com/ayashiiiyo/kuro.git
cd kuro
./gradlew assembleDebug
```
The output APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 📄 License
This project is licensed under the GNU General Public License v3.0 — see the [LICENSE](LICENSE) file for details.
