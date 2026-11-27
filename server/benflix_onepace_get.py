"""One Pace downloads (Ben's picks 1, 2 and 4, 2026-11-26): fetch missing episodes and newer cuts with one press.

The One Pace list (ladyisatis/one-pace-metadata) gives every episode's official release: its torrent's info hash
and the file's name inside it. "Get" hands those to Ben's qBittorrent (binhex-qbittorrentvpn, port 8085) with
only the wanted file ticked, saved into Media/One Pace Drop under the "onepace" category. Ben presses Sort himself
when they're done (no timers, his rule); Sort leaves unfinished downloads alone and afterwards removes the finished
ones from qBittorrent (the files stay; they've been sorted).

qBittorrent sign-in: first without a password (works when Tower is on its "skip sign-in" list), then with
qbit.json in the One Pace notes folder: {"user": "admin", "password": "..."} (Unraid path
/mnt/user/appdata/benflix-skips/onepace/qbit.json). Only the Python standard library.
"""

import http.client
import http.cookiejar
import json
import os
import socket
import threading
import time
import urllib.error
import urllib.parse
import urllib.request

CATEGORY = "onepace"
QBIT_URLS = [u for u in (os.environ.get("QBIT_URL", "").rstrip("/"), "http://127.0.0.1:8085") if u]
TRACKERS = (
    "http://nyaa.tracker.wf:7777/announce",
    "udp://open.stealth.si:80/announce",
    "udp://tracker.opentrackr.org:1337/announce",
    "udp://exodus.desync.com:6969/announce",
)


class GetError(Exception):
    """Something Ben can act on, in plain words"""


_lock = threading.Lock()
_qb = {"url": None, "opener": None}
_ctx = {"dir": None, "log": print, "drop": None}


def setup(notes_dir, log, drop):
    _ctx.update({"dir": notes_dir, "log": log, "drop": drop})


# ---------------------------------------------------------------- qBittorrent


def _login_file():
    try:
        with open(os.path.join(_ctx["dir"] or "", "qbit.json")) as f:
            return json.load(f) or {}
    except (OSError, ValueError, TypeError):
        return {}


def _call(opener, url, path, data=None, timeout=20):
    body = urllib.parse.urlencode(data).encode() if data is not None else None
    req = urllib.request.Request(url + path, data=body, headers={"Referer": url, "Origin": url})
    with opener.open(req, timeout=timeout) as r:
        return r.status, r.read()


def _connect():
    """Signs in to qBittorrent once and keeps the cookie"""
    last = "qBittorrent didn't answer on port 8085. Check it's running on Tower."
    login = _login_file()
    for url in [login.get("url", "").rstrip("/")] + QBIT_URLS:
        if not url:
            continue
        opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        try:
            status, _ = _call(opener, url, "/api/v2/app/version", timeout=8)
            if status == 200:
                return url, opener
        except urllib.error.HTTPError as e:
            if e.code not in (401, 403):
                last = f"qBittorrent answered {e.code}."
                continue
        except (OSError, ValueError) as e:
            last = f"qBittorrent didn't answer ({e})."
            continue
        if not login.get("password"):
            raise GetError(
                "qBittorrent wants its password. Save it once in /mnt/user/appdata/benflix-skips/onepace/qbit.json "
                "(Claude can give you the command), then press the button again."
            )
        try:
            status, raw = _call(opener, url, "/api/v2/auth/login", {"username": login.get("user") or "admin", "password": login["password"]})
        except (OSError, ValueError) as e:
            last = f"qBittorrent didn't answer the sign-in ({e})."
            continue
        if raw.strip() != b"Ok.":
            raise GetError("qBittorrent said the saved password is wrong. Save the right one in qbit.json and try again.")
        return url, opener
    raise GetError(last)


def qb(path, data=None, timeout=20):
    """One qBittorrent API call (signs in again once if the cookie ran out) -> bytes"""
    for attempt in (0, 1):
        with _lock:
            if not _qb["opener"] or attempt:
                _qb["url"], _qb["opener"] = _connect()
            url, opener = _qb["url"], _qb["opener"]
        try:
            return _call(opener, url, path, data, timeout)[1]
        except urllib.error.HTTPError as e:
            if e.code in (401, 403) and not attempt:
                continue
            raise GetError(f"qBittorrent answered {e.code} to {path}.")
        except (OSError, ValueError) as e:
            raise GetError(f"qBittorrent didn't answer ({e}).")
    return b""


def qb_json(path, data=None):
    return json.loads(qb(path, data) or b"null")


# ---------------------------------------------------------------- where qBittorrent sees the drop folder


def _docker(path):
    conn = http.client.HTTPConnection("localhost", timeout=10)
    sock = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
    sock.settimeout(10)
    try:
        sock.connect("/var/run/docker.sock")
        conn.sock = sock
        conn.request("GET", path)
        r = conn.getresponse()
        body = r.read()
    finally:
        conn.close()
    if r.status >= 400:
        raise OSError(f"Docker answered {r.status}")
    return json.loads(body or b"null")


def _host_path(container_hint, inside):
    """Tower's own path for a folder the given container sees as `inside`"""
    for c in _docker("/containers/json?all=1") or []:
        names = " ".join(c.get("Names") or []).lower()
        if container_hint not in names:
            continue
        for m in c.get("Mounts") or []:
            dest = (m.get("Destination") or "").rstrip("/")
            if inside == dest or inside.startswith(dest + "/"):
                return (m.get("Source") or "").rstrip("/") + inside[len(dest):]
    return None


def drop_path_for_qbit():
    """The One Pace Drop folder as qBittorrent's container sees it"""
    host = _host_path("benflix-games", _ctx["drop"]) or "/mnt/user/Homelab/Media/One Pace Drop"
    best = None
    for c in _docker("/containers/json?all=1") or []:
        if "qbittorrent" not in " ".join(c.get("Names") or []).lower():
            continue
        for m in c.get("Mounts") or []:
            src = (m.get("Source") or "").rstrip("/")
            if src and (host == src or host.startswith(src + "/")) and (not best or len(src) > len(best[0])):
                best = (src, (m.get("Destination") or "").rstrip("/"))
    if not best:
        raise GetError("qBittorrent can't see the Media folder, so it has nowhere to save One Pace. Add Homelab/Media to its folders in Unraid.")
    return best[1] + host[len(best[0]):]


# ---------------------------------------------------------------- what to get


def magnet(info_hash, name):
    q = "xt=urn:btih:" + info_hash + "&dn=" + urllib.parse.quote(name) + "".join("&tr=" + urllib.parse.quote(t, safe="") for t in TRACKERS)
    return "magnet:?" + q


def start_downloads(wanted, state, save):
    """wanted: [{"hash", "name", "label"}] -> message. Adds each release once, then ticks only the wanted files."""
    if not wanted:
        return "Nothing to get."
    path = drop_path_for_qbit()
    try:
        qb("/api/v2/torrents/createCategory", {"category": CATEGORY, "savePath": path})
    except GetError:
        pass  # it already exists
    groups = {}
    for w in wanted:
        groups.setdefault(w["hash"].lower(), []).append(w)
    have = {t.get("hash", "").lower() for t in qb_json("/api/v2/torrents/info", {"category": CATEGORY}) or []}
    added = 0
    for h, items in groups.items():
        names = [i["name"] for i in items]
        old = (state.get("downloads") or {}).get(h) or {}
        state.setdefault("downloads", {})[h] = {"names": sorted(set(names + (old.get("names") or []))),
                                                "labels": sorted(set([i["label"] for i in items] + (old.get("labels") or []))), "when": time.time()}
        _picked.discard(h)
        if h not in have:
            # Stops by itself once it knows its file list, so nothing downloads before only the wanted files are ticked
            qb("/api/v2/torrents/add", {"urls": magnet(h, names[0]), "savepath": path, "category": CATEGORY, "tags": "onepace",
                                       "autoTMM": "false", "stopCondition": "MetadataReceived"})
        added += len(items)
    save()
    threading.Thread(target=_tick_files, args=(state, list(groups)), name="onepace-pick-files", daemon=True).start()
    return f"Asked qBittorrent for {added} episode{'s' if added != 1 else ''}. Press Sort One Pace now when they're done."


def _verb(verbs, h):
    for verb in verbs:  # qBittorrent 5 says start/stop, 4 says resume/pause
        try:
            qb(f"/api/v2/torrents/{verb}", {"hashes": h})
            return
        except GetError:
            continue


def _pick(state, h):
    """Ticks only the wanted files in one release and starts it. True once done (False while its file list isn't known yet)."""
    if h in _picked:
        return True
    want = set(((state.get("downloads") or {}).get(h) or {}).get("names") or [])
    files = qb_json("/api/v2/torrents/files", {"hash": h}) or []
    if not files:
        return False
    keep, drop = [], []
    for i, f in enumerate(files):
        idx = f.get("index", i)
        (keep if os.path.basename(f.get("name") or "") in want else drop).append(str(idx))
    if not keep:
        _verb(("stop", "pause"), h)  # never let a whole batch download
        _ctx["log"](f"onepace: none of the wanted files are in release {h}, so it was stopped")
        _picked.add(h)
        return True
    if drop:
        qb("/api/v2/torrents/filePrio", {"hash": h, "id": "|".join(drop), "priority": "0"})
    qb("/api/v2/torrents/filePrio", {"hash": h, "id": "|".join(keep), "priority": "1"})
    _verb(("start", "resume"), h)
    _picked.add(h)
    return True


def _tick_files(state, hashes):
    """Waits for each release's file list (up to 3 hours, slow magnets can take a while) and ticks only the wanted files"""
    deadline = time.time() + 3 * 3600
    left = set(hashes)
    while left and time.time() < deadline:
        for h in list(left):
            try:
                if _pick(state, h):
                    left.discard(h)
            except GetError as e:
                _ctx["log"](f"onepace: couldn't pick files in {h}: {e}")
        if left:
            time.sleep(3)
    if left:
        _ctx["log"](f"onepace: qBittorrent didn't get the file list for {len(left)} release(s) in 3 hours")


_picked = set()


def progress(state):
    """-> (rows of (light, title, detail), busy_paths, done_hashes). Never raises."""
    downloads = state.get("downloads") or {}
    if not downloads:
        return [], set(), []
    try:
        info = {t.get("hash", "").lower(): t for t in qb_json("/api/v2/torrents/info", {"category": CATEGORY}) or []}
    except GetError as e:
        return [("bad", "Can't see One Pace downloads", str(e))], set(), []
    rows, busy, done = [], set(), []
    going = []
    for h, d in downloads.items():
        t = info.get(h)
        label = ", ".join(d.get("labels") or d.get("names") or [h])[:120]
        if not t:
            # qBittorrent already removed it (Ben's seeding limit can do that): finished, once it's had time to start
            if time.time() - float(d.get("when") or 0) > 300:
                done.append(h)
            continue
        if h not in _picked and t.get("state") != "metaDL":
            try:
                _pick(state, h)  # after a Tower restart, or when the first try missed it
            except GetError:
                pass
        p = float(t.get("progress") or 0)
        if h not in _picked:
            p = 0.0  # stopped after its file list arrived, waiting for the wanted files to be ticked
        if p >= 1 or (t.get("amount_left") == 0 and t.get("state") not in ("metaDL", "checkingResumeData")):
            done.append(h)
        else:
            going.append((label, p, t.get("state") or ""))
            if t.get("content_path"):
                busy.add(t["content_path"])
            if t.get("save_path") and t.get("name"):
                busy.add(os.path.join(t["save_path"], t["name"]))
    if going:
        avg = sum(p for _, p, _ in going) / len(going)
        first = going[0]
        waiting = " (finding the file)" if first[2] == "metaDL" else ""
        rows.append(("warn", f"Downloading {len(going)} One Pace episode{'s' if len(going) != 1 else ''}, {int(avg * 100)}% done",
                     f"{first[0]}: {int(first[1] * 100)}%{waiting}" + (f", and {len(going) - 1} more" if len(going) > 1 else "")))
    if done:
        rows.append(("ok", f"{len(done)} download{'s' if len(done) != 1 else ''} finished",
                     "Press Sort One Pace now to file " + ("it." if len(done) == 1 else "them.")))
    return rows, busy, done


def _drop_path(p):
    """A qBittorrent path in One Pace Drop, as the helper sees it"""
    i = p.find("/One Pace Drop")
    return (_ctx["drop"] or "") + p[i + len("/One Pace Drop"):] if i >= 0 and _ctx["drop"] else None


def remove_unwanted(state, done):
    """Before a Sort: deletes the unticked files a finished release left behind (bits of neighbouring episodes that
    share a piece with a wanted one). Only files in One Pace Drop that qBittorrent itself says weren't wanted."""
    removed = 0
    for h in done:
        want = set(((state.get("downloads") or {}).get(h) or {}).get("names") or [])
        try:
            t = (qb_json("/api/v2/torrents/info", {"hashes": h}) or [None])[0]
            files = qb_json("/api/v2/torrents/files", {"hash": h}) or [] if t else []
        except GetError:
            continue
        for f in files:
            name = f.get("name") or ""
            if f.get("priority") != 0 or os.path.basename(name) in want:
                continue
            path = _drop_path(os.path.join(t.get("save_path") or "", name))
            if not path:
                continue
            for p in (path, path + ".!qB"):
                try:
                    if os.path.isfile(p):
                        os.remove(p)
                        removed += 1
                except OSError:
                    pass
    if removed:
        _ctx["log"](f"onepace: removed {removed} unticked file(s) left by finished downloads")
    return removed


def tidy_finished(state, done):
    """After a Sort: removes finished One Pace downloads from qBittorrent (keeps the files, already sorted)"""
    if not done:
        return
    try:
        qb("/api/v2/torrents/delete", {"hashes": "|".join(done), "deleteFiles": "false"})
    except GetError as e:
        _ctx["log"](f"onepace: couldn't clear finished downloads: {e}")
        return
    for h in done:
        (state.get("downloads") or {}).pop(h, None)
