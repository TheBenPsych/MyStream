"""
Benflix "Did you know" facts for every movie and show on Tower.

Loaded by benflix-games-helper.py (it sits next to it on Tower). Three jobs:
  * Keeps a list of short facts for each movie and show. Two kinds, mixed:
      - hand-checked facts from the Benflix repo (server/facts/title-facts.json), downloaded again every
        night so new ones arrive without running the setup command
      - behind-the-scenes facts found on the title's Wikipedia page (Production, Filming, Casting...).
        The page is found through Wikidata by the title's IMDb or TMDB number, so it's the right page.
  * Every night (and once soon after the helper starts) it looks for movies and shows it hasn't looked up
    yet, so new titles get facts by the next morning.
  * Answers the apps: /api/facts/<item id> gives that title's facts (a show's episodes and seasons use the
    show's facts). /facts/titles.txt (at home only) lists every title and how many facts it has.

Facts stay short (they're shown on a TV in a small spot), never about endings or deaths (no spoilers),
and never news-style lines like release dates or box office.

Saves its notes in /skips/facts (next to the skip times).
"""

import json
import os
import re
import threading
import time
import urllib.parse
import urllib.request

_jf = None  # The helper's jellyfin_call(method, path, token=None, body=None) -> (status, bytes, type)
_admin_from_helper = None  # () -> (token, user id) of the admin, known once they've signed in
_dir = None
_lock = threading.RLock()
_log_lines = []
_gathering = threading.Event()
_checked = {}  # normalised "name year" or "tt123" -> [facts]
_auto = {}  # Jellyfin item id -> {"name", "year", "type", "facts", "at", "page"}
_titles = {}  # Jellyfin item id -> {"name", "year", "type", "imdb", "tmdb"}

CHECKED_URL = "https://raw.githubusercontent.com/TheBenPsych/MyStream/main/server/facts/title-facts.json"
UA = "Benflix/1.0 (home media app; https://github.com/TheBenPsych/MyStream)"
PER_NIGHT = 1500  # Wikipedia lookups in one night (about one a second, so well under an hour)
RETRY_DAYS = 30  # titles with no facts are looked at again after this long (Wikipedia pages grow)
MAX_AUTO = 6
MAX_LEN = 170
MIN_LEN = 45

SECTIONS = re.compile(
    r"\n== *(Production|Development|Filming|Casting|Pre-production|Writing|Design|Visual effects|Effects|"
    r"Special effects|Music|Soundtrack|Animation|Conception|Origins?|Background|Behind the scenes)[^=]*==\n",
    re.IGNORECASE,
)
GOOD_WORDS = (
    "budget", "originally", "improvis", "real ", "actual", "practical", "record", "inspired", "nickname",
    "first time", "props", "turned down", "accident", "injur", "secret", "replaced", "auditioned", "ad-lib",
    "by hand", "for real", "animatronic", "puppet", "trained", "learned to", "did his own", "did her own",
    "stunt", "costume", "makeup", "prosthetic", "weeks", "hours", "built", "hand-made", "handmade",
    "based on", "named after", "cameo", "rejected", "almost", "nearly", "instead of", "reshot", "rewrote",
    "voice", "sound of", "recorded", "composed", "shaved", "gained", "lost ", "pounds", "kilograms",
    "insisted", "refused", "favorite", "favourite", "tribute", "homage", "easter egg",
)
BORING_WORDS = (
    "was asked", "announced", "confirmed", "in talks", "negotiat", "reported", "principal photography",
    "wrapped", "release date", "box office", "joined the cast", "was cast", "were cast", "signed on",
    "set to", "expected to", "would return", "screenplay", "deal", "years ago", "anniversary", "years old",
    "premiere", "grossed", "distribution", "distributor", "rights to", "trailer", "teaser", "streaming",
    "according to", "citation", "lawsuit", "sued", "allegation",
)
SPOILER_WORDS = (
    "ending", "twist", "dies", "death of", "killed", "is killed", "final scene", "last scene", "climax",
    "reveal", "spoiler", "murder", "suicide", "funeral", "the end of the film", "the end of the series",
)
BAD_STARTS = ("He", "She", "It", "They", "This", "These", "That", "His", "Her", "Their", "However", "Also",
              "In addition", "Additionally", "As a result", "Later", "The scene", "The film", "The series", "The show")
SENTENCE_END = re.compile(r"(?<=[.!?])(?<!\b[A-Z]\.)(?<!\b(?:Mr|Ms|Dr|St|Jr|Sr|vs|Mt|No)\.)(?<!\bMrs\.)\s+(?=[A-Z0-9\"“])")


def log(text):
    line = time.strftime("%H:%M:%S ") + text
    print("facts: " + text, flush=True)
    with _lock:
        _log_lines.append(line)
        del _log_lines[:-40]


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


def start(jellyfin_call, admin):
    """Called once by the helper when it starts. admin() gives (token, user id) of the admin."""
    global _jf, _admin_from_helper, _dir, _auto, _titles
    _jf, _admin_from_helper = jellyfin_call, admin
    _dir = _pick_dir()
    if not _dir:
        log("no folder to save facts in, so facts are off")
        return
    _auto = _read("auto.json", {}) or {}
    _titles = _read("titles.json", {}) or {}
    _load_checked(_read("checked.json", {}) or {})
    threading.Thread(target=_nightly, name="facts-nightly", daemon=True).start()
    log(f"ready ({len(_auto)} titles looked up, saving in {_dir})")


def remember_admin(token, user_id):
    if not _dir or not token:
        return
    saved = _read("admin.json", {}) or {}
    if saved.get("token") != token:
        _write("admin.json", {"token": token, "user": user_id})


def _admin():
    token, user = (_admin_from_helper() if _admin_from_helper else (None, None)) or (None, None)
    if not token:
        saved = _read("admin.json", {}) or {}
        token, user = saved.get("token"), saved.get("user")
    return token, user


# ---------- names ----------

def _norm(text):
    return re.sub(r"[^a-z0-9]+", " ", (text or "").lower().replace("&", " and ")).strip()


def _keys(name, year, imdb=None):
    keys = []
    if imdb:
        keys.append(imdb.lower())
    if year:
        keys.append(f"{_norm(name)} {year}")
    keys.append(_norm(name))
    return keys


def _load_checked(data):
    global _checked
    found = {}
    for key, facts in (data or {}).items():
        if key.startswith("_") or not isinstance(facts, list):
            continue
        clean = [f.strip() for f in facts if isinstance(f, str) and f.strip()]
        if not clean:
            continue
        # "Five Nights at Freddy's (2023)" -> "five nights at freddy s 2023"; "tt4589218" stays as it is
        m = re.fullmatch(r"\s*(.+?)\s*\((\d{4})\)\s*", key)
        k = key.lower().strip() if re.fullmatch(r"tt\d+", key.strip().lower()) else (f"{_norm(m.group(1))} {m.group(2)}" if m else _norm(key))
        found[k] = clean
    _checked = found


def _checked_for(t):
    for k in _keys(t.get("name"), t.get("year"), t.get("imdb")):
        if k in _checked:
            return _checked[k]
    return []


# ---------- web ----------

def _http_json(url, timeout=20):
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "application/json"})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return json.loads(r.read().decode("utf-8", "replace"))


def _refresh_checked():
    try:
        req = urllib.request.Request(CHECKED_URL, headers={"User-Agent": UA, "Cache-Control": "no-cache"})
        with urllib.request.urlopen(req, timeout=30) as r:
            data = json.loads(r.read().decode("utf-8"))
        _write("checked.json", data)
        _load_checked(data)
        log(f"hand-checked facts: {len(_checked)} titles")
    except Exception as e:  # noqa: BLE001
        log(f"couldn't download the hand-checked facts ({e}); using the saved copy")


def _wikidata_page(imdb=None, tmdb=None, kind="Movie"):
    """The English Wikipedia page name for this IMDb / TMDB number, through Wikidata"""
    tries = []
    if imdb:
        tries.append(f"haswbstatement:P345={imdb}")
    if tmdb:
        tries.append(f"haswbstatement:{'P4947' if kind == 'Movie' else 'P4983'}={tmdb}")
    for q in tries:
        try:
            found = _http_json(
                "https://www.wikidata.org/w/api.php?"
                + urllib.parse.urlencode({"action": "query", "list": "search", "srsearch": q, "format": "json", "srlimit": 1})
            )
            hits = (found.get("query") or {}).get("search") or []
            if not hits:
                continue
            qid = hits[0]["title"]
            ent = _http_json(
                "https://www.wikidata.org/w/api.php?"
                + urllib.parse.urlencode({"action": "wbgetentities", "ids": qid, "props": "sitelinks", "sitefilter": "enwiki", "format": "json"})
            )
            link = (((ent.get("entities") or {}).get(qid) or {}).get("sitelinks") or {}).get("enwiki")
            if link and link.get("title"):
                return link["title"]
        except Exception as e:  # noqa: BLE001
            log(f"lookup failed: {e}")
            continue
    return None


def _wikipedia_text(title):
    data = _http_json(
        "https://en.wikipedia.org/w/api.php?"
        + urllib.parse.urlencode({"action": "query", "prop": "extracts", "explaintext": 1, "redirects": 1, "format": "json", "titles": title})
    )
    pages = (data.get("query") or {}).get("pages") or {}
    for key, page in pages.items():
        if key != "-1" and page.get("extract"):
            return page["extract"]
    return None


def _guess_page(name, year, kind):
    word = "film" if kind == "Movie" else "series"
    guesses = [f"{name} ({year} film)", f"{name} (film)", name] if kind == "Movie" else [
        f"{name} ({year} TV series)", f"{name} (TV series)", name]
    for title in guesses if year else guesses[1:]:
        try:
            text = _wikipedia_text(title)
        except Exception as e:  # noqa: BLE001
            log(f"lookup failed: {e}")
            continue
        if text and word in text[:1500].lower():
            return title, text
    return None, None


def _complete(s):
    if not s.endswith((".", "!", "?", ".”", '."')):
        return False
    if re.search(r"(^|\s)[A-Z]\.$", s):
        return False
    if s.count("(") != s.count(")") or s.count("“") != s.count("”") or s.count('"') % 2:
        return False
    return s[0].isupper() or s[0].isdigit()


def _facts_from(name, text):
    picked = []
    for m in SECTIONS.finditer(text):
        rest = text[m.end() - 1:]
        end = re.search(r"\n== [^=]+ ==\n", rest)
        section = re.sub(r"\n=+[^=]+=+\n", "\n", rest[: end.start() if end else len(rest)])
        for s in SENTENCE_END.split(section.replace("\n", " ")):
            s = re.sub(r"\s+", " ", s).strip()
            low = s.lower()
            if not (MIN_LEN <= len(s) <= MAX_LEN) or not _complete(s):
                continue
            if not any(w in low for w in GOOD_WORDS):
                continue
            if any(w in low for w in BORING_WORDS) or any(w in low for w in SPOILER_WORDS):
                continue
            if any(s.startswith(b + " ") for b in BAD_STARTS):
                continue
            if not s.lower().startswith(name.lower()) and name.lower() not in low and len(s) + len(name) + 2 <= MAX_LEN:
                s = f"{name}: {s}"
            if s not in picked:
                picked.append(s)
    # Lines that name the title read best on their own, so they go first
    picked.sort(key=lambda s: 0 if not s.startswith(name + ":") else 1)
    return picked[:MAX_AUTO]


def _look_up(item_id, t):
    page = _wikidata_page(t.get("imdb"), t.get("tmdb"), t.get("type"))
    text = None
    if page:
        try:
            text = _wikipedia_text(page)
        except Exception:  # noqa: BLE001
            text = None
    if not text:
        page, text = _guess_page(t["name"], t.get("year"), t.get("type"))
    facts = _facts_from(t["name"], text) if text else []
    with _lock:
        _auto[item_id] = {"name": t["name"], "year": t.get("year"), "type": t.get("type"), "facts": facts, "at": int(time.time()), "page": page}
    return facts


# ---------- the library ----------

def _library():
    token, user = _admin()
    if not token or not user:
        return None
    q = urllib.parse.urlencode({
        "Recursive": "true",
        "IncludeItemTypes": "Movie,Series",
        "Fields": "ProviderIds,ProductionYear",
        "EnableImages": "false",
        "EnableUserData": "false",
    })
    status, data, _ = _jf("GET", f"/Users/{user}/Items?{q}", token=token)
    if status != 200:
        return None
    found = {}
    for it in (json.loads(data or b"{}").get("Items") or []):
        # Jellyfin can send collections too; facts are only for movies and shows
        if it.get("Type") not in ("Movie", "Series"):
            continue
        ids = it.get("ProviderIds") or {}
        found[it["Id"].replace("-", "")] = {
            "name": it.get("Name") or "",
            "year": it.get("ProductionYear"),
            "type": it.get("Type"),
            "imdb": ids.get("Imdb") or ids.get("imdb"),
            "tmdb": ids.get("Tmdb") or ids.get("tmdb"),
        }
    return found


def gather(why="night"):
    """Looks up every title that hasn't been looked up yet (or had nothing a month ago)"""
    if _gathering.is_set():
        return
    _gathering.set()
    try:
        _refresh_checked()
        lib = _library()
        if lib is None:
            log("Tower needs the main profile to sign in on Benflix once before facts can be gathered")
            return
        global _titles
        _titles = lib
        _write("titles.json", lib)
        now = time.time()
        todo = [
            (i, t) for i, t in lib.items()
            if t["name"] and (i not in _auto or (not _auto[i].get("facts") and now - _auto[i].get("at", 0) > RETRY_DAYS * 86400))
        ]
        done = got = 0
        for item_id, t in todo[:PER_NIGHT]:
            try:
                if _look_up(item_id, t):
                    got += 1
            except Exception as e:  # noqa: BLE001
                log(f"skipped {t['name']}: {e}")
            done += 1
            if done % 25 == 0:
                with _lock:
                    _write("auto.json", _auto)
            time.sleep(1)
        with _lock:
            _write("auto.json", _auto)
        _write("night.json", {"at": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()), "looked_up": done, "found": got, "left": max(0, len(todo) - done), "why": why})
        log(f"looked up {done} titles, {got} had facts ({max(0, len(todo) - done)} left for next time)")
    except Exception as e:  # noqa: BLE001
        log(f"couldn't gather facts: {e}")
    finally:
        _gathering.clear()


def _nightly():
    time.sleep(600)
    if _admin()[0]:
        gather("start")
    done_day = None
    while True:
        time.sleep(600)
        now = time.localtime()
        if not (4 <= now.tm_hour < 6) or done_day == now.tm_yday:
            continue
        done_day = now.tm_yday
        if _admin()[0]:
            gather()


# ---------- answers ----------

def _series_of(item_id, token):
    """A season or episode's show id (so its pages and screensaver use the show's facts), or None"""
    status, data, _ = _jf("GET", f"/Items?Ids={item_id}&Fields=ProviderIds,ProductionYear&EnableImages=false", token=token)
    if status != 200:
        return None, None
    items = json.loads(data or b"{}").get("Items") or []
    if not items:
        return None, None
    it = items[0]
    if it.get("Type") in ("Episode", "Season") and it.get("SeriesId"):
        return it["SeriesId"].replace("-", ""), None
    ids = it.get("ProviderIds") or {}
    return item_id, {"name": it.get("Name") or "", "year": it.get("ProductionYear"), "type": it.get("Type"),
                     "imdb": ids.get("Imdb"), "tmdb": ids.get("Tmdb")}


def facts_for(item_id, token):
    item_id = item_id.replace("-", "").lower()
    t = _titles.get(item_id)
    if t is None:
        sid, info = _series_of(item_id, token)
        if sid and sid != item_id:
            item_id, t = sid, _titles.get(sid)
            if t is None:
                _, t = _series_of(sid, token)
        else:
            t = info
    if not t or t.get("type") not in ("Movie", "Series"):
        return {"facts": [], "ready": True}
    checked = _checked_for(t)
    auto = (_auto.get(item_id) or {}).get("facts")
    if auto is None and not checked and _dir:
        # A brand-new title: look it up now, in the background, so it's there next time
        threading.Thread(target=lambda: _safe_look_up(item_id, t), daemon=True).start()
    seen = {_norm(f) for f in checked}
    mixed = list(checked) + [f for f in (auto or []) if _norm(f) not in seen]
    return {"facts": [{"text": f, "checked": i < len(checked)} for i, f in enumerate(mixed)], "ready": auto is not None or bool(checked)}


def _safe_look_up(item_id, t):
    try:
        _look_up(item_id, t)
        with _lock:
            _write("auto.json", _auto)
    except Exception as e:  # noqa: BLE001
        log(f"couldn't look up {t.get('name')}: {e}")


def titles_text(few=False):
    """
    Every movie and show with its fact counts, for sending to a chat (at home only). With few=True, only
    titles with fewer than 2 facts so far (the ones that need hand-checked facts most), in a short form.
    """
    lines = ["# Benflix titles and their facts: name (year) | type | IMDb | hand-checked | found on Wikipedia", ""]
    for item_id, t in sorted(_titles.items(), key=lambda kv: (kv[1].get("type") or "", _norm(kv[1].get("name")))):
        if t.get("type") not in ("Movie", "Series"):
            continue
        auto = _auto.get(item_id)
        checked = len(_checked_for(t))
        found = len(auto["facts"]) if auto else None
        if few and (auto is None or checked + found >= 2):
            continue
        lines.append(
            f"{t['name']} ({t.get('year') or '?'}) | {t.get('type')} | {t.get('imdb') or '-'} | "
            f"{checked} | {found if found is not None else 'not yet'}"
        )
    if not _titles:
        lines.append("Nothing yet. Tower gathers the list about 10 minutes after the helper starts, once Ben has signed in on Benflix.")
    return "\n".join(lines) + "\n"


def status():
    n = _read("night.json", {}) or {}
    with_facts = sum(1 for i in _titles if (_auto.get(i) or {}).get("facts") or _checked_for(_titles[i]))
    return {"titles": len(_titles), "with_facts": with_facts, "checked_titles": len(_checked), "busy": _gathering.is_set(), "last": n, "log": _log_lines[-15:]}


def _can_see(item_id, token, user_id):
    """Is this title in this person's Benflix? (Jellyfin knows each person's libraries and age limits)"""
    try:
        status, _, _ = _jf("GET", f"/Users/{user_id}/Items/{item_id}", token=token)
        return status == 200
    except Exception:  # noqa: BLE001
        return False


def home_facts(token, user_id, count=2, skip=(), longest=MAX_LEN):
    """
    Home's "Did you know" rows: [count] facts, each about a different movie or show this person can watch,
    picked at random every time Home opens. [skip] = title ids shown last time, so it changes.
    """
    import random

    skip = {s.replace("-", "").lower() for s in skip if s}
    with _lock:
        ids = [i for i, t in _titles.items() if t.get("type") in ("Movie", "Series") and i not in skip]
    random.shuffle(ids)
    out = []
    for item_id in ids[:60]:
        t = _titles[item_id]
        checked = _checked_for(t)
        auto = (_auto.get(item_id) or {}).get("facts") or []
        pool = list(checked) + [f for f in auto if _norm(f) not in {_norm(c) for c in checked}]
        # Home's rows are small: only the shorter facts
        pool = [f for f in pool if len(f) <= longest]
        if not pool:
            continue
        if not _can_see(item_id, token, user_id):
            continue
        out.append({"id": item_id, "name": t.get("name") or "", "type": t.get("type"), "fact": random.choice(pool)})
        if len(out) >= count:
            break
    return {"facts": out}


def route(method, path, query, token, user_id, is_admin):
    """/api/facts/... for the apps. Returns (status, answer)."""
    if method == "GET" and path == "/api/facts/home":
        n = max(1, min(4, int((query.get("n") or ["2"])[0] or 2)))
        skip = ",".join(query.get("skip") or []).split(",")
        longest = max(60, min(MAX_LEN, int((query.get("max") or [str(MAX_LEN)])[0] or MAX_LEN)))
        return 200, home_facts(token, user_id, n, skip, longest)
    if method == "GET" and path == "/api/facts/status":
        return 200, status()
    if method == "POST" and path == "/api/facts/gather":
        if not is_admin:
            return 403, {"error": "Only the server admin can do that."}
        remember_admin(token, user_id)
        threading.Thread(target=gather, kwargs={"why": "asked"}, name="facts-gather", daemon=True).start()
        return 200, {"message": "Gathering facts now. It takes a while the first time."}
    m = re.fullmatch(r"/api/facts/([0-9a-fA-F-]{32,36})", path)
    if method == "GET" and m:
        if is_admin:
            remember_admin(token, user_id)
        return 200, facts_for(m.group(1), token)
    return 404, {"error": "Not found"}
