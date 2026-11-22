#!/usr/bin/env bash
# Runs the plain-JVM tests for the Android-independent classes (data formatting, i18n, logic).
set -euo pipefail
cd "$(dirname "$0")/.."
OUT=build/test-classes
rm -rf "$OUT" && mkdir -p "$OUT"
# shellcheck disable=SC2046
javac --release 8 -Xlint:all,-options -Werror -encoding UTF-8 -d "$OUT" \
  app/src/pl/q50/app/data/Fmt.java app/src/pl/q50/app/data/SignalDef.java app/src/pl/q50/app/data/Channel.java \
  app/src/pl/q50/app/i18n/Strings.java $(find app/src/pl/q50/app/logic -name '*.java') \
  $(find app/test -name '*.java')
java -cp "$OUT" pl.q50.app.data.DataTest
java -cp "$OUT" pl.q50.app.logic.LogicTest
