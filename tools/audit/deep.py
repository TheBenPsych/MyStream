#!/usr/bin/env python3
"""
Benflix "deep check" robot for the TV (remote only) and the phone (finger only), against a private test
Jellyfin made by test_server.sh on the same GitHub computer (so nothing on Tower is touched).

Passes (each takes pictures and writes what it found to deep.json):
  1. Tricky files: plays each test video (1080p two languages, 4K HDR HEVC + 5.1, Dolby E-AC3 + styled
     subtitles, a kids cartoon) and asks the server whether it is really playing, how (direct or
     converted) and whether the time moves on.
  2. Sound and subtitles: switches to Spanish sound and subtitles in the player's Settings, checks
     the server sees the switch.
  3. Resume: after watching a while and going back, the title page should offer Resume.
  4. Next episode: an episode ends by itself and the next one should start.
  5. Bad Wi-Fi: Tower "freezes" (docker pause) in the middle of a movie, then comes back; and Tower
     switched off (docker stop) while browsing, then on again.
  6. Kids and Guest profiles: Kids should only find the G-rated cartoon; Guest shouldn't save progress.
  7. Easy read (bigger words) turned on: Home, a door and a title page, looking for cut-off words.
  8. Speed: seconds for each door, title page and the player to show.

Usage: deep.py <tv|phone> <package> <results dir>   (Jellyfin on 127.0.0.1:8096 for the server checks)
"""
import json
import os
import re
import subprocess
import sys
import time
import urllib.request

sys.path.insert(0, os.path.dirname(__file__))
os.environ.setdefault("SMOKE", "")
import drive as d  # noqa: E402
import journeys as j  # noqa: E402  (step pictures + the trip-over checks)

MODE, PKG, OUT = d.MODE, d.PKG, d.OUT
J = j.J
J["deep"] = {"files": {}, "timings": {}, "notes": []}
DEEP = J["deep"]
d.DEADLINE = time.time() + 55 * 60
JF = "http://127.0.0.1:8096"
AUTH = 'MediaBrowser Client="deep-robot", Device="runner", DeviceId="deep-robot", Version="1"'
TOKEN = [None]
TITLES = ["Test Pattern One", "Big Color Test", "Surround Test", "Kids Cartoon Test"]


def note(text):
    DEEP["notes"].append(text)
    d.log("NOTE " + text)
    j.save()


def api(path, method="GET", body=None):
    if TOKEN[0] is None and not path.startswith("/Users/AuthenticateByName"):
        r = api("/Users/AuthenticateByName", "POST", {"Username": "demo", "Pw": ""})
        TOKEN[0] = r["AccessToken"]
        DEEP["user_id"] = r["User"]["Id"]
    req = urllib.request.Request(JF + path, method=method, data=json.dumps(body).encode() if body is not None else None)
    req.add_header("Content-Type", "application/json")
    req.add_header("X-Emby-Authorization", AUTH + (f', Token="{TOKEN[0]}"' if TOKEN[0] else ""))
    try:
        with urllib.request.urlopen(req, timeout=15) as r:
            data = r.read()
            return json.loads(data) if data else {}
    except Exception as e:  # the server paused/stopped on purpose, or a hiccup
        return {"error": repr(e)[:200]}


def app_session():
    """The app's playing session on the server, or None"""
    s = api("/Sessions?ActiveWithinSeconds=60")
    if not isinstance(s, list):
        return None
    playing = [x for x in s if x.get("DeviceId") != "deep-robot" and x.get("NowPlayingItem")]
    # the newest one (an older stopped video can linger for a moment)
    playing.sort(key=lambda x: x.get("LastPlaybackCheckIn") or x.get("LastActivityDate") or "", reverse=True)
    return playing[0] if playing else None


def play_info():
    s = app_session()
    if s is None:
        return None
    ps = s.get("PlayState", {})
    t = s.get("TranscodingInfo") or {}
    return {
        "item": s["NowPlayingItem"].get("Name"),
        "seconds": round(ps.get("PositionTicks", 0) / 1e7, 1),
        "paused": ps.get("IsPaused"),
        "method": ps.get("PlayMethod"),
        "audio_index": ps.get("AudioStreamIndex"),
        "subtitle_index": ps.get("SubtitleStreamIndex"),
        "converting_because": t.get("TranscodeReasons"),
        "video": t.get("VideoCodec"),
        "audio": t.get("AudioCodec"),
    }


def item_id(name):
    r = api(f"/Items?Recursive=true&SearchTerm={urllib.request.quote(name)}&IncludeItemTypes=Movie,Episode,Series&Fields=UserData")
    for it in r.get("Items", []) if isinstance(r, dict) else []:
        if it.get("Name") == name:
            return it
    return None


def docker(*args):
    r = subprocess.run(["docker", *args], capture_output=True, text=True, timeout=120)
    d.log(f"docker {' '.join(args)}: {r.returncode} {r.stderr.strip()[:120]}")
    return r.returncode == 0


HOMES = d.HOME_GREETING_RE + r"|^Hi there!$|^Welcome!?$"


def go_home():
    for _ in range(6):
        if d.find(d.dump(), HOMES) is not None:
            return
        d.key("BACK", wait=1)
    if d.find(d.dump(), HOMES) is None:
        j.ensure_app()


def open_title(name):
    """Search for a title by name and open it, like a person would. Returns seconds for the title page"""
    go_home()
    if not d.press(r"^Search$", timeout=8):
        j.problem("could not find Search", "Home")
        return None
    time.sleep(2)
    root = d.dump()
    edit = [n for n in d.nodes(root) if n.get("class", "").endswith("EditText")]
    if MODE != "tv" and edit:
        d.touch_tap(edit[0])
        time.sleep(1)
    elif MODE == "tv":
        d.key("DPAD_CENTER", wait=1.5)  # TV: the box says "OK to type"
    d.sh("input keyevent " + " ".join(["DEL"] * 30))
    for w in name.split():
        d.sh(f"input text '{w}'")
        d.sh("input keyevent SPACE")
        time.sleep(0.3)
    if d.wait_for(re.escape(name), 20) is None:
        j.step(f"search-{name}")
        j.problem("search didn't find a title that is on the server", name)
        return None
    d.key("BACK", wait=1)  # hide the keyboard
    time.sleep(1)
    root = d.dump()
    hit = None
    for n in d.nodes(root):
        t = (n.get("text") or "") + " " + (n.get("content-desc") or "")
        if name in t and not n.get("class", "").endswith("EditText"):
            a, b, c, e = d.bounds(n)
            if b > 250:  # not the search box at the top
                hit = n
                break
    if hit is None:
        j.problem("search result couldn't be picked", name)
        return None
    start = time.time()
    d.tap(hit)
    t = d.wait_for(r"^Play$|^Resume$|^Play from start$|^Episodes$", 25)
    if t is None:
        j.step(f"title-{name}")
        j.problem("title page didn't open from Search", name)
        return None
    secs = round(time.time() - start, 1)
    DEEP["timings"][f"title page: {name}"] = secs
    return secs


def start_playing(label, button=r"^Play$|^Resume$|^Play from start$|^Play S\d", server=True):
    """Press Play and time until the server says the video is moving"""
    if not d.press(button, timeout=8):
        j.problem("no Play button", label)
        return None
    if not server:  # Guest tells the server nothing, on purpose
        return 0
    start = time.time()
    first = None
    while time.time() - start < 45:
        p = play_info()
        if p and p["seconds"] > 0.5:
            first = round(time.time() - start, 1)
            break
        time.sleep(1)
    DEEP["timings"][f"press Play to picture moving: {label}"] = first
    if first is None:
        j.step(f"{label}-not-playing")
        j.problem("the video never started playing", f"{label}: {play_info()}")
    return first


def watch(label, seconds=12):
    """Is the time moving on? (two looks at the server, [seconds] apart)"""
    a = play_info()
    time.sleep(seconds)
    b = play_info()
    moved = bool(a and b and b["seconds"] - a["seconds"] > seconds * 0.5)
    return a, b, moved


def tap_player():
    if MODE == "tv":
        d.key("DPAD_CENTER", wait=1.2)
        return
    root = d.dump()
    nodes = d.nodes(root)
    if nodes:
        a, b, c, e = d.bounds(nodes[0])
        d.sh(f"input tap {(a + c) // 2} {(b + e) // 3}")
    time.sleep(1)


def leave_player():
    for _ in range(4):
        if d.find(d.dump(), r"^Play$|^Resume$|^Play from start$|^Episodes$|^Overview$") is not None:
            return
        d.key("BACK", wait=2)


def tricky_files():
    for name in TITLES:
        if d.out_of_time():
            return
        info = {"title page seconds": open_title(name)}
        DEEP["files"][name] = info
        if info["title page seconds"] is None:
            continue
        j.step(f"file-{name}-title")
        info["seconds to start"] = start_playing(name)
        time.sleep(4)
        j.step(f"file-{name}-playing")
        a, b, moved = watch(name)
        info.update({"first look": a, "second look": b, "time moves on": moved})
        if info["seconds to start"] is not None and not moved:
            j.problem("video stuck (time not moving)", f"{name}: {a} -> {b}")
        on_screen = " | ".join(t for t in d.texts(d.dump()) if t)
        if re.search(r"can't play|cannot play|error|went wrong|not supported", on_screen, re.I):
            j.problem("error on the player", f"{name}: {on_screen[:200]}")
        j.save()
        leave_player()
        j.step(f"file-{name}-after-back")


def sound_and_subtitles():
    if open_title("Test Pattern One") is None:
        return
    if start_playing("Test Pattern One (switching)") is None:
        return
    time.sleep(3)
    before = play_info()
    # show the controls, then Settings > Audio > Spanish
    # the controls hide again after a few seconds, so press Settings straight from the same look
    opened = False
    for _ in range(3):
        tap_player()
        n = d.find(d.dump(), r"^Settings$")
        if n is not None:
            d.tap(n)
            opened = True
            break
    j.step("switch-controls")
    if not opened:
        j.problem("no Settings button on the player controls", "Test Pattern One")
        leave_player()
        return
    time.sleep(2)
    j.step("switch-settings-panel")
    if d.press(r"^Audio", timeout=5):
        time.sleep(1)
        j.step("switch-audio-list")
        if d.press(r"Spanish|Español|spa", timeout=5):
            time.sleep(6)
            j.step("switch-audio-spanish")
    after_audio = play_info()
    # subtitles: the player's own Subtitles button
    subs_open = False
    for _ in range(3):
        tap_player()
        n = d.find(d.dump(), r"^Subtitles$")
        if n is not None:
            d.tap(n)
            subs_open = True
            break
    if subs_open:
        time.sleep(1)
        j.step("switch-subtitle-list")
        if d.press(r"Spanish|Español|spa", timeout=5):
            time.sleep(12)
            j.step("switch-subtitles-spanish")
    after_subs = play_info()
    DEEP["switching"] = {"before": before, "after sound": after_audio, "after subtitles": after_subs}
    if before and after_audio and before["audio_index"] == after_audio["audio_index"]:
        j.problem("switching the sound to Spanish didn't change it on the server", f"{before} -> {after_audio}")
    if before and after_subs and before["subtitle_index"] == after_subs["subtitle_index"]:
        j.problem("switching subtitles to Spanish didn't change them on the server", f"{before} -> {after_subs}")
    _, _, moved = watch("after switching", 8)
    if not moved:
        j.problem("video stuck after switching sound/subtitles", str(play_info()))
    leave_player()


def resume_check():
    if open_title("Test Pattern One") is None:
        return
    start_playing("Test Pattern One (resume)", r"^Resume$|^Play$")
    time.sleep(30)
    leave_player()
    time.sleep(3)
    root, s = j.step("resume-title-page")
    it = item_id("Test Pattern One") or {}
    pos = round((it.get("UserData") or {}).get("PlaybackPositionTicks", 0) / 1e7)
    DEEP["resume"] = {"server saved seconds": pos, "Resume on screen": d.find(root, r"^Resume") is not None}
    if pos < 10:
        j.problem("watching a while didn't save the place on the server", f"saved {pos}s")
    elif d.find(root, r"^Resume") is None:
        j.problem("title page shows no Resume after watching part of it", str(s.get("texts", [])[:12]))
    # going back in from Home: Continue watching?
    go_home()
    root, s = j.step("resume-home")
    DEEP["resume"]["Continue watching on Home"] = d.find(root, r"Continue watching|Keep watching|Test Pattern One") is not None


def next_episode():
    if open_title("Test Show") is None:
        return
    j.step("show-page")
    # Play on a show page starts the first unwatched episode (episode 1 here)
    if start_playing("Test Show") is None:
        return
    first = play_info()
    end = time.time() + 150
    seen = []
    while time.time() < end:
        p = play_info()
        name = p["item"] if p else None
        if name and name not in seen:
            seen.append(name)
        if name and first and name != first["item"]:
            break
        if p and p["seconds"] > 60 and len(seen) == 1 and "countdown" not in DEEP:
            DEEP["countdown"] = True
            j.step("episode-ending")
        time.sleep(3)
    j.step("episode-after-end")
    DEEP["next episode"] = {"played in order": seen}
    if len(seen) < 2:
        j.problem("the next episode didn't start by itself", f"saw {seen}")
    leave_player()


def bad_wifi():
    """Tower freezes for 45 s in the middle of a movie, then comes back"""
    if open_title("Test Pattern One") is None:
        return
    if start_playing("Test Pattern One (bad Wi-Fi)", r"^Resume$|^Play$") is None:
        return
    time.sleep(5)
    j.step("wifi-before")
    docker("pause", "jellyfin")
    t0 = time.time()
    for at in (8, 25, 45):
        time.sleep(max(0, at - (time.time() - t0)))
        j.step(f"wifi-frozen-{at}s")
    docker("unpause", "jellyfin")
    back = None
    t1 = time.time()
    while time.time() - t1 < 60:
        p = play_info()
        if p:
            time.sleep(5)
            q = play_info()
            if q and q["seconds"] > p["seconds"] + 2:
                back = round(time.time() - t1, 1)
                break
        time.sleep(2)
    j.step("wifi-back")
    DEEP["bad wifi"] = {"seconds to play again after Tower came back": back}
    if back is None:
        j.problem("after Tower came back, the movie didn't carry on by itself", str(play_info()))
    leave_player()

    # Tower switched off while browsing: open Movies, then just wait like a person would
    go_home()
    d.press(r"^Movies$", timeout=6)
    time.sleep(4)
    docker("stop", "jellyfin")
    d.key("BACK", wait=2)
    d.press(r"^Shows$", timeout=6)
    for at in (8, 30):
        time.sleep(at - (8 if at == 30 else 0))
        j.step(f"tower-off-{at}s")
    docker("start", "jellyfin")
    for _ in range(60):
        if "Version" in json.dumps(api("/System/Info/Public")):
            break
        time.sleep(2)
    TOKEN[0] = None
    t0 = time.time()
    t = None
    while time.time() - t0 < 60:
        root = d.dump()
        if d.find(root, r"taking a nap|being worked on|Checking again") is None and d.texts(root):
            t = round(time.time() - t0, 1)
            break
        time.sleep(2)
    root, s = j.step("tower-back-on")
    landed = " | ".join(x for x in d.texts(root) if x)[:200]
    DEEP["tower off"] = {"seconds to leave the nap screen after Tower came back": t, "landed on": landed}
    if t is None:
        j.problem("still on the nap screen a minute after Tower came back", "")
    elif d.find(root, r"Who's watching") is not None:
        j.problem("after Tower came back, the app went to Who's watching instead of where you were", landed)


def switch_profile(tile):
    """Profile picture -> Who's watching -> [tile]"""
    go_home()
    if MODE == "tv":
        d.key("DPAD_UP", 4, wait=0.5)
        d.key("DPAD_RIGHT", 2, wait=0.4)
        d.key("DPAD_CENTER", wait=1.5)
    else:
        root = d.dump()
        best = None
        for n in d.nodes(root):
            if n.get("clickable") == "true":
                a, b, c, e = d.bounds(n)
                if e < 400 and (best is None or c > d.bounds(best)[2]):
                    best = n
        if best is not None:
            d.touch_tap(best)
            time.sleep(1.5)
    d.press(r"^Switch profile$|^My (?:Benflix|Glowfin)$", timeout=4)
    if d.find(d.dump(), r"Who's watching") is None:
        d.press(r"^Switch profile$", timeout=4)
    if d.wait_for(r"Who's watching", 15) is None:
        j.step(f"who-{tile}-missing")
        j.problem("couldn't get to Who's watching", tile)
        return False
    j.step(f"who-for-{tile}")
    if not d.press(rf"^{tile}$", timeout=6):
        j.problem("tile missing on Who's watching", tile)
        return False
    time.sleep(3)
    for b in (r"^Got it$", r"^OK$", r"^Close$"):
        n = d.find(d.dump(), b)
        if n is not None:
            d.tap(n)
    if d.wait_for(d.HOME_GREETING_RE + r"|^Hi there!$|^Welcome!?$", 30) is None:
        j.step(f"{tile}-no-home")
        j.problem("profile didn't reach Home", tile)
        return False
    return True


def kids_and_guest():
    if switch_profile("Kids"):
        j.step("kids-home")
        d.press(r"^Movies$", timeout=6)
        time.sleep(6)
        root, s = j.step("kids-movies")
        seen = " | ".join(s.get("texts", []))
        bad = [t for t in TITLES[:3] if t in seen]
        DEEP["kids"] = {"grown-up titles seen in Kids Movies": bad, "cartoon seen": "Kids Cartoon Test" in seen}
        if bad:
            j.problem("Kids can see grown-up movies", ", ".join(bad))
        go_home()
        # Search too
        if d.press(r"^Search$", timeout=6):
            time.sleep(2)
            edit = [n for n in d.nodes(d.dump()) if n.get("class", "").endswith("EditText")]
            if MODE != "tv" and edit:
                d.touch_tap(edit[0])
                time.sleep(1)
            d.sh("input text 'Test'")
            time.sleep(6)
            root, s = j.step("kids-search")
            # (Test Show is TV-PG, which Kids may watch)
            bad = [t for t in TITLES[:3] if d.find(root, re.escape(t)) is not None]
            DEEP["kids"]["grown-up titles in Kids Search"] = bad
            if bad:
                j.problem("Kids Search finds grown-up titles", ", ".join(bad))
            d.key("BACK", wait=1)
    if switch_profile("Guest"):
        j.step("guest-home")
        before = ((item_id("Surround Test") or {}).get("UserData") or {}).get("PlaybackPositionTicks", 0)
        if open_title("Surround Test") is not None:
            start_playing("Surround Test (guest)", r"^Play$|^Resume$", server=False)
            time.sleep(5)
            j.step("guest-playing")
            time.sleep(25)
            leave_player()
            time.sleep(3)
            after = ((item_id("Surround Test") or {}).get("UserData") or {}).get("PlaybackPositionTicks", 0)
            DEEP["guest"] = {"saved seconds before": before / 1e7, "after": after / 1e7}
            if after != before:
                j.problem("Guest's watching was saved to the owner's account", f"{before / 1e7}s -> {after / 1e7}s")
    # back to the normal profile
    switch_profile("demo")


def easy_read():
    go_home()
    if MODE == "tv":
        d.key("DPAD_UP", 4, wait=0.5)
        d.key("DPAD_RIGHT", 2, wait=0.4)
        d.key("DPAD_CENTER", wait=1.5)
    else:
        root = d.dump()
        best = None
        for n in d.nodes(root):
            if n.get("clickable") == "true":
                a, b, c, e = d.bounds(n)
                if e < 400 and (best is None or c > d.bounds(best)[2]):
                    best = n
        if best is not None:
            d.touch_tap(best)
            time.sleep(1.5)
    if not d.press(r"^Settings$", timeout=6):
        return
    time.sleep(2)
    n = d.find_scrolling(r"Easy read|Bigger words|Text size")
    if n is None and MODE == "tv":
        d.tv_focus_down_to(r"Easy read|Bigger words|Text size", 25)
    elif n is not None:
        d.tap(n)
    time.sleep(1.5)
    j.step("easy-setting")
    n = d.find(d.dump(), r"^(✓ )?Bigger$")
    if n is not None:
        d.tap(n)
        time.sleep(2)
    j.step("easy-turned-on")
    go_home()
    j.step("easy-home")
    for door in ("Movies", "Shows"):
        d.press(rf"^{door}$", timeout=6)
        time.sleep(5)
        j.step(f"easy-{door}")
        d.key("BACK", wait=2)
    if open_title("Test Pattern One") is not None:
        j.step("easy-title-page")


def speed():
    for door in ("Movies", "Shows", "Live TV", "Music", "Games", "Books"):
        if d.out_of_time():
            return
        go_home()
        n = d.find(d.dump(), rf"^{door}$")
        if n is None:
            continue
        start = time.time()
        d.tap(n)
        # the door is "open" once something other than Home's greeting shows and the screen settles
        t = None
        last = None
        while time.time() - start < 30:
            root = d.dump()
            sig = tuple(x for x in d.texts(root) if x)[:30]
            if d.find(root, d.HOME_GREETING_RE) is None and sig and sig == last:
                t = round(time.time() - start, 1)
                break
            last = sig
            time.sleep(0.4)
        DEEP["timings"][f"door: {door}"] = t
        j.step(f"speed-{door}")
        d.key("BACK", wait=2)


def main():
    dens = d.sh("wm density")
    try:
        j.DENSITY[0] = int(dens.strip().split()[-1]) / 160
    except (ValueError, IndexError):
        pass
    d.sh("pm grant %s android.permission.POST_NOTIFICATIONS" % PKG)
    d.adb("logcat", "-c")
    d.adb("logcat", "-b", "crash", "-c")
    start = time.time()
    d.sh(f"am start -W -n {PKG}/com.github.damontecres.wholphin.MainActivity", timeout=90)
    d.sign_in()
    d.welcome_setup()
    for name in ["close", "later", "not now", "dismiss", "ok", "skip"]:
        n = d.find(d.dump(), rf"^{name}$")
        if n is not None:
            d.tap(n)
            time.sleep(1)
    if d.wait_for(d.DOORS_RE, 60) is None:
        j.problem("robot", "never got to Home")
        return
    DEEP["timings"]["first start to Home (with sign-in)"] = round(time.time() - start, 1)
    j.step("home")
    for name, trip in (
        ("speed", speed),
        ("tricky files", tricky_files),
        ("sound and subtitles", sound_and_subtitles),
        ("resume", resume_check),
        ("next episode", next_episode),
        ("bad wifi", bad_wifi),
        ("kids and guest", kids_and_guest),
        ("easy read", easy_read),
    ):
        if d.out_of_time():
            note(f"ran out of time before {name}")
            break
        d.log(f"=== {name} ===")
        try:
            j.ensure_app()
            trip()
        except Exception as e:
            j.problem("robot error", f"{name}: {repr(e)[:300]}")
    # a warm start after everything: close and open again
    d.sh(f"am force-stop {PKG}")
    start = time.time()
    d.sh(f"am start -W -n {PKG}/com.github.damontecres.wholphin.MainActivity", timeout=90)
    t = d.wait_for(d.DOORS_RE + r"|Who's watching", 40)
    DEEP["timings"]["open the app again to Home"] = round(time.time() - start, 1) if t is not None else None
    j.step("reopened")
    d.check_crash("end")
    J["crashes"] = [p for p in d.REPORT["problems"] if p.get("type") in ("crash", "anr", "system popup")]
    with open(f"{OUT}/logcat.txt", "w") as f:
        f.write(d.adb("logcat", "-d", "-v", "time", timeout=120))


if __name__ == "__main__":
    try:
        main()
    finally:
        j.save()
        os.replace(f"{OUT}/journeys.json", f"{OUT}/deep.json")
        d.save_report()
