"""
Benflix games helper: a tiny web service on the Unraid server (port 8097).

- Benflix asks it for the game list. It checks the person is signed in to Jellyfin (and isn't a kids
  account), then reads the games from RomM using the RomM login saved on the server. So nobody has to
  type a RomM login on the TV.
- Games play ON the TV or phone: Benflix downloads the game's files (/api/rom), any BIOS files it needs
  (/api/bios) and the person's saves (/api/save) from here, and sends the saves back when they quit.
  Saves are kept per Jellyfin account in /saves/<account id>/<game id>/.
- Server page tools (admin only): apps, downloads, how busy Tower is, track tidy. See benflix_server_tools.py.

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

try:  # Benflix Server page tools (admin only), in benflix_server_tools.py next to this file
    import benflix_server_tools as tools
except ImportError:
    tools = None

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
    "atari2600": "stella",
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
    "arcade": "fbneo",
    "neogeoaes": "fbneo",
}

# For games that are a folder of files, the file to start from (best first)
START_FILES = (".m3u", ".cue", ".gdi", ".chd", ".iso", ".rvz", ".wbfs", ".cso", ".pbp")

_games = {}  # RomM id -> (platform folder, file name)
_tokens = {}  # Jellyfin token -> (time it was checked, account id, is admin)
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
    """Only people signed in to Jellyfin get the games, and not kids accounts. Returns (account id, is admin)."""
    if not token:
        raise HelperError(401, "Sign in to Benflix first.")
    with _lock:
        known = _tokens.get(token)
        if known and time.time() - known[0] < 600:
            return known[1], known[2]
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
    if policy.get("MaxParentalRating") is not None or user.get("Name", "").lower() == "kids":
        raise HelperError(403, "Games aren't available on kids accounts.")
    user_id = re.sub(r"[^0-9a-fA-F-]", "", str(user.get("Id", "")))
    if not user_id:
        raise HelperError(502, "Jellyfin didn't say who you are.")
    is_admin = bool(policy.get("IsAdministrator"))
    with _lock:
        _tokens[token] = (time.time(), user_id, is_admin)
    return user_id, is_admin


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
                }
            )
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


def rom_files(game_id):
    """The files Benflix downloads to play a game on the TV/phone, and which one to start"""
    folder, path = game_path(game_id)
    start = start_file(path)
    if os.path.isfile(path):
        files = [os.path.basename(path)]
    else:
        files = sorted(f for f in os.listdir(path) if os.path.isfile(os.path.join(path, f)))
    return {
        "console": folder,
        "start": os.path.basename(start),
        "files": [{"name": f, "size": os.path.getsize(os.path.join(os.path.dirname(start), f))} for f in files],
    }


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
    def send(self, status, body, content_type="application/json"):
        if not isinstance(body, bytes):
            body = json.dumps(body).encode()
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        if content_type.startswith("image/"):
            self.send_header("Cache-Control", "max-age=86400")
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

    def admin_route(self, method, path, query):
        """The Server page tools. Returns False when the path isn't one of them."""
        if tools is None:
            raise HelperError(503, "The Server page tools aren't on the server yet. Run the Benflix setup command again.")
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
        try:
            if method == "GET" and path == "/health":
                return self.send(200, {"ok": True, "local_play": True, "server_tools": tools is not None})
            user_id, is_admin = check_signed_in(self.headers.get("X-Benflix-Token", ""))
            if path.startswith("/api/admin/"):
                if not is_admin:
                    raise HelperError(403, "Only the server admin can use the Server page tools.")
                if self.admin_route(method, path, query) is not False:
                    return
                return self.send(404, {"error": "Not found"})
            if method == "GET" and path == "/api/games":
                return self.send(200, load_games())
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
                return self.send_file(rom_file(int(m.group(1)), (query.get("name") or [""])[0]))
            if method == "GET" and path == "/api/bios":
                return self.send(200, bios_list())
            if method == "GET" and path == "/api/bios/file":
                return self.send_file(bios_file((query.get("path") or [""])[0]))
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


if __name__ == "__main__":
    print(f"Benflix games helper on port {PORT} (RomM {ROMM_URL}, Jellyfin {JELLYFIN_URL})", flush=True)
    ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
