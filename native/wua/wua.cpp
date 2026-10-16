// wua: packs Wii U games kept as folders (code/, content/, meta/) into a WUA, the ZArchive file Cemu opens, checks
// one, and unpacks one back into folders. A WUA holds each title (a game, its update, its DLC) in a folder named
// <title id>_v<version>, e.g. 0005000e10110200_v32.
//
//   wua info FILE...                         one line per file, tab-separated: unpacked size, its top folders
//                                            (comma-separated), path; "?" for the size when it can't be read
//   wua pack [-j N] OUT NAME=DIR...          each DIR's files under NAME/ in OUT, compressed on N threads
//   wua verify FILE                          reads every file of FILE back
//   wua digest NAME=DIR...                   the same over folders
//   wua extract FILE OUTDIR NAME=TARGET...   FILE's NAME/ into OUTDIR/TARGET
//
// pack, verify, digest and extract print "SHA-1 : <hex>": one SHA-1 over every file's path (NAME/...), size and
// contents, in path order, so a WUA and the folders it was made from (or unpacked into) give the same one.
// Progress goes to stderr ("Compressing, 12.5% complete... (ratio=80.0%)").
// Exit codes: 0 done, 1 wrong use, 2 a file couldn't be read or written, 4 a damaged WUA.

#include <algorithm>
#include <cctype>
#include <cerrno>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <filesystem>
#include <memory>
#include <string>
#include <vector>

#include <fcntl.h>
#include <unistd.h>

#if defined(__aarch64__) && defined(__ARM_FEATURE_SHA2)
#include <arm_neon.h>
#include <asm/hwcap.h>
#include <sys/auxv.h>
#endif

#include "zarchive/zarchivereader.h"
#include "zarchive/zarchivewriter.h"

namespace fs = std::filesystem;

namespace {

enum { DONE = 0, USAGE = 1, IO = 2, DAMAGED = 4 };

constexpr size_t CHUNK = 1 << 20;

// ---- SHA-1 (FIPS 180-4) ----

struct Sha1 {
    uint32_t h[5] = {0x67452301, 0xEFCDAB89, 0x98BADCFE, 0x10325476, 0xC3D2E1F0};
    uint64_t length = 0;
    uint8_t block[64] = {};
    size_t used = 0;

    static uint32_t rol(uint32_t v, int n) { return (v << n) | (v >> (32 - n)); }

    void compress(const uint8_t* p) {
#if defined(__aarch64__) && defined(__ARM_FEATURE_SHA2)
        static const bool arm = (getauxval(AT_HWCAP) & HWCAP_SHA1) != 0;
        if (arm) return compressArm(p);
#endif
        compressSoft(p);
    }

#if defined(__aarch64__) && defined(__ARM_FEATURE_SHA2)
    // The same block on the ARMv8 SHA-1 instructions, four rounds per step
    void compressArm(const uint8_t* p) {
        uint32x4_t abcd = vld1q_u32(h);
        uint32_t e = h[4];
        const uint32x4_t abcd0 = abcd;
        const uint32_t e0 = e;
        uint32x4_t m[4];
        for (int i = 0; i < 4; i++) m[i] = vreinterpretq_u32_u8(vrev32q_u8(vld1q_u8(p + 16 * i)));
        for (int step = 0; step < 20; step++) {
            if (step >= 4) m[step % 4] = vsha1su1q_u32(vsha1su0q_u32(m[step % 4], m[(step + 1) % 4], m[(step + 2) % 4]), m[(step + 3) % 4]);
            uint32_t k = step < 5 ? 0x5A827999 : step < 10 ? 0x6ED9EBA1 : step < 15 ? 0x8F1BBCDC : 0xCA62C1D6;
            uint32x4_t wk = vaddq_u32(m[step % 4], vdupq_n_u32(k));
            uint32_t next = vsha1h_u32(vgetq_lane_u32(abcd, 0));
            if (step < 5) {
                abcd = vsha1cq_u32(abcd, e, wk);
            } else if (step >= 10 && step < 15) {
                abcd = vsha1mq_u32(abcd, e, wk);
            } else {
                abcd = vsha1pq_u32(abcd, e, wk);
            }
            e = next;
        }
        vst1q_u32(h, vaddq_u32(abcd, abcd0));
        h[4] = e + e0;
    }
#endif

    void compressSoft(const uint8_t* p) {
        uint32_t w[80];
        for (int i = 0; i < 16; i++) w[i] = uint32_t(p[4 * i]) << 24 | uint32_t(p[4 * i + 1]) << 16 | uint32_t(p[4 * i + 2]) << 8 | p[4 * i + 3];
        for (int i = 16; i < 80; i++) w[i] = rol(w[i - 3] ^ w[i - 8] ^ w[i - 14] ^ w[i - 16], 1);
        uint32_t a = h[0], b = h[1], c = h[2], d = h[3], e = h[4];
        for (int i = 0; i < 80; i++) {
            uint32_t f, k;
            if (i < 20) {
                f = (b & c) | (~b & d);
                k = 0x5A827999;
            } else if (i < 40) {
                f = b ^ c ^ d;
                k = 0x6ED9EBA1;
            } else if (i < 60) {
                f = (b & c) | (b & d) | (c & d);
                k = 0x8F1BBCDC;
            } else {
                f = b ^ c ^ d;
                k = 0xCA62C1D6;
            }
            uint32_t t = rol(a, 5) + f + e + k + w[i];
            e = d;
            d = c;
            c = rol(b, 30);
            b = a;
            a = t;
        }
        h[0] += a;
        h[1] += b;
        h[2] += c;
        h[3] += d;
        h[4] += e;
    }

    void update(const void* data, size_t n) {
        auto p = static_cast<const uint8_t*>(data);
        length += n;
        if (used) {
            size_t take = std::min(64 - used, n);
            memcpy(block + used, p, take);
            used += take;
            p += take;
            n -= take;
            if (used < 64) return;
            compress(block);
            used = 0;
        }
        for (; n >= 64; p += 64, n -= 64) compress(p);
        memcpy(block, p, n);
        used = n;
    }

    void print() {
        uint64_t bits = length * 8;
        uint8_t tail[72];
        size_t n = 0;
        tail[n++] = 0x80;
        while ((used + n) % 64 != 56) tail[n++] = 0;
        for (int i = 7; i >= 0; i--) tail[n++] = uint8_t(bits >> (8 * i));
        update(tail, n);
        printf("SHA-1 : %08x%08x%08x%08x%08x\n", h[0], h[1], h[2], h[3], h[4]);
        fflush(stdout);
    }
};

// ---- what the check hashes ----

// A file as the check sees it: its path in the WUA, its size, and where it comes from.
struct Entry {
    std::string path;
    uint64_t size = 0;
    fs::path source;            // a file on disk
    ZArchiveNodeHandle node{};  // or a file in a WUA
};

void sortEntries(std::vector<Entry>& entries) {
    std::sort(entries.begin(), entries.end(), [](const Entry& a, const Entry& b) {
        std::string x = a.path, y = b.path;
        std::transform(x.begin(), x.end(), x.begin(), [](unsigned char c) { return char(std::tolower(c)); });
        std::transform(y.begin(), y.end(), y.begin(), [](unsigned char c) { return char(std::tolower(c)); });
        return x != y ? x < y : a.path < b.path;
    });
}

void hashHeader(Sha1& sha, const Entry& entry) {
    uint8_t size[8];
    for (int i = 0; i < 8; i++) size[i] = uint8_t(entry.size >> (8 * i));
    sha.update(entry.path.data(), entry.path.size());
    sha.update("", 1);
    sha.update(size, sizeof size);
}

struct Progress {
    const char* step;
    uint64_t total;
    uint64_t done = 0;
    int last = -1;

    void add(uint64_t n, uint64_t written = 0) {
        done += n;
        int tenths = total ? int(1000 * done / total) : 1000;
        if (tenths == last) return;
        last = tenths;
        if (written) {
            fprintf(stderr, "%s, %.1f%% complete... (ratio=%.1f%%)\r", step, tenths / 10.0, done ? 100.0 * double(written) / double(done) : 100.0);
        } else {
            fprintf(stderr, "%s, %.1f%% complete...\r", step, tenths / 10.0);
        }
    }
};

// NAME=DIR arguments into the files under each DIR, as NAME/<path inside DIR>.
bool folderEntries(int count, char** pairs, std::vector<Entry>& entries, std::vector<std::string>* names = nullptr) {
    for (int i = 0; i < count; i++) {
        const char* eq = strchr(pairs[i], '=');
        if (!eq || eq == pairs[i] || !eq[1]) return false;
        std::string name(pairs[i], eq - pairs[i]);
        fs::path dir(eq + 1);
        std::error_code ec;
        if (!fs::is_directory(dir, ec)) {
            fprintf(stderr, "not a folder: %s\n", eq + 1);
            return false;
        }
        if (names) names->push_back(name);
        for (fs::recursive_directory_iterator it(dir, ec), end; !ec && it != end; it.increment(ec)) {
            if (!it->is_regular_file(ec) || it->is_symlink(ec)) continue;
            Entry entry;
            entry.path = name + "/" + fs::relative(it->path(), dir, ec).generic_string();
            entry.size = it->file_size(ec);
            entry.source = it->path();
            entries.push_back(entry);
        }
        if (ec) {
            fprintf(stderr, "can't read %s\n", eq + 1);
            return false;
        }
    }
    sortEntries(entries);
    return true;
}

void archiveEntries(ZArchiveReader* reader, const std::string& path, std::vector<Entry>& entries) {
    ZArchiveNodeHandle dir = reader->LookUp(path, false, true);
    if (dir == ZARCHIVE_INVALID_NODE) return;
    uint32_t count = reader->GetDirEntryCount(dir);
    for (uint32_t i = 0; i < count; i++) {
        ZArchiveReader::DirEntry entry;
        if (!reader->GetDirEntry(dir, i, entry)) continue;
        std::string child = path.empty() ? std::string(entry.name) : path + "/" + std::string(entry.name);
        if (entry.isDirectory) {
            archiveEntries(reader, child, entries);
        } else if (entry.isFile) {
            Entry file;
            file.path = child;
            file.node = reader->LookUp(child, true, false);
            file.size = reader->GetFileSize(file.node);
            entries.push_back(file);
        }
    }
}

// Reads a file on disk whole, a chunk at a time.
template <typename Use>
bool readFile(const fs::path& path, uint64_t size, std::vector<uint8_t>& buffer, Use use) {
    int fd = open(path.c_str(), O_RDONLY);
    if (fd < 0) return false;
    uint64_t done = 0;
    while (done < size) {
        ssize_t got = read(fd, buffer.data(), size_t(std::min<uint64_t>(buffer.size(), size - done)));
        if (got < 0 && errno == EINTR) continue;
        if (got <= 0) {
            close(fd);
            return false;
        }
        use(buffer.data(), size_t(got));
        done += uint64_t(got);
    }
    char extra;
    bool whole = read(fd, &extra, 1) == 0;
    close(fd);
    return whole;
}

uint64_t totalSize(const std::vector<Entry>& entries) {
    uint64_t total = 0;
    for (const auto& entry : entries) total += entry.size;
    return total;
}

// ---- commands ----

struct Output {
    FILE* file = nullptr;
    uint64_t written = 0;
    bool failed = false;
};

int pack(const char* out, int count, char** pairs, int threads) {
    std::vector<Entry> entries;
    if (!folderEntries(count, pairs, entries)) return USAGE;
    Output output;
    output.file = fopen(out, "wb");
    if (!output.file) {
        fprintf(stderr, "can't write %s\n", out);
        return IO;
    }
    auto newFile = [](const int32_t, void*) {};
    auto write = [](const void* data, size_t length, void* context) {
        auto o = static_cast<Output*>(context);
        if (!o->failed && fwrite(data, 1, length, o->file) != length) o->failed = true;
        o->written += length;
    };
    Sha1 sha;
    Progress progress{"Compressing", totalSize(entries)};
    std::vector<uint8_t> buffer(CHUNK);
    {
        ZArchiveWriter writer(newFile, write, &output);
        writer.SetThreadCount(threads);
        for (const auto& entry : entries) {
            std::string parent = entry.path.substr(0, entry.path.rfind('/'));
            if (!writer.MakeDir(parent.c_str(), true) || !writer.StartNewFile(entry.path.c_str())) {
                fprintf(stderr, "can't add %s\n", entry.path.c_str());
                fclose(output.file);
                return IO;
            }
            hashHeader(sha, entry);
            bool ok = readFile(entry.source, entry.size, buffer, [&](const uint8_t* data, size_t n) {
                writer.AppendData(data, n);
                sha.update(data, n);
                progress.add(n, output.written);
            });
            if (!ok || output.failed) {
                fprintf(stderr, "can't %s %s\n", ok ? "write" : "read", ok ? out : entry.source.c_str());
                fclose(output.file);
                return IO;
            }
        }
        writer.Finalize();
    }
    if (output.failed || fflush(output.file) != 0 || fsync(fileno(output.file)) != 0 || fclose(output.file) != 0) {
        fprintf(stderr, "can't finish %s\n", out);
        return IO;
    }
    uint64_t total = progress.total;
    fprintf(stderr, "\nCompression complete ... final ratio = %.1f%%\n", total ? 100.0 * double(output.written) / double(total) : 100.0);
    sha.print();
    return DONE;
}

std::unique_ptr<ZArchiveReader> openArchive(const char* path) {
    std::error_code ec;
    if (!fs::is_regular_file(path, ec)) return nullptr;
    return std::unique_ptr<ZArchiveReader>(ZArchiveReader::OpenFromFile(path));
}

int verify(const char* path) {
    auto reader = openArchive(path);
    if (!reader) {
        fprintf(stderr, "not a WUA: %s\n", path);
        return DAMAGED;
    }
    std::vector<Entry> entries;
    archiveEntries(reader.get(), "", entries);
    sortEntries(entries);
    Sha1 sha;
    Progress progress{"Verifying", totalSize(entries)};
    std::vector<uint8_t> buffer(CHUNK);
    for (const auto& entry : entries) {
        hashHeader(sha, entry);
        for (uint64_t at = 0; at < entry.size;) {
            uint64_t want = std::min<uint64_t>(buffer.size(), entry.size - at);
            uint64_t got = reader->ReadFromFile(entry.node, at, want, buffer.data());
            if (got != want) {
                fprintf(stderr, "damaged: %s\n", entry.path.c_str());
                return DAMAGED;
            }
            sha.update(buffer.data(), size_t(got));
            at += got;
            progress.add(got);
        }
    }
    fprintf(stderr, "\n");
    sha.print();
    return DONE;
}

int digest(int count, char** pairs) {
    std::vector<Entry> entries;
    if (!folderEntries(count, pairs, entries)) return USAGE;
    Sha1 sha;
    Progress progress{"Verifying", totalSize(entries)};
    std::vector<uint8_t> buffer(CHUNK);
    for (const auto& entry : entries) {
        hashHeader(sha, entry);
        bool ok = readFile(entry.source, entry.size, buffer, [&](const uint8_t* data, size_t n) {
            sha.update(data, n);
            progress.add(n);
        });
        if (!ok) {
            fprintf(stderr, "can't read %s\n", entry.source.c_str());
            return IO;
        }
    }
    fprintf(stderr, "\n");
    sha.print();
    return DONE;
}

int extract(const char* path, const char* outdir, int count, char** pairs) {
    auto reader = openArchive(path);
    if (!reader) {
        fprintf(stderr, "not a WUA: %s\n", path);
        return DAMAGED;
    }
    std::vector<std::pair<std::string, std::string>> targets;
    for (int i = 0; i < count; i++) {
        const char* eq = strchr(pairs[i], '=');
        if (!eq || eq == pairs[i] || !eq[1]) return USAGE;
        targets.emplace_back(std::string(pairs[i], eq - pairs[i]), std::string(eq + 1));
    }
    std::vector<Entry> entries;
    archiveEntries(reader.get(), "", entries);
    sortEntries(entries);
    Sha1 sha;
    Progress progress{"Extracting", totalSize(entries)};
    std::vector<uint8_t> buffer(CHUNK);
    for (const auto& entry : entries) {
        std::string top = entry.path.substr(0, entry.path.find('/'));
        auto target = std::find_if(targets.begin(), targets.end(), [&](const auto& t) { return t.first == top; });
        if (target == targets.end()) {
            fprintf(stderr, "no folder given for %s\n", top.c_str());
            return USAGE;
        }
        fs::path file = fs::path(outdir) / target->second / entry.path.substr(top.size() + 1);
        std::error_code ec;
        fs::create_directories(file.parent_path(), ec);
        FILE* out = fopen(file.c_str(), "wb");
        if (!out) {
            fprintf(stderr, "can't write %s\n", file.c_str());
            return IO;
        }
        hashHeader(sha, entry);
        for (uint64_t at = 0; at < entry.size;) {
            uint64_t want = std::min<uint64_t>(buffer.size(), entry.size - at);
            uint64_t got = reader->ReadFromFile(entry.node, at, want, buffer.data());
            if (got != want) {
                fclose(out);
                fprintf(stderr, "damaged: %s\n", entry.path.c_str());
                return DAMAGED;
            }
            if (fwrite(buffer.data(), 1, size_t(got), out) != got) {
                fclose(out);
                fprintf(stderr, "can't write %s\n", file.c_str());
                return IO;
            }
            sha.update(buffer.data(), size_t(got));
            at += got;
            progress.add(got);
        }
        if (fflush(out) != 0 || fsync(fileno(out)) != 0 || fclose(out) != 0) {
            fprintf(stderr, "can't finish %s\n", file.c_str());
            return IO;
        }
    }
    fprintf(stderr, "\n");
    sha.print();
    return DONE;
}

int info(int count, char** paths) {
    for (int i = 0; i < count; i++) {
        auto reader = openArchive(paths[i]);
        if (!reader) {
            printf("?\t-\t%s\n", paths[i]);
            continue;
        }
        std::vector<Entry> entries;
        archiveEntries(reader.get(), "", entries);
        std::string tops;
        ZArchiveNodeHandle root = reader->LookUp("", false, true);
        uint32_t n = root == ZARCHIVE_INVALID_NODE ? 0 : reader->GetDirEntryCount(root);
        for (uint32_t j = 0; j < n; j++) {
            ZArchiveReader::DirEntry entry;
            if (!reader->GetDirEntry(root, j, entry) || !entry.isDirectory) continue;
            if (!tops.empty()) tops += ",";
            tops += std::string(entry.name);
        }
        printf("%llu\t%s\t%s\n", (unsigned long long)totalSize(entries), tops.empty() ? "-" : tops.c_str(), paths[i]);
    }
    fflush(stdout);
    return DONE;
}

int usage() {
    fprintf(stderr,
            "usage: wua info FILE...\n"
            "       wua pack [-j THREADS] OUT NAME=DIR...\n"
            "       wua verify FILE\n"
            "       wua digest NAME=DIR...\n"
            "       wua extract FILE OUTDIR NAME=TARGET...\n");
    return USAGE;
}

}  // namespace

int main(int argc, char** argv) {
    if (argc < 3) return usage();
    std::string command = argv[1];
    if (command == "info") return info(argc - 2, argv + 2);
    if (command == "pack" && argc >= 6 && std::string(argv[2]) == "-j") {
        int threads = std::max(1, std::min(16, atoi(argv[3])));
        return pack(argv[4], argc - 5, argv + 5, threads);
    }
    if (command == "pack" && argc >= 4) return pack(argv[2], argc - 3, argv + 3, 1);
    if (command == "verify" && argc == 3) return verify(argv[2]);
    if (command == "digest") return digest(argc - 2, argv + 2);
    if (command == "extract" && argc >= 5) return extract(argv[2], argv[3], argc - 4, argv + 4);
    return usage();
}
