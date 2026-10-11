#!/bin/sh
# Computes the minimal set of JDK modules needed by the extracted Spring Boot app,
# for use with jlink. Usage: jdeps-modules.sh <extracted-dir> <output-file>
#
# jdeps aborts on jars that carry a module-info.class whose "requires" cannot be
# resolved (e.g. jakarta.transaction -> jakarta.interceptor). For those we fall
# back to analysing the exploded classes with the module descriptor removed.
set -eu
EXTRACTED="$1"
OUT="$2"
LIBS="$(find "$EXTRACTED" -path '*BOOT-INF/lib/*.jar' | sort)"
CP="$(printf '%s\n' "$LIBS" | tr '\n' ':')"
TMP="$(mktemp -d)"
MODS="$TMP/mods.txt"
: > "$MODS"

analyse() { # $1 = jar or dir
  jdeps --ignore-missing-deps --multi-release 21 -q --print-module-deps \
        --class-path "$CP" "$1" 2>/dev/null | tr ',' '\n' >> "$MODS"
}

analyse "$EXTRACTED/application/BOOT-INF/classes"
for jar in $LIBS; do
  if ! analyse "$jar"; then
    d="$TMP/$(basename "$jar" .jar)"
    mkdir -p "$d" && (cd "$d" && jar xf "$jar")
    find "$d" -name module-info.class -delete
    analyse "$d" || echo "WARN: jdeps failed for $(basename "$jar")" >&2
  fi
done

# Modules jdeps cannot see through reflection / JNI / service loading.
cat >> "$MODS" <<'MODLIST'
java.base
java.desktop
java.instrument
java.logging
java.management
java.naming
java.net.http
java.security.jgss
java.security.sasl
java.sql
java.xml
jdk.crypto.cryptoki
jdk.management
jdk.unsupported
jdk.zipfs
MODLIST

grep -v '^$' "$MODS" | sort -u | paste -sd, - > "$OUT"
rm -rf "$TMP"
echo "jlink modules: $(cat "$OUT")"
