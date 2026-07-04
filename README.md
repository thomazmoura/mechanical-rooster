# MechanicalRooster 🐓

A persistent-reminder to-do app: tasks are extremely fast to add, and the app **nags you with notifications until you mark them done**.

- **Backend** — .NET 10 Web API + PostgreSQL (`backend/`)
- **App** — native Android, Kotlin, Jetpack Compose + Material 3 (`android/`)
- **Auth** — Google Sign-In only (OAuth2); you log in once per device
- **Reminders** — scheduled locally on the device (AlarmManager), no Firebase needed; they work offline and survive reboots
- **Quick-add** — fzf-style fuzzy autocomplete over your task history: type `totr` and *take out trash* is suggested

## How reminders work

Each task snapshots two user-configurable defaults at creation time (Settings screen):

1. **First reminder after** N minutes (default 60)
2. **Then nag every** M minutes (default 15) — repeats until the task is completed

The server is the source of truth for tasks and settings; the app mirrors open tasks into a local Room database and drives all notifications from exact alarms, so the phone doesn't need to reach the server for reminders to fire. Tapping **Done** on the notification (or in the list) stops the nagging immediately and syncs the completion to the API (retried later if you're offline).

## Prerequisites

- .NET 10 SDK, Docker (for the API machine)
- JDK 17 + Android SDK (or just Android Studio) to build the app
- An Android device on the same network as the API machine

## 1. Run the backend

```bash
docker compose up -d                       # PostgreSQL 17 on :5432
cd backend/MechanicalRooster.Api
dotnet run                                 # http://0.0.0.0:5000, auto-applies migrations
```

The `Development` environment (the default with `dotnet run`) includes a dev JWT signing key and a **dev auth bypass**: `POST /auth/google` with `{"idToken": "dev-token"}` returns a valid session token without Google. Try it:

```bash
TOKEN=$(curl -s -X POST http://localhost:5000/auth/google \
  -H 'Content-Type: application/json' -d '{"idToken":"dev-token"}' | jq -r .token)
curl -s -X POST http://localhost:5000/tasks \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"title":"take out trash"}'
```

For anything beyond local development, set a real `Jwt:Key` and remove `Auth:DevBypassToken` (see `appsettings.Development.json`).

Make sure the phone can reach port 5000 (e.g. `sudo ufw allow 5000` on Ubuntu). Find the machine's LAN IP with `ip -4 addr` — you'll enter `http://<that-ip>:5000` in the app's sign-in screen.

## 2. Google OAuth setup (one-time)

The PoC only supports Google accounts. In [Google Cloud Console](https://console.cloud.google.com/):

1. Create a project → **APIs & Services → OAuth consent screen**. Choose *External*, stay in **Testing** mode, and add your Gmail address as a test user.
2. **Credentials → Create credentials → OAuth client ID**, twice:
   - **Android** client: package name `com.mechanicalrooster.app`, SHA-1 from your debug keystore:
     ```bash
     keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android | grep SHA1
     ```
   - **Web application** client: no redirect URIs needed. **Its client ID is the one both sides use.**
3. Put the **Web** client ID in two places:
   - `backend/MechanicalRooster.Api/appsettings.json` → `Auth:GoogleClientId`
   - `android/gradle.properties` → `ROOSTER_GOOGLE_WEB_CLIENT_ID`

If `ROOSTER_GOOGLE_WEB_CLIENT_ID` is left empty, the app shows a **Dev sign-in** button instead of Google — handy for testing the whole flow before OAuth is configured (requires the server's dev bypass).

## 3. Build & install the app

```bash
cd android
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

On first launch: enter the server URL, sign in with Google (once — the session token lasts ~180 days), allow notifications, and optionally grant exact alarms if the banner appears. Traffic is plain HTTP, intended for a trusted LAN only.

## API

| Method | Route | Purpose |
|---|---|---|
| POST | `/auth/google` | exchange a Google ID token for an app JWT |
| GET/PUT | `/me/settings` | default initial delay + repeat interval |
| GET | `/tasks?status=open\|done\|all` | list tasks |
| POST | `/tasks` | create a task `{"title": "..."}` |
| POST | `/tasks/{id}/complete` | mark done |
| DELETE | `/tasks/{id}` | delete |
| GET | `/tasks/titles` | distinct past titles (frequency-ordered) feeding the fuzzy autocomplete |

## Tests

```bash
cd backend && dotnet test        # API integration tests (in-memory SQLite, fake Google validator)
cd android && ./gradlew test     # fuzzy-matcher + reminder-scheduling unit tests
```

## Repo layout

```
├── docker-compose.yml            # PostgreSQL 17
├── backend/
│   ├── MechanicalRooster.Api/    # minimal-API endpoints, EF Core + Npgsql, JWT auth
│   └── MechanicalRooster.Api.Tests/
└── android/
    └── app/src/main/java/com/mechanicalrooster/app/
        ├── data/                 # Retrofit client, DataStore session, repository
        ├── db/                   # Room cache of open tasks (drives alarms)
        ├── fuzzy/                # fzf-style matcher for quick-add suggestions
        ├── notify/               # AlarmManager scheduling, receivers, notifications
        └── ui/                   # Compose Material 3 screens
```
