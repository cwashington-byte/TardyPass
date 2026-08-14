# TardyPass Android

Native Android rewrite of the [Tardy-Pass-PWA](https://github.com/cwashington-byte/Tardy-Pass-PWA):
search a student, print a personalized tardy pass on a handheld Android receipt-printer
PDA (58mm thermal, ESC/POS), and keep a record of every pass.

Targets low-spec Android 13/14 devices (generic "H10" PDA class). Kotlin + Jetpack
Compose, Room (FTS roster search), DataStore settings.

## Printing

Pluggable driver chain, auto-detected in order:

1. **Vendor SDK** (`printing/drivers/VendorSdkDriver.kt`) — stub until Phase 0
   hardware discovery identifies the H10's interface (see `docs/hardware-h10.md`)
2. **Serial** — raw ESC/POS to a writable `/dev/tty*` node (typical for generic PDAs)
3. **Bluetooth SPP** — external ESC/POS printers
4. **RAWBT app** — same URL-scheme hand-off the PWA used; guaranteed fallback

The ESC/POS byte builder (`printing/EscPos.kt`) is a byte-compatible port of the
PWA's `escpos.ts`; `EscPosTest` locks the parity with fixtures generated from the
PWA's actual output.

## Roster import

Same CSV contract as the PWA (`Student_Number, First_Name, Last_Name, Grade,
Schedule` with the Schedule cell holding a JSON object). Import is a
non-destructive merge by student number with per-row error reporting; a
"full replace" checkbox deactivates students missing from the file. CSVs can
also be shared to the app (ACTION_SEND) from Files/Drive.

## Roadmap

- **Phase 2**: Firebase Auth (Google Sign-In, school Workspace) + Firestore sync of
  passes and roster across devices (offline-first).
- **Phase 3**: repeat-tardy alerts, reprint, admin tools, kiosk mode.
- **Phase 4**: PowerSchool roster sync via a scheduled Cloud Function.

## Building

JDK 17 + Android SDK 34. `gradlew :app:assembleRelease` (release currently signs
with the debug key for sideloading; create a proper keystore before wide rollout
— the signing key is the app's permanent identity on sideloaded devices).

## Tests

`gradlew :app:testDebugUnitTest` — ESC/POS byte-parity, CSV parse/export contracts.
