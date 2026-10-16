"""Prints the licence notice of nodtool as built by build.sh: nod's own licences, then every crate compiled into the
program (normal dependencies of nodtool for Android) with its licence, then the licence texts those crates ship,
including those of the C libraries bundled in the -sys crates. Each distinct text is printed once.

    python notice.py <cargo metadata json> <version>
"""
import json
import os
import sys

NAMES = ("license", "licence", "copying", "notice", "copyright", "unlicense", "authors")
SKIP_DIRS = {"tests", "test", "benches", "examples", "doc", "docs", "target", ".git", "fuzz"}


def licence_files(root, deep):
    found = []
    for folder, dirs, files in os.walk(root):
        depth = 0 if folder == root else os.path.relpath(folder, root).count(os.sep) + 1
        dirs[:] = sorted(d for d in dirs if d.lower() not in SKIP_DIRS) if deep and depth < 3 else []
        for name in sorted(files):
            stem = name.lower().split(".")[0].replace("-", "_").split("_")[0]
            code = os.path.splitext(name)[1].lower() in (".sh", ".py", ".c", ".h", ".rs", ".in", ".am", ".cmake")
            if stem in NAMES and not code and os.path.getsize(os.path.join(folder, name)) < 200_000:
                found.append(os.path.join(folder, name))
    return found


def main():
    metadata = json.load(open(sys.argv[1], encoding="utf-8"))
    version = sys.argv[2]
    packages = {p["id"]: p for p in metadata["packages"]}
    nodes = {n["id"]: n for n in metadata["resolve"]["nodes"]}
    root = next(p["id"] for p in metadata["packages"] if p["name"] == "nodtool")

    # Crates compiled into the program: nodtool and its normal (not build or dev) dependencies, recursively
    shipped, todo = set(), [root]
    while todo:
        current = todo.pop()
        if current in shipped:
            continue
        shipped.add(current)
        for dep in nodes[current]["deps"]:
            if any(kind["kind"] is None for kind in dep["dep_kinds"]):
                todo.append(dep["pkg"])
    crates = sorted((packages[i] for i in shipped), key=lambda p: (p["name"], p["version"]))

    out = []
    out.append(f"nodtool {version} (from nod, GameCube and Wii disc image tool by Luke Street), built for Android from")
    out.append("its release sources: https://github.com/encounter/nod-rs (the v" + version + " tag, with the patches in")
    out.append("native/nod/patches of Thor Tools' repository). nod is licensed MIT OR Apache-2.0.")
    out.append("")
    out.append("=== Crates in the program and their licences ===")
    for p in crates:
        out.append(f"{p['name']} {p['version']}: {p.get('license') or p.get('license_file') or 'see its files'}")
    out.append("")

    texts = {}
    for p in crates:
        folder = os.path.dirname(p["manifest_path"])
        for path in licence_files(folder, deep=p["name"].endswith("-sys")):
            text = open(path, encoding="utf-8", errors="replace").read().strip()
            relative = os.path.relpath(path, folder).replace(os.sep, "/")
            texts.setdefault(text, []).append(f"{p['name']} {p['version']} ({relative})")
    out.append("=== Licence texts ===")
    for text, owners in texts.items():
        out.append("")
        out.append("--- " + "; ".join(owners) + " ---")
        out.append(text)
    sys.stdout.reconfigure(encoding="utf-8")
    print("\n".join(out))


main()
