#!/usr/bin/env python3
"""Read-only Android playback diagnostics; run while reproducing a slowdown."""
import argparse
from datetime import datetime
import os
from pathlib import Path
import shutil
import subprocess
import time


def find_adb():
    candidate = shutil.which("adb")
    if candidate:
        return candidate
    props = Path(__file__).resolve().parents[1] / "local.properties"
    if props.exists():
        for line in props.read_text().splitlines():
            if line.startswith("sdk.dir="):
                candidate = Path(line.split("=", 1)[1]) / "platform-tools/adb"
                if candidate.is_file():
                    return str(candidate)
    for name in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        if os.environ.get(name):
            candidate = Path(os.environ[name]) / "platform-tools/adb"
            if candidate.is_file():
                return str(candidate)
    raise SystemExit("ADB не найден. Добавьте его в PATH или укажите --adb.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", help="Path to adb")
    parser.add_argument("--serial", help="Device serial from adb devices")
    parser.add_argument("--port", type=int, help="Local adb server port, if non-default")
    parser.add_argument("--minutes", type=float, default=30)
    parser.add_argument("--interval", type=float, default=30)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    if args.minutes < 0 or args.interval < 1:
        parser.error("minutes must be >= 0 and interval must be >= 1")
    adb = [args.adb or find_adb()]
    if args.port:
        adb += ["-P", str(args.port)]
    if args.serial:
        adb += ["-s", args.serial]

    def run(*command, timeout=20):
        try:
            result = subprocess.run(adb + list(command), capture_output=True, text=True,
                                    timeout=timeout, errors="replace")
            return result.stdout + result.stderr
        except subprocess.TimeoutExpired:
            return "[diagnostic command timed out]\n"

    state = run("get-state").strip()
    if state != "device":
        raise SystemExit("Нужно одно подключённое устройство (или --serial).\n" + state)
    output = args.output or Path("dist/diagnostics") / datetime.now().strftime("%Y%m%d-%H%M%S")
    output.mkdir(parents=True, exist_ok=False)

    def save(name, *command):
        (output / (name + ".txt")).write_text(run(*command), encoding="utf-8")

    for prop in ("ro.product.manufacturer", "ro.product.model", "ro.build.version.release", "ro.build.version.sdk"):
        save(prop, "shell", "getprop", prop)
    save("webview", "shell", "dumpsys", "webviewupdate")
    save("app-version", "shell", "dumpsys", "package", "pro.sportcast.tv")
    save("codecs-start", "shell", "dumpsys", "media.codec")
    log_tags = ["SportcastPlayer:V", "SportcastNative:V", "ExoPlayerImplInternal:E", "chromium:W", "MediaCodec:W",
                "ACodec:W", "CCodec:W", "cr_MediaCodecBridge:W", "OMXClient:W",
                "AndroidRuntime:E", "lmkd:I", "*:S"]
    live_log = (output / "player-log-live.txt").open("w", encoding="utf-8")
    # BlueStacks may close the direct logcat service; shell transport works there too.
    log_process = subprocess.Popen(adb + ["shell", "logcat", "-T", "100", "-v", "threadtime"] + log_tags,
                                   stdout=live_log, stderr=subprocess.STDOUT)
    started = time.monotonic()
    deadline = started + args.minutes * 60
    print(f"Сохраняю диагностику в {output.resolve()}. Включите проблемный эфир; Ctrl+C завершит сбор.", flush=True)
    try:
        while True:
            elapsed = int(time.monotonic() - started)
            prefix = f"{elapsed:05d}s"
            # Include system totals: WebView's renderer and media services are separate processes.
            save(prefix + "-memory-system", "shell", "dumpsys", "meminfo", "-s")
            save(prefix + "-memory-app", "shell", "dumpsys", "meminfo", "pro.sportcast.tv")
            save(prefix + "-thermal", "shell", "dumpsys", "thermalservice")
            save(prefix + "-cpu", "shell", "dumpsys", "cpuinfo")
            print(f"{elapsed // 60:02d}:{elapsed % 60:02d} — снимок памяти и нагрузки", flush=True)
            if time.monotonic() >= deadline:
                break
            time.sleep(min(args.interval, max(0, deadline - time.monotonic())))
    except KeyboardInterrupt:
        pass
    finally:
        log_process.terminate()
        try:
            log_process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            log_process.kill()
            log_process.wait()
        live_log.close()
        save("codecs-end", "shell", "dumpsys", "media.codec")
        save("player-log", "shell", "logcat", "-d", "-t", "3000", "-v", "threadtime",
             *log_tags)
        print(f"Готово: {output.resolve()}")


if __name__ == "__main__":
    main()
