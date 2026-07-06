#!/usr/bin/env bash
# One-command local dev environment: PostgreSQL + API + Android emulator + app.
#
#   ./dev.sh          start everything, stream API logs (Ctrl+C stops the API,
#                     leaves the DB and emulator running for fast restarts)
#   ./dev.sh down     stop everything (API, emulator, database)
#
# Overrides: AVD=<name> API_PORT=<port> ./dev.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUN_DIR="$ROOT/.dev"
API_PORT="${API_PORT:-5000}"
APP_ID="com.mechanicalrooster.app"

# --- Android SDK ------------------------------------------------------------
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$SDK" && -f "$ROOT/android/local.properties" ]]; then
  SDK="$(sed -n 's/^sdk\.dir=//p' "$ROOT/android/local.properties")"
fi
SDK="${SDK:-$HOME/Android/Sdk}"
ADB="$SDK/platform-tools/adb"
EMULATOR="$SDK/emulator/emulator"

log() { printf '\033[1;33m[dev]\033[0m %s\n' "$*"; }

api_pid() { [[ -f "$RUN_DIR/api.pid" ]] && cat "$RUN_DIR/api.pid" || true; }

stop_api() {
  local pid; pid="$(api_pid)"
  if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
    log "Stopping API (pid $pid)"
    # dotnet watch spawns children (the watcher, the app); it runs in its own
    # process group (setsid below), so kill the whole group or they leak.
    kill -TERM -- "-$pid" 2>/dev/null || kill "$pid" 2>/dev/null || true
    for _ in $(seq 1 20); do kill -0 "$pid" 2>/dev/null || break; sleep 0.5; done
    kill -KILL -- "-$pid" 2>/dev/null || true
  fi
  rm -f "$RUN_DIR/api.pid"
}

if [[ "${1:-up}" == "down" ]]; then
  stop_api
  if "$ADB" devices | grep -q '^emulator-'; then
    log "Stopping emulator"
    for serial in $("$ADB" devices | sed -n 's/^\(emulator-[0-9]*\).*/\1/p'); do
      "$ADB" -s "$serial" emu kill || true
    done
  fi
  log "Stopping database"
  docker compose -f "$ROOT/docker-compose.yml" down
  log "All stopped."
  exit 0
fi

mkdir -p "$RUN_DIR"

# --- 1. Database ------------------------------------------------------------
log "Starting PostgreSQL (docker compose)..."
docker compose -f "$ROOT/docker-compose.yml" up -d --wait

# --- 2. Emulator (boots in the background while we build) --------------------
"$ADB" start-server >/dev/null 2>&1 || true
AVD="${AVD:-$("$EMULATOR" -list-avds 2>/dev/null | head -n1)}"
# Check the qemu process, not `adb devices`: the adb server can be briefly
# down/restarting, and an empty device list here would boot a duplicate.
if pgrep -f "qemu.*-avd ${AVD:-__none__}" >/dev/null || "$ADB" devices | grep -q '^emulator-'; then
  log "Emulator already running."
else
  if [[ -z "$AVD" ]]; then
    log "No AVD found. Create one in Android Studio (or: avdmanager create avd ...) and re-run."
    exit 1
  fi
  log "Booting emulator '$AVD' in the background..."
  # setsid: detach from this script's process group so Ctrl+C (or killing
  # the script) doesn't take the emulator down with it.
  setsid "$EMULATOR" -avd "$AVD" -netdelay none -netspeed full \
    >"$RUN_DIR/emulator.log" 2>&1 &
fi

# --- 3. API -----------------------------------------------------------------
pid="$(api_pid)"
if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
  log "API already running (pid $pid)."
else
  log "Starting API (dotnet watch run — hot-reloads on code changes)..."
  # setsid: own process group so stop_api can kill watch + app together.
  # RESTART_ON_RUDE_EDIT: auto-restart on edits hot reload can't handle,
  # instead of prompting (the prompt would hang — output goes to a log file).
  ( cd "$ROOT/backend/MechanicalRooster.Api" && \
    DOTNET_WATCH_RESTART_ON_RUDE_EDIT=true \
    exec setsid dotnet watch run --non-interactive >"$RUN_DIR/api.log" 2>&1 ) &
  echo $! >"$RUN_DIR/api.pid"
fi

# --- 4. Build the APK (overlaps with emulator boot) ---------------------------
log "Building the app (gradle assembleDebug)..."
( cd "$ROOT/android" && ./gradlew --console=plain -q assembleDebug )
APK="$ROOT/android/app/build/outputs/apk/debug/app-debug.apk"

# --- 5. Wait for the API ------------------------------------------------------
log "Waiting for the API on :$API_PORT..."
for _ in $(seq 1 60); do
  curl -s -o /dev/null "http://localhost:$API_PORT/" && break
  if [[ -n "$(api_pid)" ]] && ! kill -0 "$(api_pid)" 2>/dev/null; then
    log "API process died — last log lines:"
    tail -n 30 "$RUN_DIR/api.log"
    exit 1
  fi
  sleep 1
done
curl -s -o /dev/null "http://localhost:$API_PORT/" || { log "API never came up; see $RUN_DIR/api.log"; exit 1; }
log "API is up."

# --- 6. Wait for the emulator to finish booting ------------------------------
log "Waiting for the emulator to boot..."
"$ADB" wait-for-device
until [[ "$("$ADB" -e shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; do
  sleep 2
done
log "Emulator booted."

# --- 7. Install & launch the app ----------------------------------------------
# localhost:$API_PORT inside the emulator -> the host API (also reachable at 10.0.2.2)
"$ADB" -e reverse "tcp:$API_PORT" "tcp:$API_PORT"
log "Installing the app..."
"$ADB" -e install -r "$APK" >/dev/null
"$ADB" -e shell am start -n "$APP_ID/.MainActivity" >/dev/null
log "App launched. Server URL inside the emulator: http://localhost:$API_PORT"

# --- 8. Stream API logs --------------------------------------------------------
log "Streaming API logs (Ctrl+C stops the API; DB and emulator keep running)."
log "Stop everything with: ./dev.sh down"
trap 'echo; stop_api; exit 0' INT TERM
tail -f "$RUN_DIR/api.log" &
wait $!
