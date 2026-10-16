#!/usr/bin/env bash
# Builds maxcso for Android (arm64-v8a) from its release sources and puts it into the app as
# app/src/main/jniLibs/arm64-v8a/libmaxcso.so (a program, named like a library so Android installs it executable).
# maxcso writes CSO and ZSO files, compressed disc images that PlayStation 2 emulators open.
#
# Needs the Android SDK with an NDK and CMake (with Ninja), curl and tar. The sources are fetched once into a cache
# folder outside the repository: $THORTOOLS_NATIVE_CACHE, else %LOCALAPPDATA%\thortools-native or
# ~/.cache/thortools-native. patches/ holds Thor Tools' changes (progress on stderr when it isn't a terminal).
#
#   bash native/maxcso/build.sh
set -euo pipefail

VERSION="1.13.0"
SHA256="af9c05add1a1d199ec184d3471081af1b91d591b2473800ea989c882fb632730"
HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$HERE/../.." && pwd)"
CACHE="${THORTOOLS_NATIVE_CACHE:-${LOCALAPPDATA:-$HOME/.cache}/thortools-native}"
TARBALL="$CACHE/maxcso-$VERSION.tar.gz"
SRC="$CACHE/maxcso-$VERSION"
OUT="$REPO/app/src/main/jniLibs/arm64-v8a/libmaxcso.so"

native_path() { if command -v cygpath >/dev/null 2>&1; then cygpath -m "$1"; else echo "$1"; fi; }

# The release sources, checked, unpacked fresh and patched
mkdir -p "$CACHE"
if [ ! -f "$TARBALL" ]; then
  curl -sSL -o "$TARBALL" "https://github.com/unknownbrackets/maxcso/archive/refs/tags/v$VERSION.tar.gz"
fi
echo "$SHA256  $TARBALL" | sha256sum -c - >/dev/null || { echo "maxcso $VERSION: wrong checksum"; exit 1; }
rm -rf "$SRC"
(cd "$CACHE" && tar xzf "maxcso-$VERSION.tar.gz")
for patch in "$HERE"/patches/*.patch; do
  git -C "$SRC" apply --whitespace=nowarn "$(native_path "$patch")"
done

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
echo "maxcso $VERSION, NDK $(basename "$NDK"), CMake $(basename "$(dirname "$CMAKE_BIN")")"

BUILD="$CACHE/build-maxcso-$VERSION"
rm -rf "$BUILD"
"$CMAKE_BIN/cmake" -S "$(native_path "$HERE")" -B "$(native_path "$BUILD")" -G Ninja \
  -DCMAKE_MAKE_PROGRAM="$(native_path "$CMAKE_BIN/ninja")" \
  -DCMAKE_TOOLCHAIN_FILE="$(native_path "$NDK/build/cmake/android.toolchain.cmake")" \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-33 -DANDROID_STL=c++_static \
  -DCMAKE_BUILD_TYPE=Release -DCMAKE_POLICY_VERSION_MINIMUM=3.5 \
  -DMAXCSO_DIR="$(native_path "$SRC")"
"$CMAKE_BIN/cmake" --build "$(native_path "$BUILD")" --parallel --target maxcso

mkdir -p "$(dirname "$OUT")"
cp "$BUILD/maxcso" "$OUT"
echo "Wrote $OUT ($(wc -c < "$OUT") bytes, sha256 $(sha256sum "$OUT" | cut -d' ' -f1))"

# The licenses of everything in the program, shown in the app and kept beside this script
NOTICE="$REPO/app/src/main/assets/licenses/maxcso.txt"
mkdir -p "$(dirname "$NOTICE")"
{
  echo "maxcso $VERSION (CSO and ZSO compressor by Unknown W. Brackets), built for Android from its release sources."
  echo "https://github.com/unknownbrackets/maxcso (sources of this build: the v$VERSION tag, with the patches in"
  echo "native/maxcso/patches of Thor Tools' repository)."
  echo
  echo "=== maxcso: ISC ==="
  cat "$SRC/LICENSE.md"
  echo
  echo "=== 7-Zip's deflate encoder (Igor Pavlov): GNU LGPL 2.1 ==="
  echo "The source code of these parts is in the maxcso sources named above (folder 7zip)."
  cat "$SRC/7zip/DOC/License.txt" 2>/dev/null || true
  cat "$SRC/7zip/DOC/copying.txt" 2>/dev/null || true
  echo
  echo "=== Zopfli (Google): Apache License 2.0 ==="
  cat "$SRC/zopfli/COPYING"
  echo
  echo "=== libdeflate (Eric Biggers): MIT ==="
  cat "$SRC/libdeflate/COPYING"
  echo
  echo "=== libuv: MIT ==="
  cat "$SRC/libuv/LICENSE"
  echo
  echo "=== LZ4 (Yann Collet): BSD-2-Clause ==="
  cat "$SRC/lz4/lib/LICENSE"
  echo
  echo "=== zlib (the Android NDK's): zlib license ==="
  cat "$SRC/zlib/README" 2>/dev/null | sed -n '/Copyright notice/,$p' || true
} | tr -d '\r' > "$NOTICE"
cp "$NOTICE" "$HERE/NOTICE.txt"
echo "Wrote $NOTICE"
