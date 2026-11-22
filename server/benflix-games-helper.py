"""
Benflix games helper: a tiny web service on the Unraid server (port 8097).

- Benflix asks it for the game list. It checks the person is signed in to Jellyfin (and isn't a kids
  account), then reads the games from RomM using the RomM login saved on the server. So nobody has to
  type a RomM login on the TV.
- Games play ON the TV or phone: Benflix downloads the game's files (/api/rom), any BIOS files it needs
  (/api/bios) and the person's saves (/api/save) from here, and sends the saves back when they quit.
  Saves are kept per Jellyfin account in /saves/<account id>/<game id>/.
- Server page tools (admin only): apps, downloads, how busy Tower is, track tidy. See benflix_server_tools.py.
- Music (admin only): ask for songs and albums (Lidarr does the downloading), playlists, the Music Drop
  folder and the music tidy-up. See benflix_music.py.
- Books (everyone): manga, ebooks and audiobooks from Tower's book folders, your place in each, and asking
  for books (LazyLibrarian does the downloading). See benflix_books.py.
- Benflix in a web browser: open http://<server>:8097 and anyone signs in with their Benflix name.
  The app is in web/ next to this file (benflix_web.py serves it and keeps it up to date from GitHub).
  /api/web/login signs in through Jellyfin, /api/web/jf/... passes the browser's requests on to
  Jellyfin, and /api/web/stream/... streams video (so the browser only talks to this helper).
  The old tools page (benflix-web.html) is only used if web/ is missing.

Settings come from environment variables: ROMM_URL, ROMM_USER, ROMM_PASS, JELLYFIN_URL.
Started by benflix-games-helper-setup.sh. Only uses the Python standard library.
"""

import base64
import gzip
import hashlib
import json
import os
import re
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

try:  # Free Live TV channels for Jellyfin, checked nightly (benflix_livetv.py next to this file)
    import benflix_livetv as livetv
except ImportError:
    livetv = None

try:  # Where the intro and credits really are, worked out on Tower (benflix_skips.py next to this file)
    import benflix_skips as skips
except ImportError:
    skips = None

try:  # Benflix in a web browser (the files, Quick Connect, video), in benflix_web.py next to this file
    import benflix_web as web
except ImportError:
    web = None

try:  # Music requests (Lidarr), the Music Drop folder and the music tidy-up, in benflix_music.py next to this file
    import benflix_music as music
except ImportError:
    music = None

try:  # Books: manga, ebooks and audiobooks, your places, and asking for books, in benflix_books.py next to this file
    import benflix_books as books
except ImportError:
    books = None

try:  # Benflix Server page tools (admin only), in benflix_server_tools.py next to this file
    import benflix_server_tools as tools
except ImportError:
    tools = None

try:  # Report card, tidy-up, night-time play test and subtitle fixer, in benflix_care.py next to this file
    import benflix_care as care
except ImportError:
    care = None

try:  # Parents' guide for the web version, in benflix_facts.py ("Did you know" facts were removed)
    import benflix_facts as facts
except ImportError:
    facts = None

try:  # Better copies from Radarr/Sonarr keep their first "date added", so they don't show as new, in benflix_arrivals.py
    import benflix_arrivals as arrivals
except ImportError:
    arrivals = None

try:  # Fix posters: blurry poster finder and night-time fill of missing pictures, in benflix_posters.py
    import benflix_posters as posters
except ImportError:
    posters = None

ROMM_URL = os.environ.get("ROMM_URL", "http://127.0.0.1:8095").rstrip("/")
ROMM_AUTH = "Basic " + base64.b64encode(
    f"{os.environ.get('ROMM_USER', '')}:{os.environ.get('ROMM_PASS', '')}".encode()
).decode()
JELLYFIN_URL = os.environ.get("JELLYFIN_URL", "http://127.0.0.1:8096").rstrip("/")
ROMS = "/ROMs"  # The same folder the game app sees
BIOS = "/bios"  # Games/bios on the server
SAVES = "/saves"  # Games/saves on the server, one folder per Jellyfin account
SAVE_KINDS = {"srm": 16 * 1024 * 1024, "state": 96 * 1024 * 1024, "png": 4 * 1024 * 1024}
# The three save slots from the game menu, each with a picture
for _slot in (1, 2, 3):
    SAVE_KINDS[f"state{_slot}"] = SAVE_KINDS["state"]
    SAVE_KINDS[f"png{_slot}"] = SAVE_KINDS["png"]
PORT = int(os.environ.get("PORT", "8097"))

# Which RetroArch emulator (core) plays each console. Keys are RomM folder names.
CORES = {
    "nes": "mesen",
    "famicom": "mesen",
    "snes": "snes9x",
    "sfam": "snes9x",
    "n64": "mupen64plus_next",
    "gb": "gambatte",
    "gbc": "gambatte",
    "gba": "mgba",
    "nds": "desmume",
    "genesis": "genesis_plus_gx",
    "megadrive": "genesis_plus_gx",
    "genesis-slash-megadrive": "genesis_plus_gx",
    "sms": "genesis_plus_gx",
    "gamegear": "genesis_plus_gx",
    "segacd": "genesis_plus_gx",
    "sega32": "picodrive",
    "psx": "swanstation",
    "ps2": "pcsx2",
    "ngc": "dolphin",
    "gc": "dolphin",
    "wii": "dolphin",
    "psp": "ppsspp",
    "dc": "flycast",
    "dreamcast": "flycast",
    "saturn": "mednafen_saturn",
    "tg16": "mednafen_pce_fast",
    "pce": "mednafen_pce_fast",
    "atari2600": "stella2014",
    "atari-2600": "stella2014",
    "turbografx16--1": "mednafen_pce_fast",
    "turbografx-16": "mednafen_pce_fast",
    "pc-engine": "mednafen_pce_fast",
    "pcengine": "mednafen_pce_fast",
    "tg-cd": "mednafen_pce_fast",
    "pce-cd": "mednafen_pce_fast",
    "atari-7800": "prosystem",
    "atari-lynx": "handy",
    "neo-geo-pocket": "mednafen_ngp",
    "neo-geo-pocket-color": "mednafen_ngp",
    "wsc": "mednafen_wswan",
    "ws": "mednafen_wswan",
    "virtual-boy": "mednafen_vb",
    "coleco": "gearcoleco",
    "arcade": "fbneo",
    "fbneo": "fbneo",
    "mame": "fbneo",
    "cps1": "fbneo",
    "cps2": "fbneo",
    "cps3": "fbneo",
    "neogeoaes": "fbneo",
    "neogeomvs": "fbneo",
    "atari7800": "prosystem",
    "lynx": "handy",
    "ngp": "mednafen_ngp",
    "ngpc": "mednafen_ngp",
    "wonderswan": "mednafen_wswan",
    "wonderswan-color": "mednafen_wswan",
    "virtualboy": "mednafen_vb",
    "msx": "bluemsx",
    "colecovision": "gearcoleco",
    "intellivision": "freeintv",
    "vectrex": "vecx",
    "3do": "opera",
    # Other folder names the app knows (games/Consoles.kt), so games in them don't say "Can't play here yet"
    "fds": "fceumm",
    "sega-cd": "genesis_plus_gx",
    "ps1": "swanstation",
    "playstation": "swanstation",
    "a2600": "stella2014",
    "2600": "stella2014",
    "a7800": "prosystem",
    "7800": "prosystem",
    "atarilynx": "handy",
    "neogeopocket": "mednafen_ngp",
    "neogeopocketcolor": "mednafen_ngp",
    "wonderswancolor": "mednafen_wswan",
    "vb": "mednafen_vb",
    "neo-geo": "fbneo",
    "nintendo-64": "mupen64plus_next",
    "nintendo64": "mupen64plus_next",
    "turbografx16": "mednafen_pce_fast",
    "pcecd": "mednafen_pce_fast",
}

# For games that are a folder of files, the file to start from (best first)
START_FILES = (".m3u", ".cue", ".gdi", ".chd", ".iso", ".rvz", ".wbfs", ".cso", ".pbp")

try:  # The Lobby: a 3D room to walk around in and watch together (who's where, chat, the TV, pool), in benflix_lobby.py
    import benflix_lobby as lobby
except ImportError:
    lobby = None

AWAY_PATH = "/benflix-games"

# Benflix security: pages from inside an ebook (the reader shows them in a frame) may never run their own code.
# Ebooks come from the internet, and a booby-trapped one could otherwise read the reader's sign-in.
BOOK_PAGE_RULES = "script-src 'none'; object-src 'none'; base-uri 'self'; form-action 'none'; frame-ancestors 'self'"

# Benflix: bump this (and SETUP_NEEDED in the app's TowerSetupCheck.kt and web server.js) whenever a change only
# starts working after Ben runs the setup command on Tower again. Older helpers say 0 (they don't send it).
SETUP_VERSION = 16
# 2026-11-16: autosetup now also syncs Recyclarr settings from GitHub (this line makes Tower pick that up)
# 2026-11-16: autosetup now also syncs Benflix custom formats (files.txt) for English or original audio only
# 2026-11-21: autosetup now also keeps Harbor on its newest build

_games = {}  # RomM id -> (platform folder, file name)
_tokens = {}  # Jellyfin token -> (time it was checked, account id, is admin)
_names = {}  # Jellyfin account id -> the profile's name (for "asked by")
_kids_big = {}  # Jellyfin kids account id -> True when its max rating is PG-13 / TV-14 ("Big kids")
_lock = threading.Lock()
# Benflix silent updates: when someone last used the helper (anything but the quick health checks), so Tower's
# hourly self-update can wait until nobody is using Benflix
_last_use = {"at": 0.0}
QUIET_FOR_S = 15 * 60


class HelperError(Exception):
    def __init__(self, status, message):
        super().__init__(message)
        self.status = status
        self.message = message


def romm_get(path, raw=False):
    req = urllib.request.Request(ROMM_URL + path, headers={"Authorization": ROMM_AUTH, "Accept": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            data = r.read()
            return (data, r.headers.get("Content-Type", "image/jpeg")) if raw else json.loads(data)
    except urllib.error.HTTPError as e:
        if e.code in (401, 403):
            raise HelperError(502, "The game library (RomM) didn't accept the login saved on the server. Run the setup command again.")
        raise HelperError(502, f"The game library (RomM) answered with an error ({e.code}).")
    except (urllib.error.URLError, OSError):
        raise HelperError(502, "The server couldn't reach the game library (RomM). Check it's running.")


# How long a checked sign-in is trusted before Jellyfin is asked again. Short, so removing someone or
# switching a profile to Kids takes hold within a couple of minutes.
TOKEN_MEMORY_S = 120


def check_signed_in(token):
    """Only people signed in to Jellyfin get the games. Returns (account id, is admin, is kids account).
    Kids accounts only get games rated for everyone (see kids_safe)."""
    if not token:
        raise HelperError(401, "Sign in to Glowfin first.")
    with _lock:
        known = _tokens.get(token)
        if known and time.time() - known[0] < TOKEN_MEMORY_S:
            return known[1], known[2], known[3]
    req = urllib.request.Request(
        JELLYFIN_URL + "/Users/Me", headers={"Authorization": f'MediaBrowser Token="{token}"', "Accept": "application/json"}
    )
    try:
        with urllib.request.urlopen(req, timeout=10) as r:
            user = json.loads(r.read())
    except urllib.error.HTTPError:
        raise HelperError(401, "Your Glowfin sign-in wasn't accepted. Try signing in again.")
    except (urllib.error.URLError, OSError):
        raise HelperError(502, "Tower's helper couldn't reach Jellyfin to check your sign-in.")
    policy = user.get("Policy") or {}
    is_kids = policy.get("MaxParentalRating") is not None or user.get("Name", "").lower() == "kids"
    user_id = re.sub(r"[^0-9a-fA-F-]", "", str(user.get("Id", "")))
    if not user_id:
        raise HelperError(502, "Jellyfin didn't say who you are.")
    is_admin = bool(policy.get("IsAdministrator"))
    # Big kids: a kids account Jellyfin lets watch PG-13 / TV-14 (rating score 13 or more) also gets Teen games
    try:
        big = is_kids and int(policy.get("MaxParentalRating") or 0) >= 13
    except (TypeError, ValueError):
        big = False
    with _lock:
        # Forget sign-ins that haven't been used for a while, so the list never grows without end
        now = time.time()
        for old in [t for t, k in _tokens.items() if now - k[0] > 10 * TOKEN_MEMORY_S]:
            del _tokens[old]
        if big:
            _big_users.add(user_id)
        else:
            _big_users.discard(user_id)
        _tokens[token] = (time.time(), user_id, is_admin, is_kids)
        _kids_big[user_id] = big
        _names[user_id] = str(user.get("Name") or "")
    return user_id, is_admin, is_kids


_family = [0.0, []]  # (when, people) for Ben's "Request for" picker


def family_people(token):
    """Everyone on Benflix who can be asked for (Ben's "Request for" picker): grown-up profiles only, never Kids.
    Needs the admin's sign-in. Kept for 5 minutes."""
    with _lock:
        if time.time() - _family[0] < 300 and _family[1]:
            return _family[1]
    status, data, _ = jellyfin_call("GET", "/Users", token=token, device_id="benflix-helper")
    if status != 200:
        raise HelperError(502, "Jellyfin didn't hand over the list of profiles.")
    people = []
    for u in json.loads(data or b"[]"):
        policy = u.get("Policy") or {}
        name = str(u.get("Name") or "")
        if policy.get("IsDisabled") or policy.get("MaxParentalRating") is not None or name.lower() == "kids":
            continue
        uid = re.sub(r"[^0-9a-fA-F-]", "", str(u.get("Id", "")))
        if uid and name:
            people.append({"id": uid, "name": name, "admin": bool(policy.get("IsAdministrator"))})
    people.sort(key=lambda p: (not p["admin"], p["name"].lower()))
    try:
        _add_seerr_ids(token, people)
    except Exception as e:  # Seerr can be down: music and books still work, movies and shows say so
        print(f"Request for: couldn't match Seerr accounts ({e!r})", flush=True)
    with _lock:
        _family[0], _family[1] = time.time(), people
    return people


def _seerr_link(token):
    """Seerr's address and key, read from the Jellyfin Enhanced plugin's settings (admin sign-in), or None"""
    status, data, _ = jellyfin_call("GET", "/Plugins", token=token, device_id="benflix-helper")
    if status != 200:
        return None
    plugin = next((x for x in json.loads(data or b"[]") if "enhanced" in str(x.get("Name", "")).lower()), None)
    if not plugin:
        return None
    status, data, _ = jellyfin_call("GET", f"/Plugins/{plugin.get('Id')}/Configuration", token=token, device_id="benflix-helper")
    if status != 200:
        return None
    conf = json.loads(data or b"{}")
    key = str(conf.get("JellyseerrApiKey") or "").strip()
    urls = [u.strip().rstrip("/") for u in re.split(r"[\s,;]+", str(conf.get("JellyseerrUrls") or conf.get("JellyseerrUrl") or "")) if u.strip()]
    return (urls, key) if urls and key else None


def _seerr(link, method, path, body=None):
    urls, key = link
    last = None
    for base in urls:
        req = urllib.request.Request(
            base + "/api/v1" + path, method=method, data=json.dumps(body).encode() if body is not None else None,
            headers={"X-Api-Key": key, "Accept": "application/json", "Content-Type": "application/json"},
        )
        try:
            with urllib.request.urlopen(req, timeout=15) as r:
                return json.loads(r.read() or b"null")
        except (urllib.error.URLError, OSError, ValueError) as e:
            last = e
    raise last or OSError("no Seerr address")


def _add_seerr_ids(token, people):
    """Puts each person's Seerr account number on them ("seerr"), so movies and shows can be asked for them.
    Anyone who isn't in Seerr yet gets brought in from Jellyfin (Seerr's own "Import Jellyfin users")."""
    link = _seerr_link(token)
    if not link:
        return
    def match():
        got = _seerr(link, "GET", "/user?take=1000") or {}
        return {str(u.get("jellyfinUserId") or "").replace("-", "").lower(): u.get("id") for u in got.get("results") or []}
    ids = match()
    missing = [p["id"] for p in people if p["id"].replace("-", "").lower() not in ids]
    if missing:
        try:
            _seerr(link, "POST", "/user/import-from-jellyfin", {"jellyfinUserIds": [m.replace("-", "") for m in missing]})
            ids = match()
        except Exception as e:
            print(f"Request for: couldn't bring {len(missing)} profile(s) into Seerr ({e!r})", flush=True)
    for p in people:
        sid = ids.get(p["id"].replace("-", "").lower())
        if sid:
            p["seerr"] = sid


def asking_for(token, body):
    """Ben asked for someone else ("for": their Jellyfin id): (their id, their name), or None for himself"""
    want = re.sub(r"[^0-9a-fA-F]", "", str(body.get("for") or "")).lower()
    if not want:
        return None
    for p in family_people(token):
        if p["id"].replace("-", "").lower() == want:
            return p["id"], p["name"]
    raise HelperError(400, "That person can't be asked for (they're not on Glowfin, or it's the Kids profile).")


# Ratings that mean "fine for kids" (ESRB, PEGI, CERO, USK) and ones that mean "not for kids"
KIDS_OK = {"E", "EC", "E10", "E10+", "EVERYONE", "EVERYONE10+", "3", "7", "A", "CEROA", "0", "6", "USK0", "USK6", "G", "ALL"}
KIDS_NO = {"T", "M", "AO", "TEEN", "MATURE", "ADULTSONLY", "12", "16", "18", "B", "C", "D", "Z", "USK12", "USK16", "USK18", "RP"}


# Big kids (like PG-13 / TV-14 for movies) may also play these: ESRB E10+ and Teen, PEGI 12, CERO B, USK 12
TEEN_OK = {"E10", "E10+", "EVERYONE10+", "T", "TEEN", "12", "B", "USK12"}
GROWN_UP = {"M", "AO", "MATURE", "ADULTSONLY", "16", "18", "C", "D", "Z", "USK16", "USK18"}


def age_ratings(g):
    """The game's age ratings from RomM, tidied up ("ESRB E10+" -> "E10+")"""
    m = g.get("metadatum") or {}
    ratings = m.get("age_ratings") or g.get("age_ratings") or []
    seen = set()
    for r in ratings:
        text = str((r.get("rating") or r.get("name") or "") if isinstance(r, dict) else r)
        for prefix in ("ESRB", "PEGI", "CERO", "USK"):
            text = text.upper().replace(prefix, "")
        seen.add(re.sub(r"[^A-Z0-9+]", "", text.upper()))
    seen.discard("")
    return seen


def rating_level(g):
    """"everyone", "teen", "grown" or "none" (no rating RomM knows of)"""
    seen = age_ratings(g)
    if not seen or seen <= {"RP"}:
        return "none"
    if seen & GROWN_UP:
        return "grown"
    if seen & (TEEN_OK - KIDS_OK):
        return "teen"
    return "everyone" if seen & KIDS_OK else "none"


def kids_safe(g):
    """True when the game is for everyone (or Ben switched it on for Kids). Games with no rating are left
    out unless Ben says so, like movies on the Kids profile."""
    choice = kids_choices().get(str(g.get("id")))
    if choice is not None:
        return bool(choice.get("kids"))
    return rating_level(g) == "everyone"


def bigkids_safe(g):
    """Big kids: everything Kids gets, plus E10+ and Teen games (or what Ben chose)"""
    choice = kids_choices().get(str(g.get("id")))
    if choice is not None:
        return bool(choice.get("kids") or choice.get("big"))
    return rating_level(g) in ("everyone", "teen")


# Ben's own choices from the Server page, kept with the saves: {"<game id>": {"kids": bool, "big": bool}}
KIDS_CHOICES = os.path.join(SAVES, ".benflix", "kids-games.json")
_kids_choices = None


def kids_choices():
    global _kids_choices
    if _kids_choices is None:
        try:
            with open(KIDS_CHOICES) as f:
                _kids_choices = json.load(f) or {}
        except (OSError, ValueError):
            _kids_choices = {}
    return _kids_choices


def set_kids_choice(game_id, kids=None, big=None, reset=False):
    """Server page: switches one game on or off for Kids / Big kids (reset = back to its age rating)"""
    choices = dict(kids_choices())
    key = str(int(game_id))
    if reset:
        choices.pop(key, None)
    else:
        now = dict(choices.get(key) or {})
        if kids is not None:
            now["kids"] = bool(kids)
            if kids:
                now["big"] = True  # anything fine for Kids is fine for Big kids too
        if big is not None:
            now["big"] = bool(big) or bool(now.get("kids"))
        now.setdefault("kids", False)
        now.setdefault("big", bool(now["kids"]))
        choices[key] = now
    os.makedirs(os.path.dirname(KIDS_CHOICES), exist_ok=True)
    tmp = KIDS_CHOICES + ".tmp"
    with open(tmp, "w") as f:
        json.dump(choices, f)
    os.replace(tmp, KIDS_CHOICES)
    global _kids_choices
    _kids_choices = choices
    return {"ok": True}


def kids_games_list():
    """Server page: every game, its age rating, and whether Kids and Big kids get it"""
    load_games()
    out = []
    with _lock:
        raw = list(_raw_games)
    for g in raw:
        level = rating_level(g)
        choice = kids_choices().get(str(g.get("id")))
        out.append(
            {
                "id": g["id"],
                "name": g.get("name") or g.get("fs_name_no_tags") or g.get("fs_name") or "Unknown game",
                "console": g.get("platform_display_name") or g.get("platform_name") or g.get("platform_fs_slug") or "",
                "rating": ", ".join(sorted(age_ratings(g))) or "No rating",
                "level": level,
                "kids": kids_safe(g),
                "big": bigkids_safe(g),
                "chosen": choice is not None,
            }
        )
    out.sort(key=lambda x: (x["level"] != "none", x["name"].lower()))
    return {"games": out}


# ---------- "Didn't start" list (Idea Box 24) ----------
# TVs, phones and browsers quietly say when a game showed only a black screen, couldn't load, or closed by itself in
# its first half minute. The Server page lists them so bad copies get replaced. Newest first, at most 200 kept.
GAME_PROBLEMS = os.path.join(SAVES, ".benflix", "game-problems.json")
_problems_lock = threading.Lock()


def game_problems():
    try:
        with open(GAME_PROBLEMS) as f:
            return json.load(f) or []
    except (OSError, ValueError):
        return []


def add_game_problem(body, who):
    try:
        game_id = int(body.get("id") or 0)
    except (TypeError, ValueError):
        game_id = 0
    if game_id <= 0:
        raise HelperError(400, "Which game?")
    clip = lambda v, n: str(v or "")[:n]  # noqa: E731
    entry = {
        "id": game_id,
        "name": clip(body.get("name"), 120),
        "console": clip(body.get("console"), 60),
        "what": clip(body.get("what"), 60) or "Didn't start",
        "device": clip(body.get("device"), 80),
        "who": clip(who, 60),
        "at": int(time.time()),
    }
    with _problems_lock:
        now = game_problems()
        # The same game on the same device within a day counts once
        now = [p for p in now if not (p.get("id") == game_id and p.get("device") == entry["device"] and entry["at"] - p.get("at", 0) < 86400)]
        now.insert(0, entry)
        os.makedirs(os.path.dirname(GAME_PROBLEMS), exist_ok=True)
        tmp = GAME_PROBLEMS + ".tmp"
        with open(tmp, "w") as f:
            json.dump(now[:200], f)
        os.replace(tmp, GAME_PROBLEMS)
    return {"ok": True}


def clear_game_problems(game_id=None):
    with _problems_lock:
        now = [] if game_id is None else [p for p in game_problems() if p.get("id") != game_id]
        os.makedirs(os.path.dirname(GAME_PROBLEMS), exist_ok=True)
        with open(GAME_PROBLEMS, "w") as f:
            json.dump(now, f)
    return {"ok": True}


_kids_ids = set()  # games a kids account may download, filled by load_games()
_bigkids_ids = set()  # and a Big kids account
_big_users = set()  # Jellyfin accounts that are Big kids
_raw_games = []  # RomM's own list, for the Server page


WEB_PAGE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "benflix-web.html")


def jellyfin_auth(token=None, device_id="benflix-web"):
    auth = f'MediaBrowser Client="Benflix Web", Device="Web browser", DeviceId="{device_id}", Version="2.0"'
    if token:
        auth += f', Token="{token}"'
    return auth


def jellyfin_call(method, path, token=None, body=None, device_id="benflix-web", content_type="application/json"):
    """Sends one request to Jellyfin for the browser page. Returns (status, body bytes, content type)."""
    headers = {"Authorization": jellyfin_auth(token, device_id), "Accept": "application/json"}
    if body is not None:
        headers["Content-Type"] = content_type or "application/json"
    req = urllib.request.Request(JELLYFIN_URL + path, data=body, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            return r.status, r.read(), r.headers.get("Content-Type", "application/json")
    except urllib.error.HTTPError as e:
        return e.code, e.read() or b"", e.headers.get("Content-Type", "application/json")
    except (urllib.error.URLError, OSError):
        raise HelperError(502, "The helper couldn't reach Jellyfin. Check it's running.")


# ---------- Kids accounts in the browser ----------
# The same rules as the app's Kids profile (services/KidsMode.kt): lists of movies & shows only hold kid
# ratings, and anything with no rating at all is left out. Jellyfin's own max-rating setting lets unrated
# titles through (and an account just named "Kids" has no limit at all), so the helper checks every list.
KIDS_RATINGS = {"G", "PG", "TV-Y", "TV-Y7", "TV-Y7-FV", "TV-G", "TV-PG", "U", "UC", "ALL", "APPROVED", "0", "6", "7", "FSK-0", "FSK-6"}
BIG_KIDS_RATINGS = {"PG-13", "TV-14", "12", "12A", "13", "FSK-12", "M", "PG13"}
KIDS_RATED_TYPES = {"Movie", "Series", "Episode", "Season", "Video", "Trailer", "MusicVideo", "BoxSet"}
KIDS_LIST_PATH = re.compile(
    r"(?i)(Items(/Latest|/Resume|/Suggestions)?|/Shows/NextUp|/Shows/Upcoming|/Shows/[^/]+/(Episodes|Seasons)|/Similar|/Search/Hints)$"
)
_kids_series = {}  # show id -> its rating ("" when it has none)


def kids_list_path(jf_path, big):
    """The browser's Jellyfin address with the kids limits added (or None when it isn't a list of titles)"""
    path, _, q = jf_path.partition("?")
    if not KIDS_LIST_PATH.search(path):
        return None
    params = urllib.parse.parse_qsl(q, keep_blank_values=True)
    names = {k.lower(): v for k, v in params}
    if "maxofficialrating" not in names:
        params.append(("maxOfficialRating", "PG-13" if big else "PG"))
    # Ben's "Kids profile: Let it through / Hide it" choice is the title's custom rating, so ask for it too
    fkey = next((k for k, _ in params if k.lower() == "fields"), None)
    if fkey is None:
        params.append(("Fields", "CustomRating"))
    elif "customrating" not in names.get("fields", "").lower():
        params = [(k, v + ",CustomRating" if k == fkey else v) for k, v in params]
    types = names.get("includeitemtypes")
    if types is not None and "episode" not in types.lower() and "season" not in types.lower() and "hasparentalrating" not in names:
        params.append(("hasParentalRating", "true"))
    return path + "?" + urllib.parse.urlencode(params)


def _kids_rating(item):
    """Ben's own choice (custom rating) first, else the official rating"""
    for key in ("CustomRating", "OfficialRating"):
        r = item.get(key)
        if r and str(r).strip():
            return str(r).strip()
    return None


def kids_filter(data, token, device_id, big):
    """Takes out of a Jellyfin answer every movie, show or episode that isn't rated for kids"""
    try:
        answer = json.loads(data)
    except ValueError:
        return b'{"Items":[],"TotalRecordCount":0}'
    lists = [answer] if isinstance(answer, list) else [answer.get(k) for k in ("Items", "SearchHints") if isinstance(answer.get(k), list)]
    missing = set()
    for items in lists:
        for it in items:
            if isinstance(it, dict) and it.get("Type") in ("Episode", "Season") and not _kids_rating(it):
                sid = str(it.get("SeriesId") or "")
                if sid and sid not in _kids_series:
                    missing.add(sid)
    if missing:
        status, body, _ = jellyfin_call("GET", "/Items?Fields=CustomRating&ids=" + ",".join(sorted(missing)), token=token, device_id=device_id)
        if status == 200:
            for it in (json.loads(body).get("Items") or []):
                _kids_series[str(it.get("Id"))] = _kids_rating(it) or ""

    def allowed(it):
        if not isinstance(it, dict) or it.get("Type") not in KIDS_RATED_TYPES:
            return True
        rating = _kids_rating(it)
        if not rating and it.get("Type") in ("Episode", "Season"):
            rating = _kids_series.get(str(it.get("SeriesId") or "")) or None
        if not rating:
            return False
        r = rating.upper()
        return r in KIDS_RATINGS or (big and r in BIG_KIDS_RATINGS)

    if isinstance(answer, list):
        answer = [it for it in answer if allowed(it)]
    else:
        for k in ("Items", "SearchHints"):
            if isinstance(answer.get(k), list):
                answer[k] = [it for it in answer[k] if allowed(it)]
    return json.dumps(answer).encode()


def web_login(info):
    name, password = str(info.get("username") or "").strip(), str(info.get("password") or "")
    if not name:
        raise HelperError(400, "Type your Jellyfin user name.")
    device = "benflix-web-" + re.sub(r"[^\w-]", "", str(info.get("device") or ""))[:40]
    body = json.dumps({"Username": name, "Pw": password}).encode()
    status, data, _ = jellyfin_call("POST", "/Users/AuthenticateByName", body=body, device_id=device)
    # Benflix redesign 2: plain words (the web page turns these into a card with one button)
    if status == 401:
        raise HelperError(401, "Wrong password. Check the name and password and try again.")
    if status == 403:
        # Jellyfin's "max active sessions" limit on the account (or the account is switched off)
        ask = "" if name.lower() == "ben" else ", or ask Ben"
        raise HelperError(403, f"{name}'s account is on too many TVs. Sign out on one{ask}.")
    if status != 200:
        raise HelperError(502, f"Jellyfin answered with an error ({status}). If you limit how many devices a person can use, sign out somewhere else first.")
    return web_session(json.loads(data))


def web_session(auth):
    """What the browser keeps after signing in: the Jellyfin sign-in and who the person is"""
    user = auth.get("User") or {}
    policy = user.get("Policy") or {}
    return {
        "token": auth.get("AccessToken"),
        "name": user.get("Name"),
        "id": user.get("Id"),
        "admin": bool(policy.get("IsAdministrator")),
        "kids": policy.get("MaxParentalRating") is not None or str(user.get("Name", "")).lower() == "kids",
        "image": user.get("PrimaryImageTag"),
    }


def load_games():
    games, offset = [], 0
    while True:
        page = romm_get(
            f"/api/roms?limit=250&offset={offset}&order_by=name&order_dir=asc&with_char_index=false"
            "&with_filter_values=false&with_rom_id_index=false&with_provider_metadata=false"
        )
        items = page.get("items", []) if isinstance(page, dict) else page
        games.extend(items)
        offset += len(items)
        total = page.get("total") if isinstance(page, dict) else None
        if len(items) < 250 or (total is not None and offset >= total):
            break
    consoles, slugs = {}, {}
    with _lock:
        for g in games:
            folder = g.get("platform_fs_slug") or g.get("platform_slug") or ""
            _games[g["id"]] = (folder, g.get("fs_name") or "")
            name = g.get("platform_display_name") or g.get("platform_custom_name") or g.get("platform_name") or folder
            slugs[name] = folder
            has_cover = bool(g.get("path_cover_large") or g.get("path_cover_small") or g.get("url_cover"))
            consoles.setdefault(name, []).append(
                {
                    "id": g["id"],
                    "name": g.get("name") or g.get("fs_name_no_tags") or g.get("fs_name") or "Unknown game",
                    "cover": f"/api/cover/{g['id']}" if has_cover else None,
                    "playable": folder in CORES,
                    "size": g.get("fs_size_bytes") or 0,
                    "year": release_year(g),
                    "genres": genres_of(g),
                    "kids": kids_safe(g),
                    "bigkids": bigkids_safe(g),
                    "rating": rating_of(g),
                    "players": players_of(g),
                }
            )
            if kids_safe(g):
                _kids_ids.add(g["id"])
            else:
                _kids_ids.discard(g["id"])
            if bigkids_safe(g):
                _bigkids_ids.add(g["id"])
            else:
                _bigkids_ids.discard(g["id"])
        _raw_games[:] = games
    return {
        "consoles": [
            {"name": n, "slug": slugs.get(n, ""), "games": sorted(gs, key=lambda x: x["name"].lower())}
            for n, gs in sorted(consoles.items())
        ]
    }


def release_year(g):
    """The year a game came out, from RomM's metadata (0 if unknown)"""
    m = g.get("metadatum") or {}
    when = m.get("first_release_date") or g.get("first_release_date")
    try:
        if isinstance(when, (int, float)) and when > 0:
            return time.gmtime(when / 1000 if when > 10**11 else when).tm_year
        if isinstance(when, str) and len(when) >= 4 and when[:4].isdigit():
            return int(when[:4])
    except Exception:
        pass
    return 0


def rating_of(g):
    """How well loved a game is, 0-100 (0 if unknown). RomM fills this in from IGDB (and ScreenScraper or
    LaunchBox when it uses those). Used for the Games "Best on <console>" rows."""
    m = g.get("metadatum") or {}
    igdb = g.get("igdb_metadata") or {}
    for value in (
        m.get("average_rating"),
        igdb.get("total_rating"),
        igdb.get("aggregated_rating"),
        (g.get("ss_metadata") or {}).get("ss_score"),
        (g.get("launchbox_metadata") or {}).get("community_rating"),
    ):
        try:
            r = float(value)
        except (TypeError, ValueError):
            continue
        if r <= 0:
            continue
        # Some sources score out of 5 or 10
        if r <= 5:
            r *= 20
        elif r <= 10:
            r *= 10
        return round(min(r, 100.0), 1)
    return 0


def players_of(g):
    """Idea Box 25 "Games for two": how many people can play at once (0 = one, or unknown). RomM gives a
    player count like "1-4" (ScreenScraper) or game modes like "Co-operative" / "Split screen" (IGDB)."""
    m = g.get("metadatum") or {}
    count = str(m.get("player_count") or g.get("player_count") or "")
    nums = [int(n) for n in re.findall(r"\d+", count)]
    if nums and max(nums) >= 2:
        return min(max(nums), 8)
    modes = " ".join(str(x.get("name") if isinstance(x, dict) else x) for x in (m.get("game_modes") or g.get("game_modes") or [])).lower()
    if any(w in modes for w in ("multiplayer", "co-operative", "cooperative", "split screen", "split-screen")):
        return 2
    return 0


def genres_of(g):
    m = g.get("metadatum") or {}
    out = m.get("genres") or g.get("genres") or []
    return [x.get("name") if isinstance(x, dict) else str(x) for x in out if x][:4]


def game_info(game_id):
    """The game details page: story, year, genres, players and makers"""
    g = romm_get(f"/api/roms/{game_id}")
    m = g.get("metadatum") or {}
    igdb = g.get("igdb_metadata") or {}
    players = m.get("player_count") or igdb.get("player_count") or ""
    companies = m.get("companies") or g.get("companies") or []
    return {
        "summary": (g.get("summary") or "").strip(),
        "year": release_year(g),
        "genres": genres_of(g),
        "players": str(players) if players else "",
        "companies": [c.get("name") if isinstance(c, dict) else str(c) for c in companies if c][:2],
    }


def cover(game_id):
    g = romm_get(f"/api/roms/{game_id}")
    path = g.get("path_cover_large") or g.get("path_cover_small")
    if path:
        # RomM may give "roms/2/308/cover/big.png" or the full "/assets/romm/resources/roms/...?ts=2026-11-01 03:24:05"
        path = path.split("?")[0].lstrip("/")
        if not path.startswith("assets/"):
            path = "assets/romm/resources/" + path
        return romm_get("/" + urllib.parse.quote(path), raw=True)
    url = g.get("url_cover")
    if url:
        if url.startswith("//"):
            url = "https:" + url
        with urllib.request.urlopen(url, timeout=20) as r:
            return r.read(), r.headers.get("Content-Type", "image/jpeg")
    raise HelperError(404, "No box art")


def start_file(path):
    """A game can be one file, or a folder (multi-disc, cue + bin). Pick the file RetroArch should open."""
    if os.path.isfile(path):
        return path
    if not os.path.isdir(path):
        raise HelperError(404, "That game's file wasn't found on the server. Try a RomM scan.")
    files = sorted(f for f in os.listdir(path) if os.path.isfile(os.path.join(path, f)))
    for ext in START_FILES:
        for f in files:
            if f.lower().endswith(ext):
                return os.path.join(path, f)
    if not files:
        raise HelperError(404, "That game's folder is empty.")
    return os.path.join(path, max(files, key=lambda f: os.path.getsize(os.path.join(path, f))))


def game_path(game_id):
    """Where a game lives in the ROMs folder: (console folder, full path of its file or folder)"""
    with _lock:
        known = _games.get(game_id)
    if known is None:
        g = romm_get(f"/api/roms/{game_id}")
        known = (g.get("platform_fs_slug") or g.get("platform_slug") or "", g.get("fs_name") or "")
        with _lock:
            _games[game_id] = known
    folder, name = known
    path = os.path.realpath(os.path.join(ROMS, folder, name))
    if not path.startswith(ROMS + "/") or not name:
        raise HelperError(400, "That game's file name looks wrong.")
    return folder, path


# Multi-disc games (Idea Box 24): a folder with one .cue (or .chd / .iso) per disc gets a made-up list file, so the
# game player opens disc 1 and can swap to the others ("Swap disc" in the game menu). The files themselves stay as
# they are. Folders that already have their own .m3u use that.
DISC_LIST = "Benflix discs.m3u"
DISC_KINDS = (".cue", ".chd", ".iso", ".pbp", ".cso")


def disc_order(name):
    """Disc 1 before Disc 2 before Disc 10 (by the first "disc/disk/cd N" in the name, then the name)"""
    m = re.search(r"(?:disc|disk|cd)\s*[-_]?\s*(\d+)", name, re.I)
    return (int(m.group(1)) if m else 0, name.lower())


def disc_list(path):
    """The made-up .m3u for a multi-disc folder (as bytes), or None when there's nothing to list"""
    if not os.path.isdir(path):
        return None
    files = [f for f in os.listdir(path) if os.path.isfile(os.path.join(path, f))]
    if any(f.lower().endswith(".m3u") for f in files):
        return None
    for kind in DISC_KINDS:
        discs = sorted((f for f in files if f.lower().endswith(kind)), key=disc_order)
        if len(discs) > 1:
            return ("\n".join(discs) + "\n").encode("utf-8")
        if discs:
            return None
    return None


def rom_files(game_id):
    """The files Benflix downloads to play a game on the TV/phone, and which one to start"""
    folder, path = game_path(game_id)
    start = start_file(path)
    if os.path.isfile(path):
        files = [os.path.basename(path)]
    else:
        files = sorted(f for f in os.listdir(path) if os.path.isfile(os.path.join(path, f)))
    listed = [{"name": f, "size": os.path.getsize(os.path.join(os.path.dirname(start), f))} for f in files]
    discs = disc_list(path)
    if discs is not None:
        listed.append({"name": DISC_LIST, "size": len(discs)})
        start = os.path.join(path, DISC_LIST)
    return {
        "console": folder,
        "start": os.path.basename(start),
        "files": listed,
        "discs": discs.decode("utf-8").count("\n") if discs else 1,
    }


def send_rom_zip(handler, game_id):
    """The browser's game player can only fetch one file, so a game made of several files (a .cue and its .bin
    files, or several discs) is sent as one .zip, packed as it goes (nothing is written to Tower's drives)."""
    import zipfile

    _, path = game_path(game_id)
    if not os.path.isdir(path):
        return handler.send_file(path)
    files = sorted(f for f in os.listdir(path) if os.path.isfile(os.path.join(path, f)))
    discs = disc_list(path)
    handler.send_response(200)
    handler.send_header("Content-Type", "application/zip")
    handler.send_header("Content-Disposition", 'attachment; filename="game.zip"')
    handler.end_headers()

    class Out:
        """The connection, as something zipfile can write to (it can't jump back, so zipfile adds sizes after)"""

        def __init__(self, w):
            self.w = w

        def write(self, b):
            self.w.write(b)
            return len(b)

        def flush(self):
            self.w.flush()

    with zipfile.ZipFile(Out(handler.wfile), "w", zipfile.ZIP_STORED) as z:
        if discs is not None:
            z.writestr(DISC_LIST, discs)
        for f in files:
            full = os.path.join(path, f)
            info = zipfile.ZipInfo(f, time.localtime(os.path.getmtime(full))[:6])
            info.file_size = os.path.getsize(full)
            with open(full, "rb") as src, z.open(info, "w", force_zip64=info.file_size > 2**31 - 1) as dst:
                while True:
                    chunk = src.read(1024 * 1024)
                    if not chunk:
                        break
                    dst.write(chunk)


def rom_file(game_id, name):
    _, path = game_path(game_id)
    base = path if os.path.isdir(path) else os.path.dirname(path)
    full = os.path.realpath(os.path.join(base, name))
    if os.path.dirname(full) != os.path.realpath(base) or not os.path.isfile(full):
        raise HelperError(404, "That game file wasn't found on the server.")
    if os.path.isfile(path) and full != path:
        raise HelperError(404, "That game file wasn't found on the server.")
    return full


def bios_list():
    """Every file in Games/bios (any sub-folder), so Benflix can fetch the ones its emulators need"""
    out = []
    if os.path.isdir(BIOS):
        for root, _, files in os.walk(BIOS):
            for f in files:
                full = os.path.join(root, f)
                out.append({"name": f, "path": os.path.relpath(full, BIOS), "size": os.path.getsize(full)})
    return {"files": sorted(out, key=lambda x: x["path"].lower())}


def bios_file(rel):
    full = os.path.realpath(os.path.join(BIOS, rel))
    if not full.startswith(os.path.realpath(BIOS) + "/") or not os.path.isfile(full):
        raise HelperError(404, "That BIOS file wasn't found on the server.")
    return full


def save_dir(user_id, game_id):
    return os.path.join(SAVES, user_id, str(game_id))


def save_info(user_id, game_id):
    """Which saves this account has for a game, and when they were made (milliseconds)"""
    d = save_dir(user_id, game_id)
    out = {}
    for kind in SAVE_KINDS:
        f = os.path.join(d, "save." + kind)
        if os.path.isfile(f):
            out[kind] = {"time": int(os.path.getmtime(f) * 1000), "size": os.path.getsize(f)}
    return out


_popular = {"at": 0.0, "items": None}
_popular_lock = threading.Lock()
POPULAR_DAYS = 30
POPULAR_CACHE_S = 3600


def _played_at(text):
    """Jellyfin's "2026-11-07T14:00:00.0000000Z" as seconds, or 0"""
    try:
        return time.mktime(time.strptime(str(text)[:19], "%Y-%m-%dT%H:%M:%S")) - time.timezone
    except (ValueError, TypeError):
        return 0


def busy_now():
    """For Tower's hourly self-update (benflix-autosetup.sh, asked from Tower itself only): is anyone using
    Benflix right now? Busy when Jellyfin is playing anything for anyone (paused counts too), or the helper
    was used in the last 15 minutes (web videos, games, the Lobby, Home lists). "jellyfin" says whether
    Jellyfin could be asked (it needs Ben to have opened Benflix once since Tower started)."""
    quiet_for = time.time() - _last_use["at"]
    if quiet_for < QUIET_FOR_S:
        return {"busy": True, "why": "Glowfin was used in the last 15 minutes", "jellyfin": None}
    token = (getattr(skips, "_admin", None) or {}).get("token") if skips is not None else None
    if not token:
        return {"busy": False, "why": "", "jellyfin": False}
    try:
        status, data, _ = jellyfin_call("GET", "/Sessions?ActiveWithinSeconds=900", token=token)
    except Exception:
        # Jellyfin not answering: play it safe
        return {"busy": True, "why": "Jellyfin didn't answer", "jellyfin": False}
    if status != 200:
        return {"busy": False, "why": "", "jellyfin": False}
    playing = [x for x in json.loads(data) if x.get("NowPlayingItem")]
    if playing:
        return {"busy": True, "why": f"{len(playing)} playing in Jellyfin", "jellyfin": True}
    return {"busy": False, "why": "", "jellyfin": True}


def household_popular():
    """What the household watched most in the last 30 days: movies and shows, ranked by how many
    people watched them (then by plays). Uses the admin's sign-in that Tower keeps for skip times,
    and leaves out Kids accounts. Worked out at most once an hour."""
    with _popular_lock:
        if _popular["items"] is not None and time.time() - _popular["at"] < POPULAR_CACHE_S:
            return _popular["items"]
        token = (getattr(skips, "_admin", None) or {}).get("token") if skips is not None else None
        if not token:
            raise HelperError(503, "Ben needs to open Glowfin once so Tower can see what everyone watched.")
        status, data, _ = jellyfin_call("GET", "/Users", token=token)
        if status != 200:
            raise HelperError(503, "Tower couldn't read the people list. Ben needs to open Glowfin again.")
        cutoff = time.time() - POPULAR_DAYS * 86400
        people, plays = {}, {}
        for user in json.loads(data):
            policy = user.get("Policy") or {}
            if policy.get("IsDisabled") or policy.get("MaxParentalRating") is not None:
                continue
            uid = user.get("Id")
            query = urllib.parse.urlencode(
                {
                    "Recursive": "true",
                    "IsPlayed": "true",
                    "IncludeItemTypes": "Movie,Episode",
                    "Fields": "SeriesId",
                    "SortBy": "DatePlayed",
                    "SortOrder": "Descending",
                    "Limit": "400",
                    "EnableImages": "false",
                }
            )
            st, body, _ = jellyfin_call("GET", f"/Users/{uid}/Items?{query}", token=token)
            if st != 200:
                continue
            for item in json.loads(body).get("Items") or []:
                if _played_at((item.get("UserData") or {}).get("LastPlayedDate")) < cutoff:
                    break
                if item.get("Type") == "Episode":
                    key = ("Series", item.get("SeriesId"))
                else:
                    key = ("Movie", item.get("Id"))
                if not key[1]:
                    continue
                people.setdefault(key, set()).add(uid)
                plays[key] = plays.get(key, 0) + 1
        ranked = sorted(people, key=lambda k: (len(people[k]), plays[k]), reverse=True)[:20]
        items = [{"id": k[1], "type": k[0], "people": len(people[k])} for k in ranked]
        _popular.update({"at": time.time(), "items": items})
        return items


def recent_saves(user_id):
    """This account's games with a "pick up where you left off" save, newest first"""
    base = os.path.join(SAVES, user_id)
    out = []
    if os.path.isdir(base):
        for g in os.listdir(base):
            f = os.path.join(base, g, "save.state")
            if g.isdigit() and os.path.isfile(f):
                out.append({"id": int(g), "time": int(os.path.getmtime(f) * 1000)})
    return {"games": sorted(out, key=lambda x: -x["time"])[:20]}


def put_save(user_id, game_id, kind, body, when):
    d = save_dir(user_id, game_id)
    os.makedirs(d, exist_ok=True)
    f = os.path.join(d, "save." + kind)
    tmp = f + ".tmp"
    with open(tmp, "wb") as out:
        out.write(body)
    os.replace(tmp, f)
    if when:
        os.utime(f, (when / 1000, when / 1000))
    return {"ok": True}


class Handler(BaseHTTPRequestHandler):
    _page_rules = None

    def end_headers(self):
        # Benflix security, on every reply: other websites can't show Tower's pages inside theirs (clickjacking),
        # and browsers use the file type Tower says instead of guessing (so a picture is never run as a page)
        self.send_header("X-Frame-Options", "SAMEORIGIN")
        self.send_header("X-Content-Type-Options", "nosniff")
        if self._page_rules:
            self.send_header("Content-Security-Policy", self._page_rules)
        super().end_headers()

    def send(self, status, body, content_type="application/json", etag=None):
        if not isinstance(body, bytes):
            body = json.dumps(body).encode()
        ctype = content_type or ""
        if etag and status == 200 and etag in (self.headers.get("If-None-Match") or ""):
            # The browser already has this exact file: say so instead of sending it again (much faster away from home)
            self.send_response(304)
            self.send_header("ETag", etag)
            self.send_header("Cache-Control", "no-cache")
            self.end_headers()
            return
        # Text (pages, JavaScript, lists from Jellyfin) shrinks to about a quarter when packed, so it loads much faster
        # over the internet. Pictures, video and sound are already packed.
        packed = False
        if (
            len(body) > 1024
            and "gzip" in (self.headers.get("Accept-Encoding") or "")
            and (ctype.startswith("text/") or "json" in ctype or "javascript" in ctype or "svg" in ctype or "font/ttf" in ctype)
        ):
            body = gzip.compress(body, 5)
            packed = True
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        if packed:
            self.send_header("Content-Encoding", "gzip")
            self.send_header("Vary", "Accept-Encoding")
        if etag:
            self.send_header("ETag", etag)
            self.send_header("Cache-Control", "no-cache")
        elif ctype.startswith("image/"):
            self.send_header("Cache-Control", "max-age=86400")
        elif ctype.startswith("text/"):
            self.send_header("Cache-Control", "no-cache")
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(body)

    def send_file(self, path):
        size = os.path.getsize(path)
        self.send_response(200)
        self.send_header("Content-Type", "application/octet-stream")
        self.send_header("Content-Length", str(size))
        self.end_headers()
        with open(path, "rb") as f:
            while True:
                chunk = f.read(1024 * 1024)
                if not chunk:
                    break
                self.wfile.write(chunk)

    def send_range(self, path, content_type, head=False):
        """A big file (an audiobook chapter, an ebook, a PDF) with Range support, so players can jump around"""
        size = os.path.getsize(path)
        start, end = 0, size - 1
        rng = self.headers.get("Range", "")
        m = re.fullmatch(r"bytes=(\d*)-(\d*)", rng.strip())
        partial = False
        if m and (m.group(1) or m.group(2)):
            if m.group(1):
                start = int(m.group(1))
                end = int(m.group(2)) if m.group(2) else size - 1
            else:
                start = max(0, size - int(m.group(2)))
            end = min(end, size - 1)
            if start > end:
                self.send_response(416)
                self.send_header("Content-Range", f"bytes */{size}")
                self.send_header("Content-Length", "0")
                self.end_headers()
                return
            partial = True
        self.send_response(206 if partial else 200)
        self.send_header("Content-Type", content_type)
        self.send_header("Accept-Ranges", "bytes")
        self.send_header("Content-Length", str(end - start + 1))
        self.send_header("Cache-Control", "private, max-age=3600")
        if partial:
            self.send_header("Content-Range", f"bytes {start}-{end}/{size}")
        self.end_headers()
        if head:
            return
        with open(path, "rb") as f:
            f.seek(start)
            left = end - start + 1
            while left > 0:
                chunk = f.read(min(1024 * 1024, left))
                if not chunk:
                    break
                self.wfile.write(chunk)
                left -= len(chunk)

    def read_body(self, limit):
        length = int(self.headers.get("Content-Length") or 0)
        if length <= 0 or length > limit:
            raise HelperError(413, "That save is too big.")
        data = b""
        while len(data) < length:
            chunk = self.rfile.read(min(1024 * 1024, length - len(data)))
            if not chunk:
                raise HelperError(400, "The save didn't arrive in one piece.")
            data += chunk
        return data

    def read_json(self):
        length = int(self.headers.get("Content-Length") or 0)
        if length <= 0:
            return {}
        if length > 64 * 1024:
            raise HelperError(413, "That's too big.")
        try:
            return json.loads(self.rfile.read(length) or b"{}")
        except ValueError:
            raise HelperError(400, "That didn't make sense to the helper.")

    def web_device(self):
        """The browser's own device id (each profile signed in on a browser has its own)"""
        d = re.sub(r"[^\w-]", "", self.headers.get("X-Benflix-Device", ""))[:60]
        return "benflix-web-" + d if d else "benflix-web"

    def quick_connect(self, method, path):
        """Sign in with a code: the browser shows a code, the person types it on a TV or phone already signed in"""
        device = self.web_device()
        try:
            if method == "POST" and path == "/api/web/qc/start":
                return self.send(200, web.quick_connect_start(jellyfin_call, device))
            body = self.read_json() if method == "POST" else {}
            secret = str(body.get("secret") or "")
            if method == "POST" and path == "/api/web/qc/check":
                return self.send(200, web.quick_connect_check(jellyfin_call, device, secret))
            if method == "POST" and path == "/api/web/qc/finish":
                return self.send(200, web_session(web.quick_connect_finish(jellyfin_call, device, secret)))
        except ValueError as e:
            raise HelperError(400, str(e))
        return self.send(404, {"error": "Not found"})

    def admin_route(self, method, path, query):
        """The Server page tools. Returns False when the path isn't one of them."""
        if tools is None:
            raise HelperError(503, "The Server page tools aren't on the server yet. Run the Glowfin setup command again.")
        if method == "POST" and path == "/api/admin/livetv/refresh" and livetv is not None:
            return self.send(200, {"started": livetv.refresh(), **livetv.status()})
        if method == "GET" and path == "/api/admin/arrivals" and arrivals is not None:
            return self.send(200, arrivals.status())
        if method == "GET" and path == "/api/admin/drives":
            return self.send(200, tools.drives(fresh=(query.get("fresh") or ["0"])[0] == "1"))
        if method == "GET" and path == "/api/admin/harbor":
            return self.send(200, tools.harbor())
        if method == "GET" and path == "/api/admin/tower":
            return self.send(200, tools.tower(fresh=(query.get("fresh") or ["0"])[0] == "1"))
        m = re.fullmatch(r"/api/admin/apps/([\w.-]+)/restart", path)
        if method == "POST" and m:
            return self.send(200, tools.restart_app(m.group(1)))
        m = re.fullmatch(r"/api/admin/downloads/(sonarr|radarr)/(\d+)/(retry|replace)", path)
        if method == "POST" and m:
            return self.send(200, tools.download_action(m.group(1), int(m.group(2)), m.group(3)))
        if path in ("/api/admin/holdup", "/api/admin/holdup/search", "/api/admin/holdup/copies"):
            kind, tmdb = (query.get("kind") or [""])[0], (query.get("tmdb") or [""])[0]
            if kind not in ("movie", "tv") or not tmdb.isdigit():
                raise HelperError(400, "Which movie or show?")
            if method == "GET" and path == "/api/admin/holdup":
                return self.send(200, tools.holdup(kind, tmdb))
            if method == "GET" and path == "/api/admin/holdup/copies" and kind == "movie":
                return self.send(200, tools.holdup_copies(tmdb))
            if method == "POST" and path == "/api/admin/holdup/search":
                return self.send(200, tools.holdup_search(kind, tmdb))
        if method == "POST" and path == "/api/admin/search/missing":
            return self.send(200, tools.search_missing(self.read_json()))
        if method == "POST" and path == "/api/admin/search/better":
            return self.send(200, tools.search_better(self.read_json()))
        if method == "GET" and path == "/api/admin/tracks":
            return self.send(200, tools.tracks_status())
        if method == "POST" and path == "/api/admin/tracks":
            body = self.read_json()
            return self.send(200, tools.tracks_start(body.get("mode"), body.get("folder"), body.get("undo")))
        return False

    def route(self, method):
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path
        query = urllib.parse.parse_qs(parsed.query)
        # Away from home the app comes in through Tower's web proxy at https://<jellyfin address>/benflix-games.
        # Some proxies keep that start of the path, some drop it; both work.
        via_internet = bool(self.headers.get("X-Forwarded-For") or self.headers.get("X-Real-IP"))
        if path == AWAY_PATH or path.startswith(AWAY_PATH + "/"):
            path = path[len(AWAY_PATH):] or "/"
            via_internet = True
        self._page_rules = BOOK_PAGE_RULES if path.startswith("/api/books/x/") else None
        try:
            if via_internet and (path.startswith("/api/admin/") or (web is None and path in ("/", "/web", "/index.html"))):
                # Tower's tools (restarting apps, fixing files) only work from home, never through the internet.
                # Benflix in a browser does work away from home (https://<internet address>/benflix-games/):
                # it signs in through Jellyfin like the apps do, and Jellyfin checks every request.
                raise HelperError(403, "Tower's tools only work at home.")
            if via_internet:
                learn_internet_address(
                    self.headers.get("X-Forwarded-Host") or self.headers.get("Host"),
                    self.headers.get("X-Forwarded-Proto") or self.headers.get("X-Forwarded-Scheme"),
                )
            if method == "GET" and path == "/api/addresses":
                return self.send(200, tower_addresses())
            if method == "GET" and path == "/busy":
                # Only Tower itself may ask (it says whether someone is watching)
                if via_internet or self.client_address[0] not in ("127.0.0.1", "::1"):
                    raise HelperError(403, "Only Tower can ask this.")
                return self.send(200, busy_now())
            if path not in ("/health", "/api/addresses"):
                _last_use["at"] = time.time()
            if method == "GET" and path == "/health":
                return self.send(
                    200,
                    {
                        "ok": True,
                        "local_play": True,
                        "server_tools": tools is not None,
                        "web": True,
                        "live_tv": livetv is not None,
                        "web_app": web is not None,
                        "skips": skips is not None and skips.tools_ok(),
                        "music": music is not None,
                        "care": care is not None and care.ffmpeg_ok(),
                        "facts": facts is not None,
                        "books": books is not None,
                        "lobby": lobby is not None,
                        # Which setup the helper came from (the app and web show the yellow setup card when it's older)
                        "setup": SETUP_VERSION,
                    },
                )
            if livetv is not None and method in ("GET", "HEAD") and path.startswith("/livetv/play/"):
                # One live channel: the first of its copies that plays right now (works away from home too,
                # since it only hands out the free services' own public streams)
                key = path[len("/livetv/play/"):].split(".")[0]
                try:
                    skip = max(0, min(5, int((query.get("skip") or ["0"])[0])))
                except ValueError:
                    skip = 0
                found = livetv.play(key, skip)
                if not found:
                    raise HelperError(503, "This channel isn't working right now. Try another one.")
                if found[0] == "go":
                    self.send_response(302)
                    self.send_header("Location", found[1])
                    self.send_header("Content-Length", "0")
                    self.end_headers()
                    return
                return self.send(200, found[1], "application/vnd.apple.mpegurl")
            if livetv is not None and method == "GET" and path == "/livetv/channels.m3u" and not via_internet:
                if not os.path.isfile(livetv.M3U_FILE):
                    raise HelperError(503, "The Live TV list is still being made. Try again in about 15 minutes.")
                return self.send(200, livetv.m3u(self.headers.get("Host")), "audio/x-mpegurl")
            if livetv is not None and method == "GET" and path.startswith("/livetv/"):
                # Jellyfin reads the channel list and guide from here (at home only, no sign-in needed)
                if via_internet:
                    raise HelperError(403, "Live TV lists only work at home.")
                if path == "/livetv/status":
                    return self.send(200, livetv.status())
                found = livetv.file_for(path)
                if not found:
                    raise HelperError(404, "Not found")
                if not os.path.isfile(found[0]):
                    raise HelperError(503, "The Live TV list is still being made. Try again in about 15 minutes.")
                with open(found[0], "rb") as f:
                    return self.send(200, f.read(), found[1])
            if web is not None and method in ("GET", "HEAD") and (
                path in ("/", "/web", "/index.html", "/sw.js", "/manifest.webmanifest", "/favicon.ico") or path.startswith("/web/")
            ):
                # Benflix in a web browser (server/web in the repo). The install helper (sw.js) and the
                # app description sit at the top so "Install Benflix" covers the whole site.
                rel = path[len("/web/"):] if path.startswith("/web/") else path.lstrip("/") if path in ("/sw.js", "/manifest.webmanifest") else "index.html"
                if path == "/favicon.ico":  # browsers ask for this for bookmarks: give them the fin
                    rel = "icons/fin-32.png"
                found = web.file_for(rel)
                if found:
                    with open(found[0], "rb") as f:
                        data = f.read()
                    # A fingerprint of the file, so a browser that already has it doesn't download it again
                    return self.send(200, data, found[1], etag='"' + hashlib.sha1(data).hexdigest()[:20] + '"')
                if path.startswith("/web/"):
                    raise HelperError(404, "Not found")
            if method == "GET" and path in ("/", "/web", "/index.html"):
                if not os.path.isfile(WEB_PAGE):
                    raise HelperError(404, "The tools page isn't on the server yet. Run the Glowfin setup command again.")
                with open(WEB_PAGE, "rb") as f:
                    return self.send(200, f.read(), "text/html; charset=utf-8")
            if method == "POST" and path == "/api/web/login":
                return self.send(200, web_login(self.read_json()))
            if web is not None and path.startswith("/api/web/qc/"):
                return self.quick_connect(method, path)
            if method == "GET" and path == "/api/web/version":
                return self.send(200, web.status() if web is not None else {"ready": False})
            token = self.headers.get("X-Benflix-Token", "")
            if not token and method in ("GET", "HEAD") and (
                (path.startswith("/api/web/jf/") and "/Images/" in path)
                or path.startswith("/api/web/stream/")
                or re.fullmatch(r"/api/(?:cover/\d+|rom/\d+/(?:file|zip)|bios/file)", path)
                or re.fullmatch(r"/api/books/(?:cover/\w+|vol/\w+/p/\d+|file/\w+(?:/\d+)?|x/\w+/.+)", path)
            ):
                # Pictures, video and game files in the browser (an <img>, <video> or emulator can't send the
                # header, so the sign-in rides along in the address)
                token = (query.get("api_key") or [""])[0]
                if not token and path.startswith("/api/books/x/"):
                    # The ebook reader's pages and pictures carry the sign-in in a cookie (relative links inside a book can't)
                    for part in (self.headers.get("Cookie") or "").split(";"):
                        k, _, v = part.strip().partition("=")
                        if k == "bftoken":
                            token = urllib.parse.unquote(v)
            user_id, is_admin, is_kids = check_signed_in(token)
            if skips is not None and is_admin:
                # Lets Tower check every season's skip times by itself, without waiting for someone to press play
                skips.remember_admin(token, user_id)
            if method == "POST" and path == "/api/livetv/broken":
                # Benflix "This channel is broken" (anyone signed in)
                if livetv is None:
                    raise HelperError(503, "Live TV isn't on the server yet. Run the Glowfin setup command again.")
                return self.send(200, livetv.report_broken(self.read_json(), user_id))
            if path.startswith("/api/skips/"):
                # Skip intro / credits times for the player (anyone signed in; fixes not from kids accounts)
                if skips is None:
                    raise HelperError(503, "Skip times aren't on the server yet. Run the Glowfin setup command again.")
                status, answer = skips.route(method, path, query, token, user_id, is_admin, is_kids, self.read_json)
                return self.send(status, answer)
            if path.startswith("/api/facts/"):
                # Parents' guide for the web version (anyone signed in). Did you know facts were removed.
                if facts is None:
                    raise HelperError(503, "Facts aren't on the server yet. Run the Glowfin setup command again.")
                status, answer = facts.route(method, path, query, token, user_id, is_admin)
                return self.send(status, answer)
            if path.startswith("/api/lobby/"):
                # The Lobby (everyone signed in; Kids only join lobbies showing something for Kids)
                if lobby is None:
                    raise HelperError(503, "The Lobby isn't on the server yet. Run the Glowfin setup command again.")
                try:
                    status, answer = lobby.route(method, path, self.read_json, user_id, _names.get(user_id, ""), is_admin, is_kids)
                except lobby.LobbyError as e:
                    raise HelperError(e.status, e.message)
                return self.send(status, answer)
            if method == "GET" and path == "/api/anime/tmdb":
                # AniList / MyAnimeList numbers -> TMDB numbers, so the Anime tab opens exactly the right title
                return self.send(200, anime_tmdb(query))
            if path.startswith("/api/asks/"):
                # Why Ben said no (everyone reads, Ben writes) and who gets an automatic yes (Ben only)
                return self.send(200, asks_route(method, path, token, user_id, is_admin, is_kids, self.read_json))
            if path == "/api/reviews" or path == "/api/reviews/key":
                return self.send(200, reviews_route(method, path, query, is_admin, is_kids, self.read_json))
            if method == "GET" and path == "/api/activity":
                # Server, Activity: hours watched by each person, most watched, busiest times (Ben only)
                if not is_admin:
                    raise HelperError(403, "Only Ben can see this.")
                return self.send(200, activity_page((query.get("month") or [""])[0]))
            if method == "GET" and path == "/api/family":
                # Ben's "Request for" picker: who he can ask for (grown-ups only, never Kids)
                if not is_admin:
                    raise HelperError(403, "Only Ben can ask for someone else.")
                return self.send(200, {"people": family_people(token), "me": user_id})
            read_json, for_person = self.read_json, None
            if method == "POST" and is_admin and path in ("/api/music/request", "/api/books/request"):
                # Ben asking on behalf of someone in the family: it's theirs (their list, their "it's ready" news)
                body = self.read_json()
                for_person = asking_for(token, body)
                read_json = lambda: body  # noqa: E731
            if path.startswith("/api/music/"):
                # Music requests, playlists and the music tidy-up (only the admin, Ben, can use them)
                if music is None:
                    raise HelperError(503, "Music isn't on the server yet. Run the Glowfin setup command again.")
                status, answer = music.route(
                    method, path, query, token, user_id, is_admin, read_json, is_kids=is_kids, user_name=_names.get(user_id, ""), for_person=for_person
                )
                return self.send(status, answer)
            if path.startswith("/api/books/"):
                # Books: manga, ebooks and audiobooks (everyone; Kids only see kids' books)
                if books is None:
                    raise HelperError(503, "Books aren't on the server yet. Run the Glowfin setup command again.")
                answer = books.route(method, path, query, token, user_id, is_admin, is_kids, read_json, user_name=_names.get(user_id, ""), for_person=for_person)
                if answer[0] == "file":
                    return self.send_range(answer[1], answer[2], head=(method == "HEAD"))
                if len(answer) == 3:
                    return self.send(answer[0], answer[1], answer[2])
                return self.send(answer[0], answer[1])
            if path.startswith("/api/subsync/"):
                # The player's "Subtitles are off" button: Tower lines them up with the voices
                if care is None:
                    raise HelperError(503, "The subtitle fixer isn't on the server yet. Run the Glowfin setup command again.")
                if is_kids:
                    raise HelperError(403, "Ask a grown-up to fix these subtitles.")
                status, answer = care.user_route(method, path, self.read_json)
                return self.send(status, answer)
            if path.startswith("/api/admin/"):
                if not is_admin:
                    raise HelperError(403, "Only the server admin can use the Server page tools.")
                if care is not None and path.startswith("/api/admin/care/"):
                    care.remember_admin(token, user_id)
                    answer = care.admin_route(method, path, query, self.read_json)
                    if answer is not None:
                        return self.send(answer[0], answer[1])
                if posters is not None and path.startswith("/api/admin/posters/"):
                    posters.remember_admin(token, user_id)
                    answer = posters.admin_route(method, path, query, self.read_json)
                    if answer is not None:
                        return self.send(answer[0], answer[1])
                if self.admin_route(method, path, query) is not False:
                    return
                return self.send(404, {"error": "Not found"})
            if web is not None and path.startswith("/api/web/stream/") and method in ("GET", "HEAD"):
                # Video, sound and subtitles for the browser player, passed on from Jellyfin a piece at a time
                jf_path = path[len("/api/web/stream"):] + (("?" + parsed.query) if parsed.query else "")
                return web.stream(self, JELLYFIN_URL + jf_path, jellyfin_auth(token, self.web_device()))
            if path.startswith("/api/web/jf/"):
                # The browser's Jellyfin requests. Everyone signed in can use them; Jellyfin itself checks
                # what each person is allowed to see and change.
                body = None
                if method in ("POST", "PUT", "DELETE"):
                    length = int(self.headers.get("Content-Length") or 0)
                    if length > 1024 * 1024:
                        raise HelperError(413, "That's too big.")
                    body = self.rfile.read(length) if length > 0 else b""
                jf_path = path[len("/api/web/jf"):] + (("?" + parsed.query) if parsed.query else "")
                kids_path = kids_list_path(jf_path, _kids_big.get(user_id, False)) if is_kids and method == "GET" else None
                status, data, ctype = jellyfin_call(
                    method, kids_path or jf_path, token=token, body=body, device_id=self.web_device(), content_type=self.headers.get("Content-Type")
                )
                if kids_path and status == 200 and "json" in (ctype or ""):
                    # Kids accounts: only titles rated for kids (unrated ones are left out, like in the app)
                    data = kids_filter(data, token, self.web_device(), _kids_big.get(user_id, False))
                return self.send(status, data, ctype)
            if method == "GET" and path == "/api/games":
                games = load_games()
                if is_kids:
                    field = "bigkids" if user_id in _big_users else "kids"
                    games["consoles"] = [
                        dict(c, games=[x for x in c["games"] if x[field]]) for c in games["consoles"]
                    ]
                    games["consoles"] = [c for c in games["consoles"] if c["games"]]
                return self.send(200, games)
            if path == "/api/games/problems":
                if method == "POST":
                    return self.send(200, add_game_problem(self.read_json(), _names.get(user_id, "")))
                if not is_admin:
                    raise HelperError(403, "Only the main profile can see this.")
                if method == "GET":
                    return self.send(200, {"problems": game_problems()})
                if method == "DELETE":
                    gid = (query.get("id") or [""])[0]
                    return self.send(200, clear_game_problems(int(gid) if gid.isdigit() else None))
            if path == "/api/games/kids":
                # Server page: which games Kids and Big kids get (the main profile only)
                if not is_admin:
                    raise HelperError(403, "Only the main profile can change this.")
                if method == "GET":
                    return self.send(200, kids_games_list())
                if method == "POST":
                    body = self.read_json()
                    return self.send(200, set_kids_choice(body.get("id"), body.get("kids"), body.get("big"), bool(body.get("reset"))))
            m = re.fullmatch(r"/api/(?:info|cover|rom|save)/(\d+)(?:/.*)?", path)
            if is_kids and m:
                if not _kids_ids:
                    load_games()
                allowed = _bigkids_ids if user_id in _big_users else _kids_ids
                if int(m.group(1)) not in allowed:
                    raise HelperError(403, "That game isn't rated for kids.")
            m = re.fullmatch(r"/api/info/(\d+)", path)
            if method == "GET" and m:
                return self.send(200, game_info(int(m.group(1))))
            m = re.fullmatch(r"/api/cover/(\d+)", path)
            if method == "GET" and m:
                data, ctype = cover(int(m.group(1)))
                return self.send(200, data, ctype)
            m = re.fullmatch(r"/api/rom/(\d+)", path)
            if method == "GET" and m:
                return self.send(200, rom_files(int(m.group(1))))
            m = re.fullmatch(r"/api/rom/(\d+)/file", path)
            if method == "GET" and m:
                name = (query.get("name") or [""])[0]
                if name == DISC_LIST:
                    _, gpath = game_path(int(m.group(1)))
                    discs = disc_list(gpath)
                    if discs is None:
                        raise HelperError(404, "That game file wasn't found on the server.")
                    return self.send(200, discs, "audio/x-mpegurl")
                return self.send_file(rom_file(int(m.group(1)), name))
            m = re.fullmatch(r"/api/rom/(\d+)/zip", path)
            if method == "GET" and m:
                return send_rom_zip(self, int(m.group(1)))
            if method == "GET" and path == "/api/bios":
                return self.send(200, bios_list())
            if method == "GET" and path == "/api/bios/file":
                return self.send_file(bios_file((query.get("path") or [""])[0]))
            if method == "GET" and path == "/api/popular":
                # Search's Popular row: what the household watches most (not for Kids)
                if is_kids:
                    return self.send(200, {"items": []})
                return self.send(200, {"items": household_popular()})
            if method == "GET" and path == "/api/saves/recent":
                return self.send(200, recent_saves(user_id))
            m = re.fullmatch(r"/api/save/(\d+)", path)
            if method == "GET" and m:
                return self.send(200, save_info(user_id, int(m.group(1))))
            m = re.fullmatch(r"/api/save/(\d+)/(srm|state[1-3]?|png[1-3]?)", path)
            if m:
                game_id, kind = int(m.group(1)), m.group(2)
                f = os.path.join(save_dir(user_id, game_id), "save." + kind)
                if method == "GET":
                    if not os.path.isfile(f):
                        raise HelperError(404, "No save yet.")
                    return self.send_file(f)
                if method == "PUT":
                    when = int((query.get("time") or ["0"])[0] or 0)
                    return self.send(200, put_save(user_id, game_id, kind, self.read_body(SAVE_KINDS[kind]), when))
                if method == "DELETE":
                    if os.path.isfile(f):
                        os.remove(f)
                    return self.send(200, {"ok": True})
            self.send(404, {"error": "Not found"})
        except HelperError as e:
            self.send(e.status, {"error": e.message})
        except (BrokenPipeError, ConnectionResetError):
            pass  # The TV stopped the download
        except Exception as e:  # Keep the helper running whatever happens
            if tools is not None and isinstance(e, tools.ToolError):
                return self.send(e.status, {"error": e.message})
            self.log_error("Unexpected error: %r", e)
            try:
                self.send(500, {"error": "Something went wrong in Tower's helper."})
            except OSError:
                pass

    def do_GET(self):
        self.route("GET")

    def do_POST(self):
        self.route("POST")

    def do_PUT(self):
        self.route("PUT")

    def do_DELETE(self):
        self.route("DELETE")

    def do_HEAD(self):
        self.route("HEAD")



# Benflix: Tower's "address book". The apps ask for it while they're connected and remember every address, so when
# one stops working (Tower moved, the phone left home, the internet address changed) they quietly try the others.
_ADDR_FILES = ("/skips/benflix-addresses.json", "/musicdata/benflix-addresses.json", "/tmp/benflix-addresses.json")
_addr_lock = threading.Lock()


def _addr_file():
    for f in _ADDR_FILES:
        if os.path.isdir(os.path.dirname(f)):
            return f
    return _ADDR_FILES[-1]


def _addr_saved():
    try:
        with open(_addr_file()) as f:
            data = json.load(f)
        return data if isinstance(data, dict) else {}
    except (OSError, ValueError):
        return {}


def learn_internet_address(host, proto):
    """Remembers the internet name (like myserver.duckdns.org) people reach Tower by, from the proxy's Host header."""
    host = (host or "").strip().lower().split(":")[0]
    if not host or not re.fullmatch(r"[a-z0-9.-]+\.[a-z]{2,}", host) or host.startswith("tower"):
        return
    url = ("https://" if (proto or "https").lower() != "http" else "http://") + host
    with _addr_lock:
        data = _addr_saved()
        known = [u for u in data.get("internet", []) if isinstance(u, str)]
        if url in known:
            return
        data["internet"] = ([url] + known)[:4]
        try:
            with open(_addr_file(), "w") as f:
                json.dump(data, f)
        except OSError:
            pass


def _own_ip(towards):
    import socket

    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        try:
            s.connect((towards, 80))
            return s.getsockname()[0]
        finally:
            s.close()
    except OSError:
        return None


def tower_addresses():
    """Every address Tower's Jellyfin can be reached at, best first. The apps check each one is really this server."""
    port = urllib.parse.urlparse(JELLYFIN_URL).port or 8096
    out = []

    def add(url, kind):
        if url and url not in [a["url"] for a in out]:
            out.append({"url": url, "kind": kind})

    home = _own_ip("1.1.1.1")
    if home and not home.startswith("127."):
        add(f"http://{home}:{port}", "home")
    add(f"http://tower:{port}", "name")
    saved = _addr_saved()
    for u in saved.get("internet", []):
        add(u, "internet")
    ts = _own_ip("100.100.100.100")
    if ts and ts.startswith("100.") and 64 <= int(ts.split(".")[1]) <= 127:
        add(f"http://{ts}:{port}", "tailscale")
    for u in saved.get("extra", []):
        add(u, "extra")
    server_id = None
    try:
        server_id = json.loads(urllib.request.urlopen(JELLYFIN_URL + "/System/Info/Public", timeout=4).read()).get("Id")
    except Exception:
        pass
    return {"id": server_id, "addresses": out, "checked": int(time.time())}


# ---------------------------------------------------------------------------------------------------------------
# Request doctor (Ben 2026-11-17): movie and show requests that go through Seerr into Radarr and Sonarr.
#  - Anime movies: Seerr only knows "anime" for shows, so anime movies went to the normal Movies folder with the
#    normal movie rules (which block Japanese + English audio). A Seerr override rule now sends Japanese animated
#    movies to Radarr's anime profile and anime folder. Seerr's Sonarr anime profile/folder are filled in if empty.
#  - Requests Seerr couldn't hand to Radarr/Sonarr ("failed") are retried every few hours instead of looking declined.
#  - Anime ids: AniList / MyAnimeList number -> TMDB number, so the Anime tab opens the right title instead of
#    guessing by name (the live-action One Piece instead of the anime). From Fribb's anime-lists, refreshed daily.
ANIME_IDS_URL = "https://raw.githubusercontent.com/Fribb/anime-lists/master/anime-list-full.json"
ANIME_IDS_FILE = os.path.join(SAVES, ".benflix", "anime-tmdb-ids.json")
DOCTOR_FILE = os.path.join(SAVES, ".benflix", "request-doctor.json")
RETRY_EVERY_S = 6 * 3600
_anime_ids = {"at": 0, "al": {}, "mal": {}}
_anime_ids_lock = threading.Lock()


def _doctor_log(text):
    print(f"Request doctor: {text}", flush=True)


def _anime_ids_load():
    """The AniList/MAL -> TMDB table, from disk or GitHub (once a day)"""
    with _anime_ids_lock:
        if _anime_ids["al"] and time.time() - _anime_ids["at"] < 86400:
            return _anime_ids
        try:
            with open(ANIME_IDS_FILE) as f:
                saved = json.load(f)
            if time.time() - saved.get("at", 0) < 86400 and saved.get("al"):
                _anime_ids.update(saved)
                return _anime_ids
        except (OSError, ValueError):
            saved = None
        try:
            with urllib.request.urlopen(ANIME_IDS_URL, timeout=60) as r:
                rows = json.loads(r.read())
            al, mal = {}, {}
            for x in rows:
                t = x.get("themoviedb_id")
                if not isinstance(t, dict):
                    continue
                key = f"movie:{t['movie']}" if t.get("movie") else (f"tv:{t['tv']}" if t.get("tv") else None)
                if not key:
                    continue
                if x.get("anilist_id"):
                    al[str(x["anilist_id"])] = key
                if x.get("mal_id"):
                    mal[str(x["mal_id"])] = key
            _anime_ids.update({"at": time.time(), "al": al, "mal": mal})
            try:
                os.makedirs(os.path.dirname(ANIME_IDS_FILE), exist_ok=True)
                with open(ANIME_IDS_FILE + ".tmp", "w") as f:
                    json.dump(_anime_ids, f)
                os.replace(ANIME_IDS_FILE + ".tmp", ANIME_IDS_FILE)
            except OSError:
                pass
        except Exception as e:  # GitHub down: keep the old table, the apps fall back to names
            _doctor_log(f"couldn't get the anime id list ({e})")
            if saved and saved.get("al"):
                _anime_ids.update(saved)
        return _anime_ids


def anime_tmdb(query):
    """GET /api/anime/tmdb?anilist=1,21&mal=5 -> {"anilist": {"1": "tv:30991"}, "mal": {...}}"""
    table = _anime_ids_load()

    def pick(kind, src):
        ids = [i for i in re.split(r"[,\s]+", ",".join(query.get(kind, []))) if i.isdigit()][:100]
        return {i: src[i] for i in ids if i in src}

    return {"anilist": pick("anilist", table["al"]), "mal": pick("mal", table["mal"])}


def _doctor_state():
    try:
        with open(DOCTOR_FILE) as f:
            return json.load(f)
    except (OSError, ValueError):
        return {}


def _doctor_save(state):
    try:
        os.makedirs(os.path.dirname(DOCTOR_FILE), exist_ok=True)
        with open(DOCTOR_FILE + ".tmp", "w") as f:
            json.dump(state, f)
        os.replace(DOCTOR_FILE + ".tmp", DOCTOR_FILE)
    except OSError:
        pass


def _anime_profile(profiles):
    return next((p for p in profiles or [] if "anime" in str(p.get("name", "")).lower()), None)


def _anime_folder(folders, want_movies):
    folders = [f for f in folders or [] if "anime" in str(f.get("path", "")).lower()]
    words = ("movie", "film") if want_movies else ("show", "series", "tv")
    return next((f for f in folders if any(w in f["path"].lower() for w in words)), folders[0] if len(folders) == 1 else None)


def _doctor_anime_routing(link, state):
    """Anime movies -> Radarr's anime profile and folder; Seerr's Sonarr anime settings filled in when empty"""
    radarrs = [r for r in (_seerr(link, "GET", "/settings/radarr") or []) if not r.get("is4k")]
    radarr = next((r for r in radarrs if r.get("isDefault")), radarrs[0] if radarrs else None)
    if radarr:
        detail = _seerr(link, "GET", f"/service/radarr/{radarr['id']}") or {}
        prof, folder = _anime_profile(detail.get("profiles")), _anime_folder(detail.get("rootFolders"), True)
        if prof and folder:
            rules = _seerr(link, "GET", "/overrideRule") or []
            mine = [r for r in rules if r.get("radarrServiceId") == radarr["id"] and "16" in str(r.get("genre") or "").split(",")]
            if not mine:
                _seerr(link, "POST", "/overrideRule", {
                    "radarrServiceId": radarr["id"], "genre": "16", "language": "ja",
                    "profileId": prof["id"], "rootFolder": folder["path"],
                })
                _doctor_log(f"anime movies now go to {folder['path']} with {prof['name']}")
        elif not state.get("told-radarr"):
            state["told-radarr"] = True
            _doctor_log("Radarr has no anime profile or no anime movie folder, so anime movies stay with the normal ones")
    sonarrs = [x for x in (_seerr(link, "GET", "/settings/sonarr") or []) if not x.get("is4k")]
    sonarr = next((x for x in sonarrs if x.get("isDefault")), sonarrs[0] if sonarrs else None)
    if sonarr and (not sonarr.get("activeAnimeProfileId") or not sonarr.get("activeAnimeDirectory")):
        detail = _seerr(link, "GET", f"/service/sonarr/{sonarr['id']}") or {}
        prof, folder = _anime_profile(detail.get("profiles")), _anime_folder(detail.get("rootFolders"), False)
        if prof and folder:
            fixed = dict(sonarr)
            fixed.setdefault("animeSeriesType", "anime")
            if not fixed.get("activeAnimeProfileId"):
                fixed["activeAnimeProfileId"], fixed["activeAnimeProfileName"] = prof["id"], prof["name"]
            if not fixed.get("activeAnimeDirectory"):
                fixed["activeAnimeDirectory"] = folder["path"]
            _seerr(link, "PUT", f"/settings/sonarr/{sonarr['id']}", fixed)
            _doctor_log(f"anime shows now go to {fixed['activeAnimeDirectory']} with {prof['name']}")


def _doctor_retry_failed(link, state):
    """Requests Seerr couldn't hand to Radarr/Sonarr get another go every few hours"""
    tried = state.setdefault("retried", {})
    now = time.time()
    answer = _seerr(link, "GET", "/request?take=100&skip=0&filter=failed&sort=modified") or {}
    for req in answer.get("results") or []:
        rid = str(req.get("id"))
        if now - tried.get(rid, 0) < RETRY_EVERY_S:
            continue
        tried[rid] = now
        try:
            _seerr(link, "POST", f"/request/{rid}/retry")
            media = req.get("media") or {}
            _doctor_log(f"retried failed request {rid} ({media.get('mediaType')} {media.get('tmdbId')})")
        except Exception as e:
            _doctor_log(f"retry of request {rid} didn't work ({e})")
    # forget old ones
    state["retried"] = {k: v for k, v in tried.items() if now - v < 30 * 86400}


# ---------------------------------------------------------------------------------------------------------------
# Asks (Ben 2026-11-21):
#  - Why not: when Ben says no to a request he can say why (a ready-made reason or his own words); the person
#    who asked sees it on their list instead of a bare "no".
#  - Auto-yes: people Ben trusts get their movie and show requests said yes to by Tower, up to a number each
#    week that Ben picks. Never Kids (Kids isn't in the family list). Everything past the number waits for Ben.
ASKS_FILE = os.path.join(SAVES, ".benflix", "asks.json")
ASKS_WEEK_S = 7 * 86400
_asks_lock = threading.Lock()


def _asks_load():
    try:
        with open(ASKS_FILE) as f:
            got = json.load(f)
    except (OSError, ValueError):
        got = {}
    got.setdefault("reasons", {})
    got.setdefault("trust", {})
    got.setdefault("auto", [])
    got.setdefault("wish", {})
    return got


def _asks_save(state):
    try:
        os.makedirs(os.path.dirname(ASKS_FILE), exist_ok=True)
        with open(ASKS_FILE + ".tmp", "w") as f:
            json.dump(state, f)
        os.replace(ASKS_FILE + ".tmp", ASKS_FILE)
    except OSError:
        pass


def _asks_plain(uid):
    return re.sub(r"[^0-9a-f]", "", str(uid or "").lower())


def _asks_used(state, uid, now=None):
    now = now or time.time()
    return sum(1 for a in state["auto"] if a.get("user") == uid and now - a.get("at", 0) < ASKS_WEEK_S)


def asks_route(method, path, token, user_id, is_admin, is_kids, read_json):
    """/api/asks/reasons (everyone signed in reads; Ben writes), /api/asks/wish (your own My List of things that
    aren't on Glowfin yet; not Kids) and /api/asks/trust (Ben only)"""
    if path == "/api/asks/wish":
        uid = _asks_plain(user_id)
        if method == "GET":
            with _asks_lock:
                mine = _asks_load()["wish"].get(uid) or {}
            return {"items": sorted(mine.values(), key=lambda w: -w.get("at", 0))}
        if method == "POST":
            if is_kids:
                raise HelperError(403, "Ask a grown-up to add this one.")
            body = read_json() or {}
            kind = "movie" if str(body.get("type")) == "movie" else "tv"
            try:
                tmdb = int(body.get("tmdb"))
            except (TypeError, ValueError):
                raise HelperError(400, "Which title is this?")
            key = f"{kind}:{tmdb}"
            with _asks_lock:
                state = _asks_load()
                mine = state["wish"].setdefault(uid, {})
                if body.get("on", True):
                    mine[key] = {"type": kind, "tmdb": tmdb, "title": str(body.get("title") or "")[:200], "poster": str(body.get("poster") or "")[:200], "at": time.time()}
                    for old in sorted(mine, key=lambda k: -mine[k].get("at", 0))[200:]:
                        mine.pop(old, None)
                else:
                    mine.pop(key, None)
                _asks_save(state)
            return {"ok": True}
    if path == "/api/asks/reasons" and method == "GET":
        with _asks_lock:
            return {"reasons": {k: v.get("reason", "") for k, v in _asks_load()["reasons"].items()}}
    if not is_admin:
        raise HelperError(403, "Only Ben can change this.")
    if path == "/api/asks/reason" and method == "POST":
        body = read_json() or {}
        key = re.sub(r"[^\w:-]", "", str(body.get("key") or ""))[:80]
        reason = " ".join(str(body.get("reason") or "").split())[:140]
        if not key:
            raise HelperError(400, "Which request is this about?")
        with _asks_lock:
            state = _asks_load()
            if reason:
                state["reasons"][key] = {"reason": reason, "at": time.time()}
            else:
                state["reasons"].pop(key, None)
            # a year is plenty
            state["reasons"] = {k: v for k, v in state["reasons"].items() if time.time() - v.get("at", 0) < 365 * 86400}
            _asks_save(state)
        return {"ok": True}
    if path == "/api/asks/trust" and method == "GET":
        people = [p for p in family_people(token) if not p.get("admin")]
        with _asks_lock:
            state = _asks_load()
        out = []
        for p in people:
            uid = _asks_plain(p["id"])
            t = state["trust"].get(uid) or {}
            out.append({"id": p["id"], "name": p["name"], "on": bool(t.get("on")), "weekly": int(t.get("weekly") or 3), "used": _asks_used(state, uid)})
        return {"people": out}
    if path == "/api/asks/trust" and method == "POST":
        body = read_json() or {}
        uid = _asks_plain(body.get("id"))
        if not uid:
            raise HelperError(400, "Who is this for?")
        try:
            weekly = max(1, min(20, int(body.get("weekly") or 3)))
        except (TypeError, ValueError):
            weekly = 3
        with _asks_lock:
            state = _asks_load()
            state["trust"][uid] = {"on": bool(body.get("on")), "weekly": weekly}
            _asks_save(state)
        return {"ok": True}
    if path in ("/api/asks/stop", "/api/asks/undo") and method == "POST":
        body = read_json() or {}
        try:
            rid = int(body.get("id"))
        except (TypeError, ValueError):
            raise HelperError(400, "Which request is this about?")
        link = _seerr_link(token)
        if not link:
            raise HelperError(503, "Requests aren't connected on Tower yet. Check the Jellyfin Enhanced plugin's Seerr settings.")
        if path == "/api/asks/undo":
            return _asks_undo(link, rid)
        return _asks_stop(link, rid, bool(body.get("delete")))
    raise HelperError(404, "Not found")


def _asks_request(link, rid):
    try:
        req = _seerr(link, "GET", f"/request/{rid}")
    except Exception:
        req = None
    if not isinstance(req, dict) or not req.get("id"):
        raise HelperError(404, "That request isn't there any more.")
    return req


def _asks_stop(link, rid, delete):
    """Stop a request that already had a yes (Ben, 2026-11-21): Seerr marks it as said no, and Radarr or Sonarr
    stop looking for it. With delete, whatever already arrived for THIS request goes too. A show keeps the
    seasons nobody asked to stop."""
    req = _asks_request(link, rid)
    media = req.get("media") or {}
    kind = "movie" if req.get("type") == "movie" else "tv"
    try:
        _seerr(link, "POST", f"/request/{rid}/decline")
    except Exception:
        raise HelperError(502, "Seerr didn't take that. Try again in a minute.")
    words = "Stopped."
    if tools is None:
        return {"ok": True, "message": words + " Tower's download tools aren't connected to the helper, so check Radarr or Sonarr yourself."}
    try:
        if kind == "movie":
            words = tools.stop_movie(media.get("tmdbId"), delete)
        else:
            seasons = [s.get("seasonNumber") for s in (req.get("seasons") or []) if isinstance(s.get("seasonNumber"), int)]
            words = tools.stop_show({"tvdb": media.get("tvdbId"), "tmdb": media.get("tmdbId")}, seasons, delete)
    except tools.ToolError as e:
        return {"ok": True, "message": f"Said no in Requests, but the download side didn't answer: {e.message}"}
    return {"ok": True, "message": words}


def _asks_undo(link, rid):
    """Changed my mind: a request Ben said no to goes back to a yes, and Tower starts looking"""
    _asks_request(link, rid)
    try:
        _seerr(link, "POST", f"/request/{rid}/approve")
    except Exception:
        raise HelperError(502, "Seerr didn't take that. Try again in a minute.")
    with _asks_lock:
        state = _asks_load()
        if state["reasons"].pop(f"seerr:{rid}", None) is not None:
            _asks_save(state)
    return {"ok": True, "message": "Back to a yes. Tower starts looking now."}


def _asks_auto_yes(link, token):
    """Says yes to waiting movie and show requests from trusted people who still have some left this week"""
    with _asks_lock:
        state = _asks_load()
    trusted = {uid: t for uid, t in state["trust"].items() if t.get("on")}
    if not trusted:
        return
    by_seerr = {p.get("seerr"): _asks_plain(p["id"]) for p in family_people(token) if p.get("seerr") and not p.get("admin")}
    answer = _seerr(link, "GET", "/request?take=50&skip=0&filter=pending&sort=added") or {}
    waiting = sorted(answer.get("results") or [], key=lambda r: str(r.get("createdAt") or ""))
    now, added = time.time(), []
    for req in waiting:
        uid = by_seerr.get((req.get("requestedBy") or {}).get("id"))
        t = trusted.get(uid) if uid else None
        if not t or _asks_used(state, uid, now) >= int(t.get("weekly") or 3):
            continue
        try:
            _seerr(link, "POST", f"/request/{req.get('id')}/approve")
        except Exception as e:
            _doctor_log(f"auto-yes for request {req.get('id')} didn't work ({e})")
            continue
        entry = {"user": uid, "at": now, "id": req.get("id")}
        state["auto"].append(entry)
        added.append(entry)
        _doctor_log(f"auto-yes for request {req.get('id')}")
    if added:
        with _asks_lock:
            fresh = _asks_load()
            fresh["auto"] = [a for a in fresh["auto"] + added if now - a.get("at", 0) < 5 * ASKS_WEEK_S]
            _asks_save(fresh)


def _asks_wish_arrivals(link, token):
    """My List for things not on Glowfin yet: once one arrives it goes on that person's real My List"""
    with _asks_lock:
        wish = _asks_load()["wish"]
    landed = []
    for uid, mine in wish.items():
        for key, w in mine.items():
            try:
                info = (_seerr(link, "GET", f"/{w.get('type')}/{w.get('tmdb')}") or {}).get("mediaInfo") or {}
            except Exception:
                continue
            item = re.sub(r"[^0-9a-fA-F]", "", str(info.get("jellyfinMediaId") or ""))
            if info.get("status") != 5 or not item:
                continue
            status, _, _ = jellyfin_call("POST", f"/UserFavoriteItems/{item}?userId={uid}", token=token, body=b"", device_id="benflix-helper")
            if status in (200, 204):
                landed.append((uid, key))
                _doctor_log(f"{w.get('title')} arrived and went on a My List")
    if landed:
        with _asks_lock:
            state = _asks_load()
            for uid, key in landed:
                (state["wish"].get(uid) or {}).pop(key, None)
            _asks_save(state)


def asks_auto_yes(admin):
    """Every 3 minutes, so a trusted person's request starts almost right away"""
    time.sleep(150)
    passes = 0
    while True:
        try:
            token = (admin() or (None, None))[0]
            link = _seerr_link(token) if token else None
            if link:
                _asks_auto_yes(link, token)
                if passes % 5 == 0:
                    _asks_wish_arrivals(link, token)
            passes += 1
        except Exception as e:
            _doctor_log(f"auto-yes problem ({e})")
        time.sleep(180)


# ---------- "What people said" (Ben 2026-11-21): two short reviews on the title page ----------
# From TMDb, where the posters come from. TMDb wants a free key for this, which Ben pastes in once on the
# Server page; until then the row simply doesn't show. Kids never get reviews. Kept for a week per title.
REVIEWS_FILE = os.path.join(SAVES, ".benflix", "reviews.json")
REVIEWS_KEEP_S = 7 * 86400
_reviews_lock = threading.Lock()


def _reviews_load():
    try:
        with open(REVIEWS_FILE) as f:
            got = json.load(f)
    except (OSError, ValueError):
        got = {}
    got.setdefault("key", "")
    got.setdefault("cache", {})
    return got


def _reviews_save(state):
    try:
        os.makedirs(os.path.dirname(REVIEWS_FILE), exist_ok=True)
        with open(REVIEWS_FILE + ".tmp", "w") as f:
            json.dump(state, f)
        os.replace(REVIEWS_FILE + ".tmp", REVIEWS_FILE)
    except OSError:
        pass


def _reviews_clean(text):
    text = re.sub(r"https?://\S+", "", str(text or ""))
    text = re.sub(r"<[^>]+>", "", text)
    text = re.sub(r"[*_#>`]+", "", text)
    text = re.sub(r"[ \t]+", " ", text.replace("\r", ""))
    return re.sub(r"\n{3,}", "\n\n", text).strip()


def _reviews_fetch(key, kind, tmdb):
    url = f"https://api.themoviedb.org/3/{kind}/{tmdb}/reviews?language=en-US&page=1"
    headers = {"Accept": "application/json"}
    if key.count(".") == 2:  # the long "API Read Access Token"
        headers["Authorization"] = "Bearer " + key
    else:
        url += "&api_key=" + urllib.parse.quote(key)
    with urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=20) as r:
        rows = json.loads(r.read()).get("results") or []
    out = []
    for row in rows:
        text = _reviews_clean(row.get("content"))
        if len(text) < 120:
            continue
        score = (row.get("author_details") or {}).get("rating")
        out.append({"author": str(row.get("author") or "TMDb member")[:40], "score": score if isinstance(score, (int, float)) else None, "text": text[:4000]})
    # Reviews with a score first, and not walls of text
    out.sort(key=lambda r: (r["score"] is None, len(r["text"]) > 2500))
    return out[:2]


def reviews_route(method, path, query, is_admin, is_kids, read_json):
    if path == "/api/reviews/key":
        if not is_admin:
            raise HelperError(403, "Only Ben can change this.")
        with _reviews_lock:
            state = _reviews_load()
            if method == "POST":
                state["key"] = re.sub(r"\s", "", str(read_json().get("key") or ""))[:400]
                state["cache"] = {}
                _reviews_save(state)
            return {"set": bool(state["key"])}
    if method != "GET" or path != "/api/reviews":
        raise HelperError(404, "Not found")
    kind = "tv" if (query.get("type") or [""])[0] == "tv" else "movie"
    tmdb = re.sub(r"\D", "", (query.get("tmdb") or [""])[0])[:12]
    with _reviews_lock:
        state = _reviews_load()
    if is_kids or not tmdb or not state["key"]:
        return {"reviews": [], "ready": bool(state["key"])}
    ck = f"{kind}:{tmdb}"
    hit = state["cache"].get(ck)
    if hit and time.time() - hit.get("at", 0) < REVIEWS_KEEP_S:
        return {"reviews": hit.get("list") or [], "ready": True}
    try:
        found = _reviews_fetch(state["key"], kind, tmdb)
    except urllib.error.HTTPError as e:
        if e.code == 401:
            raise HelperError(502, "TMDb didn't accept the key. Check it on the Server page.")
        return {"reviews": (hit or {}).get("list") or [], "ready": True}
    except (urllib.error.URLError, OSError, ValueError):
        return {"reviews": (hit or {}).get("list") or [], "ready": True}
    with _reviews_lock:
        state = _reviews_load()
        state["cache"][ck] = {"at": int(time.time()), "list": found}
        if len(state["cache"]) > 3000:
            for old in sorted(state["cache"], key=lambda k: state["cache"][k].get("at", 0))[:1000]:
                state["cache"].pop(old, None)
        _reviews_save(state)
    return {"reviews": found, "ready": True}


# ---------- Activity (Ben 2026-11-21, Ben only): who watches what, and when ----------
# Once a minute Tower looks at who is playing something and adds that minute to this month's totals: per person,
# per title, and per hour of the week (kept in UTC; the page turns it into the viewer's own clock). Counting
# starts the day this runs for the first time, and 13 months are kept.
ACTIVITY_FILE = os.path.join(SAVES, ".benflix", "activity.json")
ACTIVITY_STEP = 60
ACTIVITY_TYPES = {"Movie", "Episode", "Video", "MusicVideo", "TvChannel", "Program", "LiveTvProgram", "Recording"}
_activity_lock = threading.Lock()
_activity = None


def _activity_load():
    global _activity
    if _activity is None:
        try:
            with open(ACTIVITY_FILE) as f:
                _activity = json.load(f)
        except (OSError, ValueError):
            _activity = {}
        _activity.setdefault("months", {})
        _activity.setdefault("since", int(time.time()))
    return _activity


def _activity_save():
    try:
        os.makedirs(os.path.dirname(ACTIVITY_FILE), exist_ok=True)
        with open(ACTIVITY_FILE + ".tmp", "w") as f:
            json.dump(_activity, f)
        os.replace(ACTIVITY_FILE + ".tmp", ACTIVITY_FILE)
    except OSError:
        pass


def _playing_sessions(token):
    status, body, _ = jellyfin_call("GET", "/Sessions?activeWithinSeconds=180", token)
    if status != 200:
        return None
    return [x for x in json.loads(body or b"[]") if x.get("NowPlayingItem")]


def _activity_tick(token):
    sessions = _playing_sessions(token)
    if not sessions:
        return
    now = time.gmtime()
    key = time.strftime("%Y-%m", now)
    changed = False
    with _activity_lock:
        state = _activity_load()
        month = state["months"].setdefault(key, {"people": {}, "titles": {}, "grid": [[0] * 24 for _ in range(7)]})
        for x in sessions:
            item = x.get("NowPlayingItem") or {}
            if item.get("Type") not in ACTIVITY_TYPES or (x.get("PlayState") or {}).get("IsPaused"):
                continue
            who = x.get("UserName") or "Someone"
            live = item.get("Type") in ("TvChannel", "Program", "LiveTvProgram")
            tid = "live" if live else str(item.get("SeriesId") or item.get("Id") or "")
            name = "Live TV" if live else str(item.get("SeriesName") or item.get("Name") or "Something")
            month["people"][who] = month["people"].get(who, 0) + ACTIVITY_STEP
            t = month["titles"].setdefault(tid, {"name": name, "s": 0})
            t["name"] = name
            t["s"] += ACTIVITY_STEP
            month["grid"][now.tm_wday][now.tm_hour] += ACTIVITY_STEP
            changed = True
        if not changed:
            return
        if len(month["titles"]) > 600:  # keep the file small: the long tail of barely-watched titles goes
            keep = sorted(month["titles"].items(), key=lambda kv: -kv[1]["s"])[:400]
            month["titles"] = dict(keep)
        for old in sorted(state["months"])[:-13]:
            state["months"].pop(old, None)
        _activity_save()


def activity_page(month):
    with _activity_lock:
        state = _activity_load()
        months = sorted(state["months"], reverse=True)
        key = month if month in state["months"] else (months[0] if months else time.strftime("%Y-%m", time.gmtime()))
        m = state["months"].get(key) or {"people": {}, "titles": {}, "grid": [[0] * 24 for _ in range(7)]}
        return {
            "month": key,
            "months": months,
            "since": state.get("since"),
            "people": sorted(({"name": k, "s": v} for k, v in m["people"].items()), key=lambda r: -r["s"]),
            "titles": sorted(({"name": v["name"], "s": v["s"]} for v in m["titles"].values()), key=lambda r: -r["s"])[:10],
            "grid": m["grid"],
        }


# ---------- Missing actor photos (Ben 2026-11-21) ----------
# Jellyfin often never fetches photos for the cast of older movies, so they show as a letter in a circle. While
# nobody is watching, Tower asks Jellyfin to look up a few of the actors with no photo (from TMDb, where the
# posters come from). Each actor is only tried once a month; when there truly is no photo, the letter stays.
CAST_FILE = os.path.join(SAVES, ".benflix", "cast-photos.json")
CAST_BATCH = 25
CAST_RETRY_S = 30 * 86400


def _cast_pass(token, user):
    if _playing_sessions(token):
        return  # someone is watching: Tower stays out of the way
    try:
        with open(CAST_FILE) as f:
            state = json.load(f)
    except (OSError, ValueError):
        state = {}
    tried = state.setdefault("tried", {})
    start = int(state.get("start") or 0)
    now = time.time()
    done = 0
    status, body, _ = jellyfin_call("GET", f"/Persons?userId={user}&startIndex={start}&limit=400&enableImages=true&enableUserData=false", token)
    people = (json.loads(body or b"{}").get("Items") or []) if status == 200 else []
    for person in people:
        pid = person.get("Id")
        if not pid or (person.get("ImageTags") or {}).get("Primary") or now - tried.get(pid, 0) < CAST_RETRY_S:
            continue
        jellyfin_call("POST", f"/Items/{pid}/Refresh?metadataRefreshMode=FullRefresh&imageRefreshMode=FullRefresh&replaceAllMetadata=false&replaceAllImages=false", token, body=b"")
        tried[pid] = int(now)
        done += 1
        if done >= CAST_BATCH:
            break
        time.sleep(2)
    # Move on to the next page once this one has nothing left to try (back to the start at the end of the list)
    if done < CAST_BATCH:
        state["start"] = start + 400 if len(people) == 400 else 0
    for pid in [k for k, at in tried.items() if now - at > CAST_RETRY_S * 2]:
        tried.pop(pid, None)
    try:
        os.makedirs(os.path.dirname(CAST_FILE), exist_ok=True)
        with open(CAST_FILE + ".tmp", "w") as f:
            json.dump(state, f)
        os.replace(CAST_FILE + ".tmp", CAST_FILE)
    except OSError:
        pass


def activity_watch(admin):
    """Every minute: count who is watching. Every 20 minutes: fetch a few missing actor photos."""
    time.sleep(90)
    passes = 0
    while True:
        try:
            token, user = admin() or (None, None)
            if token:
                _activity_tick(token)
                if passes % 20 == 10 and user:
                    _cast_pass(token, user)
            passes += 1
        except Exception as e:
            _doctor_log(f"activity problem ({e})")
        time.sleep(ACTIVITY_STEP)


def request_doctor(admin):
    """Hourly. admin() gives (token, user id) of Ben's sign-in (needed to read Seerr's address and key)."""
    time.sleep(120)
    while True:
        try:
            token = (admin() or (None, None))[0]
            link = _seerr_link(token) if token else None
            if link:
                state = _doctor_state()
                for job in (_doctor_anime_routing, _doctor_retry_failed):
                    try:
                        job(link, state)
                    except Exception as e:  # an older Seerr without override rules, Radarr down, ...
                        _doctor_log(f"{job.__name__} skipped ({e})")
                _doctor_save(state)
            _anime_ids_load()
        except Exception as e:
            _doctor_log(f"problem ({e})")
        time.sleep(3600)


def wifi_finder():
    """Benflix: answers "who is JellyfinServer?" on port 7360, so TVs find Tower on the Wi-Fi even when Jellyfin
    (in Docker) can't hear the question or names an old address. The answer says Tower's real address."""
    import socket

    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        sock.bind(("0.0.0.0", 7360))
    except Exception as e:
        print(f"Wi-Fi finder off: {e}", flush=True)
        return
    jf_port = urllib.parse.urlparse(JELLYFIN_URL).port or 8096
    while True:
        try:
            data, sender = sock.recvfrom(1024)
            if b"jellyfin" not in data.lower():
                continue
            info = json.loads(urllib.request.urlopen(JELLYFIN_URL + "/System/Info/Public", timeout=4).read())
            # Tower's own address on the network the question came from
            probe = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            probe.connect(sender)
            mine = probe.getsockname()[0]
            probe.close()
            answer = {"Address": f"http://{mine}:{jf_port}", "Id": info.get("Id"), "Name": info.get("ServerName"), "EndpointAddress": None}
            sock.sendto(json.dumps(answer).encode(), sender)
        except Exception as e:
            print(f"Wi-Fi finder: {e}", flush=True)


if __name__ == "__main__":
    if livetv is not None:
        livetv.start()
    if skips is not None:
        skips.start(jellyfin_call)
    if web is not None:
        web.start()
    if music is not None:
        music.start(jellyfin_call)
    if books is not None:
        books.start(jellyfin_call)
    if care is not None:
        care.start(
            jellyfin_call,
            lambda: ((getattr(skips, "_admin", None) or {}).get("token"), (getattr(skips, "_admin", None) or {}).get("user")) if skips is not None else (None, None),
            tools,
        )
    if posters is not None:
        posters.start(
            jellyfin_call,
            lambda: ((getattr(skips, "_admin", None) or {}).get("token"), (getattr(skips, "_admin", None) or {}).get("user")) if skips is not None else (None, None),
        )
    if arrivals is not None:
        arrivals.start(
            jellyfin_call,
            lambda: ((getattr(skips, "_admin", None) or {}).get("token"), (getattr(skips, "_admin", None) or {}).get("user")) if skips is not None else (None, None),
            tools,
        )
    if facts is not None:
        facts.start(
            jellyfin_call,
            lambda: ((getattr(skips, "_admin", None) or {}).get("token"), (getattr(skips, "_admin", None) or {}).get("user")) if skips is not None else (None, None),
        )
    threading.Thread(
        target=request_doctor,
        args=(lambda: ((getattr(skips, "_admin", None) or {}).get("token"), (getattr(skips, "_admin", None) or {}).get("user")) if skips is not None else (None, None),),
        name="request-doctor",
        daemon=True,
    ).start()
    threading.Thread(
        target=asks_auto_yes,
        args=(lambda: ((getattr(skips, "_admin", None) or {}).get("token"), (getattr(skips, "_admin", None) or {}).get("user")) if skips is not None else (None, None),),
        name="asks-auto-yes",
        daemon=True,
    ).start()
    threading.Thread(
        target=activity_watch,
        args=(lambda: ((getattr(skips, "_admin", None) or {}).get("token"), (getattr(skips, "_admin", None) or {}).get("user")) if skips is not None else (None, None),),
        name="activity-watch",
        daemon=True,
    ).start()
    threading.Thread(target=wifi_finder, daemon=True).start()
    print(f"Benflix games helper on port {PORT} (RomM {ROMM_URL}, Jellyfin {JELLYFIN_URL})", flush=True)
    ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
