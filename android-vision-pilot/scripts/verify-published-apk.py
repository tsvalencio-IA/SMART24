"""Verify the released binaries without rebuilding or resigning either APK.

The uninstall below affects only the disposable Actions emulator. It must never
be run against a user's phone: uninstalling removes that app's local data.
"""

import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time
import zipfile


ROOT = Path("published-apk")
REPORTS = ROOT / "reports"
PACKAGE = "br.com.thiaguinhosolucoes.smart24vision"
ACTIVITY = PACKAGE + "/.MobileVigilanteActivity"
RELEASES = {
    "3.1": {
        "bytes": 393641838,
        "sha256": "97e0ae0e5369195725a72ad3588f233181c69215c10760eb683ac36ad583dc78",
        "certificate_sha256": "a6a9a984611d97691fe36df598d69fb4210f8585f38cd10724db5dc8d489d8c7",
        "version_code": 8,
    },
    "3.2": {
        "bytes": 393658222,
        "sha256": "a8359d87933d959e64d6a87d229f5e18276cb3e8b6df65790ef350c5fdd33de1",
        "certificate_sha256": "65a8783f17b9196344ded1785b4c8e53e6e9c2ab19908a86663bee0a572ace01",
        "version_code": 9,
    },
}


def run(args, name, *, check=True, timeout=180):
    result = subprocess.run(args, text=True, stdout=subprocess.PIPE,
                            stderr=subprocess.STDOUT, timeout=timeout)
    (REPORTS / (name + ".txt")).write_text(result.stdout, encoding="utf-8")
    print(name + ": " + result.stdout.strip(), flush=True)
    if check and result.returncode:
        raise RuntimeError(f"{name} failed with exit {result.returncode}")
    return result


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def build_tool(name):
    sdk = Path(os.environ.get("ANDROID_HOME") or os.environ["ANDROID_SDK_ROOT"])
    candidates = sorted((sdk / "build-tools").glob("*/" + name),
                        key=lambda p: tuple(int(n) for n in re.findall(r"\d+", p.parent.name)))
    require(bool(candidates), "Android SDK tool missing: " + name)
    return str(candidates[-1])


def verify_file(version):
    apk = ROOT / ("v" + version + ".apk")
    expected = RELEASES[version]
    require(apk.stat().st_size == expected["bytes"], "Incomplete APK: " + version)
    with apk.open("rb") as stream:
        digest = hashlib.file_digest(stream, "sha256").hexdigest()
    require(digest == expected["sha256"], "APK hash mismatch: " + version)
    with zipfile.ZipFile(apk) as archive:
        require(archive.testzip() is None, "APK ZIP integrity failed: " + version)
    signature = run([build_tool("apksigner"), "verify", "--verbose", "--print-certs", str(apk)],
                    "signature-" + version).stdout
    certificates = re.findall(r"certificate SHA-256 digest:\s*([0-9a-fA-F:]+)", signature)
    require(len(certificates) == 1, "Unexpected number of APK signing certificates")
    certificate = certificates[0].replace(":", "").lower()
    require(certificate == expected["certificate_sha256"], "APK certificate changed")
    badging = run([build_tool("aapt"), "dump", "badging", str(apk)], "package-" + version).stdout
    require("name='" + PACKAGE + "'" in badging.splitlines()[0], "Wrong Android package")
    require("versionCode='" + str(expected["version_code"]) + "'" in badging.splitlines()[0],
            "Wrong APK version")
    require("sdkVersion:'26'" in badging, "Unexpected minimum Android version")
    return dict(expected, zip_integrity="passed", cryptographic_signature="verified")


def prepare():
    evidence = {version: verify_file(version) for version in RELEASES}
    require(evidence["3.1"]["certificate_sha256"] != evidence["3.2"]["certificate_sha256"],
            "Expected legacy release certificate difference was not present")
    (REPORTS / "integrity.json").write_text(json.dumps(evidence, indent=2) + "\n")


def install(apk, name, *, update=False, check=True):
    args = ["adb", "install", "--no-streaming"]
    if update:
        args.append("-r")
    result = run(args + [str(apk)], name, check=check, timeout=300)
    if check:
        require("Success" in result.stdout, name + " did not report installation success")
    return result


def check_installed_file(name):
    paths = run(["adb", "shell", "pm", "path", PACKAGE], name + "-path").stdout
    base = next((line.removeprefix("package:").strip() for line in paths.splitlines()
                 if line.strip().endswith("/base.apk")), None)
    require(bool(base), "Installed APK was not found")
    digest = run(["adb", "shell", "sha256sum", base], name + "-hash").stdout.split()[0]
    require(digest == RELEASES["3.2"]["sha256"], "Installed APK differs from released APK")
    return digest


def cold_launch(number):
    run(["adb", "shell", "am", "force-stop", PACKAGE], f"stop-{number}")
    launch = run(["adb", "shell", "am", "start", "-W", "-n", ACTIVITY], f"launch-{number}").stdout
    require("Status: ok" in launch, "Android did not confirm launching the app")
    time.sleep(5)
    pid = run(["adb", "shell", "pidof", PACKAGE], f"process-{number}").stdout.strip()
    require(bool(pid), "App exited after launch")
    logs = run(["adb", "logcat", "-d", "-b", "crash"], f"crash-buffer-{number}").stdout
    require(PACKAGE not in logs, "App crash was recorded")
    run(["adb", "shell", "dumpsys", "activity", "activities"], f"activities-{number}")
    shot = subprocess.run(["adb", "exec-out", "screencap", "-p"],
                          stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True, timeout=30)
    require(shot.stdout.startswith(b"\x89PNG"), "Emulator screenshot was not captured")
    (REPORTS / f"screen-{number}.png").write_bytes(shot.stdout)
    return {"launch_status": "ok", "process_running": True, "app_crash": False}


def verify_installation():
    run(["adb", "wait-for-device"], "device")
    sdk = run(["adb", "shell", "getprop", "ro.build.version.sdk"], "android-api").stdout.strip()
    # Refuse to remove existing app data: this workflow requires a fresh emulator.
    existing = run(["adb", "shell", "pm", "path", PACKAGE], "initial-app-state", check=False)
    require("package:" not in existing.stdout, "This is not a fresh emulator; refusing to uninstall")
    install(ROOT / "v3.1.apk", "install-3.1")
    failed_update = install(ROOT / "v3.2.apk", "update-3.1-to-3.2", update=True, check=False)
    require(failed_update.returncode != 0 and "INSTALL_FAILED_UPDATE_INCOMPATIBLE" in failed_update.stdout,
            "The expected certificate conflict was not reproduced")
    uninstall = run(["adb", "uninstall", PACKAGE], "remove-emulator-test-copy")
    require("Success" in uninstall.stdout, "Emulator test copy was not removed")
    install(ROOT / "v3.2.apk", "fresh-install-3.2")
    installed_hash = check_installed_file("fresh-install")
    run(["adb", "logcat", "-c"], "clear-log-buffer")
    launches = [cold_launch(1)]
    install(ROOT / "v3.2.apk", "same-certificate-update-3.2", update=True)
    check_installed_file("same-certificate-update")
    launches.append(cold_launch(2))
    evidence = {
        "android_api": int(sdk),
        "package": PACKAGE,
        "released_apk_sha256": installed_hash,
        "legacy_update": "INSTALL_FAILED_UPDATE_INCOMPATIBLE",
        "fresh_install": "Success",
        "same_certificate_update": "Success",
        "cold_launches": launches,
        "physical_phone_or_camera_tested": False,
    }
    (REPORTS / "installation.json").write_text(json.dumps(evidence, indent=2) + "\n")
    print("EXACT_PUBLISHED_APK_INSTALLATION_PASSED " + json.dumps(evidence), flush=True)


if __name__ == "__main__":
    REPORTS.mkdir(parents=True, exist_ok=True)
    require(len(sys.argv) == 2 and sys.argv[1] in ("prepare", "install"), "Use prepare or install")
    try:
        if sys.argv[1] == "prepare":
            prepare()
        else:
            verify_installation()
    except Exception as error:
        (REPORTS / "failure.txt").write_text(str(error) + "\n")
        raise
