"""
Glowfin "One Pace" sorter (Ben 2026-11-25).

One Pace (onepace.net) is a fan edit of the One Piece anime. It has no listing on TheTVDB or TMDB that covers it,
and every season is a story arc (Season 1 Romance Dawn ... Season 36 Egghead, Specials as Season 0).

Ben drops One Pace files (any names, loose or in folders) into Media/One Pace Drop and presses
"Sort One Pace now" on Server > Tools > Library > One Pace. Nothing runs on a timer (Ben: "I will rarely grab
content"). One press:
  * reads the free One Pace data list (github.com/ladyisatis/one-pace-metadata, kept up to date every hour from
    One Pace's own release feed and episode spreadsheets),
  * finds each file's episode by its CRC32 code (in the name like [D767799C], or worked out from the file itself
    when the name was changed), with the "[One Pace][1-7] Romance Dawn 03" name as a last try for old copies,
  * moves it to Anime Shows/One Pace/Season NN/One Pace - SNNEMM - Title.mkv (subtitles go with it),
  * writes locked .nfo files (show, arc, episode: titles, descriptions, dates, chapters) and the arc posters,
  * an extended cut takes the normal cut's place; a newer copy of an episode takes the older one's place.
    The file that loses goes to One Pace Drop/Replaced and is deleted 7 days later (on a later press),
  * tells Jellyfin to look again and re-read the show.
Files that aren't One Pace stay in the drop folder and are listed on the card. Nothing else is ever deleted.

Saves its notes in /skips/onepace. Only uses the Python standard library.
"""

import json
import os
import re
import shutil
import threading
import time
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
import zlib

DATA_URL = "https://raw.githubusercontent.com/ladyisatis/one-pace-metadata/refs/heads/v2/metadata/data.json"
POSTER_URL = "https://raw.githubusercontent.com/ladyisatis/OnePaceOrganizer/refs/heads/main/posters"
# Ben's own set (the One Pace arc "wanted posters", 2024), kept in Glowfin's repo; used first, OnePaceOrganizer's as backup
BEN_POSTERS = "https://raw.githubusercontent.com/TheBenPsych/MyStream/main/server/onepace-posters"
POSTER_SET = "ben-2026-11-25"  # change this when the poster set changes, so the next Sort swaps them all
MEDIA = os.environ.get("MEDIA_DIR", "/media")
DROP = os.path.join(MEDIA, "One Pace Drop")
REPLACED = os.path.join(DROP, "Replaced")
SHOW_NAME = "One Pace"
KEEP_REPLACED = 7 * 86400
DATA_MAX_AGE = 3600
VIDEO = {".mkv", ".mp4", ".avi", ".m4v", ".mov", ".ts", ".webm"}
SIDE = {".srt", ".ass", ".ssa", ".sub", ".idx", ".vtt", ".sup"}
CRC_IN_NAME = re.compile(r"\[([0-9A-Fa-f]{8})\]")
OLD_NAME = re.compile(r"\[One Pace\]\[[^\]]*\]\s*(.+?)\s+(\d{1,3})(?:\s|\[|$)", re.I)
OUR_NAME = re.compile(r"^One Pace - S(\d{2})E(\d{2,3}) - ")

_jf = None  # helper's jellyfin_call(method, path, token=None, body=None) -> (status, bytes, type)
_admin = None  # () -> (token, user id)
_dir = None
_lock = threading.RLock()
_log_lines = []
_state = {}  # saved: files {"S14E03": {crc, extended, name, when}}, replaced {path: when}, last {...}
_data = {"loaded": 0, "raw": None}
_status = {"busy": False, "step": "", "done": 0, "total": 0}
_journal = []  # this sort's moves [(from, to)], saved so "Put back last sort" can undo them


def log(text):
    print("onepace: " + text, flush=True)
    with _lock:
        _log_lines.append(time.strftime("%Y-%m-%d %H:%M ") + text)
        del _log_lines[:-80]


# ---------------------------------------------------------------- saving


def _pick_dir():
    for d in (os.environ.get("ONEPACE_DIR"), "/skips/onepace", "/tracks/benflix-onepace", "/tmp/benflix-onepace"):
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
        tmp = os.path.join(_dir, "state.json.tmp")
        try:
            with open(tmp, "w") as f:
                json.dump(_state, f)
            os.replace(tmp, os.path.join(_dir, "state.json"))
        except OSError as e:
            log(f"couldn't save notes: {e}")


def _load_state():
    try:
        with open(os.path.join(_dir, "state.json")) as f:
            return json.load(f) or {}
    except (OSError, ValueError, TypeError):
        return {}


# ---------------------------------------------------------------- the One Pace data list


def _get_url(url, timeout=60):
    req = urllib.request.Request(url, headers={"User-Agent": "Glowfin-helper/1.0"})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.read()


def _load_data(force=False):
    """The data list as {"crc": {CRC: episode}, "arcs": {part: arc}, "desc": {(arc, ep): {...}}, "show": {...}}"""
    with _lock:
        if _data["raw"] and not force and time.time() - _data["loaded"] < DATA_MAX_AGE:
            return _data["raw"]
    raw = None
    saved = os.path.join(_dir, "data.json") if _dir else None
    try:
        body = _get_url(DATA_URL)
        raw = json.loads(body)
        if saved:
            with open(saved + ".tmp", "wb") as f:
                f.write(body)
            os.replace(saved + ".tmp", saved)
    except Exception as e:  # noqa: BLE001
        log(f"couldn't download the One Pace list ({e}), using the saved copy")
        try:
            with open(saved) as f:
                raw = json.load(f)
        except (OSError, ValueError, TypeError):
            raise RuntimeError("Couldn't get the One Pace list. Check Tower has internet.")
    crc = {}
    for key, ep in (raw.get("episodes") or {}).items():
        if isinstance(ep, dict) and ep.get("arc") is not None:
            crc[str(key).upper()] = ep
    for edit in (raw.get("other_edits") or {}).values():
        for ep in (edit or {}).values():
            c = str(((ep or {}).get("hashes") or {}).get("crc32") or "").upper()
            if c and c not in crc:
                crc[c] = {**ep, "edit": True}
    arcs = {}
    for a in (raw.get("arcs") or {}).get("en") or []:
        try:
            arcs[int(a["part"])] = a
        except (KeyError, ValueError, TypeError):
            continue
    desc = {}
    for d in (raw.get("descriptions") or {}).get("en") or []:
        try:
            desc[(int(d["arc"]), int(d["episode"]))] = d
        except (KeyError, ValueError, TypeError):
            continue
    data = {"crc": crc, "arcs": arcs, "desc": desc, "show": (raw.get("tvshow") or {}).get("en") or {},
            "version": (raw.get("status") or {}).get("version"), "updated": (raw.get("status") or {}).get("last_update")}
    with _lock:
        _data.update({"raw": data, "loaded": time.time()})
    return data


def _info(data, ep):
    """Episode info with its title and description"""
    arc, num = int(ep.get("arc") or 0), int(ep.get("episode") or 0)
    d = data["desc"].get((arc, num)) or {}
    return {
        "arc": arc,
        "episode": num,
        "title": ep.get("title") or d.get("title") or f"Episode {num}",
        "originaltitle": d.get("originaltitle") or "",
        "description": ep.get("description") or d.get("description") or "",
        "manga_chapters": ep.get("manga_chapters") or "",
        "anime_episodes": ep.get("anime_episodes") or "",
        "released": str(ep.get("released") or ""),
        "duration": ep.get("duration") or 0,
        "extended": bool(ep.get("extended")),
        "crc": str((ep.get("hashes") or {}).get("crc32") or "").upper(),
    }


def _norm(text):
    return re.sub(r"[^a-z0-9]+", "", str(text or "").lower())


def _by_old_name(data, name):
    """'[One Pace][1-7] Romance Dawn 03 [1080p][XXXX].mkv' -> (arc, episode), for copies older than the list"""
    m = OLD_NAME.search(name)
    if not m:
        return None
    words, num = m.group(1), int(m.group(2))
    extended = "extended" in name.lower()
    words = re.sub(r"(?i)\s*-?\s*extended( cut)?", "", words)
    for part, a in data["arcs"].items():
        if part and _norm(words) in (_norm(a.get("title")), _norm(a.get("mkvcode")), _norm(a.get("originaltitle"))):
            return part, num, extended
    return None


def _file_crc(path):
    value = 0
    with open(path, "rb") as f:
        while True:
            chunk = f.read(8 * 1024 * 1024)
            if not chunk:
                break
            value = zlib.crc32(chunk, value)
    return f"{value & 0xFFFFFFFF:08X}"


# ---------------------------------------------------------------- where things go


ANIME_NAMES = ("Anime Shows", "Anime Series", "Anime TV", "Anime")
OLD_ROOTS = ("Anime", "anime", "Shows", "TV Shows", "TV")  # where an earlier version may have put One Pace


def _anime_root():
    """Ben's anime shows folder (Media/Anime Shows), never the regular Shows folder"""
    for name in ANIME_NAMES:
        p = os.path.join(MEDIA, name)
        if os.path.isdir(p):
            return p
    try:
        for name in sorted(os.listdir(MEDIA)):
            low = name.lower()
            if "anime" in low and "movie" not in low and os.path.isdir(os.path.join(MEDIA, name)):
                return os.path.join(MEDIA, name)
    except OSError:
        pass
    return os.path.join(MEDIA, "Anime Shows")


def _move_old_show():
    """Moves a One Pace folder an earlier version filed in the wrong place into the anime shows folder.
    Returns the old place (so Jellyfin can be told it's gone) or None."""
    target = _show_dir()
    if os.path.exists(target):
        return None
    for name in OLD_ROOTS:
        old = os.path.join(MEDIA, name, SHOW_NAME)
        if os.path.abspath(old) != os.path.abspath(target) and os.path.isdir(old) and os.path.exists(os.path.join(old, "tvshow.nfo")):
            _folder(os.path.dirname(target))
            _move(old, target)
            log(f"moved One Pace from {name} to {os.path.basename(_anime_root())}")
            return old
    return None


def _show_dir():
    return os.path.join(_anime_root(), SHOW_NAME)


def _season_dir(arc):
    return os.path.join(_show_dir(), "Specials" if arc == 0 else f"Season {arc:02d}")


def _safe(text):
    return re.sub(r'[<>:"/\\|?*\x00-\x1F]', "", str(text or "")).strip().rstrip(".")


def _key(arc, ep):
    return f"S{arc:02d}E{ep:02d}"


def _have():
    """What's in Anime Shows/One Pace right now: {"S14E03": full path}"""
    have = {}
    root = _show_dir()
    if not os.path.isdir(root):
        return have
    for folder in os.listdir(root):
        sub = os.path.join(root, folder)
        if not os.path.isdir(sub):
            continue
        for name in os.listdir(sub):
            m = OUR_NAME.match(name)
            if m and os.path.splitext(name)[1].lower() in VIDEO:
                have[_key(int(m.group(1)), int(m.group(2)))] = os.path.join(sub, name)
    return have


def _sidecars(video):
    stem = os.path.splitext(video)[0]
    folder = os.path.dirname(video)
    out = []
    try:
        for name in os.listdir(folder):
            full = os.path.join(folder, name)
            if full != video and name.startswith(os.path.basename(stem)) and (os.path.splitext(name)[1].lower() in SIDE or name.endswith(".nfo") or "-thumb." in name):
                out.append(full)
    except OSError:
        pass
    return out


def _share(path):
    """Unraid's usual owner (nobody:users) and open rights, so Ben can add, rename and delete from Windows.
    (The helper runs as root, so anything it makes would otherwise be locked to Windows.)"""
    try:
        os.chown(path, 99, 100)
        os.chmod(path, 0o777 if os.path.isdir(path) else 0o666)
    except OSError:
        pass


def _folder(path):
    """Makes a folder (and any missing parents under Media) that Windows can write to"""
    missing = []
    p = path
    while p and not os.path.isdir(p) and p not in ("/", MEDIA):
        missing.append(p)
        p = os.path.dirname(p)
    os.makedirs(path, exist_ok=True)
    for m in missing:
        _share(m)


def _move(src, dst):
    _folder(os.path.dirname(dst))
    try:
        os.rename(src, dst)
    except OSError:
        shutil.move(src, dst)


def _retire(path):
    """Moves a file that lost its place (and its subtitles and .nfo) to One Pace Drop/Replaced"""
    _folder(REPLACED)
    for p in [path] + _sidecars(path):
        target = os.path.join(REPLACED, os.path.basename(p))
        if os.path.exists(target):
            base, ext = os.path.splitext(target)
            target = f"{base} ({int(time.time())}){ext}"
        try:
            _move(p, target)
            _share(target)
            _journal.append((p, target))
            with _lock:
                _state.setdefault("replaced", {})[target] = time.time()
        except OSError as e:
            log(f"couldn't move {os.path.basename(p)} to Replaced: {e}")


def _clean_replaced():
    now = time.time()
    with _lock:
        rep = dict(_state.get("replaced") or {})
    gone = 0
    for path, when in rep.items():
        if now - float(when or 0) < KEEP_REPLACED:
            continue
        try:
            if os.path.isfile(path):
                os.remove(path)
                gone += 1
        except OSError:
            continue
        with _lock:
            (_state.get("replaced") or {}).pop(path, None)
    if gone:
        log(f"deleted {gone} replaced file(s) older than 7 days")


# ---------------------------------------------------------------- .nfo files and posters


def _xml(root):
    ET.indent(root)
    return ET.tostring(root, encoding="utf-8", xml_declaration=True)


def _write(path, body):
    tmp = path + ".tmp"
    with open(tmp, "wb") as f:
        f.write(body)
    os.replace(tmp, path)
    if path.startswith(MEDIA + "/"):
        _share(path)


def _poster(sources, folder, force=False):
    """Puts poster.jpg/.png in [folder] from the first source that works ([(key, url)], Ben's own set first).
    Each picture is kept in the notes folder so it's downloaded once."""
    have = [n for n in ("poster.jpg", "poster.png") if os.path.exists(os.path.join(folder, n))]
    if have and not force:
        return True
    for key, url in sources:
        ext = os.path.splitext(url)[1].lower() or ".jpg"
        cache = os.path.join(_dir, "posters", key + ext) if _dir else None
        try:
            if cache and os.path.exists(cache):
                with open(cache, "rb") as f:
                    body = f.read()
            else:
                body = _get_url(url, timeout=120)
                if cache:
                    os.makedirs(os.path.dirname(cache), exist_ok=True)
                    _write(cache, body)
        except Exception as e:  # noqa: BLE001
            log(f"no poster at {url.rsplit('/', 2)[-2:]}: {e}")
            continue
        for old in have:
            try:
                os.remove(os.path.join(folder, old))
            except OSError:
                pass
        _write(os.path.join(folder, "poster" + ext), body)
        return True
    return False


def _show_poster(force=False):
    return _poster([("ben-show", f"{BEN_POSTERS}/show.jpg"), ("op-show", f"{POSTER_URL}/poster.png")], _show_dir(), force)


def _arc_poster(arc, force=False):
    return _poster([(f"ben-{arc}", f"{BEN_POSTERS}/{arc}.jpg"), (f"op-{arc}", f"{POSTER_URL}/{arc}/poster.png")], _season_dir(arc), force)


def _show_nfo(data):
    show = data["show"]
    root = ET.Element("tvshow")
    ET.SubElement(root, "title").text = SHOW_NAME
    ET.SubElement(root, "originaltitle").text = "One Piece"
    ET.SubElement(root, "sorttitle").text = SHOW_NAME
    plot = show.get("plot") or "One Pace is a fan project that recuts the One Piece anime to match the pacing of the manga."
    ET.SubElement(root, "plot").text = plot
    ET.SubElement(root, "outline").text = plot
    for g in show.get("genre") or ["Anime"]:
        ET.SubElement(root, "genre").text = str(g)
    for k in ("premiered", "year", "status"):
        if show.get(k):
            ET.SubElement(root, k).text = str(show[k])
    ET.SubElement(root, "mpaa").text = str(show.get("customrating") or "TV-14")
    for part, a in sorted(data["arcs"].items()):
        ET.SubElement(root, "namedseason", attrib={"number": str(part)}).text = a.get("title") or f"Arc {part}"
    ET.SubElement(root, "lockdata").text = "true"
    _folder(_show_dir())
    _write(os.path.join(_show_dir(), "tvshow.nfo"), _xml(root))
    _show_poster()


def _season_nfo(data, arc):
    a = data["arcs"].get(arc) or {}
    folder = _season_dir(arc)
    _folder(folder)
    root = ET.Element("season")
    ET.SubElement(root, "title").text = a.get("title") or ("Specials" if arc == 0 else f"Arc {arc}")
    ET.SubElement(root, "seasonnumber").text = str(arc)
    if a.get("description"):
        ET.SubElement(root, "plot").text = a["description"]
        ET.SubElement(root, "outline").text = a["description"]
    ET.SubElement(root, "lockdata").text = "true"
    _write(os.path.join(folder, "season.nfo"), _xml(root))
    _arc_poster(arc)


def _episode_nfo(info, video):
    root = ET.Element("episodedetails")
    title = info["title"] + (" (Extended)" if info["extended"] else "")
    ET.SubElement(root, "title").text = title
    if info["originaltitle"]:
        ET.SubElement(root, "originaltitle").text = info["originaltitle"]
    ET.SubElement(root, "showtitle").text = SHOW_NAME
    ET.SubElement(root, "season").text = str(info["arc"])
    ET.SubElement(root, "episode").text = str(info["episode"])
    plot = info["description"]
    if info["manga_chapters"]:
        plot += ("\n\n" if plot else "") + f"Manga chapters: {info['manga_chapters']}"
    if info["anime_episodes"]:
        plot += ("\n\n" if plot else "") + f"Anime episodes: {info['anime_episodes']}"
    ET.SubElement(root, "plot").text = plot
    if isinstance(info["duration"], int) and info["duration"] > 0:
        ET.SubElement(root, "runtime").text = str(max(1, round(info["duration"] / 60)))
    day = info["released"].split("T")[0].split(" ")[0]
    if re.match(r"^\d{4}-\d{2}-\d{2}$", day):
        ET.SubElement(root, "year").text = day[:4]
        ET.SubElement(root, "premiered").text = day
        ET.SubElement(root, "aired").text = day
    ET.SubElement(root, "lockdata").text = "true"
    _write(os.path.splitext(video)[0] + ".nfo", _xml(root))


# ---------------------------------------------------------------- one sort


def _drop_files():
    out = []
    if not os.path.isdir(DROP):
        return out
    for folder, dirs, names in os.walk(DROP):
        if os.path.abspath(folder).startswith(os.path.abspath(REPLACED)):
            dirs[:] = []
            continue
        dirs[:] = [d for d in dirs if os.path.join(folder, d) != REPLACED and not d.startswith(".")]
        for name in names:
            if os.path.splitext(name)[1].lower() in VIDEO and not name.startswith("."):
                out.append(os.path.join(folder, name))
    return sorted(out)


def _identify(data, path):
    """-> (info, how) or (None, why)"""
    name = os.path.basename(path)
    found = CRC_IN_NAME.findall(name)
    for c in reversed(found):
        ep = data["crc"].get(c.upper())
        if ep:
            return _info(data, ep), "name"
    with _lock:
        _status["step"] = f"Reading {name[:60]}"
    try:
        c = _file_crc(path)
    except OSError as e:
        return None, f"couldn't read it ({e})"
    ep = data["crc"].get(c)
    if ep:
        return _info(data, ep), "file"
    old = _by_old_name(data, name)
    if old:
        arc, num, extended = old
        ep = next((e for e in data["crc"].values() if int(e.get("arc") or -1) == arc and int(e.get("episode") or -1) == num and not e.get("edit")), None)
        if ep:
            info = _info(data, ep)
            info.update({"extended": extended, "crc": c, "older": True})
            return info, "older copy"
    return None, "not a One Pace episode Glowfin knows"


def _place(data, info, path, seasons_done):
    arc, num = info["arc"], info["episode"]
    key = _key(arc, num)
    have = _have().get(key)
    with _lock:
        mine = (_state.get("files") or {}).get(key) or {}
    label = f"{(data['arcs'].get(arc) or {}).get('title') or 'Arc ' + str(arc)} {num}"
    if have and os.path.abspath(have) != os.path.abspath(path):
        old_ext = mine.get("extended") if mine else " (Extended)" in os.path.basename(have)
        if old_ext and not info["extended"]:
            _retire(path)
            return f"{label}: kept the extended cut you already have (this normal cut went to Replaced)"
        if mine.get("crc") == info["crc"] and not info.get("older"):
            _retire(path)
            return f"{label}: already here (the extra copy went to Replaced)"
        _retire(have)
    if arc not in seasons_done:
        _season_nfo(data, arc)
        seasons_done.add(arc)
    ext = os.path.splitext(path)[1].lower()
    title = _safe(info["title"]) + (" (Extended)" if info["extended"] else "")
    dst = os.path.join(_season_dir(arc), f"One Pace - {key} - {title}{ext}")
    subs = [p for p in _sidecars(path) if os.path.splitext(p)[1].lower() in SIDE]
    _move(path, dst)
    _share(dst)
    _journal.append((path, dst))
    for s in subs:
        tail = os.path.basename(s)[len(os.path.splitext(os.path.basename(path))[0]):]
        try:
            _move(s, os.path.splitext(dst)[0] + tail)
            _share(os.path.splitext(dst)[0] + tail)
            _journal.append((s, os.path.splitext(dst)[0] + tail))
        except OSError:
            pass
    _episode_nfo(info, dst)
    with _lock:
        _state.setdefault("files", {})[key] = {"crc": info["crc"], "extended": info["extended"], "name": os.path.basename(dst), "when": time.time(), "older": bool(info.get("older"))}
    a = data["arcs"].get(arc) or {}
    return f"{a.get('title') or 'Arc ' + str(arc)} {num}: {info['title']}" + (" (Extended)" if info["extended"] else "")


def _tidy_drop():
    """Removes empty folders left in the drop folder after a sort"""
    for folder, dirs, names in os.walk(DROP, topdown=False):
        if os.path.abspath(folder) in (os.path.abspath(DROP), os.path.abspath(REPLACED)):
            continue
        try:
            if not os.listdir(folder):
                os.rmdir(folder)
        except OSError:
            continue


def _refresh_posters():
    """When the poster set changed, swaps the show and every arc poster already on Tower. True if it did."""
    with _lock:
        if _state.get("poster_set") == POSTER_SET:
            return False
    root = _show_dir()
    if not os.path.isdir(root):
        with _lock:
            _state["poster_set"] = POSTER_SET
        return False
    if _dir:
        shutil.rmtree(os.path.join(_dir, "posters"), ignore_errors=True)
    _show_poster(force=True)
    for name in os.listdir(root):
        m = re.match(r"^Season (\d+)$", name)
        arc = 0 if name == "Specials" else int(m.group(1)) if m else None
        if arc is not None:
            _arc_poster(arc, force=True)
    with _lock:
        _state["poster_set"] = POSTER_SET
    log("swapped in the new poster set")
    return True


def _tell_jellyfin(new_pictures=False, old_place=None):
    token, _ = _admin() if _admin else (None, None)
    if not token or not _jf:
        return "Open Glowfin once as Ben so Tower can tell Jellyfin."
    updates = [{"Path": _show_dir(), "UpdateType": "Created" if old_place else "Modified"}]
    if old_place:
        updates.append({"Path": old_place, "UpdateType": "Deleted"})
    body = json.dumps({"Updates": updates}).encode()
    _jf("POST", "/Library/Media/Updated", token=token, body=body)
    # Re-read the show so new .nfo notes and posters win (only One Pace, never the whole library)
    try:
        q = urllib.parse.urlencode({"Recursive": "true", "IncludeItemTypes": "Series", "SearchTerm": SHOW_NAME, "Fields": "Path"})
        status, raw, _ = _jf("GET", f"/Items?{q}", token=token)
        items = json.loads(raw or b"{}").get("Items") or [] if status == 200 else []
        for it in items:
            if str(it.get("Path") or "").rstrip("/").endswith("/" + SHOW_NAME):
                q2 = urllib.parse.urlencode({"Recursive": "true", "MetadataRefreshMode": "FullRefresh", "ImageRefreshMode": "FullRefresh", "ReplaceAllMetadata": "false", "ReplaceAllImages": "true" if new_pictures else "false"})
                _jf("POST", f"/Items/{it['Id']}/Refresh?{q2}", token=token)
    except Exception as e:  # noqa: BLE001
        log(f"couldn't ask Jellyfin to re-read One Pace: {e}")
    threading.Thread(target=_push_names, name="onepace-names", daemon=True).start()
    return ""


# ---------------------------------------------------------------- names straight into Jellyfin
# Some libraries don't read .nfo files (Jellyfin's "Nfo" metadata reader is off), and then episodes show their
# file names. So after each Sort, Tower also writes the titles, stories and arc names into Jellyfin itself and
# locks them, the same way Jellyfin's own Edit metadata page does.


def _read_nfo(path):
    try:
        root = ET.parse(path).getroot()
    except (OSError, ET.ParseError):
        return None
    return {c.tag: (c.text or "") for c in root}


def _jf_json(method, path, token, body=None):
    status, raw, _ = _jf(method, path, token=token, body=json.dumps(body).encode() if body is not None else None)
    if status not in (200, 204):
        raise RuntimeError(f"Jellyfin said {status}")
    return json.loads(raw or b"{}") if raw else {}


def _update_item(item_id, changes, token, user):
    full = None
    for path in (f"/Items/{item_id}?userId={user}", f"/Users/{user}/Items/{item_id}"):
        try:
            full = _jf_json("GET", path, token)
            if full.get("Id"):
                break
        except Exception:  # noqa: BLE001
            continue
    if not full or not full.get("Id"):
        return False
    if all(full.get(k) == v for k, v in changes.items()) and full.get("LockData"):
        return False
    full.update(changes)
    full["LockData"] = True
    _jf_json("POST", f"/Items/{item_id}", token, full)
    return True


def _series_id(token):
    q = urllib.parse.urlencode({"Recursive": "true", "IncludeItemTypes": "Series", "SearchTerm": SHOW_NAME, "Fields": "Path"})
    for it in _jf_json("GET", f"/Items?{q}", token).get("Items") or []:
        if os.path.abspath(str(it.get("Path") or "")) == os.path.abspath(_show_dir()):
            return it["Id"]
    # Jellyfin may still call it by its file name or an old guess; find it by its folder instead
    q = urllib.parse.urlencode({"Recursive": "true", "IncludeItemTypes": "Series", "Fields": "Path", "Limit": "2000"})
    for it in _jf_json("GET", f"/Items?{q}", token).get("Items") or []:
        if os.path.abspath(str(it.get("Path") or "")) == os.path.abspath(_show_dir()):
            return it["Id"]
    return None


def push_names(wait=True):
    """Writes the One Pace names and stories into Jellyfin. Returns how many items it changed."""
    token, user = _admin() if _admin else (None, None)
    if not token or not user or not _jf:
        return 0
    sid = None
    for _ in range(12 if wait else 1):  # Jellyfin needs a moment to find new files
        sid = _series_id(token)
        if sid:
            break
        time.sleep(20)
    if not sid:
        log("Jellyfin hasn't found the One Pace folder yet")
        return 0
    if wait:
        time.sleep(60)  # let Jellyfin's own refresh finish first, so it can't undo these
    changed = 0
    show = _read_nfo(os.path.join(_show_dir(), "tvshow.nfo")) or {}
    if show and _update_item(sid, {"Name": SHOW_NAME, "SortName": SHOW_NAME, "Overview": show.get("plot") or ""}, token, user):
        changed += 1
    q = urllib.parse.urlencode({"ParentId": sid, "Recursive": "true", "IncludeItemTypes": "Season,Episode", "Fields": "Path,Overview", "Limit": "5000"})
    for it in _jf_json("GET", f"/Items?{q}", token).get("Items") or []:
        path = str(it.get("Path") or "")
        try:
            if it.get("Type") == "Season" and path:
                info = _read_nfo(os.path.join(path, "season.nfo"))
                if info:
                    if _update_item(it["Id"], {"Name": info.get("title") or it.get("Name"), "Overview": info.get("plot") or ""}, token, user):
                        changed += 1
            elif it.get("Type") == "Episode" and path:
                info = _read_nfo(os.path.splitext(path)[0] + ".nfo")
                if info and info.get("title"):
                    ch = {"Name": info["title"], "Overview": info.get("plot") or "",
                          "IndexNumber": int(info.get("episode") or it.get("IndexNumber") or 0),
                          "ParentIndexNumber": int(info.get("season") or it.get("ParentIndexNumber") or 0)}
                    if info.get("aired"):
                        ch["PremiereDate"] = info["aired"] + "T00:00:00.0000000Z"
                        ch["ProductionYear"] = int(info["aired"][:4])
                    if _update_item(it["Id"], ch, token, user):
                        changed += 1
        except Exception as e:  # noqa: BLE001
            log(f"couldn't name {os.path.basename(path)} in Jellyfin: {e}")
    log(f"wrote names and stories for {changed} item(s) into Jellyfin")
    return changed


def _push_names():
    try:
        push_names(wait=True)
    except Exception as e:  # noqa: BLE001
        log(f"couldn't write names into Jellyfin: {e}")


def sort_now():
    with _lock:
        if _status["busy"]:
            return
        _status.update({"busy": True, "step": "Getting the One Pace list", "done": 0, "total": 0})
    moved, skipped, unknown = [], [], []
    problem = None
    old_place = None
    _journal.clear()
    try:
        _folder(DROP)
        _share(DROP)
        old_place = _move_old_show()
        _clean_replaced()
        data = _load_data(force=True)
        files = _drop_files()
        with _lock:
            _status["total"] = len(files)
        seasons_done = set()
        if files or not os.path.exists(os.path.join(_show_dir(), "tvshow.nfo")):
            _show_nfo(data)
        for i, path in enumerate(files):
            with _lock:
                _status.update({"done": i, "step": f"Sorting {os.path.basename(path)[:60]}"})
            try:
                info, how = _identify(data, path)
                if not info:
                    unknown.append({"name": os.path.relpath(path, DROP), "why": how})
                    continue
                said = _place(data, info, path, seasons_done)
                (skipped if "Replaced" in said else moved).append(said + (" (older copy, a newer one is out)" if info.get("older") else ""))
            except Exception as e:  # noqa: BLE001
                unknown.append({"name": os.path.relpath(path, DROP), "why": f"couldn't move it: {e}"})
        _tidy_drop()
        new_pictures = _refresh_posters()
        if os.path.isdir(_show_dir()):
            note = _tell_jellyfin(new_pictures, old_place)
            if note:
                problem = note
        log(f"sorted {len(moved)}, {len(skipped)} went to Replaced, {len(unknown)} left in the drop folder")
    except Exception as e:  # noqa: BLE001
        problem = str(e)
        log(f"sort stopped: {e}")
    finally:
        with _lock:
            if _journal:
                _state["undo"] = {"when": time.time(), "moves": list(_journal)}
            _state["last"] = {"when": time.time(), "moved": moved[-60:], "skipped": skipped[-30:], "unknown": unknown[-40:], "problem": problem, "count": len(moved)}
            _status.update({"busy": False, "step": "", "done": 0, "total": 0})
        _save()


# ---------------------------------------------------------------- Server page card (Server > Tools > Library > One Pace)


def _size_mib(text):
    m = re.match(r"([\d.]+)\s*(KiB|MiB|GiB)", str(text or ""))
    if not m:
        return 0.0
    return float(m.group(1)) * {"KiB": 1 / 1024, "MiB": 1, "GiB": 1024}[m.group(2)]


def _nice_size(mib):
    return f"{mib / 1024:.1f} GB" if mib >= 1024 else f"{mib:.0f} MB"


def _arc_rows(data):
    have = _have()
    with _lock:
        mine = dict(_state.get("files") or {})
    rows, missing_total, newer_total = [], 0, 0
    for part, a in sorted(data["arcs"].items()):
        eps = a.get("episodes") or []
        got, newer, need_mib = 0, 0, 0.0
        for e in eps:
            try:
                num = int(e.get("episode"))
            except (TypeError, ValueError):
                continue
            key = _key(part, num)
            std, ext = str(e.get("standard") or "").upper(), str(e.get("extended") or "").upper()
            if key in have:
                got += 1
                crc = (mine.get(key) or {}).get("crc")
                if crc and crc not in (std, ext):
                    newer += 1
            else:
                info = data["crc"].get(ext or std) or data["crc"].get(std) or {}
                need_mib += _size_mib((info.get("file") or {}).get("size"))
        total = len(eps)
        if not got and part == 0:
            continue
        missing = total - got
        missing_total += missing
        newer_total += newer
        if got == 0:
            light, line = "off", f"Not on Tower. {total} episode{'s' if total != 1 else ''}" + (f", about {_nice_size(need_mib)}" if need_mib else "")
        elif missing or newer:
            bits = []
            if missing:
                bits.append(f"{missing} missing" + (f" (about {_nice_size(need_mib)})" if need_mib else ""))
            if newer:
                bits.append(f"{newer} with a newer copy on onepace.net")
            light, line = "warn", f"{got} of {total} here. " + ", ".join(bits)
        else:
            light, line = "ok", f"All {total} here"
        label = a.get("title") or f"Arc {part}"
        rows.append({"light": light, "t": f"{'Specials' if part == 0 else str(part) + '. ' + label}", "s": line, "acts": []})
    return rows, missing_total, newer_total, len(have)


def card():
    with _lock:
        busy = dict(_status)
        last = dict(_state.get("last") or {})
    try:
        data = _load_data()
    except Exception as e:  # noqa: BLE001
        data = None
        err = str(e)
    rows = []
    if busy["busy"]:
        level, headline = "warn", "Sorting One Pace…"
        reason = busy["step"] + (f" ({busy['done'] + 1} of {busy['total']})" if busy["total"] else "")
    elif data is None:
        level, headline, reason = "bad", "Can't read the One Pace list right now", err
    else:
        left = {u.get("name") for u in (last.get("unknown") or [])}
        waiting = len([p for p in _drop_files() if os.path.relpath(p, DROP) not in left])
        if last.get("problem"):
            level, headline, reason = "bad", "The last sort hit a problem", last["problem"]
        elif waiting:
            level, headline = "warn", f"{waiting} file{'s' if waiting != 1 else ''} waiting in One Pace Drop"
            reason = "Press Sort One Pace now to file them under the right arc and episode."
        else:
            level, headline = "ok", "One Pace is sorted"
            reason = "Drop new files in Media/One Pace Drop (\\\\TOWER\\Homelab\\Media\\One Pace Drop), then press Sort One Pace now."
    if last.get("when") and not busy["busy"]:
        when = time.strftime("%b %d, %I:%M %p", time.localtime(last["when"]))
        for line in (last.get("moved") or [])[:20]:
            rows.append({"light": "ok", "t": line, "s": f"Sorted {when}", "acts": []})
        for line in (last.get("skipped") or [])[:10]:
            rows.append({"light": "off", "t": line, "s": f"Sorted {when}. Files in Replaced are deleted after 7 days.", "acts": []})
        for u in (last.get("unknown") or [])[:20]:
            rows.append({"light": "bad", "t": f"Left in the drop folder: {u['name']}", "s": u["why"][:1].upper() + u["why"][1:] + ".", "acts": []})
    if data is not None:
        arc_rows, missing, newer, here = _arc_rows(data)
        if here and level == "ok" and (missing or newer):
            reason = f"{here} episodes here. " + (f"{missing} not on Tower yet" if missing else "") + (", " if missing and newer else "") + (f"{newer} {'has' if newer == 1 else 'have'} a newer copy on onepace.net" if newer else "") + ". Get them from onepace.net and drop them in Media/One Pace Drop."
        rows.extend(arc_rows)
    return {
        "title": "One Pace",
        "intro": "One Pace (onepace.net) is the fan edit of One Piece that cuts the filler, with every story arc as its own season. Drop the files in Media/One Pace Drop and press Sort. Glowfin finds each episode and adds the arc names, descriptions and posters.",
        "level": level,
        "headline": headline,
        "reason": reason,
        "acts": [] if busy["busy"] else [{"label": "Sort One Pace now", "do": "sort"}] + ([{"label": "Put back last sort", "do": "undo"}] if (_state.get("undo") or {}).get("moves") else []),
        "rows": rows,
    }


def undo_last():
    """Puts every file the last Sort moved back where it was, with its old name, and removes the
    .nfo notes that Sort wrote for them. Nothing is deleted."""
    with _lock:
        undo = dict(_state.get("undo") or {})
    moves = undo.get("moves") or []
    if not moves:
        return "There's nothing to put back."
    back, problems = 0, []
    for src, dst in reversed(moves):
        try:
            if not os.path.exists(dst):
                continue
            if os.path.exists(src):
                problems.append(f"{os.path.basename(src)} (something else has that name now)")
                continue
            _move(dst, src)
            _share(src)
            nfo = os.path.splitext(dst)[0] + ".nfo"
            if os.path.splitext(dst)[1].lower() in VIDEO and os.path.exists(nfo):
                os.remove(nfo)
            back += 1
            with _lock:
                (_state.get("replaced") or {}).pop(dst, None)
                for key, f in list((_state.get("files") or {}).items()):
                    if f.get("name") == os.path.basename(dst):
                        _state["files"].pop(key, None)
        except OSError as e:
            problems.append(f"{os.path.basename(dst)} ({e})")
    with _lock:
        _state["undo"] = {}
        _state["last"] = {"when": time.time(), "moved": [], "skipped": [], "unknown": [], "problem": None, "count": 0,
                          "note": f"Put back {back} file(s) from the last sort."}
    _save()
    try:
        _tell_jellyfin()
    except Exception:  # noqa: BLE001
        pass
    log(f"put back {back} file(s) from the last sort")
    return f"Put back {back} file(s) where they were before the last Sort." + (f" Couldn't put back: {'; '.join(problems[:5])}" if problems else "")


def card_do(do):
    if do == "undo":
        with _lock:
            if _status["busy"]:
                return "Wait for the sort to finish first."
        return undo_last()
    if do == "sort":
        with _lock:
            if _status["busy"]:
                return "Already sorting."
        threading.Thread(target=sort_now, name="onepace-sort", daemon=True).start()
        return "Sorting now. This card shows how it's going."
    return "Glowfin didn't understand that button."


def start(jellyfin_call, admin):
    """Called once by the helper when it starts (makes the drop folder, nothing runs on a timer)"""
    global _jf, _admin, _dir
    _jf, _admin = jellyfin_call, admin
    _dir = _pick_dir()
    with _lock:
        _state.clear()
        _state.update(_load_state() if _dir else {})
    try:
        _folder(DROP)
        _share(DROP)
        # Folders an earlier version made as root (Windows couldn't write to them)
        for folder, dirs, _names in os.walk(DROP):
            for d in dirs:
                _share(os.path.join(folder, d))
        if os.path.isdir(_show_dir()):
            _share(_show_dir())
            for d in os.listdir(_show_dir()):
                full = os.path.join(_show_dir(), d)
                if os.path.isdir(full):
                    _share(full)
    except OSError as e:
        log(f"couldn't make the drop folder: {e}")
    log(f"ready (notes in {_dir}, drop folder {DROP})")
