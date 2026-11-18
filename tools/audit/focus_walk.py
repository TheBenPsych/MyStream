#!/usr/bin/env python3
"""
Benflix "remote walker" robot (TV only): on every TV page it presses Up, Down, Left and Right from every
button it can reach, and writes down every place where the remote gets stuck.

A "stuck" spot (a focus trap) is: you press an arrow, the focus stays where it was, yet there is something
you could press on the screen in that direction. It also notes when the focus disappears (nothing lit up
after a press), because on a TV that looks the same as being stuck.

Pages: Home, every door (Movies, Shows, Live TV, Games, Music, Books), a movie's page, a show's page with two
seasons (an anime one, like Ben's), the player, Search, Requests, Settings and the profile slide-out.

Results: focus.json (every trap with a picture number) next to the pictures. Exit code 4 when traps were found.
Usage: focus_walk.py tv <package> <results dir>   (Jellyfin test server on 127.0.0.1:8096, see test_server.sh)
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
F = {"pages": {}, "traps": [], "vanished": [], "notes": []}
d.DEADLINE = time.time() + 70 * 60
OPPOSITE = {"DPAD_UP": "DPAD_DOWN", "DPAD_DOWN": "DPAD_UP", "DPAD_LEFT": "DPAD_RIGHT", "DPAD_RIGHT": "DPAD_LEFT"}
ARROWS = ["DPAD_DOWN", "DPAD_RIGHT", "DPAD_UP", "DPAD_LEFT"]
WORD = {"DPAD_UP": "Up", "DPAD_DOWN": "Down", "DPAD_LEFT": "Left", "DPAD_RIGHT": "Right"}
RAIL_PX = [160]  # the tucked side menu is 66 dp; anything left of this is the menu


def save():
    with open(f"{OUT}/focus.json", "w") as f:
        json.dump(F, f, indent=1)


def note(text):
    F["notes"].append(text)
    d.log("NOTE " + text)
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
    return t[:70]


def ident(n):
    """What the focus is on: where it is and what it says (a row that slides keeps the box but changes the words)"""
    if n is None:
        return None
    words = "|".join((c.get("text") or c.get("content-desc") or "") for c in n.iter("node"))[:200]
    return (n.get("bounds"), words)


def snap(wait=0.0):
    if wait:
        time.sleep(wait)
    root = d.dump(dismiss=False)
    return root, d.focused_node(root)


def screen_box(root):
    for n in d.nodes(root):
        return d.bounds(n)
    return (0, 0, 1920, 1080)


def inside(a, b):
    """box a is inside box b"""
    return a[0] >= b[0] and a[1] >= b[1] and a[2] <= b[2] and a[3] <= b[3]


def candidates(root, f, arrow):
    """Things you could press that sit in the direction of the arrow from the focused one"""
    fb = d.bounds(f)
    sx1, sy1, sx2, sy2 = screen_box(root)
    out = []
    for n in d.nodes(root):
        if n.get("focusable") != "true" and n.get("clickable") != "true":
            continue
        if n.get("enabled") == "false":
            continue
        b = d.bounds(n)
        if b[2] - b[0] < 12 or b[3] - b[1] < 12:
            continue
        # on the screen (not slid off the edge)
        if b[0] < sx1 or b[1] < sy1 or b[2] > sx2 or b[3] > sy2:
            continue
        # not the focused thing, something inside it, or something around it (a card and its button)
        if inside(b, fb) or inside(fb, b):
            continue
        # the side menu is only a candidate for Left
        if b[2] <= RAIL_PX[0] and arrow != "DPAD_LEFT":
            continue
        tol = 6
        across = min(fb[3], b[3]) - max(fb[1], b[1]) > 0  # shares some height (for Left/Right)
        if arrow == "DPAD_DOWN" and b[1] >= fb[3] - tol:
            out.append(n)
        elif arrow == "DPAD_UP" and b[3] <= fb[1] + tol:
            out.append(n)
        elif arrow == "DPAD_RIGHT" and b[0] >= fb[2] - tol and across:
            out.append(n)
        elif arrow == "DPAD_LEFT" and b[2] <= fb[0] + tol and across:
            out.append(n)
    return out


def picture(name):
    d.shot("f-" + name)
    return d.shot_no[0]


def walk(page, presses=70, player=False):
    """Presses arrows from every button it can reach on this page, noting where the remote gets stuck"""
    tested = set()
    seen = set()
    traps = 0
    used = 0
    root, f = snap(1.0)
    first = picture(f"{page}-start")
    if f is None:
        root, f = snap(1.5)
    if f is None and not player:
        F["vanished"].append({"page": page, "after": "opening the page", "shot": first})
        d.key("DPAD_DOWN", wait=0.6)
        root, f = snap()
    while used < presses and not d.out_of_time():
        if f is None:
            # nothing lit up: one Down usually wakes it; the player hides its buttons on purpose
            d.key("DPAD_CENTER" if player else "DPAD_DOWN", wait=0.8)
            used += 1
            root, f = snap()
            continue
        here = ident(f)
        fb = d.bounds(f)
        if fb[2] <= RAIL_PX[0] and not player:
            # in the side menu: back to the page (the side menu has its own walk)
            d.key("DPAD_RIGHT", wait=0.7)
            used += 1
            root, f = snap()
            continue
        seen.add(here)
        todo = [a for a in ARROWS if (here, a) not in tested]
        if not todo:
            # everything tried here: go somewhere not seen yet (down first, then right)
            moved = False
            for a in ("DPAD_DOWN", "DPAD_RIGHT", "DPAD_UP"):
                d.key(a, wait=0.5)
                used += 1
                root, f2 = snap()
                if f2 is not None and ident(f2) != here:
                    f = f2
                    moved = True
                    if ident(f2) not in seen:
                        break
            if not moved:
                break
            if ident(f) in seen:
                # walked into places we know: stop early when nearly everything is done
                if all((ident(f), a) in tested for a in ARROWS):
                    break
            continue
        arrow = todo[0]
        tested.add((here, arrow))
        before_root = root
        d.key(arrow, wait=0.6)
        used += 1
        root, f2 = snap()
        if f2 is None:
            # give it a moment (a page that opens on focus, a slow row)
            root, f2 = snap(0.8)
        if f2 is None:
            if not player:
                F["vanished"].append({"page": page, "from": label(f), "pressed": WORD[arrow], "shot": picture(f"{page}-vanished")})
                save()
            d.key(OPPOSITE[arrow], wait=0.6)
            used += 1
            root, f = snap()
            continue
        if ident(f2) == here:
            cand = candidates(before_root, f, arrow)
            if cand:
                traps += 1
                shot = picture(f"{page}-stuck-{WORD[arrow].lower()}")
                trap = {
                    "page": page,
                    "on": label(f),
                    "box": f.get("bounds"),
                    "pressed": WORD[arrow],
                    "could_reach": [label(c) for c in cand][:6],
                    "shot": shot,
                }
                F["traps"].append(trap)
                d.log(f"TRAP {page}: {WORD[arrow]} from '{label(f)}' stays put; could reach {trap['could_reach']}")
                save()
            f = f2
            continue
        # moved: carry on from the new spot (left into the side menu comes back on the next turn)
        f = f2
    F["pages"][page] = {"buttons_visited": len(seen), "presses": used, "traps": traps, "first_shot": first}
    save()
    d.log(f"walk {page}: {len(seen)} spots, {used} presses, {traps} traps")


# ---- getting to each page with the remote ----

HOMES = d.HOME_GREETING_RE + r"|^Hi there!$|^Welcome$"


def ensure_app():
    top = d.sh("dumpsys activity activities | grep -m1 -E 'topResumedActivity|mResumedActivity'")
    if PKG not in top:
        note("app was closed; opening it again")
        d.sh(f"am start -W -n {PKG}/com.github.damontecres.wholphin.MainActivity", timeout=90)
        time.sleep(6)
        d.sign_in()


def no_update_offer():
    """This branch's build is older than the published one, so Benflix offers the update: Not now"""
    root = d.dump()
    if d.find(root, r"^Benflix got better$") is not None and d.find(root, r"^Not now$") is not None:
        d.key("DPAD_RIGHT", wait=0.6)
        d.key("DPAD_CENTER", wait=2)


def go_home():
    ensure_app()
    no_update_offer()
    for _ in range(6):
        if d.find(d.dump(), HOMES) is not None:
            return True
        d.key("BACK", wait=1.2)
    return d.find(d.dump(), HOMES) is not None


def open_rail():
    d.key("DPAD_LEFT", 8, wait=0.2)
    time.sleep(1.2)
    return d.dump()


def rail_go(name, place=None):
    """Side menu -> a door (its panel opens) -> a place in it, or Home/Search/Requests straight away"""
    root = open_rail()
    n = d.find(root, rf"^{re.escape(name)}$")
    if n is None:
        note(f"side menu has no '{name}'")
        d.key("DPAD_RIGHT", wait=0.6)
        return False
    d.tap(n)
    time.sleep(2)
    if place:
        if not d.press(rf"^{re.escape(place)}$", timeout=8):
            note(f"'{name}' panel has no '{place}'")
            d.key("BACK", wait=1)
            return False
        time.sleep(3)
    return True


def open_door(name):
    go_home()
    if rail_go(name, f"{name} home"):
        time.sleep(3)
        return True
    # older panel names: the door's first place
    go_home()
    return rail_go(name)


def open_title(name):
    """Search for a title by name and open it"""
    go_home()
    if not rail_go("Search"):
        return False
    time.sleep(2)
    d.key("DPAD_CENTER", wait=1.5)  # the box says "OK to type"
    d.sh("input keyevent " + " ".join(["DEL"] * 30))
    for w in name.split():
        d.sh(f"input text '{w}'")
        d.sh("input keyevent SPACE")
        time.sleep(0.3)
    if d.wait_for(re.escape(name), 20) is None:
        note(f"search didn't find {name}")
        return False
    d.key("BACK", wait=1)  # hide the keyboard
    time.sleep(1)
    root = d.dump()
    hit = None
    for n in d.nodes(root):
        t = (n.get("text") or "") + " " + (n.get("content-desc") or "")
        if name in t and not n.get("class", "").endswith("EditText") and d.bounds(n)[1] > 250:
            hit = n
            break
    if hit is None:
        note(f"search result for {name} couldn't be picked")
        return False
    d.tap(hit)
    if d.wait_for(r"^Play|^Resume|^Episodes$|^Overview$", 25) is None:
        note(f"{name}: title page didn't open")
        return False
    time.sleep(2)
    return True


def title_tabs(page):
    """A title page: the buttons and tab line, then every tab's content"""
    walk(page, presses=90)
    # every tab: go to the tab line and walk each one (tabs open as you land on them)
    root = d.dump()
    tabs = [n for n in d.nodes(root) if re.match(r"^(Episodes|Overview|More like this|Cast|Parents' guide|Did you know|Characters and voices|Ask for similar|Extras|Collection)$", n.get("text") or "")]
    names = [t.get("text") for t in tabs]
    for name in names[1:4]:
        n = d.find(d.dump(), rf"^{re.escape(name)}$")
        if n is None:
            continue
        d.tap(n)  # walks the focus there (lands = opens on TV); OK does nothing harmful on a tab
        time.sleep(1.5)
        walk(f"{page}-{re.sub(r'[^a-z]+', '-', name.lower())}", presses=30)


def player(name):
    if not d.press(r"^Play|^Resume", timeout=8):
        note(f"{name}: no Play button")
        return
    time.sleep(10)
    d.key("DPAD_CENTER", wait=1.2)
    walk(f"player-{name}", presses=30, player=True)
    for _ in range(3):
        d.key("BACK", wait=2)
        if d.find(d.dump(), r"^Play|^Resume|^Episodes$|^Overview$") is not None:
            break


def profile_panel():
    go_home()
    d.sh("input keyevent --longpress MENU")
    time.sleep(2)
    if d.find(d.dump(), r"Switch profile|Settings") is None:
        # hold didn't take: the profile button at the bottom of the side menu
        open_rail()
        d.key("DPAD_DOWN", 12, wait=0.2)
        d.key("DPAD_CENTER", wait=2)
    walk("profile-panel", presses=30)
    d.key("BACK", wait=1.2)


def settings():
    go_home()
    open_rail()
    d.key("DPAD_DOWN", 12, wait=0.2)
    d.key("DPAD_CENTER", wait=2)
    if not d.press(r"^Settings$", timeout=6):
        note("could not open Settings")
        return
    time.sleep(3)
    walk("settings", presses=60)


def side_menu():
    go_home()
    root = open_rail()
    picture("rail-open")
    # Up/Down along the whole menu, and Right back to the page from each
    seen = set()
    for _ in range(14):
        root, f = snap()
        if f is None:
            break
        seen.add(label(f))
        before = ident(f)
        d.key("DPAD_DOWN", wait=0.5)
        root, f2 = snap()
        if f2 is None or ident(f2) == before:
            break
    F["pages"]["side-menu"] = {"buttons_visited": len(seen), "names": sorted(x for x in seen if x)}
    save()
    d.key("DPAD_RIGHT", wait=0.8)
    root, f = snap()
    if f is not None and d.bounds(f)[2] <= RAIL_PX[0]:
        F["traps"].append({"page": "side-menu", "on": label(f), "pressed": "Right", "could_reach": ["the page"], "shot": picture("rail-stuck-right")})
        save()


def main():
    d.sh("pm grant %s android.permission.POST_NOTIFICATIONS" % PKG)
    d.adb("logcat", "-c")
    d.adb("logcat", "-b", "crash", "-c")
    dens = d.sh("wm density")
    try:
        RAIL_PX[0] = int(80 * int(dens.strip().split()[-1]) / 160)
    except (ValueError, IndexError):
        pass
    d.sh(f"am start -W -n {PKG}/com.github.damontecres.wholphin.MainActivity", timeout=90)
    d.sign_in()
    d.welcome_setup()
    for name in ["close", "later", "not now", "dismiss", "ok", "skip"]:
        n = d.find(d.dump(), rf"^{name}$")
        if n is not None:
            d.tap(n)
            time.sleep(1)
    if d.wait_for(d.DOORS_RE, 60) is None:
        note("never got to Home")
        picture("no-home")
        F["never_home"] = True
        return
    trips = [
        ("home", lambda: (go_home(), walk("home", presses=50))),
        ("side menu", side_menu),
        ("show page (2 seasons, anime)", lambda: open_title("Robot Anime Test") and title_tabs("show-anime")),
        ("show page", lambda: open_title("Test Show") and title_tabs("show")),
        ("movie page", lambda: open_title("Test Pattern One") and (title_tabs("movie"), player("movie"))),
    ]
    for door in ("Movies", "Shows", "Live TV", "Games", "Music", "Books"):
        trips.append((f"{door} door", lambda door=door: open_door(door) and walk(f"door-{door.lower().replace(' ', '-')}", presses=45)))
    trips += [
        ("search", lambda: (go_home(), rail_go("Search"), walk("search", presses=30))),
        ("requests", lambda: (go_home(), rail_go("Requests"), time.sleep(3), walk("requests", presses=45))),
        ("profile panel", profile_panel),
        ("settings", settings),
    ]
    for name, trip in trips:
        if d.out_of_time():
            note(f"ran out of time before {name}")
            break
        d.log(f"=== {name} ===")
        try:
            trip()
        except Exception as e:  # one broken trip shouldn't hide the rest
            note(f"robot error in {name}: {repr(e)[:300]}")
    d.check_crash("end")
    F["crashes"] = [p for p in d.REPORT["problems"] if p.get("type") in ("crash", "anr")]


if __name__ == "__main__":
    try:
        main()
    finally:
        save()
        d.save_report()
    print("\n===== Where the remote gets stuck =====", flush=True)
    for t in F["traps"]:
        print(f"- {t['page']}: {t['pressed']} from '{t['on']}' does nothing (could reach {t['could_reach'][:3]}) [picture {t.get('shot')}]")
    for v in F["vanished"]:
        print(f"- {v['page']}: the focus disappeared after {v.get('pressed', v.get('after'))} from '{v.get('from')}' [picture {v.get('shot')}]")
    print(f"{len(F['traps'])} stuck spots, {len(F['vanished'])} lost-focus spots, on {len(F['pages'])} pages", flush=True)
    if F.get("never_home"):
        print("The robot never got to Home, so nothing was walked", flush=True)
        sys.exit(5)
    sys.exit(4 if F["traps"] else 0)
