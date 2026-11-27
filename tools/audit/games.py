#!/usr/bin/env python3
"""
Glowfin games robot. Opens every game built into Glowfin on a pretend TV (remote only) or a pretend phone
(finger only), plays it for a while like a person would, and takes pictures along the way:

  - every movie puzzle (Scene by scene, Poster puzzle, Movie grid, ...): its page, then a run
  - the 6 arcade games
  - the 5 party games, with up to 4 pretend phones joined at once. One phone drops out part way and
    another joins late, to check nothing gets stuck waiting for someone who left
  - Ember Road (the adventure)
  - phone only: the party "join" screen a phone shows (question buttons, typing, the drawing pad, voting)

It notes what a person would trip over: crashes, a screen that stops changing, words that stick out
past the screen edge, words drawn on top of other words, a round that waits for a player who left.

Usage: games.py <tv|phone> <package> <results dir>     (the app must be a debug build: it opens games
with wholphin://game?id=..., a robot-only link)
Results: <out>/games.json plus numbered pictures (.png) and screen text (.txt).
"""
import json
import os
import random
import re
import socket
import subprocess
import sys
import threading
import time

sys.path.insert(0, os.path.dirname(__file__))
os.environ.setdefault("SMOKE", "")
import drive as d  # noqa: E402  (signing in, the screen reader, key presses)

MODE, PKG, OUT = d.MODE, d.PKG, d.OUT
G = {"mode": MODE, "games": [], "problems": []}
d.DEADLINE = time.time() + 80 * 60
ACTIVITY = f"{PKG}/com.github.damontecres.wholphin.MainActivity"
SIZE = [1920, 1080]
random.seed(7)

PUZZLES = ["SCENES", "POSTER", "GRID", "TIMELINE", "ACTOR", "QUOTE", "ROLE", "BLITZ", "SEASON", "GUESS", "RIDDLE", "HIGHER_LOWER"]
PARTY = ["QUIZ", "CLOSEST", "FAKE_PLOT", "DRAW", "CAPTION"]
ARCADE = ["POPCORN_POP", "REEL_RUSH", "SNACK_CATCH", "USHER_DASH", "FILM_CAN_STACK", "MATINEE_MATCH"]
ONLY = [g for g in os.environ.get("GAMES_ONLY", "").split(",") if g]


def save():
    with open(f"{OUT}/games.json", "w") as f:
        json.dump(G, f, indent=1)


def problem(game, kind, text):
    G["problems"].append({"game": game, "type": kind, "text": text})
    d.log(f"PROBLEM {game} {kind}: {text}")
    save()


CUR = [None]


def entry(game, kind):
    e = {"game": game, "kind": kind, "shots": [], "notes": []}
    G["games"].append(e)
    CUR[0] = e
    save()
    return e


def note(text):
    CUR[0]["notes"].append(text)
    d.log(text)


def look(root, name):
    """Words past the screen edge, or words drawn over other words"""
    if root is None:
        return
    w, h = SIZE
    boxes = []
    for n in d.nodes(root):
        t = (n.get("text") or n.get("content-desc") or "").strip()
        if not t or not n.get("bounds"):
            continue
        a, b, c, e = d.bounds(n)
        if c - a < 2 or e - b < 2:
            continue
        if a < -2 or c > w + 2 or (MODE == "tv" and (b < -2 or e > h + 2)):
            problem(CUR[0]["game"], "sticks out past the screen", f"{name}: '{t[:50]}' at {n.get('bounds')} on a {w}x{h} screen")
        boxes.append((a, b, c, e, t))
    seen = set()
    for i, (a, b, c, e, t) in enumerate(boxes):
        for a2, b2, c2, e2, t2 in boxes[i + 1:]:
            if t == t2:
                continue
            # one inside the other is a card and its words, not two things on top of each other
            if (a <= a2 and b <= b2 and c >= c2 and e >= e2) or (a2 <= a and b2 <= b and c2 >= c and e2 >= e):
                continue
            ix = max(0, min(c, c2) - max(a, a2))
            iy = max(0, min(e, e2) - max(b, b2))
            small = min((c - a) * (e - b), (c2 - a2) * (e2 - b2))
            if small > 0 and ix * iy > 0.35 * small:
                k = (t[:30], t2[:30])
                if k not in seen:
                    seen.add(k)
                    problem(CUR[0]["game"], "words on top of other words", f"{name}: '{t[:40]}' and '{t2[:40]}'")


def shot(name):
    root = d.shot(f"{CUR[0]['game']}-{name}")
    CUR[0]["shots"].append({"n": d.shot_no[0], "name": name, "file": sorted(f for f in os.listdir(OUT) if f.startswith(f"{d.shot_no[0]:03d}-") and f.endswith(".png"))[-1:]})
    look(root, name)
    save()
    return root


def open_link(gid, tv=None):
    url = f"wholphin://game?id={gid}" + (f"&tv={tv}" if tv else "")
    d.sh(f"am start -W -n {ACTIVITY} -d '{url}'", timeout=60)
    time.sleep(4)


def screen_words(root):
    """What's on screen, without numbers (clocks and scores tick even when nothing else moves)"""
    return "|".join(sorted(re.sub(r"\d+", "#", t) for t in d.texts(root) if t))


def leave():
    for _ in range(3):
        d.key("BACK", wait=1.2)
    if not d.app_running():
        problem(CUR[0]["game"], "app closed", "Glowfin was not running after leaving the game")
        d.sh(f"am start -W -n {ACTIVITY}", timeout=90)
        time.sleep(6)
        d.sign_in()


def press_play(game):
    """The game's own page: picture it, then press its big Play / Host button"""
    root = d.dump()
    if d.find(root, r"Play$|^▶ Host on this") is None:
        time.sleep(3)
        root = d.dump()
    shot("game-page")
    if MODE != "tv":
        d.sh(f"input swipe {SIZE[0] // 2} {SIZE[1] * 3 // 4} {SIZE[0] // 2} {SIZE[1] // 4} 400")
        time.sleep(1.2)
        shot("game-page-scrolled")
        d.sh(f"input swipe {SIZE[0] // 2} {SIZE[1] // 4} {SIZE[0] // 2} {SIZE[1] * 3 // 4} 300")
        time.sleep(1)
    n = d.find(d.dump(), r"^▶ (Play|Host on this (TV|phone))$")
    if n is None:
        problem(game, "no Play button", "the game's page had no Play button")
        return False
    if MODE == "tv":
        f = d.focused_node(d.LAST_ROOT[0])
        if f is None or not (d.contains(f, n) or d.contains(n, f)):
            note("TV: Play did not have the focus when the page opened")
            problem(game, "remote focus", "Play button was not focused when the game page opened")
        d.tap(n)
    else:
        d.tap(n)
    time.sleep(3)
    return True


OVER_RX = r"^Play again$"
STUCK_S = 25


def tv_move():
    d.key(random.choice(["DPAD_RIGHT", "DPAD_LEFT", "DPAD_DOWN", "DPAD_UP", "DPAD_RIGHT"]), wait=0.25)
    d.key("DPAD_CENTER", wait=0.9)


AVOID = re.compile(r"^(Back|Leave|Open (movie|show)|\+ My List|✓ On My List|Leaderboard|Settings|Search|Home)", re.I)


def phone_tap_something(root):
    w, h = SIZE
    can = []
    for n in d.nodes(root):
        if n.get("clickable") != "true" or not n.get("bounds"):
            continue
        a, b, c, e = d.bounds(n)
        if e < h * 0.12 or b > h * 0.97 or c - a < 20:
            continue
        label = (n.get("text") or n.get("content-desc") or "")
        if not label:
            label = " ".join((x.get("text") or "") for x in n.iter("node")).strip()
        if AVOID.search(label):
            continue
        can.append(n)
    if can:
        x, y = d.center(random.choice(can))
        d.sh(f"input tap {x} {y}")
    else:
        d.sh(f"input tap {w // 2} {h // 2}")
    time.sleep(1.0)


def play_puzzle(game):
    entry(game, "puzzle")
    open_link(f"movie:{game}")
    if not press_play(game):
        leave()
        return
    start = time.time()
    shots_at = [3, 15, 35]
    last_words, same_since, over = None, time.time(), False
    played_again = False
    while time.time() - start < 55 and not d.out_of_time():
        root = d.dump()
        t = time.time() - start
        if shots_at and t >= shots_at[0]:
            shots_at.pop(0)
            root = shot(f"playing-{int(t)}s")
        if d.find(root, r"^Not enough to play yet$|^Couldn't load|^Couldn't reach") is not None:
            shot("not-enough")
            note("the game said there isn't enough to play with (the demo server is small)")
            break
        if d.find(root, OVER_RX) is not None:
            if not over:
                over = True
                shot("run-over")
                note(f"a run ended after {int(t)} s")
            if not played_again:
                played_again = True
                n = d.find(root, OVER_RX)
                d.tap(n)
                time.sleep(4)
                shot("play-again")
                if d.find(d.dump(), OVER_RX) is not None:
                    problem(game, "Play again did nothing", "the end screen was still showing after Play again")
                continue
            break
        words = screen_words(root)
        if words != last_words:
            last_words, same_since = words, time.time()
        elif time.time() - same_since > STUCK_S:
            shot("looks-stuck")
            problem(game, "looks stuck", f"nothing on screen changed for {STUCK_S} s while pressing buttons")
            break
        if MODE == "tv":
            tv_move()
        else:
            phone_tap_something(root)
    if not over:
        shot("after-55s")
    d.check_crash(game)
    leave()


def play_arcade(game):
    entry(game, "arcade")
    open_link(f"arcade:{game}")
    if not press_play(game):
        leave()
        return
    start = time.time()
    shots_at = [2, 10, 22]
    w, h = SIZE
    while time.time() - start < 30 and not d.out_of_time():
        t = time.time() - start
        if shots_at and t >= shots_at[0]:
            shots_at.pop(0)
            shot(f"playing-{int(t)}s")
        if MODE == "tv":
            k = random.choice(["DPAD_LEFT", "DPAD_RIGHT", "DPAD_CENTER", "DPAD_CENTER", "DPAD_UP", "DPAD_DOWN"])
            d.sh(f"input keyevent {'--longpress ' if k in ('DPAD_LEFT', 'DPAD_RIGHT') and random.random() < .4 else ''}{k}")
        else:
            x = random.choice([w // 5, w // 2, w * 4 // 5])
            y = random.choice([h // 3, h // 2, h * 2 // 3])
            if random.random() < .4:
                d.sh(f"input swipe {x} {y} {x} {y} 600")
            else:
                d.sh(f"input tap {x} {y}")
        time.sleep(0.35)
    shot("after-30s")
    # Pause (Back) and the menu it shows
    d.key("BACK", wait=1.5)
    shot("back-pressed")
    d.check_crash(game)
    leave()


# ---------- party games: pretend phones ----------

PH = "/data/local/tmp/gfph"


def link_port(label):
    out = d.adb("logcat", "-d", "-s", "*:I", timeout=60)
    ports = re.findall(rf"Phone link for {re.escape(label)} is on port (\d+)", out)
    return int(ports[-1]) if ports else None


class Phone:
    def __init__(self, i, name, port):
        self.i, self.name, self.port = i, name, port
        self.f = f"{PH}{i}"
        self.sent = {}
        self.views = []
        d.sh(f"rm -f {self.f}.*; touch {self.f}.on")
        loop = (f'while [ -f {self.f}.on ]; do echo "BFE1 robot{i} HELLO {name}"; sleep 0.7; done '
                f'| nc -u 127.0.0.1 {port} > {self.f}.out 2> {self.f}.err')
        subprocess.run(["adb", "shell", f"nohup sh -c '{loop}' > /dev/null 2>&1 &"], capture_output=True, timeout=15)

    def send(self, text):
        msg = f"BFE1 robot{self.i} {text}".replace("'", "")
        d.sh(f"echo '{msg}' | timeout 2 nc -u 127.0.0.1 {self.port} > /dev/null 2>&1", timeout=10)

    def view(self):
        raw = d.sh(f"tail -c 40000 {self.f}.out 2>/dev/null", timeout=15)
        parts = raw.split("BFE1 STATE ")
        for p in reversed(parts[1:]):
            try:
                v, _ = json.JSONDecoder().raw_decode(p.strip())
                return v
            except ValueError:
                continue
        return None

    def stop(self):
        d.sh(f"rm -f {self.f}.on")
        self.send("BYE")


def stop_all_phones():
    d.sh(f"rm -f {PH}*.on; pkill -f 'nc -u 127.0.0.1' 2>/dev/null")


SMILE = ["300,300 320,320 300,340", "700,300 720,320 700,340", "250,600 400,750 600,750 750,600", "500,100 100,500 500,900 900,500 500,100"]


def act(p, v, game):
    """What a person would do with what their phone shows right now"""
    key = (v.get("title"), v.get("round"), (v.get("text") or "")[:40], v.get("input"), len(v.get("actions") or []))
    last = p.sent.get(key)
    if last and time.time() - last < 8:
        return
    inp = v.get("input") or ""
    actions = v.get("actions") or []
    if inp == "draw":
        for line in SMILE:
            p.send(f"DRAW {line}")
        p.sent[key] = time.time()
    elif inp == "number":
        p.send(f"TYPE {random.choice([1994, 2005, 112, 7.4, 2016])}")
        p.sent[key] = time.time()
    elif inp == "text":
        words = (f"Robot caption {p.i}: when the popcorn runs out" if game == "CAPTION"
                 else f"A lost robot named {p.name} has to win a baking contest to save the city from a giant goose.")
        p.send(f"TYPE {words}")
        p.sent[key] = time.time()
    elif actions:
        if game == "DRAW":
            # give the drawer time to draw (and the robot time to picture the TV board) before guessing
            first = p.sent.setdefault(("seen",) + key, time.time())
            if time.time() - first < 12:
                return
        p.send(f"DO {random.choice(actions)['id']}")
        p.sent[key] = time.time()


def play_party(game, players, drop_at=None, late_join=None):
    """players = number of pretend phones; drop_at = round when the last phone leaves; late_join = round a new phone joins"""
    entry(game, f"party, {players} phones")
    stop_all_phones()
    d.adb("logcat", "-c")
    open_link(f"movie:{game}")
    if not press_play(game):
        leave()
        return
    time.sleep(2)
    port = link_port("Glowfin party")
    if port is None:
        shot("lobby-no-port")
        problem(game, "robot", "could not find the TV's phone link port in the log")
        leave()
        return
    names = ["Ana", "Ben", "Cy", "Dee", "Eli"]
    phones = [Phone(i + 1, names[i], port) for i in range(players)]
    time.sleep(4)
    root = shot("lobby-with-phones")
    joined = [p for p in phones if d.find(root, rf"\b{p.name}\b") is not None]
    vs = [p.view() for p in phones]
    note(f"{len(joined)} of {players} phone names showed on the TV; {sum(1 for v in vs if v)} phones got the TV's screen")
    if not any(vs):
        problem(game, "phones can't join", "no pretend phone heard back from the TV")
    with open(f"{OUT}/{game.lower()}-phone-views.jsonl", "a") as f:
        for p, v in zip(phones, vs):
            f.write(json.dumps({"phone": p.name, "at": "lobby", "view": v}) + "\n")
    start_btn = d.find(root, r"^Start with \d+$")
    if start_btn is None and MODE != "tv":
        # a phone hosting: Start can be further down the page
        d.sh(f"input swipe {SIZE[0] // 2} {SIZE[1] * 3 // 4} {SIZE[0] // 2} {SIZE[1] // 4} 400")
        time.sleep(1.2)
        root = d.dump()
        start_btn = d.find(root, r"^Start with \d+$")
        if start_btn is not None:
            note("phone host: had to scroll down to find Start")
    if start_btn is None:
        n = d.find(root, r"^Needs \d+ more")
        problem(game, "can't start", n.get("text") if n is not None else "no Start button on the TV")
        stop_all_phones()
        leave()
        return
    d.tap(start_btn)
    started = time.time()
    last_round, round_started, shots_taken = None, time.time(), 0
    dropped = joined_late = False
    round_times = []
    over = False
    seen_keys = set()
    draw_shot_at = None
    while time.time() - started < 260 and not d.out_of_time():
        active = [p for p in phones if not getattr(p, "gone", False)]
        views = [(p, p.view()) for p in active]
        lead = next((v for _, v in views if v), None)
        if lead is None:
            time.sleep(1)
            continue
        title = lead.get("title") or ""
        rnd = lead.get("round", 0)
        k = (title.split(" ")[0], rnd)
        if k not in seen_keys and shots_taken < 16:
            seen_keys.add(k)
            shots_taken += 1
            time.sleep(0.8)
            shot(f"tv-{re.sub(r'[^a-z0-9]+', '-', title.lower())[:24]}-{rnd}")
            with open(f"{OUT}/{game.lower()}-phone-views.jsonl", "a") as f:
                for p, v in views:
                    f.write(json.dumps({"phone": p.name, "at": f"{title} {rnd}", "view": v}) + "\n")
        if game == "DRAW":
            drawer = next((v for _, v in views if v and v.get("input") == "draw"), None)
            if drawer and (drawer.get("round"), drawer.get("secret")) not in seen_keys:
                seen_keys.add((drawer.get("round"), drawer.get("secret")))
                draw_shot_at = time.time() + 7
        if draw_shot_at and time.time() > draw_shot_at:
            draw_shot_at = None
            shot(f"tv-drawing-on-screen-{rnd}")
        if title in ("Game over", "You win!", "You tied for the win!"):
            over = True
            time.sleep(1.5)
            shot("tv-game-over")
            break
        if rnd != last_round and rnd and rnd < 100:
            if last_round is not None:
                round_times.append(round(time.time() - round_started, 1))
            last_round, round_started = rnd, time.time()
            if drop_at and rnd >= drop_at and not dropped:
                dropped = True
                phones[-1].stop()
                phones[-1].gone = True
                note(f"{phones[-1].name}'s phone left in round {rnd}")
            if late_join and rnd >= late_join and not joined_late:
                joined_late = True
                np = Phone(len(phones) + 1, names[len(phones)], port)
                phones.append(np)
                note(f"{np.name}'s phone joined late, in round {rnd}")
        for p, v in views:
            if v:
                act(p, v, game)
        time.sleep(1)
    note(f"seconds per round: {round_times}")
    if not over:
        shot("tv-after-260s")
        problem(game, "game didn't finish", f"still going after 260 s (last: {title} round {rnd})")
    if dropped and round_times:
        before = round_times[:max(1, (drop_at or 1) - 1)]
        after = round_times[(drop_at or 1):]
        usual = sorted(before)[len(before) // 2]
        if after and max(after) > usual + 8:
            problem(game, "waits for a player who left",
                    f"after a phone left, rounds took up to {max(after)} s (everyone else had answered): {round_times}")
    # The end screen buttons
    if over:
        root = d.dump()
        if d.find(root, r"^Back to players$") is not None:
            d.press(r"^Back to players$", timeout=5)
            time.sleep(2.5)
            shot("tv-back-to-players")
    stop_all_phones()
    d.check_crash(game)
    leave()


# ---------- phone only: the screen a phone shows when it joins a TV's party ----------

class FakeTv:
    """A pretend TV on this computer. The pretend phone reaches it at 10.0.2.2 (the emulator's way to this computer)"""

    def __init__(self, port=47123):
        self.s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        self.s.bind(("0.0.0.0", port))
        self.s.settimeout(0.5)
        self.port = port
        self.view = {"title": "You're in!", "text": "Waiting for the TV to start Quiz night. 3 joined: Ana, Ben, Robot", "status": "Quiz night · Robot · 0 points"}
        self.addr = None
        self.heard = []
        self.on = True
        threading.Thread(target=self.loop, daemon=True).start()

    def loop(self):
        last = 0
        while self.on:
            try:
                data, addr = self.s.recvfrom(65000)
                text = data.decode("utf-8", "replace").strip()
                self.addr = addr
                parts = text.split(" ", 3)
                if len(parts) >= 3 and parts[2] != "HELLO":
                    self.heard.append(text)
                self.push()
            except socket.timeout:
                pass
            if time.time() - last > 1 and self.addr:
                last = time.time()
                self.push()

    def push(self):
        if self.addr:
            self.s.sendto(("BFE1 STATE " + json.dumps(self.view)).encode(), self.addr)

    def show(self, view):
        self.view = view
        self.heard = []
        self.push()


def phone_join_screens():
    entry("PARTY_PHONE", "phone joining a TV party")
    tv = FakeTv()
    open_link("join", tv=f"10.0.2.2:{tv.port}")
    time.sleep(4)
    if tv.addr is None:
        shot("join-searching")
        problem("PARTY_PHONE", "phone didn't join", "the phone never said hello to the pretend TV")
        tv.on = False
        leave()
        return
    shot("joined-lobby")
    st = "Quiz night · Robot · 1,250 points"
    screens = [
        ("question", {"title": "Question 3 of 10", "text": "Which movie is this poster from?", "status": st, "round": 3,
                      "actions": [{"id": f"c{i}", "label": n} for i, n in enumerate(["The Great St. Louis Bank Robbery", "Jungle Book", "King Lear", "Night of the Living Dead"])]}, "tap", r" DO c\d"),
        ("number", {"title": "Round 2 of 8", "text": "What year did Night of the Living Dead come out?", "status": "Closest wins · Robot · 500 points", "input": "number", "round": 2}, "number", r" TYPE "),
        ("write", {"title": "Round 1 of 3", "text": "Write a made-up plot for King Lear (1953). Make it sound real!", "status": "Fake plot · Robot · 0 points", "input": "text", "round": 1}, "text", r" TYPE "),
        ("vote", {"title": "Vote", "text": "Which is the REAL plot of King Lear (1953)? (yours isn't shown)", "status": "Fake plot · Robot · 0 points", "round": 101,
                  "actions": [{"id": "p0", "label": "1. In this abridged television production, the old king vows revenge against his conniving daughters after they try to take swift control of his power."},
                              {"id": "p2", "label": "2. A lost robot named Ana has to win a baking contest to save the city from a giant goose."},
                              {"id": "p3", "label": "3. Short one."}]}, "tap", r" DO p\d"),
        ("draw", {"title": "You're drawing!", "text": "Draw this so the others can guess it. No letters or numbers!", "secret": "The Great St. Louis Bank Robbery", "input": "draw", "status": "Draw the movie · Robot · 300 points", "round": 2}, "draw", r" DRAW "),
        ("guess-drawing", {"title": "Round 3 of 4", "text": "What is Ana drawing? Watch the TV.", "status": "Draw the movie · Robot · 300 points", "round": 3,
                           "actions": [{"id": f"c{i}", "label": n} for i, n in enumerate(["Jungle Book", "King Lear", "Popeye the Sailor Meets Sindbad the Sailor", "Big Buck Bunny", "Sintel", "Cosmos Laundromat"])]}, "tap", r" DO c\d"),
        ("reveal", {"title": "Answer", "text": "It's Jungle Book!\nAna +950   Robot +720", "status": st, "round": 3}, None, None),
        ("over", {"title": "You win!", "text": "Robot: 4,120\nAna: 3,900\nBen: 1,200", "status": st}, None, None),
    ]
    w, h = SIZE
    for name, view, how, expect in screens:
        tv.show(view)
        time.sleep(2.5)
        root = shot(name)
        if how is None:
            continue
        if how == "tap":
            n = d.find(root, "^" + re.escape(view["actions"][0]["label"][:20]))
            if n is None:
                problem("PARTY_PHONE", "button missing", f"{name}: the first answer button isn't on the phone screen")
                continue
            d.touch_tap(n)
        elif how in ("number", "text"):
            edit = [n for n in d.nodes(root) if n.get("class", "").endswith("EditText")]
            if not edit:
                problem("PARTY_PHONE", "no typing box", f"{name}: no box to type in")
                continue
            d.touch_tap(edit[0])
            time.sleep(1)
            d.sh("input text " + ("1968" if how == "number" else "A%sgoose%stakes%sover%sthe%scastle"))
            time.sleep(1)
            shot(f"{name}-typed")
            send = d.find(d.dump(), r"^Send|^Lock it in|^Done$")
            if send is None:
                d.key("ENTER")
            else:
                d.touch_tap(send)
        elif how == "draw":
            x0, y0 = w // 3, h // 2
            d.sh(f"input swipe {x0} {y0} {x0 + w // 3} {y0 + 120} 500")
            d.sh(f"input swipe {x0} {y0 + 200} {x0 + w // 4} {y0 + 50} 500")
            shot(f"{name}-drawn")
        time.sleep(2.5)
        if not any(re.search(expect, t) for t in tv.heard):
            problem("PARTY_PHONE", "phone answer didn't reach the TV", f"{name}: the TV never got the answer ({how}); heard {tv.heard[:3]}")
        else:
            note(f"{name}: TV got '{[t for t in tv.heard if re.search(expect, t)][0][:80]}'")
    tv.on = False
    d.check_crash("PARTY_PHONE")
    leave()


def ember():
    """Ember Road: the title screen, the phones lobby, the setup steps, then a few days on the road"""
    entry("EMBER", "adventure")
    open_link("ember")
    if d.wait_for(r"New journey|Continue the journey", 25) is None:
        shot("title-missing")
        problem("EMBER", "didn't open", "Ember Road's title screen never showed")
        leave()
        return
    shot("title")
    d.press(r"^New journey", timeout=6)
    time.sleep(2)
    if d.find(d.dump(), r"Skip, just the TV") is not None:
        shot("phones-lobby")
        d.press(r"Skip, just the TV", timeout=5)
        time.sleep(2)
    for step in range(4):
        shot(f"setup-{step + 1}")
        target = r"^Set out!$" if step == 3 else r"^Next$"
        if MODE == "tv":
            if not d.tv_focus_down_to(target, presses=40, right_of=r"^(Back|Cancel)$"):
                problem("EMBER", "remote can't reach", f"{target} on setup step {step + 1}")
                break
        else:
            n = d.find_scrolling(target)
            if n is None:
                problem("EMBER", "button missing", f"no {target} on setup step {step + 1}")
                break
            d.tap(n)
        time.sleep(1.5)
    start = time.time()
    last_words, same_since = None, time.time()
    i = 0
    while time.time() - start < 70 and not d.out_of_time():
        root = d.dump()
        if i % 3 == 0:
            root = shot(f"road-{i}")
        i += 1
        n = None
        for pattern in (r"^Keep going$", r"^Continue$", r"^Go this way$", r"^Pack up and go on$", r"^Leave$", r"^Done shopping$"):
            n = d.find(root, pattern)
            if n is not None:
                break
        if n is not None:
            d.tap(n)
        elif MODE == "tv":
            if d.focused_node(root) is None:
                d.key("DPAD_DOWN", wait=0.6)
            d.key("DPAD_CENTER", wait=1.2)
        else:
            phone_tap_something(root)
        words = screen_words(root)
        if words != last_words:
            last_words, same_since = words, time.time()
        elif time.time() - same_since > STUCK_S:
            shot("looks-stuck")
            problem("EMBER", "looks stuck", f"nothing changed for {STUCK_S} s")
            break
        time.sleep(1.5)
    d.key("BACK", wait=1.5)
    shot("menu")
    d.check_crash("EMBER")
    leave()


def games_page():
    entry("GAMES_PAGE", "Games page")
    open_link("games")
    time.sleep(4)
    shot("games-page")
    for i in range(4):
        if MODE == "tv":
            d.key("DPAD_DOWN", times=2, wait=0.6)
        else:
            d.sh(f"input swipe {SIZE[0] // 2} {SIZE[1] * 3 // 4} {SIZE[0] // 2} {SIZE[1] // 4} 400")
        time.sleep(1.5)
        shot(f"games-page-{i + 1}")
    leave()


def want(g):
    return not ONLY or g in ONLY


def main():
    m = re.findall(r"(\d+)x(\d+)", d.sh("wm size"))
    if m:
        w, h = map(int, m[-1])
        SIZE[:] = [max(w, h), min(w, h)] if MODE == "tv" else [min(w, h), max(w, h)]
    d.sh("pm grant %s android.permission.POST_NOTIFICATIONS" % PKG)
    d.adb("logcat", "-c")
    d.adb("logcat", "-b", "crash", "-c")
    d.sh(f"am start -W -n {ACTIVITY}", timeout=90)
    time.sleep(6)
    if d.find(d.dump(), r"Glowfin has crashed") is not None:
        n = d.find(d.dump(), r"^Do not send$")
        d.tap(n) if n is not None else d.key("BACK")
        time.sleep(3)
    d.sign_in()
    d.welcome_setup()
    for name in ["close", "later", "not now", "dismiss", "ok", "skip"]:
        n = d.find(d.dump(), rf"^{name}$")
        if n is not None:
            d.tap(n)
            time.sleep(1)
    if d.wait_for(d.DOORS_RE, 60) is None:
        entry("START", "sign in")
        shot("never-got-home")
        problem("START", "robot", "never got to Home")
        return
    jobs = [("games", games_page)] if want("GAMES_PAGE") else []
    jobs += [(g, lambda g=g: play_party(g, *{"QUIZ": (4, 3, None), "CLOSEST": (2, None, 2), "FAKE_PLOT": (4, None, None),
                                             "DRAW": (3, 1, None), "CAPTION": (3, None, None)}[g])) for g in PARTY if want(g)]
    if MODE != "tv" and want("PARTY_PHONE"):
        jobs.append(("PARTY_PHONE", phone_join_screens))
    jobs += [(g, lambda g=g: play_puzzle(g)) for g in PUZZLES if want(g)]
    jobs += [(g, lambda g=g: play_arcade(g)) for g in ARCADE if want(g)]
    if want("EMBER"):
        jobs.append(("EMBER", ember))
    for name, job in jobs:
        if d.out_of_time():
            problem(name, "robot", "ran out of time before this game")
            continue
        try:
            job()
        except Exception as e:  # one broken game shouldn't hide the rest
            problem(name, "robot error", repr(e)[:300])
            stop_all_phones()
            try:
                leave()
            except Exception:
                pass
    d.check_crash("end")
    G["crashes"] = [p for p in d.REPORT["problems"] if p.get("type") in ("crash", "anr", "system popup")]
    G["remote"] = [p for p in d.REPORT["problems"] if p.get("type") == "could not reach with remote"]
    with open(f"{OUT}/logcat.txt", "w") as f:
        f.write(d.adb("logcat", "-d", "-v", "time", timeout=120))


if __name__ == "__main__":
    try:
        main()
    finally:
        save()
        d.save_report()
