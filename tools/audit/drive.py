#!/usr/bin/env python3
"""
Benflix audit robot. Drives the app on an emulator with adb (like a person with a remote or a finger),
takes a screenshot of every screen, checks every button can be reached with the D-pad (TV),
times how long screens take to load, then does a random "monkey" stress test and collects any crashes.

Usage: drive.py <tv|phone> <package> <results dir>
Server: the public Jellyfin demo server (no password), so no private server is needed.
"""
import json
import signal
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

MODE, PKG, OUT = sys.argv[1], sys.argv[2], sys.argv[3]
SERVER = os.environ.get("AUDIT_SERVER", "https://demo.jellyfin.org/stable")
USER = os.environ.get("AUDIT_USER", "demo")
os.makedirs(OUT, exist_ok=True)
REPORT = {"mode": MODE, "steps": [], "timings": {}, "unreachable": {}, "problems": []}
shot_no = [0]
DEADLINE = time.time() + 36 * 60


def out_of_time():
    return time.time() > DEADLINE


def adb(*args, check=False, timeout=60):
    try:
        r = subprocess.run(["adb", *args], capture_output=True, timeout=timeout)
        return r.stdout.decode("utf-8", "replace")
    except subprocess.TimeoutExpired:
        return ""


def sh(cmd, timeout=60):
    return adb("shell", cmd, timeout=timeout)


def save_report():
    with open(f"{OUT}/report.json", "w") as f:
        json.dump(REPORT, f, indent=1)


def log(msg):
    print(f"[{MODE}] {msg}", flush=True)
    REPORT["steps"].append(msg)
    if len(REPORT["steps"]) % 5 == 0:
        save_report()


PKG_LABEL = re.compile(r"Benflix|MyStream", re.I)
SYSTEM_POPUPS = re.compile(r"isn't responding|keeps stopping|has stopped")


def dump(dismiss=True):
    """Returns the screen's UI tree, or None when the screen never settled"""
    for _ in range(3):
        out = sh("rm -f /sdcard/ui.xml; uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; cat /sdcard/ui.xml", timeout=20)
        if "<hierarchy" in out:
            try:
                root = ET.fromstring(out[out.index("<?xml") if "<?xml" in out else out.index("<hierarchy"):])
            except ET.ParseError:
                root = None
            if root is not None and dismiss:
                popup = [n.get("text") or "" for n in root.iter("node") if SYSTEM_POPUPS.search(n.get("text") or "")]
                if popup:
                    if PKG_LABEL.search(popup[0]):
                        REPORT["problems"].append({"type": "system popup", "text": popup[0]})
                    for n in root.iter("node"):
                        if (n.get("text") or "") in ("Wait", "Close app", "OK"):
                            tap(n)
                            time.sleep(2)
                            break
                    continue
            if root is not None:
                LAST_ROOT[0] = root
                return root
        time.sleep(1)
    return None


def nodes(root):
    return [] if root is None else list(root.iter("node"))


def texts(root):
    return [n.get("text") or n.get("content-desc") or "" for n in nodes(root)]


def find(root, pattern):
    rx = re.compile(pattern, re.I)
    for n in nodes(root):
        if rx.search(n.get("text") or "") or rx.search(n.get("content-desc") or ""):
            return n
    return None


def center(n):
    a, b, c, d = map(int, re.findall(r"\d+", n.get("bounds")))
    return (a + c) // 2, (b + d) // 2


def shot(name):
    shot_no[0] += 1
    base = f"{OUT}/{shot_no[0]:03d}-{re.sub(r'[^a-z0-9]+', '-', name.lower()).strip('-')}"
    with open(base + ".png", "wb") as f:
        f.write(subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True).stdout)
    root = dump()
    if root is not None:
        with open(base + ".txt", "w") as f:
            for n in nodes(root):
                t = n.get("text") or n.get("content-desc")
                if t or n.get("focused") == "true":
                    f.write(f"{'*' if n.get('focused') == 'true' else ' '} {n.get('bounds')} {t}\n")
    check_crash(name)
    log(f"screen {name}: " + " | ".join(t for t in texts(root) if t)[:300])
    return root


def key(k, times=1, wait=0.35):
    for _ in range(times):
        sh(f"input keyevent {k}")
        time.sleep(wait)


LAST_ROOT = [None]


def bounds(n):
    return tuple(map(int, re.findall(r"\d+", n.get("bounds"))))


def resolve(n):
    """The button a label belongs to: the thing itself, the smallest button around it, or the closest button"""
    def actionable(c):
        return c.get("clickable") == "true" or c.get("focusable") == "true"

    if actionable(n) or LAST_ROOT[0] is None:
        return n
    x, y = center(n)
    best = None
    for c in nodes(LAST_ROOT[0]):
        if not actionable(c):
            continue
        a, b, cc, d = bounds(c)
        if a <= x <= cc and b <= y <= d:
            dist = -1e9 + (cc - a) * (d - b)  # containing: prefer the smallest
        else:
            cx, cy = (a + cc) / 2, (b + d) / 2
            dist = ((cx - x) ** 2 + (cy - y) ** 2) ** 0.5
            if dist > 250:
                continue
        if best is None or dist < best[0]:
            best = (dist, c)
    return best[1] if best is not None else n


def touch_tap(n):
    target = resolve(n)
    x, y = center(target)
    sh(f"input tap {x} {y}")


def focused_node(root):
    found = None
    for n in nodes(root):
        if n.get("focused") == "true":
            found = n  # deepest wins
    return found


def contains(outer, inner):
    a, b, c, d = bounds(outer)
    x, y = center(inner)
    return a <= x <= c and b <= y <= d


def tap(n):
    """Phone: a finger tap. TV: walk the focus to it with the D-pad, then press OK"""
    if MODE != "tv":
        touch_tap(n)
        return
    label = n.get("text") or n.get("content-desc") or ""
    n = resolve(n)
    tx, ty = center(n)
    for _ in range(30):
        root = LAST_ROOT[0] if _ == 0 else dump(dismiss=False)
        f = focused_node(root)
        if f is None:
            key("DPAD_DOWN", wait=0.4)
            continue
        # is the target inside the focused thing (or the focused thing inside the target)?
        if contains(f, n) or contains(n, f):
            key("DPAD_CENTER", wait=0.8)
            return
        fx, fy = center(f)
        dx, dy = tx - fx, ty - fy
        if abs(dy) > 40 and abs(dy) >= abs(dx) / 3:
            key("DPAD_DOWN" if dy > 0 else "DPAD_UP", wait=0.4)
        else:
            key("DPAD_RIGHT" if dx > 0 else "DPAD_LEFT", wait=0.4)
    log(f"TV: could not move focus to '{label}'")
    REPORT["problems"].append({"type": "could not reach with remote", "text": label})


def press(pattern, timeout=15):
    """Taps (phone) or focuses + presses OK (TV) the thing whose text matches"""
    end = time.time() + timeout
    while time.time() < end:
        root = dump()
        n = find(root, pattern)
        if n is not None:
            tap(n)  # a tap also works on TV emulators for things the remote would press
            time.sleep(1.5)
            return True
        time.sleep(1)
    log(f"could not find '{pattern}'")
    return False


def wait_for(pattern, timeout=40):
    """Seconds until text matching pattern shows, or None"""
    start = time.time()
    while time.time() - start < timeout:
        if find(dump(), pattern) is not None:
            return round(time.time() - start, 1)
        time.sleep(0.5)
    return None


def app_running():
    return bool(sh(f"pidof {PKG}").strip())


crash_seen = set()


def check_crash(where):
    out = adb("logcat", "-d", "-b", "crash")
    for block in re.split(r"(?=FATAL EXCEPTION)", out):
        if "FATAL EXCEPTION" in block and PKG in block:
            sig = block[:600]
            if sig not in crash_seen:
                crash_seen.add(sig)
                REPORT["problems"].append({"type": "crash", "where": where, "trace": block[:4000]})
                log(f"CRASH near '{where}'")
    anr = sh("ls /data/anr 2>/dev/null")
    if anr.strip() and "anr" not in crash_seen:
        crash_seen.add("anr")
        REPORT["problems"].append({"type": "anr", "where": where, "files": anr})


def focus_sweep(name, max_moves=60):
    """TV: walks the D-pad over the screen and lists clickable things that never got focus"""
    root = dump()
    clickable = {}
    for n in nodes(root):
        if n.get("clickable") == "true" or n.get("focusable") == "true":
            label = n.get("text") or n.get("content-desc") or ""
            if not label:
                for c in n.iter("node"):
                    label = c.get("text") or c.get("content-desc") or ""
                    if label:
                        break
            if label:
                clickable[n.get("bounds")] = label
    seen = set()

    def record():
        r = dump()
        for n in nodes(r):
            if n.get("focused") == "true":
                seen.add(n.get("bounds"))
                for c in n.iter("node"):
                    seen.add(c.get("bounds"))

    record()
    # the top menu first: up into it, then all the way right and back
    key("DPAD_UP", 8, wait=0.2)
    record()
    for _ in range(12):
        key("DPAD_RIGHT", wait=0.25)
        record()
    for _ in range(12):
        key("DPAD_LEFT", wait=0.2)
    record()
    # then the page: snake right across, down one, left across, down one ...
    for row in range(6):
        for _ in range(8):
            key("DPAD_RIGHT" if row % 2 == 0 else "DPAD_LEFT", wait=0.25)
            record()
        key("DPAD_DOWN", wait=0.3)
        record()
    key("DPAD_UP", 12, wait=0.15)
    record()
    missed = sorted({v for b, v in clickable.items() if b not in seen})
    REPORT["unreachable"][name] = missed
    log(f"focus sweep '{name}': {len(clickable)} buttons, {len(missed)} never focused")


def back_to_home():
    if MODE != "tv":
        for _ in range(3):
            n = find(dump(), r"^Home$")
            if n is not None:
                tap(n)
                time.sleep(2)
                return
            key("BACK", wait=1)
        return
    for _ in range(6):
        if find(dump(), r"^Home$") is not None:
            return
        key("BACK", wait=1)


def monkey():
    """Random stress: thousands of remote presses / taps"""
    back_to_home()
    log("monkey test")
    args = "--pct-nav 35 --pct-majornav 35 --pct-syskeys 5 --pct-appswitch 0 --pct-anyevent 0"
    if MODE == "tv":
        args += " -c android.intent.category.LEANBACK_LAUNCHER --pct-touch 0 --pct-motion 0 --pct-trackball 0 --pct-flip 0 --pct-pinchzoom 0 --pct-rotation 0 --pct-permission 0"
    else:
        args = "--pct-touch 45 --pct-motion 20 --pct-nav 10 --pct-majornav 15 --pct-syskeys 5 --pct-appswitch 0 --pct-rotation 0 --pct-anyevent 0 --pct-permission 0"
    mk = sh(f"monkey -p {PKG} -s 42 --throttle 120 {args} --ignore-security-exceptions -v 2500", timeout=900)
    with open(f"{OUT}/monkey.txt", "w") as f:
        f.write(mk)
    shot("after-monkey")
    check_crash("monkey")
    if not app_running():
        REPORT["problems"].append({"type": "not running after monkey"})
    # back into the app for the guided tour
    sh(f"am start -n {PKG}/com.github.damontecres.wholphin.MainActivity")
    time.sleep(6)



def main():
    sh("pm grant %s android.permission.POST_NOTIFICATIONS" % PKG)
    adb("logcat", "-c")
    adb("logcat", "-b", "crash", "-c")
    t0 = time.time()
    out = sh(f"am start -W -n {PKG}/com.github.damontecres.wholphin.MainActivity", timeout=90)
    m = re.search(r"TotalTime: (\d+)", out)
    REPORT["timings"]["cold start (ms, first frame)"] = int(m.group(1)) if m else None
    for i in range(6):
        time.sleep(3)
        shot(f"start-{i}")
    # Sign in to the demo server: look at the screen and do the next sensible thing, for up to 4 minutes
    typed_server = typed_user = False
    end = time.time() + 240
    step = 0
    while time.time() < end:
        step += 1
        root = dump()
        if find(root, r"Continue watching|Top 10|New on Benflix|My List|Because you watched|^Home$") is not None:
            log("signed in")
            break
        if step % 3 == 1:
            shot(f"login-{step}")
        edit = [n for n in nodes(root) if n.get("class", "").endswith("EditText")]
        if find(root, r"Enter Server IP or URL") is not None and edit:
            if not typed_server:
                if MODE != "tv":  # on TV the box already has focus; pressing OK would open the keyboard
                    tap(edit[0])
                time.sleep(1)
                sh("input text '%s'" % SERVER)
                time.sleep(1)
                typed_server = True
                shot("typed-server")
            if MODE == "tv":
                key("ENTER", wait=3)  # the keyboard's Go button
                if find(dump(), r"Enter Server IP or URL") is not None:
                    key("ESCAPE", wait=1)
                    key("DPAD_DOWN", wait=0.5)
                    key("DPAD_CENTER", wait=3)
            else:
                n = find(dump(), r"^Submit$")
                if n is not None:
                    tap(n)
            time.sleep(4)
            continue
        if find(root, r"^Username$") is not None and edit:
            if not typed_user:
                if find(root, rf"^{USER}$") is None:
                    if MODE != "tv":
                        tap(edit[0])
                    time.sleep(1)
                    sh(f"input text {USER}")
                typed_user = True
                shot("typed-user")
            if MODE == "tv":
                key("ENTER", wait=4)  # the keyboard's Go button on the password box
                if find(dump(), r"^Username$") is not None:
                    key("ESCAPE", wait=1)
                    key("DPAD_DOWN", wait=0.5)
                    key("DPAD_CENTER", wait=4)
            else:
                n = find(dump(), r"^Login$")
                if n is not None:
                    tap(n)
                time.sleep(4)
            continue
        n = find(root, r"Username or password|username/password")
        if n is not None and find(root, r"Quick Connect") is not None:
            tap(n)
            time.sleep(2)
            continue
        n = find(root, rf"^{USER}$")
        if n is not None and find(root, r"Who's watching") is not None:
            tap(n)
            time.sleep(4)
            continue
        n = find(root, r"^Add profile$")
        if n is not None:
            tap(n)
            time.sleep(2)
            continue
        n = find(root, r"^Enter server address$")
        if n is not None:
            tap(n)
            time.sleep(2)
            continue
        n = find(root, r"^Add Server$")
        if n is not None:
            tap(n)
            time.sleep(3)
            continue
        time.sleep(2)
    shot("after-login")
    for name in ["close", "later", "not now", "dismiss", "ok", "skip"]:
        n = find(dump(), rf"^{name}$")
        if n is not None:
            tap(n)
            time.sleep(1)
    t = wait_for(r"Continue watching|Top 10|New on Benflix|My List|Because you watched", 60)
    REPORT["timings"]["home rows visible (s)"] = t
    shot("home")
    if t is None and find(dump(), r"^Home$") is None:
        log("never got signed in; stopping here")
        REPORT["problems"].append({"type": "robot could not sign in"})
        return
    save_report()
    monkey()
    save_report()
    home = dump()
    REPORT["home_texts"] = [x for x in texts(home) if x][:200]
    if MODE == "tv":
        focus_sweep("home")
        shot("home-after-sweep")
    else:
        for i in range(5):
            sh("input swipe 500 1500 500 500 400")
            time.sleep(1.2)
            shot(f"home-scroll-{i}")
        sh("input swipe 500 500 500 2000 300")

    # Visit every tab in the top bar / bottom bar
    tabs = (
        [r"^Home$", r"Browse", r"^Channels$", r"^Games$", r"My Benflix", r"^More$", r"Search"]
        if MODE == "tv"
        else [r"^Home$", r"^Search$", r"^Clips$", r"^Channels$", r"My Benflix"]
    )
    for tab in tabs:
        if out_of_time():
            break
        back_to_home()
        start = time.time()
        if not press(tab, timeout=8):
            continue
        time.sleep(6)
        name = re.sub(r"[^A-Za-z ]", "", tab).strip() or "tab"
        root = shot(f"tab-{name}")
        if tab in (r"Browse", r"^More$"):
            # a pop-up list: open each entry
            entries = [n.get("text") for n in nodes(root) if n.get("text") and n.get("clickable") != "false"]
            entries = [e for e in entries if e not in ("Browse", "More")][:8]
            for e in entries:
                if not press(re.escape(e), timeout=5):
                    continue
                time.sleep(3)
                shot(f"{name}-{e}")
                if MODE == "tv":
                    focus_sweep(f"{name}/{e}")
                key("BACK", wait=1.5)
                back_to_home()
                press(tab, timeout=6)
                time.sleep(1.5)
            key("BACK", wait=1)
            continue
        REPORT["timings"][f"open {name} (s)"] = round(time.time() - start, 1)
        if MODE == "tv":
            focus_sweep(name)

    # Open a movie's details, then play it for a bit
    back_to_home()
    if MODE == "tv":
        key("DPAD_DOWN", 2)
        key("DPAD_CENTER", wait=1)
    else:
        root = dump()
        heading = find(root, r"picks$|^New on|^Because you|^Top 10|^My List")
        if heading is not None:
            a, b, c, d = bounds(heading)
            sh(f"input tap {a + 120} {d + 200}")
        else:
            log("no poster row found on phone Home")
    t = wait_for(r"^Play$|Resume|Episodes|More like this", 30)
    REPORT["timings"]["details page (s)"] = t
    shot("details")
    if MODE == "tv":
        focus_sweep("details")
    time.sleep(4)
    shot("details-trailer")
    if press(r"^Play$|^Resume$", timeout=8):
        time.sleep(10)
        shot("playing")
        key("DPAD_CENTER", wait=1)
        shot("player-controls")
        key("BACK", 2, wait=1.5)
        shot("after-player")

    # Settings
    back_to_home()
    if MODE == "tv":
        press(r"^Settings$", timeout=6)
    else:
        press(r"My Benflix", timeout=6)
    time.sleep(2)
    shot("settings")

    # Save the app's log
    with open(f"{OUT}/logcat.txt", "w") as f:
        f.write(adb("logcat", "-d", "-v", "time", timeout=120))
    with open(f"{OUT}/report.json", "w") as f:
        json.dump(REPORT, f, indent=1)
    print(json.dumps({k: REPORT[k] for k in ("timings", "unreachable", "problems")}, indent=1)[:20000])


def on_term(*_):
    save_report()
    sys.exit(1)


signal.signal(signal.SIGTERM, on_term)

if __name__ == "__main__":
    try:
        main()
    finally:
        with open(f"{OUT}/report.json", "w") as f:
            json.dump(REPORT, f, indent=1)
