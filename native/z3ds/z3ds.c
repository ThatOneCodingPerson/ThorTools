/*
 * z3ds: makes, checks and unpacks the compressed 3DS ROMs Azahar opens: a .zcci from a .3ds or .cci (an NCSD card
 * image), a .zcxi from a .cxi (an NCCH).
 *
 * A Z3DS file is a 32-byte header ("Z3DS", the ROM's own magic, version 1, reserved, header size, metadata size,
 * compressed size, ROM size; little-endian), optional metadata (a version byte, then items of type, name length,
 * data length, name and data, an empty end item, zeros to 16 bytes), then the ROM as seekable zstd: one independent
 * zstd frame for every 256 KiB of the ROM, then a seek table (a skippable frame with each frame's compressed and
 * unpacked size) so any part of the ROM can be read without unpacking the rest.
 *
 *   z3ds info FILE...                     one line per file, tab-separated: type, inner type, crypto, ROM size,
 *                                         end of the ROM's data (plain) or compressed size (Z3DS), path
 *   z3ds compress [-l LEVEL] [-j N] ROM OUT
 *   z3ds verify [-j N] FILE               unpacks in memory
 *   z3ds decompress [-j N] FILE OUT
 *   z3ds sha1 FILE
 *
 * compress prints the SHA-1 of the ROM it read; verify and decompress that of the ROM they unpacked; sha1 that of a
 * file; each as "SHA-1 : <hex>". Progress goes to stderr ("Compressing, 12.5% complete... (ratio=48.0%)").
 * Exit codes: 0 done, 1 wrong use, 2 a file couldn't be read or written, 3 not a ROM this tool compresses (or an
 * encrypted one), 4 a damaged Z3DS file.
 */
#define _FILE_OFFSET_BITS 64
#include <errno.h>
#include <fcntl.h>
#include <pthread.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <time.h>
#include <unistd.h>

#include "zstd.h"

#define HEADER_SIZE 0x20
#define FRAME_SIZE 0x40000
#define FRAMES_PER_THREAD 8
#define MAX_THREADS 16
#define MAX_FRAME (64u << 20)
#define SKIPPABLE_MAGIC 0x184D2A5Eu
#define SEEKABLE_MAGIC 0x8F92EAB1u
#define MEDIA_UNIT 0x200
#define CHUNK (8u << 20)

enum { DONE = 0, USAGE = 1, IO = 2, NOT_ROM = 3, DAMAGED = 4 };

static void put16(uint8_t *p, uint32_t v) { p[0] = (uint8_t)v; p[1] = (uint8_t)(v >> 8); }
static void put32(uint8_t *p, uint32_t v) { for (int i = 0; i < 4; i++) p[i] = (uint8_t)(v >> (8 * i)); }
static void put64(uint8_t *p, uint64_t v) { for (int i = 0; i < 8; i++) p[i] = (uint8_t)(v >> (8 * i)); }
static uint32_t get16(const uint8_t *p) { return (uint32_t)p[0] | (uint32_t)p[1] << 8; }
static uint32_t get32(const uint8_t *p) { return (uint32_t)p[0] | (uint32_t)p[1] << 8 | (uint32_t)p[2] << 16 | (uint32_t)p[3] << 24; }
static uint64_t get64(const uint8_t *p) { return (uint64_t)get32(p) | (uint64_t)get32(p + 4) << 32; }

/* ---- SHA-1 (FIPS 180-4) ---- */

typedef struct {
    uint32_t h[5];
    uint64_t length;
    uint8_t block[64];
    size_t used;
} Sha1;

static uint32_t rol(uint32_t v, int n) { return (v << n) | (v >> (32 - n)); }

static void sha1_block(Sha1 *s, const uint8_t *p) {
    uint32_t w[80];
    for (int i = 0; i < 16; i++) w[i] = (uint32_t)p[4 * i] << 24 | (uint32_t)p[4 * i + 1] << 16 | (uint32_t)p[4 * i + 2] << 8 | p[4 * i + 3];
    for (int i = 16; i < 80; i++) w[i] = rol(w[i - 3] ^ w[i - 8] ^ w[i - 14] ^ w[i - 16], 1);
    uint32_t a = s->h[0], b = s->h[1], c = s->h[2], d = s->h[3], e = s->h[4];
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
    s->h[0] += a;
    s->h[1] += b;
    s->h[2] += c;
    s->h[3] += d;
    s->h[4] += e;
}

static void sha1_init(Sha1 *s) {
    static const uint32_t start[5] = {0x67452301, 0xEFCDAB89, 0x98BADCFE, 0x10325476, 0xC3D2E1F0};
    memcpy(s->h, start, sizeof start);
    s->length = 0;
    s->used = 0;
}

static void sha1_update(Sha1 *s, const uint8_t *p, size_t n) {
    s->length += n;
    if (s->used) {
        size_t take = 64 - s->used;
        if (take > n) take = n;
        memcpy(s->block + s->used, p, take);
        s->used += take;
        p += take;
        n -= take;
        if (s->used < 64) return;
        sha1_block(s, s->block);
        s->used = 0;
    }
    for (; n >= 64; p += 64, n -= 64) sha1_block(s, p);
    memcpy(s->block, p, n);
    s->used = n;
}

static void sha1_print(Sha1 *s) {
    uint64_t bits = s->length * 8;
    uint8_t tail[72];
    size_t n = 0;
    tail[n++] = 0x80;
    while ((s->used + n) % 64 != 56) tail[n++] = 0;
    for (int i = 7; i >= 0; i--) tail[n++] = (uint8_t)(bits >> (8 * i));
    sha1_update(s, tail, n);
    printf("SHA-1 : %08x%08x%08x%08x%08x\n", s->h[0], s->h[1], s->h[2], s->h[3], s->h[4]);
    fflush(stdout);
}

/* ---- files ---- */

static int read_at(int fd, void *buffer, size_t n, uint64_t at) {
    uint8_t *p = buffer;
    while (n) {
        ssize_t got = pread(fd, p, n, (off_t)at);
        if (got < 0 && errno == EINTR) continue;
        if (got <= 0) return -1;
        p += got;
        n -= (size_t)got;
        at += (uint64_t)got;
    }
    return 0;
}

static int write_all(int fd, const void *buffer, size_t n) {
    const uint8_t *p = buffer;
    while (n) {
        ssize_t put = write(fd, p, n);
        if (put < 0 && errno == EINTR) continue;
        if (put <= 0) return -1;
        p += put;
        n -= (size_t)put;
    }
    return 0;
}

static int open_read(const char *path, uint64_t *size) {
    int fd = open(path, O_RDONLY);
    struct stat st;
    if (fd < 0) return -1;
    if (fstat(fd, &st) != 0) {
        close(fd);
        return -1;
    }
    *size = (uint64_t)st.st_size;
    return fd;
}

static void *grab(size_t n) {
    void *p = malloc(n ? n : 1);
    if (!p) {
        fprintf(stderr, "out of memory\n");
        exit(IO);
    }
    return p;
}

/* ---- what a file is ---- */

typedef struct {
    char type[5];  /* NCSD, NCCH, 3DSX, Z3DS, or "?" */
    char inner[5]; /* a Z3DS file's ROM type, else "-" */
    int encrypted; /* 1 yes, 0 no, -1 not known */
    uint64_t size; /* the ROM's size (unpacked, for Z3DS) */
    uint64_t extra; /* where a plain ROM's data ends; a Z3DS file's compressed size */
} Info;

/* 1 when the NCCH at [at] is encrypted (its flags lack NoCrypto), 0 when it isn't, -1 when there is no NCCH. */
static int ncch_encrypted(int fd, uint64_t at) {
    uint8_t h[0x200];
    if (read_at(fd, h, sizeof h, at) != 0 || memcmp(h + 0x100, "NCCH", 4) != 0) return -1;
    return (h[0x188 + 7] & 0x04) ? 0 : 1;
}

/* A 4-byte magic as text ("CIA\0" becomes "CIA"). */
static void copy_magic(char out[5], const uint8_t *magic) {
    memcpy(out, magic, 4);
    out[4] = 0;
}

static int identify(int fd, uint64_t size, Info *info) {
    uint8_t h[0x200];
    memset(info, 0, sizeof *info);
    strcpy(info->type, "?");
    strcpy(info->inner, "-");
    info->encrypted = -1;
    info->size = size;
    if (size < HEADER_SIZE || read_at(fd, h, size < sizeof h ? HEADER_SIZE : sizeof h, 0) != 0) return 0;
    if (memcmp(h, "Z3DS", 4) == 0) {
        strcpy(info->type, "Z3DS");
        copy_magic(info->inner, h + 4);
        info->size = get64(h + 0x18);
        info->extra = get64(h + 0x10);
        return 1;
    }
    if (memcmp(h, "3DSX", 4) == 0) {
        strcpy(info->type, "3DSX");
        info->encrypted = 0;
        info->extra = size;
        return 1;
    }
    if (size < sizeof h) return 0;
    if (memcmp(h + 0x100, "NCSD", 4) == 0) {
        uint64_t unit = (uint64_t)MEDIA_UNIT << (h[0x188 + 6] & 0x1F);
        uint64_t end = 0;
        uint64_t first = (uint64_t)get32(h + 0x120) * unit;
        for (int i = 0; i < 8; i++) {
            uint64_t offset = (uint64_t)get32(h + 0x120 + 8 * i) * unit;
            uint64_t length = (uint64_t)get32(h + 0x124 + 8 * i) * unit;
            if (length && offset + length > end) end = offset + length;
        }
        strcpy(info->type, "NCSD");
        info->extra = end > size ? size : end;
        info->encrypted = first ? ncch_encrypted(fd, first) : -1;
        return 1;
    }
    if (memcmp(h + 0x100, "NCCH", 4) == 0) {
        strcpy(info->type, "NCCH");
        info->encrypted = (h[0x188 + 7] & 0x04) ? 0 : 1;
        info->extra = size;
        return 1;
    }
    return 0;
}

static int info(int count, char **paths) {
    for (int i = 0; i < count; i++) {
        uint64_t size = 0;
        Info found;
        int fd = open_read(paths[i], &size);
        if (fd < 0 || !identify(fd, size, &found)) {
            memset(&found, 0, sizeof found);
            strcpy(found.type, "?");
            strcpy(found.inner, "-");
            found.encrypted = -1;
            found.size = size;
        }
        if (fd >= 0) close(fd);
        const char *crypto = found.encrypted == 1 ? "encrypted" : found.encrypted == 0 ? "decrypted" : "-";
        printf("%s\t%s\t%s\t%llu\t%llu\t%s\n", found.type, found.inner, crypto, (unsigned long long)found.size,
               (unsigned long long)found.extra, paths[i]);
    }
    return DONE;
}

/* ---- the work threads ---- */

typedef struct {
    int index;
    int stride;
    int level;
    ZSTD_CCtx *cctx;
    ZSTD_DCtx *dctx;
    size_t frames;            /* frames in this batch */
    const uint8_t *in;        /* the batch's input */
    const uint64_t *in_at;    /* where each frame starts in [in] */
    const size_t *in_size;    /* each frame's input size */
    uint8_t *out;             /* the batch's output */
    const uint64_t *out_at;   /* where each frame goes in [out] */
    const size_t *out_cap;    /* room for each frame there */
    size_t *made;             /* each frame's output size */
    int failed;
} Work;

static void *compress_frames(void *arg) {
    Work *w = arg;
    for (size_t f = (size_t)w->index; f < w->frames; f += (size_t)w->stride) {
        size_t r = ZSTD_compressCCtx(w->cctx, w->out + w->out_at[f], w->out_cap[f], w->in + w->in_at[f], w->in_size[f], w->level);
        if (ZSTD_isError(r)) {
            w->failed = 1;
            return NULL;
        }
        w->made[f] = r;
    }
    return NULL;
}

static void *decompress_frames(void *arg) {
    Work *w = arg;
    for (size_t f = (size_t)w->index; f < w->frames; f += (size_t)w->stride) {
        size_t r = ZSTD_decompressDCtx(w->dctx, w->out + w->out_at[f], w->out_cap[f], w->in + w->in_at[f], w->in_size[f]);
        if (ZSTD_isError(r) || r != w->out_cap[f]) {
            w->failed = 1;
            return NULL;
        }
        w->made[f] = r;
    }
    return NULL;
}

static int run_threads(Work *work, int threads, void *(*task)(void *)) {
    pthread_t ids[MAX_THREADS];
    int started[MAX_THREADS] = {0};
    for (int t = 1; t < threads; t++) started[t] = pthread_create(&ids[t], NULL, task, &work[t]) == 0;
    task(&work[0]);
    int failed = work[0].failed;
    for (int t = 1; t < threads; t++) {
        if (started[t]) {
            pthread_join(ids[t], NULL);
        } else {
            task(&work[t]);
        }
        failed |= work[t].failed;
    }
    return failed;
}

/* ---- compress ---- */

static size_t metadata_item(uint8_t *buffer, size_t n, const char *name, const char *value) {
    size_t a = strlen(name), b = strlen(value);
    buffer[n++] = 1;
    buffer[n++] = (uint8_t)a;
    put16(buffer + n, (uint32_t)b);
    n += 2;
    memcpy(buffer + n, name, a);
    n += a;
    memcpy(buffer + n, value, b);
    return n + b;
}

static size_t metadata(uint8_t *buffer) {
    char date[32], frame[16];
    time_t now = time(NULL);
    struct tm utc;
    gmtime_r(&now, &utc);
    strftime(date, sizeof date, "%Y-%m-%dT%H:%M:%SZ", &utc);
    snprintf(frame, sizeof frame, "%d", FRAME_SIZE);
    size_t n = 0;
    buffer[n++] = 1;
    n = metadata_item(buffer, n, "compressor", "Thor Tools");
    n = metadata_item(buffer, n, "date", date);
    n = metadata_item(buffer, n, "maxframesize", frame);
    memset(buffer + n, 0, 4);
    n += 4;
    while (n % 16) buffer[n++] = 0;
    return n;
}

static int compress_rom(const char *in_path, const char *out_path, int level, int threads) {
    uint64_t size = 0;
    Info found;
    int fd = open_read(in_path, &size);
    if (fd < 0) {
        fprintf(stderr, "can't read %s\n", in_path);
        return IO;
    }
    if (!identify(fd, size, &found) || (strcmp(found.type, "NCSD") != 0 && strcmp(found.type, "NCCH") != 0)) {
        fprintf(stderr, "not a 3DS card image (.3ds, .cci) or .cxi\n");
        close(fd);
        return NOT_ROM;
    }
    if (found.encrypted != 0) {
        fprintf(stderr, found.encrypted == 1 ? "encrypted\n" : "no NCCH found\n");
        close(fd);
        return NOT_ROM;
    }
    int out = open(out_path, O_WRONLY | O_CREAT | O_TRUNC, 0644);
    if (out < 0) {
        fprintf(stderr, "can't write %s\n", out_path);
        close(fd);
        return IO;
    }

    uint8_t header[HEADER_SIZE] = {0}, meta[256];
    size_t meta_size = metadata(meta);
    if (write_all(out, header, sizeof header) != 0 || write_all(out, meta, meta_size) != 0) goto failed_write;

    uint64_t frames = (size + FRAME_SIZE - 1) / FRAME_SIZE;
    size_t batch = (size_t)threads * FRAMES_PER_THREAD;
    size_t bound = ZSTD_compressBound(FRAME_SIZE);
    uint8_t *in[2] = {grab(batch * FRAME_SIZE), grab(batch * FRAME_SIZE)};
    uint8_t *packed = grab(batch * bound);
    uint32_t *sizes = grab((size_t)frames * sizeof(uint32_t) + 1);
    uint64_t *in_at = grab(batch * sizeof(uint64_t)), *out_at = grab(batch * sizeof(uint64_t));
    size_t *in_size = grab(batch * sizeof(size_t)), *out_cap = grab(batch * sizeof(size_t)), *made = grab(batch * sizeof(size_t));
    for (size_t f = 0; f < batch; f++) {
        in_at[f] = (uint64_t)f * FRAME_SIZE;
        out_at[f] = (uint64_t)f * bound;
        out_cap[f] = bound;
    }
    Work work[MAX_THREADS];
    for (int t = 0; t < threads; t++) {
        memset(&work[t], 0, sizeof work[t]);
        work[t].index = t;
        work[t].stride = threads;
        work[t].level = level;
        work[t].cctx = ZSTD_createCCtx();
        work[t].in_at = in_at;
        work[t].in_size = in_size;
        work[t].out = packed;
        work[t].out_at = out_at;
        work[t].out_cap = out_cap;
        work[t].made = made;
    }

    Sha1 sha;
    sha1_init(&sha);
    uint64_t done = 0, written = 0, frame = 0;
    int current = 0;
    size_t length = (size_t)(size < (uint64_t)batch * FRAME_SIZE ? size : (uint64_t)batch * FRAME_SIZE);
    if (length && read_at(fd, in[0], length, 0) != 0) goto failed_read;
    sha1_update(&sha, in[0], length);
    while (done < size) {
        size_t count = (length + FRAME_SIZE - 1) / FRAME_SIZE;
        for (size_t f = 0; f < count; f++) in_size[f] = f + 1 < count ? FRAME_SIZE : length - f * FRAME_SIZE;
        for (int t = 0; t < threads; t++) {
            work[t].frames = count;
            work[t].in = in[current];
            work[t].failed = 0;
        }
        /* The next batch is read while this one is compressed. */
        pthread_t ids[MAX_THREADS];
        int started[MAX_THREADS] = {0};
        for (int t = 0; t < threads; t++) started[t] = pthread_create(&ids[t], NULL, compress_frames, &work[t]) == 0;
        uint64_t next_at = done + length;
        size_t next = (size_t)(size - next_at < (uint64_t)batch * FRAME_SIZE ? size - next_at : (uint64_t)batch * FRAME_SIZE);
        int read_failed = next && read_at(fd, in[current ^ 1], next, next_at) != 0;
        if (!read_failed) sha1_update(&sha, in[current ^ 1], next);
        int failed = 0;
        for (int t = 0; t < threads; t++) {
            if (started[t]) {
                pthread_join(ids[t], NULL);
            } else {
                compress_frames(&work[t]);
            }
            failed |= work[t].failed;
        }
        if (failed) {
            fprintf(stderr, "zstd failed\n");
            goto failed_write;
        }
        if (read_failed) goto failed_read;
        for (size_t f = 0; f < count; f++) {
            if (write_all(out, packed + out_at[f], made[f]) != 0) goto failed_write;
            sizes[frame++] = (uint32_t)made[f];
            written += made[f];
        }
        done += length;
        length = next;
        current ^= 1;
        fprintf(stderr, "Compressing, %.1f%% complete... (ratio=%.1f%%)\r", 100.0 * (double)done / (double)size,
                100.0 * (double)written / (double)done);
    }

    size_t table_size = (size_t)frames * 8 + 9;
    uint8_t *table = grab(table_size + 8);
    put32(table, SKIPPABLE_MAGIC);
    put32(table + 4, (uint32_t)table_size);
    for (uint64_t f = 0; f < frames; f++) {
        put32(table + 8 + f * 8, sizes[f]);
        put32(table + 12 + f * 8, f + 1 < frames ? FRAME_SIZE : (uint32_t)(size - f * FRAME_SIZE));
    }
    put32(table + 8 + frames * 8, (uint32_t)frames);
    table[12 + frames * 8] = 0;
    put32(table + 13 + frames * 8, SEEKABLE_MAGIC);
    if (write_all(out, table, table_size + 8) != 0) goto failed_write;

    memcpy(header, "Z3DS", 4);
    memcpy(header + 4, found.type, 4);
    header[8] = 1;
    header[9] = 0;
    put16(header + 0x0A, HEADER_SIZE);
    put32(header + 0x0C, (uint32_t)meta_size);
    put64(header + 0x10, written + table_size + 8);
    put64(header + 0x18, size);
    if (pwrite(out, header, sizeof header, 0) != (ssize_t)sizeof header || fsync(out) != 0 || close(out) != 0) {
        fprintf(stderr, "can't finish %s\n", out_path);
        close(fd);
        return IO;
    }
    close(fd);
    fprintf(stderr, "Compression complete ... final ratio = %.1f%%\n", size ? 100.0 * (double)written / (double)size : 100.0);
    sha1_print(&sha);
    return DONE;

failed_read:
    fprintf(stderr, "can't read %s\n", in_path);
    close(fd);
    close(out);
    return IO;
failed_write:
    fprintf(stderr, "can't write %s\n", out_path);
    close(fd);
    close(out);
    return IO;
}

/* ---- verify and decompress ---- */

static int unpack(const char *in_path, const char *out_path, int threads) {
    uint64_t file_size = 0;
    int fd = open_read(in_path, &file_size);
    if (fd < 0) {
        fprintf(stderr, "can't read %s\n", in_path);
        return IO;
    }
    uint8_t h[HEADER_SIZE], footer[9], skip[8];
    if (file_size < HEADER_SIZE + 17 || read_at(fd, h, sizeof h, 0) != 0 || memcmp(h, "Z3DS", 4) != 0 || h[8] != 1) {
        fprintf(stderr, "not a Z3DS file\n");
        close(fd);
        return DAMAGED;
    }
    uint64_t data_at = (uint64_t)get16(h + 0x0A) + get32(h + 0x0C);
    uint64_t packed_size = get64(h + 0x10), rom_size = get64(h + 0x18);
    if (get16(h + 0x0A) < HEADER_SIZE || data_at + packed_size != file_size || read_at(fd, footer, 9, file_size - 9) != 0 ||
        get32(footer + 5) != SEEKABLE_MAGIC) {
        fprintf(stderr, "damaged: sizes or seek table\n");
        close(fd);
        return DAMAGED;
    }
    uint64_t frames = get32(footer);
    uint64_t entry = (footer[4] & 0x80) ? 12 : 8;
    uint64_t table_bytes = frames * entry;
    if (table_bytes + 17 > packed_size) {
        fprintf(stderr, "damaged: seek table\n");
        close(fd);
        return DAMAGED;
    }
    uint64_t table_at = file_size - 9 - table_bytes - 8;
    uint8_t *table = grab((size_t)table_bytes);
    if (read_at(fd, skip, 8, table_at) != 0 || get32(skip) != SKIPPABLE_MAGIC || get32(skip + 4) != table_bytes + 9 ||
        read_at(fd, table, (size_t)table_bytes, table_at + 8) != 0) {
        fprintf(stderr, "damaged: seek table\n");
        close(fd);
        return DAMAGED;
    }
    uint64_t compressed_total = 0, rom_total = 0;
    for (uint64_t f = 0; f < frames; f++) {
        uint32_t c = get32(table + f * entry), d = get32(table + f * entry + 4);
        if (d > MAX_FRAME || c > MAX_FRAME) {
            fprintf(stderr, "damaged: frame %llu\n", (unsigned long long)f);
            close(fd);
            return DAMAGED;
        }
        compressed_total += c;
        rom_total += d;
    }
    if (data_at + compressed_total != table_at || rom_total != rom_size) {
        fprintf(stderr, "damaged: the seek table doesn't match the file\n");
        close(fd);
        return DAMAGED;
    }
    int out = -1;
    if (out_path) {
        out = open(out_path, O_WRONLY | O_CREAT | O_TRUNC, 0644);
        if (out < 0) {
            fprintf(stderr, "can't write %s\n", out_path);
            close(fd);
            return IO;
        }
    }

    size_t batch = (size_t)threads * FRAMES_PER_THREAD;
    uint64_t *in_at = grab(batch * sizeof(uint64_t)), *out_at = grab(batch * sizeof(uint64_t));
    size_t *in_size = grab(batch * sizeof(size_t)), *out_cap = grab(batch * sizeof(size_t)), *made = grab(batch * sizeof(size_t));
    size_t in_room = 0, out_room = 0;
    uint8_t *in = NULL, *rom = NULL;
    Work work[MAX_THREADS];
    for (int t = 0; t < threads; t++) {
        memset(&work[t], 0, sizeof work[t]);
        work[t].index = t;
        work[t].stride = threads;
        work[t].dctx = ZSTD_createDCtx();
        work[t].in_at = in_at;
        work[t].in_size = in_size;
        work[t].out_at = out_at;
        work[t].out_cap = out_cap;
        work[t].made = made;
    }
    Sha1 sha;
    sha1_init(&sha);
    uint64_t at = data_at, done = 0;
    const char *step = out_path ? "Extracting" : "Verifying";
    for (uint64_t first = 0; first < frames; first += batch) {
        size_t count = (size_t)(frames - first < batch ? frames - first : batch);
        uint64_t c_sum = 0, d_sum = 0;
        for (size_t f = 0; f < count; f++) {
            in_at[f] = c_sum;
            in_size[f] = get32(table + (first + f) * entry);
            out_at[f] = d_sum;
            out_cap[f] = get32(table + (first + f) * entry + 4);
            c_sum += in_size[f];
            d_sum += out_cap[f];
        }
        if (c_sum > in_room) {
            free(in);
            in = grab((size_t)c_sum);
            in_room = (size_t)c_sum;
        }
        if (d_sum > out_room) {
            free(rom);
            rom = grab((size_t)d_sum);
            out_room = (size_t)d_sum;
        }
        if (c_sum && read_at(fd, in, (size_t)c_sum, at) != 0) {
            fprintf(stderr, "can't read %s\n", in_path);
            close(fd);
            if (out >= 0) close(out);
            return IO;
        }
        for (int t = 0; t < threads; t++) {
            work[t].frames = count;
            work[t].in = in;
            work[t].out = rom;
            work[t].failed = 0;
        }
        if (run_threads(work, threads, decompress_frames)) {
            fprintf(stderr, "damaged: a frame doesn't unpack\n");
            close(fd);
            if (out >= 0) close(out);
            return DAMAGED;
        }
        sha1_update(&sha, rom, (size_t)d_sum);
        if (out >= 0 && write_all(out, rom, (size_t)d_sum) != 0) {
            fprintf(stderr, "can't write %s\n", out_path);
            close(fd);
            close(out);
            return IO;
        }
        at += c_sum;
        done += d_sum;
        fprintf(stderr, "%s, %.1f%% complete...\r", step, rom_size ? 100.0 * (double)done / (double)rom_size : 100.0);
    }
    close(fd);
    if (out >= 0 && (fsync(out) != 0 || close(out) != 0)) {
        fprintf(stderr, "can't finish %s\n", out_path);
        return IO;
    }
    fprintf(stderr, "\n");
    sha1_print(&sha);
    return DONE;
}

static int sha1_file(const char *path) {
    uint64_t size = 0;
    int fd = open_read(path, &size);
    if (fd < 0) {
        fprintf(stderr, "can't read %s\n", path);
        return IO;
    }
    uint8_t *buffer = grab(CHUNK);
    Sha1 sha;
    sha1_init(&sha);
    for (uint64_t at = 0; at < size;) {
        size_t n = (size_t)(size - at < CHUNK ? size - at : CHUNK);
        if (read_at(fd, buffer, n, at) != 0) {
            fprintf(stderr, "can't read %s\n", path);
            close(fd);
            return IO;
        }
        sha1_update(&sha, buffer, n);
        at += n;
        fprintf(stderr, "Verifying, %.1f%% complete...\r", 100.0 * (double)at / (double)size);
    }
    close(fd);
    fprintf(stderr, "\n");
    sha1_print(&sha);
    return DONE;
}

static int usage(void) {
    fprintf(stderr,
            "usage: z3ds info FILE...\n"
            "       z3ds compress [-l LEVEL] [-j THREADS] ROM OUT\n"
            "       z3ds verify [-j THREADS] FILE\n"
            "       z3ds decompress [-j THREADS] FILE OUT\n"
            "       z3ds sha1 FILE\n");
    return USAGE;
}

int main(int argc, char **argv) {
    if (argc < 3) return usage();
    const char *command = argv[1];
    if (strcmp(command, "info") == 0) return info(argc - 2, argv + 2);
    int level = 3, threads = 4, i = 2;
    for (; i + 1 < argc && argv[i][0] == '-' && argv[i][1] && argv[i][2] == 0; i += 2) {
        if (argv[i][1] == 'l') {
            level = atoi(argv[i + 1]);
        } else if (argv[i][1] == 'j') {
            threads = atoi(argv[i + 1]);
        } else {
            return usage();
        }
    }
    if (threads < 1) threads = 1;
    if (threads > MAX_THREADS) threads = MAX_THREADS;
    if (level < 1 || level > ZSTD_maxCLevel()) return usage();
    int left = argc - i;
    if (strcmp(command, "compress") == 0 && left == 2) return compress_rom(argv[i], argv[i + 1], level, threads);
    if (strcmp(command, "verify") == 0 && left == 1) return unpack(argv[i], NULL, threads);
    if (strcmp(command, "decompress") == 0 && left == 2) return unpack(argv[i], argv[i + 1], threads);
    if (strcmp(command, "sha1") == 0 && left == 1) return sha1_file(argv[i]);
    return usage();
}
