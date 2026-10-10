#!/usr/bin/env bash
# Checks that every native library in an APK (or AAB base module) can be loaded on
# devices with 16 KB memory pages:
#   1. each ELF LOAD segment is aligned to at least 16 KB (0x4000), and
#   2. (APK only, if build-tools 35+ is installed) zipalign -P 16 accepts the archive.
# Usage: scripts/check-16kb-alignment.sh path/to/app.apk|app.aab
set -euo pipefail

archive="${1:?usage: $0 <apk-or-aab>}"
command -v readelf >/dev/null || { echo "readelf (binutils) is required" >&2; exit 2; }

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
unzip -q -o "$archive" '*.so' -d "$work" 2>/dev/null || true

mapfile -t libs < <(find "$work" -name '*.so' | sort)
if [ "${#libs[@]}" -eq 0 ]; then
  echo "No native libraries found in $archive."
else
  failed=0
  for lib in "${libs[@]}"; do
    rel="${lib#"$work"/}"
    # Smallest alignment among LOAD segments, compared numerically (e.g. 0x1000 < 0x4000).
    min_align=""
    for a in $(readelf -lW "$lib" | awk '$1=="LOAD"{print $NF}'); do
      v=$((a))
      if [ -z "$min_align" ] || [ "$v" -lt "$min_align" ]; then min_align=$v; fi
    done
    if [ -z "$min_align" ]; then
      echo "UNKNOWN  $rel (no LOAD segments found)"; failed=1; continue
    fi
    if [ "$min_align" -ge 16384 ]; then
      printf 'OK        %s (align 0x%x)\n' "$rel" "$min_align"
    else
      printf 'TOO_SMALL %s (align 0x%x)\n' "$rel" "$min_align"; failed=1
    fi
  done
  [ "$failed" -eq 0 ] || { echo "Some libraries are not 16 KB aligned." >&2; exit 1; }
fi

if [[ "$archive" == *.apk ]]; then
  zipalign=""
  if [ -n "${ANDROID_HOME:-}" ]; then
    zipalign="$(ls -d "$ANDROID_HOME"/build-tools/*/zipalign 2>/dev/null | sort -V | tail -n1 || true)"
  fi
  if [ -n "$zipalign" ]; then
    "$zipalign" -c -P 16 -v 4 "$archive" >/dev/null && echo "zipalign -P 16: OK"
  else
    echo "zipalign not found (set ANDROID_HOME with build-tools 35+); skipped the zip alignment check."
  fi
fi
