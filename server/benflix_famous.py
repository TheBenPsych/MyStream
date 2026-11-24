"""
Glowfin Famous lists (Idea Box 28, Ben 2026-11-24): the Famous lists tab on Movies and Shows.

How it works, in plain words:
- Once a week Tower gathers a few well known lists and keeps them, so the tab opens straight away:
    * Movies: IMDb Top 250, every Best Picture winner at the Oscars, every Palme d'Or winner (Cannes).
    * Shows: IMDb Top 250 TV, every Emmy winner for Best Drama and for Best Comedy.
  IMDb's charts come from imdb.com's own chart page. The award winners come from Wikidata (free, no key).
  IMDb numbers are turned into TMDB numbers with Wikidata too, so Seerr and Glowfin know each title.
- Each title's name, year and poster come from Seerr (kept for a month).
- Which titles are already on Glowfin is checked with Jellyfin every 10 minutes, so each person sees
  "You have 112 of 250" and every title that isn't here yet opens its Ask page.
- Not on Kids.

Saved beside the skip times (/skips/famous) as lists.json and titles.json. Only uses the Python standard library.
"""

import json
import os
import re
import threading
import time
import urllib.error
import urllib.parse
import urllib.request

UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
WIKIDATA = "https://query.wikidata.org/sparql"
WEEK = 7 * 24 * 3600
MONTH = 30 * 24 * 3600

# (key, name on the row, where it comes from, what it is)
LISTS = {
    "movies": [
        ("imdb250", "IMDb Top 250", ("imdb", "https://www.imdb.com/chart/top/"), "movie"),
        ("oscars", "Best Picture winners", ("award", "Academy Award for Best Picture"), "movie"),
        ("palme", "Palme d'Or winners", ("award", "Palme d'Or"), "movie"),
    ],
    "shows": [
        ("imdb250tv", "IMDb Top 250 TV", ("imdb", "https://www.imdb.com/chart/toptv/"), "tv"),
        ("emmydrama", "Emmy winners: Best Drama", ("award", "Primetime Emmy Award for Outstanding Drama Series"), "tv"),
        ("emmycomedy", "Emmy winners: Best Comedy", ("award", "Primetime Emmy Award for Outstanding Comedy Series"), "tv"),
    ],
}

_jf = None  # helper's jellyfin_call(method, path, token=None, body=None, device_id=None) -> (status, bytes, type)
_admin = None  # () -> (token, user id)
_seerr_link = None  # (token) -> (urls, key) or None
_seerr = None  # (link, method, path) -> json
_dir = None
_lock = threading.RLock()
_lists = {}  # key -> {"at": time, "items": [{"id": tmdb, "type": "movie"|"tv"}]}
_titles = {}  # "movie-123" -> {"title", "year", "poster", "at"}
_owned = {"at": 0, "movie": {}, "tv": {}}  # tmdb -> Jellyfin id
_status = {"busy": False, "last": None, "problem": None}


def log(text):
    print("famous: " + text, flush=True)


def _pick_dir():
    for d in (os.environ.get("FAMOUS_DIR"), "/skips/famous", "/tracks/benflix-famous", "/tmp/benflix-famous"):
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


def _read(name, default):
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
    tmp = os.path.join(_dir, name + ".tmp")
    try:
        with open(tmp, "w") as f:
            json.dump(data, f)
        os.replace(tmp, os.path.join(_dir, name))
    except OSError as e:
        log(f"couldn't save {name}: {e}")


def _get(url, timeout=60, headers=None):
    req = urllib.request.Request(url, headers=dict({"User-Agent": UA, "Accept-Language": "en-US,en;q=0.9"}, **(headers or {})))
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.read().decode("utf-8", "replace")


def _sparql(query):
    url = WIKIDATA + "?" + urllib.parse.urlencode({"format": "json", "query": query})
    data = json.loads(_get(url, timeout=90, headers={"Accept": "application/sparql-results+json", "User-Agent": "Glowfin-FamousLists/1.0 (home media server)"}))
    return (data.get("results") or {}).get("bindings") or []


def _val(row, name):
    return str((row.get(name) or {}).get("value") or "")


# ---------------------------------------------------------------- gathering


def _imdb_chart(url):
    """The tt numbers on an IMDb chart page, in order"""
    page = _get(url)
    ids = []
    for block in re.findall(r'<script[^>]*type="application/ld\+json"[^>]*>(.*?)</script>', page, re.S):
        try:
            data = json.loads(block)
        except ValueError:
            continue
        for el in data.get("itemListElement") or []:
            item = el.get("item") if isinstance(el, dict) else None
            m = re.search(r"(tt\d+)", str((item or {}).get("url") or (item or {}).get("@id") or ""))
            if m and m.group(1) not in ids:
                ids.append(m.group(1))
    if not ids:
        # The page's own data, when the ld+json list is missing
        m = re.search(r'<script id="__NEXT_DATA__"[^>]*>(.*?)</script>', page, re.S)
        if m:
            for tt in re.findall(r'"id":"(tt\d+)"', m.group(1)):
                if tt not in ids:
                    ids.append(tt)
    return ids[:250]


def _imdb_to_tmdb(ids, kind):
    """IMDb tt numbers -> TMDB numbers, through Wikidata (P345 IMDb, P4947 TMDB movie, P4983 TMDB show)"""
    prop = "P4947" if kind == "movie" else "P4983"
    out = {}
    for i in range(0, len(ids), 100):
        part = ids[i : i + 100]
        values = " ".join(f'"{tt}"' for tt in part)
        rows = _sparql(f"SELECT ?imdb ?tmdb WHERE {{ VALUES ?imdb {{ {values} }} ?item wdt:P345 ?imdb . ?item wdt:{prop} ?tmdb . }}")
        for r in rows:
            tt, tmdb = _val(r, "imdb"), _val(r, "tmdb")
            if tt and tmdb.isdigit() and tt not in out:
                out[tt] = int(tmdb)
    return out


def _award(label, kind):
    """Every winner of an award, newest first, as TMDB numbers"""
    prop = "P4947" if kind == "movie" else "P4983"
    safe = label.replace("\\", "").replace('"', '\\"')
    rows = _sparql(
        f'SELECT ?item ?tmdb ?time WHERE {{ ?award rdfs:label "{safe}"@en . ?item p:P166 ?st . ?st ps:P166 ?award . '
        f"?item wdt:{prop} ?tmdb . OPTIONAL {{ ?st pq:P585 ?time }} }}"
    )
    best = {}
    for r in rows:
        tmdb = _val(r, "tmdb")
        if not tmdb.isdigit():
            continue
        when = _val(r, "time")[:10]
        key = int(tmdb)
        if key not in best or when > best[key]:
            best[key] = when
    return [k for k, _ in sorted(best.items(), key=lambda kv: kv[1], reverse=True)]


def _gather_one(key, source, kind):
    how, what = source
    if how == "imdb":
        ids = _imdb_chart(what)
        if not ids:
            raise OSError("IMDb's chart page had no titles")
        mapping = _imdb_to_tmdb(ids, kind)
        tmdbs = [mapping[tt] for tt in ids if tt in mapping]
    else:
        tmdbs = _award(what, kind)
    if not tmdbs:
        raise OSError("the list came back empty")
    return [{"id": t, "type": kind} for t in dict.fromkeys(tmdbs)]


def gather(force=False):
    """Weekly: gathers every list (keeps the old one when a source can't be reached), then looks up any new titles"""
    with _lock:
        if _status["busy"]:
            return
        _status["busy"] = True
    problems = []
    try:
        for page in LISTS.values():
            for key, _name, source, kind in page:
                old = _lists.get(key) or {}
                if not force and old.get("items") and time.time() - old.get("at", 0) < WEEK:
                    continue
                try:
                    items = _gather_one(key, source, kind)
                    with _lock:
                        _lists[key] = {"at": time.time(), "items": items}
                    log(f"{key}: {len(items)} titles")
                except (urllib.error.URLError, OSError, ValueError) as e:
                    problems.append(f"{key}: {e}")
                    log(f"couldn't gather {key}: {e}")
        with _lock:
            _write("lists.json", _lists)
        _fill_titles()
        _status["last"] = time.time()
        _status["problem"] = "; ".join(problems) or None
    finally:
        _status["busy"] = False


def _fill_titles():
    """Name, year and poster for each title, from Seerr (kept for a month)"""
    token, _user = _admin() if _admin else (None, None)
    if not token or not _seerr_link:
        return
    link = _seerr_link(token)
    if not link:
        log("Seerr isn't set up in Jellyfin Enhanced, so the lists have no posters yet")
        return
    wanted = []
    with _lock:
        for entry in _lists.values():
            for it in entry.get("items") or []:
                k = f"{it['type']}-{it['id']}"
                got = _titles.get(k)
                if not got or time.time() - got.get("at", 0) > MONTH:
                    wanted.append(it)
    changed = 0
    for it in wanted:
        k = f"{it['type']}-{it['id']}"
        try:
            d = _seerr(link, "GET", f"/{it['type']}/{it['id']}") or {}
        except (urllib.error.URLError, OSError, ValueError) as e:
            if isinstance(e, urllib.error.HTTPError) and e.code == 404:
                with _lock:
                    _titles[k] = {"title": None, "at": time.time()}
            continue
        date = str(d.get("releaseDate") or d.get("firstAirDate") or "")
        with _lock:
            _titles[k] = {
                "title": d.get("title") or d.get("name"),
                "year": date[:4] if date[:4].isdigit() else None,
                "poster": d.get("posterPath"),
                "at": time.time(),
            }
        changed += 1
        if changed % 50 == 0:
            with _lock:
                _write("titles.json", _titles)
        time.sleep(0.15)
    if changed:
        with _lock:
            _write("titles.json", _titles)
        log(f"looked up {changed} titles")


def _refresh_owned():
    """What's on Glowfin, by TMDB number (checked at most every 10 minutes)"""
    if time.time() - _owned["at"] < 600:
        return
    token, _user = _admin() if _admin else (None, None)
    if not token or not _jf:
        return
    q = urllib.parse.urlencode(
        {"Recursive": "true", "IncludeItemTypes": "Movie,Series", "Fields": "ProviderIds", "EnableImages": "false", "EnableUserData": "false"}
    )
    try:
        status, data, _ = _jf("GET", "/Items?" + q, token=token, device_id="benflix-helper")
    except Exception as e:  # noqa: BLE001 (Jellyfin down: keep the old answer)
        log(f"couldn't ask Jellyfin what's here: {e}")
        return
    if status != 200:
        return
    movie, tv = {}, {}
    for it in json.loads(data or b"{}").get("Items") or []:
        tmdb = str((it.get("ProviderIds") or {}).get("Tmdb") or "")
        if not tmdb.isdigit():
            continue
        (movie if it.get("Type") == "Movie" else tv).setdefault(int(tmdb), it.get("Id"))
    _owned.update({"at": time.time(), "movie": movie, "tv": tv})


# ---------------------------------------------------------------- the page


def page(which):
    _refresh_owned()
    rows = []
    with _lock:
        for key, name, _source, kind in LISTS.get(which, []):
            entry = _lists.get(key) or {}
            items = []
            have = 0
            for it in entry.get("items") or []:
                t = _titles.get(f"{it['type']}-{it['id']}") or {}
                if not t.get("title"):
                    continue
                jf = _owned[it["type"]].get(it["id"])
                if jf:
                    have += 1
                items.append(
                    {
                        "id": it["id"],
                        "mediaType": it["type"],
                        "title": t.get("title"),
                        "year": t.get("year"),
                        "posterPath": t.get("poster"),
                        "jellyfinId": jf,
                        "rank": len(items) + 1,
                    }
                )
            if items:
                rows.append({"key": key, "title": name, "have": have, "total": len(items), "items": items})
    return {"rows": rows, "ready": bool(rows), "busy": _status["busy"]}


def route(method, path, is_kids):
    """GET /api/famous/movies or /api/famous/shows"""
    if is_kids:
        return 403, {"error": "Famous lists aren't on Kids."}
    which = path.rsplit("/", 1)[-1]
    if method != "GET" or which not in LISTS:
        return 404, {"error": "No such list."}
    if not _lists and not _status["busy"]:
        threading.Thread(target=gather, daemon=True).start()
    return 200, page(which)


def _loop():
    time.sleep(90)
    while True:
        try:
            gather()
        except Exception as e:  # noqa: BLE001 (never let the weekly job die)
            log(f"problem: {e}")
        time.sleep(6 * 3600)


def start(jellyfin_call, admin, seerr_link, seerr):
    global _jf, _admin, _seerr_link, _seerr, _dir
    _jf, _admin, _seerr_link, _seerr = jellyfin_call, admin, seerr_link, seerr
    _dir = _pick_dir()
    _lists.update(_read("lists.json", {}))
    _titles.update(_read("titles.json", {}))
    threading.Thread(target=_loop, daemon=True, name="famous").start()
