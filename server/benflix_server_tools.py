"""
Benflix Server page tools (admin only). The Benflix helper on Tower (port 8097) loads this file, so the
Server page in Benflix can show and fix things that Jellyfin alone can't see:

- How long Tower has been on, and how hard it's working (processor, memory, graphics chip)
- Which apps (Docker containers) are running, and a Restart button for a stuck one
- What Sonarr and Radarr are downloading, which downloads are stuck, and buttons to fix them
- "Search for missing episodes" (Sonarr) and "Find a better copy" (Radarr)
- The track tidy tool (benflix-fix-tracks.py) with Preview, Fix and Undo

Settings come from environment variables written by benflix-games-helper-setup.sh:
SONARR_URL, SONARR_KEY, RADARR_URL, RADARR_KEY. Only uses the Python standard library.
"""

import http.client
import json
import os
import re
import socket
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.parse
import urllib.request

DOCKER_SOCK = "/var/run/docker.sock"
MEDIA = "/media"  # Homelab/Media on the server
TRACKS_SCRIPT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "benflix-fix-tracks.py")
TRACK_LOGS = "/tracks"  # appdata/benflix-tracks on the server: reports and undo files
CACHE_SECONDS = 20
SKIP_MEDIA_FOLDERS = {"downloads", "incomplete", "trailers", "torrents"}

ARRS = {
    "sonarr": (os.environ.get("SONARR_URL", "").rstrip("/"), os.environ.get("SONARR_KEY", "")),
    "radarr": (os.environ.get("RADARR_URL", "").rstrip("/"), os.environ.get("RADARR_KEY", "")),
}

# Friendly names for apps, in the order they show (anything else shows after these, by its own name)
APP_NAMES = (
    ("jellyfin", "Jellyfin"),
    ("jellyseerr", "Seerr"),
    ("overseerr", "Seerr"),
    ("seerr", "Seerr"),
    ("sonarr", "Sonarr (shows)"),
    ("radarr", "Radarr (movies)"),
    ("qbittorrent", "qBittorrent"),
    ("prowlarr", "Prowlarr"),
    ("bazarr", "Bazarr"),
    ("trailarr", "Trailarr"),
    ("romm", "Games library (RomM)"),
    ("mariadb", "Games database"),
    ("benflix-games", "Benflix helper"),
)
SELF = "benflix-games"  # Restarting the helper from itself would cut off its own answer

_lock = threading.Lock()
_cache = {"at": 0.0, "data": None}
_tracks = {"running": False, "mode": None, "folder": None, "lines": [], "started": None, "finished": None, "result": None}


class ToolError(Exception):
    def __init__(self, status, message):
        super().__init__(message)
        self.status = status
        self.message = message


# ---------------------------------------------------------------------------
# Tower: up since, how busy
# ---------------------------------------------------------------------------


def _cpu_times():
    with open("/proc/stat") as f:
        vals = [int(v) for v in f.readline().split()[1:9]]
    return vals[3] + vals[4], sum(vals)


def cpu_busy():
    """Processor use in % over half a second"""
    try:
        idle1, total1 = _cpu_times()
        time.sleep(0.5)
        idle2, total2 = _cpu_times()
        total = total2 - total1
        return round(100 * (1 - (idle2 - idle1) / total)) if total > 0 else 0
    except (OSError, ValueError, IndexError):
        return None


def memory_busy():
    try:
        info = {}
        with open("/proc/meminfo") as f:
            for line in f:
                k, v = line.split(":", 1)
                info[k] = int(v.split()[0])
        return round(100 * (1 - info["MemAvailable"] / info["MemTotal"]))
    except (OSError, ValueError, KeyError, ZeroDivisionError):
        return None


def gpu_busy():
    """Intel graphics chip: how fast it's running compared to its top speed (a good sign of how busy it is)"""
    try:
        for card in sorted(os.listdir("/sys/class/drm")):
            base = os.path.join("/sys/class/drm", card)
            act, top = os.path.join(base, "gt_act_freq_mhz"), os.path.join(base, "gt_RP0_freq_mhz")
            if os.path.isfile(act) and os.path.isfile(top):
                with open(act) as a, open(top) as t:
                    now, most = int(a.read().strip()), int(t.read().strip())
                return round(100 * now / most) if most > 0 else None
    except (OSError, ValueError):
        pass
    return None


def up_seconds():
    try:
        with open("/proc/uptime") as f:
            return int(float(f.read().split()[0]))
    except (OSError, ValueError, IndexError):
        return None


# ---------------------------------------------------------------------------
# Apps (Docker containers)
# ---------------------------------------------------------------------------


def _docker(method, path, timeout=30):
    if not os.path.exists(DOCKER_SOCK):
        raise ToolError(503, "The helper can't see your apps yet. Run the Benflix setup command on the server again.")
    conn = http.client.HTTPConnection("localhost", timeout=timeout)
    sock = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
    sock.settimeout(timeout)
    try:
        sock.connect(DOCKER_SOCK)
        conn.sock = sock
        conn.request(method, path)
        r = conn.getresponse()
        body = r.read()
    except OSError:
        raise ToolError(502, "Couldn't talk to Docker on the server.")
    finally:
        conn.close()
    if r.status >= 400:
        raise ToolError(502, f"Docker answered with an error ({r.status}).")
    return json.loads(body) if body else None


def _app_rank(low):
    for i, (key, _) in enumerate(APP_NAMES):
        if key in low:
            return i
    return len(APP_NAMES)


def _nice_name(name):
    low = name.lower()
    for key, nice in APP_NAMES:
        if key in low:
            return nice
    return name


def _plain_status(state, status):
    status = status or ""
    if state == "running":
        if "unhealthy" in status:
            return "Running, but not answering properly"
        m = re.match(r"Up (.+?)(?: \(.*\))?$", status)
        return f"Running for {m.group(1)}" if m else "Running"
    if state == "restarting":
        return "Keeps restarting (something is wrong)"
    if state == "paused":
        return "Paused"
    if state == "created":
        return "Never started"
    m = re.search(r"Exited \((\d+)\) (.+)", status)
    return f"Stopped {m.group(2)}" if m else "Stopped"


def apps():
    containers = _docker("GET", "/containers/json?all=1")
    out = []
    for c in containers or []:
        name = (c.get("Names") or ["/?"])[0].lstrip("/")
        state = c.get("State") or ""
        status = c.get("Status") or ""
        out.append(
            {
                "name": name,
                "nice": _nice_name(name),
                "running": state == "running",
                "ok": state == "running" and "unhealthy" not in status,
                "status": _plain_status(state, status),
                "restartable": name.lower() != SELF,
            }
        )
    out.sort(key=lambda a: (_app_rank(a["name"].lower()), a["nice"].lower()))
    return out


def restart_app(name):
    known = {a["name"]: a for a in apps()}
    app = known.get(name)
    if app is None:
        raise ToolError(404, "That app isn't on the server.")
    if not app["restartable"]:
        raise ToolError(400, "The Benflix helper can't restart itself. Restart it in Unraid's Docker tab.")
    _docker("POST", f"/containers/{urllib.parse.quote(name)}/restart?t=15", timeout=90)
    _forget()
    return {"message": f"{app['nice']} restarted. Give it a minute to wake up."}


# ---------------------------------------------------------------------------
# Sonarr and Radarr
# ---------------------------------------------------------------------------


def arr_ready(app):
    url, key = ARRS[app]
    return bool(url and key)


def _arr(app, method, path, body=None, timeout=20):
    url, key = ARRS[app]
    nice = "Sonarr" if app == "sonarr" else "Radarr"
    if not (url and key):
        raise ToolError(503, f"{nice} isn't connected to the helper yet. Run the Benflix setup command on the server again.")
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(
        url + path,
        data=data,
        method=method,
        headers={"X-Api-Key": key, "Content-Type": "application/json", "Accept": "application/json"},
    )
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            raw = r.read()
            return json.loads(raw) if raw else None
    except urllib.error.HTTPError as e:
        if e.code in (401, 403):
            raise ToolError(502, f"{nice} didn't accept the helper's key. Run the Benflix setup command again.")
        raise ToolError(502, f"{nice} answered with an error ({e.code}).")
    except (urllib.error.URLError, OSError):
        raise ToolError(502, f"Couldn't reach {nice}. Check it's running.")


def _time_left(text):
    """"01:02:03" or "1.02:03:04" -> "about 1 hour" """
    if not text:
        return None
    m = re.match(r"(?:(\d+)\.)?(\d+):(\d+):(\d+)", text)
    if not m:
        return None
    days, hours, minutes = int(m.group(1) or 0), int(m.group(2)), int(m.group(3))
    total = days * 24 * 60 + hours * 60 + minutes
    if total < 1:
        return "less than a minute left"
    if total < 60:
        return f"{total} min left"
    if total < 48 * 60:
        h = round(total / 60)
        return f"about {h} hour{'s' if h != 1 else ''} left"
    return f"about {round(total / 1440)} days left"


def _plain_why(message, state, status):
    m = (message or "").lower()
    if "stalled" in m or status == "warning" and not m:
        return "Stalled: nobody is sharing it right now"
    if "no files found are eligible" in m or "no video files" in m:
        return "The download has no video file that can be used"
    if "sample" in m:
        return "It's only a short sample, not the real thing"
    if "not an upgrade" in m:
        return "It's not better than the copy you already have"
    if "unable to determine if file is a sample" in m:
        return "Couldn't check the file, it may be broken"
    if "manual import required" in m or "matched to series by id" in m or "was not found in the grabbed" in m:
        return "Downloaded, but couldn't be matched to the right episode or movie"
    if state in ("importblocked", "importpending") and message:
        return "Downloaded, but couldn't be added to the library: " + message
    if state in ("failedpending", "failed") or status == "failed":
        return "The download failed" + (": " + message if message else "")
    return message


def _download(app, r):
    size = r.get("size") or 0
    left = r.get("sizeleft") or 0
    pct = int(round(100 * (size - left) / size)) if size else 0
    if app == "sonarr":
        series = (r.get("series") or {}).get("title")
        ep = r.get("episode") or {}
        if series and ep:
            title = f"{series} S{ep.get('seasonNumber', 0):02d}E{ep.get('episodeNumber', 0):02d}"
        else:
            title = series or r.get("title") or "Unknown"
    else:
        movie = r.get("movie") or {}
        title = f"{movie['title']} ({movie.get('year')})" if movie.get("title") else (r.get("title") or "Unknown")
    state = (r.get("trackedDownloadState") or "").lower()
    tracked = (r.get("trackedDownloadStatus") or "").lower()
    status = (r.get("status") or "").lower()
    messages = [m for sm in r.get("statusMessages") or [] for m in sm.get("messages") or []]
    message = r.get("errorMessage") or (messages[0] if messages else None)
    stuck = (
        tracked in ("warning", "error")
        or state in ("importblocked", "importfailed", "failedpending", "failed")
        or status in ("failed", "warning")
    )
    return {
        "app": app,
        "id": r.get("id"),
        "title": title,
        "pct": pct,
        "left": None if stuck else _time_left(r.get("timeleft")),
        "stuck": stuck,
        "paused": status == "paused",
        "queued": status in ("queued", "delay"),
        "why": _plain_why(message, state, status) if stuck else None,
    }


def downloads():
    out, problems = [], []
    for app in ("sonarr", "radarr"):
        if not arr_ready(app):
            continue
        extra = "includeSeries=true&includeEpisode=true" if app == "sonarr" else "includeMovie=true"
        try:
            page = _arr(app, "GET", f"/api/v3/queue?page=1&pageSize=100&{extra}") or {}
            out.extend(_download(app, r) for r in page.get("records") or [])
        except ToolError as e:
            problems.append(e.message)
    out.sort(key=lambda d: (not d["stuck"], d["queued"], -d["pct"]))
    return out, problems


def download_action(app, queue_id, action):
    if app not in ARRS:
        raise ToolError(404, "Not found")
    nice = "Sonarr" if app == "sonarr" else "Radarr"
    if action == "retry":
        _arr(app, "POST", "/api/v3/command", {"name": "RefreshMonitoredDownloads"})
        message = f"{nice} is checking its downloads again."
    elif action == "replace":
        _arr(
            app,
            "DELETE",
            f"/api/v3/queue/{int(queue_id)}?removeFromClient=true&blocklist=true&skipRedownload=false",
        )
        message = f"Removed. {nice} won't pick that download again and is looking for another one."
    else:
        raise ToolError(404, "Not found")
    _forget()
    return {"message": message}


def _find_series(info):
    tvdb, name = str(info.get("tvdb") or ""), (info.get("name") or "").strip().lower()
    if tvdb.isdigit():
        found = _arr("sonarr", "GET", f"/api/v3/series?tvdbId={tvdb}") or []
        if found:
            return found[0]
    every = _arr("sonarr", "GET", "/api/v3/series") or []
    tmdb = str(info.get("tmdb") or "")
    for s in every:
        if tmdb and str(s.get("tmdbId") or "") == tmdb:
            return s
    for s in every:
        if name and (s.get("title") or "").strip().lower() == name:
            return s
    return None


def search_missing(info):
    """Asks Sonarr to look for every episode of a show that has aired but isn't on the server"""
    series = _find_series(info)
    if not series:
        raise ToolError(404, "Sonarr doesn't have this show. Add it in Sonarr (or request it) first.")
    episodes = _arr("sonarr", "GET", f"/api/v3/episode?seriesId={series['id']}") or []
    now = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
    missing = [
        e["id"]
        for e in episodes
        if not e.get("hasFile") and e.get("airDateUtc") and e["airDateUtc"] < now and e.get("seasonNumber", 0) > 0
    ]
    if not missing:
        return {"message": f"Sonarr thinks {series['title']} has every episode already. Try Scan for new stuff."}
    _arr("sonarr", "POST", "/api/v3/command", {"name": "EpisodeSearch", "episodeIds": missing[:300]})
    _forget()
    n = len(missing)
    return {"message": f"Sonarr is looking for {n} missing episode{'s' if n != 1 else ''} of {series['title']}."}


def search_better(info):
    """Asks Radarr to look for a better copy of a movie"""
    tmdb, imdb = str(info.get("tmdb") or ""), str(info.get("imdb") or "")
    name = (info.get("name") or "").strip().lower()
    movie = None
    if tmdb.isdigit():
        found = _arr("radarr", "GET", f"/api/v3/movie?tmdbId={tmdb}") or []
        movie = found[0] if found else None
    if movie is None:
        for m in _arr("radarr", "GET", "/api/v3/movie") or []:
            if (imdb and m.get("imdbId") == imdb) or (name and (m.get("title") or "").strip().lower() == name):
                movie = m
                break
    if not movie:
        raise ToolError(404, "Radarr doesn't have this movie. Add it in Radarr (or request it) first.")
    _arr("radarr", "POST", "/api/v3/command", {"name": "MoviesSearch", "movieIds": [movie["id"]]})
    _forget()
    return {
        "message": f"Radarr is looking for a better copy of {movie['title']}. "
        "If your quality settings allow an upgrade, it downloads by itself."
    }


# ---------------------------------------------------------------------------
# Everything on the Server page in one answer (saved for 20 seconds)
# ---------------------------------------------------------------------------


def _forget():
    with _lock:
        _cache["at"] = 0.0


def tower(fresh=False):
    with _lock:
        if not fresh and _cache["data"] is not None and time.time() - _cache["at"] < CACHE_SECONDS:
            return _cache["data"]
    result = {"up_seconds": up_seconds(), "memory": memory_busy(), "gpu": gpu_busy()}
    problems = []
    holder = {}

    def load_apps():
        try:
            holder["apps"] = apps()
        except ToolError as e:
            holder["apps"] = None
            problems.append(e.message)

    def load_downloads():
        holder["downloads"], more = downloads()
        problems.extend(more)

    threads = [threading.Thread(target=load_apps), threading.Thread(target=load_downloads)]
    for t in threads:
        t.start()
    result["cpu"] = cpu_busy()  # Takes half a second, while the others load
    for t in threads:
        t.join(40)
    result.update(
        {
            "apps": holder.get("apps"),
            "downloads": holder.get("downloads") or [],
            "sonarr": arr_ready("sonarr"),
            "radarr": arr_ready("radarr"),
            "problems": problems,
            "tracks": tracks_ready(),
        }
    )
    with _lock:
        _cache.update(at=time.time(), data=result)
    return result


# ---------------------------------------------------------------------------
# Track tidy (benflix-fix-tracks.py)
# ---------------------------------------------------------------------------


def tracks_ready():
    return os.path.isfile(TRACKS_SCRIPT) and os.path.isdir(MEDIA) and _has_mkvtoolnix()


def _has_mkvtoolnix():
    return any(os.access(os.path.join(p, "mkvpropedit"), os.X_OK) for p in os.environ.get("PATH", "").split(":"))


def _folders():
    try:
        return sorted(
            d
            for d in os.listdir(MEDIA)
            if os.path.isdir(os.path.join(MEDIA, d)) and not d.startswith(".") and d.lower() not in SKIP_MEDIA_FOLDERS
        )
    except OSError:
        return []


def _undo_files():
    try:
        names = [n for n in os.listdir(TRACK_LOGS) if re.fullmatch(r"undo-[\w-]+\.json", n)]
    except OSError:
        return []
    return sorted(names, reverse=True)[:5]


def tracks_status():
    with _lock:
        state = dict(_tracks, lines=list(_tracks["lines"]))
    state.update(ready=tracks_ready(), folders=_folders(), undo_files=_undo_files())
    if not state["ready"]:
        state["problem"] = "The track tool isn't set up on the server yet. Run the Benflix setup command on the server again."
    return state


def tracks_start(mode, folder=None, undo_file=None):
    if not tracks_ready():
        raise ToolError(503, "The track tool isn't set up on the server yet. Run the Benflix setup command again.")
    cmd = [sys.executable, "-u", TRACKS_SCRIPT, "--log-dir", TRACK_LOGS]
    if mode in ("preview", "apply"):
        if folder not in _folders():
            raise ToolError(400, "Pick one of the folders.")
        cmd.append(os.path.join(MEDIA, folder))
        if mode == "apply":
            cmd.append("--apply")
    elif mode == "undo":
        if undo_file not in _undo_files():
            raise ToolError(400, "That undo file isn't there any more.")
        cmd += ["--undo", os.path.join(TRACK_LOGS, undo_file)]
    else:
        raise ToolError(400, "Unknown choice.")
    with _lock:
        if _tracks["running"]:
            raise ToolError(409, "The track tool is already running. Wait for it to finish.")
        _tracks.update(running=True, mode=mode, folder=folder or undo_file, lines=[], started=int(time.time()), finished=None, result=None)
    os.makedirs(TRACK_LOGS, exist_ok=True)
    threading.Thread(target=_run_tracks, args=(cmd,), daemon=True).start()
    return tracks_status()


def _run_tracks(cmd):
    env = dict(os.environ, LC_ALL="C.UTF-8", LANG="C.UTF-8", PYTHONIOENCODING="utf-8")
    code = None
    try:
        proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, env=env, errors="replace")
        for line in proc.stdout:
            line = line.rstrip()
            if not line:
                continue
            with _lock:
                lines = _tracks["lines"]
                lines.append(line.replace(MEDIA + "/", ""))
                del lines[:-80]
        code = proc.wait()
    except OSError as e:
        with _lock:
            _tracks["lines"].append(f"Couldn't start the track tool: {e}")
    with _lock:
        _tracks.update(running=False, finished=int(time.time()), result="done" if code == 0 else "failed")
