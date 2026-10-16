#!/usr/bin/env bash
# Builds 7-Zip's 7zz (by Igor Pavlov) for Android (arm64-v8a) from its release sources and puts it into the app as
# app/src/main/jniLibs/arm64-v8a/lib7zz.so (a program, named like a library so Android installs it executable).
# 7zz writes the ZIP and 7z files of the classic consoles' games, tests them and lists their CRCs.
#
# Needs the Android SDK with an NDK (its clang and make), curl, tar with xz, and sh (Git Bash on Windows). The sources
# are fetched once into a cache folder outside the repository: $THORTOOLS_NATIVE_CACHE, else
# %LOCALAPPDATA%\thortools-native or ~/.cache/thortools-native. Built without RAR (DISABLE_RAR=1), so no part of it is
# under the unRAR licence restriction.
#
#   bash native/7zip/build.sh
set -euo pipefail

VERSION="26.04"
STEM="7z${VERSION//./}"
SHA256="9691944c0fe0d01bb49373a704fb983fd33bc98b1738695179dfbf99ac1734f6"
API=33
HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$HERE/../.." && pwd)"
CACHE="${THORTOOLS_NATIVE_CACHE:-${LOCALAPPDATA:-$HOME/.cache}/thortools-native}"
TARBALL="$CACHE/$STEM-src.tar.xz"
SRC="$CACHE/7zip-$VERSION"
OUT="$REPO/app/src/main/jniLibs/arm64-v8a/lib7zz.so"

native_path() { if command -v cygpath >/dev/null 2>&1; then cygpath -m "$1"; else echo "$1"; fi; }

# The release sources, checked and unpacked fresh (they sit at the top of the tarball, without a folder)
mkdir -p "$CACHE"
if [ ! -f "$TARBALL" ]; then
  curl -sSL -o "$TARBALL" "https://github.com/ip7z/7zip/releases/download/$VERSION/$STEM-src.tar.xz"
fi
echo "$SHA256  $TARBALL" | sha256sum -c - >/dev/null || { echo "7-Zip $VERSION: wrong checksum"; exit 1; }
rm -rf "$SRC"
mkdir -p "$SRC"
(cd "$CACHE" && tar -xJf "$STEM-src.tar.xz" -C "7zip-$VERSION")

# The SDK: ANDROID_HOME, ANDROID_SDK_ROOT or local.properties
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$SDK" ] && [ -f "$REPO/local.properties" ]; then
  SDK="$(grep '^sdk.dir=' "$REPO/local.properties" | cut -d= -f2- | sed 's/\\:/:/g; s/\\\\/\//g')"
fi
[ -n "$SDK" ] || { echo "Android SDK not found"; exit 1; }
SDK="$(cd "$SDK" && pwd)"
NDK="$(ls -d "$SDK"/ndk/* | sort -V | tail -1)"
HOST="$(ls -d "$NDK"/toolchains/llvm/prebuilt/* | head -1)"
BIN="$HOST/bin"
MAKE_BIN="$(ls "$NDK"/prebuilt/*/bin/make* 2>/dev/null | head -1)"
[ -d "$BIN" ] && [ -n "$MAKE_BIN" ] || { echo "Install an NDK from the SDK manager"; exit 1; }
CLANG="$BIN/clang"
CLANGXX="$BIN/clang++"
if [ -f "$CLANG.exe" ]; then CLANG="$CLANG.exe"; CLANGXX="$CLANGXX.exe"; fi
echo "7-Zip $VERSION, NDK $(basename "$NDK")"

# 7-Zip's own clang makefile for arm64 (its assembly included). It takes a host with SystemDrive set for MinGW, so
# those variables are cleared; warnings stay warnings; the C++ library is linked in.
TARGET="--target=aarch64-linux-android$API"
(
  cd "$SRC/CPP/7zip/Bundles/Alone2"
  env -u SystemDrive -u SYSTEMDRIVE -u MSYSTEM "$MAKE_BIN" -f ../../cmpl_clang_arm64.mak \
    CC="$(native_path "$CLANG") $TARGET -D_GNU_SOURCE" \
    CXX="$(native_path "$CLANGXX") $TARGET -D_GNU_SOURCE" \
    CFLAGS_WARN_WALL="-Wall" \
    LDFLAGS_STATIC_3="-static-libstdc++" \
    LIB2="-ldl" \
    DISABLE_RAR=1 \
    O=b/android \
    -j "$(nproc 2>/dev/null || echo 4)"
)
BUILT="$SRC/CPP/7zip/Bundles/Alone2/b/android/7zz"
"$BIN/llvm-strip$( [ -f "$BIN/llvm-strip.exe" ] && echo .exe)" -o "$BUILT.stripped" "$BUILT"

mkdir -p "$(dirname "$OUT")"
cp "$BUILT.stripped" "$OUT"
echo "Wrote $OUT ($(wc -c < "$OUT") bytes, sha256 $(sha256sum "$OUT" | cut -d' ' -f1))"

# The licences of everything in the program, shown in the app and kept beside this script
NOTICE="$REPO/app/src/main/assets/licenses/7zip.txt"
mkdir -p "$(dirname "$NOTICE")"
{
  echo "7-Zip $VERSION (7zz, by Igor Pavlov), built for Android from its release sources without RAR support"
  echo "(DISABLE_RAR=1). https://www.7-zip.org, https://github.com/ip7z/7zip (sources of this build: $STEM-src.tar.xz,"
  echo "built by native/7zip/build.sh of Thor Tools' repository)."
  echo
  echo "=== 7-Zip: License for use and distribution (DOC/License.txt) ==="
  cat "$SRC/DOC/License.txt"
  echo
  echo "=== GNU Lesser General Public License 2.1 (DOC/copying.txt) ==="
  cat "$SRC/DOC/copying.txt"
} | tr -d '\r' > "$NOTICE"
cp "$NOTICE" "$HERE/NOTICE.txt"
echo "Wrote $NOTICE"
