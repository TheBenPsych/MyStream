"""Tower looking after itself (Idea Box 30, Ben 2026-11-27 "build all now"). Five small jobs in one place:

  letgo      Paused for 20 minutes while Tower is converting? Tower stops the converting so the graphics card and
             processor rest. Pressing Play starts it again in about 2 seconds (Jellyfin restarts it by itself on
             the next piece the TV asks for). Files that play as they are never convert, so nothing changes for them.
  front      Someone is watching a show and its next episodes are still downloading? Those downloads go to the front
             of qBittorrent's line.
  drives     Every night Tower writes down which drive each movie and show sits on. If a drive goes missing, the
             card says which titles were on it, with "Ask for them again" (Radarr / Sonarr look for them).
  updates    Night updates for Tower's other apps, one at a time, only when nobody is watching. If the new version
             doesn't start, the old one is put straight back. Pinned versions, databases, Harbor and this helper
             are left alone (Harbor and the helper already update themselves).
  groups     Release groups whose copies got "Bad copy" twice are skipped from then on (a "Glowfin: skipped groups"
             release profile in Radarr and Sonarr), with Undo on the card.

Cards: GET /api/admin/card/{drives,updates,groups} through benflix_keeper.add_card, the same shape as every other card.
"""

import http.client
import json
import os
import re
import socket
import threading
import time
import urllib.parse

STATE_DIRS = ("/skips/nightwatch", "/saves/.benflix/nightwatch", "/tmp/benflix-nightwatch")
DOCKER_SOCK = "/var/run/docker.sock"
LET_GO_AFTER = 20 * 60
LOST_AT_ONCE = 5  # this many titles from one drive gone at once = the drive is missing, not Ben tidying up
GROUP_STRIKES = 2
PROFILE_NAME = "Glowfin: skipped groups"
SKIP_APPS = ("benflix-games", "harbor", "mariadb", "mysql", "postgres", "redis", "valkey", "mongo")
UPDATES_PER_NIGHT = 4

_lock = threading.Lock()
_dir = None
_jf = None  # helper's jellyfin_call(method, path, token=None, body=None) -> (status, bytes, type)
_admin = None  # () -> (token, user id)
_tools = None  # benflix_server_tools
_paused = {}  # session id -> first time seen paused
_let_go = set()  # play session ids already let go
_fronted = {}  # torrent hash -> time it was moved to the front
_state = {"drives": {}, "lost": [], "updates": [], "updates_on": True, "groups": {}, "skipped": [], "drive_note": ""}


def log(text):
    print(f"Night watch: {text}", flush=True)


def _pick_dir():
    for d in STATE_DIRS:
        try:
            os.makedirs(d, exist_ok=True)
            probe = os.path.join(d, ".probe")
            with open(probe, "w") as f:
                f.write("ok")
            os.remove(probe)
            return d
        except OSError:
            continue
    return None


def _save():
    if not _dir:
        return
    with _lock:
        data = json.dumps(_state)
    tmp = os.path.join(_dir, "state.json.tmp")
    try:
        with open(tmp, "w") as f:
            f.write(data)
        os.replace(tmp, os.path.join(_dir, "state.json"))
    except OSError as e:
        log(f"couldn't save notes ({e})")


def _load():
    if not _dir:
        return
    try:
        with open(os.path.join(_dir, "state.json")) as f:
            saved = json.load(f)
        with _lock:
            _state.update({k: v for k, v in saved.items() if k in _state})
    except (OSError, ValueError):
        pass


def _get(path):
    token, _ = _admin() if _admin else (None, None)
    if not token or not _jf:
        return None
    status, raw, _ = _jf("GET", path, token=token)
    if status != 200:
        return None
    try:
        return json.loads(raw or b"null")
    except ValueError:
        return None


def _jf_call(method, path):
    token, _ = _admin() if _admin else (None, None)
    if not token or not _jf:
        return 0
    return _jf(method, path, token=token)[0]


def _sessions():
    return [s for s in (_get("/Sessions?activeWithinSeconds=600") or []) if isinstance(s, dict)]


def _watching():
    return [s for s in _sessions() if s.get("NowPlayingItem")]


# ---------------------------------------------------------------- letgo: stop converting after 20 paused minutes


def _letgo(sessions, now):
    seen = set()
    for s in sessions:
        play = s.get("PlayState") or {}
        sid = s.get("Id")
        if not sid or not s.get("NowPlayingItem") or not play.get("IsPaused") or not s.get("TranscodingInfo"):
            continue
        seen.add(sid)
        first = _paused.setdefault(sid, now)
        ps = play.get("PlaySessionId") or ""
        if now - first < LET_GO_AFTER or not ps or ps in _let_go:
            continue
        q = urllib.parse.urlencode({"deviceId": s.get("DeviceId") or "", "playSessionId": ps})
        code = _jf_call("DELETE", f"/Videos/ActiveEncodings?{q}")
        if code and code < 300:
            _let_go.add(ps)
            log(f"{s.get('UserName') or 'someone'} was paused 20 minutes, so Tower stopped converting until they press Play")
    for sid in list(_paused):
        if sid not in seen:
            _paused.pop(sid, None)
    if len(_let_go) > 500:
        _let_go.clear()


# ---------------------------------------------------------------- front: downloads for what's being watched go first


def _plain_name(text):
    text = re.sub(r"\(\d{4}\)|\[[^\]]*\]", " ", str(text or "").lower())
    return re.sub(r"[^a-z0-9]+", "", text.replace("&", "and"))


def _front(sessions, now):
    shows = {_plain_name((s.get("NowPlayingItem") or {}).get("SeriesName")) for s in sessions if (s.get("NowPlayingItem") or {}).get("SeriesName")}
    shows.discard("")
    if not shows:
        return
    try:
        import benflix_onepace_get as qbit

        torrents = qbit.qb_json("/api/v2/torrents/info?filter=downloading") or []
    except Exception:  # noqa: BLE001 - no qBittorrent sign-in saved yet, or it's off: nothing to move
        return
    moved = []
    for t in torrents:
        h, name = t.get("hash"), _plain_name(t.get("name"))
        if not h or (h in _fronted and now - _fronted[h] < 3600):
            continue
        if any(len(show) >= 4 and name.startswith(show) for show in shows):
            moved.append(h)
    if not moved:
        return
    try:
        qbit.qb("/api/v2/torrents/topPrio", {"hashes": "|".join(moved)})
        for h in moved:
            _fronted[h] = now
        log(f"moved {len(moved)} download(s) to the front because someone is watching that show")
    except Exception as e:  # noqa: BLE001
        log(f"couldn't move downloads to the front ({e})")


def _watch_loop():
    time.sleep(90)
    while True:
        try:
            sessions = _sessions()
            now = time.time()
            _letgo(sessions, now)
            _front(sessions, now)
        except Exception as e:  # noqa: BLE001 - keep watching
            log(f"watch check failed ({e})")
        time.sleep(60)


# ---------------------------------------------------------------- drives: which drive each title is on


def _disks_of(path):
    """Unraid's share files say which drive(s) a file or folder is on (system.LOCATIONS, like "disk1,disk3")"""
    for attr in ("system.LOCATIONS", "system.LOCATION", "user.LOCATIONS"):
        try:
            raw = os.getxattr(path, attr)
        except (OSError, AttributeError):
            continue
        names = [x.strip() for x in raw.decode("utf-8", "ignore").strip("\x00").split(",") if x.strip()]
        if names:
            return names
    return []


def _nice_disk(name):
    m = re.match(r"disk(\d+)$", name)
    if m:
        return f"Drive {m.group(1)}"
    return name.replace("cache", "Cache drive").replace("_", " ")


def map_drives():
    """Nightly: every movie and show, and the drive it's on. Then the hourly check can tell a lost drive."""
    _, user = _admin() if _admin else (None, None)
    if not user:
        raise RuntimeError("Tower needs the admin's sign-in first. Open the Server page once.")
    items = (_get(f"/Items?userId={user}&Recursive=true&IncludeItemTypes=Movie,Series&Fields=Path,ProviderIds") or {}).get("Items") or []
    found, unknown = {}, 0
    for it in items:
        path = it.get("Path") or ""
        if not path or not os.path.exists(path):
            continue
        disks = _disks_of(path)
        if not disks:
            unknown += 1
            continue
        ids = it.get("ProviderIds") or {}
        found[path] = {"n": it.get("Name") or "", "k": it.get("Type"), "tmdb": ids.get("Tmdb") or "", "tvdb": ids.get("Tvdb") or "", "d": disks}
    with _lock:
        if found:
            _state["drives"] = found
            _state["drive_note"] = ""
        elif items:
            _state["drive_note"] = "Tower can't tell which drive things are on (the media folder isn't an Unraid share here)."
        _state["mapped_at"] = int(time.time())
    _save()
    log(f"drive map: {len(found)} titles written down ({unknown} unknown)")


def check_lost():
    """Hourly: titles from the map that are gone. Lots from one drive at once = that drive is missing."""
    with _lock:
        drives = dict(_state.get("drives") or {})
    if not drives or not os.path.isdir(os.path.dirname(next(iter(drives)))):
        return  # the media folder itself isn't there: Tower is starting up, nothing to say yet
    gone = {p: v for p, v in drives.items() if not os.path.exists(p)}
    if not gone:
        return
    by_disk = {}
    for p, v in gone.items():
        for d in v.get("d") or []:
            by_disk.setdefault(d, []).append(p)
    lost = [d for d, paths in by_disk.items() if len(paths) >= LOST_AT_ONCE]
    with _lock:
        for d in lost:
            if any(x.get("disk") == d and not x.get("done") for x in _state["lost"]):
                continue
            titles = [dict(drives[p], p=p) for p in by_disk[d]]
            _state["lost"].append({"disk": d, "at": int(time.time()), "titles": titles})
            log(f"{_nice_disk(d)} looks missing: {len(titles)} titles that were on it are gone")
        # A few gone titles from a drive that's fine = Ben deleted them: forget them
        for p, v in gone.items():
            if not any(d in lost for d in v.get("d") or []):
                _state["drives"].pop(p, None)
        _state["lost"] = _state["lost"][-6:]
    _save()


def _ask_again(index):
    with _lock:
        if index >= len(_state["lost"]):
            return "That list is gone. Open the card again."
        entry = _state["lost"][index]
    if _tools is None:
        return "Tower couldn't reach Radarr and Sonarr."
    asked = missed = 0
    for t in entry["titles"]:
        try:
            if t.get("k") == "Movie" and _tools.arr_ready("radarr"):
                movie = _tools._find_movie(t.get("tmdb")) if str(t.get("tmdb") or "").isdigit() else None
                if not movie:
                    missed += 1
                    continue
                _tools._arr("radarr", "POST", "/api/v3/command", {"name": "RefreshMovie", "movieIds": [movie["id"]]})
                _tools._arr("radarr", "POST", "/api/v3/command", {"name": "MoviesSearch", "movieIds": [movie["id"]]})
            elif t.get("k") == "Series" and _tools.arr_ready("sonarr"):
                series = _tools._find_series({"tvdb": t.get("tvdb"), "name": t.get("n")})
                if not series:
                    missed += 1
                    continue
                _tools._arr("sonarr", "POST", "/api/v3/command", {"name": "RefreshSeries", "seriesId": series["id"]})
                _tools._arr("sonarr", "POST", "/api/v3/command", {"name": "SeriesSearch", "seriesId": series["id"]})
            else:
                missed += 1
                continue
            asked += 1
        except Exception:  # noqa: BLE001 - keep asking for the rest
            missed += 1
    with _lock:
        entry["done"] = int(time.time())
    _save()
    return f"Asked Radarr and Sonarr to find {asked} title{'s' if asked != 1 else ''} again." + (f" {missed} need Ben to ask by hand." if missed else "")


def drives_card():
    with _lock:
        drives = dict(_state.get("drives") or {})
        lost = [dict(x) for x in _state.get("lost") or []]
        note = _state.get("drive_note") or ""
        at = _state.get("mapped_at")
    count = {}
    for v in drives.values():
        for d in v.get("d") or []:
            count[d] = count.get(d, 0) + 1
    rows = []
    for i, x in enumerate(lost):
        names = ", ".join(t.get("n") for t in x["titles"][:6]) + (f" and {len(x['titles']) - 6} more" if len(x["titles"]) > 6 else "")
        when = time.strftime("%b %d, %I:%M %p", time.localtime(x["at"]))
        acts = [] if x.get("done") else [{"label": "Ask for them again", "do": f"ask:{i}", "ask": f"Ask Radarr and Sonarr to find {len(x['titles'])} titles again?"}]
        rows.append({"light": "ok" if x.get("done") else "bad", "t": f"{_nice_disk(x['disk'])} went missing ({len(x['titles'])} titles)",
                     "s": f"Noticed {when}. {names}." + (" Asked for again." if x.get("done") else ""), "acts": acts})
    for d in sorted(count, key=lambda n: (not n.startswith("disk"), int(re.sub(r"\D", "", n) or 0), n)):
        rows.append({"light": "ok", "t": _nice_disk(d), "s": f"{count[d]} movies and shows", "acts": []})
    open_lost = [x for x in lost if not x.get("done")]
    if open_lost:
        level, head, why = "bad", f"{_nice_disk(open_lost[-1]['disk'])} looks missing", "Lots of titles that were on it are gone at once. Check the drive in Unraid first, then ask for them again."
    elif note:
        level, head, why = "off", "Drive list is off", note
    elif drives:
        level, head, why = "ok", f"{len(drives)} titles written down", "Written every night" + (f", last {time.strftime('%b %d, %I:%M %p', time.localtime(at))}" if at else "") + ". If a drive goes missing, this card says what was on it."
    else:
        level, head, why = "off", "Not written down yet", "The first list is made tonight (or press Write it now)."
    return {"title": "Which drive is what on", "intro": "If a drive dies, you'll know exactly what was on it.", "level": level, "headline": head, "reason": why,
            "acts": [{"label": "Write it now", "do": "map"}], "rows": rows}


def drives_do(do):
    if do == "map":
        threading.Thread(target=_safe, args=(map_drives,), daemon=True).start()
        return "Writing the list now. It takes a minute or two."
    if do.startswith("ask:"):
        return _ask_again(int(do[4:] or 0))
    return "Glowfin didn't understand that button."


# ---------------------------------------------------------------- updates: other apps, one at a time, with put-back


def _docker(method, path, body=None, timeout=60):
    conn = http.client.HTTPConnection("localhost", timeout=timeout)
    sock = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
    sock.settimeout(timeout)
    try:
        sock.connect(DOCKER_SOCK)
        conn.sock = sock
        data = json.dumps(body).encode() if body is not None else None
        conn.request(method, path, body=data, headers={"Content-Type": "application/json"} if data else {})
        r = conn.getresponse()
        raw = r.read()
    finally:
        conn.close()
    if r.status >= 400:
        raise RuntimeError(f"Docker said {r.status}: {raw[:200].decode('utf-8', 'ignore')}")
    try:
        return json.loads(raw) if raw else None
    except ValueError:
        return None  # pulls answer with a stream of lines


def _image_ref(image):
    """("repo", "tag") for an image name, or None when the version is pinned (a number tag or a digest)"""
    if "@" in image:
        return None
    repo, _, tag = image.rpartition(":") if ":" in image.split("/")[-1] else (image, "", "latest")
    tag = tag or "latest"
    if re.match(r"^v?\d+(\.\d+)+", tag):
        return None
    return repo, tag


def _recreate_body(info, image):
    cfg = dict(info.get("Config") or {})
    cfg["Image"] = image
    nets = ((info.get("NetworkSettings") or {}).get("Networks") or {})
    endpoints = {}
    for name, ep in nets.items():
        keep = {k: ep.get(k) for k in ("IPAMConfig", "Aliases", "Links", "MacAddress", "DriverOpts") if ep.get(k)}
        endpoints[name] = keep
    first = dict(list(endpoints.items())[:1])
    cfg["HostConfig"] = info.get("HostConfig") or {}
    cfg["NetworkingConfig"] = {"EndpointsConfig": first}
    return cfg, endpoints


def _healthy(cid, wait=150):
    end = time.time() + wait
    while time.time() < end:
        time.sleep(15)
        st = (_docker("GET", f"/containers/{cid}/json") or {}).get("State") or {}
        if not st.get("Running") or st.get("Restarting"):
            return False
        health = (st.get("Health") or {}).get("Status")
        if health == "unhealthy":
            return False
        if health in (None, "healthy") and time.time() > end - wait + 60:
            return True
    st = (_docker("GET", f"/containers/{cid}/json") or {}).get("State") or {}
    return bool(st.get("Running")) and (st.get("Health") or {}).get("Status") != "unhealthy"


def _update_one(info):
    """Pulls the app's newer version; if there is one, swaps it in and keeps the old one until the new one runs"""
    name = (info.get("Name") or "").lstrip("/")
    image = (info.get("Config") or {}).get("Image") or ""
    ref = _image_ref(image)
    if not ref:
        return None
    repo, tag = ref
    _docker("POST", f"/images/create?fromImage={urllib.parse.quote(repo)}&tag={urllib.parse.quote(tag)}", timeout=900)
    new_id = (_docker("GET", f"/images/{urllib.parse.quote(f'{repo}:{tag}', safe='')}/json") or {}).get("Id")
    if not new_id or new_id == info.get("Image"):
        return None
    old = info["Id"]
    body, endpoints = _recreate_body(info, f"{repo}:{tag}")
    _docker("POST", f"/containers/{old}/stop?t=30", timeout=90)
    _docker("POST", f"/containers/{old}/rename?name={urllib.parse.quote(name + '-glowfin-old')}")
    new = None
    try:
        new = (_docker("POST", f"/containers/create?name={urllib.parse.quote(name)}", body) or {}).get("Id")
        for net, ep in list(endpoints.items())[1:]:
            _docker("POST", f"/networks/{urllib.parse.quote(net)}/connect", {"Container": new, "EndpointConfig": ep})
        _docker("POST", f"/containers/{new}/start")
        ok = _healthy(new)
    except Exception as e:  # noqa: BLE001
        log(f"{name}: new version didn't go in ({e})")
        ok = False
    if ok:
        _docker("DELETE", f"/containers/{old}?force=true")
        return {"app": name, "ok": True, "at": int(time.time()), "s": "Updated, and it's running fine."}
    # Put the old one straight back
    if new:
        try:
            _docker("DELETE", f"/containers/{new}?force=true")
        except Exception:  # noqa: BLE001
            pass
    _docker("POST", f"/containers/{old}/rename?name={urllib.parse.quote(name)}")
    _docker("POST", f"/containers/{old}/start")
    return {"app": name, "ok": False, "at": int(time.time()), "s": "The new version didn't start, so the old one was put back. It's skipped for a week."}


def night_updates(forced=False):
    with _lock:
        if not _state.get("updates_on", True) and not forced:
            return
        skip_until = dict(_state.get("skip_until") or {})
    done = []
    apps = _docker("GET", "/containers/json") or []
    for c in sorted(apps, key=lambda x: (x.get("Names") or [""])[0]):
        if len(done) >= UPDATES_PER_NIGHT:
            break
        name = (c.get("Names") or [""])[0].lstrip("/")
        low = name.lower()
        if any(s in low for s in SKIP_APPS) or skip_until.get(name, 0) > time.time() or low.endswith("-glowfin-old"):
            continue
        if (c.get("Labels") or {}).get("glowfin.noupdate"):
            continue
        if _watching():
            log("someone started watching, so the rest of the updates wait for tomorrow night")
            break
        try:
            res = _update_one(_docker("GET", f"/containers/{c['Id']}/json") or {})
        except Exception as e:  # noqa: BLE001
            res = {"app": name, "ok": False, "at": int(time.time()), "s": f"Couldn't check for a newer version ({e})."}
        if res:
            done.append(res)
            log(f"{name}: {res['s']}")
            if not res["ok"]:
                skip_until[name] = time.time() + 7 * 86400
    with _lock:
        _state["updates"] = (done + list(_state.get("updates") or []))[:20]
        _state["skip_until"] = skip_until
        _state["updates_at"] = int(time.time())
    _save()


def updates_card():
    with _lock:
        on = _state.get("updates_on", True)
        ups = [dict(x) for x in _state.get("updates") or []]
        at = _state.get("updates_at")
    rows = [{"light": "ok" if u["ok"] else "warn", "t": u["app"], "s": time.strftime("%b %d. ", time.localtime(u["at"])) + u["s"], "acts": []} for u in ups[:12]]
    bad = [u for u in ups[:6] if not u["ok"]]
    level = "off" if not on else "warn" if bad else "ok"
    head = "Night updates are off" if not on else f"{bad[0]['app']} was put back" if bad else "Apps update themselves at night"
    why = ("Up to 4 apps a night between 3 and 5 AM, one at a time, only when nobody is watching. "
           "Pinned versions, databases, Harbor and this helper are left alone.") + (f" Last check {time.strftime('%b %d, %I:%M %p', time.localtime(at))}." if at else "")
    acts = [{"label": "Turn off" if on else "Turn on", "do": "toggle"}, {"label": "Check now", "do": "now", "ask": "Update Tower's apps now? Each one stops for a minute while it updates."}]
    return {"title": "Night updates", "intro": "Tower's other apps stay up to date by themselves.", "level": level, "headline": head, "reason": why, "acts": acts, "rows": rows}


def updates_do(do):
    if do == "toggle":
        with _lock:
            _state["updates_on"] = not _state.get("updates_on", True)
            on = _state["updates_on"]
        _save()
        return "Night updates are on." if on else "Night updates are off."
    if do == "now":
        threading.Thread(target=_safe, args=(night_updates, True), daemon=True).start()
        return "Checking each app now, one at a time. The list fills in as they finish."
    return "Glowfin didn't understand that button."


# ---------------------------------------------------------------- groups: skip release groups with two bad copies


def _group_rule(group):
    return "/-" + re.escape(group) + r"\b/i"


def _sync_profiles():
    """One release profile in Radarr and in Sonarr that never takes the skipped groups"""
    with _lock:
        groups = sorted(_state.get("skipped") or [])
    if _tools is None:
        return
    for app in ("radarr", "sonarr"):
        if not _tools.arr_ready(app):
            continue
        try:
            have = next((p for p in _tools._arr(app, "GET", "/api/v3/releaseprofile") or [] if p.get("name") == PROFILE_NAME), None)
            body = dict(have or {}, name=PROFILE_NAME, enabled=True, required=[], ignored=[_group_rule(g) for g in groups], indexerId=0, tags=[])
            if have and not groups:
                _tools._arr(app, "DELETE", f"/api/v3/releaseprofile/{have['id']}")
            elif have:
                _tools._arr(app, "PUT", f"/api/v3/releaseprofile/{have['id']}", body)
            elif groups:
                _tools._arr(app, "POST", "/api/v3/releaseprofile", body)
        except Exception as e:  # noqa: BLE001
            log(f"couldn't update the skipped groups in {app} ({e})")


def bad_copy_group(group, title):
    """Called by Bad copy (benflix_server_tools.bad_copy) with the release group of the copy thrown away"""
    group = re.sub(r"[^A-Za-z0-9._-]", "", str(group or ""))[:40]
    if not group:
        return
    with _lock:
        g = _state["groups"].setdefault(group, {"n": 0, "titles": []})
        g["n"] += 1
        g["titles"] = (g["titles"] + [title])[-5:]
        newly = g["n"] >= GROUP_STRIKES and group not in _state["skipped"]
        if newly:
            _state["skipped"].append(group)
    _save()
    if newly:
        log(f"release group {group} had {GROUP_STRIKES} bad copies, so Radarr and Sonarr skip it from now on")
        threading.Thread(target=_safe, args=(_sync_profiles,), daemon=True).start()


def groups_card():
    with _lock:
        skipped = list(_state.get("skipped") or [])
        groups = {k: dict(v) for k, v in (_state.get("groups") or {}).items()}
    rows = [{"light": "warn", "t": g, "s": f"{groups.get(g, {}).get('n', 2)} bad copies: " + ", ".join(groups.get(g, {}).get("titles") or []), "acts": [{"label": "Undo", "do": f"undo:{g}"}]} for g in skipped]
    rows += [{"light": "off", "t": g, "s": f"1 bad copy so far: {', '.join(v.get('titles') or [])}", "acts": []} for g, v in groups.items() if g not in skipped][:10]
    level = "ok"
    head = f"{len(skipped)} release group{'s' if len(skipped) != 1 else ''} skipped" if skipped else "No groups skipped yet"
    why = f"A group whose copies get Bad copy {GROUP_STRIKES} times is never picked again by Radarr or Sonarr. Undo takes it off the list."
    return {"title": "Skipped release groups", "intro": "Learns from Bad copy, so the same bad uploaders don't come back.", "level": level, "headline": head, "reason": why, "acts": [], "rows": rows}


def groups_do(do):
    if do.startswith("undo:"):
        g = do[5:]
        with _lock:
            if g in _state["skipped"]:
                _state["skipped"].remove(g)
            _state["groups"].pop(g, None)
        _save()
        threading.Thread(target=_safe, args=(_sync_profiles,), daemon=True).start()
        return f"{g} can be picked again."
    return "Glowfin didn't understand that button."


# ---------------------------------------------------------------- start


def _safe(fn, *args):
    try:
        fn(*args)
    except Exception as e:  # noqa: BLE001
        log(f"{getattr(fn, '__name__', 'job')} failed ({e})")


def _night_loop():
    time.sleep(600)
    mapped = updated = None
    while True:
        now = time.localtime()
        if now.tm_hour == 2 and mapped != now.tm_yday:
            mapped = now.tm_yday
            _safe(map_drives)
        if 3 <= now.tm_hour < 5 and updated != now.tm_yday and not _watching():
            updated = now.tm_yday
            _safe(night_updates)
        if now.tm_min < 10:
            _safe(check_lost)
        time.sleep(600)


def start(jellyfin_call, admin, tools=None, keeper=None):
    global _dir, _jf, _admin, _tools
    _jf, _admin, _tools = jellyfin_call, admin, tools
    _dir = _pick_dir()
    _load()
    threading.Thread(target=_watch_loop, name="nightwatch-watch", daemon=True).start()
    threading.Thread(target=_night_loop, name="nightwatch-night", daemon=True).start()
    if keeper is not None:
        keeper.add_card("drivemap", drives_card, drives_do)
        keeper.add_card("updates", updates_card, updates_do)
        keeper.add_card("groups", groups_card, groups_do)
        keeper.job("drivemap", "Write down which drive each title is on", "Every night at 2 AM", run=map_drives)
        keeper.job("nightupdates", "Update Tower's other apps, one at a time", "Every night between 3 and 5 AM", run=lambda: night_updates(True))
    log(f"ready (notes in {_dir})")
