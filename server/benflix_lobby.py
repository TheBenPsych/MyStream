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

Rules: up to 8 people a lobby. Kids profiles can join only when the TV is showing something Kids may watch,
can't open a lobby, and chat with ready-made phrases instead of typing. A lobby closes 15 minutes after the
last person leaves. Only uses the Python standard library.
"""

import random
import re
import threading
import time

MAX_PEOPLE = 8
MAX_LOBBIES = 6
GONE_AFTER = 12  # seconds without hearing from a browser = that person left
CLOSE_AFTER = 15 * 60  # an empty lobby closes after this long
CHAT_KEEP = 60
ROOMS = ("den", "theater", "roof")
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
                "style": spot.get("style") if spot.get("style") in STYLES else "pebble",
                "color": _colour(spot.get("color")),
                "act": _text(spot.get("act"), 12),
                "seen": time.time(),
            })
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
                    {"id": uid, "name": p["name"], "color": p["color"], "style": p["style"], "x": p["x"], "z": p["z"], "yaw": p["yaw"], "sit": p["sit"], "act": p["act"], "kids": p["kids"]}
                    for uid, p in lob["people"].items() if uid != user_id and "x" in p
                ],
                "chat": [c for c in lob["chat"] if c["n"] > since],
                "chat_n": lob["chat_n"],
                "tv": _public_tv(lob),
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
                _say(lob, None, f"{me['name']} moved everyone to the {dict(den='living room', theater='home theater', roof='rooftop')[body['room']]}")
            if "anyone_remote" in body:
                lob["anyone_remote"] = bool(body["anyone_remote"])
            return 200, {"room": lob["room"], "anyone_remote": lob["anyone_remote"]}
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
