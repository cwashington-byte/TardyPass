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
- That the named author was a provisioned member of this school at the moment of
  the write.
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
- The selection clears after a period of inactivity (default 5 minutes,
  configurable in Settings → Staff selection, or Off). Each print extends the
  window, so a staff member working a rush is never interrupted.
- Expiry is computed from a stored timestamp, not a running timer, so it survives
  the app being killed and reboots.
- If the staff list can't be loaded, the picker says so and falls back to typed
  initials — the pass is then recorded on the device only.

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

Why the split: attribution from a shared printer is claimed rather than proven no
matter what, so requiring a Google account per teacher would have bought nothing
while adding an account and a sign-in step per person.

## Roles

| Role | Roster | Passes | Manage staff | Dashboard |
|---|---|---|---|---|
| `admin` | read + import | own only, proven | yes | yes, with upload |
| `staff` | read | own only, proven | no | yes |
| `device` | read | anyone on the staff list, claimed | no | blocked |

A device can never be an admin — role is a single string, and member documents are
admin-writable only, so a printer can't promote itself.

The two attribution fields on a pass are mutually exclusive, and the rules enforce
it: `staffUid` means an account proved the author's identity; `staffId` means a
name was picked off the staff list. A device pass may never carry `staffUid`.
