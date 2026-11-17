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
import copy
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
    def __init__(self, status, message, busy=False):
        super().__init__(message)
        self.status = status
        self.message = message
        # busy = the other app is only busy or restarting (Lidarr's 503 when its music list server is
        # overloaded), so the ask waits and tries again by itself instead of failing
        self.busy = busy


# Lidarr answers with these when it (or its online music list) is busy, not when the ask is wrong
LIDARR_BUSY_CODES = (429, 500, 502, 503, 504)
BUSY_WORDS = "Lidarr's music list is busy, trying again by itself"
# Waits between tries while Lidarr is busy (minutes), then every hour; gives up after two days
BUSY_WAITS = (2, 5, 10, 20, 30, 60)
BUSY_GIVE_UP = 2 * 86400
_lidarr_rest = [0.0]  # When Lidarr was last busy, nothing else is sent to it until this time


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
    # Asks that failed only because Lidarr was busy (before Benflix waited by itself) go back in line
    for r in _state["requests"]:
        if r.get("state") == "problem" and re.search(r"Lidarr answered with an error \((429|50[0234])\)|Couldn't reach Lidarr", r.get("note") or ""):
            _retry(r)
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
    threading.Thread(target=_info_worker, name="music-song-info", daemon=True).start()
    threading.Thread(target=_cover_worker, name="music-covers", daemon=True).start()
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
        if e.code in LIDARR_BUSY_CODES:
            raise MusicError(503, f"Lidarr is busy right now ({e.code}). {_lidarr_reason(detail)}".strip(), busy=True)
        raise MusicError(502, f"Lidarr answered with an error ({e.code}). {_lidarr_reason(detail)}".strip())
    except (urllib.error.URLError, OSError):
        raise MusicError(502, "Couldn't reach Lidarr. Check it's running on Tower.", busy=True)
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


def route(method, path, query, token, user_id, is_admin, read_json, is_kids=False, user_name="", for_person=None):
    """The helper sends /api/music/... here. Returns (status, answer). Made for you is for everyone but Kids.
    Grown-up family profiles can look and ask too (Redesign 2, Ben 2026-11-10), but their asks wait for Ben's
    Approve before anything downloads. Playlists, the Drop folder and the tidy-up stay Ben's only."""
    if method == "GET" and path == "/api/music/mixes":
        if is_kids:
            return 403, {"error": "No music on the Kids profile."}
        try:
            return 200, mixes_for(token, user_id)
        except Exception as e:
            log(f"Made for you problem: {e!r}")
            return 200, {"mixes": []}
    if method == "GET" and path == "/api/music/themes":
        # Themed playlists (holidays in season, decades, genres, moods, throwbacks). Never Kids.
        if is_kids:
            return 403, {"error": "No music on the Kids profile."}
        try:
            return 200, themes_for(token, user_id, is_admin)
        except Exception as e:
            log(f"themed playlists problem: {e!r}")
            return 200, {"themes": []}
    if method == "GET" and path == "/api/music/albumsize":
        # How many songs the whole album has, for the "3 of 12 songs" note and its Ask for the rest (everyone but Kids)
        if is_kids:
            return 403, {"error": "No music on the Kids profile."}
        one = lambda k: (query.get(k) or [""])[0].strip()  # noqa: E731
        found = album_find(one("artist"), one("album"))
        return 200, {"songs": found["songs"], "id": found["id"]}
    if method == "GET" and path in ("/api/music/people", "/api/music/lyricsearch"):
        # Send to a profile, and search by the words of a song (Music redesign 3; everyone but Kids)
        if is_kids:
            return 403, {"error": "No music on the Kids profile."}
        if path == "/api/music/people":
            return 200, {"people": music_people(user_id)}
        return 200, lyrics_search((query.get("q") or [""])[0])
    if method == "GET" and path == "/api/music/artistfind":
        # Artist pages (Music redesign 3): Popular worldwide, every album and Fans also like (everyone but Kids)
        if is_kids:
            return 403, {"error": "No music on the Kids profile."}
        try:
            return 200, artist_find((query.get("name") or [""])[0].strip())
        except MusicError as e:
            return e.status, {"error": e.message}
    if method == "POST" and path == "/api/music/mixes/hide":
        # Not for this playlist (Ben 2026-11-16): the song leaves this made-for-you or themed playlist for this profile
        if is_kids:
            return 403, {"error": "No music on the Kids profile."}
        body = read_json()
        return 200, mix_hide(user_id, str(body.get("key") or ""), str(body.get("id") or ""))
    if path.startswith("/api/music/me"):
        # Music redesign 3 (Ben 2026-11-16): what this profile played, pinned and searched, on every screen
        if is_kids:
            return 403, {"error": "No music on the Kids profile."}
        try:
            return 200, me_route(method, path, user_id, read_json)
        except MusicError as e:
            return e.status, {"error": e.message}
    if is_kids:
        return 403, {"error": "No music requests on the Kids profile."}
    if not is_admin:
        return _family_route(method, path, query, user_id, user_name, read_json, token=token)
    if not _dir:
        return 503, {"error": "Music is off: the helper has no folder to save requests in. Run the Benflix setup command again."}
    remember_admin(token, user_id)
    q = lambda k: (query.get(k) or [""])[0].strip()  # noqa: E731
    try:
        if method == "GET" and path == "/api/music/status":
            return 200, status()
        if method == "GET" and path == "/api/music/search":
            return 200, search(q("q"))
        if method == "GET" and path == "/api/music/discover":
            return 200, discover(token, user_id)
        if method == "GET" and path == "/api/music/similar":
            return 200, similar(q("artist"), q("title"))
        if method == "GET" and path == "/api/music/genre":
            return 200, genre_page(q("id"))
        if method == "GET" and path == "/api/music/preview":
            return 200, preview(q("id"))
        if method == "GET" and path == "/api/music/artist":
            return 200, artist_page(q("id"))
        if method == "GET" and path == "/api/music/album":
            return 200, album_page(q("id"))
        if method == "GET" and path == "/api/music/theme":
            return 200, theme_adds(token, user_id, q("key"))
        if method == "POST" and path == "/api/music/request":
            body = read_json()
            if for_person:
                # Ben asking for someone in the family: theirs, but no waiting for Ben's yes
                answer = ask(str(body.get("kind") or ""), body.get("id"), who=for_person[1], user=for_person[0], wait=False)
            else:
                answer = ask(str(body.get("kind") or ""), body.get("id"))
            if body.get("theme") and answer.get("kind") == "song":
                _theme_pin(str(body["theme"]), answer)
            return 200, answer
        if method == "POST" and path == "/api/music/redo":
            body = read_json()
            return 200, redo_song(str(body.get("id") or ""), token, user_id)
        if method == "POST" and path == "/api/music/delete":
            body = read_json()
            return 200, delete_song(str(body.get("id") or ""), token, user_id)
        m = re.fullmatch(r"/api/music/request/([\w-]+)/(retry|remove|approve|decline)", path)
        if method == "POST" and m:
            act = m.group(2)
            if act == "approve":
                return 200, approve(m.group(1))
            if act == "decline":
                return 200, remove(m.group(1))
            return 200, (retry(m.group(1)) if act == "retry" else remove(m.group(1)))
        if method == "POST" and path == "/api/music/playlist":
            body = read_json()
            return 200, playlist_import(str(body.get("link") or ""), str(body.get("name") or ""), body.get("playlist", True) is not False)
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
    _mark_asked(out, _jellyfin_have(text))
    return out


ITUNES = "https://itunes.apple.com/search"


def preview(song_id):
    """A 30 second taste of a song, to hear before asking for it. Deezer's free preview first (it's the same
    catalogue, so it's the exact song), then Apple's free iTunes preview when Deezer has none. The links only
    work for a while, so the app and web page ask for a fresh one each time Play is pressed."""
    if not str(song_id).isdigit():
        raise MusicError(400, "Which song?")
    t = deezer(f"/track/{song_id}")
    title = t.get("title_short") or t.get("title") or ""
    artist = (t.get("artist") or {}).get("name") or ""
    url = t.get("preview") or ""
    source = "deezer"
    if not url and title:
        url = _itunes_preview(artist, title)
        source = "itunes"
    if not url:
        raise MusicError(404, "No preview for this song.")
    return {"url": url, "title": title, "artist": artist, "from": source, "seconds": 30}


def _itunes_preview(artist, title):
    """Apple's free 30 second preview for the same song (empty when it can't be found)"""
    q = urllib.parse.urlencode({"term": f"{artist} {title}", "media": "music", "entity": "song", "limit": 10})
    try:
        found = _http(f"{ITUNES}?{q}", timeout=15) or {}
    except (urllib.error.URLError, OSError, ValueError):
        return ""
    results = [r for r in (found.get("results") or []) if r.get("previewUrl")]
    for r in results:
        if same_song(r.get("trackName"), title) and same(r.get("artistName"), artist):
            return r["previewUrl"]
    for r in results:
        if same_song(r.get("trackName"), title):
            return r["previewUrl"]
    return ""


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


# ---------- Discover: what's popular right now (Deezer's charts, looked at every few hours) ----------

_found_cache = {}


def _cached(key, load, keep=4 * 3600):
    with _lock:
        hit = _found_cache.get(key)
    if hit and time.time() - hit[0] < keep:
        return copy.deepcopy(hit[1])
    value = load()
    with _lock:
        _found_cache[key] = (time.time(), value)
    return copy.deepcopy(value)


def _genres():
    def load():
        out = []
        for g in deezer("/genre").get("data") or []:
            if g.get("id") and g.get("name") and g["id"] != 0:
                out.append({"id": g["id"], "name": g["name"], "picture": g.get("picture_medium") or ""})
        return out

    return _cached("genres", load, keep=7 * 24 * 3600)


def _chart(genre_id, songs=100):
    """Top songs, albums and artists, and new releases, for everything (0) or one genre"""

    def load():
        def part(path, **k):
            try:
                return deezer(path, **k).get("data") or []
            except MusicError:
                return []

        tracks = part(f"/chart/{genre_id}/tracks", limit=songs)
        albums = part(f"/chart/{genre_id}/albums", limit=30)
        artists = part(f"/chart/{genre_id}/artists", limit=30) if genre_id == 0 else part(f"/genre/{genre_id}/artists", limit=30)
        new = part(f"/editorial/{genre_id}/releases", limit=30)
        if not tracks and not albums and not new:
            raise MusicError(502, "Couldn't reach the music catalogue. Check Tower has internet.")
        return {
            "songs": [_song(t) for t in tracks if t.get("id")],
            "albums": [_album(a) for a in albums if a.get("id")],
            "artists": [_artist(a) for a in artists if a.get("id")],
            "new": [_album(a) for a in new if a.get("id")],
        }

    return _cached(f"chart-{genre_id}", load)


def _picks():
    """Albums Deezer's editors are picking right now"""

    def load():
        try:
            return [_album(a) for a in deezer("/editorial/0/selection", limit=30).get("data") or [] if a.get("id")]
        except MusicError:
            return []

    return _cached("picks", load)


def _your_artists():
    """Artist folder names in Music, the ones with the most songs first"""
    counts = {}
    if os.path.isdir(MUSIC):
        for n in os.listdir(MUSIC):
            p = os.path.join(MUSIC, n)
            if n.startswith(".") or not os.path.isdir(p) or n.lower() in ("various artists", "unknown", "singles"):
                continue
            counts[n] = sum(1 for _b, _d, files in os.walk(p) for f in files if os.path.splitext(f)[1].lower() in AUDIO)
    return [n for n, c in sorted(counts.items(), key=lambda x: -x[1]) if c]


# Trending by genre: the genres shown as "Trending in <genre>" lists, in this order (names as the catalogue writes them)
TREND_GENRES = (
    "Pop", "Rap/Hip Hop", "R&B", "Rock", "Dance", "Alternative", "Electro", "Country", "Latin Music", "Reggaeton",
    "Asian Music", "Metal", "Soul & Funk", "Reggae", "Folk", "Jazz", "Blues", "Films/Games",
)
TREND_ROWS = 12
SEED_ROWS = 4
_pool = None


def _workers():
    """A few helpers so the catalogue calls for one page run side by side (the page opens faster on old TVs)"""
    global _pool
    with _lock:
        if _pool is None:
            from concurrent.futures import ThreadPoolExecutor

            _pool = ThreadPoolExecutor(max_workers=6, thread_name_prefix="music-discover")
        return _pool


def _each(jobs, wait=25):
    """Runs every (function, value) at once and gives back the answers in the same order (None when one failed)"""
    futures = [_workers().submit(f, v) for f, v in jobs]
    out = []
    for fu in futures:
        try:
            out.append(fu.result(timeout=wait))
        except Exception:  # noqa: BLE001 - one slow or broken list never stops the page
            out.append(None)
    return out


def _genre_songs(genre):
    """The top songs of one genre. The genre chart first; when the catalogue only sends the main chart back,
    the best songs of the genre's top artists instead. Kept for 4 hours."""

    def load():
        try:
            tracks = deezer(f"/chart/{genre['id']}/tracks", limit=50).get("data") or []
        except MusicError:
            tracks = []
        try:
            artists = (deezer(f"/genre/{genre['id']}/artists", limit=8).get("data") or [])[:6]
        except MusicError:
            artists = []
        extra = []
        for a in artists:
            try:
                extra.append([t for t in deezer(f"/artist/{a['id']}/top", limit=5).get("data") or [] if t.get("id")])
            except MusicError:
                continue
        # One song from each artist in turn, so the list isn't five songs in a row from the same person
        mixed = [lst[i] for i in range(5) for lst in extra if i < len(lst)]
        return {"chart": [_song(t) for t in tracks if t.get("id")], "artists": [_song(t) for t in mixed]}

    return _cached(f"genre-songs-{genre['id']}", load)


def _trending(top_ids):
    """'Trending in Pop', 'Trending in Rock' ...: each list skips songs already in the Top 100 or an earlier list"""
    by_name = {norm(g["name"]): g for g in _genres()}
    wanted = [by_name[norm(n)] for n in TREND_GENRES if norm(n) in by_name]
    found = _each([(_genre_songs, g) for g in wanted])
    seen = set(top_ids)
    rows = []
    for g, songs in zip(wanted, found):
        if not songs:
            continue
        fresh = [x for x in songs["chart"] if x["id"] not in seen]
        if len(fresh) < 10:
            # The genre chart was just the main chart again: use the genre's top artists
            fresh = [x for x in songs["artists"] if x["id"] not in seen]
        fresh = list({x["id"]: x for x in fresh}.values())[:30]
        if len(fresh) >= 8:
            seen.update(x["id"] for x in fresh)
            rows.append({"id": g["id"], "genre": g["name"], "songs": fresh})
        if len(rows) >= TREND_ROWS:
            break
    return rows


def _loose(name):
    """Names compared with no spaces and $ read as s, so Ke$ha = Kesha and P!nk = Pink"""
    return re.sub(r"[^a-z0-9]", "", norm(str(name or "").replace("$", "s").replace("!", "i")))


def _same_artist(a, b):
    return same(a, b) or (bool(_loose(a)) and _loose(a) == _loose(b))


class _Unreachable(Exception):
    """The music catalogue didn't answer: nothing is kept, so the next try asks again"""


def _seed_row(seed):
    """'Because you have <song>': finds the song in the catalogue, then songs like it from its artist's radio
    (the artist and artists like them). Kept for a day.
    Ben 2026-11-17: Kesha's Your Love Is My Drug found nothing. The song has to match closely, but when it
    doesn't the artist alone is enough, and a catalogue that's out of reach is no longer kept as 'nothing'."""
    artist, title = seed

    def load():
        try:
            found = deezer("/search/track", q=f'artist:"{artist}" track:"{title}"', limit=5).get("data") or []
            if not found:
                found = deezer("/search/track", q=f"{artist} {title}", limit=15).get("data") or []
        except MusicError as e:
            raise _Unreachable() from e
        by = lambda x: (x.get("artist") or {}).get("name")  # noqa: E731
        t = next((x for x in found if same(x.get("title"), title) and _same_artist(by(x), artist)), None)
        t = t or next((x for x in found if _same_artist(by(x), artist)), None)
        a = (t or {}).get("artist") or {}
        if not a.get("id"):
            # The song isn't in the catalogue: start from the artist instead
            try:
                hits = deezer("/search/artist", q=artist, limit=5).get("data") or []
            except MusicError as e:
                raise _Unreachable() from e
            a = next((x for x in hits if _same_artist(x.get("name"), artist)), None) or {}
            if not a.get("id"):
                return None
        try:
            radio = deezer(f"/artist/{a['id']}/radio", limit=50).get("data") or []
        except MusicError as e:
            raise _Unreachable() from e
        mine = lambda x: _same_artist(by(x), a.get("name"))  # noqa: E731
        # Other artists' songs first, so it's something new; a couple from the same artist at the end
        others = [_song(x) for x in radio if x.get("id") and not mine(x)]
        own = [_song(x) for x in radio if x.get("id") and mine(x) and x.get("id") != (t or {}).get("id")]
        if len(others) < 6:
            # A thin radio: top songs of artists like this one
            try:
                like = (deezer(f"/artist/{a['id']}/related", limit=8).get("data") or [])[:6]
                for r in like:
                    for x in (deezer(f"/artist/{r['id']}/top", limit=4).get("data") or [])[:4]:
                        if x.get("id") and all(x["id"] != y.get("id") for y in others):
                            others.append(_song(x))
            except (MusicError, KeyError):
                pass
        return {"artist": a.get("name") or artist, "song": (t or {}).get("title") or title, "seed": (t or {}).get("id"), "songs": others + own[:3]}

    try:
        return _cached(f"seed2-{norm(artist)}-{norm(title)}", load, keep=24 * 3600)
    except _Unreachable:
        return None


SKIP_SEEDS = re.compile(r"\b(intro|outro|skit|interlude|prelude|untitled|track \d+)\b", re.I)


def _seeds_for(token, user_id):
    """(artist, song) pairs this person plays most or liked, most played first (Ben 2026-11-16: Because you have
    starts from what you actually listen to)"""
    if not token or not user_id:
        return []
    out, seen = [], set()
    for s in _songs(token, user_id, "Filters=IsPlayed&SortBy=PlayCount,DatePlayed&SortOrder=Descending&Limit=150") + _songs(token, user_id, "Filters=IsFavorite&SortBy=Random&Limit=100"):
        who = s.get("AlbumArtist") or (s.get("Artists") or [""])[0] or ""
        if not who or who.lower() in ("various artists", "unknown") or not s.get("Name"):
            continue
        k = (norm(who), norm(s["Name"]))
        if k not in seen:
            seen.add(k)
            out.append((main_artist(who) or who, s["Name"]))
    return out


def _because(seeds=None):
    """Several 'Because you have <song>' lists: from the songs this person plays most and liked (picked again every
    time the page opens), topped up with random songs from Music when they haven't played much yet"""
    import random

    try:
        _have_songs()
        anyone = list(_have["named"])
    except OSError:
        anyone = []
    random.shuffle(anyone)
    own = list(seeds or [])
    # Mostly their top songs, with some of the rest so it isn't the same few lists every time
    top, rest = own[:40], own[40:]
    random.shuffle(top)
    random.shuffle(rest)
    mine = top + rest + anyone
    mine = [x for x in mine if len(x[1]) > 1 and not SKIP_SEEDS.search(x[1])]
    if not _christmas_time():
        xre = THEME_BY_KEY["christmas"]["_re"]
        mine = [x for x in mine if not xre.search(norm(x[1]))]
    # One song per artist, so two lists are never the same artist's radio
    picked, artists = [], set()
    for who, title in mine:
        if norm(who) not in artists:
            artists.add(norm(who))
            picked.append((who, title))
        if len(picked) >= SEED_ROWS * 2:
            break
    rows, shown = [], set()
    for row in _each([(_seed_row, x) for x in picked]):
        if row and len(row["songs"]) >= 8:
            row = dict(row)
            # Two artists' radios often share songs: each song shows in one list only
            fresh = [x for x in row["songs"] if x["id"] not in shown]
            if len(fresh) < 8:
                continue
            row["songs"] = random.sample(fresh, len(fresh))
            shown.update(x["id"] for x in fresh)
            rows.append(row)
    return rows


def _album_owned(album, have_albums):
    who = {norm(main_artist(album.get("artist"))), norm(album.get("artist"))}
    title = norm(album.get("title"))
    return bool(title) and any((w, title) in have_albums for w in who)


def _hide_owned_albums(out):
    """Albums already in Music never show in the lists"""
    try:
        _have_songs()
        have_albums = set(_have["albums"])
    except OSError:
        return
    for key in ("albums", "new", "picks"):
        if out.get(key):
            out[key] = [a for a in out[key] if not _album_owned(a, have_albums)]


def discover(token=None, user_id=None):
    out = _chart(0, songs=100)
    top_ids = [x["id"] for x in out["songs"][:100]]
    out["genres"] = _genres()
    out["picks"] = _picks()
    try:
        seeds = _seeds_for(token, user_id)
    except Exception:  # noqa: BLE001 - no plays to start from: random songs from Music instead
        seeds = []
    trending, because = _each([(lambda _x: _trending(top_ids), None), (lambda _x: _because(seeds), None)], wait=40)
    out["trending"] = trending or []
    out["because"] = because or []
    _mark_asked(out)
    _hide_owned_albums(out)
    for row in out["trending"]:
        _mark_asked(row)
        row["songs"] = [x for x in row["songs"] if not x.get("have")]
    out["trending"] = [r for r in out["trending"] if len(r["songs"]) >= 6]
    for b in out["because"]:
        _mark_asked(b)
        # Songs you already have aren't much of a find
        b["songs"] = [x for x in b["songs"] if not x.get("have")][:24]
    out["because"] = [b for b in out["because"] if len(b["songs"]) >= 6][:SEED_ROWS]
    return out


def similar(artist, title):
    """A few songs like the one playing, to ask for from Now playing (songs already in Music left out)"""
    if not artist or not title:
        raise MusicError(400, "Which song?")
    row = _seed_row((artist, title))
    if not row:
        return {"songs": []}
    row = copy.deepcopy(row)
    _mark_asked(row)
    row["songs"] = [x for x in row["songs"] if not x.get("have")][:12]
    return row


def genre_page(genre_id):
    if not str(genre_id).isdigit():
        raise MusicError(400, "Which genre?")
    g = next((x for x in _genres() if str(x["id"]) == str(genre_id)), None)
    if not g:
        raise MusicError(404, "That genre isn't in the music catalogue.")
    out = _chart(int(genre_id), songs=50)
    out["genre"] = g
    _mark_asked(out)
    _hide_owned_albums(out)
    # Ben 2026-11-15: genre pages only show songs you don't have yet
    out["songs"] = [x for x in out["songs"] if not x.get("have")]
    return out


def album_size(artist, album):
    """How many songs this album has in the music catalogue (0 when it can't be found). Kept for 30 days."""
    if not artist or not album:
        return 0

    def load():
        try:
            found = deezer("/search/album", q=f'artist:"{artist}" album:"{album}"', limit=10).get("data") or []
            if not found:
                found = deezer("/search/album", q=f"{artist} {album}", limit=10).get("data") or []
        except MusicError:
            return None
        for a in found:
            if same(a.get("title"), album) and same((a.get("artist") or {}).get("name"), artist):
                return int(a.get("nb_tracks") or 0)
        return 0

    key = f"size-{norm(artist)}-{norm(album)}"
    n = _cached(key, load, keep=30 * 24 * 3600)
    if n is None:
        with _lock:
            _found_cache.pop(key, None)  # The catalogue was out of reach: ask again next time
        return 0
    return n


def album_find(artist, album):
    """The album in the music catalogue: how many songs it has and its id, for Ask for the rest. Kept for 30 days."""
    if not artist or not album:
        return {"songs": 0, "id": None}

    def load():
        try:
            found = deezer("/search/album", q=f'artist:"{artist}" album:"{album}"', limit=10).get("data") or []
            if not found:
                found = deezer("/search/album", q=f"{artist} {album}", limit=10).get("data") or []
        except MusicError:
            return None
        for a in found:
            if same(a.get("title"), album) and same((a.get("artist") or {}).get("name"), artist):
                return {"songs": int(a.get("nb_tracks") or 0), "id": a.get("id")}
        return {"songs": 0, "id": None}

    key = f"albumfind-{norm(artist)}-{norm(album)}"
    got = _cached(key, load, keep=30 * 24 * 3600)
    if got is None:
        with _lock:
            _found_cache.pop(key, None)  # The catalogue was out of reach: ask again next time
        return {"songs": 0, "id": None}
    return got


_have = {"at": 0, "songs": set(), "albums": set(), "named": []}


def _have_reset():
    """Music changed (a song came in or was deleted): look at the folder again next time"""
    _have["at"] = 0


def _have_songs():
    """(artist, song) pairs already in Music, from the folder and file names (looked at every 10 minutes).
    Also keeps (artist, album) pairs, and the songs with their real names for 'Because you have <song>'."""
    if time.time() - _have["at"] < 600:
        return _have["songs"]
    found, albums, named = set(), set(), []
    if os.path.isdir(MUSIC):
        for base, dirs, files in os.walk(MUSIC):
            dirs[:] = [d for d in dirs if not d.startswith(".")]
            rel = os.path.relpath(base, MUSIC)
            if rel == ".":
                continue
            parts = rel.split(os.sep)
            shown = main_artist(parts[0])
            who = norm(shown)
            if len(parts) >= 2:
                albums.add((who, norm(re.sub(r"\s*[\(\[]\d{4}[\)\]]\s*$", "", parts[1]))))
            various = parts[0].lower() in ("various artists", "unknown", "singles")
            for f in files:
                if os.path.splitext(f)[1].lower() in AUDIO:
                    stem = re.sub(r"^\d{1,2}-?\d{0,3}\s+-\s+", "", os.path.splitext(f)[0])
                    found.add((who, norm(stem)))
                    if not various and shown:
                        named.append((shown, stem))
    _have.update({"at": time.time(), "songs": found, "albums": albums, "named": named})
    return found


def _jellyfin_have(text):
    """(artist, song) pairs Jellyfin finds for these search words, so songs already on Benflix say so"""
    user = _state["admin"].get("user")
    if not user or len(text or "") < 2:
        return set()
    q = urllib.parse.urlencode({"searchTerm": text, "IncludeItemTypes": "Audio", "Recursive": "true", "Limit": 60, "userId": user, "Fields": "ArtistItems"})
    try:
        j = _jellyfin("GET", f"/Items?{q}") or {}
    except Exception:
        return set()
    out = set()
    for it in j.get("Items") or []:
        names = [it.get("AlbumArtist") or ""] + [a.get("Name", "") for a in it.get("ArtistItems") or []] + list(it.get("Artists") or [])
        for n in names:
            if n:
                out.add((norm(main_artist(n)), norm(it.get("Name"))))
    return out


def _owned(song, have):
    """Is this catalogue song already in Music? Same artist and the same take of the song"""
    title = norm(song.get("title"))
    if not title:
        return False
    for who in {norm(main_artist(song.get("artist"))), norm(song.get("artist"))}:
        if (who, title) in have:
            return True
    return False


def _mark_asked(out, extra_have=None):
    """Adds 'asked' (the words for its request) to anything already asked for"""
    with _lock:
        by = {(r["kind"], r.get("deezer")): r for r in _state["requests"]}
    for kind, key in (("song", "songs"), ("album", "albums"), ("album", "new"), ("album", "picks")):
        for x in out.get(key) or []:
            r = by.get((kind, x.get("id")))
            if r:
                x["asked"] = _words(r)[0]
    try:
        have = set(_have_songs())
    except OSError:
        have = set()
    have |= extra_have or set()
    for x in out.get("songs") or []:
        if _owned(x, have):
            # Already in Music: never asked for again, whatever the old request says
            x["have"] = True
            x.pop("asked", None)
    if out.get("album"):
        r = by.get(("album", out["album"].get("id")))
        if r:
            out["album"]["asked"] = _words(r)[0]


FAMILY_READ = ("/api/music/search", "/api/music/discover", "/api/music/similar", "/api/music/genre", "/api/music/preview", "/api/music/artist", "/api/music/album")


def _family_route(method, path, query, user_id, user_name, read_json, token=None):
    """A grown-up family profile: look, hear previews and ask. Asks wait for Ben (state waiting_ben)."""
    if not _dir:
        return 503, {"error": "Music requests aren't set up on Tower yet. Ask Ben."}
    q = lambda k: (query.get(k) or [""])[0].strip()  # noqa: E731
    try:
        if method == "GET" and path in FAMILY_READ:
            if path == "/api/music/search":
                return 200, search(q("q"))
            if path == "/api/music/discover":
                return 200, discover(token, user_id)
            if path == "/api/music/similar":
                return 200, similar(q("artist"), q("title"))
            if path == "/api/music/genre":
                return 200, genre_page(q("id"))
            if path == "/api/music/preview":
                return 200, preview(q("id"))
            if path == "/api/music/artist":
                return 200, artist_page(q("id"))
            return 200, album_page(q("id"))
        if method == "GET" and path == "/api/music/theme":
            # Songs to add under a themed playlist (Ben 2026-11-16: every profile but Kids can ask, Ben okays it)
            return 200, theme_adds(token, user_id, q("key"))
        if method == "GET" and path == "/api/music/status":
            with _lock:
                reqs = [_public(r) for r in _state["requests"] if r.get("user") == user_id]
            return 200, {"lidarr": True, "soulseek": False, "problems": [], "requests": reqs, "playlists": [], "family": True,
                         "drop_folder": "", "music_folder": "", "drop_lines": [], "drop_last": None, "tidy": None}
        if method == "POST" and path == "/api/music/request":
            body = read_json()
            answer = ask(str(body.get("kind") or ""), body.get("id"), who=user_name or "Someone", user=user_id)
            if body.get("theme") and answer.get("kind") == "song":
                _theme_pin(str(body["theme"]), answer)
            return 200, answer
        m = re.fullmatch(r"/api/music/request/([\w-]+)/remove", path)
        if method == "POST" and m:
            with _lock:
                r = _find(m.group(1))
                if r.get("user") != user_id:
                    raise MusicError(403, "Only Ben can take off someone else's request.")
            return 200, remove(m.group(1))
    except MusicError as e:
        return e.status, {"error": e.message}
    return 403, {"error": "Only Ben can do that."}


def approve(rid):
    """Ben said yes to a family profile's music ask: now it really gets looked for"""
    with _lock:
        r = _find(rid)
        if r["state"] == "waiting_ben":
            r.update({"state": "adding", "since": time.time(), "approved": time.time()})
            _save()
    log(f"Ben said yes to {r.get('who') or 'someone'}'s {r['kind']} {r['artist']} - {r['title']}")
    _kick.set()
    return _public(r)


def ask(kind, deezer_id, playlist=None, quiet=False, who=None, user=None, wait=True):
    """Asks for one song or album. Returns the request (an existing one if it was already asked for).
    [who] set = a family profile asking: it waits (waiting_ben) until Ben presses Approve, unless [wait] is False
    (Ben asked for them with "Request for")."""
    if kind not in ("song", "album") or not str(deezer_id).isdigit():
        raise MusicError(400, "Pick a song or an album to ask for.")
    deezer_id = int(deezer_id)
    with _lock:
        for r in _state["requests"]:
            if r["kind"] == kind and r.get("deezer") == deezer_id:
                if who and not wait and not r.get("user"):
                    # Ben asked for someone who wanted it too: now it's on their list as well
                    r.update({"who": who, "user": user})
                if r["state"] in ("not_found", "problem") and not quiet:
                    _retry(r)
                    _save()
                    _kick.set()
                return _public(r)
    if kind == "song":
        t = deezer(f"/track/{deezer_id}")
        al = t.get("album") or {}
        if not quiet:
            one = _song(t)
            try:
                have = set(_have_songs()) | _jellyfin_have(one["title"])
            except OSError:
                have = set()
            if _owned(one, have):
                raise MusicError(409, f"{one['title']} is already in your music.")
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
    r.update({"id": uuid.uuid4().hex[:12], "kind": kind, "deezer": deezer_id, "state": "waiting_ben" if who and wait else "adding", "created": time.time(), "tries": []})
    if who:
        r.update({"who": who, "user": user})
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
    r.update({"state": "adding" if not r.get("album_id") else "search_now", "tries": [], "note": None, "since": time.time(), "slsk_tried": False, "fails": 0})
    _not_busy(r)
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
    if s == "waiting_ben":
        return "Asked · waiting for Ben", 0.05, False
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
    if r.get("busy_until") and s not in ("downloading", "slsk_downloading", "tidying"):
        return BUSY_WORDS, 0.1, False
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
        # Redesign 2: one of the five status pills (asked, getting, looking, cant, ready) and how far along
        "pill": _pill(r),
        "pct": int(r.get("pct") or 0),
        "who": r.get("who") or "",
        "waiting": r["state"] == "waiting_ben",
        "days": int((time.time() - (r.get("approved") or r.get("created") or time.time())) // 86400),
        "created": int(r.get("created") or 0),
        "playlist": r.get("playlist"),
    }


def _pill(r):
    s = r["state"]
    if s == "ready":
        return "ready"
    if s in ("not_found", "problem"):
        return "cant"
    if s in ("downloading", "slsk_downloading", "tidying"):
        return "getting"
    if s in ("searching", "search_now", "slsk_search"):
        return "looking"
    return "asked"


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
        try:
            _doubles_daily()
        except Exception as e:
            log(f"doubles check problem: {e!r}")
        try:
            if _state["admin"].get("token"):
                _artist_photos_nightly()
        except Exception as e:
            log(f"artist photos problem: {e!r}")


def _tick():
    with _lock:
        todo = [r for r in _state["requests"] if r["state"] not in ("ready", "not_found", "problem", "waiting_ben")]
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
    lidarr_busy = False  # Once Lidarr says it's busy, the rest wait for the next round instead of piling on
    now = time.time()
    for r in todo:
        uses_lidarr = not r["state"].startswith("slsk_") and lidarr_ok
        if uses_lidarr and (lidarr_busy or _lidarr_rest[0] > now or r.get("busy_until", 0) > now):
            continue
        adding = r["state"] == "adding"
        try:
            if r["state"].startswith("slsk_"):
                _slsk_step(r)
            elif lidarr_ok:
                _step(r, queue)
                _maybe_soulseek(r)
            elif r["kind"] == "song" and _slsk_on() and not r.get("slsk_tried"):
                r["state"] = "slsk_search"
                _slsk_step(r)
            if uses_lidarr:
                _not_busy(r)
        except MusicError as e:
            if e.busy and uses_lidarr:
                lidarr_busy = True
                _lidarr_rest[0] = time.time() + 90
                _busy(r, e)
            else:
                r["fails"] = r.get("fails", 0) + 1
                if r["fails"] >= 5:
                    r.update({"state": "problem", "note": e.message})
                    log(f"gave up on {r['artist']} - {r['title']}: {e.message}")
        with _lock:
            _save()
        if adding and uses_lidarr:
            time.sleep(1.5)  # Lidarr looks each new ask up online; many at once makes that list say "busy"


def _busy(r, e):
    """Lidarr (or its online music list) is busy: wait a bit longer each time, then try again by itself"""
    first = r.setdefault("busy_since", time.time())
    if time.time() - first > BUSY_GIVE_UP:
        r.update({"state": "problem", "note": "Lidarr's music list was busy for two days. Press Try again later."})
        _not_busy(r)
        log(f"gave up on {r['artist']} - {r['title']}: Lidarr busy for two days ({e.message})")
        return
    n = r.get("busy_tries", 0)
    r["busy_tries"] = n + 1
    r["busy_until"] = time.time() + 60 * BUSY_WAITS[min(n, len(BUSY_WAITS) - 1)]
    log(f"Lidarr busy for {r['artist']} - {r['title']} ({e.message}); trying again in {BUSY_WAITS[min(n, len(BUSY_WAITS) - 1)]} min")


def _not_busy(r):
    for k in ("busy_until", "busy_since", "busy_tries"):
        r.pop(k, None)


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
    # Songs that were already in the Music folder before these requests are never deleted (only the extra
    # songs of the copy that just came in are), so "Delete and find again" can't take an old album with it
    since = min((x.get("files_after") or x.get("created") or 0) for x in mates) if mates else 0
    for f in files:
        if whole or f.get("id") in keep_ids:
            paths.append(_to_helper_path(f.get("path")))
        elif since and _added(f) and _added(f) < since - 60:
            continue
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
    _have_reset()
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


def _added(f):
    """When Lidarr put a song file in (seconds), or 0 when it doesn't say"""
    t = str(f.get("dateAdded") or "")
    m = re.match(r"(\d{4})-(\d\d)-(\d\d)T(\d\d):(\d\d):(\d\d)", t)
    if not m:
        return 0
    import calendar

    return calendar.timegm(tuple(int(x) for x in m.groups()) + (0, 0, 0))


# ---------- Delete and find again (a bad copy of a song) ----------


def _to_lidarr_path(path):
    """The helper's /media/Music/... is Lidarr's /data/Music/..."""
    if path.startswith(MUSIC + "/"):
        return _lidarr_music_path() + path[len(MUSIC):]
    return None


def _jf_song(item_id, token, user_id):
    if not re.fullmatch(r"[0-9a-fA-F-]{32,36}", item_id or ""):
        raise MusicError(400, "Which song?")
    j = _as_user(token, f"/Users/{user_id}/Items?Ids={item_id}&Fields=Path,MediaSources&EnableImages=false") or {}
    items = [x for x in j.get("Items") or [] if x.get("Type") == "Audio"]
    if not items:
        raise MusicError(404, "That song isn't on the server any more.")
    return items[0]


def _deezer_match(title, artist, album, seconds):
    """The same song in the music catalogue, so it can be asked for again"""
    tries = [f'artist:"{artist}" track:"{title}"', f"{artist} {title}"]
    best = None
    for q in tries:
        for t in deezer("/search", q=q, limit=25).get("data") or []:
            if not same_song(t.get("title"), title) or not same((t.get("artist") or {}).get("name"), artist):
                continue
            score = 0
            if seconds and t.get("duration") and abs(t["duration"] - seconds) > 8:
                score += 2
            if album and not same((t.get("album") or {}).get("title"), album):
                score += 1
            if best is None or score < best[0]:
                best = (score, t)
            if score == 0:
                return t
        if best:
            return best[1]
    return None


def _lidarr_file(path):
    """Lidarr's record of this file: (track file, artist) or (None, None)"""
    if not LIDARR_URL or not _ensure_lidarr():
        return None, None
    stem = os.path.splitext(path)[0]
    for a in lidarr("GET", "/artist") or []:
        home = _to_helper_path(a.get("path"))
        if not home or not path.startswith(home.rstrip("/") + "/"):
            continue
        for f in lidarr("GET", "/trackfile", artistId=a["id"]) or []:
            # The MP3 Benflix made may not be rescanned yet, so the name without its ending is enough
            hp = _to_helper_path(f.get("path")) or ""
            if hp == path or os.path.splitext(hp)[0] == stem:
                return f, a
    return None, None


def _blocklist(track_file, artist):
    """Marks the download this file came from as bad, so Lidarr never picks that copy again. True when done."""
    try:
        hist = lidarr("GET", "/history/artist", artistId=artist["id"], albumId=track_file.get("albumId")) or []
    except MusicError:
        return False
    if isinstance(hist, dict):
        hist = hist.get("records") or []
    hist.sort(key=lambda h: str(h.get("date") or ""), reverse=True)
    lpath = track_file.get("path") or ""
    stem = os.path.splitext(lpath)[0]
    download = None
    for h in hist:
        if (h.get("eventType") or "").lower() not in ("trackfileimported", "downloadimported"):
            continue
        got = str((h.get("data") or {}).get("importedPath") or "")
        if h.get("downloadId") and (not got or os.path.splitext(got)[0] == stem):
            download = h["downloadId"]
            break
    grabs = [h for h in hist if (h.get("eventType") or "").lower() == "grabbed"]
    grab = next((h for h in grabs if download and h.get("downloadId") == download), None) or (grabs[0] if grabs and not download else None)
    if not grab:
        return False
    try:
        lidarr("POST", f"/history/failed/{grab['id']}")
        log(f"told Lidarr never to take {grab.get('sourceTitle') or 'that copy'} again")
        return True
    except MusicError as e:
        log(f"couldn't block that copy in Lidarr: {e.message}")
        return False


def redo_song(item_id, token, user_id):
    """Ben's "Delete and find again": deletes this song's file from Tower, makes sure the same copy is never
    taken again (Lidarr's blocklist, or the Soulseek person and file), then asks for the song again"""
    s = _jf_song(item_id, token, user_id)
    path = s.get("Path") or ""
    real = os.path.realpath(path) if path else ""
    if not real.startswith(os.path.realpath(MUSIC) + os.sep):
        raise MusicError(400, "Only songs in Tower's Music folder can be deleted from here.")
    title = s.get("Name") or ""
    artist = (s.get("AlbumArtist") or (s.get("Artists") or [""])[0] or "").strip()
    album = s.get("Album") or ""
    seconds = int(round((s.get("RunTimeTicks") or 0) / 10_000_000))
    # The request this song came from, if Benflix got it
    with _lock:
        old = next(
            (r for r in _state["requests"] if r["kind"] == "song" and same_song(title, _wanted_title(r)) and same(r.get("artist"), artist)),
            None,
        )
    # Found in the catalogue BEFORE anything is deleted, so the song is never just lost
    deezer_id = old.get("deezer") if old else None
    if not deezer_id:
        t = _deezer_match(title, artist, album, seconds)
        if not t:
            raise MusicError(404, "Couldn't find this song in the music catalogue, so it wasn't deleted.")
        deezer_id = t["id"]
    track_file, lartist = (None, None)
    try:
        track_file, lartist = _lidarr_file(real)
    except MusicError as e:
        log(f"couldn't look the song up in Lidarr: {e.message}")
    blocked = False
    if track_file:
        blocked = _blocklist(track_file, lartist)
        try:
            lidarr("DELETE", f"/trackfile/{track_file['id']}")
        except MusicError as e:
            log(f"Lidarr couldn't delete the file, so Benflix will: {e.message}")
    if os.path.exists(real):
        try:
            os.remove(real)
        except OSError:
            raise MusicError(500, "Tower wouldn't let Benflix delete the file. Check the Music folder's permissions.")
    _quiet_remove(os.path.splitext(real)[0] + ".lrc")
    _tell_jellyfin([path], kind="Deleted")
    _have_reset()
    log(f"deleted a bad copy of {artist} - {title}")
    # Ask for it again
    now = time.time()
    if old is None:
        ask("song", deezer_id, quiet=True)
        with _lock:
            old = next((r for r in _state["requests"] if r["kind"] == "song" and r.get("deezer") == int(deezer_id)), None)
    with _lock:
        if old is not None:
            if old.get("source") == "soulseek" and old.get("slsk_got"):
                old.setdefault("slsk_bad", []).append(old["slsk_got"])
                blocked = True
            old.pop("slsk_got", None)
            old.pop("source", None)
            if track_file and track_file.get("albumId"):
                old["album_id"] = track_file["albumId"]
                old["artist_id"] = lartist["id"]
            _retry(old)  # Straight to a new search when Lidarr already knows the album
            old.update({"files_after": now, "redone": int(old.get("redone") or 0) + 1, "created": old.get("created") or now})
            old.pop("takes_checked", None)
            _state["requests"].remove(old)
            _state["requests"].insert(0, old)
        _save()
    _kick.set()
    words = f"Deleted {title}. Tower is finding a new copy"
    words += " (that copy won't be picked again)." if blocked else "."
    return {"ok": True, "words": words, "request": _public(old) if old else None}


def delete_song(item_id, token, user_id):
    """Ben's "Delete song" (2026-11-17, a song he just doesn't want): deletes the file from Tower, takes it off
    Jellyfin, and makes sure Lidarr never gets it back (its album is unmonitored, and the artist too when none
    of their songs are left). Nothing is asked for again."""
    s = _jf_song(item_id, token, user_id)
    path = s.get("Path") or ""
    real = os.path.realpath(path) if path else ""
    if not real.startswith(os.path.realpath(MUSIC) + os.sep):
        raise MusicError(400, "Only songs in Tower's Music folder can be deleted from here.")
    title = s.get("Name") or ""
    artist = (s.get("AlbumArtist") or (s.get("Artists") or [""])[0] or "").strip()
    track_file, lartist = (None, None)
    try:
        track_file, lartist = _lidarr_file(real)
    except MusicError as e:
        log(f"couldn't look the song up in Lidarr: {e.message}")
    lidarr_done = False
    if track_file:
        try:
            lidarr("DELETE", f"/trackfile/{track_file['id']}")
        except MusicError as e:
            log(f"Lidarr couldn't delete the file, so Benflix will: {e.message}")
        # Lidarr watches whole albums, not single songs: an unwatched album never fetches the missing song again
        if track_file.get("albumId"):
            try:
                lidarr("PUT", "/album/monitor", {"albumIds": [track_file["albumId"]], "monitored": False})
                lidarr_done = True
            except MusicError as e:
                log(f"couldn't stop Lidarr watching that album: {e.message}")
        # No songs of this artist left: Lidarr stops watching the artist as well
        if lartist and lartist.get("id"):
            try:
                left = lidarr("GET", "/trackfile", artistId=lartist["id"]) or []
                if not left and lartist.get("monitored"):
                    a = lidarr("GET", f"/artist/{lartist['id']}") or {}
                    if a.get("id"):
                        a["monitored"] = False
                        lidarr("PUT", f"/artist/{a['id']}", a)
            except MusicError as e:
                log(f"couldn't stop Lidarr watching {artist}: {e.message}")
    if os.path.exists(real):
        try:
            os.remove(real)
        except OSError:
            raise MusicError(500, "Tower wouldn't let Benflix delete the file. Check the Music folder's permissions.")
    _quiet_remove(os.path.splitext(real)[0] + ".lrc")
    # The album folder has no songs left: its cover and extras go too (never a folder that still has music)
    folder = os.path.dirname(real)
    try:
        if os.path.realpath(folder) != os.path.realpath(MUSIC):
            rest = os.listdir(folder)
            if not any(os.path.splitext(f)[1].lower() in AUDIO or os.path.isdir(os.path.join(folder, f)) for f in rest):
                for f in rest:
                    _quiet_remove(os.path.join(folder, f))
                os.rmdir(folder)
    except OSError:
        pass
    _tell_jellyfin([path], kind="Deleted")
    _have_reset()
    # Benflix's own ask for this song goes away, so it's never retried
    with _lock:
        gone = [
            r
            for r in _state["requests"]
            if r["kind"] == "song" and same_song(title, _wanted_title(r)) and same(r.get("artist"), artist)
        ]
        for r in gone:
            _state["requests"].remove(r)
        _save()
    log(f"deleted {artist} - {title} for good" + ("" if lidarr_done or not track_file else " (Lidarr may still watch the album)"))
    return {"ok": True, "words": f"Deleted {title}. It won't be downloaded again."}


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
    # Copies Ben deleted with "Delete and find again" are never taken again
    bad = {(b.get("user"), b.get("file")) for b in r.get("slsk_bad") or []}
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
            if key in bad or key in seen:
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
    r.update({"state": "ready", "ready_at": time.time(), "note": None, "source": "soulseek", "slsk_got": {"user": c["user"], "file": c["file"]}})
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
    try:
        out["seconds"] = float((j.get("format") or {}).get("duration") or 0)
    except (TypeError, ValueError):
        out["seconds"] = 0

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


# ---------- The same song twice in different albums of one artist ----------


GENERIC_TITLES = {"intro", "outro", "interlude", "skit", "prelude", "untitled", "bonus track", "hidden track", "reprise"}


def _song_key(path):
    """'Music/Kid Cudi/Some Album/05 - Tequila Shots.mp3' -> 'tequila shots' (other takes like '(Live)' stay different)"""
    stem = re.sub(r"^\d{1,2}-?\d{0,3}\s+-\s+", "", os.path.splitext(os.path.basename(path))[0])
    return norm(clean_title(stem))


def _artist_doubles(mode, say, undo):
    """Finds songs that are in Music twice under the same artist (say an album copy from SoulSync and another
    from a request, in folders named a little differently) and puts the spare copies in Music Leftovers/Duplicates.
    Keeps the better copy: the better sound first, then the one in the fuller album. Returns how many."""
    if not os.path.isdir(MUSIC):
        return 0
    count, gone = 0, []
    for artist in sorted(os.listdir(MUSIC)):
        top = os.path.join(MUSIC, artist)
        if artist.startswith(".") or not os.path.isdir(top) or artist.lower() in ("various artists", "va", "soundtrack", "soundtracks"):
            continue
        groups = {}
        for f in _audio_files(top):
            k = _song_key(f)
            if k:
                groups.setdefault(k, []).append(f)
        for k, files in groups.items():
            if len(files) < 2 or len({os.path.dirname(f) for f in files}) < 2:
                continue  # Two copies in one album folder are left to the tidy-up's own check
            if k in GENERIC_TITLES or re.fullmatch(r"(intro|outro|interlude|skit|untitled|track)( \d+)?", k):
                continue  # Different songs often share these names
            info = {f: probe(f) for f in files}
            # Only the same length counts, so an album version and a shorter radio edit are both kept, and
            # nothing is moved when the length can't be read
            if not all(info[f].get("seconds") for f in files):
                continue
            base = max(files, key=lambda f: sum(abs(info[f]["seconds"] - info[g]["seconds"]) <= 3 for g in files))
            files = [f for f in files if abs(info[f]["seconds"] - info[base]["seconds"]) <= 3]
            if len(files) < 2 or len({os.path.dirname(f) for f in files}) < 2:
                continue
            in_folder = {f: len([n for n in os.listdir(os.path.dirname(f)) if os.path.splitext(n)[1].lower() in AUDIO]) for f in files}

            def worth(f):
                return (min(info[f].get("bitrate") or 0, 256000), in_folder[f], os.path.getsize(f))

            keep = max(files, key=worth)
            for f in files:
                if f == keep:
                    continue
                count += 1
                say(f"Same song twice: {os.path.relpath(f, MUSIC)} (keeping {os.path.relpath(keep, MUSIC)})")
                if mode == "preview":
                    continue
                to = _free_name(os.path.join(LEFT, "Duplicates", os.path.relpath(f, MUSIC)))
                try:
                    _move(f, to)
                except OSError as e:
                    say(f"Couldn't move {os.path.relpath(f, MUSIC)}: {e}")
                    continue
                undo.append(["move", f, to])
                gone.append(f)
                lrc = os.path.splitext(f)[0] + ".lrc"
                if os.path.exists(lrc):
                    lto = os.path.splitext(to)[0] + ".lrc"
                    _move(lrc, lto)
                    undo.append(["move", lrc, lto])
    if gone:
        try:
            _tell_jellyfin(gone, kind="Deleted")
        except Exception as e:
            log(f"couldn't tell Jellyfin about the doubles: {e!r}")
        _have_reset()
    return count


def _doubles_daily():
    """Once a day (and soon after Benflix's helper starts) the spare copies of songs go to Music Leftovers"""
    last = (_read("doubles.json") or {}).get("at", 0)
    if time.time() - last < 24 * 3600:
        return
    with _lock:
        if _tidy["running"]:
            return
        _tidy["running"] = True
    try:
        undo = []
        n = _artist_doubles("apply", lambda t: log(t), undo)
        if undo:
            _write(f"tidy-{time.strftime('%Y%m%d-%H%M%S')}.json", undo)
            _remove_empty(MUSIC)
        if n:
            log(f"put {n} spare copies of songs in Music Leftovers/Duplicates")
        _write("doubles.json", {"at": time.time(), "moved": n})
    finally:
        _tidy["running"] = False


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
        if os.path.abspath(root) == os.path.abspath(MUSIC) or mode == "apply":
            # The same song in two albums of one artist (looked at across all of Music)
            counts["dupes"] += _artist_doubles(mode, _tidy_say, undo)
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


def playlist_import(link, name, make=True):
    """make=False (Ben): only get the songs, no playlist in Jellyfin (for someone else's playlist)"""
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
        "make": bool(make),
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
        make = p.get("make", True)
        if new and make:
            changed = True
            _playlist_add(p, new)
        if all(s.get("state") in ("here", "not_found") for s in p["songs"]) and (
            not make or all(s.get("added") or s.get("state") != "here" for s in p["songs"])
        ):
            if make:
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
        words = (f"Ready in Jellyfin: {here} songs" if p.get("make", True) else f"Songs are on Benflix: {here}") + (f", {missing} couldn't be found" if missing else "")
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


# Daily mixes by style (Ben 2026-11-16): each Daily mix keeps to one kind of music. A song's style is the first of
# these its genres fit (whole words), or else its own first genre.
STYLES = [
    ("Metal", ["metal", "metalcore", "deathcore", "djent", "hardcore"]),
    ("Rap and hip hop", ["hip hop", "hip-hop", "rap", "trap", "drill", "grime"]),
    ("R&B and soul", ["r&b", "rnb", "soul", "neo soul", "funk", "motown"]),
    ("Country", ["country", "americana", "bluegrass"]),
    ("Electronic and dance", ["electronic", "electronica", "edm", "dance", "house", "techno", "trance", "dubstep", "drum and bass", "drum & bass", "electro", "synthwave"]),
    ("K-pop and J-pop", ["k-pop", "kpop", "j-pop", "jpop", "j-rock", "anime"]),
    ("Latin", ["latin", "reggaeton", "salsa", "bachata", "cumbia"]),
    ("Punk and emo", ["punk", "pop punk", "emo", "post-hardcore"]),
    ("Rock", ["rock", "grunge", "alternative", "indie rock", "classic rock", "hard rock"]),
    ("Indie and folk", ["indie", "folk", "acoustic", "singer-songwriter", "singer songwriter"]),
    ("Pop", ["pop", "dance pop", "synthpop", "electropop"]),
    ("Jazz and blues", ["jazz", "blues", "swing"]),
    ("Classical and soundtracks", ["classical", "soundtrack", "score", "orchestral", "piano", "game", "film"]),
    ("Reggae", ["reggae", "ska", "dancehall"]),
]
STYLES = [(n, w, re.compile(r"(?<![a-z0-9])(" + "|".join(re.escape(x) for x in w) + r")(?![a-z0-9])")) for n, w in STYLES]
DAILY_MIXES = 4


def _style(song):
    gs = [g.strip().lower() for g in song.get("Genres") or [] if g.strip()]
    if not gs:
        return None
    joined = " | ".join(gs)
    for name, _w, rx in STYLES:
        if rx.search(joined):
            return name
    return (song.get("Genres") or [""])[0].strip() or None


def _slug(text):
    return re.sub(r"[^a-z0-9]+", "-", (text or "").lower()).strip("-") or "mix"


def _day_part(hour):
    """The part of the day the daylist is for"""
    if 5 <= hour < 11:
        return "morning"
    if 11 <= hour < 17:
        return "afternoon"
    if 17 <= hour < 22:
        return "evening"
    return "night"


# The daylist (Music redesign 3): (name words, slowest and fastest speed, styles it leans to)
DAYLIST = {
    "morning": ("easy", 70, 118, ("Indie and folk", "Pop", "R&B and soul", "Jazz and blues", "Country")),
    "afternoon": ("upbeat", 100, 170, ("Pop", "Rock", "Electronic and dance", "Rap and hip hop", "K-pop and J-pop")),
    "evening": ("feel-good", 85, 135, ("R&B and soul", "Pop", "Rock", "Country", "Latin", "Indie and folk")),
    "night": ("late-night", 55, 112, ("R&B and soul", "Electronic and dance", "Indie and folk", "Jazz and blues", "Classical and soundtracks")),
}


def mixes_for(token, user_id):
    part = _day_part(time.localtime().tm_hour)
    day = time.strftime("%Y-%m-%d") + "-" + part
    with _mix_lock:
        cached = _mixes.get(user_id)
        if cached and cached[0] == day:
            return cached[1]
    import datetime
    import random

    rnd = random.Random(f"{user_id}-{day}")
    now = datetime.datetime.now(datetime.timezone.utc)
    played = _songs(token, user_id, "Filters=IsPlayed&SortBy=PlayCount,DatePlayed&SortOrder=Descending&Limit=400")
    liked = _songs(token, user_id, "Filters=IsFavorite&Limit=300")
    fresh = _songs(token, user_id, "SortBy=Random&Limit=200")
    lately = _songs(token, user_id, "Filters=IsPlayed&SortBy=DatePlayed&SortOrder=Descending&Limit=300")
    # Christmas songs only mix in from Thanksgiving to Boxing Day (they have their own In season playlist)
    xmas_ok = _christmas_time()
    if not xmas_ok:
        played, liked, fresh, lately = ([x for x in lst if not _is_christmas(x)] for lst in (played, liked, fresh, lately))
    # Don't play this artist in my mixes (Music redesign 3)
    blocked = _no_artists(user_id)
    if blocked:
        ok = lambda x: norm(_mix_artist(x)[0]) not in blocked  # noqa: E731
        played, liked, fresh, lately = ([x for x in lst if ok(x)] for lst in (played, liked, fresh, lately))
    # Skip learning (Music redesign 3): songs skipped early again and again stay out
    skipped = _skipped(user_id)
    if skipped:
        played, liked, fresh, lately = ([x for x in lst if x["Id"] not in skipped] for lst in (played, liked, fresh, lately))
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

    def daily(n, artists, style=None, extra=()):
        names = {a[0] for a in artists}
        # All of these artists' songs, not only the ones already played, so the "few you've barely played" are real
        ids = [a[1] for a in artists if a[1]]
        more = _songs(token, user_id, f"ArtistIds={','.join(ids)}&SortBy=Random&Limit=200") if ids else []
        if not xmas_ok:
            more = [x for x in more if not _is_christmas(x)]
        if blocked:
            more = [x for x in more if norm(_mix_artist(x)[0]) not in blocked]
        both = dict(pool)
        both.update({x["Id"]: x for x in list(more) + list(extra) if x["Id"] not in both})
        mine = [s for s in both.values() if _mix_artist(s)[0] in names or (style and s["Id"] in {e["Id"] for e in extra})]
        if style:
            mine = [s for s in mine if _style(s) == style]
        rnd.shuffle(mine)
        # Mostly songs they know, plus a few they've barely played
        known = [s for s in mine if count(s) > 0][:22]
        rare = [s for s in mine if count(s) == 0][:8]
        songs = known + rare
        rnd.shuffle(songs)
        who = ", ".join(a[0] for a in artists[:3])
        line = f"{style} · {who}" if style and who else (who or style or "A bit of everything")
        return _mix(f"daily-{_slug(style)}" if style else f"daily-{n}", f"Daily mix {n}", line, songs)

    # One Daily mix per style they play most (up to 4), each with that style's top artists
    style_weight, style_artists = {}, {}
    liked_ids = {x["Id"] for x in liked}
    for s in played + liked:
        st = _style(s)
        if not st:
            continue
        w = count(s) + (3 if s["Id"] in liked_ids else 0)
        style_weight[st] = style_weight.get(st, 0) + w
        a = _mix_artist(s)
        if a[0]:
            style_artists.setdefault(st, {})
            style_artists[st][a] = style_artists[st].get(a, 0) + w
    for st in sorted(style_weight, key=lambda k: -style_weight[k]):
        if len(mixes) >= DAILY_MIXES:
            break
        artists = [a for a, _ in sorted(style_artists.get(st, {}).items(), key=lambda x: -x[1])][:6]
        m = daily(len(mixes) + 1, artists, st, extra=[x for x in fresh if _style(x) == st])
        if m:
            mixes.append(m)
    if not mixes:
        # Not enough genres on the songs to tell styles apart: the top artists, split in two
        if top:
            half = max(1, min(len(top), 8) // 2)
            mixes.append(daily(1, top[:half]))
            # Only one or two artists played so far: a second Daily mix would be the same songs again
            if top[half : half * 2]:
                mixes.append(daily(2, top[half : half * 2]))
        else:
            songs = list(fresh)
            rnd.shuffle(songs)
            mixes.append(_mix("daily-1", "Daily mix 1", "A bit of everything", songs))
    mixes = [m for m in mixes if m]
    # On repeat (Ben 2026-11-16): the songs played most in the last 30 days, not most ever
    recent = [s for s in lately if (_played_days_ago(s, now) or 999) <= 30 and count(s) >= 2]
    recent.sort(key=lambda s: -count(s))
    repeat = _mix("repeat", "On repeat", "Your most played songs lately", recent[:30], limit=30)
    if not repeat:
        repeat = _mix("repeat", "On repeat", "The songs you play most", [s for s in played if count(s) >= 2][:30], limit=30)
    mixes.append(repeat)
    for i, (name, aid) in enumerate([a for a in top if a[1]][:2]):
        j = _as_user(token, f"/Items/{aid}/InstantMix?UserId={user_id}&Limit=40&{SONG_FIELDS}") or {}
        radio = [x for x in j.get("Items") or [] if x.get("Id") and (xmas_ok or not _is_christmas(x)) and norm(_mix_artist(x)[0]) not in blocked]
        mixes.append(_mix(f"radio-{i + 1}", f"{name} radio", f"{name} and music like it", radio))
    # The decade they play most (by the year each song first came out, when Tower knows it)
    decades = {}
    for s in played:
        y = _song_year(s)
        if y:
            decades[y // 10 * 10] = decades.get(y // 10 * 10, 0) + count(s)
    if decades:
        d = max(decades, key=decades.get)
        # The songs they play most from that decade (the Decades row already has a mix of every song from it)
        theirs = [s for s in played if (_song_year(s) or 0) // 10 * 10 == d][:30]
        if len(theirs) < 15:
            theirs += [x for x in fresh if (_song_year(x) or 0) // 10 * 10 == d]
        mixes.append(_mix(f"decade-{d}", f"Your {d}s", f"Your most played songs from the {d}s", theirs))
    else:
        songs = list(fresh)
        rnd.shuffle(songs)
        mixes.append(_mix("discover", "Something new", "Songs you haven't played yet", [s for s in songs if count(s) == 0]))
    # ---- Music redesign 3, part 4: the new mixes ----
    keep = lambda lst: [x for x in lst if (xmas_ok or not _is_christmas(x)) and norm(_mix_artist(x)[0]) not in blocked and x["Id"] not in skipped]  # noqa: E731
    first = []
    # daylist: changes with the part of the day (by the songs' speed when Tower knows it, else their style)
    words, slow, fast, leans = DAYLIST[part]
    both = list({x["Id"]: x for x in played + liked + fresh}.values())
    by_speed = [x for x in both if slow <= (_song_bpm(x) or 0) <= fast]
    by_style = [x for x in both if not _song_bpm(x) and _style(x) in leans]
    pick = by_speed + by_style
    rnd.shuffle(pick)
    pick.sort(key=lambda x: 0 if count(x) > 0 else 1)
    picked = pick[:22] + [x for x in pick[22:] if count(x) == 0][:8]
    rnd.shuffle(picked)
    weekday = time.strftime("%A")
    styles_here = []
    for x in picked:
        st = _style(x)
        if st and st not in styles_here:
            styles_here.append(st)
    first.append(_mix("daylist", f"daylist · {words} {weekday} {part}", ", ".join(styles_here[:3]) or "Made for this time of day", picked))
    # Discover weekly and Release radar: songs that aren't on Benflix yet, to preview and ask for (made on Tower when opened)
    first.append({"key": "discover-weekly", "name": "Discover weekly", "line": "20 new songs for you, every Monday", "songs": [], "covers": [], "adds": True, "catalogue": True})
    first.append({"key": "release-radar", "name": "Release radar", "line": "New songs from artists you play, every Friday", "songs": [], "covers": [], "adds": True, "catalogue": True})
    # Fresh on Benflix: songs that came in over the last 2 weeks
    new = keep(_songs(token, user_id, "SortBy=DateCreated&SortOrder=Descending&Limit=120"))
    cut = (now - datetime.timedelta(days=14)).strftime("%Y-%m-%d")
    new = [x for x in new if (x.get("DateCreated") or "")[:10] >= cut]
    later = [_mix("fresh", "Fresh on Benflix", "Songs that came in over the last 2 weeks", new, limit=60)]
    # Your top songs of the year: all December, and the first week of January for the year before
    if now.month == 12 or (now.month == 1 and now.day <= 7):
        year = now.year if now.month == 12 else now.year - 1
        this_year = [x for x in played if ((x.get("UserData") or {}).get("LastPlayedDate") or "")[:4] == str(year)]
        this_year.sort(key=lambda x: -count(x))
        later.append(_mix(f"top-{year}", f"Your top songs {year}", f"The songs you played most in {year}", this_year, limit=50))
    # This is: each of the 10 artists you play most, their songs you play most first
    for name, aid in [a for a in top if a[1]][:10]:
        theirs = keep(_songs(token, user_id, f"ArtistIds={aid}&SortBy=PlayCount,CommunityRating,SortName&SortOrder=Descending&Limit=40"))
        later.append(_mix(f"thisis-{aid}", f"This is {name}", f"The essential {name}, the ones you play most first", theirs, limit=40))
    mixes = first + mixes[: DAILY_MIXES + 5] + later
    out = [m for m in mixes if m]
    # Songs this person said aren't for a playlist (Not for this playlist) stay out of it
    out = [m for m in (m if m.get("catalogue") else _apply_hides(user_id, m) for m in out) if m]
    answer = {"mixes": out[:30]}
    if answer["mixes"]:  # an empty answer (server busy, no plays yet) is tried again next time
        with _mix_lock:
            _mixes[user_id] = (day, answer)
    return answer


# ---------- Not for this playlist: songs a person took out of a made-for-you or themed playlist ----------


def _hides(user_id):
    return ((_read("mix-hides.json") or {}).get(user_id) or {}) if _dir else {}


def _apply_hides(user_id, m, need=8):
    gone = set(_hides(user_id).get(m["key"]) or [])
    if not gone:
        return m
    m = dict(m)
    m["songs"] = [i for i in m["songs"] if i not in gone]
    return m if len(m["songs"]) >= min(need, 1) else None


def mix_hide(user_id, key, song_id):
    if not _dir:
        raise MusicError(503, "Music is off on Tower right now.")
    if not key or not song_id or not re.fullmatch(r"[\w-]{1,80}", key) or not re.fullmatch(r"[\w-]{1,64}", song_id):
        raise MusicError(400, "Which song and playlist?")
    with _lock:
        all_hides = _read("mix-hides.json") or {}
        mine = all_hides.setdefault(user_id, {})
        lst = mine.setdefault(key, [])
        if song_id not in lst:
            lst.append(song_id)
            del lst[:-500]
        _write("mix-hides.json", all_hides)
    # Make this person's playlists again next time they look
    with _mix_lock:
        _mixes.pop(user_id, None)
    with _theme_lock:
        _themes.pop(user_id, None)
    return {"ok": True}


# ---------- Your music places: Recently played, pins, Recent searches (Music redesign 3, Ben 2026-11-16) ----------
# Kept per profile on Tower so the TV, the phones and the browser all show the same Jump back in, Recently played,
# pins in Your library and Recent searches. Recently played keeps 3 months (at most 300), searches the last 10,
# pins 4 (Liked songs is always first and doesn't count).

ME_KINDS = ("album", "playlist", "mix", "artist", "liked", "songs", "song")
ME_RECENT_DAYS = 92
ME_RECENT_MAX = 300
ME_SEARCH_MAX = 10
ME_PIN_MAX = 4


def _me_all():
    return _read("music-me.json") or {}


def _me_clean(entry):
    kind = str(entry.get("kind") or "")
    item = str(entry.get("id") or "")
    if kind not in ME_KINDS or not re.fullmatch(r"[\w-]{0,80}", item):
        raise MusicError(400, "What did you play?")
    return {"kind": kind, "id": item, "name": str(entry.get("name") or "")[:120]}


def me_get(user_id):
    mine = _me_all().get(user_id) or {}
    cut = time.time() - ME_RECENT_DAYS * 86400
    return {
        "recents": [r for r in mine.get("recents") or [] if (r.get("at") or 0) >= cut],
        "searches": (mine.get("searches") or [])[:ME_SEARCH_MAX],
        "pins": (mine.get("pins") or [])[:ME_PIN_MAX],
        "noartists": mine.get("noartists") or [],
        "adds": mine.get("adds") or {},
        "sent": [x for x in mine.get("sent") or [] if (x.get("at") or 0) >= time.time() - 14 * 86400],
    }


SKIP_DAYS = 60
SKIPS_OUT = 3


def _skipped(user_id):
    """Songs this profile skipped early 3 times or more in the last 60 days (they stay out of the mixes)"""
    try:
        cut = time.time() - SKIP_DAYS * 86400
        return {k for k, v in ((_me_all().get(user_id) or {}).get("skips") or {}).items() if isinstance(v, list) and len(v) > 1 and v[0] >= SKIPS_OUT and v[1] >= cut}
    except Exception:
        return set()


def _no_artists(user_id):
    """Artists this profile said not to play in their mixes (lowercase, simple letters)"""
    try:
        return {norm(n) for n in (_me_all().get(user_id) or {}).get("noartists") or [] if n}
    except Exception:
        return set()


def me_route(method, path, user_id, read_json):
    if not _dir:
        raise MusicError(503, "Music is off on Tower right now.")
    if method == "GET" and path == "/api/music/me":
        return me_get(user_id)
    if method != "POST":
        raise MusicError(404, "Not here.")
    body = read_json() or {}
    with _lock:
        everyone = _me_all()
        mine = everyone.setdefault(user_id, {})
        now = time.time()
        if path == "/api/music/me/played":
            entry = _me_clean(body)
            entry["at"] = now
            same = lambda r: r.get("kind") == entry["kind"] and r.get("id") == entry["id"]  # noqa: E731
            recents = [entry] + [r for r in mine.get("recents") or [] if not same(r)]
            cut = now - ME_RECENT_DAYS * 86400
            mine["recents"] = [r for r in recents if (r.get("at") or 0) >= cut][:ME_RECENT_MAX]
        elif path == "/api/music/me/search":
            q = " ".join(str(body.get("q") or "").split())[:80]
            searches = [s for s in mine.get("searches") or [] if s.get("q", "").lower() != q.lower()]
            if body.get("clear"):
                searches = []
            elif body.get("remove"):
                pass
            elif q:
                searches = [{"q": q, "at": now, "opened": str(body.get("opened") or "")[:120]}] + searches
            mine["searches"] = searches[:ME_SEARCH_MAX]
        elif path == "/api/music/me/pin":
            entry = _me_clean(body)
            pins = [p for p in mine.get("pins") or [] if not (p.get("kind") == entry["kind"] and p.get("id") == entry["id"])]
            if body.get("on", True):
                if len(pins) >= ME_PIN_MAX:
                    raise MusicError(409, f"You can pin {ME_PIN_MAX}. Unpin one first.")
                pins.append(entry)
            mine["pins"] = pins
        elif path == "/api/music/me/add":
            # Songs to add on your own playlist (Music redesign 3): an asked-for song joins the playlist once it's in.
            # The app adds it when it finds the song on Benflix, then takes it off this list ({"remove": true}).
            pl = str(body.get("playlist") or "")
            if not re.fullmatch(r"[\w-]{1,64}", pl):
                raise MusicError(400, "Which playlist?")
            artist = " ".join(str(body.get("artist") or "").split())[:120]
            title = " ".join(str(body.get("title") or "").split())[:160]
            adds = mine.setdefault("adds", {})
            lst = [a for a in adds.get(pl) or [] if not (norm(a.get("artist")) == norm(artist) and norm(a.get("title")) == norm(title))]
            if not body.get("remove") and title:
                lst.append({"artist": artist, "title": title, "at": now})
            lst = [a for a in lst if now - (a.get("at") or now) < 60 * 86400][-50:]
            if lst:
                adds[pl] = lst
            else:
                adds.pop(pl, None)
        elif path == "/api/music/me/skip":
            # Skip learning (Music redesign 3): a song skipped in its first 30 seconds 3 times stays out of the mixes
            song = str(body.get("id") or "")
            if not re.fullmatch(r"[\w-]{8,64}", song):
                raise MusicError(400, "Which song?")
            skips = {k: v for k, v in (mine.get("skips") or {}).items() if now - (v[1] if isinstance(v, list) and len(v) > 1 else 0) < SKIP_DAYS * 86400}
            n = (skips.get(song) or [0, 0])[0] + 1
            skips[song] = [n, now]
            mine["skips"] = dict(sorted(skips.items(), key=lambda kv: -kv[1][1])[:2000])
            if n == SKIPS_OUT:
                with _mix_lock:
                    _mixes.pop(user_id, None)
        elif path == "/api/music/me/send":
            # Send to a profile (Music redesign 3): it shows in their Home news
            to = str(body.get("to") or "")
            if not re.fullmatch(r"[\w-]{8,64}", to) or to == user_id:
                raise MusicError(400, "Who should it go to?")
            if to not in {u["id"] for u in music_people(user_id)}:
                raise MusicError(403, "You can't send music to that profile.")
            entry = _me_clean(body)
            entry.update({"at": now, "from": str(body.get("from_name") or "")[:60], "id_sent": uuid.uuid4().hex[:10]})
            theirs = everyone.setdefault(to, {})
            theirs["sent"] = ([entry] + [x for x in theirs.get("sent") or [] if now - (x.get("at") or 0) < 14 * 86400])[:20]
        elif path == "/api/music/me/noartist":
            # Don't play this artist in my mixes (Music redesign 3): Made for you leaves them out from now on
            name = " ".join(str(body.get("name") or "").split())[:120]
            if not name:
                raise MusicError(400, "Which artist?")
            rest = [n for n in mine.get("noartists") or [] if norm(n) != norm(name)]
            mine["noartists"] = (rest + [name]) if body.get("on", True) else rest
            with _mix_lock:
                _mixes.pop(user_id, None)
        else:
            raise MusicError(404, "Not here.")
        _write("music-me.json", everyone)
    return me_get(user_id)


# ---------- Artist pages (Music redesign 3): the artist in the music catalogue, found by name ----------


def _artist_id(name):
    """The catalogue's id for an artist on Benflix (None when it can't be found). Kept for 30 days."""
    if not name:
        return None

    def load():
        try:
            found = deezer("/search/artist", q=name, limit=10).get("data") or []
        except MusicError:
            return None
        for a in found:
            if same(a.get("name"), name):
                return a.get("id")
        return 0

    key = f"artistid-{norm(name)}"
    got = _cached(key, load, keep=30 * 24 * 3600)
    if got is None:
        with _lock:
            _found_cache.pop(key, None)
    return got or None


def artist_find(name):
    """Popular (their top 5 songs worldwide, the ones not in Music marked), every album and single newest first
    (the ones not in Music marked "missing") and Fans also like, for the artist page. Empty when not found."""
    aid = _artist_id(name)
    if not aid:
        return {"artist": None, "songs": [], "albums": [], "related": []}
    out = artist_page(aid)
    out["songs"] = out["songs"][:5]
    try:
        _have_songs()
        have_albums = set(_have["albums"])
    except OSError:
        have_albums = set()
    for al in out["albums"]:
        al["have"] = _album_owned(al, have_albums)
    try:
        related = deezer(f"/artist/{aid}/related", limit=12).get("data") or []
    except MusicError:
        related = []
    out["related"] = [_artist(a) for a in related]
    return out


def music_people(user_id):
    """The profiles you can send music to: everyone else on Benflix except Kids, hidden and switched-off profiles"""
    j = _jellyfin("GET", "/Users") or []
    out = []
    for u in j if isinstance(j, list) else []:
        pol = u.get("Policy") or {}
        if not u.get("Id") or u["Id"].replace("-", "") == str(user_id).replace("-", ""):
            continue
        if pol.get("IsDisabled") or pol.get("IsHidden") or pol.get("MaxParentalRating") is not None or (u.get("Name") or "").lower() in ("kids", "guest"):
            continue
        out.append({"id": u["Id"], "name": u.get("Name") or ""})
    return sorted(out, key=lambda x: x["name"].lower())


_lrc = {"at": 0, "songs": []}


def _lrc_index():
    """Every song's saved words (.lrc next to the song), looked at every 30 minutes"""
    if time.time() - _lrc["at"] < 1800 and _lrc["songs"]:
        return _lrc["songs"]
    out = []
    if os.path.isdir(MUSIC):
        for base, dirs, files in os.walk(MUSIC):
            dirs[:] = [d for d in dirs if not d.startswith(".")]
            for f in files:
                if not f.lower().endswith(".lrc"):
                    continue
                try:
                    with open(os.path.join(base, f), encoding="utf-8", errors="ignore") as fh:
                        raw = fh.read(20000)
                except OSError:
                    continue
                lines = [re.sub(r"\[[^\]]*\]", "", x).strip() for x in raw.splitlines()]
                lines = [x for x in lines if x]
                rel = os.path.relpath(base, MUSIC).split(os.sep)
                stem = re.sub(r"^\d{1,2}-?\d{0,3}\s+-\s+", "", os.path.splitext(f)[0])
                out.append({"artist": main_artist(rel[0]) if rel and rel[0] != "." else "", "title": stem, "lines": lines})
    _lrc.update({"at": time.time(), "songs": out})
    return out


def lyrics_search(q):
    """Songs on Benflix whose words have all of these words, with the line that matched (search by lyrics)"""
    words = [w for w in re.findall(r"[\w']+", (q or "").lower()) if len(w) > 1]
    if len(words) < 2:
        return {"songs": []}
    found = []
    for song in _lrc_index():
        text = " ".join(song["lines"]).lower()
        if all(w in text for w in words):
            line = next((x for x in song["lines"] if sum(w in x.lower() for w in words) >= max(1, len(words) // 2)), song["lines"][0] if song["lines"] else "")
            found.append({"artist": song["artist"], "title": song["title"], "line": line[:140]})
            if len(found) >= 10:
                break
    return {"songs": found}


# ---------- Artist photos (Music redesign 3): artists with no picture get one from the catalogue overnight ----------


def _artist_photos_nightly():
    """Once a night (1 to 5 in the morning), artists in Jellyfin with no picture get the catalogue's photo"""
    hour = time.localtime().tm_hour
    last = (_read("artist-photos.json") or {}).get("at", 0)
    if not (1 <= hour < 5) or time.time() - last < 20 * 3600:
        return
    _write("artist-photos.json", {"at": time.time(), "added": 0})
    j = _jellyfin("GET", "/Artists?Recursive=true&Fields=ImageTags&EnableImages=true&Limit=5000") or {}
    added = 0
    for a in j.get("Items") or []:
        if (a.get("ImageTags") or {}).get("Primary") or not a.get("Id") or not a.get("Name"):
            continue
        if a["Name"].lower() in ("various artists", "unknown", "unknown artist"):
            continue
        try:
            aid = _artist_id(a["Name"])
            if not aid:
                continue
            info = deezer(f"/artist/{aid}")
            url = info.get("picture_xl") or info.get("picture_big") or ""
            if not url or "/artist//" in url:
                continue
            pic = _http(url, raw=True, timeout=30)
            if not pic or len(pic) < 2000:
                continue
            status, _, _ = _jf("POST", f"/Items/{a['Id']}/Images/Primary", token=_state["admin"].get("token"), body=base64.b64encode(pic), device_id="benflix-music", content_type="image/jpeg")
            if status < 300:
                added += 1
            time.sleep(0.5)
        except Exception as e:
            log(f"artist photo for {a.get('Name')}: {e!r}")
        if added >= 300:
            break
    _write("artist-photos.json", {"at": time.time(), "added": added})
    if added:
        log(f"added {added} artist photos from the music catalogue")


# ---------- Themed playlists: holidays in season, decades, genres, moods, activities, throwbacks, seasons ----------
# Ben 2026-11-16 ("like Spotify does"). Tower makes them each morning from the person's own music, the same way as
# Made for you. A song joins a theme when its name or album has the theme's words (Christmas, ghost, summer...),
# its genre fits, it is on the music catalogue's own playlists for that theme, or Ben asked for it from that theme's
# "Songs to add" row. Everyone sees a theme once it has 15 songs (Ben sooner). Every profile but Kids gets Songs to add;
# asks from other profiles wait for Ben's Approve, like the rest of music requests (Ben 2026-11-16).

THEME_MIN = 15
THEME_MIN_ADMIN = 3
THEME_SONGS = 60
THEME_ROWS = {
    "season": "In season",
    "decade": "Decades",
    "genre": "Genres",
    "mood": "Moods and moments",
    "lofi": "LoFi",
    "throwback": "Throwbacks",
}

_themes = {}  # user id -> (day, answer)
_theme_lock = threading.Lock()
_theme_warm = {"running": False}


def _thanksgiving(year):
    import datetime

    d = datetime.date(year, 11, 1)
    first_thu = d + datetime.timedelta(days=(3 - d.weekday()) % 7)
    return first_thu + datetime.timedelta(days=21)


def _holiday_dates(key, today):
    """When a holiday or season theme shows for everyone: (first day, last day) of the next one that hasn't ended"""
    import datetime

    spans = {
        "halloween": ((10, 1), (10, 31)),
        "newyear": ((12, 27), (1, 1)),
        "valentines": ((2, 1), (2, 14)),
        "july4": ((6, 28), (7, 5)),
        "stpatricks": ((3, 10), (3, 17)),
        "summer": ((6, 1), (8, 31)),
        "fall": ((9, 1), (11, 30)),
        "winter": ((12, 1), (2, 28)),
    }
    if key != "christmas" and key not in spans:
        return None
    for y in (today.year - 1, today.year, today.year + 1):
        if key == "christmas":
            start, end = _thanksgiving(y), datetime.date(y, 12, 26)
        else:
            (sm, sd), (em, ed) = spans[key]
            start = datetime.date(y, sm, sd)
            end = datetime.date(y if (em, ed) >= (sm, sd) else y + 1, em, ed)
        if end >= today:
            return start, end
    return None


# words: whole words in the song or album name. genres: bits of a genre name. dz: the catalogue search for its
# playlists (Songs to add, and songs of yours on them). Moods and activities are a best guess from genre.
THEMES = [
    {"key": "christmas", "group": "season", "name": "Christmas", "line": "Christmas songs from your music", "dz": "christmas hits",
     "genres": ["christmas", "holiday"],
     "words": ["christmas", "xmas", "santa claus", "santa baby", "dear santa", "santa tell me", "sleigh", "jingle", "mistletoe", "reindeer", "rudolph", "frosty", "noel", "navidad", "silent night", "holly jolly", "let it snow", "winter wonderland", "north pole", "feliz", "carol of", "little drummer", "deck the halls", "o holy night", "chestnuts", "snowman"]},
    {"key": "halloween", "group": "season", "name": "Halloween", "line": "Spooky songs from your music", "dz": "halloween party",
     "genres": ["horror"],
     "words": ["halloween", "monster mash", "ghost", "ghosts", "ghostbusters", "zombie", "zombies", "witch", "witchy", "spooky", "haunted", "thriller", "vampire", "werewolf", "skeleton", "skeletons", "pumpkin", "nightmare", "graveyard", "black magic", "superstition", "psycho killer", "horror", "this is halloween", "time warp", "somebody s watching me"]},
    {"key": "newyear", "group": "season", "name": "New Year's Eve party", "line": "Count down to midnight", "dz": "new year's eve party",
     "genres": [],
     "words": ["new year", "new years", "auld lang syne", "midnight", "countdown", "celebrate", "celebration", "champagne", "1999", "tonight", "party", "fireworks", "firework"]},
    {"key": "valentines", "group": "season", "name": "Valentine's love songs", "line": "Love songs from your music", "dz": "love songs",
     "genres": ["romance"],
     "words": ["love", "lover", "valentine", "kiss", "darling", "sweetheart", "my girl", "crazy in love", "forever", "baby i", "heart", "romance", "honey"]},
    {"key": "july4", "group": "season", "name": "4th of July", "line": "Summer in the USA", "dz": "4th of july",
     "genres": ["americana"],
     "words": ["america", "american", "usa", "u s a", "freedom", "independence", "fireworks", "firework", "star spangled", "red white", "fourth of july", "4th of july", "born in the u s a", "party in the u s a"]},
    {"key": "stpatricks", "group": "season", "name": "St. Patrick's Day", "line": "A bit of the Irish", "dz": "st patrick's day",
     "genres": ["celtic", "irish"],
     "words": ["irish", "ireland", "dublin", "galway", "shamrock", "whiskey", "whisky", "shipping up to boston", "danny boy"]},
    {"key": "summer", "group": "season", "name": "Summer hits", "line": "Sunny songs for June to August", "dz": "summer hits",
     "genres": ["reggae", "tropical", "surf"],
     "words": ["summer", "summertime", "sun", "sunshine", "beach", "california", "heat", "heatwave", "july", "august", "vacation", "island", "surf", "ocean", "waves", "pool"]},
    {"key": "fall", "group": "season", "name": "Cozy fall", "line": "Warm songs for cooler days", "dz": "cozy fall",
     "genres": ["folk", "acoustic", "singer-songwriter", "singer songwriter"],
     "words": ["autumn", "falling leaves", "october", "september", "november", "leaves", "harvest", "rain", "rainy", "sweater", "cozy"]},
    {"key": "winter", "group": "season", "name": "Winter", "line": "Songs for cold days", "dz": "winter songs",
     "genres": [],
     "words": ["winter", "snow", "snowfall", "cold", "frozen", "freeze", "december", "january", "fireplace", "blizzard"]},
    # Moods
    {"key": "chill", "group": "mood", "name": "Chill", "line": "Easy, laid-back songs", "dz": "chill hits",
     "genres": ["chill", "lo-fi", "lofi", "ambient", "downtempo", "acoustic", "soul", "r&b", "rnb", "bossa", "trip hop", "trip-hop", "dream pop", "neo soul", "indie folk"],
     "words": ["chill", "slow", "easy", "calm", "breeze", "lazy", "sunday"]},
    {"key": "feelgood", "group": "mood", "name": "Feel-good", "line": "Songs to lift you up", "dz": "feel good hits",
     "genres": ["funk", "disco", "motown", "reggae", "ska", "dance pop", "k-pop", "kpop"],
     "words": ["happy", "good day", "good vibes", "good times", "sunshine", "dance", "dancing", "smile", "celebrate", "fun", "feel good", "walking on", "best day", "shake"]},
    {"key": "sad", "group": "mood", "name": "Sad songs", "line": "For when you need a good cry", "dz": "sad songs",
     "genres": ["emo", "blues", "slowcore", "sad"],
     "words": ["cry", "crying", "tears", "sad", "alone", "lonely", "goodbye", "broken", "hurt", "sorry", "miss you", "without you", "gone", "lost", "heartbreak", "say something", "someone like you"]},
    {"key": "hype", "group": "mood", "name": "Hype", "line": "Loud songs to get going", "dz": "hype songs",
     "genres": ["hip hop", "hip-hop", "rap", "trap", "edm", "dubstep", "metal", "hard rock", "punk", "drum and bass", "drum & bass", "grime", "drill"],
     "words": ["hype", "turn up", "jump", "loud", "bounce"]},
    # Activities
    {"key": "workout", "group": "mood", "name": "Workout", "line": "Fast songs for the gym", "dz": "workout hits",
     "genres": ["hip hop", "hip-hop", "rap", "edm", "electro", "dance", "metal", "hard rock", "punk", "drum and bass", "house", "trap", "dubstep", "techno"],
     "words": ["run", "running", "stronger", "power", "fighter", "eye of the tiger", "pump", "lose yourself", "champion", "unstoppable"]},
    {"key": "focus", "group": "mood", "name": "Focus", "line": "Quiet songs for working or study", "dz": "focus music",
     "genres": ["classical", "instrumental", "ambient", "lo-fi", "lofi", "soundtrack", "score", "piano", "post-rock", "post rock", "new age", "jazz", "game"],
     "words": ["theme", "prelude", "sonata", "concerto", "nocturne", "etude", "suite", "interlude", "instrumental"]},
    {"key": "roadtrip", "group": "mood", "name": "Road trip", "line": "Windows down, volume up", "dz": "road trip",
     "genres": ["classic rock", "country", "americana", "pop rock", "pop-rock", "folk rock", "southern rock", "alternative rock"],
     "words": ["road", "drive", "driving", "highway", "car", "ride", "riding", "home", "miles", "route", "life is a highway", "take me home"]},
    {"key": "dinner", "group": "mood", "name": "Dinner", "line": "Smooth songs for eating together", "dz": "dinner jazz",
     "genres": ["jazz", "soul", "bossa", "swing", "lounge", "motown", "crooner", "vocal", "easy listening", "neo soul"],
     "words": ["wine", "dinner", "moonlight", "fly me", "sway", "smooth"]},
    {"key": "sleep", "group": "mood", "name": "Sleep", "line": "Soft songs to drift off to", "dz": "sleep",
     "genres": ["ambient", "classical", "piano", "new age", "sleep", "meditation"],
     "words": ["sleep", "sleeping", "dream", "dreams", "dreaming", "lullaby", "goodnight", "good night", "moon", "stars", "night", "quiet"]},
    # LoFi (Ben 2026-11-17): one LoFi mix with everything, then a mix for each kind of LoFi. Like Christmas, Ben
    # always sees them (even empty) with top songs to ask for, so he can fill them.
    {"key": "lofi", "group": "lofi", "name": "LoFi", "line": "Every kind of LoFi, all in one", "dz": "lofi hits",
     "genres": ["lo-fi", "lofi", "lo fi", "chillhop", "chill hop", "jazzhop", "jazz hop", "lofi hip hop", "lo-fi hip hop", "lofi house", "lo-fi house", "lo-fi beats", "lofi beats"],
     "words": ["lofi", "lo fi", "chillhop", "jazzhop"]},
    {"key": "lofi-hiphop", "group": "lofi", "name": "LoFi hip hop", "line": "Dusty beats to relax and study to", "dz": "lofi hip hop",
     "genres": ["lofi hip hop", "lo-fi hip hop", "lo fi hip hop", "lofi hip-hop", "lo-fi hip-hop", "lo-fi beats", "lofi beats"],
     "words": ["lofi hip hop", "lo fi hip hop", "lofi beats", "lo fi beats"]},
    {"key": "lofi-house", "group": "lofi", "name": "LoFi house", "line": "Warm, fuzzy house beats", "dz": "lofi house",
     "genres": ["lofi house", "lo-fi house", "lo fi house", "outsider house"],
     "words": ["lofi house", "lo fi house"]},
    {"key": "lofi-jazz", "group": "lofi", "name": "LoFi jazz", "line": "Jazzy keys over soft beats", "dz": "lofi jazz",
     "genres": ["jazzhop", "jazz hop", "jazz-hop", "lofi jazz", "lo-fi jazz"],
     "words": ["lofi jazz", "lo fi jazz", "jazzhop", "jazz hop"]},
    {"key": "lofi-chillhop", "group": "lofi", "name": "Chillhop", "line": "Sunny, easy LoFi grooves", "dz": "chillhop",
     "genres": ["chillhop", "chill hop", "chill-hop"],
     "words": ["chillhop", "chill hop"]},
    {"key": "lofi-sleep", "group": "lofi", "name": "Sleepy LoFi", "line": "Slow LoFi for late nights", "dz": "lofi sleep",
     "genres": ["lofi sleep", "lo-fi sleep", "sleep lofi"],
     "words": ["lofi sleep", "lo fi sleep", "sleepy lofi"]},
]
THEME_BY_KEY = {t["key"]: t for t in THEMES}


def _theme_words_re(words):
    if not words:
        return None
    return re.compile(r"\b(" + "|".join(re.escape(norm(w)) for w in words if norm(w)) + r")\b")


for _t in THEMES:
    _t["_re"] = _theme_words_re(_t["words"])
    _t["_gre"] = re.compile(r"(?<![a-z0-9])(" + "|".join(re.escape(g) for g in _t["genres"]) + r")(?![a-z0-9])") if _t["genres"] else None


def _theme_genre(t, genres):
    """Does the song's genre fit the theme? Whole genre words only, so "electro" isn't every Electronic song"""
    return bool(t["_gre"] and t["_gre"].search(genres))


def _is_christmas(song):
    """A Christmas song: Christmas words in its name or album, or a Christmas/holiday genre"""
    t = THEME_BY_KEY["christmas"]
    names = norm(song.get("Name")) + " | " + norm(song.get("Album"))
    genres = " | ".join(g.lower() for g in song.get("Genres") or [])
    return bool(t["_re"].search(names)) or _theme_genre(t, genres)


def _christmas_time(today=None):
    """Thanksgiving to Boxing Day: the only time Christmas songs mix in with the rest"""
    import datetime

    today = today or datetime.date.today()
    span = _holiday_dates("christmas", today)
    return bool(span and span[0] <= today <= span[1])


def _theme_song_key(song):
    """(artist, song name) the same way the catalogue songs are compared"""
    who = song.get("AlbumArtist") or (song.get("Artists") or [""])[0] or ""
    return norm(main_artist(who)), norm(song.get("Name"))


def _theme_pins():
    return (_read("theme-pins.json") or {}) if _dir else {}


def _theme_pin(key, song):
    """Ben asked for this song from a theme's Songs to add: it joins that theme once it's in"""
    if not _dir or (key not in THEME_BY_KEY and not key.startswith(("decade-", "genre-"))):
        return
    with _lock:
        pins = _read("theme-pins.json") or {}
        lst = pins.setdefault(key, [])
        pair = [norm(main_artist(song.get("artist"))), norm(song.get("title"))]
        if pair not in lst:
            lst.append(pair)
            del lst[:-300]
        _write("theme-pins.json", pins)


def _theme_dz(key):
    """The catalogue's songs for a theme (from its top playlists, or decade/genre playlists), kept a week.
    None while it hasn't been looked up yet (the morning look-up runs in the background)."""
    with _lock:
        hit = _found_cache.get("theme-" + key)
    if hit and time.time() - hit[0] < 7 * 24 * 3600:
        return copy.deepcopy(hit[1])
    return None


def _theme_query(key):
    t = THEME_BY_KEY.get(key)
    if t:
        return t["dz"]
    if key.startswith("decade-"):
        d = key.split("-", 1)[1]
        return f"{d[2:] if d < '2000' else d}s hits"
    if key.startswith("genre-"):
        return key.split("-", 1)[1].replace("_", " ") + " hits"
    return None


def _theme_dz_load(key):
    q = _theme_query(key)
    if not q:
        return []
    out, seen = [], set()
    found = deezer("/search/playlist", q=q, limit=6).get("data") or []
    # The biggest playlists first (the catalogue's own editors' ones are usually biggest)
    found.sort(key=lambda p: -(p.get("nb_tracks") or 0) if (p.get("nb_tracks") or 0) <= 300 else 0)
    for p in found[:3]:
        try:
            tracks = deezer(f"/playlist/{p['id']}/tracks", limit=100).get("data") or []
        except MusicError:
            continue
        for tr in tracks:
            s = _song(tr)
            k = (norm(main_artist(s["artist"])), norm(s["title"]))
            if s.get("id") and k not in seen:
                seen.add(k)
                out.append(s)
    with _lock:
        _found_cache["theme-" + key] = (time.time(), out)
    return out


def _theme_warm_start(keys):
    """Looks up the catalogue songs for these themes in the background, then remakes the themes"""
    with _theme_lock:
        if _theme_warm["running"]:
            return
        _theme_warm["running"] = True

    def run():
        try:
            for k in keys:
                if _theme_dz(k) is None:
                    try:
                        _theme_dz_load(k)
                    except Exception as e:
                        log(f"theme look-up for {k} didn't work: {e!r}")
            with _theme_lock:
                _themes.clear()
        finally:
            _theme_warm["running"] = False

    threading.Thread(target=run, name="music-themes", daemon=True).start()


def _played_days_ago(song, now):
    """How many days since this person last played the song (None if never). Jellyfin's times are UTC."""
    import datetime

    when = (song.get("UserData") or {}).get("LastPlayedDate") or ""
    try:
        t = datetime.datetime.strptime(when[:19], "%Y-%m-%dT%H:%M:%S").replace(tzinfo=datetime.timezone.utc)
    except ValueError:
        return None
    return (now - t).total_seconds() / 86400


def _theme_out(key, group, name, line, songs, rnd, need, limit=THEME_SONGS, adds=False, note=""):
    seen, ids, covers = set(), [], []
    songs = list(songs)
    rnd.shuffle(songs)
    for s in songs:
        if s["Id"] in seen:
            continue
        seen.add(s["Id"])
        ids.append(s["Id"])
        if len(ids) >= limit:
            break
    for s in songs:
        a = s.get("AlbumId")
        if s["Id"] in seen and a and a not in covers:
            covers.append(a)
        if len(covers) >= 4:
            break
    if len(ids) < need:
        return None
    out = {"key": key, "name": name, "line": line, "songs": ids, "covers": covers, "group": group}
    if note:
        out["note"] = note
    if adds:
        out["adds"] = True
    return out


def themes_for(token, user_id, is_admin):
    import datetime
    import random

    day = time.strftime("%Y-%m-%d")
    with _theme_lock:
        cached = _themes.get(user_id)
        if cached and cached[0] == day:
            return cached[1]
    songs = _songs(token, user_id, "Limit=8000")
    if not songs:
        return {"themes": []}
    rnd = random.Random(f"themes-{user_id}-{day}")
    today = datetime.date.today()
    now = datetime.datetime.now(datetime.timezone.utc)
    need = THEME_MIN_ADMIN if is_admin else THEME_MIN
    xmas_time = _christmas_time(today)
    pins = _theme_pins()
    by_key = {}
    for s in songs:
        by_key.setdefault(_theme_song_key(s), []).append(s)
    for s in songs:
        s["_name"] = norm(s.get("Name"))
        s["_names"] = s["_name"] + " | " + norm(s.get("Album"))
        s["_genres"] = " | ".join(g.lower() for g in s.get("Genres") or [])
    out, warm = [], []

    def from_catalogue(key):
        dz = _theme_dz(key)
        if dz is None:
            warm.append(key)
            return []
        mine = []
        for x in dz:
            for k in {(norm(main_artist(x["artist"])), norm(x["title"])), (norm(x["artist"]), norm(x["title"]))}:
                mine += by_key.get(k, [])
        return mine

    def pinned(key):
        mine = []
        for a, t in pins.get(key) or []:
            mine += by_key.get((a, t), [])
        return mine

    # Holidays and seasons: only in season (Ben sees a holiday 3 weeks early, so he can fill it before it starts)
    for t in THEMES:
        if t["group"] != "season":
            continue
        span = _holiday_dates(t["key"], today)
        if not span:
            continue
        start, end = span
        live = start <= today <= end
        early = is_admin and not live and 0 < (start - today).days <= 21
        if not live and not early:
            continue
        where = "_names" if t["key"] == "christmas" else "_name"
        mine = [s for s in songs if (t["_re"] and t["_re"].search(s[where])) or _theme_genre(t, s["_genres"])]
        mine += from_catalogue(t["key"]) + pinned(t["key"])
        # The card keeps describing the songs; the "not out yet" note goes on the playlist page only
        note = f"Only you can see this until {start.strftime('%b')} {start.day}" if early else ""
        # A holiday Ben is filling shows on his profile even before it has songs
        m = _theme_out(t["key"], "season", t["name"], t["line"], mine, rnd, 0 if is_admin else need, limit=100, adds=True, note=note)
        if m:
            out.append(m)

    # LoFi (Ben 2026-11-17): the LoFi mix has every LoFi song, then one mix for each kind of LoFi. They show
    # even before they have songs (like Christmas), with top songs to ask for; Kids never get here.
    lofi_all = []
    lofi_out = []
    for t in THEMES:
        if t["group"] != "lofi" or t["key"] == "lofi":
            continue
        mine = [s for s in songs if (t["_re"] and t["_re"].search(s["_names"])) or _theme_genre(t, s["_genres"])]
        mine += from_catalogue(t["key"]) + pinned(t["key"])
        lofi_all += mine
        lofi_out.append(_theme_out(t["key"], "lofi", t["name"], t["line"], mine, rnd, 0, limit=100, adds=True))
    t = THEME_BY_KEY["lofi"]
    mine = [s for s in songs if (t["_re"] and t["_re"].search(s["_names"])) or _theme_genre(t, s["_genres"])]
    mine += from_catalogue("lofi") + pinned("lofi") + lofi_all
    out.append(_theme_out("lofi", "lofi", t["name"], t["line"], mine, rnd, 0, limit=150, adds=True))
    out += lofi_out

    # Decades: every decade with enough songs, oldest first
    decades = {}
    for s in songs:
        y = _song_year(s)
        if y and 1940 <= y <= today.year:
            decades.setdefault(y // 10 * 10, []).append(s)
    for d in sorted(decades):
        key = f"decade-{d}"
        short = f"{str(d)[2:]}s" if d < 2000 else f"{d}s"
        mine = decades[d] + pinned(key)
        m = _theme_out(key, "decade", f"{short} mix", f"Songs from the {d}s", mine, rnd, need, adds=True)
        if m:
            out.append(m)

    # Genres: the biggest ones in the music (up to 10)
    counts = {}
    names = {}
    for s in songs:
        for g in s.get("Genres") or []:
            k = g.strip().lower()
            if k:
                counts[k] = counts.get(k, 0) + 1
                names.setdefault(k, g.strip())
    for g in sorted(counts, key=lambda k: -counts[k])[:10]:
        if counts[g] < need:
            break
        if re.search(r"christmas|holiday|xmas", g) and not xmas_time:
            continue
        key = "genre-" + re.sub(r"[^a-z0-9]+", "_", g).strip("_")
        mine = [s for s in songs if g in [x.strip().lower() for x in s.get("Genres") or []]] + pinned(key)
        m = _theme_out(key, "genre", f"{names[g]} mix", f"{names[g]} from your music", mine, rnd, need, adds=True)
        if m:
            out.append(m)

    # Moods and activities
    for t in THEMES:
        if t["group"] != "mood":
            continue
        mine = [s for s in songs if _mood_fits(t, s)]
        mine += from_catalogue(t["key"]) + pinned(t["key"])
        m = _theme_out(t["key"], "mood", t["name"], t["line"], mine, rnd, need, adds=True)
        if m:
            out.append(m)

    # Throwbacks (no Songs to add: these are about what you've played)
    count = lambda s: (s.get("UserData") or {}).get("PlayCount") or 0  # noqa: E731
    forgot = [s for s in songs if count(s) >= 3 and (_played_days_ago(s, now) or 0) > 90]
    m = _theme_out("forgotten", "throwback", "Forgotten favourites", "Songs you loved and haven't played in months", forgot, rnd, max(8, need))
    if m:
        out.append(m)
    lastyear = [s for s in songs if 344 <= (_played_days_ago(s, now) or 0) <= 386]
    m = _theme_out("lastyear", "throwback", "This week last year", "What you were playing around now last year", lastyear, rnd, max(8, need))
    if m:
        out.append(m)

    # Christmas songs stay in the Christmas playlist: never in Winter (it was mostly Christmas songs again),
    # and outside Thanksgiving to Boxing Day not in any decade, genre, mood or throwback playlist either
    xmas = {s["Id"] for s in songs if _is_christmas(s)} | {s["Id"] for s in from_catalogue("christmas")}
    album_of = {s["Id"]: s.get("AlbumId") for s in songs}
    kept = []
    for m in out:
        if m["key"] != "christmas" and xmas and (m["key"] == "winter" or not xmas_time):
            before = len(m["songs"])
            m["songs"] = [i for i in m["songs"] if i not in xmas]
            if len(m["songs"]) < before:
                if len(m["songs"]) < (need if m["group"] != "throwback" else max(8, need)) and not (is_admin and m["group"] == "season") and m["group"] != "lofi":
                    continue
                m["covers"] = list(dict.fromkeys(album_of[i] for i in m["songs"] if album_of.get(i)))[:4]
        kept.append(m)
    out = [m for m in (_apply_hides(user_id, m) for m in kept) if m]
    for s in songs:
        s.pop("_name", None)
        s.pop("_names", None)
        s.pop("_genres", None)
    if warm:
        _theme_warm_start(warm)
    answer = {"themes": out, "rows": THEME_ROWS}
    with _theme_lock:
        _themes[user_id] = (day, answer)
    return answer


def theme_adds(token, user_id, key):
    """Songs to add for one theme: catalogue songs that fit it and aren't in the music yet"""
    if key == "discover-weekly":
        return discover_weekly(token, user_id)
    if key == "release-radar":
        return release_radar(token, user_id)
    if not _theme_query(key):
        raise MusicError(404, "That theme isn't there any more.")
    dz = _theme_dz(key)
    if dz is None:
        dz = _theme_dz_load(key)
    out = {"songs": dz}
    have = set()
    try:
        have = set(_have_songs())
    except OSError:
        pass
    for s in _songs(token, user_id, "Limit=8000"):
        have.add(_theme_song_key(s))
    _mark_asked(out, extra_have=have)
    out["songs"] = [s for s in out["songs"] if not s.get("have")][:40]
    return out


# ---------- Discover weekly and Release radar (Music redesign 3, part 4) ----------
# Songs that aren't on Benflix yet, picked from what each profile plays. Discover weekly is new every Monday,
# Release radar every Friday morning. Each plays its 30 second previews back to back; the heart asks for a song.

_weekly = {}  # (user id, kind) -> (week, answer)
_weekly_lock = threading.Lock()


def _week_from(weekday, hour=0):
    """The date of the last [weekday] (0 = Monday) at [hour] o'clock, as text: when this week's list started"""
    import datetime

    now = datetime.datetime.now()
    back = (now.weekday() - weekday) % 7
    start = (now - datetime.timedelta(days=back)).replace(hour=hour, minute=0, second=0, microsecond=0)
    if start > now:
        start -= datetime.timedelta(days=7)
    return start.strftime("%Y-%m-%d")


def _weekly_cached(user_id, kind, week, load):
    with _weekly_lock:
        got = _weekly.get((user_id, kind))
        if got and got[0] == week:
            return got[1]
    answer = load()
    if answer.get("songs"):
        with _weekly_lock:
            _weekly[(user_id, kind)] = (week, answer)
    return answer


def _not_owned(token, user_id, songs, n):
    out = {"songs": songs}
    have = set()
    try:
        have = set(_have_songs())
    except OSError:
        pass
    for x in _songs(token, user_id, "Limit=8000"):
        have.add(_theme_song_key(x))
    _mark_asked(out, extra_have=have)
    blocked = _no_artists(user_id)
    return [x for x in out["songs"] if not x.get("have") and norm(x.get("artist")) not in blocked][:n]


def discover_weekly(token, user_id):
    """20 songs that aren't on Benflix, like the ones this profile plays most (no more than 2 by one artist)"""
    import random

    week = _week_from(0)

    def load():
        rnd = random.Random(f"{user_id}-{week}-dw")
        seeds = _seeds_for(token, user_id)[:24]
        rnd.shuffle(seeds)
        found, per_artist = [], {}
        for seed in seeds[:10]:
            row = _seed_row(seed) or {}
            songs = list(row.get("songs") or [])
            rnd.shuffle(songs)
            for x in songs[:6]:
                who = norm(x.get("artist"))
                if per_artist.get(who, 0) >= 2 or any(f["id"] == x["id"] for f in found):
                    continue
                per_artist[who] = per_artist.get(who, 0) + 1
                found.append(dict(x))
        rnd.shuffle(found)
        return {"songs": _not_owned(token, user_id, found, 20), "week": week}

    return _weekly_cached(user_id, "dw", week, load)


def release_radar(token, user_id):
    """New songs (the last 4 weeks) from the artists this profile follows or plays most, that aren't on Benflix"""
    import datetime

    week = _week_from(4, 6)

    def load():
        names = []
        j = _as_user(token, f"/Users/{user_id}/Items?IncludeItemTypes=MusicArtist&Recursive=true&Filters=IsFavorite&Limit=40&EnableImages=false") or {}
        names += [x.get("Name") for x in j.get("Items") or [] if x.get("Name")]
        names += [a for a, _ in _seeds_for(token, user_id)]
        seen, artists = set(), []
        for n in names:
            if norm(n) not in seen:
                seen.add(norm(n))
                artists.append(n)
        cut = (datetime.date.today() - datetime.timedelta(days=28)).isoformat()
        found = []
        for name in artists[:25]:
            aid = _artist_id(name)
            if not aid:
                continue
            try:
                albums = _cached(f"newalbums-{aid}-{week}", lambda: deezer(f"/artist/{aid}/albums", limit=10).get("data") or [], keep=7 * 24 * 3600) or []
            except MusicError:
                continue
            for al in albums:
                if (al.get("release_date") or "") < cut:
                    continue
                try:
                    tracks = deezer(f"/album/{al['id']}/tracks", limit=3).get("data") or []
                except MusicError:
                    continue
                for t in tracks[:3]:
                    t = dict(t)
                    t["album"] = {"id": al.get("id"), "title": al.get("title"), "cover_medium": al.get("cover_medium")}
                    t.setdefault("artist", {"name": name})
                    found.append(_song(t))
            if len(found) >= 60:
                break
        return {"songs": _not_owned(token, user_id, found, 30), "week": week}

    return _weekly_cached(user_id, "rr", week, load)


# Moods by how songs sound (Ben 2026-11-16): the song's speed in beats per minute, when Tower knows it.
# (slowest, fastest, add): a song outside slowest..fastest leaves the mood; a song at least "add" joins it even
# without a matching genre. Unknown speeds keep the genre guess.
MOOD_BPM = {
    "workout": (115, None, 128),
    "hype": (105, None, None),
    "chill": (None, 110, None),
    "sleep": (None, 95, None),
    "dinner": (None, 125, None),
    "focus": (None, 130, None),
    "feelgood": (90, None, None),
    "sad": (None, 120, None),
}
SLOW_GENRES = re.compile(r"(?<![a-z0-9])(classical|ambient|piano|new age|sleep|meditation|soundtrack|score)(?![a-z0-9])")


def _mood_fits(t, s):
    fits = _theme_genre(t, s["_genres"]) or bool(t["_re"] and t["_re"].search(s["_name"]))
    bpm = _song_bpm(s)
    lo, hi, add = MOOD_BPM.get(t["key"], (None, None, None))
    if not bpm:
        return fits
    if fits and ((lo and bpm < lo) or (hi and bpm > hi)):
        return False
    if not fits and add and bpm >= add and not SLOW_GENRES.search(s["_genres"]):
        return True
    return fits


# ---------- Song facts Tower looks up at night: the year a song first came out, and its speed ----------
# Ben 2026-11-16: songs from a 2015 Greatest Hits or a remaster counted as 2010s songs. MusicBrainz knows when each
# recording first came out; the music catalogue (Deezer) knows its beats per minute. Nothing changes in the files.

MUSICBRAINZ = "https://musicbrainz.org/ws/2/recording"
INFO_PER_RUN = 1500
_info = {"at": 0, "data": {}}


def _info_data():
    if not _dir:
        return {}
    if time.time() - _info["at"] > 600:
        _info.update({"at": time.time(), "data": _read("song-info.json") or {}})
    return _info["data"]


def _song_year(song):
    """The year the song first came out (Tower's look-up), else the album's year"""
    y = song.get("ProductionYear")
    first = (_info_data().get(song.get("Id") or "") or {}).get("y")
    if first and (not y or first < y):
        return first
    return y


def _song_bpm(song):
    return (_info_data().get(song.get("Id") or "") or {}).get("bpm") or 0


def _first_year(artist, title):
    q = f'recording:"{title}" AND artist:"{artist}"'
    try:
        got = _http(MUSICBRAINZ + "?" + urllib.parse.urlencode({"query": q, "fmt": "json", "limit": 15}),
                    headers={"User-Agent": "Benflix/1.0 (https://github.com/TheBenPsych/MyStream)"}, timeout=20) or {}
    except Exception:  # noqa: BLE001 - one song not found never stops the rest
        return None
    years = []
    for r in got.get("recordings") or []:
        if (r.get("score") or 0) < 85 or not same(r.get("title"), title):
            continue
        who = " ".join(c.get("name", "") for c in r.get("artist-credit") or [] if isinstance(c, dict))
        if not (same(who, artist) or norm(main_artist(artist)) in norm(who)):
            continue
        d = (r.get("first-release-date") or "")[:4]
        if d.isdigit() and int(d) > 1900:
            years.append(int(d))
    return min(years) if years else None


def _bpm(artist, title):
    try:
        found = deezer("/search/track", q=f'artist:"{artist}" track:"{title}"', limit=5).get("data") or []
        t = next((x for x in found if same(x.get("title"), title)), None)
        if not t:
            return None
        bpm = deezer(f"/track/{t['id']}").get("bpm") or 0
        return round(float(bpm)) if bpm and 40 <= float(bpm) <= 250 else None
    except (MusicError, ValueError, TypeError):
        return None


def _info_worker():
    time.sleep(900)
    while True:
        try:
            _info_tick()
        except Exception as e:
            log(f"song facts problem: {e!r}")
        # Then once a night, around 2am
        import datetime

        now = datetime.datetime.now()
        nxt = now.replace(hour=2, minute=0, second=0, microsecond=0)
        if nxt <= now:
            nxt += datetime.timedelta(days=1)
        time.sleep(max(600, (nxt - now).total_seconds()))


def _info_tick():
    if not _dir:
        return
    j = _jellyfin("GET", "/Items?IncludeItemTypes=Audio&Recursive=true&Fields=Genres&EnableImages=false&Limit=20000")
    if not j:
        return
    data = _read("song-info.json") or {}
    now, done = time.time(), 0
    for s in j.get("Items") or []:
        sid = s.get("Id")
        artist = (s.get("AlbumArtist") or (s.get("Artists") or [""])[0] or "").strip()
        title = clean_title(s.get("Name") or "", artist) if s.get("Name") else ""
        if not sid or not artist or not title or artist.lower() in ("various artists", "unknown"):
            continue
        old = data.get(sid)
        # Looked up already: try again after a month only when nothing was found
        if old and (old.get("y") or old.get("bpm") or now - old.get("t", 0) < 30 * 86400):
            continue
        if done >= INFO_PER_RUN:
            break
        done += 1
        y = _first_year(main_artist(artist) or artist, title)
        time.sleep(1.1)  # MusicBrainz asks for one call a second
        data[sid] = {"y": y or 0, "bpm": _bpm(main_artist(artist) or artist, title) or 0, "t": now}
        if done % 100 == 0:
            _write("song-info.json", data)
    if done:
        _write("song-info.json", data)
        _info.update({"at": 0})
        with _mix_lock:
            _mixes.clear()
        with _theme_lock:
            _themes.clear()
        log(f"song facts: looked up {done} songs")


# ---------- Album covers: Tower finds missing ones by itself every hour ----------

COVER_ART = "https://coverartarchive.org/release-group/"
MB_GROUPS = "https://musicbrainz.org/ws/2/release-group"
COVERS_PER_RUN = 200
COVER_RETRY = 7 * 86400  # An album nobody has a cover for is looked at again after a week


def _cover_worker():
    time.sleep(600)
    while True:
        try:
            _cover_tick()
        except Exception as e:
            log(f"album covers problem: {e!r}")
        time.sleep(3600)


def _cover_choices(album, artist):
    """Picture addresses for an album, best first: Jellyfin's own picture sources, then Deezer, then Cover Art Archive"""
    out = []
    j = _jellyfin("GET", f"/Items/{album['Id']}/RemoteImages?Type=Primary&Limit=5&IncludeAllLanguages=true") or {}
    out += [x["Url"] for x in j.get("Images") or [] if x.get("Url")]
    name = album.get("Name") or ""
    plain = re.sub(r"\s*[\(\[][^\)\]]*[\)\]]", "", name).strip() or name
    who = main_artist(artist) or artist
    try:
        found = deezer("/search/album", q=f'artist:"{who}" album:"{plain}"', limit=10).get("data") or []
        if not found:
            found = deezer("/search/album", q=f"{who} {plain}", limit=10).get("data") or []
    except MusicError:
        found = []
    for a in found:
        if (same(a.get("title"), name) or same(a.get("title"), plain)) and same((a.get("artist") or {}).get("name"), who):
            if a.get("cover_xl"):
                out.append(a["cover_xl"])
                break
    try:
        q = f'releasegroup:"{plain}" AND artist:"{who}"'
        got = _http(MB_GROUPS + "?" + urllib.parse.urlencode({"query": q, "fmt": "json", "limit": 5}),
                    headers={"User-Agent": "Benflix/1.0 (https://github.com/TheBenPsych/MyStream)"}, timeout=20) or {}
        time.sleep(1.1)  # MusicBrainz asks for one call a second
        for g in got.get("release-groups") or []:
            if (g.get("score") or 0) >= 90 and same(g.get("title"), plain) and g.get("id"):
                out.append(COVER_ART + g["id"] + "/front-500")
                break
    except Exception:  # noqa: BLE001 - one album not found never stops the rest
        pass
    return out


def _cover_tick():
    """Albums with no cover get one saved through Jellyfin, so TV, phone and web all show it"""
    if not _dir:
        return
    j = _jellyfin("GET", "/Items?IncludeItemTypes=MusicAlbum&Recursive=true&Fields=ProviderIds&EnableImageTypes=Primary&Limit=20000")
    if not j:
        return
    tried = _read("covers-tried.json") or {}
    now, looked, saved = time.time(), 0, 0
    for a in j.get("Items") or []:
        aid = a.get("Id")
        if not aid or (a.get("ImageTags") or {}).get("Primary") or now - tried.get(aid, 0) < COVER_RETRY:
            continue
        artist = (a.get("AlbumArtist") or ", ".join(a.get("Artists") or [])).strip()
        if not artist or not a.get("Name"):
            continue
        if looked >= COVERS_PER_RUN:
            break
        looked += 1
        tried[aid] = now
        for url in _cover_choices(a, artist):
            q = urllib.parse.urlencode({"Type": "Primary", "ImageUrl": url})
            if _jellyfin("POST", f"/Items/{aid}/RemoteImages/Download?{q}") is not None:
                saved += 1
                tried.pop(aid, None)
                break
    if looked:
        _write("covers-tried.json", tried)
        log(f"album covers: looked for {looked} albums with no cover, found {saved}")


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
