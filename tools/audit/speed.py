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


WALK = ["DPAD_DOWN"] * 4 + ["DPAD_RIGHT"] * 6 + ["DPAD_LEFT"] * 3 + ["DPAD_DOWN"] * 2 + ["DPAD_UP"] * 6
PAGES = ["Home", "Movies", "Shows", "Live TV", "Music", "Games", "Books", "Requests"]
LAST_RUN = "https://raw.githubusercontent.com/TheBenPsych/MyStream/audit-results/speed/speed-benflix.json"


def walk(name):
    """Move around the page that is open with the remote and note how smoothly it drew"""
    time.sleep(6)
    d.sh(f"dumpsys gfxinfo {PKG} reset")
    for k in WALK:
        d.sh(f"input keyevent {k}")
        time.sleep(0.25)
    time.sleep(1)
    res["pages"][name] = gfx()
    d.shot(f"{LABEL}-page-{name.replace(' ', '')}")


def pages():
    """Speed report with every update (Ben 2026-11-21): the same short walk on every main page, so a page
    that got slower is caught here before it reaches a real TV"""
    res["pages"] = {}
    for name in PAGES:
        try:
            if d.open_place(name):
                walk(name)
            else:
                res["notes"].append(f"could not open {name}")
        except Exception as e:  # one page failing must not lose the rest of the report
            res["notes"].append(f"{name}: {e}")
    # Settings is behind the profile picture, not in the menu
    try:
        d.back_to_home()
        d.key("DPAD_UP", 4, wait=0.5)
        d.key("DPAD_RIGHT", 2, wait=0.4)
        d.key("DPAD_CENTER", wait=1.5)
        if d.press(r"^Settings$", timeout=6):
            walk("Settings")
            d.key("BACK", wait=2)
        else:
            res["notes"].append("could not open Settings")
        d.back_to_home()
    except Exception as e:
        res["notes"].append(f"Settings: {e}")


def report():
    """REPORT.md in plain words: each page against the run before this one. The test TV is a slow cloud
    emulator, so the numbers only mean something next to last time's, never on their own."""
    import urllib.request

    before = {}
    try:
        with urllib.request.urlopen(LAST_RUN, timeout=20) as r:
            before = json.load(r).get("pages") or {}
    except Exception as e:
        res["notes"].append(f"no earlier run to compare with: {e}")
    lines = ["# Glowfin speed report", "", "| Page | This build | Last build | Verdict |", "|---|---|---|---|"]
    slower = []
    for name, now in (res.get("pages") or {}).items():
        a, b = now.get("p90_ms"), (before.get(name) or {}).get("p90_ms")
        if a is None:
            verdict = "Could not measure"
        elif b is None:
            verdict = "First measurement"
        elif a > b * 1.3 and a - b >= 100:
            verdict = "SLOWER than last build"
            slower.append(name)
        elif a < b * 0.75 and b - a >= 100:
            verdict = "Faster"
        else:
            verdict = "Same as before"
        lines.append(f"| {name} | {a if a is not None else '-'} ms | {b if b is not None else '-'} ms | {verdict} |")
    times = [t["steps_ms"] for t in res.get("app_timings", []) if t.get("steps_ms")]
    who = sorted(t["Who's watching"] for t in times if "Who's watching" in t)
    intro = sorted(t["intro done"] for t in times if "intro done" in t)
    lines += ["", "Each number is how long the slowest tenth of frames took while moving around the page (lower is better)."]
    if who:
        lines.append(f"Opening the app: Who's watching was ready after {who[len(who) // 2] / 1000:.1f} s.")
    if intro:
        lines.append(f"The start-up logo was gone after {intro[len(intro) // 2] / 1000:.1f} s.")
    lines.append("Slower pages: " + (", ".join(slower) if slower else "none") + ".")
    if res["notes"]:
        lines += ["", "Notes: " + "; ".join(res["notes"])]
    with open(f"{OUT}/REPORT.md", "w") as f:
        f.write("\n".join(lines) + "\n")
    res["slower_pages"] = slower
    with open(f"{OUT}/speed-{LABEL}.json", "w") as f:
        json.dump(res, f, indent=1)


def main():
    d.sh(f"pm grant {PKG} android.permission.POST_NOTIFICATIONS")
    # first start includes typing the server and signing in
    res["first_start_incl_sign_in_s"] = to_home("first")
    time.sleep(20)  # let the first load finish
    res["app_timings"] = []
    for i in range(3):
        d.sh(f"am force-stop {PKG}")
        time.sleep(3)
        d.sh("logcat -c")
        res["starts"].append(to_home(f"start{i}"))
        time.sleep(15)
        # Benflix's own stopwatch (StartupTimer) writes each step to the log; this is more exact
        # than the robot's timing, which includes the robot reading the screen
        log = d.sh("logcat -d")
        steps = re.findall(r"Benflix start-up: (.+?) after (\d+) ms", log)
        summary = re.findall(r"Benflix start-up summary: (.+)", log)
        res["app_timings"].append({"steps_ms": {k: int(v) for k, v in steps}, "summary": summary[-1].strip() if summary else None})
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
    if LABEL == "benflix":
        # after the first save, so a page walk that runs out of time can't lose the numbers above
        pages()
        report()
    print(json.dumps(res, indent=1))


main()
