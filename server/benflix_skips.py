"""
Benflix skip times: where the intro and the credits really are in each episode, worked out on Tower.

Why: Intro Skipper (the Jellyfin plugin) only compares two episodes at a time and finds credits by
looking for black screens, so its times are often off. Benflix then skipped at the wrong moment.

How it works, in plain words:
- The sound of each episode is turned into a "fingerprint" (fpcalc, the same tool music apps use to
  recognise songs). Only the start of the episode (where intros are) and the end (where credits are).
- Every episode is compared with up to 4 others from the same season. The theme song is the bit of
  sound they share. When at least 2 other episodes agree on where it is, the time is "sure".
- The credits are found the same way: the ending song is the same every week.
- Chapter names (like "Opening" or "End Credits"), TheIntroDB (times people checked by hand) and
  Intro Skipper's own times are looked at too. Times that two sources agree on are also "sure".
- Anyone can press "Fix skip times" in the player. Their fix is used for that episode, and Benflix
  looks for the same sound in every other episode of the show, so one fix mends the whole season.
- Benflix only skips by itself when the time is sure. Otherwise it just offers the Skip button.

Everything is saved in /skips (Tower's appdata/benflix-skips folder). Runs inside the Benflix helper
(benflix-games-helper.py), one episode at a time with low priority so it never slows down watching.
Needs ffmpeg and fpcalc (installed by the setup command). Only uses the Python standard library.
"""

import array
import base64
import heapq
import itertools
import json
import os
import re
import shutil
import subprocess
import tempfile
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
import zlib

VERSION = 1  # Raise to make Tower work every season out again after the method changes
PT = 0.1238  # Seconds of sound per fingerprint number (fpcalc: 11025 Hz, 4096 frames, 2/3 overlap)
TICKS = 10_000_000  # Jellyfin counts time in these (100 nanoseconds)

HEAD_PART, HEAD_MAX = 0.30, 720  # Look for the intro in the first 30% of the episode, at most 12 minutes
TAIL_PART, TAIL_MAX = 0.30, 480  # Look for the credits in the last 30%, at most 8 minutes
INTRO_MIN, INTRO_MAX = 12, 200  # Believable intro length (seconds)
CREDITS_MIN, CREDITS_MAX = 15, 900
MATCH_BITS = 7  # Two fingerprint numbers "match" when at most this many of their 32 bits differ
MAX_GAP = 3.5  # A shared stretch may have short gaps (seconds) where the sound differs, like a sound effect
AGREE = 3.0  # Two times "agree" when this close (seconds)
NEIGHBOURS = 4  # Each episode is compared with this many others from its season
# Each fingerprint number hears about 2 seconds of sound, so a shared stretch is found starting a
# touch early and ending about a second early. Measured on test episodes; these put it right.
START_PAD, END_PAD = 0.3, 1.0
CLIP = 15.0  # Seconds of sound around a fix that are looked for in the other episodes
CLIP_BER = 0.30  # A clip is "found" when at most this share of its bits differ
TIDB_URL = "https://api.theintrodb.org/v4/media"
JELLYFIN_TYPES = ("Intro", "Outro", "Recap", "Preview", "Commercial")

FIX_KINDS = {
    "intro_start": "Intro starts here",
    "intro_end": "Intro ends here",
    "credits_start": "Credits start here",
    "credits_end": "Credits end here",
    "no_intro": "No intro in this episode",
    "no_intro_show": "No intro in this show",
    "undo": "Undo my fixes",
}

_jf = None  # The helper's jellyfin_call(method, path, token=None, body=None) -> (status, bytes, type)
_dir = None
_lock = threading.RLock()
_queue = []  # (priority, order, kind, key, info)
_queued = {}  # key -> priority
_running = None
_counter = itertools.count()
_wake = threading.Event()
_admin = {"token": None, "user": None}
_log_lines = []


def log(text):
    line = time.strftime("%H:%M:%S ") + text
    print("skips: " + text, flush=True)
    with _lock:
        _log_lines.append(line)
        del _log_lines[:-60]


# ---------- Start up ----------


def _pick_dir():
    for d in (os.environ.get("SKIPS_DIR"), "/skips", "/tracks/benflix-skips"):
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


def tools_ok():
    return bool(shutil.which("ffmpeg") and shutil.which("fpcalc"))


def start(jellyfin_call):
    """Called once by the helper when it starts"""
    global _jf, _dir
    _jf = jellyfin_call
    _dir = _pick_dir()
    if not _dir:
        log("no folder to save skip times in, so they're off")
        return
    for sub in ("fp", "seasons", "tidb", "found"):
        os.makedirs(os.path.join(_dir, sub), exist_ok=True)
    saved = _read("admin.json") or {}
    _admin.update({k: saved.get(k) for k in ("token", "user")})
    threading.Thread(target=_worker, name="skips-worker", daemon=True).start()
    threading.Thread(target=_nightly, name="skips-nightly", daemon=True).start()
    log(f"ready (saving in {_dir}; ffmpeg and fpcalc {'found' if tools_ok() else 'MISSING, run the setup command again'})")


def status():
    with _lock:
        seasons = len(os.listdir(os.path.join(_dir, "seasons"))) if _dir else 0
        return {
            "on": bool(_dir),
            "tools": tools_ok(),
            "seasons_done": seasons,
            "waiting": len(_queue),
            "working_on": _running,
            "log": list(_log_lines[-20:]),
        }


# ---------- Saving ----------


def _path(name):
    return os.path.join(_dir, name)


def _read(name):
    try:
        with open(_path(name), "rb") as f:
            data = f.read()
        if name.endswith(".z"):
            data = zlib.decompress(data)
        return json.loads(data)
    except (OSError, ValueError, zlib.error):
        return None


def _write(name, value):
    data = json.dumps(value, separators=(",", ":")).encode()
    if name.endswith(".z"):
        data = zlib.compress(data, 6)
    tmp = _path(name) + ".tmp"
    with open(tmp, "wb") as f:
        f.write(data)
    os.replace(tmp, _path(name))


def _safe(item_id):
    return re.sub(r"[^0-9a-fA-F]", "", str(item_id or "")).lower()


def _fixes():
    return _read("fixes.json") or {"episodes": {}, "series": {}}


# ---------- Talking to Jellyfin and TheIntroDB ----------


def _get(path, token):
    status, data, _ = _jf("GET", path, token=token)
    if status != 200:
        raise RuntimeError(f"Jellyfin answered {status} for {path.split('?')[0]}")
    return json.loads(data or b"null")


def _item(item_id, token, user_id):
    q = urllib.parse.urlencode({"userId": user_id} if user_id else {})
    return _get(f"/Items/{item_id}?{q}", token)


def _episodes(series_id, season_id, token, user_id):
    q = {"seasonId": season_id, "fields": "Path,Chapters,MediaSources,ProviderIds"}
    if user_id:
        q["userId"] = user_id
    found = _get(f"/Shows/{series_id}/Episodes?{urllib.parse.urlencode(q)}", token) or {}
    return [e for e in found.get("Items") or [] if e.get("LocationType") != "Virtual"]


def _server_segments(item_id, token):
    try:
        found = _get(f"/MediaSegments/{item_id}", token) or {}
    except Exception:
        return []
    out = []
    for s in found.get("Items") or []:
        kind = str(s.get("Type") or "")
        kind = next((t for t in JELLYFIN_TYPES if t.lower() == kind.lower()), None)
        if kind:
            out.append((kind, (s.get("StartTicks") or 0) / TICKS, (s.get("EndTicks") or 0) / TICKS))
    return out


def _provider(ids, name):
    for k, v in (ids or {}).items():
        if k.lower() == name and v:
            return str(v).strip()
    return None


def _tidb(item, series, length):
    """TheIntroDB's hand-checked times for this episode or movie, saved for a week. {} when none."""
    key = f"tidb/{_safe(item.get('Id'))}.json"
    saved = _read(key)
    if saved and time.time() - saved.get("at", 0) < saved.get("keep", 0):
        return saved.get("data") or {}
    is_movie = item.get("Type") == "Movie"
    ids = (item if is_movie else series or {}).get("ProviderIds") or {}
    q = {}
    tvdb, tmdb, imdb = _provider(ids, "tvdb"), _provider(ids, "tmdb"), _provider(ids, "imdb")
    if tvdb and tvdb.isdigit() and not is_movie:
        q["tvdb_id"] = tvdb
    elif tmdb and tmdb.isdigit():
        q["tmdb_id"] = tmdb
    elif imdb:
        q["imdb_id"] = imdb
    else:
        return {}
    if not is_movie:
        season, episode = item.get("ParentIndexNumber"), item.get("IndexNumber")
        if season is None or episode is None:
            return {}
        q.update({"season": season, "episode": episode})
    if length > 0:
        q["duration_ms"] = int(length * 1000)
    req = urllib.request.Request(
        TIDB_URL + "?" + urllib.parse.urlencode(q),
        headers={"Accept": "application/json", "User-Agent": "benflix-helper/1.0"},
    )
    data, keep = {}, 7 * 86400
    try:
        with urllib.request.urlopen(req, timeout=5) as r:
            raw = json.loads(r.read() or b"{}")
        for name, kind in (("intro", "Intro"), ("credits", "Outro"), ("recap", "Recap"), ("preview", "Preview")):
            for stamp in raw.get(name) or []:
                s = (stamp.get("start_ms") or 0) / 1000
                e = stamp.get("end_ms")
                e = e / 1000 if e else length
                if e and e > s:
                    data.setdefault(kind, [s, e])
    except urllib.error.HTTPError as e:
        keep = 3 * 86400 if e.code in (404, 400, 422) else 3600
    except Exception:
        keep = 3600
    _write(key, {"at": time.time(), "keep": keep, "data": data})
    return data


# ---------- Fingerprints ----------


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


def fingerprint(path, start, length):
    """The sound from [start] for [length] seconds as a list of fingerprint numbers"""
    with tempfile.TemporaryDirectory(prefix="benflix-skip-") as d:
        wav = os.path.join(d, "a.wav")
        subprocess.run(
            ["nice", "-n", "15", "ffmpeg", "-nostdin", "-v", "error", "-threads", "1", "-ss", f"{max(0.0, start):.3f}",
             "-i", path, "-t", f"{length:.3f}", "-map", "0:a:0", "-vn", "-sn", "-dn", "-ac", "1", "-ar", "11025",
             "-y", wav],
            check=True, capture_output=True, timeout=900,
        )
        out = subprocess.run(
            ["nice", "-n", "15", "fpcalc", "-raw", "-length", str(int(length) + 5), wav],
            check=True, capture_output=True, timeout=300, text=True,
        ).stdout
    for line in out.splitlines():
        if line.startswith("FINGERPRINT="):
            return [int(x) & 0xFFFFFFFF for x in line[12:].split(",") if x.strip()]
    return []


def _pack(points):
    return base64.b64encode(array.array("I", points).tobytes()).decode()


def _unpack(text):
    a = array.array("I")
    a.frombytes(base64.b64decode(text))
    return a.tolist()


def _windows(length):
    head = min(HEAD_PART * length, HEAD_MAX)
    tail = min(TAIL_PART * length, TAIL_MAX)
    return head, max(0.0, length - tail), tail


def _file_key(path):
    st = os.stat(path)
    return f"{st.st_size}-{int(st.st_mtime)}"


def episode_prints(ep):
    """{"head": [...], "tail": [...], "tail_start": seconds} for one episode, saved so it's only done once"""
    path = ep["file"]
    key = _file_key(path)
    name = f"fp/{_safe(ep['id'])}.json.z"
    saved = _read(name)
    if saved and saved.get("key") == key:
        return {"head": _unpack(saved["head"]), "tail": _unpack(saved["tail"]), "tail_start": saved["tail_start"]}
    head_len, tail_start, tail_len = _windows(ep["len"])
    head = fingerprint(path, 0, head_len)
    tail = fingerprint(path, tail_start, tail_len)
    _write(name, {"key": key, "head": _pack(head), "tail": _pack(tail), "tail_start": tail_start})
    return {"head": head, "tail": tail, "tail_start": tail_start}


# ---------- Finding shared sound ----------


def shared_stretch(a, b, min_len):
    """The longest stretch of sound both [a] and [b] have. Returns (a_start, a_end, b_start, b_end) as
    fingerprint positions, or None. Like a "spot the same bit" between two recordings."""
    if not a or not b:
        return None
    where = {}
    for j, v in enumerate(b):
        where.setdefault(v, []).append(j)
    votes = {}
    for i, v in enumerate(a):
        spots = where.get(v, ())
        if len(spots) > 12:
            continue  # Silence and hum repeat the same number over and over, so they don't count
        for j in spots:
            votes[i - j] = votes.get(i - j, 0) + 1
    if not votes:
        return None
    gap = int(MAX_GAP / PT)
    need = int(min_len / PT)
    best = None
    for shift, count in sorted(votes.items(), key=lambda kv: -kv[1])[:10]:
        if count < 5:
            break
        lo, hi = max(0, shift), min(len(a), len(b) + shift)
        runs = []
        run_start = last_good = None
        for i in range(lo, hi):
            if (a[i] ^ b[i - shift]).bit_count() <= MATCH_BITS:
                if run_start is None or i - last_good > gap:
                    if run_start is not None:
                        runs.append((run_start, last_good))
                    run_start = i
                last_good = i
        if run_start is not None:
            runs.append((run_start, last_good))
        for s, e in runs:
            if e - s >= need and lively(a[s:e + 1]) and (best is None or e - s > best[1] - best[0]):
                best = (s, e, shift)
    if not best:
        return None
    s, e, shift = best
    return s, e + 1, s - shift, e + 1 - shift


def lively(points):
    """False for silence or a steady hum: their fingerprint is the same few numbers again and again"""
    return len(points) > 0 and len(set(points)) >= 0.4 * len(points)


def find_clip(clip, hay, max_ber=CLIP_BER):
    """Where [clip] is in [hay], as a position, and how different it was (0 = the same, 0.5 = unrelated).
    (None, ber) when it isn't there."""
    n = len(clip)
    if n < 8 or len(hay) < n or not lively(clip):
        return None, 1.0
    step = 4
    coarse = []
    sample = range(0, n, step)
    for o in range(0, len(hay) - n + 1):
        coarse.append((sum((hay[o + k] ^ clip[k]).bit_count() for k in sample), o))
    coarse.sort()
    best = (None, 1e18)
    tried = set()
    for _, o in coarse[:6]:
        for p in range(max(0, o - 2), min(len(hay) - n, o + 2) + 1):
            if p in tried:
                continue
            tried.add(p)
            score = sum((hay[p + k] ^ clip[k]).bit_count() for k in range(n))
            if score < best[1]:
                best = (p, score)
    ber = best[1] / (n * 32)
    return (best[0] if ber <= max_ber else None), ber


def _cluster(cands):
    """The time most of the other episodes agree on: (start, end, how many agree)"""
    best = []
    for c in cands:
        group = [d for d in cands if abs(d[0] - c[0]) <= AGREE and abs(d[1] - c[1]) <= AGREE]
        if len(group) > len(best):
            best = group
    if not best:
        return None
    starts = sorted(g[0] for g in best)
    ends = sorted(g[1] for g in best)
    return starts[len(starts) // 2], ends[len(ends) // 2], len(best)


def analyze(eps):
    """Works out the intro and credits of each episode in one season.
    [eps]: dicts with id, len and prints (from episode_prints). Returns {id: {"intro": [s, e, n], "credits": [...]}}"""
    found = {}
    order = list(range(len(eps)))
    pairs = {}

    def compare(i, j, part):
        k = (min(i, j), max(i, j), part)
        if k not in pairs:
            a, b = eps[k[0]]["prints"][part], eps[k[1]]["prints"][part]
            pairs[k] = shared_stretch(a, b, INTRO_MIN if part == "head" else CREDITS_MIN)
        m = pairs[k]
        if m is None:
            return None
        return (m[0], m[1]) if i == k[0] else (m[2], m[3])

    for i in order:
        ep = eps[i]
        others = sorted((j for j in order if j != i), key=lambda j: (abs(j - i), j))[:NEIGHBOURS]
        result = {}
        intros, credits = [], []
        for j in others:
            m = compare(i, j, "head")
            if m:
                s, e = m[0] * PT + (START_PAD if m[0] else 0.0), m[1] * PT + END_PAD
                if INTRO_MIN <= e - s <= INTRO_MAX:
                    intros.append((s, e))
            m = compare(i, j, "tail")
            if m:
                base = ep["prints"]["tail_start"]
                s, e = base + m[0] * PT + START_PAD, base + m[1] * PT + END_PAD
                if ep["len"] - e < 15:
                    e = ep["len"]  # The credits run to the end
                if CREDITS_MIN <= e - s <= CREDITS_MAX:
                    credits.append((s, e))
        c = _cluster(intros)
        if c:
            result["intro"] = [round(c[0], 2), round(c[1], 2), c[2]]
        c = _cluster(credits)
        if c:
            result["credits"] = [round(c[0], 2), round(c[1], 2), c[2]]
        found[ep["id"]] = result
    return found


# ---------- Chapters ----------

INTRO_NAMES = re.compile(r"\b(intro|opening|op|title sequence|main titles?|theme song)\b", re.I)
CREDITS_NAMES = re.compile(r"\b(credits|ending|ed|end titles|outro|closing)\b", re.I)


def chapter_times(item, length):
    """Intro and credits from the chapter names the file came with, like "Opening" and "End Credits" """
    chapters = sorted(item.get("Chapters") or [], key=lambda c: c.get("StartPositionTicks") or 0)
    out = {}
    for n, ch in enumerate(chapters):
        name = str(ch.get("Name") or "")
        s = (ch.get("StartPositionTicks") or 0) / TICKS
        e = (chapters[n + 1].get("StartPositionTicks") or 0) / TICKS if n + 1 < len(chapters) else length
        if e <= s:
            continue
        if INTRO_NAMES.search(name) and not re.search(r"\b(end|closing)\b", name, re.I):
            if INTRO_MIN <= e - s <= INTRO_MAX * 1.5:
                out.setdefault("Intro", [s, e])
        elif CREDITS_NAMES.search(name) and s > length * 0.5:
            out.setdefault("Outro", [s, e])
    return out


# ---------- The work queue ----------


def _enqueue(kind, key, info, priority):
    with _lock:
        if key == _running:
            return
        if key in _queued and _queued[key] <= priority:
            return
        _queued[key] = priority
        heapq.heappush(_queue, (priority, next(_counter), kind, key, info))
    _wake.set()


def _busy(key):
    with _lock:
        return key in _queued or key == _running


def _worker():
    global _running
    while True:
        with _lock:
            job = None
            while _queue:
                p, _, kind, key, info = heapq.heappop(_queue)
                if _queued.get(key) == p:
                    del _queued[key]
                    job = (kind, key, info)
                    break
            if job:
                _running = job[1]
        if not job:
            _wake.wait(60)
            _wake.clear()
            continue
        kind, key, info = job
        try:
            if kind == "season":
                season_job(info)
            elif kind == "fix":
                fix_job(info)
        except Exception as e:
            log(f"couldn't finish {key}: {e!r}")
        finally:
            with _lock:
                _running = None


def _token_for(info):
    """The sign-in to read Jellyfin with: the person's own, or the admin's for the nightly run"""
    return info.get("token") or _admin.get("token"), info.get("user") or _admin.get("user")


def _season_eps(series_id, season_id, token, user_id):
    eps = []
    for e in _episodes(series_id, season_id, token, user_id):
        f = _local_path(e.get("Path"))
        length = (e.get("RunTimeTicks") or 0) / TICKS
        if f and length > 60:
            eps.append({"id": e["Id"], "file": f, "len": length, "index": e.get("IndexNumber") or 0})
    eps.sort(key=lambda x: x["index"])
    return eps


def season_job(info):
    series_id, season_id = info["series"], info["season"]
    token, user_id = _token_for(info)
    if not tools_ok():
        log("ffmpeg or fpcalc is missing, so seasons can't be checked. Run the Benflix setup command again.")
        return
    eps = _season_eps(series_id, season_id, token, user_id)
    started = time.time()
    keys = {}
    for ep in eps:
        try:
            ep["prints"] = episode_prints(ep)
            keys[ep["id"]] = _file_key(ep["file"])
        except Exception as e:
            log(f"couldn't listen to {os.path.basename(ep['file'])}: {e!r}")
    eps = [ep for ep in eps if "prints" in ep]
    found = analyze(eps) if len(eps) >= 2 else {}
    _write(
        f"seasons/{_safe(season_id)}.json",
        {"version": VERSION, "at": time.time(), "series": series_id, "keys": keys, "episodes": found},
    )
    sure = sum(1 for v in found.values() if v.get("intro", [0, 0, 0])[2] >= 2)
    log(f"checked {len(eps)} episodes of season {_safe(season_id)[:8]} in {time.time() - started:.0f}s ({sure} sure intros)")
    # Fixes made on this show reach this season too
    if (_fixes()["episodes"] or {}) and any(v.get("series") == series_id for v in _fixes()["episodes"].values()):
        _enqueue("fix", f"fix-{series_id}", {"series": series_id, "token": token, "user": user_id}, 1)


def _season_fresh(season_id, eps_keys=None):
    saved = _read(f"seasons/{_safe(season_id)}.json")
    if not saved or saved.get("version") != VERSION:
        return None
    return saved


# ---------- Fixes ----------


def _clip_for(ep, prints, kind, t):
    """The 15 seconds of sound next to a fix: after it for a start, before it for an end.
    Returns (clip, seconds from the clip's start to the fix)."""
    start = t if kind.endswith("_start") else t - CLIP
    start = max(0.0, start)
    part, base = ("head", 0.0) if kind.startswith("intro") else ("tail", prints["tail_start"])
    pts = prints[part]
    a, b = int((start - base) / PT), int((start + CLIP - base) / PT)
    if a >= 0 and b <= len(pts):
        return pts[a:b], t - start
    return fingerprint(ep["file"], start, CLIP), t - start


def fix_job(info):
    """Looks for the sound next to each fix in every other episode of the show"""
    series_id = info["series"]
    token, user_id = _token_for(info)
    fixes = _fixes()
    mine = {k: v for k, v in fixes["episodes"].items() if v.get("series") == series_id}
    seasons = (_get(f"/Shows/{series_id}/Seasons?{urllib.parse.urlencode({'userId': user_id} if user_id else {})}", token)
               or {}).get("Items") or []
    # The season with a fix first, so its episodes are mended soonest
    fixed_seasons = {v.get("season") for v in mine.values()}
    seasons.sort(key=lambda s: 0 if s.get("Id") in fixed_seasons else 1)
    all_eps = {}
    for season in seasons:
        sid = season.get("Id")
        if not _season_fresh(sid):
            season_job({"series": series_id, "season": sid, "token": token, "user": user_id})
        for ep in _season_eps(series_id, sid, token, user_id):
            ep["season"] = sid
            all_eps[ep["id"]] = ep
    prints = {}

    def prints_of(eid):
        if eid not in prints:
            try:
                prints[eid] = episode_prints(all_eps[eid])
            except Exception:
                prints[eid] = None
        return prints[eid]

    out = {}
    for src_id, fix in mine.items():
        src = all_eps.get(src_id)
        if not src or not prints_of(src_id):
            continue
        for kind in ("intro_start", "intro_end", "credits_start", "credits_end"):
            if kind not in fix:
                continue
            t = fix[kind]
            clip, into = _clip_for(src, prints[src_id], kind, t)
            from_end = src["len"] - t
            for eid, ep in all_eps.items():
                if eid == src_id or not prints_of(eid):
                    continue
                p = prints[eid]
                if kind.startswith("intro"):
                    hay, base = p["head"], 0.0
                else:
                    hay, base = p["tail"], p["tail_start"]
                pos, ber = find_clip(clip, hay)
                if pos is None:
                    continue
                at = round(base + pos * PT + into, 2)
                if kind.startswith("credits") and abs((ep["len"] - at) - from_end) > 600:
                    continue
                same_season = ep.get("season") == src.get("season")
                score = ber - (0.05 if same_season else 0)
                have = out.setdefault(eid, {}).get(kind)
                if have is None or score < have[1]:
                    out[eid][kind] = [at, round(score, 3), src_id]
    _write(f"found/{_safe(series_id)}.json", {"at": time.time(), "episodes": out})
    log(f"used {len(mine)} fixes on {len(out)} other episodes of the show")


def add_fix(item, kind, at, user_id):
    """Saves one fix from the player"""
    eid = item["Id"]
    series_id = item.get("SeriesId") or eid
    with _lock:
        fixes = _fixes()
        if kind == "no_intro_show":
            fixes["series"].setdefault(series_id, {})["no_intro"] = True
        elif kind == "undo":
            fixes["episodes"].pop(eid, None)
            if series_id in fixes["series"]:
                fixes["series"][series_id].pop("no_intro", None)
        else:
            mine = fixes["episodes"].setdefault(eid, {})
            mine.update({"series": series_id, "season": item.get("SeasonId"), "by": user_id, "when": int(time.time())})
            if kind == "no_intro":
                mine["no_intro"] = True
                mine.pop("intro_start", None)
                mine.pop("intro_end", None)
            else:
                mine[kind] = round(float(at), 2)
                if kind.startswith("intro"):
                    mine.pop("no_intro", None)
        _write("fixes.json", fixes)


# ---------- Picking the times ----------

PRIORITY = {"you": 100, "fix": 95, "chapters": 85, "benflix": 75, "theintrodb": 70, "server": 40}


def _agrees(a, b, kind):
    if abs(a[0] - b[0]) > AGREE:
        return False
    return kind == "Outro" or abs(a[1] - b[1]) <= AGREE


def pick(cands, length, kind="Intro"):
    """From every source's times for one kind, the one to use. cands: [(start, end, source, sure)]"""
    cands = [c for c in cands if c[1] > c[0] >= 0 and c[0] < max(length, 1)]
    if not cands:
        return None
    kind_check = []
    for c in cands:
        sure = c[3] or any(_agrees(c, d, kind) and d[2] != c[2] for d in cands)
        kind_check.append((PRIORITY[c[2]] >= 90, sure, PRIORITY[c[2]], c))
    kind_check.sort(key=lambda x: (x[0], x[1], x[2]), reverse=True)
    best = kind_check[0]
    s, e, source, _ = best[3]
    return {"start": round(s, 2), "end": round(min(e, length) if length else e, 2), "source": source, "sure": bool(best[1] or best[0])}


def _fix_segment(kind, fix, found, auto, length):
    """Turns the fix markers (the person's own, else ones found from fixes on other episodes) into a time"""
    start_key, end_key = ("intro_start", "intro_end") if kind == "Intro" else ("credits_start", "credits_end")
    marks, source = {}, None
    for key in (start_key, end_key):
        if key in fix:
            marks[key], source = fix[key], "you"
        elif key in found:
            marks[key] = found[key][0]
            source = source or "fix"
    if not marks:
        return None
    s = marks.get(start_key)
    e = marks.get(end_key)
    if s is None:
        s = auto[0] if auto and auto[0] < e else max(0.0, e - 90 if kind == "Intro" else e - 120)
    if e is None:
        if kind == "Outro":
            e = auto[1] if auto and auto[1] > s else length
        else:
            e = auto[1] if auto and auto[1] > s and auto[1] - s <= INTRO_MAX else s + 90
    if e <= s:
        return None
    return (s, e, source, True)


def answer(item_id, token, user_id, is_admin):
    """Benflix asks this when an episode or movie starts playing"""
    if is_admin and token and _admin.get("token") != token:
        _admin.update({"token": token, "user": user_id})
        _write("admin.json", dict(_admin))
        try:
            os.chmod(_path("admin.json"), 0o600)
        except OSError:
            pass
    item = _item(item_id, token, user_id)
    length = (item.get("RunTimeTicks") or 0) / TICKS
    eid = item.get("Id") or item_id
    series_id, season_id = item.get("SeriesId"), item.get("SeasonId")
    is_episode = item.get("Type") == "Episode" and series_id and season_id
    cands = {t: [] for t in JELLYFIN_TYPES}
    ready = True
    if is_episode:
        saved = _season_fresh(season_id)
        f = _local_path(item.get("Path"))
        if saved and f and saved.get("keys", {}).get(eid) not in (None, _file_key(f)):
            saved = None  # The file was swapped for a new one
        if saved:
            mine = saved.get("episodes", {}).get(eid) or {}
            for name, kind in (("intro", "Intro"), ("credits", "Outro")):
                if name in mine:
                    s, e, n = mine[name]
                    cands[kind].append((s, e, "benflix", n >= 2))
        elif tools_ok():
            ready = False
            _enqueue("season", f"season-{season_id}", {"series": series_id, "season": season_id, "token": token, "user": user_id}, 0)
        if _busy(f"fix-{series_id}"):
            ready = False
    for kind, (s, e) in chapter_times(item, length).items():
        cands[kind].append((s, e, "chapters", True))
    try:
        series = _item(series_id, token, user_id) if is_episode else None
        for kind, (s, e) in _tidb(item, series, length).items():
            cands[kind].append((s, e, "theintrodb", False))
    except Exception as e:
        log(f"TheIntroDB lookup failed: {e!r}")
    for kind, s, e in _server_segments(eid, token):
        cands[kind].append((s, e, "server", False))

    fixes = _fixes()
    fix = fixes["episodes"].get(eid) or {}
    found = ((_read(f"found/{_safe(series_id)}.json") or {}).get("episodes") or {}).get(eid, {}) if is_episode else {}
    none = []
    no_intro = fix.get("no_intro") or (fixes["series"].get(series_id or "") or {}).get("no_intro")
    segments = []
    for kind in JELLYFIN_TYPES:
        if kind == "Intro" and no_intro:
            none.append("Intro")
            continue
        auto = pick(cands[kind], length, kind)
        auto_pair = (auto["start"], auto["end"]) if auto else None
        if kind in ("Intro", "Outro"):
            fixed = _fix_segment(kind, fix, found, auto_pair, length)
            if fixed:
                cands[kind].append(fixed)
        chosen = pick(cands[kind], length, kind)
        if chosen:
            segments.append({"type": kind, **chosen})
    return {"ready": ready, "segments": segments, "none": none, "item": eid}


def route(method, path, query, token, user_id, is_admin, is_kids, read_json):
    """The helper sends /api/skips/... here. Returns (status, answer)."""
    if not _dir:
        return 503, {"error": "Skip times are off: the helper has no folder to save them in."}
    if method == "GET" and path == "/api/skips/status":
        return 200, status()
    m = re.fullmatch(r"/api/skips/([0-9a-fA-F-]{32,36})", path)
    if method == "GET" and m:
        return 200, answer(m.group(1), token, user_id, is_admin)
    m = re.fullmatch(r"/api/skips/([0-9a-fA-F-]{32,36})/fix", path)
    if method == "POST" and m:
        if is_kids:
            return 403, {"error": "Ask a grown-up to fix skip times."}
        body = read_json() or {}
        kind = str(body.get("kind") or "")
        if kind not in FIX_KINDS:
            return 400, {"error": "That isn't a fix Benflix knows."}
        try:
            at = float(body.get("at") or 0)
        except (TypeError, ValueError):
            return 400, {"error": "That time didn't make sense."}
        item = _item(m.group(1), token, user_id)
        add_fix(item, kind, at, user_id)
        series_id = item.get("SeriesId")
        if series_id and kind not in ("no_intro", "no_intro_show") and tools_ok():
            _enqueue("fix", f"fix-{series_id}", {"series": series_id, "token": token, "user": user_id}, 0)
        result = answer(m.group(1), token, user_id, is_admin)
        result["saved"] = FIX_KINDS[kind]
        return 200, result
    return 404, {"error": "Not found"}


# ---------- Every night: get every season ready ----------


def _nightly():
    done_day = None
    while True:
        time.sleep(600)
        now = time.localtime()
        if not (3 <= now.tm_hour < 5) or done_day == now.tm_yday:
            continue
        done_day = now.tm_yday
        token, user_id = _admin.get("token"), _admin.get("user")
        if not token or not tools_ok():
            continue
        try:
            q = urllib.parse.urlencode({"IncludeItemTypes": "Season", "Recursive": "true", "userId": user_id or ""})
            seasons = (_get(f"/Items?{q}", token) or {}).get("Items") or []
            added = 0
            for s in seasons:
                if s.get("SeriesId") and not _season_fresh(s["Id"]):
                    _enqueue("season", f"season-{s['Id']}", {"series": s["SeriesId"], "season": s["Id"]}, 5)
                    added += 1
            log(f"night run: {added} seasons to check")
        except Exception as e:
            log(f"night run couldn't start: {e!r}")
