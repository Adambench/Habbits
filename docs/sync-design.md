# Sync design

Requirement: sync between phone and desktop **with no limits, by any method** —
no account, no server, no vendor, no quota.

## Why the database file is not the sync unit

The obvious approach — put `habbits.db` in Syncthing or Nextcloud — is wrong and
would lose data:

- SQLite writes through WAL and journal side-files. A sync tool copying the
  main file mid-write produces a torn database.
- Two devices editing on the same day both rewrite the whole file, so the sync
  tool sees a conflict on an opaque binary blob. Its only options are "keep
  mine" or "keep theirs" — one device's day is silently discarded.
- Conflict copies (`habbits.sync-conflict-2026….db`) are unmergeable by hand.

So the database stays **local and private to each device**. It is a cache, not
the source of truth.

## What is synced: a per-device append-only log

The synced folder holds one log file per device:

```
<synced folder>/habbits/
  device-3f9a.jsonl      written only by the phone
  device-b12c.jsonl      written only by the desktop
  snapshot-2026-09.json  periodic compaction (optional)
```

**A device only ever writes its own file.** Two devices therefore never write
the same file, so a file-level sync tool has no conflict to resolve — ever. That
single property is what makes "any method" true: whatever moves whole files
correctly is a valid transport.

Each line is one event:

```json
{"op":"set","habit":"tahajjud","date":20340,"value":2,"hlc":"2026-09-09T05:12:31.442Z-0-3f9a"}
{"op":"clear","habit":"adkarAlSabah","date":20340,"hlc":"2026-09-09T05:13:02.008Z-1-3f9a"}
{"op":"habit","id":"quranMorning","fields":{"label":"Read 400+ verses"},"hlc":"…"}
```

## Merging

Read every log, order by hybrid logical clock, apply in order. Per
`(date, habit_id)` the last write wins; habit definition fields merge per field.
Clears are tombstones, not row deletions, so a delete cannot be resurrected by a
device that was offline.

The HLC (wall clock + counter + device ID) matters because phone and laptop
clocks drift. Ordering by raw wall-clock time would let a device with a fast
clock win every conflict permanently.

Merging is deterministic: every device applying the same set of logs reaches
byte-identical state, regardless of arrival order.

## Transports this admits

Anything that moves files: **Syncthing** (recommended — peer-to-peer, no server,
no account, no quota), Nextcloud, Dropbox, Google Drive, rclone to any storage,
git, a USB stick, or an email attachment. The app has no opinion and no network
code; it reads and writes a folder.

## Size

The existing 8-month history is 3,187 entries — a few hundred KB as JSONL, and
roughly 15k events per year of use. Compaction folds logs older than the last
snapshot into `snapshot-*.json` so the working set stays small on a phone.

## Ordering note

An event log makes the M8 dashboard cheaper, not harder: "when did this habit
actually get logged" is answerable from the log, while the SQLite view stays a
fast materialised projection for the day screen.

## As built

The engine lives in `shared/src/commonMain/kotlin/dev/adambench/habbits/sync/`:
`SyncEvent` (the four event types), `BufferedSyncJournal` (records writes,
buffers them, compacts), and `SyncMerger` (folds every log into the database).

Two store implementations back it. Desktop uses `FileSyncStore` over a plain
directory. Android uses `SafSyncStore`, which goes through the Storage Access
Framework: the user grants access to exactly one folder tree, so reaching a
Syncthing or Nextcloud folder still needs **no storage permission**.

Writes are buffered rather than hitting the disk per tap, and flushed when the
app pauses, when the window closes, and before every merge.

`SyncConvergenceTest` covers the claims this document makes: two devices reach
identical state from independent edits; the later write wins a genuine conflict;
a cleared entry is not resurrected by a device that was offline when it was
cleared; merging twice changes nothing; a corrupt line is counted and skipped
rather than aborting the merge; and compaction shrinks a log without changing
what a fresh device computes from it.
