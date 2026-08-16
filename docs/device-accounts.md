# Shared printer accounts

## Why they exist

Google Sign-In on Android reads **device-level** accounts. If staff signed in with
their own Workspace accounts, each printer would be holding a personal identity:
whoever picked the device up next could open Gmail or Drive as that person, and
every handoff would mean removing the account in Android settings.

So each printer gets its own Workspace account instead — signed in once, never
signed out, holding nothing. Staff say who they are **inside the app** by tapping
their name.

## What a pass record proves — and what it doesn't

Read this before telling staff their name is on a pass.

**Guaranteed, cryptographically and by security rules:**

- Which physical printer created the pass (`deviceUid` must equal the caller's own
  uid — a printer cannot claim to be a different printer).
- For a picked name, that the author was on this school's staff list at the moment
  of the write. A **typed** name proves nothing beyond having been typed, which is
  why it's marked as typed everywhere it appears.
- That the record has not been edited since (`allow update: if false`).
- A server-side upper bound on when it arrived (`createdAt`).

**Not guaranteed:**

- That the named person was present, consented, or even knew. Anyone at an
  unlocked printer can pick any name from the list, and the resulting record is
  **indistinguishable** from a genuine one.

That symmetry cuts both ways, and it is the important part: the record can neither
convict a staff member who denies issuing a pass, nor exonerate one who did.

> **Pass attribution on a shared printer is an operational convenience and an
> investigative lead. It is not evidence in a personnel matter.**
> Tell staff this in writing before rollout.

Two smaller caveats. `localCreatedAt` — the time the app and dashboard display —
is the device's own clock, which anyone with device settings can change; only
`createdAt` is server-controlled. And because passes are deliberately immutable, a
*misattributed* pass can only be deleted by an admin, not corrected.

A personal account keeps the stronger old guarantee: it can only ever attribute a
pass to itself. The rules enforce both branches separately.

## What the printer's credentials are worth if stolen

Someone with a printer's password gains, until revoked: read of the student roster
(names, grades, schedules), read of every pass recorded for the school, read of the
staff member list, and the ability to create passes attributed to any staff member
from anywhere.

They do **not** gain: admin rights, roster writes, pass deletion, role changes, or —
because of the stripped OU below — a mailbox, Drive, or anything else in Workspace.

Net versus signing staff in personally: the blast radius shifts from *one person's
entire Workspace identity* to *one printer's school-scoped read access*. That's the
trade, and it's a good one.

## Setting up a printer

Do all of this before touching the device. **Order matters at step 6.**

1. **Create an OU** — Admin console → Directory → Organizational units →
   *Devices / Tardy Pass Printers*.
2. **Turn every service OFF for that OU** — Apps → Google Workspace, with the OU
   selected, set Gmail, Drive & Docs, Calendar, Chat, Meet, Groups, Sites, Keep,
   Tasks, Photos to **OFF**, and set additional Google services off by default.
   Sign-in itself needs no service, so the account ends up holding nothing. This
   is what makes the credentials boring to steal. (Gmail off still leaves a valid
   address, which is all the app's domain check and invite lookup need.)
3. **Create the user, named for its physical location** — "Pass Printer 01 (Main
   Hall)". The display name becomes `deviceName` on every pass and is what the
   attendance clerk reads on the dashboard. Address `passprinter01@…`, in the
   devices OU, "change password at next sign-in" **unchecked**.
4. **Password** — long, random, unique per printer, stored in the school password
   manager. Staff never receive it and never need it.
5. **2SV** — exclude this OU if it's enforced org-wide; a kiosk can't complete a
   second factor. Set Google Session control to "never expires" for the OU so
   Android stops re-prompting.
6. **Grant access in the app first, then sign in.** As an admin:
   Settings → Administration → Manage staff → enter the address → choose
   **Device** → Grant access. *Then* on the printer: remove every personal account
   from Android settings, add only `passprinter01@`, open TardyPass → Sign in.
   Reversed, no member doc is created and nothing works until you add the grant
   and tap **Retry sync**. This is the most common setup mistake.
7. **One account per printer, never shared.** `deviceUid` can't tell two printers
   apart if they share an account, and one compromise would cost you all of them.

## Setting up a brand-new printer

Do the steps in this order. Step 1 before step 4 especially — reversed, the
printer signs in, finds no member document, and sits showing sync errors.

### 1. Grant the printer access, from a device that's already an admin

Manage staff lives in the app, not the dashboard, so you need TardyPass open
somewhere you're signed in as an admin — an existing printer, or the app on your
own phone. Settings → Administration → Manage staff → enter the new printer's
address (`passprinter02@…`), choose **Device**, Grant access.

*(If this is the very first device and there's nowhere to do that from, create the
member document by hand in the Firebase console — see the bootstrap note at the
top of this file.)*

### 2. Create the Workspace account

Per "Setting up a printer" above: its own account, in the stripped OU, named for
its physical location.

### 3. Install the app

Two routes, depending on what the laptop has.

#### Route A — the laptop has `adb`

On the printer: Settings → About phone → tap **Build number** seven times → back →
System → Developer options → enable **USB debugging**. Connect USB and accept the
"Allow USB debugging?" prompt on the device.

```bash
adb install -r app-release.apk
```

`-r` replaces in place, keeping sign-in and local pass history. Use it for updates
too — there's no need to uninstall first, and uninstalling would discard any
passes that hadn't synced yet.

#### Route B — the laptop has only the APK, no tooling

No adb, nothing to install on the laptop. The H10 ships with **Files by Google**,
which is all you need.

1. Connect the printer by USB.
2. On the **printer**, swipe down and tap the *"Charging this device via USB"*
   notification → choose **File Transfer**. (Windows won't show the device's
   storage until you do this — if the device appears with no folders inside, this
   is the step that was missed.)
3. On the laptop, open the device in Explorer → *Internal shared storage* →
   **Download**, and copy `app-release.apk` in.
4. On the printer, open **Files** → Downloads → tap `app-release.apk`.
5. Android will say Files isn't allowed to install unknown apps. Tap **Settings**,
   turn on *Allow from this source*, then press back. This is per-app and one-time.
6. Tap **Install**, then **Open**.

Any transfer method works for step 3 — a USB stick, or a network share. Note that
**Gmail and Drive won't work for this on a configured printer**: the runbook turns
those services off for the device OU, which is the whole point of that OU.

### 4. Sign the printer in

Open TardyPass → Settings → Sign in with Google → pick the printer's account. It
claims the grant from step 1 and provisions itself as a device. The account card
should then read *"This is a shared printer"* and the roster sync dot should go
green.

### 5. Check it actually prints

Settings → Printer → the **Built-in printer (SENRAISE)** row should say
*available* → tap **Test**. Paper should come out. If the row says *not found*,
the vendor print service is missing or disabled on that unit — see the AIDL
section above.

Then on the Pass screen: the red *"Tap to choose your name"* bar should be
showing, which confirms the device role took effect.

## Updating an existing printer

Same command, or the same Files procedure:

```bash
adb install -r app-release.apk
```

Three things that will bite if ignored:

- **Always install the RELEASE build, never debug.** Measured on an H10: 4,780 ms
  cold start for debug versus 635 ms for release. A debug build feels broken on
  this hardware.
- **Always sign with the same keystore** (`C:\Users\Chris\TardyPass\secrets\`).
  Android refuses to replace an app with a differently-signed one, so a lost or
  changed key means uninstalling every printer — which also discards unsynced
  passes. Back that folder up. If an install fails with a signature mismatch,
  check the key before reaching for `adb uninstall`.
- **Bump `versionCode`** in `app/build.gradle.kts` for each release you actually
  distribute. `adb install -r` doesn't care, but without it there's no way to tell
  which build a printer is running, and the automated update channel below has
  nothing to compare against.

To check what a printer is running without a laptop, scroll to the bottom of
**Settings** — the version and build number are printed there. That's the quick way
to answer "did that one actually get updated?" while standing in the hallway. From
a laptop, `adb shell dumpsys package com.iar.tardypass | grep version` says the
same thing.

**The automated path, when it's wanted:** the APK goes to Cloud Storage rather
than Hosting — Hosting serves everything publicly, whereas a Storage rule can
require a `members/{uid}` document, so only a provisioned device can download it.
The app fetches it through the authenticated SDK and hands it to Android's package
installer, with the version advertised in `/schools/{id}/config/appVersion`. This
needs the **Blaze** plan, because projects created after October 2024 can't
provision a Storage bucket on Spark. Deferred until Blaze is enabled for the
PowerSchool sync.

## If a printer is lost or stolen

**Settings → Administration → Manage staff → Remove** that device.

The rules check the member doc on every read and write, so access dies within
seconds. That's faster and more complete than suspending the Workspace account,
which leaves an already-issued ID token valid for up to an hour. Do both — Remove
first, then suspend.

Local printing on the device keeps working (by design, printing never depends on
the network), but it can no longer read the roster or sync anything.

## How it behaves day to day

- The Pass screen shows a bar at the top: **"Tap to choose your name"** in red
  when nobody is selected, or the current person's initials and name.
- Pressing PRINT with nobody selected opens the picker and **resumes the print
  automatically** once a name is chosen. One extra tap, never a dead end — and
  never a pass silently attributed to the printer.
- The selection clears after a period of inactivity — default 10 minutes, with
  30 minutes, 1 hour, 3 hours or Off available under Settings → Staff selection.
  Each print extends the window, so a staff member working a rush is never
  interrupted. A printer still set to one of the old 2- or 5-minute windows is
  moved up to 10 on first launch, never down to Off: a window that is longer
  than intended is a smaller problem than one that never clears.
- Expiry is computed from a stored timestamp, not a running timer, so it survives
  the app being killed and reboots.
- Anyone not on the staff list types their name in the same field they'd filter
  with, and an option appears to use it as typed. Subs need no setup.
- If the staff list can't be loaded at all, the picker says so and the typed-name
  path still works, so a printer is never a dead end.

## Two separate lists

This trips people up, so it's worth stating plainly.

**Staff list** (`/schools/{id}/staff`) — everyone who can be *credited* on a pass.
No Google account, no sign-in, no licence. An admin types the names under
Settings → Administration → Staff list, and they appear in the picker on every
printer within seconds. This is where almost all your teachers belong.

**Accounts** (`/schools/{id}/members`) — only people who actually *sign in*:
admins, whoever uses the attendance dashboard, and the printers themselves. Each
needs a real Workspace account.

A teacher who only ever issues passes on a hallway printer needs **no account at
all**. Add them to the staff list and you're done.

### Someone who isn't on either list

A substitute, or a teacher who has never issued a pass before, can type their name
straight into the picker and use it. Nothing is blocked and no admin is needed.

Such a pass records the name but **no staff id**, and is shown as *"Dana
Substitute (typed)"* everywhere — the app, the dashboard, and exports. That mark
is the point of the design. A picked name is a stable identity that reports can
group by; a typed one is just a string, and might be a misspelling of someone real.
Keeping them visibly separate means a typo can never quietly merge into a real
person's totals.

Typed names are deliberately **not** added to the staff list. Someone covering a
class for a day shouldn't permanently join the picker on every printer, and if
printers could append to that list, every typo would be permanent. If a typed name
keeps appearing on the dashboard, that's the signal for an admin to add them
properly under Settings → Administration → Staff list.

Why the split: attribution from a shared printer is claimed rather than proven no
matter what, so requiring a Google account per teacher would have bought nothing
while adding an account and a sign-in step per person.

## Roles

| Role | Roster | Passes | Manage staff | Dashboard |
|---|---|---|---|---|
| `admin` | read + import | own only, proven | yes | yes, with upload |
| `staff` | read | own only, proven | no | yes |
| `device` | read | anyone on the staff list, or a typed name; claimed | no | blocked |

A device can never be an admin — role is a single string, and member documents are
admin-writable only, so a printer can't promote itself.

The attribution fields on a pass are mutually exclusive, and the rules enforce it:
`staffUid` means an account proved the author's identity; `staffId` means a name
was picked off the staff list. A device pass may never carry `staffUid`. A device
pass with `staffName` but no `staffId` was typed by hand — allowed, and marked as
such wherever it's displayed.
