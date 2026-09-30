#!/usr/bin/env bash
set -euo pipefail

SCRIPT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$SCRIPT/.." && pwd)"
COURSE="$(cd "$REPO/../java-advanced-2026" && pwd)"

JAR="$SCRIPT/Implementor.jar"
TMP="$(mktemp -d)"

cleanup() {
  rm -rf "$TMP"
}
trap cleanup EXIT

rm -f "$JAR"

CP="$COURSE/artifacts/info.kgeorgiy.java.advanced.implementor.jar:$COURSE/artifacts/info.kgeorgiy.java.advanced.implementor.tools.jar"

javac \
  -cp "$CP" \
  -d "$TMP" \
  "$REPO/java-solutions/info/kgeorgiy/ja/Gebesht/implementor/Implementor.java"

jar cfm "$JAR" "$SCRIPT/MANIFEST.MF" -C "$TMP" .
