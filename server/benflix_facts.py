"""
Glowfin Parents' guide on Tower (the web version can't ask IMDb itself).

Loaded by benflix-games-helper.py (it sits next to it on Tower). Answers /api/facts/parents/<IMDb id>.

"Did you know" facts were removed (Ben 2026-11-18): no fact lists, no Wikipedia look-ups, no nightly job.
/api/facts/<id> and /api/facts/home still answer with no facts, so older app versions just show nothing.
Old saved fact files are deleted once, to free up space.

Saves its notes in /skips/facts (next to the skip times).
"""

import json
import os
import re
import threading
import time
import urllib.error
import urllib.request

_dir = None
OLD_FILES = ("auto.json", "titles.json", "checked.json", "admin.json")


def log(text):
    print("facts: " + text, flush=True)


def _pick_dir():
    for d in (os.environ.get("FACTS_DIR"), "/skips/facts", "/tracks/benflix-facts"):
        if not d:
            continue
        try:
            os.makedirs(d, exist_ok=True)
            test = os.path.join(d, ".write-test")
            with open(test, "w") as f:
                f.write("ok")
            os.remove(test)
            return d
        except OSError:
            continue
    return None


def _read(name, default=None):
    if not _dir:
        return default
    try:
        with open(os.path.join(_dir, name)) as f:
            return json.load(f)
    except (OSError, ValueError, TypeError):
        return default


def _write(name, data):
    if not _dir:
        return
    path = os.path.join(_dir, name)
    tmp = path + ".tmp"
    with open(tmp, "w") as f:
        json.dump(data, f)
    os.replace(tmp, path)


def start(jellyfin_call=None, admin=None):
    """Called once by the helper when it starts."""
    global _dir
    _dir = _pick_dir()
    if not _dir:
        return
    for name in OLD_FILES:  # left over from "Did you know" facts
        try:
            os.remove(os.path.join(_dir, name))
        except OSError:
            pass
    log("ready (Parents' guide only)")


# ---------- Parents' guide ----------
# How much violence, swearing, scary bits and adult scenes a title has, from IMDb's public parents guide
# (the same one the TV and phone app shows). Kept for a month per title, in parents.json.
PARENTS_NAMES = {"VIOLENCE": "Violence", "PROFANITY": "Swearing", "FRIGHTENING": "Scary bits", "NUDITY": "Adult scenes", "ALCOHOL": "Drinking and drugs"}
PARENTS_ORDER = ["VIOLENCE", "PROFANITY", "FRIGHTENING", "NUDITY", "ALCOHOL"]
PARENTS_LEVELS = {"none": 0, "mild": 1, "moderate": 2, "severe": 3}
PARENTS_WORDS = ["None", "A little", "Some", "A lot"]
PARENTS_QUERY = ("query ParentsGuide($id: ID!) { title(id: $id) { parentsGuide { categories "
                 "{ category { id text } severity { text } } } } }")
_parents_lock = threading.Lock()


def parents_guide(imdb_id):
    """[{label, level, rank}] for an IMDb id, or [] when IMDb has no guide (or can't be reached)."""
    month = 30 * 86400
    with _parents_lock:
        saved = _read("parents.json", {}) or {}
        hit = saved.get(imdb_id)
        if isinstance(hit, dict) and time.time() - hit.get("at", 0) < month:
            return hit.get("lines") or []
    try:
        body = json.dumps({"query": PARENTS_QUERY, "variables": {"id": imdb_id}}).encode()
        req = urllib.request.Request("https://graphql.imdb.com/", data=body, method="POST", headers={
            "Content-Type": "application/json", "Referer": "https://www.imdb.com/", "User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(req, timeout=12) as r:
            data = json.loads(r.read().decode("utf-8", "replace"))
    except (urllib.error.URLError, OSError, ValueError):
        return []
    cats = ((((data or {}).get("data") or {}).get("title") or {}).get("parentsGuide") or {}).get("categories") or []
    found = {}
    for c in cats:
        cid = str(((c or {}).get("category") or {}).get("id") or "").upper()
        sev = str(((c or {}).get("severity") or {}).get("text") or "").lower()
        if cid in PARENTS_NAMES and sev in PARENTS_LEVELS:
            rank = PARENTS_LEVELS[sev]
            found[cid] = {"label": PARENTS_NAMES[cid], "level": PARENTS_WORDS[rank], "rank": rank}
    lines = [found[k] for k in PARENTS_ORDER if k in found]
    with _parents_lock:
        saved = _read("parents.json", {}) or {}
        saved[imdb_id] = {"lines": lines, "at": int(time.time())}
        if len(saved) > 5000:
            saved = dict(sorted(saved.items(), key=lambda kv: kv[1].get("at", 0))[-4000:])
        try:
            _write("parents.json", saved)
        except OSError:
            pass
    return lines


def route(method, path, query, token, user_id, is_admin):
    """/api/facts/... for the apps. Returns (status, answer)."""
    m = re.fullmatch(r"/api/facts/parents/(tt\d{5,10})", path)
    if method == "GET" and m:
        # Parents' guide for the web version (browsers can't ask IMDb themselves)
        return 200, {"lines": parents_guide(m.group(1))}
    if method == "GET" and (path == "/api/facts/home" or re.fullmatch(r"/api/facts/([0-9a-fA-F-]{32,36})", path)):
        return 200, {"facts": []}  # Did you know was removed; older apps show nothing
    return 404, {"error": "Not found"}
