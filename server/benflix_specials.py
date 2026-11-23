"""
Glowfin "Anime movies go inside their show" (Ben 2026-11-22).

Anime movies that belong to a show (like Demon Slayer: Mugen Train) used to land in Movies, away from the show.
This puts them in the show's Specials (Season 0), at the spot TheTVDB gives them, so they play in story order.

Loaded by benflix-games-helper.py (it sits next to it on Tower).
  * Which show: the fan-made anime lists (github.com/Anime-Lists/anime-lists) say "TMDB movie 635302 is
    TheTVDB show 348545". They are only used to find the show.
  * Which special and where: TheTVDB alone, read through Sonarr (Sonarr's show details come from TheTVDB).
    The movie must match a TheTVDB special by name or by date, or Ben is asked. Glowfin never guesses.
  * Moving: the file (and its subtitles) is moved into the show's Specials folder with Sonarr's own naming,
    with a small .nfo note next to it (title, date, "airs after Season 1"). Radarr stops looking for the
    movie, Sonarr and Jellyfin are told to look again.
  * Double check: a few minutes later Glowfin reads back what Jellyfin shows for the file. If the title or
    number doesn't match TheTVDB it goes on the card in red with a Fix button. Every week every anime show's
    specials are checked the same way, including ones filed by hand.
  * Movies already here when this started wait for Ben's "Move all" on Server > Tools > Library.
    New ones move by themselves. Every move is noted so it can be put back.
  * Missing pieces: for each anime show, the movies and specials that aren't here, labelled Canon, Mixed,
    Filler, Recap, Extra or Not sure (from TheTVDB, AniList and Anime Filler List). Filler episode labels
    for the regular episodes come from Anime Filler List too (for "Skip filler").
  * Heads-up: when a new canon or mixed movie appears for a show that's here, it goes on Radarr's list so it
    arrives (and moves into the show) as soon as it's out.

Saves its notes in /skips/specials. Only uses the Python standard library.
"""

import difflib
import json
import os
import re
import shutil
import threading
import time
import unicodedata
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
from datetime import datetime, timezone

LISTS_URL = "https://raw.githubusercontent.com/Anime-Lists/anime-lists/master/anime-list-master.xml"
IDS_URL = "https://raw.githubusercontent.com/Fribb/anime-lists/master/anime-list-mini.json"
ANILIST_URL = "https://graphql.anilist.co"
AFL_URL = "https://www.animefillerlist.com"
EVERY = 600  # seconds between looks
CHECK_AFTER = 180  # wait this long after a move before reading Jellyfin back
CHECK_TRIES = 4
WEEK = 7 * 86400
DAY = 86400
VIDEO = {".mkv", ".mp4", ".avi", ".m4v", ".mov", ".ts", ".webm", ".wmv"}
SIDE = {".srt", ".ass", ".ssa", ".sub", ".idx", ".vtt", ".sup", ".nfo"}
LABEL_WORDS = {
    "canon": "Canon",
    "mixed": "Mixed",
    "filler": "Filler",
    "recap": "Recap",
    "extra": "Extra",
    "unsure": "Not sure",
}
SHOWN_LABELS = ("canon", "mixed", "unsure")  # what Missing pieces lists; filler, recaps and extras hide

_jf = None  # helper's jellyfin_call(method, path, token=None, body=None) -> (status, bytes, type)
_admin = None  # () -> (token, user id)
_tools = None  # benflix_server_tools (Radarr, Sonarr)
_arrivals = None
_dir = None
_lock = threading.RLock()
_wake = threading.Event()
_log_lines = []
_state = {}  # saved: moves, keep, picks, started, known movies, checks
_lists = {"movies": {}, "shows": {}, "loaded": 0}  # tmdb movie -> entries; tvdb -> entries
_ids = {}  # anidb -> {"anilist", "mal"}
_cache = {}  # (kind, key) -> (time, value)
_view = {"rows": [], "ready": [], "pick": [], "waiting": [], "bad": [], "last": None, "problem": None}
_status = {"busy": False}


def log(text):
    print("specials: " + text, flush=True)
    with _lock:
        _log_lines.append(time.strftime("%Y-%m-%d %H:%M ") + text)
        del _log_lines[:-80]


# ---------------------------------------------------------------- saving


def _pick_dir():
    for d in (os.environ.get("SPECIALS_DIR"), "/skips/specials", "/tracks/benflix-specials", "/tmp/benflix-specials"):
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
    tmp = os.path.join(_dir, name + ".tmp")
    try:
        with open(tmp, "w") as f:
            json.dump(data, f)
        os.replace(tmp, os.path.join(_dir, name))
    except OSError as e:
        log(f"couldn't save {name}: {e}")


def _save():
    with _lock:
        _write("state.json", _state)


# ---------------------------------------------------------------- small helpers


def _norm(text):
    text = unicodedata.normalize("NFKD", str(text or "")).encode("ascii", "ignore").decode().lower()
    text = re.sub(r"\b(the movie|gekijouban|gekijoban|movie|film|the|a|an|eiga|hen|arc|part)\b", " ", text)
    return re.sub(r"[^a-z0-9]+", "", text)


def _alike(a, b):
    """How alike two titles are, 0 to 1"""
    x, y = _norm(a), _norm(b)
    if not x or not y:
        return 0.0
    if len(x) >= 5 and len(y) >= 5 and (x in y or y in x):
        return 0.95
    return difflib.SequenceMatcher(None, x, y).ratio()


def _disagree(item, ep_no, title):
    """Why Jellyfin and TheTVDB disagree about one special, or "" when they agree.

    The title is what counts: when Jellyfin shows TheTVDB's title, the special is in the right spot even if
    Jellyfin's own number differs (its order can come from another site, a two-part file covers two numbers,
    or the number is blank). The number only decides when there is no title to compare."""
    name = item.get("Name")
    if title and name:
        if _alike(name, title) >= 0.6:
            return ""
        return f"Jellyfin calls special {ep_no} \"{name}\" but TheTVDB says \"{title}\""
    first, last = item.get("IndexNumber"), item.get("IndexNumberEnd") or item.get("IndexNumber")
    if first is None or ep_no is None or first <= ep_no <= last:
        return ""
    return f"Jellyfin numbers it {first} instead of {ep_no}"


def _day(text):
    """'2020-10-16' or an ISO time -> seconds, or None"""
    if not text:
        return None
    try:
        return datetime.fromisoformat(str(text)[:10]).replace(tzinfo=timezone.utc).timestamp()
    except ValueError:
        return None


def _iso(seconds):
    return datetime.fromtimestamp(seconds, timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.0000000Z")


def _safe_name(text):
    text = re.sub(r'[<>:"/\\|?*\x00-\x1f]', "", str(text or "")).strip().rstrip(".")
    return re.sub(r"\s+", " ", text)[:150] or "Special"


def _get_url(url, timeout=60, data=None, headers=None):
    req = urllib.request.Request(url, data=data, headers={"User-Agent": "Glowfin-helper/1.0", **(headers or {})})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.read()


def _cached(kind, key, max_age, fetch):
    hit = _cache.get((kind, key))
    if hit and time.time() - hit[0] < max_age:
        return hit[1]
    value = fetch()
    _cache[(kind, key)] = (time.time(), value)
    return value


def _arr(app, method, path, body=None, timeout=30):
    return _tools._arr(app, method, path, body, timeout=timeout)


def _arr_ready():
    return _tools is not None and _tools.arr_ready("sonarr") and _tools.arr_ready("radarr")


def _jget(path, token):
    status, body, _ = _jf("GET", path, token=token)
    if status != 200:
        raise RuntimeError(f"Jellyfin said {status} for {path.split('?')[0]}")
    return json.loads(body or b"{}")


def _token():
    try:
        token, user = _admin() if _admin else (None, None)
    except Exception:  # noqa: BLE001
        token, user = None, None
    return token, user


# Paths: Radarr and Sonarr see the Media folder as /data, Jellyfin and the helper as /media
_PREFIXES = ("/data", "/media", "/mnt/user/Homelab/Media", "/mnt/user/media")


def _local(path):
    if not path:
        return None
    if os.path.exists(path):
        return path
    for prefix in _PREFIXES:
        if path.startswith(prefix + "/"):
            guess = "/media" + path[len(prefix):]
            if os.path.exists(guess):
                return guess
    return None


def _arr_path(local_path, like):
    """The helper's /media/... as Radarr/Sonarr see it, using one of their own paths ([like]) for the prefix"""
    for prefix in _PREFIXES:
        if like and like.startswith(prefix + "/"):
            return prefix + local_path[len("/media"):] if local_path.startswith("/media/") else local_path
    return local_path


# ---------------------------------------------------------------- the anime lists


def _load_lists(force=False):
    """Reads the fan-made anime lists (downloaded once a day, kept in the notes folder)"""
    if not force and _lists["loaded"] and time.time() - _lists["loaded"] < DAY:
        return
    raw = None
    path = os.path.join(_dir, "anime-list-master.xml") if _dir else None
    fresh = path and os.path.exists(path) and time.time() - os.path.getmtime(path) < DAY
    if not fresh:
        try:
            raw = _get_url(LISTS_URL, timeout=120)
            if path:
                with open(path, "wb") as f:
                    f.write(raw)
        except Exception as e:  # noqa: BLE001
            log(f"couldn't download the anime lists ({e}); using the last copy")
    if raw is None and path and os.path.exists(path):
        with open(path, "rb") as f:
            raw = f.read()
    if not raw:
        raise RuntimeError("Couldn't get the anime lists from GitHub. Check Tower can reach the internet.")
    movies, shows = {}, {}
    for a in ET.fromstring(raw).iter("anime"):
        tvdb = a.get("tvdbid") or ""
        if not tvdb.isdigit():
            continue
        tvdb = int(tvdb)
        season = a.get("defaulttvdbseason") or ""
        tmdb = a.get("tmdbid") or ""
        name = (a.findtext("name") or "").strip()
        ep = None
        if season == "0":
            off = a.get("episodeoffset") or "0"
            ep = (int(off) if off.lstrip("-").isdigit() else 0) + 1
            for m in a.iter("mapping"):
                if m.get("anidbseason") == "1" and m.get("tvdbseason") == "0" and m.text:
                    pair = re.search(r";1-(\d+)", m.text)
                    if pair and int(pair.group(1)) > 0:
                        ep = int(pair.group(1))
        entry = {
            "anidb": int(a.get("anidbid") or 0),
            "tvdb": tvdb,
            "season": int(season) if season.isdigit() else None,
            "ep": ep,
            "tmdb": int(tmdb) if tmdb.isdigit() else None,
            "name": name,
        }
        shows.setdefault(tvdb, []).append(entry)
        if entry["tmdb"] and season == "0":
            movies.setdefault(entry["tmdb"], []).append(entry)
    with _lock:
        _lists.update({"movies": movies, "shows": shows, "loaded": time.time()})
    try:
        ids = json.loads(_get_url(IDS_URL, timeout=120))
        _ids.clear()
        for row in ids if isinstance(ids, list) else []:
            if row.get("anidb_id"):
                _ids[int(row["anidb_id"])] = {"anilist": row.get("anilist_id"), "mal": row.get("mal_id")}
    except Exception as e:  # noqa: BLE001
        log(f"couldn't download the AniList number list ({e}); labels will say Not sure more often")
    log(f"anime lists read: {len(movies)} movies that belong to shows")


# ---------------------------------------------------------------- Sonarr, Radarr, Jellyfin reads


def _series_all():
    return _cached("sonarr", "series", 300, lambda: _arr("sonarr", "GET", "/api/v3/series") or [])


def _series_by_tvdb():
    return {s.get("tvdbId"): s for s in _series_all() if s.get("tvdbId")}


def _episodes(series_id):
    return _cached("sonarr-eps", series_id, 300, lambda: _arr("sonarr", "GET", f"/api/v3/episode?seriesId={series_id}") or [])


def _radarr_by_tmdb():
    return _cached("radarr", "movies", 300, lambda: {m.get("tmdbId"): m for m in (_arr("radarr", "GET", "/api/v3/movie") or []) if m.get("tmdbId")})


def _jf_movies(token):
    """Every movie in Jellyfin with its TMDB number and file"""

    def fetch():
        out, start, page = [], 0, 1000
        while True:
            q = urllib.parse.urlencode(
                {
                    "IncludeItemTypes": "Movie",
                    "Recursive": "true",
                    "Fields": "ProviderIds,Path,DateCreated,PremiereDate,ProductionYear,OriginalTitle",
                    "EnableImages": "false",
                    "EnableUserData": "false",
                    "StartIndex": str(start),
                    "Limit": str(page),
                }
            )
            items = _jget("/Items?" + q, token).get("Items") or []
            out.extend(items)
            if len(items) < page:
                return out
            start += page

    return _cached("jf", "movies", 240, fetch)


def _jf_series(token):
    def fetch():
        q = urllib.parse.urlencode({"IncludeItemTypes": "Series", "Recursive": "true", "Fields": "ProviderIds,Path", "EnableImages": "false"})
        return _jget("/Items?" + q, token).get("Items") or []

    return _cached("jf", "series", 300, fetch)


def _jf_show_for(series, token):
    """Jellyfin's show for a Sonarr show (by its folder, else by TheTVDB number)"""
    local = _local(series.get("path"))
    tvdb = str(series.get("tvdbId") or "")
    for s in _jf_series(token):
        if local and s.get("Path") and os.path.normpath(s["Path"]) == os.path.normpath(local):
            return s
    for s in _jf_series(token):
        if tvdb and str((s.get("ProviderIds") or {}).get("Tvdb") or "") == tvdb:
            return s
    return None


def _playing_paths(token):
    try:
        status, body, _ = _jf("GET", "/Sessions?activeWithinSeconds=300", token=token)
        sessions = json.loads(body or b"[]") if status == 200 else []
    except Exception:  # noqa: BLE001
        return set()
    return {(s.get("NowPlayingItem") or {}).get("Path") for s in sessions if s.get("NowPlayingItem")}


# ---------------------------------------------------------------- where a special goes


def _placement(special, eps):
    """Where a special sits among the regular episodes, from TheTVDB dates: (nfo tags, words)"""
    when = _day(special.get("airDate"))
    regular = sorted(
        (e for e in eps if (e.get("seasonNumber") or 0) > 0 and _day(e.get("airDate"))),
        key=lambda e: (_day(e.get("airDate")), e.get("seasonNumber"), e.get("episodeNumber")),
    )
    if not regular:
        return {}, ""
    last_season = max(e["seasonNumber"] for e in regular)
    if when is None:
        return {"airsafter_season": last_season}, f"after Season {last_season}"
    after = next((e for e in regular if _day(e.get("airDate")) > when), None)
    if after is None:
        return {"airsafter_season": last_season}, f"after Season {last_season}"
    s, n = after["seasonNumber"], after["episodeNumber"]
    if n == 1 and s > 1:
        return {"airsafter_season": s - 1}, f"after Season {s - 1}"
    if n == 1:
        return {"airsbefore_season": s, "airsbefore_episode": 1}, f"before Season {s}"
    return {"airsbefore_season": s, "airsbefore_episode": n}, f"during Season {s}, before episode {n}"


def _match(movie, specials, listed_ep):
    """TheTVDB special for a movie: (episode, how sure) - 'sure' only when TheTVDB's own name or date agrees"""
    titles = [movie.get("title"), movie.get("originalTitle")] + [t.get("title") for t in movie.get("alternateTitles") or []]
    titles = [t for t in titles if t]
    released = [_day(movie.get(k)) for k in ("inCinemas", "physicalRelease", "digitalRelease", "premiereDate")]
    released = [d for d in released if d]
    year = movie.get("year")

    def score(ep):
        name = max((_alike(t, ep.get("title")) for t in titles), default=0.0)
        aired = _day(ep.get("airDate"))
        close = bool(aired and any(abs(aired - d) <= 60 * DAY for d in released))
        same_year = bool(aired and year and str(ep.get("airDate", ""))[:4] == str(year))
        return name, close, same_year

    if listed_ep is not None:
        ep = next((e for e in specials if e.get("episodeNumber") == listed_ep), None)
        if ep:
            name, close, same_year = score(ep)
            if name >= 0.6 or close or (same_year and name >= 0.4):
                return ep, "sure"
    best = []
    for ep in specials:
        name, close, same_year = score(ep)
        if name >= 0.75 or (close and name >= 0.35) or (close and same_year):
            best.append((name + (0.3 if close else 0), ep))
    best.sort(key=lambda x: -x[0])
    if len(best) == 1 or (len(best) > 1 and best[0][0] - best[1][0] >= 0.25):
        return best[0][1], "sure"
    return None, "ask"


def _candidates(movie, specials, listed_ep, n=3):
    titles = [movie.get("title"), movie.get("originalTitle")]
    scored = sorted(specials, key=lambda e: -max(_alike(t, e.get("title")) for t in titles if t) if any(titles) else 0)
    out = scored[:n]
    listed = next((e for e in specials if e.get("episodeNumber") == listed_ep), None)
    if listed and listed not in out:
        out = [listed] + out[: n - 1]
    return out


# ---------------------------------------------------------------- the look


def _scan():
    """Finds every anime movie in Movies that belongs to a show, and what to do with it"""
    token, _ = _token()
    if not token:
        raise RuntimeError("Waiting for Ben to open Glowfin once (Tower needs the admin sign-in to read Jellyfin).")
    _load_lists()
    series = _series_by_tvdb()
    radarr = _radarr_by_tmdb()
    with _lock:
        keep = set(map(str, _state.get("keep", [])))
        moves = dict(_state.get("moves", {}))
        picks = dict(_state.get("picks", {}))
        started = _state.get("started") or time.time()
    ready, pick, waiting = [], [], []
    for it in _jf_movies(token):
        tmdb = str((it.get("ProviderIds") or {}).get("Tmdb") or "")
        if not tmdb.isdigit() or int(tmdb) not in _lists["movies"] or tmdb in keep:
            continue
        if tmdb in moves and moves[tmdb].get("state") in ("moved", "checked", "mismatch"):
            continue  # Jellyfin hasn't noticed the move yet
        local = _local(it.get("Path"))
        if not local or not os.path.isfile(local):
            continue
        r = radarr.get(int(tmdb)) or {}
        movie = {
            "title": r.get("title") or it.get("Name"),
            "originalTitle": r.get("originalTitle") or it.get("OriginalTitle"),
            "alternateTitles": r.get("alternateTitles") or [],
            "inCinemas": r.get("inCinemas"),
            "physicalRelease": r.get("physicalRelease"),
            "digitalRelease": r.get("digitalRelease"),
            "premiereDate": it.get("PremiereDate"),
            "year": r.get("year") or it.get("ProductionYear"),
        }
        entries = _lists["movies"][int(tmdb)]
        owned = [(e, series[e["tvdb"]]) for e in entries if e["tvdb"] in series]
        base = {
            "tmdb": tmdb,
            "movie": movie["title"],
            "file": local,
            "radarr": r.get("id"),
            "added": _day(it.get("DateCreated")) or 0,
            "new": (_day(it.get("DateCreated")) or 0) > started,
        }
        if not owned:
            waiting.append({**base, "show": entries[0]["name"], "tvdb": entries[0]["tvdb"]})
            continue
        entry, show = owned[0]
        eps = _episodes(show["id"])
        specials = [e for e in eps if e.get("seasonNumber") == 0]
        chosen = picks.get(tmdb)
        if chosen and int(chosen.get("tvdb", 0)) in series:
            show = series[int(chosen["tvdb"])]
            eps = _episodes(show["id"])
            specials = [e for e in eps if e.get("seasonNumber") == 0]
            ep = next((e for e in specials if e.get("episodeNumber") == int(chosen["ep"])), None)
            how = "sure" if ep else "ask"
        else:
            ep, how = _match(movie, specials, entry.get("ep"))
        row = {**base, "show": show.get("title"), "tvdb": show.get("tvdbId"), "series": show["id"]}
        if how != "sure" or ep is None:
            row["choices"] = [
                {"ep": e.get("episodeNumber"), "title": e.get("title") or f"Special {e.get('episodeNumber')}"}
                for e in _candidates(movie, specials, entry.get("ep"))
            ]
            pick.append(row)
            continue
        _, where = _placement(ep, eps)
        row.update({"ep": ep.get("episodeNumber"), "ep_id": ep.get("id"), "ep_title": ep.get("title"), "where": where, "has_file": bool(ep.get("hasFile"))})
        ready.append(row)
    return ready, pick, waiting


# ---------------------------------------------------------------- moving


def _nfo(ep, eps, show_title):
    tags, _ = _placement(ep, eps)

    def esc(t):
        return str(t or "").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    lines = [
        '<?xml version="1.0" encoding="utf-8" standalone="yes"?>',
        "<episodedetails>",
        f"  <title>{esc(ep.get('title'))}</title>",
        f"  <showtitle>{esc(show_title)}</showtitle>",
        "  <season>0</season>",
        f"  <episode>{int(ep.get('episodeNumber') or 0)}</episode>",
    ]
    if ep.get("airDate"):
        lines += [f"  <aired>{esc(ep['airDate'])}</aired>", f"  <premiered>{esc(ep['airDate'])}</premiered>"]
    if ep.get("overview"):
        lines.append(f"  <plot>{esc(ep['overview'])}</plot>")
    if ep.get("tvdbId"):
        lines.append(f'  <uniqueid type="tvdb" default="true">{int(ep["tvdbId"])}</uniqueid>')
    for k, v in tags.items():
        lines.append(f"  <{k}>{int(v)}</{k}>")
    lines.append("</episodedetails>")
    return "\n".join(lines) + "\n"


def _specials_folder(show):
    try:
        naming = _cached("sonarr", "naming", 3600, lambda: _arr("sonarr", "GET", "/api/v3/config/naming") or {})
        f = naming.get("specialsFolderFormat") or "Specials"
        return f if "{" not in f else "Specials"
    except Exception:  # noqa: BLE001
        return "Specials"


def _move_one(row, token, why):
    """Moves one movie into its show's Specials. Returns words for the card."""
    series = next((s for s in _series_all() if s.get("id") == row["series"]), None)
    if not series:
        raise RuntimeError(f"Sonarr doesn't have {row['show']} any more.")
    show_dir = _local(series.get("path"))
    if not show_dir:
        raise RuntimeError(f"Tower can't see the folder for {row['show']} ({series.get('path')}).")
    src = row["file"]
    if not os.path.isfile(src):
        raise RuntimeError(f"The file for {row['movie']} isn't there any more.")
    if src in _playing_paths(token):
        return f"{row['movie']} is playing right now, so it moves after."
    eps = _episodes(series["id"])
    ep = next((e for e in eps if e.get("id") == row["ep_id"]), None)
    if not ep:
        raise RuntimeError(f"TheTVDB no longer lists that special for {row['show']}.")
    if ep.get("hasFile"):
        return f"{row['show']} already has Specials episode {row['ep']}, so {row['movie']} stays in Movies. Use Keep in Movies or remove one of them."
    folder = os.path.join(show_dir, _specials_folder(series))
    os.makedirs(folder, exist_ok=True)
    stem = _safe_name(f"{series.get('title')} - S00E{int(row['ep']):02d} - {ep.get('title') or row['movie']}")
    src_dir, src_name = os.path.split(src)
    src_stem, ext = os.path.splitext(src_name)
    moved = []
    dst = os.path.join(folder, stem + ext.lower())
    if os.path.exists(dst):
        raise RuntimeError(f"There's already a file called {os.path.basename(dst)} in {row['show']}.")
    shutil.move(src, dst)
    moved.append([src, dst])
    for name in os.listdir(src_dir):
        if name.startswith(src_stem) and name != src_name and os.path.splitext(name)[1].lower() in SIDE - {".nfo"}:
            a = os.path.join(src_dir, name)
            b = os.path.join(folder, stem + name[len(src_stem):])
            if not os.path.exists(b):
                shutil.move(a, b)
                moved.append([a, b])
    nfo = os.path.join(folder, stem + ".nfo")
    with open(nfo, "w", encoding="utf-8") as f:
        f.write(_nfo(ep, eps, series.get("title")))
    _cache.pop(("sonarr-eps", series["id"]), None)
    _cache.pop(("jf", "movies"), None)
    note = {
        "state": "moved",
        "movie": row["movie"],
        "show": row["show"],
        "tvdb": row["tvdb"],
        "series": series["id"],
        "ep": row["ep"],
        "ep_id": row["ep_id"],
        "ep_title": ep.get("title"),
        "where": row.get("where"),
        "files": moved,
        "nfo": nfo,
        "radarr": row.get("radarr"),
        "added": row.get("added"),
        "keep_date": not row.get("new"),
        "when": time.time(),
        "why": why,
        "tries": 0,
    }
    with _lock:
        _state.setdefault("moves", {})[row["tmdb"]] = note
    _save()
    _after_move(note, token, back=False)
    log(f"moved {row['movie']} into {row['show']} Specials {row['ep']} ({why})")
    return f"Moved {row['movie']} into {row['show']}, {row.get('where') or 'Specials'}."


def _after_move(note, token, back):
    """Tells Radarr, Sonarr and Jellyfin about a move"""
    rid = note.get("radarr")
    if rid:
        try:
            m = _arr("radarr", "GET", f"/api/v3/movie/{rid}")
            if m:
                m["monitored"] = bool(back)
                _arr("radarr", "PUT", f"/api/v3/movie/{rid}", m)
            _arr("radarr", "POST", "/api/v3/command", {"name": "RefreshMovie", "movieIds": [rid]})
        except Exception as e:  # noqa: BLE001
            log(f"Radarr didn't take the change for {note.get('movie')}: {e}")
    try:
        _arr("sonarr", "POST", "/api/v3/command", {"name": "RescanSeries", "seriesId": note["series"]})
    except Exception as e:  # noqa: BLE001
        log(f"Sonarr didn't rescan {note.get('show')}: {e}")
    updates = []
    for a, b in note.get("files", []):
        if os.path.splitext(a)[1].lower() in VIDEO:
            updates += [{"Path": a, "UpdateType": "Deleted" if not back else "Created"}, {"Path": b, "UpdateType": "Created" if not back else "Deleted"}]
    if updates:
        try:
            _jf("POST", "/Library/Media/Updated", token=token, body=json.dumps({"Updates": updates}).encode())
        except Exception as e:  # noqa: BLE001
            log(f"Jellyfin didn't hear about the move: {e}")
    _cache.pop(("radarr", "movies"), None)


def _undo(tmdb, token):
    with _lock:
        note = (_state.get("moves") or {}).get(tmdb)
    if not note:
        return "Glowfin has no note of moving that one."
    for a, b in reversed(note.get("files", [])):
        if os.path.exists(b) and not os.path.exists(a):
            os.makedirs(os.path.dirname(a), exist_ok=True)
            shutil.move(b, a)
    try:
        if note.get("nfo") and os.path.exists(note["nfo"]):
            os.remove(note["nfo"])
    except OSError:
        pass
    _after_move(note, token, back=True)
    with _lock:
        _state["moves"].pop(tmdb, None)
        keep = set(_state.get("keep", []))
        keep.add(tmdb)
        _state["keep"] = sorted(keep)
    _save()
    _cache.pop(("jf", "movies"), None)
    log(f"put {note.get('movie')} back in Movies")
    return f"Put {note.get('movie')} back in Movies. It stays there from now on."


# ---------------------------------------------------------------- double check


def _episode_in_jellyfin(note, token):
    series = next((s for s in _series_all() if s.get("id") == note["series"]), None)
    if not series:
        return None, "Sonarr doesn't have the show any more."
    show = _jf_show_for(series, token)
    if not show:
        return None, "Jellyfin hasn't found the show yet."
    eps = _jget(f"/Shows/{show['Id']}/Episodes?season=0&Fields=Path,ProviderIds,DateCreated", token).get("Items") or []
    video = next((b for a, b in note.get("files", []) if os.path.splitext(b)[1].lower() in VIDEO), None)
    for e in eps:
        if video and e.get("Path") and os.path.normpath(e["Path"]) == os.path.normpath(video):
            return e, None
    return None, "Jellyfin hasn't found the file in Specials yet."


def _check_moves(token):
    with _lock:
        moves = {k: dict(v) for k, v in (_state.get("moves") or {}).items()}
    changed = False
    for tmdb, note in moves.items():
        if note.get("state") != "moved" or time.time() - note.get("when", 0) < CHECK_AFTER:
            continue
        try:
            item, problem = _episode_in_jellyfin(note, token)
        except Exception as e:  # noqa: BLE001
            item, problem = None, str(e)
        if item is None:
            note["tries"] = note.get("tries", 0) + 1
            if note["tries"] >= CHECK_TRIES:
                note.update({"state": "mismatch", "problem": problem})
            changed = True
        else:
            wrong = []
            if item.get("ParentIndexNumber") not in (0, None):
                wrong.append(f"Jellyfin put it in season {item.get('ParentIndexNumber')} instead of Specials")
            problem = _disagree(item, note["ep"], note.get("ep_title"))
            if problem:
                wrong.append(problem)
            note.update({"state": "mismatch" if wrong else "checked", "problem": "; ".join(wrong), "jf": item.get("Id")})
            if not wrong and note.get("keep_date") and note.get("added") and _arrivals is not None:
                # Movies that were already here keep their first date, so they don't show up as new episodes
                try:
                    _, user = _token()
                    _arrivals._set_date(item["Id"], note["added"], token, user)
                except Exception:  # noqa: BLE001
                    pass
            changed = True
        with _lock:
            if tmdb in _state.get("moves", {}):
                _state["moves"][tmdb] = note
    if changed:
        _save()


def _fix(tmdb, token):
    """Writes the TheTVDB note again and asks Jellyfin to read it fresh"""
    with _lock:
        note = dict((_state.get("moves") or {}).get(tmdb) or {})
    if note:
        eps = _episodes(note["series"])
        ep = next((e for e in eps if e.get("id") == note.get("ep_id")), None)
        series = next((s for s in _series_all() if s.get("id") == note["series"]), {})
        if ep and note.get("nfo"):
            with open(note["nfo"], "w", encoding="utf-8") as f:
                f.write(_nfo(ep, eps, series.get("title")))
        item_id = note.get("jf")
        if not item_id:
            item, _ = _episode_in_jellyfin(note, token)
            item_id = item and item.get("Id")
        if item_id:
            _refresh_item(item_id, token)
        note.update({"state": "moved", "when": time.time(), "tries": 0, "problem": ""})
        with _lock:
            _state["moves"][tmdb] = note
        _save()
        return f"Asked Jellyfin to read {note.get('movie')} again. Glowfin checks it in a few minutes."
    with _lock:
        bad = dict((_state.get("weekly") or {}).get("bad", {}).get(tmdb) or {})
    if bad:
        if bad.get("nfo_text") and bad.get("nfo"):
            with open(bad["nfo"], "w", encoding="utf-8") as f:
                f.write(bad["nfo_text"])
        _refresh_item(bad["jf"], token)
        with _lock:
            _state["weekly"]["bad"].pop(tmdb, None)
        _save()
        return f"Asked Jellyfin to read {bad.get('show')} special {bad.get('ep')} again."
    return "That one is already fixed."


def _refresh_item(item_id, token):
    q = urllib.parse.urlencode({"MetadataRefreshMode": "FullRefresh", "ImageRefreshMode": "Default", "ReplaceAllMetadata": "true", "ReplaceAllImages": "false"})
    _jf("POST", f"/Items/{item_id}/Refresh?{q}", token=token, body=b"")


def weekly(token=None):
    """Every anime show's specials: does Jellyfin agree with TheTVDB? (also ones filed by hand)"""
    token = token or _token()[0]
    if not token or not _arr_ready():
        return
    _load_lists()
    bad = {}
    for tvdb, s in _series_by_tvdb().items():
        if tvdb not in _lists["shows"]:
            continue
        eps = [e for e in _episodes(s["id"]) if e.get("seasonNumber") == 0 and e.get("hasFile")]
        if not eps:
            continue
        show = _jf_show_for(s, token)
        if not show:
            continue
        try:
            jeps = _jget(f"/Shows/{show['Id']}/Episodes?season=0&Fields=Path", token).get("Items") or []
        except Exception:  # noqa: BLE001
            continue
        files = {}
        try:
            for f in _arr("sonarr", "GET", f"/api/v3/episodefile?seriesId={s['id']}") or []:
                files[f.get("id")] = _local(f.get("path"))
        except Exception:  # noqa: BLE001
            pass
        for ep in eps:
            path = files.get(ep.get("episodeFileId"))
            j = next((x for x in jeps if path and x.get("Path") and os.path.normpath(x["Path"]) == os.path.normpath(path)), None)
            if not j:
                continue
            problem = _disagree(j, ep.get("episodeNumber"), ep.get("title"))
            if problem:
                key = f"w{tvdb}-{ep.get('episodeNumber')}"
                bad[key] = {
                    "show": s.get("title"),
                    "ep": ep.get("episodeNumber"),
                    "tvdb_title": ep.get("title"),
                    "jf_title": j.get("Name"),
                    "jf_number": j.get("IndexNumber"),
                    "problem": problem,
                    "jf": j["Id"],
                    "nfo": os.path.splitext(path)[0] + ".nfo",
                    "nfo_text": _nfo(ep, _episodes(s["id"]), s.get("title")),
                }
    with _lock:
        _state["weekly"] = {"when": time.time(), "bad": bad}
    _save()
    log(f"weekly check: {len(bad)} specials where Jellyfin and TheTVDB disagree")


# ---------------------------------------------------------------- missing pieces


def _anilist(ids):
    """AniList entries (format and relations) for a few AniList numbers, kept a week"""
    ids = sorted({int(i) for i in ids if i})
    if not ids:
        return {}

    def fetch():
        q = """query($ids:[Int]){Page(perPage:50){media(id_in:$ids,type:ANIME){id format episodes duration
          title{romaji english} relations{edges{relationType node{id format title{romaji english}}}}}}}"""
        body = json.dumps({"query": q, "variables": {"ids": ids[:50]}}).encode()
        raw = _get_url(ANILIST_URL, timeout=30, data=body, headers={"Content-Type": "application/json", "Accept": "application/json"})
        media = ((json.loads(raw).get("data") or {}).get("Page") or {}).get("media") or []
        return {m["id"]: m for m in media}

    try:
        return _cached("anilist", tuple(ids), WEEK, fetch)
    except Exception as e:  # noqa: BLE001
        log(f"AniList didn't answer ({e})")
        return {}


RELATION_LABEL = {
    "PREQUEL": "canon",
    "SEQUEL": "canon",
    "PARENT": "canon",
    "SIDE_STORY": "mixed",
    "SUMMARY": "recap",
    "COMPILATION": "recap",
    "SPIN_OFF": "filler",
    "CHARACTER": "filler",
    "OTHER": "filler",
}
RECAP_WORDS = re.compile(r"(?i)\b(recap|digest|compilation|summary|retold|special edition|soushuuhen|総集編)\b")


def _label(entry_anilist, relations, title, runtime):
    if entry_anilist and entry_anilist in relations:
        return relations[entry_anilist]
    if RECAP_WORDS.search(str(title or "")):
        return "recap"
    if runtime and runtime <= 12:
        return "extra"
    return "unsure"


def pieces(tvdb, token=None):
    """Missing pieces for one anime show: its movies and specials, labelled, with what's here"""
    _load_lists()
    tvdb = int(tvdb)
    entries = _lists["shows"].get(tvdb) or []
    if not entries:
        return {"anime": False, "pieces": [], "complete": None}
    s = _series_by_tvdb().get(tvdb)
    eps = _episodes(s["id"]) if s else []
    specials = {e.get("episodeNumber"): e for e in eps if e.get("seasonNumber") == 0}
    main_ids = [(_ids.get(e["anidb"]) or {}).get("anilist") for e in entries if (e.get("season") or 0) >= 1]
    sp_entries = [e for e in entries if e.get("season") == 0]
    sp_ids = [(_ids.get(e["anidb"]) or {}).get("anilist") for e in entries if e.get("season") == 0]
    media = _anilist(main_ids + sp_ids)
    relations = {}
    for m in media.values():
        for edge in (m.get("relations") or {}).get("edges") or []:
            lab = RELATION_LABEL.get(edge.get("relationType"))
            node = edge.get("node") or {}
            if lab and node.get("id") and node["id"] not in relations:
                relations[node["id"]] = lab
    radarr = _radarr_by_tmdb() if _arr_ready() else {}
    token = token or _token()[0]
    in_movies = set()
    if token:
        try:
            in_movies = {str((m.get("ProviderIds") or {}).get("Tmdb") or "") for m in _jf_movies(token)}
        except Exception:  # noqa: BLE001
            pass
    with _lock:
        moved = {k for k, v in (_state.get("moves") or {}).items()}
    out, seen_eps = [], set()
    for e in sp_entries:
        anilist = (_ids.get(e["anidb"]) or {}).get("anilist")
        ep = specials.get(e.get("ep"))
        english = ((media.get(anilist) or {}).get("title") or {}).get("english") if anilist else None
        title = (ep or {}).get("title") or english or e["name"]
        kind = "movie" if e.get("tmdb") else "special"
        have = bool(ep and ep.get("hasFile")) or (e.get("tmdb") and (str(e["tmdb"]) in in_movies or str(e["tmdb"]) in moved))
        r = radarr.get(e.get("tmdb")) if e.get("tmdb") else None
        tvdb_eps = [(ep or {}).get("episodeNumber")] if ep else []
        seen_eps.update(tvdb_eps)
        _, where = _placement(ep, eps) if ep else ({}, "")
        out.append(
            {
                "kind": kind,
                "title": title,
                "ep": (ep or {}).get("episodeNumber"),
                "ep_id": (ep or {}).get("id"),
                "tmdb": e.get("tmdb"),
                "label": _label(anilist, relations, title, (ep or {}).get("runtime")),
                "have": bool(have),
                "coming": bool(r and r.get("monitored") and not r.get("hasFile")) or bool(ep and ep.get("monitored") and not ep.get("hasFile") and s),
                "aired": (ep or {}).get("airDate") or "",
                "where": where,
            }
        )
    for n, ep in specials.items():
        if n in seen_eps:
            continue
        _, where = _placement(ep, eps)
        out.append(
            {
                "kind": "special",
                "title": ep.get("title") or f"Special {n}",
                "ep": n,
                "ep_id": ep.get("id"),
                "tmdb": None,
                "label": _label(None, relations, ep.get("title"), ep.get("runtime")),
                "have": bool(ep.get("hasFile")),
                "coming": bool(ep.get("monitored") and not ep.get("hasFile")),
                "aired": ep.get("airDate") or "",
                "where": where,
            }
        )
    out.sort(key=lambda p: (p["ep"] is None, p["ep"] or 0, p["title"]))
    for p in out:
        p["label_words"] = LABEL_WORDS[p["label"]]
        p["shown"] = p["label"] in SHOWN_LABELS
    needed = [p for p in out if p["label"] in ("canon", "mixed")]
    return {
        "anime": True,
        "show": (s or {}).get("title") or entries[0]["name"],
        "here": bool(s),
        "pieces": out,
        "missing": sum(1 for p in out if p["shown"] and not p["have"]),
        "complete": bool(needed) and all(p["have"] for p in needed),
        "sources": "TheTVDB (through Sonarr), AniList, Anime Filler List",
    }


def _radarr_defaults():
    """Quality and folder for a new anime movie: the same as most anime movies already in Radarr"""
    movies = _radarr_by_tmdb()
    anime = [m for t, m in movies.items() if t in _lists["movies"]] or list(movies.values())
    prof, roots = {}, {}
    for m in anime:
        prof[m.get("qualityProfileId")] = prof.get(m.get("qualityProfileId"), 0) + 1
        root = os.path.dirname(str(m.get("path") or "").rstrip("/"))
        roots[root] = roots.get(root, 0) + 1
    profile = max(prof, key=prof.get) if prof else None
    root = max(roots, key=roots.get) if roots else None
    if not profile:
        profile = ((_arr("radarr", "GET", "/api/v3/qualityprofile") or [{}])[0]).get("id")
    if not root:
        root = ((_arr("radarr", "GET", "/api/v3/rootfolder") or [{}])[0]).get("path")
    return profile, root


def get_piece(tmdb=None, ep_id=None, why="asked"):
    """Asks Radarr (a movie) or Sonarr (a special) to get one missing piece"""
    if not _arr_ready():
        raise RuntimeError("Sonarr and Radarr aren't connected to the helper yet.")
    if tmdb:
        tmdb = int(tmdb)
        have = _radarr_by_tmdb().get(tmdb)
        if have:
            have["monitored"] = True
            _arr("radarr", "PUT", f"/api/v3/movie/{have['id']}", have)
            _arr("radarr", "POST", "/api/v3/command", {"name": "MoviesSearch", "movieIds": [have["id"]]})
            with _lock:
                _state["keep"] = [k for k in _state.get("keep", []) if k != str(tmdb)]
            _save()
            return f"Looking for {have.get('title')} again."
        found = _arr("radarr", "GET", f"/api/v3/movie/lookup/tmdb?tmdbId={tmdb}")
        found = found[0] if isinstance(found, list) and found else found
        if not found:
            raise RuntimeError("Radarr couldn't find that movie.")
        profile, root = _radarr_defaults()
        body = {
            **found,
            "qualityProfileId": profile,
            "rootFolderPath": root,
            "monitored": True,
            "minimumAvailability": "released",
            "addOptions": {"searchForMovie": True},
        }
        _arr("radarr", "POST", "/api/v3/movie", body)
        _cache.pop(("radarr", "movies"), None)
        log(f"asked Radarr for {found.get('title')} ({why})")
        return f"Getting {found.get('title')}. It goes into its show by itself when it arrives."
    if ep_id:
        _arr("sonarr", "PUT", "/api/v3/episode/monitor", {"episodeIds": [int(ep_id)], "monitored": True})
        _arr("sonarr", "POST", "/api/v3/command", {"name": "EpisodeSearch", "episodeIds": [int(ep_id)]})
        _cache.clear()
        return "Getting that special."
    raise RuntimeError("Nothing to get.")


def get_all(tvdb):
    info = pieces(tvdb)
    said = []
    for p in info["pieces"]:
        if p["label"] in ("canon", "mixed") and not p["have"] and not p["coming"]:
            try:
                get_piece(p.get("tmdb"), None if p.get("tmdb") else p.get("ep_id"))
                said.append(p["title"])
            except Exception as e:  # noqa: BLE001
                log(f"couldn't get {p['title']}: {e}")
    return f"Getting {len(said)}: {', '.join(said)}." if said else "Nothing canon or mixed is missing."


def _heads_up():
    """New canon or mixed movies for shows that are here go on Radarr's list (only ones that showed up after this started)"""
    with _lock:
        known = set(_state.get("known_movies") or [])
        first = not known
    series = _series_by_tvdb()
    fresh = []
    for tmdb, entries in _lists["movies"].items():
        if str(tmdb) in known:
            continue
        known.add(str(tmdb))
        if first:
            continue
        if any(e["tvdb"] in series for e in entries):
            fresh.append((tmdb, entries[0]))
    for tmdb, e in fresh:
        try:
            info = pieces(e["tvdb"])
            p = next((x for x in info["pieces"] if x.get("tmdb") == tmdb), None)
            if p and p["label"] in ("canon", "mixed") and not p["have"] and tmdb not in _radarr_by_tmdb():
                get_piece(tmdb, why="new movie for a show you have")
                with _lock:
                    _state.setdefault("heads_up", []).append({"tmdb": tmdb, "title": p["title"], "show": info["show"], "when": time.time()})
                    del _state["heads_up"][:-20]
        except Exception as ex:  # noqa: BLE001
            log(f"heads-up for {e['name']} didn't work: {ex}")
    with _lock:
        _state["known_movies"] = sorted(known)
    _save()


# ---------------------------------------------------------------- filler labels for regular episodes (Anime Filler List)


AFL_KIND = {"manga_canon": "canon", "anime_canon": "canon", "mixed_canon/filler": "mixed", "filler": "filler"}


def _afl_index():
    def fetch():
        html = _get_url(AFL_URL + "/shows", timeout=30).decode("utf-8", "replace")
        return {m.group(2).strip(): m.group(1) for m in re.finditer(r'href="/shows/([a-z0-9-]+)"[^>]*>([^<]+)</a>', html)}

    return _cached("afl", "index", WEEK, fetch)


def filler(tvdb):
    """{'1x5': 'filler', ...} for a show's regular episodes, from Anime Filler List (empty when it isn't listed)"""
    s = _series_by_tvdb().get(int(tvdb))
    if not s:
        return {}
    names = [s.get("title")] + [t.get("title") for t in s.get("alternateTitles") or []]

    def fetch():
        index = _afl_index()
        best, slug = 0.0, None
        for name in names:
            for shown, sl in index.items():
                score = difflib.SequenceMatcher(None, _norm(name), _norm(shown)).ratio()
                if score > best:
                    best, slug = score, sl
        if not slug or best < 0.85:
            return {}
        html = _get_url(f"{AFL_URL}/shows/{slug}", timeout=30).decode("utf-8", "replace")
        kinds = {}
        for row in re.finditer(r'<tr class="([a-z_/]+)[^"]*">(.*?)</tr>', html, re.S):
            num = re.search(r'class="Number">\s*(\d+)', row.group(2))
            kind = AFL_KIND.get(row.group(1))
            if num and kind:
                kinds[int(num.group(1))] = kind
        return {"slug": slug, "kinds": kinds}

    try:
        got = _cached("afl-show", int(tvdb), WEEK, fetch)
    except Exception as e:  # noqa: BLE001
        log(f"Anime Filler List didn't answer ({e})")
        return {}
    if not got:
        return {}
    regular = sorted((e for e in _episodes(s["id"]) if (e.get("seasonNumber") or 0) > 0), key=lambda e: (e["seasonNumber"], e["episodeNumber"]))
    out = {}
    for i, e in enumerate(regular, start=1):
        n = e.get("absoluteEpisodeNumber") or i
        kind = got["kinds"].get(n)
        if kind:
            out[f"{e['seasonNumber']}x{e['episodeNumber']}"] = kind
    return out


# ---------------------------------------------------------------- the loop


def check():
    """One look: double check earlier moves, find what can move, move the new ones"""
    if not _arr_ready():
        with _lock:
            _view["problem"] = "Sonarr and Radarr aren't connected to the helper yet. Run the Glowfin setup command once."
        return
    token, _ = _token()
    with _lock:
        _status["busy"] = True
    try:
        if token:
            _check_moves(token)
        ready, pick, waiting = _scan()
        with _lock:
            first = not _state.get("started")
            if first:
                _state["started"] = time.time()
        if first:
            ready = [{**r, "new": False} for r in ready]
            _save()
        said = []
        for r in ready:
            if r.get("new") and not r.get("has_file"):
                try:
                    said.append(_move_one(r, token, "arrived"))
                except Exception as e:  # noqa: BLE001
                    log(f"couldn't move {r['movie']}: {e}")
        if said:
            ready, pick, waiting = _scan()
        try:
            _heads_up()
        except Exception as e:  # noqa: BLE001
            log(f"heads-up look didn't work: {e}")
        with _lock:
            _view.update({"ready": ready, "pick": pick, "waiting": waiting, "last": time.time(), "problem": None})
            last_week = (_state.get("weekly") or {}).get("when") or 0
        if token and time.time() - last_week > WEEK:
            weekly(token)
    except Exception as e:  # noqa: BLE001
        with _lock:
            _view["problem"] = str(e)
        log(f"look didn't work: {e}")
    finally:
        with _lock:
            _status["busy"] = False


def _loop():
    time.sleep(90)
    while True:
        try:
            check()
        except Exception as e:  # noqa: BLE001
            log(f"loop: {e}")
        _wake.wait(EVERY)
        _wake.clear()


def start(jellyfin_call, admin, tools=None, arrivals=None):
    """Called once by the helper when it starts"""
    global _jf, _admin, _tools, _arrivals, _dir
    _jf, _admin, _tools, _arrivals = jellyfin_call, admin, tools, arrivals
    _dir = _pick_dir()
    with _lock:
        _state.clear()
        _state.update(_read("state.json", {}) or {})
        # Older checks also flagged specials whose title matched but whose number didn't: drop those false alarms
        bad = (_state.get("weekly") or {}).get("bad") or {}
        for key in [k for k, b in bad.items() if b.get("tvdb_title") and _alike(b.get("jf_title"), b.get("tvdb_title")) >= 0.6]:
            bad.pop(key, None)
        for note in (_state.get("moves") or {}).values():
            if note.get("state") == "mismatch" and str(note.get("problem") or "").startswith("Jellyfin numbers it") and "calls" not in str(note.get("problem")):
                note.update({"state": "moved", "tries": 0})
    threading.Thread(target=_loop, name="specials", daemon=True).start()
    log(f"ready (notes in {_dir})")


# ---------------------------------------------------------------- Server page card (Server > Tools > Library)


def card():
    with _lock:
        v = {k: list(x) if isinstance(x, list) else x for k, x in _view.items()}
        moves = dict(_state.get("moves") or {})
        weekly_bad = dict((_state.get("weekly") or {}).get("bad") or {})
        heads = list(_state.get("heads_up") or [])
    rows = []
    for r in v["pick"]:
        acts = [{"label": f"Special {c['ep']}: {c['title']}"[:60], "do": f"pick:{r['tmdb']}:{r['tvdb']}:{c['ep']}"} for c in r.get("choices", []) if c.get("ep") is not None]
        acts.append({"label": "Keep in Movies", "do": f"keep:{r['tmdb']}"})
        rows.append({"light": "warn", "t": f"{r['movie']}: which special in {r['show']}?", "s": "TheTVDB has no clear match, so Glowfin won't guess. Pick one, or keep it in Movies.", "acts": acts})
    for tmdb, n in moves.items():
        if n.get("state") == "mismatch":
            rows.append({"light": "bad", "t": f"{n['movie']} in {n['show']}: Jellyfin doesn't agree with TheTVDB", "s": n.get("problem") or "", "acts": [{"label": "Fix it", "do": f"fix:{tmdb}"}, {"label": "Put back in Movies", "do": f"undo:{tmdb}"}]})
    for key, b in list(weekly_bad.items())[:15]:
        rows.append({"light": "bad", "t": f"{b['show']} special {b['ep']}: Jellyfin doesn't agree with TheTVDB", "s": b.get("problem") or f"TheTVDB says special {b['ep']} is \"{b['tvdb_title']}\".", "acts": [{"label": "Fix it", "do": f"fix:{key}"}]})
    for r in v["ready"]:
        if r.get("has_file"):
            rows.append({"light": "warn", "t": f"{r['movie']} is in Movies and in {r['show']} Specials", "s": f"Special {r['ep']} already has a file, so the Movies copy is a double.", "acts": [{"label": "Keep in Movies", "do": f"keep:{r['tmdb']}"}]})
        else:
            rows.append({"light": "warn", "t": f"{r['movie']} → {r['show']}", "s": f"Specials episode {r['ep']} on TheTVDB ({r.get('ep_title') or ''}), {r.get('where') or ''}".strip(" ,"), "acts": [{"label": "Move", "do": f"move:{r['tmdb']}"}, {"label": "Keep in Movies", "do": f"keep:{r['tmdb']}"}]})
    for h in heads[-5:]:
        rows.append({"light": "ok", "t": f"New movie for {h['show']}: {h['title']}", "s": "On Radarr's list. It goes into the show by itself when it arrives.", "acts": []})
    for r in v["waiting"][:10]:
        rows.append({"light": "off", "t": f"{r['movie']} waits for its show", "s": f"It belongs to {r['show']}. It moves by itself when the show is on Tower.", "acts": [{"label": "Keep in Movies", "do": f"keep:{r['tmdb']}"}]})
    done = sorted(((k, n) for k, n in moves.items() if n.get("state") in ("moved", "checked")), key=lambda x: -x[1].get("when", 0))
    for tmdb, n in done[:10]:
        rows.append({"light": "ok" if n["state"] == "checked" else "off", "t": f"{n['movie']} is in {n['show']}", "s": ("Checked in Jellyfin. " if n["state"] == "checked" else "Checking Jellyfin… ") + f"Special {n['ep']}, {n.get('where') or ''}".rstrip(", "), "acts": [{"label": "Put back in Movies", "do": f"undo:{tmdb}"}]})
    movable = [r for r in v["ready"] if not r.get("has_file")]
    bad = sum(1 for r in rows if r["light"] == "bad")
    if v.get("problem"):
        level, headline, reason = "bad", "Can't check anime movies right now", v["problem"]
    elif movable or v["pick"]:
        level = "warn"
        headline = f"{len(movable)} anime movie{'s' if len(movable) != 1 else ''} can go into {'their shows' if len(movable) != 1 else 'its show'}" if movable else "A movie needs you to pick its special"
        reason = "Nothing is deleted. Files are only moved and renamed, and each one can be put back."
    elif bad:
        level, headline, reason = "bad", f"{bad} special{'s' if bad != 1 else ''} where Jellyfin and TheTVDB disagree", "Fix it asks Jellyfin to read TheTVDB's name and number again."
    else:
        level, headline, reason = "ok", "Every anime movie is in its show", "New ones move by themselves when they arrive."
    acts = []
    if movable:
        acts.append({"label": f"Move all {len(movable)}", "do": "moveall"})
    acts.append({"label": "Check now", "do": "check"})
    return {
        "title": "Anime movies into their shows",
        "intro": "Anime movies that belong to a show go into its Specials, at the spot TheTVDB gives them, so they play in story order.",
        "level": level,
        "headline": headline,
        "reason": reason,
        "acts": acts,
        "rows": rows,
    }


def card_do(do):
    token, _ = _token()
    if not token:
        return "Open Glowfin once as Ben first, so Tower can read Jellyfin."
    with _lock:
        ready = list(_view["ready"])
        pick = list(_view["pick"])
    if do == "check":
        _wake.set()
        return "Checking now."
    if do == "moveall":
        said, problems = 0, []
        for r in ready:
            if r.get("has_file"):
                continue
            try:
                _move_one(r, token, "Move all")
                said += 1
            except Exception as e:  # noqa: BLE001
                problems.append(f"{r['movie']}: {e}")
        _wake.set()
        return f"Moved {said}." + (f" Didn't move: {'; '.join(problems)}" if problems else "")
    kind, _, rest = do.partition(":")
    if kind == "move":
        r = next((x for x in ready if x["tmdb"] == rest), None)
        if not r:
            return "That one already moved, or isn't in Movies any more."
        msg = _move_one(r, token, "Move")
        _wake.set()
        return msg
    if kind == "keep":
        with _lock:
            keep = set(_state.get("keep", []))
            keep.add(rest)
            _state["keep"] = sorted(keep)
            _view["ready"] = [x for x in _view["ready"] if x["tmdb"] != rest]
            _view["pick"] = [x for x in _view["pick"] if x["tmdb"] != rest]
            _view["waiting"] = [x for x in _view["waiting"] if x["tmdb"] != rest]
        _save()
        return "It stays in Movies."
    if kind == "pick":
        tmdb, tvdb, ep = rest.split(":")
        with _lock:
            _state.setdefault("picks", {})[tmdb] = {"tvdb": int(tvdb), "ep": int(ep)}
        _save()
        r = next((x for x in pick if x["tmdb"] == tmdb), None)
        if r:
            ready2, _, _ = _scan()
            r2 = next((x for x in ready2 if x["tmdb"] == tmdb), None)
            if r2 and not r2.get("has_file"):
                return _move_one(r2, token, "picked")
        _wake.set()
        return "Got it. It moves on the next look."
    if kind == "undo":
        msg = _undo(rest, token)
        _wake.set()
        return msg
    if kind == "fix":
        return _fix(rest, token)
    return "Glowfin didn't understand that button."


# ---------------------------------------------------------------- for the apps (anyone signed in)


def _tvdb_for(query):
    tvdb = (query.get("tvdb") or [""])[0]
    if tvdb.isdigit():
        return int(tvdb)
    tmdb = (query.get("tmdb") or [""])[0]
    if tmdb.isdigit():
        for s in _series_all():
            if str(s.get("tmdbId") or "") == tmdb:
                return s.get("tvdbId")
        raw_path = os.path.join(_dir, "anime-list-master.xml") if _dir else None
        if raw_path and os.path.exists(raw_path):
            for a in ET.parse(raw_path).iter("anime"):
                if a.get("tmdbtv") == tmdb and (a.get("tvdbid") or "").isdigit():
                    return int(a.get("tvdbid"))
    return None


def route(method, path, query, read_json, is_admin, is_kids):
    """GET /api/specials/show?tvdb=|tmdb=, POST /api/specials/get, GET /api/specials/moved. Returns (status, answer)."""
    try:
        if method == "GET" and path == "/api/specials/moved":
            with _lock:
                moves = _state.get("moves") or {}
                return 200, {"tmdb": sorted(moves.keys()), "items": {k: v.get("jf") for k, v in moves.items() if v.get("jf")}, "keep": list(_state.get("keep", []))}
        if method == "GET" and path == "/api/specials/show":
            tvdb = _tvdb_for(query)
            if not tvdb or not _arr_ready():
                return 200, {"anime": False, "pieces": [], "complete": None, "filler": {}}
            info = pieces(tvdb)
            if not info.get("anime"):
                return 200, {**info, "filler": {}}
            info["filler"] = filler(tvdb) if info.get("here") else {}
            info["tvdb"] = tvdb
            info["can_get"] = not is_kids
            return 200, info
        if method == "POST" and path == "/api/specials/get":
            if is_kids:
                return 403, {"error": "Ask a grown-up to get this."}
            body = read_json() or {}
            if body.get("all") and body.get("tvdb"):
                return 200, {"message": get_all(int(body["tvdb"]))}
            return 200, {"message": get_piece(body.get("tmdb"), body.get("episode_id"))}
        if method == "GET" and path == "/api/specials/status":
            with _lock:
                return 200, {**_status, "view": {k: (len(v) if isinstance(v, list) else v) for k, v in _view.items()}, "log": list(_log_lines[-30:])}
    except Exception as e:  # noqa: BLE001
        return 502, {"error": str(e)}
    return 404, {"error": "Not found"}
