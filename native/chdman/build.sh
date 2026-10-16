#!/usr/bin/env bash
# Builds chdman for Android (arm64-v8a) from MAME's sources and puts it into the app as
# app/src/main/jniLibs/arm64-v8a/libchdman.so (a program, named like a library so Android installs it executable).
#
# Needs the Android SDK with an NDK and CMake (with Ninja), and git. MAME's sources are fetched once, sparsely, into a
# cache folder outside the repository: $THORTOOLS_NATIVE_CACHE, else %LOCALAPPDATA%\thortools-native or
# ~/.cache/thortools-native.
#
#   bash native/chdman/build.sh            (MAME 0.289)
#   bash native/chdman/build.sh mame0290   (another MAME release tag)
set -euo pipefail

TAG="${1:-mame0289}"
VERSION="0.${TAG#mame0}"
HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$HERE/../.." && pwd)"
CACHE="${THORTOOLS_NATIVE_CACHE:-${LOCALAPPDATA:-$HOME/.cache}/thortools-native}"
SRC="$CACHE/$TAG"
OUT="$REPO/app/src/main/jniLibs/arm64-v8a/libchdman.so"

native_path() { if command -v cygpath >/dev/null 2>&1; then cygpath -m "$1"; else echo "$1"; fi; }

# MAME's sources, only the parts chdman is built from
if [ ! -f "$SRC/src/tools/chdman.cpp" ]; then
  mkdir -p "$CACHE"
  rm -rf "$SRC"
  git clone --depth 1 --branch "$TAG" --filter=blob:none --sparse https://github.com/mamedev/mame.git "$SRC"
  MSYS_NO_PATHCONV=1 git -C "$SRC" sparse-checkout set --no-cone \
    /src/lib/util/ /src/osd/ /src/tools/chdman.cpp /3rdparty/expat/ /3rdparty/lzma/C/ /3rdparty/zlib/ \
    /3rdparty/zstd/ /3rdparty/flac/ /3rdparty/utf8proc/ /scripts/src/ /makefile
fi

# Every MAME file compiled must carry a permissive license (the 3rdparty libraries carry their own, see NOTICE.md)
bad=0
for f in "$SRC"/src/tools/chdman.cpp "$SRC"/src/lib/util/*.cpp "$SRC"/src/lib/util/*.h "$SRC"/src/osd/*.cpp \
  "$SRC"/src/osd/*.h "$SRC"/src/osd/modules/lib/osdlib_unix.cpp \
  "$SRC"/src/osd/modules/file/posix*.cpp; do
  case "$(basename "$f")" in nanosvg.cpp | path_to_regex.cpp | aes256cbc.cpp) continue ;; esac
  license="$(head -3 "$f" | grep -o -i -m1 'license:[^ ]*' || true)"
  case "$license" in
    license:BSD-3-Clause | license:MIT | license:Public*) ;;
    "") [ "$(basename "$f")" = disasmintf.cpp ] || { echo "No license line: $f"; bad=1; } ;;
    *) echo "Not permissive ($license): $f"; bad=1 ;;
  esac
done
[ "$bad" = 0 ] || { echo "License check failed"; exit 1; }

# The SDK: ANDROID_HOME, ANDROID_SDK_ROOT or local.properties
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$SDK" ] && [ -f "$REPO/local.properties" ]; then
  SDK="$(grep '^sdk.dir=' "$REPO/local.properties" | cut -d= -f2- | sed 's/\\:/:/g; s/\\\\/\//g')"
fi
[ -n "$SDK" ] || { echo "Android SDK not found"; exit 1; }
SDK="$(cd "$SDK" && pwd)"
NDK="$(ls -d "$SDK"/ndk/* | sort -V | tail -1)"
CMAKE_BIN="$(ls -d "$SDK"/cmake/* | sort -V | tail -1)/bin"
[ -d "$NDK" ] && [ -d "$CMAKE_BIN" ] || { echo "Install an NDK and CMake from the SDK manager"; exit 1; }
echo "MAME $TAG, NDK $(basename "$NDK"), CMake $(basename "$(dirname "$CMAKE_BIN")")"

BUILD="$CACHE/build-$TAG"
rm -rf "$BUILD"
"$CMAKE_BIN/cmake" -S "$(native_path "$HERE")" -B "$(native_path "$BUILD")" -G Ninja \
  -DCMAKE_MAKE_PROGRAM="$(native_path "$CMAKE_BIN/ninja")" \
  -DCMAKE_TOOLCHAIN_FILE="$(native_path "$NDK/build/cmake/android.toolchain.cmake")" \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-33 -DANDROID_STL=c++_static \
  -DCMAKE_BUILD_TYPE=Release \
  -DMAME_DIR="$(native_path "$SRC")" -DMAME_VERSION="$VERSION" -DMAME_VERSION_TAG="${TAG#mame}"
"$CMAKE_BIN/cmake" --build "$(native_path "$BUILD")" --parallel

mkdir -p "$(dirname "$OUT")"
cp "$BUILD/chdman" "$OUT"
echo "Wrote $OUT ($(wc -c < "$OUT") bytes, sha256 $(sha256sum "$OUT" | cut -d' ' -f1))"

# The licenses of everything in the program, shown in the app and kept beside this script
NOTICE="$REPO/app/src/main/assets/licenses/chdman.txt"
mkdir -p "$(dirname "$NOTICE")"
{
  echo "chdman $VERSION (MAME's Compressed Hunks of Data manager), built for Android from MAME's sources ($TAG)."
  echo "https://github.com/mamedev/mame"
  echo
  echo "=== MAME (chdman and the files it is built from): BSD-3-Clause ==="
  echo "Copyright (c) the MAME contributors (Aaron Giles, R. Belmont, Olivier Galibert, Vas Crabb and others);"
  echo "each source file names its copyright holders."
  echo
  sed '1,2d' "$HERE/bsd-3-clause.txt"
  echo
  echo "=== zlib ==="
  cat "$SRC/3rdparty/zlib/LICENSE"
  echo
  echo "=== LZMA SDK (Igor Pavlov): public domain ==="
  echo "The LZMA SDK is placed in the public domain."
  echo
  echo "=== libFLAC (Xiph.Org Foundation): BSD-3-Clause ==="
  cat "$SRC/3rdparty/flac/COPYING.Xiph"
  echo
  echo "=== Zstandard (Meta Platforms): BSD ==="
  cat "$SRC/3rdparty/zstd/LICENSE"
  echo
  echo "=== utf8proc: MIT ==="
  cat "$SRC/3rdparty/utf8proc/LICENSE.md"
  echo
  echo "=== Expat: MIT ==="
  cat "$SRC/3rdparty/expat/COPYING"
} | tr -d '\r' > "$NOTICE"
cp "$NOTICE" "$HERE/NOTICE.txt"
echo "Wrote $NOTICE"
