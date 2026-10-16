#!/usr/bin/env bash
# Builds Thor Tools' z3ds (z3ds.c beside this script) with Zstandard (zstd, BSD licence) for Android (arm64-v8a) and
# puts it into the app as app/src/main/jniLibs/arm64-v8a/libz3ds.so (a program, named like a library so Android
# installs it executable). z3ds makes, checks and unpacks the Z3DS files (.zcci, .zcxi) Azahar opens.
#
# Needs the Android SDK with an NDK (its clang), curl, tar and sh (Git Bash on Windows). zstd's release sources are
# fetched once into a cache folder outside the repository: $THORTOOLS_NATIVE_CACHE, else %LOCALAPPDATA%\thortools-native
# or ~/.cache/thortools-native.
#
#   bash native/z3ds/build.sh
set -euo pipefail

ZSTD="1.5.7"
SHA256="eb33e51f49a15e023950cd7825ca74a4a2b43db8354825ac24fc1b7ee09e6fa3"
API=33
HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$HERE/../.." && pwd)"
CACHE="${THORTOOLS_NATIVE_CACHE:-${LOCALAPPDATA:-$HOME/.cache}/thortools-native}"
TARBALL="$CACHE/zstd-$ZSTD.tar.gz"
SRC="$CACHE/zstd-$ZSTD"
OUT="$REPO/app/src/main/jniLibs/arm64-v8a/libz3ds.so"
BUILD="$CACHE/z3ds-build"

native_path() { if command -v cygpath >/dev/null 2>&1; then cygpath -m "$1"; else echo "$1"; fi; }

# zstd's release sources, checked and unpacked fresh
mkdir -p "$CACHE"
if [ ! -f "$TARBALL" ]; then
  curl -sSL -o "$TARBALL" "https://github.com/facebook/zstd/releases/download/v$ZSTD/zstd-$ZSTD.tar.gz"
fi
echo "$SHA256  $TARBALL" | sha256sum -c - >/dev/null || { echo "zstd $ZSTD: wrong checksum"; exit 1; }
rm -rf "$SRC"
(cd "$CACHE" && tar -xzf "zstd-$ZSTD.tar.gz" "zstd-$ZSTD/lib" "zstd-$ZSTD/LICENSE")

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
[ -d "$BIN" ] || { echo "Install an NDK from the SDK manager"; exit 1; }
CLANG="$BIN/clang"
[ -f "$CLANG.exe" ] && CLANG="$CLANG.exe"
EXE="$( [ -f "$BIN/llvm-strip.exe" ] && echo .exe || true)"
echo "z3ds with zstd $ZSTD, NDK $(basename "$NDK")"

# Only zstd's library parts it uses: common, compress, decompress (no dictionary builder, no legacy formats, no
# assembly). Each file is compiled on its own, then linked with z3ds.c.
rm -rf "$BUILD"
mkdir -p "$BUILD"
FLAGS=(--target=aarch64-linux-android$API -O2 -fPIE -ffunction-sections -fdata-sections
  -DZSTD_DISABLE_ASM -DZSTD_LEGACY_SUPPORT=0 -DXXH_NAMESPACE=ZSTD_
  -I"$(native_path "$SRC/lib")" -I"$(native_path "$SRC/lib/common")")
OBJECTS=()
for file in "$SRC"/lib/common/*.c "$SRC"/lib/compress/*.c "$SRC"/lib/decompress/*.c; do
  object="$BUILD/$(basename "$(dirname "$file")")-$(basename "$file" .c).o"
  "$CLANG" "${FLAGS[@]}" -c "$(native_path "$file")" -o "$(native_path "$object")"
  OBJECTS+=("$(native_path "$object")")
done
"$CLANG" "${FLAGS[@]}" -Wall -Wextra -c "$(native_path "$HERE/z3ds.c")" -o "$(native_path "$BUILD/z3ds.o")"
"$CLANG" --target=aarch64-linux-android$API -pie -Wl,--gc-sections \
  "$(native_path "$BUILD/z3ds.o")" "${OBJECTS[@]}" -o "$(native_path "$BUILD/z3ds")"
"$BIN/llvm-strip$EXE" -o "$(native_path "$BUILD/z3ds.stripped")" "$(native_path "$BUILD/z3ds")"

mkdir -p "$(dirname "$OUT")"
cp "$BUILD/z3ds.stripped" "$OUT"
echo "Wrote $OUT ($(wc -c < "$OUT") bytes, sha256 $(sha256sum "$OUT" | cut -d' ' -f1))"

# The licence of zstd, shown in the app and kept beside this script (z3ds.c is Thor Tools' own code)
NOTICE="$REPO/app/src/main/assets/licenses/zstd.txt"
mkdir -p "$(dirname "$NOTICE")"
{
  echo "Zstandard (zstd) $ZSTD, by Meta Platforms, Inc. and affiliates, used under its BSD licence by Thor Tools'"
  echo "z3ds tool (libz3ds.so), which makes and unpacks the Z3DS files of 3DS games."
  echo "https://github.com/facebook/zstd (sources of this build: zstd-$ZSTD.tar.gz, built by native/z3ds/build.sh"
  echo "of Thor Tools' repository)."
  echo
  echo "=== zstd: LICENSE ==="
  cat "$SRC/LICENSE"
} | tr -d '\r' > "$NOTICE"
cp "$NOTICE" "$HERE/NOTICE.txt"
echo "Wrote $NOTICE"
