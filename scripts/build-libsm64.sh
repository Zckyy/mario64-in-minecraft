#!/usr/bin/env bash
# Builds sm64.dll (Windows) / libsm64.so (Linux) / libsm64.dylib (macOS) from libsm64 + the n64decomp/sm64 sources,
# with this project's music-volume patch applied. Nothing from Nintendo is downloaded here except the decomp's own
# Mario geometry *source* (fetched by libsm64's import script); the art/sound data is read from YOUR ROM at runtime.
#
# Windows: run from Git Bash with a MinGW gcc on PATH (w64devkit or MSYS2 MinGW64), plus Python 3.
# Linux/macOS: needs git, make, gcc/clang and Python 3.
set -euo pipefail

LIBSM64_COMMIT=fd11813208272b4271d92bd92feb8f3fdbe61be5   # tested commit (2026-02-13)
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="${1:-$ROOT/build/libsm64}"

if [ ! -d "$WORK/.git" ]; then
  git clone https://github.com/libsm64/libsm64 "$WORK"
fi
cd "$WORK"
git fetch --quiet origin && git checkout --quiet "$LIBSM64_COMMIT"
git reset --quiet --hard && git clean --quiet -fdx -e src/decomp/mario
git apply "$ROOT/patches/libsm64-music-volume.patch"

# (on Windows "python3" may be a Microsoft Store stub that exists but doesn't run, so test it)
PY=""
for c in python3 python py; do
  if "$c" -c "import sys" >/dev/null 2>&1; then PY="$c"; break; fi
done
[ -n "$PY" ] || { echo "Python 3 is required (https://www.python.org/downloads/)"; exit 1; }
"$PY" import-mario-geo.py
make -j"$(getconf _NPROCESSORS_ONLN 2>/dev/null || echo 4)" lib CC="${CC:-gcc}"

echo
echo "Built:"; ls -la dist/
echo "Copy dist/sm64.dll (or libsm64.so / libsm64.dylib) into <minecraft game dir>/config/mario64/"
