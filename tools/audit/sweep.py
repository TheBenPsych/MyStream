#!/usr/bin/env python3
"""
Benflix deep sweep robot (TV and phone). Uses the same helpers as drive.py, but instead of a fixed
walk it opens every door, Search, the profile menu and Settings, and presses EVERY button it can
find on each of them (and one level further in for menus and Settings pages), like a person who
tries everything once. For each press it notes:
  - crashes and "isn't responding"
  - error words that appeared on the screen after the press
  - presses that did nothing at all (the screen stayed exactly the same)
  - presses that dropped you out of Benflix
  - (TV) buttons the remote can't reach
Results: <out>/sweep-report.json plus a screenshot of every problem.

Usage: sweep.py <tv|phone> <package> <results dir>
"""
import json
import os
import re
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import drive as d  # noqa: E402  (reads the same command line: mode, package, results dir)

MODE, PKG, OUT = d.MODE, d.PKG, d.OUT
MINUTES = float(os.environ.get("SWEEP_MINUTES", "80"))
END = time.time() + MINUTES * 60
d.DEADLINE = END + 3600  # drive's own clock: don't let it stop us early

R = {"mode": MODE, "screens": {}, "problems": [], "pressed": 0, "steps": []}
d.REPORT = R  # drive's helpers log into the same report
R.setdefault("timings", {})
R.setdefault("unreachable", {})


def save():
    with open(f"{OUT}/sweep-report.json", "w") as f:
        json.dump(R, f, indent=1)


def problem(kind, where, button="", text="", shoot=True):
    p = {"type": kind, "where": where, "button": button, "text": text[:400]}
    if shoot:
        d.shot_no[0] += 1
        name = f"{d.shot_no[0]:03d}-problem-{re.sub(r'[^a-z0-9]+', '-', (where + '-' + button).lower())[:50]}.png"
        with open(f"{OUT}/{name}", "wb") as f:
            import subprocess

            f.write(subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True).stdout)
        p["shot"] = name
    R["problems"].append(p)
    print(f"[{MODE}] PROBLEM {kind}: {where} → {button} {text[:160]}", flush=True)
    save()


# Never press these on the robot's demo account
DANGER = re.compile(
    r"sign out|log ?out|switch (profile|user)|change server|delete|remove|forget|clear|reset|restart|shut ?down|"
    r"install|uninstall|update now|turn off|\bpin\b|\block\b|bedtime|kids mode|lite mode|language|\bexit\b|\bquit\b|"
    r"approve|deny|decline|\bblock\b|\bsend\b|ask for it|^request( it| this| again)?$|factory|wipe|erase",
    re.I,
)
# Words that mean something went wrong (only counted when they appear because of the press)
ERROR_WORDS = re.compile(
    r"went wrong|something broke|couldn't (load|open|play|reach|connect|start)|could not|can't (load|play|open|reach)|"
    r"\berror\b|exception|\bnull\b|undefined|\bNaN\b|failed|not available right now",
    re.I,
)
CLOCK = re.compile(r"^\d{1,2}:\d{2}( ?[AP]M)?$|^[AP]M$")


def label_of(n):
    t = (n.get("text") or n.get("content-desc") or "").strip()
    if t:
        return t
    for c in n.iter("node"):
        t = (c.get("text") or c.get("content-desc") or "").strip()
        if t:
            return t
    return ""


def screen_texts(root):
    return {t for t in d.texts(root) if t and not CLOCK.match(t)}


def in_app():
    top = d.sh("dumpsys activity activities | grep -m1 -E 'topResumedActivity|mResumedActivity'")
    return PKG in top, top.strip()[:160]


def relaunch():
    d.sh(f"am start -n {PKG}/com.github.damontecres.wholphin.MainActivity")
    time.sleep(5)
    root = d.dump()
    if d.find(root, r"Who's watching") is not None:
        d.sign_in()


def home():
    ok, _ = in_app()
    if not ok:
        relaunch()
    for _ in range(7):
        root = d.dump()
        if d.find(root, d.HOME_GREETING_RE) is not None:
            return True
        if d.find(root, r"Who's watching") is not None:
            d.sign_in()
            continue
        d.key("BACK", wait=1.2)
    relaunch()
    return d.find(d.dump(), d.HOME_GREETING_RE) is not None


def open_profile_menu():
    if MODE == "tv":
        d.key("DPAD_UP", 3, wait=0.5)
        # the profile picture is the right-most thing on the top bar
        d.key("DPAD_RIGHT", 6, wait=0.4)
        d.key("DPAD_CENTER", wait=1.5)
    else:
        n = d.find(d.dump(), r"^Search$")
        if n is None:
            return False
        a, b, c, e = d.bounds(n)
        d.sh(f"input tap {c + 60} {(b + e) // 2}")
        time.sleep(1.5)
    return True


def go(path):
    """Opens a screen from Home. path is a list of labels to press in turn ('@me' = the profile picture)"""
    if not home():
        return False
    for step in path:
        if step == "@me":
            if not open_profile_menu():
                return False
            continue
        n = d.find(d.dump(), rf"^{re.escape(step)}$") if MODE == "tv" else find_scrolling(rf"^{re.escape(step)}$")
        if n is None:
            n = d.find(d.dump(), re.escape(step))
        if n is None:
            return False
        d.tap(n)
        time.sleep(3)
    return True


def find_scrolling(pattern, tries=6):
    for i in range(tries):
        n = d.find(d.dump(), pattern)
        if n is not None:
            return n
        d.sh("input swipe 540 1700 540 900 350")
        time.sleep(1)
    return None


def judge(where, button, before, after_root, before_focus=None):
    """What happened after a press"""
    R["pressed"] += 1
    d.check_crash(f"{where} → {button}")
    for p in R["problems"][:]:
        if p.get("type") == "crash" and not p.get("reported"):
            p["reported"] = True
            p["button"] = button
    ok, top = in_app()
    if not ok:
        if re.search(r"youtube|browser|chrome|webview|launcher|settings", top, re.I):
            R.setdefault("left_app", []).append(f"{where} → {button}: {top}")
        else:
            problem("press dropped out of Benflix", where, button, top)
        return "left"
    after = screen_texts(after_root)
    if not after:
        return "unreadable"
    new = after - before
    bad = [t for t in new if ERROR_WORDS.search(t)]
    if bad:
        problem("error words after press", where, button, " | ".join(bad)[:400])
    if after == before:
        if MODE == "tv":
            f = d.focused_node(after_root)
            if before_focus is not None and f is not None and label_of(f) != before_focus:
                return "focus moved"
        problem("press did nothing", where, button)
        return "nothing"
    return "changed"


def poster_like(n):
    a, b, c, e = d.bounds(n)
    w, h = c - a, e - b
    return h > 1.25 * w and w > 120


def sweep_phone(name, path, depth, queue, seen_screens):
    if not go(path):
        R["screens"][name] = "could not open"
        return
    time.sleep(2)
    d.shot(f"sweep-{name}")
    # collect every button, scrolling down the page
    targets, seen, scroll = [], set(), 0
    last = None
    posters = 0
    for scroll in range(7):
        root = d.dump()
        if root is None:
            break
        sig = tuple(sorted(screen_texts(root)))
        if sig == last:
            break
        last = sig
        for n in d.nodes(root):
            if n.get("clickable") != "true" or n.get("enabled") == "false":
                continue
            lab = label_of(n)
            if not lab or lab in seen or DANGER.search(lab):
                continue
            seen.add(lab)
            if poster_like(n):
                posters += 1
                if posters > 4:
                    continue
            targets.append((lab, scroll))
        d.sh("input swipe 540 1700 540 900 350")
        time.sleep(1.2)
    targets = targets[:45]
    R["screens"][name] = {"buttons": [t[0] for t in targets]}
    print(f"[{MODE}] {name}: {len(targets)} buttons", flush=True)
    save()
    fresh = False
    for lab, scroll in targets:
        if time.time() > END:
            return
        root = d.dump()
        n = d.find(root, rf"^{re.escape(lab)}$")
        if n is None or not fresh:
            if not go(path):
                continue
            time.sleep(1.5)
            for _ in range(scroll):
                d.sh("input swipe 540 1700 540 900 350")
                time.sleep(1)
            root = d.dump()
            n = d.find(root, rf"^{re.escape(lab)}$")
            if n is None:
                for _ in range(2):
                    d.sh("input swipe 540 1700 540 900 350")
                    time.sleep(1)
                    root = d.dump()
                    n = d.find(root, rf"^{re.escape(lab)}$")
                    if n is not None:
                        break
            if n is None:
                continue
        before = screen_texts(root)
        d.tap(n)
        time.sleep(2.5)
        after_root = d.dump()
        res = judge(name, lab, before, after_root)
        fresh = res == "nothing"
        if res == "changed" and depth < 2 and len(seen_screens) < 40:
            # a new screen or pop-up: sweep it too (one level deeper)
            sig = frozenset(list(screen_texts(after_root))[:30])
            heading = next((t for t in d.texts(after_root) if t and len(t) < 40 and t not in before), lab)
            key = name + " > " + heading
            if key not in seen_screens and len(screen_texts(after_root) - before) > 3:
                seen_screens.add(key)
                queue.append((key, path + [lab], depth + 1))
        if res != "nothing":
            d.key("BACK", wait=1.5)
            back = d.dump()
            fresh = d.find(back, rf"^{re.escape(lab)}$") is not None
        save()


def tv_focus_label(root):
    f = d.focused_node(root)
    return (label_of(f) if f is not None else ""), f


def sweep_tv(name, path, depth, queue, seen_screens):
    """Walks the remote over the screen row by row; at every new button: OK, look, Back"""
    if not go(path):
        R["screens"][name] = "could not open"
        return
    time.sleep(2.5)
    d.shot(f"sweep-{name}")
    tested, keys, pressed_here = set(), [], []
    reached = set()
    same_down = 0
    per_row = 0
    direction = "DPAD_RIGHT"
    for step in range(140):
        if time.time() > END or len(pressed_here) >= 40:
            break
        root = d.dump(dismiss=False)
        lab, f = tv_focus_label(root)
        if lab:
            reached.add(lab)
        if lab and lab not in tested and not DANGER.search(lab) and per_row < 4:
            tested.add(lab)
            per_row += 1
            before = screen_texts(root)
            d.key("DPAD_CENTER", wait=2.5)
            after_root = d.dump()
            res = judge(name, lab, before, after_root, before_focus=lab)
            pressed_here.append(lab)
            if res == "changed" and depth < 2 and len(seen_screens) < 40:
                heading = next((t for t in d.texts(after_root) if t and len(t) < 40 and t not in before), lab)
                k = name + " > " + heading
                if k not in seen_screens and len(screen_texts(after_root) - before) > 3:
                    seen_screens.add(k)
                    queue.append((k, path + [lab], depth + 1))
            if res in ("changed", "left", "unreadable"):
                d.key("BACK", wait=1.5)
                back = d.dump(dismiss=False)
                blab, _ = tv_focus_label(back)
                if blab != lab:
                    # didn't come back to the same spot: open the screen again and replay the remote presses
                    if not go(path):
                        break
                    time.sleep(2)
                    for k2 in keys:
                        d.key(k2, wait=0.35)
                    time.sleep(1)
            elif res == "focus moved":
                # OK moved the focus (e.g. a row header): carry on from where it is
                pass
            continue
        # move on: along the row, then down a row
        before_lab = lab
        d.key(direction, wait=0.45)
        keys.append(direction)
        lab2, _ = tv_focus_label(d.dump(dismiss=False))
        if lab2 == before_lab or (lab2 in tested and per_row >= 4):
            d.key("DPAD_DOWN", wait=0.6)
            keys.append("DPAD_DOWN")
            per_row = 0
            lab3, _ = tv_focus_label(d.dump(dismiss=False))
            if lab3 == lab2:
                same_down += 1
                if same_down >= 2:
                    break
            else:
                same_down = 0
    R["screens"][name] = {"pressed": pressed_here, "reached": sorted(reached)}
    print(f"[{MODE}] {name}: pressed {len(pressed_here)}", flush=True)
    save()


def main():
    d.sh("pm grant %s android.permission.POST_NOTIFICATIONS" % PKG)
    d.adb("logcat", "-c")
    d.adb("logcat", "-b", "crash", "-c")
    d.sh(f"am start -W -n {PKG}/com.github.damontecres.wholphin.MainActivity", timeout=90)
    time.sleep(8)
    d.sign_in()
    d.welcome_setup()
    if d.wait_for(d.DOORS_RE, 60) is None:
        problem("robot could not sign in", "start")
        return
    d.shot("sweep-home")
    queue = [
        ("Home", [], 1),
        ("Movies", ["Movies"], 1),
        ("Shows", ["Shows"], 1),
        ("Live TV", ["Live TV"], 1),
        ("Games", ["Games"], 1),
        ("Music", ["Music"], 1),
        ("Books", ["Books"], 1),
        ("Search", ["Search"], 1),
        ("Profile menu", ["@me"], 1),
        ("Settings", ["@me", "Settings"], 1),
    ]
    seen_screens = {q[0] for q in queue}
    sweep = sweep_tv if MODE == "tv" else sweep_phone
    # the main screens first, then the screens found inside them
    while queue and time.time() < END:
        name, path, depth = queue.pop(0)
        print(f"[{MODE}] === {name} ({len(queue)} more waiting)", flush=True)
        try:
            sweep(name, path, depth, queue, seen_screens)
        except Exception as e:  # keep going with the next screen
            problem("robot error", name, text=repr(e), shoot=False)
            d.reconnect()
    R["not_reached_in_time"] = [q[0] for q in queue]
    try:
        log = d.adb("logcat", "-d", "-b", "main", "--pid", d.sh(f"pidof {PKG}").strip() or "0", timeout=60)
        with open(f"{OUT}/sweep-app-log.txt", "w") as f:
            f.write(log[-400000:])
    except Exception:
        pass


if __name__ == "__main__":
    try:
        main()
    finally:
        save()
    print(f"[{MODE}] sweep done: {R['pressed']} presses, {len(R['problems'])} problems", flush=True)
