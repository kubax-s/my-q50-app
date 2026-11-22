#!/usr/bin/env bash
# Runs the plain-JVM tests for the Android-independent classes in app/src/pl/q50/app/data.
set -euo pipefail
cd "$(dirname "$0")/.."
OUT=build/test-classes
rm -rf "$OUT" && mkdir -p "$OUT"
javac --release 8 -Xlint:-options -encoding UTF-8 -d "$OUT" \
  app/src/pl/q50/app/data/Fmt.java app/src/pl/q50/app/data/SignalDef.java app/src/pl/q50/app/data/Channel.java \
  app/test/pl/q50/app/data/DataTest.java
java -cp "$OUT" pl.q50.app.data.DataTest
