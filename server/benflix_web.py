"""
Benflix in a web browser: the files for the web version and the bits of the helper only it needs.

- The web version lives in server/web/ in the MyStream repo (index.html, CSS, JavaScript, fonts, sounds).
  web/files.txt lists every file. The setup command copies them to /app/web, and the helper also
  fetches the newest copy from GitHub when it starts and then every hour, so web-only updates show up
  by themselves without running the setup command again. If GitHub can't be reached, the copy from
  the setup command is used.
- Quick Connect sign-in (type a code on a TV or phone that is already signed in).
- Video for the browser player: passes Jellyfin's video pieces through bit by bit, so long movies
  don't have to fit in memory.

Only uses the Python standard library.
"""

import json
import os
import shutil
import threading
import time
import urllib.error
import urllib.request

RAW = "https://raw.githubusercontent.com/TheBenPsych/MyStream/main/server/web"
SHIPPED = os.path.join(os.path.dirname(os.path.abspath(__file__)), "web")
CACHE = "/tmp/benflix-web"
CHECK_EVERY = 3600

TYPES = {
    ".html": "text/html; charset=utf-8",
    ".css": "text/css; charset=utf-8",
    ".js": "text/javascript; charset=utf-8",
    ".json": "application/json",
    ".txt": "text/plain; charset=utf-8",
    ".svg": "image/svg+xml",
    ".png": "image/png",
    ".ico": "image/x-icon",
    ".ttf": "font/ttf",
    ".woff2": "font/woff2",
    ".wav": "audio/wav",
    ".mp3": "audio/mpeg",
    ".wasm": "application/wasm",
}

_lock = threading.Lock()
_live = None  # folder the helper serves from right now (newest download, or None = the setup copy)
_status = {"source": "setup", "checked": None, "problem": None}


def _fetch(url, timeout=30):
    req = urllib.request.Request(url, headers={"User-Agent": "benflix-helper", "Cache-Control": "no-cache"})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.read()


def _safe(name):
    name = name.strip()
    if not name or name.startswith("#") or name.startswith("/") or ".." in name.split("/"):
        return None
    return name


def update():
    """Fetches the newest web version from GitHub into a fresh folder, then switches to it."""
    global _live
    try:
        names = [n for n in (_safe(x) for x in _fetch(RAW + "/files.txt").decode().splitlines()) if n]
        if "index.html" not in names:
            raise ValueError("files.txt doesn't list index.html")
        folder = os.path.join(CACHE, str(int(time.time())))
        for n in names:
            data = _fetch(RAW + "/" + n)
            dest = os.path.join(folder, n)
            os.makedirs(os.path.dirname(dest), exist_ok=True)
            with open(dest, "wb") as f:
                f.write(data)
        with _lock:
            old, _live = _live, folder
            _status.update(source="github", checked=int(time.time()), problem=None)
        if old and old != folder:
            shutil.rmtree(old, ignore_errors=True)
        return True
    except (urllib.error.URLError, OSError, ValueError) as e:
        with _lock:
            _status.update(checked=int(time.time()), problem=f"Couldn't get the newest web version: {e}")
        return False


def _loop():
    while True:
        update()
        time.sleep(CHECK_EVERY)


def start():
    threading.Thread(target=_loop, daemon=True, name="benflix-web-update").start()


def status():
    with _lock:
        return dict(_status, ready=bool(_live) or os.path.isfile(os.path.join(SHIPPED, "index.html")))


def file_for(rel):
    """(full path, content type) of a web version file, or None. rel is like "index.html" or "js/app.js"."""
    rel = _safe(rel or "index.html")
    if not rel:
        return None
    with _lock:
        roots = [r for r in (_live, SHIPPED) if r]
    for root in roots:
        full = os.path.realpath(os.path.join(root, rel))
        if full.startswith(os.path.realpath(root) + os.sep) and os.path.isfile(full):
            return full, TYPES.get(os.path.splitext(full)[1].lower(), "application/octet-stream")
    return None


# ---------- Quick Connect ----------


def quick_connect_start(jellyfin_call, device):
    status, data, _ = jellyfin_call("POST", "/QuickConnect/Initiate", device_id=device)
    if status == 401 or status == 403 or status == 400:
        raise ValueError("Quick Connect is turned off on the server. Turn it on in Jellyfin's Dashboard > General.")
    if status != 200:
        raise ValueError(f"Jellyfin answered with an error ({status}).")
    d = json.loads(data)
    return {"code": d.get("Code"), "secret": d.get("Secret")}


def quick_connect_check(jellyfin_call, device, secret):
    status, data, _ = jellyfin_call("GET", "/QuickConnect/Connect?secret=" + urllib.request.quote(secret), device_id=device)
    if status != 200:
        return {"ok": False, "gone": True}
    return {"ok": bool(json.loads(data).get("Authenticated"))}


def quick_connect_finish(jellyfin_call, device, secret):
    body = json.dumps({"Secret": secret}).encode()
    status, data, _ = jellyfin_call("POST", "/Users/AuthenticateWithQuickConnect", body=body, device_id=device)
    if status != 200:
        raise ValueError("That code expired. Try again.")
    return json.loads(data)


# ---------- Video for the browser player ----------

PASS_HEADERS = ("Content-Type", "Content-Length", "Content-Range", "Accept-Ranges", "Last-Modified", "ETag")


def stream(handler, url, auth, timeout=60):
    """Sends one Jellyfin video/audio/subtitle request on to the browser, a piece at a time.
    Keeps the browser's Range header so seeking in a direct-play file works."""
    headers = {"Authorization": auth}
    rng = handler.headers.get("Range")
    if rng:
        headers["Range"] = rng
    req = urllib.request.Request(url, headers=headers)
    try:
        r = urllib.request.urlopen(req, timeout=timeout)
    except urllib.error.HTTPError as e:
        r = e
    with r:
        handler.send_response(r.status if hasattr(r, "status") else r.code)
        for h in PASS_HEADERS:
            v = r.headers.get(h)
            if v:
                handler.send_header(h, v)
        handler.send_header("Cache-Control", "no-store")
        handler.end_headers()
        if handler.command == "HEAD":
            return
        while True:
            chunk = r.read(256 * 1024)
            if not chunk:
                break
            handler.wfile.write(chunk)
