# Habbits — Build Plan

A native Android habit logger that replaces the Datacore/JSX tracker currently
embedded in the `LifeOS` Obsidian vault. Offline-first, fast, and structured for
F-Droid from day one.

> Reference implementation: [`docs/legacy/obsidian-habit-logger.jsx`](docs/legacy/obsidian-habit-logger.jsx) (575 lines, captured verbatim from `Habit logger.md`).

---

## 1. What exists today (measured, not assumed)

Read directly from `/home/zayn/Obsidian/LifeOS` on 2026-09-08:

| Fact | Value |
|---|---|
| Configured habits | **36**, in `Habit logger.md` frontmatter (`habit_config`) |
| Daily notes with habit data | **238** (`Daily Notes/yyyy/MM/yyyy-MM-dd.md`) |
| Date range | **2026-01-01 → 2026-08-29** |
| Total logged entries | **3,187** |
| Value types | 1,780 boolean `true`, 1,407 integers, **0 malformed** |
| Distinct habit IDs in history | **57** |
| Orphans (logged, but deleted from config) | **24** |
| Configured but never logged | 3 |
| `Templates/Prayer Times.md` | **0 bytes — empty** |
| Stray file under `Daily Notes/` | `Daily Note Template.md` (must be skipped) |
| Other frontmatter keys seen | `pomodoros` (17 notes), `timeblocks` (8 notes) |

Two findings drive real design decisions:

1. **The prayer-time feature is dead code.** `Prayer Times.md` is empty, so
   `prayerTimes` is always `{}`, `parseT` always returns `null`, and the
   auto-scroll effect never fires. Computing times on-device is a genuine
   upgrade, not a port.
2. **24 orphan habit IDs hold real history.** They were deleted from the config
   but their entries remain in the notes. Dropping them would silently discard
   months of data and make every future streak statistic a lie.

## 2. Decisions

| Decision | Choice | Why |
|---|---|---|
| Stack | **Kotlin Multiplatform + Compose Multiplatform** | One shared codebase for Android and Linux desktop: UI, domain and data layers are written once. Android stays fully native Compose, so nothing is given up on the phone. |
| Storage | **SQLite via Room**, local to each device | The day view becomes one indexed query and the dashboard becomes SQL aggregation. The database is a per-device cache — the synced event log is the source of truth. |
| Obsidian | **Standalone, one-time import** | No SAF permissions, no YAML parsing at runtime. JSON export covers backup. |
| Desktop | **Compose Multiplatform on the JVM**, packaged as RPM/DEB | Verified: Room 2.8.4 and androidx.sqlite 2.7.0 both publish `jvm` and `linuxX64` variants, so the same schema and DAOs run on both platforms. |
| Sync | **Per-device append-only event log in a synced folder** | No account, no server, no quota, and transport-agnostic — Syncthing, Nextcloud, git, or a USB stick all work. See [`docs/sync-design.md`](docs/sync-design.md). |
| Prayer times | **Computed on-device (Adhan)** | Set location + calculation method once; exact times forever, offline, no file to maintain. |
| Distribution | **GitHub Releases + Obtainium now, F-Droid RFP later** | APK on the phone in days with auto-updates, while the repo stays F-Droid-compliant so the RFP is a formality later. |
| License | **GPL-3.0** — decided | F-Droid's norm for copyleft apps. Full text in [`LICENSE`](LICENSE). |
| Application ID | **Deferred** — proposal: `dev.adambench.habbits` | Permanent and unchangeable after first release. Must be fixed before M7 (first signed release), not before M0. |

**Dependency injection:** manual (a small `AppContainer`). Hilt's annotation
processing would add build time and method count for an app with roughly six
injectable objects.

## 3. Environment prerequisites

Verified on this machine — both are blockers for M0:

- **JDK 25 is installed and the Android Gradle Plugin does not support it.**
  Install JDK 21 (`java-21-openjdk-devel`) and pin it via a Gradle toolchain, so
  the system default stays untouched.
- **No Android SDK, no Gradle.** Install `cmdline-tools`, then via `sdkmanager`:
  `platform-tools`, the current stable `platforms;android-NN`, and matching
  `build-tools`. Roughly 2–3 GB. Gradle arrives via the wrapper — nothing global.

Testing runs on the physical phone over `adb` (USB or wireless). An emulator is
optional; Fedora has KVM if it is wanted later.

Exact versions of Kotlin, AGP, Gradle and the Compose BOM get pinned at M0 from
whatever is current stable then, and recorded in `gradle/libs.versions.toml`.

## 4. Data model

```
habits
  id             TEXT PRIMARY KEY   -- legacy IDs preserved verbatim
  label          TEXT NOT NULL
  description    TEXT
  category       INTEGER NOT NULL   -- 0..7, ordinal = display order
  type           INTEGER NOT NULL   -- dua|adkar|prayer|reading|other
  unit           TEXT
  default_value  INTEGER
  step           INTEGER            -- default 25
  frequency_type INTEGER NOT NULL   -- daily|weekly|interval
  recurring_days INTEGER NOT NULL   -- 7-bit mask, Mon=bit0
  interval_days  INTEGER
  interval_start INTEGER            -- epochDay
  status         INTEGER NOT NULL   -- active|sleeping|archived
  sort_order     INTEGER NOT NULL
  created_at     INTEGER NOT NULL

entries
  date       INTEGER NOT NULL   -- LocalDate.toEpochDay()
  habit_id   TEXT NOT NULL REFERENCES habits(id) ON DELETE CASCADE
  value      INTEGER            -- NULL = plain completion; non-null = measured
  logged_at  INTEGER NOT NULL
  PRIMARY KEY (date, habit_id)

INDEX entries(habit_id, date)     -- per-habit streaks and Phase 2 charts
```

Three points worth stating explicitly:

- **Row presence means completion**, exactly matching the legacy semantics where
  `delete habitsData[id]` un-completes a habit. `value NULL` is the faithful
  mapping of `true`; an integer maps to itself. No sentinel values, no ambiguity.
- **`date` is an epoch day, not a timestamp**, so it is immune to timezone and
  DST drift. A log written at 23:50 belongs to that local calendar date.
- **`recurring_days` is a bitmask** rather than a list table — seven booleans do
  not deserve a join.

Sync adds one column to each table, `hlc TEXT NOT NULL`: the hybrid logical clock
of the write that produced the row. That is what makes the merge deterministic
and clock-drift-proof. Nothing else in the schema changes, because the database
is a projection of the event log rather than the thing being synced.

**Performance budget:** cold start < 400 ms; day switch < 16 ms; zero disk I/O on
the main thread (all DAO calls are `suspend`/`Flow`); full database under 1 MB.

## 5. Import pipeline

A one-time migration, built as two halves that share a format.

**`tools/import/` — a Node CLI** that reads the vault and emits a single
`habits-export.json`. Deterministic, diffable, testable on the desktop where the
data lives, and re-runnable without touching the phone.

**In-app JSON import** via the system file picker. Critically, this consumes the
*same* schema the app's own backup export produces — so import and backup-restore
are one code path, not two.

The importer must handle, specifically:

- 36 configured habits → `habits`, preserving array order as `sort_order`.
- 24 orphan IDs → auto-created with `status = archived` and a label derived from
  the ID (`duaYunusMorning` → "Dua Yunus Morning"), so 8 months of history stays
  attached to something real. A post-import review screen lists them for renaming,
  merging into an existing habit, or deleting.
- `true` → `value = NULL`; integers → `value = n`.
- Skip any filename that is not `yyyy-MM-dd.md` (catches `Daily Note Template.md`).
- Preserve `pomodoros` and `timeblocks` into the export JSON but leave them
  unused — cheap insurance for the Phase 2 dashboard.
- **Idempotent**: upsert on `(date, habit_id)`, so re-running never duplicates.

**Verification gate:** the CLI prints a reconciliation report (files scanned,
entries parsed, per-habit counts, orphans found) and the app shows the same
totals after import. The migration is only accepted when it reports exactly
**3,187 entries across 238 days for 57 habits**. That number is the test.

## 6. UI plan

Keep what already works: the category timeline with its rail and dots,
colour-by-type, the check circle, the duration pill, the quick-add steppers, and
the edit-mode toggle. The changes below are the "make it better" half.

**Ergonomics**

1. **Week strip + swipeable days.** A 7-day row with per-day completion dots,
   backed by a `HorizontalPager`, replaces tapping `‹` `›`. Day changes stop being
   a two-tap operation.
2. **Thumb-reachable controls.** Date navigation and primary actions move within
   reach on a phone-sized screen instead of sitting at the top edge.
3. **Bottom sheet editor** replaces the centred modal — native, one-handed, and
   keyboard-aware.
4. **Long-press → inline stepper**, with hold-to-repeat on ±, plus direct numeric
   entry for large values.
5. **Drag-to-reorder** in edit mode, replacing `↑`/`↓` (which currently cost two
   taps per position and can silently move a habit across categories). Arrow
   buttons stay as an accessibility fallback.
6. **Swipe gestures** on a card: right to complete, left to sleep.

**Information**

7. **Header summary** — today's completion ring and current streak. Motivating on
   its own, and it is the first brick of the Phase 2 dashboard.
8. **Sticky category headers** showing the computed prayer time, with the active
   window marked and auto-scrolled to — the feature that has never actually run.
9. **Descriptions surfaced on demand.** 28 of 36 habits have a description that
   the current UI deliberately never renders. An expand-on-long-press keeps the
   list clean without wasting the content.

**Feel**

10. **Material 3 with dynamic colour**, plus a true-black AMOLED dark theme. The
    five type colours are retained but retuned to clear WCAG AA contrast in both
    themes.
11. **Haptics on toggle** and spring animation on the check.
12. **Undo snackbar** for toggles and deletes.
13. **Accessibility**: 48 dp targets, TalkBack labels, and no state conveyed by
    colour alone (checkmark plus fill).

## 7. Feature parity checklist

Ported from the legacy component, verified item by item before M6 closes:

- [ ] 8 categories: Anytime, Before Fajr, Fajr, Shuruq, Dhuhr, Asr, Maghrib, Isha
- [ ] 5 type colours: dua, adkar, prayer, reading, other
- [ ] Toggle completion; default value on completion
- [ ] Unit label, step, and ±step / ±2×step quick-add
- [ ] Frequency: daily, weekly (specific weekdays), interval (every N days from a start date)
- [ ] Sleeping status — greyed and hidden outside edit mode
- [ ] Reorder within and across categories
- [ ] Create, edit, delete a habit
- [ ] Date navigation and "back to today"
- [ ] Auto-scroll to the current prayer window (toggleable)
- [ ] The `migrateTrueToDuration` "Convert 'True'" tool → becomes an explicit
      import-time choice plus a settings action, rather than a destructive
      vault-wide rewrite

## 8. Repository layout

```
shared/                    Kotlin Multiplatform library — all cross-platform code
  src/commonMain/kotlin/dev/adambench/habbits/
    data/                  Room entities, DAOs, repository
    domain/                scheduling rules, streaks, prayer windows
    sync/                  event log, hybrid logical clock, merge
    ui/                    Compose screens, theme, components
    di/                    AppContainer
  src/androidMain/         Android actuals (dynamic colour, file paths)
  src/jvmMain/             desktop actuals
  src/commonTest/          scheduling, merge and import-mapping tests
androidApp/                thin Android host: MainActivity, manifest, resources
desktopApp/                thin desktop host: window entry point, RPM/DEB packaging
tools/import/              Node CLI: vault → habits-export.json
fastlane/metadata/android/en-US/   F-Droid listing, changelogs, screenshots
docs/legacy/               original Datacore JSX reference
gradle/libs.versions.toml  pinned dependency versions
.github/workflows/         build + signed release on tag
```

## 9. Milestones

| # | Milestone | Done when |
|---|---|---|
| **M0** | Toolchain & skeleton | JDK 21 + Android SDK installed; shared Compose UI builds for Android **and** desktop |
| **M1** | Data layer | Room schema, DAOs, repository, unit tests for scheduling rules |
| **M2** | Import | CLI + in-app import reconcile to exactly 3,187 entries / 238 days / 57 habits |
| **M3** | Core day view | Categories, cards, toggle, stepper, date navigation — daily use possible |
| **M4** | Habit management | Editor sheet, all three frequency types, drag-reorder, sleep, delete |
| **M5** | Prayer times | Adhan integration, location/method settings, sticky headers, auto-scroll |
| **M6** | Polish | Animations, haptics, theming, accessibility, undo; parity checklist all green |
| **M6.5** | Sync | Event log, HLC ordering, deterministic merge, compaction; two devices converge |
| **M7** | Ship | Release keystore, CI release workflow, signed APK + desktop RPM/DEB, Obtainium tracking the repo |
| **M8** | *(later)* | Metrics and dashboard — the reason the data model is SQL |

M3 is the point at which the app becomes usable in place of Obsidian; everything
after it is improvement rather than migration.

## 10. Risks

| Risk | Mitigation |
|---|---|
| JDK 25 incompatible with AGP | Install JDK 21 and pin a Gradle toolchain; leave the system default alone |
| Android SDK download is 2–3 GB | Scripted one-time setup at M0 |
| Orphan IDs silently dropped | Explicit archived-habit path + a reconciliation count that must match 3,187 |
| Release keystore lost | Generate at M7, gitignored, backed up off-machine — losing it means the app can never be updated |
| Timezone/DST corrupting dates | `epochDay` from `LocalDate`, never an instant |
| Application ID churn | Placeholder until M7; settled before the first signed release, after which it can never change |
| F-Droid build reproducibility | No proprietary dependencies (Adhan is MIT, AndroidX is Apache-2.0); fastlane metadata maintained from M0 |
| Syncing the SQLite file would corrupt it | The database is never the sync unit — only append-only per-device logs are synced. See `docs/sync-design.md` |

## 11. Open items

- **Application ID** — deferred by choice. `dev.adambench.habbits` is the working
  placeholder; it must be settled before M7, since it can never change after the
  first signed release.
- Location and calculation method for prayer times (city + e.g. MWL, Umm al-Qura, ISNA).
- Whether the 24 orphan habits should be reviewed individually after import, or
  left archived in bulk.
