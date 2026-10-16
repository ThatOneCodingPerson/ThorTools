#!/usr/bin/env bash
# Builds nodtool (from nod, by Luke Street) for Android (arm64-v8a) from its release sources and puts it into the app as
# app/src/main/jniLibs/arm64-v8a/libnodtool.so (a program, named like a library so Android installs it executable).
# nodtool reads and writes GameCube and Wii disc images: ISO, RVZ, WIA, WBFS, CISO and GCZ.
#
# Needs the Android SDK with an NDK, Rust (rustup with the aarch64-linux-android target), curl, tar and python. The
# sources are fetched once into a cache folder outside the repository: $THORTOOLS_NATIVE_CACHE, else
# %LOCALAPPDATA%\thortools-native or ~/.cache/thortools-native; cargo fetches the crates Cargo.lock names, checked by
# their checksums. patches/ holds Thor Tools' changes (progress lines, a thread count, Wii hash areas kept in RVZ).
#
#   bash native/nod/build.sh
set -euo pipefail

VERSION="2.0.0-alpha.12"
SHA256="e696c90d37cbf9f1daf9edafca021c2a4006a9b9d7261af6f573f602522aa5cc"
TARGET="aarch64-linux-android"
API=33
HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$HERE/../.." && pwd)"
CACHE="${THORTOOLS_NATIVE_CACHE:-${LOCALAPPDATA:-$HOME/.cache}/thortools-native}"
TARBALL="$CACHE/nod-$VERSION.tar.gz"
SRC="$CACHE/nod-$VERSION"
OUT="$REPO/app/src/main/jniLibs/arm64-v8a/libnodtool.so"

native_path() { if command -v cygpath >/dev/null 2>&1; then cygpath -m "$1"; else echo "$1"; fi; }

# The release sources, checked, unpacked fresh and patched
mkdir -p "$CACHE"
if [ ! -f "$TARBALL" ]; then
  curl -sSL -o "$TARBALL" "https://github.com/encounter/nod-rs/archive/refs/tags/v$VERSION.tar.gz"
fi
echo "$SHA256  $TARBALL" | sha256sum -c - >/dev/null || { echo "nod $VERSION: wrong checksum"; exit 1; }
rm -rf "$SRC"
(cd "$CACHE" && tar xzf "nod-$VERSION.tar.gz")
for patch in "$HERE"/patches/*.patch; do
  git -C "$SRC" apply --whitespace=nowarn "$(native_path "$patch")"
done

# Rust: cargo on the PATH, else rustup's own folder
CARGO="$(command -v cargo || true)"
[ -n "$CARGO" ] || for c in "${USERPROFILE:-$HOME}/.cargo/bin/cargo.exe" "$HOME/.cargo/bin/cargo"; do [ -x "$c" ] && CARGO="$c"; done
[ -n "$CARGO" ] || { echo "Rust not found: install rustup, then: rustup target add $TARGET"; exit 1; }

# The SDK: ANDROID_HOME, ANDROID_SDK_ROOT or local.properties
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$SDK" ] && [ -f "$REPO/local.properties" ]; then
  SDK="$(grep '^sdk.dir=' "$REPO/local.properties" | cut -d= -f2- | sed 's/\\:/:/g; s/\\\\/\//g')"
fi
[ -n "$SDK" ] || { echo "Android SDK not found"; exit 1; }
SDK="$(cd "$SDK" && pwd)"
NDK="$(ls -d "$SDK"/ndk/* | sort -V | tail -1)"
BIN="$(ls -d "$NDK"/toolchains/llvm/prebuilt/*/bin | head -1)"
[ -d "$BIN" ] || { echo "Install an NDK from the SDK manager"; exit 1; }
CC="$BIN/$TARGET$API-clang"
[ -f "$CC.cmd" ] && CC="$CC.cmd"
AR="$BIN/llvm-ar"
[ -f "$AR.exe" ] && AR="$AR.exe"
echo "nod $VERSION, NDK $(basename "$NDK"), $("$CARGO" --version)"

# The C libraries inside the crates (zstd, xz, bzip2, zlib) and the program, built with the NDK's clang
export CARGO_TARGET_DIR="$(native_path "$CACHE/build-nod-$VERSION")"
export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$(native_path "$CC")"
export CC_aarch64_linux_android="$(native_path "$CC")"
export AR_aarch64_linux_android="$(native_path "$AR")"
# On Windows with Rust's GNU toolchain, the build scripts run on the PC and need a C compiler for it (nodtool's packs
# its Redump lists with zstd) and a dlltool: llvm-mingw, a portable toolchain, fetched once into the cache, when the PC
# has no gcc
if [ -f "$BIN/llvm-dlltool.exe" ] && ! command -v gcc >/dev/null 2>&1; then
  MINGW_VERSION="20261006"
  MINGW_SHA256="24847b37a951c8583c3f7ec061de2eb87f6394215c8ef3fdc4565ed0835396e1"
  MINGW_NAME="llvm-mingw-$MINGW_VERSION-msvcrt-x86_64"
  if [ ! -f "$CACHE/$MINGW_NAME.zip" ]; then
    curl -sSL -o "$CACHE/$MINGW_NAME.zip" "https://github.com/mstorsjo/llvm-mingw/releases/download/$MINGW_VERSION/$MINGW_NAME.zip"
  fi
  echo "$MINGW_SHA256  $CACHE/$MINGW_NAME.zip" | sha256sum -c - >/dev/null || { echo "llvm-mingw: wrong checksum"; exit 1; }
  if [ ! -d "$CACHE/$MINGW_NAME" ]; then
    python -I -c "import sys, zipfile; zipfile.ZipFile(sys.argv[1]).extractall(sys.argv[2])" \
      "$(native_path "$CACHE/$MINGW_NAME.zip")" "$(native_path "$CACHE")"
  fi
  mkdir -p "$CACHE/nod-tools"
  cp "$BIN/llvm-dlltool.exe" "$CACHE/nod-tools/dlltool.exe"
  PATH="$CACHE/nod-tools:$PATH"
  export CC_x86_64_pc_windows_gnu="$(native_path "$CACHE/$MINGW_NAME/bin/x86_64-w64-mingw32-clang.exe")"
  export AR_x86_64_pc_windows_gnu="$(native_path "$CACHE/$MINGW_NAME/bin/llvm-ar.exe")"
fi
(cd "$SRC" && "$CARGO" build --locked --profile release-lto --config 'profile.release-lto.strip=true' --target "$TARGET" -p nodtool)

mkdir -p "$(dirname "$OUT")"
cp "$CACHE/build-nod-$VERSION/$TARGET/release-lto/nodtool" "$OUT"
echo "Wrote $OUT ($(wc -c < "$OUT") bytes, sha256 $(sha256sum "$OUT" | cut -d' ' -f1))"

# The licences of everything in the program, shown in the app and kept beside this script
NOTICE="$REPO/app/src/main/assets/licenses/nod.txt"
mkdir -p "$(dirname "$NOTICE")"
(cd "$SRC" && "$CARGO" metadata --locked --format-version 1 --filter-platform "$TARGET") > "$CACHE/nod-metadata.json"
python -I "$HERE/notice.py" "$(native_path "$CACHE/nod-metadata.json")" "$VERSION" | tr -d '\r' > "$NOTICE"
cp "$NOTICE" "$HERE/NOTICE.txt"
echo "Wrote $NOTICE"
