# chdman from MAME's sources, built as one program for Android (arm64). The file lists follow MAME's own
# scripts/src/tools.lua (chdman), lib.lua (utils), osd/sdl.lua (ocore) and 3rdparty.lua; SDL itself is not needed.
cmake_minimum_required(VERSION 3.22)
project(chdman C CXX)

if(NOT MAME_DIR)
  message(FATAL_ERROR "Set MAME_DIR to a MAME source tree")
endif()
if(NOT MAME_VERSION)
  message(FATAL_ERROR "Set MAME_VERSION, for example 0.289")
endif()

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_C_STANDARD 11)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

add_compile_definitions(
  NDEBUG
  LSB_FIRST
  CRLF=2
  XML_STATIC
  FLAC__NO_DLL
  UTF8PROC_STATIC
)
add_compile_options(-O2 -fno-strict-aliasing -w)

set(M ${MAME_DIR})

# version.cpp, as MAME's makefile writes it
set(VERSION_CPP ${CMAKE_BINARY_DIR}/version.cpp)
file(WRITE ${VERSION_CPP}
  "#define BARE_BUILD_VERSION \"${MAME_VERSION}\"\n"
  "#define BARE_VCS_REVISION \"mame${MAME_VERSION_TAG}\"\n"
  "extern const char bare_build_version[];\n"
  "extern const char bare_vcs_revision[];\n"
  "extern const char build_version[];\n"
  "const char bare_build_version[] = BARE_BUILD_VERSION;\n"
  "const char bare_vcs_revision[] = BARE_VCS_REVISION;\n"
  "const char build_version[] = BARE_BUILD_VERSION \" (\" BARE_VCS_REVISION \")\";\n")

# 3rdparty
add_library(zlib STATIC
  ${M}/3rdparty/zlib/adler32.c ${M}/3rdparty/zlib/compress.c ${M}/3rdparty/zlib/crc32.c
  ${M}/3rdparty/zlib/deflate.c ${M}/3rdparty/zlib/inffast.c ${M}/3rdparty/zlib/inflate.c
  ${M}/3rdparty/zlib/infback.c ${M}/3rdparty/zlib/inftrees.c ${M}/3rdparty/zlib/trees.c
  ${M}/3rdparty/zlib/uncompr.c ${M}/3rdparty/zlib/zutil.c)
target_compile_definitions(zlib PRIVATE ZLIB_CONST)

set(LZMA ${M}/3rdparty/lzma/C)
add_library(7z STATIC
  ${LZMA}/7zAlloc.c ${LZMA}/7zArcIn.c ${LZMA}/7zBuf.c ${LZMA}/7zBuf2.c ${LZMA}/7zCrc.c ${LZMA}/7zCrcOpt.c
  ${LZMA}/7zDec.c ${LZMA}/7zFile.c ${LZMA}/7zStream.c ${LZMA}/Aes.c ${LZMA}/AesOpt.c ${LZMA}/Alloc.c
  ${LZMA}/Bcj2.c ${LZMA}/Bra.c ${LZMA}/Bra86.c ${LZMA}/BraIA64.c ${LZMA}/CpuArch.c ${LZMA}/Delta.c
  ${LZMA}/LzFind.c ${LZMA}/LzFindOpt.c ${LZMA}/Lzma2Dec.c ${LZMA}/LzmaDec.c ${LZMA}/LzmaEnc.c
  ${LZMA}/Ppmd7.c ${LZMA}/Ppmd7Dec.c ${LZMA}/Sha256.c ${LZMA}/Sha256Opt.c ${LZMA}/Sort.c)
target_compile_definitions(7z PRIVATE Z7_PPMD_SUPPORT Z7_ST)

set(ZSTD ${M}/3rdparty/zstd/lib)
add_library(zstd STATIC
  ${ZSTD}/common/debug.c ${ZSTD}/common/entropy_common.c ${ZSTD}/common/error_private.c
  ${ZSTD}/common/fse_decompress.c ${ZSTD}/common/pool.c ${ZSTD}/common/threading.c ${ZSTD}/common/xxhash.c
  ${ZSTD}/common/zstd_common.c ${ZSTD}/compress/fse_compress.c ${ZSTD}/compress/hist.c
  ${ZSTD}/compress/huf_compress.c ${ZSTD}/compress/zstd_compress.c ${ZSTD}/compress/zstd_compress_literals.c
  ${ZSTD}/compress/zstd_compress_sequences.c ${ZSTD}/compress/zstd_compress_superblock.c
  ${ZSTD}/compress/zstd_double_fast.c ${ZSTD}/compress/zstd_fast.c ${ZSTD}/compress/zstd_lazy.c
  ${ZSTD}/compress/zstd_ldm.c ${ZSTD}/compress/zstdmt_compress.c ${ZSTD}/compress/zstd_opt.c
  ${ZSTD}/decompress/huf_decompress.c ${ZSTD}/decompress/zstd_ddict.c
  ${ZSTD}/decompress/zstd_decompress_block.c ${ZSTD}/decompress/zstd_decompress.c)
target_compile_definitions(zstd PRIVATE XXH_NAMESPACE=ZSTD_ DEBUGLEVEL=0 ZSTD_DISABLE_ASM)

set(FLAC ${M}/3rdparty/flac/src/libFLAC)
add_library(flac STATIC
  ${FLAC}/bitmath.c ${FLAC}/bitreader.c ${FLAC}/bitwriter.c ${FLAC}/cpu.c ${FLAC}/crc.c ${FLAC}/fixed.c
  ${FLAC}/float.c ${FLAC}/format.c ${FLAC}/lpc.c ${FLAC}/lpc_intrin_neon.c ${FLAC}/md5.c ${FLAC}/memory.c
  ${FLAC}/stream_decoder.c ${FLAC}/stream_encoder.c ${FLAC}/stream_encoder_framing.c ${FLAC}/window.c)
target_include_directories(flac PRIVATE ${FLAC}/include PUBLIC ${M}/3rdparty/flac/include)
target_compile_definitions(flac PRIVATE
  HAVE_CONFIG_H ENABLE_64_BIT_WORDS=1 OGG_FOUND=0 FLAC__HAS_OGG=0 HAVE_LROUND=1 HAVE_INTTYPES_H HAVE_STDBOOL_H
  HAVE_STDINT_H HAVE_STDIO_H HAVE_STDLIB_H HAVE_STRING_H _FILE_OFFSET_BITS=64 _LARGEFILE_SOURCE HAVE_BSWAP16
  HAVE_BSWAP32 CPU_IS_BIG_ENDIAN=0 CPU_IS_LITTLE_ENDIAN=1 WORDS_BIGENDIAN=0 FLAC__SYS_LINUX)

add_library(utf8proc STATIC ${M}/3rdparty/utf8proc/utf8proc.c)

add_library(expat STATIC
  ${M}/3rdparty/expat/lib/xmlparse.c ${M}/3rdparty/expat/lib/xmlrole.c ${M}/3rdparty/expat/lib/xmltok.c)
target_compile_definitions(expat PRIVATE
  HAVE_MEMMOVE HAVE_STDINT_H HAVE_STDIO_H HAVE_STDLIB_H HAVE_STRING_H STDC_HEADERS XML_CONTEXT_BYTES=1024 XML_DTD
  XML_GE=1 XML_NS BYTEORDER=1234 HAVE_DLFCN_H HAVE_FCNTL_H HAVE_MMAP HAVE_SYS_STAT_H HAVE_SYS_TYPES_H
  HAVE_UNISTD_H XML_DEV_URANDOM PACKAGE="expat" VERSION="2.7.1")

# MAME's osd core (POSIX files, threads and the like)
add_library(ocore STATIC
  ${M}/src/osd/osdcore.cpp ${M}/src/osd/strconv.cpp ${M}/src/osd/osdsync.cpp
  ${M}/src/osd/modules/lib/osdlib_unix.cpp
  ${M}/src/osd/modules/file/posixdir.cpp ${M}/src/osd/modules/file/posixfile.cpp
  ${M}/src/osd/modules/file/posixptty.cpp ${M}/src/osd/modules/file/posixsocket.cpp)
target_include_directories(ocore PRIVATE
  ${CMAKE_CURRENT_SOURCE_DIR}/sdl-stub ${M}/src/osd ${M}/src/lib ${M}/src/lib/util ${M}/src/osd/sdl)
target_compile_definitions(ocore PRIVATE SDLMAME_UNIX=1 SDLMAME_SDL2=1 OSD_SDL)
target_compile_options(ocore PRIVATE -include ${M}/src/osd/sdl/sdlprefix.h)

# MAME's util library (CHD, CD and DVD images, codecs, files)
set(U ${M}/src/lib/util)
add_library(utils STATIC
  ${U}/avhuff.cpp ${U}/aviio.cpp ${U}/bitmap.cpp ${U}/cdrom.cpp ${U}/chd.cpp
  ${U}/chdcodec.cpp ${U}/corealloc.cpp ${U}/corefile.cpp ${U}/corestr.cpp ${U}/coreutil.cpp ${U}/delegate.cpp
  ${U}/disasmintf.cpp ${U}/dvdrom.cpp ${U}/dynamicclass.cpp ${U}/flac.cpp ${U}/harddisk.cpp ${U}/hash.cpp
  ${U}/hashing.cpp ${U}/huffman.cpp ${U}/ioprocs.cpp ${U}/ioprocsfilter.cpp ${U}/jedparse.cpp ${U}/language.cpp
  ${U}/md5.cpp ${U}/mfpresolve.cpp ${U}/msdib.cpp ${U}/opresolv.cpp ${U}/options.cpp ${U}/palette.cpp
  ${U}/path.cpp ${U}/plaparse.cpp ${U}/png.cpp ${U}/simh_tape_file.cpp ${U}/strformat.cpp ${U}/timeconv.cpp
  ${U}/unicode.cpp ${U}/unzip.cpp ${U}/un7z.cpp ${U}/vbiparse.cpp ${U}/vecstream.cpp ${U}/wavwrite.cpp
  ${U}/xmlfile.cpp ${U}/zippath.cpp)
target_include_directories(utils PRIVATE
  ${M}/src/osd ${M}/src/lib/util ${M}/3rdparty ${M}/3rdparty/expat/lib ${M}/3rdparty/zlib
  ${M}/3rdparty/zstd/lib ${M}/3rdparty/flac/include ${M}/3rdparty/utf8proc)
target_link_libraries(utils PUBLIC expat 7z zlib zstd flac utf8proc)

add_executable(chdman ${M}/src/tools/chdman.cpp ${VERSION_CPP})
target_include_directories(chdman PRIVATE
  ${M}/src/osd ${M}/src/lib/util ${M}/3rdparty ${M}/3rdparty/flac/include)
target_link_libraries(chdman PRIVATE utils ocore expat 7z zlib zstd flac utf8proc log)
target_link_options(chdman PRIVATE -static-libstdc++ -Wl,--gc-sections -s)
