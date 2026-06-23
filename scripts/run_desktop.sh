#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

if [ -n "${JAVA_HOME:-}" ]; then
  export PATH="$JAVA_HOME/bin:$PATH"
fi
exec java -jar build/deadscout-desktop.jar "$@"
