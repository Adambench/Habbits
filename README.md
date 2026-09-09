# Habbits

A fast, offline-first habit logger for Android — a standalone successor to the
Datacore/JSX habit tracker that currently lives inside an Obsidian vault.

Habits are organised along the day's prayer timeline (Anytime, Before Fajr,
Fajr, Shuruq, Dhuhr, Asr, Maghrib, Isha) with prayer times computed on-device,
and all existing history imported from the vault in one pass.

**Status: planning.** No application code yet — see [PLAN.md](PLAN.md) for the
full build plan, measured against the real vault data (36 habits, 238 days,
3,187 logged entries).

## Stack

Kotlin · Jetpack Compose · Room/SQLite · Adhan for prayer times.
Distribution via GitHub Releases (Obtainium) first, F-Droid once stable.

## Repository

- [`PLAN.md`](PLAN.md) — build plan, data model, milestones
- [`docs/legacy/obsidian-habit-logger.jsx`](docs/legacy/obsidian-habit-logger.jsx) —
  the original Datacore component, kept verbatim as a behavioural reference

## License

[GPL-3.0](LICENSE).
