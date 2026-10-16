#!/usr/bin/env bash
# Builds Thor Tools' wua (wua.cpp beside this script) with ZArchive (Exzap, MIT No Attribution; its SHA-256 code public
# domain) and Zstandard (zstd, BSD licence) for Android (arm64-v8a), and puts it into the app as
# app/src/main/jniLibs/arm64-v8a/libwua.so (a program, named like a library so Android installs it executable). wua
# packs Wii U games kept as folders into the WUA files Cemu opens, checks them and unpacks them.
#
# Needs the Android SDK with an NDK (its clang), curl, tar and sh (Git Bash on Windows). The sources are fetched once
# into a cache folder outside the repository: $THORTOOLS_NATIVE_CACHE, else %LOCALAPPDATA%\thortools-native or
# ~/.cache/thortools-native.
#
#   bash native/wua/build.sh
set -euo pipefail

ZARCHIVE="965b66c8d67b6b7e30fd63b3b75aa91a99ff303b"
ZARCHIVE_SHA256="8811f42220127424c4520b4cff71dc41b05449e6609a0b84327fdf2842abd15f"
ZSTD="1.5.7"
ZSTD_SHA256="eb33e51f49a15e023950cd7825ca74a4a2b43db8354825ac24fc1b7ee09e6fa3"
API=33
HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$HERE/../.." && pwd)"
CACHE="${THORTOOLS_NATIVE_CACHE:-${LOCALAPPDATA:-$HOME/.cache}/thortools-native}"
OUT="$REPO/app/src/main/jniLibs/arm64-v8a/libwua.so"
BUILD="$CACHE/wua-build"
ZSRC="$BUILD/zstd-$ZSTD"
ASRC="$BUILD/ZArchive-$ZARCHIVE"

native_path() { if command -v cygpath >/dev/null 2>&1; then cygpath -m "$1"; else echo "$1"; fi; }

# Both release sources, checked and unpacked fresh
mkdir -p "$CACHE"
[ -f "$CACHE/zstd-$ZSTD.tar.gz" ] ||
  curl -sSL -o "$CACHE/zstd-$ZSTD.tar.gz" "https://github.com/facebook/zstd/releases/download/v$ZSTD/zstd-$ZSTD.tar.gz"
[ -f "$CACHE/zarchive-$ZARCHIVE.tar.gz" ] ||
  curl -sSL -o "$CACHE/zarchive-$ZARCHIVE.tar.gz" "https://github.com/Exzap/ZArchive/archive/$ZARCHIVE.tar.gz"
echo "$ZSTD_SHA256  $CACHE/zstd-$ZSTD.tar.gz" | sha256sum -c - >/dev/null || { echo "zstd $ZSTD: wrong checksum"; exit 1; }
echo "$ZARCHIVE_SHA256  $CACHE/zarchive-$ZARCHIVE.tar.gz" | sha256sum -c - >/dev/null || { echo "ZArchive: wrong checksum"; exit 1; }
rm -rf "$BUILD"
mkdir -p "$BUILD/obj"
(cd "$CACHE" && tar -xzf "zstd-$ZSTD.tar.gz" -C "$BUILD" "zstd-$ZSTD/lib" "zstd-$ZSTD/LICENSE")
(cd "$CACHE" && tar -xzf "zarchive-$ZARCHIVE.tar.gz" -C "$BUILD")
# patches/ holds Thor Tools' change to ZArchive: its blocks compressed on several threads at once (the file the same)
# and its SHA-256 on the ARMv8 instructions where the processor has them
for patch in "$HERE"/patches/*.patch; do
  git -C "$ASRC" apply --whitespace=nowarn "$(native_path "$patch")"
done

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
CLANGXX="$BIN/clang++"
if [ -f "$CLANG.exe" ]; then CLANG="$CLANG.exe"; CLANGXX="$CLANGXX.exe"; fi
EXE="$( [ -f "$BIN/llvm-strip.exe" ] && echo .exe || true)"
echo "wua with ZArchive ${ZARCHIVE:0:7} and zstd $ZSTD, NDK $(basename "$NDK")"

# zstd's library parts (common, compress, decompress), ZArchive's library (not its own command-line tool), then wua.cpp.
# +crypto lets the SHA code use the ARMv8 SHA instructions; it checks at run time that the processor has them
TARGET="--target=aarch64-linux-android$API"
CFLAGS=("$TARGET" -march=armv8-a+crypto -O2 -fPIE -ffunction-sections -fdata-sections -DZSTD_DISABLE_ASM -DZSTD_LEGACY_SUPPORT=0
  -DXXH_NAMESPACE=ZSTD_ -I"$(native_path "$ZSRC/lib")" -I"$(native_path "$ZSRC/lib/common")")
CXXFLAGS=("$TARGET" -march=armv8-a+crypto -O2 -fPIE -ffunction-sections -fdata-sections -std=c++20
  -isystem "$(native_path "$ASRC/include")" -I"$(native_path "$ASRC/src")" -I"$(native_path "$ZSRC/lib")")
OBJECTS=()
for file in "$ZSRC"/lib/common/*.c "$ZSRC"/lib/compress/*.c "$ZSRC"/lib/decompress/*.c "$ASRC/src/sha_256.c"; do
  object="$BUILD/obj/$(basename "$(dirname "$file")")-$(basename "$file" .c).o"
  "$CLANG" "${CFLAGS[@]}" -c "$(native_path "$file")" -o "$(native_path "$object")"
  OBJECTS+=("$(native_path "$object")")
done
for file in "$ASRC/src/zarchivereader.cpp" "$ASRC/src/zarchivewriter.cpp"; do
  object="$BUILD/obj/$(basename "$file" .cpp).o"
  "$CLANGXX" "${CXXFLAGS[@]}" -c "$(native_path "$file")" -o "$(native_path "$object")"
  OBJECTS+=("$(native_path "$object")")
done
"$CLANGXX" "${CXXFLAGS[@]}" -Wall -Wextra -c "$(native_path "$HERE/wua.cpp")" -o "$(native_path "$BUILD/obj/wua.o")"
"$CLANGXX" "$TARGET" -pie -static-libstdc++ -Wl,--gc-sections \
  "$(native_path "$BUILD/obj/wua.o")" "${OBJECTS[@]}" -o "$(native_path "$BUILD/wua")"
"$BIN/llvm-strip$EXE" -o "$(native_path "$BUILD/wua.stripped")" "$(native_path "$BUILD/wua")"

mkdir -p "$(dirname "$OUT")"
cp "$BUILD/wua.stripped" "$OUT"
echo "Wrote $OUT ($(wc -c < "$OUT") bytes, sha256 $(sha256sum "$OUT" | cut -d' ' -f1))"

# The licences of what is in the program (wua.cpp is Thor Tools' own code), shown in the app and kept beside this script
NOTICE="$REPO/app/src/main/assets/licenses/zarchive.txt"
mkdir -p "$(dirname "$NOTICE")"
{
  echo "ZArchive (Exzap), commit $ZARCHIVE, used under the MIT No Attribution licence by Thor Tools' wua tool"
  echo "(libwua.so), which packs Wii U games into the WUA files Cemu opens. https://github.com/Exzap/ZArchive. Its SHA-256"
  echo "code (src/sha_256.c) is in the public domain. wua also uses Zstandard (zstd) $ZSTD, by Meta Platforms, Inc. and"
  echo "affiliates, under its BSD licence (https://github.com/facebook/zstd). Built by native/wua/build.sh of Thor"
  echo "Tools' repository."
  echo
  echo "=== ZArchive: LICENSE ==="
  cat "$ASRC/LICENSE"
  echo
  echo "=== zstd: LICENSE ==="
  cat "$ZSRC/LICENSE"
} | tr -d '\r' > "$NOTICE"
cp "$NOTICE" "$HERE/NOTICE.txt"
echo "Wrote $NOTICE"
