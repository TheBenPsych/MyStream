"""Glowfin's keeper (Ben's picks, 2026-11-21): four small Server page cards that all speak the same shape.

  jobs    One list of everything Tower's helper does by itself: when it last ran, if it worked, Run now.
  backup  A nightly safety copy of the small things that would be painful to lose (not the movies).
  away    A nightly check that the family can still reach Glowfin from outside the house.
  try     "Try these on a real screen": new things only the test robot has checked, with Works / Not right.

Every card answers GET /api/admin/card/<name> with
  {"title", "intro", "level": ok|warn|bad|off, "headline", "reason",
   "acts": [{"label", "do", "ask"?}], "rows": [{"light", "t", "s", "acts": [...]}]}
and takes POST /api/admin/card/<name>/do {"do": "<id>"} -> {"message"}. The browser, TV and phone each draw
that shape once, so a new card needs no new screen.
"""

import datetime
import json
import os
import shutil
import socket
import sqlite3
import ssl
import tarfile
import threading
import time
import urllib.parse
import urllib.request

STATE_DIRS = ("/saves/.benflix", "/skips/keeper", "/tmp/benflix-keeper")
APPDATA = "/appdata"  # Tower's appdata folder, read-only (the setup command mounts it)
BACKUPS = "/backups"  # where safety copies go (the setup command mounts it)
TRY_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "web", "try-list.json")
KEEP_NIGHTS = 7
KEEP_SUNDAYS = 4
BIG = 300 * 1024 * 1024  # one file bigger than this is left out of the safety copy (databases are always kept)
CERT_WARN_DAYS = 14

_lock = threading.Lock()
_dir = None
_jobs = {}  # id -> {"title", "when", "run"}
_order = []
_running = set()
_addresses = None
_docker = None


def log(text):
    print(f"Keeper: {text}", flush=True)


class CardError(Exception):
    def __init__(self, status, message):
        super().__init__(message)
        self.status = status
        self.message = message


# --------------------------------------------------------------------------- small saved notes


def _pick_dir():
    for d in STATE_DIRS:
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


def _read(name, default):
    try:
        with open(os.path.join(_dir or "", name)) as f:
            return json.load(f)
    except (OSError, ValueError):
        return default


def _write(name, data):
    if not _dir:
        return
    tmp = os.path.join(_dir, name + ".tmp")
    try:
        with open(tmp, "w") as f:
            json.dump(data, f)
        os.replace(tmp, os.path.join(_dir, name))
    except OSError:
        pass


def _ago(at):
    if not at:
        return ""
    mins = max(0, int((time.time() - at) / 60))
    if mins < 1:
        return "just now"
    if mins < 60:
        return f"{mins} minute{'' if mins == 1 else 's'} ago"
    if mins < 1440:
        h = mins // 60
        return f"{h} hour{'' if h == 1 else 's'} ago"
    d = mins // 1440
    return "yesterday" if d == 1 else f"{d} days ago"


def _size(n):
    if n >= 1e9:
        return f"{n / 1e9:.1f} GB"
    if n >= 1e6:
        return f"{n / 1e6:.0f} MB"
    return f"{max(1, int(n / 1e3))} KB"


def _plain(e):
    return str(getattr(e, "message", None) or e)[:160] or "It stopped without saying why"


# --------------------------------------------------------------------------- jobs


def job(job_id, title, when, module=None, name=None, run=None):
    """Lists one of Tower's own jobs. With module + name, that function is wrapped so every run is noted
    (when, how long, did it work) without changing the module. run, when given, is what Run now calls."""
    if module is not None and name and hasattr(module, name):
        inner = getattr(module, name)

        def noted(*args, **kwargs):
            started = time.time()
            try:
                out = inner(*args, **kwargs)
            except Exception as e:
                ran(job_id, False, _plain(e), started)
                raise
            ran(job_id, True, "", started)
            return out

        noted.__name__ = getattr(inner, "__name__", name)
        noted.__doc__ = inner.__doc__
        setattr(module, name, noted)
    elif module is not None and name:
        return
    with _lock:
        if job_id not in _jobs:
            _order.append(job_id)
        _jobs[job_id] = {"title": title, "when": when, "run": run}


def ran(job_id, ok=True, note="", started=None):
    """Notes that a job just finished."""
    with _lock:
        all_runs = _read("jobs.json", {})
        all_runs[job_id] = {"at": int(time.time()), "ok": bool(ok), "note": note, "secs": int(time.time() - started) if started else 0}
        _write("jobs.json", all_runs)


def _self_update_line():
    """The hourly self-update writes its own notes on Tower (autosetup.log beside the helper's files)."""
    try:
        with open("/app/autosetup.log") as f:
            lines = [ln.strip() for ln in f.readlines()[-40:] if ln.strip()]
    except OSError:
        return None
    if not lines:
        return None
    last = lines[-1]
    return {"bad": "PROBLEM" in last, "text": last}


def _jobs_card():
    runs = _read("jobs.json", {})
    rows = []
    bad = 0
    up = _self_update_line()
    rows.append(
        {
            "light": "warn" if up and up["bad"] else "ok",
            "t": "Update Glowfin and Harbor on Tower",
            "s": "Every hour, waiting while anyone is watching" + (f" · last note: {up['text']}" if up else " · nothing to do so far"),
        }
    )
    bad += 1 if up and up["bad"] else 0
    with _lock:
        listed = [(i, dict(_jobs[i])) for i in _order]
    for job_id, j in listed:
        r = runs.get(job_id)
        busy = job_id in _running
        if busy:
            light, last = "ok", "running now"
        elif not r:
            light, last = "off", "hasn't run since Tower's helper last started"
        elif r.get("ok"):
            light, last = "ok", f"last ran {_ago(r.get('at'))}, it worked"
        else:
            light, last = "warn", f"last ran {_ago(r.get('at'))}, it didn't finish: {r.get('note') or 'no reason given'}"
            bad += 1
        row = {"light": light, "t": j["title"], "s": f"{j['when']} · {last}"}
        if j["run"] and not busy:
            row["acts"] = [{"label": "Run now", "do": "run:" + job_id}]
        rows.append(row)
    return {
        "title": "Tower's own jobs",
        "intro": "Everything Tower's helper does by itself. Jellyfin's chores are listed underneath. Harbor's jobs join this list when Harbor is linked to Glowfin.",
        "level": "warn" if bad else "ok",
        "headline": f"{bad} job{'' if bad == 1 else 's'} didn't finish last time" if bad else "Every job ran fine",
        "reason": "",
        "rows": rows,
    }


def _run_job(job_id):
    with _lock:
        j = _jobs.get(job_id)
        if not j or not j["run"]:
            raise CardError(404, "That job can't be started from here.")
        if job_id in _running:
            return "It's already running."
        _running.add(job_id)

    def work():
        started = time.time()
        try:
            j["run"]()
            # (a wrapped job notes itself; this covers the ones that are not wrapped)
            if (_read("jobs.json", {}).get(job_id) or {}).get("at", 0) < int(started):
                ran(job_id, True, "", started)
        except Exception as e:  # noqa: BLE001 - whatever went wrong is shown on the list
            ran(job_id, False, _plain(e), started)
        finally:
            with _lock:
                _running.discard(job_id)

    threading.Thread(target=work, name="keeper-run-" + job_id, daemon=True).start()
    return f"Started: {j['title']}. The list shows how it went."


# --------------------------------------------------------------------------- safety copy


def _appdata_of(container, inside="/config"):
    """Where a container keeps its settings, as this helper can see it (under /appdata), or None."""
    if not _docker or not os.path.isdir(APPDATA):
        return None
    try:
        info = _docker("GET", f"/containers/{urllib.parse.quote(container)}/json", timeout=10) or {}
    except Exception:  # noqa: BLE001 - not installed
        return None
    for m in info.get("Mounts") or []:
        if m.get("Destination") == inside:
            src = m.get("Source") or ""
            for root in ("/mnt/user/appdata/", "/mnt/cache/appdata/"):
                if src.startswith(root):
                    p = os.path.join(APPDATA, src[len(root):])
                    return p if os.path.isdir(p) else None
            parts = src.split("/appdata/", 1)  # another pool, like /mnt/fast/appdata/harbor
            if len(parts) == 2:
                p = os.path.join(APPDATA, parts[1])
                return p if os.path.isdir(p) else None
    return None


def _find_container(*words):
    if not _docker:
        return None
    try:
        for c in _docker("GET", "/containers/json?all=1", timeout=10) or []:
            name = ((c.get("Names") or ["/"])[0]).lstrip("/")
            if any(w in name.lower() for w in words):
                return name
    except Exception:  # noqa: BLE001
        pass
    return None


def _copy_db(src, dest):
    """A database is copied through SQLite itself so it isn't caught half-written; a plain copy if that can't be done."""
    try:
        a = sqlite3.connect(f"file:{urllib.parse.quote(src)}?mode=ro", uri=True, timeout=20)
        try:
            b = sqlite3.connect(dest)
            try:
                a.backup(b)
            finally:
                b.close()
        finally:
            a.close()
        return
    except sqlite3.Error:
        pass
    for extra in ("", "-wal", "-shm"):
        if os.path.exists(src + extra):
            shutil.copy2(src + extra, dest + extra)


SKIP_DIRS = {"cache", "transcodes", "transcoding-temp", "metadata", "log", "logs", "trickplay", "subtitles", "web", ".last-good", ".rejected", ".new", "backups", "mediacover", "__pycache__"}


def _gather(src, dest, only=None):
    """Copies the small things from src into dest. Returns bytes copied."""
    total = 0
    for root, dirs, files in os.walk(src):
        rel = os.path.relpath(root, src)
        dirs[:] = [d for d in dirs if d.lower() not in SKIP_DIRS]
        if only and rel == ".":
            dirs[:] = [d for d in dirs if d in only]
            files = [f for f in files if f in only]
        for f in files:
            p = os.path.join(root, f)
            try:
                if os.path.islink(p) or not os.path.isfile(p):
                    continue
                is_db = f.endswith((".db", ".sqlite", ".sqlite3"))
                if f.endswith(("-wal", "-shm")) or (not is_db and os.path.getsize(p) > BIG):
                    continue
                out = os.path.join(dest, rel, f)
                os.makedirs(os.path.dirname(out), exist_ok=True)
                if is_db:
                    _copy_db(p, out)
                else:
                    shutil.copy2(p, out)
                total += os.path.getsize(out)
            except (OSError, sqlite3.Error):
                continue
    return total


def _parts():
    """What goes in the safety copy: (name on the page, folder name in the copy, source folder, only these)."""
    out = []
    jf = _find_container("jellyfin")
    jf_dir = _appdata_of(jf) if jf else None
    if jf_dir:
        out.append(("Everyone's profiles, watch history and My List", "jellyfin", jf_dir, {"data", "config", "root", "plugins"}))
    for name, folder, src in (
        ("Glowfin's own saved things", "glowfin-saves", "/saves"),
        ("Skip times, play test notes and Tower's addresses", "glowfin-skips", "/skips"),
        ("Music mixes, pins and playlists", "glowfin-music", "/musicdata"),
        ("Book notes, highlights and places", "glowfin-books", "/booksdata"),
        ("Tower's helper and its keys", "glowfin-helper", "/app"),
    ):
        if os.path.isdir(src):
            out.append((name, folder, src, None))
    hb = _appdata_of("harbor")
    if hb:
        out.append(("Harbor's rules, asks and history", "harbor", hb, None))
    return out


def backup_now(why="night"):
    """Makes one safety copy. Returns a short line about how it went."""
    started = time.time()
    if not os.path.isdir(BACKUPS) or not os.access(BACKUPS, os.W_OK):
        raise CardError(503, "Tower hasn't made the safety copy folder yet. It appears by itself within the hour.")
    day = time.strftime("%Y-%m-%d")
    work = os.path.join(BACKUPS, ".working")
    shutil.rmtree(work, ignore_errors=True)
    os.makedirs(work, exist_ok=True)
    done = []
    try:
        for name, folder, src, only in _parts():
            size = _gather(src, os.path.join(work, folder), only)
            done.append({"name": name, "bytes": size})
        target = os.path.join(BACKUPS, f"glowfin-{day}.tar.gz")
        with tarfile.open(target + ".tmp", "w:gz", compresslevel=3) as tar:
            tar.add(work, arcname=f"glowfin-{day}")
        os.replace(target + ".tmp", target)
        total = os.path.getsize(target)
    finally:
        shutil.rmtree(work, ignore_errors=True)
    _trim()
    _write("backup.json", {"at": int(time.time()), "ok": True, "bytes": total, "parts": done, "file": os.path.basename(target), "why": why})
    log(f"safety copy made ({_size(total)}, {int(time.time() - started)} s)")
    return f"Safety copy made: {_size(total)}."


def _copies():
    try:
        return sorted(f for f in os.listdir(BACKUPS) if f.startswith("glowfin-") and f.endswith(".tar.gz"))
    except OSError:
        return []


def _trim():
    """Keeps the last 7 nights and the last 4 Sundays."""
    files = _copies()
    keep = set(files[-KEEP_NIGHTS:])
    sundays = []
    for f in files:
        try:
            if datetime.date.fromisoformat(f[len("glowfin-"):-len(".tar.gz")]).weekday() == 6:
                sundays.append(f)
        except ValueError:
            keep.add(f)
    keep.update(sundays[-KEEP_SUNDAYS:])
    for f in files:
        if f not in keep:
            try:
                os.remove(os.path.join(BACKUPS, f))
            except OSError:
                pass


def _backup_card():
    last = _read("backup.json", {})
    ready = os.path.isdir(BACKUPS) and os.access(BACKUPS, os.W_OK)
    rows = []
    if not ready:
        level, headline = "off", "The safety copy isn't set up yet"
        reason = "Tower sets up the folder by itself with its next hourly update. Nothing for you to do."
    elif not last.get("at"):
        level, headline = "warn", "No safety copy has been made yet"
        reason = "The first one is made tonight between 5 and 6 AM. Or make one now."
    else:
        old = time.time() - last["at"] > 36 * 3600
        ok = last.get("ok") and not old
        level = "ok" if ok else "warn"
        headline = f"Last safety copy: {_ago(last['at'])}" if last.get("ok") else "Last night's safety copy didn't finish"
        reason = (
            f"{_size(last.get('bytes') or 0)}, in the Backups folder next to your Media folder. Tower keeps the last {KEEP_NIGHTS} nights and {KEEP_SUNDAYS} Sundays ({len(_copies())} copies now)."
            if last.get("ok")
            else (last.get("note") or "It stopped without saying why.")
        )
        if old and last.get("ok"):
            reason = "That is more than a day and a half ago. " + reason
        for p in last.get("parts") or []:
            rows.append({"light": "ok" if p.get("bytes") else "off", "t": p.get("name"), "s": _size(p["bytes"]) if p.get("bytes") else "Nothing found to copy"})
    rows.append({"light": "off", "t": "Not in the copy", "s": "Movies, shows, music, books and games themselves (far too big), and the game library's database."})
    rows.append(
        {
            "light": "off",
            "t": "How to put a copy back",
            "s": "Ask Claude. Each copy is one file named glowfin-(date).tar.gz in the Backups folder; Claude gives you the exact steps for what was lost.",
        }
    )
    return {
        "title": "Safety copy",
        "intro": "A small copy of everything that would be painful to lose, made every night. Not the movies themselves.",
        "level": level,
        "headline": headline,
        "reason": reason,
        "acts": [{"label": "Make one now", "do": "now"}] if ready else [],
        "rows": rows,
    }


def _backup_job():
    try:
        return backup_now("night")
    except Exception as e:
        _write("backup.json", dict(_read("backup.json", {}), ok=False, note=_plain(e), tried=int(time.time())))
        raise


# --------------------------------------------------------------------------- away check


def _cert_days(host, connect_to=None):
    """Days until the lock (https certificate) for host runs out."""
    ctx = ssl.create_default_context()
    with socket.create_connection((connect_to or host, 443), timeout=8) as raw:
        with ctx.wrap_socket(raw, server_hostname=host) as s:
            cert = s.getpeercert()
    return int((ssl.cert_time_to_seconds(cert["notAfter"]) - time.time()) / 86400)


def _public_ip():
    for url in ("https://api.ipify.org", "https://checkip.amazonaws.com"):
        try:
            ip = urllib.request.urlopen(url, timeout=8).read().decode().strip()
            socket.inet_aton(ip)
            return ip
        except (OSError, ValueError):
            continue
    return None


def away_check():
    found = _addresses() if _addresses else {}
    addrs = found.get("addresses") or []
    names = [a["url"] for a in addrs if a.get("kind") == "internet"]
    home = next((urllib.parse.urlparse(a["url"]).hostname for a in addrs if a.get("kind") == "home"), None)
    out = {"at": int(time.time()), "rows": [], "level": "ok"}

    def row(light, t, s):
        out["rows"].append({"light": light, "t": t, "s": s})
        order = ["ok", "off", "warn", "bad"]
        if light in ("warn", "bad") and order.index(light) > order.index(out["level"]):
            out["level"] = light

    if not names:
        out["level"] = "off"
        out["headline"] = "Tower doesn't know its internet name yet"
        row("off", "No internet name seen", "It learns the name the first time someone opens Glowfin from outside the house.")
        _write("away.json", out)
        return out
    url = names[0]
    host = urllib.parse.urlparse(url).hostname
    out["host"] = host
    # 1. the name points at this house
    mine = _public_ip()
    try:
        points = socket.gethostbyname(host)
    except OSError:
        points = None
    if not points and not mine:
        row("off", "Couldn't look anything up", "Tower had no internet for a moment. It checks again tonight.")
    elif not points:
        row("bad", "Your internet name doesn't lead anywhere", f"{host} could not be looked up. Check DuckDNS.")
    elif not mine:
        row("off", "Your internet name", f"{host} leads to {points}. Tower couldn't look up your home's own address to compare.")
    elif points == mine:
        row("ok", "Your internet name points at your house", host)
    else:
        row("bad", "Your internet name points somewhere else", f"{host} leads to {points}, but your house is at {mine}. DuckDNS needs updating.")
    # 2. the lock
    days = None
    if url.startswith("https://"):
        for target in (None, home):
            try:
                days = _cert_days(host, target)
                break
            except ssl.SSLCertVerificationError as e:
                days = -1 if "expired" in str(e).lower() else None
                if days == -1:
                    break
            except (OSError, ValueError, KeyError):
                continue
        if days is None:
            row("warn", "The lock (https certificate) couldn't be read", "Tower couldn't reach its own front door on port 443 to look at it.")
        elif days < 0:
            row("bad", "The lock (https certificate) has run out", "Phones and TVs outside the house will refuse to connect. In NGINX Proxy Manager: SSL Certificates, then Renew.")
        elif days <= CERT_WARN_DAYS:
            row("warn", f"The lock (https certificate) runs out in {days} day{'' if days == 1 else 's'}", "It normally renews itself around 30 days before. It hasn't, so open NGINX Proxy Manager: SSL Certificates, then Renew.")
        else:
            row("ok", "The lock (https certificate)", f"Good for {days} more days. It renews by itself.")
    out["days"] = days
    # 3. does Glowfin answer at that name
    try:
        req = urllib.request.Request(url.rstrip("/") + "/System/Info/Public", headers={"User-Agent": "Glowfin-away-check"})
        with urllib.request.urlopen(req, timeout=10) as r:
            answered = r.status < 500
        row("ok" if answered else "warn", "Jellyfin answers at your internet name", "Asked from inside your house, through the front door.")
    except Exception:  # noqa: BLE001 - many home routers won't loop back; that is not a fault
        row("off", "Couldn't ask through the front door from inside", "Many home routers don't allow that. It doesn't mean anything is wrong outside.")
    # 4. the back-up way in
    if any(a.get("kind") == "tailscale" for a in addrs):
        row("ok", "Tailscale (the back-up way in)", "On")
    words = {"ok": "The family can reach Glowfin from outside", "warn": "Works, but something needs a look", "bad": "The family may not be able to reach Glowfin from outside", "off": "Checked what could be checked"}
    out["headline"] = next((r["t"] for r in out["rows"] if r["light"] == out["level"] and out["level"] in ("warn", "bad")), words[out["level"]])
    _write("away.json", out)
    return out


def _away_card():
    last = _read("away.json", {})
    if not last.get("at"):
        return {
            "title": "Away check",
            "intro": "Can the family reach Glowfin when they are not at your house? Tower checks every night.",
            "level": "off",
            "headline": "Not checked yet",
            "reason": "Tower checks tonight. Or check now.",
            "acts": [{"label": "Check now", "do": "now"}],
            "rows": [],
        }
    return {
        "title": "Away check",
        "intro": "Can the family reach Glowfin when they are not at your house? Tower checks every night.",
        "level": last.get("level") or "off",
        "headline": last.get("headline") or "",
        "reason": f"Checked {_ago(last['at'])}. Tower checks from inside your house, so it can't prove the router's open doors; everything else it can.",
        "acts": [{"label": "Check again", "do": "now"}],
        "rows": last.get("rows") or [],
    }


def notes():
    """Lines for the Server page's "Needs you" card: only when the safety copy or the away check has a problem."""
    out = []
    away = _read("away.json", {})
    if away.get("level") in ("warn", "bad"):
        out.append({"light": away["level"], "t": away.get("headline") or "The away check found a problem", "s": "Server > Tools > Tower > Away check", "go": "away"})
    b = _read("backup.json", {})
    if b.get("at") and (not b.get("ok") or time.time() - b["at"] > 60 * 3600):
        out.append({"light": "warn", "t": "The nightly safety copy hasn't worked lately", "s": "Server > Tools > Tower > Safety copy", "go": "backup"})
    return out


# --------------------------------------------------------------------------- try these


def _try_list():
    try:
        with open(TRY_FILE) as f:
            items = json.load(f).get("items") or []
    except (OSError, ValueError):
        return []
    return [i for i in items if isinstance(i, dict) and i.get("id") and i.get("title")]


def _try_card():
    items = _try_list()
    marks = _read("try-marks.json", {})
    waiting = [i for i in items if i["id"] not in marks]
    wrong = [i for i in items if (marks.get(i["id"]) or {}).get("mark") == "bad"]
    rows = []
    for i in waiting:
        rows.append(
            {
                "light": "warn",
                "t": i["title"],
                "s": "Try on: " + (i.get("where") or "any screen") + (f" · went in {i['date']}" if i.get("date") else ""),
                "acts": [{"label": "Works", "do": "works:" + i["id"]}, {"label": "Not right", "do": "bad:" + i["id"]}],
            }
        )
    for i in wrong:
        rows.append({"light": "bad", "t": "Not right: " + i["title"], "s": "Tell Claude what you saw and it gets fixed.", "acts": [{"label": "It works now", "do": "works:" + i["id"]}], "copy": True})
    n = len(waiting)
    return {
        "title": "Try these on a real screen",
        "intro": "New things that have only been checked by the test robot. Tap what you see on a real TV or phone.",
        "level": "bad" if wrong and not n else "warn" if n or wrong else "ok",
        "headline": (f"{n} thing{'' if n == 1 else 's'} to try" if n else "Nothing waiting to be tried") + (f", {len(wrong)} not right" if wrong else ""),
        "reason": "Tell Claude which ones are not right (the Copy button on the browser page copies the list)." if wrong else "",
        "copy": "\n".join("Not right: " + i["title"] for i in wrong),
        "count": n,
        "rows": rows,
    }


def _try_mark(what, item_id):
    if not any(i["id"] == item_id for i in _try_list()):
        raise CardError(404, "That one isn't on the list any more.")
    marks = _read("try-marks.json", {})
    marks[item_id] = {"mark": what, "at": int(time.time())}
    _write("try-marks.json", marks)
    return "Marked as working. Thanks!" if what == "works" else "Marked as not right. Tell Claude what you saw."


# --------------------------------------------------------------------------- the helper's side

CARDS = {"jobs": _jobs_card, "backup": _backup_card, "away": _away_card, "try": _try_card}


def admin_route(method, path, read_json):
    """GET /api/admin/card/<name> and POST /api/admin/card/<name>/do. Returns (status, answer) or None."""
    parts = path.strip("/").split("/")
    if len(parts) < 4 or parts[:3] != ["api", "admin", "card"] or parts[3] not in CARDS:
        return None
    name = parts[3]
    try:
        if method == "GET" and len(parts) == 4:
            return 200, CARDS[name]()
        if method == "POST" and len(parts) == 5 and parts[4] == "do":
            do = str((read_json() or {}).get("do") or "")
            if name == "jobs" and do.startswith("run:"):
                return 200, {"message": _run_job(do[4:])}
            if name in ("backup", "away") and do == "now":
                return 200, {"message": _run_job(name)}
            if name == "try" and do.split(":")[0] in ("works", "bad") and ":" in do:
                return 200, {"message": _try_mark(*do.split(":", 1))}
            return 400, {"message": "Glowfin didn't understand that button."}
    except CardError as e:
        return e.status, {"message": e.message}
    return None


def _nightly():
    time.sleep(300)
    done_day = None
    while True:
        now = time.localtime()
        if 5 <= now.tm_hour < 6 and done_day != now.tm_yday:
            done_day = now.tm_yday
            for job_id in ("backup", "away"):
                try:
                    _run_job(job_id)
                except CardError:
                    pass
                time.sleep(120)
        time.sleep(600)


def start(addresses=None, docker=None):
    """Called once by the helper when it starts. addresses() lists Tower's addresses; docker(method, path) asks Docker."""
    global _dir, _addresses, _docker
    _dir = _pick_dir()
    _addresses, _docker = addresses, docker
    if not _dir:
        log("no folder to save notes in, so the jobs list forgets when Tower restarts")
    job("backup", "Make the nightly safety copy", "Every night between 5 and 6 AM", run=_backup_job)
    job("away", "Check Glowfin can be reached from outside", "Every night between 5 and 6 AM", run=away_check)
    threading.Thread(target=_nightly, name="keeper-nightly", daemon=True).start()
    log(f"ready (notes in {_dir})")
