#!/usr/bin/env node
/**
 * One-time importer: Obsidian vault -> habits-export.json
 *
 * Reads the habit definitions from the `habit_config` frontmatter of the
 * tracker note, and the logged completions from each daily note's `habits`
 * frontmatter. Emits the same JSON schema the app's own backup export
 * produces, so import and restore are one code path in the app.
 *
 * Usage:  node index.mjs <vault-path> [--out habits-export.json] [--note "Habit logger.md"]
 */

import { readFileSync, writeFileSync, existsSync, readdirSync, statSync } from 'node:fs';
import { join, basename } from 'node:path';
import yaml from 'js-yaml';

const DATE_FILE = /^(\d{4})-(\d{2})-(\d{2})\.md$/;
const TIMESTAMP_ID = /^habit_(\d{10,})$/;

function fail(message) {
  console.error(`error: ${message}`);
  process.exit(1);
}

function parseArgs(argv) {
  const args = { vault: null, out: 'habits-export.json', note: 'Habit logger.md' };
  const rest = [];
  for (let i = 0; i < argv.length; i++) {
    if (argv[i] === '--out') args.out = argv[++i];
    else if (argv[i] === '--note') args.note = argv[++i];
    else rest.push(argv[i]);
  }
  args.vault = rest[0] ?? null;
  return args;
}

/** Splits `---\n…\n---` off the top of a note. Returns null when absent. */
function frontmatter(text) {
  if (!text.startsWith('---')) return null;
  const end = text.indexOf('\n---', 3);
  if (end === -1) return null;
  const raw = text.slice(text.indexOf('\n') + 1, end);
  try {
    return yaml.load(raw) ?? {};
  } catch (e) {
    return { __parseError: e.message };
  }
}

/** `duaYunusMorning` -> `Dua Yunus Morning`; `habit_1774523709153` -> dated label. */
function labelFromId(id) {
  const stamp = TIMESTAMP_ID.exec(id);
  if (stamp) {
    const date = new Date(Number(stamp[1]));
    const day = Number.isNaN(date.getTime()) ? id : date.toISOString().slice(0, 10);
    return `Untitled habit (${day})`;
  }
  const spaced = id
    .replace(/[_-]+/g, ' ')
    .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
    .replace(/\s+/g, ' ')
    .trim();
  return spaced.charAt(0).toUpperCase() + spaced.slice(1);
}

function walkDailyNotes(root) {
  const found = [];
  const skipped = [];
  const walk = (dir) => {
    let items;
    try {
      items = readdirSync(dir, { withFileTypes: true });
    } catch {
      return;
    }
    for (const item of items) {
      const full = join(dir, item.name);
      if (item.isDirectory()) walk(full);
      else if (item.name.endsWith('.md')) {
        if (DATE_FILE.test(item.name)) found.push(full);
        else skipped.push(full);
      }
    }
  };
  walk(root);
  found.sort();
  return { found, skipped };
}

function normaliseHabit(raw, sortOrder) {
  const id = raw.id;
  if (!id) return null;
  const recurring = Array.isArray(raw.recurringDays)
    ? raw.recurringDays.filter((d) => Number.isInteger(d) && d >= 1 && d <= 7)
    : null;
  const num = (v) => (typeof v === 'number' && Number.isFinite(v) ? v : null);
  const str = (v) => (typeof v === 'string' && v.trim() !== '' ? v.trim() : null);

  return {
    id: String(id),
    label: str(raw.label) ?? labelFromId(String(id)),
    description: str(raw.description),
    category: str(raw.category) ?? 'Anytime',
    type: str(raw.type) ?? 'other',
    unit: str(raw.unit),
    // The vault calls this `defaultDuration`.
    defaultValue: num(raw.defaultDuration),
    step: num(raw.step) ?? 25,
    frequencyType: str(raw.frequencyType) ?? 'daily',
    recurringDays: recurring,
    intervalDays: num(raw.intervalDays),
    intervalStart: str(raw.intervalStart),
    status: str(raw.status) ?? 'active',
    sortOrder,
  };
}

function main() {
  const args = parseArgs(process.argv.slice(2));
  if (!args.vault) {
    fail('usage: node index.mjs <vault-path> [--out FILE] [--note "Habit logger.md"]');
  }
  if (!existsSync(args.vault) || !statSync(args.vault).isDirectory()) {
    fail(`vault path is not a directory: ${args.vault}`);
  }

  const notePath = join(args.vault, args.note);
  if (!existsSync(notePath)) {
    fail(
      `tracker note not found: ${notePath}\n` +
        `       This vault may be the wrong copy — the real one contains "${args.note}".`,
    );
  }

  const config = frontmatter(readFileSync(notePath, 'utf8'));
  if (!config) fail(`no frontmatter in ${notePath}`);
  if (config.__parseError) fail(`could not parse ${notePath}: ${config.__parseError}`);
  if (!Array.isArray(config.habit_config)) fail(`no habit_config array in ${notePath}`);

  const dailyRoot = join(args.vault, config.daily_notes_path ?? 'Daily Notes');
  if (!existsSync(dailyRoot)) fail(`daily notes folder not found: ${dailyRoot}`);

  // --- habit definitions -------------------------------------------------
  const habits = new Map();
  config.habit_config.forEach((raw, i) => {
    const habit = normaliseHabit(raw, i);
    if (!habit) return;
    if (habits.has(habit.id)) {
      console.warn(`warning: duplicate habit id in config, keeping the first: ${habit.id}`);
      return;
    }
    habits.set(habit.id, habit);
  });
  const configuredCount = habits.size;

  // --- logged entries ----------------------------------------------------
  const { found, skipped } = walkDailyNotes(dailyRoot);
  const entries = [];
  const extras = { pomodoros: {}, timeblocks: {} };
  const perHabit = new Map();
  const days = new Set();
  const problems = [];

  for (const file of found) {
    const name = basename(file);
    const date = name.slice(0, 10);
    const fm = frontmatter(readFileSync(file, 'utf8'));
    if (!fm) continue;
    if (fm.__parseError) {
      problems.push(`${name}: ${fm.__parseError}`);
      continue;
    }
    if (fm.pomodoros !== undefined) extras.pomodoros[date] = fm.pomodoros;
    if (fm.timeblocks !== undefined) extras.timeblocks[date] = fm.timeblocks;

    const logged = fm.habits;
    if (!logged || typeof logged !== 'object' || Array.isArray(logged)) continue;

    for (const [habitId, value] of Object.entries(logged)) {
      let stored;
      if (value === true) stored = null; // plain check
      else if (typeof value === 'number' && Number.isFinite(value)) stored = value;
      else if (value === false || value === null || value === undefined) continue; // not completed
      else {
        problems.push(`${name}: ${habitId} has unsupported value ${JSON.stringify(value)}`);
        continue;
      }
      entries.push({ date, habitId, value: stored });
      days.add(date);
      perHabit.set(habitId, (perHabit.get(habitId) ?? 0) + 1);
    }
  }

  // --- orphans: logged, but no longer in the config -----------------------
  const orphans = [];
  for (const habitId of perHabit.keys()) {
    if (habits.has(habitId)) continue;
    const habit = {
      id: habitId,
      label: labelFromId(habitId),
      description: null,
      category: 'Anytime',
      type: 'other',
      unit: null,
      defaultValue: null,
      step: 25,
      frequencyType: 'daily',
      recurringDays: null,
      intervalDays: null,
      intervalStart: null,
      // Archived in bulk: history is preserved, and archived habits never
      // appear in the day view, so they cost nothing until reviewed.
      status: 'archived',
      sortOrder: habits.size + orphans.length,
    };
    habits.set(habitId, habit);
    orphans.push(habit);
  }

  const neverLogged = [...habits.values()].filter(
    (h) => h.status !== 'archived' && !perHabit.has(h.id),
  );

  entries.sort((a, b) => (a.date === b.date ? a.habitId.localeCompare(b.habitId) : a.date.localeCompare(b.date)));

  const payload = {
    format: 'habbits-export',
    version: 1,
    exportedAt: new Date().toISOString(),
    source: {
      kind: 'obsidian-vault',
      vault: args.vault,
      trackerNote: args.note,
      dailyNotesPath: config.daily_notes_path ?? 'Daily Notes',
    },
    stats: { habits: habits.size, entries: entries.length, days: days.size },
    habits: [...habits.values()],
    entries,
    extras,
  };

  writeFileSync(args.out, JSON.stringify(payload, null, 2) + '\n', 'utf8');

  // --- reconciliation report ---------------------------------------------
  const dates = [...days].sort();
  const bool = entries.filter((e) => e.value === null).length;
  console.log(`
Habbits import — reconciliation
───────────────────────────────────────────────
  vault                ${args.vault}
  daily notes          ${payload.source.dailyNotesPath}

  notes scanned        ${found.length}
  notes skipped        ${skipped.length}${skipped.length ? '  (' + skipped.map((f) => basename(f)).join(', ') + ')' : ''}
  days with entries    ${days.size}
  date range           ${dates[0] ?? '—'} → ${dates[dates.length - 1] ?? '—'}

  habits configured    ${configuredCount}
  orphans archived     ${orphans.length}
  never logged         ${neverLogged.length}
  habits total         ${habits.size}

  entries              ${entries.length}
    plain checks       ${bool}
    measured           ${entries.length - bool}

  parse problems       ${problems.length}
${problems.map((p) => '    ! ' + p).join('\n')}
  written to           ${args.out}
`);

  if (orphans.length) {
    console.log('  Archived orphans (reviewable in the app):');
    for (const o of orphans) {
      console.log(`    ${String(perHabit.get(o.id)).padStart(4)} entries  ${o.id}  →  "${o.label}"`);
    }
    console.log();
  }

  if (problems.length) process.exitCode = 2;
}

main();
