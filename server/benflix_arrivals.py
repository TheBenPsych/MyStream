"""
Benflix "only really new things are new" (Ben 2026-11-17).

When Radarr or Sonarr swap a movie or episode for a better copy, Jellyfin sees a brand new file and gives it
today's "date added". Then Recently added, the NEW pills, Home's news and every "new" row on the TVs, phones
and the browser show it again as if it had just arrived. This puts the first date back.

Loaded by benflix-games-helper.py (it sits next to it on Tower).
  * Tower keeps a list of when each movie and each episode FIRST arrived (by its TMDB / IMDb / TVDB number,
    and for episodes the show plus season and episode number), so a new copy can be recognised later.
    The whole library is read into it when the helper starts and again every night.
  * Every few minutes Tower looks at what Jellyfin added lately. When something already had an earlier
    arrival date (from the list, or the first time Radarr or Sonarr brought it in, from their history),
    Tower sets Jellyfin's "date added" back to that first date, the same as editing it by hand in Jellyfin.
    Shows whose episodes were put back are refreshed, so the show's "new episodes" date goes back too.
  * First-time arrivals and brand new episodes are never touched, so they still show as new.

Saves its notes in /skips/arrivals (next to the skip times). Only uses the Python standard library.
"""

import json
import os
import re
import threading
import time
import urllib.parse
from datetime import datetime, timezone

_jf = None  # The helper's jellyfin_call(method, path, token=None, body=None) -> (status, bytes, type)
_admin_from_helper = None  # () -> (token, user id) of the admin, known once they've signed in
_tools = None  # benflix_server_tools (Radarr, Sonarr), or None
_dir = None
_lock = threading.RLock()
_first = {}  # key -> earliest arrival (seconds since 1970)
_checked = {}  # item id -> its date added when it was last checked (so each copy is only looked at once)
_arr_cache = {}  # "radarr"/"sonarr" -> (time, {lookup key -> id})
_series_keys = {}  # Jellyfin show id -> its keys
_log_lines = []
_status = {"fixed": 0, "last_check": None, "last_seed": None, "problem": None}

EVERY = 300  # seconds between looks at the newest things
LOOK_BACK = 45 * 86400  # only things Jellyfin added in the last 45 days are checked
SLACK = 6 * 3600  # an earlier date has to be at least this much earlier to count as an upgrade
SEED_EVERY = 86400


def log(text):
    print("arrivals: " + text, flush=True)
    with _lock:
        _log_lines.append(time.strftime("%Y-%m-%d %H:%M ") + text)
        del _log_lines[:-80]


# ---------- Start up and saving ----------


def _pick_dir():
    for d in (os.environ.get("ARRIVALS_DIR"), "/skips/arrivals", "/tracks/benflix-arrivals"):
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


def _save():
    with _lock:
        first, checked = dict(_first), dict(_checked)
        stats = dict(_status)
    _write("first-arrivals.json", first)
    _write("checked.json", checked)
    _write("status.json", stats)


def start(jellyfin_call, admin, tools=None):
    """Called once by the helper when it starts. admin() gives (token, user id) of the admin."""
    global _jf, _admin_from_helper, _tools, _dir
    _jf, _admin_from_helper, _tools = jellyfin_call, admin, tools
    _dir = _pick_dir()
    if not _dir:
        log("no folder to save notes in, so upgrades may still show as new")
        return
    with _lock:
        _first.update(_read("first-arrivals.json", {}) or {})
        _checked.update(_read("checked.json", {}) or {})
        _status.update(_read("status.json", {}) or {})
    threading.Thread(target=_loop, name="arrivals", daemon=True).start()
    log(f"ready ({len(_first)} first arrivals remembered)")


def _admin():
    token, user = (_admin_from_helper() if _admin_from_helper else (None, None)) or (None, None)
    return token, user


# ---------- Small helpers ----------


def _when(iso):
    """Jellyfin / Radarr time text -> seconds since 1970 (0 when there's none)"""
    if not iso:
        return 0
    try:
        text = re.sub(r"(\.\d+)", "", str(iso)).replace("Z", "+00:00")
        if not re.search(r"[+-]\d\d:\d\d$", text):
            text += "+00:00"
        return datetime.fromisoformat(text).timestamp()
    except ValueError:
        return 0


def _iso(seconds):
    return datetime.fromtimestamp(seconds, timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.0000000Z")


def _get(path, token):
    status, data, _ = _jf("GET", path, token=token)
    if status != 200:
        raise RuntimeError(f"Jellyfin answered {status} for {path.split('?')[0]}")
    return json.loads(data or b"null")


def _ids(p):
    """Lower-cased provider ids of an item"""
    return {str(k).lower(): str(v).strip() for k, v in (p or {}).items() if v not in (None, "")}


def _movie_keys(it):
    p = _ids(it.get("ProviderIds"))
    keys = [f"m:{k}:{p[k]}" for k in ("tmdb", "imdb") if p.get(k)]
    if not keys and it.get("Name"):
        keys.append(f"m:name:{it['Name'].strip().lower()}|{it.get('ProductionYear') or ''}")
    return keys


def _show_keys(series_id, token):
    """Keys of a show: its TVDB / TMDB / IMDb numbers, and its Jellyfin id as a last resort"""
    if series_id in _series_keys:
        return _series_keys[series_id]
    keys = [f"s:id:{series_id}"]
    try:
        q = urllib.parse.urlencode({"Ids": series_id, "Fields": "ProviderIds", "EnableImages": "false"})
        items = (_get("/Items?" + q, token) or {}).get("Items") or []
        if items:
            p = _ids(items[0].get("ProviderIds"))
            keys = [f"s:{k}:{p[k]}" for k in ("tvdb", "tmdb", "imdb") if p.get(k)] + keys
    except Exception:  # noqa: BLE001
        pass
    _series_keys[series_id] = keys
    return keys


def _episode_keys(it, token):
    s, e = it.get("ParentIndexNumber"), it.get("IndexNumber")
    if s is None or e is None or not it.get("SeriesId"):
        return []
    return [f"e:{k[2:]}:{s}:{e}" for k in _show_keys(it["SeriesId"], token)]


def _keys(it, token):
    kind = it.get("Type")
    if kind == "Movie":
        return _movie_keys(it)
    if kind == "Episode":
        return _episode_keys(it, token)
    return []


def _earliest(keys):
    with _lock:
        dates = [_first[k] for k in keys if k in _first]
    return min(dates) if dates else 0


def _remember(keys, when):
    if not when:
        return
    with _lock:
        for k in keys:
            if k not in _first or when < _first[k]:
                _first[k] = when


def _real(it):
    """A real file (not a missing episode Jellyfin only knows about)"""
    return it.get("LocationType") != "Virtual" and not it.get("IsMissing") and it.get("DateCreated")


# ---------- Reading the whole library (so later upgrades can be recognised) ----------


def seed(token):
    """Remembers the date added of every movie and episode in the library"""
    start_at, page, n = 0, 1000, 0
    while True:
        q = urllib.parse.urlencode(
            {
                "IncludeItemTypes": "Movie,Episode",
                "Recursive": "true",
                "Fields": "ProviderIds,DateCreated",
                "EnableImages": "false",
                "EnableUserData": "false",
                "IsMissing": "false",
                "StartIndex": str(start_at),
                "Limit": str(page),
            }
        )
        items = (_get("/Items?" + q, token) or {}).get("Items") or []
        for it in items:
            if _real(it):
                _remember(_keys(it, token), _when(it.get("DateCreated")))
                n += 1
        if len(items) < page:
            break
        start_at += page
    with _lock:
        _status["last_seed"] = int(time.time())
    log(f"read the whole library ({n} movies and episodes)")


# ---------- Radarr and Sonarr history: when did this first arrive? ----------


def _arr(app, path):
    if _tools is None or not _tools.arr_ready(app):
        return None
    return _tools._arr(app, "GET", path, timeout=60)


def _arr_index(app):
    """Radarr: TMDB/IMDb number -> movie id. Sonarr: TVDB/TMDB/IMDb number -> show id. Kept for an hour."""
    got = _arr_cache.get(app)
    if got and time.time() - got[0] < 3600:
        return got[1]
    index = {}
    for e in _arr(app, "/api/v3/movie" if app == "radarr" else "/api/v3/series") or []:
        for k in ("tmdbId", "imdbId", "tvdbId"):
            v = e.get(k)
            if v not in (None, "", 0):
                index[f"{k[:4].lower()}:{v}"] = e.get("id")
    _arr_cache[app] = (time.time(), index)
    return index


def _first_import(records):
    """Earliest "imported" date in a list of Radarr / Sonarr history records"""
    dates = [_when(r.get("date")) for r in records or [] if str(r.get("eventType", "")).lower() in ("downloadfolderimported", "3")]
    dates = [d for d in dates if d]
    return min(dates) if dates else 0


def _movie_first_import(it):
    index = _arr_index("radarr")
    if not index:
        return 0
    p = _ids(it.get("ProviderIds"))
    movie_id = index.get(f"tmdb:{p.get('tmdb')}") or index.get(f"imdb:{p.get('imdb')}")
    if not movie_id:
        return 0
    data = _arr("radarr", f"/api/v3/history/movie?movieId={movie_id}&eventType=3")
    return _first_import(data if isinstance(data, list) else (data or {}).get("records"))


def _show_imports(series_id, token, wanted):
    """{(season, episode): first import date} for one show, from Sonarr's history"""
    index = _arr_index("sonarr")
    if not index:
        return {}
    show = None
    for k in _show_keys(series_id, token):
        kind, _, value = k[2:].partition(":")
        show = index.get(f"{kind[:4]}:{value}")
        if show:
            break
    if not show:
        return {}
    out = {}
    for season in sorted(wanted):
        data = _arr("sonarr", f"/api/v3/history/series?seriesId={show}&seasonNumber={season}&eventType=3&includeEpisode=true")
        for r in data if isinstance(data, list) else (data or {}).get("records") or []:
            ep = r.get("episode") or {}
            d = _when(r.get("date"))
            key = (ep.get("seasonNumber"), ep.get("episodeNumber"))
            if d and key[1] is not None and (key not in out or d < out[key]):
                out[key] = d
    return out


# ---------- Putting the first date back ----------


def _set_date(item_id, when, token, user):
    """Sets Jellyfin's "date added" of one item, the same way Jellyfin's own Edit metadata page does"""
    full = None
    for path in (f"/Items/{item_id}?userId={user}", f"/Users/{user}/Items/{item_id}"):
        try:
            full = _get(path, token)
            break
        except Exception:  # noqa: BLE001
            continue
    if not full or not full.get("Id"):
        return False
    full["DateCreated"] = _iso(when)
    status, _, _ = _jf("POST", f"/Items/{item_id}", token=token, body=json.dumps(full).encode())
    return status in (200, 204)


def _refresh(item_id, token):
    """Lets Jellyfin work out a show's (or season's) "newest episode" date again from its episodes"""
    q = urllib.parse.urlencode(
        {"Recursive": "false", "MetadataRefreshMode": "Default", "ImageRefreshMode": "Default", "ReplaceAllMetadata": "false", "ReplaceAllImages": "false"}
    )
    try:
        _jf("POST", f"/Items/{item_id}/Refresh?{q}", token=token, body=b"")
    except Exception:  # noqa: BLE001
        pass


def check(token, user):
    """Looks at what Jellyfin added lately and puts the first date back on anything that was only a better copy"""
    q = urllib.parse.urlencode(
        {
            "IncludeItemTypes": "Movie,Episode",
            "Recursive": "true",
            "SortBy": "DateCreated",
            "SortOrder": "Descending",
            "Limit": "400",
            "Fields": "ProviderIds,DateCreated",
            "EnableImages": "false",
            "EnableUserData": "false",
            "IsMissing": "false",
        }
    )
    items = [it for it in (_get("/Items?" + q, token) or {}).get("Items") or [] if _real(it)]
    cut = time.time() - LOOK_BACK
    fresh = []
    for it in items:
        added = _when(it.get("DateCreated"))
        if added < cut:
            break
        with _lock:
            if abs(_checked.get(it["Id"], 0) - added) < 1:
                continue
        fresh.append((it, added))
    if not fresh:
        return 0

    # Sonarr history, one look per show and season with new copies
    wanted = {}
    for it, _ in fresh:
        if it.get("Type") == "Episode" and it.get("SeriesId") and it.get("ParentIndexNumber") is not None:
            wanted.setdefault(it["SeriesId"], set()).add(it["ParentIndexNumber"])
    imports = {}
    for sid, seasons in wanted.items():
        try:
            imports[sid] = _show_imports(sid, token, seasons)
        except Exception as e:  # noqa: BLE001
            imports[sid] = {}
            log(f"couldn't read Sonarr's history: {getattr(e, 'message', e)}")

    fixed, refresh = 0, set()
    for it, added in fresh:
        keys = _keys(it, token)
        first = _earliest(keys)
        try:
            if it.get("Type") == "Movie":
                history = _movie_first_import(it)
            else:
                history = (imports.get(it.get("SeriesId")) or {}).get((it.get("ParentIndexNumber"), it.get("IndexNumber")), 0)
        except Exception as e:  # noqa: BLE001
            history = 0
            log(f"couldn't read Radarr's history: {getattr(e, 'message', e)}")
        best = min([d for d in (first, history) if d] or [added])
        final = added
        if best < added - SLACK:
            if _set_date(it["Id"], best, token, user):
                final = best
                fixed += 1
                name = f"{it.get('SeriesName')} S{it.get('ParentIndexNumber')}E{it.get('IndexNumber')}" if it.get("Type") == "Episode" else it.get("Name")
                log(f"{name}: better copy, kept its first date {time.strftime('%Y-%m-%d', time.gmtime(best))}")
                if it.get("SeriesId"):
                    refresh.add(it["SeriesId"])
                    if it.get("SeasonId"):
                        refresh.add(it["SeasonId"])
        _remember(keys, final)
        with _lock:
            _checked[it["Id"]] = final
    for item_id in refresh:
        _refresh(item_id, token)
    with _lock:
        _status["fixed"] = int(_status.get("fixed") or 0) + fixed
        # Forget checks of things that are long gone from the newest list
        if len(_checked) > 5000:
            for k in sorted(_checked, key=_checked.get)[: len(_checked) - 4000]:
                del _checked[k]
    return fixed


def _loop():
    time.sleep(45)
    while True:
        token, user = _admin()
        if token and user:
            try:
                last_seed = _status.get("last_seed") or 0
                if not _first or time.time() - last_seed > SEED_EVERY:
                    seed(token)
                check(token, user)
                with _lock:
                    _status.update(last_check=int(time.time()), problem=None)
            except Exception as e:  # noqa: BLE001
                with _lock:
                    _status["problem"] = str(getattr(e, "message", e))
                log(f"problem: {_status['problem']}")
            try:
                _save()
            except OSError:
                pass
        time.sleep(EVERY)


def status():
    """For the Server page (admin only): how it's going"""
    with _lock:
        return {**_status, "remembered": len(_first), "log": list(_log_lines[-30:])}
