# maxcso for Android (arm64-v8a), from its release sources with their bundled libraries: libuv, lz4, libdeflate,
# zopfli and 7-zip's deflate encoder; zlib comes from the NDK. Run by build.sh, which fetches the sources.
cmake_minimum_required(VERSION 3.22)
project(maxcso C CXX)

if(NOT MAXCSO_DIR)
  message(FATAL_ERROR "MAXCSO_DIR (the maxcso source folder) is not set")
endif()
set(M "${MAXCSO_DIR}")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
add_compile_definitions(NDEBUG=1)
add_compile_options(-O2 -w)

# libuv, with its own build
set(LIBUV_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LIBUV_BUILD_BENCH OFF CACHE BOOL "" FORCE)
set(BUILD_TESTING OFF CACHE BOOL "" FORCE)
add_subdirectory("${M}/libuv" libuv EXCLUDE_FROM_ALL)

add_library(maxcso_lz4 STATIC "${M}/lz4/lib/lz4.c" "${M}/lz4/lib/lz4hc.c")
target_include_directories(maxcso_lz4 PUBLIC "${M}/lz4/lib")

add_library(maxcso_deflate STATIC
  "${M}/libdeflate/lib/deflate_decompress.c"
  "${M}/libdeflate/lib/deflate_compress.c"
  "${M}/libdeflate/lib/utils.c"
  "${M}/libdeflate/lib/arm/cpu_features.c"
  "${M}/libdeflate/lib/adler32.c"
  "${M}/libdeflate/lib/zlib_decompress.c"
  "${M}/libdeflate/lib/zlib_compress.c"
  "${M}/libdeflate/lib/crc32.c"
  "${M}/libdeflate/lib/gzip_decompress.c"
  "${M}/libdeflate/lib/gzip_compress.c"
)
target_include_directories(maxcso_deflate PUBLIC "${M}/libdeflate")

set(ZOPFLI "${M}/zopfli/src/zopfli")
add_library(maxcso_zopfli STATIC
  "${ZOPFLI}/blocksplitter.c" "${ZOPFLI}/cache.c" "${ZOPFLI}/deflate.c" "${ZOPFLI}/gzip_container.c"
  "${ZOPFLI}/hash.c" "${ZOPFLI}/katajainen.c" "${ZOPFLI}/lz77.c" "${ZOPFLI}/squeeze.c" "${ZOPFLI}/tree.c"
  "${ZOPFLI}/util.c" "${ZOPFLI}/zlib_container.c" "${ZOPFLI}/zopfli_lib.c"
)
target_include_directories(maxcso_zopfli PUBLIC "${M}/zopfli/src")

set(SEVENZIP "${M}/7zip")
add_library(maxcso_7zip STATIC
  "${SEVENZIP}/CPP/7zip/Archive/Common/ParseProperties.cpp"
  "${SEVENZIP}/CPP/7zip/Archive/DeflateProps.cpp"
  "${SEVENZIP}/CPP/7zip/Common/CWrappers.cpp"
  "${SEVENZIP}/CPP/7zip/Common/FileStreams.cpp"
  "${SEVENZIP}/CPP/7zip/Common/InBuffer.cpp"
  "${SEVENZIP}/CPP/7zip/Common/OutBuffer.cpp"
  "${SEVENZIP}/CPP/7zip/Common/StreamUtils.cpp"
  "${SEVENZIP}/CPP/7zip/Compress/BitlDecoder.cpp"
  "${SEVENZIP}/CPP/7zip/Compress/DeflateDecoder.cpp"
  "${SEVENZIP}/CPP/7zip/Compress/DeflateEncoder.cpp"
  "${SEVENZIP}/CPP/7zip/Compress/LzOutWindow.cpp"
  "${SEVENZIP}/CPP/7zip/Compress/ZlibDecoder.cpp"
  "${SEVENZIP}/CPP/7zip/Compress/ZlibEncoder.cpp"
  "${SEVENZIP}/CPP/Common/MyString.cpp"
  "${SEVENZIP}/CPP/Common/StringConvert.cpp"
  "${SEVENZIP}/CPP/Common/StringToInt.cpp"
  "${SEVENZIP}/CPP/NotWindows/FileIO.cpp"
  "${SEVENZIP}/CPP/NotWindows/MyWindows.cpp"
  "${SEVENZIP}/CPP/Windows/PropVariant.cpp"
  "${SEVENZIP}/deflate7z.cpp"
  "${SEVENZIP}/C/Alloc.c"
  "${SEVENZIP}/C/HuffEnc.c"
  "${SEVENZIP}/C/LzFind.c"
  "${SEVENZIP}/C/Sort.c"
)
target_include_directories(maxcso_7zip PUBLIC "${SEVENZIP}" PRIVATE "${SEVENZIP}/CPP")

file(GLOB MAXCSO_SRC "${M}/src/*.cpp")
add_executable(maxcso ${MAXCSO_SRC} "${M}/cli/cli.cpp")
target_include_directories(maxcso PRIVATE "${M}/src")
target_link_libraries(maxcso PRIVATE uv_a maxcso_lz4 maxcso_deflate maxcso_zopfli maxcso_7zip z log)
target_link_options(maxcso PRIVATE -s -static-libstdc++)
