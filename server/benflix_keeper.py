"""Glowfin's keeper (Ben's picks, 2026-11-21): four small Server page cards that all speak the same shape.

  jobs    One list of everything Tower's helper does by itself: when it last ran, if it worked, Run now.
  backup  A nightly safety copy of the small things that would be painful to lose (not the movies).
  away    A nightly check that the family can still reach Glowfin from outside the house.
  try     "Try these on a real screen": new things only the test robot has checked, with Works / Not right.
  addons  Jellyfin's add-ons (plugins) in plain words, with Update (Ben's picks, 2026-11-22).
  convert The few converting settings that matter, checked against what Tower has inside.
  jellyfin Shortcuts to the Jellyfin pages that are set once and left alone (rows carry "link").

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
_jellyfin = None  # the helper's jellyfin_call(method, path, token, body)
_admin = {"token": None}  # the admin's sign-in from their last Server page visit, used for Jellyfin's own settings


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


# --------------------------------------------------------------------------- Jellyfin's own settings (2026-11-22)

ADDON_WORDS = (
    ("intro skipper", "Finds intros and credits for Skip intro"),
    ("jellyfin enhanced", "Powers Requests inside Glowfin"),
    ("omdb", "Rotten Tomatoes and IMDb scores"),
    ("tvdb", "Episode names and pictures for shows"),
    ("anilist", "Anime names and scores"),
    ("anidb", "Anime names and episode order"),
    ("tmdb", "Names, posters and descriptions for movies and shows"),
    ("open subtitles", "Finds subtitles online"),
    ("opensubtitles", "Finds subtitles online"),
    ("musicbrainz", "Album and artist details for music"),
    ("audiodb", "Artist photos and album art for music"),
    ("studio images", "Studio logos"),
    ("trakt", "Sends what you watch to Trakt"),
    ("playback reporting", "Keeps a history of what was played"),
    ("bookshelf", "Details and covers for books"),
    ("lyrics", "Finds song words"),
    ("fanart", "Extra artwork"),
    ("kodi", "Keeps Kodi in step with Jellyfin"),
    ("webhook", "Tells other apps when something happens in Jellyfin"),
)
HW_NAMES = {"qsv": "Intel Quick Sync", "vaapi": "VA-API", "nvenc": "Nvidia", "amf": "AMD", "videotoolbox": "Apple", "v4l2m2m": "V4L2", "rkmpp": "Rockchip"}
HW_CODECS = ["h264", "hevc", "mpeg2video", "vc1", "vp8", "vp9", "av1"]
CODEC_WORDS = {"h264": "H.264", "hevc": "HEVC (most 4K)", "vp9": "VP9", "av1": "AV1 (the newest)"}
WHY_WORDS = (
    ("AudioCodecNotSupported", "it can't play this sound type"),
    ("VideoCodecNotSupported", "it can't play this video type"),
    ("ContainerNotSupported", "it can't open this kind of file"),
    ("SubtitleCodecNotSupported", "the subtitles have to be drawn into the picture"),
    ("ContainerBitrateExceedsLimit", "its quality limit is lower than the file"),
    ("VideoBitrateNotSupported", "its quality limit is lower than the file"),
    ("VideoResolutionNotSupported", "the picture is bigger than it can show"),
    ("VideoRangeTypeNotSupported", "it can't show this kind of HDR"),
    ("VideoLevelNotSupported", "the video is too demanding for it"),
    ("VideoProfileNotSupported", "the video is too demanding for it"),
    ("AudioChannelsNotSupported", "it can't play surround sound"),
)
JELLYFIN_PAGES = (
    ("Add or remove a library folder", "Dashboard > Libraries", "#/dashboard/libraries"),
    ("Internet address, ports and the safety lock", "Dashboard > Networking. A mistake here can lock everyone out.", "#/dashboard/networking"),
    ("Fine tuning for converting", "Dashboard > Playback > Transcoding", "#/dashboard/playback/transcoding"),
    ("Live TV tuners and recording", "Dashboard > Live TV", "#/dashboard/livetv"),
    ("Where names and pictures come from, and in which language", "Dashboard > Libraries > Metadata", "#/dashboard/libraries/metadata"),
    ("Finding and adding brand new add-ons", "Dashboard > Plugins", "#/dashboard/plugins"),
    ("Keys for other apps", "Dashboard > API Keys", "#/dashboard/keys"),
    ("Jellyfin's full log files", "Dashboard > Logs", "#/dashboard/logs"),
    ("Jellyfin's own look and login message", "Dashboard > General", "#/dashboard/settings"),
)


def _jf(method, path, body=None):
    """One question to Jellyfin as the admin. Returns the parsed answer (None when Jellyfin sends nothing back)."""
    if not _jellyfin or not _admin.get("token"):
        raise CardError(503, "Tower couldn't ask Jellyfin. Open the Server page again and try once more.")
    try:
        status, raw, _ = _jellyfin(method, path, _admin["token"], None if body is None else json.dumps(body).encode())
    except CardError:
        raise
    except Exception as e:  # noqa: BLE001 - the helper's own "couldn't reach Jellyfin"
        raise CardError(502, _plain(e))
    if status in (401, 403):
        raise CardError(403, "Jellyfin said no. Sign in again as the admin and try once more.")
    if status >= 400:
        raise CardError(502, f"Jellyfin answered with a problem ({status}). Try again in a minute.")
    try:
        return json.loads(raw) if raw else None
    except ValueError:
        return None


def _ver(text):
    out = []
    for part in str(text or "").split("."):
        digits = "".join(ch for ch in part if ch.isdigit())
        out.append(int(digits) if digits else 0)
    return tuple(out)


def _watching():
    """Names of the people playing something right now."""
    try:
        return [s.get("UserName") or "Someone" for s in (_jf("GET", "/Sessions") or []) if isinstance(s, dict) and s.get("NowPlayingItem")]
    except CardError:
        return []


def _addons():
    """Installed add-ons, one per name (the newest copy), each with the newest version Jellyfin's catalog offers."""
    have = {}
    for p in _jf("GET", "/Plugins") or []:
        if not isinstance(p, dict) or not p.get("Name"):
            continue
        key = str(p.get("Id") or p["Name"]).replace("-", "").lower()
        old = have.get(key)
        if p.get("Status") in ("Superceded", "Deleted") and old:
            continue
        if not old or old.get("Status") in ("Superceded", "Deleted") or _ver(p.get("Version")) > _ver(old.get("Version")):
            have[key] = p
    try:
        catalog = _jf("GET", "/Packages") or []
    except CardError:
        catalog = []
    newest = {}
    for c in catalog:
        if not isinstance(c, dict):
            continue
        versions = sorted((v for v in c.get("versions") or [] if isinstance(v, dict) and v.get("version")), key=lambda v: _ver(v["version"]), reverse=True)
        if versions:
            newest[str(c.get("guid") or "").replace("-", "").lower()] = (c.get("name"), c.get("guid"), versions[0])
    out = []
    for key, p in sorted(have.items(), key=lambda kv: kv[1]["Name"].lower()):
        offer = newest.get(key)
        behind = bool(offer and _ver(offer[2]["version"]) > _ver(p.get("Version")) and p.get("Status") != "Restart")
        out.append({"key": key, "plugin": p, "offer": offer if behind else None})
    return out


def _addon_words(p):
    low = p["Name"].lower()
    for word, plain in ADDON_WORDS:
        if word in low:
            return plain
    text = " ".join(str(p.get("Description") or "").split())
    return text[:90] + ("…" if len(text) > 90 else "")


def _addons_card():
    items = _addons()
    waiting = _read("addons.json", {}).get("restart_wanted")
    rows, behind, broken, restart = [], 0, 0, 0
    for it in items:
        p, offer = it["plugin"], it["offer"]
        status = p.get("Status") or "Active"
        bits = [b for b in (_addon_words(p), f"version {p.get('Version')}") if b]
        row = {"light": "ok", "t": p["Name"]}
        if status == "Restart":
            restart += 1
            row["light"] = "warn"
            bits.append("starts working after Jellyfin restarts")
        elif status == "Disabled":
            row["light"] = "off"
            bits.append("switched off in Jellyfin")
        elif status in ("Malfunctioned", "NotSupported"):
            broken += 1
            row["light"] = "bad"
            bits.append("it stopped working" if status == "Malfunctioned" else "it doesn't fit this Jellyfin version")
        if offer:
            behind += 1
            if row["light"] == "ok":
                row["light"] = "warn"
            bits.append(f"{offer[2]['version']} is out")
            row["acts"] = [{"label": "Update", "do": "update:" + it["key"]}]
        elif status == "Active":
            bits.append("up to date")
        row["s"] = " · ".join(bits)
        rows.append(row)
    rows.sort(key=lambda r: {"bad": 0, "warn": 1, "ok": 2, "off": 3}.get(r["light"], 2))
    acts = []
    if behind > 1:
        acts.append({"label": f"Update the {behind} that are behind", "do": "updateall"})
    if restart or waiting:
        acts.append({"label": "Restart Jellyfin now", "do": "restart"})
    if broken:
        level, headline = "bad", f"{broken} add-on{'' if broken == 1 else 's'} stopped working"
        reason = "Updating it usually fixes this. If there is no Update button, look in Jellyfin (Tools > Tower > Rarely needed)."
    elif behind:
        level, headline = "warn", f"{behind} add-on{' is' if behind == 1 else 's are'} behind"
        reason = "Update puts the new version in, then Jellyfin restarts by itself as soon as nobody is watching."
    elif restart or waiting:
        level, headline = "warn", "Jellyfin needs a restart to finish an update"
        reason = "It restarts by itself as soon as nobody is watching. That takes about a minute."
    elif not rows:
        level, headline, reason = "off", "Jellyfin has no add-ons", ""
    else:
        level, headline, reason = "ok", "Every add-on is up to date", ""
    return {
        "title": "Jellyfin add-ons",
        "intro": "The extra parts added to Jellyfin that Glowfin leans on. Finding brand new ones stays in Jellyfin.",
        "level": level,
        "headline": headline,
        "reason": reason,
        "acts": acts,
        "rows": rows,
    }


def _restart_jellyfin_when_quiet():
    """After an add-on update: restart Jellyfin once nobody is playing anything. Gives up after 12 hours."""
    with _lock:
        if "addon-restart" in _running:
            return
        _running.add("addon-restart")
    notes = _read("addons.json", {})
    notes["restart_wanted"] = int(time.time())
    _write("addons.json", notes)

    def work():
        try:
            time.sleep(45)  # Jellyfin downloads the new version first
            for _ in range(144):
                if not _read("addons.json", {}).get("restart_wanted"):
                    return  # someone restarted it by hand
                if not _watching():
                    _jf("POST", "/System/Restart")
                    log("restarted Jellyfin to finish an add-on update")
                    break
                time.sleep(300)
        except Exception as e:  # noqa: BLE001 - the card still shows the Restart button
            log(f"couldn't restart Jellyfin after an add-on update: {_plain(e)}")
        finally:
            notes = _read("addons.json", {})
            notes.pop("restart_wanted", None)
            _write("addons.json", notes)
            with _lock:
                _running.discard("addon-restart")

    threading.Thread(target=work, name="keeper-addon-restart", daemon=True).start()


def _addons_do(do):
    if do == "restart":
        who = _watching()
        if who:
            raise CardError(409, f"{', '.join(sorted(set(who)))} {'is' if len(set(who)) == 1 else 'are'} watching right now. Jellyfin restarts by itself when they finish.")
        notes = _read("addons.json", {})
        notes.pop("restart_wanted", None)
        _write("addons.json", notes)
        _jf("POST", "/System/Restart")
        return "Jellyfin is restarting. Give it a minute."
    todo = [it for it in _addons() if it["offer"] and (do == "updateall" or do == "update:" + it["key"])]
    if not todo:
        return "Nothing to update. It may already be done."
    done = []
    for it in todo:
        name, guid, v = it["offer"]
        q = urllib.parse.urlencode({k: val for k, val in (("assemblyGuid", guid), ("version", v["version"]), ("repositoryUrl", v.get("repositoryUrl"))) if val})
        _jf("POST", f"/Packages/Installed/{urllib.parse.quote(str(name), safe='')}?{q}")
        done.append(it["plugin"]["Name"])
    _restart_jellyfin_when_quiet()
    who = _watching()
    after = "as soon as nobody is watching" if who else "in about a minute"
    return f"Updating {', '.join(done)}. Jellyfin restarts by itself {after} to finish."


def _jellyfin_sees_chip():
    """True when Jellyfin's container was given Tower's graphics chip (/dev/dri), False when not, None when unknown."""
    name = _find_container("jellyfin")
    if not name:
        return None
    try:
        info = _docker("GET", f"/containers/{urllib.parse.quote(name)}/json", timeout=10) or {}
    except Exception:  # noqa: BLE001
        return None
    host = info.get("HostConfig") or {}
    paths = [d.get("PathOnHost") or "" for d in host.get("Devices") or []] + [m.get("Source") or "" for m in info.get("Mounts") or []]
    return bool(host.get("Privileged")) or any(p.startswith("/dev/dri") for p in paths)


def _convert_card():
    conf = _jf("GET", "/System/Configuration/encoding") or {}
    kind = str(conf.get("HardwareAccelerationType") or "none").lower()
    chip_on = kind != "none"
    saved = _read("convert.json", {}).get("before")
    rows, wrong = [], 0
    if chip_on:
        rows.append({"light": "ok", "t": "Tower's graphics chip does the converting", "s": f"On · {HW_NAMES.get(kind, kind.upper())}. Tower can convert several movies at once."})
    else:
        wrong += 1
        sees = _jellyfin_sees_chip()
        row = {"light": "warn", "t": "Tower's graphics chip is not being used", "s": "The main processor does all the converting, so two or three people watching away from home can make everything stutter."}
        if sees:
            row["acts"] = [{"label": "Turn it on", "do": "chip"}]
        elif sees is False:
            row["s"] += " Jellyfin's app in Unraid hasn't been given the graphics chip yet, so this can't be switched on from here."
        rows.append(row)
    if chip_on:
        tone = bool(conf.get("EnableTonemapping"))
        row = {"light": "ok" if tone else "warn", "t": "4K HDR on older TVs keeps its colours", "s": "On" if tone else "Off. HDR movies look grey and washed out on screens that can't show HDR."}
        if not tone:
            wrong += 1
            row["acts"] = [{"label": "Turn it on", "do": "tone"}]
        rows.append(row)
        if kind in ("qsv", "vaapi"):
            have = {str(c).lower() for c in conf.get("HardwareDecodingCodecs") or []}
            missing = [CODEC_WORDS[c] for c in CODEC_WORDS if c not in have]
            row = {"light": "warn" if missing else "ok", "t": "The chip also opens every common video type", "s": ("Not yet for " + ", ".join(missing) + ". Those are opened the slow way.") if missing else "H.264, HEVC, VP9 and AV1"}
            if missing:
                wrong += 1
                row["acts"] = [{"label": "Turn them on", "do": "codecs"}]
            rows.append(row)
    try:
        sessions = [s for s in (_jf("GET", "/Sessions") or []) if isinstance(s, dict) and s.get("NowPlayingItem")]
    except CardError:
        sessions = []
    busy = [s for s in sessions if (s.get("TranscodingInfo") or {}).get("IsVideoDirect") is False]
    if busy:
        for s in busy[:4]:
            why = next((plain for word, plain in WHY_WORDS if word in ((s.get("TranscodingInfo") or {}).get("TranscodeReasons") or [])), "")
            rows.append({"light": "ok", "t": f"Converting now: {s['NowPlayingItem'].get('Name') or 'a title'}", "s": f"For {s.get('UserName') or 'someone'} on {s.get('DeviceName') or 'a screen'}" + (f" · {why}" if why else "")})
    else:
        rows.append({"light": "off", "t": "Nothing is being converted right now", "s": f"{len(sessions)} playing as they are, which is the best case" if sessions else "Nobody is watching"})
    if saved:
        rows.append({"light": "off", "t": "Movies stopped playing after a change here?", "s": "This puts Jellyfin's converting settings back to how they were before Glowfin changed them.", "acts": [{"label": "Put it back", "do": "undo"}]})
    return {
        "title": "Converting",
        "intro": "When a screen can't play a file as it is, Tower converts it while you watch. These are Jellyfin's settings for that, the few that matter.",
        "level": "warn" if wrong else "ok",
        "headline": f"{wrong} converting setting{'' if wrong == 1 else 's'} could be better" if wrong else "Converting is set up well",
        "reason": "" if wrong else "Everything else on Jellyfin's converting page can stay as it is.",
        "rows": rows,
    }


def _convert_do(do):
    if do not in ("chip", "tone", "codecs", "undo"):
        raise CardError(400, "Glowfin didn't understand that button.")
    notes = _read("convert.json", {})
    if do == "undo":
        if not notes.get("before"):
            return "There is nothing to put back."
        _jf("POST", "/System/Configuration/encoding", notes["before"])
        _write("convert.json", {})
        return "Converting settings are back to how they were."
    conf = _jf("GET", "/System/Configuration/encoding")
    if not isinstance(conf, dict) or "HardwareAccelerationType" not in conf:
        raise CardError(502, "Jellyfin didn't send its converting settings. Nothing was changed.")
    if not notes.get("before"):
        notes["before"] = json.loads(json.dumps(conf))
    codecs = sorted({str(c).lower() for c in conf.get("HardwareDecodingCodecs") or []} | set(HW_CODECS))
    if do == "chip":
        if not _jellyfin_sees_chip():
            raise CardError(409, "Jellyfin's app in Unraid hasn't been given the graphics chip, so nothing was changed.")
        conf.update({"HardwareAccelerationType": "qsv", "EnableHardwareEncoding": True, "HardwareDecodingCodecs": codecs, "EnableDecodingColorDepth10Hevc": True, "EnableDecodingColorDepth10Vp9": True})
        said = "The graphics chip is on. Play something away from home to try it. If it won't play, press Put it back."
    elif do == "tone":
        conf["EnableTonemapping"] = True
        said = "HDR colours are on."
    else:
        conf.update({"HardwareDecodingCodecs": codecs, "EnableDecodingColorDepth10Hevc": True, "EnableDecodingColorDepth10Vp9": True})
        said = "The chip now opens every common video type. If something won't play, press Put it back."
    _jf("POST", "/System/Configuration/encoding", conf)
    _write("convert.json", notes)
    return said


def _jellyfin_card():
    port, internet = 8096, None
    try:
        for a in ((_addresses() or {}).get("addresses") or []) if _addresses else []:
            if a.get("kind") == "home":
                port = urllib.parse.urlparse(a["url"]).port or port
            elif a.get("kind") == "internet" and not internet:
                internet = a["url"]
    except Exception:  # noqa: BLE001 - the browser falls back to this page's own address
        pass
    return {
        "title": "Rarely needed",
        "intro": "Things you set once and leave. They stay in Jellyfin, and each button opens the exact Jellyfin page.",
        "level": "off",
        "headline": "These stay in Jellyfin",
        "reason": "They are set once and left alone, and Jellyfin's own page is the safer place to change them. Sign in there as the admin.",
        "port": port,
        "internet": internet,
        "rows": [{"light": "off", "t": t, "s": s, "link": link} for t, s, link in JELLYFIN_PAGES],
    }



# --------------------------------------------------------------------------- the helper's side

CARDS = {"jobs": _jobs_card, "backup": _backup_card, "away": _away_card, "try": _try_card, "addons": _addons_card, "convert": _convert_card, "jellyfin": _jellyfin_card}


_EXTRA_DO = {}  # cards other modules add: name -> do(button id) -> message


def add_card(name, fn, do):
    """Lets another module add a card in the same shape (fn() -> card, do(button id) -> message)"""
    CARDS[name] = fn
    _EXTRA_DO[name] = do


def admin_route(method, path, read_json, token=None):
    """GET /api/admin/card/<name> and POST /api/admin/card/<name>/do. Returns (status, answer) or None."""
    if token:
        _admin["token"] = token
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
            if name == "addons" and (do in ("updateall", "restart") or do.startswith("update:")):
                return 200, {"message": _addons_do(do)}
            if name == "convert":
                return 200, {"message": _convert_do(do)}
            if name in _EXTRA_DO:
                try:
                    return 200, {"message": _EXTRA_DO[name](do)}
                except Exception as e:  # noqa: BLE001
                    return 502, {"message": _plain(e)}
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


def start(addresses=None, docker=None, jellyfin=None):
    """Called once by the helper when it starts. addresses() lists Tower's addresses; docker(method, path) asks Docker;
    jellyfin(method, path, token, body) asks Jellyfin."""
    global _dir, _addresses, _docker, _jellyfin
    _dir = _pick_dir()
    _addresses, _docker, _jellyfin = addresses, docker, jellyfin
    if not _dir:
        log("no folder to save notes in, so the jobs list forgets when Tower restarts")
    job("backup", "Make the nightly safety copy", "Every night between 5 and 6 AM", run=_backup_job)
    job("away", "Check Glowfin can be reached from outside", "Every night between 5 and 6 AM", run=away_check)
    threading.Thread(target=_nightly, name="keeper-nightly", daemon=True).start()
    log(f"ready (notes in {_dir})")
