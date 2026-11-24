"""
Benflix posters: helps the Server page's "Fix posters" tool.

Loaded by benflix-games-helper.py (it sits next to it on Tower). Two jobs:
  * Finds blurry posters: asks Jellyfin how big each movie, show, season and collection poster is and
    lists the ones narrower than 500 pixels. It remembers what it already measured, so only new or
    changed posters are measured again.
  * Fills in missing pictures at night (Ben's pick: only ones that are missing, never replacing one):
    a missing poster, season poster, background or title logo gets the best English one Jellyfin can
    find online (TMDB, TheTVDB or Fanart.tv, whichever plugins are on).

It never contacts ThePosterDB (its rules don't allow programs to search or copy from it).

Saves its notes in /skips/posters (next to the skip times).
"""

import json
import os
import threading
import time
import urllib.parse

_jf = None  # The helper's jellyfin_call(method, path, token=None, body=None) -> (status, bytes, type)
_admin_from_helper = None  # () -> (token, user id) of the admin, known once they've signed in
_dir = None
_lock = threading.RLock()
_log_lines = []
_measuring = threading.Event()
_filling = threading.Event()

SMALL = 500  # posters narrower than this look soft on a TV
FILL_PER_NIGHT = 300
# Display preferences the TV / phone app and the web version share for the poster tools
PREFS_ID = "benflix-posters"
PREFS_CLIENT = "Wholphin"


class PosterError(Exception):
    def __init__(self, status, message):
        super().__init__(message)
        self.status = status
        self.message = message


def log(text):
    line = time.strftime("%H:%M:%S ") + text
    print("posters: " + text, flush=True)
    with _lock:
        _log_lines.append(line)
        del _log_lines[:-40]


def _pick_dir():
    for d in (os.environ.get("POSTERS_DIR"), "/skips/posters", "/tracks/benflix-posters"):
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


def start(jellyfin_call, admin):
    """Called once by the helper when it starts. admin() gives (token, user id) of the admin."""
    global _jf, _admin_from_helper, _dir
    _jf, _admin_from_helper = jellyfin_call, admin
    _dir = _pick_dir()
    if not _dir:
        log("no folder to save notes in, so the night-time poster fill is off")
        return
    threading.Thread(target=_nightly, name="posters-nightly", daemon=True).start()
    log(f"ready (saving in {_dir})")


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


def _get(path):
    token = _admin()[0]
    if not token:
        raise PosterError(503, "Tower needs the main profile to sign in on Glowfin once first.")
    status, data, _ = _jf("GET", path, token=token)
    if status != 200:
        raise PosterError(502, f"Jellyfin answered with an error ({status}).")
    try:
        return json.loads(data or b"null")
    except ValueError:
        raise PosterError(502, "Jellyfin sent something the helper didn't understand.")


def _post(path):
    token = _admin()[0]
    if not token:
        raise PosterError(503, "Tower needs the main profile to sign in on Glowfin once first.")
    status, _, _ = _jf("POST", path, token=token, body=b"")
    return 200 <= status < 300


def _iso(seconds):
    return time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(seconds))


def _everything():
    """Every movie, show, season and collection with its picture tags"""
    _, user = _admin()
    q = urllib.parse.urlencode(
        {
            "IncludeItemTypes": "Movie,Series,Season,BoxSet",
            "Recursive": "true",
            "Fields": "PrimaryImageAspectRatio",
            "EnableImageTypes": "Primary,Backdrop,Logo",
            "EnableUserData": "false",
            "userId": user or "",
        }
    )
    return (_get("/Items?" + q) or {}).get("Items") or []


def _left_alone():
    """Titles Ben pressed "Leave it" on in the fixer (kept in his display preferences)"""
    _, user = _admin()
    try:
        q = urllib.parse.urlencode({"userId": user or "", "client": PREFS_CLIENT})
        prefs = _get(f"/DisplayPreferences/{PREFS_ID}?{q}") or {}
        text = (prefs.get("CustomPrefs") or {}).get("skip") or ""
        return set(x for x in text.split(",") if x)
    except PosterError:
        return set()


# ---------- Blurry posters ----------


def _measure():
    """Measures every poster that's new or changed since last time. Runs in the background."""
    if _measuring.is_set():
        return
    _measuring.set()
    try:
        saved = _read("sizes.json", {}) or {}
        sizes = saved.get("sizes") or {}  # id -> [tag, width, height]
        items = _everything()
        keep = {}
        done = 0
        for it in items:
            tag = (it.get("ImageTags") or {}).get("Primary")
            if not tag:
                continue
            iid = it.get("Id")
            old = sizes.get(iid)
            if old and old[0] == tag:
                keep[iid] = old
                continue
            try:
                infos = _get(f"/Items/{iid}/Images") or []
            except PosterError:
                continue
            info = next((x for x in infos if x.get("ImageType") == "Primary"), None)
            if info:
                keep[iid] = [tag, info.get("Width") or 0, info.get("Height") or 0]
            done += 1
            if done % 200 == 0:
                _write("sizes.json", {"at": saved.get("at"), "sizes": dict(sizes, **keep)})
        _write("sizes.json", {"at": time.time(), "sizes": keep})
        log(f"measured {done} new or changed posters ({len(keep)} in all)")
    except Exception as e:  # noqa: BLE001 - never stop the helper over this
        log(f"couldn't measure posters: {e}")
    finally:
        _measuring.clear()


def status():
    saved = _read("sizes.json", {}) or {}
    at = saved.get("at") or 0
    if time.time() - at > 6 * 3600 and not _measuring.is_set() and _admin()[0]:
        threading.Thread(target=_measure, name="posters-measure", daemon=True).start()
    small = [
        {"id": iid, "w": v[1], "h": v[2]}
        for iid, v in (saved.get("sizes") or {}).items()
        if v and 0 < (v[1] or 0) < SMALL
    ]
    night = _read("night.json", {}) or {}
    return {
        "small": small,
        "measuring": _measuring.is_set(),
        "measured_at": _iso(at) if at else None,
        "filling": _filling.is_set(),
        "night": night,
        "lines": list(_log_lines[-12:]),
    }


# ---------- Filling in missing pictures ----------


def _rank(images, kind):
    """Best first: English (or no words, for backgrounds), then most votes, best score, biggest"""

    def lang_group(x):
        lang = (x.get("Language") or "").lower()
        if kind == "Backdrop":
            return 0 if not lang else (1 if lang == "en" else 2)
        return 0 if lang == "en" else (1 if not lang else 2)

    return sorted(
        [x for x in images if x.get("Url")],
        key=lambda x: (lang_group(x), -(x.get("VoteCount") or 0), -(x.get("CommunityRating") or 0), -(x.get("Width") or 0)),
    )


def _best(item_id, kind):
    q = urllib.parse.urlencode({"type": kind, "limit": 60, "includeAllLanguages": "true"})
    found = (_get(f"/Items/{item_id}/RemoteImages?{q}") or {}).get("Images") or []
    ranked = _rank(found, kind)
    if not ranked:
        return None
    best = ranked[0]
    # Never put on a poster with words in another language by itself
    lang = (best.get("Language") or "").lower()
    if kind != "Backdrop" and lang not in ("", "en"):
        return None
    return best


TRY_AGAIN_DAYS = 7  # a title with nothing good online is looked at again after this many days


def fill_missing(limit=FILL_PER_NIGHT, why="night"):
    """Gives missing posters, season posters, backgrounds and title logos the best English picture"""
    if _filling.is_set():
        return
    _filling.set()
    filled, tried, names = 0, 0, []
    # Titles with nothing good online used to be tried again first every night and used up all the turns,
    # so newer titles never got their turn (Ben 2026-11-24, missing logos). Now each one rests a week.
    looked = _read("looked.json", {}) or {}
    now = time.time()
    looked = {k: v for k, v in looked.items() if now - (v or 0) < TRY_AGAIN_DAYS * 86400}
    try:
        skip = _left_alone()
        items = _everything()
        # Movies and shows first (their title logos show on every details page), then seasons and collections
        items.sort(key=lambda x: 0 if x.get("Type") in ("Movie", "Series") else 1)
        for it in items:
            if tried >= limit:
                break
            iid, kind_of = it.get("Id"), it.get("Type")
            if iid in skip:
                continue
            tags = it.get("ImageTags") or {}
            wanted = []
            if not tags.get("Primary"):
                wanted.append("Primary")
            if kind_of in ("Movie", "Series") and not it.get("BackdropImageTags"):
                wanted.append("Backdrop")
            if kind_of in ("Movie", "Series") and not tags.get("Logo"):
                wanted.append("Logo")
            for kind in wanted:
                if f"{iid}:{kind}" in looked:
                    continue
                tried += 1
                looked[f"{iid}:{kind}"] = now
                try:
                    best = _best(iid, kind)
                    if not best:
                        continue
                    q = urllib.parse.urlencode({"type": kind, "imageUrl": best["Url"]})
                    if _post(f"/Items/{iid}/RemoteImages/Download?{q}"):
                        filled += 1
                        label = {"Primary": "poster", "Backdrop": "background", "Logo": "title logo"}[kind]
                        name = it.get("Name") or "?"
                        if kind_of == "Season" and it.get("SeriesName"):
                            name = f"{it['SeriesName']} {name}"
                        names.append(f"{name} ({label})")
                except PosterError:
                    continue
        _write("looked.json", looked)
        _write("night.json", {"at": _iso(time.time()), "filled": filled, "names": names[:40], "why": why})
        log(f"filled {filled} missing pictures ({why})")
    except Exception as e:  # noqa: BLE001
        log(f"couldn't fill missing pictures: {e}")
    finally:
        _filling.clear()


# ---------- One title's logo, right away ----------

_logo_asked = {}  # item id -> when the helper last looked online for it


def _logo_tag(item_id):
    _, user = _admin()
    q = urllib.parse.urlencode({"Ids": item_id, "EnableImageTypes": "Logo", "EnableUserData": "false", "userId": user or ""})
    items = (_get("/Items?" + q) or {}).get("Items") or []
    if not items:
        raise PosterError(404, "That title isn't on Glowfin.")
    return items[0], (items[0].get("ImageTags") or {}).get("Logo")


def _logo_loads(item_id):
    """Jellyfin can remember a logo whose picture file is gone, so it never shows. Check it really loads."""
    token = _admin()[0]
    try:
        status, data, _ = _jf("GET", f"/Items/{item_id}/Images/Logo?maxWidth=60", token=token)
        return status == 200 and bool(data)
    except Exception:  # noqa: BLE001
        return False


def logo(item_id):
    """
    A details page with no logo asks for one (Ben 2026-11-24): if the title has a working logo, says so; if it
    has none (or a broken one), puts on the best English one from TMDB / TheTVDB / Fanart.tv right away.
    Never replaces a logo that works. Each title is looked up online at most once a day.
    """
    item_id = "".join(c for c in (item_id or "") if c.isalnum())[:40]
    if not item_id:
        raise PosterError(400, "Which title?")
    it, tag = _logo_tag(item_id)
    if tag and _logo_loads(item_id):
        return {"has": True, "tag": tag}
    if it.get("Type") not in ("Movie", "Series"):
        return {"has": False}
    with _lock:
        last = _logo_asked.get(item_id) or 0
        if time.time() - last < 86400:
            return {"has": False}
        _logo_asked[item_id] = time.time()
    best = _best(item_id, "Logo")
    if not best:
        return {"has": False}
    q = urllib.parse.urlencode({"type": "Logo", "imageUrl": best["Url"]})
    if not _post(f"/Items/{item_id}/RemoteImages/Download?{q}"):
        return {"has": False}
    log(f"put a title logo on {it.get('Name') or item_id} (asked by a details page)")
    _, tag = _logo_tag(item_id)
    return {"has": bool(tag), "tag": tag, "new": True}


def user_route(method, path, query):
    """/api/posters/... for everyone signed in. Returns (status, answer) or None when it isn't one."""
    try:
        if method == "POST" and path == "/api/posters/logo":
            return 200, logo((query.get("id") or [""])[0])
    except PosterError as e:
        return e.status, {"error": e.message}
    return None


def _nightly():
    time.sleep(900)
    done_day = None
    while True:
        time.sleep(600)
        now = time.localtime()
        if not (3 <= now.tm_hour < 5) or done_day == now.tm_yday:
            continue
        done_day = now.tm_yday
        if _admin()[0]:
            fill_missing()
            _measure()


def admin_route(method, path, query, read_json):
    """/api/admin/posters/... for the Fix posters tool. Returns (status, answer) or None when it isn't one."""
    try:
        if method == "GET" and path == "/api/admin/posters/status":
            return 200, status()
        if method == "POST" and path == "/api/admin/posters/fill":
            threading.Thread(target=fill_missing, kwargs={"why": "asked"}, name="posters-fill", daemon=True).start()
            return 200, {"message": "Filling in missing posters now. It takes a few minutes."}
        if method == "POST" and path == "/api/admin/posters/measure":
            threading.Thread(target=_measure, name="posters-measure", daemon=True).start()
            return 200, {"message": "Checking every poster's size now."}
    except PosterError as e:
        return e.status, {"error": e.message}
    return None
