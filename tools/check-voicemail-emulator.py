#!/usr/bin/env python3
"""Test system MWI delivery on a configured emulator, without placing calls.

Run only after installing the release, granting READ_PHONE_STATE, making this app
 the default dialer and opening MainActivity once. Example:
    python3 tools/check-voicemail-emulator.py --serial emulator-5554
PDU vectors: AOSP frameworks/base SMSTest.testCingularVoiceMail:
https://android.googlesource.com/platform/frameworks/base/+/076357b8567458d4b6dfdcf839ef751634cd2bfb/tests/AndroidTests/src/com/android/unit_tests/SMSTest.java
"""
import argparse
import json
import re
import shlex
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

PACKAGE = "pe.gustavo.telefonolite"
SET = "07912180958750F84401800500C87020026195702B06040102000200"
CLEAR = "07912180958750F84401800500C07020027160112B06040102000000"
RESULTS = Path(__file__).resolve().parent.parent / "docs/resultados/0.3.0/buzon"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--serial", required=True)
    args = parser.parse_args()
    if not re.fullmatch(r"emulator-\d+", args.serial):
        parser.error("--serial must match emulator-N")
    base = ["adb", "-s", args.serial]

    def adb(*parts, binary=False):
        result = subprocess.run(base + list(parts), capture_output=True, timeout=40)
        if result.returncode:
            raise RuntimeError("adb failed: " + result.stderr.decode(errors="replace"))
        return result.stdout if binary else result.stdout.decode(errors="replace")

    def shell(*parts):
        return adb("shell", shlex.join(parts))

    if shell("getprop", "ro.kernel.qemu").strip() != "1":
        raise RuntimeError("Refusing device without ro.kernel.qemu=1")
    if shell("cmd", "role", "get-role-holders", "--user", "0", "android.app.role.DIALER").strip() != PACKAGE:
        raise RuntimeError("App must already be default dialer")
    package_info = shell("dumpsys", "package", PACKAGE)
    if not re.search(r"android\.permission\.READ_PHONE_STATE: granted=true", package_info):
        raise RuntimeError("READ_PHONE_STATE must already be granted")

    RESULTS.mkdir(parents=True, exist_ok=True)
    result = {"serial": args.serial, "checks": {}}
    checks = result["checks"]
    sent_set = False

    def registry():
        return shell("dumpsys", "telephony.registry")

    def states():
        dump = registry()
        calls = re.findall(r"mCallState\s*=\s*(\d+)", dump)
        mwi = re.findall(r"mMessageWaiting\s*=\s*(true|false)", dump, re.I)
        if not calls or not mwi:
            raise RuntimeError("Cannot establish call and MWI state from telephony.registry")
        return calls, [value.lower() == "true" for value in mwi]

    def require_idle():
        calls, _ = states()
        if any(value != "0" for value in calls):
            raise RuntimeError("Call state is not idle: " + repr(calls))
        return calls

    def wait(predicate, description, timeout=25):
        until = time.monotonic() + timeout
        while time.monotonic() < until:
            value = predicate()
            if value:
                return value
            time.sleep(0.5)
        raise RuntimeError("Timed out waiting for " + description)

    def sms(pdu):
        out = adb("emu", "sms", "pdu", pdu)
        if not re.search(r"(?m)^OK\s*$", out) or re.search(r"(?m)^KO", out):
            raise RuntimeError("Emulator rejected PDU: " + out)
        return out.strip()

    def nodes():
        shell("uiautomator", "dump", "/sdcard/voicemail-check.xml")
        xml = shell("cat", "/sdcard/voicemail-check.xml")
        return xml, list(ET.fromstring(xml).iter("node"))

    def find(ns, text, exact=False):
        return next((n for n in ns if any((v == text if exact else text in v)
                    for v in (n.get("text", ""), n.get("content-desc", "")))), None)

    def ui_wait(text, exact=False):
        return wait(lambda: (lambda pair: pair if find(pair[1], text, exact) is not None else None)(nodes()), text)

    def tap(node):
        match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
        if not match:
            raise RuntimeError("Missing usable UI bounds")
        x1, y1, x2, y2 = map(int, match.groups())
        shell("input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))

    def capture(name, xml):
        (RESULTS / (name + ".xml")).write_text(xml, encoding="utf-8")
        (RESULTS / (name + ".png")).write_bytes(adb("exec-out", "screencap", "-p", binary=True))

    def notification_records():
        dump = shell("dumpsys", "notification", "--noredact")
        # Restrict the comparison to active NotificationRecord headers, not channels/history.
        headers = re.findall(r"(?m)^\s*NotificationRecord\([^\n]*", dump)
        return {line.strip() for line in headers if PACKAGE in line or "com.android.phone" in line}

    def launch():
        shell("am", "start", "-n", PACKAGE + "/.MainActivity")

    def open_dialog():
        _, ns = ui_wait("Buzón de voz")
        tap(find(ns, "Buzón de voz"))
        # The common emulator has one SIM. Fail explicitly if selection is needed.
        return ui_wait("El operador indica 2 mensajes pendientes.")

    try:
        require_idle()
        _, initial = states()
        if any(initial):
            raise RuntimeError("Initial MWI is already active; refusing to overwrite it")
        before_notifications = notification_records()
        shell("input", "keyevent", "KEYCODE_HOME")
        shell("am", "kill", PACKAGE)
        sent_set = True  # even a timeout may occur after the emulator accepted the PDU
        checks["set_command"] = {"response": sms(SET), "pass": True}
        wait(lambda: any(states()[1]), "MWI set")
        checks["mwi_set"] = {"pass": True, "state": states()[1]}
        launch()
        xml, ns = ui_wait("Buzón de voz · 2 pendientes", exact=True)
        capture("pendientes", xml)
        checks["main_pending"] = {"pass": True}
        new = notification_records() - before_notifications
        checks["no_new_phone_or_app_notification"] = {"pass": not new, "new_records": sorted(new)}
        xml, _ = open_dialog()
        capture("dialogo-pendientes", xml)
        require_idle()
        checks["dialog_pending_no_call"] = {"pass": True}
        shell("input", "keyevent", "KEYCODE_BACK")
        sms(CLEAR)
        wait(lambda: not any(states()[1]), "MWI clear")
        checks["mwi_clear"] = {"pass": True, "state": states()[1]}
        xml, ns = ui_wait("Buzón de voz", exact=True)
        capture("sin-pendientes", xml)
        tap(find(ns, "Buzón de voz", exact=True))
        xml, _ = ui_wait("El último aviso del operador no indica mensajes pendientes.")
        capture("dialogo-sin-pendientes", xml)
        require_idle()
        checks["dialog_clear_no_call"] = {"pass": True}
    except Exception as exc:
        result["error"] = str(exc)
    finally:
        errors = []
        if sent_set:
            try:
                sms(CLEAR)
                wait(lambda: not any(states()[1]), "cleanup MWI clear")
            except Exception as exc:
                errors.append(str(exc))
        try:
            shell("input", "keyevent", "KEYCODE_BACK")
            launch()
            require_idle()
        except Exception as exc:
            errors.append(str(exc))
        checks["cleanup"] = {"pass": not errors, "errors": errors}
        (RESULTS / "report.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0 if "error" not in result and all(c["pass"] for c in checks.values()) else 1


if __name__ == "__main__":
    sys.exit(main())
