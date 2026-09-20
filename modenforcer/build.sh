#!/usr/bin/env bash
# Build script for ModEnforcer Fabric mod
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVER_DIR="${1:-/var/lib/pufferpanel/servers/35ca4939}"

echo "=== Building ModEnforcer Mod ==="
echo "Base Directory:   $DIR"
echo "Server Directory: $SERVER_DIR"

python3 - <<EOF
import os, sys, glob, subprocess, shutil, zipfile

base_dir = "$DIR"
server_dir = "$SERVER_DIR"

src_dir = os.path.join(base_dir, "src/main/java")
res_dir = os.path.join(base_dir, "src/main/resources")
build_dir = os.path.join(base_dir, "build/classes")
out_jar = os.path.join(base_dir, "modenforcer-1.0.0+26.3.jar")

if os.path.exists(os.path.join(base_dir, "build")):
    shutil.rmtree(os.path.join(base_dir, "build"))
os.makedirs(build_dir, exist_ok=True)

cp = []
# Find server jar
for root, dirs, files in os.walk(os.path.join(server_dir, "versions")):
    for f in files:
        if f.endswith(".jar"):
            cp.append(os.path.join(root, f))

# Find server libraries
lib_dir = os.path.join(server_dir, "libraries")
if os.path.exists(lib_dir):
    for root, dirs, files in os.walk(lib_dir):
        for f in files:
            if f.endswith(".jar"):
                cp.append(os.path.join(root, f))

# Find fabric processed mods
for f in glob.glob(os.path.join(server_dir, ".fabric/processedMods/*.jar")):
    cp.append(f)

classpath = ":".join(cp)

java_files = []
for root, dirs, files in os.walk(src_dir):
    for f in files:
        if f.endswith(".java"):
            java_files.append(os.path.join(root, f))

if not java_files:
    print("Error: No java files found in", src_dir)
    sys.exit(1)

print(f"Compiling {len(java_files)} Java source files...")
cmd = ["javac", "-cp", classpath, "-d", build_dir] + java_files
res = subprocess.run(cmd, capture_output=True, text=True)
if res.returncode != 0:
    print("Compilation failed!")
    print(res.stderr)
    sys.exit(1)

print("Compilation successful! Packaging JAR...")
with zipfile.ZipFile(out_jar, "w", compression=zipfile.ZIP_DEFLATED) as z:
    for root, dirs, files in os.walk(build_dir):
        for f in files:
            full = os.path.join(root, f)
            rel = os.path.relpath(full, build_dir)
            z.write(full, rel)
    for root, dirs, files in os.walk(res_dir):
        for f in files:
            full = os.path.join(root, f)
            rel = os.path.relpath(full, res_dir)
            z.write(full, rel)

# Cleanup build classes directory
shutil.rmtree(os.path.join(base_dir, "build"))

print(f"Successfully built: {out_jar} ({os.path.getsize(out_jar)} bytes)")
EOF

echo "Done!"
