"""
Benflix Lobby: a 3D room in web Benflix where the family walks around together, watches one movie on the
big TV in step, plays pool and chats (designed 2026-11-13, thread "Design a 3D watch-together lobby").

This file is Tower's "room helper". It only keeps things in memory (nothing is saved to disk):
- Who is in each lobby and where they are standing (every browser sends its spot about 8 times a second
  and gets everyone else's back in the same answer).
- The chat (the last 60 lines).
- What the big TV is playing and where it is in the movie, so every browser can stay in step.
- The pool table: each shot (where the balls were and how hard the white ball was hit), so every browser
  plays the same shot.

Rules: up to 8 people a lobby. Kids profiles can't use the Lobby at all (Ben, 2026-11-15). A lobby closes 15 minutes after the
last person leaves. Only uses the Python standard library.
"""

import math
import random
import re
import threading
import time

MAX_PEOPLE = 8
MAX_LOBBIES = 6
GONE_AFTER = 12  # seconds without hearing from a browser = that person left
CLOSE_AFTER = 15 * 60  # an empty lobby closes after this long
CHAT_KEEP = 60
ROOMS = ("den", "theater", "roof", "cinema", "lounge")  # cinema = the Glowfin Cinema, a multiplex to hide in; lounge = the Harbor Lounge, a games boathouse
STYLES = ("pebble", "facet", "glow")
LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ"
# What Kids can say (the web page shows these as buttons; Tower only lets these through)
KIDS_PHRASES = (
    "Pause please",
    "So funny",
    "Can we play pool?",
    "I need a snack break",
    "Wow",
    "This is my favourite part",
    "Can we watch the next one?",
    "Good night",
)

_lock = threading.Lock()
_lobbies = {}  # code -> lobby (a dict)


class LobbyError(Exception):
    def __init__(self, status, message):
        super().__init__(message)
        self.status = status
        self.message = message


def _num(v, lo, hi, default=0.0):
    try:
        v = float(v)
    except (TypeError, ValueError):
        return default
    if v != v:  # not a number
        return default
    return max(lo, min(hi, v))


def _text(v, most):
    return re.sub(r"\s+", " ", str(v or "")).strip()[:most]


def _colour(v):
    v = str(v or "")
    return v if re.fullmatch(r"#[0-9A-Fa-f]{6}", v) else "#FFB547"


def _tidy():
    """Drops people who stopped answering and lobbies that have been empty for a while. Call with _lock held."""
    now = time.time()
    for code in list(_lobbies):
        lob = _lobbies[code]
        for uid in [u for u, p in lob["people"].items() if now - p["seen"] > GONE_AFTER]:
            name = lob["people"].pop(uid)["name"]
            _say(lob, None, f"{name} left the lobby")
            if lob["host"] == uid and lob["people"]:
                # The longest-here person becomes the host, so the TV still has someone in charge
                new = min(lob["people"].items(), key=lambda kv: kv[1]["came"])
                lob["host"], lob["host_name"] = new[0], new[1]["name"]
                _say(lob, None, f"{new[1]['name']} is the host now")
        if lob["people"]:
            lob["empty_since"] = None
        elif lob["empty_since"] is None:
            lob["empty_since"] = now
        elif now - lob["empty_since"] > CLOSE_AFTER:
            del _lobbies[code]


def _say(lob, person, text, uid=None):
    lob["chat_n"] += 1
    lob["chat"].append({
        "n": lob["chat_n"],
        "name": person["name"] if person else "",
        "color": person["color"] if person else "",
        "uid": uid or "",
        "text": text,
        "at": time.time(),
    })
    del lob["chat"][:-CHAT_KEEP]


def _tv_now(tv):
    """Where the movie is right now, in seconds."""
    pos = tv["pos"]
    if tv["playing"]:
        pos += time.time() - tv["at"]
    return pos


def _public_tv(lob):
    tv = lob["tv"]
    return {
        "item": tv["item"],
        "title": tv["title"],
        "kids_ok": tv["kids_ok"],
        "playing": tv["playing"],
        "pos": round(_tv_now(tv), 3),
        "n": tv["n"],
        "by": tv["by"],
    }


def _summary(lob, user_id):
    return {
        "code": lob["code"],
        "room": lob["room"],
        "host_name": lob["host_name"],
        "mine": lob["host"] == user_id,
        "title": lob["tv"]["title"],
        "kids_ok": lob["tv"]["kids_ok"],
        "people": [p["name"] for p in lob["people"].values()],
    }


# ---------- games in the Glowfin Cinema: Hide and seek, and Tag (the host starts them and picks the settings) ----------
GAME_SETTINGS = {
    # name: (smallest, biggest, usual)
    "hide": {"count": (10, 120, 45), "minutes": (1, 15, 5), "squeak": (0, 180, 75), "join": (0, 1, 0)},
    "tag": {"minutes": (1, 15, 3), "noback": (0, 10, 3), "infection": (0, 1, 0)},
}
TAG_READY = 5  # seconds everyone gets to scatter before a Tag round starts
TAG_REACH = 3.0  # metres: Tower's own check that the two really were next to each other (a little forgiving for lag)


def _new_game():
    return {"n": 0, "mode": "", "state": "idle", "set": {}, "it": [], "found": [], "safe": {}, "score": {}, "t_end": 0.0, "acc": 0.0, "result": "", "order": []}


def _names(lob, uids):
    names = [lob["people"][u]["name"] for u in uids if u in lob["people"]]
    return names[0] if len(names) == 1 else ", ".join(names[:-1]) + " and " + names[-1] if names else "Nobody"


def _game_over(lob, text):
    g = lob["game"]
    g["state"], g["result"], g["t_end"] = "over", text, time.time() + 20
    _say(lob, None, text)


def _game_start(lob, body):
    mode = body.get("mode")
    if mode not in GAME_SETTINGS:
        raise LobbyError(400, "Pick Hide and seek or Tag.")
    if lob["room"] != "cinema":
        raise LobbyError(400, "Games are played in the Glowfin Cinema. Move everyone there first.")
    if len(lob["people"]) < 2:
        raise LobbyError(400, "You need at least two people in the lobby to play.")
    asked = body.get("set") if isinstance(body.get("set"), dict) else {}
    rules = {k: int(_num(asked.get(k), lo, hi, usual)) for k, (lo, hi, usual) in GAME_SETTINGS[mode].items()}
    first = body.get("it") if body.get("it") in lob["people"] else random.choice(list(lob["people"]))
    g = lob["game"]
    now = time.time()
    g.update(n=g["n"] + 1, mode=mode, state="count", set=rules, it=[first], found=[], safe={}, result="", order=[],
             score={u: 0.0 for u in lob["people"]}, acc=now, t_end=now + (rules["count"] if mode == "hide" else TAG_READY))
    who = lob["people"][first]["name"]
    if mode == "hide":
        _say(lob, None, f"Hide and seek! {who} is counting to {rules['count']}. Everyone else, hide!")
    else:
        _say(lob, None, f"Tag! {who} is It. Run!")


def _game_tick(lob):
    """Moves the round along: counting -> playing -> over. Call with _lock held (every sync does)."""
    g = lob["game"]
    now = time.time()
    if g["state"] == "over":
        if now > g["t_end"]:
            g["state"], g["mode"] = "idle", ""
        return
    if g["state"] not in ("count", "play"):
        return
    here = lob["people"]
    if lob["room"] != "cinema":
        return _game_over(lob, "The game stopped because everyone left the Cinema.")
    if len(here) < 2:
        return _game_over(lob, "The game stopped because there aren't two players any more.")
    g["it"] = [u for u in g["it"] if u in here]
    g["found"] = [u for u in g["found"] if u in here]
    if not g["it"]:
        if g["mode"] == "hide":
            return _game_over(lob, "The seeker left, so the hiders win.")
        g["it"] = [random.choice(list(here))]
        _say(lob, None, f"{here[g['it'][0]]['name']} is It now.")
    hiders = [u for u in here if u not in g["it"] and u not in g["found"]]
    if g["state"] == "count":
        if now >= g["t_end"]:
            g["state"], g["acc"], g["t_end"] = "play", now, now + g["set"]["minutes"] * 60
            _say(lob, None, "Ready or not, here they come!" if g["mode"] == "hide" else "Go!")
        return
    # playing
    if g["mode"] == "tag" and not g["set"]["infection"]:
        for u in g["it"]:
            g["score"][u] = g["score"].get(u, 0.0) + (now - g["acc"])
    g["acc"] = now
    if g["mode"] == "hide":
        if not hiders:
            return _game_over(lob, f"Everyone was found. {_names(lob, g['it'][:1])} wins!" if not g["set"]["join"] else "Everyone was found. The seekers win!")
        if now >= g["t_end"]:
            return _game_over(lob, f"Time's up! {_names(lob, hiders)} stayed hidden and {'wins' if len(hiders) == 1 else 'win'}.")
    elif g["set"]["infection"]:
        if not hiders:
            last = g["order"][-1] if g["order"] and g["order"][-1] in here else None
            return _game_over(lob, f"Everyone got tagged. {here[last]['name']} lasted the longest and wins!" if last else "Everyone got tagged.")
        if now >= g["t_end"]:
            return _game_over(lob, f"Time's up! {_names(lob, hiders)} never got tagged and {'wins' if len(hiders) == 1 else 'win'}.")
    elif now >= g["t_end"]:
        best = min(here, key=lambda u: g["score"].get(u, 0.0))
        return _game_over(lob, f"Time's up! {here[best]['name']} wins with only {int(g['score'].get(best, 0))} seconds as It.")


def _game_tag(lob, user_id, target):
    g = lob["game"]
    here = lob["people"]
    now = time.time()
    if g["state"] != "play" or user_id not in g["it"] or target not in here or target in g["it"] or target in g["found"]:
        return False
    if g["safe"].get(target, 0) > now:
        return False
    a, b = here[user_id], here[target]
    if "x" not in b or math.hypot(a["x"] - b["x"], a["z"] - b["z"]) > TAG_REACH or abs(a.get("h", 0) - b.get("h", 0)) > 2.5:
        return False
    g["order"].append(target)
    if g["mode"] == "hide":
        g["found"].append(target)
        if g["set"]["join"]:
            g["it"].append(target)
        _say(lob, None, f"{a['name']} found {b['name']}!" + (f" {b['name']} is a seeker now." if g["set"]["join"] else ""))
    elif g["set"]["infection"]:
        g["it"].append(target)
        _say(lob, None, f"{a['name']} tagged {b['name']}. {b['name']} is It too!")
    else:
        g["it"] = [target]
        g["safe"] = {user_id: now + g["set"]["noback"]}
        _say(lob, None, f"{a['name']} tagged {b['name']}. {b['name']} is It!")
    _game_tick(lob)
    return True


def _public_game(lob):
    g = lob["game"]
    now = time.time()
    return {
        "n": g["n"], "mode": g["mode"], "state": g["state"], "set": g["set"], "it": g["it"], "found": g["found"],
        "safe": [u for u, t in g["safe"].items() if t > now], "left": round(max(0.0, g["t_end"] - now), 1),
        "score": {u: int(v) for u, v in g["score"].items()}, "result": g["result"],
    }


def _get(code):
    lob = _lobbies.get(str(code or "").upper())
    if not lob:
        raise LobbyError(404, "That lobby has closed. Open a new one from the Lobby page.")
    return lob


def _may_control(lob, user_id):
    return lob["host"] == user_id or lob["anyone_remote"]


def route(method, path, read_json, user_id, user_name, is_admin, is_kids):
    """Answers /api/lobby/... Returns (status, answer)."""
    parts = [p for p in path.split("/") if p][2:]  # after "api", "lobby"
    if is_kids:
        # The Lobby is for grown-ups and Big kids only, never Kids profiles (Ben, 2026-11-15)
        if method == "GET" and parts == ["list"]:
            return 200, {"lobbies": [], "kids_phrases": []}
        raise LobbyError(403, "The Lobby isn't for Kids profiles.")
    with _lock:
        _tidy()
        if method == "GET" and parts == ["list"]:
            lobs = [_summary(l, user_id) for l in _lobbies.values() if l["people"] or l["host"] == user_id]
            if is_kids:
                lobs = [l for l in lobs if l["kids_ok"]]
            return 200, {"lobbies": lobs, "kids_phrases": list(KIDS_PHRASES) if is_kids else []}
    if method == "POST" and parts == ["open"]:
        if is_kids:
            raise LobbyError(403, "Ask a grown-up to open the lobby, then you can join.")
        body = read_json() or {}
        with _lock:
            _tidy()
            # One lobby per host: opening again just changes what's on and which room
            lob = next((l for l in _lobbies.values() if l["host"] == user_id), None)
            if lob is None:
                if len(_lobbies) >= MAX_LOBBIES:
                    raise LobbyError(429, "There are already 6 lobbies open. Join one of those instead.")
                code = "".join(random.choice(LETTERS) for _ in range(4))
                while code in _lobbies:
                    code = "".join(random.choice(LETTERS) for _ in range(4))
                lob = {
                    "code": code, "host": user_id, "host_name": user_name or "Someone", "made": time.time(),
                    "room": "den", "anyone_remote": False, "people": {}, "chat": [], "chat_n": 0, "empty_since": None,
                    "tv": {"item": "", "title": "", "kids_ok": True, "playing": False, "pos": 0.0, "at": time.time(), "n": 0, "by": ""},
                    "pool": {"n": 0, "shot": None},
                    "game": _new_game(),
                }
                _lobbies[code] = lob
            if body.get("room") in ROOMS:
                lob["room"] = body["room"]
            if body.get("item"):
                _set_tv(lob, body, user_name)
            return 200, _summary(lob, user_id)
    if len(parts) != 2:
        raise LobbyError(404, "Not found")
    code, what = parts
    body = read_json() if method == "POST" else {}
    body = body if isinstance(body, dict) else {}
    with _lock:
        _tidy()
        lob = _get(code)
        me = lob["people"].get(user_id)
        if what == "leave" and method == "POST":
            if me:
                lob["people"].pop(user_id)
                _say(lob, None, f"{me['name']} left the lobby")
            return 200, {"ok": True}
        if what == "sync" and method == "POST":
            if me is None:
                if is_kids and not lob["tv"]["kids_ok"]:
                    raise LobbyError(403, "This lobby is showing something that isn't for Kids.")
                if len(lob["people"]) >= MAX_PEOPLE:
                    raise LobbyError(409, "This lobby is full (8 people).")
                me = lob["people"][user_id] = {"name": user_name or "Someone", "came": time.time(), "kids": bool(is_kids)}
                _say(lob, None, f"{me['name']} came in")
            spot = body.get("me") or {}
            me.update({
                "x": round(_num(spot.get("x"), -60, 60), 3),
                "z": round(_num(spot.get("z"), -60, 60), 3),
                "yaw": round(_num(spot.get("yaw"), -100, 100), 3),
                "sit": int(_num(spot.get("sit"), -1, 99, -1)),
                "h": round(_num(spot.get("h"), 0, 20), 2),  # how high up they stand (the cinema has stairs and an upstairs)
                "cr": 1 if spot.get("cr") else 0,  # crouching
                "style": spot.get("style") if spot.get("style") in STYLES else "pebble",
                "color": _colour(spot.get("color")),
                "act": _text(spot.get("act"), 12),
                "seen": time.time(),
            })
            _game_tick(lob)
            since = int(_num(body.get("chat_since"), 0, 1e9))
            pool_since = int(_num(body.get("pool_since"), 0, 1e9))
            return 200, {
                "now": time.time(),
                "room": lob["room"],
                "host": lob["host"] == user_id,
                "host_name": lob["host_name"],
                "anyone_remote": lob["anyone_remote"],
                "may_control": _may_control(lob, user_id),
                "people": [
                    {"id": uid, "name": p["name"], "color": p["color"], "style": p["style"], "x": p["x"], "z": p["z"], "yaw": p["yaw"], "sit": p["sit"], "h": p.get("h", 0), "cr": p.get("cr", 0), "act": p["act"], "kids": p["kids"]}
                    for uid, p in lob["people"].items() if uid != user_id and "x" in p
                ],
                "chat": [c for c in lob["chat"] if c["n"] > since],
                "chat_n": lob["chat_n"],
                "tv": _public_tv(lob),
                "game": _public_game(lob),
                "pool": lob["pool"] if lob["pool"]["n"] > pool_since else None,
                "kids_phrases": list(KIDS_PHRASES) if is_kids else [],
            }
        if me is None:
            raise LobbyError(403, "Come into the lobby first.")
        if what == "say" and method == "POST":
            text = _text(body.get("text"), 140)
            if is_kids and text not in KIDS_PHRASES:
                raise LobbyError(403, "Kids pick from the ready-made phrases.")
            if text:
                _say(lob, me, text, user_id)
            return 200, {"ok": True}
        if what == "tv" and method == "POST":
            if not _may_control(lob, user_id):
                raise LobbyError(403, f"Only {lob['host_name']} can control the TV right now.")
            action = body.get("action")
            tv = lob["tv"]
            if action == "set":
                if is_kids:
                    raise LobbyError(403, "Kids can't change what's on.")
                _set_tv(lob, body, me["name"])
                _say(lob, None, f"{me['name']} put on {tv['title'] or 'something new'}")
            elif action in ("play", "pause", "seek"):
                if not tv["item"]:
                    raise LobbyError(400, "Nothing is on the TV yet.")
                pos = _num(body.get("pos"), 0, 60 * 60 * 12, _tv_now(tv)) if "pos" in body else _tv_now(tv)
                tv["pos"], tv["at"] = pos, time.time()
                if action != "seek":
                    tv["playing"] = action == "play"
                    _say(lob, None, f"{me['name']} {'pressed play' if tv['playing'] else 'paused'}")
                tv["n"] += 1
                tv["by"] = me["name"]
            elif action == "stop":
                tv.update(item="", title="", kids_ok=True, playing=False, pos=0.0, at=time.time(), by=me["name"])
                tv["n"] += 1
            else:
                raise LobbyError(400, "Unknown TV button.")
            return 200, _public_tv(lob)
        if what == "settings" and method == "POST":
            if lob["host"] != user_id:
                raise LobbyError(403, "Only the host can change the lobby.")
            if body.get("room") in ROOMS and body["room"] != lob["room"]:
                lob["room"] = body["room"]
                _say(lob, None, f"{me['name']} moved everyone to the {dict(den='living room', theater='home theater', roof='rooftop', cinema='Glowfin Cinema', lounge='Harbor Lounge')[body['room']]}")
            if "anyone_remote" in body:
                lob["anyone_remote"] = bool(body["anyone_remote"])
            return 200, {"room": lob["room"], "anyone_remote": lob["anyone_remote"]}
        if what == "game" and method == "POST":
            # Hide and seek and Tag in the Cinema. The host starts and stops; whoever is It reports a tag and Tower checks it.
            action = body.get("action")
            if action in ("start", "stop") and lob["host"] != user_id:
                raise LobbyError(403, f"Only {lob['host_name']} can start or stop a game.")
            if action == "start":
                _game_start(lob, body)
            elif action == "stop":
                if lob["game"]["state"] in ("count", "play"):
                    _game_over(lob, f"{me['name']} stopped the game.")
            elif action == "tag":
                _game_tag(lob, user_id, str(body.get("target") or ""))
            else:
                raise LobbyError(400, "Unknown game button.")
            return 200, _public_game(lob)
        if what == "pool" and method == "POST":
            # A shot: where every ball was, and the white ball's push. Every browser plays the same shot.
            balls = body.get("balls")
            if not isinstance(balls, list) or len(balls) != 16:
                raise LobbyError(400, "The pool table needs all 16 balls.")
            clean = [[round(_num(b[0], -2, 2), 4), round(_num(b[1], -1, 1), 4), 1 if b[2] else 0] if isinstance(b, list) and len(b) == 3 else [0, 0, 1] for b in balls]
            push = body.get("push") or [0, 0]
            lob["pool"] = {
                "n": lob["pool"]["n"] + 1,
                "by": me["name"],
                "uid": user_id,
                "balls": clean,
                "push": [round(_num(push[0], -8, 8), 4), round(_num(push[1], -8, 8), 4)] if isinstance(push, list) and len(push) == 2 else [0, 0],
            }
            return 200, {"n": lob["pool"]["n"]}
    raise LobbyError(404, "Not found")


def _set_tv(lob, body, name):
    tv = lob["tv"]
    tv.update(
        item=re.sub(r"[^0-9A-Za-z-]", "", str(body.get("item") or ""))[:64],
        title=_text(body.get("title"), 120),
        kids_ok=bool(body.get("kids_ok")),
        playing=False,
        pos=_num(body.get("pos"), 0, 60 * 60 * 12),
        at=time.time(),
        by=name or "",
    )
    tv["n"] += 1
    # Kids who can't watch the new title leave the room (their browser shows why)
    if not tv["kids_ok"]:
        for uid in [u for u, p in lob["people"].items() if p.get("kids")]:
            lob["people"].pop(uid)
