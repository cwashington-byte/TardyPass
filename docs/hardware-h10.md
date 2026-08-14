# H10 Hardware Discovery (Phase 0) — COMPLETE

Discovery run 2026-08-13 against a live device over adb.

## Device identity

| | |
|---|---|
| Marketing model | H10 |
| **Real ODM** | **SENRAISE** (`ro.product.manufacturer=SENRAISE`) |
| Android | **14** (SDK 34), Go edition |
| Fingerprint | `SENRAISE/H10/H10:14/UP1A.231005.007/1720438117:user/release-keys` |
| ABI | **armeabi-v7a** (32-bit ARM) |
| RAM | 2.9 GB (`MemTotal: 2924056 kB`) |
| SELinux | **Enforcing** |
| Google Play Services | **Present** (`com.google.android.gms`, `com.google.android.gsf`) |

Implications: `minSdk 26 / target 34` is correct. GMS present ⇒ the Phase 2
Google Sign-In plan works as designed (no PIN fallback needed). Android Go +
2.9 GB RAM confirms keeping the UI lean was the right call.

## Printer interface — SOLVED

The built-in 58mm printer is owned by the preinstalled **SRPrinter** app:

- Package `recieptservice.com.recieptservice` (note the vendor's misspelling), v8.3.6
- Path `/system/priv-app/SRPrinter/SRPrinter.apk`, `sharedUser=android.uid.system` (uid 1000), `PRIVILEGED`

### Direct serial access is NOT possible (rejected path)

`/dev/ttyS1` is the printer port — mode `crw-rw-rw-` (world-writable) and its
mtime tracks print activity, unlike every other tty which shows boot time.
**But SELinux blocks it:**

```
$ adb shell 'printf "..." > /dev/ttyS1'
/system/bin/sh: can't create /dev/ttyS1: Permission denied

$ adb shell ls -Z /dev/ttyS1
u:object_r:talkie_ttyS_device:s0 /dev/ttyS1
```

Only the system-uid vendor app may open that context. `SerialEscPosDriver`
therefore stays in the chain for other hardware but will correctly report
unavailable here (its probe does a real `open()`, since `canWrite()` returns
true and would lie).

### The working path: exported AIDL service

`SRPrinter` exports a **permission-free, bindable** service:

```xml
<service android:name="recieptservice.com.recieptservice.service.PrinterService"
         android:exported="true" />   <!-- no android:permission -->
```

| Item | Value |
|---|---|
| Package | `recieptservice.com.recieptservice` |
| Component | `recieptservice.com.recieptservice.service.PrinterService` |
| Binder descriptor | `recieptservice.com.recieptservice.PrinterInterface` |

Transaction codes read from `PrinterInterface$Stub` static fields:

| Code | Method | Code | Method |
|---|---|---|---|
| 1 | **`printEpson(byte[])`** ← raw ESC/POS | 14 | `setDark(int)` |
| 2 | `getServiceVersion() → String` | 15 | `setLineHeight(float)` |
| 3 | `printText(String)` | 16 | `setTextDoubleWidth(boolean)` |
| 4 | `printBitmap(Bitmap)` | 17 | `setTextDoubleHeight(boolean)` |
| 5 | `printBarCode(String,int,int,int)` | 18 | `printPDF417Code(String,int,int)` |
| 6 | `printQRCode(String,int,int)` | 19 | `setCode(String)` |
| 7 | `setAlignment(int)` | 20 | `print128BarCode(String,int,int,int)` |
| 8 | `setTextSize(float)` | 21 | `getScannerStatus() → boolean` |
| 9 | `nextLine(int)` | 22–25 | PSAM smartcard methods (unused) |
| 10 | `printTableText(String[],int[],int[])` | | |
| 11 | `setTextBold(boolean)` | | |
| 12 | `beginWork()` | | |
| 13 | `endWork()` | | |

**`printEpson(byte[])` is the key**: it takes raw ESC/POS bytes, so the receipt
our `EscPos.kt` builds — byte-identical to the PWA's — goes straight through
unchanged. Disassembly of `PrinterService$2.printEpson` shows it logs the bytes
and posts a Runnable to the service's render `Handler`; **no `beginWork()` /
`endWork()` framing is required** for this path (those wrap the higher-level
text-composition API).

Implemented in `printing/drivers/VendorSdkDriver.kt` via a raw binder
`transact()` — no vendor jar needed, so there's nothing to bundle or license.

### Package visibility

Android 11+ hides other packages. `AndroidManifest.xml` needs (and now has) a
`<queries>` block naming `recieptservice.com.recieptservice` and the `rawbt`
scheme, or `resolveService`/`bindService` and the RAWBT probe silently fail on
targetSdk 34.

## Other packages of note

- `com.posmgt.base` (`/system/priv-app/SRService`) — SENRAISE device services
- `com.dawn.java` (DLScan) / `com.android.qr_codescan` — barcode scanner apps;
  relevant later if scanning student IDs is ever wanted
- `recieptservice.com.recieptservice/.service.PrinterPlugin` also registers a
  standard `android.printservice.PrintService`, i.e. the device can print via
  the Android print framework — but that routes through the PrintSpooler dialog,
  so it's unsuitable for one-tap passes. Not used.

## Acceptance — VERIFIED 2026-08-13 on device

Driver auto-detection reports exactly the expected picture:

| Driver | Result |
|---|---|
| Built-in printer (SENRAISE) | **available** ← auto-selected |
| Internal printer (serial) | not found (SELinux, correctly detected) |
| Bluetooth printer | not found (none paired) |
| RAWBT app | not found (not installed) |

Full workflow exercised on the H10, no RAWBT anywhere:

1. Roster CSV shared into the app (ACTION_SEND) → import preview → 3 students
2. Typed `garc` → matched "Garcia, Maria" (FTS prefix search)
3. Selected student, reason **Late**, initials **CW** → PRINT UNEXCUSED
4. Vendor service log confirms the bytes we built arrived at the printer:

```
printEpson -> 1B 40 1B 61 01 1B 45 01 "IAR Tardy Pass" 0A 1B 45 00 1B 61 00
              "Student: Maria Garcia (10003)" 0A "Date: 08/13/2026" 0A
              "Time: 08:36 PM" 0A "Reason: Late" 0A "Staff: CW" 0A
              "Status: Unexcused" 0A 2D 2D ...
```

5. Logs tab recorded `Garcia, Maria / 08/13 08:36 PM · Late · CW / UNEXCUSED`
6. Zero fatal exceptions in logcat

The app reports "sent (no confirmation available)" rather than claiming a
confirmed print, because the AIDL exposes no paper status — `AckLevel.DELIVERED`.
