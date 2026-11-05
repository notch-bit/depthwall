# DepthWall

An original, free, ad-free depth-effect live wallpaper for Android.

- Pick a photo; on-device ML Kit separates the subject from the background.
- Wallpaper renders 3 layers (background, clock, subject) and moves them
  with the gyroscope for a 3D parallax effect.
- Settings: clock on/off, 12/24h, clock size, parallax strength.
- No ads, no billing, no accounts, no network use beyond Google Play services
  downloading the segmentation model once.

## Build
1. Open this folder in Android Studio (Koala or newer).
2. Let Gradle sync, then Run on a device (Google Play services required for segmentation).
3. In the app: Choose photo -> Set as live wallpaper.

Change `applicationId`/`namespace` in app/build.gradle.kts before publishing.
