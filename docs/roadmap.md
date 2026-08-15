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

- ~~Reprint from the log (paper jams).~~ **Done.**
- ~~Bell schedule table, so the current period is preselected and the receipt can
  show minutes late.~~ **Done** — admin enters times on the dashboard; the app
  reads them read-only. Optional by construction: an unconfigured schedule leaves
  preselection and the minutes-late line exactly as they were.
- Repeat-tardy threshold alert (the per-student counter already exists).
- **Kiosk / screen-pinning mode for shared hallway devices.** *Correction to an
  earlier note in this file:* this is **not** protecting the Workspace account.
  With the printer's OU stripped of Gmail and Drive per `docs/device-accounts.md`,
  the account holds nothing, so leaving the app reveals nothing. What kiosk
  actually buys is device-tampering hygiene — mainly stopping someone changing
  the system clock, which would falsify pass timestamps since `localCreatedAt` is
  the device's own clock. The planned clock-skew warning covers that more
  directly. De-prioritised accordingly.
- Sideload update channel: GitHub Releases + an in-app version check.

## Phase 4 — SIS integration

**Next up: inbound roster sync only** (PowerSchool → TardyPass). Chris can install
plugins himself, which removes the district-approval lead time. See the approved
plan for the plugin XML, the "never filter the fetch to active students" rule,
and the guard set.

Requirement for tardies: morning ones must reach PowerSchool by the end of first
period — currently met by the clerk working from the live dashboard.

Sequenced by risk, lowest first:

1. **Live dashboard** (done) — the clerk works from an always-current list.
2. **Generated import file** — produce a PowerSchool-shaped attendance file that
   a human reviews and imports. Keeps a person between the app and official
   attendance records.
3. **Direct API write — NOT PLANNED.** Explicitly out of scope for the
   foreseeable future by the school's decision. A bug here corrupts records that
   feed state reporting, and the clerk working from the live dashboard is
   sufficient today. Kept here only so the reasoning isn't relitigated: if it is
   ever revisited it needs district approval, write permissions, and a
   `postedToSis` marker so repeated runs can't double-post.

**Roster lifecycle** — students are never deleted; culling marks them
`active: false` (enforced by rule, not convention, since passes reference
`studentId`). The dashboard's "complete roster" upload deactivates anyone absent
from the file, guarded at 40% of the active roster because that is what a partial
export looks like. Once PowerSchool sync lands, enrollment status drives `active`
automatically and the manual cull becomes an emergency tool rather than routine.

Cost is not the constraint: Cloud Scheduler bills per job (~$0.10/month), not
per run, and the compute fits inside the free allowances many times over. Blaze
billing must be enabled regardless, with a budget alert as a guardrail.
