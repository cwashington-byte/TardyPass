# H10 Hardware Discovery (Phase 0)

Device: generic Android 13/14 handheld PDA with built-in 58mm thermal printer,
model name "H10" (Settings > About). Hardware settings app exposes: sync to
server, print test, flash firmware, default code system (GB18030 / CP437 /
UTF-8), printing font — strongly implying an ESC/POS printer behind a vendor
interface.

**Status: NOT YET RUN — needs an H10 connected over USB with USB debugging enabled.**

## Discovery protocol (all read-only)

```
adb shell getprop ro.product.model ro.product.manufacturer ro.build.fingerprint
adb shell pm list packages -f | grep -iE "print|pos|serial"
adb shell service list | grep -iE "print|pos"
adb shell ls -l /dev/tty*
adb shell cat /proc/tty/drivers
adb shell pm list packages | grep gms        # GMS presence (auth path decision)
```

Then pull the preinstalled printer/settings APK (path from `pm list packages -f`)
and decompile with jadx. Look for:
- `android_serialport_api` / SerialPort JNI wrappers and the tty path they open
- vendor jar imports (`com.*.printer`) → SDK .aar we can bundle in app/libs
- exported AIDL print services (`dumpsys package <pkg>` → Service section)

If a `/dev/tty*` node is world-writable, smoke test:

```
adb shell "printf '\x1b\x40Hello\n\n\n' > /dev/ttyS1"
```

(try each candidate; a printed "Hello" ends the search)

## Findings

| Question | Answer |
|---|---|
| Real ODM / model | _(pending)_ |
| Printer interface (serial path / SDK / AIDL) | _(pending)_ |
| Working device path | _(pending)_ |
| Code system configured | _(pending)_ |
| GMS present? | _(pending)_ |

## How findings slot in

- Writable tty → set the path in app Settings ("Serial device path"); `SerialEscPosDriver` becomes primary.
- Vendor .aar / AIDL → implement `printing/drivers/VendorSdkDriver.kt` (currently a stub that reports unavailable).
- Nothing usable → ship on `RawbtDriver` (PWA parity) and iterate.
