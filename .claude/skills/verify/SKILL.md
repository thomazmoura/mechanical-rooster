---
name: verify
description: Build, launch, and drive the RelentlessBadger Android app on the local emulator to verify changes end-to-end.
---

# Verifying RelentlessBadger changes

Full stack = Postgres (docker) + .NET API + Android emulator + app.
`./dev.sh` at the repo root automates all of it, but it streams logs in the
foreground; for agent use the manual steps below work better.

## Bring the stack up

```bash
# 1. DB (beware: a stale pre-rebrand mechanicalrooster-db container may squat on 5432)
docker compose -f docker-compose.yml up -d --wait

# 2. API (listens on :5000; "404" on / means it's up)
cd backend/RelentlessBadger.Api && setsid dotnet run --no-launch-profile > /tmp/api.log 2>&1 &
curl -s -o /dev/null -w '%{http_code}' http://localhost:5000/   # wait for 404

# 3. Emulator (AVD: pixel_api35), headless
emulator -avd pixel_api35 -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect &
adb wait-for-device
adb shell 'while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 2; done'

# 4. Install + launch
cd android && ./gradlew :app:installDebug
adb shell monkey -p com.relentlessbadger.app -c android.intent.category.LAUNCHER 1
```

## Sign in

Fresh install shows a sign-in screen. Google sign-in is unconfigured in dev
builds, so use the **Dev sign-in (server dev bypass)** button. Server URL:
`http://10.0.2.2:5000` (host as seen from the emulator — no adb reverse
needed). Type it into the Server URL field, dismiss the keyboard (keyevent 4),
tap Dev sign-in. Then an Android notification-permission dialog appears —
tap Allow. Tasks in the dev DB sync in automatically.

## Driving / observing

- Screenshot: `adb exec-out screencap -p > shot.png` (works headless).
- `adb shell input text` does NOT handle spaces — use `%s` or expect the
  text to be cut at the first space.
- Quick-add field ("What needs doing right away?") sits under the exact-alarms
  banner; tap it, type, tap the + button on its right.
- Emulator has exact alarms disabled → reminder alarms fire late; don't
  treat a "next nag now" that lingers as an app bug.
- App state reset: `adb shell pm clear com.relentlessbadger.app` (forces
  the sign-in screen again).

## Teardown

`./dev.sh down` stops API + emulator + DB.
