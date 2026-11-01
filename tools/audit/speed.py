#!/usr/bin/env python3
"""
Speed check on the TV emulator: the same steps for Benflix and for plain Wholphin, so they can be compared.
Measures: how long until the home rows show after starting the app (3 tries), how smooth scrolling
with the remote is (frame times from Android), and memory use.

Usage: speed.py <package> <label> <results dir>
"""
import json
import re
import sys
import time

PKG, LABEL, OUT = sys.argv[1], sys.argv[2], sys.argv[3]
sys.argv = [sys.argv[0], "tv", PKG, OUT]
import drive as d  # noqa: E402

ACT = f"{PKG}/com.github.damontecres.wholphin.MainActivity"
res = {"app": LABEL, "package": PKG, "starts": [], "scroll": {}, "memory_kb": None, "notes": []}


def gfx():
    out = d.sh(f"dumpsys gfxinfo {PKG}")
    stats = {}
    for k, pat in {
        "frames": r"Total frames rendered: (\d+)",
        "janky": r"Janky frames: (\d+)",
        "p50_ms": r"50th percentile: (\d+)ms",
        "p90_ms": r"90th percentile: (\d+)ms",
        "p95_ms": r"95th percentile: (\d+)ms",
        "p99_ms": r"99th percentile: (\d+)ms",
        "slow_ui_thread": r"Number Slow UI thread: (\d+)",
        "slow_bitmap_uploads": r"Number Slow bitmap uploads: (\d+)",
    }.items():
        m = re.search(pat, out)
        stats[k] = int(m.group(1)) if m else None
    return stats


def to_home(tag):
    """Start the app and get to the home rows; returns seconds"""
    t0 = time.time()
    d.sh(f"am start -n {ACT}")
    d.sign_in()
    t = time.time() - t0
    d.shot(f"{LABEL}-{tag}")
    return round(t, 1)


def main():
    d.sh(f"pm grant {PKG} android.permission.POST_NOTIFICATIONS")
    # first start includes typing the server and signing in
    res["first_start_incl_sign_in_s"] = to_home("first")
    time.sleep(20)  # let the first load finish
    for i in range(3):
        d.sh(f"am force-stop {PKG}")
        time.sleep(3)
        res["starts"].append(to_home(f"start{i}"))
        time.sleep(15)
    # smooth scrolling: wait for home to settle, then move around with the remote
    time.sleep(10)
    d.sh(f"dumpsys gfxinfo {PKG} reset")
    seq = ["DPAD_DOWN"] * 6 + ["DPAD_RIGHT"] * 12 + ["DPAD_DOWN"] * 6 + ["DPAD_LEFT"] * 6 + ["DPAD_UP"] * 12
    t0 = time.time()
    for k in seq:
        d.sh(f"input keyevent {k}")
        time.sleep(0.25)
    res["scroll"] = gfx()
    res["scroll"]["seconds"] = round(time.time() - t0, 1)
    d.shot(f"{LABEL}-after-scroll")
    # open something and come back
    d.sh(f"dumpsys gfxinfo {PKG} reset")
    d.key("DPAD_CENTER", wait=6)
    d.shot(f"{LABEL}-opened")
    d.key("BACK", wait=3)
    res["open_item"] = gfx()
    mem = d.sh(f"dumpsys meminfo {PKG}")
    m = re.search(r"TOTAL PSS:\s+(\d+)", mem) or re.search(r"TOTAL\s+(\d+)", mem)
    res["memory_kb"] = int(m.group(1)) if m else None
    with open(f"{OUT}/speed-{LABEL}.json", "w") as f:
        json.dump(res, f, indent=1)
    print(json.dumps(res, indent=1))


main()
