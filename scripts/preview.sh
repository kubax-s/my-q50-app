#!/usr/bin/env bash
# Renders every tab of the real UI code to PNG on a desktop JVM (no Android needed):
# Android drawing calls go to java.awt via the stand-ins in tools/preview/shim.
# Output: build/preview/{pl,en}/N-tab.png and all.png (contact sheet).
set -euo pipefail
cd "$(dirname "$0")/.."
OUT=build/preview-classes
JSON_JAR=build/cache/json-20080701.jar
if [ ! -f "$JSON_JAR" ]; then
  echo "run scripts/build-apk.sh once first (it downloads $JSON_JAR)" >&2
  exit 1
fi
rm -rf "$OUT" && mkdir -p "$OUT"
APP_SRC=$(find app/src -name '*.java' ! -name MainActivity.java ! -name VehicleSensorSource.java ! -name DemoSource.java)
# shellcheck disable=SC2086
javac --release 8 -Xlint:-options -encoding UTF-8 -nowarn -d "$OUT" -classpath "$JSON_JAR" \
  $(find tools/preview/shim -name '*.java') $APP_SRC $(find tools/preview/src -name '*.java')
java -Djava.awt.headless=true -cp "$OUT:$JSON_JAR" pl.q50.app.ui.Preview
