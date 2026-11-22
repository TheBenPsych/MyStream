"""
Glowfin "Scene by scene" pictures (Ben 2026-11-22: "it only picks background art instead of real scenes").

Most titles on Tower only have one or two background pictures and no chapter pictures, so the game kept showing
the same poster-style art. This grabs REAL frames from the movie file itself with ffmpeg, spread across the movie
(about 10% to 80% in, so no opening logos and no end credits). For a show it picks a different episode for each
picture. ffmpeg's "thumbnail" filter looks at a couple of seconds around each spot and keeps the clearest frame,
so it skips black fades and blurry cuts.

  GET /api/scenes/ok             -> {"ok": true} when ffmpeg is here and there's a folder to keep pictures in
  GET /api/scenes/<item>/<n>.jpg -> picture n (0 to 5) of that movie or show, 1280 wide

Pictures are made once and kept in /skips/scenes (about 100 KB each). The first picture of a title also starts
making the other five in the background, so the next scene in a round is ready by the time it's needed.
At most two are made at the same time, at low priority, so nobody's movie stutters.

Loaded by benflix-games-helper.py (it sits next to it on Tower). Only uses the Python standard library.
"""

import hashlib
import json
import os
import re
import shutil
import subprocess
import tempfile
import threading
import urllib.parse

COUNT = 6
# Where in the movie each picture comes from (part of the way through)
SPOTS = (0.12, 0.25, 0.37, 0.5, 0.62, 0.76)
_jf = None
_dir = None
_slots = threading.Semaphore(2)
_busy = set()
_lock = threading.Lock()


def _pick_dir():
    for d in (os.environ.get("SCENES_DIR"), "/skips/scenes", "/tracks/benflix-scenes", "/tmp/benflix-scenes"):
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


def start(jellyfin_call):
    """Called once by the helper when it starts"""
    global _jf, _dir
    _jf = jellyfin_call
    _dir = _pick_dir()
    print(f"scenes: {'ready in ' + _dir if _dir else 'no folder to keep pictures in'}; ffmpeg {'found' if shutil.which('ffmpeg') else 'MISSING'}", flush=True)


def ok():
    return bool(_dir and shutil.which("ffmpeg"))


def _safe(item_id):
    return re.sub(r"[^0-9a-fA-F]", "", str(item_id or "")).lower()


def _get(path, token):
    status, data, _ = _jf("GET", path, token=token)
    if status != 200:
        raise LookupError(f"Jellyfin answered {status}")
    return json.loads(data or b"null")


def _local_path(path):
    """Jellyfin's path for a file, as the helper sees it (both have the Media folder at /media)"""
    if not path:
        return None
    if os.path.isfile(path):
        return path
    for prefix in ("/media", "/data", "/mnt/user/Homelab/Media", "/mnt/user/media"):
        if path.startswith(prefix + "/"):
            guess = "/media" + path[len(prefix):]
            if os.path.isfile(guess):
                return guess
    return None


def _length(ticks):
    return (ticks or 0) / 10_000_000


def _sources(item_id, token, user_id):
    """[(file, seconds long)] to take pictures from: the movie itself, or a spread of a show's episodes"""
    q = {"fields": "Path,MediaSources"}
    if user_id:
        q["userId"] = user_id
    item = _get(f"/Items/{item_id}?{urllib.parse.urlencode(q)}", token) or {}
    if item.get("Type") == "Series":
        q.update({"fields": "Path"})
        eps = (_get(f"/Shows/{item_id}/Episodes?{urllib.parse.urlencode(q)}", token) or {}).get("Items") or []
        eps = [e for e in eps if e.get("LocationType") != "Virtual" and (e.get("ParentIndexNumber") or 0) > 0] or eps
        return [(e.get("Path"), _length(e.get("RunTimeTicks"))) for e in eps if e.get("Path")]
    return [(item.get("Path"), _length(item.get("RunTimeTicks")))] if item.get("Path") else []


def _spot(sources, item_id, n):
    """The file and second for picture n"""
    if len(sources) == 1:
        path, length = sources[0]
        return path, length * SPOTS[n]
    # A show: a different episode for each picture, spread across the whole show, from the middle part of it
    h = int(hashlib.sha1(f"{item_id}:{n}".encode()).hexdigest()[:6], 16)
    if len(sources) >= COUNT:
        path, length = sources[(n * len(sources)) // COUNT + h % (len(sources) // COUNT)]
    else:
        path, length = sources[(n + h) % len(sources)]
    return path, length * (0.25 + (h % 45) / 100)


def _make(item_id, n, sources, out):
    path, at = _spot(sources, item_id, n)
    local = _local_path(path)
    if not local:
        raise LookupError("The file isn't where the helper can see it")
    if at <= 0:
        at = 600 + n * 600  # no length known: a few minutes in
    with _slots, tempfile.TemporaryDirectory(prefix="benflix-scene-") as d:
        tmp = os.path.join(d, "s.jpg")
        subprocess.run(
            ["nice", "-n", "15", "ffmpeg", "-nostdin", "-v", "error", "-threads", "2", "-ss", f"{at:.1f}", "-i", local,
             "-an", "-sn", "-dn", "-vf", "thumbnail=48,scale=1280:-2", "-frames:v", "1", "-q:v", "4", "-y", tmp],
            check=True, capture_output=True, timeout=90,
        )
        if not os.path.isfile(tmp) or os.path.getsize(tmp) < 2000:
            raise LookupError("No picture came out")
        shutil.move(tmp, out)


def _file(item_id, n):
    return os.path.join(_dir, f"{item_id}-{n}.jpg")


def _warm(item_id, sources):
    """Makes the rest of a title's pictures in the background"""
    for k in range(COUNT):
        out = _file(item_id, k)
        key = (item_id, k)
        with _lock:
            if os.path.isfile(out) or key in _busy:
                continue
            _busy.add(key)
        try:
            _make(item_id, k, sources, out)
        except Exception:  # noqa: BLE001
            pass
        finally:
            with _lock:
                _busy.discard(key)


def route(method, path, token, user_id):
    """Returns (status, answer, content type or None for JSON)"""
    if method not in ("GET", "HEAD"):
        return 405, {"error": "Not allowed"}, None
    if path == "/api/scenes/ok":
        return 200, {"ok": ok(), "count": COUNT}, None
    m = re.fullmatch(r"/api/scenes/([0-9a-fA-F-]{32,36})/(\d)\.jpg", path)
    if not m or int(m.group(2)) >= COUNT:
        return 404, {"error": "Not found"}, None
    if not ok():
        return 503, {"error": "Scene pictures need ffmpeg on Tower. They arrive with Tower's next hourly update."}, None
    item_id, n = _safe(m.group(1)), int(m.group(2))
    out = _file(item_id, n)
    if os.path.isfile(out):
        # Kept from before: still check this person may see the title (Kids only see Kids titles)
        try:
            q = urllib.parse.urlencode({"userId": user_id} if user_id else {})
            _get(f"/Items/{item_id}?{q}", token)
        except Exception:  # noqa: BLE001
            return 404, {"error": "Not found"}, None
    else:
        try:
            # Asking Jellyfin with the person's own sign-in also checks they're allowed to see this title
            sources = _sources(item_id, token, user_id)
            if not sources:
                return 404, {"error": "No video file for this title"}, None
            _make(item_id, n, sources, out)
            threading.Thread(target=_warm, args=(item_id, sources), name="scenes-warm", daemon=True).start()
        except Exception as e:  # noqa: BLE001
            return 404, {"error": f"Couldn't take a picture from this title ({str(e)[:120]})"}, None
    with open(out, "rb") as f:
        return 200, f.read(), "image/jpeg"
