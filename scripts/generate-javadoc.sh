#!/usr/bin/env bash
set -euo pipefail

SRC="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$SRC/.." && pwd)"
COURSE="$(cd "$REPO/../java-advanced-2026" && pwd)"
OUT_DIR="$REPO/javadoc"
IMPL="java-solutions/info/kgeorgiy/ja/Gebesht/implementor"
TEST="modules/info.kgeorgiy.java.advanced.implementor/info/kgeorgiy/java/advanced/implementor"
TOOLS="modules/info.kgeorgiy.java.advanced.implementor.tools/info/kgeorgiy/java/advanced/implementor/tools"

IMPLEMENTOR="$REPO/$IMPL/Implementor.java"
PACKAGE_INFO="$REPO/$IMPL/package-info.java"

IMPLER="$COURSE/$TEST/Impler.java"
IMPLER_COMP="$COURSE/$TEST/ImplerException.java"
JAR_IMPLER="$COURSE/$TOOLS/JarImpler.java"

rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"

API_LINK="https://docs.oracle.com/en/java/javase/25/docs/api/"

FILES=(
  "$IMPLEMENTOR"
  "$IMPLER"
  "$IMPLER_COMP"
  "$JAR_IMPLER"
  "$PACKAGE_INFO"
)

javadoc \
  -d "$OUT_DIR" \
  -private \
  -author \
  -version \
  -charset UTF-8 \
  -Xdoclint:all \
  -link "$API_LINK" \
  --class-path "$COURSE/artifacts/info.kgeorgiy.java.advanced.implementor.jar:$COURSE/artifacts/info.kgeorgiy.java.advanced.implementor.tools.jar" \
  "${FILES[@]}"
