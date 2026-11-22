Minimal stand-ins for the Android classes the UI code touches, backed by java.awt.
Only what the app's pages use is implemented. Used by `scripts/preview.sh` to render the real
drawing code to PNG on a desktop JVM. Fonts differ from the head unit (DejaVu Sans here,
Droid Sans on Android 2.3, which is slightly narrower), so treat the output as a close preview.
