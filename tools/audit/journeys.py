#!/usr/bin/env python3
"""
Benflix "normal person" robot for the TV (remote only) and the phone (finger only).

drive.py checks every screen opens and every button can be reached. This one instead walks the everyday
trips a person makes and pictures every step, so someone can look at them in order and ask "does this
feel right?": Home -> a door -> a movie -> Play -> Back -> Back, a show and its episodes, Search,
the other doors, the profile menu and Settings, and Back from Home.

At every step it notes what a person would trip over:
  - TV: nothing has the focus (you press a key and nothing seems to happen)
  - TV: after Back, the focus isn't on the poster you came from
  - buttons on top of each other (you'd press the wrong one)
  - phone: buttons smaller than a fingertip (under 40 dp)
  - a page that needs Back pressed more than once to leave

Usage: journeys.py <tv|phone> <package> <results dir>
"""
import json
import os
import re
import sys
import time

sys.path.insert(0, os.path.dirname(__file__))
os.environ.setdefault("SMOKE", "")
import drive as d  # noqa: E402  (signing in, the screen reader, key presses)

MODE, PKG, OUT = d.MODE, d.PKG, d.OUT
J = {"mode": MODE, "steps": [], "problems": []}
d.DEADLINE = time.time() + 40 * 60
DENSITY = [2.625]


def save():
    with open(f"{OUT}/journeys.json", "w") as f:
        json.dump(J, f, indent=1)


def problem(kind, text):
    J["problems"].append({"type": kind, "text": text})
    d.log(f"PROBLEM {kind}: {text}")
    save()


def label(n):
    if n is None:
        return None
    t = n.get("text") or n.get("content-desc") or ""
    if not t:
        for c in n.iter("node"):
            t = c.get("text") or c.get("content-desc") or ""
            if t:
                break
    return t[:80]


def checks(root):
    """Things on this screen a person would trip over"""
    out = {"covered": [], "small": []}
    clickable = [n for n in d.nodes(root) if n.get("clickable") == "true"]
    boxes = [(n, d.bounds(n)) for n in clickable]
    for i, (a, ba) in enumerate(boxes):
        for b, bb in boxes[i + 1:]:
            x1, y1 = max(ba[0], bb[0]), max(ba[1], bb[1])
            x2, y2 = min(ba[2], bb[2]), min(ba[3], bb[3])
            if x2 <= x1 or y2 <= y1:
                continue
            inter = (x2 - x1) * (y2 - y1)
            area_a = (ba[2] - ba[0]) * (ba[3] - ba[1])
            area_b = (bb[2] - bb[0]) * (bb[3] - bb[1])
            small = min(area_a, area_b)
            # one inside the other is a card with a button in it (fine); a partial overlap is not
            inside = inter >= 0.95 * small
            if not inside and small and inter > 0.3 * small:
                out["covered"].append(f"{label(a)} / {label(b)}")
    if MODE == "phone":
        for n, (a, b, c, e) in boxes:
            w, h = (c - a) / DENSITY[0], (e - b) / DENSITY[0]
            if (w < 40 or h < 40) and w > 1 and h > 1:
                out["small"].append(f"{label(n)} ({int(w)}x{int(h)} dp)")
    out["covered"] = sorted(set(out["covered"]))[:20]
    out["small"] = sorted(set(out["small"]))[:20]
    return out


def step(name, **extra):
    root = d.shot("j-" + name)
    f = d.focused_node(root)
    s = {"step": name, "shot": d.shot_no[0], "focused": label(f), "focus_box": f.get("bounds") if f is not None else None}
    if root is not None:
        s.update(checks(root))
        s["texts"] = [t for t in d.texts(root) if t][:40]
    else:
        s["unreadable"] = True
    s.update(extra)
    if MODE == "tv" and root is not None and f is None:
        problem("nothing has the focus", name)
    J["steps"].append(s)
    save()
    return root, s


def open_door(name):
    if not d.open_place(name):
        problem("could not find the door", name)
        return False
    time.sleep(5)
    return True


def first_poster_tv(downs=0, rights=1):
    d.key("DPAD_DOWN", downs, wait=0.6)
    d.key("DPAD_RIGHT", rights, wait=0.6)
    time.sleep(1)
    return label(d.focused_node(d.dump()))


def first_poster_phone():
    root = d.dump()
    # a poster: a clickable thing taller than wide, below the top quarter of the screen
    for n in d.nodes(root):
        if n.get("clickable") != "true":
            continue
        a, b, c, e = d.bounds(n)
        if b > 500 and (e - b) > (c - a) * 1.2 and (c - a) > 150:
            name = label(n)
            d.sh(f"input tap {(a + c) // 2} {(b + e) // 2}")
            return name
    return None


PLAY_RX = r"^Play$|^Resume$|^Play from start$"


def press_play():
    """TV: walk the focus onto Play with the remote and press OK (a tap can land on a
    cast picture instead). Phone: tap it."""
    if MODE != "tv":
        return d.press(PLAY_RX, timeout=6)
    if d.find(d.dump(), PLAY_RX) is None:
        return False
    # going back up the page ends on the Back button, one above Play
    for move in [None, "DPAD_DOWN", "DPAD_LEFT", "DPAD_LEFT", "DPAD_LEFT", "DPAD_DOWN"]:
        if move:
            d.key(move, wait=0.6)
        if re.search(PLAY_RX, label(d.focused_node(d.dump())) or "", re.I):
            d.key("DPAD_CENTER", wait=1.5)
            return True
    return d.press(PLAY_RX, timeout=3)


def keyboard_showing():
    return "mInputShown=true" in d.sh("dumpsys input_method")


def movie_trip(door):
    """A door -> scroll a bit -> open a poster -> look around -> Play -> Back -> Back"""
    if not open_door(door):
        return
    step(f"{door}-door")
    if MODE == "tv":
        picked = first_poster_tv()
        step(f"{door}-poster-focused", picked=picked)
        d.key("DPAD_CENTER", wait=1)
    else:
        d.sh("input swipe 500 1600 500 1000 400")
        time.sleep(1.5)
        step(f"{door}-door-scrolled")
        picked = first_poster_phone()
    t = d.wait_for(r"^Play$|^Resume$|^Episodes$|^Overview$|More like this", 25)
    root, s = step(f"{door}-title-page", opened=picked, seconds=t)
    if t is None:
        problem("title page didn't open (or has no Play/Overview)", f"{door}: {picked}")
    if MODE == "tv":
        # where does the focus land, and what does pressing down walk through?
        for i in range(1, 5):
            d.key("DPAD_DOWN", wait=0.8)
            step(f"{door}-title-down-{i}")
        d.key("DPAD_UP", 6, wait=0.4)
        step(f"{door}-title-back-up")
    else:
        for i in range(1, 3):
            d.sh("input swipe 500 1700 500 700 400")
            time.sleep(1.2)
            step(f"{door}-title-scrolled-{i}")
        d.sh("input swipe 500 600 500 2000 300")
        d.sh("input swipe 500 600 500 2000 300")
        time.sleep(1)
    played = press_play()
    if played:
        time.sleep(10)
        step(f"{door}-playing")
        if MODE == "tv":
            d.key("DPAD_CENTER", wait=1.2)
        else:
            d.sh("input tap 540 1100")
            time.sleep(1)
        step(f"{door}-player-controls")
        if MODE == "tv":
            d.key("DPAD_RIGHT", wait=1)
            step(f"{door}-player-right")
            d.key("DPAD_DOWN", wait=1)
            step(f"{door}-player-down")
        d.key("BACK", wait=2)
        _, s1 = step(f"{door}-player-back-1")
        presses = 1
        while presses < 4 and d.find(d.dump(), r"^Play$|^Resume$|^Overview$|^Episodes$") is None:
            d.key("BACK", wait=2)
            presses += 1
            step(f"{door}-player-back-{presses}")
        if presses > 2:
            problem("Back from the player takes many presses", f"{door}: {presses} presses to get back to the title page")
    else:
        problem("no Play button on the title page's first screen", f"{door}: {picked}")
    d.key("BACK", wait=2)
    root, s = step(f"{door}-back-to-door")
    # after playing, Continue watching reorders itself, so a moved focus isn't a fault then
    if MODE == "tv" and picked and t is not None and not played and s.get("focused") != picked:
        problem("Back doesn't return to the poster you opened", f"{door}: opened '{picked}', focus came back on '{s.get('focused')}'")


def search_trip():
    # Search lives in the menu since the redesign (TV: side menu, phone: bottom bar or More)
    if not d.open_place("Search"):
        problem("could not find Search", "menu")
        return
    time.sleep(3)
    step("search-open")
    root = d.dump()
    edit = [n for n in d.nodes(root) if n.get("class", "").endswith("EditText")]
    if MODE != "tv" and edit:
        d.touch_tap(edit[0])
        time.sleep(1)
    tv_keys = False
    if MODE == "tv":
        # The box says "OK to type": OK on the box opens Glowfin's own letter keys.
        # OK on the microphone beside it starts listening instead, so get onto the box first.
        def on_box():
            f = d.focused_node(d.dump())
            return f is not None and f.get("class", "").endswith("EditText")
        if not on_box():
            problem("Search doesn't open on the search box", "focus: " + str(label(d.focused_node(d.dump()))))
            for _ in range(2):
                d.key("DPAD_RIGHT", wait=0.6)
                if on_box():
                    break
        if on_box():
            d.key("DPAD_CENTER", wait=1.5)
            step("search-keys-open")
            tv_keys = all(d.press(f"^{c}$", timeout=3) for c in "the")
    if not tv_keys:
        d.sh("input text 'the'")
    time.sleep(4)
    root, _ = step("search-typed")
    if d.find(root, r"^the$") is None:
        problem("robot", "could not type in Search, so the search trip was skipped")
        if keyboard_showing():
            d.key("BACK", wait=1)
        return
    if MODE == "tv":
        if tv_keys or keyboard_showing():
            d.key("BACK", wait=1)  # hide the keyboard like a person would
        step("search-keyboard-closed")
        # on TV the results sit to the right of the box and its suggestions
        picked = None
        for _ in range(8):
            d.key("DPAD_RIGHT", wait=0.7)
            f = d.focused_node(d.dump())
            picked = label(f)
            if f is not None and not f.get("class", "").endswith("EditText") and picked and len(picked) > 3:
                break
        step("search-result-focused", picked=picked)
        d.key("DPAD_CENTER", wait=1)
    else:
        if keyboard_showing():
            d.key("BACK", wait=1)
        time.sleep(1)
        step("search-keyboard-closed")
        picked = first_poster_phone()
    time.sleep(4)
    step("search-opened-result", picked=picked)
    d.key("BACK", wait=2)
    root, s = step("search-back")
    if d.find(root, r"^the$") is None:
        problem("Back from a search result forgets what you typed", str(s.get("texts", [])[:8]))


def other_doors():
    for door in ["Live TV", "Music", "Games", "Books"]:
        if d.out_of_time():
            return
        if not open_door(door):
            continue
        name = door.replace(" ", "")
        step(f"{name}-door")
        if MODE == "tv":
            d.key("DPAD_DOWN", 2, wait=0.7)
            step(f"{name}-down-2")
            d.key("DPAD_RIGHT", 2, wait=0.7)
            step(f"{name}-right-2")
        else:
            d.sh("input swipe 500 1700 500 800 400")
            time.sleep(1.5)
            step(f"{name}-scrolled")
        d.key("BACK", wait=2)
        root, s = step(f"{name}-back-1")


def profile_and_settings():
    d.back_to_home()
    if MODE == "tv":
        # Your picture is at the bottom of the side menu since the redesign
        if not d.tv_open_profile():
            problem("could not find the profile picture", "side menu")
    else:
        root = d.dump()
        # the profile picture: the clickable thing furthest right in the top bar
        best = None
        for n in d.nodes(root):
            if n.get("clickable") == "true":
                a, b, c, e = d.bounds(n)
                if e < 400 and (best is None or c > d.bounds(best)[2]):
                    best = n
        if best is not None:
            d.touch_tap(best)
            time.sleep(1.5)
    step("profile-menu")
    if d.press(r"^Settings$", timeout=6):
        time.sleep(2)
        step("settings")
        if MODE == "tv":
            d.key("DPAD_DOWN", 5, wait=0.5)
            step("settings-down-5")
        else:
            d.sh("input swipe 500 1700 500 700 400")
            time.sleep(1)
            step("settings-scrolled")
        d.key("BACK", wait=2)
        step("settings-back-1")
        d.key("BACK", wait=2)
        step("settings-back-2")


def home_walk():
    d.back_to_home()
    root, s = step("home-first-look")
    if MODE == "tv":
        for i in range(1, 7):
            d.key("DPAD_DOWN", wait=0.8)
            step(f"home-down-{i}")
        d.key("DPAD_RIGHT", 3, wait=0.6)
        step("home-right-3")
        d.key("BACK", wait=1.5)
        step("home-back-from-row")
    else:
        for i in range(1, 5):
            d.sh("input swipe 500 1700 500 700 400")
            time.sleep(1.2)
            step(f"home-scroll-{i}")
        d.sh("input swipe 500 600 500 2000 300")


def back_from_home():
    d.back_to_home()
    d.key("BACK", wait=2)
    root, s = step("home-press-back")
    J["back_from_home"] = s.get("texts", [])[:10]
    if not d.app_running():
        J["back_from_home"] = "app closed straight away"


def ensure_app():
    """Back on Home closes the app on a phone (like most apps): open it again so the next trip can start"""
    top = d.sh("dumpsys activity activities | grep -m1 -E 'topResumedActivity|mResumedActivity'")
    if PKG not in top:
        J["steps"].append({"step": "app was closed; opening it again"})
        d.sh(f"am start -W -n {PKG}/com.github.damontecres.wholphin.MainActivity", timeout=90)
        time.sleep(6)
        d.sign_in()


def main():
    dens = d.sh("wm density")
    try:
        DENSITY[0] = int(dens.strip().split()[-1]) / 160
    except (ValueError, IndexError):
        pass
    d.sh("pm grant %s android.permission.POST_NOTIFICATIONS" % PKG)
    d.adb("logcat", "-c")
    d.adb("logcat", "-b", "crash", "-c")
    d.sh(f"am start -W -n {PKG}/com.github.damontecres.wholphin.MainActivity", timeout=90)
    for i in range(3):
        time.sleep(3)
        step(f"start-{i}")
    d.sign_in()
    step("after-sign-in")
    d.welcome_setup()
    for name in ["close", "later", "not now", "dismiss", "ok", "skip"]:
        n = d.find(d.dump(), rf"^{name}$")
        if n is not None:
            d.tap(n)
            time.sleep(1)
    if d.wait_for(d.DOORS_RE, 60) is None:
        step("never-got-to-home")
        problem("robot", "never got to Home")
        return
    for trip in (home_walk, lambda: movie_trip("Movies"), lambda: movie_trip("Shows"), search_trip, other_doors, profile_and_settings, back_from_home):
        if d.out_of_time():
            break
        try:
            ensure_app()
            trip()
        except Exception as e:  # keep going: one broken trip shouldn't hide the rest
            problem("robot error", repr(e)[:300])
    d.check_crash("end")
    J["crashes"] = [p for p in d.REPORT["problems"] if p.get("type") in ("crash", "anr", "system popup")]
    with open(f"{OUT}/logcat.txt", "w") as f:
        f.write(d.adb("logcat", "-d", "-v", "time", timeout=120))


if __name__ == "__main__":
    try:
        main()
    finally:
        save()
        d.save_report()
