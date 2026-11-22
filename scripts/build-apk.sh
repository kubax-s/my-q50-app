#!/usr/bin/env bash
# Builds the InTouch app: Java -> dex -> APK (API 10, v1 signature) -> .epk for App Garage.
#
# Tools (Ubuntu: apt-get install aapt zipalign apksigner dalvik-exchange; or an Android SDK
# build-tools dir on PATH providing d8): javac (JDK 17+), aapt, zipalign, apksigner, dx|d8, node 22.18+.
#
# Environment (all optional):
#   VERSION_CODE    integer, must grow for App Garage to offer an update (default: unix time)
#   KEYSTORE        signing keystore (default: signing/q50.keystore, created on first run)
#   KEYSTORE_PASS   keystore + key password (default: "android")
#   KEY_ALIAS       key alias (default: "q50")
#
# Icon: app/res/drawable/ic_launcher.png (from tools/icon/MakeIcon.java). To use your own image
# instead, put a square PNG at app/icon-override.png (git-ignored); it is scaled to 96×96.
set -euo pipefail
cd "$(dirname "$0")/.."

APP=app
OUT=build
NAME=my-q50
CACHE="$OUT/cache"
ANDROID_JAR_URL="https://repo1.maven.org/maven2/com/google/android/android/2.3.3/android-2.3.3.jar"
ANDROID_JAR_SHA1="949a2bef1ffe62e81af5d8919d17dfcc1309c43c"
ANDROID_JAR="${ANDROID_JAR:-$CACHE/android-2.3.3.jar}"
# org.json ships inside Android but the Maven android.jar declares it as a separate artifact.
JSON_JAR_URL="https://repo1.maven.org/maven2/org/json/json/20080701/json-20080701.jar"
JSON_JAR_SHA1="d652f102185530c93b66158b1859f35d45687258"
JSON_JAR="$CACHE/json-20080701.jar"
VERSION_CODE="${VERSION_CODE:-$(date +%s)}"
KEYSTORE="${KEYSTORE:-signing/q50.keystore}"
KEYSTORE_PASS="${KEYSTORE_PASS:-android}"
KEY_ALIAS="${KEY_ALIAS:-q50}"

step() { echo "== $*"; }

if command -v d8 >/dev/null; then DEX=d8
elif command -v dalvik-exchange >/dev/null; then DEX=dx
elif command -v dx >/dev/null; then DEX=dx
else echo "missing dx/d8" >&2; exit 1; fi
DX_BIN=$(command -v dalvik-exchange || command -v dx || true)
for t in javac aapt zipalign apksigner keytool node; do
  command -v "$t" >/dev/null || { echo "missing tool: $t" >&2; exit 1; }
done

rm -rf "$OUT/classes" "$OUT/dex" "$OUT/$NAME".*
mkdir -p "$CACHE" "$OUT/classes" "$OUT/dex"

if [ ! -f "$ANDROID_JAR" ]; then
  step "download android.jar (API 10)"
  curl -sSfL -o "$ANDROID_JAR.tmp" "$ANDROID_JAR_URL"
  echo "$ANDROID_JAR_SHA1  $ANDROID_JAR.tmp" | sha1sum -c --quiet
  mv "$ANDROID_JAR.tmp" "$ANDROID_JAR"
fi

if [ ! -f "$JSON_JAR" ]; then
  step "download org.json (compile-only)"
  curl -sSfL -o "$JSON_JAR.tmp" "$JSON_JAR_URL"
  echo "$JSON_JAR_SHA1  $JSON_JAR.tmp" | sha1sum -c --quiet
  mv "$JSON_JAR.tmp" "$JSON_JAR"
fi

step "javac (Java 8 bytecode, Android API 10 classpath)"
# shellcheck disable=SC2046
javac --release 8 -encoding UTF-8 -Xlint:all,-options -Werror -classpath "$ANDROID_JAR:$JSON_JAR" \
  -d "$OUT/classes" $(find "$APP/src" -name '*.java')

step "dex ($DEX, min API 10)"
if [ "$DEX" = d8 ]; then
  d8 --min-api 10 --release --lib "$ANDROID_JAR" --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')
else
  "$DX_BIN" --dex --min-sdk-version=10 --output="$OUT/dex/classes.dex" "$OUT/classes"
fi

RES="$APP/res"
if [ -f "$APP/icon-override.png" ]; then
  step "icon: using app/icon-override.png"
  rm -rf "$OUT/res" && cp -r "$APP/res" "$OUT/res"
  java -Djava.awt.headless=true tools/icon/ScaleIcon.java "$APP/icon-override.png" 96 "$OUT/res/drawable/ic_launcher.png"
  RES="$OUT/res"
fi

step "package (versionCode $VERSION_CODE)"
sed "s/android:versionCode=\"[0-9]*\"/android:versionCode=\"$VERSION_CODE\"/" "$APP/AndroidManifest.xml" > "$OUT/AndroidManifest.xml"
aapt package -f -M "$OUT/AndroidManifest.xml" -S "$RES" -A "$APP/assets" -I "$ANDROID_JAR" \
  -F "$OUT/$NAME.unsigned.apk"
(cd "$OUT/dex" && aapt add ../"$NAME".unsigned.apk classes.dex >/dev/null)
zipalign -f -p 4 "$OUT/$NAME.unsigned.apk" "$OUT/$NAME.aligned.apk"

step "sign (v1 JAR signature for Android 2.3)"
if [ ! -f "$KEYSTORE" ]; then
  mkdir -p "$(dirname "$KEYSTORE")"
  keytool -genkeypair -keystore "$KEYSTORE" -storetype PKCS12 -alias "$KEY_ALIAS" -keyalg RSA -keysize 2048 \
    -validity 10000 -storepass "$KEYSTORE_PASS" -keypass "$KEYSTORE_PASS" -dname "CN=Q50 Monitor" >/dev/null 2>&1
  echo "   created new signing key: $KEYSTORE (keep it – updates must use the same key)"
fi
apksigner sign --ks "$KEYSTORE" --ks-key-alias "$KEY_ALIAS" --ks-pass "pass:$KEYSTORE_PASS" \
  --key-pass "pass:$KEYSTORE_PASS" --min-sdk-version 10 --v1-signing-enabled true --v2-signing-enabled true \
  --out "$OUT/$NAME.apk" "$OUT/$NAME.aligned.apk"
rm -f "$OUT/$NAME.unsigned.apk" "$OUT/$NAME.aligned.apk" "$OUT/$NAME.apk.idsig"

step "verify"
apksigner verify --min-sdk-version 10 --print-certs "$OUT/$NAME.apk" | grep -E "Signer #1 certificate (DN|SHA-256)"
apksigner verify --min-sdk-version 10 -v "$OUT/$NAME.apk" | grep -q "Verified using v1 scheme (JAR signing): true" \
  || { echo "v1 signature missing" >&2; exit 1; }
badging=$(aapt dump badging "$OUT/$NAME.apk")
echo "$badging" | grep -q "sdkVersion:'10'" || { echo "minSdkVersion is not 10" >&2; exit 1; }
if unzip -l "$OUT/$NAME.apk" | grep -q " lib/"; then echo "native libraries are not allowed" >&2; exit 1; fi
echo "$badging" | grep -E "^package:|^application-label:"

step "wrap into .epk"
node tools/epk.ts build "$OUT/$NAME.apk" "$OUT/$NAME.epk"
node tools/epk.ts inspect "$OUT/$NAME.epk" | sed 's/^/   /'
( cd "$OUT" && sha256sum "$NAME.apk" "$NAME.epk" > "$NAME.sha256" )
echo "DONE -> $OUT/$NAME.epk (copy to the root of a FAT32 USB stick)"
