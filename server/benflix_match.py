"""
Glowfin "94% for you" (Idea Box 28, Ben 2026-11-24): how well a movie or show fits the person watching.

How it works, in plain words:
- For each person, Tower looks at what they loved (favourite or thumbs up), what they watched, and what they
  gave a thumbs down. Each title brings its genres, its director and main actors, and its studio.
- A title's match is how much it shares with the closest titles the person loved or watched (thumbs down ones
  pull it down), shown as a number between 55% and 98%, with the one title it's most like ("Because you
  loved Arrival").
- Nothing shows until the person has watched or liked at least 8 movies or shows, so new profiles don't get
  made-up numbers. Kids never see it.
- Worked out on Tower from Jellyfin only (no outside sites). Each person's taste is kept for an hour.

Only uses the Python standard library.
"""

import json
import math
import threading
import time
import urllib.parse

MIN_SIGNALS = 8
TASTE_AGE = 3600
_jf = None  # helper jellyfin_call(method, path, token=None)
_lock = threading.RLock()
_taste = {}  # user id -> {"at", "items": [(features, weight, name, loved)]}
_answers = {}  # (user, item) -> (time, answer)


def _get(path, token):
    status, data, _ = _jf("GET", path, token=token)
    if status != 200:
        raise OSError(f"Jellyfin said {status}")
    return json.loads(data or b"{}")


def _features(it):
    """genre:, person: (directors and the first 5 actors) and studio: keys, with how much each counts"""
    out = {}
    for g in it.get("Genres") or []:
        out["g:" + str(g).lower()] = 1.0
    actors = 0
    for p in it.get("People") or []:
        kind = p.get("Type")
        if kind == "Director" or kind == "Creator":
            out["p:" + str(p.get("Name"))] = 2.0
        elif kind == "Actor" and actors < 5:
            actors += 1
            out["p:" + str(p.get("Name"))] = 1.5
    for s in (it.get("Studios") or [])[:2]:
        out["s:" + str(s.get("Name") if isinstance(s, dict) else s).lower()] = 0.8
    return out


def _rows_for(user, token):
    """What the person watched, loved, started or gave a thumbs down. Asked as four small lists (only the titles
    that count) instead of every movie and show with its cast, which kept Jellyfin busy for many seconds and made
    every page slower while it ran (Ben 2026-11-24 speed check)."""
    base = {
        "Recursive": "true",
        "IncludeItemTypes": "Movie,Series",
        "Fields": "Genres,People,Studios",
        "EnableImages": "false",
        "EnableUserData": "true",
    }
    lists = (
        ("IsPlayed", "DatePlayed", 450),
        ("IsFavoriteOrLikes", "DatePlayed", 200),
        ("IsResumable", "DatePlayed", 80),
        ("Dislikes", "DatePlayed", 120),
    )
    seen = set()
    rows = []
    for flt, sort, limit in lists:
        q = urllib.parse.urlencode(dict(base, Filters=flt, SortBy=sort, SortOrder="Descending", Limit=str(limit)))
        for it in _get(f"/Users/{user}/Items?{q}", token).get("Items") or []:
            if it.get("Id") in seen:
                continue
            seen.add(it.get("Id"))
            ud = it.get("UserData") or {}
            likes = ud.get("Likes")
            loved = bool(ud.get("IsFavorite")) or likes is True
            watched = bool(ud.get("Played")) or (ud.get("PlayCount") or 0) > 0 or (ud.get("PlayedPercentage") or 0) > 20
            if likes is False:
                weight = -1.5
            elif loved:
                weight = 1.6
            elif watched:
                weight = 1.0
            else:
                continue
            f = _features(it)
            if f:
                rows.append((f, weight, it.get("Name") or "", loved, it.get("Id")))
    return rows[:600]


_building = set()  # users whose taste is being worked out right now (only one at a time per person)


def _build(user, token):
    try:
        got = {"at": time.time(), "items": _rows_for(user, token)}
        with _lock:
            _taste[user] = got
    except (OSError, ValueError):
        pass
    finally:
        with _lock:
            _building.discard(user)


def _taste_for(user, token):
    """The person's taste, or None while Tower is still working it out. Never makes a page wait: the first ask
    starts the work in the background (once, however many titles ask at the same time), and an hour-old taste
    keeps being used while a fresh one is worked out."""
    with _lock:
        got = _taste.get(user)
        fresh = got is not None and time.time() - got["at"] < TASTE_AGE
        if not fresh and user not in _building:
            _building.add(user)
            threading.Thread(target=_build, args=(user, token), name="match-taste", daemon=True).start()
    return got


def _sim(a, b):
    shared = sum(min(a[k], b[k]) for k in a.keys() & b.keys())
    if not shared:
        return 0.0
    return shared / math.sqrt(sum(a.values()) * sum(b.values()))


def match(user, token, item_id):
    """{"pct": 94, "why": "Because you loved Arrival"} or {"pct": None} when there isn't enough to go on"""
    key = (user, item_id)
    with _lock:
        got = _answers.get(key)
        if got and time.time() - got[0] < TASTE_AGE:
            return got[1]
    taste = _taste_for(user, token)
    if taste is None:
        # Still working out this person's taste (a few seconds, once an hour): ask again in a moment
        return {"pct": None, "later": True}
    positives = [r for r in taste["items"] if r[1] > 0]
    answer = {"pct": None}
    if len(positives) >= MIN_SIGNALS:
        it = _get(f"/Users/{user}/Items/{item_id}", token)
        if it.get("Type") in ("Movie", "Series"):
            f = _features(it)
            near = sorted(
                ((_sim(f, r[0]) * r[1], r) for r in taste["items"] if r[4] != it.get("Id")),
                key=lambda x: x[0],
                reverse=True,
            )
            top = [s for s, _ in near[:5] if s > 0]
            bad = sum(-s for s, r in near if r[1] < 0 and s < -0.15)
            raw = (sum(top) / 5.0 if top else 0.0) - bad * 0.25
            # raw is about 0 (nothing alike) to about 0.7 (very alike): spread that over 55% to 98%
            pct = int(round(55 + 43 * max(0.0, min(1.0, raw / 0.55))))
            answer = {"pct": pct}
            best = next((r for s, r in near if s > 0 and r[1] > 0), None)
            if best is not None and pct >= 70:
                answer["why"] = ("Because you loved " if best[3] else "Because you watched ") + best[2]
    with _lock:
        _answers[key] = (time.time(), answer)
        if len(_answers) > 5000:
            for k in sorted(_answers, key=lambda k: _answers[k][0])[:1000]:
                _answers.pop(k, None)
    return answer


def route(method, path, query, token, user, is_kids):
    """GET /api/match/<jellyfin item id>"""
    if method != "GET":
        return 404, {"error": "No such page."}
    if is_kids or not user or not token:
        return 200, {"pct": None}
    item_id = path.rsplit("/", 1)[-1].replace("-", "")
    if not item_id.isalnum() or len(item_id) != 32:
        return 400, {"error": "That's not a title."}
    try:
        return 200, match(user, token, item_id)
    except (OSError, ValueError) as e:
        return 200, {"pct": None, "problem": str(e)}


def start(jellyfin_call):
    global _jf
    _jf = jellyfin_call
