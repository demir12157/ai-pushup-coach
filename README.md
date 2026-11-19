# AI Pushup Coach (ŞınavKoç)

An AI-powered pushup counter and real-time form coach built with **React**, **TypeScript**, **MediaPipe Pose Landmarker**, and **Capacitor Android**. Point your camera from a side profile, pick a pushup variation, and get live rep counting, skeleton tracking, form corrections, voice coaching, and progress analytics.

## Features

- **6 Pushup Variations:** Standard, Wide, Diamond, Knee, Incline, and Decline — each with tailored pose angle thresholds, muscle targets, and form checklists.
- **Real-Time AI Pose & Skeleton Tracking:** Uses MediaPipe Pose Landmarker (33 body landmarks) with color-coded skeleton rendering (green = good form, amber = warning, red = form breakdown), live elbow/body angle badges, and a vertical depth gauge.
- **Live Form Analysis & Coaching:** Automatically detects common mistakes in real time:
  - Hip sag (lower back dropping)
  - High hips / piking
  - Shallow depth (incomplete range of motion)
  - Head drop
  - Too fast tempo
- **Voice Coach & Audio Cues:** Spoken rep counts, milestones, and real-time form correction cues via Web Speech API + subtle audio chimes.
- **Progress Tracking & Post-Workout Reports:** Session form score (0–100), mistake breakdown with tailored improvement recommendations, daily goal progress, streak counter, 7-day bar chart, and variation distribution saved locally.
- **PWA + Android APK:** Works directly in desktop/mobile browsers and ships with a Capacitor Android project + GitHub Actions workflow for automated APK builds.

## Quick Start

```bash
npm install
npm run dev
```

Open `http://localhost:5173` in your browser and allow camera access.

> **Tip:** Place your phone or webcam at floor level (20–40 cm high) in a side profile about 1.5–2 meters away so your shoulders, elbows, hips, and knees/ankles are clearly visible.

## Android APK Build

### Automated Cloud Build (GitHub Actions)
Every push to `main` automatically runs the **Build Android APK** workflow (`.github/workflows/build-apk.yml`) and publishes `SinavKoc-Android-APK` (`app-debug.apk`) under the **Actions** tab.

### Local Build (Android Studio / Gradle)
```bash
npm run android:sync
cd android
./gradlew assembleDebug
```
The compiled APK will be at `android/app/build/outputs/apk/debug/app-debug.apk`.

## Pure Rust Edition (`rust-app/`) — Zero JS / Zero WebView

Prefer zero JavaScript, zero Node.js, and zero WebView? Check out the **Pure Rust** native app in [`rust-app/`](rust-app/) built with `eframe`/`egui` + `nokhwa` + `cargo-apk`:

```bash
cd rust-app
cargo test
cargo run --release
```

GitHub Actions automatically builds both **Pure Rust Android APK** (`aarch64-linux-android`) and **Pure Rust Windows `.exe`** under the **Actions** tab.

## Tech Stack

- **Pure Rust Edition (`rust-app/`):** Rust, `eframe`/`egui`, `nokhwa`, `serde`, `chrono`, `cargo-apk`
- **Web / Capacitor Edition:** React 19, TypeScript, Vite, Tailwind CSS v4, `@mediapipe/tasks-vision`, Capacitor Android
