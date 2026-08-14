# Roadmap

Phases 0–2 are done (see `hardware-h10.md` and the git history). What follows is
the remaining work, roughly in priority order.

## Phase 3 — polish

### Roster import: accept more export formats

The immediate driver: the current custom SQL export from PowerSchool exists
mainly to produce the `Schedule` JSON column, and schedules are a *nice to have*
— period/course/room are optional on the pass. A plain PowerSchool student
export should work with no special query.

Already done:

- Header matching ignores case and punctuation (`Student_Number`, `student
  number`, `StudentNumber` are equivalent) in both the web uploader
  (`public/index.html`) and the Android parser (`data/csv/RosterCsv.kt`).
- Aliases cover common PowerSchool spellings: `Student_Number`/`Student ID`/
  `Local_ID`, `First_Name`/`FName`, `Last_Name`/`LName`, `Grade`/`Grade_Level`.
- Comma and tab delimited, quoted fields with embedded commas.
- `Schedule` column optional; a malformed one is reported per-row and the
  student still imports.

Still to do:

- **Excel (.xlsx) upload** — PowerSchool exports often land as a spreadsheet and
  staff currently have to Save-As CSV first.
- **Column mapping UI** — when headers aren't recognised, let an admin pick
  which column is which instead of failing the import outright.
- **Per-period columns** (`P1_course`, `P1_room`, …) as an alternative to the
  `Schedule` JSON blob. The old PWA advertised this format but never
  implemented it.
- **Keep the two parsers in sync.** The alias lists are duplicated between the
  Kotlin and JS implementations; a shared fixture set of sample exports that both
  are tested against would stop them drifting.

### Other Phase 3 items

- Reprint from the log (paper jams).
- Bell schedule table, so the current period is preselected and the receipt can
  show minutes late.
- Repeat-tardy threshold alert (the per-student counter already exists).
- Kiosk / screen-pinning mode for shared hallway devices.
- Sideload update channel: GitHub Releases + an in-app version check.

## Phase 4 — SIS integration

Requirement: morning tardies must reach PowerSchool by the end of first period.

Sequenced by risk, lowest first:

1. **Live dashboard** (done) — the clerk works from an always-current list.
2. **Generated import file** — produce a PowerSchool-shaped attendance file that
   a human reviews and imports. Keeps a person between the app and official
   attendance records.
3. **Direct API write** — a scheduled Cloud Function posts attendance via a
   PowerSchool plugin. Needs district approval and write permissions; a bug here
   corrupts records that feed state reporting, so it should only follow a term
   of clean operation. Each pass needs a `postedToSis` marker so repeated runs
   can't double-post.

Cost is not the constraint: Cloud Scheduler bills per job (~$0.10/month), not
per run, and the compute fits inside the free allowances many times over. Blaze
billing must be enabled regardless, with a budget alert as a guardrail.
