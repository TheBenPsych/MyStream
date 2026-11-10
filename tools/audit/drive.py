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
# SMOKE=1: the quick check that runs on every pull request. Signs in, opens every tab, a details page and
# the player, then fails if the app crashed. Skips the slow button sweep and the random stress test.
SMOKE = os.environ.get("SMOKE") == "1"
SERVER = os.environ.get("AUDIT_SERVER", "https://demo.jellyfin.org/stable")
USER = os.environ.get("AUDIT_USER", "demo")
os.makedirs(OUT, exist_ok=True)
# keep the report of an earlier try (when the emulator crashed and was started again)
if os.path.exists(f"{OUT}/report.json"):
    n = len([f for f in os.listdir(OUT) if f.startswith("report-try")]) + 1
    os.rename(f"{OUT}/report.json", f"{OUT}/report-try{n}.json")
REPORT = {"mode": MODE, "steps": [], "timings": {}, "unreachable": {}, "problems": []}
shot_no = [0]
DEADLINE = time.time() + (12 if SMOKE else 36) * 60


def out_of_time():
    return time.time() > DEADLINE


def adb(*args, check=False, timeout=60):
    try:
        r = subprocess.run(["adb", *args], capture_output=True, timeout=timeout)
        return r.stdout.decode("utf-8", "replace")
    except subprocess.TimeoutExpired:
        return ""


def reconnect():
    """adb sometimes loses the emulator; get it back"""
    state = subprocess.run(["adb", "get-state"], capture_output=True, text=True).stdout.strip()
    if state == "device":
        return
    print(f"[{MODE}] adb lost the device ({state!r}), reconnecting", flush=True)
    REPORT.setdefault("adb_reconnects", 0)
    REPORT["adb_reconnects"] += 1
    subprocess.run(["adb", "reconnect"], capture_output=True, timeout=30)
    time.sleep(3)
    if subprocess.run(["adb", "get-state"], capture_output=True, text=True).stdout.strip() != "device":
        subprocess.run(["adb", "kill-server"], capture_output=True, timeout=30)
        subprocess.run(["adb", "start-server"], capture_output=True, timeout=30)
        subprocess.run(["adb", "wait-for-device"], capture_output=True, timeout=120)


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


# text that only shows once you are on the home screen (Benflix rows, or plain Wholphin's rows)
HOME_RE = r"^Good (morning|afternoon|evening)|^Happy |^Merry |^Live TV$|Continue watching|^Home$"
PKG_LABEL = re.compile(r"Benflix|MyStream", re.I)
SYSTEM_POPUPS = re.compile(r"isn't responding|keeps stopping|has stopped")


def dump(dismiss=True):
    """Returns the screen's UI tree, or None when the screen never settled"""
    for attempt in range(3):
        if attempt:
            reconnect()
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


def label_under(tile, label):
    """True if the label sits just below the tile (Who's watching shows the name under the picture)"""
    a, b, c, d = bounds(tile)
    x, y = center(label)
    return a <= x <= c and d <= y <= d + 120


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
        if contains(f, n) or contains(n, f) or label_under(f, n):
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
    """Back until Home's greeting shows (no Home tab any more in the redesign)"""
    for _ in range(6):
        if find(dump(), HOME_GREETING_RE) is not None:
            return
        key("BACK", wait=1)


def tv_stress(presses=1500):
    """TV: random remote presses (arrows, OK, now and then Back), like a toddler with the remote"""
    import random

    rnd = random.Random(42)
    keys = ["DPAD_UP"] * 20 + ["DPAD_DOWN"] * 25 + ["DPAD_LEFT"] * 20 + ["DPAD_RIGHT"] * 25 + ["DPAD_CENTER"] * 8 + ["BACK"] * 2
    out = []
    relaunches = 0
    for i in range(0, presses, 25):
        batch = " ".join(f"input keyevent {rnd.choice(keys)};" for _ in range(25))
        sh(batch, timeout=120)
        if i % 100 == 0:
            top = sh("dumpsys activity activities | grep -m1 -E 'topResumedActivity|mResumedActivity'")
            if PKG not in top:
                relaunches += 1
                out.append(f"left the app after ~{i} presses: {top.strip()[:160]}")
                sh(f"am start -n {PKG}/com.github.damontecres.wholphin.MainActivity")
                time.sleep(4)
            check_crash(f"stress {i}")
    out.append(f"done: {presses} presses, {relaunches} relaunches")
    return "\n".join(out)


def monkey():
    """Random stress: thousands of remote presses / taps"""
    back_to_home()
    log("monkey test")
    args = "--pct-nav 35 --pct-majornav 35 --pct-syskeys 5 --pct-appswitch 0 --pct-anyevent 0"
    if MODE == "tv":
        args += " -c android.intent.category.LEANBACK_LAUNCHER --pct-touch 0 --pct-motion 0 --pct-trackball 0 --pct-flip 0 --pct-pinchzoom 0 --pct-rotation 0 --pct-permission 0"
    else:
        args = "--pct-touch 45 --pct-motion 20 --pct-nav 10 --pct-majornav 15 --pct-syskeys 5 --pct-appswitch 0 --pct-rotation 0 --pct-anyevent 0 --pct-permission 0"
    if MODE == "tv":
        mk = tv_stress()
    else:
        mk = adb("shell", f"monkey -p {PKG} -s 42 --throttle 120 {args} --ignore-security-exceptions -v 2500 2>&1", timeout=900)
    with open(f"{OUT}/monkey.txt", "w") as f:
        f.write(mk)
    shot("after-monkey")
    # random taps on a phone can open quick settings and turn off Wi-Fi; put the network back
    sh("cmd statusbar collapse; svc wifi enable; svc data enable; cmd connectivity airplane-mode disable")
    time.sleep(5)
    check_crash("monkey")
    if not app_running():
        REPORT["problems"].append({"type": "not running after monkey"})
    # a fresh start of the app for the guided tour (still signed in)
    sh(f"am force-stop {PKG}")
    sh(f"am start -n {PKG}/com.github.damontecres.wholphin.MainActivity")
    time.sleep(8)
    sign_in()



def type_answer(edit, text):
    """Clear the sign-in answer and type [text] in small pieces (a slow emulator drops letters)"""
    if MODE != "tv" and edit:
        tap(edit[0])
        time.sleep(1)
        sh("input keyevent MOVE_END")
    sh("input keyevent " + " ".join(["DEL"] * 60))
    for i in range(0, len(text), 6):
        sh("input text '%s'" % text[i : i + 6])
        time.sleep(0.4)
    time.sleep(1)


def sign_in():
    """Sign in to the demo server (or pick the profile again): look at the screen and do the next sensible thing"""
    typed_server = typed_user = False
    end = time.time() + 240
    step = 0
    while time.time() < end:
        step += 1
        root = dump()
        if find(root, HOME_RE) is not None:
            log("signed in")
            break
        if find(root, r"^Step 1 of \d+$") is not None:
            log("signed in (first-time setup is showing)")
            break
        if root is None:
            # the screen can't be read right now (it never "settles" while something animates); wait and look again
            shot(f"login-unreadable-{step}")
            time.sleep(3)
            continue
        if step % 3 == 1:
            shot(f"login-{step}")
        edit = [n for n in nodes(root) if n.get("class", "").endswith("EditText")]
        # Benflix redesign sign-in: one question per screen (TV types with key presses, which the screen keyboard takes)
        if find(root, r"^Is this your server\?$") is not None:
            n = find(root, r"^No, type the address$")
            if n is not None:
                tap(n)
                time.sleep(2)
                continue
        if find(root, r"^What's your server address\?$") is not None:
            if find(root, "^" + re.escape(SERVER) + "$") is None:
                type_answer(edit, SERVER)
                shot("typed-server")
                if find(dump(), "^" + re.escape(SERVER) + "$") is None:
                    log("server address came out wrong, typing again")
                    continue
            n = find(dump(), r"^Next$")
            if n is not None:
                tap(n)
            time.sleep(5)
            continue
        # Redesign 2 sign-in: TV = "Sign in with your phone" beside "Or type it in"; phone = one form
        if find(root, r"^Or type it in$|^Sign in to Benflix$") is not None:
            if find(root, rf"^{USER}$") is None:
                # TV: the keyboard has the focus and types into the Name box to begin with
                type_answer(edit, USER)
                shot("typed-user")
                continue
            n = find(dump(), r"^Sign in$")
            if n is not None:
                tap(n)
            time.sleep(6)
            log("sign-in screen: " + " | ".join(x for x in texts(dump()) if x)[:300])
            continue
        if find(root, r"^What's your name\?$") is not None:
            if find(root, rf"^{USER}$") is None:
                type_answer(edit, USER)
                shot("typed-user")
            n = find(dump(), r"^Next$")
            if n is not None:
                tap(n)
            time.sleep(3)
            continue
        if find(root, r"What's your password\?$") is not None:
            if MODE != "tv" and step % 2 == 0:
                key("ENTER", wait=0.5)  # the phone keyboard's Go key signs in
            else:
                n = find(root, r"^Sign in$")
                if n is not None:
                    tap(n)
            time.sleep(6)
            log("password step: " + " | ".join(x for x in texts(dump()) if x)[:300])
            continue
        if find(root, r"Enter Server IP or URL") is not None and edit:
            if not typed_server or (edit[0].get("text") or "") != SERVER:
                if MODE != "tv":  # on TV the box already has focus; pressing OK would open the keyboard
                    tap(edit[0])
                time.sleep(1)
                # clear whatever is there, then type in small pieces (a slow emulator drops letters)
                sh("input keyevent MOVE_END; " + "input keyevent DEL; " * 5)
                sh("input keyevent " + " ".join(["DEL"] * 60))
                for i in range(0, len(SERVER), 6):
                    sh("input text '%s'" % SERVER[i : i + 6])
                    time.sleep(0.4)
                time.sleep(1)
                typed_server = True
                field = [n for n in nodes(dump()) if n.get("class", "").endswith("EditText")]
                shot("typed-server")
                if field and (field[0].get("text") or "") != SERVER:
                    log("server address came out wrong, typing again")
                    continue
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
        if n is not None and find(root, r"Who's watching|Select User") is not None:
            f = focused_node(root)
            if MODE == "tv" and f is not None and (contains(f, n) or label_under(f, n)):
                # Who's watching starts with the focus on the first profile: just press OK
                key("DPAD_CENTER", wait=0.8)
            else:
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


def welcome_setup():
    """The first-time welcome setup opens over Home for an account that hasn't done it: go through every step"""
    if wait_for(r"^Step 1 of \d+$", 25) is None:
        log("welcome setup: didn't open (this account already did it)")
        REPORT["welcome"] = "not shown"
        return
    REPORT["welcome"] = "started"
    # what to press on each step, in order of preference. There's no Skip any more, and Back (on every
    # step after the first) is never pressed; the search box on the favourites step is left alone.
    # Hello: Let's go. Device check, picture, subtitles, a TV that already has a PIN, favourites: Next.
    # Easy read: Looks good. Parent PIN: No thanks. Hide genres (last): Done. Then the finish screen: Go to Home.
    # Redesign 2: the Parent PIN step's main button sets a PIN, so the robot uses "Skip for now" there.
    buttons = [r"^Let's go$", r"^Next", r"^Looks good$", r"^No thanks$", r"^Done$", r"^Skip for now$"]
    seen = []
    tries = {}
    finished = False
    for _ in range(40):
        root = dump()
        step = find(root, r"^Step \d+ of \d+$")
        if step is None:
            # the "You're all set" screen has no step dots
            n = find(root, r"^Go to Home$")
            if n is not None:
                shot("welcome-finish")
                tap(n)
                finished = True
            elif find(root, r"^All set") is not None or find(root, HOME_RE) is not None:
                # the redesign's "All set" opens Home by itself
                shot("welcome-finish")
                finished = True
            break
        label = step.get("content-desc") or step.get("text") or ""
        if label not in seen:
            seen.append(label)
            shot("welcome-" + label.split()[1])
        tries[label] = tries.get(label, 0) + 1
        # stuck on a step (a choice the robot doesn't understand): use its small "Skip for now"
        if tries[label] > 4:
            n = find(root, r"^Skip for now$")
            if n is not None:
                log(f"welcome setup: skipping {label}")
                tap(n)
                time.sleep(2)
                continue
        # subtitles: pick a mode first (Next stays off until one is picked)
        # (once picked it shows a tick; on TV the explanation underneath can also read just
        # "Only for other languages", so don't keep pressing it)
        n = find(root, r"^Only for other languages$")
        if n is not None and find(root, r"^✓ ") is None:
            tap(n)
            time.sleep(1.5)
            continue
        # favourites: pick 3 posters before Next turns on
        if find(root, r"^Pick a few things you love$") is not None:
            unpicked = [p for p in nodes(root) if re.match(r"^Poster: (?!.*\(picked\)$)", p.get("content-desc") or "")]
            picked = [p for p in nodes(root) if re.match(r"^Poster: .*\(picked\)$", p.get("content-desc") or "")]
            if len(picked) < 3 and unpicked:
                tap(unpicked[0])
                time.sleep(1.5)
                continue
        for b in buttons:
            n = find(root, b)
            if n is not None:
                tap(n)
                time.sleep(2)
                break
        else:
            REPORT["problems"].append({"type": "welcome setup: no button to go on", "text": label})
            break
    REPORT["welcome_steps"] = seen
    if not finished:
        REPORT["problems"].append({"type": "welcome setup: never reached Go to Home"})
    if wait_for(HOME_RE, 30) is None:
        REPORT["problems"].append({"type": "welcome setup didn't end on Home"})
    else:
        REPORT["welcome"] = "finished"
        log(f"welcome setup done: {len(seen)} steps")


# Benflix redesign: Home shows a greeting and the four doors
HOME_GREETING_RE = r"^Good (morning|afternoon|evening)|^Happy |^Merry |Día de"
DOORS_RE = HOME_GREETING_RE + r"|^Live TV$"


def main():
    global focus_sweep, monkey
    if SMOKE:
        focus_sweep = lambda *_args, **_kw: None  # noqa: E731
        monkey = lambda *_args, **_kw: None  # noqa: E731
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
    sign_in()
    shot("after-login")
    welcome_setup()
    for name in ["close", "later", "not now", "dismiss", "ok", "skip"]:
        n = find(dump(), rf"^{name}$")
        if n is not None:
            tap(n)
            time.sleep(1)
    # Benflix redesign: Home is a greeting and four doors (Movies, Shows, Live TV, Games)
    t = wait_for(DOORS_RE, 60)
    REPORT["timings"]["home doors visible (s)"] = t
    shot("home")
    if t is None:
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

    # Ember Road (the pixel adventure): walk through a short journey on both TV and phone
    if not out_of_time():
        ember_road()

    # Open each door from Home, and Search
    tabs = [r"^Movies$", r"^Shows$", r"^Live TV$", r"^Games$", r"^Search$"]
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

    # Open a movie's details (inside the Movies door), then play it for a bit
    back_to_home()
    press(r"^Movies$", timeout=8)
    time.sleep(5)
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

    # Settings, from the profile menu (your picture, top right)
    back_to_home()
    if MODE == "tv":
        key("DPAD_UP", 2, wait=0.6)
        key("DPAD_CENTER", wait=1.5)
    else:
        n = find(dump(), r"^Search$")
        if n is not None:
            a, b, c, d = bounds(n)
            sh(f"input tap {c + 60} {(b + d) // 2}")
            time.sleep(1.5)
    press(r"^Settings$", timeout=6)
    time.sleep(2)
    shot("settings")

    # Save the app's log
    with open(f"{OUT}/logcat.txt", "w") as f:
        f.write(adb("logcat", "-d", "-v", "time", timeout=120))
    check_crash("end")
    with open(f"{OUT}/report.json", "w") as f:
        json.dump(REPORT, f, indent=1)
    print(json.dumps({k: REPORT[k] for k in ("timings", "unreachable", "problems")}, indent=1)[:20000])


def find_scrolling(pattern, tries=8):
    """Phone: swipe up until the text shows on screen (TV: the remote scrolls by itself)"""
    for _ in range(tries):
        root = dump()
        n = find(root, pattern)
        if n is not None:
            a, b, c, d = bounds(n)
            if MODE == "tv" or (b > 100 and d < 2200):
                return n
        if MODE == "tv":
            return n
        sh("input swipe 500 1600 500 700 400")
        time.sleep(1.2)
    return None


def tv_focus_down_to(pattern, presses=30, right_of=None):
    """TV: press down until the focused thing holds the text, then OK.
    right_of: a button that sits just left of the one we want (press right from it)"""
    for _ in range(presses):
        root = dump(dismiss=False)
        f = focused_node(root)
        label = find(root, pattern)
        if f is not None and label is not None and contains(f, label):
            key("DPAD_CENTER", wait=1.5)
            return True
        if right_of and f is not None:
            beside = find(root, right_of)
            if beside is not None and contains(f, beside):
                key("DPAD_RIGHT", wait=0.6)
                continue
        key("DPAD_DOWN", wait=0.5)
    return False


def ember_fit(root, name):
    """Log buttons or words that stick out past the left or right edge of the screen"""
    if root is None:
        return
    m = re.search(r"(\d+)x(\d+)", sh("wm size"))
    width = int(m.group(1)) if m else 1080
    if MODE == "tv" and m and int(m.group(1)) < int(m.group(2)):
        width = int(m.group(2))
    for n in nodes(root):
        t = n.get("text") or n.get("content-desc")
        if not t or not n.get("bounds"):
            continue
        a, b, c, d = bounds(n)
        if c > width + 2 or a < -2:
            log(f"EMBER FIT {name}: '{t[:40]}' sticks out ({a}..{c} on a {width} wide screen)")
            REPORT["problems"].append({"type": "Ember Road off screen", "where": name, "text": t[:60]})


def ember_shot(name):
    root = shot(name)
    ember_fit(root, name)
    return root


def ember_road():
    back_to_home()
    if not press(r"^Games$", timeout=6):
        if not press(r"My Benflix", timeout=4) and MODE != "tv":
            # the phone menu bar can be shrunk to icons: My Benflix is the last thing on it
            m = re.search(r"(\d+)x(\d+)", sh("wm size"))
            height = int(m.group(2)) if m else 2400
            bar = [c for c in nodes(dump()) if c.get("clickable") == "true" and center(c)[1] > height - 300]
            if bar:
                sh("input tap %d %d" % center(max(bar, key=lambda c: center(c)[0])))
        time.sleep(2)
        if find_scrolling(r"^Games$") is None or not press(r"^Games$", timeout=6):
            log("Ember Road: no Games page found")
            return
    time.sleep(4)
    if MODE == "tv":
        if not tv_focus_down_to(r"^Ember Road$"):
            log("Ember Road: the remote could not reach the tile")
            REPORT["problems"].append({"type": "could not reach with remote", "text": "Ember Road"})
            return
    else:
        tile = find_scrolling(r"^Ember Road$")
        if tile is None:
            log("Ember Road: tile not found")
            REPORT["problems"].append({"type": "Ember Road tile missing"})
            return
        tap(tile)
    if wait_for(r"New journey|Continue the journey", 20) is None:
        ember_shot("ember-title-missing")
        return
    ember_shot("ember-title")
    press(r"^New journey", timeout=6)
    time.sleep(2)
    if find(dump(), r"Skip, just the TV") is not None:
        ember_shot("ember-phones-lobby")
        press(r"Skip, just the TV", timeout=5)
        time.sleep(2)
    for step in range(4):
        ember_shot(f"ember-setup-{step + 1}")
        target = r"^Set out!$" if step == 3 else r"^Next$"
        if MODE == "tv":
            # the button can sit below the screen edge: walk down to it with the remote like a person would
            if not tv_focus_down_to(target, presses=40, right_of=r"^(Back|Cancel)$"):
                log(f"Ember Road: the remote could not reach {target} on setup step {step + 1}")
                REPORT["problems"].append({"type": "could not reach with remote", "text": target})
                break
        else:
            n = find_scrolling(target)
            if n is None:
                log(f"Ember Road: no {target} on setup step {step + 1}")
                break
            tap(n)
        time.sleep(1.5)
    time.sleep(2)
    ember_shot("ember-road")
    # A few days on the road: keep going, pick the first choice, continue
    for i in range(14):
        if out_of_time():
            break
        root = dump()
        for pattern in (r"^Keep going$", r"^Continue$", r"^Go this way$", r"^Pack up and go on$", r"^Leave$", r"^Done shopping$"):
            n = find(root, pattern)
            if n is not None:
                break
        else:
            n = None
        if n is None:
            ember_shot(f"ember-step-{i}")
            if MODE == "tv":
                f = focused_node(root)
                log("Ember Road: focused on '%s'" % ((f.get("text") or f.get("content-desc") or "?")[:60] if f is not None else "nothing"))
                if f is None:
                    key("DPAD_DOWN", wait=0.8)
                key("DPAD_CENTER", wait=1.5)
            else:
                # an event: tap the first button under the words
                below = [
                    c for c in nodes(root)
                    if c.get("clickable") == "true" and center(c)[1] > 1100
                ]
                if below:
                    sh("input tap %d %d" % center(min(below, key=lambda c: center(c)[1])))
            time.sleep(1.5)
            continue
        if i % 3 == 0:
            ember_shot(f"ember-step-{i}")
        if MODE == "tv":
            # use the remote like a person (a touch tap would put the TV in touch mode and lose the focus)
            if not tv_focus_down_to(pattern, presses=12):
                log(f"Ember Road: the remote could not reach {pattern}")
                REPORT["problems"].append({"type": "could not reach with remote", "text": pattern})
        else:
            tap(n)
        time.sleep(2)
    ember_shot("ember-later")
    # The menu (Back) and the creature book
    key("BACK", wait=1.5)
    ember_shot("ember-menu")
    press(r"Save and go to the start", timeout=6)
    time.sleep(2)
    if press(r"^Creatures ", timeout=6):
        time.sleep(2)
        ember_shot("ember-creature-book")
        key("BACK", wait=1.5)
    key("BACK", wait=1.5)


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
    crashed = [p for p in REPORT["problems"] if p.get("type") == "crash"]
    if SMOKE and crashed:
        print(f"[{MODE}] Benflix CRASHED during the quick check:", flush=True)
        for p in crashed:
            print(f"--- near '{p.get('where')}' ---\n{p.get('trace', '')[:3000]}", flush=True)
        sys.exit(3)
