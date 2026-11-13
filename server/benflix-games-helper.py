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

try:  # "Did you know" facts for every movie and show, gathered nightly, in benflix_facts.py
    import benflix_facts as facts
except ImportError:
    facts = None

try:  # Home's weather line (a town typed once on the Server page), in benflix_home.py
    import benflix_home as home
except ImportError:
    home = None

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
}

# For games that are a folder of files, the file to start from (best first)
START_FILES = (".m3u", ".cue", ".gdi", ".chd", ".iso", ".rvz", ".wbfs", ".cso", ".pbp")

AWAY_PATH = "/benflix-games"

# Benflix security: pages from inside an ebook (the reader shows them in a frame) may never run their own code.
# Ebooks come from the internet, and a booby-trapped one could otherwise read the reader's sign-in.
BOOK_PAGE_RULES = "script-src 'none'; object-src 'none'; base-uri 'self'; form-action 'none'; frame-ancestors 'self'"

# Benflix: bump this (and SETUP_NEEDED in the app's TowerSetupCheck.kt and web server.js) whenever a change only
# starts working after Ben runs the setup command on Tower again. Older helpers say 0 (they don't send it).
SETUP_VERSION = 2

_games = {}  # RomM id -> (platform folder, file name)
_tokens = {}  # Jellyfin token -> (time it was checked, account id, is admin)
_names = {}  # Jellyfin account id -> the profile's name (for "asked by")
_lock = threading.Lock()


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


def check_signed_in(token):
    """Only people signed in to Jellyfin get the games. Returns (account id, is admin, is kids account).
    Kids accounts only get games rated for everyone (see kids_safe)."""
    if not token:
        raise HelperError(401, "Sign in to Benflix first.")
    with _lock:
        known = _tokens.get(token)
        if known and time.time() - known[0] < 600:
            return known[1], known[2], known[3]
    req = urllib.request.Request(
        JELLYFIN_URL + "/Users/Me", headers={"Authorization": f'MediaBrowser Token="{token}"', "Accept": "application/json"}
    )
    try:
        with urllib.request.urlopen(req, timeout=10) as r:
            user = json.loads(r.read())
    except urllib.error.HTTPError:
        raise HelperError(401, "Your Benflix sign-in wasn't accepted. Try signing in again.")
    except (urllib.error.URLError, OSError):
        raise HelperError(502, "The games helper couldn't reach Jellyfin to check your sign-in.")
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
        if big:
            _big_users.add(user_id)
        else:
            _big_users.discard(user_id)
        _tokens[token] = (time.time(), user_id, is_admin, is_kids)
        _names[user_id] = str(user.get("Name") or "")
    return user_id, is_admin, is_kids


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


def household_popular():
    """What the household watched most in the last 30 days: movies and shows, ranked by how many
    people watched them (then by plays). Uses the admin's sign-in that Tower keeps for skip times,
    and leaves out Kids accounts. Worked out at most once an hour."""
    with _popular_lock:
        if _popular["items"] is not None and time.time() - _popular["at"] < POPULAR_CACHE_S:
            return _popular["items"]
        token = (getattr(skips, "_admin", None) or {}).get("token") if skips is not None else None
        if not token:
            raise HelperError(503, "Ben needs to open Benflix once so Tower can see what everyone watched.")
        status, data, _ = jellyfin_call("GET", "/Users", token=token)
        if status != 200:
            raise HelperError(503, "Tower couldn't read the people list. Ben needs to open Benflix again.")
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

    def send(self, status, body, content_type="application/json"):
        if not isinstance(body, bytes):
            body = json.dumps(body).encode()
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        if content_type.startswith("image/"):
            self.send_header("Cache-Control", "max-age=86400")
        elif content_type.startswith("text/"):
            self.send_header("Cache-Control", "no-cache")
        self.end_headers()
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
            raise HelperError(503, "The Server page tools aren't on the server yet. Run the Benflix setup command again.")
        if method == "POST" and path == "/api/admin/livetv/refresh" and livetv is not None:
            return self.send(200, {"started": livetv.refresh(), **livetv.status()})
        if method == "GET" and path == "/api/admin/tower":
            return self.send(200, tools.tower(fresh=(query.get("fresh") or ["0"])[0] == "1"))
        m = re.fullmatch(r"/api/admin/apps/([\w.-]+)/restart", path)
        if method == "POST" and m:
            return self.send(200, tools.restart_app(m.group(1)))
        m = re.fullmatch(r"/api/admin/downloads/(sonarr|radarr)/(\d+)/(retry|replace)", path)
        if method == "POST" and m:
            return self.send(200, tools.download_action(m.group(1), int(m.group(2)), m.group(3)))
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
                        "home": home is not None,
                        # Which setup the helper came from (the app and web show the yellow setup card when it's older)
                        "setup": SETUP_VERSION,
                    },
                )
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
            if facts is not None and method == "GET" and path == "/facts/titles.txt":
                # Every movie and show with how many facts it has, to copy into a chat (at home only)
                if via_internet:
                    raise HelperError(403, "This list only works at home.")
                return self.send(200, facts.titles_text(few="few" in query).encode("utf-8"), "text/plain; charset=utf-8")
            if web is not None and method in ("GET", "HEAD") and (
                path in ("/", "/web", "/index.html", "/sw.js", "/manifest.webmanifest") or path.startswith("/web/")
            ):
                # Benflix in a web browser (server/web in the repo). The install helper (sw.js) and the
                # app description sit at the top so "Install Benflix" covers the whole site.
                rel = path[len("/web/"):] if path.startswith("/web/") else path.lstrip("/") if path in ("/sw.js", "/manifest.webmanifest") else "index.html"
                found = web.file_for(rel)
                if found:
                    with open(found[0], "rb") as f:
                        return self.send(200, f.read(), found[1])
                if path.startswith("/web/"):
                    raise HelperError(404, "Not found")
            if method == "GET" and path in ("/", "/web", "/index.html"):
                if not os.path.isfile(WEB_PAGE):
                    raise HelperError(404, "The tools page isn't on the server yet. Run the Benflix setup command again.")
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
                    raise HelperError(503, "Live TV isn't on the server yet. Run the Benflix setup command again.")
                return self.send(200, livetv.report_broken(self.read_json(), user_id))
            if path.startswith("/api/skips/"):
                # Skip intro / credits times for the player (anyone signed in; fixes not from kids accounts)
                if skips is None:
                    raise HelperError(503, "Skip times aren't on the server yet. Run the Benflix setup command again.")
                status, answer = skips.route(method, path, query, token, user_id, is_admin, is_kids, self.read_json)
                return self.send(status, answer)
            if path.startswith("/api/facts/"):
                # "Did you know" facts for a movie or show page and the screensaver (anyone signed in)
                if facts is None:
                    raise HelperError(503, "Facts aren't on the server yet. Run the Benflix setup command again.")
                status, answer = facts.route(method, path, query, token, user_id, is_admin)
                return self.send(status, answer)
            if path.startswith("/api/home/") or path.startswith("/api/admin/home/"):
                # Home's weather line (everyone signed in); setting the town is admin only (and at home only, above)
                if home is None:
                    raise HelperError(503, "Home's weather isn't on the server yet. Run the Benflix setup command again.")
                answer = home.route(method, path, self.read_json, is_admin)
                if answer is None:
                    raise HelperError(404, "Not found")
                return self.send(answer[0], answer[1])
            if path.startswith("/api/music/"):
                # Music requests, playlists and the music tidy-up (only the admin, Ben, can use them)
                if music is None:
                    raise HelperError(503, "Music isn't on the server yet. Run the Benflix setup command again.")
                status, answer = music.route(method, path, query, token, user_id, is_admin, self.read_json, is_kids=is_kids)
                return self.send(status, answer)
            if path.startswith("/api/books/"):
                # Books: manga, ebooks and audiobooks (everyone; Kids only see kids' books)
                if books is None:
                    raise HelperError(503, "Books aren't on the server yet. Run the Benflix setup command again.")
                answer = books.route(method, path, query, token, user_id, is_admin, is_kids, self.read_json, user_name=_names.get(user_id, ""))
                if answer[0] == "file":
                    return self.send_range(answer[1], answer[2], head=(method == "HEAD"))
                if len(answer) == 3:
                    return self.send(answer[0], answer[1], answer[2])
                return self.send(answer[0], answer[1])
            if path.startswith("/api/subsync/"):
                # The player's "Subtitles are off" button: Tower lines them up with the voices
                if care is None:
                    raise HelperError(503, "The subtitle fixer isn't on the server yet. Run the Benflix setup command again.")
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
                status, data, ctype = jellyfin_call(
                    method, jf_path, token=token, body=body, device_id=self.web_device(), content_type=self.headers.get("Content-Type")
                )
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
                self.send(500, {"error": "Something went wrong in the games helper."})
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
    if home is not None:
        home.start()
    if facts is not None:
        facts.start(
            jellyfin_call,
            lambda: ((getattr(skips, "_admin", None) or {}).get("token"), (getattr(skips, "_admin", None) or {}).get("user")) if skips is not None else (None, None),
        )
    print(f"Benflix games helper on port {PORT} (RomM {ROMM_URL}, Jellyfin {JELLYFIN_URL})", flush=True)
    ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
