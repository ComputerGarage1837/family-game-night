# Family Game Night

An Android compilation of our family's favourite card and board games, played around an ornate
round table in a castle hall. So far: **Go Fish** and **Crazy Eights**.

- 2–10 players: people on this phone, people on other phones over Wi-Fi, and computer players
- Computer players at **Easy** (young child), **Normal** (teen) and **Hard** (adult). They never
  cheat: they only know their own cards and what's been said at the table. The difference is how
  much they remember. Pure games of chance won't have difficulty levels.
- Every step is yours to take: tap the glowing cards to hand them over, press **Go fish!**, tap the
  pond or deck to draw. Subtle glows show what you can do.
- Computer player speed: Relaxed, Normal or Fast (Settings, or the in-game ☰ menu)
- Optional house rules for each game, shown before you start and on the rules screen
- Pass-and-play on one phone, with the screen covered between turns so nobody peeks
- Wi-Fi (LAN) games: the host's phone runs the game and others join from the same network
- If someone leaves or drops out, the game pauses and the host picks: **replace them with a
  computer player**, **save & quit**, or **wait for them to reconnect**
- The host can save at any time and resume later (Wi-Fi players just rejoin). Games are also
  auto-saved once a round in case of a crash; quitting without saving removes that auto-save.
- Users are stored on the device, each with an optional photo avatar that's shown at the table
- **Update** button plus an automatic update check from this repo's GitHub Releases

## Installing

**Newest test build (direct download):**
https://github.com/ComputerGarage1837/family-game-night/releases/download/test-build/family-game-night-test.apk

1. Open the repo's **Releases** page on your phone and download the newest `.apk`.
2. Open it. Android will ask you to allow installs from your browser/files app. Allow it once.
3. After that, the app checks for new versions each time it opens (Settings → *Check for updates
   automatically*), or you can tap **Check for updates** on the home screen. An update downloads
   and opens Android's installer, which needs one tap. Android doesn't let side-loaded apps
   update themselves completely silently.

Each build is also attached to its GitHub Actions run as an artifact, which is handy for testing
a branch.

## Turning on auto-updates (one-time setup)

Android only installs an update if it's signed with the **same key** as the installed app, so the
build needs a permanent signing key stored as GitHub secrets. Until you add them, the workflow
still builds and tests everything but doesn't publish releases.

1. Create a key on any computer with Java installed (keep the file and passwords somewhere safe):

   ```sh
   keytool -genkeypair -v -keystore family-game-night.jks -alias family \
     -keyalg RSA -keysize 4096 -validity 36500
   ```

2. Turn it into text: `base64 -w0 family-game-night.jks > keystore.txt` (on macOS: `base64 -i family-game-night.jks -o keystore.txt`).
3. In GitHub, go to **Settings → Secrets and variables → Actions** and add:
   - `SIGNING_KEYSTORE_BASE64`: the contents of `keystore.txt`
   - `SIGNING_STORE_PASSWORD`: the keystore password
   - `SIGNING_KEY_ALIAS`: `family` (or whatever alias you chose)
   - `SIGNING_KEY_PASSWORD`: the key password (often the same as the store password)
4. Push to `main`. Each push builds, tests and publishes release `v1.0.<build number>`, and the
   app on everyone's phone will offer the update.

If you installed a test build signed differently (for example, an artifact built before the
secrets existed), uninstall it once before installing the first properly signed release.

## How it's built

| Part | What it does |
| --- | --- |
| `core/` | Plain Kotlin, no Android. Holds the game rules, the AI, saves, the host session (pausing, replacing players) and the Wi-Fi protocol. Fully unit tested, including real socket tests for dropping out and reconnecting. |
| `app/` | The Android app (Jetpack Compose): menus, users and avatars, the castle table scene, network discovery and the updater. |

The table is drawn "2.5D": everything is placed with a real perspective camera (table, chairs,
floating cards), but it's drawn in code rather than with a 3D engine and models. The drawing is
kept separate from the game logic, so it could be swapped for a full 3D engine later without
touching the rules or networking.

### Adding a game

1. Write the rules as a `TypedGameModule` in `core/` (see `gofish/`), including its `GameInfo`
   (player counts, optional rules, whether hands are hidden, whether AI has skill levels).
2. Register it in `core/.../game/Games.kt`.
3. Add its table UI in `app/.../ui/table/` and hook it up in `GameScreen`.

### Building locally

- Engine and tests only (no Android SDK needed): `CORE_ONLY=1 ./gradlew :core:test`
- Whole app: `./gradlew :app:assembleDebug` (needs the Android SDK)
