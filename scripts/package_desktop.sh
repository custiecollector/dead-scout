#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

scripts/build_desktop.sh

VERSION=$(python3 - <<'PY'
import re
from pathlib import Path
text = Path('app/build.gradle').read_text(encoding='utf-8')
match = re.search(r"versionName\s+'([^']+)'", text)
if not match:
    raise SystemExit('versionName not found in app/build.gradle')
print(match.group(1))
PY
)

PKG_ROOT="build/package"
LINUX_DIR="$PKG_ROOT/deadscout-${VERSION}-linux-desktop"
WINDOWS_DIR="$PKG_ROOT/deadscout-${VERSION}-windows-desktop"
LINUX_ZIP="build/deadscout-${VERSION}-linux-desktop.zip"
WINDOWS_ZIP="build/deadscout-${VERSION}-windows-desktop.zip"
SUMS="build/SHA256SUMS-${VERSION}.txt"

rm -rf "$LINUX_DIR" "$WINDOWS_DIR" "$LINUX_ZIP" "$WINDOWS_ZIP"
mkdir -p "$LINUX_DIR/docs" "$LINUX_DIR/scripts" "$WINDOWS_DIR/docs" "$WINDOWS_DIR/packaging/windows"

cp build/deadscout-desktop.jar build/deadscout-desktop "$LINUX_DIR/"
cp LICENSE docs/desktop-readme.txt "$LINUX_DIR/"
cp docs/deadscout-architecture.md docs/capture-helpers.md "$LINUX_DIR/docs/"
cp scripts/run_desktop.sh scripts/setup_capture_helpers_linux.sh "$LINUX_DIR/scripts/"

cp build/deadscout-desktop.jar "$WINDOWS_DIR/"
cp LICENSE docs/desktop-readme.txt "$WINDOWS_DIR/"
cp docs/deadscout-architecture.md docs/capture-helpers.md "$WINDOWS_DIR/docs/"
cp packaging/windows/README-WINDOWS.txt packaging/windows/build-windows-native.ps1 packaging/windows/DeadScout.iss packaging/windows/setup-capture-helpers-windows.ps1 packaging/windows/setup-capture-helpers-windows.cmd packaging/windows/deadscout-windows-capture-helper.ps1 packaging/windows/deadscout-windows-capture-helper.cmd "$WINDOWS_DIR/packaging/windows/"
cp packaging/windows/setup-capture-helpers-windows.ps1 packaging/windows/setup-capture-helpers-windows.cmd packaging/windows/deadscout-windows-capture-helper.ps1 packaging/windows/deadscout-windows-capture-helper.cmd "$WINDOWS_DIR/"

python3 - <<PY
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED

windows_dir = Path('$WINDOWS_DIR')

def write_crlf(src, dest):
    data = Path(src).read_text(encoding='utf-8')
    data = data.replace('\r\n', '\n').replace('\r', '\n')
    Path(dest).write_bytes(data.replace('\n', '\r\n').encode('utf-8'))

write_crlf('build/deadscout-windows.cmd', windows_dir / 'deadscout-windows.cmd')
write_crlf('packaging/windows/install-deadscout-windows.cmd.in', windows_dir / 'install-deadscout-windows.cmd')
write_crlf('packaging/windows/uninstall-deadscout-windows.cmd.in', windows_dir / 'uninstall-deadscout-windows.cmd')

def make_zip(src, dest):
    src = Path(src)
    dest = Path(dest)
    with ZipFile(dest, 'w', ZIP_DEFLATED) as zf:
        for path in sorted(src.rglob('*')):
            if path.is_file():
                zf.write(path, path.relative_to(src.parent))

make_zip('$LINUX_DIR', '$LINUX_ZIP')
make_zip('$WINDOWS_DIR', '$WINDOWS_ZIP')
PY

sha256sum build/deadscout-desktop.jar "$LINUX_ZIP" "$WINDOWS_ZIP" > "$SUMS"
printf 'Created %s\nCreated %s\nWrote %s\n' "$LINUX_ZIP" "$WINDOWS_ZIP" "$SUMS"
