# AI Pushup Coach (ŞınavKoç) — Native Android

A **100% Native Android** AI pushup counter and real-time form coach built with **Kotlin**, **Jetpack Compose (Material 3)**, **CameraX**, and **Google MediaPipe Pose Landmarker** — zero WebView, zero JavaScript, zero Electron.

## Features

- **Real-Time 60 FPS Camera & AI Pose Tracking:** Hardware CameraX preview paired with Google MediaPipe Pose Landmarker (`pose_landmarker_lite.task` bundled offline inside the APK) tracking 33 body landmarks with a color-coded skeleton overlay and live depth/angle gauges.
- **6 Pushup Variations:** Standard, Wide, Diamond, Knee, Incline, and Decline — each with tailored joint thresholds, muscle targets, camera tips, and form checklists.
- **Live Form Coaching:** Detects **hip sag**, **high hips (piking)**, **shallow depth**, **head drop**, and **too fast tempo** in real time.
- **Native Turkish Voice Coach:** Spoken rep count, milestones, and form cues via Android `TextToSpeech` + `ToneGenerator` audio feedback.
- **Progress Analytics:** Daily goal tracker, streak counter, 7-day bar chart, most frequent form mistakes, workout history, and post-workout form score reports.

## Download

Grab the latest signed Android APK from the [Releases](https://github.com/demir12157/ai-pushup-coach/releases) page.

## Build from Source

1. Download the MediaPipe model into `app/src/main/assets/pose_landmarker_lite.task`:
   ```bash
   mkdir -p app/src/main/assets
   curl -L -o app/src/main/assets/pose_landmarker_lite.task \
     https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/1/pose_landmarker_lite.task
   ```
2. Open in Android Studio or run:
   ```bash
   gradle assembleRelease
   ```

## Tech Stack

- **Language & UI:** Kotlin + Jetpack Compose (Material 3)
- **Camera:** AndroidX CameraX (front & back camera switching)
- **AI Pose Detection:** Google MediaPipe Tasks Vision (`0.10.14`, offline bundled model)
- **Voice Coach:** Native Android `TextToSpeech` + `ToneGenerator`
