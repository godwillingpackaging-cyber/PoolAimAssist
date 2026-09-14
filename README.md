# Pool Aim Assist — sample project

An **original**, from-scratch Android project (Kotlin) that shows the same *category* of
feature as commercial "pool guideline" apps — a floating aim-assist overlay for pool/billiards
games — built with its own detection algorithm, its own geometry, and its own UI. No code,
assets, or resources were copied from any existing app.

Fully free, no paywall / IAP / ads of any kind.

## How it works

1. **MainActivity** — walks you through granting the "draw over other apps" permission and
   the screen-capture (MediaProjection) permission, then starts `OverlayService`.
2. **OverlayService** — a foreground service that:
   - Uses `CaptureEngine` (MediaProjection + ImageReader) to grab a screenshot-style Bitmap
     of the screen ~5x/second.
   - Feeds each frame to `BallDetector`, a small dependency-free color/blob detector (no
     OpenCV/TensorFlow needed) that finds round, ball-sized regions whose color differs from
     the dominant table-felt color, and classifies the near-white one as the cue ball.
   - Feeds cue ball + object balls + your calibrated pocket positions into
     `TrajectoryCalculator`, which applies the standard **ghost-ball aiming method** to work
     out, for each object ball/pocket pair, whether the shot is geometrically makeable and
     how hard (cut angle).
   - Draws the result via `OverlayView`, a transparent, **non-touchable** full-screen window
     so it never blocks your taps on the game underneath.
   - Also adds a small **draggable control bar** (`ControlPanelView`) with "Calibrate" and
     "Stop" buttons — this one *is* touchable, positioned wherever you drag it.
3. **Calibration** — the first time you run it (or whenever you tap "Calibrate"), a
   translucent `CalibrationOverlay` appears on top of your still-visible game and asks you to
   tap each of the 6 pockets once. Positions are saved locally via `CalibrationStore`
   (SharedPreferences) — nothing leaves your device.

## Why calibration is manual but ball detection is automatic

Pool games ship with wildly different table skins/art, so reliably auto-detecting pocket
*shape* across all of them without a trained ML model isn't realistic with a lightweight
color-based approach. Balls, by contrast, are reliably round, saturated, and visually distinct
from the felt in virtually every skin — so that part is fully automatic. This hybrid approach
is a reasonable, transparent trade-off; see "Ideas for improving it" below if you want to push
further.

## Build it yourself

This sandbox has no network access or Android SDK, so this is a complete, ready-to-open
**source project**, not a compiled `.apk`. To build a real, installable APK:

1. Install [Android Studio](https://developer.android.com/studio) (free).
2. `File > Open` and select this project folder. Let Gradle sync (downloads the Android
   Gradle Plugin, Kotlin, and the two small AndroidX dependencies — needs internet once).
3. Change `applicationId` in `app/build.gradle.kts` if you plan to publish it (it's currently
   a placeholder, `com.example.poolaimassist`).
4. Plug in a device (or start an emulator) and hit ▶ Run — Android Studio builds, signs with
   a debug key, and installs it automatically.
5. To get a shareable release `.apk`: `Build > Generate Signed Bundle / APK`, choose APK,
   create/select a keystore, and build the release variant.

## Ideas for improving it

- Swap `BallDetector`'s color-blob approach for OpenCV (Hough circle transform) or a small
  on-device ML model for more robust detection across table skins/lighting.
- Detect the table rails automatically (e.g. via edge detection) so you don't need to
  hand-tap all 6 pockets.
- Add bank-shot (rail reflection) geometry to `TrajectoryCalculator`, not just direct shots.
- Detect obstruction: currently a shot line isn't checked against other balls sitting in the
  way — add a simple "does any other ball's center lie within R of this line segment" check.

## A note on where/how you use this

This app only draws on top of your own screen locally — it doesn't modify, inject into, or
communicate with any game. That said, many pool games' terms of service prohibit third-party
aim-assistance tools regardless of how they're implemented, so check the rules of whatever
game you point this at before using it, and use it at your own discretion.
