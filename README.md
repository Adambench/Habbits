# Habbits

A fast, offline-first habit logger for **Android and Linux desktop** — a
standalone successor to the Datacore/JSX habit tracker that currently lives
inside an Obsidian vault.

Habits are organised along the day's prayer timeline (Anytime, Before Fajr,
Fajr, Shuruq, Dhuhr, Asr, Maghrib, Isha) with prayer times computed on-device,
and all existing history imported from the vault in one pass.

**Status: planning.** No application code yet — see [PLAN.md](PLAN.md) for the
full build plan, measured against the real vault data (36 habits, 238 days,
3,187 logged entries).

## Stack

Kotlin Multiplatform · Compose Multiplatform · Room/SQLite · Adhan for prayer
times. One shared codebase drives both the Android app and the desktop app.

Distribution via GitHub Releases (Obtainium) first, F-Droid once stable;
desktop ships as RPM and DEB.

## Sync

Devices sync through a **per-device append-only event log** in any synced
folder — Syncthing, Nextcloud, git, or a USB stick all work equally. No account,
no server, no quota. Each device writes only its own log file, so there is never
a conflict to resolve. See [`docs/sync-design.md`](docs/sync-design.md).

## Building

```sh
source tools/dev-env.sh
./gradlew :androidApp:assembleDebug          # APK
./gradlew :desktopApp:createDistributable    # desktop app
./gradlew :desktopApp:run                    # run desktop app
```

## Repository

- [`PLAN.md`](PLAN.md) — build plan, data model, milestones
- [`docs/sync-design.md`](docs/sync-design.md) — how multi-device sync works
- [`docs/legacy/obsidian-habit-logger.jsx`](docs/legacy/obsidian-habit-logger.jsx) —
  the original Datacore component, kept verbatim as a behavioural reference

## License

[GPL-3.0](LICENSE).
