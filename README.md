# TripTracker Android App 🗺️

Real-time, offline-first GPS route tracker with Slippy Tile Map engine, speed telemetry, auto-detected stay stops, export/import (GPX, KML, GeoJSON, CSV), and battery optimization.

---

## 🚀 GitHub Actions Automatic APK Build

Jab bhi aap is project ko GitHub par push karenge:
1. **Automated Build**: GitHub Actions workflow (`.github/workflows/build-apk.yml`) automatic trigger ho jayega.
2. **Build Environment**: JDK 21, Android SDK 36, aur Gradle 9.3.1 setup honge aur `assembleDebug` run hoga.
3. **APK Generation**: `TripTracker-debug.apk` ban kar tayyar hoga.
4. **Download Artifact**: 
   - GitHub me apne repository ke **"Actions"** tab par jayein.
   - Latest workflow run par click karein.
   - Bottom me **"Artifacts"** section me `TripTracker-debug-apk` par click karke direct APK download kar sakte hain!
5. **Manual Trigger (Workflow Dispatch)**:
   - "Actions" tab -> "Build Android APK" select karein.
   - **"Run workflow"** button par click karke bina commit ke bhi kabhi bhi naya APK generate kar sakte hain.

---

## 🛠️ Local Build Instructions

Agar aap apne computer par build karna chahte hain:
```bash
# Make gradlew executable
chmod +x gradlew

# Build Debug APK
./gradlew assembleDebug

# Output APK path
# app/build/outputs/apk/debug/app-debug.apk
```
