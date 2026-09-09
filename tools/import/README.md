# Vault importer

One-time migration: reads the Obsidian vault and writes `habits-export.json`,
the same format the app's own backup export produces — so importing a vault and
restoring a backup are one code path in the app.

```sh
cd tools/import
npm install
node index.mjs ~/Obsidian/LifeOS --out habits-export.json
```

The vault path is a required argument and is verified, deliberately. Obsidian's
own `obsidian.json` on this machine names a vault path that no longer exists,
and a second, near-empty copy of the vault sits inside a Syncthing folder —
auto-detection would have picked the wrong one.

## What it does

- Reads habit definitions from the `habit_config` frontmatter of `Habit logger.md`.
- Reads completions from each daily note's `habits` frontmatter.
- Skips any file not named `yyyy-MM-dd.md`, which excludes `Daily Note Template.md`.
- Maps `true` to a null value (a plain check) and numbers to themselves.
- Skips `false`, which means the habit was explicitly *not* done.
- Creates any habit that has history but no config entry as `archived`, so the
  history stays attached to something real.
- Preserves `pomodoros` and `timeblocks` into `extras`, unused for now.

## Reconciliation

Every run prints a report. The migration is only accepted when it reports
**3,178 completions across 239 days for 60 habits**, and the app reports the
same totals after importing the file.
