#!/usr/bin/env bash
# Check and install everything needed to build and run RelentlessBadger.
#
#   ./install-deps.sh
#
# Idempotent: safe to re-run; already-satisfied dependencies are skipped.
# PostgreSQL is intentionally NOT installed — it runs in Docker (docker-compose.yml),
# and the API image builds in Docker too (backend/Dockerfile). Only the tools that
# can't be containerized are installed locally: .NET SDK (dev.sh hot-reloads via
# dotnet watch), JDK 17 (Gradle), and the Android SDK (adb needs device access,
# the emulator needs KVM).
#
# Installs use apt (Debian/Ubuntu) and prompt for sudo only when something is missing.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BIN_DIR="$HOME/.local/bin"
CMDLINE_TOOLS_URL="${CMDLINE_TOOLS_URL:-https://dl.google.com/android/repository/commandline-tools-linux-11076708_latest.zip}"

log()  { printf '\033[1;33m[deps]\033[0m %s\n' "$*"; }
ok()   { printf '\033[1;32m[deps]\033[0m %s\n' "$*"; }
warn() { printf '\033[1;31m[deps]\033[0m %s\n' "$*"; }

SUMMARY=()
ACTIONS=()
note()   { SUMMARY+=("$1"); }
action() { ACTIONS+=("$1"); }

SUDO="sudo"
[[ "$(id -u)" == "0" ]] && SUDO=""

APT_UPDATED=0
apt_install() {
  command -v apt-get >/dev/null || { warn "Need to install '$*' but this system has no apt-get — install it manually and re-run."; exit 1; }
  if [[ "$APT_UPDATED" == "0" ]]; then $SUDO apt-get update -qq; APT_UPDATED=1; fi
  $SUDO apt-get install -y -qq "$@"
}

# --- 1. Base CLIs (used by README examples and dev.sh) ------------------------
for cmd in curl jq unzip openssl git; do
  if command -v "$cmd" >/dev/null; then
    note "$cmd: found"
  else
    log "Installing $cmd..."
    apt_install "$cmd"
    note "$cmd: installed"
  fi
done

# --- 2. Docker Engine + compose plugin (runs PostgreSQL; builds the API image) -
if command -v docker >/dev/null; then
  note "docker: found ($(docker --version))"
else
  log "Installing Docker Engine from the official Docker apt repository..."
  apt_install ca-certificates
  $SUDO install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg | $SUDO tee /etc/apt/keyrings/docker.asc >/dev/null
  $SUDO chmod a+r /etc/apt/keyrings/docker.asc
  echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] \
https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" |
    $SUDO tee /etc/apt/sources.list.d/docker.list >/dev/null
  APT_UPDATED=0
  apt_install docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
  note "docker: installed"
fi

if docker compose version >/dev/null 2>&1; then
  note "docker compose: found ($(docker compose version --short 2>/dev/null || echo ok))"
else
  log "Installing the docker compose plugin..."
  apt_install docker-compose-plugin
  note "docker compose: installed"
fi

# Rootless Docker needs no group; only set up the group when the daemon is
# unreachable AND the user isn't a member yet.
if docker info >/dev/null 2>&1; then
  note "docker daemon: accessible ('$(docker context show 2>/dev/null || echo default)' context)"
elif id -nG "$USER" | tr ' ' '\n' | grep -qx docker; then
  note "docker group: member"
  action "Docker daemon not reachable — is it running? Try 'sudo systemctl start docker'."
else
  log "Adding $USER to the docker group..."
  $SUDO usermod -aG docker "$USER"
  note "docker group: added"
  action "Log out and back in (or run 'newgrp docker') so docker works without sudo."
fi

# --- 3. .NET 10 SDK (local: dev.sh needs 'dotnet watch run' for hot reload) ----
if command -v dotnet >/dev/null && dotnet --list-sdks 2>/dev/null | grep -q '^10\.'; then
  note ".NET 10 SDK: found ($(dotnet --version))"
else
  log "Installing the .NET 10 SDK..."
  apt_install dotnet-sdk-10.0
  note ".NET 10 SDK: installed"
fi

# --- 4. JDK 17 (Gradle/AGP; also provides keytool for the OAuth SHA-1) --------
java_major() { java -version 2>&1 | awk -F'"' '/version/ {split($2, v, "."); print (v[1] == "1") ? v[2] : v[1]; exit}'; }
if command -v java >/dev/null && [[ "$(java_major)" -ge 17 ]] 2>/dev/null; then
  note "JDK: found (java $(java_major))"
else
  log "Installing OpenJDK 17..."
  apt_install openjdk-17-jdk
  note "JDK: installed (openjdk-17)"
fi

# --- 5. Android SDK (local: adb needs device access, emulator needs KVM) ------
# Same resolution order as dev.sh.
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$SDK" && -f "$ROOT/android/local.properties" ]]; then
  SDK="$(sed -n 's/^sdk\.dir=//p' "$ROOT/android/local.properties")"
fi
SDK="${SDK:-$HOME/Android/Sdk}"

SDKMANAGER="$SDK/cmdline-tools/latest/bin/sdkmanager"
if [[ ! -x "$SDKMANAGER" ]] && command -v sdkmanager >/dev/null; then
  SDKMANAGER="$(command -v sdkmanager)"
fi

if [[ ! -x "$SDKMANAGER" ]]; then
  log "Installing Android command-line tools into $SDK..."
  tmp="$(mktemp -d)"
  curl -fsSL "$CMDLINE_TOOLS_URL" -o "$tmp/cmdline-tools.zip"
  unzip -q "$tmp/cmdline-tools.zip" -d "$tmp"
  mkdir -p "$SDK/cmdline-tools"
  mv "$tmp/cmdline-tools" "$SDK/cmdline-tools/latest"
  rm -rf "$tmp"
  SDKMANAGER="$SDK/cmdline-tools/latest/bin/sdkmanager"
  note "Android cmdline-tools: installed"
else
  note "Android cmdline-tools: found"
fi

if [[ -x "$SDK/platform-tools/adb" && -d "$SDK/platforms/android-35" ]]; then
  note "Android SDK packages: found ($SDK)"
else
  log "Installing Android SDK packages (platform-tools, android-35, build-tools, emulator)..."
  yes | "$SDKMANAGER" --sdk_root="$SDK" --licenses >/dev/null
  "$SDKMANAGER" --sdk_root="$SDK" --install \
    "platform-tools" "platforms;android-35" "build-tools;35.0.0" "emulator"
  note "Android SDK packages: installed ($SDK)"
fi

if [[ -f "$ROOT/android/local.properties" ]] && grep -q '^sdk\.dir=' "$ROOT/android/local.properties"; then
  note "android/local.properties: found"
else
  log "Writing sdk.dir to android/local.properties..."
  echo "sdk.dir=$SDK" >>"$ROOT/android/local.properties"
  note "android/local.properties: written"
fi

# --- 6. Put adb/emulator on PATH (symlinks — shell-agnostic, works in pwsh too) -
mkdir -p "$BIN_DIR"
for tool in platform-tools/adb emulator/emulator; do
  name="$(basename "$tool")"
  if [[ -x "$SDK/$tool" ]]; then
    ln -sfn "$SDK/$tool" "$BIN_DIR/$name"
    note "$name: symlinked to $BIN_DIR/$name"
  else
    warn "$SDK/$tool not found — skipping its symlink."
  fi
done
case ":$PATH:" in
  *":$BIN_DIR:"*) ;;
  *) action "Add $BIN_DIR to your PATH — bash: 'export PATH=\"\$HOME/.local/bin:\$PATH\"' in ~/.bashrc; pwsh: '\$env:PATH = \"\$HOME/.local/bin:\$env:PATH\"' in \$PROFILE." ;;
esac

# --- 7. Emulator environment (warn-only) ---------------------------------------
if [[ ! -e /dev/kvm ]]; then
  action "No /dev/kvm — enable virtualization in BIOS/UEFI or the emulator will be very slow."
elif [[ ! -r /dev/kvm || ! -w /dev/kvm ]]; then
  action "No access to /dev/kvm — run 'sudo usermod -aG kvm $USER' and log out/in for emulator acceleration."
fi
if [[ -x "$SDK/emulator/emulator" ]] && [[ -z "$("$SDK/emulator/emulator" -list-avds 2>/dev/null)" ]]; then
  action "No AVD exists (dev.sh needs one) — create it in Android Studio's Device Manager, or:
       $SDKMANAGER --install 'system-images;android-35;google_apis;x86_64'
       $SDK/cmdline-tools/latest/bin/avdmanager create avd -n badger -k 'system-images;android-35;google_apis;x86_64'"
fi

# --- Summary --------------------------------------------------------------------
echo
ok "Dependency summary:"
for line in "${SUMMARY[@]}"; do printf '  - %s\n' "$line"; done
if [[ "${#ACTIONS[@]}" -gt 0 ]]; then
  echo
  warn "Action needed:"
  for line in "${ACTIONS[@]}"; do printf '  ! %s\n' "$line"; done
else
  echo
  ok "All set. Run ./dev.sh to start everything."
fi
