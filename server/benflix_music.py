"""
Benflix music: ask for songs and albums in Benflix, and keep Tower's Music folder tidy.

How it works, in plain words:
- Search: Benflix looks songs, albums and artists up in Deezer's free music catalogue (no account
  needed). That search is much better at finding one song than Lidarr's own search.
- Asking for something: the helper hands Lidarr (the music version of Sonarr and Radarr) the exact
  album the song is on, any edition. Lidarr finds a copy with the same download setup as the movies
  and shows. When it arrives:
    * lossless copies (FLAC and so on) are turned into 320 MP3s,
    * for a single song, every other song from that album is deleted again, including other takes of
      the same song (live, a cappella, acoustic, instrumental, remix ...) unless that take was asked for,
    * the album gets its cover picture, and Jellyfin is told about the new files.
  If no copy turns up, Benflix tries again by itself (after 1 hour, 6 hours, 1 day and 2 days), then
  says "Couldn't find it" with a Try again button.
- Soulseek (optional, through the slskd app): a single song Lidarr hasn't found after 20 minutes (or that
  Lidarr can't do at all) is looked for on Soulseek, where people share single songs. Only 320 MP3s,
  lossless files (turned into 320 MP3) or near-320 MP3s of about the right length are taken, then tagged and
  filed exactly like Lidarr's.
- Playlists: paste a Spotify, YouTube (Music) or Deezer playlist link, or a list of "Artist - Song"
  lines. Only the names are read. Every song is asked for, songs already on Benflix are kept, and the
  playlist is made again in Jellyfin as the songs arrive.
- Drop folder (Homelab/Media/Music Drop): put music files there and every few minutes they are
  sorted into Music/Artist/Album/01 - Song.mp3, using the song info inside the file, the file and
  folder names, and Deezer for anything missing. Lossless files become 320 MP3s (the originals are kept
  in "Music Leftovers"). Files it can't work out go to "Music Leftovers/Couldn't sort".
- Tidy-up (for the music SoulSync got): the same sorting run over a whole folder, with "Check first"
  (shows what would move), "Tidy it" and "Undo".

Only the server admin (Ben) can use any of it. Settings come from the setup command:
LIDARR_URL, LIDARR_KEY and LIDARR_MUSIC (where Lidarr sees the Music folder, like /data/Music).
Optional SPOTIFY_ID and SPOTIFY_SECRET read Spotify playlists longer than 100 songs.
Everything is saved in /musicdata (Tower's appdata/benflix-music). Only uses the Python standard
library, plus ffmpeg/ffprobe (installed by the setup command) for song info and MP3s.
"""

import base64
import json
import os
import re
import shutil
import subprocess
import threading
import time
import unicodedata
import urllib.error
import urllib.parse
import urllib.request
import uuid

MEDIA = "/media"
MUSIC = os.path.join(MEDIA, "Music")
DROP = os.path.join(MEDIA, "Music Drop")
LEFT = os.path.join(MEDIA, "Music Leftovers")
DATA_DIRS = ("/musicdata", "/skips/music")
LIDARR_URL = os.environ.get("LIDARR_URL", "").rstrip("/")
LIDARR_KEY = os.environ.get("LIDARR_KEY", "")
LIDARR_MUSIC = os.environ.get("LIDARR_MUSIC", "").rstrip("/")
# Soulseek (through the slskd app): single songs Lidarr can't find. SLSKD_DIR is slskd's download folder in the helper.
SLSKD_URL = os.environ.get("SLSKD_URL", "").rstrip("/")
SLSKD_KEY = os.environ.get("SLSKD_KEY", "")
SLSKD_DIR = os.environ.get("SLSKD_DIR", "/slskd")
SLSK_AFTER = 20 * 60  # A song Lidarr hasn't found in this long is looked for on Soulseek too
SLSK_WORDS = ("live", "remix", "instrumental", "karaoke", "cover", "acapella", "a cappella", "acoustic", "demo", "8 bit", "nightcore", "slowed", "sped up", "reverb")
DEEZER = "https://api.deezer.com"
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
AUDIO = {".mp3", ".flac", ".m4a", ".aac", ".ogg", ".opus", ".wav", ".wma", ".aiff", ".aif", ".alac", ".ape", ".wv"}
LOSSLESS = {".flac", ".wav", ".aiff", ".aif", ".alac", ".ape", ".wv"}
QUALITY_NAME = "Benflix 320 MP3"
METADATA_NAME = "Benflix"
# Tries again by itself this long after the first look (seconds), then says it couldn't find it
RETRY_AFTER = (3600, 6 * 3600, 24 * 3600, 48 * 3600)
GIVE_UP_AFTER = 72 * 3600
EDITION_WORDS = (
    "deluxe", "remaster", "edition", "expanded", "anniversary", "bonus", "version", "explicit", "clean",
    "mono", "stereo", "special", "collector", "reissue", "super", "extended", "original motion picture",
)

_lock = threading.RLock()
_kick = threading.Event()
_dir = None
_jf = None
_state = {"requests": [], "playlists": [], "admin": {}}
_ready_lidarr = {"ok": False, "checked": 0, "problem": None, "quality": None, "metadata": None, "root": None}
_drop = {"lines": [], "last": None, "sizes": {}}
_tidy = {"running": False, "mode": None, "folder": None, "lines": [], "result": None, "undo": []}
_log_lines = []


class MusicError(Exception):
    def __init__(self, status, message):
        super().__init__(message)
        self.status = status
        self.message = message


def log(text):
    line = time.strftime("%H:%M ") + text
    print("[music] " + text, flush=True)
    with _lock:
        _log_lines.append(line)
        del _log_lines[:-200]


# ---------- Starting and saving ----------


def start(jellyfin_call):
    """Called once by the helper when it starts"""
    global _dir, _jf
    _jf = jellyfin_call
    for d in DATA_DIRS:
        try:
            os.makedirs(d, exist_ok=True)
            test = os.path.join(d, ".write-test")
            with open(test, "w") as f:
                f.write("ok")
            os.remove(test)
            _dir = d
            break
        except OSError:
            continue
    if not _dir:
        log("no folder to save music requests in, so music is off")
        return
    saved = _read("music.json") or {}
    for k in ("requests", "playlists"):
        _state[k] = saved.get(k) or []
    _state["admin"] = _read("admin.json") or {}
    if os.path.isdir(MEDIA):
        for d in (MUSIC, DROP, LEFT):
            try:
                os.makedirs(d, exist_ok=True)
                _own(d)
            except OSError:
                pass
    threading.Thread(target=_worker, name="music-worker", daemon=True).start()
    threading.Thread(target=_drop_worker, name="music-drop", daemon=True).start()
    threading.Thread(target=_lyrics_worker, name="music-lyrics", daemon=True).start()
    log(f"ready (Lidarr {'set up' if LIDARR_URL else 'NOT set up yet'}, ffmpeg {'found' if _has_ffmpeg() else 'missing'})")


def _read(name):
    try:
        with open(os.path.join(_dir, name), "rb") as f:
            return json.loads(f.read())
    except (OSError, ValueError, TypeError):
        return None


def _write(name, value, private=False):
    path = os.path.join(_dir, name)
    tmp = path + ".tmp"
    with open(tmp, "w") as f:
        json.dump(value, f)
    os.replace(tmp, path)
    if private:
        try:
            os.chmod(path, 0o600)
        except OSError:
            pass


def _save():
    with _lock:
        _write("music.json", {"requests": _state["requests"], "playlists": _state["playlists"]})


def remember_admin(token, user_id):
    """The admin's sign-in (kept only on Tower) so Tower can tell Jellyfin about new songs and make playlists"""
    if not _dir or not token:
        return
    with _lock:
        if _state["admin"].get("token") == token:
            return
        _state["admin"] = {"token": token, "user": user_id}
        _write("admin.json", _state["admin"], private=True)


def _own(path):
    """Unraid's usual owner (nobody:users), so the files can be changed from Windows too"""
    try:
        os.chown(path, 99, 100)
        os.chmod(path, 0o777 if os.path.isdir(path) else 0o666)
    except OSError:
        pass


# ---------- Talking to other apps ----------


def _http(url, method="GET", body=None, headers=None, timeout=30, raw=False):
    h = {"User-Agent": "Benflix/1.0", "Accept": "application/json"}
    h.update(headers or {})
    data = None
    if body is not None:
        data = body if isinstance(body, bytes) else json.dumps(body).encode()
        h.setdefault("Content-Type", "application/json")
    req = urllib.request.Request(url, data=data, method=method, headers=h)
    with urllib.request.urlopen(req, timeout=timeout) as r:
        out = r.read()
        return out if raw else (json.loads(out) if out.strip() else None)


_deezer_last = [0.0]


def deezer(path, **params):
    """Deezer's free catalogue (about 10 calls a second is fine)"""
    with _lock:
        wait = 0.12 - (time.time() - _deezer_last[0])
        _deezer_last[0] = time.time() + max(0, wait)
    if wait > 0:
        time.sleep(wait)
    q = ("?" + urllib.parse.urlencode(params)) if params else ""
    try:
        answer = _http(DEEZER + path + q, timeout=20)
    except (urllib.error.URLError, OSError, ValueError):
        raise MusicError(502, "Couldn't reach the music catalogue. Check Tower has internet.")
    if isinstance(answer, dict) and answer.get("error"):
        err = answer["error"]
        if isinstance(err, dict) and err.get("code") == 800:
            raise MusicError(404, "That isn't in the music catalogue.")
        raise MusicError(502, "The music catalogue said no. Try again in a minute.")
    return answer or {}


def lidarr(method, path, body=None, **params):
    if not LIDARR_URL or not LIDARR_KEY:
        raise MusicError(503, "Lidarr isn't connected yet. Follow the music setup steps, then run the Benflix setup command again.")
    q = ("?" + urllib.parse.urlencode(params, doseq=True)) if params else ""
    try:
        return _http(LIDARR_URL + "/api/v1" + path + q, method, body, {"X-Api-Key": LIDARR_KEY}, timeout=60)
    except urllib.error.HTTPError as e:
        try:
            detail = e.read().decode(errors="replace")[:400]
        except OSError:
            detail = ""
        if e.code == 401:
            raise MusicError(502, "Lidarr didn't accept the key. Run the Benflix setup command again.")
        raise MusicError(502, f"Lidarr answered with an error ({e.code}). {_lidarr_reason(detail)}".strip())
    except (urllib.error.URLError, OSError):
        raise MusicError(502, "Couldn't reach Lidarr. Check it's running on Tower.")
    except ValueError:
        raise MusicError(502, "Lidarr's answer didn't make sense.")


def _lidarr_reason(detail):
    try:
        j = json.loads(detail)
        if isinstance(j, list) and j:
            return str(j[0].get("errorMessage") or "")
        if isinstance(j, dict):
            return str(j.get("message") or "")
    except ValueError:
        pass
    return ""


def _jellyfin(method, path, body=None):
    token = _state["admin"].get("token")
    if not _jf or not token:
        return None
    try:
        status, data, _ = _jf(method, path, token=token, body=json.dumps(body).encode() if body is not None else None, device_id="benflix-music")
    except Exception:  # The helper's own error type
        return None
    if status == 401:
        log("Jellyfin no longer accepts the saved sign-in; open Benflix as Ben once to fix it")
        return None
    if status >= 300:
        return None
    try:
        return json.loads(data) if data and data.strip() else {}
    except ValueError:
        return {}


def _tell_jellyfin(paths, kind="Created"):
    """Jellyfin looks at just these files (Jellyfin sees Media at /media too)"""
    paths = [p for p in paths if p]
    if paths:
        _jellyfin("POST", "/Library/Media/Updated", {"Updates": [{"Path": p, "UpdateType": kind} for p in paths[:200]]})


# ---------- Words and names ----------


def norm(text):
    """For comparing names: no capitals, accents, edition words in brackets, 'feat.' parts or punctuation"""
    t = unicodedata.normalize("NFKD", str(text or "")).encode("ascii", "ignore").decode().lower()
    t = t.replace("&", " and ")
    t = re.sub(r"[\(\[][^\)\]]*[\)\]]", lambda m: "" if any(w in m.group(0) for w in EDITION_WORDS + ("feat", "ft.", "with ", "from ")) else m.group(0), t)
    t = re.sub(r"\s+-\s+(\d{4}\s+)?(remaster(ed)?|mono|stereo|single version|radio edit|live).*$", "", t)
    t = re.sub(r"\b(feat|ft|featuring)\b.*$", "", t)
    t = re.sub(r"[^a-z0-9]+", " ", t)
    return re.sub(r"\s+", " ", t).strip()


def same(a, b):
    a, b = norm(a), norm(b)
    return bool(a) and (a == b or (len(a) > 3 and len(b) > 3 and (a.startswith(b) or b.startswith(a))))


# Other takes of a song: kept only when that exact take was asked for
ALT_WORDS = (
    "live", "remix", "mix", "instrumental", "karaoke", "cover", "acapella", "a cappella", "acoustic", "demo",
    "8 bit", "nightcore", "slowed", "sped up", "reverb", "rehearsal", "melody pass", "stripped", "unplugged",
    "orchestral", "piano version", "voice memo", "rough", "session", "sessions", "reprise", "commentary",
    "rework", "edit", "vip", "dub", "bootleg", "mashup", "megamix", "medley", "snippet", "rehearsals", "a capella",
)
FINE_EDITS = ("radio edit", "single edit", "clean edit", "explicit edit", "album edit")


def _alt_bits(title):
    """The 'other take' words in a song title, like {'live'} for 'Song (Live from Rehearsal)'"""
    t = unicodedata.normalize("NFKD", str(title or "")).encode("ascii", "ignore").decode().lower()
    # Only the extra bits count: "(...)", "[...]" and anything after " - ", so "Jam Session" or "Live Your Life" stay plain
    extra = re.findall(r"[\(\[]([^\)\]]*)[\)\]]", t) + [m for m in re.split(r"\s+[-\u2013\u2014]\s+", t)[1:]]
    t = " " + re.sub(r"[^a-z0-9]+", " ", " | ".join(extra)) + " "
    for fine in FINE_EDITS:
        t = t.replace(" " + fine + " ", " ")
    return {w for w in ALT_WORDS if " " + w + " " in t}


def same_song(track_title, wanted_title):
    """Same song AND the same take: the original never matches its live, acoustic, a cappella, remix ... takes"""
    if not same(track_title, wanted_title):
        return False
    want, got = _alt_bits(wanted_title), _alt_bits(track_title)
    return got <= want if not want else want <= got


def _wanted_title(r):
    """The full Deezer title (it keeps '(Live)' and so on), else the short one"""
    return r.get("full_title") or r.get("title") or ""


def _pick_tracks(tracks, wanted):
    """Lidarr's tracks with a file that are this exact song, the closest first (one is kept)"""
    hits = [t for t in tracks if t.get("hasFile") and same_song(t.get("title"), wanted)]
    hits.sort(key=lambda t: (norm(t.get("title")) != norm(wanted), len(t.get("title") or ""), t.get("absoluteTrackNumber") or 0))
    return hits


def safe(name, fallback="Unknown"):
    """A name that's safe as a folder or file name on Windows and Unraid"""
    t = re.sub(r'[<>:"/\\|?*\x00-\x1f]', "", str(name or "")).strip().strip(".")
    t = re.sub(r"\s+", " ", t)
    return (t or fallback)[:120]


def _song(t):
    """One Deezer song, as Benflix shows it"""
    al = t.get("album") or {}
    return {
        "id": t.get("id"),
        "title": t.get("title") or "",
        "artist": (t.get("artist") or {}).get("name") or "",
        "album": al.get("title") or "",
        "album_id": al.get("id"),
        "cover": al.get("cover_medium") or al.get("cover") or "",
        "seconds": t.get("duration") or 0,
        "explicit": bool(t.get("explicit_lyrics")),
    }


def _album(a):
    return {
        "id": a.get("id"),
        "title": a.get("title") or "",
        "artist": (a.get("artist") or {}).get("name") or "",
        "cover": a.get("cover_medium") or a.get("cover") or "",
        "year": (a.get("release_date") or "")[:4],
        "type": a.get("record_type") or "album",
        "songs": a.get("nb_tracks") or 0,
    }


def _artist(a):
    return {"id": a.get("id"), "name": a.get("name") or "", "picture": a.get("picture_medium") or "", "fans": a.get("nb_fan") or 0}


# ---------- What the app and web page can ask ----------


def route(method, path, query, token, user_id, is_admin, read_json, is_kids=False):
    """The helper sends /api/music/... here. Returns (status, answer). Made for you is for everyone but Kids;
    the rest is only for the admin (Ben)."""
    if method == "GET" and path == "/api/music/mixes":
        if is_kids:
            return 403, {"error": "No music on the Kids profile."}
        try:
            return 200, mixes_for(token, user_id)
        except Exception as e:
            log(f"Made for you problem: {e!r}")
            return 200, {"mixes": []}
    if not is_admin:
        return 403, {"error": "Only Ben can ask for music."}
    if not _dir:
        return 503, {"error": "Music is off: the helper has no folder to save requests in. Run the Benflix setup command again."}
    remember_admin(token, user_id)
    q = lambda k: (query.get(k) or [""])[0].strip()  # noqa: E731
    try:
        if method == "GET" and path == "/api/music/status":
            return 200, status()
        if method == "GET" and path == "/api/music/search":
            return 200, search(q("q"))
        if method == "GET" and path == "/api/music/artist":
            return 200, artist_page(q("id"))
        if method == "GET" and path == "/api/music/album":
            return 200, album_page(q("id"))
        if method == "POST" and path == "/api/music/request":
            body = read_json()
            return 200, ask(str(body.get("kind") or ""), body.get("id"))
        m = re.fullmatch(r"/api/music/request/([\w-]+)/(retry|remove)", path)
        if method == "POST" and m:
            return 200, (retry(m.group(1)) if m.group(2) == "retry" else remove(m.group(1)))
        if method == "POST" and path == "/api/music/playlist":
            body = read_json()
            return 200, playlist_import(str(body.get("link") or ""), str(body.get("name") or ""))
        m = re.fullmatch(r"/api/music/playlist/([\w-]+)/remove", path)
        if method == "POST" and m:
            return 200, playlist_remove(m.group(1))
        if method == "GET" and path == "/api/music/tidy":
            return 200, tidy_status()
        if method == "POST" and path == "/api/music/tidy":
            body = read_json()
            return 200, tidy_start(str(body.get("mode") or ""), str(body.get("folder") or ""), str(body.get("undo") or ""))
    except MusicError as e:
        return e.status, {"error": e.message}
    return 404, {"error": "Not found"}


def search(text):
    if len(text) < 2:
        return {"songs": [], "albums": [], "artists": []}
    songs = deezer("/search", q=text, limit=25).get("data") or []
    albums = deezer("/search/album", q=text, limit=15).get("data") or []
    artists = deezer("/search/artist", q=text, limit=10).get("data") or []
    out = {
        "songs": [_song(t) for t in songs if t.get("type", "track") == "track"],
        "albums": [_album(a) for a in albums],
        "artists": [_artist(a) for a in artists],
    }
    _mark_asked(out)
    return out


def artist_page(artist_id):
    if not str(artist_id).isdigit():
        raise MusicError(400, "Which artist?")
    a = deezer(f"/artist/{artist_id}")
    top = deezer(f"/artist/{artist_id}/top", limit=15).get("data") or []
    albums = deezer(f"/artist/{artist_id}/albums", limit=60).get("data") or []
    for al in albums:
        al["artist"] = {"name": a.get("name")}
    albums.sort(key=lambda x: x.get("release_date") or "", reverse=True)
    out = {"artist": _artist(a), "songs": [_song(t) for t in top], "albums": [_album(x) for x in albums]}
    _mark_asked(out)
    return out


def album_page(album_id):
    if not str(album_id).isdigit():
        raise MusicError(400, "Which album?")
    a = deezer(f"/album/{album_id}")
    songs = []
    for t in (a.get("tracks") or {}).get("data") or []:
        t["album"] = {"id": a.get("id"), "title": a.get("title"), "cover_medium": a.get("cover_medium")}
        songs.append(_song(t))
    out = {"album": _album(a), "songs": songs}
    _mark_asked(out)
    return out


def _mark_asked(out):
    """Adds 'asked' (the words for its request) to anything already asked for"""
    with _lock:
        by = {(r["kind"], r.get("deezer")): r for r in _state["requests"]}
    for kind, key in (("song", "songs"), ("album", "albums")):
        for x in out.get(key) or []:
            r = by.get((kind, x.get("id")))
            if r:
                x["asked"] = _words(r)[0]
    if out.get("album"):
        r = by.get(("album", out["album"].get("id")))
        if r:
            out["album"]["asked"] = _words(r)[0]


def ask(kind, deezer_id, playlist=None, quiet=False):
    """Asks for one song or album. Returns the request (an existing one if it was already asked for)."""
    if kind not in ("song", "album") or not str(deezer_id).isdigit():
        raise MusicError(400, "Pick a song or an album to ask for.")
    deezer_id = int(deezer_id)
    with _lock:
        for r in _state["requests"]:
            if r["kind"] == kind and r.get("deezer") == deezer_id:
                if r["state"] in ("not_found", "problem") and not quiet:
                    _retry(r)
                    _save()
                    _kick.set()
                return _public(r)
    if kind == "song":
        t = deezer(f"/track/{deezer_id}")
        al = t.get("album") or {}
        r = {
            "title": t.get("title_short") or t.get("title") or "",
            "full_title": t.get("title") or "",
            "artist": (t.get("artist") or {}).get("name") or "",
            "album": al.get("title") or "",
            "album_deezer": al.get("id"),
            "cover": al.get("cover_medium") or "",
            "cover_big": al.get("cover_xl") or al.get("cover_big") or "",
            "disc": t.get("disk_number") or 1,
            "number": t.get("track_position") or 0,
            "seconds": t.get("duration") or 0,
        }
    else:
        a = deezer(f"/album/{deezer_id}")
        r = {
            "title": a.get("title") or "",
            "artist": (a.get("artist") or {}).get("name") or "",
            "album": a.get("title") or "",
            "album_deezer": deezer_id,
            "cover": a.get("cover_medium") or "",
            "cover_big": a.get("cover_xl") or a.get("cover_big") or "",
            "year": (a.get("release_date") or "")[:4],
        }
    r.update({"id": uuid.uuid4().hex[:12], "kind": kind, "deezer": deezer_id, "state": "adding", "created": time.time(), "tries": []})
    if playlist:
        r["playlist"] = playlist
    with _lock:
        _state["requests"].insert(0, r)
        _save()
    if not quiet:
        log(f"asked for {kind} {r['artist']} - {r['title']}")
    _kick.set()
    return _public(r)


def _find(rid):
    for r in _state["requests"]:
        if r["id"] == rid:
            return r
    raise MusicError(404, "That request isn't there any more.")


def _retry(r):
    r.update({"state": "adding" if not r.get("album_id") else "search_now", "tries": [], "note": None, "since": time.time(), "slsk_tried": False})
    if not LIDARR_URL and _slsk_on() and r["kind"] == "song":
        r["state"] = "slsk_search"


def retry(rid):
    with _lock:
        r = _find(rid)
        _retry(r)
        _save()
    _kick.set()
    return _public(r)


def remove(rid):
    with _lock:
        r = _find(rid)
        _state["requests"].remove(r)
        if r["state"] == "slsk_downloading" and (r.get("slsk") or {}).get("cands"):
            try:
                _slsk_cancel(r["slsk"]["cands"][r["slsk"]["i"]])
            except (IndexError, MusicError):
                pass
        still = [x for x in _state["requests"] if x.get("album_id") == r.get("album_id") and x["state"] != "ready"]
        _save()
    if r.get("album_id") and r["state"] != "ready" and not still:
        try:
            lidarr("PUT", "/album/monitor", {"albumIds": [r["album_id"]], "monitored": False})
        except MusicError:
            pass
    return {"ok": True}


def _words(r):
    """(words, how full the thin bar is from 0 to 1, red?) for a request"""
    s = r["state"]
    if s == "ready":
        return "In your music", 1.0, False
    if s == "not_found":
        return r.get("note") or "Couldn't find it", 1.0, True
    if s == "problem":
        return r.get("note") or "Something went wrong", 1.0, True
    if s == "downloading":
        pct = int(r.get("pct") or 0)
        return f"Downloading, {pct}%", 0.4 + 0.55 * pct / 100, False
    if s == "tidying":
        return "Putting it in your Music folder", 0.97, False
    if s == "slsk_search":
        return "Looking on Soulseek", 0.3, False
    if s == "slsk_downloading":
        pct = int(r.get("pct") or 0)
        if not pct and r.get("queued"):
            return "Waiting in someone's Soulseek queue", 0.35, False
        return f"Downloading, {pct}%", 0.4 + 0.55 * pct / 100, False
    if s in ("searching", "search_now"):
        if len(r.get("tries") or []) > 1:
            return "Still looking (tries again by itself)", 0.3, False
        return "Looking for a copy", 0.3, False
    return "Finding it", 0.1, False


def _public(r):
    words, bar, bad = _words(r)
    return {
        "id": r["id"],
        "kind": r["kind"],
        "deezer": r.get("deezer"),
        "title": r.get("title") or "",
        "artist": r.get("artist") or "",
        "album": r.get("album") or "",
        "cover": r.get("cover") or "",
        "state": r["state"],
        "words": words,
        "bar": round(bar, 3),
        "bad": bad,
        "can_retry": r["state"] in ("not_found", "problem"),
        "created": int(r.get("created") or 0),
        "playlist": r.get("playlist"),
    }


def status():
    problems = []
    if not LIDARR_URL and not SLSKD_URL:
        problems.append("Lidarr isn't connected yet. Follow the music setup steps, then run the Benflix setup command again.")
    elif _ready_lidarr.get("problem"):
        problems.append(_ready_lidarr["problem"])
    if SLSKD_URL and _slsk_problem[0]:
        problems.append(_slsk_problem[0])
    if not _has_ffmpeg():
        problems.append("ffmpeg is missing on Tower, so lossless files can't become MP3s. Run the Benflix setup command again.")
    if not os.path.isdir(MUSIC):
        problems.append("The Music folder isn't there. Run the Benflix setup command again.")
    if not _state["admin"].get("token"):
        problems.append("Open Benflix as Ben once so Tower can add new songs to Jellyfin.")
    with _lock:
        reqs = [_public(r) for r in _state["requests"]]
        pls = [_playlist_public(p) for p in _state["playlists"]]
        drop = list(_drop["lines"][-12:])
    return {
        "lidarr": bool(LIDARR_URL) and _ready_lidarr.get("ok", False),
        "soulseek": _slsk_on(),
        "problems": problems,
        "requests": reqs,
        "playlists": pls,
        "drop_folder": "\\\\TOWER\\Homelab\\Media\\Music Drop",
        "music_folder": "\\\\TOWER\\Homelab\\Media\\Music",
        "drop_lines": drop,
        "drop_last": _drop.get("last"),
        "tidy": tidy_status(),
    }


# ---------- Lidarr set-up (done by itself, once) ----------


def _ensure_lidarr():
    """Makes Lidarr's music profile, quality and folder the way Benflix wants. Returns True when ready."""
    if _ready_lidarr["ok"]:
        return True
    if time.time() - _ready_lidarr["checked"] < 300:
        return False
    _ready_lidarr["checked"] = time.time()
    try:
        lidarr("GET", "/system/status")
        _ready_lidarr["quality"] = _quality_profile()
        _ready_lidarr["metadata"] = _metadata_profile()
        _ready_lidarr["root"] = _root_folder()
        _naming()
        problems = []
        if not lidarr("GET", "/indexer"):
            problems.append("Lidarr has no places to search yet (indexers). Add them in Prowlarr or Lidarr, see the music setup steps.")
        if not lidarr("GET", "/downloadclient"):
            problems.append("Lidarr has no download app yet. Add qBittorrent in Lidarr, see the music setup steps.")
        _ready_lidarr["problem"] = " ".join(problems) or None
        _ready_lidarr["ok"] = True
        log("Lidarr is set up for Benflix")
        return True
    except MusicError as e:
        _ready_lidarr["problem"] = e.message
        log("Lidarr set-up: " + e.message)
        return False


def _quality_profile():
    for p in lidarr("GET", "/qualityprofile") or []:
        if p.get("name") == QUALITY_NAME:
            return p["id"]
    schema = lidarr("GET", "/qualityprofile/schema")
    qualities = []

    def walk(items):
        for it in items or []:
            if it.get("quality"):
                qualities.append(it["quality"])
            walk(it.get("items"))

    walk(schema.get("items"))
    # Best last. 320 MP3 first choice, then lossless (turned into 320 MP3 here), then near-320 MP3s.
    order = ["MP3-320", "FLAC", "FLAC 24BIT", "ALAC", "MP3-VBR-V0", "MP3-256"]
    rank = {n: i for i, n in enumerate(order)}
    wanted = [q for q in qualities if q.get("name", "").upper() in rank]
    others = [q for q in qualities if q.get("name", "").upper() not in rank]
    wanted.sort(key=lambda q: -rank[q["name"].upper()])
    cutoff = next((q["id"] for q in qualities if q.get("name", "").upper() == "MP3-320"), wanted[-1]["id"] if wanted else 0)
    schema.update(
        {
            "name": QUALITY_NAME,
            "upgradeAllowed": False,
            "cutoff": cutoff,
            "items": [{"quality": q, "items": [], "allowed": False} for q in others]
            + [{"quality": q, "items": [], "allowed": True} for q in wanted],
        }
    )
    schema.pop("id", None)
    return lidarr("POST", "/qualityprofile", schema)["id"]


def _metadata_profile():
    for p in lidarr("GET", "/metadataprofile") or []:
        if p.get("name") == METADATA_NAME:
            return p["id"]
    schema = lidarr("GET", "/metadataprofile/schema")
    primary = {"album", "ep", "single"}
    secondary = {"studio", "compilation", "soundtrack", "remix", "mixtape/street"}
    for it in schema.get("primaryAlbumTypes") or []:
        it["allowed"] = (it.get("albumType") or {}).get("name", "").lower() in primary
    for it in schema.get("secondaryAlbumTypes") or []:
        it["allowed"] = (it.get("albumType") or {}).get("name", "").lower() in secondary
    for it in schema.get("releaseStatuses") or []:
        it["allowed"] = (it.get("releaseStatus") or {}).get("name", "").lower() == "official"
    schema["name"] = METADATA_NAME
    schema.pop("id", None)
    return lidarr("POST", "/metadataprofile", schema)["id"]


def _lidarr_music_path():
    if LIDARR_MUSIC:
        return LIDARR_MUSIC
    for r in lidarr("GET", "/rootfolder") or []:
        if r.get("path", "").rstrip("/").lower().endswith("/music"):
            return r["path"].rstrip("/")
    raise MusicError(503, "Couldn't work out where Lidarr sees the Music folder. Check Lidarr's folders match the setup steps, then run the Benflix setup command again.")


def _root_folder():
    want = _lidarr_music_path()
    for r in lidarr("GET", "/rootfolder") or []:
        if r.get("path", "").rstrip("/") == want:
            return r["path"]
    lidarr(
        "POST",
        "/rootfolder",
        {
            "name": "Music",
            "path": want,
            "defaultQualityProfileId": _ready_lidarr["quality"],
            "defaultMetadataProfileId": _ready_lidarr["metadata"],
            "defaultMonitorOption": "none",
            "defaultNewItemMonitorOption": "none",
            "defaultTags": [],
        },
    )
    return want


def _naming():
    """Music/Artist/Album/01 - Song, and Lidarr writes the song info and cover into the files"""
    n = lidarr("GET", "/config/naming")
    n.update(
        {
            "renameTracks": True,
            "replaceIllegalCharacters": True,
            "artistFolderFormat": "{Artist Name}",
            "standardTrackFormat": "{Album Title}/{track:00} - {Track Title}",
            "multiDiscTrackFormat": "{Album Title}/{medium:0}-{track:00} - {Track Title}",
        }
    )
    lidarr("PUT", f"/config/naming/{n.get('id', 1)}", n)
    try:
        m = lidarr("GET", "/config/metadataProvider")
        changed = False
        for k, v in (("writeAudioTags", "newFiles"), ("embedCoverArt", True)):
            if k in m and m[k] != v:
                m[k] = v
                changed = True
        if changed:
            lidarr("PUT", f"/config/metadataProvider/{m.get('id', 1)}", m)
    except MusicError:
        pass


def _to_helper_path(lidarr_path):
    """Lidarr's /data/Music/... is the helper's /media/Music/..."""
    base = _lidarr_music_path()
    p = str(lidarr_path or "")
    if p.startswith(base + "/"):
        return MUSIC + p[len(base):]
    return None


# ---------- The worker: moves every request along ----------


def _worker():
    while True:
        _kick.wait(60)
        _kick.clear()
        try:
            _tick()
        except Exception as e:  # Keep going whatever happens
            log(f"worker problem: {e!r}")
        try:
            _playlists_tick()
        except Exception as e:
            log(f"playlist problem: {e!r}")


def _tick():
    with _lock:
        todo = [r for r in _state["requests"] if r["state"] not in ("ready", "not_found", "problem")]
        recheck = [r for r in _state["requests"] if r["state"] == "ready" and r["kind"] == "song" and r.get("album_id") and not r.get("takes_checked")]
    if recheck and LIDARR_URL and _ensure_lidarr():
        for r in recheck:
            try:
                _drop_other_takes(r)
            except MusicError as e:
                log(f"couldn't check other takes of {r['artist']} - {r['title']}: {e.message}")
            with _lock:
                _save()
    if not todo:
        return
    lidarr_ok = bool(LIDARR_URL) and _ensure_lidarr()
    queue = _queue() if lidarr_ok else {}
    for r in todo:
        try:
            if r["state"].startswith("slsk_"):
                _slsk_step(r)
            elif lidarr_ok:
                _step(r, queue)
                _maybe_soulseek(r)
            elif r["kind"] == "song" and _slsk_on() and not r.get("slsk_tried"):
                r["state"] = "slsk_search"
                _slsk_step(r)
        except MusicError as e:
            r["fails"] = r.get("fails", 0) + 1
            if r["fails"] >= 5:
                r.update({"state": "problem", "note": e.message})
                log(f"gave up on {r['artist']} - {r['title']}: {e.message}")
        with _lock:
            _save()


def _queue():
    """Lidarr's downloads, by album"""
    out = {}
    try:
        page = lidarr("GET", "/queue", page=1, pageSize=200, includeAlbum="true") or {}
    except MusicError:
        return out
    for q in page.get("records") or []:
        a = q.get("albumId")
        if not a:
            continue
        size, left = q.get("size") or 0, q.get("sizeleft") or 0
        pct = int(100 * (1 - left / size)) if size else 0
        warn = (q.get("trackedDownloadStatus") or "").lower() in ("warning", "error")
        msg = None
        if warn:
            msgs = [m for s in q.get("statusMessages") or [] for m in s.get("messages") or []]
            msg = q.get("errorMessage") or (msgs[0] if msgs else None)
        out[a] = {"pct": max(pct, out.get(a, {}).get("pct", 0)), "warn": msg, "state": q.get("trackedDownloadState")}
    return out


def _step(r, queue):
    s = r["state"]
    if s == "adding":
        _add_to_lidarr(r)
        return
    if s == "wait_artist":
        _pick_album(r)
        return
    if s == "search_now":
        _search(r)
        return
    album_id = r.get("album_id")
    if not album_id:
        r["state"] = "adding"
        return
    if s in ("searching", "downloading"):
        q = queue.get(album_id)
        if q:
            r["state"] = "downloading"
            r["pct"] = q["pct"]
            r["note"] = None
            if q.get("warn") and q.get("state") in ("importPending", "importBlocked", "importFailed"):
                r["note"] = None  # Lidarr is still deciding; the tidy step checks the files
            return
        files = _album_files(album_id)
        if files:
            r["state"] = "tidying"
            _finish(r, files)
            return
        if s == "downloading":
            # Left the queue without files: the copy was bad or blocked, so look again
            r["state"] = "search_now"
            return
        first = (r.get("tries") or [time.time()])[0]
        waited = time.time() - first
        if waited > GIVE_UP_AFTER:
            r.update({"state": "not_found", "note": "Couldn't find it"})
            log(f"couldn't find {r['artist']} - {r['title']}")
            _unmonitor(r)
            return
        tries = len(r.get("tries") or [])
        if not tries or (tries <= len(RETRY_AFTER) and waited > RETRY_AFTER[tries - 1]):
            _search(r)
        return
    if s == "tidying":
        files = _album_files(album_id)
        if files:
            _finish(r, files)


def _add_to_lidarr(r):
    """Finds the exact album in Lidarr (any edition) and adds it, with its artist if needed"""
    q, m = _ready_lidarr["quality"], _ready_lidarr["metadata"]
    root = _ready_lidarr["root"]
    found = lidarr("GET", "/album/lookup", term=f"{r['artist']} {r['album']}") or []
    match = next((a for a in found if same(a.get("title"), r["album"]) and same((a.get("artist") or {}).get("artistName"), r["artist"])), None)
    if match:
        r["mb_album"] = match.get("foreignAlbumId")
        existing = [a for a in lidarr("GET", "/album", foreignAlbumId=match.get("foreignAlbumId")) or [] if a.get("foreignAlbumId") == match.get("foreignAlbumId")]
        if existing:
            r["album_id"] = existing[0]["id"]
            r["artist_id"] = existing[0].get("artistId")
            r["state"] = "search_now"
            _search(r)
            return
        body = dict(match)
        body.update({"monitored": True, "anyReleaseOk": True, "addOptions": {"searchForNewAlbum": False}})
        art = dict(body.get("artist") or {})
        if art.get("id"):
            body["artistId"] = art["id"]
        art.update(
            {
                "qualityProfileId": q,
                "metadataProfileId": m,
                "rootFolderPath": root,
                "monitored": True,
                "monitorNewItems": "none",
                "addOptions": {"monitor": "none", "searchForMissingAlbums": False},
            }
        )
        body["artist"] = art
        try:
            added = lidarr("POST", "/album", body)
            r["album_id"] = added["id"]
            r["artist_id"] = added.get("artistId")
            r["state"] = "search_now"
            log(f"added album {r['artist']} - {r['album']} to Lidarr")
            return
        except MusicError as e:
            log(f"adding the album straight away didn't work ({e.message}); adding the artist first")
        r["mb_artist"] = (match.get("artist") or {}).get("foreignArtistId")
    # The artist first, then pick the album (or the album with the song) from their list
    if not r.get("mb_artist"):
        people = lidarr("GET", "/artist/lookup", term=r["artist"]) or []
        who = next((p for p in people if same(p.get("artistName"), r["artist"])), None)
        if not who:
            r.update({"state": "not_found", "note": "Lidarr's music list doesn't know this artist"})
            return
        r["mb_artist"] = who.get("foreignArtistId")
    mine = next((a for a in lidarr("GET", "/artist") or [] if a.get("foreignArtistId") == r["mb_artist"]), None)
    if not mine:
        people = lidarr("GET", "/artist/lookup", term=f"lidarr:{r['mb_artist']}") or []
        if not people:
            r.update({"state": "not_found", "note": "Lidarr's music list doesn't know this artist"})
            return
        body = dict(people[0])
        body.update(
            {
                "qualityProfileId": q,
                "metadataProfileId": m,
                "rootFolderPath": root,
                "monitored": True,
                "monitorNewItems": "none",
                "addOptions": {"monitor": "none", "searchForMissingAlbums": False},
            }
        )
        mine = lidarr("POST", "/artist", body)
        log(f"added artist {r['artist']} to Lidarr")
    r["artist_id"] = mine["id"]
    r["state"] = "wait_artist"
    r["since"] = time.time()


def _pick_album(r):
    """After Lidarr has read the artist's albums: the right album, or for a song any album that has it"""
    albums = lidarr("GET", "/album", artistId=r["artist_id"], includeAllArtistAlbums="true") or []
    if not albums:
        if time.time() - r.get("since", time.time()) > 1800:
            r.update({"state": "not_found", "note": "Lidarr couldn't load this artist's albums"})
        return
    pick = next((a for a in albums if r.get("mb_album") and a.get("foreignAlbumId") == r["mb_album"]), None)
    pick = pick or next((a for a in albums if same(a.get("title"), r["album"])), None)
    if not pick and r["kind"] == "song":
        tracks = lidarr("GET", "/track", artistId=r["artist_id"]) or []
        having = {t.get("albumId") for t in tracks if same_song(t.get("title"), _wanted_title(r))}
        types = {"album": 0, "ep": 1, "single": 2}
        options = [a for a in albums if a["id"] in having]
        options.sort(key=lambda a: (types.get((a.get("albumType") or "").lower(), 3), a.get("releaseDate") or "9999"))
        pick = options[0] if options else None
        if not pick and time.time() - r.get("since", time.time()) < 600:
            return  # Lidarr may still be reading the songs
    if not pick:
        r.update({"state": "not_found", "note": "Couldn't find that album in Lidarr's music list"})
        return
    r["album_id"] = pick["id"]
    r["state"] = "search_now"
    _search(r)


def _search(r):
    album_id = r["album_id"]
    try:
        album = lidarr("GET", f"/album/{album_id}")
        if not album.get("monitored") or not album.get("anyReleaseOk"):
            album.update({"monitored": True, "anyReleaseOk": True})
            lidarr("PUT", f"/album/{album_id}", album)
    except MusicError:
        lidarr("PUT", "/album/monitor", {"albumIds": [album_id], "monitored": True})
    # A song from an album that's already here (an earlier song or album request) needs no download
    if r["kind"] == "song" and _wanted_track_here(r):
        r["state"] = "tidying"
        _finish(r, _album_files(album_id))
        return
    lidarr("POST", "/command", {"name": "AlbumSearch", "albumIds": [album_id]})
    r.setdefault("tries", []).append(time.time())
    r["state"] = "searching"


def _album_files(album_id):
    return lidarr("GET", "/trackfile", albumId=album_id) or []


def _tracks(album_id):
    return lidarr("GET", "/track", albumId=album_id) or []


def _wanted_track_here(r):
    return bool(_pick_tracks(_tracks(r["album_id"]), _wanted_title(r)))


def _unmonitor(r):
    with _lock:
        others = [x for x in _state["requests"] if x is not r and x.get("album_id") == r.get("album_id") and x["state"] not in ("ready", "not_found", "problem")]
    if r.get("album_id") and not others:
        try:
            lidarr("PUT", "/album/monitor", {"albumIds": [r["album_id"]], "monitored": False})
        except MusicError:
            pass


def _drop_other_takes(r):
    """Songs that arrived before the 'other takes' fix: deletes the live / a cappella / remix ... takes
    that were kept by mistake next to the asked-for song (only when every asked-for song is still there)"""
    album_id = r["album_id"]
    with _lock:
        mates = [x for x in _state["requests"] if x.get("album_id") == album_id and x["state"] not in ("not_found", "problem")]
    if any(x["kind"] == "album" for x in mates) or any(x["state"] != "ready" for x in mates):
        if any(x["kind"] == "album" for x in mates):
            for x in mates:
                x["takes_checked"] = True
        return
    tracks, files = _tracks(album_id), _album_files(album_id)
    keep = set()
    for x in mates:
        hits = _pick_tracks(tracks, _wanted_title(x))
        if not hits:
            r["takes_checked"] = True  # The song isn't in Lidarr's list any more: leave everything alone
            return
        keep.add(hits[0].get("trackFileId"))
    drop = [f for f in files if f.get("id") not in keep]
    if drop:
        lidarr("DELETE", "/trackfile/bulk", {"trackFileIds": [f["id"] for f in drop]})
        _tell_jellyfin([_to_helper_path(f.get("path")) for f in drop], kind="Deleted")
        log(f"removed {len(drop)} other take(s) kept by mistake from {r['artist']} - {r['album']}")
    for x in mates:
        x["takes_checked"] = True


def _finish(r, files):
    """The copy arrived: keep only the wanted songs, make 320 MP3s, add the cover, tell Jellyfin"""
    album_id = r["album_id"]
    tracks = _tracks(album_id)
    with _lock:
        mates = [x for x in _state["requests"] if x.get("album_id") == album_id and x["state"] not in ("not_found", "problem")]
    whole = any(x["kind"] == "album" for x in mates)
    keep_ids = set()
    if not whole:
        for x in mates:
            if x["kind"] == "song":
                hits = _pick_tracks(tracks, _wanted_title(x))
                if hits:
                    keep_ids.add(hits[0].get("trackFileId"))
        if r["kind"] == "song" and not _pick_tracks(tracks, _wanted_title(r)):
            # The copy didn't have this song: try once more, then say so
            if len(r.get("tries") or []) >= 3:
                r.update({"state": "not_found", "note": "The copies found didn't have this song"})
                _unmonitor(r)
            else:
                r["state"] = "search_now"
            return
    paths, drop = [], []
    for f in files:
        if whole or f.get("id") in keep_ids:
            paths.append(_to_helper_path(f.get("path")))
        else:
            drop.append(f["id"])
    if drop:
        lidarr("DELETE", "/trackfile/bulk", {"trackFileIds": drop})
        log(f"kept just the asked-for song(s) from {r['artist']} - {r['album']}")
    made, converted = [], False
    for p in paths:
        if p and os.path.isfile(p):
            mp3 = _make_mp3(p, delete_original=True)
            converted = converted or bool(mp3)
            made.append(mp3 or p)
    folder = os.path.dirname(made[0]) if made else None
    if folder:
        _cover(folder, r.get("cover_big"))
        if converted:
            try:
                lidarr("POST", "/command", {"name": "RescanFolders", "folders": [os.path.dirname(f.get("path", "")) for f in files[:1]]})
            except MusicError:
                pass
    _tell_jellyfin(made)
    for x in mates:
        if x["kind"] == "album" or _pick_tracks(tracks, _wanted_title(x)):
            x["state"] = "ready"
            x["ready_at"] = time.time()
            x["note"] = None
    if r["state"] != "ready":
        r.update({"state": "ready", "ready_at": time.time()})
    for x in mates:
        x["takes_checked"] = True
    try:
        lidarr("PUT", "/album/monitor", {"albumIds": [album_id], "monitored": False})
    except MusicError:
        pass
    log(f"ready: {r['artist']} - {r['title']}")


# ---------- Soulseek (single songs, through slskd) ----------

_slsk_problem = [None]


def _slsk_on():
    return bool(SLSKD_URL and SLSKD_KEY)


def slskd(method, path, body=None):
    try:
        return _http(SLSKD_URL + "/api/v0" + path, method, body, {"X-API-Key": SLSKD_KEY}, timeout=30)
    except urllib.error.HTTPError as e:
        if e.code in (401, 403):
            _slsk_problem[0] = "slskd (Soulseek) didn't accept the key. Run the Benflix setup command again."
        raise MusicError(502, f"slskd (Soulseek) answered with an error ({e.code}).")
    except (urllib.error.URLError, OSError, ValueError):
        _slsk_problem[0] = "Couldn't reach slskd (Soulseek). Check it's running on Tower."
        raise MusicError(502, "Couldn't reach slskd (Soulseek).")


def _maybe_soulseek(r):
    """A song Lidarr couldn't do, or hasn't found in 20 minutes, is looked for on Soulseek (once per try)"""
    if r["kind"] != "song" or not _slsk_on() or r.get("slsk_tried"):
        return
    stuck = r["state"] == "searching" and r.get("tries") and time.time() - r["tries"][0] > SLSK_AFTER
    if stuck or r["state"] in ("not_found", "problem"):
        r["lidarr_state"] = r["state"]
        r.update({"state": "slsk_search", "note": None})
        log(f"looking on Soulseek for {r['artist']} - {r['title']}")


def _slsk_step(r):
    if r["state"] == "slsk_search":
        r["slsk"] = {"cands": _slsk_find(r), "i": 0}
        _slsk_problem[0] = None
        if not r["slsk"]["cands"]:
            _slsk_gave_up(r)
            return
        _slsk_start(r)
    elif r["state"] == "slsk_downloading":
        _slsk_watch(r)


def _slsk_gave_up(r):
    """Soulseek had nothing good: back to Lidarr (which keeps trying), or Couldn't find it"""
    r["slsk_tried"] = True
    r.pop("slsk", None)
    back = r.pop("lidarr_state", None)
    if LIDARR_URL and r.get("album_id") and back == "searching":
        r["state"] = "searching"
    else:
        r.update({"state": "not_found", "note": "Couldn't find it (Lidarr and Soulseek)" if LIDARR_URL else "Couldn't find it on Soulseek"})
        log(f"couldn't find {r['artist']} - {r['title']} on Soulseek either")


def _slsk_find(r):
    """Searches Soulseek and returns the best files to try, best first"""
    cands = _slsk_search(f"{norm(r['artist'])} {norm(r['title'])}", r)
    if not cands and len(norm(r["title"]).split()) >= 2:
        cands = _slsk_search(norm(r["title"]), r)
    return cands[:6]


def _slsk_search(text, r):
    sid = str(uuid.uuid4())
    slskd("POST", "/searches", {"id": sid, "searchText": text, "searchTimeout": 15000, "responseLimit": 100, "fileLimit": 10000})
    for _ in range(20):
        time.sleep(2)
        st = slskd("GET", f"/searches/{sid}") or {}
        if st.get("isComplete"):
            break
    responses = slskd("GET", f"/searches/{sid}/responses") or []
    try:
        slskd("DELETE", f"/searches/{sid}")
    except MusicError:
        pass
    want_title, want_artist = norm(r["title"]), norm(r["artist"])
    artist_words = [w for w in want_artist.split() if len(w) > 1 and w not in ("the", "and")] or want_artist.split()
    out, seen = [], set()
    for resp in responses:
        user = resp.get("username")
        for f in resp.get("files") or []:
            if f.get("isLocked"):
                continue
            path = str(f.get("filename") or "")
            base = re.split(r"[\\/]", path)[-1]
            ext = os.path.splitext(base)[1].lower()
            nbase, npath = norm(os.path.splitext(base)[0]), norm(path.replace("\\", " ").replace("/", " "))
            if not want_title or not all(w in nbase.split() for w in want_title.split()):
                continue
            if artist_words and not any(w in npath.split() for w in artist_words):
                continue
            if any(w in nbase and w not in want_title for w in SLSK_WORDS):
                continue
            if _alt_bits(os.path.splitext(base)[0].replace("_", " ")) - _alt_bits(_wanted_title(r)):
                continue
            length = f.get("length") or 0
            if length and r.get("seconds") and abs(length - r["seconds"]) > 6:
                continue
            rate = f.get("bitRate") or 0
            if ext == ".mp3":
                if rate >= 320:
                    q = 3
                elif rate == 0 or rate >= 256:
                    q = 1
                else:
                    continue
            elif ext in LOSSLESS or ext == ".m4a" and (f.get("bitDepth") or 0) >= 16:
                q = 2
            else:
                continue
            key = (user, path)
            if key in seen:
                continue
            seen.add(key)
            out.append(
                {
                    "user": user,
                    "file": path,
                    "size": f.get("size") or 0,
                    "q": q,
                    "free": bool(resp.get("hasFreeUploadSlot")),
                    "queue": resp.get("queueLength") or 0,
                    "speed": resp.get("uploadSpeed") or 0,
                }
            )
    out.sort(key=lambda c: (-c["q"], not c["free"], c["queue"], -c["speed"]))
    # Different people first, so one slow person doesn't hold everything up
    firsts, rest, users = [], [], set()
    for c in out:
        (rest if c["user"] in users else firsts).append(c)
        users.add(c["user"])
    return firsts + rest


def _slsk_start(r):
    sl = r["slsk"]
    while sl["i"] < len(sl["cands"]):
        c = sl["cands"][sl["i"]]
        try:
            slskd("POST", "/transfers/downloads/" + urllib.parse.quote(c["user"], safe=""), [{"filename": c["file"], "size": c["size"]}])
            sl["since"] = time.time()
            sl["moved"] = time.time()
            sl["bytes"] = 0
            r.update({"state": "slsk_downloading", "pct": 0, "queued": True})
            return
        except MusicError:
            sl["i"] += 1
    _slsk_gave_up(r)


def _slsk_next(r, why):
    sl = r["slsk"]
    c = sl["cands"][sl["i"]]
    log(f"Soulseek: {why} ({c['user']}), trying the next copy")
    _slsk_cancel(c)
    sl["i"] += 1
    _slsk_start(r)


def _slsk_cancel(c):
    try:
        mine = slskd("GET", "/transfers/downloads/" + urllib.parse.quote(c["user"], safe="")) or {}
        for d in mine.get("directories") or []:
            for f in d.get("files") or []:
                if f.get("filename") == c["file"] and f.get("id"):
                    slskd("DELETE", f"/transfers/downloads/{urllib.parse.quote(c['user'], safe='')}/{f['id']}?remove=true")
    except MusicError:
        pass


def _slsk_watch(r):
    sl = r.get("slsk") or {}
    if not sl.get("cands") or sl.get("i", 0) >= len(sl["cands"]):
        r["state"] = "slsk_search"
        return
    c = sl["cands"][sl["i"]]
    mine = slskd("GET", "/transfers/downloads/" + urllib.parse.quote(c["user"], safe="")) or {}
    t = next((f for d in mine.get("directories") or [] for f in d.get("files") or [] if f.get("filename") == c["file"]), None)
    if not t:
        _slsk_next(r, "the download disappeared")
        return
    state = str(t.get("state") or "")
    done = int(t.get("bytesTransferred") or 0)
    if done > sl.get("bytes", 0):
        sl["bytes"], sl["moved"] = done, time.time()
    r["pct"] = int(t.get("percentComplete") or 0)
    r["queued"] = "Queued" in state or state in ("Requested", "Initializing")
    if "Succeeded" in state:
        _slsk_finish(r, c)
    elif "Completed" in state:
        _slsk_next(r, f"the download stopped ({state})")
    elif r["queued"] and time.time() - sl["since"] > 15 * 60:
        _slsk_next(r, "waited 15 minutes in their queue")
    elif not r["queued"] and time.time() - sl["moved"] > 5 * 60:
        _slsk_next(r, "the download stalled")


def _slsk_file(c):
    """Where slskd saved it: its download folder / the shared folder's name / the file"""
    parts = re.split(r"[\\/]", c["file"])
    base = parts[-1]
    guess = os.path.join(SLSKD_DIR, parts[-2] if len(parts) > 1 else "", base)
    if os.path.isfile(guess):
        return guess
    found = []
    for root, dirs, files in os.walk(SLSKD_DIR):
        if root.count(os.sep) - SLSKD_DIR.count(os.sep) > 3:
            dirs[:] = []
        if base in files:
            found.append(os.path.join(root, base))
    return max(found, key=os.path.getmtime) if found else None


def _slsk_finish(r, c):
    path = _slsk_file(c)
    if not path:
        if time.time() - r["slsk"].get("since", 0) > 3600:
            _slsk_next(r, "the finished file couldn't be found in slskd's download folder")
        else:
            _slsk_problem[0] = "A Soulseek download finished but Benflix can't see slskd's download folder. Run the Benflix setup command again."
        return
    r["state"] = "tidying"
    tags = {"artist": r["artist"], "album_artist": r["artist"], "album": r.get("album") or "Singles", "title": r["title"]}
    if r.get("number"):
        tags["track"] = str(r["number"])
    if r.get("disc"):
        tags["disc"] = str(r["disc"])
    folder = os.path.dirname(path)
    made = _make_mp3(path, delete_original=True, tags=tags)
    if made:
        path = made
    else:
        _retag(path, tags)
    name = safe(r["title"])
    if r.get("number"):
        num = f"{int(r['number']):02d}"
        if r.get("disc") and int(r["disc"]) > 1:
            num = f"{int(r['disc'])}-{num}"
        name = f"{num} - {name}"
    dest = os.path.join(MUSIC, safe(r["artist"]), safe(r.get("album") or "Singles"), name + os.path.splitext(path)[1].lower())
    if os.path.exists(dest):
        _quiet_remove(path)
    else:
        _move(path, dest)
    if os.path.abspath(folder) != os.path.abspath(SLSKD_DIR):
        try:
            os.rmdir(folder)  # Only when it's empty now
        except OSError:
            pass
    _cover(os.path.dirname(dest), r.get("cover_big"))
    _tell_jellyfin([dest])
    _slsk_cancel(c)  # Takes the finished download off slskd's list
    r.update({"state": "ready", "ready_at": time.time(), "note": None, "source": "soulseek"})
    r.pop("slsk", None)
    if r.get("album_id"):
        _unmonitor(r)
    log(f"ready from Soulseek: {r['artist']} - {r['title']}")


# ---------- Files: MP3s, covers, song info ----------


def _has_ffmpeg():
    return bool(shutil.which("ffmpeg") and shutil.which("ffprobe"))


def probe(path):
    """Song info inside a file: {'artist', 'album_artist', 'album', 'title', 'track', 'disc', 'year', 'codec', 'art'}"""
    out = {}
    if not _has_ffmpeg():
        return out
    try:
        p = subprocess.run(
            ["ffprobe", "-v", "quiet", "-print_format", "json", "-show_format", "-show_streams", path],
            capture_output=True, timeout=60,
        )
        j = json.loads(p.stdout or b"{}")
    except (OSError, subprocess.SubprocessError, ValueError):
        return out
    tags = {}
    for s in j.get("streams") or []:
        if s.get("codec_type") == "audio":
            out.setdefault("codec", s.get("codec_name"))
            out.setdefault("bitrate", int(s.get("bit_rate") or 0))
            tags.update({k.lower(): v for k, v in (s.get("tags") or {}).items()})
        if (s.get("disposition") or {}).get("attached_pic"):
            out["art"] = True
    tags.update({k.lower(): v for k, v in ((j.get("format") or {}).get("tags") or {}).items()})
    if not out.get("bitrate"):
        out["bitrate"] = int((j.get("format") or {}).get("bit_rate") or 0)

    def num(v):
        m = re.match(r"\s*(\d+)", str(v or ""))
        return int(m.group(1)) if m else 0

    out.update(
        {
            "artist": (tags.get("artist") or "").strip(),
            "album_artist": (tags.get("album_artist") or tags.get("albumartist") or tags.get("album artist") or "").strip(),
            "album": (tags.get("album") or "").strip(),
            "title": (tags.get("title") or "").strip(),
            "track": num(tags.get("track") or tags.get("tracknumber")),
            "disc": num(tags.get("disc") or tags.get("discnumber")),
            "year": str(tags.get("date") or tags.get("year") or "")[:4],
        }
    )
    return out


def _make_mp3(path, delete_original=False, keep_original_to=None, tags=None):
    """Lossless file -> 320 MP3 next to it (song info and cover kept). Returns the MP3's path, or None."""
    ext = os.path.splitext(path)[1].lower()
    if ext not in LOSSLESS and not (ext == ".m4a" and probe(path).get("codec") == "alac"):
        return None
    if not _has_ffmpeg():
        return None
    out = os.path.splitext(path)[0] + ".mp3"
    tmp = out + ".part.mp3"
    cmd = ["ffmpeg", "-nostdin", "-v", "error", "-y", "-i", path, "-map", "0:a:0", "-map", "0:v?", "-c:a", "libmp3lame", "-b:a", "320k", "-c:v", "copy", "-disposition:v", "attached_pic", "-map_metadata", "0", "-id3v2_version", "3"]
    for k, v in (tags or {}).items():
        if v:
            cmd += ["-metadata", f"{k}={v}"]
    try:
        p = subprocess.run(cmd + [tmp], capture_output=True, timeout=900)
        if p.returncode != 0:
            # Some covers can't go into an MP3; try without the picture
            p = subprocess.run(_no_picture(cmd) + [tmp], capture_output=True, timeout=900)
        if p.returncode != 0 or not os.path.isfile(tmp):
            log(f"couldn't make an MP3 of {os.path.basename(path)}")
            _quiet_remove(tmp)
            return None
        os.replace(tmp, out)
        _own(out)
        if keep_original_to:
            _move(path, keep_original_to)
        elif delete_original:
            os.remove(path)
        return out
    except (OSError, subprocess.SubprocessError):
        _quiet_remove(tmp)
        return None


def _no_picture(cmd):
    out, skip = [], False
    for i, c in enumerate(cmd):
        if skip:
            skip = False
            continue
        if c == "-map" and cmd[i + 1] == "0:v?":
            skip = True
            continue
        if c in ("-c:v", "-disposition:v"):
            skip = True
            continue
        out.append(c)
    return out


def _retag(path, tags):
    """Writes missing song info into the file (a quick copy, the sound isn't touched)"""
    if not _has_ffmpeg() or not tags:
        return False
    ext = os.path.splitext(path)[1]
    tmp = path + ".part" + ext
    cmd = ["ffmpeg", "-nostdin", "-v", "error", "-y", "-i", path, "-map", "0", "-c", "copy"]
    if ext.lower() == ".mp3":
        cmd += ["-id3v2_version", "3"]
    for k, v in tags.items():
        if v:
            cmd += ["-metadata", f"{k}={v}"]
    try:
        p = subprocess.run(cmd + [tmp], capture_output=True, timeout=300)
        if p.returncode == 0 and os.path.getsize(tmp) > 0:
            os.replace(tmp, path)
            _own(path)
            return True
    except (OSError, subprocess.SubprocessError):
        pass
    _quiet_remove(tmp)
    return False


def _cover(folder, url=None, artist=None, album=None):
    """cover.jpg in the album folder (Jellyfin shows it), from Deezer when the folder has none"""
    if not folder or not os.path.isdir(folder):
        return
    for n in os.listdir(folder):
        if n.lower() in ("cover.jpg", "cover.png", "folder.jpg", "folder.png", "front.jpg"):
            return
    try:
        if not url and artist and album:
            found = deezer("/search/album", q=f'artist:"{artist}" album:"{album}"', limit=5).get("data") or []
            hit = next((a for a in found if same(a.get("title"), album)), None)
            url = (hit or {}).get("cover_xl")
        if not url:
            return
        data = _http(url, raw=True, timeout=30)
        if data and len(data) > 1000:
            path = os.path.join(folder, "cover.jpg")
            with open(path, "wb") as f:
                f.write(data)
            _own(path)
    except (MusicError, urllib.error.URLError, OSError, ValueError):
        pass


def _quiet_remove(path):
    try:
        os.remove(path)
    except OSError:
        pass


def _move(src, dst):
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    for d in _new_dirs(os.path.dirname(dst)):
        _own(d)
    shutil.move(src, dst)
    _own(dst)


def _new_dirs(path):
    out = []
    while path and path not in (MEDIA, "/") and os.path.isdir(path):
        out.append(path)
        path = os.path.dirname(path)
        if path in (MUSIC, LEFT, DROP):
            break
    return out


def _free_name(path):
    if not os.path.exists(path):
        return path
    base, ext = os.path.splitext(path)
    for i in range(2, 1000):
        p = f"{base} ({i}){ext}"
        if not os.path.exists(p):
            return p
    return f"{base} ({uuid.uuid4().hex[:6]}){ext}"


# ---------- Sorting files (drop folder and tidy-up) ----------


# Band names with a comma or "&" in them that are one act, not several
ONE_ACT = {
    "tyler, the creator", "earth, wind & fire", "crosby, stills, nash & young", "crosby, stills & nash",
    "emerson, lake & palmer", "blood, sweat & tears", "peter, paul and mary", "now, now", "black country, new road",
    "panic! at the disco", "simon & garfunkel", "hall & oates", "daryl hall & john oates", "brooks & dunn",
    "mumford & sons", "florence + the machine", "marina and the diamonds", "nico & vinz",
}


# YouTube channels of record labels, which video downloads give as the artist
LABEL_CHANNELS = {
    "hybe labels", "jyp entertainment", "smtown", "yg entertainment", "1thek", "1thek (원더케이)", "stone music entertainment",
    "mnet k-pop", "kq entertainment", "starshiptv", "the black label", "big hit labels", "ibighit", "bangtantv",
    "vevo", "trap nation", "proximity", "monstercat", "ukf dubstep", "mrsuicidesheep", "majestic casual", "trap city",
}


def _is_label(name):
    n = (name or "").strip().lower()
    return n in LABEL_CHANNELS or n.endswith("vevo") or n.endswith(" - topic")


def main_artist(name):
    """The first artist of 'Zedd, Maren Morris & Grey' or 'A feat. B', so a song goes under one artist folder"""
    n = (name or "").strip()
    if not n or n.lower() in ONE_ACT:
        return n
    n = re.split(r"\s+(?:feat\.?|ft\.?|featuring|with|x|vs\.?)\s+", n, maxsplit=1, flags=re.I)[0].strip()
    if n.lower() in ONE_ACT:
        return n
    for act in ONE_ACT:
        if n.lower().startswith(act + ",") or n.lower().startswith(act + " &"):
            return n[: len(act)]
    if "," in n:
        parts = [x.strip() for x in n.split(",") if x.strip() and not _is_label(x)]
        n = parts[0] if parts else n.split(",", 1)[0].strip()
    elif re.search(r"\s+[&+]\s+", n) and n.count("&") + n.count("+") >= 2:
        # "A & B & C" is several people; one "&" is usually a duo name ("gianni & kyle")
        n = re.split(r"\s+[&+]\s+", n, maxsplit=1)[0].strip()
    return n or (name or "").strip()


def clean_title(title, artist=""):
    """'Ke$ha - Die Young (Lyric Video)' -> 'Die Young' (bits left in names by video downloads)"""
    t = (title or "").strip()
    t = re.sub(
        r"\s*[\(\[][^\)\]]*\b(official|lyrics?|audio|video|visuali[sz]er|hd|hq|4k|mv|m/v|music video)\b[^\)\]]*[\)\]]",
        "", t, flags=re.I,
    ).strip()
    t = re.sub(r"\s+(official\s+)?(mv|m/v)$", "", t, flags=re.I).strip()
    t = re.sub(r"\s+(official\s+(music\s+)?video|official\s+audio|official\s+visuali[sz]er|music\s+video|lyric\s+video)$", "", t, flags=re.I).strip()
    if artist and " - " in t:
        first, rest = t.split(" - ", 1)
        a, f = norm(artist.replace("$", "s")).replace(" ", ""), norm(first.replace("$", "s")).replace(" ", "")
        if same(first, artist) or same(first, main_artist(artist)) or (f and f == a):
            t = rest.strip()
    # K-pop video names: "CORTIS (코르티스) 'FaSHioN'" or "'DRIP'"
    m = re.match(r"^(.*?)\s*['‘’\"“]([^'‘’\"“”]+?(?:\([^)]*\))?[^'‘’\"“”]*)['‘’\"”]\s*$", t)
    if m:
        before = norm(m.group(1).replace("$", "s"))
        if not before or (artist and (before.startswith(norm(main_artist(artist)).strip() or "\0") or before.startswith(norm(artist).strip() or "\0"))):
            t = m.group(2).strip()
    return t or (title or "").strip()


def _same_song_there(dest):
    """Another copy of the same song in the album folder, maybe with or without a track number or another ending"""
    folder = os.path.dirname(dest)
    if not os.path.isdir(folder):
        return None
    want = norm(re.sub(r"^\d{1,2}-?\d{0,3}\s+-\s+", "", os.path.splitext(os.path.basename(dest))[0]))
    for n in os.listdir(folder):
        if os.path.splitext(n)[1].lower() not in AUDIO:
            continue
        stem = norm(re.sub(r"^\d{1,2}-?\d{0,3}\s+-\s+", "", os.path.splitext(n)[0]))
        if stem and stem == want:
            return os.path.join(folder, n)
    return None


def _guess_from_names(path, root):
    """Song info from the file and folder names, like 'Artist/Album/03 - Song.mp3' or 'Artist - Song.mp3'"""
    rel = os.path.relpath(path, root).replace("\\", "/").split("/")
    stem = os.path.splitext(rel[-1])[0]
    stem = re.sub(r"\s*[\(\[](official|lyrics?|audio|video|hd|hq|4k|visualizer)[^\)\]]*[\)\]]", "", stem, flags=re.I).strip()
    g = {"track": 0}
    m = re.match(r"^(\d{1,2})-(\d{1,3})[\s._-]+(.*)$", stem)
    if m:
        g["disc"], g["track"], stem = int(m.group(1)), int(m.group(2)), m.group(3)
    else:
        m = re.match(r"^(\d{1,3})[\s._-]+(.*)$", stem)
        if m and len(m.group(2)) > 1:
            g["track"], stem = int(m.group(1)), m.group(2)
    parts = [p.strip() for p in re.split(r"\s+-\s+", stem) if p.strip()]
    if len(parts) >= 3 and not g["track"]:
        g["artist"], g["album"], g["title"] = parts[0], parts[1], " - ".join(parts[2:])
    elif len(parts) >= 2:
        g["artist"], g["title"] = parts[0], " - ".join(parts[1:])
    else:
        g["title"] = stem
    folders = rel[:-1]
    if folders:
        last = folders[-1]
        m = re.match(r"^(.+?)\s+-\s+(.+?)(\s*[\(\[]\d{4}[\)\]])?$", last)
        if m:
            g.setdefault("artist", m.group(1))
            g.setdefault("album", m.group(2))
        elif len(folders) >= 2:
            g.setdefault("album", re.sub(r"\s*[\(\[]\d{4}[\)\]]$", "", last))
            g.setdefault("artist", folders[-2])
        else:
            g.setdefault("album", re.sub(r"\s*[\(\[]\d{4}[\)\]]$", "", last))
    return g


def _plan_one(path, root, look_up=True):
    """Where one file should go. Returns (info, destination) or (info, None) when it can't be worked out."""
    tags = probe(path)
    names = _guess_from_names(path, root)
    full_artist = tags.get("album_artist") or tags.get("artist") or names.get("artist") or ""
    song_artist = tags.get("artist") or names.get("artist") or ""
    raw_title = tags.get("title") or names.get("title") or ""
    info = {
        "artist": main_artist(full_artist),
        "full_artist": full_artist,
        "song_artist": song_artist,
        "album": tags.get("album") or names.get("album") or "",
        "title": clean_title(raw_title, song_artist or full_artist),
        "title_changed": False,
        "track": tags.get("track") or names.get("track") or 0,
        "disc": tags.get("disc") or names.get("disc") or 0,
        "year": tags.get("year") or "",
        "art": tags.get("art"),
        "bitrate": tags.get("bitrate") or 0,
        "had_tags": bool(tags.get("title") and tags.get("artist") and tags.get("album")),
    }
    info["title_changed"] = bool(raw_title) and info["title"] != raw_title
    if _is_label(info["artist"]) and look_up and info["title"]:
        # "Stray Kids CEREMONY" from the JYP channel: the catalogue says who it really is
        try:
            text = norm(info["title"])
            for t in deezer("/search", q=info["title"], limit=8).get("data") or []:
                who = (t.get("artist") or {}).get("name") or ""
                if who and norm(who) in text and norm(t.get("title")) in text:
                    info.update({"artist": who, "full_artist": who, "song_artist": who, "title": t.get("title"), "album": "", "title_changed": True})
                    break
        except MusicError:
            pass
    # Video downloads often put the artist or channel name in as the album (a self-titled album stays if the catalogue agrees)
    odd_album = bool(info["album"]) and (same(info["album"], info["artist"]) or same(info["album"], info["full_artist"]) or _is_label(info["album"]))
    if info["artist"] and info["title"] and look_up and (not info["album"] or not tags.get("album") or odd_album):
        try:
            found = deezer("/search", q=f'artist:"{info["artist"]}" track:"{info["title"]}"', limit=5).get("data") or []
            hit = next((t for t in found if same(t.get("title"), info["title"])), None)
            if odd_album and hit and (hit.get("album") or {}).get("title") and not same((hit.get("album") or {}).get("title"), info["album"]):
                info["album"] = ""
            elif odd_album and not hit and (info["title_changed"] or any(_is_label(x) for x in info["album"].split(","))):
                # A video download with no album: it goes in Singles
                info["album"] = ""
            else:
                odd_album = False
            if hit:
                al = hit.get("album") or {}
                info["album"] = info["album"] or al.get("title") or ""
                info["cover"] = al.get("cover_xl")
                if not info["track"] and same(al.get("title"), info["album"]):
                    info["track"] = hit.get("track_position") or 0
        except MusicError:
            pass
    # The song info needs writing when the folder artist, title or album was fixed up
    info["fix_tags"] = (
        not info["had_tags"]
        or info["title_changed"]
        or odd_album
        or (tags.get("album_artist") or "") != info["artist"] and info["artist"] != info["full_artist"]
    )
    if not info["artist"] or not info["title"]:
        return info, None
    album = info["album"] or "Singles"
    ext = os.path.splitext(path)[1].lower()
    name = safe(info["title"])
    if info["track"]:
        num = f"{int(info['track']):02d}"
        if info["disc"] and int(info["disc"]) > 1:
            num = f"{int(info['disc'])}-{num}"
        name = f"{num} - {name}"
    return info, os.path.join(MUSIC, safe(info["artist"]), safe(album), name + ext)


def _audio_files(folder):
    out = []
    for base, dirs, files in os.walk(folder):
        dirs[:] = [d for d in dirs if not d.startswith(".")]
        for f in files:
            if os.path.splitext(f)[1].lower() in AUDIO and (not f.startswith(".") or f.startswith("..")):
                out.append(os.path.join(base, f))
    return sorted(out)


def sort_files(files, root, mode, say, convert=False, look_up=True):
    """Sorts files into Music. mode 'preview' only says what would happen. Returns (moves for undo, counts)."""
    undo, counts, told, covers = [], {"moved": 0, "same": 0, "dupes": 0, "cant": 0, "mp3": 0}, [], {}
    for path in files:
        if not os.path.isfile(path):
            continue
        info, dest = _plan_one(path, root, look_up)
        rel = os.path.relpath(path, root)
        if root == MUSIC and len(rel.split(os.sep)) == 3 and info.get("had_tags") and not info.get("fix_tags") and rel.split(os.sep)[0] == safe(info["artist"]):
            # Already in Music/Artist/Album/ with its song info (like Lidarr's own files): left alone
            counts["same"] += 1
            continue
        if not dest:
            counts["cant"] += 1
            say(f"Couldn't work out: {rel}")
            if mode != "preview":
                to = _free_name(os.path.join(LEFT, "Couldn't sort", os.path.basename(path)))
                _move(path, to)
                undo.append(["move", path, to])
            continue
        if convert and os.path.splitext(path)[1].lower() in LOSSLESS | {".m4a"}:
            mp3_dest = os.path.splitext(dest)[0] + ".mp3"
            if os.path.splitext(path)[1].lower() in LOSSLESS or probe(path).get("codec") == "alac":
                dest = mp3_dest
        if os.path.abspath(path) == os.path.abspath(dest):
            counts["same"] += 1
            continue
        twin = dest if os.path.exists(dest) else _same_song_there(dest)
        if twin and os.path.abspath(twin) != os.path.abspath(path):
            dest = twin
        if os.path.exists(dest):
            # The same song twice: keep the bigger (better) copy, put the other one in Leftovers
            counts["dupes"] += 1
            mine, theirs = os.path.getsize(path), os.path.getsize(dest)
            better_new = os.path.splitext(path)[1].lower() == os.path.splitext(dest)[1].lower() and mine > theirs * 1.05
            say(f"Same song twice: {os.path.relpath(dest, MUSIC)} (keeping the {'new' if better_new else 'one already there'})")
            if mode != "preview":
                spare = os.path.join(LEFT, "Duplicates", os.path.relpath(dest, MUSIC))
                if better_new:
                    to = _free_name(spare)
                    _move(dest, to)
                    undo.append(["move", dest, to])
                    _move(path, dest)
                    undo.append(["move", path, dest])
                    told.append(dest)
                else:
                    to = _free_name(os.path.join(os.path.dirname(spare), os.path.basename(path)))
                    _move(path, to)
                    undo.append(["move", path, to])
            continue
        counts["moved"] += 1
        say(f"{rel}  ->  {os.path.relpath(dest, MUSIC)}")
        if mode == "preview":
            continue
        fix = {}
        if info.get("fix_tags"):
            fix = {"artist": info["song_artist"] or info["artist"], "album_artist": info["artist"], "album": info["album"] or "Singles", "title": info["title"]}
            if info["track"]:
                fix["track"] = str(info["track"])
        if dest.endswith(".mp3") and not path.lower().endswith(".mp3"):
            kept = _free_name(os.path.join(LEFT, "Lossless originals", safe(info["artist"]), os.path.basename(path)))
            made = _make_mp3(path, keep_original_to=kept, tags=fix)
            if made:
                counts["mp3"] += 1
                _move(made, dest)
                undo.append(["made", None, dest])
                undo.append(["move", path, kept])
            else:
                dest = os.path.splitext(dest)[0] + os.path.splitext(path)[1]
                _move(path, _free_name(dest))
                undo.append(["move", path, dest])
        else:
            _move(path, dest)
            undo.append(["move", path, dest])
            if fix:
                _retag(dest, fix)
        told.append(dest)
        _bring_extras(path, dest, undo)
        folder = os.path.dirname(dest)
        if folder not in covers and not info.get("art"):
            covers[folder] = True
            _cover(folder, info.get("cover"), info["artist"], info["album"])
    if told:
        _tell_jellyfin(told)
    return undo, counts


def _clear_loose(undo, say):
    """Lyrics and info files whose song moved away, SoulSync's half-finished files, and covers left in an artist
    folder that now has album folders: all go to Music Leftovers/Old extras (nothing is deleted)"""
    count = 0
    for base, dirs, files in os.walk(MUSIC):
        audio = {os.path.splitext(f)[0].lower() for f in files if os.path.splitext(f)[1].lower() in AUDIO}
        top = os.path.dirname(base) == MUSIC
        for f in files:
            low, stem = f.lower(), os.path.splitext(f)[0].lower()
            loose = (
                low.endswith(".ssync-tmp")
                or (low.endswith((".nfo", ".lrc")) and stem not in audio)
                or (top and dirs and not audio and low in ("cover.jpg", "folder.jpg", "cover.png", "folder.png"))
            )
            if loose:
                src = os.path.join(base, f)
                to = _free_name(os.path.join(LEFT, "Old extras", os.path.relpath(src, MUSIC)))
                _move(src, to)
                undo.append(["move", src, to])
                count += 1
    if count:
        say(f"Put {count} leftover lyrics, info and half-downloaded files in Music Leftovers/Old extras.")


def _bring_extras(src, dest, undo):
    """Lyrics (.lrc) next to the song, and a cover picture from the old folder"""
    stem = os.path.splitext(src)[0]
    for ext in (".lrc", ".LRC"):
        if os.path.isfile(stem + ext):
            to = os.path.splitext(dest)[0] + ".lrc"
            if not os.path.exists(to):
                _move(stem + ext, to)
                undo.append(["move", stem + ext, to])
    old, new = os.path.dirname(src), os.path.dirname(dest)
    for n in ("cover.jpg", "folder.jpg", "cover.png", "folder.png"):
        if os.path.isfile(os.path.join(old, n)) and not os.path.exists(os.path.join(new, n)):
            try:
                shutil.copy2(os.path.join(old, n), os.path.join(new, n))
                _own(os.path.join(new, n))
            except OSError:
                pass
            break


def _remove_empty(folder, keep_top=True):
    """Folders the music has gone from (only pictures, lists or nothing left) are taken away"""
    junk_ends = (".jpg", ".jpeg", ".png", ".nfo", ".m3u", ".m3u8", ".txt", ".log", ".cue", ".sfv", ".db", ".ini")
    for base, dirs, files in os.walk(folder, topdown=False):
        if keep_top and base == folder:
            continue
        try:
            left = os.listdir(base)
        except OSError:
            continue
        if any(os.path.isdir(os.path.join(base, n)) for n in left):
            continue
        if all(n.lower().endswith(junk_ends) or n.lower() == ".ds_store" for n in left):
            for n in left:
                _quiet_remove(os.path.join(base, n))
            try:
                os.rmdir(base)
            except OSError:
                pass


# ---------- Drop folder ----------


def _drop_say(text):
    with _lock:
        _drop["lines"].append(time.strftime("%d %b %H:%M  ") + text)
        del _drop["lines"][:-100]


def _drop_worker():
    time.sleep(30)
    while True:
        try:
            _drop_tick()
        except Exception as e:
            log(f"drop folder problem: {e!r}")
        time.sleep(60)


def _drop_tick():
    if not os.path.isdir(DROP) or _tidy["running"]:
        return
    ready, sizes = [], {}
    now = time.time()
    for f in _audio_files(DROP):
        try:
            st = os.stat(f)
        except OSError:
            continue
        sizes[f] = st.st_size
        # Only files that have finished copying (same size as a minute ago, not changed for 2 minutes)
        if _drop["sizes"].get(f) == st.st_size and now - st.st_mtime > 120:
            ready.append(f)
    _drop["sizes"] = sizes
    if not ready:
        return
    undo, counts = sort_files(ready, DROP, "apply", _drop_say, convert=True)
    _remove_empty(DROP)
    _drop["last"] = int(time.time())
    if undo:
        _write(f"drop-{time.strftime('%Y%m%d-%H%M%S')}.json", undo)
    log(f"drop folder: sorted {counts['moved']}, {counts['dupes']} twice, {counts['cant']} couldn't work out")


# ---------- Tidy-up (a whole folder, like SoulSync's music) ----------


def _tidy_folders():
    out = []
    if os.path.isdir(MEDIA):
        for n in sorted(os.listdir(MEDIA)):
            p = os.path.join(MEDIA, n)
            if os.path.isdir(p) and not n.startswith(".") and n not in ("Music Leftovers",):
                low = n.lower()
                if "music" in low or "soulsync" in low or "audio" in low or "songs" in low or "download" in low:
                    out.append(n)
    if "Music" not in out and os.path.isdir(MUSIC):
        out.insert(0, "Music")
    return out


def tidy_status():
    with _lock:
        undo = sorted(n for n in os.listdir(_dir) if n.startswith("tidy-") and n.endswith(".json")) if _dir else []
        return {
            "running": _tidy["running"],
            "mode": _tidy["mode"],
            "folder": _tidy["folder"],
            "lines": list(_tidy["lines"][-300:]),
            "result": _tidy["result"],
            "folders": _tidy_folders(),
            "undo_files": undo[-5:],
            "ffmpeg": _has_ffmpeg(),
        }


def tidy_start(mode, folder, undo_file):
    if mode not in ("preview", "apply", "undo"):
        raise MusicError(400, "Pick Check first, Tidy it or Undo.")
    with _lock:
        if _tidy["running"]:
            raise MusicError(409, "The tidy-up is already running.")
        if mode == "undo":
            if not re.fullmatch(r"(tidy|drop)-[\d-]+\.json", undo_file or "") or not os.path.isfile(os.path.join(_dir, undo_file)):
                raise MusicError(400, "Pick a tidy-up to undo.")
        else:
            if folder not in _tidy_folders():
                raise MusicError(400, "Pick a folder to tidy.")
        _tidy.update({"running": True, "mode": mode, "folder": folder or undo_file, "lines": [], "result": None})
    threading.Thread(target=_tidy_run, args=(mode, folder, undo_file), name="music-tidy", daemon=True).start()
    return tidy_status()


def _tidy_say(text):
    with _lock:
        _tidy["lines"].append(text)
        del _tidy["lines"][:-2000]


def _tidy_run(mode, folder, undo_file):
    try:
        if mode == "undo":
            moves = _read(undo_file) or []
            back = 0
            for kind, src, dst in reversed(moves):
                if kind == "made":
                    continue
                if os.path.exists(dst) and not os.path.exists(src):
                    _move(dst, src)
                    back += 1
            for kind, src, dst in moves:
                if kind == "made" and os.path.exists(dst):
                    _quiet_remove(dst)
            # Lossless originals kept in Leftovers go back where they were too (they're "move" lines)
            _remove_empty(MUSIC)
            os.rename(os.path.join(_dir, undo_file), os.path.join(_dir, "undone-" + undo_file))
            _tidy["result"] = f"Put {back} files back where they were."
            return
        root = os.path.join(MEDIA, folder)
        files = _audio_files(root)
        _tidy_say(f"Found {len(files)} music files in {folder}.")
        undo, counts = sort_files(files, root, mode, _tidy_say, convert=False)
        if mode == "apply":
            _clear_loose(undo, _tidy_say)
            if root != MUSIC:
                _remove_empty(root)
            _remove_empty(MUSIC)
            if undo:
                _write(f"tidy-{time.strftime('%Y%m%d-%H%M%S')}.json", undo)
        verb = "Would move" if mode == "preview" else "Moved"
        _tidy["result"] = (
            f"{verb} {counts['moved']} files, {counts['same']} already in the right place, "
            f"{counts['dupes']} songs twice, {counts['cant']} couldn't be worked out."
        )
    except Exception as e:
        _tidy["result"] = f"Stopped with a problem: {e}"
        log(f"tidy problem: {e!r}")
    finally:
        _tidy["running"] = False


# ---------- Playlists ----------


def _clean_youtube_title(title, channel):
    t = re.sub(r"\s*[\(\[][^\)\]]*(official|lyric|audio|video|visuali[sz]er|hd|hq|4k|remaster|explicit)[^\)\]]*[\)\]]", "", title, flags=re.I)
    t = re.sub(r"\s*\|.*$", "", t).strip()
    ch = re.sub(r"\s*-\s*Topic$", "", channel or "").replace("VEVO", "").strip()
    if " - " in t:
        a, s = t.split(" - ", 1)
        return a.strip(), s.strip()
    return ch, t


def _read_playlist(link):
    """Returns (name, [(artist, song) or ('deezer', id)])"""
    link = link.strip()
    m = re.search(r"open\.spotify\.com/(?:intl-\w+/)?(?:embed/)?playlist/([A-Za-z0-9]+)", link)
    if m:
        return _spotify(m.group(1))
    m = re.search(r"deezer\.com/(?:\w+/)?playlist/(\d+)", link)
    if m:
        p = deezer(f"/playlist/{m.group(1)}")
        tracks, nxt = (p.get("tracks") or {}).get("data") or [], (p.get("tracks") or {}).get("next")
        while nxt and len(tracks) < 2000:
            page = _http(nxt)
            tracks += page.get("data") or []
            nxt = page.get("next")
        return p.get("title") or "Deezer playlist", [("deezer", t["id"]) for t in tracks if t.get("id")]
    m = re.search(r"[?&]list=([\w-]+)", link)
    if m and ("youtube.com" in link or "youtu.be" in link):
        # YouTube Music's own list first (real song and artist names, every song), then the normal YouTube page
        try:
            got = _youtube_music(m.group(1))
            if got[1]:
                return got
        except Exception as e:
            log(f"YouTube Music playlist read failed, trying the YouTube page: {e!r}")
        return _youtube(m.group(1))
    if re.match(r"https?://", link):
        raise MusicError(400, "That link isn't a Spotify, YouTube or Deezer playlist. You can also paste the songs as lines like 'Artist - Song'.")
    songs = []
    for line in link.splitlines():
        line = re.sub(r"^\s*\d+[.)]\s*", "", line).strip()
        if not line:
            continue
        m = re.match(r"^(.+?)\s+by\s+(.+)$", line, flags=re.I)
        if " - " in line:
            a, s = line.split(" - ", 1)
            songs.append((a.strip(), s.strip()))
        elif " – " in line:
            a, s = line.split(" – ", 1)
            songs.append((a.strip(), s.strip()))
        elif m:
            songs.append((m.group(2).strip(), m.group(1).strip()))
        else:
            songs.append(("", line))
    if not songs:
        raise MusicError(400, "Paste a playlist link, or songs as lines like 'Artist - Song'.")
    return "", songs


def _spotify(pid):
    cid, secret = os.environ.get("SPOTIFY_ID"), os.environ.get("SPOTIFY_SECRET")
    if cid and secret:
        try:
            tok = _http(
                "https://accounts.spotify.com/api/token", "POST", b"grant_type=client_credentials",
                {"Authorization": "Basic " + base64.b64encode(f"{cid}:{secret}".encode()).decode(), "Content-Type": "application/x-www-form-urlencoded"},
            )["access_token"]
            h = {"Authorization": "Bearer " + tok}
            info = _http(f"https://api.spotify.com/v1/playlists/{pid}?fields=name", headers=h)
            url, songs = f"https://api.spotify.com/v1/playlists/{pid}/tracks?limit=100&fields=next,items(track(name,artists(name)))", []
            while url and len(songs) < 2000:
                page = _http(url, headers=h)
                for it in page.get("items") or []:
                    t = it.get("track") or {}
                    if t.get("name"):
                        songs.append(((t.get("artists") or [{}])[0].get("name") or "", t["name"]))
                url = page.get("next")
            return info.get("name") or "Spotify playlist", songs
        except (urllib.error.URLError, OSError, ValueError, KeyError):
            pass
    try:
        page = _http(f"https://open.spotify.com/embed/playlist/{pid}", headers={"User-Agent": UA, "Accept": "text/html"}, raw=True).decode("utf-8", "replace")
    except (urllib.error.URLError, OSError):
        raise MusicError(502, "Couldn't read that Spotify playlist. Is it public?")
    m = re.search(r'<script id="__NEXT_DATA__" type="application/json">(.*?)</script>', page, flags=re.S)
    try:
        entity = json.loads(m.group(1))["props"]["pageProps"]["state"]["data"]["entity"]
    except (AttributeError, ValueError, KeyError, TypeError):
        raise MusicError(502, "Couldn't read that Spotify playlist. Is it public? You can also paste the songs as lines like 'Artist - Song'.")
    songs = [((t.get("subtitle") or "").split(",")[0].strip(), t.get("title") or "") for t in entity.get("trackList") or [] if t.get("title")]
    return entity.get("name") or entity.get("title") or "Spotify playlist", songs


YTM_BROWSE = "https://music.youtube.com/youtubei/v1/browse?prettyPrint=false"
YTM_CLIENT = {"clientName": "WEB_REMIX", "clientVersion": "1.20241106.01.00", "hl": "en", "gl": "US"}


def _runs(x):
    return "".join(r.get("text", "") for r in ((x or {}).get("runs") or []))


def _youtube_music(list_id):
    """Reads a playlist the way music.youtube.com does: song name + artist per row, 100 rows a page"""
    browse = list_id if list_id.startswith("VL") else "VL" + list_id
    headers = {"User-Agent": UA, "Origin": "https://music.youtube.com", "Referer": "https://music.youtube.com/", "Cookie": "CONSENT=YES+1; SOCS=CAI"}
    body = {"context": {"client": YTM_CLIENT}, "browseId": browse}
    songs, name, seen_tokens = [], "", set()

    for _ in range(40):  # up to about 4000 songs
        data = _http(YTM_BROWSE, method="POST", body=body, headers=headers, timeout=30) or {}
        token = None

        def walk(x):
            nonlocal name, token
            if isinstance(x, dict):
                row = x.get("musicResponsiveListItemRenderer")
                if row:
                    cols = [((c.get("musicResponsiveListItemFlexColumnRenderer") or {}).get("text")) for c in row.get("flexColumns") or []]
                    title = _runs(cols[0]) if cols else ""
                    artist = ""
                    if len(cols) > 1:
                        runs = (cols[1] or {}).get("runs") or []
                        named = [r.get("text", "") for r in runs if r.get("navigationEndpoint")]
                        artist = ", ".join(named) if named else _runs(cols[1]).split(" • ")[0]
                    if title:
                        a, t = _clean_youtube_title(title, artist)
                        songs.append((a or artist, t))
                    return
                for key in ("musicResponsiveHeaderRenderer", "musicDetailHeaderRenderer", "musicEditablePlaylistDetailHeaderRenderer"):
                    if not name and isinstance(x.get(key), dict) and x[key].get("title"):
                        name = _runs(x[key]["title"])
                c = (x.get("continuationCommand") or {}).get("token") or (x.get("nextContinuationData") or {}).get("continuation")
                if c and not token:
                    token = c
                for v in x.values():
                    walk(v)
            elif isinstance(x, list):
                for v in x:
                    walk(v)

        walk(data)
        if not token or token in seen_tokens:
            break
        seen_tokens.add(token)
        body = {"context": {"client": YTM_CLIENT}, "continuation": token}
    return name or "YouTube Music playlist", songs


def _youtube(list_id):
    try:
        page = _http(
            f"https://www.youtube.com/playlist?list={list_id}&hl=en",
            headers={"User-Agent": UA, "Accept": "text/html", "Accept-Language": "en", "Cookie": "CONSENT=YES+1; SOCS=CAI"},
            raw=True,
        ).decode("utf-8", "replace")
    except (urllib.error.URLError, OSError):
        raise MusicError(502, "Couldn't read that YouTube playlist. Is it public?")
    m = re.search(r"var ytInitialData\s*=\s*(\{.*?\});\s*</script>", page, flags=re.S)
    try:
        data = json.loads(m.group(1))
    except (AttributeError, ValueError):
        raise MusicError(502, "Couldn't read that YouTube playlist. You can also paste the songs as lines like 'Artist - Song'.")
    songs, name = [], ""

    def walk(x):
        nonlocal name
        if isinstance(x, dict):
            lk = x.get("lockupViewModel")
            if isinstance(lk, dict):
                meta = ((lk.get("metadata") or {}).get("lockupMetadataViewModel") or {})
                title = ((meta.get("title") or {}).get("content")) or ""
                ch = ""
                for row in (((meta.get("metadata") or {}).get("contentMetadataViewModel") or {}).get("metadataRows") or []):
                    for part in row.get("metadataParts") or []:
                        ch = ch or ((part.get("text") or {}).get("content") or "")
                if title:
                    songs.append(_clean_youtube_title(title, ch))
                return
            v = x.get("playlistVideoRenderer")
            if v:
                title = "".join(r.get("text", "") for r in (v.get("title") or {}).get("runs") or [])
                ch = "".join(r.get("text", "") for r in (v.get("shortBylineText") or {}).get("runs") or [])
                if title:
                    songs.append(_clean_youtube_title(title, ch))
                return
            if not name and x.get("playlistHeaderRenderer"):
                name = ((x["playlistHeaderRenderer"].get("title") or {}).get("simpleText")) or ""
            if not name and isinstance(x.get("pageHeaderViewModel"), dict):
                name = ((x["pageHeaderViewModel"].get("title") or {}).get("dynamicTextViewModel") or {}).get("text", {}).get("content", "")
            for v in x.values():
                walk(v)
        elif isinstance(x, list):
            for v in x:
                walk(v)

    walk(data)
    return name or "YouTube playlist", songs


def playlist_import(link, name):
    if not link.strip():
        raise MusicError(400, "Paste a playlist link first.")
    found_name, songs = _read_playlist(link)
    if not songs:
        raise MusicError(400, "That playlist looks empty.")
    p = {
        "id": uuid.uuid4().hex[:12],
        "name": (name or found_name or "Imported playlist").strip()[:80],
        "created": time.time(),
        "state": "reading",
        "songs": [{"artist": a, "title": s} if a != "deezer" else {"deezer": s} for a, s in songs[:1000]],
    }
    with _lock:
        _state["playlists"].insert(0, p)
        _save()
    threading.Thread(target=_playlist_read, args=(p,), name="music-playlist", daemon=True).start()
    log(f"bringing in playlist {p['name']} ({len(p['songs'])} songs)")
    return _playlist_public(p)


def _playlist_read(p):
    """Matches every song to the catalogue, keeps ones already on Benflix, asks for the rest"""
    for s in p["songs"]:
        try:
            if s.get("deezer"):
                t = deezer(f"/track/{s['deezer']}")
            else:
                q = f'artist:"{s["artist"]}" track:"{s["title"]}"' if s.get("artist") else s["title"]
                found = deezer("/search", q=q, limit=5).get("data") or []
                if not found and s.get("artist"):
                    found = deezer("/search", q=f"{s['artist']} {s['title']}", limit=5).get("data") or []
                t = next((x for x in found if same(x.get("title"), s["title"])), found[0] if found else None)
            if not t:
                s["state"] = "not_found"
                continue
            s.update({"deezer": t["id"], "artist": (t.get("artist") or {}).get("name") or s.get("artist"), "title": t.get("title_short") or t.get("title")})
            here = _jellyfin_song(s["artist"], s["title"])
            if here:
                s.update({"state": "here", "jf": here})
                continue
            r = ask("song", t["id"], playlist=p["id"], quiet=True)
            s.update({"state": "asked", "req": r["id"]})
        except MusicError:
            s["state"] = "not_found"
        except Exception as e:
            s["state"] = "not_found"
            log(f"playlist song problem: {e!r}")
    p["state"] = "waiting"
    with _lock:
        _save()
    _kick.set()


def _jellyfin_song(artist, title):
    """The Jellyfin id of this song when it's already on Benflix"""
    user = _state["admin"].get("user")
    if not user or not title:
        return None
    q = urllib.parse.urlencode({"searchTerm": title, "IncludeItemTypes": "Audio", "Recursive": "true", "Limit": 20, "userId": user, "Fields": "ArtistItems"})
    j = _jellyfin("GET", f"/Items?{q}") or {}
    for it in j.get("Items") or []:
        names = [it.get("AlbumArtist") or ""] + [a.get("Name", "") for a in it.get("ArtistItems") or []] + list(it.get("Artists") or [])
        if same(it.get("Name"), title) and (not artist or any(same(n, artist) for n in names)):
            return it.get("Id")
    return None


def _playlists_tick():
    with _lock:
        pls = [p for p in _state["playlists"] if p.get("state") == "waiting"]
        by_id = {r["id"]: r for r in _state["requests"]}
    changed = False
    for p in pls:
        new = []
        for s in p["songs"]:
            if s.get("state") == "asked":
                r = by_id.get(s.get("req"))
                if not r or r["state"] in ("not_found", "problem"):
                    s["state"] = "not_found"
                    changed = True
                elif r["state"] == "ready":
                    jf = _jellyfin_song(s.get("artist"), s.get("title"))
                    if jf:
                        s.update({"state": "here", "jf": jf})
                        changed = True
            if s.get("state") == "here" and s.get("jf") and not s.get("added"):
                new.append(s)
        if new:
            changed = True
            _playlist_add(p, new)
        if all(s.get("state") in ("here", "not_found") for s in p["songs"]) and all(s.get("added") or s.get("state") != "here" for s in p["songs"]):
            _playlist_order(p)
            p["state"] = "done"
            changed = True
            log(f"playlist {p['name']} is done")
    if changed:
        with _lock:
            _save()


def _playlist_add(p, songs):
    user = _state["admin"].get("user")
    ids = [s["jf"] for s in songs]
    if not p.get("jf"):
        made = _jellyfin("POST", "/Playlists", {"Name": p["name"], "Ids": ids, "UserId": user, "MediaType": "Audio"})
        if made and made.get("Id"):
            p["jf"] = made["Id"]
        else:
            return
    else:
        q = urllib.parse.urlencode({"ids": ",".join(ids), "userId": user})
        if _jellyfin("POST", f"/Playlists/{p['jf']}/Items?{q}") is None:
            return
    for s in songs:
        s["added"] = True


def _playlist_order(p):
    """Puts the playlist in the same order as the original"""
    if not p.get("jf"):
        return
    user = _state["admin"].get("user")
    j = _jellyfin("GET", f"/Playlists/{p['jf']}/Items?userId={user}") or {}
    entry = {}
    for it in j.get("Items") or []:
        entry.setdefault(it.get("Id"), it.get("PlaylistItemId"))
    want = [s["jf"] for s in p["songs"] if s.get("jf") in entry]
    for i, jf in enumerate(want):
        _jellyfin("POST", f"/Playlists/{p['jf']}/Items/{entry[jf]}/Move/{i}")


def _playlist_public(p):
    songs = p.get("songs") or []
    here = sum(1 for s in songs if s.get("state") == "here")
    missing = sum(1 for s in songs if s.get("state") == "not_found")
    if p.get("state") == "reading":
        words = f"Reading the songs ({sum(1 for s in songs if s.get('state'))} of {len(songs)})"
    elif p.get("state") == "done":
        words = f"Ready in Jellyfin: {here} songs" + (f", {missing} couldn't be found" if missing else "")
    else:
        words = f"{here} of {len(songs)} songs here" + (f", {missing} couldn't be found" if missing else "")
    return {"id": p["id"], "name": p.get("name") or "", "words": words, "bar": round(here / len(songs), 3) if songs else 0, "done": p.get("state") == "done", "songs": len(songs), "here": here, "missing": missing}


def playlist_remove(pid):
    with _lock:
        p = next((x for x in _state["playlists"] if x["id"] == pid), None)
        if not p:
            raise MusicError(404, "That playlist isn't there any more.")
        _state["playlists"].remove(p)
        _save()
    return {"ok": True}


# ---------- Made for you (about 6 mixes per person, new every morning) ----------

_mixes = {}  # user id -> (day, answer)
_mix_lock = threading.Lock()
SONG_FIELDS = "Fields=Genres,DateCreated&EnableImages=false&EnableUserData=true"


def _as_user(token, path):
    """A Jellyfin call made with the person's own sign-in, so their plays and likes are theirs"""
    if not _jf:
        return None
    try:
        status, data, _ = _jf("GET", path, token=token, device_id="benflix-music")
    except Exception:
        return None
    if status >= 300:
        return None
    try:
        return json.loads(data) if data and data.strip() else {}
    except ValueError:
        return None


def _songs(token, user_id, extra):
    j = _as_user(token, f"/Users/{user_id}/Items?IncludeItemTypes=Audio&Recursive=true&{SONG_FIELDS}&{extra}") or {}
    return [s for s in (j.get("Items") or []) if s.get("Id")]


def _mix_artist(song):
    a = song.get("AlbumArtists") or song.get("ArtistItems") or []
    return (a[0].get("Name"), a[0].get("Id")) if a else (song.get("AlbumArtist"), None)


def _mix(key, name, line, songs, limit=30):
    seen, ids, covers = set(), [], []
    for s in songs:
        if s["Id"] in seen:
            continue
        seen.add(s["Id"])
        ids.append(s["Id"])
        album = s.get("AlbumId")
        if album and album not in covers:
            covers.append(album)
        if len(ids) >= limit:
            break
    if len(ids) < 8:
        return None
    return {"key": key, "name": name, "line": line, "songs": ids, "covers": covers[:4]}


def mixes_for(token, user_id):
    day = time.strftime("%Y-%m-%d")
    with _mix_lock:
        cached = _mixes.get(user_id)
        if cached and cached[0] == day:
            return cached[1]
    import random

    rnd = random.Random(f"{user_id}-{day}")
    played = _songs(token, user_id, "Filters=IsPlayed&SortBy=PlayCount,DatePlayed&SortOrder=Descending&Limit=400")
    liked = _songs(token, user_id, "Filters=IsFavorite&Limit=300")
    fresh = _songs(token, user_id, "SortBy=Random&Limit=200")
    pool = {s["Id"]: s for s in played + liked + fresh}
    count = lambda s: (s.get("UserData") or {}).get("PlayCount") or 0  # noqa: E731
    # The artists each person plays most (likes count a little too)
    weight = {}
    for s in played:
        name, aid = _mix_artist(s)
        if name:
            weight[(name, aid)] = weight.get((name, aid), 0) + count(s)
    for s in liked:
        name, aid = _mix_artist(s)
        if name:
            weight[(name, aid)] = weight.get((name, aid), 0) + 3
    top = [a for a, _ in sorted(weight.items(), key=lambda x: -x[1])]
    mixes = []

    def daily(n, artists):
        names = {a[0] for a in artists}
        mine = [s for s in pool.values() if _mix_artist(s)[0] in names]
        rnd.shuffle(mine)
        # Mostly songs they know, plus a few they've barely played
        known = [s for s in mine if count(s) > 0][:22]
        rare = [s for s in mine if count(s) == 0][:8]
        songs = known + rare
        rnd.shuffle(songs)
        who = ", ".join(a[0] for a in artists[:3])
        return _mix(f"daily-{n}", f"Daily mix {n}", who or "A bit of everything", songs)

    if top:
        half = max(1, min(len(top), 8) // 2)
        mixes.append(daily(1, top[:half]))
        mixes.append(daily(2, top[half : half * 2] or top[:half]))
    else:
        songs = list(fresh)
        rnd.shuffle(songs)
        mixes.append(_mix("daily-1", "Daily mix 1", "A bit of everything", songs))
    repeat = [s for s in played if count(s) >= 2][:30]
    mixes.append(_mix("repeat", "On repeat", "The songs you play most", repeat, limit=30))
    for i, (name, aid) in enumerate([a for a in top if a[1]][:2]):
        j = _as_user(token, f"/Items/{aid}/InstantMix?UserId={user_id}&Limit=30&{SONG_FIELDS}") or {}
        mixes.append(_mix(f"radio-{i + 1}", f"{name} radio", f"{name} and music like it", j.get("Items") or []))
    # The decade they play most
    decades = {}
    for s in played:
        y = s.get("ProductionYear")
        if y:
            decades[y // 10 * 10] = decades.get(y // 10 * 10, 0) + count(s)
    if decades:
        d = max(decades, key=decades.get)
        years = ",".join(str(y) for y in range(d, d + 10))
        songs = _songs(token, user_id, f"Years={years}&SortBy=Random&Limit=40")
        mixes.append(_mix(f"decade-{d}", f"Your {d}s", f"Songs from the {d}s", songs))
    else:
        songs = list(fresh)
        rnd.shuffle(songs)
        mixes.append(_mix("discover", "Something new", "Songs you haven't played yet", [s for s in songs if count(s) == 0]))
    answer = {"mixes": [m for m in mixes if m][:6]}
    if answer["mixes"]:  # an empty answer (server busy, no plays yet) is tried again next time
        with _mix_lock:
            _mixes[user_id] = (day, answer)
    return answer


# ---------- Lyrics: Tower fetches missing ones from LRCLIB each night ----------

LRCLIB = "https://lrclib.net/api/get"
LYRICS_PER_NIGHT = 400


def _lyrics_worker():
    time.sleep(300)
    while True:
        # Once a night, around 3am
        import datetime

        now = datetime.datetime.now()
        nxt = now.replace(hour=3, minute=0, second=0, microsecond=0)
        if nxt <= now:
            nxt += datetime.timedelta(days=1)
        time.sleep(max(600, (nxt - now).total_seconds()))
        try:
            _lyrics_tick()
        except Exception as e:
            log(f"lyrics problem: {e!r}")


def _lyrics_tick():
    if not _dir or not os.path.isdir(MUSIC):
        return
    tried = _read("lyrics-tried.json") or {}
    j = _jellyfin("GET", "/Items?IncludeItemTypes=Audio&Recursive=true&Fields=Path&EnableImages=false&Limit=20000")
    if not j:
        return
    now, found, asked = time.time(), [], 0
    for s in j.get("Items") or []:
        path = s.get("Path") or ""
        if not path.startswith(MEDIA) or not os.path.isfile(path):
            continue
        lrc = os.path.splitext(path)[0] + ".lrc"
        if os.path.exists(lrc) or now - tried.get(path, 0) < 30 * 86400:
            continue
        if asked >= LYRICS_PER_NIGHT:
            break
        asked += 1
        tried[path] = now
        artist = (s.get("AlbumArtist") or ", ".join(s.get("Artists") or [])).strip()
        args = {"artist_name": artist, "track_name": s.get("Name") or "", "album_name": s.get("Album") or ""}
        ticks = s.get("RunTimeTicks") or 0
        if ticks:
            args["duration"] = int(round(ticks / 10_000_000))
        try:
            got = _http(LRCLIB + "?" + urllib.parse.urlencode(args), headers={"User-Agent": "Benflix (https://github.com/TheBenPsych/MyStream)"}, timeout=20)
        except Exception:  # any network hiccup only skips this song
            got = None
        text = (got or {}).get("syncedLyrics") or (got or {}).get("plainLyrics") if isinstance(got, dict) else None
        if text and not (got or {}).get("instrumental"):
            try:
                with open(lrc, "w", encoding="utf-8") as f:
                    f.write(text)
                _own(lrc)
                found.append(s["Id"])
            except OSError:
                pass
        time.sleep(1)
    _write("lyrics-tried.json", tried)
    for item in found:
        _jellyfin("POST", f"/Items/{item}/Refresh?MetadataRefreshMode=Default&ReplaceAllMetadata=false")
    if asked:
        log(f"lyrics: looked for {asked} songs, saved {len(found)} new lyrics")
