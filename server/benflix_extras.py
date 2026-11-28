"""
Glowfin "Specials and bonus videos" (Ben 2026-11-27, design approved the same evening).

Ben: "add a way for specials to be requested in shows and Sonarr can find them and auto add them even if they are
part of a collection" and "I would definitely prefer specials/movies that are important and or canon to the story".

Loaded by benflix-games-helper.py (it sits next to it on Tower). Only the Python standard library.

  * Asking: the season picker (app and browser) has a "Specials & bonus" choice, which is Seerr's season 0.
    Seerr only sends season 0 to Sonarr when its "special episodes" switch is on, so this turns it on.
  * Labels: every special a show has on TheTVDB (read through Sonarr) gets one label:
      story  "Part of the story": TheTVDB places it between episodes, it's episode length or longer, or it's a
             movie/OVA/holiday special. Anime use the anime-movies labels (canon and mixed are story).
      nice   "Nice to have": short or loose ones (webisodes, minisodes). Only kept when a pack has them.
      skip   recaps, reunions, making-ofs, bloopers. Never searched for; a pack's copy goes to the Bonus row.
    When a show's Specials get turned on in Sonarr (by an approved ask), only the story ones stay ticked there
    (all of them when the person flipped "Get all specials"), and Sonarr searches for them.
  * Packs: a day later, anything still missing is looked for in complete-series / collection / specials packs
    through Prowlarr. Each pack's file list is read before anything downloads. The pack with the most missing
    story specials wins, and only the specials and bonus videos in it are ticked in qBittorrent.
  * Sorting: finished packs (Sonarr's own and ours) are checked for what Sonarr leaves behind. Specials go into
    the show's Specials folder with Sonarr's naming (Sonarr is told to look again), bonus videos go into the
    folders Jellyfin shows as the Bonus row (deleted scenes, behind the scenes, interviews, featurettes, shorts,
    clips, extras), at show or season level. Files are hard-linked (copied only when that's impossible), so
    the torrent keeps seeding and nothing uses double space. Unsure files wait on the card. "Put back last
    sort" removes what the last sort added.

File work runs in a short-lived container that sees Tower's /mnt (the helper itself only sees Media), made from
the helper's own image. Notes in /skips/extras.
"""

import base64
import difflib
import hashlib
import json
import os
import re
import threading
import time
import unicodedata
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET

try:
    import benflix_onepace_get as qbit  # qBittorrent sign-in and calls (shared with One Pace)
except ImportError:  # pragma: no cover
    qbit = None
try:
    import benflix_specials as anime  # anime lists and canon/mixed labels
except ImportError:  # pragma: no cover
    anime = None

CATEGORY = "glowfin-specials"
EVERY = 120  # seconds between quick looks (new asks)
SORT_EVERY = 5  # every 5th look also sorts finished packs and hunts
DAY = 86400
WEEK = 7 * DAY
PER_ROUND = 25  # packs checked per sort round
VIDEO = {".mkv", ".mp4", ".avi", ".m4v", ".mov", ".ts", ".webm", ".wmv", ".mpg", ".mpeg", ".vob", ".m2ts"}
PROWLARR = (os.environ.get("PROWLARR_URL", "").rstrip("/"), os.environ.get("PROWLARR_KEY", ""))

LABEL_WORDS = {"story": "Part of the story", "nice": "Nice to have", "skip": "Skip"}
SKIP_RE = re.compile(
    r"(?i)\b(recaps?|digest|compilation|summary|soushuuhen|reunion|behind[ ._-]the[ ._-]scenes|making[ ._-]of|"
    r"the making|bloopers?|gag[ ._-]reel|outtakes?|deleted[ ._-]scenes?|featurettes?|interviews?|sneak[ ._-]peek|"
    r"previews?|trailers?|teasers?|promo|commentary|retrospective|inside the episode|after[ ._-]?show|q ?& ?a|panel)\b|総集編"
)
STORY_RE = re.compile(r"(?i)\b(movie|film|ova|oad|christmas|holiday|halloween|thanksgiving|new year'?s|finale|feature[ ._-]length)\b")
JUNK_TITLE = re.compile(r"(?i)^(special|specials|bonus|extra|extras|episode \d+|tba|tbd|untitled)$")
BONUS = (
    ("deleted scenes", r"deleted|extended[ ._-]scenes?|alternate[ ._-](scenes?|endings?)|cut[ ._-]scenes?|unaired[ ._-]scenes?"),
    ("behind the scenes", r"behind[ ._-]the[ ._-]scenes|\bbts\b|making[ ._-]of|the[ ._-]making|on[ ._-]set|production[ ._-]diar"),
    ("interviews", r"interviews?|\bq[ ._-]?&[ ._-]?a\b|\bpanel\b|comic[ ._-]?con|conversation[ ._-]with"),
    ("featurettes", r"featurettes?|documentary|retrospective|inside[ ._-]the|a[ ._-]look[ ._-]at|special[ ._-]features?"),
    ("shorts", r"\bshorts?\b|webisodes?|minisodes?|mini[ ._-]episodes?"),
    ("clips", r"\b(pv\d*|cm\d*|promos?|teasers?|trailers?|previews?|sneak[ ._-]peek|tv[ ._-]spots?)\b"),
    ("extras", r"\b(nc[ ._-]?(op|ed)\d*|creditless|clean[ ._-](opening|ending)|textless|bloopers?|gag[ ._-]reel|outtakes?|recaps?|commentary|menus?|extras?|bonus)\b"),
)
BONUS_RE = [(kind, re.compile(r"(?i)" + rx)) for kind, rx in BONUS]
BONUS_DIR = re.compile(
    r"(?i)(^|/)(extras?|bonus([ ._-](disc|discs|features|content|material))?|special[ ._-]features|featurettes|"
    r"behind[ ._-]the[ ._-]scenes|deleted[ ._-]scenes|interviews|shorts|supplements|disc[ ._-]?\d+[ ._-]extras)(/|$)"
)
SPECIAL_DIR = re.compile(r"(?i)(^|/)(specials?|sp|ova|ovas|oad|oads|season[ ._-]?0+|s0+)(/|$)")
NOISE = re.compile(
    r"(?i)\[[^\]]*\]|\([^)]*\)|\b(\d{3,4}p|x26[45]|h\.?26[45]|hevc|avc|aac\d?(\.\d)?|ac3|e-?ac-?3|ddp?\d?(\.\d)?|dts(-hd)?|"
    r"truehd|atmos|flac|opus|bluray|blu-ray|bdrip|brrip|web-?dl|webrip|web|hdtv|dvdrip|dvd|remux|10bit|8bit|hdr\d*|"
    r"dv|sdr|multi|dual[ ._-]audio|subbed|dubbed|proper|repack|complete|uncut|amzn|nf|hulu|dsnp|hmax|atvp)\b"
)

_jf = None
_admin = None
_tools = None
_seerr_link = None
_seerr = None
_dir = None
_lock = threading.RLock()
_wake = threading.Event()
_log_lines = []
_state = {}
_cache = {}
_view = {"problem": None, "last": None, "busy": False}


def log(text):
    print("extras: " + text, flush=True)
    with _lock:
        _log_lines.append(time.strftime("%Y-%m-%d %H:%M ") + text)
        del _log_lines[:-80]


# ---------------------------------------------------------------- saving


def _pick_dir():
    for d in (os.environ.get("EXTRAS_DIR"), "/skips/extras", "/tracks/benflix-extras", "/tmp/benflix-extras"):
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


def _save():
    if not _dir:
        return
    with _lock:
        data = json.dumps(_state)
    tmp = os.path.join(_dir, "state.json.tmp")
    try:
        with open(tmp, "w") as f:
            f.write(data)
        os.replace(tmp, os.path.join(_dir, "state.json"))
    except OSError as e:
        log(f"couldn't save notes: {e}")


def _load():
    try:
        with open(os.path.join(_dir or "", "state.json")) as f:
            return json.load(f) or {}
    except (OSError, ValueError, TypeError):
        return {}


def _cached(kind, key, max_age, fetch):
    hit = _cache.get((kind, key))
    if hit and time.time() - hit[0] < max_age:
        return hit[1]
    value = fetch()
    _cache[(kind, key)] = (time.time(), value)
    return value


# ---------------------------------------------------------------- Sonarr, Prowlarr, Jellyfin, Seerr


def _arr(method, path, body=None, timeout=30):
    return _tools._arr("sonarr", method, path, body, timeout=timeout)


def _ready():
    return _tools is not None and _tools.arr_ready("sonarr")


def _series_all(fresh=False):
    if fresh:
        _cache.pop(("sonarr", "series"), None)
    return _cached("sonarr", "series", 300, lambda: _arr("GET", "/api/v3/series") or [])


def _episodes(series_id, fresh=False):
    if fresh:
        _cache.pop(("eps", series_id), None)
    return _cached("eps", series_id, 300, lambda: _arr("GET", f"/api/v3/episode?seriesId={series_id}") or [])


def _episode_files(series_id):
    return _cached("files", series_id, 300, lambda: _arr("GET", f"/api/v3/episodefile?seriesId={series_id}") or [])


def _prowlarr_ready():
    return bool(PROWLARR[0] and PROWLARR[1])


def _prowlarr(path, timeout=90):
    req = urllib.request.Request(PROWLARR[0] + path, headers={"X-Api-Key": PROWLARR[1], "Accept": "application/json"})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return json.loads(r.read() or b"null")


def _token():
    try:
        token, user = _admin() if _admin else (None, None)
    except Exception:  # noqa: BLE001
        token, user = None, None
    return token, user


def _jf_series_for(series, token):
    if not (token and _jf):
        return None
    try:
        q = urllib.parse.urlencode({"IncludeItemTypes": "Series", "Recursive": "true", "Fields": "ProviderIds,Path", "EnableImages": "false"})
        status, body, _ = _jf("GET", "/Items?" + q, token=token)
        if status != 200:
            return None
        items = json.loads(body or b"{}").get("Items") or []
    except Exception:  # noqa: BLE001
        return None
    tvdb = str(series.get("tvdbId") or "")
    name = os.path.basename(str(series.get("path") or "").rstrip("/"))
    for s in items:
        if tvdb and str((s.get("ProviderIds") or {}).get("Tvdb") or "") == tvdb:
            return s
    for s in items:
        if name and os.path.basename(str(s.get("Path") or "").rstrip("/")) == name:
            return s
    return None


def _refresh(series):
    """Sonarr looks at the show's folder again (new specials), Jellyfin reads the show again (new bonus videos)"""
    try:
        _arr("POST", "/api/v3/command", {"name": "RescanSeries", "seriesId": series["id"]})
    except Exception as e:  # noqa: BLE001
        log(f"Sonarr didn't take the look-again for {series.get('title')}: {e}")
    token, _ = _token()
    item = _jf_series_for(series, token)
    if item:
        try:
            _jf("POST", f"/Items/{item['Id']}/Refresh?Recursive=true&MetadataRefreshMode=Default&ImageRefreshMode=Default", token=token)
        except Exception as e:  # noqa: BLE001
            log(f"Jellyfin didn't take the look-again for {series.get('title')}: {e}")
    _cache.pop(("eps", series["id"]), None)
    _cache.pop(("files", series["id"]), None)
    _cache.pop(("labels", series["id"]), None)


def _seerr_switch():
    """Seerr's "special episodes" switch has to be on, or season 0 never reaches Sonarr. Checked twice a day."""
    with _lock:
        if time.time() - float(_state.get("seerr_at") or 0) < DAY / 2:
            return
    token, _ = _token()
    if not (token and _seerr_link and _seerr):
        return
    try:
        link = _seerr_link(token)
        if not link:
            return
        main = _seerr(link, "GET", "/settings/main") or {}
        if "enableSpecialEpisodes" in main and not main.get("enableSpecialEpisodes"):
            _seerr(link, "POST", "/settings/main", {"enableSpecialEpisodes": True})
            log("turned on Seerr's special episodes switch, so Specials can be asked for")
        with _lock:
            _state["seerr_at"] = time.time()
            _state["seerr_ok"] = "enableSpecialEpisodes" in main
    except Exception as e:  # noqa: BLE001
        log(f"couldn't check Seerr's special episodes switch: {e}")


# ---------------------------------------------------------------- labels


def _norm(text):
    text = unicodedata.normalize("NFKD", str(text or "")).encode("ascii", "ignore").decode().lower()
    text = re.sub(r"[^a-z0-9]+", " ", text)
    return re.sub(r"\s+", " ", text).strip()


def _generic_label(ep):
    title = str(ep.get("title") or "")
    if SKIP_RE.search(title):
        return "skip"
    if ep.get("airsAfterSeasonNumber") or ep.get("airsBeforeSeasonNumber") or ep.get("airsBeforeEpisodeNumber"):
        return "story"
    if (ep.get("runtime") or 0) >= 20:
        return "story"
    if STORY_RE.search(title):
        return "story"
    return "nice"


ANIME_LABEL = {"canon": "story", "mixed": "story", "recap": "skip", "filler": "skip", "extra": "nice"}


def labels(series):
    """{special number: {"label", "title", "have", "aired", "monitored", "id", episode bits}} for one Sonarr show"""

    def fetch():
        eps = [e for e in _episodes(series["id"]) if e.get("seasonNumber") == 0 and e.get("episodeNumber")]
        anime_labels = {}
        if anime is not None and str(series.get("seriesType") or "") == "anime":
            try:
                info = anime.pieces(series.get("tvdbId"))
                for p in info.get("pieces") or []:
                    if p.get("ep") is not None and p.get("label") in ANIME_LABEL:
                        anime_labels[p["ep"]] = ANIME_LABEL[p["label"]]
            except Exception as e:  # noqa: BLE001
                log(f"anime labels for {series.get('title')} didn't load: {e}")
        today = time.strftime("%Y-%m-%d")
        out = {}
        for e in eps:
            n = int(e["episodeNumber"])
            out[n] = {
                "id": e.get("id"),
                "title": e.get("title") or f"Special {n}",
                "label": anime_labels.get(n) or _generic_label(e),
                "have": bool(e.get("hasFile")),
                "aired": bool(e.get("airDate") and str(e.get("airDate"))[:10] <= today),
                "monitored": bool(e.get("monitored")),
                "before_s": e.get("airsBeforeSeasonNumber"),
                "before_e": e.get("airsBeforeEpisodeNumber"),
                "after_s": e.get("airsAfterSeasonNumber"),
            }
        return out

    return _cached("labels", series["id"], 3600, fetch)


def _anime_map(tvdb):
    """For anime packs: OVA/OAD and "SP n" numbers -> TheTVDB special numbers, from the fan-made anime lists"""

    def fetch():
        out = {"sp": {}, "ova": []}
        if anime is None:
            return out
        try:
            anime._load_lists()
            path = os.path.join(anime._dir or "", "anime-list-master.xml")
            root = ET.parse(path).getroot()
        except Exception:  # noqa: BLE001
            return out
        for a in root.iter("anime"):
            if a.get("tvdbid") != str(tvdb):
                continue
            name = (a.findtext("name") or "").strip()
            if a.get("defaulttvdbseason") == "0":
                off = a.get("episodeoffset") or "0"
                start = (int(off) if off.lstrip("-").isdigit() else 0) + 1
                for m in a.iter("mapping"):
                    if m.get("anidbseason") == "1" and m.get("tvdbseason") == "0" and m.text:
                        pair = re.search(r";1-(\d+)", m.text)
                        if pair and int(pair.group(1)) > 0:
                            start = int(pair.group(1))
                if not a.get("tmdbid", "").isdigit():  # movies are filed by the anime-movies feature
                    out["ova"].append({"name": name, "start": start})
            for m in a.iter("mapping"):
                if m.get("anidbseason") == "0" and m.get("tvdbseason") == "0" and m.text:
                    for pair in re.findall(r";(\d+)-(\d+)", m.text):
                        out["sp"].setdefault(int(pair[0]), int(pair[1]))
        return out

    return _cached("animemap", tvdb, DAY, fetch)


# ---------------------------------------------------------------- what is this file?


def _bonus_kind(text):
    for kind, rx in BONUS_RE:
        if rx.search(text):
            return kind
    return None


def _clean_stem(stem, show_title):
    s = stem.replace("_", " ").replace(".", " ")
    s = NOISE.sub(" ", s)
    s = re.sub(r"(?i)\bS\d{1,2}\s?E\d{1,3}(-?E\d{1,3})?\b", " ", s)
    s = re.sub(r"\b(19|20)\d{2}\b", " ", s)
    s = re.sub(r"-\s*[A-Za-z0-9]+$", " ", s.strip())  # release group at the end
    n = _norm(s)
    t = _norm(re.sub(r"\(\d{4}\)", "", show_title or ""))
    if t and n.startswith(t + " "):
        n = n[len(t) + 1:]
    return n


def _title_match(stem, ctx):
    """-> (special number or None, [up to 3 close ones]) by TheTVDB's special names"""
    clean = _clean_stem(stem, ctx["title"])
    if len(clean) < 3:
        return None, []
    scores = []
    for n, sp in ctx["specials"].items():
        t = _norm(sp["title"])
        if len(t) < 4 or JUNK_TITLE.match(t):
            continue
        if len(t) >= 6 and re.search(r"(^| )" + re.escape(t) + r"( |$)", clean):
            score = 1.0
        else:
            score = difflib.SequenceMatcher(None, clean, t).ratio()
        if score >= 0.82:
            scores.append((score, n))
    scores.sort(reverse=True)
    if not scores:
        return None, []
    if len(scores) == 1 or scores[0][0] - scores[1][0] >= 0.08:
        return scores[0][1], []
    return None, [n for _, n in scores[:3]]


def _season_hint(text):
    m = re.search(r"(?i)\bS(\d{1,2})(?=E\d)|\bseason[ ._-]?(\d{1,2})\b|(?:^|/)S(\d{1,2})(?:/|$)", text)
    if not m:
        return None
    return int(next(g for g in m.groups() if g))


def classify(relpath, ctx):
    """One file inside a pack -> {"kind": special|bonus|episode|ask|ignore, ...}"""
    p = str(relpath).replace("\\", "/")
    name = p.rsplit("/", 1)[-1]
    stem, ext = os.path.splitext(name)
    folders = p[: -len(name)].rstrip("/")
    if ext.lower() not in VIDEO:
        return {"kind": "ignore"}
    if re.search(r"(?i)(^|[/ ._-])sample([/ ._-]|$)", p):
        return {"kind": "ignore"}
    season = _season_hint(p)
    in_bonus_dir = bool(BONUS_DIR.search(folders))
    in_special_dir = bool(SPECIAL_DIR.search(folders))
    specials = ctx["specials"]

    def special(n, how):
        sp = specials.get(n)
        if not sp:
            return {"kind": "ask", "why": f"It says special {n}, but TheTVDB has no special {n}.", "choices": []}
        if sp["label"] == "skip":
            return {"kind": "bonus", "folder": _bonus_kind(sp["title"] + " " + stem) or "extras", "season": None, "ep": n}
        return {"kind": "special", "ep": n, "how": how}

    m = re.search(r"(?i)\bS0*0[ ._-]?E(\d{1,3})\b|\b0x(\d{2,3})\b", name)
    if m:
        return special(int(m.group(1) or m.group(2)), "number in the name")
    # Creditless openings/endings and promo clips are never specials or episodes
    nc = re.search(r"(?i)\b(nc[ ._-]?(op|ed)\d*|creditless|clean[ ._-](opening|ending)|textless|pv\d*|cm\d*)\b", stem)
    if nc:
        return {"kind": "bonus", "folder": _bonus_kind(stem) or "extras", "season": season}
    reg = re.search(r"(?i)\bS(\d{1,2})[ ._-]?E(\d{1,3})\b|\b(\d{1,2})x(\d{2,3})\b", name)
    if reg and not in_bonus_dir:
        s_no, e_no = int(reg.group(1) or reg.group(3)), int(reg.group(2) or reg.group(4))
        if s_no > 0 and e_no > 0:
            return {"kind": "episode"}
        if s_no > 0 and e_no == 0:  # "S01E00": TheTVDB's special that comes before that season, if there's one
            hits = [n for n, sp in specials.items() if sp.get("before_s") == s_no and sp.get("before_e") in (1, None)]
            if len(hits) == 1:
                return special(hits[0], "comes before the season")
            return {"kind": "ask", "why": f"Season {s_no} episode 0 could be any special.", "choices": hits[:3]}
    # Anime: OVA 2, OAD, SP1, Special 03, Episode 12.5, Specials/03.mkv
    amap = ctx.get("anime") or {"sp": {}, "ova": []}
    ova = re.search(r"(?i)\b(OVA|OAD|OAV)s?(?:[ ._#-]*0*(\d{1,3}))?\b", stem)
    spn = re.search(r"(?i)\b(?:SP|Special|Specials)[ ._#-]*0*(\d{1,3})\b", stem)
    half = re.search(r"(?<![\d.])(\d{1,3})\.5(?![\d])", stem)
    bare = None
    if in_special_dir and not (ova or spn or half):
        b = re.search(r"(?:^|[ ._\-\[(])(?:E|EP)?0*(\d{1,3})(?:v\d)?(?:$|[ ._\-\])])", _clean_stem(stem, "") or stem)
        bare = int(b.group(1)) if b else None
    if ova:
        n = int(ova.group(2) or 1)
        ovas = amap["ova"]
        if len(ovas) == 1:
            return special(ovas[0]["start"] + n - 1, "anime lists (OVA)")
        named = [o for o in ovas if re.search(r"(?i)\b(OVA|OAD|OAV)\b", o["name"])]
        if len(named) == 1:
            return special(named[0]["start"] + n - 1, "anime lists (OVA)")
        n2, close = _title_match(stem, ctx)
        if n2:
            return special(n2, "name")
        return {"kind": "ask", "why": f"An OVA ({ova.group(0)}), and the anime lists don't say which special it is.", "choices": close or [o["start"] + n - 1 for o in ovas][:3]}
    if spn or bare is not None:
        n = int(spn.group(1)) if spn else bare
        if n in amap["sp"]:
            return special(amap["sp"][n], "anime lists (SP)")
        if not amap["sp"] and not amap["ova"] and n in specials:
            return special(n, "special number")
        n2, close = _title_match(stem, ctx)
        if n2:
            return special(n2, "name")
        return {"kind": "ask", "why": f"Special {n} in the pack's own numbering, which doesn't say which TheTVDB special it is.", "choices": close or ([n] if n in specials else [])}
    if half:
        after = int(half.group(1))
        s_no = season or 1
        hits = [x for x, sp in specials.items() if sp.get("before_s") == s_no and sp.get("before_e") == after + 1]
        if len(hits) == 1:
            return special(hits[0], "between episodes on TheTVDB")
        n2, close = _title_match(stem, ctx)
        if n2:
            return special(n2, "name")
        return {"kind": "ask", "why": f"Episode {half.group(0)} sits between episodes {after} and {after + 1}.", "choices": close or hits[:3]}
    # A regular episode in other naming ("Show - 05 - Title", "Episode 5"): Sonarr's, unless it sits in a bonus folder
    spaced = stem.replace("_", " ").replace(".", " ")
    if not (in_bonus_dir or in_special_dir) and re.search(r"(?i)(?:^|\s)-\s*0*\d{1,4}(?:v\d)?(?:\s|$)|\b(?:ep|episode)\s*0*\d{1,4}\b", spaced):
        return {"kind": "episode"}
    n2, close = _title_match(stem, ctx)
    if n2:
        return special(n2, "name")
    if close:
        return {"kind": "ask", "why": "Its name is close to more than one special.", "choices": close}
    kind = _bonus_kind(stem) or (_bonus_kind(folders) if in_bonus_dir else None)
    if kind or in_bonus_dir:
        return {"kind": "bonus", "folder": kind or "extras", "season": season}
    return {"kind": "ignore"}


def _ctx(series):
    return {
        "title": series.get("title") or "",
        "specials": labels(series),
        "anime": _anime_map(series.get("tvdbId")) if str(series.get("seriesType") or "") == "anime" else None,
    }


# ---------------------------------------------------------------- paths and the file worker


def _docker_raw(method, path, body=None, timeout=30):
    import http.client
    import socket

    conn = http.client.HTTPConnection("localhost", timeout=timeout)
    sock = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
    sock.settimeout(timeout)
    try:
        sock.connect("/var/run/docker.sock")
        conn.sock = sock
        data = json.dumps(body).encode() if body is not None else None
        conn.request(method, path, body=data, headers={"Content-Type": "application/json"} if data else {})
        r = conn.getresponse()
        raw = r.read()
    finally:
        conn.close()
    if r.status >= 400:
        raise OSError(f"Docker answered {r.status}: {raw[:200]!r}")
    return json.loads(raw) if raw and raw[:1] in (b"{", b"[") else None


def _containers():
    return _cached("docker", "containers", 120, lambda: _docker_raw("GET", "/containers/json?all=1") or [])


def _to_host(hint, inside):
    """Tower's own path for a path the given container sees"""
    best = None
    for c in _containers():
        if hint not in " ".join(c.get("Names") or []).lower():
            continue
        for m in c.get("Mounts") or []:
            dest = (m.get("Destination") or "").rstrip("/")
            if dest and (inside == dest or inside.startswith(dest + "/")) and (not best or len(dest) > len(best[0])):
                best = (dest, (m.get("Source") or "").rstrip("/"))
        if best:
            break
    return best[1] + inside[len(best[0]):] if best else None


def _host_series_path(series):
    return _to_host("sonarr", str(series.get("path") or "").rstrip("/"))


RUNNER = r"""
import json, os, shutil, sys
job = json.load(open(sys.argv[1]))
out = []
for op in job["ops"]:
    dst = op.get("dst")
    try:
        if op["do"] == "link":
            if os.path.exists(dst):
                out.append([dst, "there"]); continue
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            try:
                os.link(op["src"], dst); how = "linked"
            except OSError:
                shutil.copy2(op["src"], dst); how = "copied"
            for p in (os.path.dirname(dst), dst):
                try: os.chown(p, 99, 100)
                except OSError: pass
            out.append([dst, how])
        elif op["do"] == "remove":
            if os.path.isfile(dst): os.remove(dst)
            try: os.rmdir(os.path.dirname(dst))
            except OSError: pass
            out.append([dst, "removed"])
    except Exception as e:
        out.append([dst, "problem: %s" % e])
json.dump(out, open(sys.argv[2], "w"))
"""


def _run_ops(ops):
    """Runs file links/removals on Tower itself (a short-lived container that sees /mnt) -> {dst: result}"""
    if not ops:
        return {}
    job_dir = os.path.join(_dir, "job")
    os.makedirs(job_dir, exist_ok=True)
    with open(os.path.join(job_dir, "job.json"), "w") as f:
        json.dump({"ops": ops}, f)
    try:
        os.remove(os.path.join(job_dir, "out.json"))
    except OSError:
        pass
    host_job = _to_host("benflix-games", job_dir)
    if not host_job:
        raise RuntimeError("The helper couldn't find its own notes folder on Tower.")
    me = _docker_raw("GET", "/containers/benflix-games/json") or {}
    image = ((me.get("Config") or {}).get("Image")) or "python:3.12-alpine"
    made = _docker_raw(
        "POST",
        f"/containers/create?name=glowfin-extras-{int(time.time())}",
        {
            "Image": image,
            "Cmd": ["python", "-c", RUNNER, "/job/job.json", "/job/out.json"],
            "Entrypoint": [],
            "User": "0",
            "HostConfig": {"Binds": ["/mnt:/mnt:rslave", f"{host_job}:/job"], "NetworkMode": "none"},
        },
    )
    cid = (made or {}).get("Id")
    try:
        _docker_raw("POST", f"/containers/{cid}/start")
        _docker_raw("POST", f"/containers/{cid}/wait", timeout=6 * 3600)
        with open(os.path.join(job_dir, "out.json")) as f:
            return {dst: how for dst, how in json.load(f)}
    finally:
        try:
            _docker_raw("DELETE", f"/containers/{cid}?force=1")
        except Exception:  # noqa: BLE001
            pass


# ---------------------------------------------------------------- watching asks (Sonarr's Specials turned on)


def _apply_labels(series, all_specials):
    """Ticks only the story specials (or all of them) in Sonarr and asks it to search for the missing ones"""
    lab = labels(series)
    on = [sp["id"] for sp in lab.values() if all_specials or sp["label"] == "story"]
    off = [sp["id"] for sp in lab.values() if sp["id"] not in on]
    if off:
        _arr("PUT", "/api/v3/episode/monitor", {"episodeIds": off, "monitored": False})
    if on:
        _arr("PUT", "/api/v3/episode/monitor", {"episodeIds": on, "monitored": True})
    search = [sp["id"] for sp in lab.values() if sp["id"] in on and not sp["have"] and sp["aired"]]
    if search:
        _arr("POST", "/api/v3/command", {"name": "EpisodeSearch", "episodeIds": search})
    story = sum(1 for sp in lab.values() if sp["label"] == "story")
    log(f"{series.get('title')}: Specials asked for, {story} part of the story, {len(on)} ticked, searching {len(search)}")
    _cache.pop(("labels", series["id"]), None)


def _watch():
    series = _series_all(fresh=True)
    now = time.time()
    with _lock:
        shows = _state.setdefault("shows", {})
        want = _state.setdefault("want", {})
        first = not _state.get("started")
        if first:
            _state["started"] = now
    changed = first
    for s in series:
        tvdb = str(s.get("tvdbId") or "")
        if not tvdb:
            continue
        s0 = next((x for x in s.get("seasons") or [] if x.get("seasonNumber") == 0), None)
        on = bool(s0 and s0.get("monitored"))
        with _lock:
            known = shows.get(tvdb)
        if on and not known:
            if first:
                with _lock:
                    shows[tvdb] = {"title": s.get("title"), "old": True, "at": now}
                continue
            w = want.pop(str(s.get("tmdbId") or ""), None) or {}
            try:
                _apply_labels(s, bool(w.get("all")))
            except Exception as e:  # noqa: BLE001
                log(f"{s.get('title')}: couldn't tick the specials in Sonarr: {e}")
            with _lock:
                shows[tvdb] = {"title": s.get("title"), "series": s["id"], "at": now, "all": bool(w.get("all")), "hunts": 0, "next": now + DAY}
            changed = True
        elif not on and known:
            with _lock:
                shows.pop(tvdb, None)
            changed = True
    with _lock:
        for k in [k for k, w in want.items() if now - float(w.get("at") or 0) > 30 * DAY]:
            want.pop(k, None)
    if changed:
        _save()


# ---------------------------------------------------------------- finding packs (Prowlarr) and reading their file lists


def _bdecode(data, i=0):
    """-> (value, next index). Dicts keep the raw bytes of "info" under b"__info_raw" """
    c = data[i:i + 1]
    if c == b"i":
        j = data.index(b"e", i)
        return int(data[i + 1:j]), j + 1
    if c == b"l":
        i, out = i + 1, []
        while data[i:i + 1] != b"e":
            v, i = _bdecode(data, i)
            out.append(v)
        return out, i + 1
    if c == b"d":
        i, out = i + 1, {}
        while data[i:i + 1] != b"e":
            k, i = _bdecode(data, i)
            start = i
            v, i = _bdecode(data, i)
            out[k] = v
            if k == b"info":
                out[b"__info_raw"] = data[start:i]
        return out, i + 1
    j = data.index(b":", i)
    n = int(data[i:j])
    return data[j + 1:j + 1 + n], j + 1 + n


def _torrent_files(raw):
    """A .torrent file -> (info hash, [(path, size)], trackers)"""
    meta, _ = _bdecode(raw)
    info = meta[b"info"]
    h = hashlib.sha1(meta[b"__info_raw"]).hexdigest()
    root = info.get(b"name", b"").decode("utf-8", "replace")
    files = []
    if b"files" in info:
        for f in info[b"files"]:
            parts = [x.decode("utf-8", "replace") for x in f.get(b"path.utf-8") or f.get(b"path") or []]
            files.append(("/".join([root] + parts), int(f.get(b"length") or 0)))
    else:
        files.append((root, int(info.get(b"length") or 0)))
    trackers = []
    for tier in meta.get(b"announce-list") or []:
        for t in tier:
            trackers.append(t.decode("utf-8", "replace"))
    if meta.get(b"announce"):
        trackers.insert(0, meta[b"announce"].decode("utf-8", "replace"))
    return h, files, list(dict.fromkeys(trackers))[:12]


def _btih(magnet):
    m = re.search(r"(?i)xt=urn:btih:([a-z0-9]+)", magnet or "")
    if not m:
        return None
    v = m.group(1)
    if len(v) == 32:
        v = base64.b32decode(v.upper()).hex()
    return v.lower()


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def _fetch_release(r):
    """-> (hash, magnet, [(path, size)]) or None. Reads the file list without downloading the videos."""
    url = r.get("downloadUrl")
    if url:
        try:
            opener = urllib.request.build_opener(_NoRedirect)
            with opener.open(urllib.request.Request(url, headers={"X-Api-Key": PROWLARR[1]}), timeout=60) as resp:
                raw = resp.read()
            if raw[:1] == b"d":
                h, files, trackers = _torrent_files(raw)
                mag = "magnet:?xt=urn:btih:" + h + "&dn=" + urllib.parse.quote(r.get("title") or "") + "".join("&tr=" + urllib.parse.quote(t, safe="") for t in trackers)
                return h, mag, files
        except urllib.error.HTTPError as e:
            loc = e.headers.get("Location") if e.code in (301, 302, 303, 307, 308) else None
            if loc and loc.startswith("magnet:"):
                r = {**r, "magnetUrl": loc}
        except Exception as e:  # noqa: BLE001
            log(f"couldn't read {r.get('title')}: {e}")
    mag = r.get("magnetUrl") or (r.get("guid") if str(r.get("guid") or "").startswith("magnet:") else None)
    h = _btih(mag)
    if not (mag and h and qbit):
        return None
    # Only a magnet: qBittorrent fetches the file list and stops right there (nothing downloads)
    try:
        _category()
        qbit.qb("/api/v2/torrents/add", {"urls": mag, "category": CATEGORY, "tags": "glowfin-peek", "stopCondition": "MetadataReceived"})
        for _ in range(60):
            files = qbit.qb_json("/api/v2/torrents/files", {"hash": h}) or []
            if files:
                return h, mag, [(f.get("name") or "", int(f.get("size") or 0)) for f in files]
            time.sleep(3)
    except Exception as e:  # noqa: BLE001
        log(f"qBittorrent couldn't read {r.get('title')}: {e}")
    _forget(h)
    return None


def _forget(h):
    try:
        qbit.qb("/api/v2/torrents/delete", {"hashes": h, "deleteFiles": "true"})
    except Exception:  # noqa: BLE001
        pass


def _category():
    try:
        qbit.qb("/api/v2/torrents/createCategory", {"category": CATEGORY})
    except Exception:  # noqa: BLE001
        pass  # already there


def _score(files, ctx, all_specials):
    """-> (story, nice, bonus, wanted paths, wanted size)"""
    story, nice, bonus, want, size = set(), set(), 0, [], 0
    for path, length in files:
        c = classify(path, ctx)
        if c["kind"] == "special":
            sp = ctx["specials"][c["ep"]]
            if sp["have"]:
                continue
            (story if sp["label"] == "story" else nice).add(c["ep"])
        elif c["kind"] == "bonus":
            bonus += 1
        elif c["kind"] == "ask":
            pass
        else:
            continue
        want.append(path)
        size += length
    if all_specials:
        story, nice = story | nice, set()
    return len(story), len(nice), bonus, want, size


def _search_terms(series):
    title = re.sub(r"\s*\(\d{4}\)\s*$", "", series.get("title") or "").strip()
    terms = [f"{title} complete", f"{title} collection", f"{title} specials"]
    if str(series.get("seriesType") or "") == "anime":
        terms += [f"{title} OVA", f"{title} batch"]
    return title, terms


def _hunt(series, show):
    """Looks for a pack holding missing story specials, picks the best one and starts only the wanted files"""
    lab = labels(series)
    all_sp = bool(show.get("all"))
    missing = [n for n, sp in lab.items() if not sp["have"] and sp["aired"] and (all_sp or sp["label"] == "story")]
    if not missing:
        return "has every special it needs"
    if not _prowlarr_ready():
        return "can't look for packs: Prowlarr isn't connected to the helper yet"
    if qbit is None:
        return "can't look for packs: qBittorrent isn't connected"
    title, terms = _search_terms(series)
    words = [w for w in _norm(title).split() if w not in ("the", "a", "an", "and", "of")]
    seen, found = set(), []
    for term in terms:
        q = urllib.parse.urlencode([("query", term), ("type", "search"), ("limit", "100"), ("categories", "5000"), ("categories", "5070")])
        try:
            results = _prowlarr("/api/v1/search?" + q) or []
        except Exception as e:  # noqa: BLE001
            log(f"Prowlarr search for {term!r} didn't work: {e}")
            continue
        for r in results:
            key = r.get("infoHash") or r.get("guid") or r.get("title")
            if key in seen or str(r.get("protocol") or "torrent") != "torrent" or int(r.get("seeders") or 0) < 2:
                continue
            name = _norm(r.get("title"))
            if not all(re.search(r"(^| )" + re.escape(w) + r"( |$)", name) for w in words):
                continue
            seen.add(key)
            found.append(r)
    found.sort(key=lambda r: -int(r.get("seeders") or 0))
    ctx = _ctx(series)
    best = None
    peeked = []
    for r in found[:8]:
        got = _fetch_release(r)
        if not got:
            continue
        h, mag, files = got
        peeked.append(h)
        story, nice, bonus, want, size = _score(files, ctx, all_sp)
        if story < 1:
            continue
        rank = (story, nice, bonus, -size)
        if not best or rank > best[0]:
            best = (rank, r, h, mag, want, size)
    with _lock:
        mine = set((_state.get("grabs") or {}).keys())
    for h in peeked:
        if (not best or h != best[2]) and h not in mine:
            _forget_if_peek(h)
    if not best:
        return f"looked through {len(found)} packs, none had the missing story specials"
    rank, r, h, mag, want, size = best
    _category()
    try:
        qbit.qb("/api/v2/torrents/add", {"urls": mag, "category": CATEGORY, "tags": "glowfin-specials", "stopCondition": "MetadataReceived"})
    except Exception:  # noqa: BLE001
        pass  # already added while peeking
    with _lock:
        _state.setdefault("grabs", {})[h] = {
            "tvdb": str(series.get("tvdbId")), "series": series["id"], "show": series.get("title"), "release": r.get("title"),
            "want": want, "size": size, "story": rank[0], "nice": rank[1], "bonus": rank[2], "at": time.time(), "picked": False,
        }
    _save()
    threading.Thread(target=_pick_files, args=(h,), name="extras-pick", daemon=True).start()
    gb = size / 1e9
    return f"found {rank[0]} of {len(missing)} missing story specials and {rank[2]} bonus videos in \"{r.get('title')}\", getting {gb:.1f} GB of it"


def _forget_if_peek(h):
    try:
        t = (qbit.qb_json("/api/v2/torrents/info", {"hashes": h}) or [None])[0]
        if t and "glowfin-peek" in str(t.get("tags") or "") and "glowfin-specials" not in str(t.get("tags") or ""):
            _forget(h)
    except Exception:  # noqa: BLE001
        pass


def _pick_files(h):
    """Ticks only the wanted files once qBittorrent has the file list, then starts it (waits up to 3 hours)"""
    deadline = time.time() + 3 * 3600
    while time.time() < deadline:
        with _lock:
            g = (_state.get("grabs") or {}).get(h)
        if not g or g.get("picked"):
            return
        try:
            files = qbit.qb_json("/api/v2/torrents/files", {"hash": h}) or []
        except Exception:  # noqa: BLE001
            files = []
        if files:
            want = set(g.get("want") or [])
            keep = [str(f.get("index", i)) for i, f in enumerate(files) if (f.get("name") or "") in want]
            drop = [str(f.get("index", i)) for i, f in enumerate(files) if (f.get("name") or "") not in want]
            try:
                if drop:
                    qbit.qb("/api/v2/torrents/filePrio", {"hash": h, "id": "|".join(drop), "priority": "0"})
                if keep:
                    qbit.qb("/api/v2/torrents/filePrio", {"hash": h, "id": "|".join(keep), "priority": "1"})
                    try:
                        qbit.qb("/api/v2/torrents/removeTags", {"hashes": h, "tags": "glowfin-peek"})
                    except Exception:  # noqa: BLE001
                        pass
                    qbit._verb(("start", "resume"), h)
                else:
                    qbit._verb(("stop", "pause"), h)
                with _lock:
                    g["picked"] = True
                _save()
                log(f"{g.get('show')}: getting {len(keep)} files from {g.get('release')}")
            except Exception as e:  # noqa: BLE001
                log(f"couldn't tick the files in {g.get('release')}: {e}")
            return
        time.sleep(5)


def _hunts():
    with _lock:
        on = _state.get("hunt", True)
        shows = {k: dict(v) for k, v in (_state.get("shows") or {}).items() if not v.get("old")}
        busy = {g.get("tvdb") for g in (_state.get("grabs") or {}).values() if not g.get("sorted")}
    if not on:
        return
    now = time.time()
    by_tvdb = {str(s.get("tvdbId")): s for s in _series_all()}
    for tvdb, show in shows.items():
        if now < float(show.get("next") or 0) or tvdb in busy or tvdb not in by_tvdb:
            continue
        try:
            said = _hunt(by_tvdb[tvdb], show)
        except Exception as e:  # noqa: BLE001
            said = f"pack search didn't work: {e}"
        hunts = int(show.get("hunts") or 0) + 1
        with _lock:
            cur = (_state.get("shows") or {}).get(tvdb)
            if cur is not None:
                cur.update({"hunts": hunts, "said": said, "said_at": now, "next": now + (3 * DAY if hunts < 3 else 4 * WEEK)})
        log(f"{show.get('title')}: {said}")
        _save()
        break  # one show per round keeps Prowlarr and qBittorrent calm


# ---------------------------------------------------------------- sorting finished packs


def _sonarr_categories():
    def fetch():
        cats = {CATEGORY}
        for dc in _arr("GET", "/api/v3/downloadclient") or []:
            if "qbit" not in str(dc.get("implementation") or "").lower():
                continue
            for f in dc.get("fields") or []:
                if f.get("name") in ("tvCategory", "tvImportedCategory", "category", "importedCategory") and f.get("value"):
                    cats.add(str(f["value"]))
        return cats

    return _cached("sonarr", "cats", 3600, fetch)


def _series_for_download(h):
    with _lock:
        g = (_state.get("grabs") or {}).get(h)
    if g:
        return g.get("series")
    try:
        got = _arr("GET", f"/api/v3/history?downloadId={h.upper()}&pageSize=20") or {}
    except Exception:  # noqa: BLE001
        return None
    for rec in (got.get("records") if isinstance(got, dict) else got) or []:
        if rec.get("seriesId"):
            return rec["seriesId"]
    return None


def _safe(text):
    return re.sub(r'[\\/:*?"<>|]+', " ", str(text or "")).strip().rstrip(".") or "Special"


def _season_folders(series, host_root):
    out = {}
    for f in _episode_files(series["id"]):
        if f.get("seasonNumber") and f.get("path"):
            out.setdefault(f["seasonNumber"], os.path.basename(os.path.dirname(f["path"])))
    return {n: host_root + "/" + d for n, d in out.items()}


def _plan(t, series, files):
    """-> (ops, asks, counts) for one finished pack"""
    ctx = _ctx(series)
    root = _host_series_path(series)
    if not root:
        raise RuntimeError(f"couldn't find {series.get('title')}'s folder on Tower")
    seasons = _season_folders(series, root)
    show = _safe(series.get("title"))
    ops, asks, counts, used = [], [], {"special": 0, "bonus": 0}, set()
    for f in files:
        name = f.get("name") or ""
        c = classify(name, ctx)
        src = _to_host("qbittorrent", os.path.join(t.get("save_path") or "", name).rstrip("/"))
        if c["kind"] in ("ignore", "episode") or not src:
            continue
        ext = os.path.splitext(name)[1].lower()
        if c["kind"] == "special":
            sp = ctx["specials"][c["ep"]]
            if sp["have"] or c["ep"] in used:
                continue
            used.add(c["ep"])
            dst = f"{root}/Specials/{show} - S00E{c['ep']:02d} - {_safe(sp['title'])}{ext}"
            ops.append({"do": "link", "src": src, "dst": dst, "what": f"special {c['ep']}: {sp['title']}"})
            counts["special"] += 1
        elif c["kind"] == "bonus":
            base = seasons.get(c.get("season")) if c.get("season") else None
            dst = f"{base or root}/{c['folder']}/{os.path.basename(name)}"
            ops.append({"do": "link", "src": src, "dst": dst, "what": c["folder"]})
            counts["bonus"] += 1
        elif c["kind"] == "ask":
            asks.append({"file": name, "src": src, "why": c.get("why"), "choices": [n for n in c.get("choices") or [] if n in ctx["specials"]][:3]})
    return ops, asks, counts


def _finished_packs():
    cats = _sonarr_categories()
    out = []
    for t in qbit.qb_json("/api/v2/torrents/info") or []:
        if t.get("category") not in cats or float(t.get("progress") or 0) < 0.999:
            continue
        if t.get("state") in ("metaDL", "checkingDL", "checkingUP", "checkingResumeData", "moving"):
            continue
        out.append(t)
    return out


def sort_round(force=False):
    """Checks finished packs once (newest ones only, PER_ROUND at a time) -> plain words"""
    if qbit is None:
        return "qBittorrent isn't connected."
    with _lock:
        done = _state.setdefault("done", {})
        grabs = _state.setdefault("grabs", {})
    packs = [t for t in _finished_packs() if t.get("hash", "").lower() not in done]
    total = {"special": 0, "bonus": 0, "ask": 0, "packs": 0}
    for t in packs[:PER_ROUND]:
        h = t.get("hash", "").lower()
        try:
            files = [f for f in qbit.qb_json("/api/v2/torrents/files", {"hash": h}) or [] if f.get("priority") != 0 and float(f.get("progress") or 0) >= 0.999]
        except Exception as e:  # noqa: BLE001
            log(f"couldn't read the files in {t.get('name')}: {e}")
            continue
        sid = _series_for_download(h)
        series = next((s for s in _series_all() if s.get("id") == sid), None) if sid else None
        if not series or (len(files) < 2 and h not in grabs):
            with _lock:
                done[h] = time.time()
            continue
        try:
            ops, asks, counts = _plan(t, series, files)
            results = _run_ops([{k: v for k, v in o.items() if k != "what"} for o in ops])
        except Exception as e:  # noqa: BLE001
            log(f"couldn't sort {t.get('name')}: {e}")
            continue
        made = [o for o in ops if str(results.get(o["dst"], "")).startswith(("linked", "copied"))]
        problems = [f"{os.path.basename(o['dst'])}: {results.get(o['dst'])}" for o in ops if str(results.get(o["dst"], "")).startswith("problem")]
        with _lock:
            done[h] = time.time()
            if h in grabs:
                grabs[h]["sorted"] = time.time()
            for a in asks:
                key = hashlib.sha1((h + a["file"]).encode()).hexdigest()[:10]
                _state.setdefault("asks", {})[key] = {**a, "hash": h, "series": series["id"], "show": series.get("title"), "at": time.time()}
            if made:
                _state.setdefault("sorts", []).append({
                    "at": time.time(), "show": series.get("title"), "series": series["id"], "release": t.get("name"),
                    "made": [o["dst"] for o in made],
                    "specials": sum(1 for o in made if o["what"].startswith("special")),
                    "bonus": sum(1 for o in made if not o["what"].startswith("special")),
                    "copied": sum(1 for o in made if results.get(o["dst"]) == "copied"),
                })
                _state["sorts"] = _state["sorts"][-20:]
            if problems:
                _state["problem"] = "; ".join(problems[:3])
            if len(done) > 5000:
                for k in sorted(done, key=done.get)[:1000]:
                    done.pop(k, None)
        if made:
            _refresh(series)
            log(f"{series.get('title')}: filed {sum(1 for o in made if o['what'].startswith('special'))} specials and "
                f"{sum(1 for o in made if not o['what'].startswith('special'))} bonus videos from {t.get('name')}")
        total["special"] += sum(1 for o in made if o["what"].startswith("special"))
        total["bonus"] += sum(1 for o in made if not o["what"].startswith("special"))
        total["ask"] += len(asks)
        total["packs"] += 1
    _save()
    if not total["packs"]:
        return "Nothing new to sort."
    return (f"Checked {total['packs']} pack{'s' if total['packs'] != 1 else ''}: filed {total['special']} specials and {total['bonus']} bonus videos"
            + (f", {total['ask']} need you" if total["ask"] else "") + ".")


def undo_last():
    with _lock:
        sorts = _state.get("sorts") or []
        last = sorts[-1] if sorts else None
    if not last:
        return "Nothing to put back."
    results = _run_ops([{"do": "remove", "dst": d} for d in last["made"]])
    removed = sum(1 for v in results.values() if v == "removed")
    with _lock:
        if _state.get("sorts") and _state["sorts"][-1] is last:
            _state["sorts"].pop()
    _save()
    series = next((s for s in _series_all() if s.get("id") == last.get("series")), None)
    if series:
        _refresh(series)
    return f"Put back the last sort ({last.get('show')}): removed {removed} file{'s' if removed != 1 else ''}. The originals are still in the download."


def _answer(key, choice):
    with _lock:
        a = (_state.get("asks") or {}).get(key)
    if not a:
        return "That one's already done."
    series = next((s for s in _series_all() if s.get("id") == a.get("series")), None)
    if choice == "leave" or not series:
        with _lock:
            (_state.get("asks") or {}).pop(key, None)
        _save()
        return "Left it alone."
    root = _host_series_path(series)
    ext = os.path.splitext(a["file"])[1].lower()
    if choice == "bonus":
        dst = f"{root}/extras/{os.path.basename(a['file'])}"
        what = "bonus video"
    else:
        n = int(choice)
        sp = labels(series).get(n) or {"title": f"Special {n}"}
        dst = f"{root}/Specials/{_safe(series.get('title'))} - S00E{n:02d} - {_safe(sp['title'])}{ext}"
        what = f"special {n}"
    res = _run_ops([{"do": "link", "src": a["src"], "dst": dst}]).get(dst, "")
    if not str(res).startswith(("linked", "copied", "there")):
        return f"Couldn't file it: {res}"
    with _lock:
        (_state.get("asks") or {}).pop(key, None)
        _state.setdefault("sorts", []).append({"at": time.time(), "show": series.get("title"), "series": series["id"], "release": a["file"], "made": [dst], "specials": int(what.startswith("special")), "bonus": int(not what.startswith("special")), "copied": int(res == "copied")})
    _save()
    _refresh(series)
    return f"Filed it as {what}."


# ---------------------------------------------------------------- loop


def check(sort=True):
    if not _ready():
        with _lock:
            _view["problem"] = "Sonarr isn't connected to the helper yet. Run the Glowfin setup command once."
        return
    with _lock:
        _view["busy"] = True
    try:
        _watch()
        _seerr_switch()
        if sort and qbit is not None:
            sort_round()
            _hunts()
        with _lock:
            _view.update({"problem": None, "last": time.time()})
    except Exception as e:  # noqa: BLE001
        with _lock:
            _view["problem"] = str(e)
        log(f"look didn't work: {e}")
    finally:
        with _lock:
            _view["busy"] = False


def _loop():
    time.sleep(120)
    n = 0
    while True:
        try:
            check(sort=(n % SORT_EVERY == 0))
        except Exception as e:  # noqa: BLE001
            log(f"loop: {e}")
        n += 1
        _wake.wait(EVERY)
        _wake.clear()


def start(jellyfin_call, admin, tools=None, seerr_link=None, seerr=None):
    """Called once by the helper when it starts"""
    global _jf, _admin, _tools, _seerr_link, _seerr, _dir
    _jf, _admin, _tools, _seerr_link, _seerr = jellyfin_call, admin, tools, seerr_link, seerr
    _dir = _pick_dir()
    if qbit is not None and not qbit._ctx.get("dir"):
        qbit._ctx["dir"] = "/skips/onepace"  # qbit.json sign-in note is shared with One Pace
    with _lock:
        _state.clear()
        _state.update(_load())
    threading.Thread(target=_loop, name="extras", daemon=True).start()
    log(f"ready (notes in {_dir})")


# ---------------------------------------------------------------- Server page card (Server > Tools > Library)


def _progress():
    with _lock:
        grabs = {h: dict(g) for h, g in (_state.get("grabs") or {}).items() if not g.get("sorted")}
    if not grabs or qbit is None:
        return []
    try:
        info = {t.get("hash", "").lower(): t for t in qbit.qb_json("/api/v2/torrents/info", {"category": CATEGORY}) or []}
    except Exception:  # noqa: BLE001
        info = {}
    rows = []
    for h, g in grabs.items():
        t = info.get(h)
        pct = int(float((t or {}).get("progress") or 0) * 100)
        state = "finding its file list" if (t or {}).get("state") == "metaDL" else f"{pct}% done"
        rows.append({"light": "warn", "t": f"{g.get('show')}: getting {g.get('story')} story specials and {g.get('bonus')} bonus videos",
                     "s": f"From \"{g.get('release')}\", {g.get('size', 0) / 1e9:.1f} GB of it, {state}." if t else f"From \"{g.get('release')}\". qBittorrent doesn't have it any more.", "acts": []})
    return rows


def card():
    with _lock:
        asks = {k: dict(v) for k, v in (_state.get("asks") or {}).items()}
        shows = {k: dict(v) for k, v in (_state.get("shows") or {}).items() if not v.get("old")}
        sorts = list(_state.get("sorts") or [])
        hunt = _state.get("hunt", True)
        problem = _view.get("problem") or _state.get("problem")
        seerr_ok = _state.get("seerr_ok")
    rows = []
    by_id = {s.get("id"): s for s in _series_all()} if _ready() else {}
    for key, a in list(asks.items())[:12]:
        lab = labels(by_id[a["series"]]) if a.get("series") in by_id else {}
        acts = [{"label": f"Special {n}: {(lab.get(n) or {}).get('title', '')}"[:60], "do": f"pick:{key}:{n}"} for n in a.get("choices") or []]
        acts += [{"label": "It's a bonus video", "do": f"bonus:{key}"}, {"label": "Leave it", "do": f"leave:{key}"}]
        rows.append({"light": "warn", "t": f"{a.get('show')}: which one is this?", "s": f"{os.path.basename(a['file'])}. {a.get('why') or ''}".strip(), "acts": acts})
    rows += _progress()
    for tvdb, sh in sorted(shows.items(), key=lambda x: -float(x[1].get("at") or 0))[:12]:
        s = next((x for x in by_id.values() if str(x.get("tvdbId")) == tvdb), None)
        if not s:
            continue
        lab = labels(s)
        want = [sp for sp in lab.values() if sh.get("all") or sp["label"] == "story"]
        have = sum(1 for sp in want if sp["have"])
        line = f"{have} of {len(want)} {'specials' if sh.get('all') else 'story specials'} here."
        if sh.get("said"):
            line += f" Last pack search: {sh['said']}."
        elif have < len(want):
            line += " Sonarr is searching. Packs are looked for a day after the ask."
        rows.append({"light": "ok" if have >= len(want) else "off", "t": sh.get("title") or s.get("title"), "s": line, "acts": []})
    for so in reversed(sorts[-8:]):
        extra = f" ({so['copied']} copied because they're on another drive)" if so.get("copied") else ""
        rows.append({"light": "ok", "t": f"{so.get('show')}: filed {so.get('specials', 0)} specials and {so.get('bonus', 0)} bonus videos",
                     "s": f"From {so.get('release')}, {time.strftime('%b %d, %I:%M %p', time.localtime(so.get('at') or 0))}{extra}.", "acts": []})
    if problem:
        level, headline, reason = "bad", "Something got in the way", problem
    elif asks:
        level, headline, reason = "warn", f"{len(asks)} video{'s' if len(asks) != 1 else ''} need{'s' if len(asks) == 1 else ''} you to say what {'it is' if len(asks) == 1 else 'they are'}", "Glowfin doesn't guess when a name could be more than one special."
    elif not _prowlarr_ready():
        level, headline, reason = "warn", "Sorting works. Pack hunting is waiting for Prowlarr", "Tower connects Prowlarr on its next hourly update."
    else:
        level, headline, reason = "ok", "Specials and bonus videos are sorted by themselves", "Finished season and complete-series packs are checked every 10 minutes."
    if seerr_ok is False:
        rows.insert(0, {"light": "warn", "t": "Seerr is too old for specials", "s": "This Seerr has no special episodes switch, so the Specials choice can't reach Sonarr. Update Seerr in Unraid's Docker tab.", "acts": []})
    acts = [{"label": "Sort now", "do": "sort"}]
    if sorts:
        acts.append({"label": "Put back last sort", "do": "undo"})
    acts.append({"label": "Turn off pack hunting" if hunt else "Turn on pack hunting", "do": "hunt:off" if hunt else "hunt:on"})
    return {
        "title": "Specials & bonus",
        "intro": "Specials that are part of the story are found first, also inside complete-series and season packs. Specials go into each show's Specials, and deleted scenes, behind-the-scenes and interviews go into its Bonus row. Files are linked, not copied.",
        "level": level,
        "headline": headline,
        "reason": reason,
        "acts": acts,
        "rows": rows,
    }


def card_do(do):
    if do == "sort":
        with _lock:
            _state.pop("problem", None)
        return sort_round(force=True)
    if do == "undo":
        return undo_last()
    if do in ("hunt:on", "hunt:off"):
        with _lock:
            _state["hunt"] = do == "hunt:on"
        _save()
        return "Pack hunting is on." if do == "hunt:on" else "Pack hunting is off. Finished packs are still sorted."
    kind, _, rest = do.partition(":")
    if kind == "pick":
        key, _, n = rest.partition(":")
        return _answer(key, n)
    if kind in ("bonus", "leave"):
        return _answer(rest, kind)
    return "Glowfin didn't understand that button."


# ---------------------------------------------------------------- for the apps (anyone signed in)


def _series_for(query):
    tvdb = (query.get("tvdb") or [""])[0]
    tmdb = (query.get("tmdb") or [""])[0]
    for s in _series_all():
        if (tvdb.isdigit() and str(s.get("tvdbId")) == tvdb) or (tmdb.isdigit() and str(s.get("tmdbId") or "") == tmdb):
            return s
    return None


def route(method, path, query, read_json, is_admin, is_kids):
    """GET /api/extras/show?tvdb=|tmdb=, POST /api/extras/want. Returns (status, answer)."""
    try:
        if method == "GET" and path == "/api/extras/show":
            s = _series_for(query) if _ready() else None
            if not s:
                return 200, {"known": False}
            lab = labels(s)
            count = {k: sum(1 for sp in lab.values() if sp["label"] == k) for k in LABEL_WORDS}
            return 200, {
                "known": True,
                "story": count["story"], "nice": count["nice"], "skip": count["skip"],
                "labels": {str(n): sp["label"] for n, sp in lab.items()},
                "words": LABEL_WORDS,
            }
        if method == "POST" and path == "/api/extras/want":
            if is_kids:
                return 403, {"error": "Ask a grown-up to get this."}
            body = read_json() or {}
            tmdb = str(body.get("tmdb") or "")
            if not tmdb.isdigit():
                return 400, {"error": "Which show?"}
            with _lock:
                _state.setdefault("want", {})[tmdb] = {"all": bool(body.get("all")), "at": time.time()}
            _save()
            # Already on Tower with Specials ticked (an earlier ask): flip to all/story right away
            s = next((x for x in _series_all(fresh=True) if str(x.get("tmdbId") or "") == tmdb), None)
            if s:
                with _lock:
                    sh = (_state.get("shows") or {}).get(str(s.get("tvdbId")))
                if sh and not sh.get("old") and bool(sh.get("all")) != bool(body.get("all")):
                    _apply_labels(s, bool(body.get("all")))
                    with _lock:
                        sh["all"] = bool(body.get("all"))
                    _save()
            _wake.set()
            return 200, {"message": "Got it."}
        if method == "GET" and path == "/api/extras/status":
            with _lock:
                return 200, {"view": dict(_view), "log": list(_log_lines[-40:])}
    except Exception as e:  # noqa: BLE001
        return 502, {"error": str(e)}
    return 404, {"error": "Not found"}
