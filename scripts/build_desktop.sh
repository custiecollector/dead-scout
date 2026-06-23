#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

if [ -n "${JAVA_HOME:-}" ]; then
  export PATH="$JAVA_HOME/bin:$PATH"
fi

OUT="build/desktop/classes"
DESKTOP_JAR="build/deadscout-desktop.jar"
rm -rf "$OUT"
mkdir -p "$OUT" build

javac -encoding UTF-8 -d "$OUT" \
  app/src/main/java/org/deadscout/core/*.java \
  desktop/src/org/deadscout/desktop/*.java

jar --create --file "$DESKTOP_JAR" --main-class org.deadscout.desktop.DeadScoutDesktopGui -C "$OUT" .

cat > build/deadscout-desktop <<'LAUNCHER'
#!/usr/bin/env bash
set -euo pipefail
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  JAVA_BIN="$JAVA_HOME/bin/java"
else
  JAVA_BIN="java"
fi
exec "$JAVA_BIN" -jar "$(dirname "$0")/deadscout-desktop.jar" "$@"
LAUNCHER
chmod +x build/deadscout-desktop

cat > build/deadscout-windows.cmd <<'WINLAUNCHER'
@echo off
setlocal
set "DIR=%~dp0"
where java >NUL 2>NUL
if errorlevel 1 (
  echo Java 17 or newer is required. Install Temurin/OpenJDK 17+, then run this again.
  pause
  exit /b 1
)
java -jar "%DIR%deadscout-desktop.jar" %*
WINLAUNCHER

printf 'Built %s\n' "$DESKTOP_JAR"
