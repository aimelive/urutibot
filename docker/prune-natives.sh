#!/bin/sh
# Strips native libraries for other operating systems / CPUs out of the
# extracted dependency jars so the image only carries what its target needs.
# Usage: prune-natives.sh <extracted-dir> <amd64|arm64>
set -eu
EXTRACTED="$1"
case "$2" in
  amd64) ONNX=linux-x64;     TOK=linux-x86_64 ;;
  arm64) ONNX=linux-aarch64; TOK=linux-aarch64 ;;
  *) echo "unsupported TARGETARCH: $2" >&2; exit 1 ;;
esac
LIB="$EXTRACTED/dependencies/BOOT-INF/lib"

prune() { # $1 = jar glob, $2 = directory inside the jar holding per-platform dirs, $3 = platform dir to keep
  jar="$(ls "$LIB"/$1)"; root="$2"; keep="$3"
  before=$(stat -c %s "$jar")
  tmp="$(mktemp -d)"
  (cd "$tmp" && jar xf "$jar")
  [ -d "$tmp/$root/$keep" ] || { echo "ERROR: $root/$keep not found in $(basename "$jar")" >&2; exit 1; }
  for d in "$tmp/$root"/*/; do
    [ "$(basename "$d")" = "$keep" ] || rm -rf "$d"
  done
  mv "$tmp/META-INF/MANIFEST.MF" "$tmp.mf"
  rm -f "$jar"
  jar cfm "$jar" "$tmp.mf" -C "$tmp" .
  rm -rf "$tmp" "$tmp.mf"
  printf '%-50s %7d KB -> %6d KB\n' "$(basename "$jar")" $((before / 1024)) $(( $(stat -c %s "$jar") / 1024 ))
}

prune 'onnxruntime-*.jar' ai/onnxruntime/native "$ONNX"
prune 'tokenizers-*.jar'  native/lib            "$TOK"
