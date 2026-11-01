"""
Benflix games helper: a tiny web service on the Unraid server (port 8097).

- Benflix asks it for the game list. It checks the person is signed in to Jellyfin (and isn't a kids
  account), then reads the games from RomM using the RomM login saved on the server. So nobody has to
  type a RomM login on the TV.
- When someone presses Play, it writes down which game to start, then Benflix opens the "Benflix" app in
  Moonlight. That app (in Wolf) reads the note and starts the game straight away in RetroArch.

Settings come from environment variables: ROMM_URL, ROMM_USER, ROMM_PASS, JELLYFIN_URL, WOLF_URL.
Started by benflix-games-setup.sh. Only uses the Python standard library.
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

ROMM_URL = os.environ.get("ROMM_URL", "http://127.0.0.1:8095").rstrip("/")
ROMM_AUTH = "Basic " + base64.b64encode(
    f"{os.environ.get('ROMM_USER', '')}:{os.environ.get('ROMM_PASS', '')}".encode()
).decode()
JELLYFIN_URL = os.environ.get("JELLYFIN_URL", "http://127.0.0.1:8096").rstrip("/")
WOLF_URL = os.environ.get("WOLF_URL", "http://127.0.0.1:47989").rstrip("/")
ROMS = "/ROMs"  # The same folder the game app sees
NEXT_GAME = "/benflix/next-game"
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
_tokens = {}  # Jellyfin token -> time it was checked
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
    """Only people signed in to Jellyfin get the games, and not kids accounts"""
    if not token:
        raise HelperError(401, "Sign in to Benflix first.")
    with _lock:
        if time.time() - _tokens.get(token, 0) < 600:
            return
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
    with _lock:
        _tokens[token] = time.time()


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
    consoles = {}
    with _lock:
        for g in games:
            folder = g.get("platform_fs_slug") or g.get("platform_slug") or ""
            _games[g["id"]] = (folder, g.get("fs_name") or "")
            name = g.get("platform_display_name") or g.get("platform_custom_name") or g.get("platform_name") or folder
            has_cover = bool(g.get("path_cover_large") or g.get("path_cover_small") or g.get("url_cover"))
            consoles.setdefault(name, []).append(
                {
                    "id": g["id"],
                    "name": g.get("name") or g.get("fs_name_no_tags") or g.get("fs_name") or "Unknown game",
                    "cover": f"/api/cover/{g['id']}" if has_cover else None,
                    "playable": folder in CORES,
                }
            )
    return {
        "consoles": [
            {"name": n, "games": sorted(gs, key=lambda x: x["name"].lower())} for n, gs in sorted(consoles.items())
        ]
    }


def cover(game_id):
    g = romm_get(f"/api/roms/{game_id}")
    path = g.get("path_cover_large") or g.get("path_cover_small")
    if path:
        return romm_get("/assets/romm/resources/" + path.lstrip("/"), raw=True)
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


def play(game_id):
    with _lock:
        known = _games.get(game_id)
    if known is None:
        g = romm_get(f"/api/roms/{game_id}")
        known = (g.get("platform_fs_slug") or g.get("platform_slug") or "", g.get("fs_name") or "")
    folder, name = known
    core = CORES.get(folder)
    if core is None:
        raise HelperError(400, "This console can't be played through Benflix yet.")
    path = os.path.realpath(os.path.join(ROMS, folder, name))
    if not path.startswith(ROMS + "/") or not name:
        raise HelperError(400, "That game's file name looks wrong.")
    rom = start_file(path)
    tmp = NEXT_GAME + ".tmp"
    with open(tmp, "w") as f:
        f.write(f"{core}\n{rom}\n")
    os.chmod(tmp, 0o666)
    os.replace(tmp, NEXT_GAME)
    return {"ok": True, "app": "Benflix", "computer": wolf_id()}


def wolf_id():
    """Moonlight knows the server by this id"""
    try:
        with urllib.request.urlopen(WOLF_URL + "/serverinfo", timeout=5) as r:
            text = r.read().decode(errors="replace")
        m = re.search(r"<uniqueid>([^<]+)</uniqueid>", text)
        h = re.search(r"<hostname>([^<]+)</hostname>", text)
        return {"uuid": m.group(1) if m else None, "name": h.group(1) if h else None}
    except (urllib.error.URLError, OSError):
        return {"uuid": None, "name": None}


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

    def route(self, method):
        path = urllib.parse.urlparse(self.path).path
        try:
            if method == "GET" and path == "/health":
                return self.send(200, {"ok": True})
            check_signed_in(self.headers.get("X-Benflix-Token", ""))
            if method == "GET" and path == "/api/games":
                return self.send(200, load_games())
            m = re.fullmatch(r"/api/cover/(\d+)", path)
            if method == "GET" and m:
                data, ctype = cover(int(m.group(1)))
                return self.send(200, data, ctype)
            m = re.fullmatch(r"/api/play/(\d+)", path)
            if method == "POST" and m:
                return self.send(200, play(int(m.group(1))))
            self.send(404, {"error": "Not found"})
        except HelperError as e:
            self.send(e.status, {"error": e.message})
        except Exception as e:  # Keep the helper running whatever happens
            self.log_error("Unexpected error: %r", e)
            self.send(500, {"error": "Something went wrong in the games helper."})

    def do_GET(self):
        self.route("GET")

    def do_POST(self):
        self.route("POST")


if __name__ == "__main__":
    print(f"Benflix games helper on port {PORT} (RomM {ROMM_URL}, Jellyfin {JELLYFIN_URL})", flush=True)
    ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
