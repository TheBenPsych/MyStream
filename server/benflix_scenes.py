"""
Glowfin "Scene by scene" pictures (Ben 2026-11-22: "it only picks background art instead of real scenes").

Most titles on Tower only have one or two background pictures and no chapter pictures, so the game kept showing
the same poster-style art. This grabs REAL frames from the movie file itself with ffmpeg, spread across the movie
(about 10% to 80% in, so no opening logos and no end credits). For a show it picks a different episode for each
picture. ffmpeg's "thumbnail" filter looks at a couple of seconds around each spot and keeps the clearest frame,
so it skips black fades and blurry cuts.

  GET /api/scenes/ok             -> {"ok": true} when ffmpeg is here and there's a folder to keep pictures in
  GET /api/scenes/<item>/<n>.jpg -> picture n (0 to 5) of that movie or show, 1280 wide
  GET /api/scenes/<show>/seasons -> {"seasons": [1, 2, ...]} the seasons with episode files ("Which season?")
  GET /api/scenes/<show>/s<season>-<n>.jpg -> picture n (0 to 2) from an episode of that season
  GET /api/scenes/<item>/quotes  -> {"lines": [...]} lines people say, from the title's English subtitles ("Quote it")

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


TEXT_SUBS = {"subrip", "srt", "ass", "ssa", "mov_text", "webvtt", "text"}
_SRT_TIME = re.compile(r"\d+:\d+:\d+[,.]\d+\s*-->")


def _season_sources(item_id, token, user_id):
    """{season: [(file, seconds long)]} for a show's real episodes"""
    q = {"fields": "Path"}
    if user_id:
        q["userId"] = user_id
    eps = (_get(f"/Shows/{item_id}/Episodes?{urllib.parse.urlencode(q)}", token) or {}).get("Items") or []
    out = {}
    for e in eps:
        k = e.get("ParentIndexNumber") or 0
        if k > 0 and e.get("Path") and e.get("LocationType") != "Virtual":
            out.setdefault(k, []).append((e.get("Path"), _length(e.get("RunTimeTicks"))))
    return out


def _season_make(item_id, season, n, eps, out):
    h = int(hashlib.sha1(f"{item_id}:s{season}:{n}".encode()).hexdigest()[:6], 16)
    path, length = eps[(n * max(1, len(eps) // 3) + h) % len(eps)]
    local = _local_path(path)
    if not local:
        raise LookupError("The file isn't where the helper can see it")
    at = length * (0.2 + (h % 55) / 100) if length > 0 else 300 + n * 240
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


def _srt_lines(text):
    """The spoken lines in an .srt/.vtt/.ass file, in order"""
    lines = []
    if "[Events]" in text:  # .ass / .ssa
        for row in text.splitlines():
            if row.startswith("Dialogue:"):
                parts = row.split(",", 9)
                if len(parts) == 10:
                    lines.append(re.sub(r"\{[^}]*\}", "", parts[9]).replace("\\N", " ").replace("\\n", " "))
        return lines
    for block in re.split(r"\n\s*\n", text.replace("\r", "")):
        rows = [x for x in block.split("\n") if x.strip()]
        rows = [x for x in rows if not x.strip().isdigit() and not _SRT_TIME.search(x) and x.strip() != "WEBVTT"]
        if rows:
            lines.append(" ".join(re.sub(r"<[^>]+>", "", x).strip() for x in rows))
    return lines


def _good_line(x):
    x = x.strip()
    if not 28 <= len(x) <= 120:
        return False
    # No sound notes ("[door slams]", "♪"), no speaker labels, no dialogue dashes, no subtitle credits
    if re.search(r"[\[\]()♪#*]|^-|^[A-Z ]{3,}:|subtit|www\.|\.com|opensub|synced|corrected", x, re.I):
        return False
    return len(x.split()) >= 5


def _quotes(item_id, token, user_id):
    out = os.path.join(_dir, f"{item_id}-quotes.json")
    if os.path.isfile(out):
        with open(out) as f:
            return json.load(f)
    q = {"fields": "Path,MediaSources,MediaStreams"}
    if user_id:
        q["userId"] = user_id
    item = _get(f"/Items/{item_id}?{urllib.parse.urlencode(q)}", token) or {}
    if item.get("Type") == "Series":
        return {"lines": []}
    streams = []
    for src in item.get("MediaSources") or []:
        streams += src.get("MediaStreams") or []
    subs = [st for st in streams if st.get("Type") == "Subtitle" and (st.get("Codec") or "").lower() in TEXT_SUBS
            and (st.get("Language") or "eng").lower() in ("eng", "en", "english", "und")]
    # Full subtitles before "forced" ones (forced only has the foreign bits)
    subs.sort(key=lambda st: (bool(st.get("IsForced")), not st.get("IsExternal")))
    text = ""
    for st in subs:
        try:
            if st.get("IsExternal") and st.get("Path"):
                local = _local_path(st.get("Path"))
                if local:
                    with open(local, encoding="utf-8", errors="replace") as f:
                        text = f.read()
            else:
                local = _local_path(item.get("Path"))
                if local:
                    with _slots:
                        r = subprocess.run(
                            ["nice", "-n", "15", "ffmpeg", "-nostdin", "-v", "error", "-i", local, "-map", f"0:{int(st.get('Index'))}",
                             "-f", "srt", "-"],
                            capture_output=True, timeout=120,
                        )
                    text = r.stdout.decode("utf-8", "replace")
        except Exception:  # noqa: BLE001
            text = ""
        if len(text) > 2000:
            break
    lines = [x for x in _srt_lines(text) if _good_line(x)]
    # Skip the first and last tenth (opening logos, end credits), keep a spread from across the movie
    if len(lines) > 30:
        lines = lines[len(lines) // 10: -len(lines) // 10]
    pick = lines[:: max(1, len(lines) // 24)][:24]
    answer = {"lines": pick}
    with open(out, "w") as f:
        json.dump(answer, f)
    return answer


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
        return 200, {"ok": ok(), "count": COUNT, "seasons": True, "quotes": True}, None
    m = re.fullmatch(r"/api/scenes/([0-9a-fA-F-]{32,36})/(seasons|quotes)", path)
    if m:
        if not ok():
            return 503, {"error": "This needs ffmpeg on Tower. It arrives with Tower's next hourly update."}, None
        item_id = _safe(m.group(1))
        try:
            if m.group(2) == "quotes":
                return 200, _quotes(item_id, token, user_id), None
            return 200, {"seasons": sorted(_season_sources(item_id, token, user_id))}, None
        except Exception as e:  # noqa: BLE001
            return 404, {"error": f"Couldn't read this title ({str(e)[:120]})"}, None
    m = re.fullmatch(r"/api/scenes/([0-9a-fA-F-]{32,36})/s(\d{1,3})-([0-2])\.jpg", path)
    if m:
        if not ok():
            return 503, {"error": "Scene pictures need ffmpeg on Tower."}, None
        item_id, season, n = _safe(m.group(1)), int(m.group(2)), int(m.group(3))
        out = os.path.join(_dir, f"{item_id}-s{season}-{n}.jpg")
        try:
            if os.path.isfile(out):
                q = urllib.parse.urlencode({"userId": user_id} if user_id else {})
                _get(f"/Items/{item_id}?{q}", token)
            else:
                eps = _season_sources(item_id, token, user_id).get(season)
                if not eps:
                    return 404, {"error": "No episodes of that season"}, None
                _season_make(item_id, season, n, eps, out)
        except Exception as e:  # noqa: BLE001
            return 404, {"error": f"Couldn't take a picture ({str(e)[:120]})"}, None
        with open(out, "rb") as f:
            return 200, f.read(), "image/jpeg"
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
