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
    ("benflix-games", "Glowfin helper"),
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


def disks():
    """Free space where the movies and shows live, straight from Tower (instant, unlike Jellyfin's check)"""
    try:
        st = os.statvfs(MEDIA)
    except OSError:
        return []
    total, free = st.f_blocks * st.f_frsize, st.f_bavail * st.f_frsize
    if total <= 0:
        return []
    return [{"name": "Movies and shows", "used": total - free, "free": free}]


# Drive health (Idea Box 24): Scrutiny reads each drive's own health report (SMART) and spots the warning signs
# of a drive that's about to fail. The setup command finds the Scrutiny app and saves its address.
_drives_cache = {"at": 0.0, "data": None}
DRIVES_SECONDS = 600


def _drive_words(status):
    """Scrutiny's status number in plain words (0 is healthy)"""
    if not status:
        return "Healthy"
    if status & 1:
        return "Failing: the drive says it's going wrong"
    return "Warning signs: back up anything you can't download again"


def drives(fresh=False):
    """Each drive's health from Scrutiny, worst first. {"set_up": False} when Scrutiny isn't connected."""
    url = os.environ.get("SCRUTINY_URL", "").rstrip("/")
    if not url:
        return {"set_up": False, "drives": []}
    with _lock:
        if not fresh and _drives_cache["data"] is not None and time.time() - _drives_cache["at"] < DRIVES_SECONDS:
            return _drives_cache["data"]
    try:
        with urllib.request.urlopen(url + "/api/summary", timeout=15) as r:
            summary = (json.loads(r.read()).get("data") or {}).get("summary") or {}
    except (OSError, ValueError, urllib.error.URLError):
        raise ToolError(502, "Couldn't reach Scrutiny (the drive health app). Is it running?")
    out = []
    for one in summary.values():
        dev = one.get("device") or {}
        smart = one.get("smart") or {}
        status = int(dev.get("device_status") or 0)
        size = int(dev.get("capacity") or 0)
        name = " ".join(x for x in (dev.get("label") or "", dev.get("model_name") or "") if x).strip()
        out.append(
            {
                "name": name or dev.get("device_name") or "Drive",
                "where": "/dev/" + dev["device_name"] if dev.get("device_name") else "",
                "size": size,
                "ok": status == 0,
                "words": _drive_words(status),
                "temp": smart.get("temp"),
                "hours": smart.get("power_on_hours"),
                "checked": smart.get("collector_date") or "",
            }
        )
    out.sort(key=lambda d: (d["ok"], d["name"].lower()))
    data = {"set_up": True, "drives": out, "link": url}
    with _lock:
        _drives_cache.update(at=time.time(), data=data)
    return data


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
        raise ToolError(503, "The helper can't see your apps yet. Run the Glowfin setup command on the server again.")
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


# Exit codes Docker gives an app that was stopped on purpose: 0 (stopped cleanly), 143 (asked to stop) and
# 137 (made to stop after it didn't in time, unless it ran out of memory)
STOPPED_CODES = (0, 137, 143)


def _exit_code(status):
    m = re.search(r"Exited \((-?\d+)\)", status or "")
    return int(m.group(1)) if m else None


def _out_of_memory(container_id):
    if not container_id:
        return False
    try:
        info = _docker("GET", f"/containers/{urllib.parse.quote(container_id)}/json", timeout=10) or {}
        return bool((info.get("State") or {}).get("OOMKilled"))
    except ToolError:
        return False


def _problem(state, status, code, oom):
    """True when an app needs a look: keeps restarting, not answering, or stopped by itself with an error"""
    if state == "running":
        return "unhealthy" in (status or "")
    if state in ("restarting", "dead"):
        return True
    if state == "exited":
        return oom or (code is not None and code not in STOPPED_CODES)
    return False  # paused, created (never started) and the like were left that way on purpose


def _plain_status(state, status, code=None, oom=False):
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
    if state == "dead":
        return "Broken (Docker couldn't stop it properly)"
    m = re.search(r"Exited \((-?\d+)\) (.+)", status)
    when = f" {m.group(2)}" if m else ""
    if oom:
        return f"Ran out of memory and stopped{when}"
    if code is not None and code not in STOPPED_CODES:
        return f"Stopped by itself with an error{when}"
    return f"Stopped{when}"


# Docker's "unhealthy" comes from a little self-test baked into some app images (slskd, some Sonarr builds). That
# self-test often fails while the app itself is fine (a changed port, web sign-in turned on, a tool missing inside the
# image, a network change), which made the Server page cry wolf. So before calling an app broken, the helper knocks on
# the app's door itself. If the app answers, it's working, whatever Docker's self-test says.
KNOWN_ADDRESSES = {
    "sonarr": lambda: [ARRS["sonarr"][0] + "/ping"] if ARRS["sonarr"][0] else [],
    "radarr": lambda: [ARRS["radarr"][0] + "/ping"] if ARRS["radarr"][0] else [],
    "slskd": lambda: [os.environ.get("SLSKD_URL", "").rstrip("/") + "/health"] if os.environ.get("SLSKD_URL") else [],
    "lidarr": lambda: [os.environ.get("LIDARR_URL", "").rstrip("/") + "/ping"] if os.environ.get("LIDARR_URL") else [],
}
CHECK_SECONDS = 4


def _http_answers(url):
    """True when something answers at this web address at all (even "sign in first" counts: the app is awake)"""
    try:
        with urllib.request.urlopen(urllib.request.Request(url, headers={"Accept": "*/*"}), timeout=CHECK_SECONDS):
            return True
    except urllib.error.HTTPError as e:
        return e.code < 500
    except (urllib.error.URLError, OSError, ValueError):
        return False


def _port_open(host, port):
    try:
        with socket.create_connection((host, int(port)), timeout=CHECK_SECONDS):
            return True
    except (OSError, ValueError):
        return False


def _app_doors(c, info):
    """Web addresses the helper (on Tower's own network) can try for this app, best first"""
    low = ((c.get("Names") or ["/?"])[0]).lower()
    urls = []
    for key, known in KNOWN_ADDRESSES.items():
        if key in low:
            urls += known()
    ports = set()
    for p in c.get("Ports") or []:
        if p.get("PublicPort") and p.get("Type", "tcp") == "tcp":
            ports.add(("127.0.0.1", p["PublicPort"]))
    exposed = [k.split("/")[0] for k in ((info.get("Config") or {}).get("ExposedPorts") or {}) if k.endswith("/tcp")]
    nets = (info.get("NetworkSettings") or {}).get("Networks") or {}
    mode = ((info.get("HostConfig") or {}).get("NetworkMode") or "").lower()
    for port in exposed:
        if mode == "host":
            ports.add(("127.0.0.1", port))
        for net in nets.values():
            if net.get("IPAddress"):
                ports.add((net["IPAddress"], port))
    for host, port in sorted(ports, key=lambda hp: (hp[0] != "127.0.0.1", str(hp[1]))):
        urls.append(f"http://{host}:{port}/")
    return urls, ports


def _health_note(info):
    """The last thing Docker's self-test said, in short, for when the app really doesn't answer"""
    log = ((info.get("State") or {}).get("Health") or {}).get("Log") or []
    text = " ".join(((log[-1] if log else {}).get("Output") or "").split())
    return text[:140]


def _second_opinion(c):
    """(answers, note). answers is True when the app answers the helper, so Docker's "unhealthy" was a false alarm."""
    try:
        info = _docker("GET", f"/containers/{urllib.parse.quote(c.get('Id') or '')}/json", timeout=10) or {}
    except ToolError:
        return None, ""
    urls, ports = _app_doors(c, info)
    for url in urls[:6]:
        if _http_answers(url):
            return True, ""
    # Apps that don't speak web (like a database) still count as awake when their port takes a call
    for host, port in list(ports)[:4]:
        if _port_open(host, port):
            return True, ""
    return (False if urls or ports else None), _health_note(info)


def apps():
    containers = _docker("GET", "/containers/json?all=1")
    # Ask the apps Docker calls "unhealthy" ourselves, all at once so the page doesn't wait long
    opinions = {}
    sick = [c for c in containers or [] if c.get("State") == "running" and "unhealthy" in (c.get("Status") or "")]

    def ask(c):
        opinions[c.get("Id")] = _second_opinion(c)

    threads = [threading.Thread(target=ask, args=(c,), daemon=True) for c in sick]
    for t in threads:
        t.start()
    for t in threads:
        t.join(CHECK_SECONDS * 4)
    out = []
    for c in containers or []:
        name = (c.get("Names") or ["/?"])[0].lstrip("/")
        state = c.get("State") or ""
        status = c.get("Status") or ""
        answers, note = opinions.get(c.get("Id"), (None, ""))
        if answers:
            # It answers the helper, so it's working. Drop Docker's false "unhealthy" from what Benflix shows.
            status = re.sub(r"\s*\((?:unhealthy|health: starting)\)", "", status)
        code = _exit_code(status) if state == "exited" else None
        # 137 can also mean it ran out of memory, which is worth knowing about
        oom = code == 137 and _out_of_memory(c.get("Id"))
        problem = _problem(state, status, code, oom)
        out.append(
            {
                "name": name,
                "nice": _nice_name(name),
                "running": state == "running",
                "ok": state == "running" and "unhealthy" not in status,
                # Needs a look (red). An app stopped on purpose isn't a problem, it just shows as Stopped (grey).
                "problem": problem,
                "state": state,
                "exit_code": code,
                "status": _plain_status(state, status, code, oom) + (f" ({note})" if problem and state == "running" and note else ""),
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
        raise ToolError(400, "The Glowfin helper can't restart itself. Restart it in Unraid's Docker tab.")
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
        raise ToolError(503, f"{nice} isn't connected to the helper yet. Run the Glowfin setup command on the server again.")
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
            raise ToolError(502, f"{nice} didn't accept the helper's key. Run the Glowfin setup command again.")
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
    series, season = None, None
    if app == "sonarr":
        series = (r.get("series") or {}).get("title")
        ep = r.get("episode") or {}
        season = ep.get("seasonNumber") if ep else r.get("seasonNumber")
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
        # A season pack is one download with a row per episode; these let the rows be shown as one
        "download_id": r.get("downloadId"),
        "series": series,
        "season": season,
        "episodes": 1,
        "pack": False,
    }


def _group_packs(rows):
    """Sonarr lists a season pack once per episode. Shows it once: "Show – Season 2 (10 episodes)"."""
    out, groups = [], {}
    for d in rows:
        key = d.get("download_id")
        if not key:
            out.append(d)
            continue
        if key not in groups:
            groups[key] = []
            out.append(key)
        groups[key].append(d)
    result = []
    for item in out:
        if not isinstance(item, str):
            result.append(item)
            continue
        rows = groups[item]
        if len(rows) == 1:
            result.append(rows[0])
            continue
        first = dict(rows[0])
        seasons = sorted({r["season"] for r in rows if r.get("season") is not None})
        show = first.get("series") or "Unknown"
        if len(seasons) == 1:
            first["title"] = f"{show} – Season {seasons[0]} ({len(rows)} episodes)"
        else:
            first["title"] = f"{show} – {len(rows)} episodes"
        stuck = [r for r in rows if r["stuck"]]
        first["stuck"] = bool(stuck)
        first["why"] = stuck[0]["why"] if stuck else None
        first["left"] = None if stuck else next((r["left"] for r in rows if r.get("left")), None)
        first["pct"] = min(r["pct"] for r in rows)
        first["paused"] = all(r["paused"] for r in rows)
        first["queued"] = all(r["queued"] for r in rows)
        first["episodes"] = len(rows)
        first["pack"] = True
        result.append(first)
    return result


def downloads():
    out, problems = [], []
    for app in ("sonarr", "radarr"):
        if not arr_ready(app):
            continue
        extra = "includeSeries=true&includeEpisode=true" if app == "sonarr" else "includeMovie=true"
        try:
            page = _arr(app, "GET", f"/api/v3/queue?page=1&pageSize=100&{extra}") or {}
            rows = [_download(app, r) for r in page.get("records") or []]
            out.extend(_group_packs(rows) if app == "sonarr" else rows)
        except ToolError as e:
            problems.append(e.message)
    out.sort(key=lambda d: (not d["stuck"], d["queued"], -d["pct"]))
    return out, problems


def _pack_size(app, queue_id):
    """How many queue rows (episodes) share this row's download, 1 when it's not a pack"""
    if app != "sonarr":
        return 1
    try:
        page = _arr(app, "GET", "/api/v3/queue?page=1&pageSize=100") or {}
    except ToolError:
        return 1
    records = page.get("records") or []
    mine = next((r for r in records if r.get("id") == int(queue_id)), None)
    if not mine or not mine.get("downloadId"):
        return 1
    return sum(1 for r in records if r.get("downloadId") == mine["downloadId"])


def download_action(app, queue_id, action):
    if app not in ARRS:
        raise ToolError(404, "Not found")
    nice = "Sonarr" if app == "sonarr" else "Radarr"
    if action == "retry":
        _arr(app, "POST", "/api/v3/command", {"name": "RefreshMonitoredDownloads"})
        message = f"{nice} is checking its downloads again."
    elif action == "replace":
        episodes = _pack_size(app, queue_id)
        # One delete removes the whole download (every episode in a season pack goes with it)
        _arr(
            app,
            "DELETE",
            f"/api/v3/queue/{int(queue_id)}?removeFromClient=true&blocklist=true&skipRedownload=false",
        )
        if episodes > 1:
            message = (
                f"Removed the whole pack ({episodes} episodes). {nice} won't pick it again "
                "and is looking for another copy of each episode."
            )
        else:
            message = f"Removed. {nice} won't pick that download again and is looking for another one."
    else:
        raise ToolError(404, "Not found")
    _forget()
    return {"message": message}


def _drop_queue(app, wanted):
    """Takes waiting or running downloads out of Sonarr's / Radarr's list (and out of the download program)"""
    try:
        queue = (_arr(app, "GET", "/api/v3/queue?page=1&pageSize=500&includeUnknownSeriesItems=false&includeUnknownMovieItems=false") or {}).get("records") or []
    except ToolError:
        return 0
    n = 0
    for item in queue:
        if not wanted(item):
            continue
        try:
            _arr(app, "DELETE", f"/api/v3/queue/{item['id']}?removeFromClient=true&blocklist=false")
            n += 1
        except ToolError:
            pass
    return n


def stop_movie(tmdb, delete):
    """Ben pressed Stop on a movie request: Radarr forgets the movie. A copy that already arrived stays unless
    Ben ticked delete."""
    movie = _find_movie(tmdb)
    if not movie:
        return "Stopped. Radarr wasn't looking for it."
    _drop_queue("radarr", lambda q: q.get("movieId") == movie["id"])
    has_file = bool(movie.get("hasFile"))
    if has_file and not delete:
        movie["monitored"] = False
        _arr("radarr", "PUT", f"/api/v3/movie/{movie['id']}", movie)
        _forget()
        return f"Stopped. The copy of {movie['title']} that already arrived stays on Glowfin."
    _arr("radarr", "DELETE", f"/api/v3/movie/{movie['id']}?deleteFiles={'true' if delete else 'false'}&addImportExclusion=false")
    _forget()
    return f"Stopped. Tower no longer looks for {movie['title']}" + (", and the copy that arrived is deleted." if has_file else ".")


def stop_show(info, seasons, delete):
    """Ben pressed Stop on a show request: Sonarr stops looking for the seasons that were asked for. Episodes
    that already arrived stay unless Ben ticked delete. Seasons from other requests are left alone."""
    series = _find_series(info)
    if not series:
        return "Stopped. Sonarr wasn't looking for it."
    all_seasons = [s.get("seasonNumber") for s in series.get("seasons") or []]
    stop = set(seasons) if seasons else set(all_seasons)
    _drop_queue("sonarr", lambda q: q.get("seriesId") == series["id"] and (q.get("seasonNumber") in stop))
    for s in series.get("seasons") or []:
        if s.get("seasonNumber") in stop:
            s["monitored"] = False
    deleted = 0
    if delete:
        files = _arr("sonarr", "GET", f"/api/v3/episodefile?seriesId={series['id']}") or []
        ids = [f["id"] for f in files if f.get("seasonNumber") in stop]
        if ids:
            _arr("sonarr", "DELETE", "/api/v3/episodefile/bulk", {"episodeFileIds": ids})
            deleted = len(ids)
    left = [s for s in series.get("seasons") or [] if s.get("monitored")]
    files_left = (series.get("statistics") or {}).get("episodeFileCount", 0) - deleted
    if not left and files_left <= 0:
        _arr("sonarr", "DELETE", f"/api/v3/series/{series['id']}?deleteFiles=false&addImportListExclusion=false")
        _forget()
        return f"Stopped. Tower no longer looks for {series['title']}" + (f", and {deleted} episode{'s' if deleted != 1 else ''} that arrived {'are' if deleted != 1 else 'is'} deleted." if deleted else ".")
    if not left:
        series["monitored"] = False
    _arr("sonarr", "PUT", f"/api/v3/series/{series['id']}", series)
    _forget()
    if deleted:
        return f"Stopped. {deleted} episode{'s' if deleted != 1 else ''} of {series['title']} that arrived {'are' if deleted != 1 else 'is'} deleted."
    return f"Stopped. Episodes of {series['title']} that already arrived stay on Glowfin."


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
# "What's the hold-up?" on a request page (Ben, 2026-11-21): why something that was asked for and
# approved hasn't arrived yet, in plain words, straight from Radarr or Sonarr.
# ---------------------------------------------------------------------------


def _find_movie(tmdb):
    tmdb = str(tmdb or "")
    if not tmdb.isdigit():
        raise ToolError(400, "That isn't a movie number.")
    found = _arr("radarr", "GET", f"/api/v3/movie?tmdbId={tmdb}") or []
    return found[0] if found else None


def _nice_day(text):
    """"2026-10-28T00:00:00Z" -> "October 28" (with the year when it isn't this year)"""
    try:
        day = time.strptime((text or "")[:10], "%Y-%m-%d")
    except ValueError:
        return None
    words = time.strftime("%B", day) + " " + str(day.tm_mday)
    return words if day.tm_year == time.gmtime().tm_year else f"{words}, {day.tm_year}"


def _queue_words(app, rows):
    """One hold-up answer from the downloads Radarr / Sonarr have going for this title, or None"""
    if not rows:
        return None
    stuck = [d for d in rows if d["stuck"]]
    if stuck:
        d = stuck[0]
        return {
            "state": "stuck",
            "line": "The download is stuck." if len(rows) == 1 else f"{len(stuck)} of {len(rows)} downloads are stuck.",
            "detail": d["why"] or "It stopped moving.",
            "replace": {"app": app, "id": d["id"]},
            "search": False,
        }
    going = [d for d in rows if not d["paused"] and not d["queued"]]
    if not going:
        return {
            "state": "waiting",
            "line": "It's found, and waiting its turn to download.",
            "detail": "Other downloads are ahead of it. Nothing is wrong.",
            "search": False,
        }
    d = max(going, key=lambda x: x["pct"])
    left = f", {d['left']} left" if d.get("left") else ""
    many = "" if len(rows) == 1 else f" ({len(rows)} downloads going)"
    return {
        "state": "downloading",
        "line": f"Downloading now: {d['pct']}% done{left}{many}.",
        "detail": "Nothing is wrong. It shows up on Glowfin when it finishes.",
        "search": False,
    }


def _movie_holdup(tmdb):
    movie = _find_movie(tmdb)
    if not movie:
        return {
            "state": "notsent",
            "line": "Radarr doesn't have this movie yet.",
            "detail": "The ask hasn't been handed over to the downloader. Glowfin tries again by itself every few hours.",
            "search": False,
        }
    if movie.get("hasFile"):
        return {
            "state": "here",
            "line": "It's downloaded.",
            "detail": "It shows up on Glowfin after the next library scan, usually within a few minutes.",
            "search": False,
        }
    queue = _arr("radarr", "GET", f"/api/v3/queue/details?movieId={movie['id']}&includeMovie=true") or []
    words = _queue_words("radarr", [_download("radarr", r) for r in queue])
    if words:
        return words
    if not movie.get("monitored", True):
        return {
            "state": "off",
            "line": "Radarr has stopped looking for it.",
            "detail": "It was switched off in Radarr (not monitored). Look again now turns nothing on; ask for it again instead.",
            "search": False,
        }
    if not movie.get("isAvailable", True):
        home = _nice_day(movie.get("digitalRelease") or movie.get("physicalRelease"))
        cinema = _nice_day(movie.get("inCinemas"))
        when = f"It comes out at home on {home}." if home else "No home release day is known yet."
        return {
            "state": "notout",
            "line": "It isn't out at home yet.",
            "detail": (f"In cinemas since {cinema}. " if cinema and movie.get("status") != "announced" else "")
            + when
            + " Radarr waits for a proper copy and grabs it by itself.",
            "search": True,
        }
    history = _arr("radarr", "GET", f"/api/v3/history/movie?movieId={movie['id']}") or []
    failed = sum(1 for h in history if (h.get("eventType") or "").lower() == "downloadfailed")
    looked = _nice_day(movie.get("lastSearchTime"))
    detail = "It's out, but Radarr hasn't found a copy that passes your quality rules."
    if failed:
        detail = f"{failed} download{'s' if failed != 1 else ''} failed and {'were' if failed != 1 else 'was'} thrown away. " + detail
    if looked:
        detail += f" It last looked on {looked}, and checks for new copies by itself all day."
    return {"state": "nothing", "line": "No good copy has turned up yet.", "detail": detail, "search": True, "copies": True}


def _show_holdup(tmdb):
    series = _find_series({"tmdb": tmdb})
    if not series:
        return {
            "state": "notsent",
            "line": "Sonarr doesn't have this show yet.",
            "detail": "The ask hasn't been handed over to the downloader. Glowfin tries again by itself every few hours.",
            "search": False,
        }
    queue = _arr("sonarr", "GET", f"/api/v3/queue/details?seriesId={series['id']}&includeSeries=true&includeEpisode=true") or []
    rows = _group_packs([_download("sonarr", r) for r in queue])
    episodes = _arr("sonarr", "GET", f"/api/v3/episode?seriesId={series['id']}") or []
    now = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
    aired = [e for e in episodes if e.get("seasonNumber", 0) > 0 and e.get("monitored") and e.get("airDateUtc") and e["airDateUtc"] < now]
    missing = [e for e in aired if not e.get("hasFile")]
    have = len(aired) - len(missing)
    count = f"{have} of {len(aired)} aired episodes are here."
    words = _queue_words("sonarr", rows)
    if words:
        words["detail"] = count + " " + words["detail"]
        return words
    if not aired:
        return {
            "state": "notout",
            "line": "Nothing you asked for has aired yet.",
            "detail": "Sonarr grabs each episode by itself once it airs.",
            "search": False,
        }
    if not missing:
        return {
            "state": "here",
            "line": "Every aired episode is downloaded.",
            "detail": "They show up on Glowfin after the next library scan, usually within a few minutes.",
            "search": False,
        }
    return {
        "state": "nothing",
        "line": f"{len(missing)} aired episode{'s are' if len(missing) != 1 else ' is'} still missing.",
        "detail": count + " Sonarr hasn't found copies that pass your quality rules, and keeps checking by itself all day.",
        "search": True,
    }


def holdup(kind, tmdb):
    app = "radarr" if kind == "movie" else "sonarr"
    if not arr_ready(app):
        raise ToolError(503, f"{'Radarr' if app == 'radarr' else 'Sonarr'} isn't connected to the helper yet.")
    return _movie_holdup(tmdb) if kind == "movie" else _show_holdup(tmdb)


def holdup_search(kind, tmdb):
    """Look again now: asks Radarr / Sonarr to search for it this minute"""
    if kind == "movie":
        movie = _find_movie(tmdb)
        if not movie:
            raise ToolError(404, "Radarr doesn't have this movie yet.")
        _arr("radarr", "POST", "/api/v3/command", {"name": "MoviesSearch", "movieIds": [movie["id"]]})
        _forget()
        return {"message": f"Radarr is looking for {movie['title']} right now. If it finds a good copy, the download starts by itself."}
    return search_missing({"tmdb": tmdb})


_PLAIN_REJECTS = (
    ("cam", "filmed in a cinema"),
    ("telesync", "filmed in a cinema"),
    ("not wanted in profile", "a quality you don't allow"),
    ("quality profile", "a quality you don't allow"),
    ("custom format", "blocked by your quality rules"),
    ("blocklist", "already tried and thrown away"),
    ("seeder", "too few people sharing it"),
    ("size", "the wrong size (too big or too small)"),
    ("language", "the wrong language"),
    ("unknown movie", "not clearly this movie"),
    ("release date", "from before the movie was out"),
)


def holdup_copies(tmdb):
    """What's out there right now for a movie: how many copies, and why none of them were taken. Slow (up to a minute)."""
    movie = _find_movie(tmdb)
    if not movie:
        raise ToolError(404, "Radarr doesn't have this movie yet.")
    found = _arr("radarr", "GET", f"/api/v3/release?movieId={movie['id']}", timeout=90) or []
    if not found:
        return {"line": "No copies are out there at all right now.", "detail": "That usually means it isn't out at home yet."}
    good = [r for r in found if not r.get("rejected")]
    if good:
        return {
            "line": f"Found {len(found)} cop{'ies' if len(found) != 1 else 'y'}, and {len(good)} look{'s' if len(good) == 1 else ''} good.",
            "detail": "Press Look again now and Radarr takes the best one.",
        }
    reasons = {}
    for r in found:
        text = " ".join(r.get("rejections") or []).lower()
        plain = next((words for key, words in _PLAIN_REJECTS if key in text), None) or "not good enough for your quality rules"
        reasons[plain] = reasons.get(plain, 0) + 1
    top = sorted(reasons.items(), key=lambda kv: -kv[1])[:3]
    return {
        "line": f"Found {len(found)} cop{'ies' if len(found) != 1 else 'y'}, but none are good enough yet.",
        "detail": "Why: " + ", ".join(f"{n} {words}" for words, n in top) + ".",
    }


# ---------------------------------------------------------------------------
# Everything on the Server page in one answer (saved for 20 seconds)
# ---------------------------------------------------------------------------


def harbor():
    """Harbor on Tower: is it there, and since when has its current build been running?
    Tower's hourly self-update (benflix-autosetup.sh) puts a newer Harbor in by itself."""
    try:
        info = _docker("GET", "/containers/harbor/json", timeout=10) or {}
    except ToolError:
        return {"installed": False}
    labels = (info.get("Config") or {}).get("Labels") or {}
    return {
        "installed": True,
        "running": bool((info.get("State") or {}).get("Running")),
        "mode": labels.get("harbor.mode") or "",
        "since": info.get("Created") or "",
    }


def _forget():
    with _lock:
        _cache["at"] = 0.0


def _keeper_notes():
    """Lines for "Needs you" from the safety copy and the away check (benflix_keeper.py), only when one has a problem."""
    try:
        import benflix_keeper

        return benflix_keeper.notes()
    except Exception:  # noqa: BLE001 - an older Tower without the keeper
        return []


def tower(fresh=False):
    with _lock:
        if not fresh and _cache["data"] is not None and time.time() - _cache["at"] < CACHE_SECONDS:
            return _cache["data"]
    result = {"up_seconds": up_seconds(), "memory": memory_busy(), "gpu": gpu_busy(), "disks": disks()}
    try:  # Drives with warning signs show on the Server page's first screen
        result["drives_bad"] = [d for d in drives().get("drives", []) if not d["ok"]]
    except ToolError:
        result["drives_bad"] = []
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
            "notes": _keeper_notes(),
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
        state["problem"] = "The track tool isn't set up on the server yet. Run the Glowfin setup command on the server again."
    return state


def tracks_start(mode, folder=None, undo_file=None):
    if not tracks_ready():
        raise ToolError(503, "The track tool isn't set up on the server yet. Run the Glowfin setup command again.")
    # Ben's qBittorrent stops seeding after a minute, so files that still have a second copy in Downloads
    # are safe to edit (otherwise most new episodes would be skipped)
    cmd = [sys.executable, "-u", TRACKS_SCRIPT, "--log-dir", TRACK_LOGS, "--include-hardlinked"]
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
    try:
        os.makedirs(TRACK_LOGS, exist_ok=True)
        threading.Thread(target=_run_tracks, args=(cmd,), daemon=True).start()
    except Exception as e:  # so the tool doesn't stay "running" until the helper restarts
        with _lock:
            _tracks["lines"].append(f"Couldn't start the track tool: {e}")
            _tracks.update(running=False, finished=int(time.time()), result="failed")
        raise ToolError(500, f"Couldn't start the track tool: {e}")
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


# ---------------------------------------------------------------------------
# Every new season from now on (Glowfin new look, Ben approved 2026-11-22). The season picker on Requests can
# ask for a show's seasons AND every season that comes out later. Tower remembers the show, and once Sonarr has
# it (after Ben's yes) it tells Sonarr to watch for new seasons too (Sonarr's "monitor new seasons: all"), so a
# new season is looked for as soon as it airs. Saved in /tracks/follow-shows.json.

FOLLOW_EVERY = 1800  # seconds between checks
_follow = {"started": False}


def _follow_file():
    for d in (TRACK_LOGS, "/skips", os.path.dirname(os.path.abspath(__file__))):
        if os.path.isdir(d) and os.access(d, os.W_OK):
            return os.path.join(d, "follow-shows.json")
    return None


def _follow_read():
    path = _follow_file()
    try:
        with open(path) as f:
            data = json.load(f)
        return data if isinstance(data, list) else []
    except (OSError, ValueError, TypeError):
        return []


def _follow_write(items):
    path = _follow_file()
    if not path:
        return
    tmp = path + ".tmp"
    with open(tmp, "w") as f:
        json.dump(items, f)
    os.replace(tmp, path)


def _follow_apply(item):
    """True once Sonarr watches the show for new seasons"""
    series = _find_series({"tmdb": item.get("tmdb"), "name": item.get("name")})
    if not series:
        return False
    if series.get("monitorNewItems") == "all" and series.get("monitored"):
        return True
    series["monitorNewItems"] = "all"
    series["monitored"] = True
    _arr("sonarr", "PUT", f"/api/v3/series/{series['id']}", series)
    _forget()
    return True


def _follow_check():
    with _lock:
        items = _follow_read()
    changed = False
    for it in items:
        if it.get("done"):
            continue
        try:
            if _follow_apply(it):
                it["done"] = int(time.time())
                changed = True
        except ToolError:
            return  # Sonarr isn't reachable right now; try again next time
        except Exception:  # noqa: BLE001 - one odd show never stops the rest
            continue
    if changed:
        with _lock:
            fresh = {str(x.get("tmdb")): x for x in _follow_read()}
            for it in items:
                if it.get("done") and str(it.get("tmdb")) in fresh:
                    fresh[str(it.get("tmdb"))]["done"] = it["done"]
            _follow_write(list(fresh.values()))


def _follow_loop():
    while True:
        try:
            _follow_check()
        except Exception as e:  # noqa: BLE001 - keep looking
            print(f"follow shows: {e}", flush=True)
        time.sleep(FOLLOW_EVERY)


def follow_show(body, who=""):
    """Remembers a show for "Every new season from now on" and tells Sonarr straight away if it already has it"""
    tmdb = str((body or {}).get("tmdb") or "")
    if not tmdb.isdigit():
        raise ToolError(400, "Which show?")
    name = str((body or {}).get("name") or "")[:200]
    with _lock:
        items = [x for x in _follow_read() if str(x.get("tmdb")) != tmdb]
        items.append({"tmdb": int(tmdb), "name": name, "who": who, "at": int(time.time())})
        _follow_write(items[-500:])
    if not _follow["started"]:
        _follow["started"] = True
        threading.Thread(target=_follow_loop, name="follow-shows", daemon=True).start()
    try:
        now = _follow_apply({"tmdb": tmdb, "name": name})
    except ToolError:
        now = False
    return {
        "ok": True,
        "message": "Every new season will be asked for too."
        if now
        else "Every new season will be asked for too, once Ben says yes.",
    }


def start_follow():
    """Called when the helper starts, so shows asked for earlier keep being checked"""
    if not _follow["started"] and _follow_read():
        _follow["started"] = True
        threading.Thread(target=_follow_loop, name="follow-shows", daemon=True).start()
