# AI Pushup Coach (Pure Rust)

A **100% Pure Rust** pushup counter, real-time form analyzer, and progress tracker — **zero JavaScript, zero React, zero Electron, zero WebView**.

Built with **`eframe` / `egui`** for GPU-accelerated native UI, **`nokhwa`** for live desktop webcam capture, and **`cargo-apk`** for native Android APK builds.

## Features

- **6 Pushup Variations:** Standard, Wide, Diamond, Knee, Incline, and Decline — each with tailored joint angle thresholds, muscle targets, and form checklists.
- **Real-Time Pose & Form Analysis (`src/analyzer.rs`):**
  - Exponential Moving Average (EMA) smoothed elbow & body alignment angles
  - Live skeleton overlay color-coded by form quality (green = good, amber = warning, red = form breakdown)
  - Vertical depth gauge & live elbow/body degree indicators
  - Real-time detection of **hip sag**, **high hips (piking)**, **shallow depth**, **head drop**, and **fast tempo**
- **Progress Analytics & Local Storage (`src/storage.rs`):**
  - Daily goal tracker, streak counter, 7-day bar chart, most frequent form mistakes, and workout history stored locally as JSON
  - Post-workout summary with 0–100 form score and personalized coaching recommendations
- **Simulation Mode:** Built-in synthetic pose simulator for testing without a camera.

## Quick Start (Desktop)

```bash
cargo test
cargo run --release
```

## Android APK Build

Every push to `main` automatically compiles both the **Android APK (`aarch64-linux-android`)** and **Windows `.exe`** via GitHub Actions (`.github/workflows/build-rust.yml`).

To build locally with Android SDK + NDK installed:

```bash
rustup target add aarch64-linux-android
cargo install cargo-apk --locked
cargo apk build --release --target aarch64-linux-android
```

## Tech Stack

- **Language:** 100% Rust
- **UI:** `eframe` / `egui` (OpenGL / Glow)
- **Camera:** `nokhwa` (native desktop capture)
- **Android Packaging:** `android-activity` + `cargo-apk`
- **Persistence:** `serde` + `serde_json` + `chrono`
